package com.ican.tvplay.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.ican.tvplay.data.ThemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

/** 当前是否为深色主题（跟随用户选择的 ThemeMode，而非仅系统设置） */
val LocalDarkTheme = compositionLocalOf { false }

@OptIn(ExperimentalTvMaterial3Api::class)
private val TvDarkColors = darkColorScheme(
    primary = BrandBlueDeep,
    onPrimary = Color.White,
    secondary = BrandTeal,
    tertiary = BrandViolet,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    scrim = DarkScrim,
)

@OptIn(ExperimentalTvMaterial3Api::class)
private val TvLightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF009A89),
    tertiary = Color(0xFF7A52D6),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceMuted,
    scrim = LightScrim,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlayTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeColor: Int = 0,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        // 主题色板：非 0 时按种子色派生，否则用品牌默认色
        themeColor != 0 ->
            if (dark) seedDarkScheme(Color(themeColor)) else seedLightScheme(Color(themeColor))
        else -> if (dark) TvDarkColors else TvLightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    TvMaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalDarkTheme provides dark, content = content)
    }
}

/**
 * miuix 主题色板：表面色固定为项目既定值（surfaceContainer 深色 #242424 / 浅色 #FFFFFF，
 * windowDimming 固定 PopupScrim）；主题色板非 0 时强调色系直接取色板原色（不做 tonal 派生，
 * 避免黄色被映射成橄榄黄等偏色），文字色按亮度自动取黑/白保证可读，换色即时生效。
 */
@Composable
fun appMiuixColors(themeColor: Int, isDark: Boolean): Colors {
    if (themeColor == 0) {
        return if (isDark) {
            miuixDarkColorScheme(surfaceContainer = DarkSurfaceVariant, windowDimming = PopupScrim)
        } else {
            miuixLightColorScheme(surfaceContainer = LightSurface, windowDimming = PopupScrim)
        }
    }
    val seed = Color(themeColor)
    // 取黑/白中对比更高的一方：亮度 0.179 是两条对比曲线的交点，中亮度色（如 #2196F3）
    // 用黑字约 7.6:1，比按 0.5 阈值取白字的约 2.9:1 可读性更好
    val onSeed = if (seed.luminance() > 0.179f) Color.Black else Color.White
    return if (isDark) {
        miuixDarkColorScheme(
            primary = seed,
            onPrimary = onSeed,
            primaryVariant = seed,
            onPrimaryVariant = onSeed,
            primaryContainer = seed,
            onPrimaryContainer = onSeed,
            surfaceContainer = DarkSurfaceVariant,
            windowDimming = PopupScrim,
        )
    } else {
        miuixLightColorScheme(
            primary = seed,
            onPrimary = onSeed,
            primaryVariant = seed,
            onPrimaryVariant = onSeed,
            primaryContainer = seed,
            onPrimaryContainer = onSeed,
            surfaceContainer = LightSurface,
            windowDimming = PopupScrim,
        )
    }
}
