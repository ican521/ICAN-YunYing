package com.ican.tvplay.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
 * - 页面左右边距统一 [pageHorizontal]（手机/平板均为 16dp）
 * - 页面顶部距状态栏统一 [pageTop]
 * - 大板块之间 [sectionSpacing]；板块标题与内容之间 [titleContentGap]
 * - 卡片网格横向间距 [cardGapH]、纵向间距 [cardGapV]
 * - 全局圆角统一 [corner]（圆形按钮/胶囊保持 CircleShape 不变）
 * - 圆形按钮统一尺寸 [circleButton]
 */
object Spacing {
    /** 页面左右内边距（全设备统一） */
    val pageHorizontal = 16.dp

    /** 页面顶部距状态栏的间距（所有页面内容首行与顶栏/搜索栏底部的视觉间距统一 12dp） */
    val pageTop = 12.dp

    /** 大板块之间的间距（今日推荐/分类Tab/视频网格等模块之间） */
    val sectionSpacing = 16.dp

    /**
     * 首页顶部分类Tab的上下间距（搜索栏→Tab、Tab→内容区）。
     * 取 4dp：Tab 行内文字垂直居中留白 11dp，合计后「Tab 文字 ↔ 搜索胶囊底缘」视觉间距为 15dp，上下对称。
     */
    val categoryTabVertical = 4.dp

    /** 板块内部标题与下方内容的间距（播放页底部弹窗的大标题间距也用此值） */
    val titleContentGap = 12.dp

    /**
     * 播放页「类型信息块 → 线路」板块边界间距：13dp。
     * 比常规板块间距（16dp）更紧，叠加上下文字字形留白后视觉约 19dp。
     */
    val videoMetaToLineGap = 13.dp

    /**
     * 播放页「视频下缘 → 视频标题行」的行框间距：11dp。
     * 收藏钮已缩为 32dp（≈标题行高），不再撑出行内居中留白，
     * 叠加字形自身留白后，11dp 行框间距呈现约 16dp 的视觉间距。
     */
    val videoTitleRowTopGap = 11.dp

    /**
     * 播放页「视频标题行 → 下方信息行」的行框间距：8dp。
     * 补偿收藏钮缩小后丢失的行内居中留白，视觉约 16dp。
     */
    val videoTitleToMetaGap = 8.dp

    /**
     * 底部弹窗（全屏播放页除外）大标题距弹窗顶部 / 距下方内容的间距，上下统一。
     */
    val dialogTitleGap = 16.dp

    /**
     * 底部弹窗内容边距：内容距弹窗左右两侧 = 距弹窗底部 = 本值（固定 16dp，不分手机/平板）。
     * 除全屏播放页外的所有底部弹窗统一引用本常量；播放页弹窗
     * （OptionDialog/SubtitleDialog/EpisodeGridDialog）也复用本值。
     */
    val dialogContentMargin = 16.dp

    /** 底部弹窗距屏幕左右 / 底部的固定边距（底部由 [dialogBottomMargin] 补偿系统导航栏） */
    val dialogOutsideMargin = 16.dp

    /** 底部弹窗列表项内边距（四边统一） */
    val dialogItemPadding = 16.dp

    /** 底部弹窗列表项之间的间距 */
    val dialogItemGap = 8.dp

    /** 选集 chip 高度（四边 [dialogItemPadding] + 单行文字的最小高度）；选集网格分页与弹窗高度按此值换算 */
    val episodeChipHeight = 48.dp

    /** 居中大标题顶栏高度（状态栏以下）：52dp，与首页顶栏收起态总高（Tab 上下 5dp + 42dp）一致 */
    val centeredTopBarHeight = 52.dp

    /** 标题在顶栏内的顶部偏移：按可见状态栏内容下缘对齐，使上下视觉间隙相等 */
    val centeredTopBarTitleInset = 5.dp

    /** 底部弹窗内并排按钮（取消/确认等）之间的间距 */
    val dialogButtonGap = 16.dp

    /** 网格卡片横向间距（首页/收藏/历史；搜索页用 [searchGridGap]） */
    val cardGapH = 16.dp

    /** 网格卡片纵向间距（首页/收藏/历史；搜索页用 [searchGridGap]） */
    val cardGapV = 16.dp

