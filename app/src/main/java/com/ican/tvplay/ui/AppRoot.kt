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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
            // 应用内跟手侧滑返回（照搬 KernelSU/Miuix 思路：系统预测性返回关闭，
            // 手势完全由 App 接管）：
            // 开关开启且处于二级页时，从屏幕左缘向右拖动实时驱动当前页 translationX；
            // 拖过阈值 → 播完滑出后弹栈；未过阈值 → 弹簧回弹。
            // 系统边缘返回（flag=false）只会触发普通 300ms 转场，不再有系统缩放动画。
            var swipeOffset by remember { mutableFloatStateOf(0f) }
            var swipeJustCompleted by remember { mutableStateOf(false) }
            val canSwipeBack = predictiveBack &&
                currentRoute != null && currentRoute != Routes.MAIN
            val edgeZonePx = with(LocalDensity.current) { 28.dp.toPx() }
            val scope = rememberCoroutineScope()

            LaunchedEffect(currentRoute) {
                // 路由变化后复位手势状态（swipeJustCompleted 已在弹栈那一帧被转场 lambda 读取）
                swipeJustCompleted = false
            }

            NavHost(
                navController = navController,
                startDestination = Routes.MAIN,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .graphicsLayer { translationX = swipeOffset }
                    .pointerInput(canSwipeBack) {
                        if (!canSwipeBack) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                pass = PointerEventPass.Initial,
                                requireUnconsumed = false,
                            )
                            if (down.position.x > edgeZonePx) return@awaitEachGesture

                            var accX = 0f
                            var accY = 0f
                            var claimed = false
                            var released = false
                            var releasedClaimed = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    released = true
                                    releasedClaimed = claimed
                                    break
                                }
                                if (!claimed) {
                                    accX += change.position.x - change.previousPosition.x
                                    accY += change.position.y - change.previousPosition.y
                                    val slop = viewConfiguration.touchSlop
                                    if (kotlin.math.abs(accX) > slop || kotlin.math.abs(accY) > slop) {
                                        if (accX > slop && kotlin.math.abs(accX) > kotlin.math.abs(accY) * 1.5f) {
                                            claimed = true
                                        } else {
                                            // 垂直主导：不消费，交还给子视图正常滚动/点击
                                            break
                                        }
                                    }
                                } else {
                                    val dx = change.position.x - change.previousPosition.x
                                    swipeOffset = (swipeOffset + dx)
                                        .coerceIn(0f, size.width.toFloat())
                                    change.consume()
                                }
                            }
                            if (released && releasedClaimed) {
                                // 抬手结算（动画不能在受限的指针作用域里挂起，放到外层协程）
                                val width = size.width.toFloat()
                                if (swipeOffset > width * 0.35f) {
                                    scope.launch {
                                        animate(
                                            initialValue = swipeOffset,
                                            targetValue = width,
                                            animationSpec = tween(150),
                                        ) { value, _ -> swipeOffset = value }
                                        // 播完剩余滑出再瞬时弹栈（popExit 读 swipeJustCompleted=None，避免二次动画）
                                        swipeJustCompleted = true
                                        navController.popBackStack()
                                        swipeOffset = 0f
                                    }
                                } else {
                                    scope.launch {
                                        animate(
                                            initialValue = swipeOffset,
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            ),
                                        ) { value, _ -> swipeOffset = value }
                                    }
                                }
                            }
                        }
                    },
                // 二级页纯覆盖式平移：新页从右滑入，旧页固定不动；返回同理
                enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } },
                exitTransition = { ExitTransition.None },
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
                            )

                            2 -> SettingsScreen(
                                onColorPaletteClick = { navController.navigate(Routes.COLOR_PALETTE) },
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
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onVideoClick = { videoId ->
                            navController.navigate(Routes.detail(videoId))
                        },
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
