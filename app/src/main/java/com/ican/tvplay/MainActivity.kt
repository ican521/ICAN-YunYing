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

    /** 全屏播放中切后台（Home/上滑）→ 自动进入画中画（后台播放选「画中画」时） */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!Players.isFullscreen) return
        val mode = (application as TvPlayApplication)
            .container.settingsRepository.playerBackgroundPlay.value
        // 仅「画中画」模式自动进入小窗
        if (mode != BACKGROUND_PIP) return
        // 需求：切后台进小窗时暂停播放，不让画面在桌面继续播。
        // 此处 Activity 进 PiP 后仍可见，paused 的前台服务不会被 MIUI 立即回收
        // （与 PlayerScreen 生命周期里「后台不暂停」的既有约束不冲突）
        Players.onPause()
        try {
            // 保持全屏时的横屏请求不释放（fongmi 同款做法）：PiP 任务沿用横屏配置，
            // 点击展开小窗时系统直接放大为横屏全屏；若进小窗前释放方向，
            // 展开会先按竖屏放大再旋转，动画割裂。
            // sourceRectHint 提供进入/退出小窗的平滑缩放动画目标
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(videoAspectRational())
                .setActions(buildPipActions())
                .setSourceRectHint(Rect().apply { window.decorView.getGlobalVisibleRect(this) })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setSeamlessResizeEnabled(true)
            }
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

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Players.setPipMode(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            wasInPip = true
            // 兜底：非全屏（预览小窗）下进了 PiP（个别 ROM 无视 setAutoEnterEnabled(false)），
            // 必须暂停播放——小窗状态不允许在桌面继续播；PlayerScreen 的 onPause 对 PiP 态是放行的
            if (!Players.isFullscreen) Players.onPause()
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
                // 退出 PiP：仍处全屏 → 手机恢复横屏；否则手机锁回竖屏，平板自由方向
                val smallScreen = resources.configuration.screenWidthDp < 600
                requestedOrientation = when {
                    Players.isFullscreen ->
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    smallScreen -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
        try {
            setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setActions(buildPipActions())
                    .build(),
            )
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
