package com.ican.tvplay.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.siteShortName
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.Spacing
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
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
    // 控制层交互计数：动作栏按钮点击时自增，重置 5 秒自动隐藏计时（fongmi setR1Callback 同款）
    var controlsTick by remember { mutableIntStateOf(0) }
    // 正在拖动进度条（小窗/全屏共用）：拖动期间挂起自动隐藏计时，松手后重新计 5 秒
    var seekDragging by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    // 全屏挂起状态（fongmi 同款时序：先请求系统旋转，旋转完成后再切换布局）
    // true=等待旋转进全屏 false=等待旋转退全屏 null=无挂起，
    // 避免"竖屏先铺满再旋转"的割裂感
    var pendingFullscreen by remember { mutableStateOf<Boolean?>(null) }
    // 平板已处于横屏时进入全屏：不会触发方向配置变化，无需等待旋转，退出时也不请求旋转
    var landscapeFullscreen by remember { mutableStateOf(false) }
    // 横屏小窗↔全屏放大/缩回动画进行中：期间只渲染纯视频，不挂任何覆盖层
    var landZooming by remember { mutableStateOf(false) }
    // 缩放会话令牌：每次进入/退出全屏自增，作为收敛监听 LaunchedEffect 的 key，
    // 避免协程体内把 landZooming 置 false 导致自身被取消、渐显逻辑丢失
    var landZoomToken by remember { mutableIntStateOf(0) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(isLandscape) {
        when {
            // 旋转进入/退出全屏后都保持纯净画面，工具栏由用户单击唤出
            pendingFullscreen == true && isLandscape -> {
                fullscreen = true; pendingFullscreen = null; controlsVisible = false
            }
            pendingFullscreen == false && !isLandscape -> {
                fullscreen = false; pendingFullscreen = null; controlsVisible = false
            }
        }
    }

    // 共享 PlayerView（fongmi 同思路：全屏/竖屏只是改容器尺寸，PlayerView 从不销毁重建）。
    // 常驻唯一 AndroidView，竖屏↔全屏↔画中画切换时 Surface 不销毁，消除切换黑屏
    val sharedPlayerView = remember {
        PlayerView(context).apply {
            useController = false
            Players.bindPlayerView(this)
        }
    }
    DisposableEffect(Unit) {
        onDispose { Players.unbindPlayerView(sharedPlayerView) }
    }

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

    // 解析并播放（video 变化也触发：自动换站源后整片替换重解析）
    LaunchedEffect(initialized, video, episodeIndex, lineFlag) {
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
        try {
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
            // start 内部已兜底同步异常；这里再包一层防止 spider/jx 解析或上下文构造异常导致闪退
            Players.start(
                context = context,
                spec = spec,
                startPositionMs = pos,
            )
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            android.util.Log.e("PlayerScreen", "resolve/play fail: ${v.id} ep=$episodeIndex", t)
            Players.notifyError("播放地址解析失败，该站源可能不支持此影片，请切换线路或站源重试")
        }
    }

    // ---- 播放失败自动切换（fongmi VodFallbackPolicy：先换线路，再换站源） ----
    // 已失败的线路/站点：本次播放会话内不再重试，避免循环
    val failedFlags = remember(videoId) { mutableStateOf(setOf<String>()) }
    val failedSiteKeys = remember(videoId) { mutableStateOf(setOf<String>()) }
    var fallbackInProgress by remember { mutableStateOf(false) }

    fun toastMsg(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(playerState.error) {
        if (playerState.error == null || fallbackInProgress) return@LaunchedEffect
        val v = video ?: return@LaunchedEffect
        // 当前站点（跨站源缓存优先，兜底首页当前站）
        val cached = Players.cachedVideo?.takeIf { it.video.id == v.id }
        val curSite = cached?.site ?: container.videoRepository.currentSite.value?.site
        // 当前站源未开「自动切换站源」→ 不兜底，走原错误提示
        if (curSite == null || !container.settingsRepository.isSiteChangeable(curSite)) {
            return@LaunchedEffect
        }

        // 1) 换下一条线路（同站源内）
        val curFlag = playLine?.flag ?: v.playFrom
        val flags = v.playSources.map { it.flag }
        if (flags.size > 1) {
            val nextFlag = flags.dropWhile { it != curFlag }.drop(1)
                .firstOrNull { it !in failedFlags.value }
            if (nextFlag != null) {
                failedFlags.value = failedFlags.value + curFlag
                toastMsg("播放失败，自动切换线路：$nextFlag")
                lineFlag = nextFlag
                return@LaunchedEffect
            }
        }

        // 2) 换站源：跨站搜同名影片（仅「参与搜索 + 允许换源」的站点）
        fallbackInProgress = true
        try {
            val curKey = cached?.siteKey ?: curSite.key
            val candidate = container.videoRepository.searchFallbackCandidate(
                title = v.title,
                excludeSiteKeys = failedSiteKeys.value + curKey,
            )
            if (candidate != null) {
                failedSiteKeys.value = failedSiteKeys.value + curKey
                failedFlags.value = emptySet()
                val newVideo = candidate.video
                toastMsg("播放失败，自动切换站源：${siteShortName(candidate.site.name, candidate.site.key)}")
                // 缓存新站点信息：解析 LaunchedEffect 据此走新站的 playerContent 延迟解析
                Players.cacheVideo(newVideo, candidate.site.key, candidate.site, candidate.jarSpec)
                val newEps = newVideo.playSources.firstOrNull()?.episodes ?: newVideo.episodes
                episodeIndex = episodeIndex.coerceIn(0, (newEps.size - 1).coerceAtLeast(0))
                lineFlag = ""
                video = newVideo
            }
            // 无候选：保留原错误提示（error 未被清除，effect 不会重复触发）
        } finally {
            fallbackInProgress = false
        }
    }

    fun saveProgress() {
        val v = video ?: return
        val episode = lineEpisodes.getOrNull(episodeIndex) ?: return
        val st = Players.state.value
        // 同步片头/片尾标记到播放上下文（后台切集/通知栏用）
        Players.playbackContext?.takeIf { it.video.id == v.id }?.let {
            Players.playbackContext = it.copy(openingMs = openingMs, endingMs = endingMs)
        }
        // 必须走应用级 scope：onDispose（退出页面/切后台）时 rememberCoroutineScope 已取消，
        // 用它 launch 会导致最后一次历史保存被静默丢弃（历史页空记录的根因）
        container.appScope.launch {
            runCatching {
                // 记录视频所在站源：点击历史时按此站源拉详情并续播（与收藏同款）
                val cached = Players.cachedVideo
                val siteKey = cached?.takeIf { it.video.id == v.id }?.siteKey ?: ""
                val siteName = v.sourceName.ifBlank {
                    com.ican.tvplay.ui.components.siteShortName(
                        cached?.takeIf { it.video.id == v.id }?.site?.name.orEmpty(),
                        siteKey,
                    )
                }
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
                        sourceKey = siteKey,
                        sourceName = siteName,
                    ),
                )
            }.onFailure {
                android.util.Log.e("PlayerScreen", "history upsert failed: ${v.id} ep=$episodeIndex", it)
            }
        }
    }

    // ========== 片头/片尾标记（fongmi: onOpening/onEnding 同款规则） ==========

    /** 标记允许的时限：视频 <15 分钟→3 分钟；<30 分钟→6 分钟；否则→10 分钟 */
    fun opEdLimit(duration: Long): Long = when {
        duration < 15 * 60_000L -> 3 * 60_000L
        duration < 30 * 60_000L -> 6 * 60_000L
        else -> 10 * 60_000L
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
        if (st.durationMs > 0 && st.positionMs > 0 && remain in 1..limit) {
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

    // 开播即落一条历史（进度可为 0）：防止停留不足一个保存周期就退出导致历史页无记录。
    // 每集只锚定一次；saveProgress 内部走应用级 scope，composition 销毁也能写完。
    var historyAnchorKey by remember { mutableStateOf("") }
    LaunchedEffect(initialized, video?.id, episodeIndex, lineFlag, playerState.playing) {
        val v = video
        if (initialized && v != null && playerState.playing) {
            val key = "${v.id}#$episodeIndex#${lineFlag}"
            if (historyAnchorKey != key) {
                historyAnchorKey = key
                saveProgress()
            }
        }
    }

    // 自动隐藏控制层（fongmi 同款：显示后 5 秒无操作隐藏，交互重置计时）
    // seekDragging 也是 key：拖动开始即挂起倒计时，松手(false)使本 effect 重启重新计 5 秒
    LaunchedEffect(controlsVisible, locked, playerState.playing, controlsTick, seekDragging) {
        if (!controlsVisible || locked || seekDragging) return@LaunchedEffect
        if (playerState.playing) {
            delay(HIDE_DELAY_MS)
            if (playerState.playing && !locked) controlsVisible = false
        }
    }

    // 全屏/放大动画期间自动收起状态栏，缩回小窗收敛后恢复（旋转请求在挂起触发处先行发出）
    val immersive = fullscreen || landscapeFullscreen || landZooming
    LaunchedEffect(immersive) {
        Players.isFullscreen = immersive
        val activity = context as? Activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (immersive) {
            // 沉浸式：隐藏状态栏，从边缘上滑可临时唤出
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
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
        // 监听 Activity 生命周期：切后台自动 pause（仅后台播放关闭时），回前台 resume
        val activity = context as? android.app.Activity
        val lifecycle = (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                // PiP 画中画中不暂停（小窗继续播）；
                // 后台播放开启/画中画模式下退后台也不暂停——否则已暂停的 mediaPlayback
                // 前台服务会被系统（尤其 MIUI）立即回收，进程被杀，表现为"退后台直接死掉"
                if (activity?.isInPictureInPictureMode != true && backgroundMode == BACKGROUND_OFF) {
                    Players.onPause()
                }
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
            if (landscapeFullscreen) {
                // 平板横屏直进的全屏：立即卸载全屏覆盖层，PlayerView 执行缩回动画，
                // 动画期间只有纯视频，结束后恢复横版小窗（同样不弹工具栏）
                fullscreen = false
                controlsVisible = false
                landscapeFullscreen = false
                landZooming = true
                landZoomToken++
            } else {
                // 先请求竖屏旋转，转完再退全屏布局（与进全屏对称）
                (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                pendingFullscreen = false
            }
            return
        }
        // 放大/缩回动画进行中忽略返回，避免动画中途退出页面
        if (landZooming) return
        // release / 后台续播转交由 onDispose 统一处理（popBackStack 后触发）
        onBack()
    }
    // 仅在页面需要自己处理返回时拦截（退全屏 / 缩放动画中忽略）；
    // 竖屏小窗状态下不拦截，交还 NavDisplay → 预测返回动画 + 边缘滑动返回生效
    BackHandler(enabled = fullscreen || landZooming) { handleBack() }

    // PiP 画中画：窗口是整个 Activity 缩小画面，只渲染纯视频（隐藏全部 UI）
    if (inPip) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { sharedPlayerView },
                update = { view ->
                    if (view.player !== Players.player) Players.bindPlayerView(view)
                },
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

    // 根：视频 View 常驻最底层，不随布局切换重建（Surface 不销毁 → 切换无黑屏），
    // 竖屏控制层/信息流、全屏控制层作为覆盖层画在其上。
    // BoxWithConstraints 提供整屏尺寸，供横屏小窗→全屏放大动画插值。
    // 旋转过渡期 / 放大动画期间为黑底；缩回后柔和渐变回主题背景，避免硬切
    val rootBackground by animateColorAsState(
        targetValue = if (fullscreen || pendingFullscreen != null ||
            landscapeFullscreen || landZooming
        ) {
            Color.Black
        } else {
            MaterialTheme.colorScheme.background
        },
        animationSpec = tween(LAND_ZOOM_DURATION_MS),
        label = "rootBackground",
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(rootBackground)
            // 横屏缩放时信息列/下方区块滑出屏幕的部分需要裁剪（等价 ViewGroup 默认 clipChildren）
            .clipToBounds(),
    ) {
        val density = LocalDensity.current
        val screenW = maxWidth
        val screenH = maxHeight
        // 状态栏高度：取进入页面时的固定值。放大后系统会隐藏状态栏，实时 inset 会归零，
        // 若用实时值会导致缩回终点 y=0、收敛恢复状态栏时画面跳一下
        val statusBarInsets = WindowInsets.statusBars
        val statusBarTop = remember(density) {
            with(density) { statusBarInsets.getTop(this).toDp() }
        }

        // 横屏小窗↔全屏放大/缩回过渡：宽/高/顶部偏移连续插值，
        // PlayerView 全程不重建，只有画面在放大，不挂任何控制层
        val expandTransition = updateTransition(
            targetState = landscapeFullscreen,
            label = "landPlayerExpand",
        )
        val animWidth by expandTransition.animateDp(
            transitionSpec = { tween(LAND_ZOOM_DURATION_MS, easing = FastOutSlowInEasing) },
            label = "animWidth",
        ) { expanded ->
            if (expanded) screenW else screenW * LAND_PLAYER_WIDTH_FRACTION
        }
        val animHeight by expandTransition.animateDp(
            transitionSpec = { tween(LAND_ZOOM_DURATION_MS, easing = FastOutSlowInEasing) },
            label = "animHeight",
        ) { expanded ->
            if (expanded) screenH
            else screenW * LAND_PLAYER_WIDTH_FRACTION * (9f / 16f)
        }
        val animTop by expandTransition.animateDp(
            transitionSpec = { tween(LAND_ZOOM_DURATION_MS, easing = FastOutSlowInEasing) },
            label = "animTop",
        ) { expanded -> if (expanded) 0.dp else statusBarTop }
        // 横屏小窗左缘：对齐页面左基线（手机 16dp / 平板 24dp），放大到全屏时收回到 0
        val pageHorizontal = pageHorizontalPadding()
        val animLeft by expandTransition.animateDp(
            transitionSpec = { tween(LAND_ZOOM_DURATION_MS, easing = FastOutSlowInEasing) },
            label = "animLeft",
        ) { expanded -> if (expanded) 0.dp else pageHorizontal }
        // 小窗圆角半径（方案 B 遮罩用）：小窗态 16dp，放大到全屏同步归零 → 缺口尺寸为 0 自动消失
        val animCorner by expandTransition.animateDp(
            transitionSpec = { tween(LAND_ZOOM_DURATION_MS, easing = FastOutSlowInEasing) },
            label = "animCorner",
        ) { expanded -> if (expanded) 0.dp else Spacing.corner }

        // 动画收敛后处理：放大结束挂全屏布局（纯净画面起手）；
        // 缩回期间横版布局全程渲染，信息列/下方区块锚定视频边缘跟随滑入滑出（fongmi ChangeBounds）
        LaunchedEffect(landZoomToken) {
            if (landZooming) {
                snapshotFlow {
                    !expandTransition.isRunning &&
                        expandTransition.currentState == expandTransition.targetState
                }.first { it }
                if (landscapeFullscreen) {
                    // 放大完成：挂全屏布局，工具栏默认隐藏
                    landZooming = false
                    fullscreen = true
                    controlsVisible = false
                } else {
                    // 缩回完成：恢复方向自由并显示状态栏（组件位置均由动画值驱动，无 inset 跳变）
                    controlsVisible = false
                    val activity = context as? Activity
                    activity?.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    if (activity != null) {
                        WindowCompat.getInsetsController(
                            activity.window, activity.window.decorView,
                        ).show(WindowInsetsCompat.Type.statusBars())
                    }
                    landZooming = false
                }
            }
        }

        AndroidView(
            factory = { sharedPlayerView },
            update = { view ->
                view.resizeMode = playerState.scaleMode.resizeMode
                if (view.player !== Players.player) Players.bindPlayerView(view)
            },
            modifier = when {
                // 手机旋转进入的全屏：直接铺满
                fullscreen && !landscapeFullscreen -> Modifier.fillMaxSize()
                // 旋转挂起期间统一沿用竖屏尺寸，避免转屏中途露出横版或画面拉伸
                // （必须排在 isLandscape 前：旋转中途配置已翻为横屏但挂起仍未结束）
                pendingFullscreen != null ->
                    Modifier.fillMaxWidth().statusBarsPadding().aspectRatio(16f / 9f)
                // 自然横屏（平板）：小窗静止态、放大/缩回动画中、已放大全屏
                // 全程使用同一套 offset/宽高修饰符（静止值恰为 38% 小窗），
                // 不在动画修饰符与 statusBarsPadding 静态修饰符之间切换，避免收敛瞬间上跳
                isLandscape ->
                    Modifier
                        .offset(x = animLeft, y = animTop)
                        .width(animWidth)
                        .height(animHeight)
                else ->
                    Modifier.fillMaxWidth().statusBarsPadding().aspectRatio(16f / 9f)
            }.background(Color.Black),
        )
        when {
        fullscreen -> {
        FullscreenPlayLayout(
            current = current,
            episodeIndex = episodeIndex,
            lineEpisodes = lineEpisodes,
            playerState = playerState,
            controlsVisible = controlsVisible,
            locked = locked,
            onToggleControls = { controlsVisible = !controlsVisible },
            onControlInteract = { controlsTick++ },
            onTogglePlay = { Players.playPause() },
            onPrev = { if (episodeIndex > 0) episodeIndex-- },
            onNext = { if (episodeIndex < lineEpisodes.size - 1) episodeIndex++ },
            onExitFullscreen = { handleBack() },
            onLockToggle = { locked = true; controlsVisible = false },
            onUnlock = { locked = false; controlsVisible = true },
            onBack = { handleBack() },
            onSpeedChange = { settings.setPlayerSpeed(it) },
            playbackSpeed = settings.playerSpeed.collectAsStateWithLifecycle().value,
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
            onSeekDragChange = { seekDragging = it },
        )
        }
        // 进全屏旋转过渡期：仅黑底，不渲染任何非全屏布局（白底信息流会露出来）
        pendingFullscreen == true -> Unit
        else -> if (pendingFullscreen == null && isLandscape) {
            // 自然横屏（平板）：fongmi activity_detail_land 横版布局，
            // 信息列/下方区块锚定动画中的播放器边界，随缩放一起滑出滑入（ChangeBounds 效果）
            LandscapePlayLayout(
                current = current,
                episodeIndex = episodeIndex,
                lineEpisodes = lineEpisodes,
                lineFlag = lineFlag,
                playerState = playerState,
                controlsVisible = controlsVisible,
                locked = locked,
                playerTop = animTop,
                playerLeft = animLeft,
                playerWidth = animWidth,
                playerHeight = animHeight,
                smallPlayerWidth = screenW * LAND_PLAYER_WIDTH_FRACTION,
                screenWidth = screenW,
                screenHeight = screenH,
                pageHorizontal = pageHorizontal,
                playerCorner = animCorner,
                onToggleControls = { controlsVisible = !controlsVisible },
                onTogglePlay = { Players.playPause() },
                onPrev = { if (episodeIndex > 0) episodeIndex-- },
                onNext = { if (episodeIndex < lineEpisodes.size - 1) episodeIndex++ },
                onEnterFullscreen = {
                    // 已在横屏：无旋转事件，启动小窗→全屏放大动画，
                    // 动画结束（LaunchedEffect）后再挂全屏布局且工具栏默认隐藏
                    controlsTick++
                    controlsVisible = false
                    (context as? Activity)?.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    landscapeFullscreen = true
                    landZooming = true
                    landZoomToken++
                },
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
                onSeekDragChange = { seekDragging = it },
            )
        } else {
        // 竖屏手机 / 退全屏旋转过渡期：统一竖屏布局
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
            onEnterFullscreen = {
                controlsTick++
                if (isLandscape) {
                    // 平板已横屏：不会触发 Configuration 变化，直接切全屏布局
                    landscapeFullscreen = true
                    fullscreen = true
                } else {
                    // 先请求横屏，旋转完成（Configuration 变化）后再铺满全屏
                    landscapeFullscreen = false
                    (context as? Activity)?.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    pendingFullscreen = true
                }
            },
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
            onSeekDragChange = { seekDragging = it },
        )
        }
        }
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
    onSeekDragChange: (Boolean) -> Unit,
) {
    var showSpeed by remember { mutableStateOf(false) }
    var showScale by remember { mutableStateOf(false) }
    var showDecode by remember { mutableStateOf(false) }
    var showBuffer by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showAddSub by remember { mutableStateOf(false) }

    // 单一 Column 根：NavHost 目的地容器会给多个顶层子元素传播 fill 约束，
    // 多顶层发射会让底部标签栏被拉伸成全屏、白色背景盖住视频区（"纯白底+只有按钮"的根因）
    Column(
        modifier = Modifier
            .fillMaxSize(),
        // 背景由根 Box 提供（Column 若带背景会盖住常驻 PlayerView）
    ) {

    // === 视频播放区占位（PlayerView 常驻于根 Box 最底层，此处仅放控制层/加载/错误覆盖层） ===
    PreviewPlayerArea(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .aspectRatio(16f / 9f),
        playerState = playerState,
        controlsVisible = controlsVisible,
        locked = locked,
        onToggleControls = onToggleControls,
        onBack = onBack,
        onTogglePlay = onTogglePlay,
        onPrev = onPrev,
        onNext = onNext,
        onEnterFullscreen = onEnterFullscreen,
        onSeekDragChange = onSeekDragChange,
    )

    // === 可滚动详情信息流（左右边距对齐全局规范；板块主间距统一 sectionSpacing） ===
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = pageHorizontalPadding(), vertical = Spacing.titleContentGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
    ) {
        val v = current ?: return@Column

        // 标题+收藏 / 更新备注 / 站源 / 年份·地区·类型（标题↔信息行的次间距在组件内部）
        VideoMetaSection(v = v, lineFlag = lineFlag)

        // 线路（标题↔chips 的次间距在组件内部）
        LineSection(v = v, lineFlag = lineFlag, onLineChange = onLineChange)

        // 选集（倒序/更多，横滑↔网格；标题↔按钮的次间距在组件内部）
        EpisodeSection(
            lineEpisodes = lineEpisodes,
            episodeIndex = episodeIndex,
            onEpisodeSelect = onEpisodeSelect,
        )

        // 简介（标题↔正文的次间距在组件内部）
        DescriptionSection(v = v)

        Spacer(modifier = Modifier.height(60.dp))
    }

    // === 对话框 ===
    if (showSpeed) {
        OptionDialog("倍速", listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 3.0f), playerState.speed, { formatSpeed(it) }, { onSpeedChange(it); showSpeed = false }) { showSpeed = false }
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

// ========== 横屏（平板）布局：对齐 fongmi activity_detail_land ==========

/** 横版播放器占屏宽比例（fongmi 38:62） */
private const val LAND_PLAYER_WIDTH_FRACTION = 0.38f

/** 横屏小窗↔全屏放大/缩回动画时长（对齐 fongmi ChangeBounds 150ms） */
private const val LAND_ZOOM_DURATION_MS = 150

/**
 * 横版布局（fongmi activity_detail_land + ChangeBounds 联动）：
 * 全部元素以动画中的播放器边界为锚绝对定位——信息列锚视频右缘（放大时被推出屏幕右侧），
 * 线路/选集/简介锚视频下缘（被推出屏幕下缘），缩放全程跟随，无渐隐渐显切换。
 * 组件始终渲染，超出屏幕的部分由根容器 clipToBounds 裁剪。
 */
@Composable
private fun LandscapePlayLayout(
    current: Video?,
    episodeIndex: Int,
    lineEpisodes: List<Episode>,
    lineFlag: String,
    playerState: Players.PlayerState,
    controlsVisible: Boolean,
    locked: Boolean,
    // 动画中的播放器边界（小窗静止值 = 38% 宽 16:9 + 状态栏顶部偏移 + 左缘对齐页面左基线）
    playerTop: Dp,
    playerLeft: Dp,
    playerWidth: Dp,
    playerHeight: Dp,
    smallPlayerWidth: Dp,
    screenWidth: Dp,
    screenHeight: Dp,
    pageHorizontal: Dp,
    /** 小窗圆角半径（遮罩方案，随放大动画归零） */
    playerCorner: Dp,
    onToggleControls: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onBack: () -> Unit,
    onLineChange: (String) -> Unit,
    onEpisodeSelect: (Int) -> Unit,
    onSeekDragChange: (Boolean) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 小窗圆角遮罩：先画（层级低于控制层），只覆盖视频四角，不拦截触摸
        PlayerCornerMask(
            left = playerLeft,
            top = playerTop,
            width = playerWidth,
            height = playerHeight,
            corner = playerCorner,
        )

        // 播放器覆盖层：与底层常驻 PlayerView 同位同尺寸（尺寸由根 AndroidView 动画值驱动）
        PreviewPlayerArea(
            modifier = Modifier
                .offset(x = playerLeft, y = playerTop)
                .width(playerWidth)
                .height(playerHeight)
                // 控制层随小窗一起裁成圆角：否则底部渐变条（半透明黑压浅色底 = 浅灰）
                // 的方角会露在圆角缺口里，形成左右下角的浅灰色直角块
                .clip(RoundedCornerShape(playerCorner)),
            playerState = playerState,
            controlsVisible = controlsVisible,
            locked = locked,
            onToggleControls = onToggleControls,
            onBack = onBack,
            onTogglePlay = onTogglePlay,
            onPrev = onPrev,
            onNext = onNext,
            onEnterFullscreen = onEnterFullscreen,
            onSeekDragChange = onSeekDragChange,
        )

        // 右侧信息列：锚在视频右缘，宽度固定（小窗时的剩余宽度），随放大向右滑出屏幕
        Column(
            modifier = Modifier
                .offset(x = playerLeft + playerWidth, y = playerTop)
                .width(screenWidth - smallPlayerWidth - playerLeft)
                .padding(
                    start = com.ican.tvplay.ui.theme.Spacing.sectionSpacing,
                    end = pageHorizontal,
                    top = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (current != null) {
                VideoMetaSection(v = current, lineFlag = lineFlag, itemSpacing = 10.dp)
            }
        }

        // 下方区块：锚在视频下缘，高度为视频下方的剩余空间（放大时随视频增高收缩到 0）
        Column(
            modifier = Modifier
                .offset(y = playerTop + playerHeight)
                .fillMaxWidth()
                .height(screenHeight - playerTop - playerHeight)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = pageHorizontal,
                    end = pageHorizontal,
                    top = com.ican.tvplay.ui.theme.Spacing.titleContentGap,
                ),
            // 板块主间距与竖屏信息流统一为 sectionSpacing
            verticalArrangement = Arrangement.spacedBy(com.ican.tvplay.ui.theme.Spacing.sectionSpacing),
        ) {
            if (current != null) {
                LineSection(v = current, lineFlag = lineFlag, onLineChange = onLineChange)
                EpisodeSection(
                    lineEpisodes = lineEpisodes,
                    episodeIndex = episodeIndex,
                    onEpisodeSelect = onEpisodeSelect,
                )
                DescriptionSection(v = current)

                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

/**
 * 小窗圆角遮罩（方案 B）：视频由 SurfaceView 渲染、画面由系统独立合成，Compose 无法裁剪它，
 * 因此在视频上方用「与页面底色同色的 4 个圆角缺口」把四个直角盖掉，视觉上等效 [Spacing.corner] 圆角。
 *
 * - 只覆盖四角那一小块，不带任何 pointerInput，不拦截小窗控制层的触摸
 * - [corner] 随放大动画归零（缺口尺寸为 0）→ 全屏时自动消失，无需额外显隐逻辑
 */
@Composable
private fun PlayerCornerMask(
    left: Dp,
    top: Dp,
    width: Dp,
    height: Dp,
    corner: Dp,
) {
    if (corner <= 0.dp) return
    val color = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.offset(x = left, y = top).size(width, height)) {
        CornerNotch(color, corner, Alignment.TopStart)
        CornerNotch(color, corner, Alignment.TopEnd)
        CornerNotch(color, corner, Alignment.BottomStart)
        CornerNotch(color, corner, Alignment.BottomEnd)
    }
}

/**
 * 单个 r×r 圆角缺口：填充「方形 − 四分之一圆」，
 * 圆心取方块中靠近视频中心的那一角（由 [alignment] 决定朝向）。
 */
@Composable
private fun BoxScope.CornerNotch(color: Color, r: Dp, alignment: Alignment) {
    val mirrorX = alignment == Alignment.TopEnd || alignment == Alignment.BottomEnd
    val mirrorY = alignment == Alignment.BottomStart || alignment == Alignment.BottomEnd
    Box(
        modifier = Modifier
            .align(alignment)
            .size(r)
            .drawWithCache {
                val s = size.minDimension
                // 基准路径：圆心在方块右下角（即左上角缺口）——左边 → 四分之一弧 → 回原点的直角区
                val base = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(0f, s)
                    arcTo(Rect(Offset(s, s), s), 180f, 90f, false)
                    close()
                }
                onDrawBehind {
                    withTransform(
                        {
                            scale(
                                scaleX = if (mirrorX) -1f else 1f,
                                scaleY = if (mirrorY) -1f else 1f,
                                pivot = center,
                            )
                        },
                    ) {
                        drawPath(base, color)
                    }
                }
            },
    )
}

// ========== 非全屏播放器区与信息区块（竖屏 / 横屏共用） ==========

/**
 * 非全屏播放器区域（竖屏通栏 16:9 / 横屏左侧 38% 共用）：
 * PlayerView 常驻根 Box 最底层、从不销毁重建，这里只承载小窗控制层/加载/错误覆盖层，
 * 区域尺寸完全由 [modifier] 决定，保证覆盖层与底层 PlayerView 像素对齐。
 */
@Composable
private fun PreviewPlayerArea(
    modifier: Modifier,
    playerState: Players.PlayerState,
    controlsVisible: Boolean,
    locked: Boolean,
    onToggleControls: () -> Unit,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onSeekDragChange: (Boolean) -> Unit,
) {
    Box(
        modifier = modifier.pointerInput(locked) {
            // 与全屏手势层同款判定：单击不等待双击超时、立即响应；
            // 单击 = 显示/隐藏控制层（原行为不变），双击 = 进入全屏（替代原右下角全屏按钮）；
            // 移动过或点在控制层按钮等已消费事件的手势全程让位
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downX = down.position.x
                val downY = down.position.y
                var moved = false
                var consumedByChild = false
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.isConsumed }) consumedByChild = true
                    if (event.changes.all { !it.pressed }) break
                    val c = event.changes.firstOrNull() ?: continue
                    if (hypot(c.position.x - downX, c.position.y - downY) >
                        viewConfiguration.touchSlop
                    ) {
                        moved = true
                    }
                }
                if (moved || consumedByChild) return@awaitEachGesture
                val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                    awaitFirstDown(requireUnconsumed = false)
                }
                if (second != null) {
                    // 双击：等第二次抬起后进入全屏（锁定时不响应）
                    while (true) {
                        val e2 = awaitPointerEvent()
                        if (e2.changes.all { !it.pressed }) break
                    }
                    if (!locked) onEnterFullscreen()
                } else {
                    onToggleControls()
                }
            }
        },
    ) {
        // 预览小窗控制层：单击视频区显示/隐藏（fongmi 同款交互）
        // 返回键随控制层显隐（fongmi 同款：控制层隐藏时返回键一起消失），48dp 纯图标无背景
        if (controlsVisible && !locked) {
            // 控制层不再自带 clickable：空白处的单击/双击统一由外层手势层处理，
            // 只有按钮（已消费事件）才让位，保证控制层显示时双击同样能进全屏
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶栏：返回（左） + 投屏（右），fongmi 同款 48dp 纯图标，无圆形底色
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp),
                ) {
                    PlainTopBtn(AppIcons.Back, "返回") { onBack() }
                    Spacer(modifier = Modifier.weight(1f))
                    PlainTopBtn(AppIcons.Cast, "投屏") { }
                }

                // 中部：上一集 / 播放暂停 / 下一集（fongmi: play 与两侧各 40dp 间距）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
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

                // 底部：进度条（fongmi: PlayerSeekView 左右 16dp padding，容器 paddingBottom 8dp）
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
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                ) {
                    Text(text = formatMs(playerState.positionMs), style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"), color = Color.White)
                    LineSlider(
                        progress = if (playerState.durationMs > 0) playerState.positionMs.toFloat() / playerState.durationMs else 0f,
                        onSeek = { Players.seekTo((it * playerState.durationMs).toLong()) },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        onDragFractionChange = { onSeekDragChange(it != null) },
                    )
                    Text(text = formatMs(playerState.durationMs), style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"), color = Color.White)
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

        // 播放/解析错误轻量提示（替代黑屏卡死；自动换源失败后停留在此提示）
        if (playerState.error != null && !playerState.buffering) {
            PlayerErrorOverlay(
                message = playerState.error!!,
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * 影片基础信息区块（竖屏信息流 / 横屏右侧信息列共用）：
 * 标题+收藏 / 更新备注 / 站源 / 年份·地区·类型
 */
@Composable
private fun VideoMetaSection(
    v: Video,
    lineFlag: String,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 6.dp,
) {
    Column(modifier = modifier) {
        // 标题 + 收藏按钮（收藏入口在标题右侧，圆钮尺寸/样式不变）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = v.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            FavoriteButton(v = v)
        }

        // 标题 ↔ 自身信息内容：统一次间距
        Spacer(modifier = Modifier.height(Spacing.titleContentGap))

        // 更新备注/站源/年份/地区/类型：信息行之间保持行内密度 itemSpacing
        Column(verticalArrangement = Arrangement.spacedBy(itemSpacing)) {
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
                text = "站源：${v.sourceName.takeIf { it.isNotBlank() } ?: lineFlag.substringBefore("|")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 年份/地区/类型各占一行（fongmi: other）
            if (v.year > 0) {
                Text(
                    text = "年份：${v.year}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (v.region.isNotBlank()) {
                Text(
                    text = "地区：${v.region}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (v.categoryName.isNotBlank()) {
                Text(
                    text = "类型：${v.categoryName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 标题行收藏按钮（竖屏/横屏共用）：裸图标无圆形底色，48dp 点击热区与原圆钮一致，
 * 已收藏心形粉色，点击切换收藏并携带站源信息 */
@Composable
private fun FavoriteButton(v: Video) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var isFav by remember { mutableStateOf(false) }

    LaunchedEffect(v.id) {
        isFav = container.favoriteDao.isFavorite(v.id)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Spacing.circleButton)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    val newFav = !isFav
                    isFav = newFav
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        if (newFav) {
                            // 记录视频所在站源：点击收藏时按此站源拉详情并播放
                            val cached = Players.cachedVideo
                            val favSiteKey = cached?.siteKey ?: ""
                            val favSiteName = v.sourceName.ifBlank {
                                com.ican.tvplay.ui.components.siteShortName(
                                    cached?.site?.name.orEmpty(),
                                    favSiteKey,
                                )
                            }
                            container.favoriteDao.upsert(
                                FavoriteEntity(
                                    videoId = v.id,
                                    title = v.title,
                                    cover = v.cover,
                                    categoryName = v.categoryName,
                                    addedAt = System.currentTimeMillis(),
                                    sourceKey = favSiteKey,
                                    sourceName = favSiteName,
                                ),
                            )
                        } else {
                            container.favoriteDao.delete(v.id)
                        }
                    }
                },
            ),
    ) {
        Icon(
            imageVector = if (isFav) AppIcons.Favorite else AppIcons.FavoriteBorder,
            contentDescription = if (isFav) "取消收藏" else "收藏",
            tint = if (isFav) Color(0xFFFF5C8A) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 线路区块（fongmi: detail_flag）：横向滚动 chips */
@Composable
private fun LineSection(
    v: Video,
    lineFlag: String,
    onLineChange: (String) -> Unit,
) {
    Column {
        Text(
            text = "线路",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(Spacing.titleContentGap))
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
    }
}

/** 选集区块（fongmi: detail_episode）：标题+倒序/正序+更多；默认横向滚动，点更多切 FlowRow 网格 */
@Composable
private fun EpisodeSection(
    lineEpisodes: List<Episode>,
    episodeIndex: Int,
    onEpisodeSelect: (Int) -> Unit,
) {
    var showAllEps by remember { mutableStateOf(false) }
    var reversedEp by remember { mutableStateOf(false) }

    // 单根 Column：对外只占外层信息流一个板块，板块主间距由外层统一；
    // 标题 ↔ 按钮为组件内部次间距，不被外层 spacedBy 影响
    Column {
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
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { reversedEp = !reversedEp },
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (showAllEps) "收起 ▾" else "更多 ▸",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showAllEps = !showAllEps },
            )
        }
        Spacer(modifier = Modifier.height(Spacing.titleContentGap))

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
    }
}

/** 简介区块：直接全部显示，不折叠、不可点击 */
@Composable
private fun DescriptionSection(v: Video) {
    Column {
        Text(
            text = "简介",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(Spacing.titleContentGap))
        Text(
            text = v.description.ifBlank { "暂无简介" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    onControlInteract: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExitFullscreen: () -> Unit,
    onLockToggle: () -> Unit,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    playbackSpeed: Float,
    onScaleChange: (Players.ScaleMode) -> Unit,
    onDecodeChange: (Players.Decode) -> Unit,
    onBufferChange: (Players.BufferTier) -> Unit,
    openingMs: Long,
    endingMs: Long,
    onMarkOpening: () -> Unit,
    onMarkEnding: () -> Unit,
    onClearOpening: () -> Unit,
    onClearEnding: () -> Unit,
    onSeekDragChange: (Boolean) -> Unit,
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
    var showBuffer by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showAddSub by remember { mutableStateOf(false) }
    // fongmi 同款手势反馈浮层：拖动 seek 预览目标 / 长按倍速提示
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var speedUpHint by remember { mutableStateOf(false) }
    // 拖动底部进度条时的实时目标比例：顶部居中显示「跳转至 MM:SS」，松手消失
    var sliderSeekFraction by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(locked) {
        if (locked) {
            seekPreview = null
            speedUpHint = false
            sliderSeekFraction = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 背景由根 Box 提供（此处若带背景会盖住常驻 PlayerView）
            .pointerInput(locked) {
                // fongmi CustomKeyDown 同款手势：单击显隐控制层、双击播放暂停、长按 2 倍速快进、横向拖动快进快退
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = down.uptimeMillis
                    val downX = down.position.x
                    val downY = down.position.y
                    var axis = 0 // 0 未定 | 1 横向seek | 2 纵向/放弃
                    var isLong = false
                    var seekTarget: Long? = null
                    // 点在控制层按钮等可点击控件上（事件被消费）：手势全程让位，
                    // 不触发长按快进/拖动seek/单击双击（否则点动作栏按钮会被误判为视频区单击而收起控制层）
                    var gestureBlocked = false

                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) gestureBlocked = true
                        if (event.changes.all { !it.pressed }) break // 手指抬起
                        if (locked || gestureBlocked) continue // 锁定：拖动/长按失效，仅保留单击/双击
                        val change = event.changes.firstOrNull() ?: continue
                        val now = change.uptimeMillis

                        // 长按快进（fongmi onLongPress → onSpeedUp：2 倍速播放）
                        if (!isLong && axis == 0 &&
                            now - downTime >= viewConfiguration.longPressTimeoutMillis &&
                            hypot(change.position.x - downX, change.position.y - downY) <= viewConfiguration.touchSlop
                        ) {
                            val st = Players.state.value
                            if (st.playing && st.durationMs > 0) {
                                isLong = true
                                Players.setSpeed(2f)
                                speedUpHint = true
                            } else {
                                axis = 2 // 未在播放：不再触发长按
                            }
                            continue
                        }

                        if (isLong) continue // 长按快进期间忽略移动
                        // 方向判定（fongmi checkFunc：累计位移 ≥20dp，横向优先）
                        if (axis == 0) {
                            val adx = abs(change.position.x - downX)
                            val ady = abs(change.position.y - downY)
                            if (hypot(adx, ady) >= 20.dp.toPx()) axis = if (adx >= ady) 1 else 2
                        }
                        // 横向拖动快进/快退（fongmi onScroll：time = deltaX * 50ms，实时预览不真 seek）
                        if (axis == 1) {
                            val st = Players.state.value
                            if (st.durationMs > 0) {
                                val delta = ((change.position.x - downX) * 50).toLong()
                                seekTarget = (st.positionMs + delta).coerceIn(0L, st.durationMs)
                                seekPreview = seekTarget
                            }
                        }
                    }

                    // 抬起（fongmi onTouchEvent ACTION_UP）
                    if (isLong) {
                        // fongmi onSpeedEnd：恢复用户设定倍速
                        Players.setSpeed(playbackSpeed)
                        speedUpHint = false
                    } else if (axis == 1 && seekTarget != null) {
                        // fongmi onSeekEnd：真正 seek 到预览位置
                        Players.seekTo(seekTarget)
                        seekPreview = null
                    } else if (axis == 0 && !gestureBlocked) {
                        // 未移动未长按（且未点在控件上）：单击/双击判定（fongmi GestureDetector）
                        val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                            awaitFirstDown(requireUnconsumed = false)
                        }
                        if (second != null) {
                            // 双击：等待第二次抬起后切换播放/暂停
                            while (true) {
                                val e2 = awaitPointerEvent()
                                if (e2.changes.all { !it.pressed }) break
                            }
                            onTogglePlay()
                        } else if (locked) {
                            unlockHint = true
                        } else {
                            onToggleControls()
                        }
                    }
                    seekPreview = null
                }
            },
    ) {
        // fongmi 手势反馈浮层：顶部居中 50% 黑底 28dp 圆角胶囊（view_widget_vod seek/message 同款）
        if (seekPreview != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0x4D000000))
                    .padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Icon(
                    imageVector = if ((seekPreview ?: 0L) >= playerState.positionMs) AppIcons.Forward else AppIcons.Rewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 宽度由透明占位文字锁定，数字逐帧变化时胶囊不伸缩（兼容不支持 tnum 的 OEM 字体）
                StableTimeText(
                    text = formatMs(seekPreview ?: 0L),
                    durationMs = playerState.durationMs,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                    color = Color.White,
                )
            }
        }
        // 拖动底部进度条：顶部居中实时显示「跳转至 MM:SS」，跟随手指位置更新
        val sliderFraction = sliderSeekFraction
        if (sliderFraction != null && playerState.durationMs > 0) {
            StableTimeText(
                text = "跳转至${formatMs((sliderFraction * playerState.durationMs).toLong())}",
                durationMs = playerState.durationMs,
                prefix = "跳转至",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0x4D000000))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (speedUpHint) {
            Text(
                text = "2.0倍速快进中",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0x4D000000))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        if (controlsVisible && !locked) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶栏（fongmi: 56dp 行高 + 左右 4dp padding，返回 48dp / 标题 16sp 占中间 / 右侧投屏 48dp）
                // 无黑色渐变遮罩：纯白图标直接叠在画面上
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
                ) {
                    PlainTopBtn(AppIcons.Back, "返回") { onExitFullscreen() }
                    Text(
                        text = "${current?.title.orEmpty()} · 第${episodeIndex + 1}集",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    PlainTopBtn(AppIcons.Cast, "投屏") { }
                }

                // 中部（fongmi: 40/48dp shape_control 按钮，play 与两侧各 40dp 间距）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
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

                // 底部（fongmi: 容器 paddingBottom 8dp，seek 左右 16dp，动作栏左右 8dp）
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000)),
                            ),
                        )
                        .padding(bottom = 8.dp),
                ) {
                    // 进度条行（时间 + 进度条 + 时长）：行整体下移 —— 去掉行底部内边距，
                    // 使「进度条行 ↔ 功能栏」的间距 ≈「功能栏 ↔ 屏幕底部」的距离
                    // （功能栏到屏幕底 = 容器 8dp 底部内边距 + 按钮自身 8dp 内边距）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp),
                    ) {
                        Text(text = formatMs(playerState.positionMs), style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"), color = Color.White)
                        LineSlider(
                            progress = if (playerState.durationMs > 0) playerState.positionMs.toFloat() / playerState.durationMs else 0f,
                            onSeek = { Players.seekTo((it * playerState.durationMs).toLong()) },
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            onDragFractionChange = {
                                sliderSeekFraction = it
                                onSeekDragChange(it != null)
                            },
                        )
                        Text(text = formatMs(playerState.durationMs), style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"), color = Color.White)
                    }
                    // 动作栏（fongmi: 左组功能 + 右侧字幕/音轨/片头/片尾，白色 14sp 文字）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    ) {
                        // 左组：解码（显示当前值，点击直接切换）/ 缓冲 / 重播 / 倍速 / 缩放（点击循环切换）
                        // 点击动作栏按钮重置 5 秒自动隐藏计时（fongmi setR1Callback 同款），不立即消失
                        ActionBtn(playerState.decode.label) {
                            onDecodeChange(
                                if (playerState.decode == Players.Decode.HARD) Players.Decode.SOFT
                                else Players.Decode.HARD,
                            )
                            onControlInteract()
                        }
                        ActionBtn("缓冲") { showBuffer = true; onControlInteract() }
                        ActionBtn("重播") { Players.seekTo(0); onControlInteract() }
                        ActionBtn("倍速") { showSpeed = true; onControlInteract() }
                        ActionBtn(playerState.scaleMode.label) {
                            val modes = Players.ScaleMode.entries
                            onScaleChange(modes[(playerState.scaleMode.ordinal + 1) % modes.size])
                            onControlInteract()
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        // 右组：字幕 / 音轨 / 片头 / 片尾（点击标记当前位置，长按清除）
                        ActionBtn("字幕") { showText = true; onControlInteract() }
                        ActionBtn("音轨") { showAudio = true; onControlInteract() }
                        ActionBtn(
                            if (openingMs > 0) formatMs(openingMs) else "片头",
                            onLongClick = onClearOpening,
                            onClick = { onMarkOpening(); onControlInteract() },
                        )
                        ActionBtn(
                            if (endingMs > 0) formatMs(endingMs) else "片尾",
                            onLongClick = onClearEnding,
                            onClick = { onMarkEnding(); onControlInteract() },
                        )
                    }
                }
            }
        }

        // 右侧居中（fongmi: view_control_right 48dp 纯图标无背景 + marginEnd 4dp）
        if (!locked && controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(48.dp)
                    .tvCardEffect(onClick = { onLockToggle() }, shape = CircleShape, focusedScale = 1.08f, glow = false),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.LockOpen, "锁屏", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        if (locked && unlockHint) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(48.dp)
                    .tvCardEffect(onClick = { onUnlock() }, shape = CircleShape, focusedScale = 1.08f, glow = false),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Lock, "解锁", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        // 播放/解析错误浮层（锁定状态下也显示，点击返回先退出全屏）
        if (playerState.error != null && !playerState.buffering) {
            PlayerErrorOverlay(
                message = playerState.error!!,
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // 动作栏对话框（与竖屏同款）
        if (showSpeed) {
            OptionDialog("倍速", listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 3.0f), playerState.speed, { formatSpeed(it) }, { onSpeedChange(it); showSpeed = false }) { showSpeed = false }
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

/** 全屏动作栏纯文字按钮（fongmi style/Control：14sp 白色文字 + 8dp padding 无背景），按压用我们自己的变浅灰特效 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ActionBtn(
    text: String,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
        color = Color.White,
        maxLines = 1,
        modifier = Modifier
            .tvCardEffect(
                onClick = onClick,
                onLongClick = onLongClick,
                shape = RoundedCornerShape(percent = 50),
                focusedScale = 1.08f,
                glow = false,
            )
            .padding(8.dp),
    )
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

/** 播放器底部动作栏文字按钮（fongmi: style/Control，白色小字横向滚动） */
@Composable
private fun ControlChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        maxLines = 1,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 顶栏纯图标按钮（fongmi 同款）：48dp 无圆形底色，仅白色图标，保留点击热区与焦点缩放 */
@Composable
private fun PlainTopBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .tvCardEffect(onClick = onClick, shape = CircleShape, focusedScale = 1.08f, glow = false),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

/** 预览小窗中部控制按钮（fongmi: shape_control 40/48dp 圆形 + 30% 黑底 + 32dp 白图标），按压用 tvCardEffect 变浅灰 */
@Composable
private fun MiniLargeBtn(icon: ImageVector, desc: String, onClick: () -> Unit, isPrimary: Boolean = false) {
    val size = if (isPrimary) 48.dp else 40.dp
    // fongmi: exo_icon_play/pause 固有尺寸 32dp（scaleType=center 不做缩放），主按钮照搬该尺寸
    val iconSize = if (isPrimary) 32.dp else 24.dp
    Box(
        modifier = Modifier
            .size(size)
            .tvCardEffect(onClick = onClick, shape = CircleShape, focusedScale = if (isPrimary) 1.12f else 1.08f, glow = false)
            .background(Color(0x4D000000), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

// ========== 对话框 ==========

@Composable
private fun <T> OptionDialog(
    title: String, options: List<T>, current: T?, label: (T) -> String,
    onSelect: (T) -> Unit, onDismiss: () -> Unit,
) {
    // 选项过多（倍速档位/音轨）时限高可滚动，避免超出屏幕被裁、末项露出半截边缘
    val maxDialogHeight = with(LocalConfiguration.current) { (screenHeightDp * 0.65f).dp }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight)
                .padding(horizontal = 32.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
            // 行间距与 OptionDialog（倍速/缓冲等弹窗）统一为 6dp
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

/**
 * 宽度稳定的时间文字：部分 OEM 定制字体会忽略 tnum，数字宽度随时间逐帧变化会让胶囊持续变形。
 * 用「当前时长可能出现的最长时间串」透明占位撑开宽度，真实文字叠放其上居中。
 * 一次拖动会话内视频时长不变 → 占位宽度恒定，胶囊形状不随手指移动而变化。
 */
@Composable
private fun StableTimeText(
    text: String,
    durationMs: Long,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = "",
) {
    // Monospace 等宽字体：不依赖 OEM 字体是否支持 tnum，数字必然等宽，
    // 同格式时间串宽度逐帧恒定，居中后连前缀文字位置都不跳动
    val monoStyle = style.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    val maxTime = if (durationMs >= 3_600_000L) "00:00:00" else "00:00"
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Text(text = "$prefix$maxTime", style = monoStyle, color = Color.Transparent)
        Text(text = text, style = monoStyle, color = color)
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

/** seek 后"保持目标进度"的释放阈值：播放器进度与目标差值 ≤ 该比例即视为已跟上 */
private const val SEEK_HOLD_TOLERANCE = 0.002f

/**
 * 细直线进度条：2dp 轨道 + 白色圆点滑块，支持点按与横向拖动 seek（小窗/全屏统一风格）。
 * [onDragFractionChange] 拖动开始/移动回调实时目标比例，拖动结束回 null（供全屏顶部「跳转至 MM:SS」提示）。
 */
@Composable
private fun LineSlider(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragFractionChange: ((Float?) -> Unit)? = null,
) {
    // 拖动中的临时进度（null=未拖动），松手才真正 seek，避免拖动过程中频繁跳转
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    // seek 已提交、但播放器进度尚未跟上时的目标进度：
    // 期间继续渲染该目标（否则 playState 还停在旧位置，滑块会先跳回原位置再跳到目标）
    var pendingSeek by remember { mutableStateOf<Float?>(null) }
    // 提交 seek：进入"保持目标进度"状态
    val commitSeek: (Float) -> Unit = { v ->
        val target = v.coerceIn(0f, 1f)
        pendingSeek = target
        onSeek(target)
    }
    // 播放器进度追上目标 → 释放保持值，交回播放器进度驱动
    LaunchedEffect(pendingSeek, progress) {
        val ps = pendingSeek ?: return@LaunchedEffect
        if (abs(progress - ps) <= SEEK_HOLD_TOLERANCE) pendingSeek = null
    }
    // 兜底：seek 迟迟未生效（如网络 seek 慢）也要释放，避免滑块被长期钉在目标位置
    LaunchedEffect(pendingSeek) {
        if (pendingSeek == null) return@LaunchedEffect
        delay(1200)
        pendingSeek = null
    }
    BoxWithConstraints(
        modifier = modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset -> commitSeek(offset.x / size.width) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        dragProgress = f
                        onDragFractionChange?.invoke(f)
                    },
                    onDragEnd = {
                        dragProgress?.let(commitSeek)
                        dragProgress = null
                        onDragFractionChange?.invoke(null)
                    },
                    onDragCancel = {
                        dragProgress = null
                        onDragFractionChange?.invoke(null)
                    },
                ) { change, _ ->
                    val f = (change.position.x / size.width).coerceIn(0f, 1f)
                    dragProgress = f
                    onDragFractionChange?.invoke(f)
                }
            },
    ) {
        val p = (dragProgress ?: pendingSeek ?: progress).coerceIn(0f, 1f)
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
 * 视频区内的轻量错误浮层（竖屏小窗 / 全屏共用）：
 * 播放或解析失败时在画面中央显示具体原因 + 返回按钮，替代黑屏卡死，绝不闪退。
 */
@Composable
private fun PlayerErrorOverlay(
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = AppIcons.Close,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = "无法播放",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onBack() }
                .padding(horizontal = 24.dp, vertical = 9.dp),
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
                text = playerError
                    ?: "未找到该视频（ID: $videoId），请返回重试或检查站源是否可用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onBack() }
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
private const val HIDE_DELAY_MS = 5_000L
