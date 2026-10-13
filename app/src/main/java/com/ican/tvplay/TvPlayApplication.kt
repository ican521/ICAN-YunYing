package com.ican.tvplay

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import com.ican.tvplay.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Application 入口：持有应用级依赖容器（数据库 / 设置 / 仓库）。
 */
class TvPlayApplication : Application() {

    companion object {
        /**
         * 照搬 KernelSU：HiddenApiBypass + 反射 ApplicationInfo.setEnableOnBackInvokedCallback
         * 动态开关系统预测返回（manifest 保持 false，开关状态由设置持久化）。
         * 已在运行的 Activity 需 recreate 后才按新值重新注册返回回调。
         */
        fun applyPredictiveBack(appInfo: ApplicationInfo, enable: Boolean) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
            runCatching {
                HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback",
                )
                val method = ApplicationInfo::class.java.getDeclaredMethod(
                    "setEnableOnBackInvokedCallback",
                    Boolean::class.javaPrimitiveType,
                )
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }
        }
    }

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        android.util.Log.d("Startup", "app onCreate begin")
        super.onCreate()
        container = AppContainer(this)
        applyPredictiveBack(applicationInfo, container.settingsRepository.enablePredictiveBack.value)
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
