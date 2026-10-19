package com.ican.tvplay.ui.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.data.model.VideoCategory
import com.ican.tvplay.data.remote.TvBoxSite
import com.ican.tvplay.ui.HomeViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.BlurredBar
import com.ican.tvplay.ui.components.dialogBottomMargin
import com.ican.tvplay.ui.components.LocalEnableTopBarBlur
import com.ican.tvplay.ui.components.rememberBlurBackdrop
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.siteShortName
import com.ican.tvplay.ui.components.topLevelContentPadding
import com.ican.tvplay.ui.components.tvCardEffect
import top.yukonga.miuix.kmp.blur.layerBackdrop
import com.ican.tvplay.ui.theme.Spacing
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 顶部分类 Tab 行高度（与 [UnderlineTabRow] 默认高度一致，用于确定性计算顶区展开总高） */
private val TabRowHeight = 42.dp

/** 搜索行松手吸附缓动：CubicBezier(0.22, 0.95, 0.25, 1) —— 起步干脆、尾巴更长 */
private val SnapEasing = CubicBezierEasing(0.22f, 0.95f, 0.25f, 1.0f)

/**
 * 把网格重置到「折叠态顶部」：滚动 0 + 折叠量偏移，即 glued 不变量要求的
 * 「折叠量 = 网格滚动量」位置。用于离屏页与刚 settle 的目标页。
 */
private suspend fun resetGridToCollapseTop(gs: LazyGridState, collapsePx: Float) {
    gs.scrollToItem(0)
    if (collapsePx > 0f) gs.scrollBy(collapsePx)
}

/**
 * 只修正「停在顶部区」（firstVisibleItemIndex == 0）的网格：把滚动量对齐到折叠量。
 * 已对齐时直接返回，不产生任何滚动与布局开销（用于切页过程中逐帧同步离屏邻页）。
 */
