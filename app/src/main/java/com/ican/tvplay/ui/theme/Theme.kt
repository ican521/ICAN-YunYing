package com.ican.tvplay.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlayTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) TvDarkColors else TvLightColors

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
