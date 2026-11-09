package com.ican.tvplay.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.data.FavoriteUpdateItem
import com.ican.tvplay.ui.collection.FavoritesScreen
import com.ican.tvplay.ui.collection.HistoryScreen
import com.ican.tvplay.ui.collection.UpdatePopupDialog
import com.ican.tvplay.ui.collection.UpdateScreen
import com.ican.tvplay.ui.components.FloatingBottomBar
import com.ican.tvplay.ui.components.FloatingBottomBarItem
import com.ican.tvplay.ui.components.LocalEnableBlur
import com.ican.tvplay.ui.components.LocalEnableTopBarBlur
import com.ican.tvplay.ui.components.rememberMainPagerState
import com.ican.tvplay.ui.home.HomeScreen
import com.ican.tvplay.ui.nav.Route
import com.ican.tvplay.ui.nav.rememberNavigator
import com.ican.tvplay.ui.nav.topDestinations
import com.ican.tvplay.player.core.Players
import com.ican.tvplay.ui.search.SearchHomeScreen
import com.ican.tvplay.ui.settings.ColorPaletteScreen
import com.ican.tvplay.ui.settings.SettingsScreen
import com.ican.tvplay.ui.theme.LocalDarkTheme
import com.ican.tvplay.ui.theme.Spacing
import com.ican.tvplay.ui.theme.TvPlayTheme
import com.ican.tvplay.ui.theme.appMiuixColors
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    val updatesViewModel = appViewModel { UpdatesViewModel(this) }

    // 启动自动弹出的「今日更新」面板（当天有更新且今天还没弹过时展示一次）
    var updatePopupItems by remember {
        androidx.compose.runtime.mutableStateOf<List<FavoriteUpdateItem>>(emptyList())
    }
    var showUpdatePopup by remember { androidx.compose.runtime.mutableStateOf(false) }
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val themeColor by appViewModel.themeColor.collectAsStateWithLifecycle()
    val enableBlur by appViewModel.enableBlur.collectAsStateWithLifecycle()
    val bottomBarBlur by appViewModel.bottomBarBlurEnabled.collectAsStateWithLifecycle()
    val topBarBlur by appViewModel.topBarBlurEnabled.collectAsStateWithLifecycle()
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

    // 链接解析播放弹窗状态
    var showUrlDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    val urlParseScope = androidx.compose.runtime.rememberCoroutineScope()

    TvPlayTheme(
        themeMode = themeMode,
        themeColor = themeColor,
    ) {
        // miuix 主题：为 KernelSU 同款组件提供色板。
        // 表面色固定（surfaceContainer 深色 #242424 / 浅色 #FFFFFF，windowDimming 固定 PopupScrim）；
        // 强调色系（primary 家族）随主题色板种子色派生，换色即时生效。
        val isDark = LocalDarkTheme.current
        MiuixTheme(
            colors = appMiuixColors(themeColor, isDark),
        ) {
        // miuix-nav 导航（照搬 KernelSU MainActivity：NavDisplay + 圆角裁剪 + 边缘滑动返回）
        val navigator = rememberNavigator(Route.Main)

        // 卡片点击防连点：快速连点只响应第一次（600ms 窗口），避免重复 push 叠页面
        val lastCardNavAt = remember { mutableLongStateOf(0L) }
        fun pushCardRouteThrottled(route: Route) {
            val now = System.currentTimeMillis()
            if (now - lastCardNavAt.longValue < 600L) return
            lastCardNavAt.longValue = now
            navigator.push(route)
        }

        /** 播放页独立 Activity（fongmi 结构）：同样做 600ms 防连点，避免重复启动两个播放页 */
        fun openPlayer(
            videoId: String,
            episodeIndex: Int,
            flag: String = "",
            preInit: Boolean = false,
            forceStart: Boolean = false,
        ) {
            val now = System.currentTimeMillis()
            if (now - lastCardNavAt.longValue < 600L) return
            lastCardNavAt.longValue = now
            com.ican.tvplay.player.PlayerLauncher.start(
                context = context,
                videoId = videoId,
                episodeIndex = episodeIndex,
                flag = flag,
                preInit = preInit,
                forceStart = forceStart,
            )
        }

        // 毛玻璃开关下发：顶栏/底栏统一从 LocalEnableBlur 读取（KernelSU 同款机制）
        // LocalContentColor 照搬 KernelSU MiuixKernelSUTheme：miuix TextField/Text/Icon 默认内容色
        // 取自该 Local（库默认值为 Black），不提供则深色模式下导入配置弹窗输入文字为黑色
        CompositionLocalProvider(
            LocalEnableBlur provides enableBlur,
            LocalEnableTopBarBlur provides topBarBlur,
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,
        ) {

        // 通知栏点击 → 恢复播放页（后台续播场景）：播放页是独立 Activity，直接启动
        LaunchedEffect(Unit) {
            Players.openPlayerRequest.collect { req ->
                if (req != null) {
                    com.ican.tvplay.player.PlayerLauncher.start(
                        context = context,
                        videoId = req.videoId,
                        episodeIndex = req.episodeIndex,
                        flag = req.flag,
                    )
                }
            }
        }

        // 启动追番检测：先按库里已有数据立即判断是否弹面板，追更检测放后台跑，跑完再刷新面板内容
        LaunchedEffect(Unit) {
            val todayItems = updatesViewModel.startupTodayUpdates()
            if (todayItems.isNotEmpty()) {
                updatePopupItems = todayItems
                // 先让弹窗以 show=false 完成首次组合，再显示：
                // 若首次组合就是 show=true，miuix 会跳过进入/退出动画
                withFrameNanos { }
                showUpdatePopup = true
                updatesViewModel.markPopupShown()
            }
            // 后台检测只更新追番更新页的数据，与启动面板无关
        }

        // 液态玻璃底栏的模糊内容源：先铺背景色再记录内容
        val backgroundColor = MiuixTheme.colorScheme.surface
        val backdrop = rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }

        // 一级页面（首页/收藏/设置）放进 HorizontalPager，底栏指示器与页面滑动双向联动
        val pagerState = rememberPagerState(pageCount = { topDestinations.size })
        val mainPagerState = rememberMainPagerState(pagerState)
        val haptic = LocalHapticFeedback.current

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
        // 画中画进出过渡：根底色转黑、关闭导航自绘圆角。
        // 退出小窗时系统先放大窗口、配置晚一帧，播放页仍按小窗尺寸布局，
        // 若不处理会露出浅色根底（底部白条）和导航自绘的小圆角
        val pipTransition by Players.pipTransition.collectAsStateWithLifecycle()
        // 视频铺满全屏/画中画时根底色也保持黑色：全屏下窗口底部最后约 1px 由根底色绘制，
        // 仅靠 pipTransition（进出过渡）覆盖不到稳态全屏，浅色主题会露出底部白色细线
        val videoPresent by Players.videoPresent.collectAsStateWithLifecycle()
        val rootBackground =
            if (pipTransition || videoPresent) androidx.compose.ui.graphics.Color.Black
            else MiuixTheme.colorScheme.surface
        val navCornerRadius = if (pipTransition) 0.dp else rememberNavSystemCornerRadius()
        Scaffold(
            containerColor = rootBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(rootBackground),
        ) {
            NavDisplay(
                backStack = navigator.backStack,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
                effects = NavDisplayEffects(cornerClipRadius = navCornerRadius),
                onBack = {
                    navigator.pop()
                },
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
                        // 三个一级页面常驻组合：切 Tab 回来时不重建页面，不重播卡片动画、不重新加载
                        beyondViewportPageCount = 2,
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen(
                                onVideoClick = { video ->
                                    pushCardRouteThrottled(Route.Search(video.title))
                                },
                                onSearchClick = { navigator.push(Route.Search()) },
                                onHistoryClick = { navigator.push(Route.History) },
                                onUrlPlayClick = { showUrlDialog = true },
                            )

                            1 -> FavoritesScreen(
                                onPlay = { videoId, episodeIndex, flag, preInit ->
                                    if (guardPlay()) {
                                        openPlayer(videoId, episodeIndex, flag, preInit)
                                    }
                                },
                                onUpdatesClick = { pushCardRouteThrottled(Route.Updates) },
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
                entry<Route.Search>(swipeDismiss = NavSwipeDirection.None) { key ->
                    SearchHomeScreen(
                        initialQuery = key.initialQuery,
                        onBack = { navigator.pop() },
                        onPlay = { videoId, episodeIndex, flag, preInit ->
                            openPlayer(videoId, episodeIndex, flag, preInit)
                        },
                    )
                }
                entry<Route.History>(swipeDismiss = NavSwipeDirection.None) {
                    HistoryScreen(
                        onPlay = { videoId, episodeIndex, flag, preInit ->
                            if (guardPlay()) {
                                openPlayer(videoId, episodeIndex, flag, preInit)
                            }
                        },
                        onBack = { navigator.pop() },
                    )
                }
                entry<Route.Updates>(swipeDismiss = NavSwipeDirection.None) {
                    UpdateScreen(
                        onPlay = { videoId, episodeIndex, flag, preInit ->
                            if (guardPlay()) {
                                openPlayer(
                                    videoId, episodeIndex, flag, preInit,
                                    forceStart = true,
                                )
                            }
                        },
                        onBack = { navigator.pop() },
                        onScheduleClick = { navigator.push(Route.UpdateSchedule) },
                    )
                }
                entry<Route.UpdateSchedule>(swipeDismiss = NavSwipeDirection.None) {
                    com.ican.tvplay.collection.UpdateScheduleScreen(
                        onBack = { navigator.pop() },
                    )
                }
            }

            // 底部悬浮液态玻璃导航：仅一级页面（Main）显示
            AnimatedVisibility(
                visible = navigator.current() is Route.Main,
                // 进场不加 fadeIn：淡入会把底栏合成到带 alpha 的离屏层，超出边界的模糊阴影被裁掉，
                // 导致动画结束后阴影才「弹」出来；只保留上滑
                enter = slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                // 底栏距屏幕底部固定 16dp；系统导航栏更高时取导航栏高度，避免压住手势条/三键按键
                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottomPadding = maxOf(Spacing.bottomBarBottom, navBottom)
                FloatingBottomBar(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { }
                        }
                        .padding(start = 28.dp, end = 28.dp, bottom = bottomPadding),
                    selectedIndex = mainPagerState.selectedPage,
                    onSelected = { index ->
                        // 底栏切换分页时使用系统触感反馈（不自行定制震动，仅在真正切页时触发一次）
                        if (index != mainPagerState.selectedPage) {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        }
                        mainPagerState.animateToPage(index)
                    },
                    backdrop = backdrop,
                    tabsCount = topDestinations.size,
                    isBlurEnabled = enableBlur,
                    isFrostedBlur = bottomBarBlur,
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

            // 链接解析播放弹窗（fongmi PushActivity 同款）：show 驱动 OverlayDialog 进/退动画
            com.ican.tvplay.ui.components.UrlPlayDialog(
                show = showUrlDialog,
                onDismiss = { showUrlDialog = false },
                onConfirm = { text ->
                    showUrlDialog = false
                    // fongmi push 流程：processPushUrl 仅做空壳缓存（毫秒级），
                    // 网盘多集展开由播放页 needAsyncDetail 异步补拉，慢站源也能秒进播放页
                    urlParseScope.launch {
                        val videoId = appViewModel.processPushUrl(text)
                        if (videoId != null) {
                            openPlayer(videoId = videoId, episodeIndex = 0)
                        } else {
                            Toast.makeText(context, "暂不支持此链接格式", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )

            // 启动「今日更新」面板：点击条目直接进播放页并选中第一条未看集
            UpdatePopupDialog(
                show = showUpdatePopup,
                items = updatePopupItems,
                title = updatesViewModel.updatePopupTitle(),
                onDismiss = { showUpdatePopup = false },
                onOpenAll = {
                    showUpdatePopup = false
                    navigator.push(Route.Updates)
                },
                onPlay = { item ->
                    showUpdatePopup = false
                    urlParseScope.launch {
                        val prepared = updatesViewModel.preparePlay(item)
                        if (prepared == null) {
                            Toast.makeText(context, "视频加载失败，站源可能已失效", Toast.LENGTH_SHORT).show()
                        } else if (guardPlay()) {
                            Players.cacheVideo(
                                prepared.video,
                                prepared.siteKey,
                                prepared.site,
                                prepared.jarSpec,
                            )
                            openPlayer(
                                videoId = prepared.video.id,
                                episodeIndex = item.resumeIndex,
                                flag = prepared.flag,
                                forceStart = true,
                            )
                        }
                    }
                },
            )

        }
        }
        }
        }
    }
}
