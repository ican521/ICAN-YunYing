package com.ican.tvplay.ui.search

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.ui.MultiSourceSearchViewModel
import com.ican.tvplay.ui.SearchViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.BlurredBar
import com.ican.tvplay.ui.components.LocalEnableTopBarBlur
import com.ican.tvplay.ui.components.MiuixBackButton
import com.ican.tvplay.ui.components.MiuixSearchInput
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.rememberBlurBackdrop
import com.ican.tvplay.ui.components.topBarPadding
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.Spacing

/**
 * 合并后的搜索页（搜索入口与首页热词/卡片直达结果共用）：
 * - 顶部：左上角返回圆钮 + 可编辑胶囊搜索栏
 * - 编辑态（未提交/输入中）：上半部分历史、下半部分推荐
 * - 结果态：左侧站源筛选 + 右侧跨站源视频卡片网格（边距/尺寸沿用原结果页基准）
 * - 结果态点击搜索栏 → 回到编辑态并保留当前词；返回 → 直接回首页
 */
@Composable
fun SearchHomeScreen(
    initialQuery: String = "",
    onBack: () -> Unit,
    onPlay: (videoId: String, episodeIndex: Int, flag: String, preInitialized: Boolean) -> Unit,
) {
    val vm = appViewModel { SearchViewModel(this) }
    val history by vm.history.collectAsStateWithLifecycle()
    val hotWords by vm.hotWords.collectAsStateWithLifecycle(initialValue = emptyList())
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val multiVm = appViewModel { MultiSourceSearchViewModel(context) }

    var queryText by remember {
        mutableStateOf(initialQuery.ifBlank { multiVm.query.value })
    }
    // 首页搜索胶囊进入本页即展开（miuix 展开态会自动聚焦并弹键盘，省掉再点一次搜索栏）；
    // 从热词/卡片直达结果时保持收起，不弹键盘
    var expanded by remember {
        mutableStateOf(initialQuery.isBlank() && multiVm.query.value.isBlank())
    }
    // true = 编辑态（历史 + 推荐）；false = 结果态
    var editing by remember {
        mutableStateOf(initialQuery.isBlank() && multiVm.query.value.isBlank())
    }

    // 输入时点击搜索栏以外区域（含历史/推荐词）收起输入法：清焦点（InputField 收起态不自接管外部点击）
    val focusManager = LocalFocusManager.current

    // 热词/卡片直达：进入即搜索并展示结果（已有同词结果则复用）
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            editing = false
            if (!multiVm.hasResultsFor(initialQuery)) multiVm.searchAll(initialQuery)
        }
    }

    fun submit(keyword: String) {
        val q = keyword.trim()
        if (q.isEmpty()) return
        // 提交即离开输入态：先收起输入法，再切换到结果态
        focusManager.clearFocus()
        expanded = false
        vm.addHistory(q)
        queryText = q
        editing = false
        multiVm.searchAll(q)
    }

    // KernelSU 同款：固定毛玻璃顶栏（搜索栏不适合折叠，保持固定高度）
    val backdrop = rememberBlurBackdrop(LocalEnableTopBarBlur.current)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BlurredBar(backdrop) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(barColor)
                        .padding(topBarPadding())
                        // 状态栏下缘 → 搜索栏顶 16dp（与首页搜索栏一致）；
                        // 搜索栏底 → 顶栏底 16dp，顶栏底缘直接贴下方内容
                        .padding(top = 16.dp, bottom = 16.dp),
                ) {
                    MiuixBackButton(onClick = {
                        // 先收键盘再返回，避免键盘跟着页面转场一直留到首页
                        focusManager.clearFocus()
                        onBack()
                    }, showBackground = true, iconSize = 20.dp)

                    // miuix 原生胶囊搜索框（左搜索图标 + 输入后右侧清除钮，IME 搜索键提交）
                    MiuixSearchInput(
                        query = queryText,
                        onQueryChange = {
                            queryText = it
                            vm.onQueryChange(it)
                            // 输入即回到编辑态（显示历史 + 实时推荐）
                            editing = true
                        },
                        onSearch = { submit(it) },
                        label = "搜索影片、剧集",
                        expanded = expanded,
                        onExpandedChange = {
                            expanded = it
                            // 结果态点击搜索栏 → 回到编辑态并保留当前词；
                            // 补一次 onQueryChange，让「推荐」区按当前词即时给出相关影片名
                            if (it) {
                                editing = true
                                vm.onQueryChange(queryText)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            // 距返回钮 16dp（右侧距屏幕保持 pageHorizontalPadding 16dp 不变）
                            .padding(start = 16.dp),
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = (if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                // 空白处点击（子项已消费的点击不受影响）→ 清焦点，输入法随之收起
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
        ) {
            if (editing) {
                // ========== 编辑态：历史 + 推荐 ==========
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        // 窗口已禁用系统级 IME 避让（MainActivity ADJUST_NOTHING），
                        // 键盘弹出时靠 imePadding 让历史/推荐底部内容仍可滚动到键盘上方
                        .imePadding()
                        .padding(horizontal = pageHorizontalPadding())
                        // 顶部间距走 Column 自身 padding，不放 Spacer 子项：
                        // 否则 spacedBy(sectionSpacing) 会在 Spacer 与首个板块之间多加 20dp
                        .padding(top = innerPadding.calculateTopPadding()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
                ) {
                    // 上半部分：历史（无历史整块隐藏；长按单个词删除）
                    if (history.isNotEmpty()) {
                        Column {
                            Text(
                                text = "历史",
                                style = MaterialTheme.typography.titleMedium,
                                color = MiuixTheme.colorScheme.onBackground,
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

                    // 下半部分：推荐（空查询时显示热搜词；输入关键词时显示匹配的影片标题）
                    Column {
                        Text(
                            text = "推荐",
                            style = MaterialTheme.typography.titleMedium,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(Spacing.titleContentGap))
                        val recommendWords = if (queryText.isBlank()) hotWords else suggestions
                        WordFlowRow(words = recommendWords, onWordClick = { submit(it) })
                    }
                }
            } else {
                // ========== 结果态：沿用原跨站源结果页的顶部间距与边距基准 ==========
                // 顶部间距走滚动容器 contentPadding（不用外层 Spacer 占位），
                // 滚动内容才能进入顶栏后方，毛玻璃有内容可采样；
                // 内容贴顶栏底缘，额外视觉间距由顶栏底部 16dp 承担
                MultiSourceSearchResults(
                    vm = multiVm,
                    onPlay = onPlay,
                    topPadding = innerPadding.calculateTopPadding(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 搜索词胶囊流式排列：固定 40dp 高（文字垂直居中）、左右 16dp、全圆角、miuix surfaceContainerHigh 底（KernelSU 搜索栏同款底色）、间距 8dp */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordFlowRow(
    words: List<String>,
    onWordClick: (String) -> Unit,
    onWordLongClick: ((String) -> Unit)? = null,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.searchChipGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.searchChipGap),
    ) {
        words.forEach { word ->
            Box(
                modifier = Modifier
                    .tvCardEffect(
                        onClick = { onWordClick(word) },
                        onLongClick = onWordLongClick?.let { { it(word) } },
                        shape = RoundedCornerShape(percent = 50),
                        focusedScale = 1.05f,
                        glow = false,
                    )
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(percent = 50))
                    .height(Spacing.chipHeight)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
