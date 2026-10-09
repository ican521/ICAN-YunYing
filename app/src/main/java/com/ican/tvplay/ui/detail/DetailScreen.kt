package com.ican.tvplay.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ican.tvplay.data.model.PlayLine
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.ui.DetailViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.Spacing
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * FongMi 式详情页：左侧线路筛选 chips + 右侧卡片网格。
 * - 顶部栏：返回 + 视频名 + 收藏
 * - 左栏：全部 + 各线路 chip
 * - 右栏：卡片网格（每条 PlaySource 一张卡，封面 + 来源标签 + 更新状态 + 视频名）
 * - 点击卡片 → 弹出该线路选集 → 选集播放
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

    val current = video
    if (current == null) {
        DetailLoading(onBack = onBack)
        return
    }

    var selectedLineIndex by remember { mutableIntStateOf(-1) } // -1 = 全部
    var showEpisodePicker by remember { mutableStateOf<PlayLine?>(null) }

    val allSources = current.playSources.ifEmpty {
        listOf(PlayLine(flag = "播放", episodes = current.episodes))
    }
    val displayedSources = if (selectedLineIndex < 0) allSources else listOf(allSources[selectedLineIndex])

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶部栏
        DetailTopBar(
            video = current,
            isFavorite = isFavorite,
            onToggleFavorite = { viewModel.toggleFavorite(current) },
            onBack = onBack,
        )

        // 主体：左 chips + 右卡片
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp),
        ) {
            // 左侧线路筛选 chips（纵向）
            SourceFilterColumn(
                allSources = allSources,
                selectedLineIndex = selectedLineIndex,
                onSelect = { selectedLineIndex = it },
                modifier = Modifier.fillMaxHeight(),
            )

            // 右侧卡片网格
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 132.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = Spacing.pageHorizontal, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.cardGapH),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGapV),
            ) {
                itemsIndexed(displayedSources) { _, source ->
                    SourceCard(
                        video = current,
                        source = source,
                        onClick = { showEpisodePicker = source },
                    )
                }
            }
        }
    }

    // 选集弹出层
    showEpisodePicker?.let { source ->
        EpisodePickerDialog(
            video = current,
            source = source,
            onSelect = { idx ->
                showEpisodePicker = null
                onPlay(current.id, idx, source.flag)
            },
            onDismiss = { showEpisodePicker = null },
        )
    }
}

// ========== 顶部栏 ==========

@Composable
private fun DetailTopBar(
    video: Video,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Spacing.pageHorizontal, end = Spacing.pageHorizontal, top = Spacing.pageTop, bottom = 8.dp),
    ) {
        CircleIconButton(icon = AppIcons.Back, contentDescription = "返回", onClick = onBack)
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        CircleIconButton(
            icon = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
            contentDescription = if (isFavorite) "已收藏" else "收藏",
            onClick = onToggleFavorite,
            tint = if (isFavorite) Color(0xFFFF5C8A) else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ========== 左侧线路筛选栏 ==========

@Composable
private fun SourceFilterColumn(
    allSources: List<PlayLine>,
    selectedLineIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(110.dp)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            text = "全部",
            selected = selectedLineIndex < 0,
            onClick = { onSelect(-1) },
        )
        allSources.forEachIndexed { idx, source ->
            FilterChip(
                text = source.flag,
                selected = selectedLineIndex == idx,
                onClick = { onSelect(idx) },
            )
        }
    }
}

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
            .padding(horizontal = 8.dp)
            .tvCardEffect(onClick = onClick, shape = shape, focusedScale = 1.05f, glow = false)
            .background(bg, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ========== 右侧来源卡片 ==========

@Composable
private fun SourceCard(
    video: Video,
    source: PlayLine,
    onClick: () -> Unit,
) {
    val updateState = source.episodes.let { eps ->
        if (eps.isEmpty()) ""
        else {
            val lastTitle = eps.last().title
            when {
                lastTitle.contains("完结") -> "已完结"
                eps.size >= 2 -> "更新至第${eps.size}集"
                else -> "共${eps.size}集"
            }
        }
    }

    val sourceChipColor = hashColor(source.flag)

    Column(
        modifier = Modifier.tvCardEffect(
            onClick = onClick,
            shape = RoundedCornerShape(Spacing.corner),
            focusedScale = 1.05f,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(186.dp)
                .clip(RoundedCornerShape(Spacing.corner))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = video.cover,
                contentDescription = source.flag,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 右上角：来源标签
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(sourceChipColor, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = source.flag,
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
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 给线路 flag 生成稳定的半透明标签颜色 */
private fun hashColor(text: String): Color {
    val hue = (text.hashCode() % 360 + 360) % 360
    return androidx.compose.ui.graphics.Color.hsv(hue.toFloat(), 0.55f, 0.75f).copy(alpha = 0.92f)
}

// ========== 选集弹出对话框 ==========

@Composable
private fun EpisodePickerDialog(
    video: Video,
    source: PlayLine,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Spacing.corner))
                .padding(20.dp),
        ) {
            Text(
                text = "${source.flag} · 选集（${source.episodes.size} 集）",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.cardGapH),
                modifier = Modifier.padding(top = Spacing.titleContentGap),
            ) {
                items(source.episodes.size) { idx ->
                    val ep = source.episodes[idx]
                    val shape = RoundedCornerShape(Spacing.corner)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .tvCardEffect(
                                onClick = { onSelect(idx) },
                                shape = shape,
                                focusedScale = 1.06f,
                                glow = false,
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = ep.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ========== 通用组件 ==========

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .size(com.ican.tvplay.ui.theme.Spacing.circleButton)
            .tvCardEffect(
                onClick = onClick,
                shape = shape,
                focusedScale = 1.08f,
                glow = false,
            )
            .background(MaterialTheme.colorScheme.surfaceVariant, shape),
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

@Composable
private fun DetailLoading(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 16.dp, top = Spacing.pageTop),
    ) {
        CircleIconButton(
            icon = AppIcons.Back,
            contentDescription = "返回",
            onClick = onBack,
        )
    }
}
