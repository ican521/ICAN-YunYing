package com.ican.tvplay.player

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.os.Build
import android.media.AudioManager
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TabRowDefaults
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import coil3.asDrawable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import com.ican.tvplay.data.BACKGROUND_OFF
import com.ican.tvplay.data.SettingsRepository
import com.ican.tvplay.data.BACKGROUND_ON
import com.ican.tvplay.data.SUBTITLE_MAX_POSITION
import com.ican.tvplay.data.SUBTITLE_MAX_SCALE
import com.ican.tvplay.data.SUBTITLE_MIN_POSITION
import com.ican.tvplay.data.SUBTITLE_MIN_SCALE
import com.ican.tvplay.data.SUBTITLE_OFFSET_MAX_MS
import com.ican.tvplay.data.SUBTITLE_OFFSET_STEP_MS
import com.ican.tvplay.data.local.FavoriteEntity
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.data.model.Episode
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.player.core.PlaybackService
import com.ican.tvplay.player.core.PlaySpec
import com.ican.tvplay.player.core.Players
import com.ican.tvplay.player.core.SubItem
import com.ican.tvplay.player.core.SubtitleStyle
import com.ican.tvplay.player.core.MediaItemFactory
import com.ican.tvplay.ui.appContainer
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.ArcStretchSpinner
import com.ican.tvplay.ui.components.dialogBottomMargin
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.siteShortName
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.BrandPink
import com.ican.tvplay.ui.theme.DarkDialogRow
import com.ican.tvplay.ui.theme.DarkDialogRowText
import com.ican.tvplay.ui.theme.DarkOnSurface
import com.ican.tvplay.ui.theme.DarkOnSurfaceMuted
import com.ican.tvplay.ui.theme.DarkSurfaceVariant
import com.ican.tvplay.ui.theme.LocalDarkTheme
import com.ican.tvplay.ui.theme.chipTintedFill
import com.ican.tvplay.ui.theme.chipTintedText
import com.ican.tvplay.ui.theme.playerDarkMiuixColors
import com.ican.tvplay.ui.theme.sideSheetScrimBrush
import com.ican.tvplay.ui.theme.SideSheetActionTextAlpha
import com.ican.tvplay.ui.theme.SideSheetFillAlpha
import com.ican.tvplay.ui.theme.SideSheetMutedTextAlpha
import com.ican.tvplay.ui.theme.SideSheetSectionTextAlpha
import com.ican.tvplay.ui.theme.Spacing
import com.ican.tvplay.ui.theme.SquircleShape
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

/** 等待画中画→全屏放大动画收敛（替代固定 delay）。
 *  系统 PiP→全屏放大动画时长不固定（负载高时会超过 900ms），固定延时会在动画未结束时
 *  把根底色提前翻回浅色，窗口底部最后 1px 仍由根底色绘制，露出 #F9F9F9 白色闪条。
 *  改为轮询 decorView 尺寸：连续 3 帧铺满物理屏（容 2px 舍入误差）即视为动画收敛并返回；
 *  带 2s 超时兜底，极端情况下也不会卡住过渡。 */
private suspend fun waitForPipExpansion(activity: Activity) {
    val decorView = activity.window.decorView
    val fullW: Int
    val fullH: Int
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = activity.windowManager.maximumWindowMetrics.bounds
        fullW = bounds.width()
        fullH = bounds.height()
    } else {
        val point = Point()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getRealSize(point)
        fullW = point.x
        fullH = point.y
    }
    var lastW = -1
    var lastH = -1
    var stable = 0
    val start = SystemClock.elapsedRealtime()
    while (true) {
        val w = decorView.width
        val h = decorView.height
        val reachedFull = w >= fullW - 2 && h >= fullH - 2
        stable = if (reachedFull && w == lastW && h == lastH) stable + 1 else 0
        lastW = w
        lastH = h
        if (stable >= 3) break
        if (SystemClock.elapsedRealtime() - start > 2000) break // 兜底
        delay(16)
    }
}

/**
 * FongMi 风格播放界面：
 * - 默认模式（竖屏）：顶部 16:9 视频区 + 下方可滚动详情信息流（标题/线路/选集/简介）
 * - 全屏模式：点击全屏按钮进入横屏全屏，视频占满屏幕 + 叠加控制层
 */
