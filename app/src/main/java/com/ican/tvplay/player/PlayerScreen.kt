package com.ican.tvplay.ui.player

import android.app.Activity
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.data.model.Episode
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.player.core.PlaySpec
import com.ican.tvplay.player.core.Players
import com.ican.tvplay.player.core.SubItem
import com.ican.tvplay.ui.appContainer
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.tvCardEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 播放页：fongmi 式播放器门面 [Players]（单例）+ Compose 自定义控制层。
 * - 播放器实例由单例持有，横竖屏 Activity 重建不丢播放状态（仅 attach/detach surface）
 * - 双解码内核（硬解/软解 ffmpeg）、倍速 0.5-3.0、画面缩放、缓冲档位、音轨/字幕轨、外挂字幕、线路切换
 * - 记忆进度 / 历史记录沿用 VideoRepository + Room（每 5 秒 & 切集 & 返回时保存）
 */
@Composable
fun PlayerScreen(
    videoId: String,
    startEpisode: Int,
    startFlag: String = "",
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = appContainer()
    val scope = rememberCoroutineScope()

    val playerState by Players.state.collectAsStateWithLifecycle()

    // 设置持久化
    val settings = container.settingsRepository
    val savedSpeed by settings.playerSpeed.collectAsStateWithLifecycle()
    val savedScale by settings.playerScale.collectAsStateWithLifecycle()
    val savedDecode by settings.playerDecode.collectAsStateWithLifecycle()
    val savedBuffer by settings.playerBuffer.collectAsStateWithLifecycle()

    var video by remember { mutableStateOf<Video?>(null) }
    var episodeIndex by remember { mutableIntStateOf(startEpisode) }
    var initialized by remember { mutableStateOf(false) }
    var pendingPosition by remember { mutableLongStateOf(0L) }

    // 线路切换：默认按传入 flag 匹配
    var lineFlag by remember { mutableStateOf(startFlag) }
    val playLine = video?.playSources
        ?.let { sources -> sources.firstOrNull { it.flag == lineFlag } ?: sources.firstOrNull() }
    val lineEpisodes = playLine?.episodes ?: video?.episodes.orEmpty()

    // 应用持久化设置到 Players
    LaunchedEffect(savedSpeed) { Players.setSpeed(savedSpeed) }
    LaunchedEffect(savedScale) {
        Players.setScaleMode(
            Players.ScaleMode.entries.firstOrNull { it.name == savedScale }
                ?: Players.ScaleMode.FIT,
        )
    }
    LaunchedEffect(savedDecode) {
        Players.setDecode(
            Players.Decode.entries.firstOrNull { it.name == savedDecode }
                ?: Players.Decode.HARD,
        )
    }
    LaunchedEffect(savedBuffer) {
        Players.setBufferTier(
            Players.BufferTier.entries.firstOrNull { it.multiplier == savedBuffer }
                ?: Players.BufferTier.LOW,
        )
    }

    // 加载视频信息 + 历史进度，然后开播
    LaunchedEffect(videoId) {
        val v = container.videoRepository.getVideo(videoId) ?: return@LaunchedEffect
        val history = container.historyDao.observeOne(videoId).first()
        video = v
        if (history != null) {
            val eps = v.playSources
                .let { s -> s.firstOrNull { it.flag == lineFlag } ?: s.firstOrNull() }
                ?.episodes ?: v.episodes
            if (history.episodeIndex in eps.indices) {
                episodeIndex = history.episodeIndex
                val nearEnd = history.durationMs > 0 &&
                    history.durationMs - history.positionMs < RESUME_THRESHOLD_MS
                pendingPosition = if (nearEnd) 0L else history.positionMs
            }
        }
        initialized = true
    }

    // 解析并播放当前选集/线路
    LaunchedEffect(initialized, episodeIndex, lineFlag) {
        if (!initialized) return@LaunchedEffect
        val v = video ?: return@LaunchedEffect
        val episode = lineEpisodes.getOrNull(episodeIndex) ?: return@LaunchedEffect
        val pos = pendingPosition
        pendingPosition = 0L
        val source = container.videoRepository.resolvePlaySource(v, episode, playLine?.flag ?: v.playFrom)
        Players.start(
            context = context,
            spec = PlaySpec(url = source.url, headers = source.headers),
            startPositionMs = pos,
        )
    }

    fun saveProgress() {
        val v = video ?: return
        val episode = lineEpisodes.getOrNull(episodeIndex) ?: return
        val st = Players.state.value
        scope.launch {
            container.historyDao.upsert(
                HistoryEntity(
                    videoId = v.id,
                    title = v.title,
                    cover = v.cover,
                    categoryName = v.categoryName,
                    episodeIndex = episodeIndex,
                    episodeTitle = episode.title,
                    positionMs = st.positionMs,
                    durationMs = st.durationMs,
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

    // surface 生命周期：横竖屏重建只 detach/attach，离开页面才 release
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? Activity
            surfaceView?.let { Players.detach(it) }
            if (activity?.isChangingConfigurations != true) {
                saveProgress()
                Players.release()
            }
        }
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
        // 视频区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    AspectRatioFrameLayout(ctx).apply {
                        resizeMode = playerState.scaleMode.resizeMode
                        if (playerState.scaleMode.ratio > 0f) {
                            setAspectRatio(playerState.scaleMode.ratio)
                        }
                        val sv = SurfaceView(ctx)
                        addView(sv, FrameLayout.LayoutParams(-1, -1))
                        val sub = SubtitleView(ctx)
                        addView(sub, FrameLayout.LayoutParams(-1, -1))
                        tag = sv
                        Players.attach(sv, sub)
                        surfaceView = sv
                    }
                },
                update = { view ->
                    view.resizeMode = playerState.scaleMode.resizeMode
                    if (playerState.scaleMode.ratio > 0f) {
                        view.setAspectRatio(playerState.scaleMode.ratio)
                    } else {
                        view.setAspectRatio(0f)
                    }
                },
            )

            // 顶部：返回 + 标题
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
                        .tvCardEffect(onClick = { handleBack() }, shape = circle, focusedScale = 1.08f, glow = false)
                        .background(Color(0x66000000), circle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Back, contentDescription = "返回", tint = Color.White, modifier = Modifier.size(20.dp))
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

            // 底部控制层
            PlayerControlBar(
                state = playerState,
                video = current,
                lineFlag = lineFlag,
                onTogglePlay = { Players.playPause() },
                onSeek = { Players.seekTo(it) },
                onSpeedChange = { settings.setPlayerSpeed(it) },
                onScaleChange = { settings.setPlayerScale(it.name) },
                onDecodeChange = { settings.setPlayerDecode(it.name) },
                onBufferChange = { settings.setPlayerBuffer(it.multiplier) },
                onLineChange = { flag ->
                    if (flag != lineFlag) {
                        saveProgress()
                        lineFlag = flag
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // 选集面板
        if (current != null) {
            EpisodePanel(
                video = current,
                episodes = lineEpisodes,
                lineFlag = lineFlag,
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

/** 底部播放控制栏：进度 + 功能按钮面板 */
@Composable
private fun PlayerControlBar(
    state: Players.PlayerState,
    video: Video?,
    lineFlag: String,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onScaleChange: (Players.ScaleMode) -> Unit,
    onDecodeChange: (Players.Decode) -> Unit,
    onBufferChange: (Players.BufferTier) -> Unit,
    onLineChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSpeed by remember { mutableStateOf(false) }
    var showScale by remember { mutableStateOf(false) }
    var showDecode by remember { mutableStateOf(false) }
    var showBuffer by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showLine by remember { mutableStateOf(false) }
    var showAddSub by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xCC000000)),
                ),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // 进度行
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatMs(state.positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
            Slider(
                value = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
                onValueChange = { onSeek((it * state.durationMs).toLong()) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color(0x44FFFFFF),
                ),
            )
            Text(
                text = formatMs(state.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }

        // 功能按钮行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
        ) {
            ControlChip(
                icon = if (state.playing) AppIcons.Pause else AppIcons.Play,
                label = if (state.playing) "暂停" else "播放",
                onClick = onTogglePlay,
            )
            ControlChip(icon = AppIcons.Speed, label = "倍速 ${formatSpeed(state.speed)}", onClick = { showSpeed = true })
            ControlChip(icon = AppIcons.AspectRatio, label = state.scaleMode.label, onClick = { showScale = true })
            ControlChip(icon = AppIcons.Memory, label = state.decode.label, onClick = { showDecode = true })
            ControlChip(icon = AppIcons.Buffer, label = "缓冲·${state.bufferTier.label}", onClick = { showBuffer = true })
            ControlChip(icon = AppIcons.AudioTrack, label = "音轨", onClick = { showAudio = true })
            ControlChip(icon = AppIcons.Subtitle, label = "字幕", onClick = { showText = true })
            if ((video?.playSources?.size ?: 0) > 1) {
                ControlChip(icon = AppIcons.Site, label = "线路", onClick = { showLine = true })
            }
        }
    }

    // ---- 对话框 ----
    if (showSpeed) {
        OptionDialog(
            title = "倍速",
            options = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f),
            current = state.speed,
            label = { formatSpeed(it) },
            onSelect = { onSpeedChange(it); showSpeed = false },
            onDismiss = { showSpeed = false },
        )
    }
    if (showScale) {
        OptionDialog(
            title = "画面缩放",
            options = Players.ScaleMode.entries,
            current = state.scaleMode,
            label = { it.label },
            onSelect = { onScaleChange(it); showScale = false },
            onDismiss = { showScale = false },
        )
    }
    if (showDecode) {
        OptionDialog(
            title = "解码内核",
            options = Players.Decode.entries,
            current = state.decode,
            label = { it.label },
            onSelect = { onDecodeChange(it); showDecode = false },
            onDismiss = { showDecode = false },
        )
    }
    if (showBuffer) {
        OptionDialog(
            title = "缓冲档位",
            options = Players.BufferTier.entries,
            current = state.bufferTier,
            label = { it.label },
            onSelect = { onBufferChange(it); showBuffer = false },
            onDismiss = { showBuffer = false },
        )
    }
    if (showAudio) {
        OptionDialog(
            title = "音轨",
            options = state.audioTracks,
            current = state.audioTracks.firstOrNull { it.selected },
            label = { it.name },
            onSelect = { Players.selectTrack(it, androidx.media3.common.C.TRACK_TYPE_AUDIO); showAudio = false },
            onDismiss = { showAudio = false },
        )
    }
    if (showText) {
        SubtitleDialog(
            tracks = state.textTracks,
            textDisabled = state.textDisabled,
            onSelect = { Players.selectTrack(it, androidx.media3.common.C.TRACK_TYPE_TEXT); showText = false },
            onDisable = { Players.selectTrack(null, androidx.media3.common.C.TRACK_TYPE_TEXT); showText = false },
            onAddExternal = { showAddSub = true },
            onDismiss = { showText = false },
        )
    }
    if (showAddSub) {
        AddSubtitleDialog(
            onAdd = { name, url -> Players.addSubtitle(SubItem(name = name, url = url)); showAddSub = false },
            onDismiss = { showAddSub = false },
        )
    }
    if (showLine && video != null) {
        OptionDialog(
            title = "切换线路",
            options = video.playSources,
            current = video.playSources.firstOrNull { it.flag == lineFlag },
            label = { it.flag },
            onSelect = { onLineChange(it.flag); showLine = false },
            onDismiss = { showLine = false },
        )
    }
}

@Composable
private fun ControlChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .tvCardEffect(onClick = onClick, shape = shape, focusedScale = 1.06f, glow = false)
            .background(Color(0x55000000), shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

/** 通用选项对话框 */
@Composable
private fun <T> OptionDialog(
    title: String,
    options: List<T>,
    current: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEach { opt ->
                    val selected = opt == current
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvCardEffect(onClick = { onSelect(opt) }, shape = shape, focusedScale = 1.02f, glow = false)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape,
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = label(opt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 字幕对话框：内嵌轨 + 关闭 + 外挂入口 */
@Composable
private fun SubtitleDialog(
    tracks: List<Players.TrackOption>,
    textDisabled: Boolean,
    onSelect: (Players.TrackOption) -> Unit,
    onDisable: () -> Unit,
    onAddExternal: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text(
                text = "字幕",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 关闭字幕
                SubtitleRow(label = "关闭字幕", selected = textDisabled, onClick = onDisable)
                tracks.forEach { track ->
                    SubtitleRow(label = track.name, selected = !textDisabled && track.selected, onClick = { onSelect(track) })
                }
                // 外挂字幕入口
                SubtitleRow(label = "添加外挂字幕…", selected = false, onClick = onAddExternal)
            }
        }
    }
}

@Composable
private fun SubtitleRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .tvCardEffect(onClick = onClick, shape = shape, focusedScale = 1.02f, glow = false)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant,
                shape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

/** 外挂字幕 URL 输入 */
@Composable
private fun AddSubtitleDialog(
    onAdd: (name: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text(
                text = "添加外挂字幕",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { androidx.compose.material3.Text("字幕文件 URL（srt/ass/vtt）") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Text(
                    text = "添加",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(enabled = url.isNotBlank()) {
                            onAdd(url.substringAfterLast('/').ifBlank { "外挂字幕" }, url.trim())
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** 选集面板（沿用原逻辑） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodePanel(
    video: Video,
    episodes: List<Episode>,
    lineFlag: String,
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
            text = episodes.getOrNull(currentEpisodeIndex)?.title ?: "",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "${video.year} · ${video.categoryName} · ${video.rating} 分" +
                if (lineFlag.isNotBlank()) " · 线路：$lineFlag" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "选集（${episodes.size} 集）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            episodes.forEach { episode ->
                val isCurrent = episode.index == currentEpisodeIndex
                val shape = RoundedCornerShape(12.dp)
                val container = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                val content = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 72.dp, height = 44.dp)
                        .tvCardEffect(onClick = { onSelect(episode.index) }, shape = shape, focusedScale = 1.08f, glow = false)
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

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0x" else "${speed}x"

private const val RESUME_THRESHOLD_MS = 5_000L
private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
