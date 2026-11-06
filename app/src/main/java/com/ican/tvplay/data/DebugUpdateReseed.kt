package com.ican.tvplay.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 【临时测试】把已有的追番测试数据回退成「等待检测发现更新」的状态，
 * 用来验证「检测一个、面板出现一个」的效果：
 * - episodeCount 调小，检测时每部都会被判定为集数增加；
 * - lastUpdatedAt 挪到昨天，启动瞬间算出的面板为空（不会一次性全弹）；
 * - 清空弹窗日期与上次检测时间，保证本次启动一定重新检测。
 *
 * 只执行一次（prefs 记 DONE_KEY）；验证完删掉本文件并移除 TvPlayApplication 里的调用即可。
 */
object DebugUpdateReseed {

    private const val TAG = "DebugUpdateReseed"
    private const val DONE_KEY = "debug_reseed_wait_check_v1"

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

            val favorites = container.favoriteDao.getAll()
            val targets = favorites.filter { favorite ->
                KEYWORDS.any { favorite.title.contains(it) }
            }
            if (targets.isEmpty()) {
                Log.d(TAG, "未找到测试收藏，跳过")
                prefs.edit().putBoolean(DONE_KEY, true).apply()
                return@withContext
            }

            val yesterday = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            targets.forEach { favorite ->
                container.favoriteDao.updateTracking(
                    videoId = favorite.videoId,
                    // 比真实集数小，检测拉回真实集数时即判定为「有更新」
                    episodeCount = (favorite.episodeCount - 5).coerceAtLeast(0),
                    ackEpisodeCount = favorite.ackEpisodeCount,
                    lastUpdatedAt = yesterday,
                    lastCheckedAt = 0L,
                )
            }
            // 清空弹窗日期和上次检测时间，保证本次启动会重新检测并弹面板
            container.settingsRepository.setFavoriteUpdatePopupDate("")
            container.settingsRepository.setFavoriteUpdateLastCheckAt(0L)
            prefs.edit().putBoolean(DONE_KEY, true).apply()
            Log.d(TAG, "已回退 ${targets.size} 部测试收藏，等待逐条检测")
        }
}
