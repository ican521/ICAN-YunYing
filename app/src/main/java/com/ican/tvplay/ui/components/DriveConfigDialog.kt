package com.ican.tvplay.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.ui.theme.Spacing

/** 网盘配置项（用于排序和持久化） */
data class DriveConfig(
    val name: String,
    val action: String,
    val token: String = "",
    val order: Int = 0,
)

/**
 * 网盘配置弹窗：配置 token、排序
 * 参考 fongmi action 机制，点击网盘卡片时弹出此弹窗而非进入播放页
 */
@Composable
fun DriveConfigDialog(
    video: Video,
    drives: List<DriveConfig>,
    onDismiss: () -> Unit,
    onSave: (List<DriveConfig>) -> Unit,
) {
    var driveList by remember { mutableStateOf(drives) }
    var newToken by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.pageHorizontal),
            shape = RoundedCornerShape(Spacing.corner),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sectionSpacing),
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "网盘配置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.titleContentGap))

                // 当前网盘信息
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sectionSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(Spacing.titleContentGap))
                        Column {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = video.action,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sectionSpacing))

                // Token 输入
                OutlinedTextField(
                    value = newToken,
                    onValueChange = { newToken = it },
                    label = { Text("Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(Spacing.sectionSpacing))

                // 已配置网盘列表（支持排序）
                if (driveList.isNotEmpty()) {
                    Text(
                        text = "已配置网盘（可排序）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(Spacing.titleContentGap))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.titleContentGap),
                    ) {
                        itemsIndexed(driveList) { index, drive ->
                            DriveItem(
                                drive = drive,
                                onMoveUp = {
                                    if (index > 0) {
                                        driveList = driveList.toMutableList().apply {
                                            add(index - 1, removeAt(index))
                                        }
                                    }
                                },
                                onMoveDown = {
                                    if (index < driveList.size - 1) {
                                        driveList = driveList.toMutableList().apply {
                                            add(index + 1, removeAt(index))
                                        }
                                    }
                                },
                                onDelete = {
                                    driveList = driveList.toMutableList().apply {
                                        removeAt(index)
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sectionSpacing))

                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(Spacing.titleContentGap))
                    TextButton(
                        onClick = {
                            // 保存逻辑
                            val updated = driveList.toMutableList()
                            if (newToken.isNotBlank()) {
                                updated.add(
                                    DriveConfig(
                                        name = video.title,
                                        action = video.action,
                                        token = newToken,
                                        order = updated.size,
                                    )
                                )
                            }
                            onSave(updated)
                            onDismiss()
                        },
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveItem(
    drive: DriveConfig,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.titleContentGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = drive.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Token: ${drive.token.take(10)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                }
                IconButton(onClick = onMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Close, contentDescription = "删除")
                }
            }
        }
    }
}
