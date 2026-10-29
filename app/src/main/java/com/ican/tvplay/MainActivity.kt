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
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
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

    /** 小窗期间监听视频尺寸：进小窗瞬间尺寸可能尚未解析，回退 16:9 会一直错下去 */
    private var pipVideoSizeListener: Player.Listener? = null

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
        // 窗口底色强制不透明黑：小窗/过渡期间 Compose 内容可能比窗口少 1px（dp 取整），
        // 窗口底色若为透明，这条缝会直接透出桌面的图标/壁纸。
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK),
        )
        // 实测窗口格式为 TRANSLUCENT（系统/edge-to-edge 造成），即使铺了黑色背景，
        // 1px 的结构缝仍会透出壁纸，表现为小窗底部/放大过程中的白线。强制不透明表面。
        window.setFormat(android.graphics.PixelFormat.OPAQUE)
        // 小屏（手机）禁用自动横向：锁定竖屏，仅播放全屏时由播放页临时请求横屏；平板保持系统方向。
        // 必须按「物理屏幕」判断，不能用 resources.configuration.screenWidthDp：
        // Activity 处于小窗或被信箱化时，该值是小窗/信箱宽度（实测平板 548dp < 600），
        // 会把自己锁成竖屏 → 系统再信箱化成竖条 → 下次启动又读到 548dp，越锁越死。
        if (isSmallScreenDevice()) {
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

    /** 是否小屏设备（手机）：按物理屏幕短边 dp 判断，不受小窗/信箱化窗口尺寸影响 */
    private fun isSmallScreenDevice(): Boolean {
        val bounds: Rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds
        } else {
            val point = android.graphics.Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            Rect(0, 0, point.x, point.y)
        }
        val shortSideDp = minOf(bounds.width(), bounds.height()) / resources.displayMetrics.density
        return shortSideDp < 600
    }

    /** 用户是否开着自动旋转（即没锁方向）。只读系统设置，无需任何权限。 */
    private fun isAutoRotate(): Boolean = runCatching {
        android.provider.Settings.System.getInt(
            contentResolver,
            android.provider.Settings.System.ACCELEROMETER_ROTATION,
            1,
        ) == 1
    }.getOrDefault(true)

    /** 平板恢复方向：用户开着自动旋转才跟随系统；锁着方向时按当前实际方向恢复。
     *  绝不能再返回 SCREEN_ORIENTATION_UNSPECIFIED —— MIUI/HyperOS 把「无偏好」当成
     *  「放开旋转」，会把用户锁好的方向一并解锁（可用设置里的方向锁定按钮复现）。
     *  fongmi 同款：VideoActivity.isAutoRotate() + PlaybackOrientation.getExitFullscreenOrientation。 */
    private fun tabletRestoreOrientation(): Int = if (isAutoRotate()) {
        ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    } else if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
    }

    /** 退出全屏/离开播放页后恢复屏幕方向：小屏手机锁竖屏，平板按用户旋转锁状态恢复。
     *  由播放页调用，判定同样按物理屏（播放页所在窗口可能已被信箱化） */
    fun restoreDefaultOrientation() {
        requestedOrientation = if (isSmallScreenDevice()) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            tabletRestoreOrientation()
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
                    // fongmi 同款：开启 seamless 缩放。系统把窗口表面整体当纹理缩放，
                    // 窗口与内容始终同步；关闭时窗口先变大、内容晚一帧，底部会闪出透壁纸的亮条
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
            // Android 15+：显式指定双击展开的目标比例（与视频一致），
            // 让系统展开时按视频比例直接放大，减少先按默认比例 reshape（圆角先变小）
            // 再放大的中间态。低版本无此 API；展开动画序列（圆角插值/旋转/放大）
            // 本身由 SystemUI 渲染，App 侧无公开 API 自定义
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                runCatching { builder.setExpandedAspectRatio(videoAspectRational()) }
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
            // 画中画窗口在 HyperOS 上是透明表面（fongmi 同样透明，但它内容铺满 100%）：
            // 我们的装饰视图若仍按系统栏避让，内容会在窗口内留出顶部一条 + 底部 1px 的边距，
            // 透明缝隙直接露出桌面图标/白线。进小窗后强制内容铺满整个窗口表面。
            runCatching {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                    .hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
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
                // 进小窗瞬间视频尺寸可能还没解析出来，宽高比会退回 16:9；
                // 尺寸一旦就绪立刻改回真实比例（fongmi 是在 enter 时直接读宽度，没有这个时间差）
                pipVideoSizeListener = object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) = updatePipAspect()
                }.also { listener -> Players.player?.addListener(listener) }
            } else {
                pipStateJob?.cancel()
                pipStateJob = null
                pipVideoSizeListener?.let { listener -> Players.player?.removeListener(listener) }
                pipVideoSizeListener = null
                // 退出 PiP：fongmi 同款——不改方向。
                // 注意此时 resources.configuration 仍是小窗尺寸（平板约 371dp×158dp），
                // 用它判断「小屏」会把平板误锁成竖屏：关闭小窗后再打开 App，
                // 就变成横屏窗口里摆一列竖屏界面、不占满屏幕。
                // 全屏展开时也不碰方向：进全屏时已请求横屏，中途重写会让 ROM 重算窗口、出现跳位
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
            // 小屏判定同样必须按物理屏：此时窗口还是小窗尺寸（371dp），
            // 用窗口宽度会把平板误锁成竖屏，重开 App 就成横屏里的竖条
            requestedOrientation = if (isSmallScreenDevice()) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                tabletRestoreOrientation()
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
