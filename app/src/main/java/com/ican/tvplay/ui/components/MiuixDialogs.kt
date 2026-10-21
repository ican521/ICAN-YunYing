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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ican.tvplay.ui.theme.PopupScrim
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全 App 黑色遮罩渐显渐隐时长（毫秒）。
 * 用于播放页自绘遮罩（详情加载蒙层、全屏选集侧边面板）。
 * 说明：弹窗（miuix OverlayDialog）遮罩走库自带 windowDim，为瞬现瞬消，不使用本常量。
 */
const val ScrimFadeMillis = 200

/**
 * 统一遮罩层：PopupScrim（黑色 40%）+ 固定 [ScrimFadeMillis] 淡入淡出。
 * 所有页面内自绘遮罩（全屏侧边面板、详情加载蒙层等）一律调用本组件，
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
    // 直接使用 miuix OverlayDialog：遮罩为库自带 windowDim（与 URL 播放弹窗一致）
    OverlayDialog(
        show = show,
        title = title,
        titleColor = MiuixTheme.colorScheme.onBackground,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
        // 渲染到 AppRoot 根宿主：弹窗+遮罩层级高于底栏
        renderInRootScaffold = true,
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
