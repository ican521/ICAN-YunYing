package com.ican.tvplay.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

    // 进入页面时排一次序：已配置星期的排前面，未配置的排后面；
    // 记录下顺序后不再重排，避免页面内改星期时当前行突然跳走
    val orderIds = remember { mutableStateListOf<String>() }
    LaunchedEffect(favorites) {
        val ids = favorites.map { it.videoId }
        if (orderIds.isEmpty()) {
            orderIds.addAll(
                favorites
                    // 已配置的按周一→周日排前面，未配置的排最后
                    .sortedBy { if (it.updateWeekday == 0) 8 else it.updateWeekday }
                    .map { it.videoId },
            )
        } else if (orderIds.any { it !in ids }) {
            orderIds.removeAll { it !in ids }
        }
    }
    val favoritesById = favorites.associateBy { it.videoId }
    val orderedFavorites = orderIds.mapNotNull { favoritesById[it] } +
        favorites.filter { it.videoId !in orderIds }

    val backdrop = rememberBlurBackdrop(LocalEnableTopBarBlur.current)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
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
                        color = MiuixTheme.colorScheme.onBackground,
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
                verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
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
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                } else {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            orderedFavorites.forEachIndexed { index, favorite ->
                                if (index > 0) {
                                    // 分隔线左右各缩 16dp，与卡片内容边距一致
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                                ScheduleRow(
                                    title = favorite.title,
                                    selected = favorite.updateWeekday.coerceIn(0, 7),
                                    onSelect = { weekday ->
                                        scope.launch {
                                            viewModel.setUpdateWeekday(favorite.videoId, weekday)
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

/**
 * 单行配置：保留原生 OverlayDropdownPreference（行高、右侧「不配置 ⌄」和星期选择弹窗都不变）。
 * 组件自带的标题行 + 空副标题行会把标题顶在上半部分，所以这里传空占位，
 * 再把真实标题左对齐叠在整行上做垂直居中，与右侧选项同一条中线。
 */
@Composable
private fun ScheduleRow(
    title: String,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OverlayDropdownPreference(
            title = " ",
            // 传 null 才不渲染副标题行；空串仍会占一行高度，导致标题到分隔线看起来远超 16dp
            summary = null,
            items = WeekdayOptions,
            selectedIndex = selected,
            onSelectedIndexChange = onSelect,
            // 每个选项卡片内边距统一 16dp
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        )
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                // 左侧距卡片 16dp，右侧给选项留出空间避免长标题重叠
                .padding(start = 16.dp, end = 96.dp),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
