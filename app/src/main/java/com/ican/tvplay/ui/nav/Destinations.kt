package com.ican.tvplay.ui.nav

import androidx.compose.ui.graphics.vector.ImageVector
import com.ican.tvplay.ui.components.AppIcons

/** 底部胶囊导航的一个入口 */
data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val topDestinations = listOf(
    TopDestination("home", "首页", AppIcons.Home),
    TopDestination("favorites", "收藏", AppIcons.Favorite),
    TopDestination("settings", "设置", AppIcons.Settings),
)
