package com.ican.tvplay

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.res.Configuration
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
import kotlinx.coroutines.launch

/**
 * 唯一 Activity，承载全部 Compose 页面。
 * 同时注册 LAUNCHER 与 LEANBACK_LAUNCHER，手机 / 平板 / Android TV 共用同一入口。
 */
class MainActivity : ComponentActivity() {

    private var pipStateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("Startup", "activity onCreate begin")
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setActions(buildPipActions())
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isInPictureInPictureMode) {
                // 监听播放状态变化，实时更新 PiP 播放/暂停图标
                pipStateJob = lifecycleScope.launch {
                    Players.state.collect { st ->
                        updatePipActions()
                    }
                }
            } else {
                pipStateJob?.cancel()
                pipStateJob = null
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
        // PiP 小窗被用户关闭等 Activity 终止场景：停掉后台播放
        if (isFinishing) {
            Players.release()
            PlaybackService.stop(this)
        }
    }
}
