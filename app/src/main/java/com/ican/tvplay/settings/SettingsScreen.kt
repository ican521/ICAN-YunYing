package com.ican.tvplay.ui.settings

import android.content.res.Configuration
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun SettingsScreen() {
    val viewModel = appViewModel { SettingsViewModel(this) }
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    val padding = topLevelContentPadding()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
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
