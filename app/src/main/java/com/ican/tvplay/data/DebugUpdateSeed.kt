package com.ican.tvplay.data

import android.content.Context
import android.util.Log
import com.ican.tvplay.data.local.FavoriteEntity
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.data.remote.VodApiClient
import com.ican.tvplay.data.remote.VodParse
import com.ican.tvplay.ui.components.siteShortName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 【临时测试数据】用站源真实数据造一条「仙逆 · 玩偶哥哥」的追番更新，
 * 用于验证：收藏页入口的今日更新数、启动「更新提醒」弹窗、追番更新页的条目。
 *
 * 只在本机首次启动时执行一次（prefs 记 DONE_KEY）；删掉本文件并移除
 * TvPlayApplication / AppRoot 里的两处调用即可完全还原。
 */
object DebugUpdateSeed {

    private const val TAG = "DebugUpdateSeed"
    private const val KEYWORD = "仙逆"
    private const val SITE_NAME_PART = "玩偶"
    private const val DONE_KEY = "debug_seed_xianni_v2"

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

            val video = runCatching {
                if (site.isSpider()) {
                    val spider = container.spiderManager.getSpider(site, jarSpec) ?: return@withContext
                    // 搜索结果不带选集，先拿到 vodId 再拉详情（与播放页同一口径）
                    val hits = VodParse.parseVodListJson(
                        container.spiderManager.searchContent(spider, KEYWORD, quick = true),
                    )
                    Log.d(TAG, "搜索命中=${hits.map { it.vodName }}")
                    // 「仙逆」正片（排除剧场版等单集条目）
                    val hit = hits.filter { it.vodName.contains(KEYWORD) && !it.vodName.contains("剧场") }
                        .firstOrNull { it.vodName.trim() == KEYWORD }
                        ?: hits.filter { it.vodName.contains(KEYWORD) && !it.vodName.contains("剧场") }
                            .firstOrNull()
                        ?: return@withContext
                    val detailJson = container.spiderManager.detailContent(spider, listOf(hit.vodId))
                    val detail = VodParse.parseVodListJson(detailJson).firstOrNull() ?: return@withContext
                    Log.d(TAG, "搜索命中=${hit.vodName}，详情=${detail.vodName}")
                    with(VodParse) { detail.toVideo(withEpisodes = true) }.also {
                        Log.d(TAG, "线路集数=${it.playSources.map { l -> "${l.flag}:${l.episodes.size}" }}")
                    }
                } else {
                    val hits = apiClient.search(site, KEYWORD)
                    val hit = hits.firstOrNull { it.vodName.contains(KEYWORD) } ?: hits.firstOrNull()
                        ?: return@withContext
                    val detail = apiClient.detail(site, hit.vodId) ?: return@withContext
                    with(VodParse) { detail.toVideo(withEpisodes = true) }
                }
            }.onFailure { Log.e(TAG, "拉详情失败", it) }.getOrNull() ?: return@withContext

            val lines = video.playSources
            // 与 FavoriteUpdateChecker 同口径：取详情首条线路（否则启动检测会用首条集数覆盖当前记录）
            val line = lines.firstOrNull()
            val episodes = line?.episodes.orEmpty()
            val total = episodes.size
            if (total < 3) {
                Log.d(TAG, "集数不足，跳过：total=$total")
                return@withContext
            }

            // 造「已看到第 total-3 集、站源已更到第 total 集」：待看 2 集，且更新发生在今天
            val seenIndex = total - 3
            val seenTitle = episodes[seenIndex].title.ifBlank { "第${seenIndex + 1}集" }
            val now = System.currentTimeMillis()
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
                    lastUpdatedAt = now,
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

            // 清空「今天已弹过」标记，让启动更新提醒弹窗能弹一次
            settings.setFavoriteUpdatePopupDate("")
            prefs.edit().putBoolean(DONE_KEY, true).apply()
            Log.d(TAG, "已写入：${video.title} 共 $total 集，已看到第 ${seenIndex + 1} 集")
        }
}
