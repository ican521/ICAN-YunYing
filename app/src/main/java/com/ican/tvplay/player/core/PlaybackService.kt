package com.ican.tvplay.player.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ican.tvplay.MainActivity
import com.ican.tvplay.data.BACKGROUND_OFF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 播放前台服务（设置开关「播放保活」开启时使用），fongmi PlaybackService 对齐：
 * - 播放器实例仍在 Players 单例，本服务持有 MediaSession 挂载该实例
 * - 退出播放页后播放器不销毁，转交本服务后台续播（横竖屏重建/切后台不断播）
 * - MediaStyle 通知栏控制：播放/暂停、上一集/下一集、进度条、点击回播放页
 * - 周期持久化后台播放进度；通知划掉 / session 停止时释放播放器
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSession? = null
    private var stateJob: Job? = null

    private val sessionCallback = object : MediaSession.Callback {
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SKIP_PREV -> Players.skipEpisode(-1)
                ACTION_SKIP_NEXT -> Players.skipEpisode(+1)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // player 实例变化（解码/缓冲切换重建）时同步给 MediaSession
        Players.onPlayerChanged = { p ->
            if (p != null) mediaSession?.player = p
        }
        // 后台续播期间周期持久化进度（页面退出后由本服务接管）
        scope.launch {
            while (isActive) {
                delay(30_000L)
                if (Players.isAlive()) Players.persistProgressIfNeeded()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Players.release()
                stopSelf()
                return START_NOT_STICKY
            }
            // Android 13 以下通知按钮走 broadcast action
            ACTION_PLAY_PAUSE -> Players.playPause()
            ACTION_SKIP_PREV -> Players.skipEpisode(-1)
            ACTION_SKIP_NEXT -> Players.skipEpisode(+1)
        }
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
        attachSession()
        observeState()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Players.onPlayerChanged = null
        mediaSession?.release()
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    /** 把 Players.player 挂到 MediaSession（首次创建，之后实例变化仅 setPlayer） */
    private fun attachSession() {
        val p = Players.player ?: return
        val s = mediaSession
        if (s == null) {
            mediaSession = MediaSession.Builder(this, p)
                .setSessionActivity(openAppPendingIntent())
                .setCallback(sessionCallback)
                .setCustomLayout(
                    listOf(
                        CommandButton.Builder()
                            .setDisplayName("上一集")
                            .setIconResId(android.R.drawable.ic_media_previous)
                            .setSessionCommand(SessionCommand(ACTION_SKIP_PREV, Bundle.EMPTY))
                            .build(),
                        CommandButton.Builder()
                            .setDisplayName("下一集")
                            .setIconResId(android.R.drawable.ic_media_next)
                            .setSessionCommand(SessionCommand(ACTION_SKIP_NEXT, Bundle.EMPTY))
                            .build(),
                    ),
                )
                .build()
        } else {
            s.player = p
        }
    }

    /** 观察播放上下文/播放状态变化：更新通知文案与播放暂停图标（进度条由系统经 session 自动同步） */
    private fun observeState() {
        if (stateJob?.isActive == true) return
        stateJob = scope.launch {
            var lastKey = ""
            kotlinx.coroutines.flow.combine(
                Players.playbackContextFlow,
                Players.state,
            ) { ctx, st -> ctx to st }.collect { (ctx, st) ->
                val key = "${ctx?.video?.id}|${ctx?.episodeIndex}|${st.playing}"
                if (key != lastKey) {
                    lastKey = key
                    notify(buildNotification())
                }
            }
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "正在播放",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        }
    }

    private fun buildNotification(): Notification {
        val ctx = Players.playbackContext
        val playing = Players.state.value.playing
        val title = ctx?.video?.title ?: "ICAN云影"
        val text = ctx?.let { c ->
            c.episodes.getOrNull(c.episodeIndex)?.title?.takeIf { it.isNotBlank() } ?: "视频播放中"
        } ?: "视频播放中"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppPendingIntent())
            .setDeleteIntent(actionPendingIntent(ACTION_STOP))
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Android 13 以下：手动 action 按钮；13+ 系统改用 session custom layout
            .addAction(android.R.drawable.ic_media_previous, "上一集", actionPendingIntent(ACTION_SKIP_PREV))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "暂停" else "播放",
                actionPendingIntent(ACTION_PLAY_PAUSE),
            )
            .addAction(android.R.drawable.ic_media_next, "下一集", actionPendingIntent(ACTION_SKIP_NEXT))

        mediaSession?.let {
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(it).setShowActionsInCompactView(0, 1, 2),
            )
        }
        return builder.build()
    }

    private fun notify(notification: Notification) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_PLAYER, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionPendingIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL_ID = "playback_keepalive"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.ican.tvplay.PLAY_PAUSE"
        const val ACTION_SKIP_PREV = "com.ican.tvplay.SKIP_PREV"
        const val ACTION_SKIP_NEXT = "com.ican.tvplay.SKIP_NEXT"
        const val ACTION_STOP = "com.ican.tvplay.STOP"
        const val EXTRA_OPEN_PLAYER = "extra_open_player"

        /** 进入播放页时调用：后台播放非关闭模式则启动前台服务 */
        fun startIfNeeded(context: Context, mode: Int) {
            if (mode == BACKGROUND_OFF) return
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 退出播放页（保活关闭）或通知栏停止时调用 */
        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }
}
