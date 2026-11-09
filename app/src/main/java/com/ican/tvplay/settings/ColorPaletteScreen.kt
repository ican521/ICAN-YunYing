package com.ican.tvplay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.ui.SettingsViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.BlurredBar
import com.ican.tvplay.ui.components.LocalEnableTopBarBlur
import com.ican.tvplay.ui.components.MiuixBackButton
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.rememberBlurBackdrop
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.BrandBlue
import com.ican.tvplay.ui.theme.BrandBlueDeep
import com.ican.tvplay.ui.theme.BrandTeal
import com.ican.tvplay.ui.theme.BrandViolet
import com.ican.tvplay.ui.theme.CheckScrim
import com.ican.tvplay.ui.theme.Spacing
import com.ican.tvplay.ui.theme.keyColorOptions

/** 色点直径与列间距（列按屏幕宽度自适应排布） */
private val ColorDotSize = 52.dp
private val ColumnGap = 16.dp

/**
 * 主题色板页（照搬 KernelSU 设置逻辑，UI 用项目 tv 风格重写）。
 * 第一项为「默认」（品牌蓝），其后为按色系分组的 27 个种子色；点击即时全局生效。
 */
@Composable
fun ColorPaletteScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(this) }
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()

    // 固定居中大标题顶栏 + 毛玻璃
    val backdrop = rememberBlurBackdrop(LocalEnableTopBarBlur.current)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        // 整窗底色（含状态栏 / 导航条区域）与设置页一致
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            BlurredBar(backdrop) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(barColor)
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .height(Spacing.centeredTopBarHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    // 返回按钮靠左
                    Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = pageHorizontalPadding())) {
                        MiuixBackButton(onClick = onBack, showBackground = false)
                    }
                    // 标题居中
                    Text(
                        text = "主题色板",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleLarge,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 页面底色与设置页一致（主题 background 的浅灰白），避免露出窗口纯白
                .background(MiuixTheme.colorScheme.surface)
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            val horizontal = pageHorizontalPadding()
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // 列数按可用宽度自适应：每列一个 52dp 色点 + 16dp 列间距，至少 4 列
                val columns = ((maxWidth - horizontal * 2 + ColumnGap) / (ColorDotSize + ColumnGap))
                    .toInt()
                    .coerceAtLeast(4)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = horizontal, end = horizontal),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = Spacing.contentBottomInset,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(ColumnGap),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
                ) {
                    // 第一格：默认色
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ColorButton(
                                selected = themeColor == 0,
                                onClick = { viewModel.setThemeColor(0) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(ColorDotSize)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(BrandBlue, BrandBlueDeep, BrandTeal, BrandViolet),
                                            ),
                                        ),
                                )
                            }
                        }
                    }
                    // 其余：按色系分组排列，每行数量随屏幕宽度变化
                    items(keyColorOptions) { colorInt ->
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ColorButton(
                                selected = themeColor == colorInt,
                                onClick = { viewModel.setThemeColor(colorInt) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(ColorDotSize)
                                        .clip(CircleShape)
                                        .background(Color(colorInt)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单个色点按钮：选中态显示 Check + 描边光圈 */
@Composable
private fun ColorButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .tvCardEffect(
                onClick = onClick,
                shape = CircleShape,
                focusedScale = 1.1f,
                glow = false,
            ),
    ) {
        content()
        if (selected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ColorDotSize)
                    .clip(CircleShape)
                    .background(CheckScrim),
            ) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = "已选中",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
