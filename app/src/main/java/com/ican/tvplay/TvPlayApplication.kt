package com.ican.tvplay

import android.app.Application
import com.ican.tvplay.data.AppContainer

/**
 * Application 入口：持有应用级依赖容器（数据库 / 设置 / 仓库）。
 */
class TvPlayApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
