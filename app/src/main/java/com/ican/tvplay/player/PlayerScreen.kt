package com.ican.tvplay.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.data.BACKGROUND_OFF
import com.ican.tvplay.data.local.FavoriteEntity
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.data.model.Episode
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.player.core.PlaybackService
import com.ican.tvplay.player.core.PlaySpec
import com.ican.tvplay.player.core.Players
import com.ican.tvplay.player.core.SubItem
import com.ican.tvplay.ui.appContainer
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.CircleBackButton
import com.ican.tvplay.ui.components.CircleIconButton
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
    preInitialized: Boolean = false,
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
    // 片头/片尾标记（ms；片尾存"距结尾时长"，fongmi History.opening/ending 同款）
    var openingMs by remember { mutableLongStateOf(0L) }
    var endingMs by remember { mutableLongStateOf(0L) }
    // 片尾自动切下一集的防重复触发标志（每集/每次改标记后重新武装）
    var autoNextArmed by remember(endingMs) { mutableStateOf(true) }
    val skipFirstStart = remember { mutableStateOf(preInitialized) }

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

    // 后台播放（设置三态：关闭/开启/画中画）：非关闭时退出播放页转交前台服务后台续播
    var backgroundMode by remember { mutableIntStateOf(BACKGROUND_OFF) }
    LaunchedEffect(Unit) {
        backgroundMode = container.settingsRepository.playerBackgroundPlay.first()
        PlaybackService.startIfNeeded(context, backgroundMode)
    }
    // PiP 画中画模式（全屏播放时切后台触发）
    val inPip by Players.pipMode.collectAsStateWithLifecycle()
    // 后台续播返回本页时跳过一次重新解析（直接接管在播的播放器）
    var resumeFromBackground by remember { mutableStateOf(false) }

    // 加载视频信息 + 历史进度
    LaunchedEffect(videoId) {
        // 后台续播返回：播放器仍在播同一部剧同一集 → 直接接管，不重新解析
        val alive = Players.playbackContext?.takeIf {
            Players.isAlive() && it.video.id == videoId
        }
        if (alive != null && startEpisode == alive.episodeIndex &&
            (startFlag.isEmpty() || startFlag == alive.lineFlag)
        ) {
            video = alive.video
            episodeIndex = alive.episodeIndex
            lineFlag = alive.lineFlag
            openingMs = alive.openingMs
            endingMs = alive.endingMs
            resumeFromBackground = true
            initialized = true
            return@LaunchedEffect
        }
        // 跨站源场景优先：MultiSourceSearchScreen 提前 cache 的完整视频直接命中，
        // 避免先在 VideoRepository 的当前站点上发起慢查询（错站查询既慢又可能查错视频）
        var v = Players.consumeCachedVideo(videoId)?.video
        if (v == null) {
            v = container.videoRepository.getVideo(videoId)
        }
        video = v
        if (v != null) {
            val history = container.historyDao.observeOne(videoId).first()
            val eps = v.playSources
                .let { s -> s.firstOrNull { it.flag == lineFlag } ?: s.firstOrNull() }
                ?.episodes ?: v.episodes
            if (history != null && history.episodeIndex in eps.indices) {
                episodeIndex = history.episodeIndex
                val nearEnd = history.durationMs > 0 &&
                    history.durationMs - history.positionMs < RESUME_THRESHOLD_MS
                val resume = if (nearEnd) 0L else history.positionMs
                // fongmi: startPositionMs = max(opening, 续播位置)，每次开播自动跳过片头
                pendingPosition = maxOf(resume, history.openingMs)
            }
            // 片头/片尾标记（按剧存储，fongmi History.opening/ending 同款）
            openingMs = history?.openingMs ?: 0L
            endingMs = history?.endingMs ?: 0L
        }
        initialized = true
    }

    // 解析并播放
    LaunchedEffect(initialized, episodeIndex, lineFlag) {
        if (!initialized) return@LaunchedEffect
        if (resumeFromBackground) {
            // 后台续播接管：播放器已在播，无需重新解析
            resumeFromBackground = false
            return@LaunchedEffect
        }
        val v = video ?: return@LaunchedEffect
        val episode = lineEpisodes.getOrNull(episodeIndex) ?: return@LaunchedEffect
        val pos = pendingPosition
        pendingPosition = 0L

        // 优先用上游（DetailScreen）提前解析好的 PlaySpec
        val preResolved = Players.pendingPreStartSpec?.also { Players.pendingPreStartSpec = null }
        val spec = if (preResolved != null) {
            preResolved
        } else {
            val cached = Players.cachedVideo?.takeIf { it.video.id == v.id }
            val source = if (cached?.site != null) {
                // 跨站源：在播放页内解析（某些网盘 jar 在 playerContent 时触发扫码弹窗，
                // 解析必须发生在进入播放页之后，弹窗时机才正确）
                container.spiderManager.resolvePlayUrl(
                    site = cached.site,
                    jarSpec = cached.jarSpec,
                    playUrl = episode.playUrl,
                    flag = playLine?.flag ?: v.playFrom,
                )
            } else {
                container.videoRepository.resolvePlaySource(v, episode, playLine?.flag ?: v.playFrom)
            }
            PlaySpec(url = source.url, headers = source.headers)
        }
        // 注册播放上下文：通知栏展示 / 上一集下一集 / 后台片尾自动切集依赖
        Players.playbackContext = Players.PlaybackContext(
            video = v,
            lineFlag = playLine?.flag ?: v.playFrom,
            episodes = lineEpisodes,
            episodeIndex = episodeIndex,
            openingMs = openingMs,
            endingMs = endingMs,
        )
        Players.start(
            context = context,
            spec = spec,
            startPositionMs = pos,
        )
    }

    fun saveProgress() {
        val v = video ?: return
        val episode = lineEpisodes.getOrNull(episodeIndex) ?: return
        val st = Players.state.value
        // 同步片头/片尾标记到播放上下文（后台切集/通知栏用）
        Players.playbackContext?.takeIf { it.video.id == v.id }?.let {
            Players.playbackContext = it.copy(openingMs = openingMs, endingMs = endingMs)
        }
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
                    openingMs = openingMs,
                    endingMs = endingMs,
                ),
            )
        }
    }

    // ========== 片头/片尾标记（fongmi: onOpening/onEnding 同款规则） ==========

    /** 标记允许的时限：视频 <15 分钟→3 分钟；<30 分钟→6 分钟；否则→10 分钟 */
    fun opEdLimit(duration: Long): Long = when {
        duration < 15 * 60_000L -> 3 * 60_000L
        duration < 30 * 60_000L -> 6 * 60_000L
        else -> 10 * 60_000L
    }

    fun toastMsg(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 短按片头：把当前进度标记为片头（fongmi: canSetOpening + setOpening(position)） */
    fun markOpening() {
        val st = Players.state.value
        val limit = opEdLimit(st.durationMs)
        if (st.durationMs > 0 && st.positionMs > 0 && st.positionMs <= limit) {
            openingMs = st.positionMs
            toastMsg("已标记片头 ${formatMs(st.positionMs)}，此后自动跳过")
            saveProgress()
        } else {
            toastMsg("片头标记需在开头 ${limit / 60_000} 分钟内")
        }
    }

    /** 短按片尾：把当前进度距结尾的时长标记为片尾（fongmi: setEnding(duration - position)） */
    fun markEnding() {
        val st = Players.state.value
        val remain = st.durationMs - st.positionMs
        val limit = opEdLimit(st.durationMs)
        if (st.durationMs > 0 && st.positionMs > 0 && remain in 1 until limit) {
            endingMs = remain
            toastMsg("已标记片尾，播到此处自动下一集")
            saveProgress()
        } else {
            toastMsg("片尾标记需在结尾 ${limit / 60_000} 分钟内")
        }
    }

    // 片尾自动切下一集（fongmi: onTimeChanged → nextEpisode）
    LaunchedEffect(playerState.positionMs) {
        val dur = playerState.durationMs
        if (autoNextArmed && endingMs > 0 && dur > 0 && playerState.playing &&
            endingMs + playerState.positionMs >= dur
        ) {
            autoNextArmed = false
            if (episodeIndex < lineEpisodes.size - 1) {
                toastMsg("已跳过片尾")
                episodeIndex++
            }
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

    // 全屏切换时请求横屏 + 自动收起/恢复状态栏
    LaunchedEffect(fullscreen) {
        Players.isFullscreen = fullscreen
        val activity = context as? Activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            // 沉浸式：隐藏状态栏，从边缘上滑可临时唤出
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    // 页面销毁时恢复状态栏（防止全屏中直接退出后状态栏缺失）
    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    // 通知栏/后台切集导航：页面存活时通过 episodeIndex 驱动页面解析流
    DisposableEffect(Unit) {
        Players.uiEpisodeNavigator = { target ->
            if (target != episodeIndex) episodeIndex = target
        }
        onDispose { Players.uiEpisodeNavigator = null }
    }

    // Surface 生命周期
    DisposableEffect(Unit) {
        // 监听 Activity 生命周期：切后台自动 pause，回前台 resume
        val activity = context as? android.app.Activity
        val lifecycle = (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                // PiP 画中画中不暂停（小窗继续播）
                if (activity?.isInPictureInPictureMode != true) Players.onPause()
            }
            override fun onResume(owner: LifecycleOwner) { Players.onResume() }
        }
        lifecycle?.addObserver(lifecycleObserver)

        onDispose {
            lifecycle?.removeObserver(lifecycleObserver)
            val isConfigChange = activity?.isChangingConfigurations == true
            // 屏幕旋转：只保存进度 + 解锁方向，不 release 播放器（单例保持播放）
            if (isConfigChange) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                saveProgress()
            } else {
                // 真正退出：保存进度；保活开启 → 转交前台服务后台续播（不销毁播放器）
                saveProgress()
                if (backgroundMode != BACKGROUND_OFF && Players.isAlive()) {
                    Players.detachForBackground()
                } else {
                    Players.release()
                    PlaybackService.stop(context)
                }
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    fun handleBack() {
        if (fullscreen) {
            fullscreen = false
            return
        }
        // release / 后台续播转交由 onDispose 统一处理（popBackStack 后触发）
        onBack()
    }
    BackHandler(enabled = true) { handleBack() }

    // PiP 画中画：窗口是整个 Activity 缩小画面，只渲染纯视频（隐藏全部 UI）
    if (inPip) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { pctx ->
                    PlayerView(pctx).apply {
                        useController = false
                        Players.bindPlayerView(this)
                    }
                },
                update = { view ->
                    if (view.player !== Players.player) Players.bindPlayerView(view)
                },
                onRelease = { view -> Players.unbindPlayerView(view) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    val current = video

    // video 加载中 → 黑底转圈过渡页（LaunchedEffect 是异步的，初始帧 video 必为 null，
    // 不能立即渲染错误页，否则加载期间会闪错误页）
    if (current == null) {
        if (!initialized) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
            }
            return
        }
        PlayerErrorScreen(
            videoId = videoId,
            playerError = playerState.error,
            onBack = { handleBack() },
        )
        return
    }

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
            onSpeedChange = { settings.setPlayerSpeed(it) },
            onScaleChange = { settings.setPlayerScale(it.name) },
            onDecodeChange = { settings.setPlayerDecode(it.name) },
            onBufferChange = { settings.setPlayerBuffer(it.multiplier) },
            openingMs = openingMs,
            endingMs = endingMs,
            onMarkOpening = { markOpening() },
            onMarkEnding = { markEnding() },
            onClearOpening = {
                openingMs = 0
                toastMsg("已清除片头标记")
                saveProgress()
            },
            onClearEnding = {
                endingMs = 0
                toastMsg("已清除片尾标记")
                saveProgress()
            },
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
            onEnterFullscreen = { controlsVisible = false; fullscreen = true },
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
    var expandedDesc by remember { mutableStateOf(false) }
    var showAllEps by remember { mutableStateOf(false) }
    var reversedEp by remember { mutableStateOf(false) }
    var isFav by remember { mutableStateOf(false) }

    LaunchedEffect(current?.id) {
        if (current != null) {
            isFav = container.favoriteDao.isFavorite(current.id)
        }
    }

    // 单一 Column 根：NavHost 目的地容器会给多个顶层子元素传播 fill 约束，
    // 多顶层发射会让底部标签栏被拉伸成全屏、白色背景盖住视频区（"纯白底+只有按钮"的根因）
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {

    // === 视频播放区（顶部贴状态栏下沿；返回/标题/收藏悬浮叠加在画面顶部） ===
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
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
                // fongmi 同款：Surface 全权交给 PlayerView 内部管理
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = playerState.scaleMode.resizeMode
                    Players.bindPlayerView(this)
                }
            },
            update = { view ->
                view.resizeMode = playerState.scaleMode.resizeMode
                // 播放器重建（切换解码/缓冲档位）后 player 实例变化 → 重新绑定
                if (view.player !== Players.player) Players.bindPlayerView(view)
            },
            onRelease = { view -> Players.unbindPlayerView(view) },
        )

        // 悬浮顶栏：返回圆钮 + 标题 + 收藏圆钮，叠加在视频画面顶部（背景为画面，标题用白字+阴影）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp),
        ) {
            CircleBackButton(onClick = onBack)
            Text(
                text = current?.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        blurRadius = 8f,
                    ),
                ),
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            // 收藏圆钮（与返回圆钮同尺寸同样式）
            CircleIconButton(
                icon = if (isFav) AppIcons.Favorite else AppIcons.FavoriteBorder,
                contentDescription = if (isFav) "取消收藏" else "收藏",
                tint = if (isFav) Color(0xFFFF5C8A) else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = {
                    val v = current ?: return@CircleIconButton
                    val newFav = !isFav
                    isFav = newFav
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        if (newFav) {
                            container.favoriteDao.upsert(
                                com.ican.tvplay.data.local.FavoriteEntity(
                                    videoId = v.id,
                                    title = v.title,
                                    cover = v.cover,
                                    categoryName = v.categoryName,
                                    addedAt = System.currentTimeMillis(),
                                ),
                            )
                        } else {
                            container.favoriteDao.delete(v.id)
                        }
                    }
                },
            )
        }

        // 预览小窗控制层：单击视频区显示/隐藏（fongmi 同款交互）
        // 视频区已做 statusBarsPadding，控制层天然避让；全屏按钮独立常显在右下角
        if (controlsVisible && !locked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onToggleControls() },
            ) {
                // 控制层：中部播放控制 + 底部进度条/全屏（返回/标题/收藏在视频区上方顶栏，不再覆盖画面）

                // 中部：上一集 / 播放暂停 / 下一集
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    MiniLargeBtn(AppIcons.SkipPrev, "上集", onClick = { onPrev() })
                    MiniLargeBtn(
                        if (playerState.playing) AppIcons.Pause else AppIcons.Play,
                        if (playerState.playing) "暂停" else "播放",
                        onClick = { onTogglePlay() },
                        isPrimary = true,
                    )
                    MiniLargeBtn(AppIcons.SkipNext, "下集", onClick = { onNext() })
                }

                // 底部：进度条
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
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(text = formatMs(playerState.positionMs), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    LineSlider(
                        progress = if (playerState.durationMs > 0) playerState.positionMs.toFloat() / playerState.durationMs else 0f,
                        onSeek = { Players.seekTo((it * playerState.durationMs).toLong()) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(text = formatMs(playerState.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    // 全屏按钮：随控制层显示/隐藏（与播放暂停按钮同机制），位于小窗右下角
                    MiniCircleBtn(AppIcons.Fullscreen, "全屏") { onEnterFullscreen() }
                }
            }
        }

        // 加载转圈（fongmi: view_progress）
        if (playerState.buffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
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

    // === 可滚动详情信息流（左右边距对齐全局规范） ===
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = com.ican.tvplay.ui.theme.Spacing.pageHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val v = current ?: return@Column

        // 标题（fongmi: name，20sp 粗体，最多 3 行）
        Text(
            text = v.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        // 更新备注（fongmi: remark）
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

        // 站源（fongmi: site）
        Text(
            text = "站源：${current?.sourceName?.takeIf { it.isNotBlank() } ?: lineFlag.substringBefore("|")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 年份/地区/类型（fongmi: other）
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

        // === 线路（fongmi: detail_flag 区块）===
        Text(
            text = "线路",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(v.playSources) { source ->
                val selected = source.flag == lineFlag
                val shape = RoundedCornerShape(com.ican.tvplay.ui.theme.Spacing.corner)
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

        Spacer(modifier = Modifier.height(12.dp))

        // === 选集（fongmi: detail_episode 标题行 + 倒序 + 更多）===
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
            Text(
                text = if (reversedEp) "正序" else "倒序",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable { reversedEp = !reversedEp },
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

        val displayEps = if (reversedEp) lineEpisodes.asReversed() else lineEpisodes
        fun realIdx(displayIdx: Int) = if (reversedEp) lineEpisodes.size - 1 - displayIdx else displayIdx

        if (showAllEps) {
            // 网格模式：与收起状态同一自适应按钮（最小正方形、随文字变长），FlowRow 自动换行
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                displayEps.forEachIndexed { dispIdx, ep ->
                    val idx = realIdx(dispIdx)
                    EpisodeChip(
                        title = ep.title,
                        selected = idx == episodeIndex,
                        onClick = { onEpisodeSelect(idx) },
                    )
                }
            }
        } else {
            // 默认横向滚动（fongmi: adapter_episode_hori），按钮尺寸与展开网格一致
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(displayEps) { dispIdx, ep ->
                    val idx = realIdx(dispIdx)
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
    } // Column 根结束
}

// ========== 视频叠加控制层（竖屏） ==========

@Composable
private fun VideoOverlay(
    title: String,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    isFav: Boolean,
    onFav: () -> Unit,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onLock: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    bottomActions: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 顶部栏（fongmi: 返回 + 标题 + 收藏）
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
            MiniCircleBtn(
                if (isFav) AppIcons.Favorite else AppIcons.FavoriteBorder,
                if (isFav) "已收藏" else "收藏",
                tint = if (isFav) Color(0xFFFF5C8A) else Color.White,
            ) { onFav() }
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

        // 底部：进度条 + 动作栏（fongmi: seek + view_control_vod_action）
        Column(
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatMs(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
                LineSlider(
                    progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                    onSeek = { onSeek((it * durationMs).toLong()) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    text = formatMs(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            // 动作栏：横向滚动文字按钮（fongmi: 解码/倍速/缩放/字幕/音轨…）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                bottomActions()
            }
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
    onSpeedChange: (Float) -> Unit,
    onScaleChange: (Players.ScaleMode) -> Unit,
    onDecodeChange: (Players.Decode) -> Unit,
    onBufferChange: (Players.BufferTier) -> Unit,
    openingMs: Long,
    endingMs: Long,
    onMarkOpening: () -> Unit,
    onMarkEnding: () -> Unit,
    onClearOpening: () -> Unit,
    onClearEnding: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 锁定状态下的解锁按钮显隐（单击唤出，2.5s 自动隐藏）
    var unlockHint by remember { mutableStateOf(false) }
    LaunchedEffect(unlockHint) {
        if (unlockHint) {
            delay(2500)
            unlockHint = false
        }
    }
    // 动作栏对话框状态
    var showSpeed by remember { mutableStateOf(false) }
    var showScale by remember { mutableStateOf(false) }
    var showDecode by remember { mutableStateOf(false) }
    var showBuffer by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showAddSub by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = {
                        if (locked) {
                            // 锁定中：单击只唤出解锁按钮，不触发任何其他控制
                            unlockHint = true
                        } else {
                            onToggleControls()
                        }
                    },
                    onDoubleTap = {
                        // 双击：任何状态下都切换播放/暂停
                        onTogglePlay()
                    },
                )
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // fongmi 同款：Surface 全权交给 PlayerView 内部管理
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = playerState.scaleMode.resizeMode
                    Players.bindPlayerView(this)
                }
            },
            update = { view ->
                view.resizeMode = playerState.scaleMode.resizeMode
                if (view.player !== Players.player) Players.bindPlayerView(view)
            },
            onRelease = { view -> Players.unbindPlayerView(view) },
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
                    CircleBtn(AppIcons.Back, "返回") { onExitFullscreen() }
                    Text(
                        text = "${current?.title.orEmpty()} · 第${episodeIndex + 1}集",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
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

                // 底部：进度条 + 动作栏（fongmi: seek + view_control_vod_action）
                Column(
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = formatMs(playerState.positionMs), style = MaterialTheme.typography.labelSmall, color = Color.White)
                        LineSlider(
                            progress = if (playerState.durationMs > 0) playerState.positionMs.toFloat() / playerState.durationMs else 0f,
                            onSeek = { Players.seekTo((it * playerState.durationMs).toLong()) },
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text(text = formatMs(playerState.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                    // 动作栏：解码/缓冲/倍速/缩放/字幕/音轨/重播
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        ActionBtn(AppIcons.Memory, playerState.decode.label) { showDecode = true }
                        ActionBtn(AppIcons.Buffer, playerState.bufferTier.label) { showBuffer = true }
                        ActionBtn(AppIcons.Speed, formatSpeed(playerState.speed)) { showSpeed = true }
                        ActionBtn(AppIcons.AspectRatio, playerState.scaleMode.label) { showScale = true }
                        ActionBtn(AppIcons.Subtitle, "字幕") { showText = true }
                        ActionBtn(AppIcons.AudioTrack, "音轨") { showAudio = true }
                        // 片头/片尾：短按标记，长按清除（fongmi 同款）
                        ActionBtn(
                            AppIcons.SkipPrev,
                            if (openingMs > 0) formatMs(openingMs) else "片头",
                            onLongClick = onClearOpening,
                            onClick = onMarkOpening,
                        )
                        ActionBtn(
                            AppIcons.SkipNext,
                            if (endingMs > 0) formatMs(endingMs) else "片尾",
                            onLongClick = onClearEnding,
                            onClick = onMarkEnding,
                        )
                        ActionBtn(AppIcons.Refresh, "重播") { Players.seekTo(0) }
                    }
                }
            }
        }

        // 右侧居中：锁定按钮（未锁定时随控制层显示）与解锁按钮（锁定中单击唤出）
        if (!locked && controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onLockToggle() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.LockOpen, "锁屏", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        if (locked && unlockHint) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onUnlock() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Lock, "解锁", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // 动作栏对话框（与竖屏同款）
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
}

// ========== 通用 UI 组件 ==========

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ActionBtn(
    icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurface,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EpisodeChip(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(com.ican.tvplay.ui.theme.Spacing.corner)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            // 高度固定，最小宽度为正方形（40dp）；文字长时宽度自适应撑开
            .height(40.dp)
            .defaultMinSize(minWidth = 40.dp)
            .tvCardEffect(onClick = onClick, shape = shape, focusedScale = 1.05f, glow = false)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant,
                shape,
            )
            .padding(horizontal = 12.dp),
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
private fun MiniCircleBtn(
    icon: ImageVector,
    desc: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .tvCardEffect(onClick = onClick, shape = CircleShape, focusedScale = 1.08f, glow = false)
            .background(Color(0x55000000), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(16.dp)) }
}

/** 播放器底部动作栏文字按钮（fongmi: style/Control，白色小字横向滚动） */
@Composable
private fun ControlChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        maxLines = 1,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
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

/** 细直线进度条：2dp 轨道 + 白色圆点滑块，支持点按与横向拖动 seek（小窗/全屏统一风格） */
@Composable
private fun LineSlider(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 拖动中的临时进度（null=未拖动），松手才真正 seek，避免拖动过程中频繁跳转
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    BoxWithConstraints(
        modifier = modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragProgress = (offset.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragProgress?.let(onSeek)
                        dragProgress = null
                    },
                    onDragCancel = { dragProgress = null },
                ) { change, _ ->
                    dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        val p = (dragProgress ?: progress).coerceIn(0f, 1f)
        val width = maxWidth
        // 背景轨道
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0x44FFFFFF), CircleShape),
        )
        // 已播放轨道
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(p)
                .height(2.dp)
                .background(Color.White, CircleShape),
        )
        // 圆点滑块
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = width * p - 5.dp)
                .size(10.dp)
                .background(Color.White, CircleShape),
        )
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0x" else "${speed}x"

private const val RESUME_THRESHOLD_MS = 5_000L
private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L

// ========== 视频加载失败全屏错误态 ==========

/**
 * 当 VideoRepository.getVideo + Players.consumeCachedVideo 都查不到时，
 * 渲染这个全屏错误界面替代残缺的 PortraitPlayLayout（纯白底+只有控制按钮）。
 */
@Composable
private fun PlayerErrorScreen(
    videoId: String,
    playerError: String?,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = "视频加载失败",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = when {
                    playerError != null -> "播放器错误：$playerError"
                    else -> "未找到该视频（ID: $videoId），请返回重试或检查站源是否可用"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onBack() }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "返回",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
private const val HIDE_DELAY_MS = 4_000L
