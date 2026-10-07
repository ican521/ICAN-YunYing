package com.ican.tvplay.ui.theme

import android.app.Activity
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.ican.tvplay.data.ThemeMode

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

/** m3 动态取色 scheme → tv scheme 映射 */
@RequiresApi(31)
@OptIn(ExperimentalTvMaterial3Api::class)
private fun dynamicTvScheme(dark: Boolean, context: android.content.Context): androidx.tv.material3.ColorScheme {
    val m3 = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return if (dark) {
        darkColorScheme(
            primary = m3.primary, onPrimary = m3.onPrimary,
            secondary = m3.secondary, tertiary = m3.tertiary,
            background = m3.background, onBackground = m3.onBackground,
            surface = m3.surface, onSurface = m3.onSurface,
            surfaceVariant = m3.surfaceVariant, onSurfaceVariant = m3.onSurfaceVariant,
            scrim = m3.scrim,
        )
    } else {
        lightColorScheme(
            primary = m3.primary, onPrimary = m3.onPrimary,
            secondary = m3.secondary, tertiary = m3.tertiary,
            background = m3.background, onBackground = m3.onBackground,
            surface = m3.surface, onSurface = m3.onSurface,
            surfaceVariant = m3.surfaceVariant, onSurfaceVariant = m3.onSurfaceVariant,
            scrim = m3.scrim,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlayTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    themeColor: Int = 0,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        // 动态取色：Android 12+ 生效，低版本回退品牌色
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicTvScheme(dark, context)
        // 主题色板：非 0 时按种子色派生
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

    TvMaterialTheme(colorScheme = colors, content = content)
}
