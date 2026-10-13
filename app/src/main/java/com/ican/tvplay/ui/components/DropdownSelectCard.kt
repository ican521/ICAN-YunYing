package com.ican.tvplay.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
 * - 点击后全屏黑色遮罩渐显（点击遮罩任意处渐隐关闭）；
 * - 选项菜单右上角精确对齐到箭头正下方（卡片底部 + 8dp），带缩放+渐显动画；
 * - 深色模式菜单用抬升色（无阴影），浅色模式白色 + 阴影。
 *
 * 用 Dialog 全屏层实现（独立窗口覆盖在页面上方），不触碰底层页面窗口，
 * 保证打开/关闭时页面零跳动、菜单定位坐标恒定。
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
    var closing by remember { mutableStateOf(false) }
    val isDark = LocalDarkTheme.current
    val shape = RoundedCornerShape(Spacing.corner)
    // 卡片在窗口中的位置（boundsInWindow 与 Dialog 全屏层坐标系一致），用于菜单定位
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }

    fun requestClose() {
        if (!closing) closing = true
    }

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
        // 遮罩与菜单共用一条进度：180ms 渐显、160ms 渐隐（KernelSU 同款节奏）
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            progress.animateTo(1f, tween(durationMillis = 180))
        }
        LaunchedEffect(closing) {
            if (closing) {
                progress.animateTo(0f, tween(durationMillis = 160))
                expanded = false
                closing = false
            }
        }

        Dialog(
            onDismissRequest = { requestClose() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
            ),
        ) {
            var menuSize by remember { mutableStateOf(IntSize.Zero) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PopupScrim.copy(alpha = PopupScrim.alpha * progress.value))
                    .pointerInput(Unit) {
                        // 点击遮罩任意处关闭（与 KernelSU 一致）
                        detectTapGestures { requestClose() }
                    },
            ) {
                if (anchorBounds != Rect.Zero) {
                    Column(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    // 右缘：卡片右缘内收 18dp（卡片内容区内边距）= 箭头正下方
                                    (anchorBounds.right.roundToInt() - with(this) { 18.dp.roundToPx() } - menuSize.width)
                                        .coerceAtLeast(0),
                                    // 顶部：卡片底部 + 8dp 间距
                                    anchorBounds.bottom.roundToInt() + with(this) { 8.dp.roundToPx() },
                                )
                            }
                            .onSizeChanged { menuSize = it }
                            .graphicsLayer {
                                // 菜单随遮罩进度渐显 + 从右上角锚点轻微放大
                                alpha = progress.value
                                val scale = 0.92f + 0.08f * progress.value
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(1f, 0f)
                            }
                            // 深色模式下隐藏所有阴影（全局规范）
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
                                            requestClose()
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
