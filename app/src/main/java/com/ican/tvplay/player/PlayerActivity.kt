package com.ican.tvplay.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.ican.tvplay.R
import com.ican.tvplay.TvPlayApplication
import com.ican.tvplay.data.BACKGROUND_PIP
import com.ican.tvplay.player.core.PlaybackService
import com.ican.tvplay.player.core.Players
import com.ican.tvplay.ui.appContainer
import com.ican.tvplay.ui.theme.LocalDarkTheme
import com.ican.tvplay.ui.theme.TvPlayTheme
import com.ican.tvplay.ui.theme.appMiuixColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 播放页宿主能力：PlayerScreen 通过 `context as? PlayerHost` 反向调用。
 * 播放页独立成 Activity 后，画中画矩形同步与屏幕方向恢复都由宿主实现。
 */
interface PlayerHost {
    fun syncPipSourceRect(rect: Rect)
    fun restoreDefaultOrientation()
}

/** 打开播放页（fongmi 同款：播放页是独立 Activity，与主页导航栈完全解耦） */
object PlayerLauncher {
    fun start(
        context: Context,
        videoId: String,
        episodeIndex: Int,
        flag: String = "",
        preInit: Boolean = false,
        forceStart: Boolean = false,
    ) {
        val intent = Intent(context, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_VIDEO_ID, videoId)
            .putExtra(PlayerActivity.EXTRA_EPISODE_INDEX, episodeIndex)
            .putExtra(PlayerActivity.EXTRA_FLAG, flag)
            .putExtra(PlayerActivity.EXTRA_PRE_INIT, preInit)
            .putExtra(PlayerActivity.EXTRA_FORCE_START, forceStart)
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/**
 * 播放页独立 Activity（对齐 fongmi VideoActivity）：
 * 播放器与 SurfaceView 不再参与主页 m iuix-nav 的 Compose 转场，彻底消除
 * 「返回转场中视频层与页面错位露出上一页」的竖缝问题。
 *
 * 画中画、屏幕方向、窗口底色等原本挂在 MainActivity 上的播放相关逻辑一并迁到这里。
 */
class PlayerActivity : ComponentActivity(), PlayerHost {

    private var pipStateJob: Job? = null
    private var pipVideoSizeListener: Player.Listener? = null
    private var wasInPip = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        // 窗口底色不透明黑：播放页大面积黑底，浅色主题下窗口若为浅色会在转场/圆角处漏白
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK),
        )
        window.setFormat(android.graphics.PixelFormat.OPAQUE)
        // targetSdk 35+ 对手势回桌面默认开启自动进 PiP：播放页由 onUserLeaveHint 显式控制
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                setPictureInPictureParams(
                    PictureInPictureParams.Builder().setAutoEnterEnabled(false).build(),
                )
            }
        }

        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
        if (videoId.isNullOrBlank()) {
            finish()
            return
        }
        val episodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, 0)
        val flag = intent.getStringExtra(EXTRA_FLAG).orEmpty()
        val preInit = intent.getBooleanExtra(EXTRA_PRE_INIT, false)
        val forceStart = intent.getBooleanExtra(EXTRA_FORCE_START, false)

        setContent {
            val container = appContainer()
            val themeMode by container.settingsRepository.themeMode.collectAsStateWithLifecycle()
            val themeColor by container.settingsRepository.themeColor.collectAsStateWithLifecycle()
            TvPlayTheme(themeMode = themeMode, themeColor = themeColor) {
                val isDark = LocalDarkTheme.current
                MiuixTheme(
                    colors = appMiuixColors(themeColor, isDark),
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides MiuixTheme.colorScheme.onBackground,
                    ) {
                        // miuix 根 Scaffold（与 AppRoot 同款）：播放页的 OptionDialog/字幕弹窗等
                        // OverlayDialog 均为 renderInRootScaffold=true，必须渲染在根 Scaffold
                        // 的弹窗宿主层，没有根 Scaffold 时弹窗静默不显示
                        val pipTransition by Players.pipTransition.collectAsStateWithLifecycle()
                        val videoPresent by Players.videoPresent.collectAsStateWithLifecycle()
                        val rootBackground =
                            if (pipTransition || videoPresent) androidx.compose.ui.graphics.Color.Black
                            else MiuixTheme.colorScheme.surface
                        Scaffold(
                            containerColor = rootBackground,
                            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                        ) {
                            PlayerScreen(
                                videoId = videoId,
                                startEpisode = episodeIndex,
                                startFlag = flag,
                                preInitialized = preInit,
                                forceStart = forceStart,
                                onBack = { finish() },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        wasInPip = false
    }

    /** 退出全屏/离开播放页后恢复屏幕方向：统一跟随用户自动旋转设置（fongmi 同款） */
    override fun restoreDefaultOrientation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    }

    /** 整个画中画生命周期复用同一个 builder，避免 setPictureInPictureParams 冲掉 sourceRectHint */
    private val pipBuilder: PictureInPictureParams.Builder? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setSeamlessResizeEnabled(true)
                    setAutoEnterEnabled(false)
                }
            }
        } else null
    }

    /** 全屏播放中切后台（Home/上滑）→ 自动进入画中画（后台播放选「画中画」时） */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val builder = pipBuilder ?: return
        if (!Players.isFullscreen) return
        val mode = (application as TvPlayApplication)
            .container.settingsRepository.playerBackgroundPlay.value
        if (mode != BACKGROUND_PIP) return
        try {
            builder
                .setAspectRatio(videoAspectRational())
                .setActions(buildPipActions())
                .setSourceRectHint(Rect().apply { window.decorView.getGlobalVisibleRect(this) })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                runCatching { builder.setExpandedAspectRatio(videoAspectRational()) }
            }
            enterPictureInPictureMode(builder.build())
        } catch (_: Exception) {
        }
    }

    /** 画中画窗口宽高比：fongmi getRational 同款，钳制在系统允许的 0.418~2.39 之间 */
    private fun videoAspectRational(): Rational {
        val size = Players.player?.videoSize
        val w = size?.width ?: 0
        val h = size?.height ?: 0
        if (w <= 0 || h <= 0) return Rational(16, 9)
        val limitWide = Rational(239, 100)
        val limitTall = Rational(100, 239)
        val rational = Rational(w, h)
        return when {
            rational.toFloat() > limitWide.toFloat() -> limitWide
            rational.toFloat() < limitTall.toFloat() -> limitTall
            else -> rational
        }
    }

    /** fongmi PiP.update 同款：视频区矩形持续同步给系统 */
    override fun syncPipSourceRect(rect: Rect) {
        val builder = pipBuilder ?: return
        try {
            builder.setSourceRectHint(rect)
            setPictureInPictureParams(builder.build())
        } catch (_: Exception) {
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Players.setPipMode(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            wasInPip = true
            // 小窗窗口表面透明：内容必须铺满整个窗口，否则顶部/底部会露出桌面
            runCatching {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                    .hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        } else if (Players.isFullscreen) {
            // 双击小窗放大回全屏：系统会恢复系统栏，浅色手势条在放大动画期间
            // 于屏幕底部闪现一条细白线。全屏播放态下立即重新隐藏系统栏压回去
            runCatching {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                controller.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isInPictureInPictureMode) {
                pipStateJob = lifecycleScope.launch {
                    Players.state
                        .map { it.playing }
                        .distinctUntilChanged()
                        .collect { updatePipActions() }
                }
                pipVideoSizeListener = object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) = updatePipAspect()
                }.also { listener -> Players.player?.addListener(listener) }
            } else {
                pipStateJob?.cancel()
                pipStateJob = null
                pipVideoSizeListener?.let { listener -> Players.player?.removeListener(listener) }
                pipVideoSizeListener = null
            }
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.O)
    private fun buildPipActions(): List<RemoteAction> {
        val playing = Players.state.value.playing
        return listOf(
            RemoteAction(
                Icon.createWithResource(
                    this,
                    if (playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
                ),
                if (playing) "暂停" else "播放", "",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, PlaybackService::class.java)
                        .setAction(PlaybackService.ACTION_PLAY_PAUSE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ),
        )
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.O)
    private fun updatePipAspect() {
        val builder = pipBuilder ?: return
        try {
            builder.setAspectRatio(videoAspectRational())
            setPictureInPictureParams(builder.build())
        } catch (_: Exception) {
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.O)
    private fun updatePipActions() {
        val builder = pipBuilder ?: return
        try {
            builder.setActions(buildPipActions())
            setPictureInPictureParams(builder.build())
        } catch (_: Exception) {
        }
    }

    override fun onStop() {
        super.onStop()
        // 画中画小窗被用户关闭（点 X / 上滑划掉）：保存进度、释放播放器、停服务并关闭本页。
        // 部分 ROM 不移除 Activity（isFinishing=false），必须在此兜底。
        if (wasInPip && !isFinishing) {
            (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            Players.isFullscreen = false
            restoreDefaultOrientation()
            androidx.core.view.WindowInsetsControllerCompat(
                window, window.decorView,
            ).show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            Players.persistProgressIfNeeded()
            Players.release()
            PlaybackService.stop(this)
            finish()
        }
    }

    companion object {
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_EPISODE_INDEX = "extra_episode_index"
        const val EXTRA_FLAG = "extra_flag"
        const val EXTRA_PRE_INIT = "extra_pre_init"
        const val EXTRA_FORCE_START = "extra_force_start"
    }
}
