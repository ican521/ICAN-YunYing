package com.ican.tvplay.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.ican.tvplay.data.ThemeMode

/** 当前是否为深色主题（跟随用户选择的 ThemeMode，而非仅系统设置） */
val LocalDarkTheme = compositionLocalOf { false }

@OptIn(ExperimentalTvMaterial3Api::class)
private val TvDarkColors = darkColorScheme(
    primary = BrandBlue,
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
    primary = BrandBlueDeep,
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
