package com.ican.tvplay.ui.collection

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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.data.FavoriteUpdateItem
import com.ican.tvplay.ui.UpdatesViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.BlurredBar
import com.ican.tvplay.ui.components.EmptyState
import com.ican.tvplay.ui.components.LocalEnableTopBarBlur
import com.ican.tvplay.ui.components.MiuixBackButton
import com.ican.tvplay.ui.components.MiuixBottomSheetDialog
import com.ican.tvplay.ui.components.PosterImage
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.rememberBlurBackdrop
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.LocalDarkTheme
import com.ican.tvplay.ui.theme.Spacing
import com.ican.tvplay.ui.theme.SquircleShape
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 追番更新页：收藏页入口进入。
 * 分「今日更新」「待看更新」两组，点击条目直接进播放页并选中第一条未看集；
 * 支持单部标记已看、全部已读、手动重新检查。
 */
@Composable
fun UpdateScreen(
    onPlay: (videoId: String, episodeIndex: Int, flag: String, preInit: Boolean) -> Unit,
    onBack: () -> Unit = {},
    onScheduleClick: () -> Unit = {},
) {
    val viewModel = appViewModel { UpdatesViewModel(this) }
    val today by viewModel.todayUpdates.collectAsStateWithLifecycle()
    val pending by viewModel.pendingUpdates.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasUpdates = today.isNotEmpty() || pending.isNotEmpty()
    // 手动检测的结果反馈：点「重新检查」后等 checking 由 true → false 再提示完成
    var awaitCheckFeedback by remember { mutableStateOf(false) }
    LaunchedEffect(checking) {
        if (awaitCheckFeedback && !checking) {
            awaitCheckFeedback = false
            android.widget.Toast.makeText(context, "更新检测完成", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val backdrop = rememberBlurBackdrop(LocalEnableTopBarBlur.current)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    fun play(item: FavoriteUpdateItem) {
        scope.launch {
            val prepared = viewModel.preparePlay(item)
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
                onPlay(prepared.video.id, item.resumeIndex, prepared.flag, false)
            }
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            BlurredBar(backdrop) {
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
                        text = "追番更新",
                        modifier = Modifier.align(Alignment.Center),
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
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = pageHorizontalPadding(),
                    end = pageHorizontalPadding(),
                    top = innerPadding.calculateTopPadding(),
                    // 本页没有悬浮底栏，底部只留常规内容留白（16dp）
                    bottom = Spacing.contentBottomInset,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
            ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = "重新检查",
                                summary = if (checking) "正在检测收藏剧集更新…" else "手动检测全部收藏的最新集数",
                                enabled = !checking,
                                onClick = {
                                    awaitCheckFeedback = true
                                    viewModel.manualCheck()
                                },
                            )
                            ArrowPreference(
                                title = "全部标记已读",
                                summary = "把当前所有更新标记为已看",
                                enabled = hasUpdates,
                                onClick = {
                                    viewModel.markAllRead()
                                    android.widget.Toast.makeText(
                                        context,
                                        "已全部标记为已看",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                            ArrowPreference(
                                title = "配置更新提醒",
                                summary = "为收藏的视频设置每周更新日",
                                onClick = onScheduleClick,
                            )
                        }
                    }

                    // 每个板块整体一张大卡片（与设置页一致的连体列表）：标题在卡片内，
                    // 条目之间用分隔线相连，点击仅显示按压蒙层、不做缩放
                    if (today.isNotEmpty()) {
                        item {
                            UpdateSectionCard(
                                title = "今日更新",
                                items = today,
                                onPlay = { play(it) },
                                onMarkRead = { viewModel.markRead(it.favorite.videoId) },
                            )
                        }
                    }

                    if (pending.isNotEmpty()) {
                        item {
                            UpdateSectionCard(
                                title = "待看更新",
                                items = pending,
                                onPlay = { play(it) },
                                onMarkRead = { viewModel.markRead(it.favorite.videoId) },
                            )
                        }
                    }

            }
            // 无更新时在列表上层居中显示空态（顶部操作卡片仍可点击）
            if (today.isEmpty() && pending.isEmpty()) {
                if (checking) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                bottom = Spacing.contentBottomInset,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "正在检测收藏剧集更新…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    EmptyState(
                        text = "暂无更新",
                        modifier = Modifier.padding(
                            top = innerPadding.calculateTopPadding(),
                            bottom = Spacing.contentBottomInset,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * 启动自动弹窗：当天有更新时展示一次（每天只弹一次）。
 * 点击条目直接进播放页并选中第一条未看集。
 */
@Composable
fun UpdatePopupDialog(
    show: Boolean,
    items: List<FavoriteUpdateItem>,
    onDismiss: () -> Unit,
    onPlay: (FavoriteUpdateItem) -> Unit,
    onOpenAll: () -> Unit,
    title: String = "更新提醒",
) {
    MiuixBottomSheetDialog(
        show = show,
        title = title,
        onDismiss = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            // 卡片之间的间距统一 16dp；首条贴标题、末条贴按钮，不额外叠加
            verticalArrangement = Arrangement.spacedBy(Spacing.dialogItemPadding),
        ) {
            items(items, key = { it.favorite.videoId }, contentType = { "update" }) { item ->
                PopupUpdateRow(item = item, onClick = { onPlay(item) })
            }
        }
        // 按钮行按全局弹窗规范：左右平分、取消在左
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.dialogTitleGap),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = "稍后",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.dialogButtonGap))
            TextButton(
                text = "查看全部",
                onClick = onOpenAll,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

/** 板块大卡片：卡片内「标题 + 连体条目列表」（与设置页一致，条目之间不画分隔线） */
@Composable
private fun UpdateSectionCard(
    title: String,
    items: List<FavoriteUpdateItem>,
    onPlay: (FavoriteUpdateItem) -> Unit,
    onMarkRead: (FavoriteUpdateItem) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        SectionTitle(
            text = title,
            count = items.size,
            modifier = Modifier.padding(
                start = Spacing.dialogItemPadding,
                end = Spacing.dialogItemPadding,
                top = Spacing.dialogItemPadding,
            ),
        )
        items.forEach { item ->
            UpdateRow(
                item = item,
                onClick = { onPlay(item) },
                onMarkRead = { onMarkRead(item) },
            )
        }
        // 卡片底部留白，与顶部 16dp 内边距在视觉上平衡
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionTitle(text: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        // 本页板块标题底部间距固定 8dp（仅本页，不改全局 Spacing.titleContentGap）
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count 部",
            style = MaterialTheme.typography.labelMedium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 更新条目：板块大卡片内的一行（海报 + 标题 + 更新进度 + 单部「已看」） */
@Composable
private fun UpdateRow(
    item: FavoriteUpdateItem,
    onClick: () -> Unit,
    onMarkRead: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 连体行仅保留点击，不显示任何按压蒙层/缩放（按压反馈只留给右侧「已看」按钮）
            .tvCardEffect(
                onClick = onClick,
                shape = RectangleShape,
                focusedScale = 1f,
                glow = false,
                pressEffect = false,
            )
            // 左右与卡片内边距一致；上下 8dp 让相邻条目之间保持 16dp，看起来是连体列表
            .padding(
                start = Spacing.dialogItemPadding,
                end = Spacing.dialogItemPadding,
                top = 8.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 76.dp)
                .clip(SquircleShape(Spacing.updateCardCorner)),
        ) {
            PosterImage(
                title = item.favorite.title,
                cover = item.favorite.cover,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        // 海报右侧 4 行：标题贴海报顶部、站源贴海报底部，中间两行分别是「更新至 x 集」
        // 和「当前看到那一集的视频标题」，整列高度与海报一致
        Column(
            modifier = Modifier
                .weight(1f)
                .height(76.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.favorite.title,
                style = MaterialTheme.typography.titleSmall,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "已更至 第${item.totalEpisodes}集",
                style = MaterialTheme.typography.bodySmall,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // 「已看到」+ 当前看到那一集的视频标题（取自历史记录），没有历史时退回集数
                text = "已看到 ${item.seenEpisodeTitle()}",
                style = MaterialTheme.typography.bodySmall,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.favorite.sourceName.ifBlank { "来源" },
                // 与上面两行统一字号（bodySmall 12sp）
                style = MaterialTheme.typography.bodySmall,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        // 每行重复的次级操作：去掉灰底胶囊，改成透明底文字按钮，避免按钮盖过更新信息；
        // 按压时的 miuix 蒙层/缩放由 TextButton 自带，保留
        TextButton(
            text = "已看",
            onClick = onMarkRead,
            colors = ButtonDefaults.textButtonColors(
                color = Color.Transparent,
                disabledColor = Color.Transparent,
                textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                disabledTextColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            ),
        )
    }
}

/**
 * 弹窗内的紧凑条目（两行式）：
 * 第一行「标题 + 右侧已更至 第 x 集」，第二行「已看到 + 已看那一集的视频标题」。
 */
@Composable
private fun PopupUpdateRow(
    item: FavoriteUpdateItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvCardEffect(
                onClick = onClick,
                // 与条目内预览小图的圆角保持一致（Spacing.updateCardCorner = 10dp）
                shape = SquircleShape(Spacing.updateCardCorner),
                // 弹窗内的卡片只保留点击，不做缩放/按压蒙层
                focusedScale = 1f,
                glow = false,
                pressEffect = false,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 76.dp)
                .clip(SquircleShape(Spacing.updateCardCorner)),
        ) {
            PosterImage(
                title = item.favorite.title,
                cover = item.favorite.cover,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        // 文字块与海报同高（76dp），三行均分：标题 / 已更至 / 已看到
        Column(
            modifier = Modifier
                .weight(1f)
                .height(76.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.favorite.title,
                style = MaterialTheme.typography.titleMedium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "已更至 第${item.totalEpisodes}集",
                style = MaterialTheme.typography.bodyMedium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
            Text(
                text = "已看到 ${item.seenEpisodeTitle()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 「已看到」后面紧跟的集标题：取历史记录里那一集的视频标题，没有历史就是未开始观看 */
private fun FavoriteUpdateItem.seenEpisodeTitle(): String =
    history?.episodeTitle?.takeIf { it.isNotBlank() } ?: "未开始观看"
