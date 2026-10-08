package com.ican.tvplay.player

import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.ican.tvplay.data.local.FavoriteEntity
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
 * FongMi 风格播放界面：
 * - 默认模式（竖屏）：顶部 16:9 视频区 + 下方可滚动详情信息流（标题/线路/选集/简介）
 * - 全屏模式：点击全屏按钮进入横屏全屏，视频占满屏幕 + 叠加控制层
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
    var fullscreen by remember { mutableStateOf(false) }

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

    // 全屏切换时请求横屏
    LaunchedEffect(fullscreen) {
        val activity = context as? Activity ?: return@LaunchedEffect
        if (fullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Surface 生命周期
    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? Activity
            Players.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (activity?.isChangingConfigurations != true) {
                saveProgress()
            }
        }
    }

    fun handleBack() {
        if (fullscreen) {
            fullscreen = false
            return
        }
        saveProgress()
        Players.release()
        onBack()
    }
    BackHandler(enabled = true) { handleBack() }

    val current = video

    if (fullscreen) {
        FullscreenPlayLayout(
            current = current,
            episodeIndex = episodeIndex,
            lineEpisodes = lineEpisodes,
            playerState = playerState,
            controlsVisible = controlsVisible,
            locked = locked,
            onToggleControls = { controlsVisible = !controlsVisible },
            onTogglePlay = { Players.playPause() },
            onPrev = { if (episodeIndex > 0) episodeIndex-- },
            onNext = { if (episodeIndex < lineEpisodes.size - 1) episodeIndex++ },
            onExitFullscreen = { fullscreen = false },
            onLockToggle = { locked = true; controlsVisible = false },
            onUnlock = { locked = false; controlsVisible = true },
            onBack = { handleBack() },
        )
    } else {
        PortraitPlayLayout(
            current = current,
            episodeIndex = episodeIndex,
            lineEpisodes = lineEpisodes,
            lineFlag = lineFlag,
            playerState = playerState,
            controlsVisible = controlsVisible,
            locked = locked,
            onToggleControls = { controlsVisible = !controlsVisible },
            onTogglePlay = { Players.playPause() },
            onPrev = { if (episodeIndex > 0) episodeIndex-- },
            onNext = { if (episodeIndex < lineEpisodes.size - 1) episodeIndex++ },
            onEnterFullscreen = { fullscreen = true },
            onLockToggle = { locked = true; controlsVisible = false },
            onUnlock = { locked = false; controlsVisible = true },
            onBack = { handleBack() },
            onLineChange = { flag ->
                if (flag != lineFlag) {
                    saveProgress()
                    lineFlag = flag
                }
            },
            onEpisodeSelect = { idx ->
                if (idx != episodeIndex) episodeIndex = idx
            },
            onSpeedChange = { settings.setPlayerSpeed(it) },
            onScaleChange = { settings.setPlayerScale(it.name) },
            onDecodeChange = { settings.setPlayerDecode(it.name) },
            onBufferChange = { settings.setPlayerBuffer(it.multiplier) },
        )
    }
}

// ========== 竖屏布局 ==========

