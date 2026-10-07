package com.ican.tvplay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.ui.SettingsViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.BrandBlue
import com.ican.tvplay.ui.theme.BrandBlueDeep
import com.ican.tvplay.ui.theme.BrandTeal
import com.ican.tvplay.ui.theme.BrandViolet
import com.ican.tvplay.ui.theme.keyColorOptions

/**
 * 主题色板页（照搬 KernelSU 设置逻辑，UI 用项目 tv 风格重写）。
 * 第一项为「默认」（品牌蓝），其后为 15 个种子色；点击即时全局生效。
 */
@Composable
fun ColorPaletteScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(this) }
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = AppIcons.Back,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(34.dp)
                    .tvCardEffect(
                        onClick = onBack,
                        shape = CircleShape,
                        focusedScale = 1.1f,
                        glow = false,
                    )
                    .padding(5.dp),
            )
            Text(
                text = "主题色板",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 64.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
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
            }
            items(keyColorOptions) { colorInt ->
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
}

/** 单个色点按钮：选中态显示 Check + 描边光圈 */
@Composable
private fun ColorButton(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .tvCardEffect(
                onClick = onClick,
                shape = CircleShape,
                focusedScale = 1.15f,
                glow = false,
            )
            .padding(4.dp),
    ) {
        content()
        if (selected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0x55000000)),
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
