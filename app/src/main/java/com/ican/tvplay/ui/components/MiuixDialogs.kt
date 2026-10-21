package com.ican.tvplay.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ican.tvplay.ui.theme.PopupScrim
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全 App 黑色遮罩渐显渐隐时长（毫秒），唯一取值：
 * 全局弹窗遮罩、播放页加载蒙层、错误浮层、平台 Dialog 窗口动画统一 200ms。
 * res/anim/scrim_fade_in.xml / scrim_fade_out.xml 的 android:duration 必须与本常量保持一致。
 */
const val ScrimFadeMillis = 200

/**
 * 统一遮罩层：PopupScrim（黑色 40%）+ 固定 [ScrimFadeMillis] 淡入淡出。
 * 所有自绘遮罩（全局 ScrimHost、全屏侧边面板、加载蒙层等）一律调用本组件，
 * 禁止再各自手写 alpha/background(PopupScrim)/动画时长。
 *
 * - [visible] 期间拦截触摸并回调 [onClick]（如点击遮罩关闭）；隐藏过程及隐藏后不拦截。
 * - [content] 居中于遮罩之上（如加载转圈）。
 */
@Composable
fun FadingScrim(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit = {},
) {
    // 透明度唯一收口：tween(ScrimFadeMillis) + FastOutSlowInEasing
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = ScrimFadeMillis, easing = FastOutSlowInEasing),
        label = "fadingScrim",
    )
    // 初始即隐藏时直接不组合，避免透明层常驻占位
    if (scrimAlpha == 0f && !visible) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(scrimAlpha)
            .background(PopupScrim)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = visible,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * 全局遮罩宿主状态：弹窗通过 [LocalScrimHost] 申请/撤销遮罩。
 * 支持多个弹窗叠加计数，点遮罩只回调最上层可见申请（实际同时只会有一个）。
 */
class ScrimHostState {
    private data class Entry(
        val id: Any,
        val visible: Boolean,
        val onDismiss: () -> Unit,
    )

    private val entries = mutableStateListOf<Entry>()

    /** 任一申请可见即显示遮罩，供 [ScrimHost] 订阅 */
    val visible: Boolean get() = entries.any { it.visible }

    fun request(id: Any, visible: Boolean, onDismiss: () -> Unit) {
        val index = entries.indexOfFirst { it.id == id }
        if (index >= 0) entries[index] = Entry(id, visible, onDismiss) else entries += Entry(id, visible, onDismiss)
    }

    fun remove(id: Any) {
        entries.removeAll { it.id == id }
    }

    fun dismissTop() {
        entries.lastOrNull { it.visible }?.onDismiss?.invoke()
    }
}

/** 全局遮罩宿主，由 AppRoot 在 Scaffold 内容层末尾提供 */
val LocalScrimHost = staticCompositionLocalOf<ScrimHostState> {
    error("ScrimHostState 未提供：AnimatedOverlayDialog 必须在 AppRoot 的 LocalScrimHost 之内调用")
}

/** 全局遮罩渲染层：放在根 Scaffold 内容的最后一个子级，层级高于页面与底栏、低于 miuix 弹窗卡片 */
@Composable
fun ScrimHost(state: ScrimHostState, modifier: Modifier = Modifier) {
    FadingScrim(
        visible = state.visible,
        modifier = modifier,
        onClick = state::dismissTop,
    )
}

/**
 * 带统一渐显渐隐遮罩的 miuix [OverlayDialog]。
 *
 * 背景：miuix-ui 0.9.4 的 OverlayDialog 内部写死 Enter/ExitTransition.None，
 * 自带 windowDim 遮罩瞬现瞬消（反编译字节码确认）。本 wrapper：
 * 1. enableWindowDim=false 关闭库自带瞬现遮罩；
 * 2. 向全局 [ScrimHostState] 申请 FadingScrim，显隐均 200ms 淡入淡出；
 * 3. 关闭时先让遮罩渐隐 [ScrimFadeMillis] 毫秒，再移除弹窗卡片（此时遮罩已全透明，移除不可见）。
 *
 * 卡片外观参数与 miuix OverlayDialog 完全一致，默认值直接引用 [DialogDefaults]，零漂移；
 * 强制 renderInRootScaffold=true：卡片渲染在根 Scaffold 宿主层，保证卡片始终位于全局遮罩之上。
 * [largeScreen] 为 null 时沿用 miuix 默认判定（容器宽 ≥840dp 且高 ≥480dp 居中）。
 */
