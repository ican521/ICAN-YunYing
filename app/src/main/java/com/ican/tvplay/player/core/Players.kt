package com.ican.tvplay.player.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.ParserException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ican.tvplay.TvPlayApplication
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.data.model.Episode
import com.ican.tvplay.data.model.PlayLine
import com.ican.tvplay.data.model.Video

/**
 * 播放器门面（仿 fongmi Players 单例模式，基于官方 media3 1.11.1 重写，
 * 避开 fork 私有 API：C.DECODE_SOFTWARE / DecodeTrackSelector / libass / setAdblock 等）。
 *
 * - 双解码内核：HARD = 仅 MediaCodec 硬解；SOFT = ffmpeg 扩展渲染器优先（软解，so 在 jniLibs）
 * - 缓冲档位：DefaultLoadControl 缓冲时长 × 档位倍率（仿 ExoUtil.buildLoadControl）
 * - 解码/缓冲切换需重建 ExoPlayer，自动恢复位置与播放状态
 * - 横竖屏 Activity 重建不丢播放：单例持有 player，视图仅 attach/detach
 */
object Players {

    private const val TAG = "Players"

    /** 解码内核 */
    enum class Decode(val label: String) { HARD("硬解"), SOFT("软解") }

    /** 画面缩放（resizeMode 用 AspectRatioFrameLayout 常量；ratio=0 表示跟随视频原始比例） */
    enum class ScaleMode(val label: String, val resizeMode: Int, val ratio: Float) {
        FIT("适应", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT, 0f),
        FILL("填充", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL, 0f),
        ZOOM("原始", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 0f),
        R16_9("16:9", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT, 16f / 9f),
        R4_3("4:3", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT, 4f / 3f),
    }

    /** 缓冲档位（默认缓冲时长倍率） */
    enum class BufferTier(val label: String, val multiplier: Int) {
        LOW("低", 1), MEDIUM("中", 2), HIGH("高", 3),
    }

    /** 可选轨道（音轨/字幕轨） */
    data class TrackOption(
        val groupIndex: Int,
        val trackIndex: Int,
        val name: String,
        val selected: Boolean,
    )