@Composable
fun PlayerScreen(
    videoId: String,
    startEpisode: Int,
    startFlag: String = "",
    preInitialized: Boolean = false,
    /** 追番更新入口：强制从 [startEpisode] 开播，不被历史记录覆盖 */
    forceStart: Boolean = false,
    /** 卡片标题提示：详情接口 vod_name 为空时（如爱刷频道卡）用它兜底标题行 */
    titleHint: String = "",
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = appContainer()
    val scope = rememberCoroutineScope()

    // 添加字幕：直接调起原生文件管理器（AOSP DocumentsUI），fongmi 同款 Intent
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uri = data?.data
        if (uri != null) {
            // 持久化读取权限：addSubtitle 会重新 prepare 播放器，若不持久化则无法再次读取该 Uri
            val flags = data.flags
            if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            val name = runCatching {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "外挂字幕"
            // 按文件名扩展名猜 mime，避免 content:// Uri 的 path 无扩展名导致解析失败
            val mime = MediaItemFactory.guessSubtitleMime(name)
            Players.addSubtitle(SubItem(name = name, url = uri.toString(), mimeType = mime))
        }
    }
    val launchSubtitlePicker = {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_MIME_TYPES, SUBTITLE_MIME_TYPES)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            .putExtra("android.content.extra.SHOW_ADVANCED", true)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        // 直接指定原生 DocumentsUI 的 PickActivity，绕过 MIUI 等定制 ROM 的文件管理器拦截
        val nativePkg = listOf("com.google.android.documentsui", "com.android.documentsui")
            .firstOrNull { pkg ->
                runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
            }
        if (nativePkg != null) {
            intent.setClassName(nativePkg, "com.android.documentsui.picker.PickActivity")
        }
        subtitlePicker.launch(intent)
    }

    val playerState by Players.state.collectAsStateWithLifecycle()

    // 设置持久化
    val settings = container.settingsRepository
    val savedSpeed by settings.playerSpeed.collectAsStateWithLifecycle()
    val savedScale by settings.playerScale.collectAsStateWithLifecycle()
    val savedAutoNext by settings.playerAutoNext.collectAsStateWithLifecycle()
    val savedEngine by settings.playerEngine.collectAsStateWithLifecycle()
    val savedRender by settings.playerRender.collectAsStateWithLifecycle()
    val savedBuffer by settings.playerBuffer.collectAsStateWithLifecycle()
    val savedDecode by settings.playerDecode.collectAsStateWithLifecycle()
    val savedSubtitleScale by settings.subtitleScale.collectAsStateWithLifecycle()
    val savedSubtitlePosition by settings.subtitlePosition.collectAsStateWithLifecycle()
    val savedSubtitleStyle by settings.subtitleStyle.collectAsStateWithLifecycle()

    var video by remember { mutableStateOf<Video?>(null) }
    // 画面比例的重置放在下面的 LaunchedEffect(videoId) 里（加载入口、续播分支之后）：
    // 不能在 video?.id 变化时重置——那是详情拉完才赋值，会让进页面先按上一部片的比例
    // 撑高小窗、再缩回 16:9 来回跳两帧。
    var episodeIndex by remember { mutableIntStateOf(startEpisode) }
    var initialized by remember { mutableStateOf(false) }
    // 本地历史读取完成前禁止写历史：慢站源进页要异步拉详情（几秒到十几秒），
    // 这期间 episodeIndex/openingMs 还是默认值，周期保存/开播锚点/退出保存
    // 会用「第 0 集 + 进度 0 + 片头 0」把旧记录整条覆盖，表现为续播丢失
    var historyLoaded by remember { mutableStateOf(false) }
    // 本次页面会话是否真正开始过播放：详情已出（历史已加载）但播放地址还没解析完就返回时，
    // player 为空，此时落库会把进度/时长写成 0（覆盖旧记录），必须等真正开播后才允许写
    var playbackStarted by remember { mutableStateOf(false) }
    var pendingPosition by remember { mutableLongStateOf(0L) }
    // 片头/片尾标记（ms；片尾存"距结尾时长"，fongmi History.opening/ending 同款）
    var openingMs by remember { mutableLongStateOf(0L) }
    var endingMs by remember { mutableLongStateOf(0L) }
    val skipFirstStart = remember { mutableStateOf(preInitialized) }

    // 线路切换
    var lineFlag by remember { mutableStateOf(startFlag) }
    val playLine = video?.playSources
        ?.let { sources -> sources.firstOrNull { it.flag == lineFlag } ?: sources.firstOrNull() }
    val lineEpisodes = playLine?.episodes ?: video?.episodes.orEmpty()

    // 画质（fongmi QualityAdapter + Url.values）：只有 playerContent 返回多画质时才显示这一行。
    // 切线路/切集都会重置为空（fongmi selectFlag/selectEpisode 里 renderQualityVisible(false)）
    var qualities by remember { mutableStateOf<List<com.ican.tvplay.data.remote.PlayQuality>>(emptyList()) }
    var qualityPosition by remember { mutableIntStateOf(0) }
    // 画质切换请求：-1 表示不指定（按 playerContent 返回体自带的 position）。
    // 单独作为重解析的触发键，避免「解析结果回写 qualityPosition」再触发一次重解析（网盘 jar 重解析很贵）
    var qualityRequest by remember { mutableIntStateOf(-1) }
    // 播放中显示用的封面（纯音频源靠它顶替画面）：站点下发的 artwork 优先，回退详情页封面
    var artworkUrl by remember { mutableStateOf("") }

    // 控制层状态：进入播放页默认隐藏，用户单击预览小窗后才呼出
    var controlsVisible by remember { mutableStateOf(false) }
    // 控制层交互计数：动作栏按钮点击时自增，重置 5 秒自动隐藏计时（fongmi setR1Callback 同款）
    var controlsTick by remember { mutableIntStateOf(0) }
    // 正在拖动进度条（小窗/全屏共用）：拖动期间挂起自动隐藏计时，松手后重新计 5 秒
    var seekDragging by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    // 全屏挂起状态（fongmi 同款时序：先请求系统旋转，旋转完成后再切换布局）
    // true=等待旋转进全屏 false=等待旋转退全屏 null=无挂起，
    // 避免"竖屏先铺满再旋转"的割裂感
    var pendingFullscreen by remember { mutableStateOf<Boolean?>(null) }
    // 平板已处于横屏时进入全屏：不会触发方向配置变化，无需等待旋转，退出时也不请求旋转
    var landscapeFullscreen by remember { mutableStateOf(false) }
    // 竖屏全屏：OK猫 PlayFragment.changedLandscape 同款——视频是竖屏时进全屏只把播放器放大铺满，
    // 完全不请求旋转（设备保持竖屏），退出时也不等旋转
    var portraitFullscreen by remember { mutableStateOf(false) }
    // 竖屏小窗↔竖屏全屏的缩放过渡：照抄横屏那套 expandTransition 做法
    // （竖屏片源进全屏不旋转，旧实现是 fullscreen 直接跳变，画面/信息流瞬间瞬移，没有过渡）
    var portraitZooming by remember { mutableStateOf(false) }
    var portraitZoomToken by remember { mutableIntStateOf(0) }
    // 字幕设置侧边栏：根级渲染，保证竖屏/全屏/横屏都能全高覆盖
    var showSubtitleSettings by remember { mutableStateOf(false) }
    // 横屏小窗↔全屏放大/缩回动画进行中：期间只渲染纯视频，不挂任何覆盖层
    var landZooming by remember { mutableStateOf(false) }
    // 缩放会话令牌：每次进入/退出全屏自增，作为收敛监听 LaunchedEffect 的 key，
    // 避免协程体内把 landZooming 置 false 导致自身被取消、渐显逻辑丢失
    var landZoomToken by remember { mutableIntStateOf(0) }
    // 缩放动画开始时刻：动画本身只有几百毫秒，若因配置变化（深浅色切换等）打断收敛协程卡住，
    // 用它可以判断「动画是不是真的卡了」，避免 landZooming 残留 true 把返回永久吞掉
    var zoomStartedAt by remember { mutableLongStateOf(0L) }
    // 退全屏等旋转期间：视频保持铺满，但不切到「当前方向的横版小窗」（否则会先闪一下平板样式），
    // 系统栏在这段时间已恢复，系统才会接受方向请求
    var exitRotating by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // 布局档位按「当前屏幕横竖」定：平板竖着走竖屏小窗、横着走横版；
    // 手机一律不落横版分支（竖屏小窗、横屏自动进全屏，见下方 checkLand 同款 effect）——
    // 手机横过来那一帧 fullscreen 还没落位，若按 isLandscape 直接判档会闪一帧横版小窗
    val isPhone = configuration.smallestScreenWidthDp < 600
    val landLayout = isLandscape && !isPhone
    // 渲染用的「全屏」：手机从横屏全屏竖回竖屏的那一瞬间，fullscreen 状态要等下面的
    // LaunchedEffect 下一帧才清掉；若照旧按 fullscreen 画，会多出 100~200ms 的
    // 「竖屏窗口 + 全屏分支」中间态——视频铺满整个 1080x2400、画面被 FIT 居中到屏幕中间，
    // 随后才一帧内缩回小窗，就是肉眼看到的「跳一下」。
    // 对齐 fongmi：VideoActivity.exitFullscreen 里 setFullscreen(false)/setVideoView(false)
    // 是在 onConfigurationChanged 里同步做完的，配置变化那一帧画出来就已经是小窗形态。
    // 所以这里在渲染期派生一次，同一帧就收敛，状态清理仍交给 LaunchedEffect。
    // 手机已横过来、fullscreen 还没被下面 LaunchedEffect 置位的那一帧，也直接按全屏渲染：
    // 否则会先落一帧「竖屏布局横躺」，Surface 多 resize 一次、视觉上先顿一下
    val enteringLandFullscreen = isPhone && isLandscape && !fullscreen &&
        pendingFullscreen == null && !exitRotating && !landZooming &&
        !portraitZooming && !landscapeFullscreen && !portraitFullscreen
    val renderFullscreen = (fullscreen || enteringLandFullscreen) &&
        !(isPhone && !isLandscape && !portraitFullscreen && !landscapeFullscreen &&
            !landZooming && !portraitZooming)
    LaunchedEffect(isLandscape) {
        when {
            // 旋转进入全屏后保持纯净画面，工具栏由用户单击唤出
            pendingFullscreen == true && isLandscape -> {
                fullscreen = true; pendingFullscreen = null; controlsVisible = false; exitRotating = false
            }
            // 转回竖屏：退全屏的过渡结束，交给竖屏布局
            !isLandscape -> {
                exitRotating = false
                // 手机从「横屏全屏」竖回竖屏：自动退全屏回详情页
                // （fongmi onConfigurationChanged：isAutoRotate && isPort && PORTRAIT && !isRotate && !isLock → exitFullscreen）
                if (isPhone && fullscreen && !locked && !portraitFullscreen && !landscapeFullscreen &&
                    !landZooming && !portraitZooming
                ) {
                    // 照搬 fongmi VideoActivity.exitFullscreen 的顺序（自动退全屏也走同一套）：
                    // setFullscreen(false) → setRequestedOrientation(USER_PORTRAIT) → 2 秒后 setOrient() 回 FULL_USER。
                    // fullscreen 立刻落 false，配合上面的 renderFullscreen，这一帧就画成竖屏详情页；
                    // 方向先钉成 USER_PORTRAIT，避免手机还歪着时 FULL_USER 允许系统再转回横屏造成二次旋转
                    fullscreen = false
                    controlsVisible = false
                    val activity = context as? Activity ?: return@LaunchedEffect
                    activity.window.decorView.post {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                        activity.window.decorView.postDelayed({
                            // 这两秒里又进了全屏就别抢方向（进全屏自己会请求 SENSOR_LANDSCAPE）
                            if (!fullscreen && pendingFullscreen == null &&
                                !landscapeFullscreen && !portraitFullscreen
                            ) {
                                activity.restoreDefaultOrientation()
                            }
                        }, 2000)
                    }
                }
            }
        }
    }
    // 手机横屏自动进全屏（照搬 fongmi checkLand：isPort() && ResUtil.isLand(this) → enterFullscreen）。
    // 手机布局固定是竖屏小窗，横着拿若不进全屏，竖屏布局会横躺，所以此时直接铺满进全屏
    LaunchedEffect(isLandscape, isPhone) {
        if (isPhone && isLandscape && !fullscreen && pendingFullscreen == null && !exitRotating &&
            !landZooming && !portraitZooming && !landscapeFullscreen && !portraitFullscreen
        ) {
            fullscreen = true
            controlsVisible = false
        }
    }
    // 退全屏转屏兜底：1 秒还横着就当系统真的不肯转，撤掉「铺满视频」态让当前方向的布局接手，
    // 免得停在「有系统栏的全屏画面」上没法操作
    LaunchedEffect(exitRotating) {
        if (!exitRotating) return@LaunchedEffect
        delay(1000)
        if (exitRotating && isLandscape) exitRotating = false
    }
    // 进全屏挂起超时兜底：等不到旋转就别停在什么都不画的 pending 空白态，
    // 撤销挂起退回当前方向的小窗布局（退全屏不再走 pending，见 exitFullscreen）
    LaunchedEffect(pendingFullscreen) {
        if (pendingFullscreen == true) {
            delay(1200)
            if (pendingFullscreen == true) {
                pendingFullscreen = null
                fullscreen = false
                controlsVisible = false
                (context as? Activity).restoreDefaultOrientation()
            }
        }
    }

    // 共享 PlayerView（fongmi 同思路：全屏/竖屏只是改容器尺寸，PlayerView 从不销毁重建）。
    // 常驻唯一 AndroidView，竖屏↔全屏↔画中画切换时 Surface 不销毁，消除切换黑屏。
    // 渲染方式来自设置（设置→播放设置→视频渲染）：
    //   TEXTURE（默认）对齐现有体验：画面参与 View 合成，缩放跟手、圆角可裁、播完能抓静帧；
    //   SURFACE 由系统合成器独立出层，HDR 片源的 10bit/PQ 才能直通（缩放动画可能滞后一帧）。
    // media3 的 surface_type 只在 inflate 时读 attrs、运行期改不了，所以切换时按设置重建这一个
    // PlayerView（Player 实例不动，音频继续，画面重新 attach）；AndroidView 节点用 key 强制重建。
    val renderSurface = savedRender == com.ican.tvplay.data.SettingsRepository.RENDER_SURFACE
    val sharedPlayerView = remember(renderSurface) {
        android.view.LayoutInflater.from(context)
            .inflate(
                if (renderSurface) {
                    com.ican.tvplay.R.layout.player_view_surface
                } else {
                    com.ican.tvplay.R.layout.player_view
                },
                null,
            ) as PlayerView
    }.apply {
        Players.bindPlayerView(this)
        // fongmi VideoActivity 同款：视频区矩形变化即同步 sourceRectHint，
        // 系统进/出画中画动画（含圆角插值）以最新矩形计算
        addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val rect = android.graphics.Rect()
            if (v.getGlobalVisibleRect(rect)) {
                // 诊断日志：仅 debug 构建保留（release 自动剔除），排查画中画进出动画用
                if (com.ican.tvplay.BuildConfig.DEBUG) {
                    // 同时打出内部画面容器的尺寸：播完后画面缩在左上角这类问题，
                    // 需要区分「PlayerView 没变大」还是「内部 AspectRatioFrameLayout 没重排」
                    val child = (v as? android.view.ViewGroup)?.let { g ->
                        (0 until g.childCount).map { g.getChildAt(it) }
                            .firstOrNull { it.visibility == android.view.View.VISIBLE }
                    }
                    android.util.Log.i(
                        "PipTrace",
                        "videoRect=${rect.width()}x${rect.height()}@${rect.left},${rect.top} " +
                            "pv=${v.width}x${v.height} " +
                            "inner=${child?.javaClass?.simpleName}@${child?.left},${child?.top} " +
                            "${child?.width}x${child?.height}",
                    )
                }
                (context as? PlayerHost)?.syncPipSourceRect(rect)
            }
        }
    }
    DisposableEffect(sharedPlayerView) {
        onDispose {
            Players.unbindPlayerView(sharedPlayerView)
            sharedPlayerView.setDefaultArtwork(null)
        }
    }

    // 纯音频源（音乐站源等）没有视频轨：照 fongmi VideoActivity.setArtwork 做法，
    // 把封面设成 PlayerView 的 defaultArtwork。media3 只在「没有视频轨」时画这张图，
    // 有视频轨的片子完全不受影响。
    LaunchedEffect(artworkUrl) {
        val url = artworkUrl.trim()
        if (url.isEmpty()) {
            sharedPlayerView.setDefaultArtwork(null)
            return@LaunchedEffect
        }
        val model = com.ican.tvplay.util.posterModel(context, url)
        if (model == null) {
            sharedPlayerView.setDefaultArtwork(null)
            return@LaunchedEffect
        }
        val drawable = withContext(Dispatchers.IO) {
            runCatching {
                val request = coil3.request.ImageRequest.Builder(context).data(model).build()
                coil3.SingletonImageLoader.get(context).execute(request).image
                    ?.asDrawable(context.resources)
            }.getOrNull()
        }
        if (drawable != null) sharedPlayerView.setDefaultArtwork(drawable)
    }

    // 应用持久化设置到 Players
    LaunchedEffect(savedSpeed) { Players.setSpeed(savedSpeed) }
    LaunchedEffect(savedScale) {
        Players.setScaleMode(
            Players.ScaleMode.entries.firstOrNull { it.name == savedScale }
                ?: Players.ScaleMode.FIT,
        )
    }
    LaunchedEffect(savedEngine) {
        Players.setKernel(
            Players.Kernel.entries.firstOrNull { it.name == savedEngine }
                ?: Players.Kernel.EXO,
        )
    }
    LaunchedEffect(savedBuffer) {
        Players.setBufferTier(
            Players.BufferTier.entries.firstOrNull { it.multiplier == savedBuffer }
                ?: Players.BufferTier.LOW,
        )
    }
    LaunchedEffect(savedDecode) {
        val decode = Players.Decode.entries.firstOrNull { it.name == savedDecode } ?: Players.Decode.AUTO
        if (Players.state.value.decode != decode) Players.setDecode(decode)
    }
    // 字幕字号/位置（持久化，仿 fongmi SubtitleSetting）：修改后实时作用到当前 Player
    LaunchedEffect(savedSubtitleScale) { Players.setSubtitleScale(savedSubtitleScale) }
    LaunchedEffect(savedSubtitlePosition) { Players.setSubtitlePosition(savedSubtitlePosition) }
    LaunchedEffect(savedSubtitleStyle) { Players.setSubtitleStyle(savedSubtitleStyle) }

    // 后台播放（设置三态：关闭/开启/画中画）：非关闭时退出播放页转交前台服务后台续播
    var backgroundMode by remember { mutableIntStateOf(BACKGROUND_OFF) }
    LaunchedEffect(Unit) {
        backgroundMode = container.settingsRepository.playerBackgroundPlay.first()
        PlaybackService.startIfNeeded(context, backgroundMode)
    }
    // PiP 画中画模式（全屏播放时切后台触发）
    val inPip by Players.pipMode.collectAsStateWithLifecycle()
    // 进 PiP 时收起控制层（对齐 fongmi onEnterPictureInPicture → hideControl）：
    // 否则双击小窗放大回全屏时，缩放动画第一帧会带上控制层，
    // 看上去像「全屏画面的缩小版」而不是当前小窗画面直接放大
    // 退出 PiP 的过渡保护：系统放大动画期间继续只渲染纯视频（同 PiP 态）。
    // 若立即挂载全屏控制层，首帧要同步完成整套 UI 的首组合，主线程被占满，
    // 窗口已放大但视频 Surface 仍停在小窗尺寸（贴左上角），看上去「先小后大」
    var pipExitHold by remember { mutableStateOf(false) }
    var wasInPip by remember { mutableStateOf(false) }
    LaunchedEffect(inPip) {
        if (inPip) {
            controlsVisible = false
            wasInPip = true
        } else if (wasInPip) {
            wasInPip = false
            pipExitHold = true
            // 不固定延时，改为等窗口真正放大到全屏尺寸再释放保护期：
            // 系统 PiP→全屏放大动画时长不固定（负载高时会超过 900ms），
            // 固定 delay(900) 会在动画还没结束时把根底色翻回浅色，
            // 窗口底部最后 1px 仍由根底色绘制，露出 #F9F9F9 白色闪条。
            // 改为轮询 decorView 尺寸：连续多帧铺满物理屏（动画收敛）才释放，
            // 带超时兜底，极端情况也不会卡住过渡。
            (context as? Activity)?.let { waitForPipExpansion(it) }
            pipExitHold = false
        }
    }
    // 后台续播返回本页时跳过一次重新解析（直接接管在播的播放器）
    var resumeFromBackground by remember { mutableStateOf(false) }
    // 画中画进出过渡（含退出保护期）上报给 AppRoot：根底色转黑、关闭导航圆角裁剪，
    // 避免退出放大时配置晚一帧造成的浅色根底（底部白条）与自绘小圆角
    LaunchedEffect(inPip, pipExitHold) {
        Players.setPipTransition(inPip || pipExitHold)
    }
    // 视频铺满全屏/画中画（含过渡期）时上报给 AppRoot：根底色保持黑色。
    // 仅靠 pipTransition（进出过渡）覆盖不到稳态全屏——全屏下窗口底部最后约 1px
    // 仍由根底色绘制，浅色主题会露出一条白色细线。
    val videoPresent = inPip || pipExitHold || renderFullscreen || exitRotating || pendingFullscreen != null
    LaunchedEffect(inPip, pipExitHold, renderFullscreen, exitRotating, pendingFullscreen) {
        Players.setVideoPresent(videoPresent)
    }
    // 画中画窗口在 HyperOS 上是透明表面（fongmi 同样透明，但它内容铺满 100%）：
    // 应用内容只要没稳定铺满整个窗口，底部缝隙就会透过透明窗口露出浅色桌面/白线。
    // 设 decorView.setBackgroundColor 不影响 HyperOS 画中画窗口的表面透明与否——
    // 决定它的是 window 背景。视频铺满全屏/画中画期间把 window 背景设成不透明黑色，
    // 任何未覆盖缝隙都显示黑色；退出后恢复原窗口背景。
    val activityWindow = (context as? Activity)?.window
    // HyperOS 画中画窗口是透明表面，透明与否由 window 背景决定（decorView 背景无效）。
    // 视频铺满全屏/画中画期间把 window 背景设成不透明黑色，任何未覆盖缝隙都显示黑色；
    // 退出后恢复主题根底色。
    val themeWindowBg = MiuixTheme.colorScheme.surface.toArgb()
    LaunchedEffect(videoPresent) {
        val window = activityWindow ?: return@LaunchedEffect
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(
                if (videoPresent) android.graphics.Color.BLACK else themeWindowBg,
            ),
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            Players.setPipTransition(false)
            Players.setVideoPresent(false)
            activityWindow?.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(themeWindowBg),
            )
        }
    }

    // 按站点补拉完整详情（spider 站走 detailContent，采集站走 VodApiClient.detail）：
    // 搜索页点击卡片不再阻塞等详情，详情下沉到播放页内异步加载（慢站源也能秒进播放页）
    suspend fun loadSiteDetail(
        site: com.ican.tvplay.data.remote.TvBoxSite,
        siteKey: String,
        jarSpec: String,
        id: String,
    ): Video? = Players.detailOnce(siteKey, id) {
        // 详情优先：用户点开详情即取消后台追番检测，让出网络与 IO（冷启动首次提速）
        container.videoRepository.yieldForDetail()
        // 24h 详情磁盘缓存：同片再进直接命中，跳过 detailContent 多跳网络
        container.videoRepository.readDetailDiskCache(siteKey, id)
            ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val item = runCatching {
                    if (site.isSpider()) {
                        val spider = container.spiderManager.getSpider(site, jarSpec)
                            ?: return@withContext null
                        com.ican.tvplay.data.remote.VodParse.parseVodListJson(
                            container.spiderManager.detailContent(spider, listOf(id)),
                        ).firstOrNull()
                    } else {
                        com.ican.tvplay.data.remote.VodApiClient().detail(site, id)
                    }
                }.getOrNull() ?: return@withContext null
                with(com.ican.tvplay.data.remote.VodParse) { item.toVideo(withEpisodes = true) }
                    // 站源名回填（与 getVideo 同款）：否则详情页只能拿线路名兜底，爱刷会显示成「刷刷刷」
                    .copy(
                        sourceName = com.ican.tvplay.ui.components.siteShortName(
                            site.name, siteKey,
                        ),
                    )
                    .also { container.videoRepository.writeDetailDiskCache(siteKey, id, it) }
            }
    }

    // 加载视频信息 + 历史进度
    LaunchedEffect(videoId) {
        // 后台续播返回：播放器仍在播同一部剧同一集 → 直接接管，不重新解析
        val alive = Players.playbackContext?.takeIf {
            Players.isAlive() && it.video.id == videoId
        }
        if (alive != null && startEpisode == alive.episodeIndex &&
            (startFlag.isEmpty() || startFlag == alive.lineFlag)
        ) {
            video = alive.video
            episodeIndex = alive.episodeIndex
            lineFlag = alive.lineFlag
            openingMs = alive.openingMs
            endingMs = alive.endingMs
            resumeFromBackground = true
            initialized = true
            // 状态直接来自存活播放上下文（等价于历史已就绪），允许继续落库
            historyLoaded = true
            playbackStarted = true
            return@LaunchedEffect
        }
        // 开新片：把播放器实例里上一部片的画面比例退回 16:9（播放器全局复用，不清的话
        // 详情页第一帧会沿用上一部片的高度，随后再缩回默认值，观感是"变高→缩回"跳两帧）。
        // 放在加载入口而不是 video 赋值之后：进页第一帧的小窗高度就与 16:9 的加载骨架一致。
        // 后台续播（上方提前 return）不重置，沿用播放器已报出的比例。
        Players.resetVideoAspectRatio()
        // 同上：换片时先清掉上一部片的封面，避免加载期先闪一下旧封面
        artworkUrl = ""
        // 每次重新进入观看视频：倍速重置为 1 倍速（用户要求），不沿用上次会话的设定值；
        // 同步写回设置，保证全屏长按快进松手恢复的 playbackSpeed（实时读取设置）与此一致；
        // 切集（videoId 不变）与后台续播返回（上方提前 return）不受影响
        Players.setSpeed(1f)
        settings.setPlayerSpeed(1f)
        // 跨站源场景优先：MultiSourceSearchScreen 提前 cache 的视频直接命中，
        // 避免先在 VideoRepository 的当前站点上发起慢查询（错站查询既慢又可能查错视频）
        var v = Players.consumeCachedVideo(videoId)?.video
        // 搜索元数据不含选集（点击卡片立即跳转）：先把元数据挂上屏（页面骨架立刻可见，
        // 小窗与详情区各转一个圈，对齐 fongmi VideoActivity 的 progress + progressLayout），
        // 再在播放页内补拉完整详情；拉取失败 → v=null 走错误页
        val metaEntry = Players.cachedVideo?.takeIf { it.video.id == videoId }
        val needAsyncDetail = v != null && metaEntry?.site != null &&
            v.playSources.isEmpty() && v.episodes.isEmpty()
        if (needAsyncDetail) {
            video = v
            initialized = true
            val full = runCatching {
                // 首等 15s；超时只是「等不及」，detailOnce 的请求在 detailScope 里继续跑并未取消。
                // 慢站源（如玩偶哥哥，多跳网盘）冷启动首次详情常 >15s：复用同一在途请求再最多等 45s，
                // 期间预览小窗持续转圈，加载完自动进入；仍失败才走错误页
                withTimeoutOrNull(DETAIL_TIMEOUT_MS) {
                    loadSiteDetail(metaEntry!!.site, metaEntry.siteKey, metaEntry.jarSpec, v!!.id)
                } ?: kotlinx.coroutines.withTimeout(DETAIL_SLOW_TIMEOUT_MS) {
                    loadSiteDetail(metaEntry!!.site, metaEntry.siteKey, metaEntry.jarSpec, v!!.id)
                }
            }.getOrNull()
            v = if (full != null) {
                val merged = full.copy(
                    // 详情标题为空时沿用卡片标题（爱刷这类源详情不给 vod_name）
                    title = full.title.ifBlank { v.title },
                    sourceName = v.sourceName.ifBlank {
                        com.ican.tvplay.ui.components.siteShortName(
                            metaEntry.site.name, metaEntry.siteKey,
                        )
                    },
                )
                // 缓存升级为完整详情：后续解析播放地址/失败自动换站源都读这份
                Players.cacheVideo(merged, metaEntry.siteKey, metaEntry.site, metaEntry.jarSpec)
                merged
            } else {
                null
            }
        } else if (v == null) {
            // 历史/收藏直达：用历史记录的站源直接拉详情，不等 ensureLoaded 的整站首页初始化
            // （配置刷新 + homeContent + 分类的串行等待），与搜索卡片点击同链路；
            // 查不到站点（URL 播放等）才回退 getVideo
            val historySite = container.historyDao.observeOne(videoId).first()
                ?.sourceKey?.takeIf { it.isNotBlank() }
                ?.let { container.videoRepository.findSiteForDirectDetail(it) }
            v = if (historySite != null) {
                val (site, jarSpec) = historySite
                loadSiteDetail(site, site.key, jarSpec, videoId)?.also {
                    Players.cacheVideo(it, site.key, site, jarSpec)
                }
            } else {
                container.videoRepository.getVideo(videoId)
            }
        }
        // 详情缺标题/站源时的兜底：标题用卡片标题（titleHint），站源用当前站源短名。
        // 部分源（爱刷这类频道卡）详情返回的 vod_name 为空、详情缓存里也可能没有 sourceName
        val fallbackSiteName = com.ican.tvplay.ui.components.siteShortName(
            Players.cachedVideo
                ?.takeIf { it.video.id == videoId }
                ?.let { it.site?.name.orEmpty() }
                .orEmpty(),
        )
        video = v?.let { item ->
            item.copy(
                title = item.title.ifBlank { titleHint },
                sourceName = item.sourceName.ifBlank { fallbackSiteName },
            )
        }
        if (v != null) {
            val history = container.historyDao.observeOne(videoId).first()
            // 恢复上次所选线路：调用方未指定线路（搜索页等）或指定的线路在新详情中不存在时，
            // 用历史记录的 lineFlag 兜底；都没有才落回首条线路。
            // 否则 eps 会取错线路的选集列表，history.episodeIndex 越界被丢弃，表现为"集数重置"。
            val currentFlagExists = v.playSources.any { it.flag == lineFlag }
            if (!currentFlagExists && history != null && history.lineFlag.isNotBlank() &&
                v.playSources.any { it.flag == history.lineFlag }
            ) {
                lineFlag = history.lineFlag
            }
            val eps = v.playSources
                .let { s -> s.firstOrNull { it.flag == lineFlag } ?: s.firstOrNull() }
                ?.episodes ?: v.episodes
            if (!forceStart && history != null && (eps.isEmpty() || history.episodeIndex in eps.indices)) {
                episodeIndex = history.episodeIndex.coerceIn(0, (eps.size - 1).coerceAtLeast(0))
                val nearEnd = history.durationMs > 0 &&
                    history.durationMs - history.positionMs < RESUME_THRESHOLD_MS
                val resume = if (nearEnd) 0L else history.positionMs
                // fongmi: startPositionMs = max(opening, 续播位置)，每次开播自动跳过片头
                pendingPosition = maxOf(resume, history.openingMs)
            }
            // 追番入口：直接选中传入的未看集（不从该集中间续播，pendingPosition 保持 0 从头播放）
            if (forceStart && eps.isNotEmpty()) {
                episodeIndex = startEpisode.coerceIn(0, eps.size - 1)
            }
            // 片头/片尾标记（按剧存储，fongmi History.opening/ending 同款）
            openingMs = history?.openingMs ?: 0L
            endingMs = history?.endingMs ?: 0L
            // 历史已应用到页面状态，此后才允许写库（避免默认值覆盖旧记录）
            historyLoaded = true
        }
        initialized = true
    }

    // ---- 播放失败自动切换（fongmi VodFallbackPolicy：先换线路，再换站源） ----
    // 已失败的线路/站点：本次播放会话内不再重试，避免循环。
    // 作用域必须是整个 PlayerScreen 实例（remember 无 key）：若按 videoId 记忆，
    // 自动换站源后 video 一变集合就被重置，会在两个坏站之间无限乒乓（每轮都调 jar 解析，
    // 网盘 jar 每次拉取几百 KB JSON，高频循环几十秒即可耗尽堆内存 OOM 闪退）。
    // 该名单在换站时也不清空，否则旧站坏线路会重新可点，同样形成死循环。
    val failedFlags = remember { mutableStateOf(setOf<String>()) }
    val failedSiteKeys = remember { mutableStateOf(setOf<String>()) }
    var fallbackInProgress by remember { mutableStateOf(false) }
    // 本会话已自动兜底次数（换线路 + 换站源合计），用于给解析开销封顶
    var autoFallbackCount by remember { mutableIntStateOf(0) }

    // 错误态下拉刷新（fongmi VideoActivity：正常播放 swipeLayout=disabled，
    // 只有 resetPlaybackForError / showEmpty 才启用）：
    // 触发后把当前集重新解析一遍（mVod.refresh() 同义），刷新期间亮圈，解析结束收圈
    var refreshNonce by remember { mutableIntStateOf(0) }
    var errorRefreshing by remember { mutableStateOf(false) }
    // 「该站源只给磁力/BT 地址」的提示态：非空时预览区画提示卡，并且不启动播放器、
    // 不触发自动换线/换站兜底（换多少条线路都还是磁力链接，只有换站源才有意义，
    // 交给用户自己决定，免得在坏源之间反复解析烧内存）
    var btBlockedMsg by remember { mutableStateOf<String?>(null) }
    val onRefreshErrorPlayback: () -> Unit = {
        if (!errorRefreshing) {
            errorRefreshing = true
            // 记住当前位置，在原位置续播（fongmi refresh 也是保留进度重解析）
            pendingPosition = Players.progress.value.positionMs
            // 清掉当前线路的失败标记，否则刷新成功后可能立刻被自动兜底再切走
            val curFlag = playLine?.flag ?: video?.playFrom.orEmpty()
            failedFlags.value = failedFlags.value - curFlag
            // 先停当前画面进入加载态（与切线路同款），避免解析期间旧错误画面继续挂着
            Players.beginSwitch()
            refreshNonce++
        }
    }

    // 解析并播放（video 变化也触发：自动换站源后整片替换重解析）
    LaunchedEffect(
        initialized, historyLoaded, video, episodeIndex, lineFlag, qualityRequest, refreshNonce,
    ) {
        if (!initialized) return@LaunchedEffect
        // 历史进度/所选线路还没还原就解析，会先用「第 0 集 + 空线路」白解析一次，
        // 等历史回来再重启解析——网盘类 jar 被连调两次，第一次弹出的扫码窗会被第二次
        // 关掉重建，表现为进详情页时闪一下扫码弹窗。这里等历史落定后再解析，只解析一次。
        if (!historyLoaded) return@LaunchedEffect
        if (resumeFromBackground) {
            // 后台续播接管：播放器已在播，无需重新解析
            resumeFromBackground = false
            return@LaunchedEffect
        }
        val v = video ?: return@LaunchedEffect
        val episode = lineEpisodes.getOrNull(episodeIndex) ?: return@LaunchedEffect
        val pos = pendingPosition
        pendingPosition = 0L

        try {
            val cached = Players.cachedVideo?.takeIf { it.video.id == v.id }
            val source = when {
                cached?.site != null -> {
                    // 跨站源：在播放页内解析（某些网盘 jar 在 playerContent 时触发扫码弹窗，
                    // 解析必须发生在进入播放页之后，弹窗时机才正确）
                    container.spiderManager.resolvePlayUrl(
                        site = cached.site,
                        jarSpec = cached.jarSpec,
                        playUrl = episode.playUrl,
                        flag = playLine?.flag ?: v.playFrom,
                        qualityPosition = qualityRequest,
                    )
                }
                cached != null -> {
                    // 推送/直链视频（fongmi playerContent PUSH 分支同款）：episode.playUrl 即真实地址，
                    // 直接播放，不经过站点 spider 解析
                    com.ican.tvplay.data.PlaySource(episode.playUrl)
                }
                else -> {
                    container.videoRepository.resolvePlaySource(
                        v, episode, playLine?.flag ?: v.playFrom, qualityRequest,
                    )
                }
            }
            // 画质行数据（fongmi renderQuality：values.size > 1 才 setQualityVisible(true)）
            qualities = source.qualities
            qualityPosition = source.qualityPosition
            // 磁力/BT 源拦截（本机没有 BT 引擎，magnet 交给 MPV 必然黑屏）：
            // 停在提示态，不创建播放器实例，也不走下面的开播/写历史逻辑
            if (isTorrentOnlyLink(source.url)) {
                btBlockedMsg = "该站源为磁力/BT 源，当前版本暂不支持播放\n请切换其他线路，或在其他站源中观看"
                android.util.Log.w("PlayerScreen", "unsupported bt link: ${source.url}")
                Players.showUnsupported(btBlockedMsg.orEmpty())
                return@LaunchedEffect
            }
            btBlockedMsg = null
            // 站点下发的字幕（fongmi Result.subs，音乐源的歌词）随播放一起挂上；
            // artwork 决定纯音频源显示什么画面（没有则回退详情页封面）
            artworkUrl = source.artwork.ifBlank { v.cover }
            val spec = PlaySpec(url = source.url, headers = source.headers, subs = source.subs)
            // 注册播放上下文：通知栏展示 / 上一集下一集 / 后台片尾自动切集依赖
            Players.playbackContext = Players.PlaybackContext(
                video = v,
                lineFlag = playLine?.flag ?: v.playFrom,
                episodes = lineEpisodes,
                episodeIndex = episodeIndex,
                openingMs = openingMs,
                endingMs = endingMs,
            )
            // start 内部已兜底同步异常；这里再包一层防止 spider/jx 解析或上下文构造异常导致闪退
            // 每次开播（含换集）都跳过片头：startPositionMs = max(续播位置, openingMs)
            Players.start(
                context = context,
                spec = spec,
                startPositionMs = maxOf(pos, openingMs),
            )
            // 真正开播后才允许写历史（此前 player 为空，落库会把进度清零）
            playbackStarted = true
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            android.util.Log.e("PlayerScreen", "resolve/play fail: ${v.id} ep=$episodeIndex", t)
            Players.notifyError("播放地址解析失败，该站源可能不支持此影片，请切换线路或站源重试")
        } finally {
            // 错误态下拉刷新的收圈时机：解析跑完（成功或失败）就收（对齐 fongmi 在回调里 setRefreshing(false)）；
            // 被取消（用户又拉了一次）时不收，交给新一轮自己的收尾
            if (errorRefreshing && currentCoroutineContext().isActive) errorRefreshing = false
        }
    }

    // ---- 播放失败自动切换（fongmi VodFallbackPolicy：先换线路，再换站源）----

    fun toastMsg(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(playerState.error) {
        if (playerState.error == null || fallbackInProgress) return@LaunchedEffect
        // 磁力/BT 提示态：不是解码/网络失败，换线换站兜底只会反复解析同一批磁力链接
        if (btBlockedMsg != null) return@LaunchedEffect
        // 当前已在正常播放：错误必来自「已被替换的旧源」的迟到事件（如死代理地址秒判失败后
        // 其 mpv EndFile 错误晚到），直接丢弃——否则会把正在播放的好源切走，
        // 并触发换线/换站连锁循环
        if (playerState.playing) {
            Players.clearError()
            return@LaunchedEffect
        }
        val v = video ?: return@LaunchedEffect
        // 自动兜底次数上限：每次兜底都要让 jar 重新解析（网盘类要拉整份文件清单，单次数十 MB），
        // 不限次数会沿站源链一路解析下去，几十秒就能把 256MB 堆耗尽闪退
        if (autoFallbackCount >= MAX_AUTO_FALLBACK) {
            toastMsg("所有线路均播放失败，请手动选择其他线路")
            return@LaunchedEffect
        }
        // 无论是否自动兜底，先把出错线路记入会话失败集合：
        // 手动反复点同一条放不了的线路会并行堆起大量代理拉流线程耗尽内存，
        // 记入后该线路在本次会话内不再重复发起（requestLineChange 拦截）
        val curFlag = playLine?.flag ?: v.playFrom
        failedFlags.value = failedFlags.value + curFlag
        // 当前站点（跨站源缓存优先，兜底首页当前站）
        val cached = Players.cachedVideo?.takeIf { it.video.id == v.id }
        val curSite = cached?.site ?: container.videoRepository.currentSite.value?.site
        // 当前站源未开「自动切换站源」→ 不兜底，走原错误提示
        if (curSite == null || !container.settingsRepository.isSiteChangeable(curSite)) {
            return@LaunchedEffect
        }

        // 1) 换下一条线路（同站源内）
        val flags = v.playSources.map { it.flag }
        if (flags.size > 1) {
            val nextFlag = flags.dropWhile { it != curFlag }.drop(1)
                .firstOrNull { it !in failedFlags.value }
            if (nextFlag != null) {
                toastMsg("播放失败，自动切换线路：$nextFlag")
                autoFallbackCount++
                lineFlag = nextFlag
                return@LaunchedEffect
            }
        }

        // 2) 换站源：跨站搜同名影片（仅「参与搜索 + 允许换源」的站点）
        fallbackInProgress = true
        try {
            val curKey = cached?.siteKey ?: curSite.key
            val candidate = container.videoRepository.searchFallbackCandidate(
                title = v.title,
                excludeSiteKeys = failedSiteKeys.value + curKey,
            )
            if (candidate != null) {
                failedSiteKeys.value = failedSiteKeys.value + curKey
                autoFallbackCount++
                // 注意：这里不能清空 failedFlags。清空会让旧站的坏线路重新可点，
                // 用户手点回去 → jar 重新解析 → 失败 → 又自动换站 → 名单再清空，
                // 形成死循环并在几十秒内刷爆堆内存闪退。
                // 代价仅是「新站同名线路被误拦一条」，远小于 OOM 风险。
                val newVideo = candidate.video
                toastMsg("播放失败，自动切换站源：${siteShortName(candidate.site.name, candidate.site.key)}")
                // 缓存新站点信息：解析 LaunchedEffect 据此走新站的 playerContent 延迟解析
                Players.cacheVideo(newVideo, candidate.site.key, candidate.site, candidate.jarSpec)
                val newEps = newVideo.playSources.firstOrNull()?.episodes ?: newVideo.episodes
                episodeIndex = episodeIndex.coerceIn(0, (newEps.size - 1).coerceAtLeast(0))
                lineFlag = ""
                video = newVideo
            }
            // 无候选：保留原错误提示（error 未被清除，effect 不会重复触发）
        } finally {
            fallbackInProgress = false
        }
    }

    fun saveProgress() {
        // 历史尚未从本地读出：此时的 episodeIndex/openingMs 都是默认值，
        // 写库会把旧记录（集数/进度/片头）整条覆盖，必须直接跳过
        if (!historyLoaded) return
        val v = video ?: return
        // 本次会话还没真正开播（详情已出、播放地址仍在解析）：player 为空，
        // 位置/时长都是 0，落库会把旧进度清零，必须跳过
        if (!playbackStarted) return
        // 集标题：从实际播放上下文里取（页面 lineEpisodes 可能为空，playbackContext 才是真实数据）
        val epTitle = Players.playbackContext
            ?.takeIf { it.video.id == v.id }
            ?.episodes?.getOrNull(episodeIndex)?.title.orEmpty()
        // 直接从 player 读当前位置，不依赖 state（release 会重置 state，导致最后一次保存读到 0）
        val pos = Players.currentPositionMs()
        val dur = Players.durationMs()
        // 同步片头/片尾标记到播放上下文（后台切集/通知栏用）
        Players.playbackContext?.takeIf { it.video.id == v.id }?.let {
            Players.playbackContext = it.copy(openingMs = openingMs, endingMs = endingMs)
        }
        // 必须走应用级 scope：onDispose（退出页面/切后台）时 rememberCoroutineScope 已取消，
        // 用它 launch 会导致最后一次历史保存被静默丢弃（历史页空记录的根因）
        container.appScope.launch {
            runCatching {
                // 记录视频所在站源：点击历史时按此站源拉详情并续播（与收藏同款）
                val cached = Players.cachedVideo
                val siteKey = cached?.takeIf { it.video.id == v.id }?.siteKey ?: ""
                val siteName = v.sourceName.ifBlank {
                    com.ican.tvplay.ui.components.siteShortName(
                        cached?.takeIf { it.video.id == v.id }?.site?.name.orEmpty(),
                        siteKey,
                    )
                }
                container.historyDao.upsert(
                    HistoryEntity(
                        videoId = v.id,
                        title = v.title,
                        cover = v.cover,
                        categoryName = v.categoryName,
                        episodeIndex = episodeIndex,
                        episodeTitle = epTitle,
                        positionMs = pos,
                        durationMs = dur,
                        updatedAt = System.currentTimeMillis(),
                        openingMs = openingMs,
                        endingMs = endingMs,
                        sourceKey = siteKey,
                        sourceName = siteName,
                        // 记住当前所选线路：从历史续播时恢复同一条线路。
                        // lineFlag 优先（切换线路后状态即时更新），为空时兜底 playLine（首条线路）
                        lineFlag = lineFlag.takeIf { it.isNotBlank() } ?: playLine?.flag.orEmpty(),
                    ),
                )
            }.onFailure {
                android.util.Log.e("PlayerScreen", "history upsert failed: ${v.id} ep=$episodeIndex", it)
            }
        }
    }

    /**
     * 手动切线统一入口。
     * 不能把失败线路永久拉黑：线路一旦判失败就永久不可用，会导致「同一个线路名在其他集/其他片全部播不了」，
     * 而用户手动点它本就是明确的「再试一次」意图。因此这里只做 400ms 防连点误触，不限制重试次数。
     */
    var lastLineChangeAt by remember { mutableLongStateOf(0L) }
    val requestLineChange: (String) -> Unit = { flag ->
        if (flag != lineFlag) {
            val now = android.os.SystemClock.elapsedRealtime()
            // 仅防连点误触（400ms），不再限制重试次数：用户点线路就是明确的「切过去试一下」意图
            if (now - lastLineChangeAt >= 400L) {
                lastLineChangeAt = now
                // 立即停掉当前画面进入加载态（对齐 fongmi 详情页切线路：先 stopPlaybackForRefresh 再解析），
                // 否则解析新线路（网盘类 jar 秒级）期间旧画面继续播，看起来像"点了没反应"
                Players.beginSwitch()
                lineFlag = flag
                // 切线路后画质列表作废（fongmi selectFlag → renderQualityVisible(false)）
                qualities = emptyList()
                qualityPosition = 0
                qualityRequest = -1
                saveProgress()
            }
        }
    }

    /**
     * 选集统一切换入口：与切线路同款「先停当前再解析」。
     * 点选集立即停掉当前画面进入加载态，避免解析新集（网盘类 jar 秒级）期间旧画面继续播，
     * 看起来像「点了延迟一会才开始加载」。
     */
    val requestEpisodeChange: (Int) -> Unit = { idx ->
        if (idx != episodeIndex) {
            Players.beginSwitch()
            saveProgress()
            // 换集后画质列表作废（fongmi selectEpisode → renderQualityVisible(false)）
            qualities = emptyList()
            qualityPosition = 0
            qualityRequest = -1
            episodeIndex = idx
        }
    }

    /**
     * 切换画质（fongmi VodPlaybackController.selectQuality）：
     * 记住当前播放位置 → 停播进加载态 → 带新的 qualityPosition 重新请求 playerContent，
     * 新源在原位置继续播。只在多画质（qualities.size > 1）时才可能被点到。
     */
    val requestQualityChange: (Int) -> Unit = { pos ->
        if (pos != qualityPosition) {
            // 切换前抓当前进度：fongmi 用 host.getPlayerPosition() 作为 startPositionMs
            pendingPosition = Players.progress.value.positionMs
            Players.beginSwitch()
            qualityRequest = pos
        }
    }

    // ========== 片头/片尾标记（fongmi: onOpening/onEnding 同款规则） ==========

    /** 标记允许的时限：视频 <15 分钟→3 分钟；<30 分钟→6 分钟；否则→10 分钟 */
    fun opEdLimit(duration: Long): Long = when {
        duration < 15 * 60_000L -> 3 * 60_000L
        duration < 30 * 60_000L -> 6 * 60_000L
        else -> 10 * 60_000L
    }

    /** 短按片头：把当前进度标记为片头（fongmi: canSetOpening + setOpening(position)） */
    fun markOpening() {
        val prog = Players.progress.value
        val limit = opEdLimit(prog.durationMs)
        if (prog.durationMs > 0 && prog.positionMs > 0 && prog.positionMs <= limit) {
            openingMs = prog.positionMs
            saveProgress()
        }
    }

    /** 短按片尾：把当前进度距结尾的时长标记为片尾（fongmi: setEnding(duration - position)） */
    fun markEnding() {
        val prog = Players.progress.value
        val remain = prog.durationMs - prog.positionMs
        val limit = opEdLimit(prog.durationMs)
        if (prog.durationMs > 0 && prog.positionMs > 0 && remain in 1..limit) {
            endingMs = remain
            saveProgress()
        }
    }

    // 片尾自动切下一集（fongmi: onTimeChanged → nextEpisode）
    // 每集只触发一次：endingSkipped 随 episodeIndex 重置（remember key 为 episodeIndex）
    // 订阅独立进度流：500ms 一跳的进度刷新只触发本效应，不再牵动整个播放页重组
    var endingSkipped by remember(episodeIndex) { mutableStateOf(false) }
    LaunchedEffect(episodeIndex) {
        Players.progress.collect { prog ->
            val dur = prog.durationMs
            if (!endingSkipped && endingMs > 0 && dur > 0 &&
                endingMs + prog.positionMs >= dur
            ) {
                if (episodeIndex < lineEpisodes.size - 1) {
                    endingSkipped = true
                    toastMsg("已跳过片尾")
                    episodeIndex++
                }
            }
        }
    }

    // 连播：一集播完（STATE_ENDED）后自动进下一集；关掉则停在最后一帧等手动切
    LaunchedEffect(playerState.ended, savedAutoNext, episodeIndex) {
        if (!playerState.ended || !savedAutoNext) return@LaunchedEffect
        if (episodeIndex >= lineEpisodes.size - 1) return@LaunchedEffect
        delay(AUTO_NEXT_DELAY_MS)
        // 停顿期间用户可能手动切集/关连播/退出，结束态已变就放弃
        if (playerState.ended && savedAutoNext) episodeIndex++
    }

    // 周期性保存进度
    LaunchedEffect(videoId) {
        while (isActive) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            if (initialized) saveProgress()
        }
    }

    // 开播即落一条历史（进度可为 0）：防止停留不足一个保存周期就退出导致历史页无记录。
    // 每集只锚定一次；saveProgress 内部走应用级 scope，composition 销毁也能写完。
    var historyAnchorKey by remember { mutableStateOf("") }
    LaunchedEffect(initialized, video?.id, episodeIndex, lineFlag, playerState.playing) {
        val v = video
        if (initialized && v != null && playerState.playing) {
            val key = "${v.id}#$episodeIndex#${lineFlag}"
            if (historyAnchorKey != key) {
                historyAnchorKey = key
                saveProgress()
            }
        }
    }

    // 自动隐藏控制层（fongmi 同款：显示后 5 秒无操作隐藏，交互重置计时）
    // seekDragging 也是 key：拖动开始即挂起倒计时，松手(false)使本 effect 重启重新计 5 秒
    LaunchedEffect(controlsVisible, locked, playerState.playing, controlsTick, seekDragging) {
        if (!controlsVisible || locked || seekDragging) return@LaunchedEffect
        if (playerState.playing) {
            delay(HIDE_DELAY_MS)
            if (playerState.playing && !locked) controlsVisible = false
        }
    }

    // 全屏/放大动画期间自动收起状态栏，缩回小窗收敛后恢复（旋转请求在挂起触发处先行发出）；
    // 进入全屏后的旋转等待期（pendingFullscreen）也要收起系统栏，否则旋转动画中顶部/底部
    // 会露出浅色状态栏/导航栏，看着像视频头尾由白色渐变成黑色
    val immersive = renderFullscreen || landscapeFullscreen || landZooming || portraitZooming ||
        pendingFullscreen != null
    LaunchedEffect(immersive) {
        Players.isFullscreen = immersive
        val activity = context as? Activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (immersive) {
            // 沉浸式：隐藏状态栏与导航栏，从边缘上滑可临时唤出
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // 状态栏图标颜色按「状态栏底下实际是什么底色」定，不能无脑跟主题：
    // 竖屏预览页顶部是「状态栏高度」的黑条（PortraitPlayLayout 与视频区统一黑底），
    // 浅色主题下若把图标设成深色就直接糊进黑条里看不见（用户实测返修）。
    // 横屏小窗顶部是主题底色 → 跟主题走；全屏/挂起/缩放动画中系统栏本就是隐藏的，保持浅色图标。
    // 用 SideEffect 每次重组都重设，进出全屏、深浅色切换后都会被纠正，
    // 不会被 enableEdgeToEdge 的配置回调覆盖。
    val darkTheme = LocalDarkTheme.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = landLayout && !immersive && !darkTheme
    }

    // 页面销毁时恢复状态栏（防止全屏中直接退出后状态栏缺失）
    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 通知栏/后台切集导航：页面存活时通过 episodeIndex 驱动页面解析流
    DisposableEffect(Unit) {
        Players.uiEpisodeNavigator = { target ->
            if (target != episodeIndex) episodeIndex = target
        }
        onDispose { Players.uiEpisodeNavigator = null }
    }

    DisposableEffect(Unit) {
        // X 关闭画中画小窗：Activity 后台触发退出播放页（不销毁 App，回到首页）
        Players.uiExitNavigator = { onBack() }
        onDispose { Players.uiExitNavigator = null }
    }

    // 返回前先暂停：独立 Activity 退场时视频若继续按片源帧率（约 25fps）出帧，
    // 会让系统窗口转场观感被拉低到 25fps；暂停后转场按屏幕刷新率播放。
    // 后台续播模式不暂停，否则退出后会打断「后台播放」。


    // Surface 生命周期
    DisposableEffect(Unit) {
        // 监听 Activity 生命周期：切后台自动 pause（仅「全屏 + 已进 PiP」与后台续播模式除外），回前台 resume
        val activity = context as? android.app.Activity
        val lifecycle = (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                // 只有「全屏 + 已在 PiP」才保持播放；预览小窗（非全屏）回桌面一律暂停，
                // 即使后台播放设为「画中画」也不例外（画中画仅全屏时触发，非全屏不回退会留在后台继续播）
                val pipPlaying = activity?.isInPictureInPictureMode == true && Players.isFullscreen
                if (!pipPlaying && backgroundMode != BACKGROUND_ON) {
                    Players.onPause()
                }
            }
            // 平板横屏预览小窗上滑回桌面时，部分 ROM 只走 onStop 不走 onPause 兜底：
            // Activity 完全不可见时强制 pause（与 onPause 同条件，幂等无副作用）
            override fun onStop(owner: LifecycleOwner) {
                val pipPlaying = activity?.isInPictureInPictureMode == true && Players.isFullscreen
                if (!pipPlaying && backgroundMode != BACKGROUND_ON) {
                    Players.onPause()
                }
            }
            override fun onResume(owner: LifecycleOwner) { Players.onResume() }
        }
        lifecycle?.addObserver(lifecycleObserver)

        onDispose {
            lifecycle?.removeObserver(lifecycleObserver)
            val isConfigChange = activity?.isChangingConfigurations == true
            // 屏幕旋转：只保存进度 + 解锁方向，不 release 播放器（单例保持播放）
            if (isConfigChange) {
                activity.restoreDefaultOrientation()
                saveProgress()
            } else {
                // 真正退出：保存进度；仅「后台播放」模式转交前台服务后台续播（不销毁播放器）；
                // 「画中画」只负责全屏切后台自动进小窗，返回退出播放页应直接停止
                saveProgress()
                if (backgroundMode == BACKGROUND_ON && Players.isAlive()) {
                    Players.detachForBackground()
                } else {
                    Players.release()
                    PlaybackService.stop(context)
                }
                // 清全屏标记：否则退出后 isFullscreen 残留 true，首页按 Home 会误触自动画中画
                Players.isFullscreen = false
                activity.restoreDefaultOrientation()
            }
        }
    }

    // 退全屏（返回键 / 预测返回手势共用）：只负责把「全屏 → 小窗」这一段动画起起来
    fun exitFullscreen() {
        if (landscapeFullscreen) {
            // 平板横屏直进的全屏：立即卸载全屏覆盖层，PlayerView 执行缩回动画，
            // 动画期间只有纯视频，结束后恢复横版小窗（同样不弹工具栏）
            fullscreen = false
            controlsVisible = false
            landscapeFullscreen = false
            landZooming = true
            zoomStartedAt = android.os.SystemClock.elapsedRealtime()
            landZoomToken++
        } else if (portraitFullscreen) {
            // 竖屏全屏：不旋转，播放器缩回预览小窗（缩放动画），收敛后再挂详情信息流
            fullscreen = false
            controlsVisible = false
            portraitFullscreen = false
            portraitZooming = true
            zoomStartedAt = android.os.SystemClock.elapsedRealtime()
            portraitZoomToken++
        } else if (pendingFullscreen == true) {
            // 还在等旋转进全屏（画面尚未铺满）：直接撤销挂起并恢复方向，别让它继续转成全屏
            (context as? Activity).restoreDefaultOrientation()
            pendingFullscreen = null
        } else {
            // 照搬 fongmi VideoActivity.exitFullscreen 的顺序：
            // 1) 先立即退全屏（系统栏回来、解掉 MIUI「视频全屏」的方向锁）；
            // 2) 再请求 USER_PORTRAIT 把屏幕转回竖屏（不等旋转落地才收敛，返回随时可用）；
            // 3) 2 秒后把方向控制权还给系统（fongmi 的 App.post(setOrient, 2000) → FULL_USER）。
            // 之前「等旋转 + 超时兜底」的问题是系统不转屏时会落在横版小窗，而 pending 还会吞掉返回。
            fullscreen = false
            controlsVisible = false
            pendingFullscreen = null
            // 转屏到位前继续铺满视频、不切当前方向的布局（否则竖屏入口返回时会先闪一下平板横版样式）
            exitRotating = true
            val activity = context as? Activity ?: return
            activity.window.decorView.post {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                activity.window.decorView.postDelayed({
                    // 这 2 秒里又进了全屏就别抢方向（进全屏自己会请求 SENSOR_LANDSCAPE）
                    if (!fullscreen && pendingFullscreen == null) activity.restoreDefaultOrientation()
                }, 2000)
            }
        }
    }

    fun handleBack() {
        // 锁定状态下屏蔽返回（对齐 fongmi isLock() 拦截语义），
        // 系统侧滑/返回键不退出全屏、不退出页面，解锁后恢复
        if (locked) return
        // 退全屏的转屏过渡期（约 200~300ms）：返回先消费掉，别在过渡中把播放页一起退出
        if (exitRotating) return
        // 放大/缩回动画进行中忽略返回，避免动画中途退出页面；
        // 但动画卡住（配置变化打断收敛协程）超过 400ms 就强制回到小窗稳态，
        // 否则 landZooming/portraitZooming 残留 true 会把返回永久吞掉（这一下仍算被消费，下次返回即生效）
        if (landZooming || portraitZooming) {
            if (android.os.SystemClock.elapsedRealtime() - zoomStartedAt < 400L) return
            landZooming = false
            portraitZooming = false
            landscapeFullscreen = false
            portraitFullscreen = false
            fullscreen = false
            controlsVisible = false
            (context as? Activity).restoreDefaultOrientation()
            return
        }
        // 全屏相关状态一律先退全屏：之前只判 fullscreen，
        // 旋转挂起（pendingFullscreen 非空）时返回被 BackHandler 吞掉、又走不到退页面 → 返回失效
        if (fullscreen || pendingFullscreen != null || landscapeFullscreen || portraitFullscreen) {
            exitFullscreen()
            return
        }
        // 退出播放页前先暂停：返回转场期间若视频继续出帧，转场观感会被片源帧率（约 25fps）拉低，
        // 看起来像动画不是满帧；先让画面静止，转场按屏幕刷新率播放。
        // 后台续播模式不暂停，否则退出后会打断「后台播放」。
        if (backgroundMode != BACKGROUND_ON) Players.onPause()
        // release / 后台续播转交由 onDispose 统一处理（popBackStack 后触发）
        onBack()
    }
    // 返回键 / 三键导航：退回最稳的 BackHandler（手势导航那一侧由下面的预测返回接管）
    // 挂起态也要接管：否则旋转等待中按返回会直接退出播放页
    BackHandler(
        enabled = fullscreen || pendingFullscreen != null || exitRotating || landZooming || portraitZooming,
    ) { handleBack() }

    // 手势导航的预测返回：targetSdk 37 在 Android 16 上预测返回已无法关闭
    // （manifest 的 enableOnBackInvokedCallback=false 从 36 起失效），只挂 BackHandler 时
    // 系统会先自播一段「窗口缩小 + 变暗 + 左侧手势箭头」的关闭预览，抬手被我们消费掉再弹回，
    // 再跑自己的 zoom-out → 两段动画叠在一起（录屏 6.30s 整屏变暗帧）。
    // 这里只消费系统进度、不提前动画面：手势过程中画面完全静默（系统也不再自播关闭预览），
    // 抬手越过阈值（flow 正常结束）才走原 handleBack 逻辑；
    // 手势取消则什么都不做——不能像上一版那样「进度一出现就缩放、取消再反向弹回」，
    // 否则用户看到的是滑一下闪一下、实际没退出去。
    PredictiveBackHandler(
        enabled = fullscreen || pendingFullscreen != null || exitRotating || landZooming || portraitZooming,
    ) { progress ->
        try {
            progress.collect { p ->
                if (com.ican.tvplay.BuildConfig.DEBUG) {
                    android.util.Log.i("PbTrace", "predictive progress=$p")
                }
            }
            if (com.ican.tvplay.BuildConfig.DEBUG) {
                android.util.Log.i("PbTrace", "predictive committed → handleBack")
            }
            handleBack()
        } catch (cancelled: CancellationException) {
            if (com.ican.tvplay.BuildConfig.DEBUG) {
                android.util.Log.i("PbTrace", "predictive cancelled: ${cancelled.message}")
            }
        }
    }

    // 播完（STATE_ENDED）后进/退全屏：播放器不再输出新帧，尺寸变化后上一帧仍按旧尺寸渲染，
    // 表现为「全屏里缩着一个小画面」。这里只强制一次重排让 media3 重算 contentFrame；
    // 视频层本身已恒等于片源比例，重排后旧帧是等比放大，不会缩在角落。
    // 不再 seekTo(结尾前一帧)：MTK 解码器在越界位置上会吐一个未写满的空缓冲，
    // 表现为整块纯蓝（#000D7F）盖住视频区（用户实测：播完后双击进全屏必现）。
    fun refreshEndedFrame() {
        sharedPlayerView.requestLayout()
    }

    // PiP 画中画：系统会把整个 Activity 窗口直接缩小，因此不切换视图子树
    // （同一个 PlayerView 跨 AndroidView 节点 reparent 有崩溃风险），
    // 只在原布局上让视频铺满、隐藏全部覆盖层与信息流（对齐 fongmi 行为）

    val current = video

    // video 加载中 → 黑底转圈过渡页（LaunchedEffect 是异步的，初始帧 video 必为 null，
    // 不能立即渲染错误页，否则加载期间会闪错误页）
    if (current == null) {
        // PiP 理论上只在播放中（current 非空）进入；极端时序下给纯黑兜底，
        // 避免空数据把错误页/信息流投进画中画
        if (inPip || !initialized) {
            if (landLayout) {
                // 横版档位（平板，含平板竖屏）/ 转屏挂起期：沿用原来的整屏黑兜底
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!inPip) {
                        ArcStretchSpinner(size = 36.dp, strokeWidth = 3.dp, color = Color.White)
                    }
                }
                return
            }
            // 竖屏加载期骨架：与详情页同款（状态栏黑条 + 满宽 16:9 黑窗 + 下方主题色信息区），
            // 转圈画在黑窗里。此前这里是整屏黑过渡页，详情回来后整块黑"收成小窗"，
            // 观感上像预览区先铺满整屏再缩回去；改成骨架后首帧小窗就是最终尺寸。
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface),
            ) {
                val density = LocalDensity.current
                val screenW = maxWidth
                val screenH = maxHeight
                val topInset = rememberStatusBarTopDp()
                val previewH = with(density) {
                    (screenW.toPx() * (1f / Players.DEFAULT_VIDEO_ASPECT_RATIO)).toDp()
                }.coerceIn(150.dp, (screenH - topInset) / 2f)
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topInset)
                            .background(Color.Black),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewH)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!inPip) {
                            ArcStretchSpinner(size = 36.dp, strokeWidth = 3.dp, color = Color.White)
                        }
                    }
                }
            }
            return
        }
        PlayerErrorScreen(
            playerError = playerState.error,
            onBack = { handleBack() },
        )
        return
    }

    // 根：视频 View 常驻最底层，不随布局切换重建（Surface 不销毁 → 切换无黑屏），
    // 竖屏控制层/信息流、全屏控制层作为覆盖层画在其上。
    // BoxWithConstraints 提供整屏尺寸，供横屏小窗→全屏放大动画插值。
    // 缩放过程中露出的背景直接使用主题背景色（不做黑↔主题色渐变，
    // 避免动画中途出现与主题不一致的灰色板块）
    val rootBackground = if (landZooming || portraitZooming) {
        // 小窗↔全屏缩放动画中：根背景必须与信息列/下方区块同为主题色。
        // 点全屏时 landscapeFullscreen 在动画开始就置 true，若让它走下面的纯黑分支，
        // 横版布局未显式刷底色的空档（播放器上方/左侧、信息列底部与下方区块之间）
        // 会透出黑色，与刷白的区块拼成黑白块
        MiuixTheme.colorScheme.surface
    } else if (inPip || pipExitHold || renderFullscreen || exitRotating || pendingFullscreen != null || landscapeFullscreen) {
        Color.Black
    } else {
        MiuixTheme.colorScheme.surface
    }
    // 播放期间保持屏幕常亮（fongmi 同款：播放中 FLAG_KEEP_SCREEN_ON，暂停/退出恢复）
    val activity = context as? Activity
    DisposableEffect(playerState.playing) {
        val window = activity?.window
        if (playerState.playing) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(rootBackground)
            // 横屏缩放时信息列/下方区块滑出屏幕的部分需要裁剪（等价 ViewGroup 默认 clipChildren）
            .clipToBounds(),
    ) {
        val density = LocalDensity.current
        val screenW = maxWidth
        val screenH = maxHeight
        // 状态栏高度：横屏小窗的顶部起点，对齐 fongmi 的静态占位做法
        // （activity_video.xml 里 statusBar 占位 View + video 的 layout_below，由 inset 回调写入高度，
        // 不参与任何缩放动画）。关键点是「首帧就必须拿到真实值」：
        // Compose 的 WindowInsets 在页面首次组合时可能仍为 0（inset 回调晚一帧），
        // 那会让小窗先贴顶、随后再下落到正确位置。这里直接读窗口度量里的状态栏 inset：
        // 组合期即同步可用，且与系统栏可见性无关（全屏隐藏状态栏也不影响），值恒定。
        val statusBarTop = rememberStatusBarTopDp()
        // 视频宽高比：播放器 onVideoSizeChanged 报出（宽/高），未就绪时按 16:9 兜底
        val videoRatio = playerState.videoAspectRatio.takeIf { it > 0f }
            ?: Players.DEFAULT_VIDEO_ASPECT_RATIO
        // 竖屏预览小窗高度照抄 fongmi VideoActivity.changeHeight（app/src/mobile/.../VideoActivity.java）：
        //   高 = 屏宽 ×（视频高 / 视频宽），夹在 [150dp, (屏高 - 状态栏) / 2]；宽度恒满屏，只改高度。
        // 效果：16:9 = 屏宽×0.5625；2.35:1 银幕变矮不再留上下黑边；
        // 竖屏片源（9:16）算出来超上限就压到半屏高，画面在满宽黑框里居中、左右留黑边。
        // 触发时机等价 fongmi 的 onSizeChanged → changeHeight()：比例一变就重算。
        // 用「竖屏归一化」的屏幕尺寸算：横屏时 screenW 是长边（872.7dp），直接套进去
        // 目标高度会被夹到 (392.7-44)/2 ≈ 174.4dp = 479px，等转回竖屏目标变 460px，
        // animateDpAsState 又用 300ms 走这段差值 → 小窗落地后还要往上蹭 10px（日志 131→121）。
        // 取 min/max 后两种方向算出的目标恒等，转屏不再触发这段高度动画。
        val portraitW = minOf(screenW, screenH)
        val portraitH = maxOf(screenW, screenH)
        val portraitPreviewTargetH = with(density) {
            (portraitW.toPx() * (1f / videoRatio)).toDp()
        }.coerceIn(150.dp, (portraitH - statusBarTop) / 2f)

        // 横屏小窗↔全屏放大/缩回过渡：宽/高/顶部偏移连续插值，
        // PlayerView 全程不重建，只有画面在放大，不挂任何控制层
        // 五个动画值保留为 State<Dp>（不委托）：使用方全部在布局/绘制阶段 lambda 内读取，
        // 动画进行期间不触发逐帧重组
        val expandTransition = updateTransition(
            targetState = landscapeFullscreen,
            label = "landPlayerExpand",
        )
        // 横屏小窗固定 16:9（硬性要求：不跟随视频比例）。但宽/高像素必须「同源取整」：
        // 若宽度与高度各自独立 roundToPx()，盒子的真实像素比例会偏离 16:9 约 1px，
        // 填充/裁剪模式下 ExoPlayer 的 SurfaceView（按容器宽度 f2i 截断高度）底部会露出一条细黑线。
        // 因此高度按「宽度像素 × 9/16」取整后再还原为 Dp，保证 heightPx = round(widthPx × 9/16)。
        val smallLandW = screenW * LAND_PLAYER_WIDTH_FRACTION
        val smallLandH = with(density) { (smallLandW.toPx() * (9f / 16f)).roundToInt().toDp() }
        val animWidth = expandTransition.animateDp(
            transitionSpec = { tween(LAND_ZOOM_DURATION_MS, easing = FastOutSlowInEasing) },
            label = "animWidth",
        ) { expanded ->
            if (expanded) screenW else smallLandW
        }
        val animHeight = expandTransition.animateDp(
            transitionSpec = { tween(LAND_ZOOM_DURATION_MS, easing = FastOutSlowInEasing) },
            label = "animHeight",
        ) { expanded ->
            if (expanded) screenH else smallLandH
        }
        val animTop = expandTransition.animateDp(
            transitionSpec = { tween(LAND_ZOOM_DURATION_MS, easing = FastOutSlowInEasing) },
            label = "animTop",
        ) { expanded -> if (expanded) 0.dp else statusBarTop }
        // 横屏小窗左缘：对齐页面左基线（手机 16dp / 平板 24dp），放大到全屏时收回到 0
        val pageHorizontal = pageHorizontalPadding()
        val animLeft = expandTransition.animateDp(
            transitionSpec = { tween(LAND_ZOOM_DURATION_MS, easing = FastOutSlowInEasing) },
            label = "animLeft",
        ) { expanded -> if (expanded) 0.dp else pageHorizontal }
        // 设备屏幕物理圆角（全屏时视频四角与屏幕边角贴合）；取不到（非圆角屏/低版本）时退回 16dp
        val deviceCorner = remember {
            val radiusPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context as? Activity)?.display
                    ?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
                    ?.radius ?: 0
            } else {
                0
            }
            if (radiusPx > 0) with(density) { radiusPx.toDp() } else Spacing.corner
        }
        // 圆角随放大/缩回同步插值：小窗 16dp → 全屏=设备屏幕圆角，
        // 这样放大最后一帧与全屏布局的圆角完全一致，切换时不跳变
        val animCorner = expandTransition.animateDp(
            transitionSpec = { tween(LAND_ZOOM_DURATION_MS, easing = FastOutSlowInEasing) },
            label = "animCorner",
        ) { expanded -> if (expanded) deviceCorner else Spacing.corner }

        // 动画收敛后处理：放大结束挂全屏布局（纯净画面起手）；
        // 缩回期间横版布局全程渲染，信息列/下方区块锚定视频边缘跟随滑入滑出（fongmi ChangeBounds）
        LaunchedEffect(landZoomToken) {
            if (landZooming) {
                snapshotFlow {
                    !expandTransition.isRunning &&
                        expandTransition.currentState == expandTransition.targetState
                }.first { it }
                if (landscapeFullscreen) {
                    // 放大完成：挂全屏布局，工具栏默认隐藏
                    landZooming = false
                    fullscreen = true
                    controlsVisible = false
                    refreshEndedFrame()
                } else {
                    // 缩回完成：恢复方向自由并显示状态栏（组件位置均由动画值驱动，无 inset 跳变）
                    controlsVisible = false
                    val activity = context as? Activity
                    activity.restoreDefaultOrientation()
                    if (activity != null) {
                        WindowCompat.getInsetsController(
                            activity.window, activity.window.decorView,
                        ).show(WindowInsetsCompat.Type.statusBars())
                    }
                    landZooming = false
                    refreshEndedFrame()
                }
            }
        }
        // 竖屏画面内容的尺寸由我们自己算，不看 media3 的 resizeMode：
        // media3 在「裁剪」下会把内层 AspectRatioFrameLayout 撑到 1880x3342（比小窗高 3 倍）
        // 这类「铺满不留边」的尺寸，溢出部分没有被裁剪，直接画到信息流上（实测画面到 2290px）。
        // 所以这里按三种档位直接给出画面矩形，PlayerView 恒 FIT，超出部分由外层 Box 裁掉：
        //   原始 = 等比装进框内（contain）；裁剪 = 等比铺满框（cover，超出裁掉）；填充 = 拉伸铺满
        // 预览框 = 状态栏之下满宽 16:9；全屏框 = 整屏。缩放期间在这两套矩形之间插值。
        fun contentSizeFor(boxW: Dp, boxH: Dp): Pair<Dp, Dp> = when (playerState.scaleMode) {
            Players.ScaleMode.FIT -> {
                val h = minOf(boxH, boxW / videoRatio)
                Pair(h * videoRatio, h)
            }
            Players.ScaleMode.ZOOM -> {
                val h = maxOf(boxH, boxW / videoRatio)
                Pair(h * videoRatio, h)
            }
            Players.ScaleMode.FILL -> Pair(boxW, boxH)
        }
        // 竖屏小窗↔竖屏全屏缩放：起点 = 预览小窗（满宽、按片源比例的高度、状态栏以下），
        // 终点 = 铺满整屏（顶部 0、高 = 屏高），收敛后由 fullscreen 分支接管。
        // 关键：几何全部只由一个进度值 pExpand 驱动，画面内容矩形每帧都从「当前框高」
        // 按片源比例重新推导（contentSizeFor 是纯函数），动画全程画面比例恒等于片源。
        // 此前宽/高各自独立插值，中间帧的矩形比例会跑偏，竖屏片源双击放大时
        // 表现为画面/黑框轻微跳动（竖屏片源宽高变化幅度最大，最明显）。
        val portraitTransition = updateTransition(
            targetState = portraitFullscreen,
            label = "portraitPlayerExpand",
        )
        val pExpand = portraitTransition.animateFloat(
            transitionSpec = { tween(LAND_ZOOM_DURATION_MS, easing = FastOutSlowInEasing) },
            label = "pExpand",
        ) { expanded -> if (expanded) 1f else 0f }
        // 以下几何值一律走 derivedStateOf：只在布局/绘制阶段读取时求值，
        // 动画逐帧不会触发重组（与原先 State<Dp> 的用法一致）
        val pAnimWidth = remember(screenW) { derivedStateOf { screenW } }
        // 目标高度平滑过渡：片源比例报出后（16:9 → 竖屏 9:16 等）小窗"长高/变矮"而不是跳一下，
        // 对齐 fongmi VideoActivity.changeHeight 的 300ms 动画
        val animatedPreviewTargetH = animateDpAsState(
            targetValue = portraitPreviewTargetH,
            animationSpec = tween(PREVIEW_HEIGHT_ANIM_MS, easing = LinearOutSlowInEasing),
            label = "previewTargetH",
        )
        val pAnimHeight = remember(animatedPreviewTargetH, screenH) {
            derivedStateOf { lerp(animatedPreviewTargetH.value, screenH, pExpand.value) }
        }
        val pAnimTop = remember(statusBarTop) {
            derivedStateOf { lerp(statusBarTop, 0.dp, pExpand.value) }
        }
        val pContentWidth = remember(pAnimHeight, playerState.scaleMode, videoRatio) {
            derivedStateOf { contentSizeFor(screenW, pAnimHeight.value).first }
        }
        val pContentHeight = remember(pAnimHeight, playerState.scaleMode, videoRatio) {
            derivedStateOf { contentSizeFor(screenW, pAnimHeight.value).second }
        }
        // 竖屏预览小窗无圆角（用户要求直角满宽铺），缩放期间也不插值圆角：
        // 收敛后由 fullscreen 分支按设备屏幕圆角裁剪，圆角落在黑边上本来就看不见

        LaunchedEffect(portraitZoomToken) {
            if (portraitZooming) {
                snapshotFlow {
                    !portraitTransition.isRunning &&
                        portraitTransition.currentState == portraitTransition.targetState
                }.first { it }
                if (portraitFullscreen) {
                    // 放大完成：挂全屏布局，工具栏默认隐藏（与横屏同一手感）
                    portraitZooming = false
                    fullscreen = true
                    controlsVisible = false
                    refreshEndedFrame()
                } else {
                    // 缩回完成：恢复系统栏并撤掉缩放态。
                    // 竖屏布局的首屏高度全部由动画值驱动（不再读 statusBars inset），
                    // 所以系统栏出现/消失都不会改变布局尺寸，无需再等帧对齐
                    val activity = context as? Activity
                    activity?.let {
                        WindowCompat.getInsetsController(it.window, it.window.decorView)
                            .show(WindowInsetsCompat.Type.systemBars())
                    }
                    portraitZooming = false
                    controlsVisible = false
                    activity?.restoreDefaultOrientation()
                    refreshEndedFrame()
                }
            }
        }

        // 竖屏（稳态 + 缩放）：外框只当黑底，视频层按片源比例居中贴在框内。
        // 直接逐帧改 PlayerView 尺寸时，media3 的黑边重排跟不上每帧变化，动画中途画面被压扁
        // （录屏 4.20s 1080×1380、4.24s 1080×1560，正常应 1080×1920），最后一帧才回正 → 「抖/跳」。
        // 让视频层比例恒等于片源后，Surface 重采样滞后最多表现为一次整体轻微缩放，永不变形；
        // 且首尾像素与静止小窗、全屏稳态完全重合（黑边本来就该是黑的）。
        // 其余分支（全屏 / 横屏 / 画中画）保持原样：盒子本身就是稳态尺寸，交给 media3 自身 FIT
        // 竖屏（非全屏、非横屏）画面几何一律由我们自己算，PlayerView 恒 FIT，
        // 所以这个分支对三种档位都走同一套「内容矩形 + 居中 + 外层裁剪」
        // 旋转挂起、屏幕还没转过去时也要沿用自算矩形 + FIT：否则 letterboxed 一落 false，
        // resizeMode 立刻切到 scaleMode，media3 重量一次 contentFrame，画面会先缩一下再转
        // 退全屏等转屏（exitRotating）必须排除：那一刻外框还是铺满整屏，若仍按预览小窗
        // 内容尺寸居中，SurfaceView 会真的把画面缩成预览大小浮在屏幕正中（转屏完成后再跳回顶部）。
        // 汉一帧都不该出现，所以这段时间内层一律铺满外框，交给 media3 按当前档位出画
        val letterboxed = !inPip && !pipExitHold && !renderFullscreen && !exitRotating &&
            (pendingFullscreen == null || !isLandscape) && !landLayout

        // 播完态「静帧替身」：STATE_ENDED 之后解码器已经停了，这时再 resize TextureView，
        // Android 要么重新分配一块没写过的空缓冲（未写入的 YUV → 深蓝块），
        // 要么保留旧缓冲但变换矩阵还是旧尺寸（画面缩到左上角）。
        // 手机和平板都会中，和播放内核无关，只跟「播完后还去改 surface 尺寸」有关。
        // 所以播完那一刻抓一帧存成位图，播完期间视频区只显示这张图、真实 surface 隐藏；
        // 之后进/退全屏、侧滑缩放都只是缩放一张图片，surface 怎么 resize 都无所谓。
        // 恢复播放 / 切集 / seek（state 离开 ENDED）时丢弃这张图，把画面交回 surface。
        var endedFrame by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        LaunchedEffect(playerState.ended) {
            if (!playerState.ended) {
                if (endedFrame != null) endedFrame = null
                return@LaunchedEffect
            }
            if (endedFrame != null) return@LaunchedEffect
            // 抓帧偶尔会赶在最后一帧上屏之前，最多重试 3 次
            var captured: android.graphics.Bitmap? = null
            var attempt = 0
            while (captured == null && attempt < 3) {
                if (attempt > 0) delay(120)
                captured = runCatching { findTextureView(sharedPlayerView)?.bitmap }.getOrNull()
                attempt++
            }
            val raw = captured
            if (raw != null) {
                val cropped = cropToContent(raw, videoRatio)
                endedFrame = cropped.asImageBitmap()
                if (com.ican.tvplay.BuildConfig.DEBUG) {
                    android.util.Log.i(
                        "PipTrace",
                        "endedFrame ${cropped.width}x${cropped.height} " +
                            "raw=${raw.width}x${raw.height} ratio=$videoRatio",
                    )
                }
            } else {
                android.util.Log.w("PipTrace", "endedFrame capture failed")
            }
        }

        // 在组合期读一次状态：AndroidView 的 update lambda 不是组合上下文，
        // 直接在里面读 State 不会触发重组，所以先取到局部变量
        val endedFrameSnapshot = endedFrame

        Box(
            modifier = when {
                // 画中画（含退出过渡）：铺满整个（已被系统缩小的）Activity 窗口
                inPip || pipExitHold -> Modifier.fillMaxSize()
                // 全屏（手机旋转 / 平板横屏）：无条件铺满，fongmi MATCH_PARENT 同款。
                // 平板横屏全屏稳态若走下面 isLandscape 定尺寸分支（宽高取自 Configuration），
                // PiP 退出放大期间可能拿到中间态尺寸，Surface 与系统动画窗口错位跳变
                renderFullscreen -> Modifier.fillMaxSize().clip(SquircleShape(deviceCorner))
                // 旋转挂起、屏幕还没转过去：几何/位置/比例全部冻结在竖屏小窗，
                // 不提前铺满、也不切 resizeMode，点全屏到真正转起来之间画面一帧都不动
                pendingFullscreen != null && !isLandscape ->
                    Modifier
                        .offset { IntOffset(0, pAnimTop.value.roundToPx()) }
                        .animatedSize(pAnimWidth, pAnimHeight)
                // 旋转挂起、已横过来只等 fullscreen 落位：直接铺满，跳过横版 38% 小窗那一帧。
                // 这里不叠加 clip：旋转过渡只有 200~300ms，四角都是黑底看不出圆角差异，
                // 省掉一次离屏图层合成（interop TextureView 上这笔开销不小，双击后更跟手）
                pendingFullscreen != null -> Modifier.fillMaxSize()
                // 退全屏等旋转：继续铺满（此刻还没转回竖屏，切横版小窗会先闪一下平板样式）。
                // 必须带上 isLandscape：屏幕一翻到竖屏、exitRotating 还没被 LaunchedEffect 清掉时，
                // 若仍走这一支，外框还是铺满整屏、内层却已按预览高度出画，画面就缩在屏幕正中闪几帧。
                // 加上 isLandscape 后，配置一变就落到竖屏分支，框和画面同时落在最终小窗位置
                exitRotating && isLandscape -> Modifier.fillMaxSize()
                // 横版档位（平板，含平板竖屏）：小窗静止态、放大/缩回动画中、已放大全屏
                // 全程使用同一套 offset/宽高修饰符（静止值恰为 38% 小窗），
                // 不在动画修饰符与 statusBarsPadding 静态修饰符之间切换，避免收敛瞬间上跳
                // 圆角：换 TextureView 后视频参与 View 合成，可由 graphicsLayer 直接裁剪；
                // 小窗 16dp，放大过程中与窗口同步插值到设备屏幕圆角
                landLayout ->
                    Modifier
                        // 动画值全部在布局/绘制阶段的 lambda 内读取：放大/缩回动画期间本分支不触发逐帧重组
                        .offset { IntOffset(animLeft.value.roundToPx(), animTop.value.roundToPx()) }
                        .animatedSize(animWidth, animHeight)
                        .graphicsLayer {
                            shape = SquircleShape(animCorner.value)
                            clip = true
                            // 离屏合成：圆角边缘只做一次抗锯齿，避免视频层与覆盖层边缘各自的
                            // 半透明像素叠出弧线黑边
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                else ->
                    // 竖屏：静止态与缩放动画共用同一套 offset/宽高修饰符
                    // （静止值即「状态栏以下 + 满宽 16:9 直角」），
                    // 不在动画修饰符与 statusBarsPadding 静态修饰符之间切换，收敛瞬间不会跳。
                    // 这里不再叠 graphicsLayer：小窗本来就没有圆角，离屏图层只会在 TextureView
                    // 尺寸逐帧变化时多一层拉伸风险，还多一次整幅拷贝
                    Modifier
                        .offset { IntOffset(0, pAnimTop.value.roundToPx()) }
                        .animatedSize(pAnimWidth, pAnimHeight)
            }
                // 裁剪档位下画面矩形会大于框（cover），超出部分在这里裁掉，
                // 绝不越界画到下面的信息流上
                .clipToBounds()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            // key 绑定到当前渲染方式对应的 PlayerView：设置里切换 TEXTURE/SURFACE 后
            // AndroidView 节点重建、factory 重新返回新视图（否则 factory 只在首帧执行，换不了视图）
            key(sharedPlayerView) {
                AndroidView(
                    factory = { sharedPlayerView },
                    update = { view ->
                        // 竖屏几何由我们自己算（原始/裁剪/填充都在 contentSizeFor 里定了尺寸），
                        // 这里恒 FIT，不让 media3 再按自己的方式撑大内层（撑大会溢出小窗压到信息流）
                        view.resizeMode = if (letterboxed) {
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        } else {
                            playerState.scaleMode.resizeMode
                        }
                        if (com.ican.tvplay.BuildConfig.DEBUG) {
                            android.util.Log.i(
                                "PipTrace",
                                "videoMode mode=${playerState.scaleMode} letterboxed=$letterboxed " +
                                    "ratio=${"%.3f".format(videoRatio)}",
                            )
                        }
                        // PlayerView 背景直接用黑色：视频 FIT 模式黑边区显示黑色。
                        view.setBackgroundColor(android.graphics.Color.BLACK)
                        if (view.player !== Players.player) Players.bindPlayerView(view)
                        // 静帧替身顶上时把真实 surface 藏掉，避免空缓冲/旧矩阵缓冲从图外露出来
                        val hide = endedFrameSnapshot != null
                        view.visibility = if (hide) android.view.View.INVISIBLE else android.view.View.VISIBLE
                    },
                    modifier = if (letterboxed) {
                        // 居中 + 精确内容矩形：宽/高在布局阶段读取，逐帧不重组；
                        // 裁剪档位下内容会比框大，由外层 Box 的 clipToBounds 裁掉
                        Modifier
                            .align(Alignment.Center)
                            .animatedSize(pContentWidth, pContentHeight)
                    } else {
                        Modifier.fillMaxSize()
                    },
                )
            }
            // 播完后的静态画面：几何与真实视频层完全一致（同一套插值），
            // 缩放动画期间它跟着黑框走，完全不碰 surface
            endedFrameSnapshot?.let { frame ->
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = if (letterboxed) {
                        Modifier
                            .align(Alignment.Center)
                            .animatedSize(pContentWidth, pContentHeight)
                    } else {
                        Modifier.aspectRatio(videoRatio, matchHeightConstraintsFirst = false)
                    },
                )
            }
        }
        // 诊断日志：仅 debug 构建保留（release 自动剔除）
        val branchName = when {
            inPip -> "pip"
            pipExitHold -> "pipHold"
            renderFullscreen -> "full"
            pendingFullscreen != null -> "pending"
            isLandscape -> "land"
            else -> "portrait"
        }
        LaunchedEffect(branchName) {
            if (com.ican.tvplay.BuildConfig.DEBUG) {
                val dv = (context as? Activity)?.window?.decorView
                android.util.Log.i(
                    "PipTrace",
                    "branch=$branchName inPip=$inPip hold=$pipExitHold full=$renderFullscreen fs=$fullscreen " +
                        "landFull=$landscapeFullscreen land=$isLandscape " +
                        "screen=${screenW.value}x${screenH.value} win=${dv?.width}x${dv?.height}",
                )
            }
        }
        when {
        // 画中画（含退出过渡）：只保留最底层视频，不挂任何覆盖层/信息流
        inPip || pipExitHold -> Unit
        renderFullscreen -> {
        FullscreenPlayLayout(
            current = current,
            episodeIndex = episodeIndex,
            lineEpisodes = lineEpisodes,
            playerState = playerState,
            controlsVisible = controlsVisible,
            locked = locked,
            settings = settings,
            onToggleControls = { controlsVisible = !controlsVisible },
            // 侧栏打开即把控制栏显隐状态落为「隐藏」：侧栏收起后控制栏不会自己弹回来，需用户再点一下才唤出
            onHideControls = { controlsVisible = false },
            onControlInteract = { controlsTick++ },
            onTogglePlay = { Players.playPause() },
            onPrev = { if (episodeIndex > 0) episodeIndex-- },
            onNext = { if (episodeIndex < lineEpisodes.size - 1) episodeIndex++ },
            onExitFullscreen = { handleBack() },
            onLockToggle = { locked = true; controlsVisible = false },
            onUnlock = { locked = false; controlsVisible = true },
            onBack = { handleBack() },
            onSpeedChange = { settings.setPlayerSpeed(it) },
            playbackSpeed = settings.playerSpeed.collectAsStateWithLifecycle().value,
            onScaleChange = { settings.setPlayerScale(it.name) },
            autoNext = savedAutoNext,
            onAutoNextChange = { settings.setPlayerAutoNext(it) },
            onEpisodeSelect = requestEpisodeChange,
            onEngineChange = { kernel ->
                // 点了就切，不能只写设置：ISO 自动切 MPV 只改内存态、设置仍是 EXO，
                // 此时 setPlayerEngine("EXO") 写的是同值，StateFlow 不发射 → LaunchedEffect 不重启 → 点了没反应。
                Players.setKernel(kernel)
                settings.setPlayerEngine(kernel.name)
            },
            onBufferChange = { settings.setPlayerBuffer(it.multiplier) },
            onDecodeChange = { Players.setDecode(it); settings.setPlayerDecode(it.name) },
            openingMs = openingMs,
            endingMs = endingMs,
            onMarkOpening = { markOpening() },
            onMarkEnding = { markEnding() },
            onClearOpening = {
                openingMs = 0
                toastMsg("已清除片头标记")
                saveProgress()
            },
            onClearEnding = {
                endingMs = 0
                toastMsg("已清除片尾标记")
                saveProgress()
            },
            onSeekDragChange = { seekDragging = it },
            onOpenSubtitleSettings = { controlsVisible = false; showSubtitleSettings = true },
            onAddSubtitle = { launchSubtitlePicker() },
            subtitleSettingsOpen = showSubtitleSettings,
        )
        }
        // 进全屏旋转过渡期：仅黑底，不渲染任何非全屏布局（白底信息流会露出来）
        pendingFullscreen == true -> Unit
        // 退全屏等旋转：只有视频，不挂横版/竖屏布局（避免先闪一下平板端样式）
        exitRotating -> Unit
        else -> if (landLayout) {
            // 自然横屏（平板）：fongmi activity_detail_land 横版布局，
            // 信息列/下方区块锚定动画中的播放器边界，随缩放一起滑出滑入（ChangeBounds 效果）
            LandscapePlayLayout(
                current = current,
                episodeIndex = episodeIndex,
                lineEpisodes = lineEpisodes,
                // 传入解析后的实际播放线路：进入播放页未手动点选时 lineFlag 可能为空，
                // 直接透传会导致线路栏没有任何选中高亮（fongmi setFlags 同款解析）
                lineFlag = playLine?.flag ?: lineFlag,
                playerState = playerState,
                controlsVisible = controlsVisible,
                locked = locked,
                playerTop = animTop,
                playerLeft = animLeft,
                playerWidth = animWidth,
                playerHeight = animHeight,
                playerCorner = animCorner,
                smallPlayerWidth = screenW * LAND_PLAYER_WIDTH_FRACTION,
                screenWidth = screenW,
                screenHeight = screenH,
                pageHorizontal = pageHorizontal,
                onToggleControls = { controlsVisible = !controlsVisible },
                onTogglePlay = { Players.playPause() },
                onPrev = { if (episodeIndex > 0) episodeIndex-- },
                onNext = { if (episodeIndex < lineEpisodes.size - 1) episodeIndex++ },
                onEnterFullscreen = {
                    // 已在横屏：无旋转事件，启动小窗→全屏放大动画，
                    // 动画结束（LaunchedEffect）后再挂全屏布局且工具栏默认隐藏
                    controlsTick++
                    controlsVisible = false
                    (context as? Activity)?.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    landscapeFullscreen = true
                    landZooming = true
                    zoomStartedAt = android.os.SystemClock.elapsedRealtime()
                    landZoomToken++
                },
                onBack = { handleBack() },
                onLineChange = requestLineChange,
                onEpisodeSelect = requestEpisodeChange,
                qualities = qualities,
                qualityPosition = qualityPosition,
                onQualityChange = requestQualityChange,
                onSeekDragChange = { seekDragging = it },
                blockedMessage = btBlockedMsg,
            )
        } else {
        // 竖屏手机 / 退全屏旋转过渡期：统一竖屏布局
        PortraitPlayLayout(
            current = current,
            episodeIndex = episodeIndex,
            lineEpisodes = lineEpisodes,
            // 同 LandscapePlayLayout：解析后的实际播放线路，保证初始就有选中高亮
            lineFlag = playLine?.flag ?: lineFlag,
            playerState = playerState,
            refreshing = errorRefreshing,
            onRefreshError = onRefreshErrorPlayback,
            // 视频占位区（含状态栏黑条）的顶部/高度交给缩放动画驱动：
            // 放大时占位区撑满整屏、信息流被顶出屏幕；缩小时反过来滑回原位。
            // 布局全程挂载不卸载，避免「信息流整块瞬间消失/出现」造成的跳动
            playerTop = pAnimTop,
            playerHeight = pAnimHeight,
            zooming = portraitZooming,
            controlsVisible = controlsVisible,
            locked = locked,
            onToggleControls = { controlsVisible = !controlsVisible },
            onTogglePlay = { Players.playPause() },
            onPrev = { if (episodeIndex > 0) episodeIndex-- },
            onNext = { if (episodeIndex < lineEpisodes.size - 1) episodeIndex++ },
            onEnterFullscreen = {
                controlsTick++
                // 视频宽高比 <1 视为竖屏片源（OK猫 changedLandscape 同款判据：只有 width>height 才请求横屏）
                val ratio = playerState.videoAspectRatio.takeIf { it > 0f }
                    ?: Players.DEFAULT_VIDEO_ASPECT_RATIO
                if (isLandscape) {
                    // 平板已横屏：不会触发 Configuration 变化，直接切全屏布局
                    landscapeFullscreen = true
                    fullscreen = true
                } else if (ratio < 1f) {
                    // 竖屏全屏：不请求旋转，把常驻 PlayerView 从预览小窗放大到铺满（设备保持竖屏）。
                    // 放大期间只渲染纯视频，收敛后再挂全屏控制层
                    // 系统栏与缩放同一帧收起：只靠 immersive 的 LaunchedEffect 会晚一帧，
                    // 状态栏图标会压在放大中的画面上（录屏 4.24s 帧）
                    activityWindow?.let { w ->
                        WindowCompat.getInsetsController(w, w.decorView)
                            .hide(WindowInsetsCompat.Type.systemBars())
                    }
                    Players.setVideoPresent(true)
                    landscapeFullscreen = false
                    portraitFullscreen = true
                    portraitZooming = true
                    zoomStartedAt = android.os.SystemClock.elapsedRealtime()
                    portraitZoomToken++
                    controlsVisible = false
                } else {
                    // 先请求横屏，旋转完成（Configuration 变化）后再铺满全屏
                    landscapeFullscreen = false
                    portraitFullscreen = false
                    // 请求旋转前同步把根底色切黑（系统栏同步隐藏并切浅色图标）。
                    // 窗口背景不再重复设置：Players.setVideoPresent(true) 已触发上方 LaunchedEffect
                    // 把 window 背景切成黑色，这里再新建 ColorDrawable 重设会多一次全窗口重绘。
                    Players.setVideoPresent(true)
                    // 旋转请求尽早发出：它是唯一无法避免的系统调用，先发出去让系统并行处理旋转，
                    // 后面的系统栏隐藏与 UI 重组与它并行，缩短双击到旋转启动的等待
                    (context as? Activity)?.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    activityWindow?.let { w ->
                        val controller = WindowCompat.getInsetsController(w, w.decorView)
                        controller.isAppearanceLightStatusBars = false
                        controller.isAppearanceLightNavigationBars = false
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    }
                    pendingFullscreen = true
                }
            },
            onLockToggle = { locked = true; controlsVisible = false },
            onUnlock = { locked = false; controlsVisible = true },
            onBack = { handleBack() },
            onLineChange = requestLineChange,
            onEpisodeSelect = requestEpisodeChange,
            qualities = qualities,
            qualityPosition = qualityPosition,
            onQualityChange = requestQualityChange,
            onSpeedChange = { settings.setPlayerSpeed(it) },
            onScaleChange = { settings.setPlayerScale(it.name) },
            onBufferChange = { settings.setPlayerBuffer(it.multiplier) },
            onSeekDragChange = { seekDragging = it },
            onOpenSubtitleSettings = { controlsVisible = false; showSubtitleSettings = true },
            onAddSubtitle = { launchSubtitlePicker() },
            blockedMessage = btBlockedMsg,
        )
        }
        }

        // 字幕设置侧边栏：与选集侧边栏同款，从右侧滑入；竖屏时避让状态栏
        if (showSubtitleSettings) {
            SubtitleSettingSideSheet(
                statusBarInset = !(renderFullscreen || landscapeFullscreen || isLandscape),
                onDismiss = { showSubtitleSettings = false },
            )
        }
    }
}

