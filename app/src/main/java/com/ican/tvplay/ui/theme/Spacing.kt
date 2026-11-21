package com.ican.tvplay.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.getRoundedCorner

/**
 * 全局 UI 间距/圆角规范（所有 Compose 页面强制遵守，禁止硬编码魔法数字）。
 *
 * ## 间距档位表（新增间距只能从下表中挑，不得引入 3dp/5dp/15dp 这类中间值）
 * | 档位 | 用途 | 对应常量 |
 * | ---- | ---- | ---- |
 * | 2dp | 极小元素内部微调（角标上下、分类 Tab 上下） | [categoryTabVertical]、[posterBadgePaddingV] |
 * | 4dp | 小元素内部 / 胶囊内衬 / 横卡文字区上下内衬 | [posterTagRowTopGap]、[bottomBarInnerPadding]、[searchWideCardPaddingV] |
 * | 6dp | 角标级别左右内边距、卡片标题层顶部 | [posterBadgePaddingH]、[posterTitleTopGap] |
 * | 8dp | 相邻元素（标题↔chips 或正文、列表项、胶囊流式排列、叠加层外边距、卡片标题层底部） | [videoTitleContentGap]、[videoTitleToMetaGap]、[dialogItemGap]、[searchChipGap]、[siteListItemGap]、[posterOverlayMargin]、[posterTitleVisualGap]
 * | 10dp | 卡片标题层水平、历史卡片进度条底距（紧贴卡片边） | [posterTitlePaddingH]、[historyProgressBarGap] |
 * | 12dp | 页面骨架次级（页面顶部、板块标题↔内容、顶栏组件之间、横卡文字区内衬） | [pageTop]、[titleContentGap]、[topBarItemGap]、[searchWideCardPadding] |
 * | 16dp | 页面骨架主级（页面左右、板块之间、卡片网格、弹窗边距与标题、底栏上下） | [pageHorizontal]、[sectionSpacing]、[cardGapH]、[cardGapV]、[dialogContentMargin]、[dialogTitleGap]、[dialogItemPadding]、[bottomBarGap]、[contentBottomInset] 等 |
 *
 * 尺寸类（非间距，按内容或控件固定高度取值）：[chipHeight]、[episodeChipHeight]、[circleButton]、[topBarHeight]、[centeredTopBarHeight]、[bottomBarHeight]、[searchWideCardHeight]。
 *
 * ## 其他规则
 * - 页面左右边距统一 [pageHorizontal]（手机/平板均为 16dp）
 * - 页面顶部距状态栏统一 [pageTop]
 * - 大板块之间 [sectionSpacing]；板块标题与内容之间 [titleContentGap]
 * - 卡片网格横向间距 [cardGapH]、纵向间距 [cardGapV]
 * - 全局圆角统一 [corner]（圆形按钮/胶囊保持 CircleShape 不变）
 * - 圆形按钮统一尺寸 [circleButton]
 * - 1.0.79 起全局文字已 Trim 字形上下留白（见 Theme 的 inkTightTypography），布局值即视觉值，不再做额外补偿
 */
object Spacing {
    /** 页面左右内边距（全设备统一） */
    val pageHorizontal = 16.dp

    /** 页面顶部距状态栏的间距（所有页面内容首行与顶栏/搜索栏底部的视觉间距统一 12dp） */
    val pageTop = 12.dp

    /** 大板块之间的间距（今日推荐/分类Tab/视频网格等模块之间） */
    val sectionSpacing = 16.dp

    /**
     * 首页顶部分类Tab与上（搜索栏）/下（内容区）的间距：2dp。
     * 旧版视觉 15dp = 4dp 布局 + 行内居中留白约 11dp；全局文字 Trim 后行盒变矮，
     * 行内留白自动涨到约 13dp，故补 2dp 即回到旧版 15dp 的视觉（Tab 行高固定 42dp，文字居中）。
     */
    val categoryTabVertical = 2.dp

    /** 板块内部标题与下方内容的间距（播放页底部弹窗的大标题间距也用此值） */
    val titleContentGap = 12.dp

    /**
     * 播放页板块标题↔其下方内容（线路/画质/选集/简介的标题→chips 或正文）：8dp。
     * 对齐 fongmi `activity_video.xml`：标题 marginTop 16dp + 内容 marginTop 8dp 的原始口径，
     * 不做字形留白补偿（fongmi 本身不区分「文字对文字」与「文字对 chip」）。
     */
    val videoTitleContentGap = 8.dp

