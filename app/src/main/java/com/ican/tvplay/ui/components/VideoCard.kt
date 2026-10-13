package com.ican.tvplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

/**
 * 首页 / 搜索 / 收藏复用的视频卡片（海报 + 评分角标 + 标题）。
 *
 * 容器为 miuix Card（insideMargin=0，16dp 统一圆角：API 33+ 为 squircle shader 裁剪，
 * 低版本自动回退 RoundedCornerShape；底色由 Card colors 提供，兼作海报加载占位色）。
 *
 * 交互分两层，与 miuix Card 可点版内部结构同构：
 * - [tvCardEffect]：焦点放大/发光描边 + MiuixIndication 按压蒙层（无水波纹）+ 遥控器点击；
 * - [pressable] + SinkFeedback：按压形变（缩放 0.94，spring(0.8f, 600f)，delay=null 立即下沉）。
 */
@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
) {
    Card(
        modifier = modifier
            .width(width)
            .tvCardEffect(onClick = onClick, shape = RoundedCornerShape(Spacing.corner))
            .pressable(
                interactionSource = null,
                indication = SinkFeedback(),
                delay = null,
            ),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
        ) {
            AsyncImage(
                model = video.cover,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
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

            // 底部：标题
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
