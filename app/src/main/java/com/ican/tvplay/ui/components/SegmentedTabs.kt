package com.ican.tvplay.ui.components

import androidx.compose.foundation.background
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
    val shape = RoundedCornerShape(percent = 50)
    val accent = MiuixTheme.colorScheme.primary
    val rowModifier = if (fillWidth) modifier.fillMaxWidth() else modifier
    Row(
        modifier = rowModifier
            .height(height)
            .clip(shape)
            // 外框不用主题色描边（深色底上像一圈蓝光），只留极淡中性底把整条区分出来
            .background(Color.White.copy(alpha = 0.06f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.12f)),
                )
            }
            val selected = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = (if (fillWidth) Modifier.weight(1f) else Modifier)
                    .fillMaxHeight()
                    .background(if (selected) accent else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) }
                    .padding(horizontal = horizontalPadding),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    color = if (selected) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
