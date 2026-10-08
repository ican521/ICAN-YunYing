package com.ican.tvplay.util

import android.widget.Toast
import java.lang.reflect.Proxy

/**
 * 屏蔽 spider jar（第三方站点爬虫）弹出的杂音 Toast（如「弹幕服务启动失败」）。
 *
 * 原理：jar 与本 App 同进程，Toast.show() 最终通过进程内缓存的 INotificationManager
 * 服务代理（Toast.sService）把文案入队到系统。反射将该代理替换为动态代理，
 * enqueue 时按文案关键词过滤，命中则不入队。
 *
 * 各 Android 版本字段结构可能不同，任何一步失败都静默放弃，不影响正常功能。
 */
object ToastBlocker {

    /** 需要屏蔽的文案关键词 */
    private const val BLOCK_KEYWORD = "弹幕服务"

    fun install() {
        try {
            val field = Toast::class.java.getDeclaredField("sService")
            field.isAccessible = true
            val original = field.get(null)
            val iface = Class.forName("android.app.INotificationManager")
            val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, method, args ->
                if (method.name.startsWith("enqueue") && method.name.endsWith("Toast") && shouldBlock(args)) {
                    null // 命中屏蔽文案：不入队
                } else {
                    try {
                        method.invoke(original, *(args ?: emptyArray()))
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
            field.set(null, proxy)
        } catch (_: Throwable) {
            // hook 失败则放弃屏蔽
        }
    }

    private fun shouldBlock(args: Array<Any?>?): Boolean {
        if (args == null) return false
        return args.any { arg ->
            when (arg) {
                is CharSequence -> arg.contains(BLOCK_KEYWORD)
                is Array<*> -> arg.any { it is CharSequence && it.contains(BLOCK_KEYWORD) }
                else -> false
            }
        }
    }
}
