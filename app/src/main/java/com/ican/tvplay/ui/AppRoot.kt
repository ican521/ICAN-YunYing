package com.ican.tvplay.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.ui.collection.FavoritesScreen
import com.ican.tvplay.ui.collection.HistoryScreen
import com.ican.tvplay.ui.components.FloatingBottomBar
import com.ican.tvplay.ui.components.FloatingBottomBarItem
import com.ican.tvplay.ui.components.LocalEnableBlur
import com.ican.tvplay.ui.components.rememberMainPagerState
import com.ican.tvplay.ui.home.HomeScreen
import com.ican.tvplay.ui.nav.Routes
import com.ican.tvplay.ui.nav.topDestinations
import com.ican.tvplay.player.PlayerScreen
import com.ican.tvplay.player.core.Players
import com.ican.tvplay.ui.search.MultiSourceSearchScreen
import com.ican.tvplay.ui.search.SearchHomeScreen
import com.ican.tvplay.ui.settings.ColorPaletteScreen
import com.ican.tvplay.ui.settings.SettingsScreen
import com.ican.tvplay.ui.theme.DarkSurfaceElevated
import com.ican.tvplay.ui.theme.LightSurface
import com.ican.tvplay.ui.theme.LocalDarkTheme
import com.ican.tvplay.ui.theme.TvPlayTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppRoot() {
    val appViewModel = appViewModel { AppViewModel(this) }
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val themeColor by appViewModel.themeColor.collectAsStateWithLifecycle()
    val enableBlur by appViewModel.enableBlur.collectAsStateWithLifecycle()
    val configReady by appViewModel.configReady.collectAsStateWithLifecycle()
    val context = LocalContext.current

    /** 未配置接口时拦截播放，统一提示 */
    val guardPlay: () -> Boolean = {
        if (!configReady) {
            Toast.makeText(context, "当前未配置接口", Toast.LENGTH_SHORT).show()
            false
        } else {
            true
        }
    }

    TvPlayTheme(
        themeMode = themeMode,
        themeColor = themeColor,
    ) {
        // miuix 主题：为 KernelSU 同款下拉弹窗提供色板。
        // 仅覆写 surfaceContainer（弹窗容器色，须比板块色亮一档以区分层级），
        // 其余沿用 miuix 默认（含 windowDimming 遮罩：浅色黑30% / 深色黑60%，与 KernelSU 一致未覆盖）。
        val isDark = LocalDarkTheme.current
        MiuixTheme(
            colors = if (isDark) {
                darkColorScheme(surfaceContainer = DarkSurfaceElevated)
            } else {
                lightColorScheme(surfaceContainer = LightSurface)
            },
        ) {
        val navController = rememberNavController()

        // 毛玻璃开关下发：顶栏/底栏统一从 LocalEnableBlur 读取（KernelSU 同款机制）
        CompositionLocalProvider(LocalEnableBlur provides enableBlur) {

        // 通知栏点击 → 恢复播放页（后台续播场景）
        LaunchedEffect(Unit) {
            Players.openPlayerRequest.collect { req ->
                if (req != null) {
                    navController.navigate(Routes.player(req.videoId, req.episodeIndex, req.flag))
                }
            }
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        // 液态玻璃底栏的模糊内容源：先铺背景色再记录内容
        val backgroundColor = MaterialTheme.colorScheme.background
        val backdrop = rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }

        // 一级页面（首页/收藏/设置）放进 HorizontalPager，底栏指示器与页面滑动双向联动
        val pagerState = rememberPagerState(pageCount = { topDestinations.size })
        val mainPagerState = rememberMainPagerState(pagerState)

        // 手势滑动页面后同步底栏指示器
        val settledPage = pagerState.settledPage
        LaunchedEffect(settledPage) {
            mainPagerState.syncPage()
        }
        val currentPage = pagerState.currentPage
        LaunchedEffect(currentPage) {
            mainPagerState.syncPage()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.MAIN,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
                // 二级页纯覆盖式平移：新页从右滑入，旧页固定不动；返回同理
                enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } },
                // ExitTransition.None 会让旧页瞬间消失，改用延迟快照淡出实现"保持不动"
                exitTransition = { fadeOut(animationSpec = snap(delayMillis = 300)) },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } },
            ) {
                composable(Routes.MAIN) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = false,
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen(
                                onVideoClick = { video ->
                                    navController.navigate(Routes.multiSearch(video.title))
                                },
                                onSearchClick = { navController.navigate(Routes.SEARCH) },
                                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                            )

                            1 -> FavoritesScreen(
                                onPlay = { videoId, episodeIndex, flag, preInit ->
                                    if (guardPlay()) {
                                        navController.navigate(Routes.player(videoId, episodeIndex, flag, preInit))
                                    }
                                },
                                onBack = { mainPagerState.animateToPage(0) },
                            )

                            2 -> SettingsScreen(
                                onColorPaletteClick = { navController.navigate(Routes.COLOR_PALETTE) },
                                onBack = { mainPagerState.animateToPage(0) },
                            )
                        }
                    }
                }
                composable(Routes.COLOR_PALETTE) {
                    ColorPaletteScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SEARCH) {
                    SearchHomeScreen(
                        onBack = { navController.popBackStack() },
                        onSearch = { query -> navController.navigate(Routes.multiSearch(query)) },
                    )
                }

                composable(
                    route = Routes.MULTI_SEARCH_PATTERN,
                    arguments = listOf(navArgument("query") { type = NavType.StringType }),
                ) { entry ->
                    val query = entry.arguments?.getString("query").orEmpty()
                    MultiSourceSearchScreen(
                        initialQuery = query,
                        onBack = { navController.popBackStack() },
                        onPlay = { videoId, episodeIndex, flag, preInit ->
                            navController.navigate(Routes.player(videoId, episodeIndex, flag, preInit))
                        },
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onPlay = { videoId, episodeIndex, flag, preInit ->
                            if (guardPlay()) {
                                navController.navigate(Routes.player(videoId, episodeIndex, flag, preInit))
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = Routes.PLAYER_PATTERN,
                    arguments = listOf(
                        navArgument("videoId") { type = NavType.StringType },
                        navArgument("episodeIndex") { type = NavType.IntType },
                        navArgument("flag") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("preInit") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) { entry ->
                    PlayerScreen(
                        videoId = entry.arguments?.getString("videoId").orEmpty(),
                        startEpisode = entry.arguments?.getInt("episodeIndex") ?: 0,
                        startFlag = entry.arguments?.getString("flag").orEmpty(),
                        preInitialized = entry.arguments?.getBoolean("preInit") ?: false,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // 底部悬浮液态玻璃导航：仅一级页面（main）显示
            AnimatedVisibility(
                visible = currentRoute == Routes.MAIN,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val bottomPadding = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding()
                    .let { if (it != 0.dp) 8.dp + it else 28.dp }
                FloatingBottomBar(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { }
                        }
                        .padding(start = 28.dp, end = 28.dp, bottom = bottomPadding),
                    selectedIndex = mainPagerState.selectedPage,
                    onSelected = { mainPagerState.animateToPage(it) },
                    backdrop = backdrop,
                    tabsCount = topDestinations.size,
                    isBlurEnabled = enableBlur,
                ) { activateTab ->
                    topDestinations.forEachIndexed { index, destination ->
                        FloatingBottomBarItem(
                            selected = mainPagerState.selectedPage == index,
                            onClick = { activateTab(index) },
                            modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                        ) {
                            // 颜色不手动指定，由 FloatingBottomBar 的 LocalContentColor 自动着色
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                            )
                            Text(
                                text = destination.label,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                }
            }

            // miuix 弹窗宿主：KernelSU 同款——弹窗/遮罩渲染在 App 自身窗口的全屏覆盖层
            //（MiuixPopupHost，非独立 Dialog 窗口），坐标空间与页面一致
            MiuixPopupUtils.MiuixPopupHost()
        }
        }
        }
    }
}
