package com.ican.tvplay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.data.SettingsRepository
import com.ican.tvplay.ui.SettingsViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.MiuixBackButton
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.theme.Spacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 播放设置页（设置页「播放设置」入口进入）。
 * 目前只有「视频渲染」一项：TextureView（默认）/ SurfaceView（HDR 直通）。
 * 后续内核/解码等播放参数也归到这里，不再散落在播放页弹窗。
 */
@Composable
fun PlaybackSettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(this) }
    val render by viewModel.playerRender.collectAsStateWithLifecycle()
    val surface = render == SettingsRepository.RENDER_SURFACE
    val barColor = MiuixTheme.colorScheme.surface

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
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
                    text = "播放设置",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleLarge,
                    color = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface),
        ) {
            val horizontal = pageHorizontalPadding()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = horizontal,
                    end = horizontal,
                    top = innerPadding.calculateTopPadding(),
                    bottom = Spacing.contentBottomInset,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        OverlayDropdownPreference(
                            title = "视频渲染",
                            summary = if (surface) {
                                "SurfaceView（HDR 直通）"
                            } else {
                                "TextureView（默认，缩放跟手）"
                            },
                            items = listOf("TextureView", "SurfaceView"),
                            selectedIndex = if (surface) 1 else 0,
                            onSelectedIndexChange = { index ->
                                viewModel.setPlayerRender(
                                    if (index == 1) {
                                        SettingsRepository.RENDER_SURFACE
                                    } else {
                                        SettingsRepository.RENDER_TEXTURE
                                    },
                                )
                            },
                        )
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "TextureView：画面作为普通 View 参与合成，横屏小窗↔全屏缩放跟手、支持圆角，" +
                                    "但 10bit/PQ 在应用内转成 SDR，HDR 片源看不到真 HDR。",
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                text = "SurfaceView：画面交给系统合成器独立出层，HDR10/HLG 片源的 10bit/PQ 可直通到屏幕；" +
                                    "代价是缩放动画可能滞后一帧、圆角裁剪不生效，播完的静帧替身也不再抓取。",
                                modifier = Modifier.padding(top = 8.dp),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
}
