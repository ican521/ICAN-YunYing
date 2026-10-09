package com.ican.tvplay.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.data.model.VideoCategory
import com.ican.tvplay.data.remote.TvBoxSite
import com.ican.tvplay.ui.HomeViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.siteShortName
import com.ican.tvplay.ui.components.topLevelContentPadding
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.Spacing
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    val selectedIndex = categories.indexOfFirst { it.id == selectedCategory }.coerceAtLeast(0)

    // 点击 chip → pager 平移翻页（非瞬跳）；点击瞬间记录目标 index，颜色/指示器立即响应
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

        // 固定分类 chips（不透明背景）：通栏无左右padding，内容可滑到屏幕最边缘；
        // 水平边距改为 LazyRow 的 contentPadding 传入，保证静止时最左Tab仍对齐页面基准线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            CategoryChips(
                categories = categories,
                selectedIndex = selectedIndex,
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
            val pageCategory = categories.getOrNull(page)
            val pageVideos = remember(page, pageCategory?.id) {
                mutableStateOf<List<Video>>(emptyList())
            }
            // 首次进入该页或分类变化时，触发加载（协程结果缓存到 pageVideos）
            LaunchedEffect(pageCategory?.id) {
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
                val columns = GridCells.Adaptive(minSize = 132.dp)
                LazyVerticalGrid(
                    state = gridState,
                    columns = columns,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = pageContentPadding,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.cardGapH),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGapV),
                ) {
                    pageVideos.value.firstOrNull()?.let { featured ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            FeaturedBanner(
                                video = featured,
                                onClick = { onVideoClick(featured) },
                            )
                        }
                    }
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

