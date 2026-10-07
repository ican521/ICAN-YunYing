package com.ican.tvplay.ui.settings

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ican.tvplay.data.ThemeMode
import com.ican.tvplay.ui.SettingsViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.topLevelContentPadding
import com.ican.tvplay.ui.components.tvCardEffect
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun SettingsScreen(
    onColorPaletteClick: () -> Unit = {},
    onThemePreviewClick: () -> Unit = {},
) {
    val viewModel = appViewModel { SettingsViewModel(this) }
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
    val enableBlur by viewModel.enableBlur.collectAsStateWithLifecycle()
    val predictiveBack by viewModel.predictiveBack.collectAsStateWithLifecycle()

    val padding = topLevelContentPadding()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        SettingsCard(title = "外观", subtitle = "主题模式") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeOption("跟随系统", themeMode == ThemeMode.SYSTEM) {
                    viewModel.setThemeMode(ThemeMode.SYSTEM)
                }
                ThemeOption("浅色", themeMode == ThemeMode.LIGHT) {
                    viewModel.setThemeMode(ThemeMode.LIGHT)
                }
                ThemeOption("深色", themeMode == ThemeMode.DARK) {
                    viewModel.setThemeMode(ThemeMode.DARK)
                }
            }
        }

        SettingsCard(title = "主题", subtitle = "取色与效果") {
            val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            SettingsSwitchRow(
                label = "动态取色",
                hint = if (supportsDynamic) "跟随系统壁纸色调" else "需要 Android 12+",
                checked = dynamicColor && supportsDynamic,
                enabled = supportsDynamic,
                onToggle = { viewModel.setDynamicColor(it) },
            )
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
            SettingsSwitchRow(
                label = "预测性返回",
                hint = "返回时动画跟随手指滑动，预览返回目标",
                checked = predictiveBack,
                enabled = true,
                onToggle = { viewModel.setPredictiveBack(it) },
            )
            SettingsEntryRow(
                label = "主题预览",
                value = "查看效果",
                dotColor = null,
                onClick = onThemePreviewClick,
            )
        }

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

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
private fun ThemeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "themeOption",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .tvCardEffect(
                onClick = onClick,
                shape = shape,
                focusedScale = 1.05f,
                glow = false,
            )
            .background(container, shape)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        if (selected) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(start = if (selected) 6.dp else 0.dp),
        )
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
                shape = RoundedCornerShape(14.dp),
                focusedScale = 1.02f,
                glow = false,
                enabled = enabled,
            )
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
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
                .background(trackColor, RoundedCornerShape(12.dp)),
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
                shape = RoundedCornerShape(14.dp),
                focusedScale = 1.02f,
                glow = false,
            )
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
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
