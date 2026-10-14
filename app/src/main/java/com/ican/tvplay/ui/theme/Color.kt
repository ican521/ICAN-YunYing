package com.ican.tvplay.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 品牌色
val BrandBlue = Color(0xFF3482FF)
val BrandBlueDeep = Color(0xFF277AF7)
val BrandTeal = Color(0xFF00E0C8)
val BrandViolet = Color(0xFFB48CFF)

// 深色主题（全局背景纯黑）
val DarkBackground = Color(0xFF000000)
// 深色模式板块/弹窗统一容器色 rgb(36,36,36)（设置板块、各类 Dialog、关闭毛玻璃时的底栏胶囊均引用此色）
val DarkSurface = Color(0xFF242424)
// 深色模式次级容器色（搜索栏、圆钮、胶囊、开关槽等），与板块/弹窗容器色统一为 rgb(36,36,36)
val DarkSurfaceVariant = Color(0xFF242424)
// 深色模式浮层抬升色（KernelSU 风格下拉弹窗等悬浮层），比板块色亮一档以区分层级
val DarkSurfaceElevated = Color(0xFF2C2C2C)
val DarkOnSurface = Color(0xFFEDEDF5)
val DarkOnSurfaceMuted = Color(0xFFA6A6BA)
val DarkGlassTint = Color(0x33FFFFFF)
val DarkGlassTintStrong = Color(0x52FFFFFF)
val DarkScrim = Color(0xCC07070D)

// 浅色主题
val LightBackground = Color(0xFFF7F7F7)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE6E8F1)
val LightOnSurface = Color(0xFF15161D)
val LightOnSurfaceMuted = Color(0xFF5C5E6E)
val LightGlassTint = Color(0x66FFFFFF)
val LightGlassTintStrong = Color(0x99FFFFFF)
val LightScrim = Color(0xB3101018)

// 全局弹窗遮罩色（单点收口）：miuix 对话框/下拉弹窗的 windowDimming 统一引用此函数。
// 取值与 miuix 默认完全一致（浅色黑30% / 深色黑60%，逐位相同），仅将来源收口到项目内，观感零变化
@Composable
fun popupDimColor(): Color =
    Color.Black.copy(alpha = if (LocalDarkTheme.current) 0.6f else 0.3f)
