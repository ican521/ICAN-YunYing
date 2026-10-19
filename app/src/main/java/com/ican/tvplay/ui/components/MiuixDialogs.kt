package com.ican.tvplay.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    // 用 OverlayDialog（App 内覆盖层渲染）而非 WindowDialog（独立平台窗口）：
    // 与下拉弹窗同一条 miuix 覆盖层管线，遮罩颜色/透明度/动画天然一致
    OverlayDialog(
        show = show,
        title = title,
        titleColor = MiuixTheme.colorScheme.onBackground,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
        // 渲染到 AppRoot 根宿主：弹窗+遮罩层级高于底栏
        renderInRootScaffold = true,
        // 三边统一 16dp：底部 = max(16dp, 系统导航栏高度)，跨设备自适应
        outsideMargin = DpSize(16.dp, dialogBottomMargin()),
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
