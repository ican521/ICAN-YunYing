package com.ican.tvplay.ui

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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
    val enableSwipeDismiss by appViewModel.enableSwipeDismiss.collectAsStateWithLifecycle()
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
        // miuix-nav 导航（照搬 KernelSU MainActivity：NavDisplay + 圆角裁剪 + 边缘滑动返回）
        val navigator = rememberNavigator(Route.Main)

        // 毛玻璃开关下发：顶栏/底栏统一从 LocalEnableBlur 读取（KernelSU 同款机制）
        CompositionLocalProvider(LocalEnableBlur provides enableBlur) {

        // 通知栏点击 → 恢复播放页（后台续播场景）
        LaunchedEffect(Unit) {
            Players.openPlayerRequest.collect { req ->
                if (req != null) {
                    navigator.push(Route.Player(req.videoId, req.episodeIndex, req.flag))
                }
            }
        }

        // KernelSU 同款：边缘滑动返回方向（RTL 布局反向；开关关闭时不响应边缘滑动）
        val swipeDismiss = if (enableSwipeDismiss) {
            if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
                NavSwipeDirection.RightToLeft
            } else {
                NavSwipeDirection.LeftToRight
            }
        } else {
            NavSwipeDirection.None
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
                entry<Route.Main>(swipeDismiss = swipeDismiss) {
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
                entry<Route.ColorPalette>(swipeDismiss = swipeDismiss) {
                    ColorPaletteScreen(onBack = { navigator.pop() })
                }
                entry<Route.Search>(swipeDismiss = swipeDismiss) {
                    SearchHomeScreen(
                        onBack = { navigator.pop() },
                        onSearch = { query -> navigator.push(Route.MultiSearch(query)) },
                    )
                }
                entry<Route.MultiSearch>(swipeDismiss = swipeDismiss) { key ->
                    MultiSourceSearchScreen(
                        initialQuery = key.query,
                        onBack = { navigator.pop() },
                        onPlay = { videoId, episodeIndex, flag, preInit ->
                            navigator.push(Route.Player(videoId, episodeIndex, flag, preInit))
                        },
                    )
                }
                entry<Route.History>(swipeDismiss = swipeDismiss) {
                    HistoryScreen(
                        onPlay = { videoId, episodeIndex, flag, preInit ->
                            if (guardPlay()) {
                                navigator.push(Route.Player(videoId, episodeIndex, flag, preInit))
                            }
                        },
                        onBack = { navigator.pop() },
                    )
                }
                entry<Route.Player>(swipeDismiss = swipeDismiss) { key ->
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

            // miuix 弹窗宿主：KernelSU 同款——弹窗/遮罩渲染在 App 自身窗口的全屏覆盖层
            //（MiuixPopupHost，非独立 Dialog 窗口），坐标空间与页面一致
            MiuixPopupUtils.MiuixPopupHost()
        }
        }
        }
    }
}
