package com.ican.tvplay.ui.home

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.ican.tvplay.ui.components.topLevelContentPadding
import com.ican.tvplay.ui.components.tvCardEffect
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
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

    val contentPadding = topLevelContentPadding()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 顶部分类 pager：第 0 页"全部" + 每个分类一页
    val pagerState = rememberPagerState(pageCount = { categories.size + 1 })

    // pager 滑动停止后 → 同步分类选中态到 ViewModel
    LaunchedEffect(pagerState.settledPage) {
        val index = pagerState.settledPage
        val id = if (index == 0) null else categories.getOrNull(index - 1)?.id
        if (id != selectedCategory) {
            viewModel.selectCategory(id)
        }
    }

    val selectedIndex = if (selectedCategory == null) 0
    else categories.indexOfFirst { it.id == selectedCategory }.let { if (it >= 0) it + 1 else 0 }

    // 每页数据缓存：key=null 为「全部」页，其余为分类 id。
    // 滑动过程中 ViewModel 选中态要等 settle 才同步，若目标页只认实时 sections 会全程空白（黑屏闪现），
    // 因此命中缓存先渲染旧数据，settle 后新数据到达自动覆盖。
    val pageCache = remember { mutableStateMapOf<String?, List<Pair<VideoCategory, List<Video>>>>() }
    LaunchedEffect(sections, selectedCategory) {
        if (sections.isEmpty()) return@LaunchedEffect
        pageCache[selectedCategory] = sections
        if (selectedCategory == null) {
            // 「全部」页包含所有分类数据，顺带填充各分类页缓存
            sections.forEach { (cat, videos) -> pageCache[cat.id] = listOf(cat to videos) }
        }
    }

    // 点击 chip → pager 平滑翻页（非瞬跳）
    val onChipClick: (Int) -> Unit = { index ->
        scope.launch { pagerState.animateScrollToPage(index) }
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

        Spacer(Modifier.height(14.dp))

        // 固定分类 chips（不透明背景）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    start = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = contentPadding.calculateRightPadding(LayoutDirection.Ltr),
                ),
        ) {
            CategoryChips(
                categories = categories,
                selectedIndex = selectedIndex,
                onSelect = onChipClick,
            )
        }

        Spacer(Modifier.height(18.dp))

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

            if (page == 0) {
                // 「全部」页：始终用实时 sections
                val pageSections = sections
                val listState = remember(page) { LazyListState() }
                // 离开当前页时立即滚回顶部（currentPage ≠ page）
                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage != page) listState.scrollToItem(0)
                }
                // 进入目标页 settle 后再次确保在顶部
                LaunchedEffect(pagerState.settledPage) {
                    if (pagerState.settledPage == page) listState.scrollToItem(0)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = pageContentPadding,
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    pageSections.firstOrNull()?.second?.firstOrNull()?.let { featured ->
                        item {
                            FeaturedBanner(video = featured, onClick = { onVideoClick(featured) })
                        }
                    }

                    items(pageSections, key = { it.first.id }) { (category, videos) ->
                        VideoSection(
                            category = category,
                            videos = videos,
                            onVideoClick = onVideoClick,
                        )
                    }
                }
            } else {
                // 具体分类页：按 page-1 直接取 categories，独立加载/缓存该分类数据
                val pageCategory = categories.getOrNull(page - 1)
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        pageVideos.value.firstOrNull()?.let { featured ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                FeaturedBanner(video = featured, onClick = { onVideoClick(featured) })
                            }
                        }
                        gridItems(pageVideos.value, key = { it.id }) { video ->
                            VideoCardItem(video = video, onClick = { onVideoClick(video) })
                        }
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
                .clip(RoundedCornerShape(24.dp))
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
                        val shape = RoundedCornerShape(14.dp)
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
                                    text = site.name.ifBlank { site.key },
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
                .tvCardEffect(onClick = onSearchClick, shape = CircleShape, focusedScale = 1.04f, glow = false)
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
                .size(48.dp)
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
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current

    val chipOffsets = remember { mutableStateMapOf<Int, Float>() }
    val chipWidths = remember { mutableStateMapOf<Int, Float>() }
    var rowLeft by remember { mutableStateOf(0f) }

    val targetX = chipOffsets[selectedIndex] ?: 0f
    val targetW = chipWidths[selectedIndex] ?: 0f

    // 简单平移动画（默认 tween 260ms）
    val indicatorX by animateFloatAsState(targetValue = targetX, label = "chipX")
    val indicatorW by animateFloatAsState(targetValue = targetW, label = "chipW")

    Column(modifier = Modifier.fillMaxWidth()) {
        // 文字行（无底色、无 pill，纯文字）
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { rowLeft = it.boundsInRoot().left },
        ) {
            item {
                CategoryChipPlain("全部", 0, selectedIndex == 0, rowLeft, chipOffsets, chipWidths, onSelect)
            }
            items(categories.size, key = { categories[it].id }) { i ->
                val idx = i + 1
                CategoryChipPlain(categories[i].name, idx, selectedIndex == idx, rowLeft, chipOffsets, chipWidths, onSelect)
            }
        }

        // === 底部横线指示器（直接贴文字下方） ===
        Box(modifier = Modifier.fillMaxWidth()) {
            val barHeight = 3.dp
            val barCorner = RoundedCornerShape(percent = 50)
            val barW = (indicatorW * 0.5f).coerceAtLeast(18f)
            val barOffsetX = indicatorX + (indicatorW - barW) / 2f

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

@Composable
private fun CategoryChipPlain(
    text: String,
    index: Int,
    selected: Boolean,
    rowLeft: Float,
    chipOffsets: MutableMap<Int, Float>,
    chipWidths: MutableMap<Int, Float>,
    onSelect: (Int) -> Unit,
) {
    val textColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal

    Box(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                chipOffsets[index] = coords.boundsInRoot().left - rowLeft
                chipWidths[index] = coords.size.width.toFloat()
            }
            .tvCardEffect(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(percent = 50),
                focusedScale = 1.06f,
                glow = false,
            )
            .padding(horizontal = 6.dp, vertical = 12.dp),
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
    val shape = RoundedCornerShape(24.dp)
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
private fun VideoSection(
    category: VideoCategory,
    videos: List<Video>,
    onVideoClick: (Video) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 2.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(videos, key = { it.id }) { video ->
                VideoCardItem(video = video, onClick = { onVideoClick(video) })
            }
        }
    }
}

@Composable
private fun VideoCardItem(video: Video, onClick: () -> Unit) {
    com.ican.tvplay.ui.components.VideoCard(video = video, onClick = onClick)
}
