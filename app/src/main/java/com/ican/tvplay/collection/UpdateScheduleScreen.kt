package com.ican.tvplay.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.BlurredBar
import com.ican.tvplay.ui.components.LocalEnableTopBarBlur
import com.ican.tvplay.ui.components.MiuixBackButton
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.rememberBlurBackdrop
import com.ican.tvplay.ui.theme.Spacing
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

/** 每周更新日选项：索引即存储值（0=不配置，1..7=周一..周日） */
private val WeekdayOptions = listOf("不配置", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 更新提醒配置：为每部收藏设置每周几更新。
 * 启动弹窗按「今天星期几」筛出这些收藏，标题显示「周X的更新」。
 */
@Composable
fun UpdateScheduleScreen(onBack: () -> Unit = {}) {
    val viewModel = appViewModel { com.ican.tvplay.ui.UpdatesViewModel(this) }
    val favorites by viewModel.scheduleFavorites.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

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
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .height(Spacing.centeredTopBarHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = pageHorizontalPadding()),
                    ) {
                        MiuixBackButton(onClick = onBack, showBackground = false)
                    }
                    Text(
                        text = "更新提醒",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = pageHorizontalPadding(),
                    end = pageHorizontalPadding(),
                    top = innerPadding.calculateTopPadding(),
                    bottom = Spacing.contentBottomInset,
                ),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sectionSpacing),
            ) {
                if (favorites.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 64.dp, bottom = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "还没有收藏的视频",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            favorites.forEachIndexed { index, favorite ->
                                if (index > 0) HorizontalDivider()
                                OverlayDropdownPreference(
                                    title = favorite.title,
                                    summary = "",
                                    items = WeekdayOptions,
                                    selectedIndex = favorite.updateWeekday.coerceIn(0, 7),
                                    onSelectedIndexChange = { selected ->
                                        scope.launch {
                                            viewModel.setUpdateWeekday(favorite.videoId, selected)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
