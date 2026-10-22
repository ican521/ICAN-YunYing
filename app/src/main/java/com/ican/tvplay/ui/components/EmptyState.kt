package com.ican.tvplay.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * 空页面占位：上方颜文字、下方「空空如也」。
 * 两行放在同一 Column 并以 [Alignment.CenterHorizontally] 对齐，
 * 保证四个字在颜文字正下方居中（而不是跟着左缘对齐）。
 */
@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "¯\\_(ツ)_/¯",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                fontSize = 24.sp,
                lineHeight = 30.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "空空如也",
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