@Composable
private fun PortraitPlayLayout(
    current: Video?,
    episodeIndex: Int,
    lineEpisodes: List<Episode>,
    lineFlag: String,
    playerState: Players.PlayerState,
    controlsVisible: Boolean,
    locked: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onLockToggle: () -> Unit,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
    onLineChange: (String) -> Unit,
    onEpisodeSelect: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onScaleChange: (Players.ScaleMode) -> Unit,
    onDecodeChange: (Players.Decode) -> Unit,
    onBufferChange: (Players.BufferTier) -> Unit,
) {
    val context = LocalContext.current
    val container = appContainer()
    val scope = rememberCoroutineScope()

    var showSpeed by remember { mutableStateOf(false) }
    var showScale by remember { mutableStateOf(false) }
    var showDecode by remember { mutableStateOf(false) }
    var showBuffer by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showAddSub by remember { mutableStateOf(false) }
    var showLineDialog by remember { mutableStateOf(false) }
    var expandedDesc by remember { mutableStateOf(false) }
    var showAllEps by remember { mutableStateOf(false) }
    var isFav by remember { mutableStateOf(false) }

    LaunchedEffect(current?.id) {
        if (current != null) {
            isFav = container.favoriteDao.isFavorite(current.id)
        }
    }

    // === 视频播放区 ===
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggleControls() },
    ) {
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

        // 叠加控制层
        if (controlsVisible && !locked) {
            VideoOverlay(
                title = "${current?.title.orEmpty()} · ${lineEpisodes.getOrNull(episodeIndex)?.title.orEmpty()}",
                positionMs = playerState.positionMs,
                durationMs = playerState.durationMs,
                playing = playerState.playing,
                onSeek = { Players.seekTo(it) },
                onBack = onBack,
                onFullscreen = onEnterFullscreen,
                onLock = onLockToggle,
                onPrev = onPrev,
                onNext = onNext,
                onTogglePlay = onTogglePlay,
            )
        }

        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .clickable { onUnlock() },
            ) {
                Icon(
                    AppIcons.LockOpen,
                    contentDescription = "解锁",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }

    // === 阴影分割线 ===
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x33000000), Color.Transparent),
                ),
            ),
    )

    // === 可滚动详情信息流 ===
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val v = current ?: return@Column

        // 标题
        Text(
            text = v.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 3,
        )

        // 更新备注
        val eps = v.playSources.firstOrNull()?.episodes ?: v.episodes
        val remark = when {
            eps.isEmpty() -> ""
            eps.last().title.contains("完结") -> "已完结"
            else -> "更新至第${eps.size}集"
        }
        if (remark.isNotBlank()) {
            Text(
                text = remark,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 站源
        Text(
            text = "站源：$lineFlag",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 年份/地区/类型
        val meta = buildString {
            if (v.year > 0) append("年份：${v.year}")
            if (v.region.isNotBlank()) {
                if (isNotEmpty()) append("  ")
                append("地区：${v.region}")
            }
            if (v.categoryName.isNotBlank()) {
                if (isNotEmpty()) append("  ")
                append("类型：${v.categoryName}")
            }
        }
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 操作按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionBtn(
                icon = if (isFav) AppIcons.Favorite else AppIcons.FavoriteBorder,
                text = if (isFav) "已收藏" else "收藏",
                tint = if (isFav) Color(0xFFFF5C8A) else MaterialTheme.colorScheme.onSurface,
            ) {
                scope.launch {
                    if (container.favoriteDao.isFavorite(v.id)) {
                        container.favoriteDao.delete(v.id)
                    } else {
                        container.favoriteDao.upsert(
                            FavoriteEntity(
                                videoId = v.id, title = v.title, cover = v.cover,
                                categoryName = v.categoryName, addedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                    isFav = container.favoriteDao.isFavorite(v.id)
                }
            }
            ActionBtn(icon = AppIcons.Speed, text = formatSpeed(playerState.speed)) { showSpeed = true }
            ActionBtn(icon = AppIcons.Settings, text = playerState.scaleMode.label) { showScale = true }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === 线路 ===
        if (v.playSources.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "线路",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "切换 ▸",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showLineDialog = true },
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(v.playSources) { source ->
                    val selected = source.flag == lineFlag
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .tvCardEffect(
                                onClick = { onLineChange(source.flag) },
                                shape = shape, focusedScale = 1.06f, glow = false,
                            )
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape,
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = source.flag,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === 选集 ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "选集",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (showAllEps) "收起 ▾" else "更多 ▸",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showAllEps = !showAllEps },
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        val epsToShow = if (showAllEps || lineEpisodes.size <= 20) {
            lineEpisodes
        } else {
            lineEpisodes.take(20)
        }

        if (showAllEps || lineEpisodes.size <= 20) {
            val cols = if (lineEpisodes.size > 50) 8 else 6
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height((((lineEpisodes.size / cols) + 1) * 44).dp),
                userScrollEnabled = false,
            ) {
                itemsIndexed(lineEpisodes) { idx, ep ->
                    EpisodeChip(
                        title = ep.title,
                        selected = idx == episodeIndex,
                        onClick = { onEpisodeSelect(idx) },
                    )
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(epsToShow.withIndex().toList()) { (idx, ep) ->
                    EpisodeChip(
                        title = ep.title,
                        selected = idx == episodeIndex,
                        onClick = { onEpisodeSelect(idx) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === 简介 ===
        Text(
            text = "简介",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().clickable { expandedDesc = !expandedDesc }) {
            Text(
                text = v.description.ifBlank { "暂无简介" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expandedDesc) Int.MAX_VALUE else 4,
            )
        }

        Spacer(modifier = Modifier.height(60.dp))
    }

    // === 底部标签栏 ===
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiniTag(playerState.decode.label)
        MiniTag(formatSpeed(playerState.speed)) { showSpeed = true }
        MiniTag(playerState.scaleMode.label) { showScale = true }
        MiniTag(playerState.bufferTier.label) { showBuffer = true }
        MiniTag("刷新") { Players.release() }
    }

    // === 对话框 ===
    if (showSpeed) {
        OptionDialog("倍速", listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f), playerState.speed, { formatSpeed(it) }, { onSpeedChange(it); showSpeed = false }) { showSpeed = false }
    }
    if (showScale) {
        OptionDialog("画面缩放", Players.ScaleMode.entries, playerState.scaleMode, { it.label }, { onScaleChange(it); showScale = false }) { showScale = false }
    }
    if (showDecode) {
        OptionDialog("解码内核", Players.Decode.entries, playerState.decode, { it.label }, { onDecodeChange(it); showDecode = false }) { showDecode = false }
    }
    if (showBuffer) {
        OptionDialog("缓冲档位", Players.BufferTier.entries, playerState.bufferTier, { it.label }, { onBufferChange(it); showBuffer = false }) { showBuffer = false }
    }
    if (showLineDialog && current != null) {
        OptionDialog(
            title = "切换线路",
            options = current.playSources,
            current = current.playSources.firstOrNull { it.flag == lineFlag },
            label = { it.flag },
            onSelect = { onLineChange(it.flag); showLineDialog = false },
            onDismiss = { showLineDialog = false },
        )
    }
    if (showAudio) {
        OptionDialog("音轨", playerState.audioTracks, playerState.audioTracks.firstOrNull { it.selected }, { it.name }, { Players.selectTrack(it, androidx.media3.common.C.TRACK_TYPE_AUDIO); showAudio = false }) { showAudio = false }
    }
    if (showText) {
        SubtitleDialog(
            tracks = playerState.textTracks,
            textDisabled = playerState.textDisabled,
            onSelect = { Players.selectTrack(it, androidx.media3.common.C.TRACK_TYPE_TEXT); showText = false },
            onDisable = { Players.selectTrack(null, androidx.media3.common.C.TRACK_TYPE_TEXT); showText = false },
            onAddExternal = { showAddSub = true },
            onDismiss = { showText = false },
        )
    }
    if (showAddSub) {
        AddSubtitleDialog(
            onAdd = { n, u -> Players.addSubtitle(SubItem(name = n, url = u)); showAddSub = false },
            onDismiss = { showAddSub = false },
        )
    }
}

// ========== 视频叠加控制层（竖屏） ==========

@Composable
private fun VideoOverlay(
    title: String,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onLock: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 顶部栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xCC000000), Color.Transparent),
                    ),
                )
                .statusBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 20.dp),
        ) {
            MiniCircleBtn(AppIcons.Back, "返回") { onBack() }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            MiniCircleBtn(AppIcons.Fullscreen, "全屏") { onFullscreen() }
            MiniCircleBtn(AppIcons.Lock, "锁屏") { onLock() }
        }

        // 中部播放控制
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.align(Alignment.Center),
        ) {
            MiniLargeBtn(AppIcons.SkipPrev, "上集", onClick = { onPrev() })
            MiniLargeBtn(
                if (playing) AppIcons.Pause else AppIcons.Play,
                if (playing) "暂停" else "播放",
                onClick = { onTogglePlay() },
                isPrimary = true,
            )
            MiniLargeBtn(AppIcons.SkipNext, "下集", onClick = { onNext() })
        }

        // 底部进度条
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xAA000000)),
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = formatMs(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
            Slider(
                value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                onValueChange = { onSeek((it * durationMs).toLong()) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color(0x44FFFFFF),
                ),
            )
            Text(
                text = formatMs(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
}

// ========== 全屏布局 ==========

@Composable
private fun FullscreenPlayLayout(
    current: Video?,
    episodeIndex: Int,
    lineEpisodes: List<Episode>,
    playerState: Players.PlayerState,
    controlsVisible: Boolean,
    locked: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExitFullscreen: () -> Unit,
    onLockToggle: () -> Unit,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (locked) {
                    scope.launch {
                        delay(400)
                        onUnlock()
                    }
                } else {
                    onToggleControls()
                }
            },
    ) {
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
            update = { view -> view.resizeMode = playerState.scaleMode.resizeMode },
        )

        if (controlsVisible && !locked) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xCC000000), Color.Transparent),
                            ),
                        )
                        .statusBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                ) {
                    CircleBtn(AppIcons.Back, "返回") { onBack() }
                    Text(
                        text = "${current?.title.orEmpty()} · 第${episodeIndex + 1}集",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    CircleBtn(AppIcons.Fullscreen, "退出全屏") { onExitFullscreen() }
                    CircleBtn(AppIcons.Lock, "锁屏") { onLockToggle() }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    BigBtn(AppIcons.SkipPrev, "上集", onClick = { onPrev() })
                    BigBtn(
                        if (playerState.playing) AppIcons.Pause else AppIcons.Play,
                        if (playerState.playing) "暂停" else "播放",
                        onClick = { onTogglePlay() },
                        isPrimary = true,
                    )
                    BigBtn(AppIcons.SkipNext, "下集", onClick = { onNext() })
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000)),
                            ),
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(text = formatMs(playerState.positionMs), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    Slider(
                        value = if (playerState.durationMs > 0) playerState.positionMs.toFloat() / playerState.durationMs else 0f,
                        onValueChange = { Players.seekTo((it * playerState.durationMs).toLong()) },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color(0x44FFFFFF),
                        ),
                    )
                    Text(text = formatMs(playerState.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }

        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .clickable { onUnlock() },
            ) {
                Icon(AppIcons.LockOpen, "解锁", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
            }
        }
    }
}