// ========== 竖屏布局 ==========

@Composable
private fun PortraitPlayLayout(
    current: Video?,
    episodeIndex: Int,
    lineEpisodes: List<Episode>,
    lineFlag: String,
    playerState: Players.PlayerState,
    /** 错误态下拉刷新的转圈状态（只在 playerState.error != null 时才会变成 true） */
    refreshing: Boolean,
    /** 错误态下拉刷新：重解析当前集（等价 fongmi mVod.refresh() → RefreshEvent.Type.PLAYER） */
    onRefreshError: () -> Unit,
    /** 视频占位区顶部（静止=状态栏高度，全屏=0），由小窗↔全屏缩放动画驱动 */
    playerTop: State<Dp>,
    /** 视频占位区高度（静止=满宽 16:9，全屏=屏高），同上 */
    playerHeight: State<Dp>,
    /** 缩放动画进行中：隐藏覆盖层（控制条/转圈），只让占位区尺寸跟着变 */
    zooming: Boolean,
    controlsVisible: Boolean,
    locked: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onLockToggle: () -> Unit,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
    onLineChange: (String) -> Unit,
    onEpisodeSelect: (Int) -> Unit,
    /** 可选画质（fongmi Url.values）：>1 条才显示「画质」行 */
    qualities: List<com.ican.tvplay.data.remote.PlayQuality>,
    qualityPosition: Int,
    onQualityChange: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onScaleChange: (Players.ScaleMode) -> Unit,
    onBufferChange: (Players.BufferTier) -> Unit,
    onSeekDragChange: (Boolean) -> Unit,
    onOpenSubtitleSettings: () -> Unit,
    onAddSubtitle: () -> Unit,
    /** 非空时代表当前源是磁力/BT 地址（播不了），预览区画提示文案 */
    blockedMessage: String? = null,
) {
    var showSpeed by remember { mutableStateOf(false) }
    var showScale by remember { mutableStateOf(false) }
    var showBuffer by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }

    // 下拉刷新受控态：仅用户真正下拉触发（onRefresh）才置 true；refreshing 结束后清掉，交组件播完成收回动画。
    // 放在函数体顶部而非 if 块内，避免条件翻转时状态被丢弃；自动刷新路径不经 onRefresh，此值保持 false。
    var pullRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (!refreshing) pullRefreshing = false }

    // 单一 Column 根：NavHost 目的地容器会给多个顶层子元素传播 fill 约束，
    // 多顶层发射会让底部标签栏被拉伸成全屏、白色背景盖住视频区（"纯白底+只有按钮"的根因）
    Column(
        modifier = Modifier
            .fillMaxSize(),
        // 背景由根 Box 提供（Column 若带背景会盖住常驻 PlayerView）
    ) {

    // === 视频播放区占位（PlayerView 常驻于根 Box 最底层，此处仅放控制层/加载/错误覆盖层） ===
    // 竖屏预览小窗照抄 OK猫：满宽 + 固定 16:9，视频在盒内由 PlayerView 的 resizeMode 自适应，
    // 容器尺寸不随视频比例变化 —— 与底层 PlayerView 用同一个 16:9 比例，像素天然对齐。
    // 平板横屏走 LandscapePlayLayout（固定 38% 宽 16:9），不进入此布局。
    // 状态栏占位区：与视频区统一黑底（沉浸式），避免返回动画中浅色 surface 被转场压暗成灰色横条。
    // 用独立黑条承载状态栏高度，而不给视频占位容器加背景——否则不透明背景会盖住底层常驻的 PlayerView 画面。
    // 高度跟随缩放动画（全屏时收到 0），正好贴着视频上缘，不会在放大过程中露出一条黑缝
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animatedHeight(playerTop)
            .background(Color.Black),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animatedHeight(playerHeight),
    ) {
        PreviewPlayerArea(
            modifier = Modifier.fillMaxSize(),
            playerState = playerState,
            // 进详情页即转圈（首次画面就绪前，无网速）；ready 后只随缓冲转。
            // 刷新态只由 miuix 圆圈在内容区顶部表达，预览小窗不再叠加第二个缓冲转圈。
            // 缩放动画期间不画覆盖层（转圈跟着放大缩小会很怪）
            showLoading = !zooming && !playerState.ready && playerState.error == null,
            blockedMessage = blockedMessage,
            controlsVisible = controlsVisible && !zooming,
            locked = locked,
            onToggleControls = onToggleControls,
            onBack = onBack,
            onTogglePlay = onTogglePlay,
            onPrev = onPrev,
            onNext = onNext,
            onEnterFullscreen = onEnterFullscreen,
            onSeekDragChange = onSeekDragChange,
        )
    }

    // === 详情信息流（左右边距对齐全局规范；板块主间距统一 sectionSpacing） ===
    // 详情加载期间整屏遮罩转圈统一显示（见根 Box），此处不再分区域转圈
    //
    // 下拉刷新只在「播放错误」态启用 —— 对齐 fongmi mobile VideoActivity：
    // swipeLayout 在 initView / prepareSource 里都是 setEnabled(false)，
    // 只有 resetPlaybackForError / showEmpty 才 setEnabled(true)，即正常播放时详情区根本拉不动。
    // `|| refreshing`：刷新一开始 beginSwitch 会把错误态清掉，这时必须继续挂着这层包裹，
    // 否则圆点会连同容器一起消失（刷新过程中途"闪没"）
    if (playerState.error != null || refreshing) {
        PullToRefresh(
            isRefreshing = pullRefreshing && refreshing,
            onRefresh = { pullRefreshing = true; onRefreshError() },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            // 文案与样式回到 miuix 原生：四态中文提示 + 默认小号字，不再用 0.sp 隐藏文字
            refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新完成"),
            refreshTextStyle = MaterialTheme.typography.bodySmall,
            // contentPadding 只 offset RefreshHeader（不影响 DetailInfoColumn）：
            // 12dp 实测仍偏上，16dp 再下移一档（黑块底→标题顶这段空白内居中）
            contentPadding = PaddingValues(top = 16.dp),
        ) {
            DetailInfoColumn(
                modifier = Modifier.fillMaxSize(),
                current = current,
                lineFlag = lineFlag,
                qualities = qualities,
                qualityPosition = qualityPosition,
                onQualityChange = onQualityChange,
                lineEpisodes = lineEpisodes,
                episodeIndex = episodeIndex,
                onEpisodeSelect = onEpisodeSelect,
                onLineChange = onLineChange,
            )
        }
    } else {
        DetailInfoColumn(
            modifier = Modifier.weight(1f),
            current = current,
            lineFlag = lineFlag,
            qualities = qualities,
            qualityPosition = qualityPosition,
            onQualityChange = onQualityChange,
            lineEpisodes = lineEpisodes,
            episodeIndex = episodeIndex,
            onEpisodeSelect = onEpisodeSelect,
            onLineChange = onLineChange,
        )
    }

    // === 对话框（常驻组合：内部按 show 做「底部滑入 / 原路滑回」动画） ===
    OptionDialog(showSpeed, "倍速", listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 3.0f), playerState.speed, { formatSpeed(it) }, { onSpeedChange(it); showSpeed = false }) { showSpeed = false }
    OptionDialog(showScale, "画面缩放", Players.ScaleMode.entries, playerState.scaleMode, { it.label }, { onScaleChange(it); showScale = false }) { showScale = false }
    OptionDialog(showBuffer, "缓冲档位", Players.BufferTier.entries, playerState.bufferTier, { it.label }, { onBufferChange(it); showBuffer = false }) { showBuffer = false }
    OptionDialog(showAudio, "音轨", playerState.audioTracks, playerState.audioTracks.firstOrNull { it.selected }, { it.name }, { Players.selectTrack(it, androidx.media3.common.C.TRACK_TYPE_AUDIO); showAudio = false }) { showAudio = false }
    SubtitleDialog(
        show = showText,
        tracks = playerState.textTracks,
        textDisabled = playerState.textDisabled,
        onSelect = { Players.selectTrack(it, androidx.media3.common.C.TRACK_TYPE_TEXT); showText = false },
        onAddSubtitle = { showText = false; onAddSubtitle() },
        onSettings = { showText = false; onOpenSubtitleSettings() },
        onDismiss = { showText = false },
    )
    } // Column 根结束
}

