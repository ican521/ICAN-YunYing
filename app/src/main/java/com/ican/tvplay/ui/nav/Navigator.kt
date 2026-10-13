package com.ican.tvplay.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack

/**
 * 简单的导航辅助类，持有 miuix-nav 返回栈。
 * 支持基于共享 [NavBackStack] 的 push/replace/pop/popUntil。
 * （照搬 KernelSU navigation3/Navigator.kt）
 */
@Suppress("unused")
class Navigator(
    val backStack: NavBackStack,
) {

    private val resultBus = mutableMapOf<String, MutableSharedFlow<Any>>()

    /**
     * 幂等地把 key 压入返回栈：双击重复压入相同路由值会产生重复 contentKey
     *（会被运行时拒绝），因此跳过已在栈中的 key。
     * 需要同时存活多个实例的路由应携带唯一值。
     */
    fun push(key: NavKey) {
        if (key !in backStack) {
            backStack.add(key)
        }
    }

    /**
     * 替换栈顶 key；栈为空时直接压入。
     */
    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }

    /**
     * 栈非空时用新的 key 列表整体替换返回栈。
     */
    fun replaceAll(keys: List<NavKey>) {
        if (keys.isEmpty()) {
            return
        }
        if (backStack.isNotEmpty()) {
            backStack.clear()
            backStack.addAll(keys)
        }
    }


    /**
     * 弹出栈顶 key（栈底根路由保留）。
     */
    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    /**
     * 持续弹出，直到栈顶 key 满足条件。
     */
    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    /**
     * 以接收结果的方式导航。调用方需通过 observeResult(requestKey) 订阅。
     */
    fun navigateForResult(route: Route, requestKey: String) {
        ensureChannel(requestKey)
        push(route)
    }

    /**
     * 为指定请求设置结果并弹出当前页。
     */
    fun <T : Any> setResult(requestKey: String, value: T) {
        ensureChannel(requestKey).tryEmit(value)
        pop()
    }

    /**
     * 以 SharedFlow 观察指定请求 key 的结果。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> observeResult(requestKey: String): SharedFlow<T> {
        return ensureChannel(requestKey) as SharedFlow<T>
    }

    /**
     * 清除该请求 key 最近一次发射的结果。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearResult(requestKey: String) {
        ensureChannel(requestKey).resetReplayCache()
    }

    /**
     * 获取返回栈当前栈顶 NavKey。
     */
    fun current() = backStack.lastOrNull()

    /**
     * 获取返回栈当前大小。
     */
    fun backStackSize() = backStack.size

    private fun ensureChannel(key: String): MutableSharedFlow<Any> {
        return resultBus.getOrPut(key) { MutableSharedFlow(replay = 1, extraBufferCapacity = 0) }
    }

}

@Composable
fun rememberNavigator(startRoute: Route): Navigator {
    val backStack = rememberNavBackStack<Route>(startRoute)
    return androidx.compose.runtime.remember(backStack) { Navigator(backStack) }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
