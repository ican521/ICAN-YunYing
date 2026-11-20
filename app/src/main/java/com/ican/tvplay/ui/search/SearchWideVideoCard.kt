package com.ican.tvplay.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.ui.components.PosterImage
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.LightOnSurface
import com.ican.tvplay.ui.theme.Spacing
import com.ican.tvplay.ui.theme.SquircleShape
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * 跨站源搜索结果「长条横卡」（借鉴 fongmi mobile `adapter_search.xml`）：
 * - 左侧：海报预览（不叠任何文字标签）
 * - 右侧：第 1 行站源、第 2 行视频标题、第 3 行标签（更新状态/备注等）
 * 圆角沿用全项目海报卡片同款 [Spacing.corner]（连续曲率 SquircleShape）；
 * 宽度由所在网格单元格决定，高度固定 [Spacing.searchWideCardHeight]，列数由网格按剩余宽度自算。
 */
@Composable
fun SearchWideVideoCard(
    video: Video,
    siteName: String,
    tagLine: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Spacing.searchWideCardHeight,
) {
    val corner = Spacing.corner
    Card(
        modifier = modifier
            .height(height)
            .tvCardEffect(
                onClick = onClick,
                shape = SquircleShape(corner),
                glow = false,
                pressColor = LightOnSurface,
            ),
        cornerRadius = corner,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(height)) {
            // 左侧海报：3:4 竖版，按卡片高度换算宽度（112dp → 84dp）
            PosterImage(
                title = video.title,
                cover = video.cover,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(height * 3f / 4f)
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            )

            // 右侧文字区：站源 → 标题 → 标签，垂直居中
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(Spacing.searchWideCardPadding),
                verticalArrangement = Arrangement.spacedBy(
                    Spacing.posterTagRowTopGap,
                    Alignment.CenterVertically,
                ),
            ) {
                // 第 1 行：站源
                Text(
                    text = siteName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 第 2 行：视频标题
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // 第 3 行：标签文字（更新状态 / 备注）
                if (tagLine != null) {
                    Text(
                        text = tagLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
