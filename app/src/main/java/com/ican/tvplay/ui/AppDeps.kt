package com.ican.tvplay.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ican.tvplay.TvPlayApplication
import com.ican.tvplay.data.AppContainer

/** 从 Application 取出应用级依赖容器 */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as TvPlayApplication).container

/** 由 [AppContainer] 直接构造 ViewModel 的便捷方法 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline creator: AppContainer.() -> VM,
): VM {
    val container = appContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { creator(container) }
        },
    )
}
