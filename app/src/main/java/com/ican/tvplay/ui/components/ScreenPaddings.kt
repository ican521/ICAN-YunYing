package com.ican.tvplay.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.ican.tvplay.ui.theme.Spacing

/**
 * 一级页面（首页 / 搜索 / 收藏 / 历史 / 设置）统一内容边距：
 * - 左右：手机 [Spacing.pageHorizontal]，平板大屏 [Spacing.pageHorizontalMax]
 * - 顶部避开状态栏 + [Spacing.pageTop]
 * - 底部避开悬浮胶囊 [Spacing.bottomBarInset]
 */
@Composable
fun topLevelContentPadding(): PaddingValues {
    val horizontal = if (LocalConfiguration.current.screenWidthDp >= 600) {
        Spacing.pageHorizontalMax
    } else {
        Spacing.pageHorizontal
    }
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + Spacing.pageTop,
        bottom = Spacing.bottomBarInset,
    )
}
