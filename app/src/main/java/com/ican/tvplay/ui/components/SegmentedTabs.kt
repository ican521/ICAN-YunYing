package com.ican.tvplay.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ican.tvplay.ui.theme.SegmentedTabsFadeMs
import com.ican.tvplay.ui.theme.SegmentedTabsOutlineAlpha
import com.ican.tvplay.ui.theme.chipTintedFill
import com.ican.tvplay.ui.theme.chipTintedText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分段选择按钮（对齐 fongmi 的 MaterialButtonToggleGroup）：
 * 整条连通（只有首尾圆角），段间 1dp 分隔线；选中段用 primary 派生 tinted 底 + 派生文字色，
 * 未选中透明底 + onSurfaceVariantSummary；选中切换为 150ms 纯淡化（无滑块位移）。
 *
 * 播放页字幕设置与设置页「收藏同步」共用同一套样式；
 * [compact] 用于弹窗里的操作行（收藏同步），比播放页的字幕 Tab 矮一档。
 */
@Composable
fun SegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    compact: Boolean = false,
) {
    val height = if (compact) 26.dp else 38.dp
    val horizontalPadding = if (compact) 10.dp else 12.dp
    val shade = RoundedCornerShape(percent = 50)
    val primary = MiuixTheme.colorScheme.primary
    // 深浅按容器底色明度判定（字幕侧栏强制套深色主题，不能用系统深浅）
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    // 外框与段间分隔线同色同宽（1dp）：深色叠白、浅色叠黑
    val outlineColor = if (dark) Color.White.copy(alpha = SegmentedTabsOutlineAlpha)
        else Color.Black.copy(alpha = SegmentedTabsOutlineAlpha)
    val tintedFill = remember(primary, dark) { chipTintedFill(primary, dark) }
    val tintedText = remember(primary, dark) { chipTintedText(primary, dark) }
    val rowModifier = if (fillWidth) modifier.fillMaxWidth() else modifier
    Row(
        modifier = rowModifier
            .height(height)
            .clip(shade)
            .border(1.dp, outlineColor, shade),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(outlineColor),
                )
            }
            val selected = index == selectedIndex
            val animatedFill by animateColorAsState(
                targetValue = if (selected) tintedFill else tintedFill.copy(alpha = 0f),
                animationSpec = tween(SegmentedTabsFadeMs, easing = FastOutSlowInEasing),
                label = "SegmentedTabsFill",
            )
            val animatedText by animateColorAsState(
                targetValue = if (selected) tintedText else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                animationSpec = tween(SegmentedTabsFadeMs, easing = FastOutSlowInEasing),
                label = "SegmentedTabsText",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = (if (fillWidth) Modifier.weight(1f) else Modifier)
                    .fillMaxHeight()
                    .background(animatedFill)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) }
                    .padding(horizontal = if (fillWidth) 8.dp else horizontalPadding),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = animatedText,
                )
            }
        }
    }
}
