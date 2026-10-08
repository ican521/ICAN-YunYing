package com.ican.tvplay.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.ui.SearchViewModel
import com.ican.tvplay.ui.appContainer
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.CircleBackButton
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.components.topLevelContentPadding
import kotlinx.coroutines.flow.map
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun SearchScreen(
    onVideoClick: (Video) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = appViewModel { SearchViewModel(this) }
    val container = appContainer()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val siteName by container.videoRepository.currentSite
        .map { it?.site?.name?.takeIf { n -> n.isNotBlank() } ?: "未配置" }
        .collectAsStateWithLifecycle(initialValue = "未配置")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // === 顶部栏 ===
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            CircleBackButton(onClick = onBack)
            Text(
                text = if (query.isNotBlank()) query else "搜索",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        // === 搜索框（紧凑嵌入）===
        val shape = RoundedCornerShape(percent = 50)
        val interactionSource = remember { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                .border(
                    width = 2.dp,
                    color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = shape,
                )
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = AppIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                interactionSource = interactionSource,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                decorationBox = { inner ->
                    if (query.isEmpty() && !focused) {
                        Text(
                            text = "搜索影片名称…",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .tvCardEffect(
                            onClick = { viewModel.onQueryChange("") },
                            shape = RoundedCornerShape(percent = 50),
                            focusedScale = 1.1f,
                            glow = false,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // === 主体 ===
        if (query.isBlank()) {
            SearchHints(
                history = history,
                hotWords = viewModel.hotWords,
                onWordClick = { viewModel.onQueryChange(it) },
                onClearHistory = viewModel::clearHistory,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "没有找到与「$query」相关的影片",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // === CollectFragment 风格：左源列表 + 右卡片网格 ===
            var selectedFilter by remember { mutableIntStateOf(0) }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
            ) {
                // 左侧筛选栏 (110dp)
                val filters = buildList {
                    add("全部")
                    add(siteName)
                    // 如果视频有分类信息，也可以按 categoryName 筛选
                    results.map { it.categoryName }.distinct().filter { it.isNotBlank() }.take(5).forEach { add(it) }
                }

                LazyColumn(
                    modifier = Modifier
                        .width(110.dp)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 4.dp),
                ) {
                    items(filters) { filter ->
                        val idx = filters.indexOf(filter)
                        FilterChip(
                            text = filter,
                            selected = selectedFilter == idx,
                            onClick = { selectedFilter = idx },
                        )
                    }
                }

                // 右侧卡片网格
                val filteredResults = if (selectedFilter == 0) results
                else results.filter {
                    it.categoryName == filters[selectedFilter] || it.categoryName == filters[selectedFilter]
                }.ifEmpty { results }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 132.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(filteredResults, key = { it.id + it.categoryId }) { video ->
                        SourceCard(
                            video = video,
                            onClick = { onVideoClick(video) },
                        )
                    }
                }
            }
        }
    }
}

// ========== 空查询时的历史/推荐 ==========

@Composable
private fun SearchHints(
    history: List<String>,
    hotWords: List<String>,
    onWordClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (history.isNotEmpty()) {
            SectionHeader(
                title = "历史搜索",
                action = "清空",
                onAction = onClearHistory,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems(history) { word ->
                    SearchChip(text = word, onClick = { onWordClick(word) })
                }
            }
        }

        SectionHeader(title = "热门推荐")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rowItems(hotWords) { word ->
                SearchChip(text = word, onClick = { onWordClick(word) }, primary = true)
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SearchChip(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val bg = if (primary) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (primary) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(percent = 50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .tvCardEffect(onClick = onClick, shape = shape, focusedScale = 1.06f, glow = false)
            .background(bg, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            maxLines = 1,
        )
    }
}

// ========== 左侧筛选 chip ==========

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(percent = 50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .tvCardEffect(onClick = onClick, shape = shape, focusedScale = 1.05f, glow = false)
            .background(bg, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

// ========== 搜索结果卡片（与详情页 SourceCard 风格一致） ==========

@Composable
private fun SourceCard(
    video: Video,
    onClick: () -> Unit,
) {
    val updateState = video.playSources.firstOrNull()?.episodes?.let { eps ->
        if (eps.isEmpty()) ""
        else {
            val lastTitle = eps.last().title
            when {
                lastTitle.contains("完结") -> "已完结"
                eps.size >= 2 -> "更新至第${eps.size}集"
                else -> "共${eps.size}集"
            }
        }
    } ?: run {
        if (video.episodes.isNotEmpty()) {
            if (video.episodes.last().title.contains("完结")) "已完结"
            else "共${video.episodes.size}集"
        } else ""
    }

    val sourceChipColor = hashColor(video.categoryName.ifBlank { "未知" })
    val sourceFlag = video.playSources.firstOrNull()?.flag ?: video.categoryName

    Column(
        modifier = Modifier.tvCardEffect(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            focusedScale = 1.05f,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(186.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            coil3.compose.AsyncImage(
                model = video.cover,
                contentDescription = sourceFlag,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 右上角来源标签
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(sourceChipColor, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = sourceFlag,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                )
            }
            // 底部渐变 + 更新状态 + 视频名
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE6000000)),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (updateState.isNotBlank()) {
                        Text(
                            text = updateState,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun hashColor(text: String): Color {
    val hue = (text.hashCode() % 360 + 360) % 360
    return Color.hsv(hue.toFloat(), 0.55f, 0.75f).copy(alpha = 0.92f)
}
