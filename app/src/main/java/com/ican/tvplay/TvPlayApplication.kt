package com.ican.tvplay

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import com.ican.tvplay.data.AppContainer
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Application 入口：持有应用级依赖容器（数据库 / 设置 / 仓库）。
 */
class TvPlayApplication : Application() {

    lateinit var container: AppContainer
        private set

    companion object {
        /**
         * 反射调用隐藏的 ApplicationInfo.setEnableOnBackInvokedCallback，
         * 运行时开关系统预测性返回（照搬 KernelSU 方案，无需重启）。
         */
        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
            runCatching {
                val applicationInfoClass = ApplicationInfo::class.java
                val method = applicationInfoClass.getDeclaredMethod(
                    "setEnableOnBackInvokedCallback",
                    Boolean::class.javaPrimitiveType,
                )
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 显式关闭系统预测性返回（与 KernelSU 一致：manifest 默认 false）。
        // 若开启，MIUI/HyperOS 会在返回手势时叠加系统级"整页圆角缩小"动画；
        // 我们的返回跟手由 AppRoot 内的应用内手势层实现，不依赖系统路径。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback",
            )
            setEnableOnBackInvokedCallback(applicationInfo, false)
        }
    }
}
