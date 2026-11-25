package com.ican.tvplay.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// 品牌色
val BrandBlue = Color(0xFF3482FF)
val BrandBlueDeep = Color(0xFF277AF7)
val BrandTeal = Color(0xFF00E0C8)
val BrandViolet = Color(0xFFB48CFF)

/** 默认主题色（未选主题色板时全 App 的强调色）：深色 / 浅色模式同色 #007AFF */
val BrandPrimaryDefault = Color(0xFF007AFF)

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
val LightOnSurface = Color(0xFF15161D)
val LightOnSurfaceMuted = Color(0xFF5C5E6E)
val LightGlassTint = Color(0x66FFFFFF)
val LightGlassTintStrong = Color(0x99FFFFFF)
val LightScrim = Color(0xB3101018)

// 选中项填充透明度（全 App 统一）：主题色叠加在选项底色上的选中高亮，禁止各页面再写 0.15/0.16/0.2 之类的魔法数。
const val SelectionFillAlpha = 0.18f

// 播放页选集旧版选中态填充透明度（主题色 25%）：现已无引用。
// 全屏侧边选集弹窗已改为与字幕设置分段单选同款配色（secondaryVariant 实底），
// 竖屏横滑选集与「更多」弹窗走 EpisodeChip 的 primary / secondaryVariant 实底。常量保留以备回溯。
const val EpisodeSelectedFillAlpha = 0.25f

// 详情页「线路 / 画质 / 选集」选中态 tinted（方案 G+）：选中底 + 选中文字，禁止页面内联算色。
// 页面传入的 primary 已是主题色压深/提亮后的档位值（见 ThemePalettes 的 seed 派生），这里不再做 HSV 偏移。

/** 浅色档选中底色：主题色 14% 叠在浅色容器底上 */
const val ChipTintedFillAlphaLight = 0.14f

/** 深色档选中底色：主题色 22% 叠在深色容器底上 */
const val ChipTintedFillAlphaDark = 0.22f

/** 分段按钮选中态切换纯淡化时长（ms）：无滑块位移，背景与文字色交叉淡化 */
const val SegmentedTabsFadeMs = 150

/** 分段按钮外框与段间分隔线透明度（深色叠白 / 浅色叠黑） */
const val SegmentedTabsOutlineAlpha = 0.10f

/** 浅色档选中文字起步主色占比（其余压向 [ChipTintedTextAnchorLight]），对比度不足时再按守护步长推远 */
private const val ChipTintedTextPrimaryRatioLight = 0.74f

/** 深色档选中文字起步主色占比（其余压向白），对比度不足时再按守护步长推远 */
private const val ChipTintedTextPrimaryRatioDark = 0.55f

/** 浅色档选中文字的压深锚色 */
private val ChipTintedTextAnchorLight = Color(0xFF001018)

/** 文字-底色对比度守护阈值（WCAG AA 正文 4.5:1） */
private const val ChipTintedTextContrastTarget = 4.5

/** 对比度守护：向锚色方向推远的单步步长（主色占比每次递减 0.01） */
private const val ChipTintedTextGuardStep = 0.01f

/**
 * 选中态底色：sRGB 通道线性插值 base→primary（等价 CSS `color-mix(in srgb, primary X%, base)`），返回不透明结果色。
 * @param dark true 叠在 [DarkSurface] 上用 [ChipTintedFillAlphaDark]；false 叠在 [LightSurface] 上用 [ChipTintedFillAlphaLight]
 */
fun chipTintedFill(primary: Color, dark: Boolean): Color {
    val base = if (dark) DarkSurface else LightSurface
    val ratio = if (dark) ChipTintedFillAlphaDark else ChipTintedFillAlphaLight
    return srgbMix(base, primary, ratio)
}

/**
 * 选中态文字：以 primary 为起点向锚色混合（浅色锚 [ChipTintedTextAnchorLight]、深色锚白），
 * 起步主色占比 [ChipTintedTextPrimaryRatioLight] / [ChipTintedTextPrimaryRatioDark]；
 * 与 [chipTintedFill] 的 WCAG 对比度不足 [ChipTintedTextContrastTarget] 时，按 [ChipTintedTextGuardStep]
 * 继续向锚色方向推远，直到达标（锚色占比上限 1.0）。
 */
