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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.data.FavoriteUpdateItem
import com.ican.tvplay.ui.UpdatesViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.BlurredBar
import com.ican.tvplay.ui.components.LocalEnableTopBarBlur
import com.ican.tvplay.ui.components.MiuixBackButton
import com.ican.tvplay.ui.components.PosterImage
import com.ican.tvplay.ui.components.dialogBottomMargin
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.rememberBlurBackdrop
import com.ican.tvplay.ui.components.topBarPadding
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.Spacing
import com.ican.tvplay.ui.theme.SquircleShape
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
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
                onPlay(prepared.video.id, item.firstUnwatchedIndex, prepared.flag, false)
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
                        .padding(topBarPadding()),
                ) {
                    Box(modifier = Modifier.align(Alignment.CenterStart)) {
                        MiuixBackButton(onClick = onBack)
                    }
                    Text(
                        text = "追番更新",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
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
                    top = innerPadding.calculateTopPadding() + Spacing.pageTop,
                    bottom = Spacing.bottomBarInset,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
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

                    if (today.isNotEmpty()) {
                        item { SectionTitle("今日更新", today.size) }
                        items(today, key = { it.favorite.videoId }, contentType = { "update" }) { item ->
                            UpdateRow(
                                item = item,
                                onClick = { play(item) },
                                onMarkRead = { viewModel.markRead(item.favorite.videoId) },
                            )
                        }
                    }

                    if (pending.isNotEmpty()) {
                        item { SectionTitle("待看更新", pending.size) }
                        items(pending, key = { it.favorite.videoId }, contentType = { "update" }) { item ->
                            UpdateRow(
                                item = item,
                                onClick = { play(item) },
                                onMarkRead = { viewModel.markRead(item.favorite.videoId) },
                            )
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
) {
    OverlayDialog(
        show = show,
        title = "更新提醒",
        titleColor = MiuixTheme.colorScheme.onBackground,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
        renderInRootScaffold = true,
        outsideMargin = DpSize(16.dp, dialogBottomMargin()),
        largeScreen = false,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(items, key = { it.favorite.videoId }, contentType = { "update" }) { item ->
                    PopupUpdateRow(item = item, onClick = { onPlay(item) })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(text = "知道了", onClick = onDismiss)
            }
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
            fontWeight = FontWeight.SemiBold,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvCardEffect(
                onClick = onClick,
                shape = SquircleShape(Spacing.corner),
                focusedScale = 1.02f,
                glow = false,
            ),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.favorite.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "更新至第 ${item.totalEpisodes} 集 · 待看 ${item.unwatchedCount} 集",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.favorite.sourceName.ifBlank { "来源" },
                    style = MaterialTheme.typography.labelSmall,
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

/** 弹窗内的紧凑条目 */
@Composable
private fun PopupUpdateRow(item: FavoriteUpdateItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvCardEffect(
                onClick = onClick,
                shape = SquircleShape(Spacing.corner),
                focusedScale = 1.02f,
                glow = false,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.favorite.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "第 ${item.totalEpisodes} 集 · 待看 ${item.unwatchedCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}
