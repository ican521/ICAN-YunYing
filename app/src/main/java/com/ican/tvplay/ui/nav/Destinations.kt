package com.ican.tvplay.ui.nav

import androidx.compose.ui.graphics.vector.ImageVector
import com.ican.tvplay.ui.components.AppIcons

/** 路由常量与导航参数 */
object Routes {
    /** 一级页面容器（HorizontalPager：首页/收藏/设置） */
    const val MAIN = "main"
    const val HOME = "home"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val COLOR_PALETTE = "color_palette"

    const val DETAIL_PATTERN = "detail/{videoId}"
    fun detail(videoId: String) = "detail/$videoId"

    const val PLAYER_PATTERN = "player/{videoId}/{episodeIndex}?flag={flag}"
    fun player(videoId: String, episodeIndex: Int, flag: String = "") =
        "player/$videoId/$episodeIndex?flag=${android.net.Uri.encode(flag)}"
}

/** 底部胶囊导航的一个入口 */
data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val topDestinations = listOf(
    TopDestination(Routes.HOME, "首页", AppIcons.Home),
    TopDestination(Routes.FAVORITES, "收藏", AppIcons.Favorite),
    TopDestination(Routes.SETTINGS, "设置", AppIcons.Settings),
)

val topLevelRoutes = topDestinations.map { it.route }.toSet()
