package com.ican.tvplay.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.ui.SearchViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.CircleBackButton
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.Spacing

/**
 * 搜索首页（未搜索时的落地页）：
 * - 顶部：左上角返回圆钮 + 胶囊搜索栏（样式与搜索结果页顶栏一致）
 * - 上半部分：历史搜索（标题 + 清空按钮 + 胶囊流式排列，无历史整块隐藏）
 * - 下半部分：热搜推荐（标题 + 胶囊流式排列）
 * 点击历史词/热搜词、或提交输入 → 记录历史并跳转跨站源搜索结果页
 */
@Composable
fun SearchHomeScreen(
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
) {
    val vm = appViewModel { SearchViewModel(this) }
    val history by vm.history.collectAsStateWithLifecycle()

    var queryText by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    fun submit(keyword: String) {
        val q = keyword.trim()
        if (q.isEmpty()) return
        vm.addHistory(q)
        onSearch(q)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ========== 顶栏（与搜索结果页顶栏同款）==========
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = Spacing.pageHorizontal, end = Spacing.pageHorizontal, top = Spacing.pageTop, bottom = 6.dp),
        ) {
            CircleBackButton(onClick = onBack)

            // 搜索框：48dp 高、全圆角、surfaceVariant 底、20dp 图标、bodyLarge 字号
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 18.dp),
            ) {
                Icon(
                    imageVector = AppIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(queryText) }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .tvCardEffect(
                            onClick = { submit(queryText) },
                            shape = RoundedCornerShape(percent = 50),
                            focusedScale = 1.1f,
                            glow = false,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Check,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // ========== 内容区：历史搜索 + 热搜推荐 ==========
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
        ) {
            Spacer(Modifier.height(Spacing.titleContentGap - 6.dp))

            // 上半部分：历史搜索（无历史整块隐藏）
            if (history.isNotEmpty()) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "历史搜索",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .tvCardEffect(
                                    onClick = { showClearConfirm = true },
                                    shape = RoundedCornerShape(percent = 50),
                                    focusedScale = 1.1f,
                                    glow = false,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = "清空历史",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.titleContentGap))
                    WordFlowRow(words = history.asReversed(), onWordClick = { submit(it) })
                }
            }

            // 下半部分：热搜推荐
            Column {
                Text(
                    text = "热搜推荐",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(Spacing.titleContentGap))
                WordFlowRow(words = vm.hotWords, onWordClick = { submit(it) })
            }
        }
    }

    // 清空全部搜索历史确认弹窗（样式与收藏页清空确认弹窗一致）
    if (showClearConfirm) {
        Dialog(onDismissRequest = { showClearConfirm = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Spacing.corner))
                    .padding(24.dp),
            ) {
                Text(
                    text = "清空搜索历史",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "确定要清空所有搜索历史吗？此操作不可恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                ) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { showClearConfirm = false }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        text = "确定",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5C5C),
                        modifier = Modifier
                            .clickable {
                                vm.clearHistory()
                                showClearConfirm = false
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** 搜索词胶囊流式排列：40dp 高、全圆角、surfaceVariant 底、间距 8dp */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordFlowRow(
    words: List<String>,
    onWordClick: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        words.forEach { word ->
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .tvCardEffect(
                        onClick = { onWordClick(word) },
                        shape = RoundedCornerShape(percent = 50),
                        focusedScale = 1.05f,
                        glow = false,
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