    data class PlayerState(
        val ready: Boolean = false,
        val playing: Boolean = false,
        val buffering: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val bufferedMs: Long = 0L,
        val speed: Float = 1.0f,
        val decode: Decode = Decode.HARD,
        val scaleMode: ScaleMode = ScaleMode.FIT,
        val bufferTier: BufferTier = BufferTier.LOW,
        val audioTracks: List<TrackOption> = emptyList(),
        val textTracks: List<TrackOption> = emptyList(),
        val textDisabled: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    var player: ExoPlayer? = null
        private set

    private var appContext: Context? = null
    private var currentSpec: PlaySpec? = null
    private var retryCount = 0

    /** fongmi 同款：只保存绑定的 PlayerView，Surface 全权交给 PlayerView 内部管理（绝不手动 setVideoSurface） */
    private var playerView: PlayerView? = null

    /** 跨站源场景下：提前 cache 的完整 Video 元数据（解决 VideoRepository 是单站点的 getVideo 查不到的问题） */
    data class CachedVideo(
        val video: Video,
        val siteKey: String,
        /** 跨站源来源站点与 jar 规格：播放页内延迟解析播放地址用（jar 扫码弹窗等交互需在播放页触发） */
        val site: com.ican.tvplay.data.remote.TvBoxSite? = null,
        val jarSpec: String = "",
    )
    var cachedVideo: CachedVideo? = null
        private set

    fun cacheVideo(video: Video, siteKey: String, site: com.ican.tvplay.data.remote.TvBoxSite? = null, jarSpec: String = "") {
        cachedVideo = CachedVideo(video, siteKey, site, jarSpec)
    }

    fun consumeCachedVideo(videoId: String): CachedVideo? {
        val c = cachedVideo
        return if (c != null && c.video.id == videoId) c else null
    }

    /** 跨站源场景下：MultiSourceSearchScreen 提前解析好的 PlaySpec（已经 jx/parse 处理过真实 URL + headers）
     *  PlayerScreen LaunchedEffect 里优先用这个，避免 VideoRepository.resolvePlaySource 查 currentSite 错站 */
    var pendingPreStartSpec: PlaySpec? = null

    // ---- 后台续播 / 通知栏控制 / PiP ----

    /** 当前播放的剧集上下文：通知栏展示 + 上一集/下一集切换 + 后台片尾自动切集所需 */
    data class PlaybackContext(
        val video: Video,
        val lineFlag: String,
        val episodes: List<Episode>,
        val episodeIndex: Int,
        val openingMs: Long = 0L,
        val endingMs: Long = 0L,
    )

    private val _playbackContext = MutableStateFlow<PlaybackContext?>(null)
    val playbackContextFlow: StateFlow<PlaybackContext?> = _playbackContext.asStateFlow()
    var playbackContext: PlaybackContext?
        get() = _playbackContext.value
        set(value) { _playbackContext.value = value }

    /** 全屏播放中（MainActivity.onUserLeaveHint 判断是否自动进 PiP） */
    @Volatile
    var isFullscreen: Boolean = false

    private val _pipMode = MutableStateFlow(false)

    /** 画中画模式（MainActivity 回调更新，PlayerScreen 收集后切换为纯画面布局） */
    val pipMode: StateFlow<Boolean> = _pipMode.asStateFlow()
    fun setPipMode(inPip: Boolean) { _pipMode.value = inPip }

    /** 通知栏点击 → 回到播放页（AppRoot 收集后导航） */
    data class OpenPlayerRequest(
        val token: Long,
        val videoId: String,
        val episodeIndex: Int,
        val flag: String,
    )

    private val _openPlayerRequest = MutableStateFlow<OpenPlayerRequest?>(null)
    val openPlayerRequest: StateFlow<OpenPlayerRequest?> = _openPlayerRequest.asStateFlow()

    /** 通知栏点击恢复播放页（从 playbackContext 取当前播放信息） */
    fun requestOpenPlayerFromContext() {
        val ctx = playbackContext ?: return
        _openPlayerRequest.value = OpenPlayerRequest(
            token = System.currentTimeMillis(),
            videoId = ctx.video.id,
            episodeIndex = ctx.episodeIndex,
            flag = ctx.lineFlag,
        )
    }

    /** player 实例变化钩子（PlaybackService 的 MediaSession 同步 setPlayer 用） */
    var onPlayerChanged: ((ExoPlayer?) -> Unit)? = null

    /** 播放器是否存活（后台续播时 player 不为 null） */
    fun isAlive(): Boolean = player != null

    /** 页面存活时的切集导航（更新页面 episodeIndex 触发页面解析流）；null = 页面已退出，走后台直接解析 */
    var uiEpisodeNavigator: ((Int) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 后台片尾自动切集防重（页面存活时由 PlayerScreen 的 autoNextArmed 负责） */
    private var autoNextFired = false

    /** 通知栏/后台切集：delta=+1 下一集，-1 上一集 */
    fun skipEpisode(delta: Int) {
        val ctx = playbackContext ?: return
        val target = ctx.episodeIndex + delta
        if (target !in ctx.episodes.indices) return
        val nav = uiEpisodeNavigator
        if (nav != null) {
            nav(target)
        } else {
            scope.launch { resolveAndStart(ctx, target) }
        }
    }

    /** 页面不在时的切集：直接解析并开播（无 UI 弹窗能力，扫码类站源后台切集不可用，可接受） */
    private suspend fun resolveAndStart(ctx: PlaybackContext, targetIndex: Int) {
        val app = appContext as? TvPlayApplication ?: return
        val container = app.container
        val episode = ctx.episodes.getOrNull(targetIndex) ?: return
        val source = try {
            val cached = cachedVideo?.takeIf { it.video.id == ctx.video.id }
            if (cached?.site != null) {
                container.spiderManager.resolvePlayUrl(
                    site = cached.site,
                    jarSpec = cached.jarSpec,
                    playUrl = episode.playUrl,
                    flag = ctx.lineFlag,
                )
            } else {
                container.videoRepository.resolvePlaySource(ctx.video, episode, ctx.lineFlag)
            }
        } catch (e: Exception) {
            Log.e(TAG, "background skipEpisode resolve failed", e)
            return
        }
        playbackContext = ctx.copy(episodeIndex = targetIndex)
        val act = appContext ?: return
        start(act, PlaySpec(url = source.url, headers = source.headers))
        // 后台切集：把新集锚点写进历史（进度从 0 开始计）
        val dao = container.historyDao
        val index = targetIndex
        scope.launch {
            runCatching {
                dao.upsert(
                    HistoryEntity(
                        videoId = ctx.video.id,
                        title = ctx.video.title,
                        cover = ctx.video.cover,
                        categoryName = ctx.video.categoryName,
                        episodeIndex = index,
                        episodeTitle = episode.title,
                        positionMs = 0L,
                        durationMs = 0L,
                        updatedAt = System.currentTimeMillis(),
                        openingMs = ctx.openingMs,
                        endingMs = ctx.endingMs,
                        sourceKey = cachedVideo?.takeIf { it.video.id == ctx.video.id }?.siteKey ?: "",
                        sourceName = ctx.video.sourceName,
                    ),
                )
            }
        }
    }

    /** 后台进度持久化（PlaybackService 周期调用）：页面退出后进度不丢 */
    fun persistProgressIfNeeded() {
        val ctx = playbackContext ?: return
        val p = player ?: return
        val app = appContext as? TvPlayApplication ?: return
        val ep = ctx.episodes.getOrNull(ctx.episodeIndex) ?: return
        val pos = p.currentPosition.coerceAtLeast(0L)
        val dur = if (p.duration != C.TIME_UNSET) p.duration.coerceAtLeast(0L) else 0L
        scope.launch {
            runCatching {
                app.container.historyDao.upsert(
                    HistoryEntity(
                        videoId = ctx.video.id,
                        title = ctx.video.title,
                        cover = ctx.video.cover,
                        categoryName = ctx.video.categoryName,
                        episodeIndex = ctx.episodeIndex,
                        episodeTitle = ep.title,
                        positionMs = pos,
                        durationMs = dur,
                        updatedAt = System.currentTimeMillis(),
                        openingMs = ctx.openingMs,
                        endingMs = ctx.endingMs,
                        sourceKey = cachedVideo?.takeIf { it.video.id == ctx.video.id }?.siteKey ?: "",
                        sourceName = ctx.video.sourceName,
                    ),
                )
            }
        }
    }


    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (p.duration != C.TIME_UNSET) {
                _state.value = _state.value.copy(
                    positionMs = p.currentPosition.coerceAtLeast(0L),
                    durationMs = p.duration.coerceAtLeast(0L),
                    bufferedMs = p.bufferedPosition.coerceAtLeast(0L),
                )
            }
            // 后台续播（页面已退出）时的片尾自动切下一集；页面存活时由 PlayerScreen 负责
            val ctx = playbackContext
            if (uiEpisodeNavigator == null && ctx != null && ctx.endingMs > 0 &&
                p.isPlaying && p.duration != C.TIME_UNSET &&
                ctx.endingMs + p.currentPosition >= p.duration && !autoNextFired
            ) {
                autoNextFired = true
                skipEpisode(+1)
            }
            handler.postDelayed(this, 500L)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(playing = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "onPlaybackStateChanged state=$playbackState")
            _state.value = _state.value.copy(
                buffering = playbackState == Player.STATE_BUFFERING,
                ready = playbackState >= Player.STATE_READY,
            )
        }

        override fun onTracksChanged(tracks: Tracks) {
            val codec = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
                ?.let { g -> (0 until g.length).firstNotNullOfOrNull { i -> g.getTrackFormat(i).codecs } }
            Log.d(TAG, "onTracksChanged videoCodec=$codec")
            _state.value = _state.value.copy(
                audioTracks = collectOptions(tracks, C.TRACK_TYPE_AUDIO),
                textTracks = collectOptions(tracks, C.TRACK_TYPE_TEXT),
                textDisabled = player?.trackSelectionParameters?.disabledTrackTypes
                    ?.contains(C.TRACK_TYPE_TEXT) == true,
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "onPlayerError [retry=$retryCount]: ${error.errorCodeName}", error)
            if (handleError(error)) {
                _state.value = _state.value.copy(error = null)  // 重试成功后清掉错误提示
                return
            }
            _state.value = _state.value.copy(error = describeError(error))
        }
    }

