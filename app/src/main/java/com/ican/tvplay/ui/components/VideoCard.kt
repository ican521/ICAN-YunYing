package com.ican.tvplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.ui.theme.Spacing
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * 首页 / 搜索 / 收藏复用的视频卡片（海报 + 评分角标 + 标题）。
 * 交互统一走 [tvCardEffect]：焦点放大、按压渐变、无水波纹。
 */
@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
) {
    Column(
        modifier = modifier
            .width(width)
            .tvCardEffect(onClick = onClick, shape = RoundedCornerShape(Spacing.corner)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .height(width * 3f / 2f),
        ) {
            AsyncImage(
                model = video.cover,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(width * 3f / 2f),
            )

            // 底部渐变，保证标题可读
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC000000)),
                        ),
                    ),
            )

            // 左上角：年份胶囊（与右上角评分胶囊同款，顶部对齐）
            Text(
                text = "${video.year}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color(0x66000000), RoundedCornerShape(Spacing.corner))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )

            // 右上角：评分胶囊
            Text(
                text = video.rating,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0x66000000), RoundedCornerShape(Spacing.corner))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )

            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}
