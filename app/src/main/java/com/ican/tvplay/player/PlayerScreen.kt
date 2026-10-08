package com.ican.tvplay.player

import android.app.Activity
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
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
 * FongMi 风格播放界面：全屏视频 + 叠加控制层（自动隐藏）。
 * - 顶部栏：返回 + 标题/分辨率 + DLNA/信息/收藏/设置
 * - 中部：上一集/播放暂停/下一集
 * - 底部：进度条 + 全屏 + 锁屏
 * - 底部标签栏：状态（解码/倍速/缩放/刷新/循环）+ 功能（字幕/音轨/选集）
 * - 锁屏模式：双击解锁后所有控件隐藏
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

    // 线路切换
    var lineFlag by remember { mutableStateOf(startFlag) }
    val playLine = video?.playSources
        ?.let { sources -> sources.firstOrNull { it.flag == lineFlag } ?: sources.firstOrNull() }
    val lineEpisodes = playLine?.episodes ?: video?.episodes.orEmpty()

    // 控制层状态
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var clickCount by remember { mutableIntStateOf(0) }

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

    // 加载视频信息 + 历史进度
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

    // 解析并播放
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

    // 自动隐藏控制层
    LaunchedEffect(controlsVisible, locked, playerState.playing) {
        if (!controlsVisible || locked) return@LaunchedEffect
        if (playerState.playing) {
            delay(HIDE_DELAY_MS)
            if (playerState.playing && !locked) controlsVisible = false
        }
    }

    // Surface 生命周期
    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? Activity
            Players.release()
            if (activity?.isChangingConfigurations != true) {
                saveProgress()
            }
        }
    }

    fun handleBack() {
        saveProgress()
        Players.release()
        onBack()
    }
    BackHandler(enabled = true) { handleBack() }

    val current = video

    // 点击视频画面：解锁 / 切换控制层
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (locked) {
                    // 锁屏：双击解锁
                    clickCount++
                    scope.launch {
                        delay(400)
                        if (clickCount >= 2) {
                            locked = false
                            controlsVisible = true
                        }
                        clickCount = 0
                    }
                } else {
                    controlsVisible = !controlsVisible
                }
            },
    ) {
        // 视频渲染层
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                AspectRatioFrameLayout(ctx).apply {
                    resizeMode = playerState.scaleMode.resizeMode
                    val sv = SurfaceView(ctx)
                    addView(sv, FrameLayout.LayoutParams(-1, -1))
                    val sub = SubtitleView(ctx)
                    addView(sub, FrameLayout.LayoutParams(-1, -1))
                    Players.attach(sv, sub)
                }
            },
            update = { view ->
                view.resizeMode = playerState.scaleMode.resizeMode
            },
        )

        // === 控制层 ===
        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize().zIndex(10f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部栏
                TopBar(
                    title = current?.title.orEmpty(),
                    episodeInfo = "第${episodeIndex + 1}集",
                    videoRes = "1280×544",
                    onBack = { handleBack() },
                    modifier = Modifier.align(Alignment.TopStart),
                )

                // 中部播放控制
                CenterPlayControls(
                    playing = playerState.playing,
                    onTogglePlay = { Players.playPause() },
                    onPrev = { if (episodeIndex > 0) episodeIndex-- },
                    onNext = { if (episodeIndex < lineEpisodes.size - 1) episodeIndex++ },
                    modifier = Modifier.align(Alignment.Center),
                )

                // 底部控制区
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000)),
                            ),
                        ),
                ) {
                    // 进度行
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = formatMs(playerState.positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                        Slider(
                            value = if (playerState.durationMs > 0) {
                                playerState.positionMs.toFloat() / playerState.durationMs
                            } else 0f,
                            onValueChange = { Players.seekTo((it * playerState.durationMs).toLong()) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color(0x44FFFFFF),
                            ),
                        )
                        Text(
                            text = formatMs(playerState.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                        IconButtonCircle(
                            icon = AppIcons.Fullscreen,
                            contentDescription = "全屏",
                            onClick = { /* TODO: 全屏切换 */ },
                        )
                        IconButtonCircle(
                            icon = AppIcons.Lock,
                            contentDescription = "锁屏",
                            onClick = { locked = true; controlsVisible = false },
                        )
                    }

                    // 标签栏：左状态 + 右功能
                    BottomTabBar(
                        state = playerState,
                        video = current,
                        lineFlag = lineFlag,
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
                        onRefresh = {
                            Players.release()
                            if (initialized) episodeIndex = episodeIndex // 触发重播
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // 锁屏提示（右下角图标叠加在视频上）
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .clickable { locked = false; controlsVisible = true },
            ) {
                Icon(
                    AppIcons.LockOpen,
                    contentDescription = "解锁",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

// ========== 顶部栏 ==========

@Composable
private fun TopBar(
    title: String,
    episodeInfo: String,
    videoRes: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xCC000000), Color.Transparent),
                ),
            )
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
    ) {
        IconButtonCircle(
            icon = AppIcons.Back,
            contentDescription = "返回",
            onClick = onBack,
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = "$title · $episodeInfo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
            )
            Text(
                text = videoRes,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButtonCircle(AppIcons.Cast, "投屏") { /* DLNA stub */ }
        IconButtonCircle(AppIcons.Info, "信息") { /* 视频详情弹层 */ }
        IconButtonCircle(AppIcons.Favorite, "收藏") { /* 收藏 */ }
        IconButtonCircle(AppIcons.Settings, "播放器设置") { /* 设置弹层 */ }
    }
}

// ========== 中部播放控制 ==========

@Composable
private fun CenterPlayControls(
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier,
    ) {
        LargeIconButton(
            icon = AppIcons.SkipPrev,
            contentDescription = "上一集",
            onClick = onPrev,
        )
        LargeIconButton(
            icon = if (playing) AppIcons.Pause else AppIcons.Play,
            contentDescription = if (playing) "暂停" else "播放",
            onClick = onTogglePlay,
            isPrimary = true,
        )
        LargeIconButton(
            icon = AppIcons.SkipNext,
            contentDescription = "下一集",
            onClick = onNext,
        )
    }
}

@Composable
private fun LargeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
) {
    val size = if (isPrimary) 56.dp else 44.dp
    Box(
        modifier = Modifier
            .size(size)
            .tvCardEffect(
                onClick = onClick,
                shape = CircleShape,
                focusedScale = if (isPrimary) 1.12f else 1.08f,
                glow = false,
            )
            .background(
                Color(0x55000000),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(if (isPrimary) 28.dp else 22.dp),
        )
    }
}

