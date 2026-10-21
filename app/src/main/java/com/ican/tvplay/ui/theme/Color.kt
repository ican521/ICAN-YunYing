package com.ican.tvplay.ui.theme

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

// 选中项填充透明度（全 App 统一）：主题色叠加在选项底色上的选中高亮，禁止各页面再写 0.15/0.16/0.2 之类的魔法数。
// 唯一例外是播放页选集选中态（品牌蓝 25%），见 [EpisodeSelectedFillAlpha]。
const val SelectionFillAlpha = 0.18f

// 播放页选集选中态填充透明度：全屏侧边选集弹窗与竖屏「更多」弹窗共用，固定品牌蓝 25%
const val EpisodeSelectedFillAlpha = 0.25f

// 海报卡片叠加层（首页/收藏/历史/搜索结果共用），保证各页海报视觉一致
val PosterBadgeScrim = Color(0x66000000)   // 左上角站源/评分角标底
val PosterBottomScrim = Color(0xE6000000)  // 底部标题渐变终点色
val PosterActionScrim = Color(0x88000000)  // 编辑模式右上角删除圆钮底

// 主题色板选中勾的背衬（半透明黑，保证勾在任意色点上都可见）
val CheckScrim = Color(0x55000000)

// 收藏/追剧按钮选中态粉色（播放页收藏图标）
val BrandPink = Color(0xFFFF5C8A)

// 全 App 统一黑色遮罩（弹窗/底部弹层/全屏加载蒙层等）：黑色 40%，不随深浅主题变化。
// 新增遮罩一律引用此常量，禁止再手写 Black.copy(alpha=…)；
// 需要淡入淡出时在引用处用 Modifier.alpha(progress) 乘到本常量上。
// 平台 Dialog（播放页功能弹窗）的窗口 dim 无法引用 Kotlin 常量，
// 在 themes.xml 用 android:backgroundDimAmount=0.4 对齐，两处取值必须保持一致。
val PopupScrim: Color = Color(0x66000000)
