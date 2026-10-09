package com.ican.tvplay.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive 风格的「新式转圈」加载指示器（自绘实现）。
 *
 * 当前 Compose BOM 解析的 material3 为 1.4.0 stable，官方 LoadingIndicator
 * 组件尚未包含在内，故按其视觉语言自绘：主弧随呼吸伸缩、正向旋转；
 * 副弧半透明、反向旋转，整体呈双层弧线的动态节奏感。
 */
@Composable
fun ExpressiveLoadingIndicator(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val transition = rememberInfiniteTransition(label = "expressiveLoading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "rotation",
    )
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )

    Canvas(modifier.size(size)) {
        val stroke = Stroke(
            width = this.size.minDimension * 0.085f,
            cap = StrokeCap.Round,
        )
        val radius = (this.size.minDimension - stroke.width) / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // 主弧：弧角随呼吸伸缩，正向旋转
        val mainSweep = 80f + breathe * 170f
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = mainSweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            style = stroke,
        )

        // 副弧：半透明，反向旋转，与主弧节奏互补
        val subSweep = 36f + (1f - breathe) * 56f
        drawArc(
            color = color.copy(alpha = 0.35f),
            startAngle = -rotation * 1.6f,
            sweepAngle = subSweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            style = stroke,
        )
    }
}