/** 站源选择对话框：列出配置里全部可用站点（spider + HTTP），高亮当前站 */
@Composable
fun SitePickerDialog(
    sites: List<TvBoxSite>,
    currentKey: String,
    onSelect: (TvBoxSite) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(Spacing.corner))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Text(
                text = "选择站源",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(14.dp))
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
                            if (isCurrent) {
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
        // 胶囊搜索框：点击进入搜索页（站源入口已迁移至设置页，搜索框占满剩余空间）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .tvCardEffect(onClick = onSearchClick, shape = CircleShape, focusedScale = 1.04f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 18.dp),
        ) {
            Icon(
                imageVector = AppIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "搜索影片、剧集",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    selectedIndex: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    tappedIndex: Int?,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current

    // 文字高亮目标：点击瞬间切换，无动画；手势滑动时跟随 pager 最近页
    val colorTargetIndex = tappedIndex
        ?: (pagerState.currentPage + pagerState.currentPageOffsetFraction.toInt())
            .coerceIn(0, (categories.size - 1).coerceAtLeast(0))

    // 目标Tab滚出屏幕边缘时，整栏最小距离滚动让其刚好完整露出；
    // 目标Tab已完整可见时保持不动（点击与手势翻页均生效）
    val listState = rememberLazyListState()
    LaunchedEffect(colorTargetIndex) {
        if (categories.isEmpty()) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == colorTargetIndex }
        if (item == null) {
            // 目标Tab完全未上屏（距离较远）：直接滚过去
            listState.animateScrollToItem(colorTargetIndex)
        } else {
            val viewportStart = layoutInfo.viewportStartOffset
            val viewportEnd = layoutInfo.viewportEndOffset
            when {
                // 左边缘被裁切 → 整栏右移，刚好露出
                item.offset < viewportStart ->
                    listState.animateScrollBy((item.offset - viewportStart).toFloat())
                // 右边缘被裁切 → 整栏左移，刚好露出
                item.offset + item.size > viewportEnd ->
                    listState.animateScrollBy((item.offset + item.size - viewportEnd).toFloat())
            }
        }
    }

    // 指示器位置：直接读 LazyRow 实时布局信息（Tab滑出屏幕被回收时拿到 null →
    // 指示器随之隐藏，不再残留卡在最后已知位置）；pager 滚动时在相邻两Tab间插值
    val indicatorPos by remember {
        derivedStateOf {
            val infos = listState.layoutInfo.visibleItemsInfo
            fun posOf(i: Int): Pair<Float, Float>? =
                infos.firstOrNull { it.index == i }?.let { it.offset.toFloat() to it.size.toFloat() }
            if (pagerState.isScrollInProgress) {
                val from = pagerState.currentPage
                val frac = pagerState.currentPageOffsetFraction
                val to = if (frac >= 0f) from + 1 else from - 1
                val p0 = posOf(from)
                val p1 = posOf(to)
                when {
                    p0 != null && p1 != null -> {
                        val f = kotlin.math.abs(frac)
                        (p0.first + (p1.first - p0.first) * f) to (p0.second + (p1.second - p0.second) * f)
                    }
                    else -> p0 ?: p1
                }
            } else {
                posOf(tappedIndex ?: pagerState.currentPage)
            }
        }
    }

    // 整个 Tab 区域总高度固定 30dp：文字行 27dp + 指示器 3dp（紧贴文字下方）
    Column(modifier = Modifier.fillMaxWidth().height(30.dp)) {
        // 文字行（无底色、无 pill，纯文字），高度 27dp，文字垂直居中
        // 遮罩移到屏幕最边缘，宽度=页面水平边距：Tab滑过搜索栏左边线(16dp)时开始渐隐，
        // 静止时最左Tab对齐基准线且完全清晰
        val fadeModifier = Modifier.edgeFade(horizontalPadding)
        LazyRow(
            state = listState,
            // 通栏+contentPadding：内容可滑到屏幕边缘，静止时首尾Tab仍对齐页面基准线
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(27.dp)
                .then(fadeModifier),
        ) {
            items(categories.size, key = { categories[it].id }) { i ->
                CategoryChipPlain(categories[i].name, i, colorTargetIndex == i, onSelect)
            }
        }

        // === 底部横线指示器（直接贴文字下方，与文字行共享同一渐隐遮罩） ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(fadeModifier),
        ) {
            val pos = indicatorPos
            if (pos != null) {
                val barHeight = 3.dp
                val barCorner = RoundedCornerShape(percent = 50)
                val barW = (pos.second * 0.5f).coerceAtLeast(18f)
                val barOffsetX = pos.first + (pos.second - barW) / 2f

                Box(
                    modifier = Modifier
                        .graphicsLayer { translationX = barOffsetX }
                        .width(with(density) { barW.toDp() })
                        .height(barHeight)
                        .clip(barCorner)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            barCorner,
                        ),
                )
            }
        }
    }
}

/**
 * 左右边缘水平渐隐遮罩（两侧常显）：内容滑到边缘 [width] 范围内逐渐变淡。
 */
private fun Modifier.edgeFade(width: androidx.compose.ui.unit.Dp): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadePx = width.toPx()
            val w = size.width
            if (w <= 0f || fadePx <= 0f) return@drawWithContent
            val frac = (fadePx / w).coerceAtMost(0.5f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        frac to Color.Black,
                        1f - frac to Color.Black,
                        1f to Color.Transparent,
                    ),
                ),
                blendMode = BlendMode.DstIn,
            )
        }

@Composable
private fun CategoryChipPlain(
    text: String,
    index: Int,
    selected: Boolean,
    onSelect: (Int) -> Unit,
) {
    val textColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .tvCardEffect(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(percent = 50),
                focusedScale = 1.06f,
                glow = false,
            )
            .padding(horizontal = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = fontWeight,
        )
    }
}

@Composable
private fun FeaturedBanner(
    video: Video,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Spacing.corner)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .tvCardEffect(onClick = onClick, shape = shape)
            // 大图首帧加载前的占位底色，避免切换时透底闪烁
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = video.cover,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xE6070710), Color(0x33070710), Color.Transparent),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(20.dp)
                .fillMaxWidth(0.62f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "今日推荐",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF9FC0FF),
            )
            Text(
                text = video.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD6D6E4),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = AppIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "立即播放",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(start = 6.dp),
                )
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