private suspend fun syncGridToCollapse(gs: LazyGridState, collapsePx: Float) {
    if (gs.firstVisibleItemIndex != 0) return
    val cur = gs.firstVisibleItemScrollOffset.toFloat()
    if (cur == collapsePx) return
    gs.scrollBy(collapsePx - cur)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
    onSearchClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onUrlPlayClick: () -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(this) }
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val contentPadding = topLevelContentPadding()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val homeLayoutSize by viewModel.homeLayoutSize.collectAsStateWithLifecycle()
    // 当前站源 key：切站后即使新旧站分类 id 完全相同，也要强制各页重新取数（修复切站后首页内容不刷新）
    val currentSiteKey by viewModel.currentSiteKey.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 「全部」页已删除：分类就绪后默认选中第一个分类
    LaunchedEffect(categories) {
        if (selectedCategory == null && categories.isNotEmpty()) {
            viewModel.selectCategory(categories.first().id)
        }
    }

    // 顶部分类 pager：每个分类一页（第 0 页 = 第一个分类）
    val pagerState = rememberPagerState(pageCount = { categories.size })

    // pager 滑动停止后 → 同步分类选中态到 ViewModel
    LaunchedEffect(pagerState.settledPage) {
        val index = pagerState.settledPage
        val id = categories.getOrNull(index)?.id
        if (id != selectedCategory) {
            viewModel.selectCategory(id)
        }
    }

    // 点击 chip → pager 平移翻页（非瞬跳）；点击瞬间记录目标 index，高亮立即响应
    var tappedChipIndex by remember { mutableStateOf<Int?>(null) }
    val onChipClick: (Int) -> Unit = { index ->
        tappedChipIndex = index
        scope.launch { pagerState.animateScrollToPage(index) }
    }
    // pager 滑动停止（含点击翻页完成、手势滑动）→ 清除点击态，指示器回归跟随 pager
    LaunchedEffect(pagerState.settledPage) {
        tappedChipIndex = null
    }

    // 顶区毛玻璃（收藏/设置页同款 BlurredBar 机制）：随「顶栏毛玻璃」开关，
    // 内容滚动到顶区（搜索行 + Tab 行）下方时被模糊透出；关闭时顶区为页面底色，与原来一致
    val backdrop = rememberBlurBackdrop(LocalEnableTopBarBlur.current)
    val density = LocalDensity.current

    // 搜索行滚动折叠（严格 glued：折叠量 = 列表滚动量）：
    // 上滑列表滚多少收多少、下滑滚多少展多少，卡片始终贴着搜索行底缘，栏无任何自主运动；
    // 列表已在顶部时下拉过界量也用于展开；只改顶区高度、不碰网格布局，无反馈回路
    // 最大收起量 = HomeTopBar 固定 48dp + 搜索行顶部间距（不含状态栏：状态栏由固定 Spacer 占位
    // 不折叠，否则收起后 Tab 行顶到屏幕最顶端与状态栏重叠），直接计算（不做实测回写，杜绝测量振荡）
    val searchRowMaxCollapsePx = with(density) {
        (48.dp + Spacing.pageTop).toPx()
    }
    // HomeTopBar 内容高（48dp）：渐隐锚点基准
    val searchRowContentPx = with(density) { 48.dp.toPx() }
    // 完全隐去的推迟量：让文字/图标在搜索栏可视高度「快要小于内容高」时才彻底隐形
    // （即可视高度 = 内容高 - 该推迟量时 alpha 才降到 0）
    val searchRowFadeEndDelayPx = with(density) { 8.dp.toPx() }
    // 顶区「展开状态」总高度 → 每页网格固定顶部 contentPadding。
    // 确定性计算，不做实测回写：实测值会受「顶栏毛玻璃」开关引起的重排影响出现瞬时偏差，
    // 从而抖动网格 padding 并导致搜索行折叠短时卡住 / 内容错位
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeightPx = with(density) {
        statusBarTopDp.toPx() + searchRowMaxCollapsePx +
            (Spacing.categoryTabVertical * 2 + TabRowHeight).toPx()
    }
    var searchRowCollapsePx by remember { mutableFloatStateOf(0f) }
    // 各页网格状态表：切页时用于把目标页滚动位置对齐折叠量
    val gridStates = remember { mutableStateMapOf<Int, LazyGridState>() }
    val collapseConnection = remember {
        object : NestedScrollConnection {
            /** 切页对齐/吸附动画滚动期间抑制折叠回调，防止程序性滚动回写折叠量 */
            var suppress: Boolean = false
            /** 最后一次手势拖动方向（松手吸附判定用）：>0 上滑收起、<0 下拉展开 */
            var lastDy: Float = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (suppress) return Offset.Zero
                val max = searchRowMaxCollapsePx
                if (max <= 0f) return Offset.Zero
                // miuix pager 跨轴手势层转发过来的滚动增量方向与标准相反，统一取反：
                // 取反后 dy>0 = 上滑收起、dy<0 = 下拉/顶部过界展开
                val dy = -(if (consumed.y != 0f) consumed.y else available.y)
                if (dy != 0f) {
                    searchRowCollapsePx = (searchRowCollapsePx + dy).coerceIn(0f, max)
                    lastDy = dy
                }
                return Offset.Zero
            }
        }
    }

    // 「网格顶部滚动量 = 折叠量」不变量：所有已组合的页（含 beyondViewport 预渲染的邻页）
    // 只要停在顶部区（firstVisibleItemIndex == 0）就必须严格等于当前折叠量，且双向维持。
    // 该不变量由三个钩子维持：
    //   ① 非当前页在「组合 / 数据装载完成 / settle 变化」后立即对齐 → 预渲染邻页滑入前已就位；
    //   ② 横向拖动 / 翻页期间逐帧把离屏页对齐到当前折叠量 → 覆盖「先纵向滚动改折叠量、紧接着横向切页」；
    //   ③ 页面 settle 后重置到对齐位置 → 目标页回到顶部，离屏页重置不会产生可见跳动。
    // 不再使用「currentPage 变化时立刻把离屏页 scrollToItem(0)」：
    // 那会把预渲染邻页拉回滚动 0（顶部空出折叠量像素），越过中点后新旧页角色互换，
    // 表现为「右侧页上移、左侧页下移、空白左右横跳」。

    // 松手吸附（AppBarLayout snap 语义）：
    // 最后方向向上 → 吸附完全收起；向下且已展开量 > 阈值 → 吸附完全展开；向下不足阈值 → 吸回收起。
    // collapse 与网格滚动同 spec 同步动画（suppress 防回流），保持「折叠量 = 滚动量」不脱开；
    // 动画期间再拖动立即打断接管
    val snapThresholdPx = with(density) { 48.dp.toPx() }
    var snapJob by remember { mutableStateOf<Job?>(null) }

    // 钩子②：横向拖动 / 翻页（含点击 chip 的翻页动画）期间，持续把离屏页对齐到当前折叠量。
    // 关键覆盖：用户先纵向滚动改变折叠量、松手吸附动画尚未结束时又横向切页——
    // 邻页必须跟着吸附动画逐帧同步，否则滑入瞬间顶部会空出折叠量像素。
    // 对齐函数在「已对齐」时直接返回，故静止/已对齐状态下不产生任何额外滚动与布局开销
    LaunchedEffect(Unit) {
        var wasMoving = false
        snapshotFlow { pagerState.isScrollInProgress || pagerState.currentPageOffsetFraction != 0f }
            .collect { moving ->
                if (moving == wasMoving) return@collect
                wasMoving = moving
                if (!moving) return@collect
                while (pagerState.isScrollInProgress || pagerState.currentPageOffsetFraction != 0f) {
                    val settled = pagerState.settledPage
                    val collapse = searchRowCollapsePx
                    collapseConnection.suppress = true
                    try {
                        for ((p, gs) in gridStates) {
                            if (p == settled) continue
                            syncGridToCollapse(gs, collapse)
                        }
                    } finally {
                        collapseConnection.suppress = false
                    }
                    withFrameNanos { }
                }
            }
    }

    LaunchedEffect(Unit) {
        var wasScrolling = false
        snapshotFlow { gridStates[pagerState.settledPage]?.isScrollInProgress == true }
            .collect { scrolling ->
                if (scrolling) {
                    wasScrolling = true
                    snapJob?.cancel()
                } else if (wasScrolling) {
                    wasScrolling = false
                    val max = searchRowMaxCollapsePx
                    val cur = searchRowCollapsePx
                    val target = when {
                        cur <= 0f || cur >= max -> cur
                        collapseConnection.lastDy > 0f -> max
                        collapseConnection.lastDy < 0f && cur <= max - snapThresholdPx -> 0f
                        collapseConnection.lastDy < 0f -> max
                        else -> if (cur > max / 2f) max else 0f
                    }
                    val gs = gridStates[pagerState.settledPage]
                    snapJob = scope.launch {
                        collapseConnection.suppress = true
                        try {
                            if (target != cur) {
                                // 逐帧低级循环：collapse 赋值与网格 scrollBy 同帧同步，严格保持 glued；
                                // 本版本无 LazyGridState.animateScrollBy，故不用双动画并行方案
                                // 缓动曲线改用 CubicBezier(0.22, 0.95, 0.25, 1)：起步更干脆、尾巴更长
                                val anim = TargetBasedAnimation(
                                    tween(240, easing = SnapEasing), Float.VectorConverter, cur, target, 0f,
                                )
                                val durationNanos = 240L * 1_000_000
                                val startNanos = withFrameNanos { it }
                                var prev = cur
                                var playTime: Long
                                do {
                                    playTime = withFrameNanos { it } - startNanos
                                    val v = anim.getValueFromNanos(playTime)
                                    val d = v - prev
                                    prev = v
                                    searchRowCollapsePx = v
                                    if (gs != null && d != 0f) gs.scrollBy(d)
                                } while (playTime < durationNanos)
                            }
                        } finally {
                            collapseConnection.suppress = false
                        }
                    }
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 禁用分类 Tab 栏 + 内容 Pager 的边界 overscroll 回弹效果
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {

        // 内容层：录制给顶区毛玻璃（模糊关闭时原样渲染）；挂搜索行折叠的滚动连接
        Box(
            modifier = (if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .nestedScroll(collapseConnection),
        ) {

        // 内容区 pager：相邻页预渲染（beyondViewportPageCount=1）；
        // 每页数据完全独立于 selectedCategory（按 page index 从 categories 直接取对应分类 + 独立缓存），
        // 不再等 settle 同步后才更新 — 避免「目标页先显示旧内容再刷新」
        // 手势照搬 KernelSU：miuix 跨轴拦截（CrossAxis）— pager 自带手势关闭，
        // 由 miuix 识别器按滑动角度仲裁横/纵向；列表纵向 fling 中横向滑动可立即打断翻页
        val pagerFling = PagerDefaults.flingBehavior(pagerState, snapAnimationSpec = PagerNavigationSpringSpec)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .pagerGestureOverride(pagerState, PagerInterceptionMode.CrossAxisInterceptor),
            beyondViewportPageCount = 1,
            userScrollEnabled = false,
            flingBehavior = pagerFling,
            pageNestedScrollConnection = PagerGestureNestedScrollConnection,
        ) { page ->
            val pageContentPadding = PaddingValues(
                start = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
                end = contentPadding.calculateRightPadding(LayoutDirection.Ltr),
                // 顶部 = 展开态顶区高度（固定值，不随折叠变化）：内容可滚动到顶区下方，被毛玻璃透出
                top = with(density) { topBarHeightPx.toDp() },
                bottom = contentPadding.calculateBottomPadding(),
            )

            // 每页对应一个分类：按 page 直接取 categories，独立加载该分类数据
            // key 含 currentSiteKey：切站后分类 id 相同时也强制重载新站内容
            val pageCategory = categories.getOrNull(page)
            val pageVideos = remember(page, pageCategory?.id, currentSiteKey) {
                mutableStateOf<List<Video>>(emptyList())
            }
            // 首次进入该页、分类变化或切换站源时，触发加载（协程结果缓存到 pageVideos）
            LaunchedEffect(pageCategory?.id, currentSiteKey) {
                if (pageCategory != null) {
                    pageVideos.value = viewModel.getCategoryVideos(pageCategory.id)
                }
            }
            val gridState = remember(page) { LazyGridState() }
            // 注册到页表：切页/同步时按页取用；页面销毁时一并移除，避免残留 stale state 被同步逻辑空转
            DisposableEffect(page) {
                gridStates[page] = gridState
                onDispose { gridStates.remove(page) }
            }
            // 钩子①/③：非当前页在「组合 / 数据装载完成 / settle 变化」后立即把网格对齐到折叠量
            // （此时该页已完全离屏，重置到顶部不会产生可见跳动）。
            // 预渲染邻页因此在其滑入前就处于「顶部 = 折叠量」位置 → 滑入瞬间无空白、无上下跳动
            LaunchedEffect(pageVideos.value, pagerState.settledPage) {
                if (page == pagerState.settledPage || pageVideos.value.isEmpty()) return@LaunchedEffect
                collapseConnection.suppress = true
                try {
                    resetGridToCollapseTop(gridState, searchRowCollapsePx)
                } finally {
                    collapseConnection.suppress = false
                }
            }
            // 钩子③（当前页）：settle 到本页后重置到「顶部」——折叠态顶部 = 对齐折叠量处
            // （Tab 行直接连内容），展开态回到真正的滚动 0；程序性滚动用 suppress 抑制，防止回写折叠量
            LaunchedEffect(pagerState.settledPage) {
                if (pagerState.settledPage != page) return@LaunchedEffect
                collapseConnection.suppress = true
                try {
                    resetGridToCollapseTop(gridState, searchRowCollapsePx)
                } finally {
                    collapseConnection.suppress = false
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                // 列数由「设置-首页布局大小」驱动（收藏/历史页共用同一规则 videoGridColumns）：
                // 手机（<600dp）小=3列/中=2列（大不开放），平板自适应最小卡宽：小≈7列、中≈5列、大≈3列
                val columns = com.ican.tvplay.ui.components.videoGridColumns(homeLayoutSize)
                LazyVerticalGrid(
                    state = gridState,
                    columns = columns,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = pageContentPadding,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.cardGapH),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGapV),
                ) {
                    gridItems(pageVideos.value, key = { it.id }) { video ->
                        VideoCardItem(
                            video = video,
                            onClick = {
                                // fongmi 同款：action 卡片直接执行 SiteApi.action → Toast 返回 msg
                                if (video.action.isNotBlank()) {
                                    viewModel.executeDriveAction(video.action) { msg ->
                                        if (msg.isNotBlank()) {
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    onVideoClick(video)
                                }
                            },
                        )
                    }
                }
            }
        }
        }

        // 顶区悬浮层：搜索 + 历史 / 分类 Tab（收藏/设置页同款 BlurredBar；
        // 模糊开 → 透出背后滚动内容，模糊关 → 页面底色，与改造前一致）
        BlurredBar(backdrop) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (backdrop != null) Color.Transparent
                        else MaterialTheme.colorScheme.background,
                    ),
            ) {
                // 状态栏固定占位：不随折叠收起，收起后 Tab 行停在状态栏下缘而非屏幕最顶端
                Spacer(Modifier.height(contentPadding.calculateTopPadding() - Spacing.pageTop))

                // 搜索行：上滑时高度连续收到 0（约束传入，胶囊/按钮随高度压缩变形 +
                // 内部占位文字与图标同步渐隐），下滑任意位置展开；
                // 高度 = 固定最大收起量 - 折叠量，全程计算值无实测回写
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            with(density) {
                                (searchRowMaxCollapsePx - searchRowCollapsePx).coerceAtLeast(0f).toDp()
                            },
                        )
                        .clipToBounds(),
                ) {
                    // 渐隐锚点 = 内容高 - 推迟量：上滑即开始渐隐，搜索栏可视高度收到
                    // 「快要小于内容高」（比内容高再低 8dp）时才完全隐去，之后保持全隐
                    val contentAlpha = run {
                        val fadeFullHidePx = searchRowContentPx - searchRowFadeEndDelayPx
                        val denom = searchRowMaxCollapsePx - fadeFullHidePx
                        if (denom > 0f) {
                            ((searchRowMaxCollapsePx - searchRowCollapsePx - fadeFullHidePx) / denom)
                                .coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
                                end = contentPadding.calculateRightPadding(LayoutDirection.Ltr),
                                top = Spacing.pageTop,
                            ),
                    ) {
                        HomeTopBar(
                            contentAlpha = contentAlpha,
                            onSearchClick = onSearchClick,
                            onHistoryClick = onHistoryClick,
                            onUrlPlayClick = onUrlPlayClick,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.categoryTabVertical))

                // 固定分类 Tab：通栏可滑至屏幕最左/最右缘，静止时首个 Tab 由内部 contentPadding 对齐页面基准线
                Box(modifier = Modifier.fillMaxWidth()) {
                    CategoryChips(
                        categories = categories,
                        pagerState = pagerState,
                        tappedIndex = tappedChipIndex,
                        onSelect = onChipClick,
                    )
                }

                Spacer(Modifier.height(Spacing.categoryTabVertical))
            }
        }
        }
    }
}

