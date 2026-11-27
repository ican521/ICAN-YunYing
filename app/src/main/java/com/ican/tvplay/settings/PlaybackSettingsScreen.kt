package com.ican.tvplay.ui.settings

import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.data.BACKGROUND_OFF
import com.ican.tvplay.data.BACKGROUND_PIP
import com.ican.tvplay.data.SettingsRepository
import com.ican.tvplay.ui.SettingsViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.MiuixBackButton
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.theme.Spacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 播放设置页（设置页「播放设置」入口进入）。
 * 视频渲染（TextureView / SurfaceView）+ 输出设置两项（照 fongmi 输出设置挑的）：
 * AAC 优先（默认关）、隧道模式（默认关，仅 Surface）+ 后台播放（关闭 / 画中画）。
 * 后续内核/解码等播放参数也归到这里，不再散落在播放页弹窗。
 */
@Composable
fun PlaybackSettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(this) }
    val render by viewModel.playerRender.collectAsStateWithLifecycle()
    val aacFirst by viewModel.playerAacFirst.collectAsStateWithLifecycle()
    val tunnel by viewModel.playerTunnel.collectAsStateWithLifecycle()
    val backgroundPlay by viewModel.playerBackgroundPlay.collectAsStateWithLifecycle()
    val surface = render == SettingsRepository.RENDER_SURFACE
    val barColor = MiuixTheme.colorScheme.surface
    val context = LocalContext.current

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
                        OverlayDropdownPreference(
                            title = "后台播放",
                            summary = if (backgroundPlay == BACKGROUND_OFF) {
                                "关闭：退出播放页即停止"
                            } else {
                                "画中画：全屏播放切后台自动进小窗继续播"
                            },
                            items = listOf("关闭", "画中画"),
                            selectedIndex = if (backgroundPlay == BACKGROUND_OFF) 0 else 1,
                            onSelectedIndexChange = { index ->
                                when (index) {
                                    0 -> viewModel.setPlayerBackgroundPlay(BACKGROUND_OFF)
                                    else -> {
                                        viewModel.setPlayerBackgroundPlay(BACKGROUND_PIP)
                                        // Android 13+ 需要运行时通知权限，后台续播的通知栏控制才显示
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            (context as? android.app.Activity)?.let { act ->
                                                androidx.core.app.ActivityCompat.requestPermissions(
                                                    act,
                                                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                                    1001,
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            title = "AAC 优先",
                            summary = "多音轨片源优先选 AAC，避免优先选中设备解不了的 AC3/EAC3",
                            checked = aacFirst,
                            onCheckedChange = { viewModel.setPlayerAacFirst(it) },
                        )
                        SwitchPreference(
                            title = "隧道模式",
                            summary = if (surface) {
                                "视频/音频走同一条 tunneled 硬件通路（需 SurfaceView，仅 EXO）"
                            } else {
                                "打开会自动切到 SurfaceView（隧道只支持 Surface 输出）"
                            },
                            checked = tunnel,
                            onCheckedChange = { viewModel.setPlayerTunnel(it) },
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
                            Text(
                                text = "AAC 优先：只影响多音轨片源选哪条（EXO 内核），关掉后按片源默认顺序选，遇到的 AC3/EAC3 可能无声。",
                                modifier = Modifier.padding(top = 8.dp),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                text = "隧道模式：画面与声音走同一条 tunneled 通路，亮度/HDR 更贴硬件能力；" +
                                    "代价是画质/音质的后处理（锐化、均衡器等）全部失效，且只有 SurfaceView + EXO 才生效。",
                                modifier = Modifier.padding(top = 8.dp),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                text = "后台播放：关闭时退出播放页就停；选画中画后全屏播放切到后台会自动缩成小窗继续播，" +
                                    "通知栏同时出现播放控制（Android 13+ 需要通知权限）。",
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