// ========== 通用 UI 组件 ==========

@Composable
private fun ActionBtn(
    icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .tvCardEffect(onClick = onClick, shape = shape, focusedScale = 1.06f, glow = false)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EpisodeChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .tvCardEffect(onClick = onClick, shape = shape, focusedScale = 1.05f, glow = false)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant,
                shape,
            )
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun MiniTag(text: String, onClick: () -> Unit = {}) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniCircleBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .tvCardEffect(onClick = onClick, shape = CircleShape, focusedScale = 1.08f, glow = false)
            .background(Color(0x55000000), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun CircleBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .tvCardEffect(onClick = onClick, shape = CircleShape, focusedScale = 1.08f, glow = false)
            .background(Color(0x55000000), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun MiniLargeBtn(icon: ImageVector, desc: String, onClick: () -> Unit, isPrimary: Boolean = false) {
    val size = if (isPrimary) 44.dp else 36.dp
    Box(
        modifier = Modifier
            .size(size)
            .tvCardEffect(onClick = onClick, shape = CircleShape, focusedScale = if (isPrimary) 1.12f else 1.08f, glow = false)
            .background(Color(0x55000000), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(if (isPrimary) 24.dp else 18.dp))
    }
}

@Composable
private fun BigBtn(icon: ImageVector, desc: String, onClick: () -> Unit, isPrimary: Boolean = false) {
    val size = if (isPrimary) 56.dp else 44.dp
    Box(
        modifier = Modifier
            .size(size)
            .tvCardEffect(onClick = onClick, shape = CircleShape, focusedScale = if (isPrimary) 1.12f else 1.08f, glow = false)
            .background(Color(0x55000000), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(if (isPrimary) 28.dp else 22.dp))
    }
}

// ========== 对话框 ==========

@Composable
private fun <T> OptionDialog(
    title: String, options: List<T>, current: T?, label: (T) -> String,
    onSelect: (T) -> Unit, onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                else MaterialTheme.colorScheme.surfaceVariant, shape,
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(label(opt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        if (selected) Icon(AppIcons.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleDialog(
    tracks: List<Players.TrackOption>, textDisabled: Boolean,
    onSelect: (Players.TrackOption) -> Unit, onDisable: () -> Unit,
    onAddExternal: () -> Unit, onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth().padding(horizontal = 32.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text("字幕", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Column(modifier = Modifier.padding(top = 14.dp)) {
                SubRow("关闭字幕", textDisabled) { onDisable() }
                tracks.forEach { t -> SubRow(t.name, !textDisabled && t.selected) { onSelect(t) } }
                SubRow("添加外挂字幕…", false) { onAddExternal() }
            }
        }
    }
}

@Composable
private fun SubRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth().clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant, shape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (selected) Icon(AppIcons.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AddSubtitleDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)).padding(20.dp),
        ) {
            Text("添加外挂字幕", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            androidx.compose.material3.TextField(
                value = url, onValueChange = { url = it },
                placeholder = { androidx.compose.material3.Text("字幕文件 URL（srt/ass/vtt）") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("取消", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 10.dp))
                Text("添加", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = url.isNotBlank()) {
                        onAdd(url.substringAfterLast('/').ifBlank { "外挂字幕" }, url.trim())
                    }.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

// ========== 工具方法 ==========

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
