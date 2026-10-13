package com.ican.tvplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.ui.theme.DarkSurfaceElevated
import com.ican.tvplay.ui.theme.LocalDarkTheme
import com.ican.tvplay.ui.theme.PopupScrim
import com.ican.tvplay.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * KernelSU 风格下拉选择卡（全局通用）：
 * - 整卡可点击，右侧显示当前值 + 上下箭头；
 * - 点击后全屏黑色遮罩（40% 黑，点击遮罩任意处关闭）；
 * - 选项弹窗右上角精确对齐到箭头正下方，选中项为主题色文字 + 对勾；
 * - 深色模式弹窗用抬升色（无阴影），浅色模式白色 + 阴影。
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
    var expanded by remember { mutableStateOf(false) }
    val isDark = LocalDarkTheme.current
    val shape = RoundedCornerShape(Spacing.corner)
    // 卡片在窗口中的位置（boundsInWindow 与弹窗内容坐标系一致），用于确定性定位弹窗
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }
    // 弹窗遮罩层尺寸（即窗口尺寸）
    var scrimSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { anchorBounds = it.boundsInWindow() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .tvCardEffect(
                    onClick = { expanded = true },
                    shape = shape,
                    focusedScale = 1.02f,
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

    if (expanded) {
        Popup(
            // 遮罩层铺满整个窗口，固定锚在窗口原点
            popupPositionProvider = remember {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset = IntOffset.Zero
                }
            },
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PopupScrim)
                    .onSizeChanged { scrimSize = it }
                    .pointerInput(Unit) {
                        // 点击遮罩任意处关闭（与 KernelSU 一致）
                        detectTapGestures { expanded = false }
                    },
            ) {
                // 等拿到窗口尺寸再显示菜单，避免首帧位置闪跳
                if (scrimSize != IntSize.Zero && anchorBounds != Rect.Zero) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset {
                                IntOffset(
                                    // 右缘对齐：窗口右缘 → 卡片右缘 → 再左移 18dp（卡片内容区内边距）= 箭头正下方
                                    -((scrimSize.width - anchorBounds.right.toInt()) +
                                        with(this) { 18.dp.roundToPx() }),
                                    // 顶部对齐：卡片底部 + 8dp 间距
                                    anchorBounds.bottom.roundToInt() + with(this) { 8.dp.roundToPx() },
                                )
                            }
                            .width(IntrinsicSize.Max)
                            // 深色模式隐藏所有阴影（全局规范），浅色模式带投影
                            .then(
                                if (!isDark) {
                                    Modifier.shadow(elevation = 6.dp, shape = shape, clip = false)
                                } else {
                                    Modifier
                                },
                            )
                            // 深色模式用抬升色区分卡片层级（KernelSU 同款）
                            .background(
                                if (isDark) DarkSurfaceElevated else MaterialTheme.colorScheme.surface,
                                shape,
                            )
                            .padding(vertical = 6.dp),
                    ) {
                        options.forEachIndexed { index, option ->
                            val selected = index == selectedIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvCardEffect(
                                        onClick = {
                                            expanded = false
                                            if (!selected) onSelect(index)
                                        },
                                        shape = shape,
                                        focusedScale = 1f,
                                        glow = false,
                                    )
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = AppIcons.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
