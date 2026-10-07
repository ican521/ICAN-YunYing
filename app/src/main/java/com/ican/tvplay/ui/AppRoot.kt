package com.ican.tvplay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.ui.collection.FavoritesScreen
import com.ican.tvplay.ui.collection.HistoryScreen
import com.ican.tvplay.ui.components.FloatingBottomBar
import com.ican.tvplay.ui.components.FloatingBottomBarItem
import com.ican.tvplay.ui.components.rememberMainPagerState
import com.ican.tvplay.ui.detail.DetailScreen
import com.ican.tvplay.ui.home.HomeScreen
import com.ican.tvplay.ui.nav.Routes
import com.ican.tvplay.ui.nav.topDestinations
import com.ican.tvplay.ui.player.PlayerScreen
import com.ican.tvplay.ui.search.SearchScreen
import com.ican.tvplay.ui.settings.ColorPaletteScreen
import com.ican.tvplay.ui.settings.SettingsScreen
import com.ican.tvplay.ui.theme.TvPlayTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppRoot() {
    val appViewModel = appViewModel { AppViewModel(this) }
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by appViewModel.dynamicColor.collectAsStateWithLifecycle()
    val themeColor by appViewModel.themeColor.collectAsStateWithLifecycle()
    val enableBlur by appViewModel.enableBlur.collectAsStateWithLifecycle()
    val predictiveBack by appViewModel.predictiveBack.collectAsStateWithLifecycle()

    TvPlayTheme(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        themeColor = themeColor,
    ) {
        val navController = rememberNavController()
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
            // 返回手势接管（照搬 KernelSU 的 NavigationBackHandler 方案，系统侧 flag=false）：
            // 二级页返回手势进度实时驱动当前页 translationX 跟手右滑；
            // 完成 → 播完剩余滑出后弹栈；取消 → 弹簧回弹。
            // 开关关闭时手势层禁用，返回走 NavHost 默认 300ms popExit 滑动转场。
            var swipeOffset by remember { mutableFloatStateOf(0f) }
            var swipeJustCompleted by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val backGestureEnabled = predictiveBack &&
                currentRoute != null && currentRoute != Routes.MAIN
            val screenWidthPx = with(LocalDensity.current) {
                LocalConfiguration.current.screenWidthDp.dp.toPx()
            }

            val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
            var isCustomAnimating by remember { mutableStateOf(false) }
            // 手势进度（系统/框架派发）→ 页面跟手位移
            LaunchedEffect(navEventState) {
                snapshotFlow { navEventState.transitionState }
                    .collect { state ->
                        if (isCustomAnimating) return@collect
                        val progress = (state as? NavigationEventTransitionState.InProgress)
                            ?.latestEvent?.progress ?: 0f
                        swipeOffset = progress * screenWidthPx
                    }
            }
            NavigationBackHandler(
                state = navEventState,
                isBackEnabled = backGestureEnabled,
                onBackCancelled = {
                    isCustomAnimating = true
                    val start = swipeOffset
                    scope.launch {
                        animate(
                            initialValue = start,
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        ) { value, _ -> swipeOffset = value }
                        isCustomAnimating = false
                    }
                },
                onBackCompleted = {
                    isCustomAnimating = true
                    val start = swipeOffset
                    scope.launch {
                        animate(
                            initialValue = start,
                            targetValue = screenWidthPx,
                            animationSpec = tween(150),
                        ) { value, _ -> swipeOffset = value }
                        // 播完剩余滑出再瞬时弹栈（popExit 读 swipeJustCompleted=None，避免二次动画）
                        swipeJustCompleted = true
                        navController.popBackStack()
                        swipeOffset = 0f
                        isCustomAnimating = false
                    }
                },
            )

            LaunchedEffect(currentRoute) {
                // 路由变化后复位手势状态（swipeJustCompleted 已在弹栈那一帧被转场 lambda 读取）
                swipeJustCompleted = false
                swipeOffset = 0f
            }

            NavHost(
                navController = navController,
                startDestination = Routes.MAIN,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .graphicsLayer { translationX = swipeOffset },
                // 二级页纯覆盖式平移：新页从右滑入，旧页固定不动；返回同理
                enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } },
                // ExitTransition.None 会让旧页瞬间消失，改用延迟快照淡出实现"保持不动"
                exitTransition = { fadeOut(animationSpec = snap(delayMillis = 300)) },
                popEnterTransition = { EnterTransition.None },
                // 跟手滑完后的弹栈已由手势层驱动过位移动画，此处瞬时消失避免二次播放
                popExitTransition = {
                    if (swipeJustCompleted) ExitTransition.None
                    else slideOutHorizontally(animationSpec = tween(300)) { it }
                },
            ) {
                composable(Routes.MAIN) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen(
                                onVideoClick = { video ->
                                    navController.navigate(Routes.detail(video.id))
                                },
                                onSearchClick = { navController.navigate(Routes.SEARCH) },
                                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                            )

                            1 -> FavoritesScreen(
                                onVideoClick = { videoId ->
                                    navController.navigate(Routes.detail(videoId))
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
                    SearchScreen(
                        onVideoClick = { video ->
                            navController.navigate(Routes.detail(video.id))
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onVideoClick = { videoId ->
                            navController.navigate(Routes.detail(videoId))
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = Routes.DETAIL_PATTERN,
                    arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                ) { entry ->
                    val videoId = entry.arguments?.getString("videoId").orEmpty()
                    DetailScreen(
                        videoId = videoId,
                        onBack = { navController.popBackStack() },
                        onPlay = { id, episodeIndex ->
                            navController.navigate(Routes.player(id, episodeIndex))
                        },
                    )
                }

                composable(
                    route = Routes.PLAYER_PATTERN,
                    arguments = listOf(
                        navArgument("videoId") { type = NavType.StringType },
                        navArgument("episodeIndex") { type = NavType.IntType },
                    ),
                ) { entry ->
                    PlayerScreen(
                        videoId = entry.arguments?.getString("videoId").orEmpty(),
                        startEpisode = entry.arguments?.getInt("episodeIndex") ?: 0,
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
        }
    }
}
