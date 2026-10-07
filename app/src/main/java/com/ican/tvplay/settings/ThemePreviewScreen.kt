package com.ican.tvplay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.VideoCard
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.nav.topDestinations

/** 主题预览页：当前配色色块 + 视频卡片 + 底部导航样例 */
@Composable
fun ThemePreviewScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = AppIcons.Back,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(34.dp)
                    .tvCardEffect(
                        onClick = onBack,
                        shape = CircleShape,
                        focusedScale = 1.1f,
                        glow = false,
                    )
                    .padding(5.dp),
            )
            Text(
                text = "主题预览",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // 当前主题色块
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "当前配色",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorSwatch("primary", scheme.primary, Modifier.weight(1f))
                ColorSwatch("secondary", scheme.secondary, Modifier.weight(1f))
                ColorSwatch("tertiary", scheme.tertiary, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorSwatch("surface", scheme.surface, Modifier.weight(1f))
                ColorSwatch("surfaceVariant", scheme.surfaceVariant, Modifier.weight(1f))
                ColorSwatch("background", scheme.background, Modifier.weight(1f))
            }
        }

        // 视频卡片样例
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "视频卡片",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            VideoCard(
                video = Video(
                    id = "preview",
                    title = "主题预览示例影片",
                    cover = "",
                    categoryId = "preview",
                    categoryName = "示例",
                    year = 2026,
                    rating = "9.9",
                    region = "演示",
                    description = "",
                    tags = emptyList(),
                    episodes = emptyList(),
                ),
                onClick = {},
            )
        }

        // 底部导航样例（静态仿制，不接 backdrop）
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "底部导航",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.surface, RoundedCornerShape(28.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                topDestinations.forEachIndexed { index, destination ->
                    val selected = index == 0
                    val itemColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) scheme.primary else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            tint = itemColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = destination.label,
                            fontSize = 11.sp,
                            color = itemColor,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(name: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .size(height = 44.dp, width = 200.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