    // ---- 生命周期 ----

    /** fongmi 同款视图绑定：PlayerView 内部自管 Surface 的创建/绑定/释放 */
    fun bindPlayerView(view: PlayerView) {
        playerView = view
        view.useController = false
        view.player = player
        Log.d(TAG, "bindPlayerView view=$view player=$player")
    }

    /** 视图销毁时解绑（仅当是当前绑定的视图才清空，防止全屏/竖屏切换时误清新绑定） */
    fun unbindPlayerView(view: PlayerView) {
        if (playerView === view) {
            playerView = null
            view.player = null
            Log.d(TAG, "unbindPlayerView view=$view")
        }
    }

    /** 开始播放（或换源/换线路） */
    fun start(context: Context, spec: PlaySpec, startPositionMs: Long = 0L) {
        appContext = context.applicationContext
        currentSpec = spec
        retryCount = 0  // 新源重置错误计数
        _state.value = _state.value.copy(error = null)  // 新源开播清除旧错误，避免换源后错误页残留
        try {
            val p = ensurePlayer()
            startInternal(p, spec, startPositionMs)
        } catch (t: Throwable) {
            // 流媒体模块缺失（ClassNotFound）、MediaSource 构造失败等同步异常一律转错误页，禁止闪退
            Log.e(TAG, "start fail url=${spec.url.take(120)}", t)
            notifyError(describeError(t))
        }
    }

