package com.ican.tvplay.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.ican.tvplay.data.ThemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

/** 当前是否为深色主题（跟随用户选择的 ThemeMode，而非仅系统设置） */
val LocalDarkTheme = compositionLocalOf { false }

/** 默认主题色上的文字/前景色：亮度阈值 0.30（高于它才取黑，否则取白），#007FFF 取白 */
private val BrandPrimaryOnColor: Color =
    if (BrandPrimaryDefault.luminance() > 0.30f) Color.Black else Color.White

@OptIn(ExperimentalTvMaterial3Api::class)
private val TvDarkColors = darkColorScheme(
    primary = BrandPrimaryDefault,
    onPrimary = BrandPrimaryOnColor,
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
    primary = BrandPrimaryDefault,
    onPrimary = BrandPrimaryOnColor,
    secondary = Color(0xFF009A89),
    tertiary = Color(0xFF7A52D6),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
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

    TvMaterialTheme(colorScheme = colors, typography = inkTightTypography()) {
        CompositionLocalProvider(LocalDarkTheme provides dark, content = content)
    }
}

/**
 * 全局文字「所见即所高」口径（2026-11-20 用户选定层次一：全局覆盖）：
 * 1. `includeFontPadding = false`：去掉 Android 给每个字形加的固定内边距（API 24+ 即生效）；
 * 2. `LineHeightStyle(Alignment.Center, Trim.Both)`：把行盒比字号多出的行距裁掉，
 *    文字块布局高度 ≈ 可见字形高度（仅 API 28+ 生效，24-27 自动回退为带行距的旧排版）。
 * 生效后所有基于「字形留白」的视觉补偿都失去前提，间距一律回到规范值（12/16 档），勿再加补偿。
 */
private fun TextStyle.inkTight(): TextStyle = copy(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

private fun Typography.inkTight(): Typography = copy(
    displayLarge = displayLarge.inkTight(),
    displayMedium = displayMedium.inkTight(),
    displaySmall = displaySmall.inkTight(),
    headlineLarge = headlineLarge.inkTight(),
    headlineMedium = headlineMedium.inkTight(),
    headlineSmall = headlineSmall.inkTight(),
    titleLarge = titleLarge.inkTight(),
    titleMedium = titleMedium.inkTight(),
    titleSmall = titleSmall.inkTight(),
    bodyLarge = bodyLarge.inkTight(),
    bodyMedium = bodyMedium.inkTight(),
    bodySmall = bodySmall.inkTight(),
    labelLarge = labelLarge.inkTight(),
    labelMedium = labelMedium.inkTight(),
    labelSmall = labelSmall.inkTight(),
)

private fun inkTightTypography(): Typography = Typography().inkTight()

/**
 * 全屏播放页侧边面板专用（选集 SideSheet / 字幕设置 SideSheet）：固定使用「App 切到深色模式时」的
 * 现成 miuix 色板，不跟随当前浅/深主题切换。表面色 / 行底色 / 文字色取 miuix 深色默认值
 * （容器 #242424、未选中行 #434343、未选中字 #D9D9D9、标题 #EDEDF5）；强调色家族接入当前主题色，
 * windowDimming 沿用统一 PopupScrim。
 * 注：底部呼出的 OptionDialog / SubtitleDialog 由根 Scaffold 宿主渲染、不继承调用处局部色板，
 * 其深色档由弹窗组件内 forceDark 显式指定色值，不走本函数。
 */
@Composable
fun playerDarkMiuixColors(themeColor: Int): Colors {
    val seed = if (themeColor == 0) BrandPrimaryDefault else Color(themeColor)
    val onSeed = if (seed.luminance() > 0.30f) Color.Black else Color.White
    return miuixDarkColorScheme(
        primary = seed,
        onPrimary = onSeed,
        primaryVariant = seed,
        onPrimaryVariant = onSeed,
        primaryContainer = seed,
        onPrimaryContainer = onSeed,
        surfaceContainer = DarkSurfaceVariant,
        secondaryVariant = Color(0xFF434343),
        onSecondaryVariant = Color(0xFFD9D9D9),
        onBackground = DarkOnSurface,
        windowDimming = PopupScrim,
    )
}

/**
 * miuix 主题色板：表面色固定为项目既定值（surfaceContainer 深色 #242424 / 浅色 #FFFFFF，
 * windowDimming 固定 PopupScrim）；主题色板非 0 时强调色系直接取色板原色（不做 tonal 派生，
 * 避免黄色被映射成橄榄黄等偏色），文字色按亮度自动取黑/白保证可读，换色即时生效。
 */
@Composable
fun appMiuixColors(themeColor: Int, isDark: Boolean): Colors {
    if (themeColor == 0) {
        // 默认主题色：miuix 强调色系也取同一色值，保证设置页下拉、胶囊、开关、按钮与 Material 侧同色
        return if (isDark) {
            miuixDarkColorScheme(
                primary = BrandPrimaryDefault,
                onPrimary = BrandPrimaryOnColor,
                primaryVariant = BrandPrimaryDefault,
                onPrimaryVariant = BrandPrimaryOnColor,
                primaryContainer = BrandPrimaryDefault,
                onPrimaryContainer = BrandPrimaryOnColor,
                surfaceContainer = DarkSurfaceVariant,
                windowDimming = PopupScrim,
            )
        } else {
            miuixLightColorScheme(
                primary = BrandPrimaryDefault,
                onPrimary = BrandPrimaryOnColor,
                primaryVariant = BrandPrimaryDefault,
                onPrimaryVariant = BrandPrimaryOnColor,
                primaryContainer = BrandPrimaryDefault,
                onPrimaryContainer = BrandPrimaryOnColor,
                surfaceContainer = LightSurface,
                windowDimming = PopupScrim,
            )
        }
    }
    val seed = Color(themeColor)
    // 取黑/白阈值与默认主题色同一口径（0.30）：亮度高于它才取黑字，蓝色系一律用白字
    val onSeed = if (seed.luminance() > 0.30f) Color.Black else Color.White
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
