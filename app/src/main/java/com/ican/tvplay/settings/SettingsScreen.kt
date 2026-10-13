package com.ican.tvplay.ui.settings

import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.window.WindowDialog
import com.ican.tvplay.data.BACKGROUND_OFF
import com.ican.tvplay.data.BACKGROUND_ON
import com.ican.tvplay.data.BACKGROUND_PIP
import com.ican.tvplay.data.HomeLayoutSize
import com.ican.tvplay.data.ThemeMode
import com.ican.tvplay.data.remote.TvBoxSite
import com.ican.tvplay.ui.SettingsViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.BlurredBar
import com.ican.tvplay.ui.components.DropdownSelectCard
import com.ican.tvplay.ui.components.LocalEnableBlur
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.rememberBlurBackdrop
import com.ican.tvplay.ui.components.tvCardEffect
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
        DropdownSelectCard(
            title = "外观",
            subtitle = "主题模式",
            options = listOf("跟随系统", "浅色", "深色"),
            selectedIndex = when (themeMode) {
                ThemeMode.SYSTEM -> 0
                ThemeMode.LIGHT -> 1
                ThemeMode.DARK -> 2
            },
            onSelect = { index ->
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

        item {
        DropdownSelectCard(
            title = "布局",
            subtitle = "首页网格大小",
            options = layoutOptions,
            selectedIndex = when (homeLayoutSize) {
                HomeLayoutSize.SMALL -> 0
                HomeLayoutSize.MEDIUM -> 1
                HomeLayoutSize.LARGE -> 2
            }.coerceAtMost(layoutOptions.lastIndex),
            onSelect = { index ->
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

        item {
        SettingsCard(title = "主题", subtitle = "自定义更多主题选项") {
            SettingsEntryRow(
                label = "主题色板",
                value = if (themeColor == 0) "默认" else "自定义",
                dotColor = if (themeColor == 0) MaterialTheme.colorScheme.primary
                else Color(themeColor),
                onClick = onColorPaletteClick,
            )
            SettingsSwitchRow(
                label = "全局模糊",
                hint = "底部导航液态玻璃效果",
                checked = enableBlur,
                enabled = true,
                onToggle = { viewModel.setEnableBlur(it) },
            )
        }
        }

        item {
        SettingsCard(title = "数据", subtitle = "配置来源") {
            SettingsEntryRow(
                label = "导入配置",
                value = configUrl.ifEmpty { "未设置" },
                dotColor = null,
                onClick = { showConfigDialog = true },
            )
            Spacer(Modifier.height(10.dp))
            SettingsEntryRow(
                label = "站源",
                value = siteName,
                dotColor = null,
                onClick = { showSiteDialog = true },
            )
        }
        }

        item {
        DropdownSelectCard(
            title = "播放",
            subtitle = "后台播放",
            options = listOf("关闭", "开启", "画中画"),
            selectedIndex = when (backgroundPlay) {
                BACKGROUND_OFF -> 0
                BACKGROUND_ON -> 1
                else -> 2
            },
            onSelect = { index ->
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
            hint = "开启：退出播放页后继续播放并可通知栏控制；画中画：全屏播放时切后台自动进入小窗",
        )
        }

        item {
        SettingsCard(title = "关于", subtitle = "版本与运行环境") {
            val isTv = LocalContext.current.resources.configuration.uiMode.let { uiMode ->
                (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
            }
            InfoLine("应用名称", "ICAN云影")
            InfoLine("版本", "0.1.0")
            InfoLine("运行设备", if (isTv) "Android TV" else "手机 / 平板")
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
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Spacing.corner))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.titleContentGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 设置项开关行：点击整行切换，右侧胶囊指示 */
@Composable
private fun SettingsSwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "switchTrack",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvCardEffect(
                onClick = { onToggle(!checked) },
                shape = RoundedCornerShape(Spacing.corner),
                focusedScale = 1.02f,
                glow = false,
                enabled = enabled,
            )
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Spacing.corner))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 24.dp)
                .background(trackColor, RoundedCornerShape(Spacing.corner)),
        ) {
            val thumbOffset by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (checked) 20.dp else 2.dp,
                animationSpec = tween(200),
                label = "switchThumb",
            )
            Box(
                modifier = Modifier
                    .padding(start = thumbOffset)
                    .size(20.dp)
                    .align(Alignment.CenterStart)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

/** 设置项入口行：点击跳转，右侧色点（可选）+ 文案 */
@Composable
private fun SettingsEntryRow(
    label: String,
    value: String,
    dotColor: Color?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvCardEffect(
                onClick = onClick,
                shape = RoundedCornerShape(Spacing.corner),
                focusedScale = 1.02f,
                glow = false,
            )
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Spacing.corner))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(dotColor, CircleShape),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 导入配置对话框：miuix WindowDialog + TextField，照搬 KernelSU 弹窗结构 */
@Composable
private fun ConfigImportDialog(
    initialUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    WindowDialog(
        show = true,
        title = "导入配置",
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
