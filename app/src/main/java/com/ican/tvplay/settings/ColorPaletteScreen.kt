package com.ican.tvplay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
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
import com.ican.tvplay.ui.components.topBarPadding
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.BrandBlue
import com.ican.tvplay.ui.theme.BrandBlueDeep
import com.ican.tvplay.ui.theme.BrandTeal
import com.ican.tvplay.ui.theme.BrandViolet
import com.ican.tvplay.ui.theme.CheckScrim
import com.ican.tvplay.ui.theme.Spacing
import com.ican.tvplay.ui.theme.keyColorOptions

/**
 * 主题色板页（照搬 KernelSU 设置逻辑，UI 用项目 tv 风格重写）。
 * 第一项为「默认」（品牌蓝），其后为 15 个种子色；点击即时全局生效。
 */
@Composable
fun ColorPaletteScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(this) }
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()

    // 固定居中大标题顶栏 + 毛玻璃
    val backdrop = rememberBlurBackdrop(LocalEnableTopBarBlur.current)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BlurredBar(backdrop) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(barColor)
                        .padding(topBarPadding()),
                ) {
                    // 返回按钮靠左
                    Box(modifier = Modifier.align(Alignment.CenterStart)) {
                        MiuixBackButton(onClick = onBack)
                    }
                    // 标题居中
                    Text(
                        text = "主题色板",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            val horizontal = pageHorizontalPadding()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = horizontal, end = horizontal),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + Spacing.pageTop,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 第一行：默认色 + 前 3 个色
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ColorButton(
                            selected = themeColor == 0,
                            onClick = { viewModel.setThemeColor(0) },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(BrandBlue, BrandBlueDeep, BrandTeal, BrandViolet),
                                        ),
                                    ),
                            )
                        }
                        keyColorOptions.take(3).forEach { colorInt ->
                            ColorButton(
                                selected = themeColor == colorInt,
                                onClick = { viewModel.setThemeColor(colorInt) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorInt)),
                                )
                            }
                        }
                    }
                }
                // 剩余行：每 4 个一行
                items(keyColorOptions.drop(3).chunked(4)) { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        rowColors.forEach { colorInt ->
                            ColorButton(
                                selected = themeColor == colorInt,
                                onClick = { viewModel.setThemeColor(colorInt) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorInt)),
                                )
                            }
                        }
                        // 补空保持对齐
                        repeat(4 - rowColors.size) {
                            Spacer(modifier = Modifier.size(52.dp))
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
                focusedScale = 1.15f,
                glow = false,
            ),
    ) {
        content()
        if (selected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
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
