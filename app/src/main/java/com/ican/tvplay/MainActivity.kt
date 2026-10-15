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
        android.util.Log.d("Startup", "activity onCreate begin")
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 进程被杀重开恢复页面（如落在搜索页）时，系统会在窗口聚焦时自动弹出输入法，
        // adjustResize 随之把界面顶得跳动——fongmi 重开是安静的：恢复场景强制初始隐藏键盘，
        // 用户点击输入框时仍正常唤起（显式 showSoftInput 不受该状态位影响），正常导航不受影响
        if (savedInstanceState != null) {
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        // 小屏（手机）禁用自动横向：锁定竖屏，仅播放全屏时由播放页临时请求横屏；平板保持系统方向
        if (resources.configuration.screenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        handleOpenPlayerIntent(intent)
        setContent {
            AppRoot()
        }
        android.util.Log.d("Startup", "activity onCreate end")
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
        try {
            // 保持全屏时的横屏请求不释放（fongmi 同款做法）：PiP 任务沿用横屏配置，
            // 点击展开小窗时系统直接放大为横屏全屏；若进小窗前释放方向，
            // 展开会先按竖屏放大再旋转，动画割裂。
            // sourceRectHint 提供进入/退出小窗的平滑缩放动画目标
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setActions(buildPipActions())
                .setSourceRectHint(Rect().apply { window.decorView.getGlobalVisibleRect(this) })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setSeamlessResizeEnabled(true)
            }
            enterPictureInPictureMode(builder.build())
        } catch (_: Exception) {
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Players.setPipMode(isInPictureInPictureMode)
        if (isInPictureInPictureMode) wasInPip = true
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
            // 恢复方向 + 清全屏标记：不解除横屏请求的话，下次打开 App 会先横屏再转竖屏，
            // 且残留的 isFullscreen 会让首页按 Home 误触画中画
            Players.isFullscreen = false
            requestedOrientation = if (resources.configuration.screenWidthDp < 600) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            Players.persistProgressIfNeeded()
            Players.release()
            PlaybackService.stop(this)
            Players.requestExitPlayer()
        }
    }
}
