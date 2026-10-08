package com.ican.tvplay.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ican.tvplay.data.model.Episode
import com.ican.tvplay.data.model.PlayLine
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
    onPlay: (videoId: String, episodeIndex: Int, flag: String) -> Unit,
) {
    val viewModel = appViewModel { DetailViewModel(this) }

    LaunchedEffect(videoId) { viewModel.load(videoId) }

    val video by viewModel.video.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    val current = video
    if (current == null) {
        DetailLoading(onBack = onBack)
        return
    }

    // 当前选中线路与集数
    var selectedSourceIndex by remember { mutableIntStateOf(0) }
    val currentSource = current.playSources.getOrNull(selectedSourceIndex)
    val watchedIndex = history?.episodeIndex ?: -1

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // 左侧：线路 tabs + 选集列表（55%）
        LeftEpisodePanel(
            playSources = current.playSources,
            currentSourceIndex = selectedSourceIndex,
            onSourceChange = { selectedSourceIndex = it },
            watchedIndex = watchedIndex,
            onPlay = { episodeIndex, flag -> onPlay(current.id, episodeIndex, flag) },
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.55f),
        )

        // 右侧：海报 + 信息 + 按钮（45%）
        RightInfoPanel(
            video = current,
            isFavorite = isFavorite,
            onBack = onBack,
            onToggleFavorite = { viewModel.toggleFavorite(current) },
            onPlay = { currentSource?.let { onPlay(current.id, 0, it.flag) } },
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.45f),
        )
    }
}

/** 左侧：线路 tabs + 选集网格 */
@Composable
private fun LeftEpisodePanel(
    playSources: List<PlayLine>,
    currentSourceIndex: Int,
    onSourceChange: (Int) -> Unit,
    watchedIndex: Int,
    onPlay: (episodeIndex: Int, flag: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(start = 20.dp, top = 12.dp, end = 14.dp, bottom = 20.dp),
    ) {
        // 顶部返回占位，与右侧顶部对齐
        Spacer(Modifier.height(4.dp))

        if (playSources.isEmpty()) {
            Text(
                text = "暂无选集",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        // 线路 tabs
        if (playSources.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(36.dp),
            ) {
                items(playSources.size) { index ->
                    val selected = index == currentSourceIndex
                    val shape = RoundedCornerShape(percent = 50)
                    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .tvCardEffect(
                                onClick = { onSourceChange(index) },
                                shape = shape,
                                focusedScale = 1.05f,
                                glow = false,
                            )
                            .background(bg, shape)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = playSources[index].flag,
                            style = MaterialTheme.typography.labelLarge,
                            color = fg,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        val episodes = playSources.getOrNull(currentSourceIndex)?.episodes.orEmpty()
        val flag = playSources.getOrNull(currentSourceIndex)?.flag.orEmpty()

        Text(
            text = "选集（${episodes.size} 集）",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // 选集网格
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            episodes.forEach { episode ->
                val isWatched = episode.index == watchedIndex
                val shape = RoundedCornerShape(12.dp)
                val container = if (isWatched) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                val content = if (isWatched) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(72.dp)
                        .tvCardEffect(
                            onClick = { onPlay(episode.index, flag) },
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

/** 右侧：海报卡片 + 信息 + 立即播放 + 收藏 */
@Composable
private fun RightInfoPanel(
    video: Video,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, top = 12.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 返回按钮
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                icon = AppIcons.Back,
                contentDescription = "返回",
                onClick = onBack,
            )
        }

        // 海报卡片
        val posterShape = RoundedCornerShape(16.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .tvCardEffect(onClick = onPlay, shape = posterShape, focusedScale = 1.02f, glow = true)
                .clip(posterShape),
        ) {
            AsyncImage(
                model = video.cover,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
            // 海报渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xB3000000)),
                        ),
                    ),
            )
            // 评分角标
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(Color(0x66000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = AppIcons.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFC53D),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = video.rating,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
            // 标题在海报底部
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
            )
        }

        // 信息行
        Text(
            text = "${video.year} · ${video.region} · ${video.categoryName} · ${video.rating} 分",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 简介
        Text(
            text = video.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 标签
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            video.tags.forEach { tag ->
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

        Spacer(Modifier.height(4.dp))

        // 按钮行
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .tvCardEffect(
                        onClick = onPlay,
                        shape = RoundedCornerShape(percent = 50),
                        focusedScale = 1.05f,
                        glow = false,
                    )
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(percent = 50))
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

/** 占位：视频未加载时的返回顶栏 */
@Composable
private fun DetailLoading(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 16.dp, top = 8.dp),
    ) {
        CircleIconButton(
            icon = AppIcons.Back,
            contentDescription = "返回",
            onClick = onBack,
        )
    }
}