    /** 播放页解析阶段（spider playerContent / jx 二次解析）致命错误上报：转错误页提示而不是崩溃 */
    fun notifyError(message: String) {
        Log.e(TAG, "notifyError: $message")
        _state.value = _state.value.copy(error = message)
    }

    /** 退出播放页但继续后台播放（保活开关开启）：解绑视图，播放器保持，转交前台服务 */
    fun detachForBackground() {
        playerView?.let { it.player = null }
        playerView = null
    }

    /** 退出播放页时释放 */
    fun release() {
        handler.removeCallbacks(ticker)
        playerView?.player = null
        playerView = null
        player?.run {
            stop()          // 先 stop 释放媒体源，再 release
            removeListener(listener)
            release()
        }
        player = null
        currentSpec = null
        appContext = null
        playbackContext = null
        autoNextFired = false
        onPlayerChanged?.invoke(null)
        _state.value = _state.value.copy(
            ready = false, playing = false, buffering = false,
            positionMs = 0L, durationMs = 0L, bufferedMs = 0L,
            audioTracks = emptyList(), textTracks = emptyList(), error = null,
        )
    }

    /** 后台：暂停并释放音频焦点（不销毁 player，页面回来还能恢复） */
    fun onPause() {
        player?.pause()
        player?.playWhenReady = false
    }

    /** 前台：恢复播放 */
    fun onResume() {
        player?.playWhenReady = true
        player?.play()
    }

    // ---- 播放控制 ----