/**
 * 竖屏详情信息流本体（标题/线路/画质/选集/简介）。
 * 抽成独立组件是为了让它可以被「错误态下拉刷新」的 PullToRefresh 包裹，
 * 又不必在两处分支里重复这段内容。
 *
 * 水平边距下放到各 Section 内部（首页顶部分类 Tab 同款结构性通栏）：
 * 线路/选集横向列表需天然占满全宽，靠自身 contentPadding 对齐基准线并支持贴边横滑。
 */
@Composable
private fun DetailInfoColumn(
    modifier: Modifier,
    current: Video?,
    lineFlag: String,
    qualities: List<com.ican.tvplay.data.remote.PlayQuality>,
    qualityPosition: Int,
    onQualityChange: (Int) -> Unit,
    lineEpisodes: List<Episode>,
    episodeIndex: Int,
    onEpisodeSelect: (Int) -> Unit,
    onLineChange: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            // 顶部 3dp：标题行被 48dp 收藏钮撑高，文字居中已含约 8dp 留白，视觉间距约 16dp
            .padding(top = Spacing.videoTitleRowTopGap, bottom = Spacing.titleContentGap),
    ) {
        val v = current ?: return@Column

        // 标题+收藏 / 更新备注 / 站源 / 年份·地区·类型（标题↔信息行的次间距在组件内部）
        VideoMetaSection(
            v = v,
            lineFlag = lineFlag,
            modifier = Modifier.padding(horizontal = pageHorizontalPadding()),
            metaTopPadding = Spacing.videoTitleToMetaGap,
        )

        // 类型信息块 → 线路：统一走板块主间距（选项→下一个标题 16dp）
        Spacer(modifier = Modifier.height(Spacing.sectionSpacing))

        // 线路（标题↔chips 的次间距在组件内部）
        LineSection(
            v = v,
            lineFlag = lineFlag,
            onLineChange = onLineChange,
            horizontalPadding = pageHorizontalPadding(),
        )

        // 画质：与线路同属一组选项行（fongmi activity_video.xml 里 flag 与 quality 两行间距 16dp，
        // 即「选项→下一个标题」的同一档间距）；只有 url.values.size > 1 时才显示
        if (qualities.size > 1) {
            Spacer(modifier = Modifier.height(Spacing.sectionSpacing))
            QualityRow(
                qualities = qualities,
                qualityPosition = qualityPosition,
                onQualityChange = onQualityChange,
                horizontalPadding = pageHorizontalPadding(),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sectionSpacing))

        // 选集（倒序/展开，横滑↔网格；标题↔按钮的次间距在组件内部）
        EpisodeSection(
            lineEpisodes = lineEpisodes,
            episodeIndex = episodeIndex,
            onEpisodeSelect = onEpisodeSelect,
            horizontalPadding = pageHorizontalPadding(),
        )

        Spacer(modifier = Modifier.height(Spacing.sectionSpacing))

        // 简介（标题↔正文的次间距在组件内部）
        DescriptionSection(
            v = v,
            modifier = Modifier.padding(horizontal = pageHorizontalPadding()),
        )

        Spacer(modifier = Modifier.height(60.dp))
    }
}

// ========== 横屏（平板）布局：对齐 fongmi activity_detail_land ==========

/** 横版播放器占屏宽比例（fongmi 38:62） */
private const val LAND_PLAYER_WIDTH_FRACTION = 0.38f

/** 横屏小窗↔全屏放大/缩回动画时长（对齐 fongmi ChangeBounds 150ms） */
private const val LAND_ZOOM_DURATION_MS = 150

/** 竖屏预览小窗高度随片源比例变化时的过渡时长（对齐项目内控制层/侧栏的 200ms，不再用 fongmi 的 300ms） */
private const val PREVIEW_HEIGHT_ANIM_MS = 200

/**
 * 状态栏高度（Dp）。直接读窗口度量里的 statusBar inset：组合期同步可用，
 * 与系统栏可见性无关（全屏隐藏状态栏也不影响），值本该恒定。
 * 所以这里**不挂 density 作 key**：转屏那一瞬间窗口度量可能先报到 131px、再落到 121px，
 * 一旦按 density 变化重算，小窗顶部就会多滑 10px（约 4dp）再回弹，肉眼是「又蹭了一下」。
 * remember 不带 key = 整个播放页生命周期只取一次值，与 fongmi 的静态占位 View 做法一致。
 */
@Composable
private fun rememberStatusBarTopDp(): Dp {
    val density = LocalDensity.current
    val context = LocalContext.current
    return remember {
        val px = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.getSystemService(android.view.WindowManager::class.java)
                    .currentWindowMetrics.windowInsets
                    .getInsets(android.view.WindowInsets.Type.statusBars()).top
            }.getOrDefault(0)
        } else {
            // API 29 及以下没有 WindowMetrics.windowInsets，退回资源里的状态栏高度
            val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        }
        with(density) { px.toDp() }
    }
}

/** 外挂字幕文件选择器的 MIME 过滤：覆盖 srt/ass/ssa/vtt/ttml，以及文件管理器把字幕标成 octet-stream 的情况 */
private val SUBTITLE_MIME_TYPES = arrayOf(
    "text/*",
    "application/octet-stream",
    "application/x-subrip",
    "text/x-ssa",
    "text/x-ass",
    "application/x-ass",
    "text/vtt",
    "application/ttml+xml",
)

/**
 * 横版布局（fongmi activity_detail_land + ChangeBounds 联动）：
 * 全部元素以动画中的播放器边界为锚绝对定位——信息列锚视频右缘（放大时被推出屏幕右侧），
 * 线路/选集/简介锚视频下缘（被推出屏幕下缘），缩放全程跟随，无渐隐渐显切换。
 * 组件始终渲染，超出屏幕的部分由根容器 clipToBounds 裁剪。
 */
