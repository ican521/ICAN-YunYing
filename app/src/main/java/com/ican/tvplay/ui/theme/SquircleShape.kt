package com.ican.tvplay.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * 全局统一 squircle（连续曲率）圆角 Shape，曲线公式与 miuix SquirclePath 完全一致
 * （extension = 1.1，Bézier 控制比 0.643），纯路径实现，全 API 生效（不依赖 API 33+ shader）。
 *
 * 所有圆角矩形类组件统一使用本 Shape 替代 RoundedCornerShape，保持与 miuix
 * Card/OverlayDialog 相同的连续曲率视觉；数值不变（16 仍是 16dp）。
 * 胶囊（50%）与正圆（CircleShape）不适用。
 */
class SquircleShape(
    private val topStart: Dp,
    private val topEnd: Dp = topStart,
    private val bottomEnd: Dp = topStart,
    private val bottomStart: Dp = topStart,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val ltr = layoutDirection == LayoutDirection.Ltr
        val tl = (if (ltr) topStart else topEnd).toPx(density)
        val tr = (if (ltr) topEnd else topStart).toPx(density)
        val br = (if (ltr) bottomEnd else bottomStart).toPx(density)
        val bl = (if (ltr) bottomStart else bottomEnd).toPx(density)
        return Outline.Generic(buildSquirclePath(size.width, size.height, tl, tr, br, bl))
    }

    private fun Dp.toPx(density: Density): Float = with(density) { toPx() }

    companion object {
        /** 与 miuix SquircleDefaults.Extension 一致：角部切块 = 半径 × 1.1 */
        private const val EXTENSION = 1.1f

        /** 与 miuix SQUIRCLE_CONTROL 一致：Bézier 手柄比 */
        private const val CONTROL = 0.643f

        /**
         * 构造 squircle 圆角矩形路径，公式照搬 miuix SquirclePath.addSquircleRect，
         * 扩展支持每角独立半径（0 表示直角）。
         */
        fun buildSquirclePath(
            width: Float,
            height: Float,
            topLeft: Float,
            topRight: Float,
            bottomRight: Float,
            bottomLeft: Float,
        ): Path {
            val path = Path()
            if (width <= 0f || height <= 0f) return path
            val halfMin = min(width, height) * 0.5f

            // 每角切块：半径 × 1.1，钳制到短边一半
            fun tile(r: Float) = max(0f, r * EXTENSION).coerceAtMost(halfMin)
            val tl = tile(topLeft)
            val tr = tile(topRight)
            val br = tile(bottomRight)
            val bl = tile(bottomLeft)

            // 每角手柄：切块 × (1 − 0.643)
            fun handle(t: Float) = t * (1f - CONTROL)
            val tlH = handle(tl)
            val trH = handle(tr)
            val brH = handle(br)
            val blH = handle(bl)

            path.moveTo(tl, 0f)
            path.lineTo(width - tr, 0f)
            if (tr > 0f) path.cubicTo(width - trH, 0f, width, trH, width, tr)
            path.lineTo(width, height - br)
            if (br > 0f) path.cubicTo(width, height - brH, width - brH, height, width - br, height)
            path.lineTo(bl, height)
            if (bl > 0f) path.cubicTo(blH, height, 0f, height - blH, 0f, height - bl)
            path.lineTo(0f, tl)
            if (tl > 0f) path.cubicTo(0f, tlH, tlH, 0f, tl, 0f)
            path.close()
            return path
        }
    }
}