@Composable
fun AnimatedOverlayDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
    titleColor: Color = DialogDefaults.titleColor(),
    backgroundColor: Color = DialogDefaults.backgroundColor(),
    outsideMargin: DpSize = DialogDefaults.outsideMargin,
    insideMargin: DpSize = DialogDefaults.insideMargin,
    maxWidth: Dp = DialogDefaults.MaxWidth,
    largeScreen: Boolean? = null,
    cornerRadius: Dp = DialogDefaults.CornerRadius,
    content: @Composable () -> Unit,
) {
    val host = LocalScrimHost.current
    val scrimId = remember { Any() }
    // 卡片比遮罩多驻留 200ms：关闭时遮罩先渐隐，再移除卡片
    var cardVisible by remember { mutableStateOf(false) }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)

    // miuix 默认大屏判定（DialogDefaults.isLargeScreen）：容器宽 ≥840dp 且高 ≥480dp
    val containerSize = LocalWindowInfo.current.containerDpSize
    val resolvedLargeScreen = largeScreen
        ?: (containerSize.width >= 840.dp && containerSize.height >= 480.dp)

    LaunchedEffect(show) {
        if (show) cardVisible = true
    }
    LaunchedEffect(cardVisible, show) {
        if (!cardVisible) return@LaunchedEffect
        host.request(scrimId, visible = show) { currentOnDismiss() }
        if (!show) {
            delay(ScrimFadeMillis.toLong())
            host.remove(scrimId)
            cardVisible = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { host.remove(scrimId) }
    }

    if (cardVisible) {
        OverlayDialog(
            show = true,
            modifier = modifier,
            title = title,
            titleColor = titleColor,
            backgroundColor = backgroundColor,
            // 库自带遮罩瞬现瞬消，关闭后统一走全局 FadingScrim
            enableWindowDim = false,
            onDismissRequest = { currentOnDismiss() },
            outsideMargin = outsideMargin,
            insideMargin = insideMargin,
            renderInRootScaffold = true,
            maxWidth = maxWidth,
            largeScreen = resolvedLargeScreen,
            cornerRadius = cornerRadius,
            content = { content() },
        )
    }
}

/**
 * 底部弹窗三边距统一方案：底部 = max(16dp, 系统导航栏高度)。
 * outsideMargin 高度叠加在弹窗宿主的手势条避让之上，故此处补偿「16dp − 系统导航栏高度」：
 * 手势导航设备（避让 < 16dp）补足差额后底边距精确 16dp，与左右一致；
 * 三键导航设备（避让 48dp）补偿为 0，弹窗顶住系统栏——这是物理下限。
 * 全部底部弹窗统一调用本函数，勿单独写死。
 */
@Composable
internal fun dialogBottomMargin(): Dp = with(LocalDensity.current) {
    (16.dp - WindowInsets.navigationBars.getBottom(this).toDp()).coerceAtLeast(0.dp)
}

/**
 * 全局统一确认弹窗：照搬 KernelSU ConfirmDialogMiuix（miuix WindowDialog + Text + 双 TextButton）。
 * 取消在左（默认色）、确定在右（主题色）；[danger] = true 时确定钮为红色（删除等破坏性操作）。
 */
@Composable
fun MiuixConfirmDialog(
    show: Boolean,
    title: String,
    content: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 走 AnimatedOverlayDialog：遮罩与全 App 统一（PopupScrim + 200ms 淡入淡出），
    // 卡片仍为 miuix OverlayDialog 原外观
    AnimatedOverlayDialog(
        show = show,
        title = title,
        titleColor = MiuixTheme.colorScheme.onBackground,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
        // 三边统一 16dp：底部 = max(16dp, 系统导航栏高度)，跨设备自适应
        outsideMargin = DpSize(16.dp, dialogBottomMargin()),
        // 强制按「非大屏」渲染：miuix 默认在宽窗口（平板横/竖屏）把弹窗改成屏幕居中，
        // 与手机端「从底部呼出」不一致；固定 false 让平板也保持底部对齐
        largeScreen = false,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = content, color = MiuixTheme.colorScheme.onBackground)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                TextButton(
                    text = dismissText,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = if (danger) {
                        ButtonDefaults.textButtonColorsPrimary(
                            color = Color(0xFFFF5C5C),
                            textColor = Color.White,
                        )
                    } else {
                        ButtonDefaults.textButtonColorsPrimary()
                    },
                )
            }
        }
    }
}
