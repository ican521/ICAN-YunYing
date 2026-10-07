package com.ican.tvplay.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

/**
 * 主题色板（照搬 KernelSU 管理器的 keyColorOptions）。
 * 列表第一项 0 表示「默认」，即品牌蓝。
 */
val keyColorOptions: List<Int> = listOf(
    0xFFF44336.toInt(),
    0xFFE91E63.toInt(),
    0xFF9C27B0.toInt(),
    0xFF673AB7.toInt(),
    0xFF3F51B5.toInt(),
    0xFF2196F3.toInt(),
    0xFF00BCD4.toInt(),
    0xFF009688.toInt(),
    0xFF4FAF50.toInt(),
    0xFFFFEB3B.toInt(),
    0xFFFFC107.toInt(),
    0xFFFF9800.toInt(),
    0xFF795548.toInt(),
    0xFF607D8F.toInt(),
    0xFFFF9CA8.toInt(),
)

/** 调整 HSV 明度/饱和度 */
private fun Color.shift(satFactor: Float, valFactor: Float, hueOffset: Float = 0f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
        hsv,
    )
    hsv[0] = (hsv[0] + hueOffset + 360f) % 360f
    hsv[1] = (hsv[1] * satFactor).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * valFactor).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** 由种子色派生深色 tv ColorScheme（surface/background 沿用品牌深色基调） */
@OptIn(ExperimentalTvMaterial3Api::class)
fun seedDarkScheme(seed: Color) = darkColorScheme(
    primary = seed.shift(0.9f, 1.05f),
    onPrimary = Color.White,
    secondary = seed.shift(0.55f, 0.9f),
    tertiary = seed.shift(0.75f, 1f, hueOffset = 60f),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    scrim = DarkScrim,
)

/** 由种子色派生浅色 tv ColorScheme */
@OptIn(ExperimentalTvMaterial3Api::class)
fun seedLightScheme(seed: Color) = lightColorScheme(
    primary = seed.shift(0.95f, 0.85f),
    onPrimary = Color.White,
    secondary = seed.shift(0.6f, 0.75f),
    tertiary = seed.shift(0.8f, 0.8f, hueOffset = 60f),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceMuted,
    scrim = LightScrim,
)
