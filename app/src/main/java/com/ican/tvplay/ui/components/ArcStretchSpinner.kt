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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 等待动画「弧长伸缩」：圆弧持续收缩 / 伸长（约 84° ↔ 220°）同时整圈旋转。
 * 不转圈式的点状 loading，弧长变化暗示网络请求耗时不确定。
 */
@Composable
fun ArcStretchSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    strokeWidth: Dp = 2.5.dp,
    color: Color = MiuixTheme.colorScheme.onSurface,
) {
    val transition = rememberInfiniteTransition(label = "arcStretch")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "arcRotate",
    )
    val sweep by transition.animateFloat(
        initialValue = 84f,
        targetValue = 220f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arcSweep",
    )

    Canvas(modifier = modifier.size(size)) {
        drawArc(
            color = color.copy(alpha = 0.55f),
            startAngle = rotation - 90f,
            sweepAngle = sweep,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
        )
    }
}
