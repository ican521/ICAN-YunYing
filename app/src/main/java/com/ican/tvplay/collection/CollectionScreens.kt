package com.ican.tvplay.ui.collection

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import com.ican.tvplay.ui.components.PosterCaption
import com.ican.tvplay.ui.components.PosterImage
import com.ican.tvplay.ui.components.posterCaptionScrim
import com.ican.tvplay.data.local.FavoriteEntity
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.ui.CollectionViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.EmptyState
import com.ican.tvplay.ui.components.MiuixBackButton
import com.ican.tvplay.ui.components.MiuixConfirmDialog
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.components.videoGridColumns
import com.ican.tvplay.ui.theme.LightOnSurface
import com.ican.tvplay.ui.theme.PosterActionScrim
import com.ican.tvplay.ui.theme.PosterBadgeScrim
import com.ican.tvplay.ui.theme.Spacing
import com.ican.tvplay.ui.theme.SquircleShape
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(
    onPlay: (videoId: String, episodeIndex: Int, flag: String, preInitialized: Boolean) -> Unit,
    onUpdatesClick: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val viewModel = appViewModel { CollectionViewModel(this) }
    val updatesViewModel = appViewModel { com.ican.tvplay.ui.UpdatesViewModel(this) }
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val todayUpdates by updatesViewModel.todayUpdates.collectAsStateWithLifecycle()
    val pendingUpdates by updatesViewModel.pendingUpdates.collectAsStateWithLifecycle()
    val homeLayoutSize by viewModel.homeLayoutSize.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 编辑模式：长按卡片进入（卡片显示叉号）；编辑模式内再长按 → 清空全部确认弹窗；返回键退出
    var editMode by remember { mutableStateOf(false) }
    // 清空确认弹窗
    var showConfirm by remember { mutableStateOf(false) }

    // 编辑模式下按返回 = 取消编辑（不退出页面）；清空弹窗打开时返回由弹窗自行消费
    BackHandler(enabled = editMode && !showConfirm) { editMode = false }

    // 固定居中大标题顶栏（不透明 surface 底色）
    val barColor = MiuixTheme.colorScheme.surface

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            Box {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(barColor)
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .height(Spacing.centeredTopBarHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "我的收藏",
                        style = MaterialTheme.typography.titleLarge,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box {
            if (favorites.isEmpty()) {
                // 空收藏时「追番更新」入口仍常驻（否则入口会随空态一起消失）
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding())
                        .padding(horizontal = pageHorizontalPadding()),
                ) {
                    UpdatesEntry(
                        todayCount = todayUpdates.size,
                        pendingCount = pendingUpdates.size,
                        onClick = onUpdatesClick,
                    )
                    EmptyState(
                        text = "还没有收藏的视频",
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = Spacing.bottomBarInset),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = videoGridColumns(homeLayoutSize),
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = pageHorizontalPadding(),
                        end = pageHorizontalPadding(),
                        top = innerPadding.calculateTopPadding(),
                        bottom = Spacing.bottomBarInset,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.cardGapH),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGapV),
                ) {
                item(contentType = "updates_entry", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    UpdatesEntry(
                        todayCount = todayUpdates.size,
                        pendingCount = pendingUpdates.size,
                        onClick = onUpdatesClick,
                    )
                }
                items(favorites, key = { it.videoId }, contentType = { "favorite" }) { item ->
                    FavoriteCard(
                        entity = item,
                        editMode = editMode,
                        onClick = {
                            // 编辑模式下点卡片本体不响应，仅右上角叉号可删除
                            if (!editMode) {
                                // 按收藏记录的站源拉详情并直接进播放页（与搜索页点击卡片同链路）
                                scope.launch {
                                    val prepared = viewModel.prepareFavoritePlay(item)
                                    if (prepared == null) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "视频加载失败，站源可能已失效",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        com.ican.tvplay.player.core.Players.cacheVideo(
                                            prepared.video,
                                            prepared.siteKey,
                                            prepared.site,
                                            prepared.jarSpec,
                                        )
                                        onPlay(prepared.video.id, 0, prepared.firstFlag, false)
                                    }
                                }
                            }
                        },
                        onDelete = { viewModel.removeFavorite(item.videoId) },
                        onLongClick = {
                            // 非编辑模式：长按进入编辑模式并 Toast 提示；编辑模式内再长按：弹出清空全部确认
                            if (editMode) {
                                showConfirm = true
                            } else {
                                editMode = true
                                android.widget.Toast.makeText(
                                    context,
                                    "再次长按可全部清空",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
                }
            }
        }
    }

    // 清空全部确认弹窗（KernelSU 同款 miuix WindowDialog）
    MiuixConfirmDialog(
        show = showConfirm,
        title = "删除全部收藏",
        content = "确定要清空所有收藏吗？此操作不可恢复。",
        danger = true,
        onConfirm = {
            viewModel.clearFavorites()
            showConfirm = false
            editMode = false
        },
        onDismiss = {
            // 取消 → 关闭弹窗 + 退出编辑模式
            showConfirm = false
            editMode = false
        },
    )
}


/**
 * 收藏页顶部的「追番更新」入口：今日更新 + 待看更新数量汇总，
 * 点击进入追番更新页。没有任何更新时也保留入口（说明文字改为引导语）。
 *
 * 用 miuix ArrowPreference，与设置页入口同构：标题 / 副标题沿用 ArrowPreference
 * 默认字号；有更新时右侧显示数字角标（覆盖默认箭头），无更新时显示默认箭头。
 */
@Composable
private fun UpdatesEntry(
    todayCount: Int,
    pendingCount: Int,
    onClick: () -> Unit,
) {
    val total = todayCount + pendingCount
    Card(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = "追番更新",
            summary = when {
                total == 0 -> "收藏的剧集有更新时会在这里提醒"
                todayCount > 0 -> "今日更新 $todayCount 部 · 待看 $total 部"
                else -> "有 $total 部已更新未看"
            },
            onClick = onClick,
        )
    }
}

