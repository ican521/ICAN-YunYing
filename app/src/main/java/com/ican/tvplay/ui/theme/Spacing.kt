package com.ican.tvplay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.utils.getRoundedCorner

/**
 * 全局 UI 间距/圆角规范（所有 Compose 页面强制遵守，禁止硬编码魔法数字）。
 *
 * 规则要点：
 * - 页面左右边距统一 [pageHorizontal]，平板等宽屏最大 [pageHorizontalMax]（内容不限宽，只保证不贴边）
 * - 页面顶部距状态栏统一 [pageTop]
 * - 大板块之间 [sectionSpacing]；板块标题与内容之间 [titleContentGap]
 * - 卡片网格横向间距 [cardGapH]、纵向间距 [cardGapV]
 * - 全局圆角统一 [corner]（圆形按钮/胶囊保持 CircleShape 不变）
 * - 圆形按钮统一尺寸 [circleButton]
 */
object Spacing {
    /** 页面左右内边距（手机） */
    val pageHorizontal = 16.dp

    /** 页面左右内边距上限（平板大屏），保证内容不贴边即可，不限制内容宽度 */
    val pageHorizontalMax = 24.dp

    /** 页面顶部距状态栏间距 */
    val pageTop = 14.dp

    /** 大板块之间的间距（今日推荐/分类Tab/视频网格等模块之间） */
    val sectionSpacing = 20.dp

    /** 首页顶部分类Tab的上下间距（搜索栏→Tab、Tab→内容区） */
    val categoryTabVertical = 3.dp

    /**
     * 搜索栏底部到下方第一个内容板块的垂直间距（全局统一，不随屏幕尺寸缩放）。
     * 所有带搜索栏的页面（首页、搜索首页、搜索结果页等）必须复用此常量，
     * 禁止各页面写死不同 dp 值。
     */
    val searchBarBottomMargin = 10.dp

    /** 板块内部标题与下方内容的间距（也是底部弹窗大标题距弹窗顶/距下方内容的间距） */
    val titleContentGap = 12.dp

    /**
     * 底部弹窗内容边距：选项方块距弹窗左右两侧 = 距弹窗底部 = 本值（固定 16dp，不分手机/平板）。
     * 全屏播放页底部功能弹窗（OptionDialog/SubtitleDialog）与选集「更多」弹窗（EpisodeGridDialog）
     * 统一引用本常量，禁止各自硬编码。
     */
    val dialogContentMargin = 16.dp

    /** 网格卡片横向间距 */
    val cardGapH = 12.dp

    /** 网格卡片纵向间距 */
    val cardGapV = 16.dp

    /** 全局统一圆角（卡片/按钮/输入框/弹窗）；圆形按钮与胶囊用 CircleShape，不适用此值 */
    val corner = 16.dp

    /**
     * miuix 弹窗同款动态圆角：跟随设备屏幕物理圆角，公式与 miuix OverlayDialog 完全一致
     * （getRoundedCorner() − 弹窗外边距 [dialogContentMargin]，钳制 32~48dp），换设备自动适配。
     * 视频卡片等需要与底部弹窗圆角保持一致的组件统一引用本值。
     */
    val dialogDeviceCorner: Dp
        @Composable get() = (getRoundedCorner() - dialogContentMargin).coerceIn(32.dp, 48.dp)

    /** 圆形按钮统一尺寸 */
    val circleButton = 48.dp

    /** 底部内容避让悬浮胶囊导航的高度 */
    val bottomBarInset = 120.dp

    /** 选项描边宽度（播放页弹窗选项与选集「更多」选项共用，全局统一） */
    val optionBorder = 1.dp
}

/**
 * 选项描边颜色（选集「更多」选项与播放页功能弹窗选项共用）：
 * 选中用主题色，未选中描 12% 中性色（深色模式白、浅色模式黑）。
 * 播放页功能弹窗仅在深色模式下描边——浅色模式底与选项底色对比已足够；
 * 选集「更多」选项沿用原有「两种模式都描边」的表现。
 */
@Composable
fun optionBorderColor(selected: Boolean): Color = when {
    selected -> MaterialTheme.colorScheme.primary
    LocalDarkTheme.current -> Color.White.copy(alpha = 0.12f)
    else -> Color.Black.copy(alpha = 0.12f)
}
