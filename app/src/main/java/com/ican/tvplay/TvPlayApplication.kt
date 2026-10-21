package com.ican.tvplay

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.ican.tvplay.data.AppContainer
import com.ican.tvplay.data.remote.VodApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.TimeUnit

/**
 * Application 入口：持有应用级依赖容器（数据库 / 设置 / 仓库）。
 */
class TvPlayApplication : Application(), SingletonImageLoader.Factory {

    companion object {
        /**
         * 图片请求默认浏览器 UA（对齐 fongmi Glide 默认值）。
         * 部分图床会拒绝 okhttp/x 这类客户端 UA；URL 自带 @User-Agent= 时以其为准。
         */
        private val imageUserAgent: String
            get() = System.getProperty("http.agent")
                ?: "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Mobile Safari/537.36"

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
        // 屏蔽 spider jar 弹出的弹幕类杂音 Toast（如「弹幕加载失败」），只过滤不显示，不影响视频加载
        com.ican.tvplay.util.ToastBlocker.install()
        applyPredictiveBack(applicationInfo, container.settingsRepository.enablePredictiveBack.value)
        android.util.Log.d("Startup", "app onCreate end")

        // 启动本地代理服务器（fongmi 同款）：spider jar 返回 127.0.0.1/proxy 地址时由本机中转拉流
        appScope.launch {
            runCatching {
                com.ican.tvplay.data.remote.LocalProxyServer.start(this@TvPlayApplication, container.spiderManager)
            }.onFailure { android.util.Log.e("Startup", "LocalProxyServer start fail", it) }
        }

        // 启动时预加载已保存的接口配置，拉取站点与分类
        appScope.launch {
            android.util.Log.d("Startup", "ensureLoaded launch")
            container.videoRepository.ensureLoaded()
        }
    }

    /**
     * Coil 单例 ImageLoader：海报走专用 OkHttp，复用全局连接池；
     * 未显式携带 UA 的图片请求统一补浏览器 UA（fongmi Glide 同款行为）。
     */
    override fun newImageLoader(context: android.content.Context): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectionPool(VodApiClient.sharedPool)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.header("User-Agent").isNullOrBlank()) {
                    chain.proceed(
                        request.newBuilder().header("User-Agent", imageUserAgent).build(),
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }
}
