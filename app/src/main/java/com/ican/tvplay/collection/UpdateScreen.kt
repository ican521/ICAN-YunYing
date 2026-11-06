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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * 追番更新页：收藏页入口进入。
 * 分「今日更新」「待看更新」两组，点击条目直接进播放页并选中第一条未看集；
 * 支持单部标记已看、全部已读、手动重新检查。
 */
@Composable
fun UpdateScreen(
    onPlay: (videoId: String, episodeIndex: Int, flag: String, preInit: Boolean) -> Unit,
    onBack: () -> Unit = {},
) {
    val viewModel = appViewModel { UpdatesViewModel(this) }
    val today by viewModel.todayUpdates.collectAsStateWithLifecycle()
    val pending by viewModel.pendingUpdates.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val backdrop = rememberBlurBackdrop(LocalEnableTopBarBlur.current)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.background

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
        containerColor = MaterialTheme.colorScheme.background,
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
                        color = MaterialTheme.colorScheme.onBackground,
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
                    bottom = Spacing.bottomBarInset,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.updateCardGap),
            ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = "重新检查",
                                summary = if (checking) "正在检测收藏剧集更新…" else "手动检测全部收藏的最新集数",
                                onClick = { viewModel.manualCheck() },
                            )
                            ArrowPreference(
                                title = "全部标记已读",
                                summary = "把当前所有更新标记为已看",
                                onClick = { viewModel.markAllRead() },
                            )
                        }
                    }

                    // 标题与第一条同属一个 item：标题底部到底部卡片的间距只保留
                    // titleContentGap(12dp)，不会再被 spacedBy(sectionSpacing) 叠加 16dp
                    if (today.isNotEmpty()) {
                        itemsIndexed(
                            today,
                            key = { _, item -> item.favorite.videoId },
                            contentType = { _, _ -> "update" },
                        ) { index, item ->
                            Column(
                                // 卡片间距改为 12dp 后，这里补 4dp 使板块之间的间距仍为 sectionSpacing(16dp)
                                modifier = if (index == 0) {
                                    Modifier.padding(top = Spacing.sectionSpacing - Spacing.updateCardGap)
                                } else {
                                    Modifier
                                },
                            ) {
                                if (index == 0) SectionTitle("今日更新", today.size)
                                UpdateRow(
                                    item = item,
                                    onClick = { play(item) },
                                    onMarkRead = { viewModel.markRead(item.favorite.videoId) },
                                )
                            }
                        }
                    }

                    if (pending.isNotEmpty()) {
                        itemsIndexed(
                            pending,
                            key = { _, item -> item.favorite.videoId },
                            contentType = { _, _ -> "update" },
                        ) { index, item ->
                            Column(
                                // 同上：首条上方补 4dp，保证与上一板块的间距为 16dp
                                modifier = if (index == 0) {
                                    Modifier.padding(top = Spacing.sectionSpacing - Spacing.updateCardGap)
                                } else {
                                    Modifier
                                },
                            ) {
                                if (index == 0) SectionTitle("待看更新", pending.size)
                                UpdateRow(
                                    item = item,
                                    onClick = { play(item) },
                                    onMarkRead = { viewModel.markRead(item.favorite.videoId) },
                                )
                            }
                        }
                    }

                    if (today.isEmpty() && pending.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (checking) "正在检测收藏剧集更新…" else "暂无更新，先收藏几部剧吧",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
) {
    MiuixBottomSheetDialog(
        show = show,
        title = "更新提醒",
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

@Composable
private fun SectionTitle(text: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.titleContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count 部",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 更新条目：海报 + 标题 + 更新进度 + 单部「已看」 */
@Composable
private fun UpdateRow(
    item: FavoriteUpdateItem,
    onClick: () -> Unit,
    onMarkRead: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvCardEffect(
                onClick = onClick,
                shape = SquircleShape(Spacing.corner),
                focusedScale = 1.03f,
                glow = false,
            ),
        insideMargin = PaddingValues(0.dp),
        // 浅色主题去掉卡片背景（直接透出页面底色）；深色主题沿用 miuix 默认背景
        colors = if (isDark) {
            CardDefaults.defaultColors()
        } else {
            CardDefaults.defaultColors(color = Color.Transparent)
        },
    ) {
        Row(
            // 海报与文字贴卡片最左；上下不留白，卡片高度与海报（76dp）一致
            modifier = Modifier.fillMaxWidth().padding(end = Spacing.dialogItemPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 76.dp)
                    .clip(SquircleShape(10.dp)),
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
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "已更至 第${item.totalEpisodes}集",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // 「已看到」+ 当前看到那一集的视频标题（取自历史记录），没有历史时退回集数
                    text = "已看到 ${item.seenEpisodeTitle()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.favorite.sourceName.ifBlank { "来源" },
                    // 与上面两行统一字号（bodySmall 12sp）
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                text = "已看",
                onClick = onMarkRead,
            )
        }
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
                // 与条目内预览小图的圆角保持一致
                shape = SquircleShape(8.dp),
                focusedScale = 1.03f,
                glow = false,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 76.dp)
                .clip(SquircleShape(8.dp)),
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
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "已更至 第${item.totalEpisodes}集",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = "已看到 ${item.seenEpisodeTitle()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 「已看到」后面紧跟的集标题：取历史记录里那一集的视频标题，没有历史就是未开始观看 */
private fun FavoriteUpdateItem.seenEpisodeTitle(): String =
    history?.episodeTitle?.takeIf { it.isNotBlank() } ?: "未开始观看"
