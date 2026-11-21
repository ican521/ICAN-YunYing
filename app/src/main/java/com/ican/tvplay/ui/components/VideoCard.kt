package com.ican.tvplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.ui.theme.CardTitleBandColor
import com.ican.tvplay.ui.theme.LightOnSurface
import com.ican.tvplay.ui.theme.PosterBadgeScrim
import com.ican.tvplay.ui.theme.Spacing
import com.ican.tvplay.ui.theme.SquircleShape
import com.ican.tvplay.util.posterModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

/**
 * 首页 / 搜索 / 收藏复用的视频卡片（海报 + 角标 + 标题）。
 *
 * 容器为 miuix Card（insideMargin=0，16dp 统一圆角：API 33+ 为 squircle shader 裁剪，
 * 低版本自动回退 RoundedCornerShape；底色由 Card colors 提供，兼作海报加载占位色）。
 *
 * 交互分两层，与 miuix Card 可点版内部结构同构：
 * - [pressable] + SinkFeedback：按压形变（整卡缩到 0.94，spring(0.8f, 600f)，delay=null 立即下沉）。
 *   放在修饰链最前面，整卡（含内容）一起缩，不加底衬也不铺投影；
 *   轮廓靠页面底色（#EDEDED）与纯白卡片的亮度反差撑出来，缩小时露出页面底色不出现异物色块。
 * - [tvCardEffect]：焦点放大/发光描边 + MiuixIndication 按压蒙层（无水波纹）+ 遥控器点击。
 *
 * @param width 固定宽度；传 null 不限宽（跨站源搜索结果网格内撑满单元格）
 * @param showRating 是否显示右上角评分胶囊（跨站源搜索结果不显示）
 * @param topStartBadge 海报右上角胶囊文字（跨站源搜索结果传站源名）；null 不显示
 * @param tagLine 海报左下角标签文字（跨站源搜索结果传更新状态/备注）；null 不显示
 */
@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 148.dp,
    showRating: Boolean = true,
    topStartBadge: String? = null,
    tagLine: String? = null,
    /** 整卡宽高比：默认 7:10 竖版（卡片底部固定一行标题，海报高度已扣出标题行）；传 4:3 即「横过来的宽卡」 */
    aspectRatio: Float = 7f / 10f,
) {
    // 全局统一 16dp 连续曲率角（SquircleShape，与 miuix Card/弹窗同曲线风格，全 API 生效）
    val corner = Spacing.corner
    val shape = SquircleShape(corner)
    // 卡片底色：Card 背景色，兼作海报加载占位色
    val cardColor = MiuixTheme.colorScheme.surfaceContainer
    Card(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .aspectRatio(aspectRatio)
            // 按压下沉放在最外层：整卡连同内容一起缩到 0.94
            .pressable(
                interactionSource = null,
                indication = SinkFeedback(),
                delay = null,
            )
            // 不铺投影：轮廓靠页面底色（#EDEDED）与纯白卡片的亮度反差撑出来，
            // 按压下沉时也不会出现投影原位残留的一圈灰
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
        colors = CardDefaults.defaultColors(color = cardColor),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 海报区：占满「整卡高度 - 标题行」，卡片仍是 7:10 整体比例
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                PosterImage(
                    title = video.title,
                    cover = video.cover,
                    modifier = Modifier.fillMaxSize(),
                )

                // 右上角：站源/来源胶囊（跨站源搜索结果用；搜索卡片 showRating=false，不会与评分胶囊冲突）
                if (topStartBadge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.posterOverlayMargin)
                            .background(PosterBadgeScrim, RoundedCornerShape(percent = 50))
                            .padding(horizontal = Spacing.posterBadgePaddingH, vertical = Spacing.posterBadgePaddingV),
                    ) {
                        Text(
                            text = topStartBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 左下角：状态/备注标签（跨站源搜索结果用：全部标签文字合并进同一个胶囊）
                if (tagLine != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(Spacing.posterOverlayMargin)
                            .background(PosterBadgeScrim, RoundedCornerShape(percent = 50))
                            .padding(horizontal = Spacing.posterBadgePaddingH, vertical = Spacing.posterBadgePaddingV),
                    ) {
                        Text(
                            text = tagLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 右上角：评分胶囊
                if (showRating) {
                    Text(
                        text = video.rating,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.posterOverlayMargin)
                            .background(PosterBadgeScrim, RoundedCornerShape(percent = 50))
                            .padding(horizontal = Spacing.posterBadgePaddingH, vertical = Spacing.posterBadgePaddingV),
                    )
                }
            }

            // 卡片底部标题行：浅黑底 + 白字、固定高度、单行、放不下结尾省略
            // 深色底让卡片底缘一眼可辨（不依赖投影或页面底色对比）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.posterTitleBandHeight)
                    .background(CardTitleBandColor)
                    .padding(horizontal = Spacing.posterTitlePaddingH),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    // 请求对象按 cover 缓存：posterModel 内含 @Headers/@Cookie/@Referer 后缀解析（JSONObject 解析），
    // 每次重组都重建等于在滑动中反复解析同一串 URL
    val model = remember(context, cover) { posterModel(context, cover) }
    // 轻量 AsyncImage（替代 SubcomposeAsyncImage：去掉子组合的二次测量开销）；
    // 加载失败叠标题文字兜底，与原 error 槽行为一致（remember key 到 cover：换封面后重置失败态）
    var failed by remember(cover) { mutableStateOf(false) }
    Box(modifier = modifier) {
        AsyncImage(
            model = model,
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
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
