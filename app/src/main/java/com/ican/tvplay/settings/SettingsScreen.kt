package com.ican.tvplay.ui.settings

import android.app.Activity
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.ican.tvplay.data.BACKGROUND_OFF
import com.ican.tvplay.TvPlayApplication
import com.ican.tvplay.data.BACKGROUND_ON
import com.ican.tvplay.data.BACKGROUND_PIP
import com.ican.tvplay.data.HomeLayoutSize
import com.ican.tvplay.data.ThemeMode
import com.ican.tvplay.data.remote.TvBoxSite
import com.ican.tvplay.ui.SettingsViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.BlurredBar
import com.ican.tvplay.ui.components.LocalEnableBlur
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.rememberBlurBackdrop
import com.ican.tvplay.ui.theme.Spacing
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun SettingsScreen(
    onColorPaletteClick: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val viewModel = appViewModel { SettingsViewModel(this) }
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
    val enableBlur by viewModel.enableBlur.collectAsStateWithLifecycle()
    val enablePredictiveBack by viewModel.enablePredictiveBack.collectAsStateWithLifecycle()
    val backgroundPlay by viewModel.playerBackgroundPlay.collectAsStateWithLifecycle()
    val homeLayoutSize by viewModel.homeLayoutSize.collectAsStateWithLifecycle()
    val configUrl by viewModel.configUrl.collectAsStateWithLifecycle()
    val siteName by viewModel.siteName.collectAsStateWithLifecycle()
    val currentSiteKey by viewModel.currentSiteKey.collectAsStateWithLifecycle()
    val siteToggleVersion by viewModel.siteToggleVersion.collectAsStateWithLifecycle()
    var showConfigDialog by remember { mutableStateOf(false) }
    var showSiteDialog by remember { mutableStateOf(false) }
    var siteList by remember { mutableStateOf<List<TvBoxSite>>(emptyList()) }
    LaunchedEffect(showSiteDialog) {
        if (showSiteDialog) siteList = viewModel.listSites()
    }
    val context = LocalContext.current

    // Android 13+ 需要运行时通知权限，后台播放的通知栏控制才能显示
    val requestNotificationPermission = {
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

    val horizontal = pageHorizontalPadding()
    // 手机（<600dp）只提供小/中两档；平板额外提供大（约3列）
    val layoutOptions = if (LocalContext.current.resources.configuration.screenWidthDp >= 600) {
        listOf("小", "中", "大")
    } else {
        listOf("小", "中")
    }
    // KernelSU 同款：折叠顶栏 + 毛玻璃（关闭毛玻璃时顶栏退化为页面背景色）
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlurState = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlurState)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = "设置",
                    titleColor = MaterialTheme.colorScheme.onBackground,
                    largeTitleColor = MaterialTheme.colorScheme.onBackground,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = horizontal,
                end = horizontal,
                top = innerPadding.calculateTopPadding() + Spacing.pageTop,
                bottom = Spacing.bottomBarInset,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
        ) {
        item {
        Card(modifier = Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                title = "主题模式",
                items = listOf("跟随系统", "浅色", "深色"),
                selectedIndex = when (themeMode) {
                    ThemeMode.SYSTEM -> 0
                    ThemeMode.LIGHT -> 1
                    ThemeMode.DARK -> 2
                },
                onSelectedIndexChange = { index ->
                    viewModel.setThemeMode(
                        when (index) {
                            0 -> ThemeMode.SYSTEM
                            1 -> ThemeMode.LIGHT
                            else -> ThemeMode.DARK
                        },
                    )
                },
            )
        }
        }

        item {
        Card(modifier = Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                title = "首页网格大小",
                items = layoutOptions,
                selectedIndex = when (homeLayoutSize) {
                    HomeLayoutSize.SMALL -> 0
                    HomeLayoutSize.MEDIUM -> 1
                    HomeLayoutSize.LARGE -> 2
                }.coerceAtMost(layoutOptions.lastIndex),
                onSelectedIndexChange = { index ->
                    viewModel.setHomeLayoutSize(
                        when (index) {
                            0 -> HomeLayoutSize.SMALL
                            1 -> HomeLayoutSize.MEDIUM
                            else -> HomeLayoutSize.LARGE
                        },
                    )
                },
            )
        }
        }

        item {
        // KernelSU 同款：miuix Card + preference 行组，卡片内无标题
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "主题色板",
                summary = if (themeColor == 0) "默认" else "自定义",
                startAction = {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(14.dp)
                            .background(
                                if (themeColor == 0) MaterialTheme.colorScheme.primary else Color(themeColor),
                                CircleShape,
                            ),
                    )
                },
                onClick = onColorPaletteClick,
            )
            SwitchPreference(
                title = "全局模糊",
                summary = "底部导航液态玻璃效果",
                checked = enableBlur,
                onCheckedChange = { viewModel.setEnableBlur(it) },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                SwitchPreference(
                    title = "预测性返回",
                    summary = "侧滑返回时预览返回效果",
                    checked = enablePredictiveBack,
                    onCheckedChange = { enabled ->
                        // KernelSU 同款：反射立即应用 + recreate 当前 Activity 重新注册返回回调，无需重启 App
                        viewModel.setEnablePredictiveBack(enabled)
                        TvPlayApplication.applyPredictiveBack(context.applicationInfo, enabled)
                        (context as? Activity)?.recreate()
                    },
                )
            }
        }
        }

        item {
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "导入配置",
                summary = configUrl.ifEmpty { "未设置" },
                onClick = { showConfigDialog = true },
            )
            ArrowPreference(
                title = "站源",
                summary = siteName,
                onClick = { showSiteDialog = true },
            )
        }
        }

        item {
        Card(modifier = Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                title = "后台播放",
                summary = "开启：退出播放页后继续播放并可通知栏控制；画中画：全屏播放时切后台自动进入小窗",
                items = listOf("关闭", "开启", "画中画"),
                selectedIndex = when (backgroundPlay) {
                    BACKGROUND_OFF -> 0
                    BACKGROUND_ON -> 1
                    else -> 2
                },
                onSelectedIndexChange = { index ->
                    when (index) {
                        0 -> viewModel.setPlayerBackgroundPlay(BACKGROUND_OFF)
                        1 -> {
                            viewModel.setPlayerBackgroundPlay(BACKGROUND_ON)
                            requestNotificationPermission()
                        }
                        else -> {
                            viewModel.setPlayerBackgroundPlay(BACKGROUND_PIP)
                            requestNotificationPermission()
                        }
                    }
                },
            )
        }
        }

        item {
        Card(modifier = Modifier.fillMaxWidth()) {
            InfoRow("应用名称", "ICAN云影")
            InfoRow("版本", "0.1.0")
        }
        }
        }
        }
    }

    if (showConfigDialog) {
        ConfigImportDialog(
            initialUrl = configUrl,
            onConfirm = {
                viewModel.setConfigUrl(it)
                showConfigDialog = false
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showConfigDialog = false },
        )
    }

    if (showSiteDialog) {
        com.ican.tvplay.ui.home.SitePickerDialog(
            sites = siteList,
            currentKey = currentSiteKey,
            onSelect = { site ->
                viewModel.switchSite(site.key)
                showSiteDialog = false
            },
            onDismiss = { showSiteDialog = false },
            isSearchable = { viewModel.isSiteSearchable(it) },
            isChangeable = { viewModel.isSiteChangeable(it) },
            onToggleSearchable = { viewModel.toggleSiteSearchable(it) },
            onToggleChangeable = { viewModel.toggleSiteChangeable(it) },
            toggleVersion = siteToggleVersion,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    BasicComponent(
        title = label,
        endActions = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** 导入配置对话框：miuix WindowDialog + TextField，照搬 KernelSU 弹窗结构 */
@Composable
private fun ConfigImportDialog(
    initialUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    OverlayDialog(
        show = true,
        title = "导入配置",
        titleColor = MiuixTheme.colorScheme.onBackground,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
        // 渲染到 AppRoot 根宿主：弹窗+遮罩层级高于底栏
        renderInRootScaffold = true,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            top.yukonga.miuix.kmp.basic.TextField(
                value = url,
                onValueChange = { url = it },
                label = "输入配置链接（http/https）…",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = "确认",
                    onClick = { onConfirm(url) },
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
