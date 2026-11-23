package com.ican.tvplay.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ican.tvplay.ui.theme.Spacing
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 「刷新完成」提示的停留时长（毫秒） */
const val REFRESH_DONE_HINT_MILLIS = 1200L

/** 提示淡入淡出时长（毫秒） */
private const val REFRESH_DONE_HINT_FADE_MILLIS = 160

/**
 * 「刷新完成」提示。
 *
 * 为什么不用 Miuix PullToRefresh 自带的完成文案：它内部在 `isRefreshing` 落到 false 后
 * 只把完成态维持 200ms（`startManualRefreshCompleteAnimation` 里的 tween(200)）就回弹收回，
 * 「刷新完成」四个字根本来不及看清。所以那句提示交给本组件接管：
 * 刷新结束后由调用方置 visible=true，停留 [REFRESH_DONE_HINT_MILLIS] 再淡出。
 */
@Composable
fun RefreshDoneHint(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = REFRESH_DONE_HINT_FADE_MILLIS),
        label = "refreshDoneHintAlpha",
    )
    // 完全透明时不参与布局，避免常驻一层空容器挡住触摸
    if (alpha <= 0f) return
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(
                color = MiuixTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(Spacing.corner),
            )
            .padding(
                horizontal = Spacing.searchWideCardPadding,
                vertical = Spacing.videoTitleContentGap,
            ),
    ) {
        Text(
            text = "刷新完成",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
        )
    }
}