// ========== 底部标签栏 ==========

@Composable
private fun BottomTabBar(
    state: Players.PlayerState,
    video: Video?,
    lineFlag: String,
    onSpeedChange: (Float) -> Unit,
    onScaleChange: (Players.ScaleMode) -> Unit,
    onDecodeChange: (Players.Decode) -> Unit,
    onBufferChange: (Players.BufferTier) -> Unit,
    onLineChange: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSpeed by remember { mutableStateOf(false) }
    var showScale by remember { mutableStateOf(false) }
    var showDecode by remember { mutableStateOf(false) }
    var showBuffer by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showLine by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var showAddSub by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // 左侧状态标签
        TabLabel("EXO")
        TabLabel(state.decode.label)
        TabLabel(formatSpeed(state.speed))
        TabLabel(state.scaleMode.label)
        TabLabel("刷新", onClick = { onRefresh() })
        TabLabel("循环", enabled = false)

        Spacer(modifier = Modifier.width(12.dp))

        // 分隔
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(Color.White.copy(alpha = 0.3f)),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 右侧功能标签
        TabLabel("字幕", onClick = { showText = true })
        TabLabel("音轨", onClick = { showAudio = true })
        if ((video?.playSources?.size ?: 0) > 1) {
            TabLabel("线路", onClick = { showLine = true })
        }
        TabLabel("选集", onClick = { showEpisodes = true })
    }

    // 对话框
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
    if (showEpisodes && video != null) {
        EpisodeDialog(
            video = video,
            lineFlag = lineFlag,
            onSelect = { idx ->
                Players.release()
                // 触发重新播放
                showEpisodes = false
            },
            onDismiss = { showEpisodes = false },
        )
    }
}

@Composable
private fun TabLabel(
    text: String,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .background(
                if (enabled) Color(0x33FFFFFF) else Color(0x11FFFFFF),
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            fontWeight = FontWeight.Medium,
        )
    }
}

// ========== 图标按钮 ==========

@Composable
private fun IconButtonCircle(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .tvCardEffect(onClick = onClick, shape = CircleShape, focusedScale = 1.08f, glow = false)
            .background(Color(0x55000000), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ========== 通用对话框 ==========

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
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEach { opt ->
                    val selected = opt == current
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = { onSelect(opt) })
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
            Column(modifier = Modifier.padding(top = 14.dp)) {
                SubtitleRow(label = "关闭字幕", selected = textDisabled, onClick = onDisable)
                tracks.forEach { track ->
                    SubtitleRow(label = track.name, selected = !textDisabled && track.selected, onClick = { onSelect(track) })
                }
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
            .clickable(onClick = onClick)
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

@Composable
private fun AddSubtitleDialog(
    onAdd: (String, String) -> Unit,
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

@Composable
private fun EpisodeDialog(
    video: Video,
    lineFlag: String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val episodes = video.playSources
        .let { sources -> sources.firstOrNull { it.flag == lineFlag } ?: sources.firstOrNull() }
        ?.episodes ?: video.episodes

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text(
                text = "选集（${episodes.size} 集）",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                itemsIndexed(episodes) { idx, ep ->
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                            .clickable { onSelect(idx) }
                            .padding(vertical = 10.dp),
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
private const val HIDE_DELAY_MS = 4_000L
