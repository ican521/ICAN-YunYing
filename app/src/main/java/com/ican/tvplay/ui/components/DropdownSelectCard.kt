package com.ican.tvplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.ui.theme.Spacing
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup

/**
 * KernelSU 风格下拉选择卡（全局通用）。
 * 整卡可点击，右侧显示当前值 + 上下箭头；点击后全屏渐暗 + 选项弹窗展开。
 *
 * 弹窗直接使用 miuix 原生 OverlayDropdownPopup——即 KernelSU OverlayDropdownPreference
 * 内部调用的同一组件（miuix-preference popup/OverlayDropdownPopup.kt）：
 * 弹窗宿主（App 同窗口全屏覆盖层 MiuixPopupHost）、渐暗遮罩（windowDimming 30%/60%）、
 * 定位（dropdownPositionProvider：End 对齐锚点右缘、下方 8dp 优先、上方/居中回退、
 * 安全区收拢）、宽度测量（[200dp, 288dp]）、进出场动画（spring 0.82/362.5 +
 * alpha 200/150ms + dim 300/150ms SinOutEasing）、条目样式（DropdownImpl）全部为
 * miuix 源码原实现，不再手写复刻。
 *
 * 结构对齐 KernelSU：OverlayDropdownPopup 放在「当前值 + 上下箭头」所在行内
 * （KernelSU 中位于 BasicComponent endActions Row），miuix 以该行的窗口边界为锚点——
 * 弹窗右缘对齐值行右缘、顶部为值行底部 + 8dp。
 */
@Composable
fun DropdownSelectCard(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    var show by remember { mutableStateOf(false) }
    // 弹窗存活期（含退出动画）冻结卡片焦点缩放：miuix 锚点取自行内 Spacer 的实时窗口坐标，
    // 卡片一旦播放 1→1.02 弹簧缩放，弹窗会跟随锚点连续位移（表现为选项跳动）。
    // KernelSU 的 BasicComponent 无缩放，靠 onDismissFinished 覆盖整个进出场周期保持锚点静止。
    var anchorFrozen by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val shape = RoundedCornerShape(Spacing.corner)

    // KernelSU OverlayDropdownPreference 同款 entry 构造（miuix preference/OverlayDropdownPreference.kt）
    val entry = remember(options, selectedIndex, onSelect) {
        DropdownEntry(
            options.mapIndexed { index, option ->
                DropdownItem(
                    text = option,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .tvCardEffect(
                onClick = {
                    // KernelSU handleClick：展开时触感反馈（HapticFeedbackType.ContextClick）
                    val expanding = !show
                    show = expanding
                    if (expanding) {
                        anchorFrozen = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                    }
                },
                shape = shape,
                focusedScale = 1.02f,
                freezeScale = anchorFrozen,
                glow = false,
            )
            .background(MaterialTheme.colorScheme.surface, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.titleContentGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // KernelSU BasicComponent endActions 结构：值文字 + 上下箭头 + OverlayDropdownPopup
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = options.getOrElse(selectedIndex) { "" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = AppIcons.Unfold,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                OverlayDropdownPopup(
                    entry = entry,
                    show = show,
                    onDismiss = { show = false },
                    onDismissFinished = { anchorFrozen = false },
                    maxHeight = null,
                    dropdownColors = DropdownDefaults.dropdownColors(),
                    renderInRootScaffold = true,
                    collapseOnSelection = true,
                )
            }
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
