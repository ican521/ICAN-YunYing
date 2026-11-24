package com.ican.tvplay.player.core

import android.graphics.Color

/**
 * 字幕样式（功能对齐 fongmi SubtitleSetting + media3 CaptionStyleCompat）。
 *
 * fongmi 的能力拆解：
 * - 三种模式：原样（尊重字幕文件自带样式）/ 跟随系统（读系统无障碍字幕设置）/ 自定义；
 * - 自定义可调：文字颜色 + 透明度、描边样式（无/描边/投影）+ 颜色 + 透明度 + 粗细、
 *   阴影偏移、背景色 + 透明度、字号、位置。
 * 这里保留同一套参数，UI 与色板按本项目风格自绘。
 */
data class SubtitleStyle(
    val mode: Int = MODE_CUSTOM,
    val textColor: Int = COLOR_WHITE,
    val textOpacity: Float = 1f,
    val edgeType: Int = EDGE_OUTLINE,
    val edgeColor: Int = COLOR_BLACK,
    val edgeOpacity: Float = 1f,
    val edgeWidth: Float = 1.2f,
    val shadow: Float = 0f,
    val backgroundColor: Int = COLOR_TRANSPARENT,
    val backgroundOpacity: Float = 1f,
) {

    /** 文字颜色叠透明度（fongmi applyOpacity 同款） */
    val foreground: Int get() = applyOpacity(textColor, textOpacity)

    /** 背景色叠透明度 */
    val background: Int get() = applyOpacity(backgroundColor, backgroundOpacity)

    /** 描边色叠透明度 */
    val edge: Int get() = applyOpacity(edgeColor, edgeOpacity)

    /** 是否强制覆盖字幕自带样式（原样模式下不覆盖） */
    val forcesStyle: Boolean get() = mode != MODE_ORIGINAL

    companion object {
        // 模式（与 fongmi SubtitleSetting.STYLE_* 同值）
        const val MODE_ORIGINAL = 0
        const val MODE_SYSTEM = 1
        const val MODE_CUSTOM = 2

        // 描边类型（与 CaptionStyleCompat.EDGE_TYPE_* 同值）
        const val EDGE_NONE = 0
        const val EDGE_OUTLINE = 1
        const val EDGE_SHADOW = 2

        // 文字色板（照 fongmi SubtitleSetting 的颜色集合）
        val COLOR_WHITE = 0xFFFFFFFF.toInt()
        val COLOR_YELLOW = 0xFFFFFF00.toInt()
        val COLOR_CYAN = 0xFF00E5FF.toInt()
        val COLOR_GREEN = 0xFF76FF03.toInt()
        val COLOR_ORANGE = 0xFFFF9800.toInt()
        val COLOR_PINK = 0xFFFF80AB.toInt()
        val COLOR_RED = 0xFFFF5252.toInt()
        val COLOR_BLUE = 0xFF64B5F6.toInt()
        val COLOR_GRAY = 0xFFBDBDBD.toInt()
        val COLOR_BLACK = Color.BLACK
        val COLOR_TRANSPARENT = Color.TRANSPARENT

        // 背景色板（照 fongmi：透明 / 半透明黑 / 黑 / 深灰）
        val BACKGROUND_DIM = 0x66000000
        val BACKGROUND_BLACK = 0xCC000000.toInt()
        val BACKGROUND_GRAY = 0xCC202020.toInt()

        /** 默认样式：白字 + 黑描边，等价本次改动前的观感 */
        val DEFAULT = SubtitleStyle()

        private fun applyOpacity(color: Int, opacity: Float): Int {
            val a = (Color.alpha(color) * opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
            return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}