fun chipTintedText(primary: Color, dark: Boolean): Color {
    val anchor = if (dark) Color.White else ChipTintedTextAnchorLight
    var primaryRatio = if (dark) ChipTintedTextPrimaryRatioDark else ChipTintedTextPrimaryRatioLight
    val fill = chipTintedFill(primary, dark)
    var text = srgbMix(anchor, primary, primaryRatio)
    while (contrastRatio(text, fill) < ChipTintedTextContrastTarget && primaryRatio > 0f) {
        primaryRatio -= ChipTintedTextGuardStep
        text = srgbMix(anchor, primary, primaryRatio)
    }
    return text
}

/** sRGB 通道线性插值：start*(1-f) + stop*f；两端均不透明，按 ARGB Long 组装 */
private fun srgbMix(start: Color, stop: Color, fraction: Float): Color {
    fun channel(a: Float, b: Float): Int = Math.round(
        (a + (b - a) * fraction).coerceIn(0f, 1f) * 255f,
    )
    val r = channel(start.red, stop.red)
    val g = channel(start.green, stop.green)
    val b = channel(start.blue, stop.blue)
    val argb = (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    return Color(argb)
}

/** WCAG 2.x 相对亮度（sRGB→线性，权重 0.2126 / 0.7152 / 0.0722） */
private fun relativeLuminance(color: Color): Float {
    fun linear(c: Float): Float =
        if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    val r = linear(color.red)
    val g = linear(color.green)
    val b = linear(color.blue)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/** WCAG 对比度 (L1+0.05)/(L2+0.05)，其中 L1 ≥ L2 */
private fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a).toDouble()
    val lb = relativeLuminance(b).toDouble()
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

// 海报卡片叠加层（首页/收藏/历史/搜索结果共用），保证各页海报视觉一致
val PosterBadgeScrim = Color(0x66000000)   // 左上角站源/评分角标底
// 海报内标题样式的副标题文字色（标题下方的更新时间/状态小字，无胶囊底）
val PosterCaptionText = Color(0xFFE8E8E8)
val PosterBottomScrim = Color(0xE6000000)  // 底部标题渐变终点色
val PosterTitleScrim = Color(0xE6000000)   // 底部标题渐变终点色（90% 黑）
val PosterTitleScrimMid = Color(0x99000000) // 底部标题渐变中段色（60% 黑，55% 位置）

/** 海报卡片底部标题层渐变（搜索页海报卡 / 收藏卡 / 历史卡共用）：黑区提前出现、终点压到 90% 黑，抬高标题对比度 */
fun posterTitleScrimBrush(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to Color.Transparent,
        0.55f to PosterTitleScrimMid,
        1.0f to PosterTitleScrim,
    ),
)
val PosterActionScrim = Color(0x88000000)  // 编辑模式右上角删除圆钮底

/**
 * 全屏侧边面板背景：横向「右实左淡」暗场（与 [posterCaptionScrim] 同一套 smoothstep 曲线）。
 * [contentFraction] 是面板占屏宽的比例，故等浓度区位于横向坐标 [1 - contentFraction] → 1（屏幕右侧），
 * 只在面板左缘外 [featherFraction] 这一小段内平滑归零，再往左完全透明（视频恢复原亮度）。
 * 97 段采样：淡出带在手机上仅约 0.11 屏宽，段数太少会出现阶梯。
 * 注意 Brush.horizontalGradient 的色标是「0 = 最左，1 = 最右」，不要写成 contentFraction 直接当起点。
 */
fun sideSheetScrimBrush(
    contentFraction: Float,
    plateauAlpha: Float = 0.94f,
    featherFraction: Float = 0.11f,
): Brush {
    // 等浓度区起点：从屏幕右缘往左数 contentFraction 个屏宽
    val start = (1f - contentFraction).coerceIn(0.001f, 0.995f)
    val feather = featherFraction.coerceIn(0.001f, start.coerceAtLeast(0.001f))
    val end = (start - feather).coerceAtLeast(0f)
    val stops = Array(97) { i ->
        val t = i / 96f
        val f = when {
            t >= start -> 1f        // 等浓度区（面板宽度内，贴屏幕右缘）
            t <= end -> 0f          // 淡出带以外，完全通透
            else -> {
                // q：0 = 面板左缘，1 = 淡出带外缘
                val q = (start - t) / feather
                1f - (3f * q * q - 2f * q * q * q)
            }
        }
        t to Color.Black.copy(alpha = plateauAlpha * f)
    }
    return Brush.horizontalGradient(colorStops = stops)
}


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
