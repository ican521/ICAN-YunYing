package com.ican.tvplay.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import coil3.compose.AsyncImage
import com.ican.tvplay.data.local.FavoriteEntity
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.ui.CollectionViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.BlurredBar
import com.ican.tvplay.ui.components.LocalEnableBlur
import com.ican.tvplay.ui.components.MiuixBackButton
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.rememberBlurBackdrop
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.Spacing
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(
    onPlay: (videoId: String, episodeIndex: Int, flag: String, preInitialized: Boolean) -> Unit,
    onBack: () -> Unit = {},
) {
    val viewModel = appViewModel { CollectionViewModel(this) }
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 编辑模式：控制卡片叉号显示 + 右上角按钮变红
    var editMode by remember { mutableStateOf(false) }
    // 清空确认弹窗
    var showConfirm by remember { mutableStateOf(false) }

    // 编辑模式下按返回 = 取消编辑（不退出页面）；清空弹窗打开时返回由弹窗自行消费
    BackHandler(enabled = editMode && !showConfirm) { editMode = false }

    // KernelSU 同款：折叠顶栏 + 毛玻璃
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlurState = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlurState)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = "我的收藏",
                    titleColor = MaterialTheme.colorScheme.onBackground,
                    largeTitleColor = MaterialTheme.colorScheme.onBackground,
                    actions = {
                        // 右上角圆形删除按钮（保留项目圆钮样式，与历史页完全一致）
                        if (favorites.isNotEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(Spacing.circleButton)
                                    .tvCardEffect(
                                        onClick = {
                                            if (!editMode) {
                                                // 进入编辑模式
                                                editMode = true
                                            } else {
                                                // 编辑模式 → 弹出清空确认
                                                showConfirm = true
                                            }
                                        },
                                        shape = CircleShape,
                                        focusedScale = 1.1f,
                                        glow = false,
                                    )
                                    .background(
                                        if (editMode) Color(0xFFFF5C5C)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape,
                                    ),
                            ) {
                                Icon(
                                    imageVector = AppIcons.Delete,
                                    contentDescription = if (editMode) "清空全部" else "编辑",
                                    tint = if (editMode) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            if (favorites.isEmpty()) {
                EmptyText(text = "还没有收藏，去首页发现好片吧")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(132.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = pageHorizontalPadding(),
                        end = pageHorizontalPadding(),
                        top = innerPadding.calculateTopPadding() + Spacing.titleContentGap,
                        bottom = Spacing.bottomBarInset,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.cardGapH),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGapV),
                ) {
                items(favorites, key = { it.videoId }) { item ->
                    FavoriteCard(
                        entity = item,
                        editMode = editMode,
                        onClick = {
                            if (editMode) {
                                // 编辑模式下点击卡片也触发单条删除（更方便）
                                viewModel.removeFavorite(item.videoId)
                            } else {
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
                    )
                }
                }
            }
        }
    }

    // 清空全部确认弹窗
    if (showConfirm) {
        Dialog(onDismissRequest = {
            // 取消 → 关闭弹窗 + 退出编辑模式
            showConfirm = false
            editMode = false
        }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Spacing.corner))
                    .padding(24.dp),
            ) {
                Text(
                    text = "删除全部收藏",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "确定要清空所有收藏吗？此操作不可恢复。",
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
                            .clickable {
                                showConfirm = false
                                editMode = false
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        text = "确定",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5C5C),
                        modifier = Modifier
                            .clickable {
                                viewModel.clearFavorites()
                                showConfirm = false
                                editMode = false
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** 收藏卡片：样式与搜索详情页卡片一致（3:4 海报 + 右上角站源标签 + 底部渐变合并标签 + 标题），编辑模式叉号替换站源标签 */
@Composable
private fun FavoriteCard(
    entity: FavoriteEntity,
    editMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.tvCardEffect(
            onClick = onClick,
            shape = RoundedCornerShape(Spacing.corner),
            focusedScale = 1.05f,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(Spacing.corner))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = entity.cover,
                contentDescription = entity.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // 右上角：站源标签（编辑模式下替换为删除叉号）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                if (editMode) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .tvCardEffect(
                                onClick = onDelete,
                                shape = CircleShape,
                                focusedScale = 1.15f,
                                glow = false,
                            )
                            .background(Color(0x88000000), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = "删除",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(Color(0x66000000), RoundedCornerShape(Spacing.corner))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = entity.sourceName.ifBlank { "来源" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 底部渐变 + 合并标签胶囊 + 标题
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE6000000)),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (entity.categoryName.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x66000000), RoundedCornerShape(Spacing.corner))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = entity.categoryName,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = entity.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    onPlay: (videoId: String, episodeIndex: Int, flag: String, preInit: Boolean) -> Unit,
    onBack: () -> Unit = {},
) {
    val viewModel = appViewModel { CollectionViewModel(this) }
    val histories by viewModel.histories.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 编辑模式：控制卡片叉号显示 + 右上角按钮变红（与收藏页同款交互）
    var editMode by remember { mutableStateOf(false) }
    // 清空确认弹窗
    var showConfirm by remember { mutableStateOf(false) }

    // 编辑模式下按返回 = 取消编辑（留在页面）；清空弹窗打开时返回由弹窗自行消费，
    // 非编辑模式不拦截，保持原有返回上一页行为
    BackHandler(enabled = editMode && !showConfirm) { editMode = false }

    // KernelSU 同款：折叠顶栏 + 毛玻璃
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlurState = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlurState)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = "播放历史",
                    titleColor = MaterialTheme.colorScheme.onBackground,
                    largeTitleColor = MaterialTheme.colorScheme.onBackground,
                    navigationIcon = {
                        // 编辑模式下返回 = 取消编辑
                        MiuixBackButton(onClick = { if (editMode) editMode = false else onBack() })
                    },
                    actions = {
                        // 右上角圆形删除按钮（与收藏页完全同款：两段式 编辑→清空确认）
                        if (histories.isNotEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(Spacing.circleButton)
                                    .tvCardEffect(
                                        onClick = {
                                            if (!editMode) {
                                                editMode = true
                                            } else {
                                                showConfirm = true
                                            }
                                        },
                                        shape = CircleShape,
                                        focusedScale = 1.1f,
                                        glow = false,
                                    )
                                    .background(
                                        if (editMode) Color(0xFFFF5C5C)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape,
                                    ),
                            ) {
                                Icon(
                                    imageVector = AppIcons.Delete,
                                    contentDescription = if (editMode) "清空全部" else "编辑",
                                    tint = if (editMode) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            if (histories.isEmpty()) {
                EmptyText(text = "还没有播放记录")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(132.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = pageHorizontalPadding(),
                        end = pageHorizontalPadding(),
                        top = innerPadding.calculateTopPadding() + Spacing.titleContentGap,
                        bottom = Spacing.bottomBarInset,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.cardGapH),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGapV),
                ) {
                items(histories, key = { it.videoId }) { history ->
                    HistoryCard(
                        history = history,
                        editMode = editMode,
                        onClick = {
                            if (editMode) {
                                // 编辑模式下点击卡片也触发单条删除（更方便）
                                viewModel.removeHistory(history.videoId)
                            } else {
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
                    )
                }
                }
            }
        }
    }

    // 清空全部确认弹窗（与收藏页同款）
    if (showConfirm) {
        Dialog(onDismissRequest = {
            showConfirm = false
            editMode = false
        }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Spacing.corner))
                    .padding(24.dp),
            ) {
                Text(
                    text = "删除全部播放历史",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "确定要清空所有播放记录吗？此操作不可恢复。",
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
                            .clickable {
                                showConfirm = false
                                editMode = false
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        text = "确定",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5C5C),
                        modifier = Modifier
                            .clickable {
                                viewModel.clearHistory()
                                showConfirm = false
                                editMode = false
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * 历史卡片：海报比例/圆角/渐变底/编辑叉号与 [FavoriteCard] 完全同款；
 * 差异点：底部标签胶囊显示「看到的集数」（位于标题上方），标题下方保留播放进度细条。
 */
@Composable
private fun HistoryCard(
    history: HistoryEntity,
    editMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = if (history.durationMs > 0) {
        (history.positionMs.toFloat() / history.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.tvCardEffect(
            onClick = onClick,
            shape = RoundedCornerShape(Spacing.corner),
            focusedScale = 1.05f,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(Spacing.corner))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = history.cover,
                contentDescription = history.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // 右上角：仅编辑模式显示删除叉号（与收藏卡片一致）
            if (editMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(30.dp)
                        .tvCardEffect(
                            onClick = onDelete,
                            shape = CircleShape,
                            focusedScale = 1.15f,
                            glow = false,
                        )
                        .background(Color(0x88000000), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            // 底部渐变 + 集数胶囊（标题上方） + 标题 + 播放进度条
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE6000000)),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 看到的集数：胶囊样式与收藏卡片站源标签一致
                Box(
                    modifier = Modifier
                        .background(Color(0x66000000), RoundedCornerShape(Spacing.corner))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = history.episodeTitle.ifBlank { "第${history.episodeIndex + 1}集" },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = history.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // 播放进度细条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x55FFFFFF), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
