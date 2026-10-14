package com.ican.tvplay.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import com.ican.tvplay.ui.nav.Route
import com.ican.tvplay.ui.nav.rememberNavigator
import com.ican.tvplay.ui.nav.topDestinations
import com.ican.tvplay.player.PlayerScreen
import com.ican.tvplay.player.core.Players
import com.ican.tvplay.ui.search.MultiSourceSearchScreen
import com.ican.tvplay.ui.search.SearchHomeScreen
import com.ican.tvplay.ui.settings.ColorPaletteScreen
import com.ican.tvplay.ui.settings.SettingsScreen
import com.ican.tvplay.ui.theme.DarkSurfaceVariant
import com.ican.tvplay.ui.theme.LightSurface
import com.ican.tvplay.ui.theme.LocalDarkTheme
import com.ican.tvplay.ui.theme.TvPlayTheme
import com.ican.tvplay.ui.theme.popupDimColor
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

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
        // miuix 主题：为 KernelSU 同款组件提供色板。
        // 覆写两项：surfaceContainer（miuix Card/弹窗容器色，深色对齐全局板块色 #242424）、
        // windowDimming（弹窗遮罩，收口到全局 popupDimColor()，值与 miuix 默认一致：浅色30%/深色60%）。
        val isDark = LocalDarkTheme.current
        MiuixTheme(
            colors = if (isDark) {
                darkColorScheme(surfaceContainer = DarkSurfaceVariant, windowDimming = popupDimColor())
            } else {
                lightColorScheme(surfaceContainer = LightSurface, windowDimming = popupDimColor())
            },
        ) {
        // miuix-nav 导航（照搬 KernelSU MainActivity：NavDisplay + 圆角裁剪 + 边缘滑动返回）
        val navigator = rememberNavigator(Route.Main)

        // 毛玻璃开关下发：顶栏/底栏统一从 LocalEnableBlur 读取（KernelSU 同款机制）
        // LocalContentColor 照搬 KernelSU MiuixKernelSUTheme：miuix TextField/Text/Icon 默认内容色
        // 取自该 Local（库默认值为 Black），不提供则深色模式下导入配置弹窗输入文字为黑色
        CompositionLocalProvider(
            LocalEnableBlur provides enableBlur,
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,
        ) {

        // 通知栏点击 → 恢复播放页（后台续播场景）
        LaunchedEffect(Unit) {
            Players.openPlayerRequest.collect { req ->
                if (req != null) {
                    navigator.push(Route.Player(req.videoId, req.episodeIndex, req.flag))
                }
            }
        }

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

        // miuix 根 Scaffold（照搬 KernelSU MainActivity）：内部提供根级弹窗状态列表与默认弹窗宿主，
        // renderInRootScaffold=true 的弹窗渲染在其宿主层，层级高于底栏，遮罩覆盖全屏
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            NavDisplay(
                backStack = navigator.backStack,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
                effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()),
                onBack = { navigator.pop() },
            ) {
                entry<Route.Main>(swipeDismiss = NavSwipeDirection.None) {
                    // 根路由关闭 miuix 边缘拖出：否则边缘手势被根页面消费，返回事件不派发。
                    // 收藏/设置 Tab 下返回手势回到首页 Tab；首页 Tab 下交还系统处理
                    BackHandler(enabled = pagerState.currentPage != 0) {
                        mainPagerState.animateToPage(0)
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = false,
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen(
                                onVideoClick = { video ->
                                    navigator.push(Route.MultiSearch(video.title))
                                },
                                onSearchClick = { navigator.push(Route.Search) },
                                onHistoryClick = { navigator.push(Route.History) },
                            )

                            1 -> FavoritesScreen(
                                onPlay = { videoId, episodeIndex, flag, preInit ->
                                    if (guardPlay()) {
                                        navigator.push(Route.Player(videoId, episodeIndex, flag, preInit))
                                    }
                                },
                                onBack = { mainPagerState.animateToPage(0) },
                            )

                            2 -> SettingsScreen(
                                onColorPaletteClick = { navigator.push(Route.ColorPalette) },
                                onBack = { mainPagerState.animateToPage(0) },
                            )
                        }
                    }
                }
                entry<Route.ColorPalette>(swipeDismiss = NavSwipeDirection.None) {
                    ColorPaletteScreen(onBack = { navigator.pop() })
                }
                entry<Route.Search>(swipeDismiss = NavSwipeDirection.None) {
                    SearchHomeScreen(
                        onBack = { navigator.pop() },
                        onSearch = { query -> navigator.push(Route.MultiSearch(query)) },
                    )
                }
                entry<Route.MultiSearch>(swipeDismiss = NavSwipeDirection.None) { key ->
                    MultiSourceSearchScreen(
                        initialQuery = key.query,
                        onBack = { navigator.pop() },
                        onPlay = { videoId, episodeIndex, flag, preInit ->
                            navigator.push(Route.Player(videoId, episodeIndex, flag, preInit))
                        },
                    )
                }
                entry<Route.History>(swipeDismiss = NavSwipeDirection.None) {
                    HistoryScreen(
                        onPlay = { videoId, episodeIndex, flag, preInit ->
                            if (guardPlay()) {
                                navigator.push(Route.Player(videoId, episodeIndex, flag, preInit))
                            }
                        },
                        onBack = { navigator.pop() },
                    )
                }
                entry<Route.Player>(swipeDismiss = NavSwipeDirection.None) { key ->
                    PlayerScreen(
                        videoId = key.videoId,
                        startEpisode = key.episodeIndex,
                        startFlag = key.flag,
                        preInitialized = key.preInit,
                        onBack = { navigator.pop() },
                    )
                }
            }

            // 底部悬浮液态玻璃导航：仅一级页面（Main）显示
            AnimatedVisibility(
                visible = navigator.current() is Route.Main,
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

        }
        }
        }
        }
    }
}
