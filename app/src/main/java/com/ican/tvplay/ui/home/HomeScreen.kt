package com.ican.tvplay.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

        // 内容区 pager：相邻页预渲染（beyondViewportPageCount=1），拖拽时页面已就绪；
        // 每页内容由 page index + 缓存决定（避免滑动过程中两页读同一状态、目标页空白）
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
                // 「全部」：Banner + 各分类 LazyRow 分区
                // 实时数据仅在选中「全部」时取用，否则回退缓存（切走再切回不空白）
                val pageSections = if (selectedCategory == null && sections.isNotEmpty()) {
                    sections
                } else {
                    pageCache[null].orEmpty()
                }
                // rememberSaveable：页面离开组合后滚动位置仍保留
                val listState = rememberSaveable(page, saver = LazyListState.Saver) { LazyListState() }
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
                // 具体分类：按 page index 直接决定该页分类；实时数据未覆盖该页时用缓存兜底
                val pageCategory = categories.getOrNull(page - 1)
                val pageVideos = when {
                    pageCategory == null -> emptyList()
                    selectedCategory == pageCategory.id -> sections.firstOrNull()?.second.orEmpty()
                    else -> pageCache[pageCategory.id]?.firstOrNull()?.second.orEmpty()
                }
                val gridState = rememberSaveable(page, saver = LazyGridState.Saver) { LazyGridState() }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    // 3:4 卡片宽度自适应列数
                    val columns = GridCells.Adaptive(minSize = 132.dp)
                    LazyVerticalGrid(
                        state = gridState,
                        columns = columns,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = pageContentPadding,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        pageVideos.firstOrNull()?.let { featured ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                FeaturedBanner(video = featured, onClick = { onVideoClick(featured) })
                            }
                        }
                        gridItems(pageVideos, key = { it.id }) { video ->
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
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(40.dp),
    ) {
        item {
            CategoryChip(text = "全部", selected = selectedIndex == 0, onClick = { onSelect(0) })
        }
        items(categories.size, key = { categories[it].id }) { index ->
            CategoryChip(
                text = categories[index].name,
                selected = selectedIndex == index + 1,
                onClick = { onSelect(index + 1) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val pill = RoundedCornerShape(percent = 50)
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(220),
        label = "chipContainer",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .tvCardEffect(
                onClick = onClick,
                shape = pill,
                focusedScale = 1.06f,
                glow = false,
            )
            .background(container, pill)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = content)
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
