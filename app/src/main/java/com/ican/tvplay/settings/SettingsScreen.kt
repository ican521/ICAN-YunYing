package com.ican.tvplay.ui.settings

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
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
import com.ican.tvplay.data.BACKGROUND_PIP
import com.ican.tvplay.data.HomeLayoutSize
import com.ican.tvplay.data.ThemeMode
import com.ican.tvplay.data.remote.TvBoxSite
import com.ican.tvplay.ui.CollectionViewModel
import com.ican.tvplay.ui.SettingsViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.BlurredBar
import com.ican.tvplay.ui.components.dialogBottomMargin
import com.ican.tvplay.ui.components.LocalEnableTopBarBlur
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.rememberBlurBackdrop
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.SelectionFillAlpha
import com.ican.tvplay.ui.theme.Spacing
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Icon

@Composable
fun SettingsScreen(
    onColorPaletteClick: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val viewModel = appViewModel { SettingsViewModel(this) }
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
    val enableBlur by viewModel.enableBlur.collectAsStateWithLifecycle()
    val topBarBlur by viewModel.topBarBlurEnabled.collectAsStateWithLifecycle()
    val enablePredictiveBack by viewModel.enablePredictiveBack.collectAsStateWithLifecycle()
    val backgroundPlay by viewModel.playerBackgroundPlay.collectAsStateWithLifecycle()
    val homeLayoutSize by viewModel.homeLayoutSize.collectAsStateWithLifecycle()
    val configUrl by viewModel.configUrl.collectAsStateWithLifecycle()
    val siteName by viewModel.siteName.collectAsStateWithLifecycle()
    val currentSiteKey by viewModel.currentSiteKey.collectAsStateWithLifecycle()
    val siteToggleVersion by viewModel.siteToggleVersion.collectAsStateWithLifecycle()
    var showConfigDialog by remember { mutableStateOf(false) }
    var showSiteDialog by remember { mutableStateOf(false) }
    // 收藏同步弹窗（从收藏页迁移：局域网设备列表 + 双向/上传/下载模式）
    var showSyncDialog by remember { mutableStateOf(false) }
    var siteList by remember { mutableStateOf<List<TvBoxSite>>(emptyList()) }
    LaunchedEffect(showSiteDialog) {
        if (showSiteDialog) siteList = viewModel.listSites()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 备份与恢复：SAF 选择文件
    val backupLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            scope.launch {
                val ok = com.ican.tvplay.data.BackupManager.backup(context, it)
                Toast.makeText(
                    context,
                    if (ok) "备份成功" else "备份失败",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            scope.launch {
                val ok = com.ican.tvplay.data.BackupManager.restore(context, it)
                if (ok) {
                    // fongmi 同款：恢复成功后立即重载配置与设置，无需重启 APP
                    viewModel.applyRestoredBackup()
                }
                Toast.makeText(
                    context,
                    if (ok) "恢复成功" else "恢复失败",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

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
    val backdrop = rememberBlurBackdrop(LocalEnableTopBarBlur.current)
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
                title = "液态玻璃",
                summary = "底部导航液态玻璃效果",
                checked = enableBlur,
                onCheckedChange = { viewModel.setEnableBlur(it) },
            )
            SwitchPreference(
                title = "顶栏毛玻璃",
                summary = "顶部栏模糊效果，独立于液态玻璃",
                checked = topBarBlur,
                onCheckedChange = { viewModel.setTopBarBlurEnabled(it) },
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
            ArrowPreference(
                title = "备份数据",
                summary = "导出设置、收藏、历史到 JSON 文件",
                onClick = {
                    val name = "tvplay_backup_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())}.json"
                    backupLauncher.launch(name)
                },
            )
            ArrowPreference(
                title = "恢复数据",
                summary = "从 JSON 文件导入设置、收藏、历史",
                onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) },
            )
            ArrowPreference(
                title = "收藏同步",
                summary = "局域网设备间同步收藏（双向/上传/下载）",
                onClick = { showSyncDialog = true },
            )
        }
        }

        item {
        Card(modifier = Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                title = "后台播放",
                summary = "全屏播放时切后台自动进入画中画",
                items = listOf("关闭", "画中画"),
                selectedIndex = if (backgroundPlay == BACKGROUND_OFF) 0 else 1,
                onSelectedIndexChange = { index ->
                    when (index) {
                        0 -> viewModel.setPlayerBackgroundPlay(BACKGROUND_OFF)
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

    ConfigImportDialog(
        show = showConfigDialog,
        initialUrl = configUrl,
        onConfirm = {
            viewModel.setConfigUrl(it)
            showConfigDialog = false
            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
        },
        onDismiss = { showConfigDialog = false },
    )

    com.ican.tvplay.ui.home.SitePickerDialog(
        show = showSiteDialog,
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

    // 收藏同步弹窗（由收藏页整体迁移至此）
    FavoriteSyncDialog(
        show = showSyncDialog,
        onDismiss = { showSyncDialog = false },
    )
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

/** 导入配置对话框：miuix WindowDialog + TextField，照搬 KernelSU 弹窗结构。
 *  通过 [show] 控制显隐，OverlayDialog 内部处理进入/退出动画。 */
@Composable
private fun ConfigImportDialog(
    show: Boolean,
    initialUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    OverlayDialog(
        show = show,
        title = "导入配置",
        titleColor = MiuixTheme.colorScheme.onBackground,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
        // 渲染到 AppRoot 根宿主：弹窗+遮罩层级高于底栏
        renderInRootScaffold = true,
        // 三边统一 16dp：底部 = max(16dp, 系统导航栏高度)，跨设备自适应
        outsideMargin = DpSize(16.dp, dialogBottomMargin()),
        // 强制「非大屏」：平板宽窗口下 miuix 会改为屏幕居中，与手机底部呼出不一致
        largeScreen = false,
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

/**
 * 收藏同步弹窗（由收藏页顶栏云同步按钮整体迁移至此）：
 * 照搬 fongmi SyncDialog——标题 + 模式切换 + 刷新 + 设备列表；
 * 点设备按当前模式同步，长按且模式非双向时强制同步（接收端先清空再写入）。
 * 与其他弹窗统一：miuix OverlayDialog（根宿主渲染 + 库自带 windowDim 遮罩）。
 */
@Composable
private fun FavoriteSyncDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val viewModel = appViewModel { CollectionViewModel(this) }
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val syncMode by viewModel.syncMode.collectAsStateWithLifecycle()
    var scanning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 打开同步弹窗：无历史设备时自动扫描（fongmi SyncDialog.getDevice）
    LaunchedEffect(show) {
        if (show && devices.isEmpty() && !scanning) {
            scanning = true
            viewModel.rescanDevices(context)
            scanning = false
        }
    }

    OverlayDialog(
        show = show,
        title = "选择设备",
        titleColor = MiuixTheme.colorScheme.onBackground,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
        // 渲染到 AppRoot 根宿主：弹窗+遮罩层级高于底栏
        renderInRootScaffold = true,
        // 左右跟随页面边距（手机 16dp / 平板 24dp），底部 = max(16dp, 系统导航栏高度)
        outsideMargin = DpSize(pageHorizontalPadding(), dialogBottomMargin()),
        largeScreen = false,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 操作行：模式切换 | 重新扫描（标题「选择设备」由弹窗提供）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 同步模式：双向 / 上传 / 下载（fongmi mode 按钮循环切换）
                Box(
                    modifier = Modifier
                        .clickable { viewModel.cycleSyncMode() }
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = SelectionFillAlpha),
                            RoundedCornerShape(Spacing.corner),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = when (syncMode) {
                            com.ican.tvplay.data.FavoriteSync.MODE_UPLOAD -> "上传"
                            com.ican.tvplay.data.FavoriteSync.MODE_DOWNLOAD -> "下载"
                            else -> "双向"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.titleContentGap))
                IconButton(
                    onClick = {
                        if (!scanning) {
                            scope.launch {
                                scanning = true
                                viewModel.rescanDevices(context)
                                scanning = false
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = AppIcons.Refresh,
                        contentDescription = "重新扫描",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.titleContentGap))

            when {
                scanning && devices.isEmpty() -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(Spacing.titleContentGap))
                        Text(
                            "正在扫描局域网设备…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                devices.isEmpty() -> {
                    Text(
                        "未发现设备，请确保其他设备已打开本应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
                else -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        devices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvCardEffect(
                                        onClick = {
                                            // fongmi SyncDialog.onItemClick
                                            scope.launch {
                                                val ok = com.ican.tvplay.data.FavoriteSync
                                                    .send(context, device, syncMode, false)
                                                android.widget.Toast.makeText(
                                                    context,
                                                    if (ok) "已发送到 ${device.name}" else "发送失败",
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                                if (ok) onDismiss()
                                            }
                                        },
                                        shape = RoundedCornerShape(Spacing.corner),
                                        focusedScale = 1.03f,
                                        glow = false,
                                        onLongClick = {
                                            // fongmi SyncDialog.onLongClick：模式非双向时强制同步
                                            if (syncMode == com.ican.tvplay.data.FavoriteSync.MODE_TWO_WAY) return@tvCardEffect
                                            scope.launch {
                                                val ok = com.ican.tvplay.data.FavoriteSync
                                                    .send(context, device, syncMode, true)
                                                android.widget.Toast.makeText(
                                                    context,
                                                    if (ok) "已强制同步到 ${device.name}" else "发送失败",
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                                if (ok) onDismiss()
                                            }
                                        },
                                    )
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(Spacing.corner),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 设备类型图标（fongmi adapter_device：type）
                                Icon(
                                    imageVector = if (device.type == 0) AppIcons.Tv else AppIcons.Phone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = device.ip,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
