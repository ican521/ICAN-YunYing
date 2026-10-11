package com.ican.tvplay.data

import android.content.Context
import com.ican.tvplay.data.local.AppDatabase
import com.ican.tvplay.data.local.FavoriteDao
import com.ican.tvplay.data.local.HistoryDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 应用级简易依赖容器 */
class AppContainer(context: Context) {

    /**
     * 应用级协程域：退出播放页等 composition 销毁场景下，
     * 进度/历史落库不能用 rememberCoroutineScope（会随页面取消），必须走这里
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: AppDatabase by lazy { AppDatabase.create(context) }

    val favoriteDao: FavoriteDao by lazy { database.favoriteDao() }
    val historyDao: HistoryDao by lazy { database.historyDao() }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }
    val videoRepository: VideoRepository by lazy {
        VideoRepository(context.applicationContext, settingsRepository)
    }

    /** 全局 spider 管理器：播放页跨站源延迟解析播放地址时使用（jar 内部缓存为进程级常驻） */
    val spiderManager: com.ican.tvplay.data.remote.SpiderManager by lazy {
        com.ican.tvplay.data.remote.SpiderManager(context.applicationContext)
    }
}