    /** 追番更新页海报圆角（连续曲率）：板块卡片内的预览小图统一使用 */
    val updateCardCorner = 10.dp

    /** 海报叠加层（角标 / 编辑删除钮）距海报边缘的外边距 */
    val posterOverlayMargin = 8.dp

    /** 海报角标内边距（水平） */
    val posterBadgePaddingH = 6.dp

    /** 海报角标内边距（垂直） */
    val posterBadgePaddingV = 2.dp

    /** 海报底部标题层内边距（水平） */
    val posterTitlePaddingH = 10.dp

    /** 海报底部标题层内边距（垂直） */
    val posterTitlePaddingV = 6.dp

    /**
     * 搜索结果页专属网格间距：卡片横向、卡片纵向、左侧站源列表与卡片网格之间的间距统一用本值。
     * 搜索页内容密度高（左站源列表 + 右网格），间距独立于 [cardGapH]/[cardGapV]，不随主网格调整。
     */
    val searchGridGap = 16.dp

    /** 搜索首页历史 / 推荐词条胶囊的流式排列间距（横向、纵向同值） */
    val searchChipGap = 8.dp

    /** 词条胶囊固定高度（上下留白由高度居中产生，左右用 16dp 内边距） */
    val chipHeight = 40.dp

    /** 搜索结果页左侧站源列表的项间距（纵向） */
    val siteListItemGap = 8.dp

    /** 全局统一圆角（卡片/按钮/输入框/弹窗）；圆形按钮与胶囊用 CircleShape，不适用此值 */
    val corner = 16.dp

    /** 右侧滑出面板（选集/字幕设置侧边栏）外围圆角：只圆左两角，与 miuix 底部弹窗本体 32dp 同值 */
    val sideSheetCorner = 32.dp

    /**
     * miuix 弹窗同款动态圆角：跟随设备屏幕物理圆角，公式与 miuix OverlayDialog 完全一致
     * （getRoundedCorner() − 弹窗外边距 [dialogContentMargin]，钳制 32~48dp），换设备自动适配。
     * 视频卡片等需要与底部弹窗圆角保持一致的组件统一引用本值。
     */
    val dialogDeviceCorner: Dp
        @Composable get() = (getRoundedCorner() - dialogContentMargin).coerceIn(32.dp, 48.dp)

    /** 圆形按钮统一尺寸 */
    val circleButton = 48.dp

    /** 顶栏行高（返回钮 / 搜索栏 / 圆形按钮所在行） */
    val topBarHeight = 48.dp

    /** 顶栏内部组件之间的间距（搜索栏 ↔ 返回钮、站源钮、历史钮等） */
    val topBarItemGap = 12.dp

    /**
     * 底部内容避让悬浮胶囊导航的高度（保证底部卡片距底栏顶部恰好 [bottomBarGap]）：
     * 胶囊容器高度 [bottomBarHeight] + 胶囊距屏幕底部（[bottomBarBottom]，但不小于系统导航栏高度）
     * + 内容与胶囊顶部间距 [bottomBarGap]。与 AppRoot 中底栏定位使用同一规则。
     */
    val bottomBarInset: Dp
        @Composable get() {
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val barBottom = maxOf(bottomBarBottom, navBottom)
            return barBottom + bottomBarHeight + bottomBarGap
        }

    /** 悬浮胶囊导航距屏幕底部的距离（系统导航栏更高时以导航栏高度为准，取二者较大值） */
    val bottomBarBottom = 16.dp

    /** 悬浮胶囊导航容器高度（含内部 4dp 内边距） */
    val bottomBarHeight = 64.dp

    /** 页面底部内容与悬浮胶囊导航顶部的间距 */
    val bottomBarGap = 16.dp

    /** 悬浮底栏胶囊内部的内边距（内容区上下、指示器左右） */
    val bottomBarInnerPadding = 4.dp

    /**
     * 无悬浮底栏页面的列表/网格内容底部留白（滚动到底时最后一行与屏幕底部之间）：
     * 搜索结果页的站源列表与卡片网格统一引用本值。
     * 带悬浮底栏的一级页（首页/收藏/历史/设置）底部改用 [bottomBarInset]。
     */
    val contentBottomInset = 16.dp

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
