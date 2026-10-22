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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import com.ican.tvplay.ui.components.PosterImage
import com.ican.tvplay.data.local.FavoriteEntity
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.ui.CollectionViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.BlurredBar
import com.ican.tvplay.ui.components.EmptyState
import com.ican.tvplay.ui.components.LocalEnableTopBarBlur
import com.ican.tvplay.ui.components.MiuixBackButton
import com.ican.tvplay.ui.components.MiuixConfirmDialog
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.rememberBlurBackdrop
import com.ican.tvplay.ui.components.topBarPadding
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.components.videoGridColumns
import com.ican.tvplay.ui.theme.LightOnSurface
import com.ican.tvplay.ui.theme.PosterActionScrim
import com.ican.tvplay.ui.theme.PosterBadgeScrim
import com.ican.tvplay.ui.theme.PosterBottomScrim
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
    val homeLayoutSize by viewModel.homeLayoutSize.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 编辑模式：长按卡片进入（卡片显示叉号）；编辑模式内再长按 → 清空全部确认弹窗；返回键退出
    var editMode by remember { mutableStateOf(false) }
    // 清空确认弹窗
    var showConfirm by remember { mutableStateOf(false) }

    // 编辑模式下按返回 = 取消编辑（不退出页面）；清空弹窗打开时返回由弹窗自行消费
    BackHandler(enabled = editMode && !showConfirm) { editMode = false }

    // 固定居中大标题顶栏 + 毛玻璃
    val backdrop = rememberBlurBackdrop(LocalEnableTopBarBlur.current)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BlurredBar(backdrop) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(barColor)
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .height(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "我的收藏",
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
            if (favorites.isEmpty()) {
                EmptyState()
            } else {
                LazyVerticalGrid(
                    columns = videoGridColumns(homeLayoutSize),
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = pageHorizontalPadding(),
                        end = pageHorizontalPadding(),
                        top = innerPadding.calculateTopPadding() + Spacing.pageTop,
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


/** 收藏卡片：与首页 [VideoCard] 同款 miuix Card 容器 + SinkFeedback 按压形变；
 *  左上角站源胶囊，编辑模式下隐藏并在右上角显示删除叉号；底部渐变 + 标题。 */
@Composable
private fun FavoriteCard(
    entity: FavoriteEntity,
    editMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            // pressable 在外层：按压蒙层跟随 Sink 缩放一起缩小（与 VideoCard 同序）
            .pressable(
                interactionSource = null,
                indication = SinkFeedback(),
                delay = null,
            )
            .tvCardEffect(
                    onClick = onClick,
                    shape = RoundedCornerShape(Spacing.corner),
                    focusedScale = 1.05f,
                    glow = false,
                    onLongClick = onLongClick,
                    // 按压蒙层固定浅色模式同款浅黑：收藏/历史卡片深色模式与浅色表现一致
                    pressColor = LightOnSurface,
                ),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
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
                        .padding(8.dp)
                        .background(PosterBadgeScrim, RoundedCornerShape(percent = 50))
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

            // 右上角：编辑模式删除叉号
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
                        .background(PosterActionScrim, CircleShape),
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

            // 底部渐变 + 标题
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, PosterBottomScrim),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
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

    // 编辑模式：长按卡片进入（卡片显示叉号）；编辑模式内再长按 → 清空全部确认弹窗；返回键退出
    var editMode by remember { mutableStateOf(false) }
    // 清空确认弹窗
    var showConfirm by remember { mutableStateOf(false) }

    // 编辑模式下按返回 = 取消编辑（留在页面）；清空弹窗打开时返回由弹窗自行消费，
    // 非编辑模式不拦截，保持原有返回上一页行为
    BackHandler(enabled = editMode && !showConfirm) { editMode = false }

    // 固定居中大标题顶栏 + 毛玻璃
    val backdrop = rememberBlurBackdrop(LocalEnableTopBarBlur.current)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.background

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
                    // 返回按钮靠左
                    Box(modifier = Modifier.align(Alignment.CenterStart)) {
                        MiuixBackButton(onClick = { if (editMode) editMode = false else onBack() })
                    }
                    // 标题居中
                    Text(
                        text = "播放历史",
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
            if (histories.isEmpty()) {
                EmptyState()
            } else {
                LazyVerticalGrid(
                    columns = videoGridColumns(homeLayoutSize),
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = pageHorizontalPadding(),
                        end = pageHorizontalPadding(),
                        top = innerPadding.calculateTopPadding() + Spacing.pageTop,
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
 * 历史卡片：与首页 [VideoCard] 同款 miuix Card 容器 + SinkFeedback 按压形变；
 * 左上角站源胶囊（编辑模式下隐藏），右上角编辑模式删除叉号；
 * 底部标签胶囊显示「看到的集数」（位于标题上方），标题下方保留播放进度细条。
 */
@Composable
private fun HistoryCard(
    history: HistoryEntity,
    editMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
) {
    val progress = if (history.durationMs > 0) {
        (history.positionMs.toFloat() / history.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Card(
        modifier = Modifier
            // pressable 在外层：按压蒙层跟随 Sink 缩放一起缩小（与 VideoCard 同序）
            .pressable(
                interactionSource = null,
                indication = SinkFeedback(),
                delay = null,
            )
            .tvCardEffect(
                    onClick = onClick,
                    shape = RoundedCornerShape(Spacing.corner),
                    focusedScale = 1.05f,
                    glow = false,
                    onLongClick = onLongClick,
                    // 按压蒙层固定浅色模式同款浅黑：收藏/历史卡片深色模式与浅色表现一致
                    pressColor = LightOnSurface,
                ),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
        ) {
            PosterImage(
                title = history.title,
                cover = history.cover,
                modifier = Modifier.fillMaxSize(),
            )

            // 左上角：站源胶囊（与收藏卡片同款，编辑模式下隐藏避免与右上角叉号冲突）
            if (!editMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(PosterBadgeScrim, RoundedCornerShape(percent = 50))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = history.sourceName.ifBlank { "来源" },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
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
                        .padding(8.dp)
                        .size(30.dp)
                        .tvCardEffect(
                            onClick = onDelete,
                            shape = CircleShape,
                            focusedScale = 1.15f,
                            glow = false,
                        )
                        .background(PosterActionScrim, CircleShape),
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
                            listOf(Color.Transparent, PosterBottomScrim),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 集数小标签：直接显示集标题
                if (history.episodeTitle.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(PosterBadgeScrim, RoundedCornerShape(percent = 50))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = history.episodeTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                        .height(4.dp)
                        .background(Color(0x44FFFFFF), CircleShape),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        }
    }
}