@Composable
private fun LandscapePlayLayout(
    current: Video?,
    episodeIndex: Int,
    lineEpisodes: List<Episode>,
    lineFlag: String,
    playerState: Players.PlayerState,
    controlsVisible: Boolean,
    locked: Boolean,
    // 动画中的播放器边界（小窗静止值 = 38% 宽 16:9 + 状态栏顶部偏移 + 左缘对齐页面左基线）
    playerTop: State<Dp>,
    playerLeft: State<Dp>,
    playerWidth: State<Dp>,
    playerHeight: State<Dp>,
    playerCorner: State<Dp>,
    smallPlayerWidth: Dp,
    screenWidth: Dp,
    screenHeight: Dp,
    pageHorizontal: Dp,
    onToggleControls: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onBack: () -> Unit,
    onLineChange: (String) -> Unit,
    onEpisodeSelect: (Int) -> Unit,
    /** 可选画质（fongmi Url.values）：>1 条才显示「画质」行 */
    qualities: List<com.ican.tvplay.data.remote.PlayQuality>,
    qualityPosition: Int,
    onQualityChange: (Int) -> Unit,
    onSeekDragChange: (Boolean) -> Unit,
    /** 非空时代表当前源是磁力/BT 地址（播不了），预览区画提示文案 */
    blockedMessage: String? = null,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 播放器覆盖层：与底层常驻 PlayerView 同位同尺寸（尺寸由根 AndroidView 动画值驱动）
        // 圆角与视频面同步（同一个 animCorner）：若只裁视频不裁控制层，
        // 底部渐变条的方角会露在圆角缺口里形成浅灰色直角块
        PreviewPlayerArea(
            modifier = Modifier
                .offset { IntOffset(playerLeft.value.roundToPx(), playerTop.value.roundToPx()) }
                .animatedSize(playerWidth, playerHeight)
                .graphicsLayer {
                    shape = SquircleShape(playerCorner.value)
                    clip = true
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                // 仅横屏小窗、深色模式加描边（竖屏小窗贴屏幕顶部，不加）：
                // 圆角与视频面共用 playerCorner 动画值（16dp squircle 连续曲率），
                // 0.5dp 宽 + outline 60% 不透明度，只叠加绘制，不改尺寸/位置/间距
                .then(
                    if (LocalDarkTheme.current) {
                        Modifier.border(
                            width = 0.5.dp,
                            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.6f),
                            shape = SquircleShape(playerCorner.value),
                        )
                    } else {
                        Modifier
                    },
                ),
            playerState = playerState,
            // 进详情页即转圈（首次画面就绪前，无网速）；ready 后只随缓冲转
            showLoading = !playerState.ready && playerState.error == null,
            blockedMessage = blockedMessage,
            controlsVisible = controlsVisible,
            locked = locked,
            onToggleControls = onToggleControls,
            onBack = onBack,
            onTogglePlay = onTogglePlay,
            onPrev = onPrev,
            onNext = onNext,
            onEnterFullscreen = onEnterFullscreen,
            onSeekDragChange = onSeekDragChange,
        )

        // 右侧信息列：锚在视频右缘，宽度固定（小窗时的剩余宽度），随放大向右滑出屏幕
        Column(
            modifier = Modifier
                .offset { IntOffset((playerLeft.value + playerWidth.value).roundToPx(), playerTop.value.roundToPx()) }
                // 宽度 = 视频右侧剩余宽度（动画值在布局阶段读取，等价 width()）
                .layout { measurable, constraints ->
                    val w = (screenWidth - smallPlayerWidth - playerLeft.value).roundToPx()
                    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                    layout(w, placeable.height) { placeable.place(0, 0) }
                }
                // 浅色模式下全屏缩回小窗时，根 Box 残留黑底会透出到信息列，
                // 此处显式覆盖主题背景，确保缩回后恢复浅色
                .background(MiuixTheme.colorScheme.surface)
                // 顶部不再额外留 12dp：标题文字顶部与小窗顶部对齐（横屏右侧信息列与小窗同锚 playerTop）
                .padding(
                    start = com.ican.tvplay.ui.theme.Spacing.sectionSpacing,
                    end = pageHorizontal,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (current != null) {
                // 横屏：标题顶部对齐小窗顶（信息列 top=0），仅补回标题→信息行的呼吸感
                VideoMetaSection(
                    v = current,
                    lineFlag = lineFlag,
                    itemSpacing = 8.dp,
                    metaTopPadding = com.ican.tvplay.ui.theme.Spacing.videoTitleToMetaGap,
                )
            }
        }

        // 下方区块：锚在视频下缘，高度为视频下方的剩余空间（放大时随视频增高收缩到 0）
        Column(
            modifier = Modifier
                .offset { IntOffset(0, (playerTop.value + playerHeight.value).roundToPx()) }
                .fillMaxWidth()
                // 高度 = 视频下方剩余空间（动画值在布局阶段读取，等价 height()）
                .layout { measurable, constraints ->
                    val h = (screenHeight - playerTop.value - playerHeight.value).roundToPx()
                    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                    layout(placeable.width, h) { placeable.place(0, 0) }
                }
                // 浅色模式下全屏缩回小窗时，根 Box 残留黑底会透出到下方区块，
                // 此处显式覆盖主题背景，确保缩回后恢复浅色
                .background(MiuixTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                // 水平边距下放到各 Section 内部（同竖屏信息流的结构性通栏），此处只保留顶部间距
                .padding(top = com.ican.tvplay.ui.theme.Spacing.videoTitleRowTopGap),
            // 板块主间距与竖屏信息流统一为 sectionSpacing
            verticalArrangement = Arrangement.spacedBy(com.ican.tvplay.ui.theme.Spacing.sectionSpacing),
        ) {
            if (current != null) {
                // 线路 + 画质：两行同属一组选项，行间距同「选项→下一个标题」的 16dp
                Column(verticalArrangement = Arrangement.spacedBy(com.ican.tvplay.ui.theme.Spacing.sectionSpacing)) {
                    LineSection(v = current, lineFlag = lineFlag, onLineChange = onLineChange, horizontalPadding = pageHorizontal)
                    if (qualities.size > 1) {
                        QualityRow(
                            qualities = qualities,
                            qualityPosition = qualityPosition,
                            onQualityChange = onQualityChange,
                            horizontalPadding = pageHorizontal,
                        )
                    }
                }
                EpisodeSection(
                    lineEpisodes = lineEpisodes,
                    episodeIndex = episodeIndex,
                    onEpisodeSelect = onEpisodeSelect,
                    horizontalPadding = pageHorizontal,
                )
                DescriptionSection(v = current, modifier = Modifier.padding(horizontal = pageHorizontal))

                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

/** 布局阶段读取动画 Dp 状态并设为精确宽高（视觉等价 width()/height()，动画期间不触发逐帧重组） */
private fun Modifier.animatedSize(width: State<Dp>, height: State<Dp>): Modifier =
    layout { measurable, _ ->
        val w = width.value.roundToPx()
        val h = height.value.roundToPx()
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(w, h) { placeable.place(0, 0) }
    }

/**
 * 只驱动高度、宽度铺满父级的布局修饰符（布局阶段读 State，动画期间不触发重组）。
 * 用于竖屏预览区占位：高度随小窗↔全屏动画连续变化，宽度始终取父级可用宽。
 */
private fun Modifier.animatedHeight(height: State<Dp>): Modifier =
    layout { measurable, constraints ->
        val h = height.value.roundToPx()
        val w = constraints.maxWidth
        val placeable = measurable.measure(
            if (w == Constraints.Infinity) {
                Constraints(minHeight = h, maxHeight = h)
            } else {
                Constraints(minWidth = w, maxWidth = w, minHeight = h, maxHeight = h)
            },
        )
        layout(placeable.width, h) { placeable.place(0, 0) }
    }

// ========== 非全屏播放器区与信息区块（竖屏 / 横屏共用） ==========

/**
 * 非全屏播放器区域（竖屏通栏 16:9 / 横屏左侧 38% 共用）：
 * PlayerView 常驻根 Box 最底层、从不销毁重建，这里只承载小窗控制层/加载/错误覆盖层，
 * 区域尺寸完全由 [modifier] 决定，保证覆盖层与底层 PlayerView 像素对齐。
 */
@Composable
private fun PreviewPlayerArea(
    modifier: Modifier,
    playerState: Players.PlayerState,
    /** 详情未就绪时强制显示转圈（fongmi 进页面即 showProgress），就绪后跟播放器 buffering 状态 */
    showLoading: Boolean = false,
    /** 非空时代表当前源是磁力/BT 地址（播不了），在预览区画提示文案 */
    blockedMessage: String? = null,
    controlsVisible: Boolean,
    locked: Boolean,
    onToggleControls: () -> Unit,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onSeekDragChange: (Boolean) -> Unit,
) {
    Box(
        modifier = modifier.pointerInput(locked) {
            // 与全屏手势层同款判定：单击不等待双击超时、立即响应；
            // 单击 = 显示/隐藏控制层（原行为不变），双击 = 进入全屏（替代原右下角全屏按钮）；
            // 移动过或点在控制层按钮等已消费事件的手势全程让位
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downX = down.position.x
                val downY = down.position.y
                var moved = false
                var consumedByChild = false
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.isConsumed }) consumedByChild = true
                    if (event.changes.all { !it.pressed }) break
                    val c = event.changes.firstOrNull() ?: continue
                    if (hypot(c.position.x - downX, c.position.y - downY) >
                        viewConfiguration.touchSlop
                    ) {
                        moved = true
                    }
                }
                if (moved || consumedByChild) return@awaitEachGesture
                val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                    awaitFirstDown(requireUnconsumed = false)
                }
                if (second != null) {
                    // 双击：等第二次抬起后进入全屏（锁定时不响应）
                    while (true) {
                        val e2 = awaitPointerEvent()
                        if (e2.changes.all { !it.pressed }) break
                    }
                    if (!locked) onEnterFullscreen()
                } else {
                    if (playerState.error == null) onToggleControls()
                }
            }
        },
    ) {
        // 加载转圈（fongmi: view_progress；进页面即显示，STATE_READY 后随 buffering 显隐）
        // 放在控制层下面：缓冲时转圈在按钮后方，按钮仍然可点击（与 fongmi 同款 z-order）
        // 转圈固定在屏幕中心，不随网速文字出现而位移
        if (showLoading || playerState.buffering || playerState.switching) {
            ArcStretchSpinner(
                modifier = Modifier.align(Alignment.Center),
                size = 36.dp,
                strokeWidth = 3.dp,
                color = Color.White,
            )
            // 网速文字只属于「缓冲转圈」：首次进入的加载转圈（showLoading）不显示网速，
            // 缓冲/换集期间每秒采样刷新（TrafficStats），缓冲结束随转圈一起消失
            if (playerState.buffering || playerState.switching) {
                var speedText by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    com.ican.tvplay.player.core.Traffic.reset()
                    while (true) {
                        speedText = com.ican.tvplay.player.core.Traffic.setSpeed()
                        kotlinx.coroutines.delay(1000)
                    }
                }
                if (speedText.isNotEmpty()) {
                    Text(
                        text = speedText,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 40.dp),
                    )
                }
            }
        }

        // 磁力/BT 源提示：地址只有 magnet:/ed2k:/thunder: 时本机播不了，
        // 用黑底提示盖住预览区（放在控制层下面，返回/选集按钮仍可点）
        blockedMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = msg.substringBefore('\n'),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    msg.substringAfter('\n', "").takeIf { it.isNotEmpty() }?.let { sub ->
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }

        // 预览小窗控制层：单击视频区显示/隐藏（fongmi 同款交互）
        // 返回键随控制层显隐（fongmi 同款：控制层隐藏时返回键一起消失），48dp 纯图标无背景
        // 显隐加淡入淡出（200ms，对齐 miuix），不改尺寸/位置/间距
        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(tween(CONTROL_FADE_DURATION_MS)),
            exit = fadeOut(tween(CONTROL_FADE_DURATION_MS)),
        ) {
            // 控制层不再自带 clickable：空白处的单击/双击统一由外层手势层处理，
            // 只有按钮（已消费事件）才让位，保证控制层显示时双击同样能进全屏
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶栏：返回（左） + 投屏（右），fongmi 同款 48dp 纯图标，无圆形底色
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp),
                ) {
                    PlainTopBtn(
                        icon = AppIcons.Back,
                        desc = "返回",
                        pressEffect = true,
                        pressColor = Color.White,
                        onClick = { onBack() },
                    )
                }

                // 中部：上一集 / 播放暂停 / 下一集（fongmi: play 与两侧各 40dp 间距）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    MiniLargeBtn(AppIcons.SkipPrev, "上集", onClick = { onPrev() })
                    MiniLargeBtn(
                        if (playerState.playing) AppIcons.Pause else AppIcons.Play,
                        if (playerState.playing) "暂停" else "播放",
                        onClick = { onTogglePlay() },
                        isPrimary = true,
                    )
                    MiniLargeBtn(AppIcons.SkipNext, "下集", onClick = { onNext() })
                }

                // 底部：进度条（fongmi: PlayerSeekView 左右 16dp padding，容器 paddingBottom 8dp）
                // 不挂底部渐变阴影：进度条行直接叠加在视频画面上
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                ) {
                    PlayerProgressRow(onDragFractionChange = { onSeekDragChange(it != null) })
                }
            }
        }
    }
}

/**
 * 影片基础信息区块（竖屏信息流 / 横屏右侧信息列共用）：
 * 标题+收藏 / 更新备注 / 站源 / 年份·地区·类型
 */
@Composable
private fun VideoMetaSection(
    v: Video,
    lineFlag: String,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 8.dp,
    // 标题行 → 下方信息行的间距：竖屏补 8dp（收藏钮缩 32dp 后需恢复呼吸感）；
    // 横屏 0（横屏标题顶部需与小窗顶对齐，仅标题下方要呼吸感，由 metaTopPadding 提供）
    metaTopPadding: Dp = 0.dp,
) {
    Column(modifier = modifier) {
        // 标题 + 收藏按钮（收藏入口在标题右侧，圆钮尺寸/样式不变）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = v.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            FavoriteButton(v = v)
        }

        // 标题 ↔ 自身信息内容：收藏钮缩为 32dp 后行内居中留白消失，按方向补回
        if (metaTopPadding > 0.dp) {
            Spacer(modifier = Modifier.height(metaTopPadding))
        }

        // 更新备注/站源/年份/地区/类型：信息行之间保持行内密度 itemSpacing
        Column(verticalArrangement = Arrangement.spacedBy(itemSpacing)) {
            // 更新备注（fongmi: remark）
            val eps = v.playSources.firstOrNull()?.episodes ?: v.episodes
            val remark = when {
                eps.isEmpty() -> ""
                eps.last().title.contains("完结") -> "已完结"
                else -> "更新至第${eps.size}集"
            }
            if (remark.isNotBlank()) {
                Text(
                    text = remark,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            // 站源（fongmi: site）
            // 兜底顺序：detail 自带的站源名 → 本次播放缓存的站点名 → 线路名。
            // 爱刷这类详情返回不带站点信息的源，只能靠后两级，否则会显示成线路名「刷刷刷」
            val sourceDisplay = v.sourceName
                .ifBlank {
                    com.ican.tvplay.ui.components.siteShortName(
                        Players.cachedVideo?.site?.name.orEmpty(),
                    )
                }
                .ifBlank { lineFlag.substringBefore("|") }
            Text(
                text = "站源：$sourceDisplay",
                style = MaterialTheme.typography.bodyMedium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            // 年份/地区/类型各占一行（fongmi: other）
            if (v.year > 0) {
                Text(
                    text = "年份：${v.year}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (v.region.isNotBlank()) {
                Text(
                    text = "地区：${v.region}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (v.categoryName.isNotBlank()) {
                Text(
                    text = "类型：${v.categoryName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 标题行收藏按钮（竖屏/横屏共用）：裸图标无圆形底色，32dp 点击热区，
 * 已收藏心形粉色，点击切换收藏并携带站源信息。
 * 动画（移植自 React Like 组件）：收藏时心形先消失再弹簧回弹（过冲放大），
 * 同时迸射绽放粒子；取消收藏时心形轻微收缩再恢复。 */
/**
 * 收藏态缓存（videoId → 是否已收藏）。
 * 详情信息流会因「错误态/刷新态切换」而整块重建（PullToRefresh 分支变化），
 * FavoriteButton 的 remember 状态随之清零；用这份缓存兜住重建后的首帧，
 * 避免切集/切源时收藏钮先画空心、查库回来才变红的闪动。
 */
private val favoriteStateCache = mutableMapOf<String, Boolean>()

@Composable
private fun FavoriteButton(v: Video) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var isFav by remember { mutableStateOf(favoriteStateCache[v.id] ?: false) }
    // 每次点击收藏自增，作为粒子动画重播的 key；初始 -1 表示页面加载时不播动画
    var beat by remember { mutableIntStateOf(-1) }

    LaunchedEffect(v.id) {
        val fav = container.favoriteDao.isFavorite(v.id)
        favoriteStateCache[v.id] = fav
        isFav = fav
    }

    // 心形弹出动画：收藏时 snap 到 0 再弹簧回弹（过冲），取消收藏时轻微收缩再恢复
    val heartScale = remember { Animatable(1f) }
    val scale = heartScale.value

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // 32dp（原 48dp circleButton）：不再把标题行撑高，横屏下标题字顶与小窗顶对齐；
            // 图标仍 26dp 不变，仅点击热区变小
            .size(32.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    val newFav = !isFav
                    isFav = newFav
                    favoriteStateCache[v.id] = newFav
                    if (newFav) beat++
                    // 心形动画仅在点击时触发
                    scope.launch {
                        if (newFav) {
                            heartScale.snapTo(0f)
                            heartScale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow,
                                ),
                            )
                        } else {
                            heartScale.animateTo(0.82f, tween(durationMillis = 70))
                            heartScale.animateTo(1f, tween(durationMillis = 120))
                        }
                    }
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        if (newFav) {
                            // 记录视频所在站源：点击收藏时按此站源拉详情并播放
                            val cached = Players.cachedVideo
                            val favSiteKey = cached?.siteKey ?: ""
                            val favSiteName = v.sourceName.ifBlank {
                                com.ican.tvplay.ui.components.siteShortName(
                                    cached?.site?.name.orEmpty(),
                                    favSiteKey,
                                )
                            }
                            // 追更基线：以收藏时的总集数为已看基线，避免「刚收藏就提示更新」
                            // 与播放页「更新至第 x 集」口径一致：取首条线路的集数
                            val episodeCount = v.playSources.firstOrNull()?.episodes?.size
                                ?: v.episodes.size
                            val now = System.currentTimeMillis()
                            container.favoriteDao.upsert(
                                FavoriteEntity(
                                    videoId = v.id,
                                    title = v.title,
                                    cover = v.cover,
                                    categoryName = v.categoryName,
                                    addedAt = now,
                                    sourceKey = favSiteKey,
                                    sourceName = favSiteName,
                                    episodeCount = episodeCount,
                                    ackEpisodeCount = episodeCount,
                                    lastUpdatedAt = 0L,
                                    lastCheckedAt = now,
                                ),
                            )
                        } else {
                            container.favoriteDao.delete(v.id)
                        }
                    }
                },
            ),
    ) {
        // 绽放粒子层（仅点击收藏后挂载，beat=-1 时页面加载不播）
        if (isFav && beat >= 0) {
            FavoriteBloom(beat = beat)
        }
        Icon(
            imageVector = if (isFav) AppIcons.Favorite else AppIcons.FavoriteBorder,
            contentDescription = if (isFav) "取消收藏" else "收藏",
            tint = if (isFav) BrandPink else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            // 26dp：心形图形约 20dp 视觉高，与标题（24sp）字面齐平；空心/实心共用同一尺寸
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/** 绽放粒子：7 对彩色圆点沿辐条方向向外迸射，末段渐隐。
 * 每次 [beat] 变化重播一次。移植自 React Like 组件 Bloom 效果（已移除绽放环）。 */
@Composable
private fun FavoriteBloom(beat: Int) {
    val density = LocalDensity.current
    // 7 种固定颜色，与 React 版本一致
    val colors = remember {
        listOf(
            Color(0xFFf48ea7),
            Color(0xFFcc8ef5),
            Color(0xFF8ce8c3),
            Color(0xFF91d2fa),
            Color(0xFFf5a524),
            Color(0xFFe5484d),
            Color(0xFF9fc7fa),
        )
    }

    // 7 条辐条，每条 2 粒（颜色 k 与 k+3），沿辐条方向向外飞
    val spokes = remember {
        (0 until 7).flatMap { k ->
            val angle = k * (2.0 * Math.PI / 7.0) - Math.PI / 2.0
            listOf(
                BloomDotSpec(angle, colors[k % colors.size], 0L),
                BloomDotSpec(angle, colors[(k + 3) % colors.size], 35L),
            )
        }
    }

    Box(
        modifier = Modifier.requiredSize(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 彩点
        spokes.forEach { spec ->
            BloomDot(spec, beat, density)
        }
    }
}

private data class BloomDotSpec(
    val angle: Double, // 弧度，辐条方向
    val color: Color,
    val delay: Long,   // ms，发射延迟
)

@Composable
private fun BloomDot(spec: BloomDotSpec, beat: Int, density: Density) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(beat) {
        progress = 0f
        delay(spec.delay)
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = LinearEasing),
        ) { value, _ -> progress = value }
    }

    val t = progress
    // 分段飞行：前段快速抵达爱心边缘（对应爱心过冲峰值时刻），后段缓慢向外扩散
    // 爱心峰值约在 360ms（StiffnessLow + MediumBouncy），粒子总时长 800ms
    val peakRatio = 0.45f   // t=0.45 时粒子抵达爱心边缘
    val edgeDist = 15f      // dp，爱心峰值时的边缘半径（26dp 心形 × 过冲 ~1.15）
    val maxDist = 26f       // dp，最终扩散距离
    val dist = if (t < peakRatio) {
        (t / peakRatio) * edgeDist
    } else {
        edgeDist + ((t - peakRatio) / (1f - peakRatio)) * (maxDist - edgeDist)
    }
    val x = (Math.cos(spec.angle) * dist).toFloat()
    val y = (Math.sin(spec.angle) * dist).toFloat()
    // 末段渐隐
    val alpha = (1f - ((t - 0.5f) / 0.5f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
    val dotScale = 1f - t * 0.3f

    with(density) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .graphicsLayer {
                    translationX = x.dp.toPx()
                    translationY = y.dp.toPx()
                    this.alpha = alpha
                    scaleX = dotScale
                    scaleY = dotScale
                }
                .background(spec.color, CircleShape),
        )
    }
}

/** 线路区块（fongmi: detail_flag）：横向滚动 chips */
@Composable
private fun LineSection(
    v: Video,
    lineFlag: String,
    onLineChange: (String) -> Unit,
    horizontalPadding: Dp,
) {
    val darkMode = LocalDarkTheme.current
    // 当前选中线路在列表中的索引
    val selectedIdx = v.playSources.indexOfFirst { it.flag == lineFlag }.coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIdx,
    )
    // 用户点击平滑滚动时，让落定的 chip 与边缘留出半个列表间距（仅 animate 分支加，自动定位不加）
    val paddingHalfPx = with(LocalDensity.current) { (Spacing.dialogItemGap / 2).toPx() }
    // userScrolled：仅当本次选中项是用户在列表里点出来的（且点的不是当前项）才置 true；
    // 进页 / 异步到位 / 倒序切换等自动定位一律零动画；动画与否由这一次是否用户点击决定
    var userScrolled by remember { mutableStateOf(false) }
    // 切线路时：进页 / 自动定位无动画贴左；仅用户点击才按遮挡量平滑滚动并留半间距
    LaunchedEffect(lineFlag) {
        val animate = userScrolled
        userScrolled = false
        if (v.playSources.isEmpty() || selectedIdx !in v.playSources.indices) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .filter { it > selectedIdx }
            .first()
        if (!animate) {
            // 进页 / 自动定位：无动画贴左，选中项落在列表第一位
            listState.scrollToItem(selectedIdx)
        } else {
            val info = listState.layoutInfo
            val viewportStart = info.viewportStartOffset
            val viewportEnd = info.viewportEndOffset
            val item = info.visibleItemsInfo.firstOrNull { it.index == selectedIdx }
            when {
                item == null -> listState.animateScrollToItem(selectedIdx)
                item.offset < viewportStart ->
                    listState.animateScrollBy((item.offset - viewportStart).toFloat() - paddingHalfPx)
                item.offset + item.size > viewportEnd ->
                    listState.animateScrollBy((item.offset + item.size - viewportEnd).toFloat() + paddingHalfPx)
            }
        }
    }
    Column {
        Text(
            text = "线路",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = horizontalPadding),
        )
        Spacer(modifier = Modifier.height(Spacing.videoTitleContentGap))
    // 通栏横滑（首页顶部分类 Tab 同款）：列表天然占满全宽，contentPadding 保证
    // 静止时首尾 chips 对齐全局基准线，滑动时条目可完整滑到屏幕最边缘
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.dialogItemGap),
    ) {
        items(v.playSources, key = { it.flag }, contentType = { "line" }) { source ->
            val selected = source.flag == lineFlag
            val shape = SquircleShape(com.ican.tvplay.ui.theme.Spacing.corner)
            val primary = MiuixTheme.colorScheme.primary
            val tintedFill = remember(primary, darkMode) { chipTintedFill(primary, darkMode) }
            val tintedText = remember(primary, darkMode) { chipTintedText(primary, darkMode) }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    // 几何与选集 EpisodeChip 一致：40dp 最小宽、四边 dialogItemPadding，高度随内容撑开
                    .defaultMinSize(minWidth = 40.dp)
                    .tvCardEffect(
                        onClick = { if (source.flag != lineFlag) userScrolled = true; onLineChange(source.flag) },
                        shape = shape, focusedScale = 1.05f, glow = false,
                        // 线路列表自带选中态：整列表统一去掉按压/点击蒙层，选中色即反馈
                        pressEffect = false,
                    )
                    .background(
                        // 选中：primary 派生 tinted 底；未选中 secondaryVariant
                        if (selected) tintedFill
                        else MiuixTheme.colorScheme.secondaryVariant,
                        shape,
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = source.flag,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) tintedText
                    else MiuixTheme.colorScheme.onSecondaryVariant,
                    fontWeight = if (selected) FontWeight.SemiBold else null,
                    maxLines = 1,
                )
            }
        }
    }
    }
}

/**
 * 画质行（fongmi QualityAdapter + activity_video.xml 的 quality 行）：
 * 结构与 LineSection 一致（标题 + chip 行），显示条件是 url.values.size > 1（由调用方判断）。
 * 样式与线路/选集完全一致，选中态用主题 primary。
 */
@Composable
private fun QualityRow(
    qualities: List<com.ican.tvplay.data.remote.PlayQuality>,
    qualityPosition: Int,
    onQualityChange: (Int) -> Unit,
    horizontalPadding: Dp,
) {
    val darkMode = LocalDarkTheme.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = qualityPosition.coerceAtLeast(0))
    // 用户点击平滑滚动时，让落定的 chip 与边缘留出半个列表间距（仅 animate 分支加，自动定位不加）
    val paddingHalfPx = with(LocalDensity.current) { (Spacing.dialogItemGap / 2).toPx() }
    // userScrolled：仅用户点击切换画质才置 true；进页 / 异步到位自动定位零动画（口径同 LineSection）
    var userScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(qualityPosition) {
        val animate = userScrolled
        userScrolled = false
        if (qualityPosition !in qualities.indices) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .filter { it > qualityPosition }
            .first()
        if (!animate) {
            // 进页 / 自动定位：无动画贴左，选中项落在列表第一位
            listState.scrollToItem(qualityPosition)
        } else {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == qualityPosition }
            when {
                item == null -> listState.animateScrollToItem(qualityPosition)
                item.offset < info.viewportStartOffset ->
                    listState.animateScrollBy((item.offset - info.viewportStartOffset).toFloat() - paddingHalfPx)
                item.offset + item.size > info.viewportEndOffset ->
                    listState.animateScrollBy((item.offset + item.size - info.viewportEndOffset).toFloat() + paddingHalfPx)
            }
        }
    }
    Column {
        Text(
            text = "画质",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = horizontalPadding),
        )
        Spacer(modifier = Modifier.height(Spacing.videoTitleContentGap))
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.dialogItemGap),
    ) {
        itemsIndexed(qualities, key = { idx, q -> "$idx|${q.name}|${q.url.take(40)}" }, contentType = { _, _ -> "quality" }) { idx, q ->
            val selected = idx == qualityPosition
            val shape = SquircleShape(com.ican.tvplay.ui.theme.Spacing.corner)
            val primary = MiuixTheme.colorScheme.primary
            val tintedFill = remember(primary, darkMode) { chipTintedFill(primary, darkMode) }
            val tintedText = remember(primary, darkMode) { chipTintedText(primary, darkMode) }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minWidth = 40.dp)
                    .tvCardEffect(
                        onClick = { if (idx != qualityPosition) userScrolled = true; onQualityChange(idx) },
                        shape = shape, focusedScale = 1.05f, glow = false,
                        pressEffect = false,
                    )
                    .background(
                        if (selected) tintedFill
                        else MiuixTheme.colorScheme.secondaryVariant,
                        shape,
                    )
                    .padding(12.dp),
            ) {
                Text(
                    // 名称为空时兜底成位置（fongmi Url.n(position)，数组形态才有名称）
                    text = q.name.ifBlank { "画质${idx + 1}" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) tintedText
                    else MiuixTheme.colorScheme.onSecondaryVariant,
                    fontWeight = if (selected) FontWeight.SemiBold else null,
                    maxLines = 1,
                )
            }
        }
    }
    }
}

/** 选集区块（fongmi: detail_episode）：标题+倒序/正序+展开；默认横向滚动，点展开弹出分页网格弹窗 */
@Composable
private fun EpisodeSection(
    lineEpisodes: List<Episode>,
    episodeIndex: Int,
    onEpisodeSelect: (Int) -> Unit,
    horizontalPadding: Dp,
) {
    var showEpisodeGrid by remember { mutableStateOf(false) }
    var reversedEp by remember { mutableStateOf(false) }
    // userScrolled：仅用户在列表 / 网格弹窗里点出别的集才置 true；
    // 进页、异步到位、倒序切换等自动定位一律零动画；声明在函数体顶部，供 Column 内 effect/chip 与块外弹窗 onSelect 共用
    var userScrolled by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "选集",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Text(
                text = if (reversedEp) "倒序" else "正序",
                style = MaterialTheme.typography.bodySmall,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { reversedEp = !reversedEp },
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "展开",
                style = MaterialTheme.typography.bodySmall,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showEpisodeGrid = true },
            )
        }
        Spacer(modifier = Modifier.height(Spacing.videoTitleContentGap))

        val displayEps = if (reversedEp) lineEpisodes.asReversed() else lineEpisodes
        // 选中集在显示序列中的位置（倒序时需换算）
        val selectedDispIdx = if (reversedEp) lineEpisodes.size - 1 - episodeIndex else episodeIndex
        // 进入页面定位：优先用初始滚动状态让列表首次布局直接落在当前集（贴左可见）——
        // 布局前滚动即已就位，无动画、无瞬移闪现；异步补拉场景（进入时无数据）由下方
        // 加载兜底 effect 在剧集加载完成后瞬移定位一次。
        val initialDispIdx = if (reversedEp) lineEpisodes.size - 1 - episodeIndex else episodeIndex
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = if (lineEpisodes.isNotEmpty() && initialDispIdx in 0 until lineEpisodes.size) {
                initialDispIdx
            } else 0,
        )
        // 用户点击平滑滚动时，让落定的 chip 与边缘留出半个列表间距（仅 animate 分支加，自动定位不加）
        val paddingHalfPx = with(LocalDensity.current) { (Spacing.dialogItemGap / 2).toPx() }
        LaunchedEffect(selectedDispIdx, lineEpisodes.size) {
            val animate = userScrolled
            userScrolled = false
            if (lineEpisodes.isEmpty() || selectedDispIdx !in 0 until lineEpisodes.size) return@LaunchedEffect
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .filter { it > selectedDispIdx }
                .first()
            if (!animate) {
                // 进页 / 自动定位：无动画贴左，选中项落在列表第一位
                listState.scrollToItem(selectedDispIdx)
            } else {
                val info = listState.layoutInfo
                val viewportStart = info.viewportStartOffset
                val viewportEnd = info.viewportEndOffset
                val item = info.visibleItemsInfo.firstOrNull { it.index == selectedDispIdx }
                when {
                    item == null -> listState.animateScrollToItem(selectedDispIdx)
                    item.offset < viewportStart ->
                        listState.animateScrollBy((item.offset - viewportStart).toFloat() - paddingHalfPx)
                    item.offset + item.size > viewportEnd ->
                        listState.animateScrollBy((item.offset + item.size - viewportEnd).toFloat() + paddingHalfPx)
                }
            }
        }
        // 默认横向滚动（fongmi: adapter_episode_hori）；通栏横滑同 LineSection：列表天然占满
        // 全宽，contentPadding 保证静止时首尾 chip 对齐全局基准线，滑动可贴到屏幕最边缘
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.dialogItemGap),
        ) {
            itemsIndexed(displayEps, key = { _, ep -> ep.index }, contentType = { _, _ -> "episode" }) { dispIdx, ep ->
                val idx = if (reversedEp) lineEpisodes.size - 1 - dispIdx else dispIdx
                EpisodeChip(
                    title = ep.title,
                    selected = idx == episodeIndex,
                    onClick = { if (idx != episodeIndex) userScrolled = true; onEpisodeSelect(idx) },
                    enforceMinHeight = false,
                )
            }
        }
    }

    EpisodeGridDialog(
        show = showEpisodeGrid,
        episodes = lineEpisodes,
        currentIndex = episodeIndex,
        // 倒序仅作用于播放页选集行；"更多"弹窗始终正序显示
        reversed = false,
        onSelect = { idx ->
            if (idx != episodeIndex) userScrolled = true
            onEpisodeSelect(idx)
            showEpisodeGrid = false
        },
        onDismiss = { showEpisodeGrid = false },
    )
}

/** 选集「更多」分页网格弹窗（照搬 fongmi EpisodeGridDialog 框架 + MIUIX OverlayDialog 风格）：
 *  - 按集名平均长度动态计算列数 spanCount（1~5 列）
 *  - 每页 itemCount = spanCount × 行数（按 2/3 窗口容器高再预留一整行推导，宁少一行不截断）
 *  - 顶部分页 Tab 显示每集范围（如 "1 - 10"），底部 HorizontalPager 切换页面
 *  - 打开自动跳转到当前播放集所在页 */