    fun playPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceIn(0L, _state.value.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE))
    }

    fun setSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    fun setScaleMode(mode: ScaleMode) {
        _state.value = _state.value.copy(scaleMode = mode)
    }

    // ---- 解码 / 缓冲（需重建播放器） ----

    fun setDecode(decode: Decode) {
        if (_state.value.decode == decode) return
        _state.value = _state.value.copy(decode = decode)
        rebuild()
    }

    fun setBufferTier(tier: BufferTier) {
        if (_state.value.bufferTier == tier) return
        _state.value = _state.value.copy(bufferTier = tier)
        rebuild()
    }

    // ---- 轨道 ----

    fun selectTrack(option: TrackOption?, trackType: @C.TrackType Int) {
        val p = player ?: return
        val builder = p.trackSelectionParameters.buildUpon()
        if (trackType == C.TRACK_TYPE_TEXT) {
            // option=null 表示关闭字幕
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, option == null)
            _state.value = _state.value.copy(textDisabled = option == null)
        }
        if (option != null) {
            val tracks = p.currentTracks
            val group = tracks.groups.getOrNull(option.groupIndex) ?: return
            builder.setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex),
            )
        } else {
            builder.clearOverridesOfType(trackType)
        }
        p.trackSelectionParameters = builder.build()
        listener.onTracksChanged(p.currentTracks)
    }

    /** 动态添加外挂字幕：重建 MediaItem 并保持当前播放位置 */
    fun addSubtitle(sub: SubItem) {
        val spec = currentSpec ?: return
        val p = player ?: return
        val pos = p.currentPosition
        val newSpec = spec.copy(subs = spec.subs.filterNot { it.url == sub.url } + sub)
        currentSpec = newSpec
        startInternal(p, newSpec, pos)
    }

    // ---- 内部实现 ----

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val ctx = appContext ?: error("Players.start() 未先调用")
        val p = buildPlayer(ctx, _state.value.decode, _state.value.bufferTier)
        player = p
        handler.post(ticker)
        onPlayerChanged?.invoke(p)
        // 创建新 player 后重新绑定到已保存的 PlayerView（PlayerView 内部自管 surface）
        playerView?.let { pv ->
            Log.d(TAG, "ensurePlayer re-bind playerView=$pv")
            pv.player = p
        }
        return p
    }

    /** 仿 fongmi ExoUtil.buildPlayer/buildLoadControl/buildRenderersFactory 的官方 media3 等价实现 */
    private fun buildPlayer(ctx: Context, decode: Decode, tier: BufferTier): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(ctx)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                if (decode == Decode.SOFT) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
            )
        // 缓冲：默认时长 × 档位倍率（仿 fongmi buffer 设置）
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * tier.multiplier,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * tier.multiplier,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .build()
        return ExoPlayer.Builder(ctx, renderersFactory)
            .setTrackSelector(DefaultTrackSelector(ctx))
            .setLoadControl(loadControl)
            .build()
            .apply {
                // CONTENT_TYPE_MOVIE 让系统把 AC3/EAC3/DTS 等音频路由到正确的硬件路径
                setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                playWhenReady = true
                addListener(listener)
                setPlaybackSpeed(_state.value.speed)
            }
    }

    private fun startInternal(p: ExoPlayer, spec: PlaySpec, positionMs: Long) {
        Log.d(TAG, "startInternal url=${spec.url.take(120)} headers=${spec.headers.keys}")
        autoNextFired = false   // 新一集重新武装后台片尾自动切集
        // 换源前先 stop 释放上一个 MediaSource，避免资源冲突导致无声/崩溃
        p.stop()
        p.clearMediaItems()
        // 挂上片名/集名元数据：通知栏（Android 13+ 系统媒体模板）与媒体控件展示用
        val metaTitle = playbackContext?.video?.title
        val metaArtist = playbackContext?.episodes
            ?.getOrNull(playbackContext?.episodeIndex ?: -1)?.title
        val item = MediaItemFactory.from(spec).buildUpon()
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(metaTitle)
                    .setArtist(metaArtist?.takeIf { it.isNotBlank() } ?: "视频播放中")
                    .build(),
            )
            .build()
        val headers = MediaItemFactory.checkUa(spec.headers)
        if (headers.isNotEmpty()) {
            val ds = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)
            p.setMediaSource(DefaultMediaSourceFactory(ds).createMediaSource(item), positionMs)
        } else {
            p.setMediaItem(item, positionMs)
        }
        p.prepare()
        p.play()
    }

    /** 解码/缓冲切换：保存位置 → 重建 → 恢复 */
    private fun rebuild() {
        val ctx = appContext ?: return
        val old = player ?: return
        val pos = old.currentPosition
        val wasPlaying = old.playWhenReady
        val spec = currentSpec
        handler.removeCallbacks(ticker)
        playerView?.player = null   // 先解绑旧 player
        old.stop()           // 先 stop 释放媒体资源
        old.removeListener(listener)
        old.release()
        player = null
        val p = buildPlayer(ctx, _state.value.decode, _state.value.bufferTier)
        player = p
        handler.post(ticker)
        onPlayerChanged?.invoke(p)
        // 重新绑定到 PlayerView（PlayerView 内部自管 surface）
        playerView?.let { it.player = p }
        if (spec != null) {
            retryCount = 0    // 重建后重置错误计数
            try {
                startInternal(p, spec, pos)
            } catch (t: Throwable) {
                Log.e(TAG, "rebuild startInternal fail", t)
                notifyError(describeError(t))
                return
            }
            if (!wasPlaying) p.pause()
        }
    }

    /** 错误回退（仿 fongmi ExoPlayerEngine.handleError）：解码失败→自动切软解；容器解析失败→改 mimeType 重试 */
    private fun handleError(e: PlaybackException): Boolean {
        if (retryCount >= 2) return false
        when (e.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            -> {
                if (_state.value.decode == Decode.SOFT) return false
                retryCount++
                Log.w(TAG, "decoder error, fallback to SOFT decode")
                setDecode(Decode.SOFT)
                return true
            }
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            -> {
                val spec = currentSpec ?: return false
                if (spec.format == MimeTypes.APPLICATION_M3U8) return false
                retryCount++
                Log.w(TAG, "container error, retry as m3u8")
                currentSpec = spec.copy(format = MimeTypes.APPLICATION_M3U8)
                val p = player ?: return false
                return runCatching { startInternal(p, currentSpec!!, p.currentPosition) }
                    .onFailure { Log.e(TAG, "retry as m3u8 fail", it); notifyError(describeError(it)) }
                    .isSuccess
            }
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            -> {
                val spec = currentSpec ?: return false
                if (spec.format == null) return false
                retryCount++
                Log.w(TAG, "manifest error, retry without mimeType")
                currentSpec = spec.copy(format = null)
                val p = player ?: return false
                return runCatching { startInternal(p, currentSpec!!, p.currentPosition) }
                    .onFailure { Log.e(TAG, "retry without mimeType fail", it); notifyError(describeError(it)) }
                    .isSuccess
            }
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                player?.seekToDefaultPosition()
                player?.prepare()
                return true
            }
        }
        return false
    }

    /**
     * 把任意播放/解析异常归类成用户可读的具体中文提示。
     * 先沿 cause 链识别网络层具体异常（HTTP 状态码/DNS/超时/内容类型/解析器），
     * 再按 PlaybackException.errorCode 兜底。
     */
    private fun describeError(t: Throwable): String {
        var c: Throwable? = t
        while (c != null) {
            when (c) {
                is HttpDataSource.InvalidResponseCodeException -> return when (c.responseCode) {
                    401, 403 -> "视频地址鉴权失败（HTTP ${c.responseCode}），该站源可能需要权限、Cookie 或已失效"
                    404 -> "视频地址不存在（HTTP 404），该资源可能已下架"
                    in 500..599 -> "源站服务器异常（HTTP ${c.responseCode}），请稍后重试或切换站源"
                    else -> "源站拒绝访问（HTTP ${c.responseCode}），请切换线路或站源"
                }
                is UnknownHostException -> return "无法连接视频源站（域名解析失败），请检查网络后重试"
                is SocketTimeoutException -> return "连接视频源站超时，请切换网络或站源后重试"
                is ParserException -> return "视频内容解析失败，地址可能已失效或不是真实播放地址"
                is ClassNotFoundException, is NoClassDefFoundError ->
                    return "当前视频格式缺少播放组件支持，请更新 App 后重试"
            }
            c = c.cause
        }
        val pe = t as? PlaybackException
        return when (pe?.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> "网络连接失败，请检查网络或稍后重试"
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
                "返回内容不是视频流（可能是网页或验证页），该站源解析失败"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "源站拒绝访问该视频地址，请切换线路或站源"
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                "没有网络访问权限，请检查 App 联网设置"
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                "该站源使用不安全的明文 HTTP 地址，无法播放"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            -> "当前视频格式暂不支持，请切换线路或站源"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            -> "视频数据损坏或地址已失效，请切换线路重试"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            -> "视频解码失败，可在播放菜单切换为软解后重试"
            PlaybackException.ERROR_CODE_TIMEOUT -> "视频加载超时，请切换网络或站源后重试"
            else -> pe?.errorCodeName?.let { "播放失败：$it" }
                ?: (t.message?.takeIf { it.isNotBlank() }?.let { "播放失败：${it.take(60)}" }
                    ?: "播放失败，请切换线路或站源重试")
        }
    }

    private fun collectOptions(tracks: Tracks, trackType: @C.TrackType Int): List<TrackOption> {
        val nameProvider = androidx.media3.ui.DefaultTrackNameProvider(
            appContext?.resources ?: return emptyList(),
        )
        val out = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { gi, group ->
            if (group.type != trackType) return@forEachIndexed
            for (ti in 0 until group.length) {
                if (!group.isTrackSupported(ti)) continue
                val format = group.getTrackFormat(ti)
                out += TrackOption(
                    groupIndex = gi,
                    trackIndex = ti,
                    name = nameProvider.getTrackName(format),
                    selected = group.isTrackSelected(ti),
                )
            }
        }
        return out
    }
}
