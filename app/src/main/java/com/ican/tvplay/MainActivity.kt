package com.ican.tvplay

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
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
import androidx.lifecycle.lifecycleScope
import com.ican.tvplay.player.core.PlaybackService
import com.ican.tvplay.player.core.Players
import com.ican.tvplay.data.BACKGROUND_PIP
import com.ican.tvplay.ui.AppRoot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 唯一 Activity，承载全部 Compose 页面。
 * 同时注册 LAUNCHER 与 LEANBACK_LAUNCHER，手机 / 平板 / Android TV 共用同一入口。
 */
class MainActivity : ComponentActivity() {

    private var pipStateJob: Job? = null

    /** 进过画中画且尚未回过前台：onStop 时据此识别「小窗被关闭」 */
    private var wasInPip = false

    override fun onResume() {
        super.onResume()
        // 展开小窗回到前台：清除关闭标记
        wasInPip = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 擦掉小窗重开是热恢复（onRestart，无 onCreate）：搜索框仍持有 Compose 焦点，
        // 窗口重新聚焦时系统自动弹出输入法，adjustResize 随之把界面顶得跳动——
        // fongmi 重开是安静的：窗口聚焦时始终不自动弹键盘（冷启/热恢复/进程恢复全覆盖），
        // 用户点击输入框是显式唤起，不受该状态位影响。
        // 同时强制 ADJUST_NOTHING：部分 ROM（MIUI/HyperOS）在 edge-to-edge 下仍会做
        // 旧式窗口级 IME 避让（把内容区整体抬高一个键盘高），而 miuix OverlayDialog 内部
        // 又用 imePadding 再抬一次，导致导入配置/链接解析弹窗与键盘之间空出约一个键盘高的间隙。
        // 禁止窗口避让后，IME 边衬区照常派发，弹窗内 imePadding 成为唯一一次抬升，全机型一致。
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        // 小屏（手机）禁用自动横向：锁定竖屏，仅播放全屏时由播放页临时请求横屏；平板保持系统方向
        if (resources.configuration.screenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        // targetSdk 35+ 对声明 supportsPictureInPicture 的 Activity 默认开启「手势回桌面自动进 PiP」，
        // 导致平板横屏预览小窗上滑回桌面时被系统塞进画中画、视频继续播放。
        // 显式关闭自动进入：本 App 只在「全屏 + 后台播放=画中画」时由 onUserLeaveHint 主动进 PiP。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                setPictureInPictureParams(
                    PictureInPictureParams.Builder().setAutoEnterEnabled(false).build(),
                )
            }
        }
        handleOpenPlayerIntent(intent)
        setContent {
            AppRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenPlayerIntent(intent)
    }

    /** 通知栏点击 → 恢复播放页（AppRoot 收集 Players.openPlayerRequest 后导航） */
    private fun handleOpenPlayerIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false) == true) {
            Players.requestOpenPlayerFromContext()
        }
    }

    /** fongmi PiP 类同款：整个画中画生命周期复用同一个 builder。
     *  宽高比/矩形/图标全部增量更新，任何一次 setPictureInPictureParams
     *  都不会把已设置的 sourceRectHint 等参数冲掉（新建 builder 会清空 hint，
     *  导致退出放大动画失去目标矩形、先跳到默认小窗位置再放大）。 */
    private val pipBuilder: PictureInPictureParams.Builder? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setSeamlessResizeEnabled(true)
                    // onCreate 里关自动进 PiP 用的是一次性 builder，之后经本 builder
                    // setPictureInPictureParams 会把 autoEnter 冲回默认（targetSdk 35 默认开），
                    // 必须在本 builder 上同步关闭，保证整个小窗生命周期参数一致
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
        // 仅「画中画」模式自动进入小窗
        if (mode != BACKGROUND_PIP) return
        // fongmi 同款：进小窗不暂停，画面持续出帧，退出放大时系统逐帧缩放无残帧
        try {
            // 保持全屏时的横屏请求不释放（fongmi 同款做法）：PiP 任务沿用横屏配置，
            // 点击展开小窗时系统直接放大为横屏全屏；若进小窗前释放方向，
            // 展开会先按竖屏放大再旋转，动画割裂。
            // sourceRectHint 提供进入/退出小窗的平滑缩放动画目标
            builder
                .setAspectRatio(videoAspectRational())
                .setActions(buildPipActions())
                .setSourceRectHint(Rect().apply { window.decorView.getGlobalVisibleRect(this) })
            enterPictureInPictureMode(builder.build())
        } catch (_: Exception) {
        }
    }

    /** 画中画窗口宽高比：fongmi getRational 同款——用视频真实宽高比，钳制在系统允许的 0.418~2.39 之间 */
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

    /** fongmi PiP.update 同款：视频区矩形持续同步给系统，进/出小窗动画（含圆角）按此计算 */
    fun syncPipSourceRect(rect: Rect) {
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
            // fongmi 同款：进小窗不暂停，继续播放
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isInPictureInPictureMode) {
                // 监听播放/暂停状态翻转，实时更新 PiP 播放/暂停图标。
                // 只盯 playing 字段：播放进度每秒都会写 state，
                // 若每次都 setPictureInPictureParams 会造成高频系统调用
                pipStateJob = lifecycleScope.launch {
                    Players.state
                        .map { it.playing }
                        .distinctUntilChanged()
                        .collect { updatePipActions() }
                }
            } else {
                pipStateJob?.cancel()
                pipStateJob = null
                // 退出 PiP：fongmi 同款——全屏展开不碰方向（进全屏时已请求横屏，
                // 放大动画中途重写方向会让 ROM 重算窗口、出现跳位）；
                // 仅非全屏兜底：手机锁回竖屏，平板恢复自由方向
                if (!Players.isFullscreen) {
                    requestedOrientation =
                        if (resources.configuration.screenWidthDp < 600)
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.O)
    private fun buildPipActions(): List<RemoteAction> {
        val playing = Players.state.value.playing
        val actions = ArrayList<RemoteAction>()

        // 播放 / 暂停（根据状态切换图标；自定义纯白矢量，去除系统自带图标的描边）
        actions.add(
            RemoteAction(
                Icon.createWithResource(
                    this,
                    if (playing) R.drawable.ic_pip_pause
                    else R.drawable.ic_pip_play,
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

        return actions
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.O)
    private fun updatePipActions() {
        val builder = pipBuilder ?: return
        try {
            // fongmi 同款：复用同一 builder 仅换图标，保留宽高比与 sourceRectHint
            builder.setActions(buildPipActions())
            setPictureInPictureParams(builder.build())
        } catch (_: Exception) {
        }
    }

    override fun onStop() {
        super.onStop()
        // 画中画小窗被用户关闭（点 X / 上滑划掉）：部分 ROM（如 MIUI）不移除 Activity
        // （isFinishing=false、Compose 不 dispose），无人停播——此处直接保存进度、
        // 释放播放器、停服务，并触发播放页返回（回首页），App 保留在后台热启动。
        // 后台时 Compose 重组挂起，返回导航会在回前台第一帧生效（onDispose 幂等，无副作用）。
        // （进 PiP 时 Activity 保持可见不会 onStop，展开回前台的 onResume 会先清标记，均不误触发）
        if (wasInPip && !isFinishing) {
            // 后台前把 IME 服务端状态清掉：系统侧会记住「应用键盘可见」，重开窗口聚焦时
            // IMMS 自动恢复键盘（结果页顶栏已无输入框，应用侧无 show 请求，纯系统恢复）；
            // 进后台即隐藏，重开无可恢复状态
            (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            // 恢复方向 + 清全屏标记：不解除横屏请求的话，下次打开 App 会先横屏再转竖屏，
            // 且残留的 isFullscreen 会让首页按 Home 误触画中画
            Players.isFullscreen = false
            requestedOrientation = if (resources.configuration.screenWidthDp < 600) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            // 同步恢复状态栏：全屏播放时隐藏的状态栏若不在此处恢复，系统会记住「隐藏」，
            // 重开回前台首帧按无状态栏渲染，播放页退出的恢复调用约 370ms 后才生效，
            // 状态栏 inset 从 0 动画到满高会把整页顶得上下跳动
            androidx.core.view.WindowInsetsControllerCompat(
                window, window.decorView,
            ).show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            Players.persistProgressIfNeeded()
            Players.release()
            PlaybackService.stop(this)
            Players.requestExitPlayer()
        }
    }
}
