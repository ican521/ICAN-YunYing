package com.ican.tvplay.util

import android.os.IBinder
import android.widget.Toast
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference

/**
 * 屏蔽 spider jar（第三方站点爬虫）弹出的弹幕相关杂音 Toast（如「弹幕加载失败」「弹幕服务启动失败」）。
 *
 * 背景：这类文案来自站点 jar 自身（jar 与本 App 同进程），App 内没有对应代码，
 * 只能从进程级 Toast 入队处过滤。与 fongmi 表现一致（fongmi 也会显示站点包杂音提示）。
 *
 * 原理：Toast.show() 最终通过进程内缓存的 INotificationManager 代理（Toast.sService）把文案入队，
 * 反射把该代理替换为动态代理，enqueue*Toast 时按文案关键词过滤，命中则不入队（不显示）。
 * 只过滤入队，不触碰播放链路，因此不影响视频加载。
 *
 * 安全性：原始服务**延迟解析**——Application 启动时 sService 往往还是 null，
 * 若直接缓存 null 会导致所有 Toast 被吞掉（历史版本的问题）。这里在 install 时尝试取值，
 * 为空时在首次放行时经 ServiceManager 现取并缓存；任一步失败都静默放弃，不影响其它 Toast。
 */
object ToastBlocker {

    /** 命中即不显示的文案关键词（弹幕类杂音提示） */
    private val BLOCK_KEYWORDS = listOf("弹幕加载失败", "弹幕服务")

    /** 进程内原始 INotificationManager（延迟解析，缓存复用） */
    private val original = AtomicReference<Any?>(null)

    private var installed = false

    fun install() {
        if (installed) return
        // 这些成员属系统隐藏 API，先申请豁免，保证反射在任意 Android 版本上可用
        runCatching {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/widget/Toast;",
                "Landroid/app/INotificationManager;",
                "Landroid/app/INotificationManager\$Stub;",
                "Landroid/os/ServiceManager;",
            )
        }
        try {
            val field = Toast::class.java.getDeclaredField("sService")
            field.isAccessible = true
            // 此刻通常为 null（首次 Toast 才初始化），交由 service() 延迟解析
            original.set(field.get(null))

            val iface = Class.forName("android.app.INotificationManager")
            val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, method, args ->
                if (isToastEnqueue(method.name) && shouldBlock(args)) {
                    null // 命中屏蔽文案：不入队，Toast 不显示
                } else {
                    invokeOriginal(method, args)
                }
            }
            field.set(null, proxy)
            installed = true
        } catch (_: Throwable) {
            // hook 失败（系统字段结构不兼容等）静默放弃，绝不因为屏蔽功能影响正常 Toast
        }
    }

    /** 仅拦截 Toast 入队方法：enqueueToast / enqueueTextToast */
    private fun isToastEnqueue(name: String): Boolean =
        name.startsWith("enqueue") && name.endsWith("Toast")

    /** 入队参数里是否带命中关键词的文案（文本 Toast 的文案在参数中） */
    private fun shouldBlock(args: Array<Any?>?): Boolean =
        args?.any { arg ->
            when (arg) {
                is CharSequence -> matches(arg)
                is Array<*> -> arg.any { it is CharSequence && matches(it) }
                else -> false
            }
        } ?: false

    private fun matches(text: CharSequence): Boolean =
        BLOCK_KEYWORDS.any { text.contains(it) }

    private fun invokeOriginal(method: Method, args: Array<Any?>?): Any? = try {
        val target = service()
        if (target == null) null else method.invoke(target, *(args ?: emptyArray<Any?>()))
    } catch (_: Throwable) {
        null
    }

    /** 原始服务：优先取 install 时抓到的；为空则经 ServiceManager 现取一次并缓存 */
    private fun service(): Any? {
        original.get()?.let { return it }
        return try {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "notification") as? IBinder ?: return null
            val stub = Class.forName("android.app.INotificationManager\$Stub")
            val svc = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            if (svc != null) original.compareAndSet(null, svc)
            original.get()
        } catch (_: Throwable) {
            null
        }
    }
}