/** 收藏卡片：与首页 [VideoCard] 海报内标题样式完全同款（整卡 5:7、海报铺满整卡、
 *  标题 + 副标题压在底部渐变压暗上）；左上角站源胶囊，编辑模式下隐藏并在右上角显示删除叉号。 */
@Composable
private fun FavoriteCard(
    entity: FavoriteEntity,
    editMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
) {
    val corner = Spacing.corner
    Card(
        modifier = Modifier
            .aspectRatio(5f / 7f)
            // pressable 在外层：按压蒙层跟随 Sink 缩放一起缩小（与 VideoCard 同序）
            .pressable(
                interactionSource = null,
                indication = SinkFeedback(),
                delay = null,
            )
            .tvCardEffect(
                onClick = onClick,
                shape = SquircleShape(corner),
                focusedScale = 1.03f,
                glow = false,
                onLongClick = onLongClick,
                // 按压蒙层固定浅色模式同款浅黑：收藏/历史卡片深色模式与浅色表现一致
                pressColor = LightOnSurface,
            ),
        cornerRadius = corner,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 海报区：占满「整卡高度 - 标题行」
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                PosterImage(
                    title = entity.title,
                    cover = entity.cover,
                    modifier = Modifier.fillMaxSize(),
                )

                // 左上角：站源胶囊（编辑模式下隐藏，避免与右上角叉号冲突）
                if (!editMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(Spacing.posterOverlayMargin)
                            .background(PosterBadgeScrim, RoundedCornerShape(percent = 50))
                            .padding(horizontal = Spacing.posterBadgePaddingH, vertical = Spacing.posterBadgePaddingV),
                    ) {
                        Text(
                            text = entity.sourceName.ifBlank { "来源" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 右上角：编辑模式删除叉号
                if (editMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.posterOverlayMargin)
                            .size(30.dp)
                            .tvCardEffect(
                                onClick = onDelete,
                                shape = CircleShape,
                                focusedScale = 1.10f,
                                glow = false,
                            )
                            .background(PosterActionScrim, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Demibold.Close,
                            contentDescription = "删除",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                // 海报内标题样式（与首页 VideoCard 同款）：底部渐变压暗 + 标题 / 副标题
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(posterCaptionScrim()),
                )
                PosterCaption(
                    title = entity.title,
                    subtitle = if (entity.episodeCount > 0) "共 ${entity.episodeCount} 集" else null,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
    }
}

/** 历史卡片逐个渐显：相邻卡片启动间隔（毫秒） */
private const val HISTORY_CARD_FADE_STAGGER_MILLIS = 40L

/** 历史卡片错峰索引上限：需覆盖平板横屏一屏可见卡片数（5 列约 20 张），
 *  否则上限之后的卡片同批 reveal、同帧解码掉帧（此前上限 6 导致第 7~20 张挤在同一时刻加载） */
private const val HISTORY_CARD_FADE_MAX_STAGGER_INDEX = 20

/** 历史卡片错峰起点延迟（毫秒）：等滑入转场动画过了主体阶段再开始错峰解码 */
private const val HISTORY_CARD_STAGGER_START_DELAY_MILLIS = 120L

/** 单张历史卡片淡入时长（毫秒），与首页一致 */
private const val HISTORY_CARD_FADE_IN_MILLIS = 200

/**
 * 实验开关：历史卡片底部的播放进度条是否挂载。
 * false = 实验版（不挂载），用于验证历史页与首页视频卡片的帧率差是否来自这条进度条。
 * 验证完改回 true 即恢复。
 */
private const val HISTORY_CARD_SHOW_PROGRESS = false

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onPlay: (videoId: String, episodeIndex: Int, flag: String, preInit: Boolean) -> Unit,
    onBack: () -> Unit = {},
) {
    val viewModel = appViewModel { CollectionViewModel(this) }
    val histories by viewModel.histories.collectAsStateWithLifecycle()
    val homeLayoutSize by viewModel.homeLayoutSize.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 预取窗口：默认只向前预取视口 10%~50%、向后 0 像素，快滑时每张卡都是「滚到眼前才开始组合」，
    // 组合峰值直接压在帧预算上。开大窗口后卡片在滚动途中/空闲期就被提前组合与预测量，
    // 滑到眼前只剩贴图（官方 LazyLayoutCacheWindow；Meta 与 Google 联合实测可降约 8% 大卡顿）
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
        cacheWindow = androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow(
            aheadFraction = 1f,
            behindFraction = 0.5f,
        ),
    )

    // 编辑模式：长按卡片进入（卡片显示叉号）；编辑模式内再长按 → 清空全部确认弹窗；返回键退出
    var editMode by remember { mutableStateOf(false) }
    // 清空确认弹窗
    var showConfirm by remember { mutableStateOf(false) }
    // 渐显基准时刻：数据首次到达后 delay 120ms 起算。
    // 用「绝对时间戳」代替原来的「全局已揭示索引集合」——后者每 40ms 更新一次 Set，
    // 而每张卡片都在组合期读取它，等于每 40ms 让整屏卡片全部重组一遍（进入页面后立刻快速滑动就掉帧）。
    // 改成时间戳后，每张卡片只在轮到自己的那一刻更新自身状态，只重组自己。
    var revealBase by remember { mutableStateOf(0L) }
    LaunchedEffect(histories.isNotEmpty()) {
        if (histories.isNotEmpty() && revealBase == 0L) {
            // 方案B：等滑入转场结束再开始错峰，转场帧上不做任何海报解码
            revealBase = System.currentTimeMillis() + HISTORY_CARD_STAGGER_START_DELAY_MILLIS
        }
    }

    // 编辑模式下按返回 = 取消编辑（留在页面）；清空弹窗打开时返回由弹窗自行消费，
    // 非编辑模式不拦截，保持原有返回上一页行为
    BackHandler(enabled = editMode && !showConfirm) { editMode = false }

    // 固定居中大标题顶栏（不透明 surface 底色）
    val barColor = MiuixTheme.colorScheme.surface

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            Box {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(barColor)
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .height(Spacing.centeredTopBarHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = pageHorizontalPadding())) {
                        // 返回箭头不画圆形底色，尺寸/图标与其他页返回键一致（48dp 按钮 + 20dp 图标）
                        MiuixBackButton(onClick = onBack, showBackground = false)
                    }
                    Text(
                        text = "播放历史",
                        style = MaterialTheme.typography.titleLarge,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box {
            if (histories.isEmpty()) {
                EmptyState(text = "还没有观看记录")
            } else {
                LazyVerticalGrid(
                    columns = videoGridColumns(homeLayoutSize),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = pageHorizontalPadding(),
                        end = pageHorizontalPadding(),
                        top = innerPadding.calculateTopPadding(),
                        bottom = Spacing.bottomBarInset,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.cardGapH),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGapV),
                ) {
                itemsIndexed(histories, key = { _, it -> it.videoId }, contentType = { _, _ -> "history" }) { index, history ->
                    // 逐个渐显：每张卡片自己按时间戳等待（自己一个协程、只置自己的状态），
                    // 不再由列表级 Set 统一推进，避免每 40ms 整屏卡片一起重组。
                    // 初始值同步按时间判断：页面停留后新滚入的卡片直接全显，不会补一次淡入（与旧行为一致）
                    val staggerIndex = index.coerceAtMost(HISTORY_CARD_FADE_MAX_STAGGER_INDEX)
                    var revealed by remember(history.videoId) {
                        val base = revealBase
                        mutableStateOf(
                            base != 0L &&
                                System.currentTimeMillis() >=
                                base + staggerIndex * HISTORY_CARD_FADE_STAGGER_MILLIS,
                        )
                    }
                    LaunchedEffect(history.videoId, revealBase) {
                        if (revealed) return@LaunchedEffect
                        val base = revealBase.takeIf { it != 0L } ?: System.currentTimeMillis()
                        val wait = base + staggerIndex * HISTORY_CARD_FADE_STAGGER_MILLIS -
                            System.currentTimeMillis()
                        if (wait > 0) delay(wait)
                        revealed = true
                    }
                    // 逐个渐显：alpha 只挂在卡片自身的 modifier 上，不新增布局层，尺寸/位置/间距不变
                    val cardAlpha by animateFloatAsState(
                        targetValue = if (revealed) 1f else 0f,
                        animationSpec = tween(HISTORY_CARD_FADE_IN_MILLIS),
                        label = "historyCardFade",
                    )
                    HistoryCard(
                        history = history,
                        editMode = editMode,
                        revealed = revealed,
                        // 渐显结束（alpha 已到 1f）后不再挂 alpha：否则每张卡常驻一个离屏图层，
                        // 滚动时一层层合成纯属浪费；渐显进行中才挂，观感与原来一致
                        modifier = if (cardAlpha < 1f) Modifier.alpha(cardAlpha) else Modifier,
                        onClick = {
                            // 编辑模式下点卡片本体不响应，仅右上角叉号可删除
                            if (!editMode) {
                                // 按历史记录的站源拉详情并直接续播（与收藏页同链路），进度/集数由播放页自动恢复
                                scope.launch {
                                    val prepared = viewModel.prepareHistoryPlay(history)
                                    if (prepared == null) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "视频加载失败，站源可能已失效",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        com.ican.tvplay.player.core.Players.cacheVideo(
                                            prepared.video,
                                            prepared.siteKey,
                                            prepared.site,
                                            prepared.jarSpec,
                                        )
                                        onPlay(prepared.video.id, 0, prepared.firstFlag, false)
                                    }
                                }
                            }
                        },
                        onDelete = { viewModel.removeHistory(history.videoId) },
                        onLongClick = {
                            // 非编辑模式：长按进入编辑模式并 Toast 提示；编辑模式内再长按：弹出清空全部确认
                            if (editMode) {
                                showConfirm = true
                            } else {
                                editMode = true
                                android.widget.Toast.makeText(
                                    context,
                                    "再次长按可全部清空",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
                }
            }
        }
    }

    // 清空全部确认弹窗（与收藏页同款，KernelSU miuix WindowDialog）
    MiuixConfirmDialog(
        show = showConfirm,
        title = "删除全部播放历史",
        content = "确定要清空所有播放记录吗？此操作不可恢复。",
        danger = true,
        onConfirm = {
            viewModel.clearHistory()
            showConfirm = false
            editMode = false
        },
        onDismiss = {
            showConfirm = false
            editMode = false
        },
    )
}

/**
 * 历史卡片：与首页 / 收藏页同款海报内标题样式（整卡 5:7、海报铺满整卡、
 * 标题 + 副标题压在底部渐变压暗上，副标题即当前集数，去掉了原来的集数胶囊）；
 * 左上角站源胶囊（编辑模式下隐藏），右上角编辑模式删除叉号；
 * 海报顶层自下而上：播放进度细条仍贴海报最底 → 标题 / 副标题。
 */
@Composable
private fun HistoryCard(
    history: HistoryEntity,
    editMode: Boolean,
    revealed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
) {
    val progress = if (history.durationMs > 0) {
        (history.positionMs.toFloat() / history.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    // 进度条高度与「胶囊到进度条」的间距：4dp 条高 + 8dp 常规叠加层边距
    val progressBarHeight = 4.dp
    val corner = Spacing.corner
    // shape / 渐变 Brush / 按压指示器 / 卡片配色 / 内边距 / 胶囊圆角 全部缓存：
    // 滚动时卡片会反复重组，每次新建实例都会让对应修饰符节点失效重配，属于纯开销
    // shape 用圆角矩形而不是 Squircle：Card 自身已按同一半径裁掉了海报，这层 clip 只用来
    // 把按压蒙层裁出圆角；而 RoundedCornerShape 的 outline 走 HWUI 的 clipRRect 快路径，
    // squircle 的自定义 Path 只能走 stencil，逐卡裁剪是滚动里的实打实开销
    val cardShape = remember(corner) { RoundedCornerShape(corner) }
    val captionScrim = remember { posterCaptionScrim(span = 1f) }
    val sinkFeedback = remember { SinkFeedback() }
    val badgeShape = remember { RoundedCornerShape(percent = 50) }
    val zeroMargin = remember { PaddingValues(0.dp) }
    // 卡片配色只能在组合里取（CardDefaults.defaultColors 是 @Composable），无法 remember
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer
    // 进度条填充色提前取出：底色条与进度条合并成一次绘制后，不再在 Box 内读主题
    val progressColor = MiuixTheme.colorScheme.primary
    Card(
        modifier = modifier
            .aspectRatio(5f / 7f)
            // pressable 在外层：按压蒙层跟随 Sink 缩放一起缩小（与 VideoCard 同序）
            .pressable(
                interactionSource = null,
                indication = sinkFeedback,
                delay = null,
            )
            .tvCardEffect(
                onClick = onClick,
                // shape 缓存：每次重组新建会让裁剪路径无法复用（滚动时反复构建 Squircle 路径）
                shape = cardShape,
                focusedScale = 1.03f,
                glow = false,
                onLongClick = onLongClick,
                // 按压蒙层固定浅色模式同款浅黑：收藏/历史卡片深色模式与浅色表现一致
                pressColor = LightOnSurface,
                // 保留裁剪（蒙层需要圆角），但换成了上面的圆角矩形快路径
            ),
        cornerRadius = corner,
        insideMargin = zeroMargin,
        colors = CardDefaults.defaultColors(color = surfaceColor),
    ) {
        // 海报区直接铺满整卡：原先外层套了一层 Column + weight(1f)，但只有一个子项，
        // 每次测量都要多走一次带权重的布局；去掉后排版结果完全一致
        Box(modifier = Modifier.fillMaxSize()) {
            PosterImage(
                title = history.title,
                // 错峰渐显轮到该卡片时才真正加载海报：把首屏 20 张海报的解码摊到错峰窗口内，
                // 避免进入页面首帧同时解码全部封面造成掉帧（未轮到时显示 Card 自带底色）
                cover = if (revealed) history.cover else null,
                modifier = Modifier.fillMaxSize(),
            )

            // 左上角：站源胶囊（与收藏卡片同款，编辑模式下隐藏避免与右上角叉号冲突）
            if (!editMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Spacing.posterOverlayMargin)
                        .background(PosterBadgeScrim, badgeShape)
                        .padding(horizontal = Spacing.posterBadgePaddingH, vertical = Spacing.posterBadgePaddingV),
                ) {
                    Text(
                        text = history.sourceName.ifBlank { "来源" },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 右上角：编辑模式删除叉号（与收藏卡片一致）
            if (editMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.posterOverlayMargin)
                        .size(30.dp)
                        .tvCardEffect(
                            onClick = onDelete,
                            shape = CircleShape,
                            focusedScale = 1.10f,
                            glow = false,
                        )
                        .background(PosterActionScrim, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Demibold.Close,
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            // 海报内标题样式（与首页同款）：底部渐变压暗 + 标题 / 副标题（副标题 = 当前集数）
            // 只铺底部 40%：曲线（span = 1）在 40% 高度内重新铺满，与原来整卡铺满 + span = 0.4 等价，
            // 上半部分原本就是全透明，收窄后填充面积降到 40%
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .align(Alignment.BottomCenter)
                    .background(captionScrim),
            )
            PosterCaption(
                title = history.title,
                subtitle = history.episodeTitle.takeIf { it.isNotBlank() },
                modifier = Modifier.align(Alignment.BottomStart),
            )

            // 播放进度细条：贴海报最底部，通栏（画在渐变压暗之上，保证进度色不被压暗）
            // 底色条 + 进度条原本是两个 Box（多层测量 + 一层 fillMaxWidth 分数布局），
            // 合并成一次绘制，像素结果一致
            //
            // 实验版：整条不挂载，用于验证「历史页比首页视频卡片少 10 帧左右」是否由进度条造成。
            // 关掉后卡片少一个布局节点 + 一次双矩形绘制；改回 true 即恢复原样
            if (HISTORY_CARD_SHOW_PROGRESS) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(progressBarHeight)
                        .drawBehind {
                            drawRect(Color(0x44FFFFFF))
                            if (progress > 0f) {
                                drawRect(
                                    color = progressColor,
                                    size = Size(size.width * progress, size.height),
                                )
                            }
                        },
                )
            }
        }
    }
}
