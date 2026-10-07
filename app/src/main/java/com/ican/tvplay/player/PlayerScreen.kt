package com.ican.tvplay.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.ui.appContainer
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.tvCardEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * 播放页：ExoPlayer 通过 [AndroidView] 嵌入原生 PlayerView。
 * - 进入自动续播上次进度（距结尾不足 5 秒视为看完，从头播放）
 * - 每 5 秒 / 切换选集 / 返回时写入播放历史
 */
@Composable
fun PlayerScreen(
    videoId: String,
    startEpisode: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = appContainer()
    val scope = rememberCoroutineScope()

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    var video by remember { mutableStateOf<Video?>(null) }
    var episodeIndex by remember { mutableIntStateOf(startEpisode) }
    var pendingPosition by remember { mutableLongStateOf(0L) }
    var initialized by remember { mutableStateOf(false) }

    // 先取视频信息与历史进度，再开始播放
    LaunchedEffect(videoId) {
        val v = container.videoRepository.getVideo(videoId)
        val history = container.historyDao.observeOne(videoId).first()
        video = v
        if (v != null && history != null && history.episodeIndex in v.episodes.indices) {
            episodeIndex = history.episodeIndex
            val nearEnd = history.durationMs > 0 &&
                history.durationMs - history.positionMs < RESUME_THRESHOLD_MS
            pendingPosition = if (nearEnd) 0L else history.positionMs
        }
        initialized = true
    }

    // 选集切换 / 首次开播
    LaunchedEffect(initialized, episodeIndex) {
        if (!initialized) return@LaunchedEffect
        val v = video ?: return@LaunchedEffect
        val episode = v.episodes.getOrNull(episodeIndex) ?: return@LaunchedEffect
        val startPosition = pendingPosition
        pendingPosition = 0L
        // spider 站需先经 playerContent 解析真实 URL（可能带 header）
        val source = container.videoRepository.resolvePlaySource(v, episode)
        val mediaItem = MediaItem.Builder()
            .setUri(source.url)
            .apply {
                if (source.headers.isNotEmpty()) {
                    // Media3 不在 MediaItem 上直接支持 header；改在 DataSource 层注入
                }
            }
            .build()
        // 为本次播放重建 DataSource 工厂，注入 header
        val dataSourceFactory = if (source.headers.isEmpty()) {
            null
        } else {
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(source.headers)
                .setAllowCrossProtocolRedirects(true)
        }
        if (dataSourceFactory != null) {
            exoPlayer.setMediaSource(
                DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem),
                startPosition,
            )
        } else {
            exoPlayer.setMediaItem(mediaItem, startPosition)
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun saveProgress() {
        val v = video ?: return
        val episode = v.episodes.getOrNull(episodeIndex) ?: return
        val position = exoPlayer.currentPosition
        val duration = exoPlayer.duration.coerceAtLeast(0L)
        scope.launch {
            container.historyDao.upsert(
                HistoryEntity(
                    videoId = v.id,
                    title = v.title,
                    cover = v.cover,
                    categoryName = v.categoryName,
                    episodeIndex = episodeIndex,
                    episodeTitle = episode.title,
                    positionMs = position,
                    durationMs = duration,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    // 周期性保存进度
    LaunchedEffect(videoId) {
        while (isActive) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            if (initialized) saveProgress()
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    fun handleBack() {
        saveProgress()
        onBack()
    }
    BackHandler(enabled = true) { handleBack() }

    val current = video
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 8.dp, end = 12.dp),
            ) {
                val circle = RoundedCornerShape(percent = 50)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .tvCardEffect(
                            onClick = { handleBack() },
                            shape = circle,
                            focusedScale = 1.08f,
                            glow = false,
                        )
                        .background(Color(0x66000000), circle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = "返回",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = current?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        if (current != null) {
            EpisodePanel(
                video = current,
                currentEpisodeIndex = episodeIndex,
                onSelect = { index ->
                    if (index != episodeIndex) {
                        saveProgress()
                        episodeIndex = index
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodePanel(
    video: Video,
    currentEpisodeIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = video.episodes.getOrNull(currentEpisodeIndex)?.title ?: "",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "${video.year} · ${video.categoryName} · ${video.rating} 分",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "选集（${video.episodes.size} 集）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            video.episodes.forEach { episode ->
                val isCurrent = episode.index == currentEpisodeIndex
                val shape = RoundedCornerShape(12.dp)
                val container = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                val content = if (isCurrent) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 72.dp, height = 44.dp)
                        .tvCardEffect(
                            onClick = { onSelect(episode.index) },
                            shape = shape,
                            focusedScale = 1.08f,
                            glow = false,
                        )
                        .background(container, shape),
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

private const val RESUME_THRESHOLD_MS = 5_000L
private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
