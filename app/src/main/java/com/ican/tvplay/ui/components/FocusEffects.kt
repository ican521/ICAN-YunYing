package com.ican.tvplay.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

/** 按下立刻渐显时长（毫秒） */
const val PRESS_FADE_IN_MS = 200

/** 松手渐隐时长（毫秒） */
const val PRESS_FADE_OUT_MS = 250

/**
 * 全局统一的卡片交互效果（手机触屏 + TV 遥控器同一套）：
 *
 * 1. 遥控器/键盘聚焦：弹簧放大 + 主题色发光描边；
 * 2. 触摸按压：高亮蒙层 200ms 渐显，按住保持，松手 250ms 渐隐；
 * 3. indication = null，彻底禁用系统默认水波纹。
 */
@Composable
fun Modifier.tvCardEffect(
    onClick: () -> Unit,
    shape: Shape,
    focusedScale: Float = 1.07f,
    glow: Boolean = true,
    enabled: Boolean = true,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "tvCardScale",
    )

    // 按下 / 松手使用不同时长的补间
    val highlight by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) {
            tween(durationMillis = PRESS_FADE_IN_MS)
        } else {
            tween(durationMillis = PRESS_FADE_OUT_MS)
        },
        label = "tvCardHighlight",
    )

    val accent = MaterialTheme.colorScheme.primary

    return this
        .scale(scale)
        .then(
            if (glow) {
                Modifier.shadow(
                    elevation = if (focused) 18.dp else 6.dp,
                    shape = shape,
                    ambientColor = accent,
                    spotColor = accent,
                    clip = false,
                )
            } else {
                Modifier
            },
        )
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
        .drawWithContent {
            drawContent()

            if (highlight > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.08f * highlight),
                            Color.White.copy(alpha = 0.22f * highlight),
                        ),
                    ),
                )
            }

            if (focused) {
                val strokeWidth = 2.dp.toPx()
                drawRect(
                    color = accent.copy(alpha = 0.95f),
                    style = Stroke(width = strokeWidth),
                )
            }
        }
}
