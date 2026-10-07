package com.ican.tvplay.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.ui.DetailViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.tvCardEffect
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun DetailScreen(
    videoId: String,
    onBack: () -> Unit,
    onPlay: (videoId: String, episodeIndex: Int) -> Unit,
) {
    val viewModel = appViewModel { DetailViewModel(this) }

    LaunchedEffect(videoId) { viewModel.load(videoId) }

    val video by viewModel.video.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    val current = video
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
    ) {
        item {
            DetailHeader(
                video = current,
                isFavorite = isFavorite,
                onBack = onBack,
                onToggleFavorite = { current?.let(viewModel::toggleFavorite) },
                onPlay = { index -> current?.let { onPlay(it.id, index) } },
            )
        }
        if (current != null) {
            item {
                EpisodeList(
                    video = current,
                    watchedEpisodeIndex = history?.episodeIndex ?: -1,
                    onPlay = { index -> onPlay(current.id, index) },
                )
            }
        }
    }
}

@Composable
private fun DetailHeader(
    video: Video?,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlay: (Int) -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp),
    ) {
        AsyncImage(
            model = video?.cover,
            contentDescription = video?.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(340.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x66000000), Color.Transparent, Color(0xF2070710)),
                    ),
                ),
        )

        CircleIconButton(
            icon = AppIcons.Back,
            contentDescription = "返回",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = topInset + 8.dp),
            onClick = onBack,
        )
        CircleIconButton(
            icon = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
            contentDescription = "收藏",
            tint = if (isFavorite) Color(0xFFFF5C8A) else Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = topInset + 8.dp),
            onClick = onToggleFavorite,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = video?.title ?: "",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            video?.let {
                Text(
                    text = "${it.year} · ${it.region} · ${it.categoryName} · ${it.rating} 分",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD2D4E6),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    it.tags.forEach { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFBDD0FF),
                            modifier = Modifier
                                .background(Color(0x335B8CFF), RoundedCornerShape(percent = 50))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(
                    text = it.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFBCBECD),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .tvCardEffect(
                                onClick = { onPlay(0) },
                                shape = RoundedCornerShape(percent = 50),
                                focusedScale = 1.05f,
                                glow = false,
                            )
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(percent = 50),
                            )
                            .padding(horizontal = 26.dp, vertical = 11.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.Play,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "立即播放",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .tvCardEffect(
                                onClick = onToggleFavorite,
                                shape = RoundedCornerShape(percent = 50),
                                focusedScale = 1.05f,
                                glow = false,
                            )
                            .background(Color(0x33FFFFFF), RoundedCornerShape(percent = 50))
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                    ) {
                        Icon(
                            imageVector = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavorite) Color(0xFFFF5C8A) else Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (isFavorite) "已收藏" else "收藏",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .size(42.dp)
            .tvCardEffect(
                onClick = onClick,
                shape = shape,
                focusedScale = 1.08f,
                glow = false,
            )
            .background(Color(0x66000000), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeList(
    video: Video,
    watchedEpisodeIndex: Int,
    onPlay: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "选集（${video.episodes.size} 集）",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            video.episodes.forEach { episode ->
                val isCurrent = episode.index == watchedEpisodeIndex
                val isWatched = episode.index < watchedEpisodeIndex
                val shape = RoundedCornerShape(12.dp)
                val container = when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    isWatched -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val content = when {
                    isCurrent -> MaterialTheme.colorScheme.onPrimary
                    isWatched -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(72.dp)
                        .tvCardEffect(
                            onClick = { onPlay(episode.index) },
                            shape = shape,
                            focusedScale = 1.08f,
                            glow = false,
                        )
                        .background(container, shape)
                        .padding(vertical = 12.dp),
                ) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = content,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
