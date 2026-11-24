package com.ican.tvplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分段选择按钮（对齐 fongmi 的 MaterialButtonToggleGroup + Widget.Material3.Button.OutlinedButton）：
 * 整条是一个连通的外框（只在首尾圆角），段与段之间 1dp 分隔线；
 * 选中段用主题色填充 + onPrimary 文字，未选中透明底 + 主题色描边。
 *
 * 播放页字幕设置与设置页「收藏同步」共用同一套样式。
 */
@Composable
fun SegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
) {
    val shape = RoundedCornerShape(20.dp)
    val accent = MiuixTheme.colorScheme.primary
    val rowModifier = if (fillWidth) modifier.fillMaxWidth() else modifier
    Row(
        modifier = rowModifier
            .height(38.dp)
            .clip(shape)
            .border(1.dp, accent, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                // 段间分隔线：与描边同色，连通感来自容器外框
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(accent.copy(alpha = 0.6f)),
                )
            }
            val selected = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = (if (fillWidth) Modifier.weight(1f) else Modifier)
                    .fillMaxHeight()
                    .background(if (selected) accent else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) }
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    color = if (selected) {
                        androidx.compose.ui.graphics.Color.White
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
}
