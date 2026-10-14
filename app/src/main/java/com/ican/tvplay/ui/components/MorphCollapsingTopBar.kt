package com.ican.tvplay.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.tv.material3.Text
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import kotlin.math.roundToInt

/**
 * 形变折叠顶栏（收藏页专用，替代 miuix TopAppBar 的交叉淡入淡出方案）：
 * 标题全程不消失，随滚动从大标题（32sp Normal，左下）连续缩小、移动到
 * 小标题位置（20sp Medium，顶栏水平居中）。
 *
 * 折叠进度、nestedScroll 手势、松手弹簧归位全部复用 miuix MiuixScrollBehavior，
 * 仅需把折叠行程（负值）写回 state.heightOffsetLimit；
 * 尺寸对齐 miuix TopAppBarDefaults：折叠行高 52dp、大标题底部间距 4dp、动作区内边距 16dp。
 */
@Composable
fun MorphCollapsingTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    color: Color,
    titleColor: Color,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val state = scrollBehavior.state
    // 0 = 完全展开，1 = 完全折叠（miuix TopAppBarState.collapsedFraction）
    val fraction = state.collapsedFraction

    val collapsedHeight = 52.dp
    val expandedExtra = 52.dp
    val titleBottomPadding = 4.dp
    val actionPadding = 16.dp
    // 大标题左缘与页面内容同一条基准线（手机 16dp / 平板 24dp）
    val titleStart = pageHorizontalPadding()

    val density = LocalDensity.current
    val collapsedPx = with(density) { collapsedHeight.toPx() }
    val expandedExtraPx = with(density) { expandedExtra.toPx() }
    val titleStartPx = with(density) { titleStart.toPx() }
    val titleBottomPx = with(density) { titleBottomPadding.toPx() }

    // 折叠行程同步给 miuix 状态：nestedScroll 手势与弹簧归位依赖该上限（负值）
    LaunchedEffect(expandedExtraPx) {
        state.heightOffsetLimit = -expandedExtraPx
    }

    // 形变进度统一走 LinearOutSlowInEasing（Material 标准减速曲线，起步快、收尾柔和），
    // 全程与滚动严格同步，无滞后无回弹；栏高度保持线性，与内容滚动 1:1
    val morphProgress = LinearOutSlowInEasing.transform(fraction)

    val barHeight = collapsedHeight + expandedExtra * (1f - fraction)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Box(modifier = Modifier.fillMaxWidth().height(barHeight)) {
            // 标题：字号 / 字重 / 位置随折叠进度连续插值，全程同一个 Text 不消失
            Text(
                text = title,
                color = titleColor,
                fontSize = (32f - 12f * morphProgress).sp,
                fontWeight = FontWeight((400f + 100f * morphProgress).roundToInt()),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = 0, minHeight = 0),
                    )
                    // 大标题端点：左下（x = 内容基准线，底边距栏底 4dp）
                    val largeX = titleStartPx
                    val largeY = collapsedPx + expandedExtraPx * (1f - fraction) -
                        titleBottomPx - placeable.height
                    // 小标题端点：整栏水平居中、折叠行内垂直居中
                    val smallX = (constraints.maxWidth - placeable.width) / 2f
                    val smallY = (collapsedPx - placeable.height) / 2f
                    val x = lerp(largeX, smallX, morphProgress).roundToInt()
                    val y = lerp(largeY, smallY, morphProgress).roundToInt()
                    layout(placeable.width, placeable.height) {
                        placeable.place(x, y)
                    }
                },
            )
            // 右上角动作区：固定于折叠行内垂直居中，全程常显（同 miuix actions 行为）
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .height(collapsedHeight)
                    .padding(end = actionPadding),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}
