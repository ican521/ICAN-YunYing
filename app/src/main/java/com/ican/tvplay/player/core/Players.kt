package com.ican.tvplay.player.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private var subtitleView: SubtitleView? = null
    private var retryCount = 0

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
            handler.postDelayed(this, 500L)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(playing = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = _state.value.copy(
                buffering = playbackState == Player.STATE_BUFFERING,
                ready = playbackState >= Player.STATE_READY,
            )
        }

        override fun onTracksChanged(tracks: Tracks) {
            _state.value = _state.value.copy(
                audioTracks = collectOptions(tracks, C.TRACK_TYPE_AUDIO),
                textTracks = collectOptions(tracks, C.TRACK_TYPE_TEXT),
                textDisabled = player?.trackSelectionParameters?.disabledTrackTypes
                    ?.contains(C.TRACK_TYPE_TEXT) == true,
            )
        }

        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            subtitleView?.setCues(cueGroup.cues)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "onPlayerError: ${error.errorCodeName}", error)
            if (handleError(error)) return
            _state.value = _state.value.copy(error = error.errorCodeName)
        }
    }

    // ---- 生命周期 ----

    /** 挂接视图：Activity 重建（横竖屏切换）后重新调用即可，播放不中断 */
    fun attach(surfaceView: SurfaceView, subView: SubtitleView) {
        subtitleView = subView
        player?.setVideoSurfaceView(surfaceView)
    }

    /** 视图销毁时调用：只解绑 surface，不释放播放器 */
    fun detach(surfaceView: SurfaceView) {
        if (subtitleView != null) subtitleView = null
        player?.clearVideoSurfaceView(surfaceView)
    }

    /** 开始播放（或换源/换线路） */
    fun start(context: Context, spec: PlaySpec, startPositionMs: Long = 0L) {
        appContext = context.applicationContext
        currentSpec = spec
        retryCount = 0
        val p = ensurePlayer()
        startInternal(p, spec, startPositionMs)
    }

    /** 退出播放页时释放 */
    fun release() {
        handler.removeCallbacks(ticker)
        player?.release()
        player = null
        currentSpec = null
        appContext = null
        subtitleView = null
        _state.value = _state.value.copy(
            ready = false, playing = false, buffering = false,
            positionMs = 0L, durationMs = 0L, bufferedMs = 0L,
            audioTracks = emptyList(), textTracks = emptyList(), error = null,
        )
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
                setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
                setHandleAudioBecomingNoisy(true)
                playWhenReady = true
                addListener(listener)
                setPlaybackSpeed(_state.value.speed)
            }
    }

    private fun startInternal(p: ExoPlayer, spec: PlaySpec, positionMs: Long) {
        val item = MediaItemFactory.from(spec)
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
        old.release()
        player = null
        val p = buildPlayer(ctx, _state.value.decode, _state.value.bufferTier)
        player = p
        handler.post(ticker)
        if (spec != null) {
            startInternal(p, spec, pos)
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
                player?.let { startInternal(it, currentSpec!!, it.currentPosition) }
                return true
            }
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            -> {
                val spec = currentSpec ?: return false
                if (spec.format == null) return false
                retryCount++
                Log.w(TAG, "manifest error, retry without mimeType")
                currentSpec = spec.copy(format = null)
                player?.let { startInternal(it, currentSpec!!, it.currentPosition) }
                return true
            }
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                player?.seekToDefaultPosition()
                player?.prepare()
                return true
            }
        }
        return false
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
