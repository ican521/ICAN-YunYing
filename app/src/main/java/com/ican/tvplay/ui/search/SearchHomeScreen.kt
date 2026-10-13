package com.ican.tvplay.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.ui.SearchViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.BlurredBar
import com.ican.tvplay.ui.components.LocalEnableBlur
import com.ican.tvplay.ui.components.MiuixBackButton
import com.ican.tvplay.ui.components.MiuixSearchInput
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.rememberBlurBackdrop
import com.ican.tvplay.ui.components.topBarPadding
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

    fun submit(keyword: String) {
        val q = keyword.trim()
        if (q.isEmpty()) return
        vm.addHistory(q)
        onSearch(q)
    }

    // KernelSU 同款：固定毛玻璃顶栏（搜索栏不适合折叠，保持固定高度）
    val enableBlurState = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlurState)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(barColor)
                        .padding(topBarPadding()),
                ) {
                    MiuixBackButton(onClick = onBack)

                    // miuix 原生胶囊搜索框（左搜索图标 + 输入后右侧清除钮，IME 搜索键提交）
                    MiuixSearchInput(
                        query = queryText,
                        onQueryChange = { queryText = it },
                        onSearch = { submit(it) },
                        label = "搜索影片、剧集",
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
        // ========== 内容区：历史搜索 + 热搜推荐 ==========
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = pageHorizontalPadding()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
        ) {
            Spacer(Modifier.height(innerPadding.calculateTopPadding() + Spacing.searchBarBottomMargin))
            // 上半部分：历史搜索（无历史整块隐藏；长按单个词删除）
            if (history.isNotEmpty()) {
                Column {
                    Text(
                        text = "历史搜索",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.titleContentGap))
                    WordFlowRow(
                        words = history.asReversed(),
                        onWordClick = { submit(it) },
                        onWordLongClick = { vm.removeHistory(it) },
                    )
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
    }
}

/** 搜索词胶囊流式排列：40dp 高、全圆角、surfaceVariant 底、间距 8dp */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordFlowRow(
    words: List<String>,
    onWordClick: (String) -> Unit,
    onWordLongClick: ((String) -> Unit)? = null,
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
                        onLongClick = onWordLongClick?.let { { it(word) } },
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
