package com.ican.tvplay.data

import android.content.Context
import android.util.Log
import com.ican.tvplay.data.local.FavoriteEntity
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.data.remote.TvBoxSite
import com.ican.tvplay.data.remote.VodApiClient
import com.ican.tvplay.data.remote.VodParse
import com.ican.tvplay.ui.components.siteShortName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 【临时测试数据】用玩偶哥哥站源真实数据造几部追番更新，
 * 用于验证：收藏页入口的今日更新数、启动「更新提醒」弹窗、追番更新页的滚动列表。
 *
 * 只在本机首次启动时执行一次（prefs 记 DONE_KEY）；删掉本文件并移除
 * TvPlayApplication / AppRoot 里的两处调用即可完全还原。
 */
object DebugUpdateSeed {

    private const val TAG = "DebugUpdateSeed"
    private const val SITE_NAME_PART = "玩偶"
    private const val DONE_KEY = "debug_seed_xianni_v4"

    /** 前 3 部记为「今日更新」，其余记为「待看更新」（更新时间放到昨天），让更新页能滚动 */
    private val KEYWORDS = listOf(
        "仙逆",
        "斗破苍穹",
        "凡人修仙传",
        "完美世界",
        "吞噬星空",
        "遮天",
    )

    suspend fun runOnceIfNeeded(context: Context, container: AppContainer) =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("tvplay_settings", Context.MODE_PRIVATE)
            if (prefs.getBoolean(DONE_KEY, false)) return@withContext

            val settings = container.settingsRepository
            val configUrl = settings.configUrl.value
            if (configUrl.isBlank()) return@withContext

            val apiClient = VodApiClient()
            val config = apiClient.loadConfig(configUrl) ?: return@withContext
            val site = config.sites.firstOrNull {
                (it.name.contains(SITE_NAME_PART) || it.key.contains(SITE_NAME_PART)) &&
                    (it.isSpider() || it.api.startsWith("http"))
            } ?: run {
                Log.d(TAG, "玩偶哥哥站源未找到")
                return@withContext
            }
            val jarSpec = if (site.isSpider()) site.jar.ifBlank { config.spider } else ""

            var written = 0
            KEYWORDS.forEachIndexed { index, keyword ->
                runCatching {
                    seedOne(keyword, index < 3, container, apiClient, site, jarSpec)
                }.onSuccess {
                    if (it) written++
                }.onFailure {
                    Log.e(TAG, "写入失败：$keyword", it)
                }
            }

            if (written > 0) {
                // 清空「今天已弹过」标记，让启动更新提醒弹窗能弹一次
                settings.setFavoriteUpdatePopupDate("")
                prefs.edit().putBoolean(DONE_KEY, true).apply()
            }
            Log.d(TAG, "本轮写入 $written/${KEYWORDS.size} 部")
        }

    private suspend fun seedOne(
        keyword: String,
        today: Boolean,
        container: AppContainer,
        apiClient: VodApiClient,
        site: TvBoxSite,
        jarSpec: String,
    ): Boolean {
        val video = if (site.isSpider()) {
            val spider = container.spiderManager.getSpider(site, jarSpec) ?: return false
            // 搜索结果不带选集，先拿到 vodId 再拉详情（与播放页同一口径）
            val hits = VodParse.parseVodListJson(
                container.spiderManager.searchContent(spider, keyword, quick = true),
            )
            val candidates = hits.filter { it.vodName.contains(keyword) && !it.vodName.contains("剧场") }
            val hit = candidates.firstOrNull { it.vodName.trim() == keyword }
                ?: candidates.firstOrNull()
                ?: return false
            val detail = VodParse.parseVodListJson(
                container.spiderManager.detailContent(spider, listOf(hit.vodId)),
            ).firstOrNull() ?: return false
            Log.d(TAG, "$keyword → ${detail.vodName}")
            with(VodParse) { detail.toVideo(withEpisodes = true) }
        } else {
            val hits = apiClient.search(site, keyword)
            val hit = hits.firstOrNull { it.vodName.contains(keyword) } ?: return false
            val detail = apiClient.detail(site, hit.vodId) ?: return false
            with(VodParse) { detail.toVideo(withEpisodes = true) }
        }

        // 与 FavoriteUpdateChecker 同口径：取详情首条线路（否则启动检测会用首条集数覆盖当前记录）
        val line = video.playSources.firstOrNull()
        val episodes = line?.episodes.orEmpty()
        val total = episodes.size
        if (total < 3) {
            Log.d(TAG, "$keyword 集数不足，跳过：total=$total")
            return false
        }

        // 造「已看到第 total-3 集、站源已更到第 total 集」：待看 2 集
        val seenIndex = total - 3
        val seenTitle = episodes[seenIndex].title.ifBlank { "第${seenIndex + 1}集" }
        val now = System.currentTimeMillis()
        val updatedAt = if (today) now else now - 24 * 60 * 60 * 1000L
        val sourceName = siteShortName(site.name, site.key)

        container.favoriteDao.upsert(
            FavoriteEntity(
                videoId = video.id,
                title = video.title,
                cover = video.cover,
                categoryName = video.categoryName,
                addedAt = now,
                sourceKey = site.key,
                sourceName = sourceName,
                episodeCount = total,
                ackEpisodeCount = seenIndex + 1,
                lastUpdatedAt = updatedAt,
                lastCheckedAt = now,
            ),
        )
        container.historyDao.upsert(
            HistoryEntity(
                videoId = video.id,
                title = video.title,
                cover = video.cover,
                categoryName = video.categoryName,
                episodeIndex = seenIndex,
                episodeTitle = seenTitle,
                positionMs = 18 * 60_000L,
                durationMs = 24 * 60_000L,
                updatedAt = now,
                sourceKey = site.key,
                sourceName = sourceName,
                lineFlag = line?.flag.orEmpty(),
            ),
        )
        Log.d(TAG, "已写入：${video.title} 共 $total 集，已看到第 ${seenIndex + 1} 集")
        return true
    }
}