/** 站源选择对话框：列出配置里全部可用站点（spider + HTTP），高亮当前站；
 *  每项右侧带 fongmi 同款两个开关：放大镜=是否参与搜索，循环箭头=播放失败是否自动换源。
 *  开=主题色，关=灰色；配置写死不支持（值为0）的开关锁定为灰色不可点。 */
@Composable
fun SitePickerDialog(
    show: Boolean,
    sites: List<TvBoxSite>,
    currentKey: String,
    onSelect: (TvBoxSite) -> Unit,
    onDismiss: () -> Unit,
    isSearchable: (TvBoxSite) -> Boolean = { true },
    isChangeable: (TvBoxSite) -> Boolean = { true },
    onToggleSearchable: (TvBoxSite) -> Unit = {},
    onToggleChangeable: (TvBoxSite) -> Unit = {},
    toggleVersion: Int = 0,
) {
    OverlayDialog(
        show = show,
        title = "选择站源",
        titleColor = MiuixTheme.colorScheme.onBackground,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
        // 渲染到 AppRoot 根宿主：弹窗+遮罩层级高于底栏
        renderInRootScaffold = true,
        // 三边统一 16dp：底部 = max(16dp, 系统导航栏高度)，跨设备自适应
        outsideMargin = DpSize(16.dp, dialogBottomMargin()),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (sites.isEmpty()) {
                Text(
                    text = "未获取到可用站点",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 380.dp),
                ) {
                    items(sites, key = { it.key }) { site ->
                        val isCurrent = site.key == currentKey
                        val shape = RoundedCornerShape(Spacing.corner)
                        // toggleVersion 仅作重组触发key：开关切换后重读开关状态刷新图标着色
                        val searchOn = remember(site.key, toggleVersion) { isSearchable(site) }
                        val changeOn = remember(site.key, toggleVersion) { isChangeable(site) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvCardEffect(
                                    onClick = { onSelect(site) },
                                    shape = shape,
                                    focusedScale = 1.03f,
                                    glow = false,
                                )
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    shape,
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = siteShortName(site.name, site.key),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (site.isSpider()) "spider" else "HTTP 采集",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // 参与搜索开关（放大镜）；配置 searchable=0 锁定不可点
                            SiteToggleIcon(
                                icon = AppIcons.Search,
                                enabled = searchOn,
                                locked = site.searchable == 0,
                                desc = if (searchOn) "关闭该源搜索" else "开启该源搜索",
                                onClick = { onToggleSearchable(site) },
                            )
                            Spacer(Modifier.width(6.dp))
                            // 自动换源开关（循环箭头）；配置 changeable=0 锁定不可点
                            SiteToggleIcon(
                                icon = AppIcons.Loop,
                                enabled = changeOn,
                                locked = site.changeable == 0,
                                desc = if (changeOn) "关闭自动换源" else "开启自动换源",
                                onClick = { onToggleChangeable(site) },
                            )
                            if (isCurrent) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = "当前站源",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 站源行内小开关图标：开=主题色，关/锁定=灰色；锁定时不响应点击 */
@Composable
private fun SiteToggleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    locked: Boolean,
    desc: String,
    onClick: () -> Unit,
) {
    val tint = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (locked) 0.25f else 0.45f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = !locked, onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HomeTopBar(
    contentAlpha: Float,
    onSearchClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onUrlPlayClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        // 胶囊搜索框：自绘假胶囊（视觉照搬 KernelSU SearchBarFake：左 16dp 起 20dp 放大镜 + 8dp 间距占位字），
        // 点击进入搜索页；胶囊底不渐隐，仅内部占位文字与放大镜按 contentAlpha 渐隐
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .tvCardEffect(onClick = onSearchClick, shape = CircleShape, focusedScale = 1.04f, glow = false)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 16.dp),
        ) {
            Icon(
                imageVector = AppIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "搜索影片、剧集",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 链接解析按钮：输入视频直链直接播放（fongmi PushActivity 同款）
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Spacing.circleButton)
                .tvCardEffect(onClick = onUrlPlayClick, shape = CircleShape, focusedScale = 1.1f, glow = false)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        ) {
            Icon(
                imageVector = AppIcons.Link,
                contentDescription = "链接解析",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.size(20.dp),
            )
        }

        // 历史按钮：圆形底不渐隐，仅图标按 contentAlpha 渐隐
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Spacing.circleButton)
                .tvCardEffect(onClick = onHistoryClick, shape = CircleShape, focusedScale = 1.1f, glow = false)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        ) {
            Icon(
                imageVector = AppIcons.History,
                contentDescription = "历史",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun CategoryChips(
    categories: List<VideoCategory>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    tappedIndex: Int?,
    onSelect: (Int) -> Unit,
) {
    // 高亮目标：点击瞬间切换；手势滑动时跟随 pager 最近页
    val colorTargetIndex = tappedIndex
        ?: (pagerState.currentPage + pagerState.currentPageOffsetFraction.toInt())
            .coerceIn(0, (categories.size - 1).coerceAtLeast(0))

    // 下划线式分段 Tab：等宽布局 + 选中自动居中照搬 miuix TabRow 源码，
    // 指示器改为文字底部主题色小横条（无容器底色、无未选中边框、无选中填充）；
    // 通栏无外边距，左右基准线由内部 LazyRow contentPadding 保证
    UnderlineTabRow(
        tabs = categories.map { it.name },
        selectedTabIndex = colorTargetIndex,
        onTabSelected = onSelect,
    )
}

/** miuix TabRow 的下划线变体：尺寸/等宽计算/自动居中滚动与 miuix 源码一致 */
@Composable
private fun UnderlineTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 76.dp,
    maxWidth: Dp = 98.dp,
    height: Dp = TabRowHeight,
    itemSpacing: Dp = 9.dp,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(height)) {
        val availableWidth = this.maxWidth
        // 照搬 miuix calculateTabWidth：均分可用宽度，钳制在 [minWidth, maxWidth]
        val tabWidth = remember(tabs.size, availableWidth) {
            val totalSpacing = if (tabs.size > 1) itemSpacing * (tabs.size - 1) else 0.dp
            val contentWidth = availableWidth - totalSpacing
            if (tabs.isEmpty() || contentWidth <= 0.dp) {
                minWidth
            } else {
                val ideal = contentWidth / tabs.size
                when {
                    ideal < minWidth -> minWidth
                    ideal > maxWidth ->
                        if (maxWidth * tabs.size.toFloat() + totalSpacing < availableWidth) ideal else maxWidth
                    else -> ideal
                }
            }
        }
        val listState = rememberLazyListState()
        val density = LocalDensity.current
        // 通栏后首个 Tab 靠 contentPadding 对齐页面左基准线；指示器偏移需同步加上该边距
        val edgePadding = pageHorizontalPadding()
        val edgePaddingPx = with(density) { edgePadding.toPx() }
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val spacingPx = with(density) { itemSpacing.toPx() }
        var lastSettledIndex by remember { mutableIntStateOf(-1) }

        // 照搬 miuix：选中 tab 自动滚动居中（首次/回退瞬移，前进动画滚动）
        LaunchedEffect(selectedTabIndex, availableWidth, tabWidth) {
            val centerOffset = (availableWidth - tabWidth) / 2
            val offsetPx = with(density) { -centerOffset.toPx() }.fastRoundToInt()
            if (lastSettledIndex < 0 || lastSettledIndex == selectedTabIndex) {
                listState.scrollToItem(selectedTabIndex, offsetPx)
            } else {
                listState.animateScrollToItem(selectedTabIndex, offsetPx)
            }
            lastSettledIndex = selectedTabIndex
        }

        val scrollOffset by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex * (tabWidthPx + spacingPx) +
                    listState.firstVisibleItemScrollOffset
            }
        }
        val hasScrollRange by remember {
            derivedStateOf { listState.canScrollBackward || listState.canScrollForward }
        }
        // 各 tab 文字的实际渲染宽度（选中加粗后由 onGloballyPositioned 重新记录）
        val textWidths = remember { mutableStateMapOf<Int, Int>() }
        val fallbackIndicatorPx = with(density) { 20.dp.toPx() }
        val indicatorWidthPx = textWidths[selectedTabIndex]?.toFloat() ?: fallbackIndicatorPx

        Box(Modifier.fillMaxSize().clipToBounds()) {
            // 主题色小横条：宽度=选中文字实际宽度（文字左缘→右缘），随选中切换即时变化
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 3.dp)
                    .offset {
                        IntOffset(
                            ((edgePaddingPx + selectedTabIndex * (tabWidthPx + spacingPx)) - scrollOffset +
                                (tabWidthPx - indicatorWidthPx) / 2).fastRoundToInt(),
                            0,
                        )
                    }
                    .width(with(density) { indicatorWidthPx.toDp() })
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = edgePadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                overscrollEffect = null,
                userScrollEnabled = hasScrollRange,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(tabs) { index, tabText ->
                    val selected = selectedTabIndex == index
                    // 文字贴底对齐 + 底部预留 12dp = 指示器高3 + 指示器下距3 + 目标间距6：
                    // 保证「文字底缘→指示器顶缘」与「指示器底缘→下方内容」(3+3=6) 严格相等 6dp，
                    // 不随字体缩放变化（原垂直居中方案约 5dp vs 6dp，两套对齐无关联）
                    Box(
                        contentAlignment = Alignment.BottomCenter,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(tabWidth)
                            // 纯点击无任何按压/长按特效（无水波纹、无蒙层、无缩放）
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTabSelected(index) },
                            )
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = tabText,
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 16.sp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .onGloballyPositioned { coords ->
                                    textWidths[index] = coords.size.width
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCardItem(
    video: Video,
    onClick: () -> Unit,
) {
    com.ican.tvplay.ui.components.VideoCard(
        video = video,
        onClick = onClick,
    )
}
