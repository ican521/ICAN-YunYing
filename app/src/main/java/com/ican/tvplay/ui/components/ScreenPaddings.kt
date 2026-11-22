package com.ican.tvplay.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ican.tvplay.data.HomeLayoutSize
import com.ican.tvplay.ui.theme.Spacing

/**
 * 页面统一水平内边距：[Spacing.pageHorizontal]（全设备统一 16dp）。
 * 一级页、二级页、顶栏、内容区必须统一走此函数，禁止各页面写死 16dp。
 */
@Composable
fun pageHorizontalPadding(): Dp = Spacing.pageHorizontal

/**
 * 固定顶栏（返回圆钮 / 搜索栏 / 居中大标题）统一内容边距：
 * - 左右：[pageHorizontalPadding]（全设备统一 16dp）
 * - 顶部：仅状态栏高度 —— 顶栏内容紧贴状态栏下缘，与收藏/设置页同一基准
 * 不含 bottom —— 顶栏与下方内容的 [Spacing.pageTop] 间距由各页内容区自行控制。
 */
@Composable
fun topBarPadding(): PaddingValues {
    val horizontal = pageHorizontalPadding()
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
    )
}

/**
 * 一级页面（首页 / 收藏 / 设置 等带悬浮底栏的 Tab 页）统一内容边距：
 * - 左右：[Spacing.pageHorizontal]
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

/**
 * 「默认（自动）」档下普通卡片在当前屏幕上会排出的列数 —— 供设置页「普通卡片列数」生成候选范围用：
 * 手机（<600dp）：小=3列、中=2列；平板按最小卡宽自适应（中≈5列 @1069dp 横屏，3列 @668dp 竖屏）。
 */
@Composable
fun defaultPosterColumns(size: HomeLayoutSize): Int =
    defaultPosterColumns(LocalConfiguration.current.screenWidthDp, size)

/**
 * 同上，但显式给定屏宽（dp）—— 设置页在横屏/竖屏两种姿态下各自预判默认列数时用。
 */
fun defaultPosterColumns(widthDp: Int, size: HomeLayoutSize): Int =
    if (widthDp < 600) {
        if (size == HomeLayoutSize.SMALL) 3 else 2
    } else {
        val available = (widthDp - 2 * Spacing.pageHorizontal.value).toFloat()
        val gap = Spacing.cardGapH.value
        val minCard: Float = when (size) {
            HomeLayoutSize.MEDIUM -> 190f
            HomeLayoutSize.SMALL -> 132f
            HomeLayoutSize.LARGE -> 300f
        }
        kotlin.math.floor((available + gap) / (minCard + gap)).toInt().coerceAtLeast(2)
    }

/**
 * 视频网格统一列数规则（首页 / 收藏 / 历史共用），跟随「设置-首页布局大小」：
 * 手机（<600dp）：小=3列、中=2列（大不开放）。
 * 平板：全部按「最小卡宽」自适应，列数随屏幕宽度自动变化（对齐 OK猫「卡片物理尺寸固定、列数由宽度算」的思路），
 * 这样横竖屏切换、不同尺寸的平板都能保持同样的卡片观感，不会一台偏大一台偏小。
 * 中号取 190dp：Compose 的 Adaptive 是 floor((可用宽 + 间距) / (最小卡宽 + 间距))，
 * 可用宽要先扣掉页面左右各 16dp、再扣卡片间距 16dp——
 * 1069dp 横屏：(1037+16)/206≈5.1 → 5 列；668dp 竖屏：(636+16)/206≈3.2 → 3 列。
 *
 * @param override 用户在设置页「普通卡片列数」里选的固定列数；null = 默认（上面的自动算法）
 */
@Composable
fun videoGridColumns(size: HomeLayoutSize, override: Int? = null): GridCells {
    if (override != null) return GridCells.Fixed(override)
    return if (LocalConfiguration.current.screenWidthDp < 600) {
        if (size == HomeLayoutSize.SMALL) GridCells.Fixed(3) else GridCells.Fixed(2)
    } else {
        when (size) {
            HomeLayoutSize.MEDIUM -> GridCells.Adaptive(minSize = 190.dp)
            HomeLayoutSize.SMALL -> GridCells.Adaptive(minSize = 132.dp)
            HomeLayoutSize.LARGE -> GridCells.Adaptive(minSize = 300.dp)
        }
    }
}

/**
 * 「默认（自动）」档下宽卡（4:3，爱刷 / 易直播这类短视、直播源）的列数：
 * 取普通卡片列数的 0.6 倍（最少 2 列）——4:3 宽卡比 5:7 竖卡矮，同屏列数少一档才保持相近观感。
 * 1069dp 横屏（竖卡 5 列）→ 3 列；更大屏（竖卡 7 列）→ 4 列；668dp 竖屏（竖卡 3 列）→ 2 列。
 */
@Composable
fun defaultWideColumns(size: HomeLayoutSize): Int =
    defaultWideColumns(LocalConfiguration.current.screenWidthDp, size)

/**
 * 同上，但显式给定屏宽（dp）。
 */
fun defaultWideColumns(widthDp: Int, size: HomeLayoutSize): Int =
    kotlin.math.round(defaultPosterColumns(widthDp, size) * 0.6f).toInt().coerceAtLeast(2)

/**
 * 横版宽卡的最终列数。
 *
 * @param override 用户在设置页「宽卡列数」里选的固定列数；null = 默认（[defaultWideColumns] 的自动值）
 */
@Composable
fun wideCardGridColumns(size: HomeLayoutSize, override: Int? = null): GridCells =
    GridCells.Fixed(override ?: defaultWideColumns(size))
