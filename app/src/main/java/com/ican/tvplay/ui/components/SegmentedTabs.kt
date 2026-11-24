package com.ican.tvplay.ui.components

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分段选择按钮（对齐 fongmi 的 MaterialButtonToggleGroup）：
 * 整条连通（只有首尾圆角），段间 1dp 分隔线；选中段主题色填充 + 白字，未选中透明底。
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
    // 选中态不用主题色（深色弹窗里太跳）：用主题内的次级容器色（深色=深灰、浅色=浅灰），
    // 只靠明度差区分，不引入任何色相，深浅两套主题都成立
    val selectedFill = MiuixTheme.colorScheme.secondaryVariant
    // 外框与段间分隔线同色同宽（照 fongmi PlaybackDialogTabs 的 M3 outlined 分段按钮）
    val outline = MiuixTheme.colorScheme.outline
    val rowModifier = if (fillWidth) modifier.fillMaxWidth() else modifier
    Row(
        modifier = rowModifier
            .height(height)
            .clip(shade)
            .border(1.dp, outline, shade),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(outline),
                )
            }
            val selected = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = (if (fillWidth) Modifier.weight(1f) else Modifier)
                    .fillMaxHeight()
                    .background(if (selected) selectedFill else Color.Transparent)
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
                    color = if (selected) {
                        MiuixTheme.colorScheme.onSurface
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
}
