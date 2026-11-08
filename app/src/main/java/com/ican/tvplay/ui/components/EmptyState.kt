package com.ican.tvplay.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ican.tvplay.ui.theme.Spacing
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 空页面占位：只显示一行与页面相关的提示文字，居中排版。
 * [text] 由各页面传入，不再统一显示颜文字/“空空如也”。
 * [action] 可选：在文案下方追加一个操作入口（如「重新加载」）。
 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.82f),
                fontSize = 18.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                // 空态文案统一保持单行
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (action != null) {
                Box(modifier = Modifier.padding(top = Spacing.sectionSpacing)) {
                    action()
                }
            }
        }
    }
}
