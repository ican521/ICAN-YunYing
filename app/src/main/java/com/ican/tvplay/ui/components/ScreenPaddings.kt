package com.ican.tvplay.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import com.ican.tvplay.ui.theme.Spacing

/**
 * 页面统一水平内边距：手机 [Spacing.pageHorizontal]，平板大屏 [Spacing.pageHorizontalMax]。
 * 一级页、二级页、顶栏、内容区必须统一走此函数，禁止各页面写死 16/24dp。
 */
@Composable
fun pageHorizontalPadding(): Dp =
    if (LocalConfiguration.current.screenWidthDp >= 600) {
        Spacing.pageHorizontalMax
    } else {
        Spacing.pageHorizontal
    }

/**
 * 二级页面顶栏（返回圆钮 / 搜索栏）统一内容边距：
 * - 左右：[pageHorizontalPadding]（手机 16dp / 平板 24dp）
 * - 顶部：状态栏高度 + [Spacing.pageTop]
 * 不含 bottom —— 顶栏与下方内容的间距由各页内容区自行控制。
 */
@Composable
fun topBarPadding(): PaddingValues {
    val horizontal = pageHorizontalPadding()
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + Spacing.pageTop,
    )
}

/**
 * 一级页面（首页 / 收藏 / 设置 等带悬浮底栏的 Tab 页）统一内容边距：
 * - 左右：手机 [Spacing.pageHorizontal]，平板大屏 [Spacing.pageHorizontalMax]
 * - 顶部避开状态栏 + [Spacing.pageTop]
 * - 底部避开悬浮胶囊 [Spacing.bottomBarInset]
 */
@Composable
fun topLevelContentPadding(): PaddingValues {
    val horizontal = pageHorizontalPadding()
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + Spacing.pageTop,
        bottom = Spacing.bottomBarInset,
    )
}
