package com.ican.tvplay.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ican.tvplay.data.local.FavoriteEntity
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.ui.CollectionViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.CircleBackButton
import com.ican.tvplay.ui.components.topLevelContentPadding
import com.ican.tvplay.ui.components.tvCardEffect
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun FavoritesScreen(
    onVideoClick: (String) -> Unit,
    onBack: () -> Unit = {},
) {
    val viewModel = appViewModel { CollectionViewModel(this) }
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    CollectionGridScaffold(
        title = "我的收藏",
        items = favorites,
        emptyText = "还没有收藏，去首页发现好片吧",
        onBack = onBack,
        idOf = { it.videoId },
        coverOf = { it.cover },
        titleOf = { it.title },
        subtitleOf = { "${it.categoryName} · 收藏" },
        onClick = { onVideoClick(it.videoId) },
        onDelete = { viewModel.removeFavorite(it.videoId) },
    )
}

@Composable
fun HistoryScreen(
    onVideoClick: (String) -> Unit,
    onBack: () -> Unit = {},
) {
    val viewModel = appViewModel { CollectionViewModel(this) }
    val histories by viewModel.histories.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .padding(
                    top = topLevelContentPadding().calculateTopPadding(),
                    start = 16.dp,
                    end = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircleBackButton(onClick = onBack)
            Text(
                text = "播放历史",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (histories.isNotEmpty()) {
                val pill = RoundedCornerShape(percent = 50)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .tvCardEffect(
                            onClick = viewModel::clearHistory,
                            shape = pill,
                            focusedScale = 1.05f,
                            glow = false,
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant, pill)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = AppIcons.Delete,
                        contentDescription = "清空历史",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        if (histories.isEmpty()) {
            EmptyText(text = "还没有播放记录")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 120.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(histories, key = { it.videoId }) { history ->
                    HistoryCard(
                        history = history,
                        onClick = { onVideoClick(history.videoId) },
                    )
                }
            }
        }
    }
}

/** 收藏网格（复用统一骨架） */
@Composable
private fun <T> CollectionGridScaffold(
    title: String,
    items: List<T>,
    emptyText: String,
    onBack: () -> Unit = {},
    idOf: (T) -> String,
    coverOf: (T) -> String,
    titleOf: (T) -> String,
    subtitleOf: (T) -> String,
    onClick: (T) -> Unit,
    onDelete: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(
                top = topLevelContentPadding().calculateTopPadding(),
                start = 16.dp,
                end = 16.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircleBackButton(onClick = onBack)
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (items.isEmpty()) {
            EmptyText(text = emptyText)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 120.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = idOf) { item ->
                    CollectionCard(
                        cover = coverOf(item),
                        title = titleOf(item),
                        subtitle = subtitleOf(item),
                        onClick = { onClick(item) },
                        onDelete = { onDelete(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(
    cover: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.tvCardEffect(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(186.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = cover,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))),
                    ),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .tvCardEffect(
                        onClick = onDelete,
                        shape = RoundedCornerShape(percent = 50),
                        focusedScale = 1.1f,
                        glow = false,
                    )
                    .background(Color(0x88000000), RoundedCornerShape(percent = 50)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = "移除",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun HistoryCard(
    history: HistoryEntity,
    onClick: () -> Unit,
) {
    val progress = if (history.durationMs > 0) {
        (history.positionMs.toFloat() / history.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = Modifier.tvCardEffect(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(186.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = history.cover,
                contentDescription = history.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = history.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = history.episodeTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB9C6F0),
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x55FFFFFF), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
        Text(
            text = history.categoryName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
