package com.ican.tvplay.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** 一级页面（首页 / 搜索 / 收藏 / 历史 / 设置）统一内容边距：顶部避开状态栏，底部避开悬浮胶囊 */
@Composable
fun topLevelContentPadding(): PaddingValues = PaddingValues(
    start = 16.dp,
    end = 16.dp,
    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
    bottom = 120.dp,
)
