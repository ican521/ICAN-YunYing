package com.ican.tvplay.ui.home

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.data.model.VideoCategory
import com.ican.tvplay.data.remote.TvBoxSite
import com.ican.tvplay.ui.HomeViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.MiuixSearchInput
import com.ican.tvplay.ui.components.siteShortName
import com.ican.tvplay.ui.components.topLevelContentPadding
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.Spacing
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
    onSearchClick: () -> Unit,
    onHistoryClick: () -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(this) }
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 固定顶栏：搜索 + 历史（不透明背景，防止内容透出）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    start = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = contentPadding.calculateRightPadding(LayoutDirection.Ltr),
                    top = contentPadding.calculateTopPadding(),
                ),
        ) {
            HomeTopBar(
                onSearchClick = onSearchClick,
                onHistoryClick = onHistoryClick,
            )
        }

        Spacer(Modifier.height(Spacing.categoryTabVertical))

        // 禁用分类 Tab 栏 + 内容 Pager 的边界 overscroll 回弹效果
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {

        // 固定分类 chips（不透明背景）：通栏无左右padding，内容可滑到屏幕最边缘；
        // 水平边距改为 LazyRow 的 contentPadding 传入，保证静止时最左Tab仍对齐页面基准线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            CategoryChips(
                categories = categories,
                pagerState = pagerState,
                tappedIndex = tappedChipIndex,
                horizontalPadding = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
                onSelect = onChipClick,
            )
        }

        Spacer(Modifier.height(Spacing.categoryTabVertical))

        // 内容区 pager：相邻页预渲染（beyondViewportPageCount=1）；
        // 每页数据完全独立于 selectedCategory（按 page index 从 categories 直接取对应分类 + 独立缓存），
        // 不再等 settle 同步后才更新 — 避免「目标页先显示旧内容再刷新」
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            val pageContentPadding = PaddingValues(
                start = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
                end = contentPadding.calculateRightPadding(LayoutDirection.Ltr),
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
            // 离开当前页时立即滚回顶部（currentPage ≠ page）
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != page) gridState.scrollToItem(0)
            }
            // 进入目标页 settle 后再次确保在顶部
            LaunchedEffect(pagerState.settledPage) {
                if (pagerState.settledPage == page) gridState.scrollToItem(0)
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                // 列数由「设置-首页布局大小」驱动：手机（<600dp）小=3列/中=2列（大不开放），
                // 平板沿用自适应最小卡宽：小≈7列、中≈5列、大≈3列
                val columns = if (maxWidth < 600.dp) {
                    if (homeLayoutSize == com.ican.tvplay.data.HomeLayoutSize.SMALL) {
                        GridCells.Fixed(3)
                    } else {
                        GridCells.Fixed(2)
                    }
                } else {
                    GridCells.Adaptive(
                        minSize = when (homeLayoutSize) {
                            com.ican.tvplay.data.HomeLayoutSize.SMALL -> 132.dp
                            com.ican.tvplay.data.HomeLayoutSize.MEDIUM -> 200.dp
                            com.ican.tvplay.data.HomeLayoutSize.LARGE -> 300.dp
                        },
                    )
                }
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
                            onClick = { onVideoClick(video) },
                        )
                    }
                }
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
    WindowDialog(
        show = true,
        title = "选择站源",
        titleColor = MiuixTheme.colorScheme.onBackground,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
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
    onSearchClick: () -> Unit,
    onHistoryClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        // 胶囊搜索框：miuix 原生假胶囊（KernelSU SearchBarFake 同款），点击进入搜索页
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .tvCardEffect(onClick = onSearchClick, shape = CircleShape, focusedScale = 1.04f)
                .clip(CircleShape),
        ) {
            MiuixSearchInput(
                query = "",
                onQueryChange = {},
                onSearch = {},
                label = "搜索影片、剧集",
                enabled = false,
                expanded = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 历史按钮
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Spacing.circleButton)
                .tvCardEffect(onClick = onHistoryClick, shape = CircleShape, focusedScale = 1.1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Icon(
                imageVector = AppIcons.History,
                contentDescription = "历史",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onSelect: (Int) -> Unit,
) {
    // 高亮目标：点击瞬间切换；手势滑动时跟随 pager 最近页
    val colorTargetIndex = tappedIndex
        ?: (pagerState.currentPage + pagerState.currentPageOffsetFraction.toInt())
            .coerceIn(0, (categories.size - 1).coerceAtLeast(0))

    // 下划线式分段 Tab：等宽布局 + 选中自动居中照搬 miuix TabRow 源码，
    // 指示器改为文字底部主题色小横条（无容器底色、无未选中边框、无选中填充）
    UnderlineTabRow(
        tabs = categories.map { it.name },
        selectedTabIndex = colorTargetIndex,
        onTabSelected = onSelect,
        modifier = Modifier.padding(horizontal = horizontalPadding),
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
    height: Dp = 42.dp,
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

        Box(Modifier.fillMaxSize()) {
            // 主题色小横条：宽度=选中文字实际宽度（文字左缘→右缘），随选中切换即时变化
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 3.dp)
                    .offset {
                        IntOffset(
                            ((selectedTabIndex * (tabWidthPx + spacingPx)) - scrollOffset +
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                overscrollEffect = null,
                userScrollEnabled = hasScrollRange,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(tabs) { index, tabText ->
                    val selected = selectedTabIndex == index
                    Box(
                        contentAlignment = Alignment.Center,
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
                            modifier = Modifier.onGloballyPositioned { coords ->
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
