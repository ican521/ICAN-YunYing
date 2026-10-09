package com.ican.tvplay.ui.theme

import androidx.compose.ui.unit.dp

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

    /** 板块内部标题与下方内容的间距 */
    val titleContentGap = 12.dp

    /** 网格卡片横向间距 */
    val cardGapH = 12.dp

    /** 网格卡片纵向间距 */
    val cardGapV = 16.dp

    /** 全局统一圆角（卡片/按钮/输入框/弹窗）；圆形按钮与胶囊用 CircleShape，不适用此值 */
    val corner = 16.dp

    /** 圆形按钮统一尺寸 */
    val circleButton = 48.dp

    /** 底部内容避让悬浮胶囊导航的高度 */
    val bottomBarInset = 120.dp
}
