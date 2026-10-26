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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.mpvplayer.MpvPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ican.tvplay.TvPlayApplication
import com.ican.tvplay.data.SUBTITLE_MAX_POSITION
import com.ican.tvplay.data.SUBTITLE_MAX_SCALE
import com.ican.tvplay.data.SUBTITLE_MIN_POSITION
import com.ican.tvplay.data.SUBTITLE_MIN_SCALE
import com.ican.tvplay.data.SUBTITLE_OFFSET_MAX_MS
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
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object Players {

    private const val TAG = "Players"

    /** 解码内核 */
    enum class Decode(val label: String) { HARD("硬解"), SOFT("软解") }

    /** 播放内核（仿 fongmi PlayerEngine.Type：EXO=ExoPlayer，MPV=libmpv） */
    enum class Kernel(val label: String) { EXO("EXO"), MPV("MPV") }

    /** 画面缩放（resizeMode 用 AspectRatioFrameLayout 常量；ratio=0 表示跟随视频原始比例），命名对齐 fongmi select_scale */
    enum class ScaleMode(val label: String, val resizeMode: Int, val ratio: Float) {
        FIT("原始", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT, 0f),
        FILL("填充", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL, 0f),
        ZOOM("裁剪", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 0f),
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
        val speed: Float = 1.0f,
        val decode: Decode = Decode.HARD,
        val kernel: Kernel = Kernel.EXO,
        val scaleMode: ScaleMode = ScaleMode.FIT,
        val bufferTier: BufferTier = BufferTier.LOW,
        val audioTracks: List<TrackOption> = emptyList(),
        val textTracks: List<TrackOption> = emptyList(),
        val textDisabled: Boolean = false,
        /** 外挂字幕时间偏移（ms，仿 fongmi 播放内状态，新开播重置） */
        val subtitleOffsetMs: Long = 0L,
        val error: String? = null,
        /** 实时网络速度（bps），缓冲时显示在转圈下方 */
        val networkSpeedBps: Long = 0L,
    )

    /** 高频进度（播放中每 500ms 一跳）：与 PlayerState 拆开，
     *  进度刷新只重组订阅本流的进度条/时间文本，不再牵动整个播放页 */
    data class ProgressState(
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val bufferedMs: Long = 0L,
    )

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(ProgressState())
    val progress: StateFlow<ProgressState> = _progress.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    /** 带宽计：用于缓冲时实时网速显示 */
    private var bandwidthMeter: DefaultBandwidthMeter? = null
    private val bandwidthListener = BandwidthMeter.EventListener { elapsedMs, bytesTransferred, bitrateEstimate ->
        // 用本次采样实际速率（bytes/ms → bps）作为实时网速，比估计带宽更贴近瞬时速度
        val bps = if (elapsedMs > 0) bytesTransferred * 8 * 1000 / elapsedMs else bitrateEstimate
        if (_state.value.networkSpeedBps != bps) {
            _state.value = _state.value.copy(networkSpeedBps = bps)
        }
    }

    var player: Player? = null
        private set

    /** 当前 EXO 播放器的可热切换缓冲 LoadControl（重建播放器时随之重建） */
    private var tieredLoadControl: TieredLoadControl? = null

    private var appContext: Context? = null
    private var currentSpec: PlaySpec? = null
    private var retryCount = 0

    /** 字幕字号倍率（持久化设置，绑定视图 / 修改时应用到 SubtitleView） */
    private var subtitleScale = 1.0f

    /** 字幕位置百分比（正数上移，0 为默认贴底） */
    private var subtitlePosition = 0f

    /** 添加外挂字幕后，需要在 onTracksChanged 中强制选中该外挂字幕轨道 */
    private var pendingSelectExternalSubtitle = false

    /** MPV addSubtitle 的 native 结果不可靠：用来延迟校验文本轨是否真正出现 */
    private var mpvSubtitleVerifyToken = 0

    // ---- 对外状态 ----
    private var subtitleViewRef: SubtitleView? = null

    /** 播放前源类型探测任务：换源/切集时取消上一个，避免旧探测结果覆盖新播放 */
    private var probeJob: Job? = null
    private var startToken = 0

    /** 字幕偏移重载任务：+/- 连点时合并为一次重解析，避免反复 stop/prepare */
    private var offsetApplyJob: Job? = null

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

    private val _pipTransition = MutableStateFlow(false)

    /** 画中画进出过渡（含退出后的短暂保护期）：
     *  AppRoot 据此把根底色切成黑色并关闭导航圆角裁剪。
     *  退出小窗时系统先放大窗口、配置更新晚一帧，此时播放页仍按小窗尺寸布局，
     *  露出的根底色（浅色主题为白）会被看到，形成底部白色闪条。 */
    val pipTransition: StateFlow<Boolean> = _pipTransition.asStateFlow()
    fun setPipTransition(active: Boolean) { _pipTransition.value = active }

    private val _videoPresent = MutableStateFlow(false)

    /** 视频是否以铺满全屏/画中画的方式呈现（全屏播放 + PiP + 相关过渡期）。
     *  AppRoot 据此把根底色保持黑色：全屏/小窗下窗口底部最后约 1px 由根底色绘制，
     *  浅色主题为白，会露出一条白色细线。仅靠 pipTransition（进出过渡）覆盖不到
     *  稳态全屏，故单独上报。 */
    val videoPresent: StateFlow<Boolean> = _videoPresent.asStateFlow()
    fun setVideoPresent(active: Boolean) { _videoPresent.value = active }

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
    var onPlayerChanged: ((Player?) -> Unit)? = null

    /** 播放器是否存活（后台续播时 player 不为 null） */
    fun isAlive(): Boolean = player != null

    /** 页面存活时的切集导航（更新页面 episodeIndex 触发页面解析流）；null = 页面已退出，走后台直接解析 */
    var uiEpisodeNavigator: ((Int) -> Unit)? = null

    /** 页面返回导航：X 关闭画中画小窗后由 Activity 触发，退出播放页（App 保留在后台） */
    var uiExitNavigator: (() -> Unit)? = null
    fun requestExitPlayer() { uiExitNavigator?.invoke() }

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
        // 后台切集同样跳过片头
        start(act, PlaySpec(url = source.url, headers = source.headers), startPositionMs = ctx.openingMs)
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
                _progress.value = ProgressState(
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
            // 字幕轨道调试日志
            val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            textGroups.forEachIndexed { gi, g ->
                for (i in 0 until g.length) {
                    val fmt = g.getTrackFormat(i)
                    Log.d(TAG, "  textTrack[$gi][$i] label=${fmt.label} mime=${fmt.sampleMimeType} lang=${fmt.language} flags=${fmt.selectionFlags} selected=${g.isSelected}")
                }
            }
            // 添加外挂字幕后，强制选中 label 不为空的外挂字幕轨道（内嵌字幕 label 通常为 null）
            // 注意：onTracksChanged 可能在轨道清空时先触发一次，此时找不到外挂字幕，不能重置标志
            if (pendingSelectExternalSubtitle) {
                val p = player
                if (p != null) {
                    val extGroupIndex = textGroups.indexOfFirst { g ->
                        (0 until g.length).any { i -> g.getTrackFormat(i).label != null }
                    }
                    if (extGroupIndex >= 0) {
                        val g = textGroups[extGroupIndex]
                        val trackIndex = (0 until g.length).first { i -> g.getTrackFormat(i).label != null }
                        val builder = p.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            // 清掉内封 CEA/默认字幕轨 override，避免外挂轨已选中但渲染仍落到内封轨
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setOverrideForType(
                                TrackSelectionOverride(g.mediaTrackGroup, listOf(trackIndex))
                            )
                        p.trackSelectionParameters = builder.build()
                        pendingSelectExternalSubtitle = false
                        Log.d(TAG, "  force select external subtitle group=$extGroupIndex track=$trackIndex label=${g.getTrackFormat(trackIndex).label}")
                    } else {
                        Log.d(TAG, "  pendingSelectExternalSubtitle: no external subtitle track found yet, keep waiting")
                    }
                }
            }
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
        attachSubtitleView(view.subtitleView)
        Log.d(TAG, "bindPlayerView view=$view player=$player")
    }

    /** 视图销毁时解绑（仅当是当前绑定的视图才清空，防止全屏/竖屏切换时误清新绑定） */
    fun unbindPlayerView(view: PlayerView) {
        if (playerView === view) {
            subtitleViewRef = null
            playerView = null
            view.player = null
            Log.d(TAG, "unbindPlayerView view=$view")
        }
    }

    /** 开始播放（或换源/换线路） */
    fun start(context: Context, spec: PlaySpec, startPositionMs: Long = 0L) {
        appContext = context.applicationContext
        probeJob?.cancel()
        offsetApplyJob?.cancel()
        val token = ++startToken
        currentSpec = spec
        retryCount = 0  // 新源重置错误计数
        // 新源开播清除旧错误，并重置播放内的字幕偏移（偏移只对当前这一集有效，仿 fongmi）
        _state.value = _state.value.copy(error = null, subtitleOffsetMs = 0L)
        val inferred = spec.format ?: ContentTypeProbe.fromUrl(spec.url)
        val needProbe = inferred == null && _state.value.kernel == Kernel.EXO &&
            (spec.url.startsWith("http://") || spec.url.startsWith("https://"))
        if (!needProbe) {
            startResolved(spec.copy(format = inferred), startPositionMs)
            return
        }
        // 未标注格式：先探测容器再起播，期间停掉旧画面并显示缓冲
        player?.stop()
        _state.value = _state.value.copy(buffering = true)
        probeJob = scope.launch {
            val format = withContext(Dispatchers.IO) {
                ContentTypeProbe.probe(spec.url, MediaItemFactory.checkUa(spec.headers))
            }
            if (token != startToken) return@launch
            startResolved(spec.copy(format = format), startPositionMs)
        }
    }

    /** 源类型已确定（或探测完成）后同步起播 */
    private fun startResolved(spec: PlaySpec, startPositionMs: Long) {
        currentSpec = spec
        try {
            val p = ensurePlayer()
            startInternal(p, spec, startPositionMs)
        } catch (t: Throwable) {
            // 流媒体模块缺失（ClassNotFound）、MediaSource 构造失败等同步异常一律转错误页，禁止闪退
            Log.e(TAG, "start fail url=${spec.url.take(120)}", t)
            notifyError(describeError(t))
        }
    }

    /** 字幕字号（0.5x - 4.0x）：实时应用到已绑定的 SubtitleView / MPV 字幕选项 */
    fun setSubtitleScale(scale: Float) {
        subtitleScale = scale.coerceIn(SUBTITLE_MIN_SCALE, SUBTITLE_MAX_SCALE)
        applySubtitleScale()
        applyMpvSubtitleOptions()
    }

    /** 字幕位置（-100% - +100%，正数上移）：EXO 用 SubtitleView，MPV 用 sub-pos */
    fun setSubtitlePosition(position: Float) {
        subtitlePosition = position.coerceIn(SUBTITLE_MIN_POSITION, SUBTITLE_MAX_POSITION)
        applySubtitlePosition()
        applyMpvSubtitleOptions()
    }

    private fun applySubtitleScale() {
        val view = resolveSubtitleView()
        if (com.ican.tvplay.BuildConfig.DEBUG) {
            val cues = player?.currentCues?.cues?.size ?: -1
            Log.d(TAG, "applySubtitleScale scale=$subtitleScale view=$view cues=$cues")
        }
        if (view == null) return
        // media3 默认 applyEmbeddedFontSizes=true，SSA/ASS 等带内嵌样式字幕的 textSize
        // 会覆盖 setFractionalTextSize 设置的 defaultTextSize。
        // 非默认字号时禁用内嵌字体大小，让 defaultTextSize 对所有字幕格式生效。
        view.setApplyEmbeddedFontSizes(subtitleScale == 1.0f)
        view.setFractionalTextSize(
            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleScale,
        )
        view.invalidate()
    }

    private fun applySubtitlePosition() {
        val view = resolveSubtitleView()
        if (com.ican.tvplay.BuildConfig.DEBUG) {
            Log.d(TAG, "applySubtitlePosition position=$subtitlePosition view=$view")
        }
        if (view == null) return
        // 用 media3 官方 bottomPaddingFraction 控制字幕垂直位置：
        // 默认 0.08（距底部 8%），范围 0~1。
        // position: -100(贴底) ~ 0(默认) ~ +100(顶部)
        val defaultFraction = SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION
        val fraction = if (subtitlePosition >= 0f) {
            defaultFraction + (subtitlePosition / 100f) * (1.0f - defaultFraction)
        } else {
            defaultFraction + (subtitlePosition / 100f) * defaultFraction
        }
        view.setBottomPaddingFraction(fraction.coerceIn(0f, 1f))
        view.invalidate()
    }

    /**
     * MPV 字幕样式：MPV 由 libmpv 直接渲染字幕，不走 media3 SubtitleView。
     * 当前 mpvplayer AAR 的 MpvSubtitleOptions 只暴露 sub-pos/sub-scale：
     * sub-pos 100=底部默认，0=顶部；我们的 position 正数上移，所以映射为 100-position。
     */
    private fun buildMpvSubtitleOptions(): androidx.media3.mpvplayer.MpvSubtitleOptions {
        val mpvPos = (100f - subtitlePosition).coerceIn(0f, 100f)
        // 固定使用字幕原始样式（ASS 内嵌字体/样式），只暴露 sub-pos/sub-scale 调整。
        return androidx.media3.mpvplayer.MpvSubtitleOptions.Builder()
            .setPosition(mpvPos.toDouble())
            .setScale(subtitleScale.toDouble())
            .build()
    }

    private fun applyMpvSubtitleOptions() {
        val p = player as? MpvPlayer ?: return
        runCatching { p.setSubtitleOptions(buildMpvSubtitleOptions()) }
            .onFailure { Log.e(TAG, "applyMpvSubtitleOptions failed", it) }
    }

    /** 获取当前绑定的 SubtitleView，若引用丢失则尝试从 PlayerView 重新取 */
    private fun resolveSubtitleView(): SubtitleView? {
        subtitleViewRef?.let { return it }
        val pv = playerView ?: return null
        val sv = pv.subtitleView
        if (sv != null) {
            subtitleViewRef = sv
            applySubtitleScale()
            applySubtitlePosition()
        }
        return sv
    }

    /** 绑定 PlayerView 内部的 SubtitleView，并立即应用字号/位置设置（样式固定用字幕原始样式） */
    private fun attachSubtitleView(view: SubtitleView?) {
        if (subtitleViewRef !== view) {
            subtitleViewRef = view
        }
        // EXO 不追 ASS 原生字体/特效：固定回系统字幕样式，再应用我们的字号/位置。
        view?.setApplyEmbeddedStyles(false)
        view?.setUserDefaultStyle()
        applySubtitleScale()
        applySubtitlePosition()
    }

    /** 外挂字幕时间偏移（ms）：重新解析字幕并保持当前播放位置 */
    fun setSubtitleOffset(offsetMs: Long) {
        val value = offsetMs.coerceIn(-SUBTITLE_OFFSET_MAX_MS, SUBTITLE_OFFSET_MAX_MS)
        if (_state.value.subtitleOffsetMs == value) return
        _state.value = _state.value.copy(subtitleOffsetMs = value)
        // 连点 +/- 时只重解析一次：延迟合并，期间只更新状态供 UI 显示
        offsetApplyJob?.cancel()
        offsetApplyJob = scope.launch {
            delay(400)
            val spec = currentSpec ?: return@launch
            if (spec.subs.isEmpty()) return@launch   // 内置字幕轨道无法在解析阶段平移
            val p = player ?: return@launch
            val wasPlaying = p.playWhenReady
            startInternal(p, spec, p.currentPosition)
            if (!wasPlaying) p.pause()   // 调偏移前处于暂停时，重载后保持暂停
        }
    }

    /** 播放页解析阶段（spider playerContent / jx 二次解析）致命错误上报：转错误页提示而不是崩溃 */
    fun notifyError(message: String) {
        Log.e(TAG, "notifyError: $message")
        _state.value = _state.value.copy(error = message)
    }

    /**
     * 清除错误状态（不清播放）：用于丢弃「已被替换的旧源」迟到的错误事件。
     * 旧源（如秒判失败的死代理地址）的错误到达时新源往往已在正常播放，
     * 若不丢弃会把正在播放的好源切走，并引发换线/换站循环。
     */
    fun clearError() {
        if (_state.value.error != null) _state.value = _state.value.copy(error = null)
    }

    /** 退出播放页但继续后台播放（保活开关开启）：解绑视图，播放器保持，转交前台服务 */
    fun detachForBackground() {
        playerView?.let { it.player = null }
        playerView = null
    }

    /** 退出播放页时释放 */
    fun release() {
        probeJob?.cancel()
        probeJob = null
        offsetApplyJob?.cancel()
        offsetApplyJob = null
        startToken++
        handler.removeCallbacks(ticker)
        bandwidthMeter?.removeEventListener(bandwidthListener)
        bandwidthMeter = null
        playerView?.player = null
        playerView = null
        player?.run {
            stop()          // 先 stop 释放媒体源，再 release
            removeListener(listener)
            release()
        }
        player = null
        tieredLoadControl = null
        currentSpec = null
        appContext = null
        playbackContext = null
        autoNextFired = false
        onPlayerChanged?.invoke(null)
        _state.value = _state.value.copy(
            ready = false, playing = false, buffering = false,
            audioTracks = emptyList(), textTracks = emptyList(),
            subtitleOffsetMs = 0L, error = null,
            networkSpeedBps = 0L,
        )
        _progress.value = ProgressState()
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

    /** 直接从 player 读当前位置（不依赖 state，避免 release 重置后读到 0） */
    fun currentPositionMs(): Long = player?.currentPosition?.coerceAtLeast(0L) ?: 0L

    /** 直接从 player 读总时长 */
    fun durationMs(): Long = player?.let { if (it.duration != C.TIME_UNSET) it.duration.coerceAtLeast(0L) else 0L } ?: 0L

    fun seekTo(positionMs: Long) {
        val dur = durationMs().takeIf { it > 0 } ?: Long.MAX_VALUE
        player?.seekTo(positionMs.coerceIn(0L, dur))
    }

    fun setSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    fun setScaleMode(mode: ScaleMode) {
        _state.value = _state.value.copy(scaleMode = mode)
    }

    /** 当前播放信息（「其他」外部播放器跳转用，仿 fongmi PlaybackIntent.choose 的入参） */
    data class ExternalPlayInfo(
        val url: String,
        val headers: Map<String, String>,
        val positionMs: Long,
        val title: String,
    )

    fun externalPlayInfo(): ExternalPlayInfo? {
        val spec = currentSpec ?: return null
        if (spec.url.isBlank()) return null
        return ExternalPlayInfo(
            url = spec.url,
            headers = spec.headers,
            positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            title = playbackContext?.video?.title ?: "",
        )
    }

    // ---- 内核 / 解码 / 缓冲（需重建播放器） ----

    fun setKernel(kernel: Kernel) {
        if (_state.value.kernel == kernel) return
        _state.value = _state.value.copy(kernel = kernel)
        rebuild()
    }

    fun setDecode(decode: Decode) {
        if (_state.value.decode == decode) return
        _state.value = _state.value.copy(decode = decode)
        rebuild()
    }

    fun setBufferTier(tier: BufferTier) {
        if (_state.value.bufferTier == tier) return
        _state.value = _state.value.copy(bufferTier = tier)
        // 热切换缓冲档位：只替换 LoadControl 的缓冲时长策略，绝不重建播放器——
        // 播放不暂停、已缓冲数据不重置；播放头消耗到切换时的缓冲边缘后才按新档位继续缓冲。
        // MPV 内核没有 LoadControl 概念，档位在下次构建播放器时自然生效。
        val p = player
        val lc = tieredLoadControl
        if (p is ExoPlayer && lc != null) {
            val remainingUs = (p.bufferedPosition - p.currentPosition).coerceAtLeast(0L) * 1000L
            lc.setTier(tier, remainingUs)
        }
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
            if (trackType == C.TRACK_TYPE_TEXT) {
                // 手动选择外挂/内封字幕前清掉旧文本轨 override，避免多文本轨同时被选中
                builder.clearOverridesOfType(trackType)
            }
            builder.setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, listOf(option.trackIndex)),
            )
        } else {
            builder.clearOverridesOfType(trackType)
        }
        p.trackSelectionParameters = builder.build()
        listener.onTracksChanged(p.currentTracks)
    }

    /** 动态添加外挂字幕：content:// 先落成缓存文件；MPV 直接 addSubtitle，EXO 重建 MediaItem 保持当前播放位置 */
    fun addSubtitle(sub: SubItem) {
        val spec = currentSpec ?: return
        val p = player ?: return
        val pos = p.currentPosition
        val resolved = resolveExternalSubtitle(sub)
        if (com.ican.tvplay.BuildConfig.DEBUG) {
            Log.d(TAG, "addSubtitle url=${sub.url} resolved=${resolved.url} pos=$pos mime=${resolved.mimeType} kernel=${_state.value.kernel}")
        }
        val newSpec = spec.copy(subs = spec.subs.filterNot { it.url == sub.url || it.url == resolved.url } + resolved)
        currentSpec = newSpec
        // 选择/导入字幕后确保文本轨道未被禁用（否则 override 选中也不会出字幕）
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        _state.value = _state.value.copy(textDisabled = false)
        pendingSelectExternalSubtitle = true

        // fongmi MpvPlayerEngine.addSubtitle：MPV 播放中直接挂载外挂字幕，不重建视频源。
        // 只有 direct add 失败（idle/ended/native 拒绝）时才回退到重建 MediaItem。
        if (p is MpvPlayer && p.currentMediaItem != null &&
            p.playbackState != Player.STATE_IDLE && p.playbackState != Player.STATE_ENDED
        ) {
            val added = runCatching { p.addSubtitle(MediaItemFactory.buildMpvSubConfig(resolved)) }
                .onFailure { Log.e(TAG, "mpv direct addSubtitle failed ${resolved.url}", it) }
                .getOrDefault(false)
            if (added) {
                Log.d(TAG, "mpv direct addSubtitle ok ${resolved.url}")
                applyMpvSubtitleOptions()
                // mpvplayer 的 addSubtitle 在 active file 分支会吞掉 native false 仍返回 true；
                // 延迟校验文本轨真的出现，否则回退重建（已用 file:// 缓存，位置由 startInternal 恢复）。
                val verifyToken = ++mpvSubtitleVerifyToken
                handler.postDelayed({
                    if (verifyToken != mpvSubtitleVerifyToken) return@postDelayed
                    val cur = player as? MpvPlayer ?: return@postDelayed
                    val hasExternalTrack = cur.currentTracks.groups.any { g ->
                        g.type == C.TRACK_TYPE_TEXT &&
                            (0 until g.length).any { i -> g.getTrackFormat(i).label == resolved.name }
                    }
                    if (!hasExternalTrack) {
                        Log.w(TAG, "mpv direct addSubtitle no text track, fallback rebuild ${resolved.url}")
                        startInternal(cur, currentSpec ?: newSpec, cur.currentPosition)
                    }
                }, 800L)
                return
            }
            Log.w(TAG, "mpv direct addSubtitle rejected, fallback to rebuild ${resolved.url}")
        }
        startInternal(p, newSpec, pos)
    }

    /**
     * SAF content:// 字幕对 EXO/MPV 都不稳定：EXO 可能被默认 UA 的 HttpDataSource 当成 http 打开，
     * MPV native 也读不了 content 协议。导入时复制到 app cache，后续切内核/重载都用 file://，无需重新导入。
     */
    private fun resolveExternalSubtitle(sub: SubItem): SubItem {
        val uri = android.net.Uri.parse(sub.url)
        if (uri.scheme != "content") return sub
        val ctx = appContext ?: return sub
        return runCatching {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching sub
            val lowerName = sub.name.lowercase()
            val ext = when {
                sub.mimeType == MimeTypes.TEXT_SSA || lowerName.endsWith(".ass") || lowerName.endsWith(".ssa") -> ".ass"
                sub.mimeType == MimeTypes.TEXT_VTT || lowerName.endsWith(".vtt") -> ".vtt"
                sub.mimeType == MimeTypes.APPLICATION_TTML || lowerName.endsWith(".ttml") || lowerName.endsWith(".xml") -> ".ttml"
                else -> ".srt"
            }
            val dir = java.io.File(ctx.cacheDir, "subtitles").apply { mkdirs() }
            // 文件名别带前导 '-'（hashCode 可能为负），避免 mpv/libass 对路径参数解析边界情况
            val file = java.io.File(dir, "${Integer.toHexString(sub.url.hashCode())}$ext")
            file.writeBytes(bytes)
            sub.copy(
                url = android.net.Uri.fromFile(file).toString(),
                mimeType = sub.mimeType ?: MediaItemFactory.guessSubtitleMime(file.name),
            )
        }.onSuccess {
            Log.d(TAG, "resolveExternalSubtitle ${sub.url} -> ${it.url}")
        }.onFailure {
            Log.e(TAG, "resolveExternalSubtitle failed ${sub.url}", it)
        }.getOrDefault(sub)
    }

    // ---- 内部实现 ----

    private fun ensurePlayer(): Player {
        player?.let { return it }
        val ctx = appContext ?: error("Players.start() 未先调用")
        val p = buildPlayer(ctx, _state.value.kernel, _state.value.decode, _state.value.bufferTier)
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

    /** 仿 fongmi PlayerEngineFactory + ExoUtil.buildPlayer：按内核创建 ExoPlayer 或 MpvPlayer */
    private fun buildPlayer(ctx: Context, kernel: Kernel, decode: Decode, tier: BufferTier): Player {
        if (kernel == Kernel.MPV) {
            // libmpv so 仅 arm64-v8a / armeabi-v7a；不可用（x86 模拟器等）自动回退 EXO
            val available = runCatching { MpvPlayer.isAvailable() }.getOrDefault(false)
            if (!available) {
                Log.w(TAG, "MPV not available, fallback to EXO")
                _state.value = _state.value.copy(kernel = Kernel.EXO)
            } else {
                // fongmi PlayerEngine.HARD/SOFT → mpv hwdec（C.DECODE_HARDWARE=1 / DECODE_SOFTWARE=0，官方 C 无常量，直接传值）
                val mpvDecode = if (decode == Decode.SOFT) 0 else 1
                return try {
                    val config = buildMpvConfig(ctx)
                    MpvPlayer.Builder(ctx).setDecode(mpvDecode).setConfig(config).build().apply {
                        addListener(listener)
                        setPlaybackSpeed(_state.value.speed)
                        setSubtitleOptions(buildMpvSubtitleOptions())
                        playWhenReady = true
                    }
                } catch (t: Throwable) {
                    // MPV native 初始化失败（如缺少配置目录/证书等），回退 EXO 避免闪退
                    Log.e(TAG, "MPV build failed, fallback to EXO", t)
                    _state.value = _state.value.copy(kernel = Kernel.EXO)
                    buildExoPlayer(ctx, decode, tier)
                }
            }
        }
        return buildExoPlayer(ctx, decode, tier)
    }

    /**
     * 构建 MpvPlayerConfig（fongmi MpvUtil.buildConfig 简化版）：
     * 配置目录（mpv.conf 读取）+ shader 缓存 + Android 默认选项，
     * 缺少这些配置时 MPV native 初始化可能崩溃。
     */
    private fun buildMpvConfig(ctx: Context): androidx.media3.mpvplayer.MpvPlayerConfig {
        val mpvDir = java.io.File(ctx.filesDir, "mpv").apply { mkdirs() }
        val mpvCacheDir = java.io.File(ctx.cacheDir, "mpv").apply { mkdirs() }
        // fongmi MpvUtil.buildConfig 同款：指定 CA 证书包，mpv(curl) 拉流 TLS 校验必需，
        // 否则报 "TLS certificate verification failed" 所有 https 分片打不开
        val caFile = java.io.File(ctx.filesDir, "cacert.pem")
        val builder = androidx.media3.mpvplayer.MpvPlayerConfig.Builder()
            .addConfigDirectory(mpvDir)
            .addTlsCaFileFromAsset(ctx, "cacert.pem", caFile)
            .addAndroidSubtitleOptions(ctx, buildMpvSubtitleOptions())
            .addAndroidDefaults(
                androidx.media3.mpvplayer.MpvAndroidOptions.Builder()
                    .setShaderCacheDirectory(mpvCacheDir)
                    .setAudioPassthroughEnabled(false)
                    .setVulkanEnabled(false)
                    .setGpuNextEnabled(false)
                    .build(),
            )
        return builder.build()
    }

    /** 仿 fongmi ExoUtil.buildPlayer/buildLoadControl/buildRenderersFactory 的官方 media3 等价实现 */
    private fun buildExoPlayer(ctx: Context, decode: Decode, tier: BufferTier): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(ctx)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                if (decode == Decode.SOFT) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
            )
        // MediaSourceFactory：统一处理 headers 和字幕偏移解析器
        val mediaSourceFactory = DefaultMediaSourceFactory(ctx)
            .setSubtitleParserFactory(SubtitleOffsetParserFactory { _state.value.subtitleOffsetMs })
        // 缓冲：默认时长 × 档位倍率（仿 fongmi buffer 设置）。
        // 用支持热切换的 TieredLoadControl 包装：切换档位不重建播放器，播放头追上旧缓冲边缘后换策略
        val loadControl = TieredLoadControl(tier)
        tieredLoadControl = loadControl
        // 带宽计：缓冲时实时网速显示
        val meter = DefaultBandwidthMeter.Builder(ctx)
            .build()
            .apply { addEventListener(handler, bandwidthListener) }
        bandwidthMeter = meter
        return ExoPlayer.Builder(ctx, renderersFactory)
            .setTrackSelector(DefaultTrackSelector(ctx))
            .setLoadControl(loadControl)
            .setBandwidthMeter(meter)
            .setMediaSourceFactory(mediaSourceFactory)
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
                if (com.ican.tvplay.BuildConfig.DEBUG) {
                    // 字幕链路诊断：确认外挂字幕是否真正发起加载/读取成功/产生 cues
                    addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                        private fun isTextLike(mime: String?): Boolean {
                            return mime == "application/x-media3-cues" ||
                                mime?.startsWith("text/") == true ||
                                mime == MimeTypes.APPLICATION_SUBRIP ||
                                mime == MimeTypes.APPLICATION_TTML
                        }

                        override fun onLoadStarted(
                            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                            loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                            mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
                        ) {
                            val mime = mediaLoadData.trackFormat?.sampleMimeType
                            if (isTextLike(mime)) {
                                Log.d(TAG, "exoLoad start uri=${loadEventInfo.dataSpec.uri} mime=$mime")
                            }
                        }

                        override fun onLoadCompleted(
                            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                            loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                            mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
                        ) {
                            val mime = mediaLoadData.trackFormat?.sampleMimeType
                            if (isTextLike(mime)) {
                                Log.d(TAG, "exoLoad done uri=${loadEventInfo.dataSpec.uri} bytes=${loadEventInfo.bytesLoaded} mime=$mime")
                            }
                        }

                        override fun onLoadError(
                            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                            loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                            mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
                            error: java.io.IOException,
                            wasCanceled: Boolean,
                        ) {
                            val mime = mediaLoadData.trackFormat?.sampleMimeType
                            if (isTextLike(mime) || loadEventInfo.dataSpec.uri.scheme == "content") {
                                Log.e(TAG, "exoLoad error uri=${loadEventInfo.dataSpec.uri} mime=$mime canceled=$wasCanceled", error)
                            }
                        }

                        override fun onCues(
                            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                            cueGroup: androidx.media3.common.text.CueGroup,
                        ) {
                            Log.d(TAG, "exoCues size=${cueGroup.cues.size} first=${cueGroup.cues.firstOrNull()?.text}")
                        }
                    })
                }
                playWhenReady = true
                addListener(listener)
                setPlaybackSpeed(_state.value.speed)
            }
    }

    /** 仿 fongmi LangUtil.getPreferredTextLanguages：按系统语言生成首选字幕语言列表 */
    private fun preferredTextLanguages(): Array<String> {
        val locale = java.util.Locale.getDefault()
        val tag = locale.toLanguageTag()
        val language = locale.language
        return if (tag == language) arrayOf(language) else arrayOf(tag, language)
    }

    private fun startInternal(p: Player, spec: PlaySpec, positionMs: Long) {
        Log.d(TAG, "startInternal kernel=${_state.value.kernel} url=${spec.url.take(120)} headers=${spec.headers.keys}")
        autoNextFired = false   // 新一集重新武装后台片尾自动切集
        // 换源前先 stop 释放上一个 MediaSource，避免资源冲突导致无声/崩溃
        p.stop()
        p.clearMediaItems()
        // 挂上片名/集名元数据：通知栏（Android 13+ 系统媒体模板）与媒体控件展示用
        val metaTitle = playbackContext?.video?.title
        val metaArtist = playbackContext?.episodes
            ?.getOrNull(playbackContext?.episodeIndex ?: -1)?.title
        // headers 同时写入 RequestMetadata.extras：MPV 内核（native 拉流）由此读取 UA/Referer/http-header-fields
        val extras = android.os.Bundle().apply {
            spec.headers.forEach { (k, v) -> putString(k, v) }
        }
        val baseItem = if (p is MpvPlayer) MediaItemFactory.fromMpv(spec) else MediaItemFactory.from(spec)
        val item = baseItem.buildUpon()
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(metaTitle)
                    .setArtist(metaArtist?.takeIf { it.isNotBlank() } ?: "视频播放中")
                    .build(),
            )
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setExtras(extras).build(),
            )
            .build()
        val headers = MediaItemFactory.checkUa(spec.headers)
        var seekAfterSet = positionMs > 0L
        if (p is ExoPlayer) {
            val ctx = appContext
            if (ctx != null) {
                // EXO：http(s) 走 DefaultHttpDataSource 注入 UA/headers；content/file 由 DefaultDataSource 分发，
                // 避免外挂字幕 content:// 被 HttpDataSource 当成 http 打开（unknown protocol: content）。
                val httpDs = DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(headers)
                    .setAllowCrossProtocolRedirects(true)
                val ds = DefaultDataSource.Factory(ctx, httpDs)
                p.setMediaSource(
                    DefaultMediaSourceFactory(ds)
                        .setSubtitleParserFactory(SubtitleOffsetParserFactory { _state.value.subtitleOffsetMs })
                        .createMediaSource(item),
                )
            } else {
                p.setMediaItem(item)
            }
        } else {
            // MPV：seekTo(prepare 前) 不生效，必须用 setMediaItem(item, startPositionMs) 带起始进度，
            // 否则 EXO→MPV 切内核会从头播放。
            if (positionMs > 0L) {
                p.setMediaItem(item, positionMs)
                seekAfterSet = false
            } else {
                p.setMediaItem(item)
            }
        }
        if (seekAfterSet) p.seekTo(positionMs)
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
        mpvSubtitleVerifyToken++   // 取消上一次 MPV 动态挂字幕的延迟校验
        handler.removeCallbacks(ticker)
        bandwidthMeter?.removeEventListener(bandwidthListener)
        bandwidthMeter = null
        playerView?.player = null   // 先解绑旧 player
        old.stop()           // 先 stop 释放媒体资源
        old.removeListener(listener)
        old.release()
        player = null
        val p = buildPlayer(ctx, _state.value.kernel, _state.value.decode, _state.value.bufferTier)
        player = p
        handler.post(ticker)
        onPlayerChanged?.invoke(p)
        // 重新绑定到 PlayerView（PlayerView 内部自管 surface）
        playerView?.let { it.player = p }
        if (spec != null) {
            retryCount = 0    // 重建后重置错误计数
            // 已导入外挂字幕时切内核/解码：重建后自动选中同一条外挂字幕，无需重新导入
            if (spec.subs.isNotEmpty()) pendingSelectExternalSubtitle = true
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
                if (trackType == C.TRACK_TYPE_TEXT) {
                    // 隐藏 EXO 里常见的无标识字幕轨（如内嵌 CEA-608：label/language 都为空，UI 只显示 Unknown）
                    val noIdentity = format.label == null &&
                        (format.language == null || format.language == "und")
                    if (noIdentity) continue
                }
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

/**
 * 支持缓冲档位热切换的 LoadControl：
 * 切换档位时不重建 ExoPlayer（播放不暂停、已缓冲数据不清空），
 * 先记录切换瞬间「剩余已缓冲时长」，播放头消耗到该缓冲边缘后，才启用新档位的 DefaultLoadControl；
 * 等待期间也不允许把缓冲扩到快照边缘之外（旧档位不再多缓冲）。
 * 期间若换集/重新 prepare（旧缓冲已无意义），立即启用新档位。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class TieredLoadControl(initialTier: Players.BufferTier) : LoadControl {

    private var active: DefaultLoadControl = buildDelegate(initialTier)
    private var preparedPlayerId: PlayerId? = null
    private var lastTracks: TracksSnapshot? = null

    /** 待生效的新档位委托；null 表示无挂起切换 */
    private var pending: DefaultLoadControl? = null
    /** 切换瞬间「缓冲边缘 − 播放位置」剩余时长（us） */
    private var pendingRemainingUs: Long = C.TIME_UNSET

    private data class TracksSnapshot(
        val params: LoadControl.Parameters,
        val trackGroups: TrackGroupArray,
        val trackSelections: Array<ExoTrackSelection>,
    )

    /**
     * @param remainingBufferedUs 切换瞬间 bufferedPosition − currentPosition（us）
     */
    fun setTier(tier: Players.BufferTier, remainingBufferedUs: Long) {
        // 连续切换：旧 pending 从未接收过回调，直接丢弃即可
        pending = buildDelegate(tier)
        pendingRemainingUs = remainingBufferedUs.coerceAtLeast(0L)
    }

    private fun activate(next: DefaultLoadControl) {
        // 新委托此前没接收过任何生命周期回调，按当前轨道状态补齐，保证 targetBufferBytes 已计算
        preparedPlayerId?.let { next.onPrepared(it) }
        lastTracks?.let { next.onTracksSelected(it.params, it.trackGroups, it.trackSelections) }
        active = next
        pending = null
        pendingRemainingUs = C.TIME_UNSET
    }

    override fun onPrepared(playerId: PlayerId) {
        preparedPlayerId = playerId
        // 新一轮 prepare（换集/重新加载）：旧缓冲随新媒体源失效，挂起的新档位立即生效
        pending?.let {
            active = it
            pending = null
            pendingRemainingUs = C.TIME_UNSET
        }
        active.onPrepared(playerId)
    }

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<ExoTrackSelection>,
    ) {
        lastTracks = TracksSnapshot(parameters, trackGroups, trackSelections)
        active.onTracksSelected(parameters, trackGroups, trackSelections)
    }

    override fun onStopped(playerId: PlayerId) {
        active.onStopped(playerId)
    }

    override fun onReleased(playerId: PlayerId) {
        active.onReleased(playerId)
        pending = null
        pendingRemainingUs = C.TIME_UNSET
    }

    override fun getAllocator(playerId: PlayerId): Allocator = active.getAllocator(playerId)

    override fun getBackBufferDurationUs(playerId: PlayerId): Long =
        active.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean =
        active.retainBackBufferFromKeyframe(playerId)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean =
        active.shouldStartPlayback(parameters)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        val next = pending
        if (next != null) {
            // 播放头已消耗到切换时的缓冲边缘（或期间 seek 导致剩余缓冲被跳空）→ 启用新档位
            if (parameters.bufferedDurationUs <= ACTIVATE_THRESHOLD_US) {
                activate(next)
                return active.shouldContinueLoading(parameters)
            }
            // 等待期间：已缓冲到快照边缘就停止加载，旧档位不再向外扩缓冲
            if (parameters.bufferedDurationUs >= pendingRemainingUs - ACTIVATE_THRESHOLD_US) {
                return false
            }
        }
        return active.shouldContinueLoading(parameters)
    }

    override fun shouldContinuePreloading(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        positionUs: Long,
    ): Boolean = active.shouldContinuePreloading(playerId, timeline, mediaPeriodId, positionUs)

    private companion object {
        /** 距缓冲边缘 ≤1s 即视为已追上（避免临界点抖动） */
        const val ACTIVATE_THRESHOLD_US = 1_000_000L

        /** EXO 单个播放器允许占用的缓冲字节上限（4K 高码率时按时长算会算出数百 MB） */
        const val TARGET_BUFFER_BYTES = 64 * 1024 * 1024

        /**
         * 缓冲档位只放大「最大缓冲时长」，最小缓冲固定 10s。
         * default 的 min/max 都是 50s：4K 原盘（40Mbps 左右）意味着开播前要先缓存约 250MB，
         * 手机堆根本放不下，表现就是「一直转圈、网速在跑、然后 OOM 闪退」。
         */
        fun buildDelegate(tier: Players.BufferTier): DefaultLoadControl =
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    10_000,
                    (30_000L * tier.multiplier).coerceAtMost(60_000L).toInt(),
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                )
                // 高码率片源再叠加字节上限，避免单纯按「时长」算出几百 MB 的缓冲预算
                .setTargetBufferBytes(TARGET_BUFFER_BYTES)
                .build()
    }
}
