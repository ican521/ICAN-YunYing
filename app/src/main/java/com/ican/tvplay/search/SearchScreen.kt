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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.CircleBackButton
import com.ican.tvplay.ui.components.VideoCard
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.components.topLevelContentPadding
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun SearchScreen(
    onVideoClick: (Video) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = appViewModel { SearchViewModel(this) }
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(
                top = topLevelContentPadding().calculateTopPadding(),
                start = 16.dp,
                end = 16.dp,
            ),
        ) {
            CircleBackButton(onClick = onBack)
            Text(
                text = "搜索",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        SearchField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )

        if (query.isBlank()) {
            // 空查询：历史搜索 + 推荐热门词
            SearchHints(
                history = history,
                hotWords = viewModel.hotWords,
                onWordClick = { viewModel.onQueryChange(it) },
                onClearHistory = viewModel::clearHistory,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (results.isEmpty()) {
            EmptyHint(text = "没有找到与「$query」相关的影片")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 120.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(results, key = { it.id }) { video ->
                    VideoCard(video = video, onClick = { onVideoClick(video) })
                }
            }
        }
    }
}

// ========== 空查询时的历史/推荐区域 ==========

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
        // 历史搜索
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

        // 热门推荐（横向可滚动）
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

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape,
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = AppIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
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
            decorationBox = { innerTextField ->
                if (value.isEmpty() && !focused) {
                    Text(
                        text = "搜索影片名称…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .tvCardEffect(
                        onClick = { onValueChange("") },
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
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