@Composable
private fun EpisodeGridDialog(
    show: Boolean,
    episodes: List<Episode>,
    currentIndex: Int,
    reversed: Boolean,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // 分页与 Tab 始终正序（"1-10"、"11-20"…）；倒序只反转每页内部的集排列
    val displayEps = episodes

    // fongmi setSpanCount：按集名平均长度定列数
    val avgLen = if (episodes.isNotEmpty()) {
        episodes.map { it.title.length }.average().toInt()
    } else 0
    val spanCount = when {
        avgLen >= 12 -> 1
        avgLen >= 8 -> 2
        avgLen >= 4 -> 3
        avgLen >= 2 -> 4
        else -> 5
    }
    // 行数宁少勿截：反编译确认 miuix 硬限弹窗总高 = 2/3 × LocalWindowInfo.containerDpSize.height
    // （窗口容器高，不含系统栏，与 screenHeightDp 不同源），此处用同一基准预留整行安全余量，
    // 保证任意设备/横竖屏/视频下末行完整不截断。固定区 = 标题行28 + dialogTitleGap×4（上下留白+安全余量）+ Tab行44；
    // 每行占高 = chip 48dp（16dp 四边内边距 + 单行文字）+ 行距 8dp
    val dialogFixedHeight = 28.dp + Spacing.dialogTitleGap * 4 + 44.dp
    val dialogCapHeight = androidx.compose.ui.platform.LocalWindowInfo.current.containerDpSize.height * 2f / 3f
    val rowPitch = Spacing.episodeChipHeight + Spacing.dialogItemGap
    val rowsPerPage = ((dialogCapHeight - dialogFixedHeight - rowPitch).value / rowPitch.value).toInt().coerceIn(2, 10)
    val itemCount = spanCount * rowsPerPage
    // 空集（详情加载中）时 pageCount 至少为 1，避免 coerceIn(0, -1) 崩溃
    val pageCount = ((displayEps.size + itemCount - 1) / itemCount).coerceAtLeast(1)

    // 分页标题：始终正序 "1 - 10" / "11 - 20" ...
    val pageTitles = (0 until pageCount).map { p ->
        val start = p * itemCount + 1
        val end = minOf((p + 1) * itemCount, displayEps.size)
        "$start - $end"
    }

    // 当前播放集所在页（始终按正序分页计算）
    val currentPage = if (displayEps.isNotEmpty()) {
        (currentIndex / itemCount).coerceIn(0, pageCount - 1)
    } else 0

    val pagerState = rememberPagerState(initialPage = currentPage, pageCount = { pageCount })
    // 顶部 Tab 行自绘为 LazyRow（不用 material3 ScrollableTabRow，避免其内部 edgePadding 几何不可控）：
    // 打开弹窗时与 pager 同步瞬时定位到当前页，之后翻页再动画跟随
    val tabListState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
    val scope = rememberCoroutineScope()

    // 每次弹窗打开时，强制跳回当前播放集所在页（不记忆上次浏览页）
    LaunchedEffect(show) {
        if (show) {
            pagerState.scrollToPage(currentPage)
            tabListState.scrollToItem(currentPage)
        }
    }
    // 翻页后 Tab 行跟随：与播放页单行选集同款「智能滚动」——
    // 目标 Tab 已完全可见则不动；仅露出一部分（左/右缘裁切）时才滚到刚好完整露出，
    // 避免无论点哪个 Tab 都强制滚到第一位的突兀感。
    LaunchedEffect(pagerState.currentPage) {
        val target = pagerState.currentPage
        snapshotFlow { tabListState.layoutInfo.totalItemsCount }
            .filter { it > target }
            .first()
        val info = tabListState.layoutInfo
        val viewportStart = info.viewportStartOffset
        val viewportEnd = info.viewportEndOffset
        val item = info.visibleItemsInfo.firstOrNull { it.index == target }
        when {
            item == null -> tabListState.animateScrollToItem(target)
            item.offset < viewportStart ->
                tabListState.animateScrollBy((item.offset - viewportStart).toFloat())
            item.offset + item.size > viewportEnd ->
                tabListState.animateScrollBy((item.offset + item.size - viewportEnd).toFloat())
            else -> Unit // 已完全可见：不滚动
        }
    }

    OverlayDialog(
        show = show,
        // 标题改为内容区自绘；必须传 null 而不是空串——miuix 对 title 只做 null 判断，
        // 传 "" 仍会渲染空标题行+12dp 间距，导致弹窗顶部出现大片死空间
        title = null,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
        // 渲染到 AppRoot 根宿主：弹窗+遮罩层级高于底栏
        renderInRootScaffold = true,
        // 强制「非大屏」：平板宽窗口下 miuix 会改为屏幕居中，与手机底部呼出不一致
        largeScreen = false,
        onDismissRequest = onDismiss,
        // 占满当前状态下允许的最大宽度（去掉库默认 maxWidth 上限），
        // 左右外边距 = 全局弹窗内容边距（固定 16dp，不分设备）
        maxWidth = Dp.Infinity,
        // 三边统一 16dp：左右 = 全局弹窗内容边距；底部 = max(16dp, 系统导航栏高度)，跨设备自适应
        outsideMargin = DpSize(Spacing.dialogContentMargin, dialogBottomMargin()),
        // 弹窗内部边距全部归零、由内容区自控：选集网格左右不再内缩，
        // 滑动切页时网格内容直接绘制到弹窗最左/最右边缘；Tab 单独保留 dialogContentMargin 内缩
        insideMargin = DpSize(0.dp, 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 自绘标题：字号/字重/居中与 miuix 原标题一致；距弹窗顶部统一 dialogTitleGap
            Text(
                text = "选集",
                style = MaterialTheme.typography.titleLarge,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.dialogTitleGap),
            )
            if (pageCount > 1) {
                // 标题 ↔ Tab 间距统一 dialogTitleGap
                Spacer(modifier = Modifier.height(Spacing.dialogTitleGap))
                // 自绘分页 Tab（LazyRow）：
                // 几何对齐——内容左右内缩 dialogContentMargin（与选集网格 LazyVerticalGrid 的 horizontal padding 同源），
                // 每个 Tab 盒内左右再内缩 16dp，故静止时首个 Tab 文字墨迹起点 = 16 + 16 = 32dp，
                // 恰好落在第一个选集按钮左缘(16dp) + 圆角半径(Spacing.corner=16dp) 的圆角/直线相切点上；
                // 16dp 内容内边距随滚动滑走，故滚动时 Tab 仍可贴到弹窗最左/最右边缘。
                LazyRow(
                    state = tabListState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.dialogContentMargin),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(pageTitles, key = { _, title -> title }, contentType = { _, _ -> "page_tab" }) { idx, title ->
                        val isSelected = pagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { scope.launch { pagerState.animateScrollToPage(idx) } }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                // Tab ↔ 网格间距统一 dialogTitleGap
                Spacer(modifier = Modifier.height(Spacing.dialogTitleGap))
            } else {
                // 无 Tab 栏（单页）时标题 ↔ 网格同样是 dialogTitleGap
                Spacer(modifier = Modifier.height(Spacing.dialogTitleGap))
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    // 弹窗底部与末行选集的视觉间距对齐左右（dialogContentMargin=16dp）：
                    // 网格页高内含 4dp 防裁余量（透明不可见），故此处补偿 4dp，视觉底部间距 = 16dp
                    .padding(bottom = Spacing.dialogContentMargin - 4.dp),
                // 页与页之间留出间隙（视觉上与 6dp 的 chip 间距区分开），滑动时能看到页与页不粘连
                pageSpacing = Spacing.titleContentGap,
            ) { page ->
                val start = page * itemCount
                val rawPage = displayEps.subList(start, minOf(start + itemCount, displayEps.size))
                // 倒序只反转本页内部的集顺序（分页本身保持正序）
                val pageEpisodes = if (reversed) rawPage.asReversed() else rawPage
                // 页高 = rowsPerPage 行（chip episodeChipHeight + 行距 dialogItemGap）+ 4dp 视口余量：
                // 行距换算像素非整数（2.75x 密度下 22px），LazyGrid 逐项像素取整会在末行底边
                // 裁掉 1~2px 圆角；视口略高于内容即可完整渲染末行（网格背景透明，余量不可见）
                val pageHeight = Spacing.episodeChipHeight * rowsPerPage +
                    Spacing.dialogItemGap * (rowsPerPage - 1) + 4.dp
                // 左右 dialogContentMargin 内缩放在「每一页内部」并随页滑动：静止时网格有正常边距；
                // 滑动切页时 pager 视口为弹窗全宽，内容可以一直绘制到弹窗最左/最右边缘
                LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(spanCount),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.dialogItemGap),
                    verticalArrangement = Arrangement.spacedBy(Spacing.dialogItemGap),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.dialogContentMargin)
                        .height(pageHeight),
                ) {
                    itemsIndexed(pageEpisodes, key = { _, ep -> ep.index }, contentType = { _, _ -> "episode" }) { dispIdx, ep ->
                        // 倒序时本页内部反转：dispIdx 0 对应页内最后一集
                        val realIdx = if (reversed) {
                            start + rawPage.size - 1 - dispIdx
                        } else {
                            start + dispIdx
                        }
                        EpisodeChip(
                            title = ep.title,
                            selected = realIdx == currentIndex,
                            onClick = { onSelect(realIdx) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** 站源接口返回的简介常带 HTML：既有 <p>/<br> 等标签残留，也有 &nbsp;/&amp; 等实体。
 *  显示前用 Html.fromHtml 一次性剥标签 + 反转义实体（&nbsp;→不可断空格、&amp;→&、&lt;/&gt; 等），
 *  避免简介里出现 &nbsp; 这类乱码。
 *  另外部分站源会在简介开头加「免费分享」及后续空白作为推广语，显示前一并剥离。 */
private fun cleanDescription(raw: String): String {
    if (raw.isBlank()) return ""
    val text = android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
    return text.replace(Regex("^免费分享[\\s\\u3000]*"), "").trim()
}

/** 退出全屏/离开播放页后恢复屏幕方向：统一由播放页宿主 Activity 决定 */
private fun Activity?.restoreDefaultOrientation() {
    (this as? PlayerHost)?.restoreDefaultOrientation()
}

/** 简介区块：直接全部显示，不折叠、不可点击 */
@Composable
private fun DescriptionSection(v: Video, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "简介",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(Spacing.videoTitleContentGap))
        Text(
            text = cleanDescription(v.description).ifBlank { "暂无简介" },
            style = MaterialTheme.typography.bodyMedium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

// ========== 全屏布局 ==========

@Composable
private fun FullscreenPlayLayout(
    current: Video?,
    episodeIndex: Int,
    lineEpisodes: List<Episode>,
    playerState: Players.PlayerState,
    controlsVisible: Boolean,
    locked: Boolean,
    settings: SettingsRepository,
    onToggleControls: () -> Unit,
    onHideControls: () -> Unit,
    onControlInteract: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExitFullscreen: () -> Unit,
    onLockToggle: () -> Unit,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    playbackSpeed: Float,
    onScaleChange: (Players.ScaleMode) -> Unit,
    autoNext: Boolean,
    onAutoNextChange: (Boolean) -> Unit,
    onEpisodeSelect: (Int) -> Unit,
    onEngineChange: (Players.Kernel) -> Unit,
    onBufferChange: (Players.BufferTier) -> Unit,
    onDecodeChange: (Players.Decode) -> Unit,
    openingMs: Long,
    endingMs: Long,
    onMarkOpening: () -> Unit,
    onMarkEnding: () -> Unit,
    onClearOpening: () -> Unit,
    onClearEnding: () -> Unit,
    onSeekDragChange: (Boolean) -> Unit,
    onOpenSubtitleSettings: () -> Unit,
    onAddSubtitle: () -> Unit,
    subtitleSettingsOpen: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 亮度持久化（仿 fongmi PlayerSetting）：进全屏应用上次保存的亮度，离开时保存当前值并恢复「跟随系统」
    LaunchedEffect(Unit) {
        val saved = settings.playerBrightness.value
        if (saved >= 0f) {
            (context as? Activity)?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = saved
                w.attributes = lp
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val window = (context as? Activity)?.window
            // 保存当前亮度（跟随系统时为 -1），退出后恢复跟随系统，避免把系统亮度改掉
            settings.setPlayerBrightness(window?.attributes?.screenBrightness ?: -1f)
            window?.let { w ->
                val lp = w.attributes
                if (lp.screenBrightness >= 0f) {
                    lp.screenBrightness = -1f
                    w.attributes = lp
                }
            }
        }
    }
    // 锁定状态下的解锁按钮显隐（单击唤出，2.5s 自动隐藏）
    var unlockHint by remember { mutableStateOf(false) }
    LaunchedEffect(unlockHint) {
        if (unlockHint) {
            delay(2500)
            unlockHint = false
        }
    }
    // 动作栏对话框状态
    var showEngine by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showBuffer by remember { mutableStateOf(false) }
    var showDecode by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    // 视频信息面板（全屏顶栏右上角 info 按钮）：只列编码参数，不显示标题/集名/网速/内核
    var showVideoInfo by remember { mutableStateOf(false) }
    // 选集或字幕设置侧栏打开期间抑制全部播放手势
    val sideSheetOpen = showEpisodes || subtitleSettingsOpen
    // fongmi 同款手势反馈浮层：拖动 seek 预览目标 / 长按倍速提示
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var speedUpHint by remember { mutableStateOf(false) }
    // 拖动底部进度条时的实时目标比例：顶部居中显示「跳转至 MM:SS」，松手消失
    var sliderSeekFraction by remember { mutableStateOf<Float?>(null) }
    // 全屏左右侧上下滑调亮度/音量（fongmi PlayerGestureView 同款：左 1/4 亮度、右 1/4 音量），滑动中显示反馈浮层
    var adjustType by remember { mutableStateOf<AdjustType?>(null) }
    var adjustPercent by remember { mutableIntStateOf(0) }
    // 手势作用区域实际像素尺寸（onSizeChanged 捕获，全屏 fillMaxSize = 整屏），供亮度/音量分区与换算
    var gestureW by remember { mutableIntStateOf(0) }
    var gestureH by remember { mutableIntStateOf(0) }
    // 音量走系统媒体音量，亮度作用于本 Activity 窗口
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    LaunchedEffect(locked) {
        // 锁定状态切换时清理解锁按钮显隐：上锁/解锁后都从隐藏开始，避免残留上一状态的显隐
        unlockHint = false
        if (locked) {
            seekPreview = null
            speedUpHint = false
            sliderSeekFraction = null
            adjustType = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { gestureW = it.width; gestureH = it.height }
            // 背景由根 Box 提供（此处若带背景会盖住常驻 PlayerView）
            // 选集侧边弹窗打开期间（showEpisodes）抑制全部手势：弹窗改为组件内覆盖层后，
            // 事件不再被子窗口拦截，需防止遮罩上的长按/拖动误触发快进/seek
            // keys 必须含 playbackSpeed：长按快进松手时恢复「用户设定的倍速」，
            // 该值变化（如从 1.0 调为 3.0）若不重启本手势层，闭包会一直捕获进入全屏时的旧值，
            // 导致松手后错误地恢复成旧倍速
            .pointerInput(locked, sideSheetOpen, playbackSpeed) {
                // fongmi CustomKeyDown 同款手势：单击显隐控制层、双击播放暂停、长按 2 倍速快进、横向拖动快进快退
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = down.uptimeMillis
                    val downX = down.position.x
                    val downY = down.position.y
                    // 亮度/音量滑动的基准值（按下瞬间捕获一次）：亮度取当前窗口值（跟随系统时换算系统亮度），音量取当前媒体音量
                    val baseBrightness = currentWindowBrightness(context as? Activity)
                    val baseVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                    val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
                    var axis = 0 // 0 未定 | 1 横向seek | 2 纵向/放弃
                    var isLong = false
                    var seekTarget: Long? = null
                    // 点在控制层按钮等可点击控件上（down 事件被消费）：手势全程让位，
                    // 不触发长按快进/拖动seek/单击双击（否则点动作栏按钮会被误判为视频区单击而收起控制层）
                    // 必须用 down 的消费状态立即置位：长按判定走「无后续事件超时」路径，
                    // 若只靠后续事件的 consumed 检查，手指静止长按时 gestureBlocked 永远不会置位，
                    // 底部功能栏按钮/空白区的长按就会穿透触发二倍速快进
                    var gestureBlocked = down.isConsumed
                    // 手指最后已知位置（长按位移判定用）：手指静止时不产生触摸事件，
                    // 裸 await 会让长按判定饿死——必须靠下方超时等待到点主动判定
                    var lastX = downX
                    var lastY = downY

                    while (true) {
                        // 在到达长按阈值的剩余时间内等事件；超时无事件 = 手指静止按住，
                        // 立即触发长按（对齐 fongmi GestureDetector.onLongPress 的系统阈值回调，约 400-500ms）
                        val canLongPress = !isLong && axis == 0 && !locked && !sideSheetOpen && !gestureBlocked
                        val event = if (canLongPress) {
                            val remaining = viewConfiguration.longPressTimeoutMillis -
                                (SystemClock.uptimeMillis() - downTime)
                            if (remaining > 0) {
                                withTimeoutOrNull(remaining) { awaitPointerEvent() }
                            } else {
                                null // 已到阈值：立即走下方长按判定，不再等事件
                            }
                        } else {
                            awaitPointerEvent()
                        }

                        if (event == null) {
                            // 长按阈值到点（期间无新事件）：位移仍在 slop 内 → 2 倍速快进
                            if (hypot(lastX - downX, lastY - downY) <= viewConfiguration.touchSlop) {
                                if (Players.state.value.playing && Players.progress.value.durationMs > 0) {
                                    isLong = true
                                    Players.setSpeed(2f)
                                    speedUpHint = true
                                } else {
                                    axis = 2 // 未在播放：不再触发长按
                                }
                            } else {
                                axis = 2 // 已移出 slop：取消长按资格
                            }
                            continue
                        }

                        if (event.changes.any { it.isConsumed }) gestureBlocked = true
                        if (event.changes.all { !it.pressed }) break // 手指抬起
                        if (locked || gestureBlocked || sideSheetOpen) continue // 锁定/弹窗打开：拖动/长按失效，仅保留单击/双击
                        val change = event.changes.firstOrNull() ?: continue
                        lastX = change.position.x
                        lastY = change.position.y

                        if (isLong) continue // 长按快进期间忽略移动
                        // 方向判定（fongmi checkFunc：累计位移 ≥20dp，横向优先）
                        if (axis == 0) {
                            val adx = abs(change.position.x - downX)
                            val ady = abs(change.position.y - downY)
                            if (hypot(adx, ady) >= 20.dp.toPx()) axis = if (adx >= ady) 1 else 2
                        }
                        // 横向拖动快进/快退（fongmi onScroll：time = deltaX * 50ms，实时预览不真 seek）
                        if (axis == 1) {
                            val prog = Players.progress.value
                            if (prog.durationMs > 0) {
                                val delta = ((change.position.x - downX) * 50).toLong()
                                seekTarget = (prog.positionMs + delta).coerceIn(0L, prog.durationMs)
                                seekPreview = seekTarget
                            }
                        }
                        // 纵向滑动：左侧调亮度、右侧调音量（fongmi 左 1/4 亮度、右 1/4 音量）；
                        // 锁定时上方 locked 已 continue 屏蔽此分支，天然满足「锁定不响应亮度/音量」
                        if (axis == 2) {
                            val dy = change.position.y - downY // 上滑为负
                            val delta = -dy / gestureH.coerceAtLeast(1) // 上滑增：0~1 变化量
                            // 顶部区域（约 15% 屏高）让位给系统下拉状态栏，避免下滑呼出状态栏时误触亮度/音量
                            if (downY >= gestureH * GESTURE_TOP_EXCLUDE_FRACTION) {
                                when {
                                    gestureW > 0 && downX < gestureW / 4f -> {
                                        val newB = (baseBrightness + delta).coerceIn(0.05f, 1f)
                                        (context as? Activity)?.window?.let { w ->
                                            val lp = w.attributes
                                            lp.screenBrightness = newB
                                            w.attributes = lp
                                        }
                                        adjustType = AdjustType.BRIGHTNESS
                                        adjustPercent = (newB * 100).roundToInt()
                                    }
                                    gestureW > 0 && downX > gestureW * 3f / 4f -> {
                                        val newV = (baseVolume + delta * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newV, 0)
                                        adjustType = AdjustType.VOLUME
                                        adjustPercent = if (maxVolume > 0) (newV * 100 / maxVolume) else 0
                                    }
                                    // 中间区域纵向滑动：忽略（中间留给横向 seek）
                                }
                            }
                        }
                    }

                    // 抬起（fongmi onTouchEvent ACTION_UP）
                    if (isLong) {
                        // fongmi onSpeedEnd：恢复用户设定倍速
                        Players.setSpeed(playbackSpeed)
                        speedUpHint = false
                    } else if (axis == 1 && seekTarget != null) {
                        // fongmi onSeekEnd：真正 seek 到预览位置
                        Players.seekTo(seekTarget)
                        seekPreview = null
                    } else if (axis == 2) {
                        // 亮度/音量滑动结束：隐藏反馈浮层
                        adjustType = null
                    } else if (axis == 0 && !gestureBlocked && !sideSheetOpen) {
                        // 未移动未长按（且未点在控件上）：单击/双击判定（fongmi GestureDetector）
                        val second = withTimeoutOrNull(DOUBLE_TAP_TIMEOUT_MS) {
                            awaitFirstDown(requireUnconsumed = false)
                        }
                        if (second != null) {
                            // 双击：等待第二次抬起后切换播放/暂停
                            while (true) {
                                val e2 = awaitPointerEvent()
                                if (e2.changes.all { !it.pressed }) break
                            }
                            onTogglePlay()
                        } else if (locked) {
                            // fongmi onSingleTap 同款：单击切换控制层显隐。
                            // 锁定态下控制层只有解锁按钮，这里取反实现「再点一次收起」
                            unlockHint = !unlockHint
                        } else {
                            onToggleControls()
                        }
                    }
                    seekPreview = null
                }
            },
    ) {
        // 视频信息面板（顶栏 info 按钮展开）：放在控制层之外，控制层 3 秒自动隐藏后仍保留；
        // 打开期间每 500ms 重取一次快照（缓冲/码率等字段会变）
        if (showVideoInfo) {
            var videoInfo by remember { mutableStateOf(Players.videoInfo()) }
            LaunchedEffect(Unit) {
                while (true) {
                    videoInfo = Players.videoInfo()
                    delay(500)
                }
            }
            // start 侧有挖孔时 inset 本身已足够让位，不再叠 16dp；top 的 56dp 是给顶栏留位，始终保留
            val cutoutStart = WindowInsets.displayCutout
                .asPaddingValues(LocalDensity.current)
                .calculateStartPadding(LocalLayoutDirection.current)
            VideoInfoPanel(
                info = videoInfo,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Start + WindowInsetsSides.Top),
                    )
                    .padding(
                        start = if (cutoutStart > 0.dp) 0.dp else 16.dp,
                        top = 56.dp,
                    ),
            )
        }

        // fongmi 手势反馈浮层：顶部居中显示快进/快退目标时间（无胶囊底，纯文字+图标）
        // 拖动底部进度条：顶部居中实时显示「跳转至 MM:SS」，跟随手指位置更新（无胶囊底）
        // 内部订阅独立进度流：拖动期间的进度刷新只重组本浮层
        if (seekPreview != null || sliderSeekFraction != null) {
            SeekFeedbackOverlay(seekPreview = seekPreview, sliderFraction = sliderSeekFraction)
        }
        // 亮度/音量滑动反馈浮层：顶部居中（与 2 倍速提示同位置），图标 + 细进度条
        adjustType?.let { type ->
            GestureAdjustOverlay(
                type = type,
                percent = adjustPercent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
            )
        }
        if (speedUpHint) {
            // 与亮度/音量浮层同尺寸胶囊：36dp 高，文字垂直居中
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x4D000000))
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = "2.0倍速快进中",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                    color = Color.White,
                )
            }
        }

        // 全屏控制层显隐加淡入淡出（200ms，对齐 miuix）
        // 侧栏（选集/字幕设置）打开期间整层控制栏一起隐去，画面上只留侧栏和背景渐变
        AnimatedVisibility(
            visible = controlsVisible && !locked && !sideSheetOpen,
            enter = fadeIn(tween(CONTROL_FADE_DURATION_MS)),
            exit = fadeOut(tween(CONTROL_FADE_DURATION_MS)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶栏（fongmi: 56dp 行高 + 左右 4dp padding，返回 48dp / 标题 16sp 占中间 / 右侧投屏 48dp）
                // 无黑色渐变遮罩：纯白图标直接叠在画面上
                // 全屏不加 statusBarsPadding：上滑回多任务时系统会短暂亮出状态栏，
                // 实时 inset 会让顶栏跟着下移（fongmi 控制层锚定屏幕顶缘，不响应 inset）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
                ) {
                    PlainTopBtn(AppIcons.Back, "返回") { onExitFullscreen() }
                    // 标题 = 仅当前集标题（剔除网盘类选集的 [2.8GB] 等体积前缀），不再拼片名；
                    // 集标题为空（部分站源无集名）时留空，不回退剧名
                    val epTitle = cleanEpisodeTitle(lineEpisodes.getOrNull(episodeIndex)?.title.orEmpty())
                    Text(
                        text = epTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    // 视频信息按钮（圆圈 i，仿当贝播放页右上角）：点击展开/收起下方编码参数面板
                    PlainTopBtn(AppIcons.Info, "视频信息") {
                        showVideoInfo = !showVideoInfo
                        onControlInteract()
                    }
                }

                // 中部（fongmi: 40/48dp shape_control 按钮，play 与两侧各 40dp 间距）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    MiniLargeBtn(AppIcons.SkipPrev, "上集", onClick = { onPrev() })
                    MiniLargeBtn(
                        if (playerState.playing) AppIcons.Pause else AppIcons.Play,
                        if (playerState.playing) "暂停" else "播放",
                        onClick = { onTogglePlay() },
                        isPrimary = true,
                    )
                    MiniLargeBtn(AppIcons.SkipNext, "下集", onClick = { onNext() })
                }

                // 底部（fongmi: 容器 paddingBottom 8dp，seek 左右 16dp，动作栏左右 8dp）
                // 进度条行 + 功能动作栏背后不加阴影/底色（用户要求：全屏底栏完全透明）
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    // 进度条行（时间 + 进度条 + 时长）：行整体下移 —— 去掉行底部内边距，
                    // 使「进度条行 ↔ 功能栏」的间距 ≈「功能栏 ↔ 屏幕底部」的距离
                    // （功能栏到屏幕底 = 容器 8dp 底部内边距 + 按钮自身 8dp 内边距）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp)
                            // 屏蔽本行点击穿透：消费行内空白区（时间文字/进度条两侧）的全部手势，
                            // 手势层收到 consumed 事件即 gestureBlocked，不再误触发
                            // 单击显隐/双击暂停/长按快进/横向拖动 seek；进度条自身拖动不受影响
                            // （子组件先处理事件，此处仅在之后补消费；
                            //   awaitFirstDown 返回的 down 也必须立即消费——手指静止长按期间无后续事件，
                            //   若不消费 down，手势层 gestureBlocked 永远不置位，长按穿透触发 2 倍速）
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false).consume()
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        e.changes.forEach { it.consume() }
                                        if (e.changes.all { !it.pressed }) break
                                    }
                                }
                            },
                    ) {
                        PlayerProgressRow(onDragFractionChange = {
                            sliderSeekFraction = it
                            onSeekDragChange(it != null)
                        })
                    }
                    // 动作栏（fongmi: 左组功能 + 右侧字幕/音轨/片头/片尾，白色 14sp 文字）
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    // 窄屏（竖屏全屏，含平板竖置 668dp；手机横屏约 872dp、平板横屏 1069dp 不受影响）
                    // 一行放不下全部按钮，照抄 OK猫 player_vod_control_view.xml 的做法：
                    // 整条动作栏包进 HorizontalScrollView，可左右滑动；
                    // 宽屏保持原样「左组 + 弹性间距 + 右组贴右」
                    val barScrollable = maxWidth < 700.dp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .then(
                                if (barScrollable) {
                                    // 可横滑分支不能挂「消费全部指针事件」的 pointerInput：
                                    // 那会把拖动全部吃掉，横滑失效（用户反馈的「底栏无法左右滑动」）
                                    Modifier.horizontalScroll(rememberScrollState())
                                } else {
                                    // 宽屏分支沿用原逻辑：消费栏内空白区手势，避免点击穿透误触显隐/暂停
                                    Modifier.pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            while (true) {
                                                val e = awaitPointerEvent()
                                                e.changes.forEach { it.consume() }
                                                if (e.changes.all { !it.pressed }) break
                                            }
                                        }
                                    }
                                },
                            ),
                    ) {
                        // 左组：内核（EXO/MPV/其他弹窗，fongmi PlayerEngineDialog）/ 缓冲 / 重播 / 倍速 / 缩放（点击循环切换）
                        // 点击动作栏按钮重置 5 秒自动隐藏计时（fongmi setR1Callback 同款），不立即消失
                        ActionBtn(playerState.kernel.label) { showEngine = true; onControlInteract() }
                        // 解码方式：EXO 自动/硬解/软解；MPV 软解=纯软解，自动与硬解都走 hwdec
                        ActionBtn(playerState.decode.label) { showDecode = true; onControlInteract() }
                        ActionBtn("缓冲") { showBuffer = true; onControlInteract() }
                        ActionBtn("重播") { Players.seekTo(0); onControlInteract() }
                        // 倍速：按钮直接显示当前倍速值（与内核/缩放按钮同款「显示当前态」）；
                        // 当前倍速非 1 倍时，长按一键重置为 1 倍（已是 1 倍则长按无效，不干扰点击弹窗）
                        ActionBtn(
                            formatSpeed(playerState.speed),
                            onLongClick = if (playerState.speed != 1f) {
                                { onSpeedChange(1f); onControlInteract() }
                            } else {
                                null
                            },
                        ) { showSpeed = true; onControlInteract() }
                        // 画面缩放：竖屏的裁剪/填充几何由 contentSizeFor 自己算，
                        // 换档位只改画面矩形，media3 恒 FIT，不会再把内层撑大溢出小窗
                        ActionBtn(playerState.scaleMode.label) {
                            if (com.ican.tvplay.BuildConfig.DEBUG) {
                                android.util.Log.i(
                                    "PipTrace",
                                    "scaleTap mode=${playerState.scaleMode}",
                                )
                            }
                            val modes = Players.ScaleMode.entries
                            onScaleChange(modes[(playerState.scaleMode.ordinal + 1) % modes.size])
                            onControlInteract()
                        }
                        // 连播开关：开 = 一集播完自动进下一集；关 = 停在最后一帧
                        ActionBtn(if (autoNext) "连播·开" else "连播·关", active = autoNext) {
                            onAutoNextChange(!autoNext)
                            onControlInteract()
                        }
                        // 弹性占位只在宽屏分支有效：横滑分支里 weight 需要一个有界父宽，
                        // 而滚动容器宽度无限，占位会退化成 0 甚至崩溃，所以窄屏直接不留
                        if (!barScrollable) Spacer(modifier = Modifier.weight(1f))
                        // 右组：字幕 / 音轨 / 片头 / 片尾（点击标记当前位置，长按清除）
                        ActionBtn("字幕") { showText = true; onControlInteract() }
                        ActionBtn("音轨") { showAudio = true; onControlInteract() }
                        ActionBtn(
                            if (openingMs > 0) formatMs(openingMs) else "片头",
                            onLongClick = onClearOpening,
                            onClick = { onMarkOpening(); onControlInteract() },
                        )
                        ActionBtn(
                            if (endingMs > 0) formatMs(endingMs) else "片尾",
                            onLongClick = onClearEnding,
                            onClick = { onMarkEnding(); onControlInteract() },
                        )
                        // 最右侧：选集（fongmi control.action.episodes：剧集数 ≥2 才显示，
                        // 点击弹出右侧选集列表 EpisodeListDialog）
                        if (lineEpisodes.size >= 2) {
                            ActionBtn("选集") { onHideControls(); showEpisodes = true; onControlInteract() }
                        }
                    }
                    } // BoxWithConstraints（动作栏横滑容器）结束
                }
            }
        }

        // 右侧居中（fongmi: view_control_right 48dp 纯图标无背景 + marginEnd 4dp）
        // 未锁态属于控制层的一部分，跟随控制层淡入淡出；锁定态由 unlockHint 单独控制。
        // 两态合并在同一个容器里：解锁时 visible 由 unlockHint 切到 controlsVisible，前后都为 true，
        // 因此不触发进出场动画，只是把锁形图标直接换成开启锁形，不会跟控制栏一起 200ms 渐显。
        AnimatedVisibility(
            visible = if (locked) unlockHint else (controlsVisible && !sideSheetOpen),
            enter = fadeIn(tween(CONTROL_FADE_DURATION_MS)),
            exit = fadeOut(tween(CONTROL_FADE_DURATION_MS)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            // 挖孔落在右侧时 inset 本身就承担了视觉间距，此时不再叠原来的 4dp；
            // 没有挖孔时才用 fongmi 的 4dp 边距
            val cutoutEnd = WindowInsets.displayCutout
                .asPaddingValues(LocalDensity.current)
                .calculateEndPadding(LocalLayoutDirection.current)
            Box(
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.End),
                    )
                    .padding(end = if (cutoutEnd > 0.dp) 0.dp else 4.dp)
                    .size(48.dp)
                    .tvCardEffect(
                        onClick = { if (locked) onUnlock() else onLockToggle() },
                        shape = CircleShape,
                        focusedScale = 1.10f,
                        glow = false,
                        pressEffect = false,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (locked) AppIcons.Lock else AppIcons.LockOpen,
                    if (locked) "解锁" else "锁屏",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // 缓冲转圈（fongmi 全屏 view_progress 同款：中心固定位置，不随网速文字位移）
        if (playerState.buffering || playerState.switching) {
            ArcStretchSpinner(
                modifier = Modifier.align(Alignment.Center),
                size = 36.dp,
                strokeWidth = 3.dp,
                color = Color.White,
            )
            // fongmi view_progress 同款：网速文字居中偏下 40dp（12sp 白色），缓冲期间每秒刷新
            var fsSpeedText by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                com.ican.tvplay.player.core.Traffic.reset()
                while (true) {
                    fsSpeedText = com.ican.tvplay.player.core.Traffic.setSpeed()
                    kotlinx.coroutines.delay(1000)
                }
            }
            if (fsSpeedText.isNotEmpty()) {
                Text(
                    text = fsSpeedText,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 40.dp),
                )
            }
        }

        // 动作栏对话框（与竖屏同款）
        // 播放内核（fongmi PlayerEngineDialog：EXO / MPV / 其他=系统选择器外部播放器）
        // 全屏浮层固定深色：容器/未选中行取深色档现成色值（不跟随 App 浅/深主题），
        // 选中强调色仍接入当前主题色。根宿主弹窗不继承调用处局部色板，故色值在弹窗组件内按 forceDark 显式指定。
        val engineOptions = Players.Kernel.entries.map { it.label } + "其他"
        OptionDialog(showEngine, "播放内核", engineOptions, playerState.kernel.label, { it }, { label ->
            when (label) {
                "其他" -> {
                    // 仿 fongmi PlaybackIntent.choose：系统选择器外部播放器打开（url + headers + 进度）
                    val info = Players.externalPlayInfo()
                    if (info != null) {
                        runCatching {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(android.net.Uri.parse(info.url), "video/*")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                putExtra("title", info.title)
                                putExtra("position", info.positionMs.toInt())
                                putExtra("return_result", true)
                                if (info.headers.isNotEmpty()) {
                                    putExtra("headers", info.headers.flatMap { (k, v) -> listOf(k, v) }.toTypedArray())
                                }
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, null))
                        }
                    }
                }
                else -> Players.Kernel.entries.firstOrNull { it.label == label }?.let(onEngineChange)
            }
            showEngine = false
            },
            // ISO 光盘镜像只有 MPV 能解：EXO 置灰且不可点，避免切过去必然进错误页
            enabled = { label -> !(playerState.isoSource && label == Players.Kernel.EXO.label) },
            forceDark = true,
        ) { showEngine = false }
        OptionDialog(showSpeed, "倍速", listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 3.0f), playerState.speed, { formatSpeed(it) }, { onSpeedChange(it); showSpeed = false }, forceDark = true) { showSpeed = false }
        OptionDialog(showBuffer, "缓冲档位", Players.BufferTier.entries, playerState.bufferTier, { it.label }, { onBufferChange(it); showBuffer = false }, forceDark = true) { showBuffer = false }
        OptionDialog(showDecode, "解码方式", Players.Decode.entries, playerState.decode, { it.label }, { onDecodeChange(it); showDecode = false }, forceDark = true) { showDecode = false }
        if (showEpisodes) {
            EpisodeSideSheet(
                episodes = lineEpisodes,
                currentIndex = episodeIndex,
                onSelect = { idx ->
                    // 切集由 sheet 收回动画结束后通过 onDismiss 统一关闭
                    onEpisodeSelect(idx)
                },
                onDismiss = { showEpisodes = false },
            )
        }
        OptionDialog(showAudio, "音轨", playerState.audioTracks, playerState.audioTracks.firstOrNull { it.selected }, { it.name }, { Players.selectTrack(it, androidx.media3.common.C.TRACK_TYPE_AUDIO); showAudio = false }, forceDark = true) { showAudio = false }
        SubtitleDialog(
            show = showText,
            tracks = playerState.textTracks,
            textDisabled = playerState.textDisabled,
            onSelect = { Players.selectTrack(it, androidx.media3.common.C.TRACK_TYPE_TEXT); showText = false },
            onAddSubtitle = { showText = false; onAddSubtitle() },
            onSettings = { showText = false; onOpenSubtitleSettings() },
            onDismiss = { showText = false },
            forceDark = true,
        )
    }
}

