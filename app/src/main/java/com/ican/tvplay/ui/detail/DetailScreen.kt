package com.ican.tvplay.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

/**
 * 详情页（纵向信息流布局，仿主流视频 App）：
 * 顶部大海报 → 立即播放/收藏 → 线路 chips → 选集卡片列表 → 详情信息
 */
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

    var selectedSourceIndex by remember { mutableIntStateOf(0) }
    val watchedIndex = history?.episodeIndex ?: -1

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶部大海报区域
        item { DetailHero(video = current, onBack = onBack) }

        // 操作按钮 + 信息
        item {
            DetailActions(
                video = current,
                isFavorite = isFavorite,
                onToggleFavorite = { viewModel.toggleFavorite(current) },
                onPlay = {
                    val flag = current.playSources.getOrNull(selectedSourceIndex)?.flag.orEmpty()
                    onPlay(current.id, 0, flag)
                },
            )
        }

        // 线路 chips
        if (current.playSources.size > 1) {
            item {
                SourceChips(
                    playSources = current.playSources,
                    selectedIndex = selectedSourceIndex,
                    onSelect = { selectedSourceIndex = it },
                )
            }
        }

        // 选集列表
        item {
            val episodes = current.playSources.getOrNull(selectedSourceIndex)?.episodes.orEmpty()
            val flag = current.playSources.getOrNull(selectedSourceIndex)?.flag.orEmpty()
            EpisodeList(
                video = current,
                episodes = episodes,
                flag = flag,
                watchedIndex = watchedIndex,
                onPlay = { episodeIndex -> onPlay(current.id, episodeIndex, flag) },
            )
        }

        // 视频详情信息区
        item { DetailInfo(video = current) }
    }
}

/** 顶部大海报：封面横幅 + 渐变遮罩 + 标题/标签/简介 + 返回按钮 */
@Composable
private fun DetailHero(video: Video, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
    ) {
        AsyncImage(
            model = video.cover,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        // 底部渐变遮罩融入背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x40000000), MaterialTheme.colorScheme.background),
                    ),
                ),
        )
        // 左上角返回按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp),
        ) {
            CircleIconButton(icon = AppIcons.Back, contentDescription = "返回", onClick = onBack)
        }
        // 海报底部：标题 + 标签
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val infoText = buildString {
                append(video.year)
                if (video.region.isNotBlank()) append(" · ${video.region}")
                if (video.categoryName.isNotBlank()) append(" · ${video.categoryName}")
                if (video.rating.isNotBlank()) append(" · ${video.rating} 分")
            }
            Text(
                text = infoText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE0E0E8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 立即播放 + 收藏按钮行 */
@Composable
private fun DetailActions(
    video: Video,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 立即播放（主题色主按钮）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .tvCardEffect(
                    onClick = onPlay,
                    shape = RoundedCornerShape(percent = 50),
                    focusedScale = 1.04f,
                    glow = false,
                )
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(percent = 50))
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = AppIcons.Play,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "立即播放",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        // 收藏按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .tvCardEffect(
                    onClick = onToggleFavorite,
                    shape = RoundedCornerShape(percent = 50),
                    focusedScale = 1.04f,
                    glow = false,
                )
                .background(Color(0x22FFFFFF), RoundedCornerShape(percent = 50))
                .padding(horizontal = 18.dp, vertical = 13.dp),
        ) {
            Icon(
                imageVector = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                contentDescription = null,
                tint = if (isFavorite) Color(0xFFFF5C8A) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (isFavorite) "已收藏" else "收藏",
                style = MaterialTheme.typography.titleSmall,
                color = if (isFavorite) Color(0xFFFF5C8A) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** 线路 chips（横向滚动） */
@Composable
private fun SourceChips(
    playSources: List<PlayLine>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "线路",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(playSources.size) { index ->
                val selected = index == selectedIndex
                val shape = RoundedCornerShape(percent = 50)
                val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .tvCardEffect(
                            onClick = { onSelect(index) },
                            shape = shape,
                            focusedScale = 1.05f,
                            glow = false,
                        )
                        .background(bg, shape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
    }
}

/** 选集列表：纵向卡片，每项左侧小封面 + 右侧标题/信息 */
@Composable
private fun EpisodeList(
    video: Video,
    episodes: List<Episode>,
    flag: String,
    watchedIndex: Int,
    onPlay: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "选集（${episodes.size} 集）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (episodes.isEmpty()) {
            Text(
                text = "暂无选集",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            episodes.forEach { episode ->
                EpisodeCard(
                    video = video,
                    episode = episode,
                    isCurrent = episode.index == watchedIndex,
                    onClick = { onPlay(episode.index) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 单个选集卡片：左小封面 + 右标题/信息 */
@Composable
private fun EpisodeCard(
    video: Video,
    episode: Episode,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    else MaterialTheme.colorScheme.surface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .tvCardEffect(onClick = onClick, shape = shape, focusedScale = 1.02f, glow = false)
            .background(bg, shape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 左侧小封面缩略图（共用视频封面）
        val thumbShape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier
                .size(width = 107.dp, height = 60.dp)
                .clip(thumbShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = video.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 当前集播放中角标
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000), thumbShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Play,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        // 右侧标题/信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${video.title} · ${video.year}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isCurrent) {
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 视频详情信息区：完整简介、标签 */
@Composable
private fun DetailInfo(video: Video) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "详情",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        // 标签
        if (video.tags.isNotEmpty()) {
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
        }
        // 简介
        if (video.description.isNotBlank()) {
            Text(
                text = video.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(20.dp))
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