    /**
     * 播放页「视频下缘 → 视频标题行」的行框间距：16dp（fongmi paddingTop 同值）。
     * 不再做字形留白补偿。
     */
    val videoTitleRowTopGap = 16.dp

    /**
     * 播放页「视频标题行 → 下方信息行」的行框间距：8dp（fongmi 逐行 marginTop 8dp）。
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

    /** 底部弹窗内并排按钮（取消/确认等）之间的间距 */
    val dialogButtonGap = 16.dp

    /** 网格卡片横向间距（首页/收藏/历史；搜索页用 [searchGridGap]） */
    val cardGapH = 16.dp

    /** 网格卡片纵向间距（首页/收藏/历史；搜索页用 [searchGridGap]） */
    val cardGapV = 16.dp

    /** 追番更新页海报圆角（连续曲率）：板块卡片内的预览小图统一使用 */
    val updateCardCorner = 10.dp

    /**
     * 海报叠加层（左上/右上角标胶囊、编辑删除钮）距海报边缘的外边距：8dp（档位表 8 档）。
     * 胶囊底是绘制出来的，没有字形下沉，布局值即视觉值。
     */
    val posterOverlayMargin = 8.dp

    /** 海报角标内边距（水平） */
    val posterBadgePaddingH = 6.dp

    /** 海报角标内边距（垂直） */
    val posterBadgePaddingV = 2.dp

    /**
     * 海报底部标题层内边距（水平）：10dp（档位表 10 档）。
     * 汉字左侧字面约 1.3dp、基线下墨迹约 3.1dp，故 水平 10 + 上下 8 才能让墨迹
     * 到左边 ≈ 11.3dp、到底边 ≈ 11.1dp，四边视觉齐平。
     */
    val posterTitlePaddingH = 10.dp

    /**
     * 海报底部标题层底部留白：8dp（档位表 8 档）。
     * 全局文字已 Trim 掉行盒留白，但汉字墨迹仍在基线下约 3dp，故 8dp 布局 ≈ 墨迹离卡片底边 10dp，
     * 与水平 10dp 在视觉上齐平。
     */
    val posterTitleVisualGap = 8.dp

    /**
     * 海报底部标题层顶部留白：6dp（档位表 6 档）。
     * 上方紧邻标签胶囊，胶囊自带 2dp 垂直内衬，留白收得比底部（8dp）更紧，
     * 墨迹距离 ≈ 9.3dp，视觉上标题更贴近上方的标签胶囊。
     */
    val posterTitleTopGap = 6.dp

    /**
     * 播放历史卡片底部播放进度条距卡片底边的距离：10dp（档位表 10 档）。
     * 进度条是绘制元素、没有字形下沉，故布局值即视觉值。
     */
    val historyProgressBarGap = 10.dp

    /** 海报标签行（标题上方胶囊）与上方内容的间距 */
    val posterTagRowTopGap = 4.dp

    /**
     * 卡片底部标题行固定高度：32dp（8dp 上衬 + 14sp 文字 + 8dp 下衬，另留 2dp 余量）。
     * 整卡按 7:10 定高后，海报高度 = 卡高 - 该值，保证标题永远一行、卡片比例恒定。
     */
    val posterTitleBandHeight = 32.dp

    /** 浅色模式下卡片外圈投影高度：12dp（只在浅色模式生效，卡片浮起感更强） */
    val cardShadowElevation = 12.dp

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

    /** 跨站源搜索结果「长条横卡」高度（尺寸类，对齐 fongmi adapter_search 的 128dp 视觉，留 3:4 海报 84×112） */
    val searchWideCardHeight = 112.dp

    /** 跨站源搜索结果「长条横卡」文字区左右内边距（12 档） */
    val searchWideCardPadding = 12.dp

    /** 跨站源搜索结果「长条横卡」文字区上下内边距（4dp：与站源胶囊内的行距感觉一致） */
    val searchWideCardPaddingV = 4.dp

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
    selected -> MiuixTheme.colorScheme.primary
    LocalDarkTheme.current -> Color.White.copy(alpha = 0.12f)
    else -> Color.Black.copy(alpha = 0.12f)
}