/** 全屏手势调节类型：左 1/4 亮度、右 1/4 音量（fongmi PlayerGestureView 同款） */
private enum class AdjustType { BRIGHTNESS, VOLUME }

/** 全屏顶部预留高度（屏幕高比例）：顶部区域纵向滑动让位给系统下拉状态栏，避免下滑呼出状态栏时误触亮度/音量 */
private const val GESTURE_TOP_EXCLUDE_FRACTION = 0.15f

/** 读取 Activity 窗口当前亮度（0.0~1.0）：窗口 screenBrightness 为 -1（跟随系统）时换算系统亮度值 */
private fun currentWindowBrightness(activity: Activity?): Float {
    val window = activity?.window ?: return 0.5f
    val attrs = window.attributes
    if (attrs.screenBrightness >= 0f) return attrs.screenBrightness
    val sys = runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    }.getOrDefault(128)
    return (sys / 255f).coerceIn(0f, 1f)
}

/**
 * 亮度/音量滑动反馈浮层：顶部居中（与 2 倍速提示同位置），只保留系统图标 + 细进度条
 * 尺寸对齐灵动岛比例：胶囊高 36dp（图标 20dp + 上下各 8dp），
 * 左 8dp 让图标圆心与左端帽圆心同心，条距图标 8dp（条左端与端帽圆相切），
 * 右 16dp 让条端小圆（r=2dp）与右端帽圆（r=18dp）圆心同心
 */
@Composable
private fun GestureAdjustOverlay(type: AdjustType, percent: Int, modifier: Modifier = Modifier) {
    val label = if (type == AdjustType.BRIGHTNESS) "亮度" else "音量"
    val icon = if (type == AdjustType.BRIGHTNESS) AppIcons.MiuBrightness else AppIcons.MiuVolume
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0x4D000000))
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        // 图标用澎湃OS 原始配色（橙色太阳 / 蓝色喇叭），不跟随 tint
        Icon(icon, label, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        // 细进度条（100dp × 4dp，全圆角）：白底半透明轨道 + 白色前景
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0x40FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White),
            )
        }
    }
}

// ========== 通用 UI 组件 ==========

/** 全屏动作栏纯文字按钮（fongmi style/Control：14sp 白色文字 + 8dp padding 无背景），按压用我们自己的变浅灰特效 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ActionBtn(
    text: String,
    onLongClick: (() -> Unit)? = null, active: Boolean = true, onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
        color = if (active) Color.White else Color.White.copy(alpha = 0.45f),
        maxLines = 1,
        modifier = Modifier
            .tvCardEffect(
                onClick = onClick,
                onLongClick = onLongClick,
                shape = RoundedCornerShape(percent = 50),
                focusedScale = 1.05f,
                glow = false,
                pressEffect = false,
            )
            .padding(8.dp),
    )
}

@Composable
private fun EpisodeChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enforceMinHeight: Boolean = true,
    fill: Color? = null,
    selectedTextColor: Color? = null,
    // 未选中文字色：侧栏深色场景下需要比 onSecondaryVariant 更收敛，允许调用方覆盖
    unselectedTextColor: Color? = null,
    // 字号覆盖：侧栏里 chip 与列表项统一 14sp（默认 labelMedium 只有 12sp，全屏里偏小）
    fontSize: TextUnit? = null,
) {
    val shape = SquircleShape(com.ican.tvplay.ui.theme.Spacing.corner)
    val darkMode = LocalDarkTheme.current
    val primary = MiuixTheme.colorScheme.primary
    val tintedFill = remember(primary, darkMode) { chipTintedFill(primary, darkMode) }
    val tintedText = remember(primary, darkMode) { chipTintedText(primary, darkMode) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            // 展开网格弹窗兜底到 episodeChipHeight（与分页换算一致）；单行横滑选集栏不约束高度
            .defaultMinSize(
                minWidth = 40.dp,
                minHeight = if (enforceMinHeight) Spacing.episodeChipHeight else 0.dp,
            )
            // 选集列表自带选中态：整列表（含未选中项）统一不要按压/点击蒙层，选中色即反馈
            .tvCardEffect(onClick = onClick, shape = shape, focusedScale = 1.05f, glow = false, pressEffect = false)
            // 选中：primary 派生 tinted 底；未选中 secondaryVariant（浅 #F0F0F0 / 深 #434343）
            .background(
                fill ?: if (selected) tintedFill
                else MiuixTheme.colorScheme.secondaryVariant,
                shape,
            )
            .padding(12.dp),
    ) {
        Text(
            text = title,
            style = if (fontSize != null) {
                MaterialTheme.typography.labelMedium.copy(fontSize = fontSize)
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = if (selected) (selectedTextColor ?: tintedText)
            else (unselectedTextColor ?: MiuixTheme.colorScheme.onSecondaryVariant),
            fontWeight = if (selected) FontWeight.SemiBold else null,
            maxLines = 1,
        )
    }
}

/**
 * 全屏侧边面板的渐变背景层：整屏「右实左淡」横向暗场。
 * 右侧与面板等宽的一段保持 94% 等浓度（内容始终压在稳定底色上），
 * 面板左缘外一直到屏幕最左缘都在平滑褪淡。
 * 与面板同起同收：跟面板共用同一个 offset 位移，同时 260ms 淡入淡出
 * （淡出带横跨到屏幕最左，单靠位移不可能整块移出屏幕，所以必须配合透明度收尾）。
 */
@Composable
private fun SideSheetGradientLayer(
    visible: Boolean,
    contentFraction: Float,
    featherFraction: Float,
    modifier: Modifier = Modifier,
) {
    val layerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "sideSheetGradient",
    )
    // 完全隐藏且不处于显示态时直接不组合，避免透明层常驻占位
    if (layerAlpha == 0f && !visible) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(layerAlpha)
            .background(sideSheetScrimBrush(contentFraction, featherFraction = featherFraction)),
    )
}

/** 全屏侧边面板的触摸屏蔽层：不绘制任何颜色，只负责点空白收起与挡住底层视频控件 */
@Composable
private fun SideSheetTouchShield(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    )
}

/** 全屏选集侧边弹窗（fongmi EpisodeListDialog 同款：右侧滑出面板，宽度=max(200dp,最长集名14sp文本宽)且≤屏宽1/2，
 * 垂直列表、居中 14sp 文本、无面板底色与圆角（左缘为直边，靠整屏渐变暗场托底）、选中集高亮，打开自动滚动到当前集，点击切集并关闭）。
 * 收回动画与呼出对称：先从右往左滑入，收回时原路从左往右滑出，动画结束再通知父级移除。 */
@Composable
private fun EpisodeSideSheet(
    episodes: List<Episode>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val settings = appContainer().settingsRepository
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val configuration = LocalConfiguration.current
    val textStyle = TextStyle(fontSize = 14.sp)
    // fongmi getWidth()：minWidth=200dp，逐项取最长文本宽，上限为屏宽 1/2（1/3 会在平板横屏截断长集名）。
    // 文本宽需另加按钮水平内边距 24dp（左右各 12dp）+ 面板水平边距 32dp（dialogContentMargin 左右各 16dp），
    // 否则实际可用显示宽比测量值少 ~56dp，中短集名仍会被截断
    val panelWidth = remember(episodes, configuration) {
        val minPx = with(density) { 200.dp.toPx() }
        val maxPx = with(density) { configuration.screenWidthDp.dp.toPx() / 2f }
        val paddingPx = with(density) { (24.dp + Spacing.dialogContentMargin * 2).toPx() }
        var w = minPx
        episodes.forEach { ep ->
            w = maxOf(w, textMeasurer.measure(ep.title, textStyle).size.width + paddingPx)
        }
        with(density) { minOf(w, maxPx).toDp() }
    }
    // 背景渐变里「等浓度区」宽度 = 面板宽度占屏宽的比例：面板按最长集名动态算宽，等浓度区跟着变
    val contentFraction = remember(panelWidth, configuration) {
        (panelWidth.value / configuration.screenWidthDp.toFloat()).coerceIn(0f, 1f)
    }
    // 淡出带一直拉到屏幕最左缘：变淡起点仍在面板左缘（= 列表按钮左缘再往左 16dp）
    val featherFraction = remember(contentFraction) { (1f - contentFraction).coerceIn(0f, 1f) }
    // 用组件内全屏覆盖层而非 Popup/Dialog：
    // - Dialog 会新建窗口并自动唤出状态栏/导航栏，破坏全屏沉浸；
    // - Popup 子窗口的 layoutInDisplayCutoutMode 固定为 DEFAULT，横屏全屏时会避开左侧
    //   挖孔/刘海区，与 Activity 窗口错位；覆盖层跟随 Activity 窗口绘制，与视频区像素级对齐
    // 呼出：首帧就停在距终点 SIDE_SHEET_ENTER_OFFSET 的位置并同步淡入（200ms，与控制栏淡出同起同落），
    // 不再从屏外整段滑入——那样前 100ms 面板还没进画面，看起来是“控制栏先消失、弹窗后出现”
    // 收回：仍从左往右滑出到屏外，动画结束再调用 onDismiss 移除覆盖层
    var sheetShown by remember { mutableStateOf(false) }
    var sheetClosing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sheetShown = true }
    val sheetSlideX by animateDpAsState(
        targetValue = when {
            sheetClosing -> panelWidth + 20.dp
            sheetShown -> 0.dp
            else -> SIDE_SHEET_ENTER_OFFSET
        },
        animationSpec = tween(
            if (sheetClosing) SIDE_SHEET_EXIT_MS else SIDE_SHEET_ENTER_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "episodeSheetSlide",
    )
    val sheetAlpha by animateFloatAsState(
        targetValue = if (sheetShown) 1f else 0f,
        animationSpec = tween(
            if (sheetClosing) SIDE_SHEET_EXIT_MS else SIDE_SHEET_ENTER_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "episodeSheetAlpha",
    )
    // 面板外不再压暗：空白区只放透明触摸拦截层（DismissTouchLayer），点击即收起
    // 收回：播放滑出动画后再通知父级
    val dismissWithAnim: () -> Unit = {
        scope.launch {
            sheetShown = false
            sheetClosing = true
            kotlinx.coroutines.delay(SIDE_SHEET_EXIT_MS.toLong())
            onDismiss()
        }
    }
    // 返回键先收起弹窗（原为 Popup 的 dismissOnBackPress）
    BackHandler(onBack = dismissWithAnim)
    Box(modifier = Modifier.fillMaxSize().alpha(sheetAlpha)) {
            // 触摸屏蔽层（无颜色）：点面板外收起面板，挡住底层视频控件
            SideSheetTouchShield(enabled = sheetShown, onClick = dismissWithAnim)
            // 渐变背景与面板同进同退：同一个 offset，一起滑入/滑出
            SideSheetGradientLayer(
                visible = sheetShown,
                contentFraction = contentFraction,
                featherFraction = featherFraction,
                modifier = Modifier.offset { IntOffset(sheetSlideX.roundToPx(), 0) },
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset { IntOffset(sheetSlideX.roundToPx(), 0) }
                    .fillMaxHeight()
                    .width(panelWidth)
                    // 面板本体消费点击，避免点面板空白处误触发外层收起
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    // 左右边距 = 全局弹窗内容边距；顶部不再用固定内边距，
                    // 顶部留白改由列表 contentPadding 承担，内容可一路滑到面板最顶缘
                    .padding(horizontal = Spacing.dialogContentMargin),
            ) {
                // 面板不再单独铺色：文字直接压在整屏「右浓左淡」渐变暗场上（面板区域仍 ≥90% 黑），
                // 内部强制深色档色板以保证滑杆/文字对比；强调色跟随用户选的主题色，
                // 与同页「倍速/音轨/字幕」等弹窗（playerDarkMiuixColors(themeColor)）保持同色
                val sheetThemeColor by settings.themeColor.collectAsStateWithLifecycle()
                MiuixTheme(colors = playerDarkMiuixColors(sheetThemeColor)) {
                // 弹出前就定位到当前集（首帧就位，无滚动动画）：
                // initialFirstVisibleItemIndex 让当前集作为第一个可见 item 出现在顶部。
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = currentIndex.coerceIn(0, episodes.lastIndex),
                )
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(Spacing.dialogItemGap),
                    // 顶部留白随内容滚动：列表可滑到面板最顶缘，静止时首项距顶 = dialogContentMargin（与左右/底部一致）
                    // 底边距同样随内容滚动，末项距底 = 全局弹窗内容边距
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = Spacing.dialogContentMargin,
                        bottom = Spacing.dialogContentMargin,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(episodes, key = { _, ep -> ep.index }, contentType = { _, _ -> "episode" }) { idx, ep ->
                        val selected = idx == currentIndex
                        val shape = SquircleShape(com.ican.tvplay.ui.theme.Spacing.corner)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                // 行高对齐选集 chip（episodeChipHeight 48dp），与底部弹窗条目同一密度
                                .heightIn(min = Spacing.episodeChipHeight)
                                .clip(shape)
                                // 选中/未选中统一浅灰胶囊底（白 10%），仅文字区分选中态
                                .background(Color.White.copy(alpha = SideSheetFillAlpha))
                                // 选集列表自带选中态：去掉按压/点击涟漪，选中色即反馈
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onSelect(idx); dismissWithAnim() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = ep.title,
                                fontSize = 14.sp,
                                // 选中 primary 文字；未选中 onSurfaceVariantSummary
                                color = if (selected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontWeight = if (selected) FontWeight.SemiBold else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                }
            }
        }
    }

/** 播放器底部动作栏文字按钮（fongmi: style/Control，白色小字横向滚动） */
@Composable
private fun ControlChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        maxLines = 1,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 顶栏纯图标按钮（fongmi 同款）：48dp 无圆形底色，仅白色图标，保留点击热区与焦点缩放 */
@Composable
private fun PlainTopBtn(
    icon: ImageVector,
    desc: String,
    // 按压反馈（miuix MiuixIndication 圆形蒙层）：默认关闭，投屏等按钮保持现状无反馈
    pressEffect: Boolean = false,
    // 蒙层色覆盖：小窗按钮压在暗视频上，用白色 10% 叠出「浅灰」按压感
    pressColor: Color? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .tvCardEffect(
                onClick = onClick,
                shape = CircleShape,
                focusedScale = 1.10f,
                glow = false,
                pressEffect = pressEffect,
                pressColor = pressColor,
            ),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

/** 预览小窗中部控制按钮（fongmi: shape_control 40/48dp 圆形 + 30% 黑底 + 32dp 白图标），按压用 tvCardEffect 变浅灰 */
@Composable
private fun MiniLargeBtn(icon: ImageVector, desc: String, onClick: () -> Unit, isPrimary: Boolean = false) {
    val size = if (isPrimary) 48.dp else 40.dp
    // fongmi: exo_icon_play/pause 固有尺寸 32dp（scaleType=center 不做缩放），主按钮照搬该尺寸
    val iconSize = if (isPrimary) 32.dp else 24.dp
    Box(
        modifier = Modifier
            .size(size)
            .tvCardEffect(onClick = onClick, shape = CircleShape, focusedScale = 1.10f, glow = false, pressEffect = false)
            .background(Color(0x4D000000), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

// ========== 对话框 ==========
// 播放页底部功能弹窗（倍速/缓冲/音轨/播放内核/画面缩放/字幕/添加字幕）统一走
// miuix OverlayDialog，与设置页「站源设置」同款实现：底部呼出、滑入滑出与遮罩
// 均由库处理，不再自建 Dialog 窗口（避免平台窗口淡入淡出与遮罩动画不一致）。


@Composable
private fun <T> OptionDialog(
    show: Boolean, title: String, options: List<T>, current: T?, label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: (T) -> Boolean = { true },
    // true = 全屏呼出的功能弹窗：容器/未选中行固定深色档（不跟随主题），选中强调色仍取主题色；
    // false = 竖屏详情页弹窗，跟随全局浅/深主题
    forceDark: Boolean = false,
    onDismiss: () -> Unit = {},
) {
    val scheme = MiuixTheme.colorScheme
    val dialogContainer = if (forceDark) DarkSurfaceVariant else scheme.surfaceContainer
    val dialogTitleColor = if (forceDark) DarkOnSurface else scheme.onBackground
    val rowFill = if (forceDark) DarkDialogRow else scheme.secondaryVariant
    val rowText = if (forceDark) DarkDialogRowText else scheme.onSecondaryVariant
    // 选项过多（倍速档位/音轨）时限高可滚动，避免超出屏幕被裁、末项露出半截边缘
    val maxDialogHeight = with(LocalConfiguration.current) { (screenHeightDp * 0.65f).dp }
    // 与设置页「站源设置」同款：miuix OverlayDialog 底部呼出（滑入滑出 + 遮罩由库处理）
    OverlayDialog(
        show = show,
        // 标题自绘（与选集「更多」弹窗同款）：库默认 insideMargin=24dp 会在列表底部压出一条背景色
        // 遮挡带且标题间距不可控；传 null + insideMargin 归零后全部边距由内容区自控
        title = null,
        backgroundColor = dialogContainer,
        renderInRootScaffold = true,
        largeScreen = false,
        onDismissRequest = onDismiss,
        outsideMargin = DpSize(Spacing.dialogContentMargin, 0.dp),
        insideMargin = DpSize(0.dp, 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight),
        ) {
            // 标题距弹窗顶 = 标题距下方选项 = dialogTitleGap
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = dialogTitleColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.dialogTitleGap),
            )
            Spacer(modifier = Modifier.height(Spacing.dialogTitleGap))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    // 左右边距 = 全局弹窗内容边距（scroll 之前，滑动时选项不进入两侧边距区）
                    .padding(horizontal = Spacing.dialogContentMargin)
                    .verticalScroll(rememberScrollState())
                    // 底部边距随内容滚动（scroll 之后）：长列表可一直滑到弹窗最底缘，
                    // 静止时末项与弹窗底部的间距 = 全局弹窗内容边距（与左右一致）
                    .padding(bottom = Spacing.dialogContentMargin),
                verticalArrangement = Arrangement.spacedBy(Spacing.dialogItemGap),
            ) {
                options.forEach { opt ->
                    val selected = opt == current
                    val itemEnabled = enabled(opt)
                    val shape = SquircleShape(com.ican.tvplay.ui.theme.Spacing.corner)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 不可用项整行淡化（播放内核里 ISO 源时的 EXO），且不响应点击
                            .alpha(if (itemEnabled) 1f else 0.35f)
                            .clickable(
                                enabled = itemEnabled,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(opt) }
                            // 与线路/选集 chip 一致：选中 primary 主题色底 + onPrimary 自适应文字；未选中 secondaryVariant 灰底，无描边
                            .background(
                                if (selected) scheme.primary else rowFill, shape,
                            )
                            .padding(Spacing.dialogItemPadding),
                    ) {
                        Text(label(opt), style = MaterialTheme.typography.bodyMedium, color = if (selected) scheme.onPrimary else rowText, modifier = Modifier.weight(1f))
                        if (selected) Icon(AppIcons.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleDialog(
    show: Boolean, tracks: List<Players.TrackOption>, textDisabled: Boolean,
    onSelect: (Players.TrackOption) -> Unit,
    onAddSubtitle: () -> Unit, onSettings: () -> Unit,
    // true = 全屏呼出的字幕弹窗：容器/未选中行固定深色档；false = 竖屏详情页弹窗，跟随主题
    forceDark: Boolean = false,
    onDismiss: () -> Unit = {},
) {
    val scheme = MiuixTheme.colorScheme
    val dialogContainer = if (forceDark) DarkSurfaceVariant else scheme.surfaceContainer
    val dialogTitleColor = if (forceDark) DarkOnSurface else scheme.onBackground
    // 与设置页「站源设置」同款：miuix OverlayDialog 底部呼出
    // 标题自绘 + insideMargin 归零（同 OptionDialog）：消除库默认 24dp 内边距造成的底部背景遮挡条
    OverlayDialog(
        show = show,
        title = null,
        backgroundColor = dialogContainer,
        renderInRootScaffold = true,
        largeScreen = false,
        onDismissRequest = onDismiss,
        outsideMargin = DpSize(Spacing.dialogContentMargin, 0.dp),
        insideMargin = DpSize(0.dp, 0.dp),
    ) {
        val maxDialogHeight = with(LocalConfiguration.current) { (screenHeightDp * 0.65f).dp }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight),
        ) {
            // 标题距弹窗顶 = 标题距下方选项 = dialogTitleGap
            Text(
                text = "字幕",
                style = MaterialTheme.typography.titleLarge,
                color = dialogTitleColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.dialogTitleGap),
            )
            Spacer(modifier = Modifier.height(Spacing.dialogTitleGap))
            // 行间距与 OptionDialog（倍速/缓冲等弹窗）统一为 dialogItemGap；
            // 左右/底部边距 = 全局弹窗内容边距，底部边距在 scroll 之后随内容滚动（长列表可滑到弹窗最底缘）
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = Spacing.dialogContentMargin)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Spacing.dialogContentMargin),
                verticalArrangement = Arrangement.spacedBy(Spacing.dialogItemGap),
            ) {
                tracks.forEach { t -> SubRow(t.name, !textDisabled && t.selected, forceDark) { onSelect(t) } }
                SubRow("添加字幕", false, forceDark) { onAddSubtitle() }
                SubRow("字幕设置", false, forceDark) { onSettings() }
            }
        }
    }
}

/** 字幕设置侧边栏：与 EpisodeSideSheet 同款右侧滑入面板 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SubtitleSettingSideSheet(
    statusBarInset: Boolean,
    onDismiss: () -> Unit,
) {
    val settings = appContainer().settingsRepository
    val playerState by Players.state.collectAsStateWithLifecycle()
    val subtitleScale by settings.subtitleScale.collectAsStateWithLifecycle()
    val subtitlePosition by settings.subtitlePosition.collectAsStateWithLifecycle()
    val subtitleStyle by settings.subtitleStyle.collectAsStateWithLifecycle()
    // 默认点震动：记录上一次是否处于吸附区，仅在“进入”时震动一次
    val haptic = LocalHapticFeedback.current
    var scaleAtKeyPoint by remember { mutableStateOf(abs(subtitleScale - 1f) < 0.02f) }
    var positionAtKeyPoint by remember { mutableStateOf(subtitlePosition == 0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // 侧栏宽度：手机横屏约 42% 屏宽，限制在 280-400dp
    val panelWidth = remember(configuration) {
        val screenPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        with(density) { (screenPx * 0.42f).coerceIn(280.dp.toPx(), 400.dp.toPx()).toDp() }
    }
    // 呼出：首帧停在距终点 SIDE_SHEET_ENTER_OFFSET 的位置并同步淡入（200ms，与控制栏淡出同起同落）
    var sheetShown by remember { mutableStateOf(false) }
    var sheetClosing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sheetShown = true }
    // 背景渐变里「等浓度区」宽度 = 面板宽度占屏宽的比例
    val contentFraction = remember(panelWidth, configuration) {
        (panelWidth.value / configuration.screenWidthDp.toFloat()).coerceIn(0f, 1f)
    }
    // 淡出带一直拉到屏幕最左缘：变淡起点仍在面板左缘（= 列表按钮左缘再往左 16dp）
    val featherFraction = remember(contentFraction) { (1f - contentFraction).coerceIn(0f, 1f) }
    val sheetSlideX by animateDpAsState(
        targetValue = when {
            sheetClosing -> panelWidth + 20.dp
            sheetShown -> 0.dp
            else -> SIDE_SHEET_ENTER_OFFSET
        },
        animationSpec = tween(
            if (sheetClosing) SIDE_SHEET_EXIT_MS else SIDE_SHEET_ENTER_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "subtitleSheetSlide",
    )
    val sheetAlpha by animateFloatAsState(
        targetValue = if (sheetShown) 1f else 0f,
        animationSpec = tween(
            if (sheetClosing) SIDE_SHEET_EXIT_MS else SIDE_SHEET_ENTER_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "subtitleSheetAlpha",
    )
    val dismissWithAnim: () -> Unit = {
        scope.launch {
            sheetShown = false
            sheetClosing = true
            delay(SIDE_SHEET_EXIT_MS.toLong())
            onDismiss()
        }
    }
    BackHandler(onBack = dismissWithAnim)
    Box(modifier = Modifier.fillMaxSize().alpha(sheetAlpha)) {
        // 触摸屏蔽层（无颜色）：点面板外收起面板，挡住底层视频控件
        SideSheetTouchShield(enabled = sheetShown, onClick = dismissWithAnim)
        // 渐变背景与面板同进同退：同一个 offset，一起滑入/滑出
        SideSheetGradientLayer(
            visible = sheetShown,
            contentFraction = contentFraction,
            featherFraction = featherFraction,
            modifier = Modifier.offset { IntOffset(sheetSlideX.roundToPx(), 0) },
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset { IntOffset(sheetSlideX.roundToPx(), 0) }
                .fillMaxHeight()
                .width(panelWidth)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                // 左右边距 = 全局弹窗内容边距；顶部内边距不再放在滚动容器上
                //（放上去会在面板上方形成一条硬截断带，内容永远滑不到面板最顶端），
                // 改由标题自身带上顶部留白，内容可一直滚到面板顶缘。
                // 底部边距移到 verticalScroll 之后随内容滚动：列表可滑到面板最底缘，不再被固定内边距遮挡
                .padding(horizontal = Spacing.dialogContentMargin)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.dialogContentMargin),
        ) {
            // 面板不再单独铺色：文字直接压在整屏「右浓左淡」渐变暗场上（面板区域仍 ≥90% 黑），
            // 内部强制深色档色板；强调色跟随用户选的主题色，与同页其他弹窗同色
            val sheetThemeColor by settings.themeColor.collectAsStateWithLifecycle()
            MiuixTheme(colors = playerDarkMiuixColors(sheetThemeColor)) {
            // 竖屏非全屏时给状态栏让位：作为可滚动内容的顶部留白（而不是容器内边距）
            val statusBarTopPadding = if (statusBarInset) {
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            } else {
                0.dp
            }
            // 排版对齐 fongmi dialog_subtitle_setting：顶部标题 + 右侧「重置」，下面一排 Tab 按钮
            var subtitleTab by remember { mutableIntStateOf(0) }
            // 面板内所有单选 chip 统一配色：底一律 white 10%，选中=主题色文字+加粗，未选中=muted 白
            val chipFill = Color.White.copy(alpha = SideSheetFillAlpha)
            val chipSelectedText = MiuixTheme.colorScheme.primary
            val chipUnselectedText = Color.White.copy(alpha = SideSheetMutedTextAlpha)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = Spacing.titleContentGap + statusBarTopPadding,
                        bottom = Spacing.titleContentGap,
                    ),
            ) {
                Text(
                    text = "字幕设置",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                // 「重置」与标题视觉等大：固定 40dp 高（≈ 20sp 标题行高 + 一点余量），文字垂直居中；
                // 底用 white 10%（与面板内 chip 同源）、不描边，避免黑底上的 1dp 细边发灰
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(SquircleShape(Spacing.corner))
                        .background(Color.White.copy(alpha = SideSheetFillAlpha))
                        .combinedClickable(
                            // 对齐 fongmi：短按重置当前 Tab
                            onClick = {
                                when (subtitleTab) {
                                    0 -> settings.resetSubtitleStyle()
                                    1 -> {
                                        settings.setSubtitleScale(1f)
                                        settings.setSubtitlePosition(0f)
                                    }
                                    else -> Players.setSubtitleOffset(0)
                                }
                            },
                            // 长按全重置（fongmi resetAll 同款）：不切 Tab、不加 toast/snackbar 提示
                            onLongClick = {
                                settings.resetSubtitleStyle()
                                settings.setSubtitleScale(1f)
                                settings.setSubtitlePosition(0f)
                                Players.setSubtitleOffset(0)
                            },
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "重置",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = SideSheetActionTextAlpha),
                    )
                }
            }
            SubtitleStyleChips(
                labels = listOf("外观", "调节", "偏移"),
                selectedIndex = subtitleTab,
                onSelect = { subtitleTab = it },
                modifier = Modifier.padding(bottom = Spacing.sectionSpacing),
                fill = chipFill,
                selectedTextColor = chipSelectedText,
                unselectedTextColor = chipUnselectedText,
            )
            if (subtitleTab == 0) {
            SideSheetSectionTitle("字幕样式")
            // 两档样式（对齐 fongmi SubtitleSetting：系统 / 自定义）
            SubtitleStyleChipRow(
                labels = listOf("系统", "自定义"),
                selectedIndex = if (subtitleStyle.mode == SubtitleStyle.MODE_CUSTOM) 1 else 0,
                onSelect = { index ->
                    settings.setSubtitleStyle(subtitleStyle.copy(mode = if (index == 1) SubtitleStyle.MODE_CUSTOM else SubtitleStyle.MODE_SYSTEM))
                },
                modifier = Modifier.padding(top = Spacing.videoTitleContentGap),
                fill = chipFill,
                selectedTextColor = chipSelectedText,
                unselectedTextColor = chipUnselectedText,
            )
            if (subtitleStyle.mode == SubtitleStyle.MODE_CUSTOM) {
                SideSheetSectionTitle(
                    text = "文字颜色",
                    modifier = Modifier.padding(top = Spacing.sectionSpacing),
                )
                SubtitleColorSwatches(
                    colors = listOf(
                        SubtitleStyle.COLOR_WHITE,
                        SubtitleStyle.COLOR_YELLOW,
                        SubtitleStyle.COLOR_CYAN,
                        SubtitleStyle.COLOR_GREEN,
                        SubtitleStyle.COLOR_ORANGE,
                        SubtitleStyle.COLOR_PINK,
                        SubtitleStyle.COLOR_RED,
                        SubtitleStyle.COLOR_BLUE,
                        SubtitleStyle.COLOR_GRAY,
                        SubtitleStyle.COLOR_BLACK,
                    ),
                    selected = subtitleStyle.textColor,
                    onSelect = { color ->
                        settings.setSubtitleStyle(subtitleStyle.copy(textColor = color))
                    },
                    modifier = Modifier.padding(top = Spacing.videoTitleContentGap),
                )
                SubtitleStyleSliderRow(
                    title = "文字透明度",
                    value = subtitleStyle.textOpacity,
                    display = "${(subtitleStyle.textOpacity * 100).roundToInt()}%",
                    onChange = { opacity ->
                        settings.setSubtitleStyle(subtitleStyle.copy(textOpacity = opacity))
                    },
                )
                SideSheetSectionTitle(
                    text = "描边样式",
                    modifier = Modifier.padding(top = Spacing.sectionSpacing),
                )
                SubtitleStyleChipRow(
                    labels = listOf("无", "描边", "投影"),
                    selectedIndex = subtitleStyle.edgeType,
                    onSelect = { type ->
                        settings.setSubtitleStyle(subtitleStyle.copy(edgeType = type))
                    },
                    modifier = Modifier.padding(top = Spacing.videoTitleContentGap),
                    fill = chipFill,
                    selectedTextColor = chipSelectedText,
                    unselectedTextColor = chipUnselectedText,
                )
                if (subtitleStyle.edgeType != SubtitleStyle.EDGE_NONE) {
                    SideSheetSectionTitle(
                        text = "描边颜色",
                        modifier = Modifier.padding(top = Spacing.sectionSpacing),
                    )
                    SubtitleColorSwatches(
                        colors = listOf(
                            SubtitleStyle.COLOR_BLACK,
                            SubtitleStyle.COLOR_WHITE,
                            SubtitleStyle.COLOR_GRAY,
                            SubtitleStyle.COLOR_YELLOW,
                        ),
                        selected = subtitleStyle.edgeColor,
                        onSelect = { color ->
                            settings.setSubtitleStyle(subtitleStyle.copy(edgeColor = color))
                        },
                        modifier = Modifier.padding(top = Spacing.videoTitleContentGap),
                    )
                    SubtitleStyleSliderRow(
                        title = "描边透明度",
                        value = subtitleStyle.edgeOpacity,
                        display = "${(subtitleStyle.edgeOpacity * 100).roundToInt()}%",
                        onChange = { opacity ->
                            settings.setSubtitleStyle(subtitleStyle.copy(edgeOpacity = opacity))
                        },
                    )
                }
                SideSheetSectionTitle(
                    text = "背景颜色",
                    modifier = Modifier.padding(top = Spacing.sectionSpacing),
                )
                SubtitleStyleChips(
                    labels = listOf("透明", "半透明", "黑色", "深灰"),
                    selectedIndex = when (subtitleStyle.backgroundColor) {
                        SubtitleStyle.BACKGROUND_DIM -> 1
                        SubtitleStyle.BACKGROUND_BLACK -> 2
                        SubtitleStyle.BACKGROUND_GRAY -> 3
                        else -> 0
                    },
                    onSelect = { index ->
                        val color = when (index) {
                            1 -> SubtitleStyle.BACKGROUND_DIM
                            2 -> SubtitleStyle.BACKGROUND_BLACK
                            3 -> SubtitleStyle.BACKGROUND_GRAY
                            else -> SubtitleStyle.COLOR_TRANSPARENT
                        }
                        settings.setSubtitleStyle(subtitleStyle.copy(backgroundColor = color))
                    },
                    modifier = Modifier.padding(top = Spacing.videoTitleContentGap),
                    fill = chipFill,
                    selectedTextColor = chipSelectedText,
                    unselectedTextColor = chipUnselectedText,
                )
                if (android.graphics.Color.alpha(subtitleStyle.backgroundColor) > 0) {
                    SubtitleStyleSliderRow(
                        title = "背景透明度",
                        value = subtitleStyle.backgroundOpacity,
                        display = "${(subtitleStyle.backgroundOpacity * 100).roundToInt()}%",
                        onChange = { opacity ->
                            settings.setSubtitleStyle(subtitleStyle.copy(backgroundOpacity = opacity))
                        },
                    )
                }
            }
            }
            if (subtitleTab == 1) {
            SideSheetSectionTitle("字幕大小")
            Text(
                text = "${(subtitleScale * 100).roundToInt()}%",
                fontSize = 20.sp,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.titleToValueGap),
            )
            Slider(
                value = subtitleScale,
                onValueChange = { raw ->
                    val atKeyPoint = abs(raw - 1f) < 0.02f
                    if (atKeyPoint && !scaleAtKeyPoint) {
                        // 抵达默认 100% 刻度点时使用系统触感反馈
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    scaleAtKeyPoint = atKeyPoint
                    settings.setSubtitleScale(raw)
                },
                valueRange = SUBTITLE_MIN_SCALE..SUBTITLE_MAX_SCALE,
                steps = ((SUBTITLE_MAX_SCALE - SUBTITLE_MIN_SCALE) / 0.05f).toInt() - 1,
                // 只在默认 100% 处显示刻度点，不做吸附
                showKeyPoints = true,
                keyPoints = listOf(1.0f),
                magnetThreshold = 0f,
                hapticEffect = SliderDefaults.SliderHapticEffect.None,
                modifier = Modifier.fillMaxWidth(),
            )
            SideSheetSectionTitle(
                text = "字幕位置（正值上移）",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sectionSpacing),
            )
            Text(
                text = String.format(
                    java.util.Locale.getDefault(),
                    "%+.0f%%",
                    subtitlePosition,
                ),
                fontSize = 20.sp,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.titleToValueGap),
            )
            Slider(
                value = subtitlePosition,
                onValueChange = { raw ->
                    val snapped = snapSubtitlePosition(raw)
                    val atKeyPoint = snapped == 0f
                    if (atKeyPoint && !positionAtKeyPoint) {
                        // 使用系统触感反馈，不自行定制震动时长/振幅
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    positionAtKeyPoint = atKeyPoint
                    settings.setSubtitlePosition(snapped)
                },
                valueRange = SUBTITLE_MIN_POSITION..SUBTITLE_MAX_POSITION,
                steps = (SUBTITLE_MAX_POSITION - SUBTITLE_MIN_POSITION).toInt() - 1,
                // 默认 0（居中）处显示刻度点，进入吸附区时吸住并震动
                showKeyPoints = true,
                keyPoints = listOf(0f),
                magnetThreshold = 0f,
                hapticEffect = SliderDefaults.SliderHapticEffect.None,
                modifier = Modifier.fillMaxWidth(),
            )
            }
            if (subtitleTab == 2) {
            SideSheetSectionTitle("字幕偏移")
            OffsetAdjustRow(
                offsetMs = playerState.subtitleOffsetMs,
                modifier = Modifier.padding(top = Spacing.videoTitleContentGap),
            )
            Text(
                text = "正数延后显示，负数提前显示，单位秒；仅对外挂字幕生效",
                fontSize = 12.sp,
                color = DarkOnSurfaceMuted,
                modifier = Modifier.padding(top = Spacing.videoTitleContentGap),
            )
            }
        }
    }
    }
}

/**
 * 字幕设置侧栏段落标题：15sp / Medium / 通栏；段内标题通过 modifier 传 sectionSpacing 顶部间距。
 * 字号比面板大标题（20sp）低两档但字重提一档，避免层级落差过大像辅助说明。
 */
@Composable
private fun SideSheetSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White.copy(alpha = SideSheetSectionTextAlpha),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 字幕样式 Tab 行（外观/调节/偏移）与「背景颜色」行：统一走独立胶囊 chip 行。
 */
@Composable
private fun SubtitleStyleChips(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Color? = null,
    selectedTextColor: Color? = null,
    unselectedTextColor: Color? = null,
) {
    // 面板内所有单选行统一走独立胶囊 chip 行，不再用连通外框的 SegmentedTabs
    SubtitleStyleChipRow(
        labels = labels,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
        fill = fill,
        selectedTextColor = selectedTextColor,
        unselectedTextColor = unselectedTextColor,
    )
}

/**
 * 字幕样式独立胶囊 chip 行：与线路/选集 [EpisodeChip] 同款外观（独立胶囊 + 圆角）；
 * 面板内 Tab（外观/调节/偏移）、系统/自定义、描边样式、背景颜色全部复用本行。
 */
@Composable
private fun SubtitleStyleChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Color? = null,
    selectedTextColor: Color? = null,
    unselectedTextColor: Color? = null,
) {
    // 等宽均分：同一面板里 3 个 Tab 行与 4 个「背景颜色」行左右端能对齐（按内容宽度排会参差不齐）
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.dialogItemGap),
    ) {
        labels.forEachIndexed { index, label ->
            EpisodeChip(
                title = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
                // 高度对齐选集 chip（episodeChipHeight 48dp），字号统一 14sp
                enforceMinHeight = true,
                fill = fill,
                selectedTextColor = selectedTextColor,
                unselectedTextColor = unselectedTextColor,
                fontSize = 14.sp,
            )
        }
    }
}

