package com.ican.tvplay

import android.app.Application
import com.ican.tvplay.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application 入口：持有应用级依赖容器（数据库 / 设置 / 仓库）。
 */
class TvPlayApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        android.util.Log.d("Startup", "app onCreate begin")
        super.onCreate()
        container = AppContainer(this)
        android.util.Log.d("Startup", "app onCreate end")

        // 启动本地代理服务器（fongmi 同款）：spider jar 返回 127.0.0.1/proxy 地址时由本机中转拉流
        appScope.launch {
            runCatching {
                com.ican.tvplay.data.remote.LocalProxyServer.start(container.spiderManager)
            }.onFailure { android.util.Log.e("Startup", "LocalProxyServer start fail", it) }
        }

        // 启动时预加载已保存的接口配置，拉取站点与分类
        appScope.launch {
            android.util.Log.d("Startup", "ensureLoaded launch")
            container.videoRepository.ensureLoaded()
        }
    }
}
