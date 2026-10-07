package com.ican.tvplay.data

import android.content.Context
import com.ican.tvplay.data.local.AppDatabase
import com.ican.tvplay.data.local.FavoriteDao
import com.ican.tvplay.data.local.HistoryDao

/** 应用级简易依赖容器 */
class AppContainer(context: Context) {

    private val database: AppDatabase by lazy { AppDatabase.create(context) }

    val favoriteDao: FavoriteDao by lazy { database.favoriteDao() }
    val historyDao: HistoryDao by lazy { database.historyDao() }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }
    val videoRepository: VideoRepository by lazy { VideoRepository(settingsRepository) }
}
