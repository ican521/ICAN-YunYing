package com.ican.tvplay.data

import android.util.Log
import com.ican.tvplay.data.local.FavoriteDao
import com.ican.tvplay.data.local.FavoriteEntity
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.data.remote.SpiderManager
import com.ican.tvplay.data.remote.TvBoxSite
import com.ican.tvplay.data.remote.VodApiClient
import com.ican.tvplay.data.remote.VodItem
import com.ican.tvplay.data.remote.VodParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

private const val TAG = "FavoriteUpdate"

/** 一条「有更新待看」的收藏（计算产物，不落库） */
data class FavoriteUpdateItem(
    val favorite: FavoriteEntity,
    val history: HistoryEntity?,
    /** 站源最新总集数 */
    val totalEpisodes: Int,
    /** 点条目进播放页的位置：历史记录所在集（有历史就续播），没有历史从头开始 */
    val resumeIndex: Int,
    /** 最近一次集数增加发生在今天 */
    val updatedToday: Boolean,
)

/** 追更计算规则：统一给收藏页入口、追更页、启动弹窗使用 */
object FavoriteUpdates {

    /**
     * 由收藏表计算「详情页集数比上次记录变多了」的列表：
     * 只比较详情页集数（[FavoriteEntity.episodeCount]）与上次已确认的集数（ackEpisodeCount），
     * 与当前看到第几集无关；历史记录只用于展示「已看到」和点击进入时的续播位置。
     */
    fun compute(
        favorites: List<FavoriteEntity>,
        histories: List<HistoryEntity>,
        now: Long = System.currentTimeMillis(),
    ): List<FavoriteUpdateItem> {
        val historyById = histories.associateBy { it.videoId }
        return favorites.mapNotNull { favorite ->
            val total = favorite.episodeCount
            if (total <= 0) return@mapNotNull null
            val history = historyById[favorite.videoId]
            val acked = favorite.ackEpisodeCount.coerceIn(0, total)
            if (acked >= total) return@mapNotNull null
            FavoriteUpdateItem(
                favorite = favorite,
                history = history,
                totalEpisodes = total,
                resumeIndex = history?.episodeIndex?.coerceIn(0, total - 1) ?: 0,
                updatedToday = isSameDay(favorite.lastUpdatedAt, now),
            )
        }.sortedWith(
            compareByDescending<FavoriteUpdateItem> { it.favorite.lastUpdatedAt }
                .thenByDescending { it.favorite.addedAt },
        )
    }

    /** 时间戳是否落在同一自然日（0 视为没有更新） */
    fun isSameDay(timestamp: Long, now: Long): Boolean {
        if (timestamp <= 0L) return false
        val a = Calendar.getInstance().apply { timeInMillis = timestamp }
        val b = Calendar.getInstance().apply { timeInMillis = now }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    /** 弹窗日期键（yyyy-MM-dd），用于「每天只弹一次」 */
    fun todayKey(now: Long = System.currentTimeMillis()): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
}

/**
 * 收藏剧集更新检测：启动时按收藏记录的站源拉详情，比较最新总集数。
 * - 首次检测只建立基线（不当作「今天更新」，避免刚收藏就误报一次）；
 * - 集数增加时记录 lastUpdatedAt = 当前时间；
 * - 检测失败（站源失效/网络异常）不写库，下次再试。
 */
class FavoriteUpdateChecker(
    private val favoriteDao: FavoriteDao,
    private val settings: SettingsRepository,
    private val spiderManager: SpiderManager,
    private val apiClient: VodApiClient = VodApiClient(),
) {

    /**
     * 执行检测。返回本次检测到「集数增加」的剧集数量。
     * [force] = true 忽略频率节流（手动「重新检查」）。
     */
    suspend fun check(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        if (!settings.favoriteUpdateCheckEnabled.value) return@withContext 0
        val configUrl = settings.configUrl.value
        if (configUrl.isBlank()) return@withContext 0

        val now = System.currentTimeMillis()
        val intervalMs = settings.favoriteUpdateIntervalHours.value * 60L * 60L * 1000L
        if (!force && intervalMs > 0 && now - settings.favoriteUpdateLastCheckAt() < intervalMs) {
            return@withContext 0
        }

        val config = apiClient.loadConfig(configUrl) ?: return@withContext 0
        val favorites = favoriteDao.getAll()
        if (favorites.isEmpty()) {
            settings.setFavoriteUpdateLastCheckAt(now)
            return@withContext 0
        }

        val defaultSite = config.sites.firstOrNull { it.key == settings.siteKey.value }
        val semaphore = Semaphore(4)
        val flags = coroutineScope {
            favorites.map { favorite ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val site = config.sites.firstOrNull { it.key == favorite.sourceKey }
                            ?: defaultSite
                            ?: return@withPermit 0
                        val latest = withTimeoutOrNull(20_000L) {
                            runCatching { fetchEpisodeCount(site, config.spider, favorite.videoId) }
                                .onFailure {
                                    Log.d(TAG, "check fail ${favorite.title}: ${it.message}")
                                }.getOrNull()
                        }
                        if (latest == null || latest <= 0) return@withPermit 0
                        if (applyResult(favorite, latest, System.currentTimeMillis())) 1 else 0
                    }
                }
            }.awaitAll()
        }
        val increased = flags.sum()
        settings.setFavoriteUpdateLastCheckAt(System.currentTimeMillis())
        Log.d(TAG, "check done favorites=${favorites.size} increased=$increased")
        increased
    }

    /** 取站源详情里首条线路的集数（与播放页「更新至第 x 集」口径一致）；无法解析返回 0 */
    private suspend fun fetchEpisodeCount(
        site: TvBoxSite,
        configSpider: String,
        videoId: String,
    ): Int {
        val item: VodItem = (if (site.isSpider()) {
            val jarSpec = site.jar.ifBlank { configSpider }
            val spider = spiderManager.getSpider(site, jarSpec) ?: return 0
            VodParse.parseVodListJson(
                spiderManager.detailContent(spider, listOf(videoId)),
            ).firstOrNull()
        } else {
            apiClient.detail(site, videoId)
        }) ?: return 0
        val video = with(VodParse) { item.toVideo(withEpisodes = true) }
        return video.playSources.firstOrNull()?.episodes?.size
            ?: video.episodes.size
    }

    /** 写回检测结果；返回是否「集数增加（算一次更新）」 */
    private suspend fun applyResult(
        favorite: FavoriteEntity,
        latest: Int,
        now: Long,
    ): Boolean {
        if (favorite.episodeCount <= 0) {
            // 首次检测：只建基线，不当作更新（旧收藏/刚收藏都从当前集数开始算）
            favoriteDao.updateTracking(
                videoId = favorite.videoId,
                episodeCount = latest,
                ackEpisodeCount = latest,
                lastUpdatedAt = 0L,
                lastCheckedAt = now,
            )
            return false
        }
        val increased = latest > favorite.episodeCount
        favoriteDao.updateTracking(
            videoId = favorite.videoId,
            episodeCount = latest,
            // 集数回退时基线不超过总集数，避免「待看 = 负数」
            ackEpisodeCount = favorite.ackEpisodeCount.coerceAtMost(latest),
            lastUpdatedAt = if (increased) now else favorite.lastUpdatedAt,
            lastCheckedAt = now,
        )
        return increased
    }
}