/**
 * 字幕颜色色板（仿 fongmi 的颜色 chip，改用圆点色块，选中加白圈）。
 */
@Composable
private fun SubtitleColorSwatches(
    colors: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 色点用 FlowRow 自动换行：侧栏最窄 280dp、除去边距只剩 248dp，
    // 10 个 24dp 色点 + 间距一行放不下，之前会溢出到面板外面
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { color ->
            val isSelected = color == selected
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                        shape = CircleShape,
                    )
                    .clickable { onSelect(color) },
            )
        }
    }
}

/**
 * 字幕样式里的百分比滑杆行（标题 + 当前值 + 滑杆），步进 5%。
 */
@Composable
private fun SubtitleStyleSliderRow(
    title: String,
    value: Float,
    display: String,
    onChange: (Float) -> Unit,
) {
    SideSheetSectionTitle(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sectionSpacing),
    )
    Text(
        text = display,
        fontSize = 20.sp,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.titleToValueGap),
    )
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = 0f..1f,
        steps = 19,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 字幕偏移调节行：−/+ 按钮按 100ms 步进，中间输入框可直接输入秒数。
 * 输入框失焦或点键盘完成时提交，非法输入恢复为当前值。
 */
@Composable
private fun OffsetAdjustRow(offsetMs: Long, modifier: Modifier = Modifier) {
    var text by remember(offsetMs) { mutableStateOf(formatSubtitleOffsetInput(offsetMs)) }
    val focusManager = LocalFocusManager.current
    var hadFocus by remember { mutableStateOf(false) }

    fun commit() {
        val seconds = text.trim().removeSuffix("s").toFloatOrNull()
        if (seconds == null) {
            text = formatSubtitleOffsetInput(offsetMs)
            return
        }
        val ms = (seconds * 1000).roundToLong()
            .coerceIn(-SUBTITLE_OFFSET_MAX_MS, SUBTITLE_OFFSET_MAX_MS)
        Players.setSubtitleOffset(ms)
        text = formatSubtitleOffsetInput(ms)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OffsetStepButton("−") {
            // 读取最新状态而非组合时快照：输入框先提交时也能基于新值步进
            Players.setSubtitleOffset(Players.state.value.subtitleOffsetMs - SUBTITLE_OFFSET_STEP_MS)
        }
        val inputShape = SquircleShape(Spacing.corner)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(inputShape)
                .border(1.dp, Color.White.copy(alpha = 0.15f), inputShape)
                .padding(horizontal = 12.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    text = raw.filter { it.isDigit() || it == '-' || it == '+' || it == '.' }.take(8)
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        commit()
                        focusManager.clearFocus()
                    },
                ),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        if (!state.isFocused && hadFocus) commit()
                        hadFocus = state.isFocused
                    }
                    .padding(vertical = 10.dp),
            )
            Text("s", fontSize = 12.sp, color = DarkOnSurfaceMuted)
        }
        OffsetStepButton("+") {
            Players.setSubtitleOffset(Players.state.value.subtitleOffsetMs + SUBTITLE_OFFSET_STEP_MS)
        }
    }
}

/** 字幕偏移输入框格式：+1.5 / -0.5（不带单位，单位固定显示在右侧） */
private fun formatSubtitleOffsetInput(offsetMs: Long): String {
    val seconds = if (abs(offsetMs) < 50) 0f else offsetMs / 1000f
    return String.format(java.util.Locale.getDefault(), "%+.1f", seconds)
}

/** 字幕位置吸附区：默认 0 附近 ±2% 内吸住 */
private const val SUBTITLE_POSITION_SNAP_DELTA = 2f

/** 字幕位置：默认点磁吸，其余按 1% 粒度取整 */
private fun snapSubtitlePosition(raw: Float): Float {
    if (abs(raw) <= SUBTITLE_POSITION_SNAP_DELTA) return 0f
    return raw.roundToInt().toFloat().coerceIn(SUBTITLE_MIN_POSITION, SUBTITLE_MAX_POSITION)
}

@Composable
private fun OffsetStepButton(text: String, onClick: () -> Unit) {
    val shape = SquircleShape(Spacing.corner)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 44.dp, height = 42.dp)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        Text(text, fontSize = 18.sp, color = Color.White)
    }
}

@Composable
private fun SubInfoRow(label: String, value: String) {
    val shape = SquircleShape(com.ican.tvplay.ui.theme.Spacing.corner)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surfaceContainer, shape)
            .padding(Spacing.dialogItemPadding),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SubRow(label: String, selected: Boolean, forceDark: Boolean = false, onClick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    val rowFill = if (forceDark) DarkDialogRow else scheme.secondaryVariant
    val rowText = if (forceDark) DarkDialogRowText else scheme.onSecondaryVariant
    val shape = SquircleShape(com.ican.tvplay.ui.theme.Spacing.corner)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            // 与线路/选集 chip 一致：选中 primary 主题色底 + onPrimary 自适应文字；未选中 secondaryVariant 灰底，无描边
            .background(
                if (selected) scheme.primary else rowFill, shape,
            )
            .padding(Spacing.dialogItemPadding),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) scheme.onPrimary else rowText, modifier = Modifier.weight(1f))
        if (selected) Icon(AppIcons.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
    }
}

// ========== 工具方法 ==========

/** 进度条行（时间 + 进度条 + 时长）：内部订阅独立进度流，
 *  播放中 500ms 一跳的进度刷新只重组本行，不再牵动整个控制层 */
@Composable
private fun RowScope.PlayerProgressRow(onDragFractionChange: (Float?) -> Unit) {
    val progress by Players.progress.collectAsStateWithLifecycle()
    Text(text = formatMs(progress.positionMs), style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"), color = Color.White)
    LineSlider(
        progress = if (progress.durationMs > 0) progress.positionMs.toFloat() / progress.durationMs else 0f,
        buffered = if (progress.durationMs > 0) progress.bufferedMs.toFloat() / progress.durationMs else 0f,
        onSeek = { Players.seekTo((it * Players.durationMs()).toLong()) },
        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        onDragFractionChange = onDragFractionChange,
    )
    Text(text = formatMs(progress.durationMs), style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"), color = Color.White)
}

/** 手势 seek 反馈浮层：快进/快退目标时间 + 拖动进度条「跳转至」提示（fongmi 同款，无胶囊底） */
@Composable
private fun BoxScope.SeekFeedbackOverlay(seekPreview: Long?, sliderFraction: Float?) {
    val progress by Players.progress.collectAsStateWithLifecycle()
    if (seekPreview != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
        ) {
            Icon(
                imageVector = if (seekPreview >= progress.positionMs) AppIcons.Forward else AppIcons.Rewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 宽度由透明占位文字锁定，数字逐帧变化时位置不跳动
            StableTimeText(
                text = formatMs(seekPreview),
                durationMs = progress.durationMs,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                color = Color.White,
            )
        }
    }
    if (sliderFraction != null && progress.durationMs > 0) {
        StableTimeText(
            text = "跳转至${formatMs((sliderFraction * progress.durationMs).toLong())}",
            durationMs = progress.durationMs,
            prefix = "跳转至",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
        )
    }
}

/**
 * 宽度稳定的时间文字：部分 OEM 定制字体会忽略 tnum，数字宽度随时间逐帧变化会让布局跳动。
 * 用「当前时长可能出现的最长时间串」透明占位撑开宽度，真实文字叠放其上居中。
 * 一次拖动会话内视频时长不变 → 占位宽度恒定，时间文字位置不随手指移动而变化。
 * 字体跟随系统默认（不指定 fontFamily），用户设置的系统字体会自动生效。
 */
@Composable
private fun StableTimeText(
    text: String,
    durationMs: Long,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = "",
) {
    val maxTime = if (durationMs >= 3_600_000L) "00:00:00" else "00:00"
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Text(text = "$prefix$maxTime", style = style, color = Color.Transparent)
        Text(text = text, style = style, color = color)
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** seek 后"保持目标进度"的释放阈值：播放器进度与目标差值 ≤ 该比例即视为已跟上 */
private const val SEEK_HOLD_TOLERANCE = 0.002f

/**
 * 细直线进度条：2dp 轨道 + 白色圆点滑块，支持点按与横向拖动 seek（小窗/全屏统一风格）。
 * [onDragFractionChange] 拖动开始/移动回调实时目标比例，拖动结束回 null（供全屏顶部「跳转至 MM:SS」提示）。
 */
@Composable
private fun LineSlider(
    progress: Float,
    buffered: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragFractionChange: ((Float?) -> Unit)? = null,
) {
    // 拖动中的临时进度（null=未拖动），松手才真正 seek，避免拖动过程中频繁跳转
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    // seek 已提交、但播放器进度尚未跟上时的目标进度：
    // 期间继续渲染该目标（否则 playState 还停在旧位置，滑块会先跳回原位置再跳到目标）
    var pendingSeek by remember { mutableStateOf<Float?>(null) }
    // 提交 seek：进入"保持目标进度"状态
    val commitSeek: (Float) -> Unit = { v ->
        val target = v.coerceIn(0f, 1f)
        pendingSeek = target
        onSeek(target)
    }
    // 播放器进度追上目标 → 释放保持值，交回播放器进度驱动
    LaunchedEffect(pendingSeek, progress) {
        val ps = pendingSeek ?: return@LaunchedEffect
        if (abs(progress - ps) <= SEEK_HOLD_TOLERANCE) pendingSeek = null
    }
    // 兜底：seek 迟迟未生效（如网络 seek 慢）也要释放，避免滑块被长期钉在目标位置
    LaunchedEffect(pendingSeek) {
        if (pendingSeek == null) return@LaunchedEffect
        delay(1200)
        pendingSeek = null
    }
    BoxWithConstraints(
        modifier = modifier
            // 对齐 fongmi DefaultTimeBar：触摸目标高 26dp、轨道条高 4dp
            .height(26.dp)
            // 单 pointerInput 统一处理 tap + drag（合并原因：detectTapGestures 会消费 down 事件，
            // 导致后注册的 detectHorizontalDragGestures 收到已消费 down 而 awaitTouchSlopOrCancellation 取消，
            // 进度条拖不动）。down 由本组件自行 consume：手势层收到已消费 down 即 gestureBlocked，
            // 不再误触发长按 2 倍速/横向 seek，无需依赖外层 Row 的屏蔽
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val downX = down.position.x
                    val slop = viewConfiguration.touchSlop
                    var dragging = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            change.consume() // 消费后续事件，手势层 gestureBlocked 全程让位
                            if (change.changedToUpIgnoreConsumed()) {
                                if (dragging) {
                                    dragProgress?.let(commitSeek)
                                } else {
                                    commitSeek(change.position.x / size.width)
                                }
                                break
                            }
                            val dx = change.position.x - downX
                            if (!dragging && abs(dx) >= slop) dragging = true
                            if (dragging) {
                                val f = (change.position.x / size.width).coerceIn(0f, 1f)
                                dragProgress = f
                                onDragFractionChange?.invoke(f)
                            }
                        }
                    } finally {
                        // 手势被打断（来电/切后台/弹窗覆盖）也要复位，避免滑块卡在拖动位置
                        dragProgress = null
                        onDragFractionChange?.invoke(null)
                    }
                }
            },
    ) {
        val p = (dragProgress ?: pendingSeek ?: progress).coerceIn(0f, 1f)
        val width = maxWidth
        // 背景轨道
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0x44FFFFFF), CircleShape),
        )
        // 缓冲进度轨道（fongmi 同款次级轨道）：能看出「已缓冲到哪里」，
        // 也是缓冲档位差异唯一可见的地方（档位只影响 ExoPlayer 的缓冲时长上限）
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(buffered.coerceIn(0f, 1f))
                .height(4.dp)
                .background(Color(0x99FFFFFF), CircleShape),
        )
        // 已播放轨道
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(p)
                .height(4.dp)
                .background(Color.White, CircleShape),
        )
        // 圆点滑块
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = width * p - 5.dp)
                .size(10.dp)
                .background(Color.White, CircleShape),
        )
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0X" else "${speed}X"

private const val RESUME_THRESHOLD_MS = 5_000L
private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L

/** 连播：一集播完后停顿多久再进下一集（留出最后一帧的观感） */
private const val AUTO_NEXT_DELAY_MS = 1_200L

/** 播放页内补拉完整详情的超时：避免慢/坏站源让骨架无限转圈 */
private const val DETAIL_TIMEOUT_MS = 15_000L

/** 首等超时后，复用同一在途详情请求的额外等待上限（慢站源多跳网盘冷启动可 >15s） */
private const val DETAIL_SLOW_TIMEOUT_MS = 45_000L

/** 控制层显隐淡入淡出时长（200ms，对齐 miuix 动画时长） */
private const val CONTROL_FADE_DURATION_MS = 200

/**
 * 侧边面板展开：挂载首帧就停在终点右侧 [SIDE_SHEET_ENTER_OFFSET] 处并同步淡入，
 * 时长与控制栏淡出同为 200ms —— 点按钮时面板和控制栏同时开始变化，不再“控制栏先没、弹窗后到”。
 */
private val SIDE_SHEET_ENTER_OFFSET = 56.dp
private const val SIDE_SHEET_ENTER_MS = 200

/** 侧边面板收起：滑出到屏外 + 淡出的时长 */
private const val SIDE_SHEET_EXIT_MS = 260

/**
 * 双击判定窗口：系统默认 300ms（ViewConfiguration.doubleTapTimeoutMillis），
 * 这里缩短到 200ms 以减小单击唤出控制层的延迟；
 * 代价是双击两次点击必须落在 200ms 内，稍慢会被判成两次单击。
 */
private const val DOUBLE_TAP_TIMEOUT_MS = 200L

// ========== 视频加载失败全屏错误态 ==========

/**
 * 判断解析出来的播放地址是否只有 BT/磁力一种形态（magnet: / ed2k: / thunder://）。
 *
 * 这些地址不是可直接拉流的 HTTP 资源，fongmi 靠外挂的 Thunder 插件（闭源动态模块）
 * 先下载再做本地 http 服务；本项目没有 BT 引擎，送入 MPV 只会卡在黑屏，
 * 因此由播放页直接给提示。
 */
private fun isTorrentOnlyLink(url: String): Boolean {
    val u = url.trim().lowercase()
    return u.startsWith("magnet:") || u.startsWith("ed2k:") || u.startsWith("thunder:")
}

/**
 * 当 VideoRepository.getVideo + Players.consumeCachedVideo 都查不到时，
 * 渲染这个全屏错误界面替代残缺的 PortraitPlayLayout（纯白底+只有控制按钮）。
 */
@Composable
private fun PlayerErrorScreen(
    playerError: String?,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.error,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = "视频加载失败",
                style = MaterialTheme.typography.titleLarge,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Text(
                text = playerError ?: "请求超时，返回重试一下吧",
                style = MaterialTheme.typography.bodyMedium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MiuixTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onBack() }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "返回",
                    style = MaterialTheme.typography.labelLarge,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

private const val HIDE_DELAY_MS = 5_000L

/** 网盘类站源选集标题的体积前缀：[2.8GB] / 【512MB】等，可连续出现多个，全屏标题展示前剔除 */
private val EPISODE_SIZE_PREFIX =
    Regex("""^\s*(?:[\[【]\s*[\d.]+\s*(?i:GB|MB|TB|KB)\s*[\]】]\s*)+""")

private fun cleanEpisodeTitle(title: String): String =
    title.replace(EPISODE_SIZE_PREFIX, "").trim()

/** 单次播放会话内自动兜底（换线路/换站源）的总次数上限，防止沿站源链无限解析 */
private const val MAX_AUTO_FALLBACK = 2

/** 在 PlayerView 的视图树里找 TextureView（surface_type=texture_view 时由 media3 创建） */
private fun findTextureView(root: android.view.View?): android.view.TextureView? {
    if (root == null) return null
    if (root is android.view.TextureView) return root
    if (root is android.view.ViewGroup) {
        for (i in 0 until root.childCount) {
            val found = findTextureView(root.getChildAt(i))
            if (found != null) return found
        }
    }
    return null
}

/**
 * 把 TextureView 的整帧裁成「片源内容矩形」（media3 FIT 之后真正有画面的那部分），
 * 静帧替身才能直接按 aspectRatio(片源比例) 摆放，和真实视频层几何完全重合。
 */
private fun cropToContent(
    src: android.graphics.Bitmap,
    ratio: Float,
): android.graphics.Bitmap {
    if (ratio <= 0f || src.width <= 0 || src.height <= 0) return src
    val w = src.width.toFloat()
    val h = src.height.toFloat()
    val contentW = if (w / h > ratio) h * ratio else w
    val contentH = contentW / ratio
    val left = ((w - contentW) / 2f).toInt().coerceIn(0, src.width - 1)
    val top = ((h - contentH) / 2f).toInt().coerceIn(0, src.height - 1)
    val cw = contentW.toInt().coerceIn(1, src.width - left)
    val ch = contentH.toInt().coerceIn(1, src.height - top)
    if (cw >= src.width && ch >= src.height) return src
    return runCatching { android.graphics.Bitmap.createBitmap(src, left, top, cw, ch) }
        .getOrDefault(src)
}
