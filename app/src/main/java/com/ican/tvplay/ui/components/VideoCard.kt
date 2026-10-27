package com.ican.tvplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.ui.theme.LightOnSurface
import com.ican.tvplay.ui.theme.PosterBadgeScrim
import com.ican.tvplay.ui.theme.PosterBottomScrim
import com.ican.tvplay.ui.theme.Spacing
import com.ican.tvplay.ui.theme.SquircleShape
import com.ican.tvplay.util.posterModel
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
 * - [pressable] + SinkFeedback：按压形变（缩放 0.94，spring(0.8f, 600f)，delay=null 立即下沉）。
 *   必须放在外层：缩放绘制作用于其后的全部内容，按压蒙层才跟随卡片一起缩小；
 * - [tvCardEffect]：焦点放大/发光描边 + MiuixIndication 按压蒙层（无水波纹）+ 遥控器点击。
 */
@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
) {
    // 全局统一 16dp 连续曲率角（SquircleShape，与 miuix Card/弹窗同曲线风格，全 API 生效）
    val corner = Spacing.corner
    Card(
        modifier = modifier
            .width(width)
            .pressable(
                interactionSource = null,
                indication = SinkFeedback(),
                delay = null,
            )
            // 按压蒙层固定浅色模式同款浅黑(#15161D)：深色模式不再出现浅白按压块，两套模式完全一致
            // shape 用 SquircleShape 连续曲率角，与 Card 自带 squircleSurface 轮廓一致
            .tvCardEffect(
                onClick = onClick,
                shape = SquircleShape(corner),
                glow = false,
                pressColor = LightOnSurface,
            ),
        cornerRadius = corner,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
        ) {
            PosterImage(
                title = video.title,
                cover = video.cover,
                modifier = Modifier.fillMaxSize(),
            )

            // 底部渐变 + 标题（与收藏/历史/搜索结果卡片统一：渐变随标题内容收缩，不再固定 72dp）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, PosterBottomScrim),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
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

            // 右上角：评分胶囊
            Text(
                text = video.rating,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(PosterBadgeScrim, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * 海报统一渲染入口（首页 / 搜索 / 收藏 / 历史共用）。
 *
 * 封面缺失或加载失败（如糯米站源）时，海报位置统一显示「[空]」占位，不再显示视频标题。
 */
@Composable
fun PosterImage(
    title: String,
    cover: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    // 轻量 AsyncImage（替代 SubcomposeAsyncImage：去掉子组合的二次测量开销）；
    // 加载失败叠标题文字兜底，与原 error 槽行为一致（remember key 到 cover：换封面后重置失败态）
    var failed by remember(cover) { mutableStateOf(false) }
    Box(modifier = modifier) {
        AsyncImage(
            model = posterModel(context, cover),
            contentDescription = title,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { failed = false },
            onError = { failed = true },
        )
        if (failed) {
            PosterTextFallback()
        }
    }
}

/** 无海报时的兜底：不显示视频标题，统一显示「[空]」占位 */
@Composable
private fun PosterTextFallback() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "[空]",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
