package com.ican.tvplay.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.player.core.PlaySpec
import com.ican.tvplay.player.core.Players
import com.ican.tvplay.ui.MultiSourceSearchViewModel
import com.ican.tvplay.ui.appContainer
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.CircleBackButton
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.nav.Routes
import kotlinx.coroutines.launch
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * 跨站源搜索结果页（图一风格）：
 * - 左侧：全部可用站源（来自配置文件），每个条目显示名称 + 搜索命中数量
 * - 右侧：当前选中站源的视频卡片网格
 * - 点击卡片 → 加载完整详情 + 解析播放地址 → 启动播放器 → 跳转到 PlayerScreen
 */
@Composable
fun MultiSourceSearchScreen(
    initialQuery: String,
    onBack: () -> Unit,
    onPlay: (videoId: String, episodeIndex: Int, flag: String, preInitialized: Boolean) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = appViewModel { MultiSourceSearchViewModel(context) }

    val sites by vm.sites.collectAsStateWithLifecycle()
    val selectedIndex by vm.selectedIndex.collectAsStateWithLifecycle()
    val displayVideos by vm.displayVideos.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    var queryText by remember { mutableStateOf(initialQuery) }

    // 首次进入时触发跨站搜索
    LaunchedEffect(initialQuery) {
        vm.searchAll(initialQuery)
    }

    // 回车/点搜索按钮重新搜索
    fun doSearch() {
        val q = queryText.trim()
        if (q.isNotEmpty()) vm.searchAll(q)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ========== 顶栏 ==========
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            CircleBackButton(onClick = onBack)

            // 搜索框（紧凑嵌入顶栏）
            val shape = RoundedCornerShape(percent = 50)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = AppIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                BasicTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.titleSmall.fontSize,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    decorationBox = { inner ->
                        if (queryText.isEmpty()) {
                            Text(
                                text = "跨站源搜索…",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .tvCardEffect(
                            onClick = { doSearch() },
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

        // ========== 主体：左站源列表 + 右卡片网格 ==========
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp),
        ) {
            // 左侧站源列表（纵向）
            LazyColumn(
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 4.dp),
            ) {
                // "全部"（显示全部站点的所有视频合并）— 暂不实现，直接按站点
                items(sites, key = { it.site.key }) { entry ->
                    val idx = sites.indexOf(entry)
                    val selected = idx == selectedIndex
                    SiteSourceChip(
                        name = entry.site.name.ifBlank { entry.site.key },
                        count = entry.videos.size,
                        selected = selected,
                        onClick = { vm.selectSite(idx) },
                    )
                }
                if (loading || sites.isEmpty()) {
                    item {
                        Text(
                            text = if (loading) "搜索中…" else "暂无可用站点",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // 右侧卡片网格
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 132.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(displayVideos, key = { _, v -> "${v.id}_${v.playFrom}" }) { _, video ->
                    val siteKey = sites.getOrNull(selectedIndex)?.site?.key.orEmpty()
                    CrossSourceVideoCard(
                        video = video,
                        siteName = sites.getOrNull(selectedIndex)?.site?.name.orEmpty(),
                        onClick = {
                            scope.launch {
                                handlePlayClick(vm, context, video, siteKey, onPlay)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 点击卡片的播放流程：
 * 1. 从目标站点拉取完整详情（带 episodes / playSources）
 * 2. 解析播放地址（spider 走 playerContent，HTTP 直接返回原 URL）
 * 3. 启动 Players
 * 4. 通知导航层跳转 PlayerScreen（preInit=true → PlayerScreen 不再重新 start）
 */
private suspend fun handlePlayClick(
    vm: MultiSourceSearchViewModel,
    context: android.content.Context,
    video: Video,
    siteKey: String,
    onPlay: (String, Int, String, Boolean) -> Unit,
) {
    val fullVideo = vm.getDetail(siteKey, video.id) ?: return
    val firstLine = fullVideo.playSources.firstOrNull()
    if (firstLine == null) return
    val firstEp = firstLine.episodes.firstOrNull() ?: return

    // 解析播放地址
    val source = vm.resolvePlaySourceForSite(siteKey, firstEp.playUrl, firstLine.flag)
        ?: com.ican.tvplay.data.PlaySource(firstEp.playUrl)

    Players.start(
        context = context,
        spec = PlaySpec(url = source.url, headers = source.headers),
        startPositionMs = 0L,
    )

    onPlay(fullVideo.id, 0, firstLine.flag, true)
}

// ========== 左侧站源 chip ==========

@Composable
private fun SiteSourceChip(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(percent = 50)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .tvCardEffect(onClick = onClick, shape = shape, focusedScale = 1.05f, glow = false)
            .background(bg, shape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(
            text = "$count 条",
            style = MaterialTheme.typography.labelSmall,
            color = fg.copy(alpha = 0.7f),
        )
    }
}

// ========== 右侧视频卡片 ==========

@Composable
private fun CrossSourceVideoCard(
    video: Video,
    siteName: String,
    onClick: () -> Unit,
) {
    val updateState = video.episodes.let { eps ->
        if (eps.isEmpty()) ""
        else {
            val lastTitle = eps.lastOrNull()?.title ?: ""
            when {
                lastTitle.contains("完结") -> "已完结"
                eps.size >= 2 -> "更新至第${eps.size}集"
                else -> "共${eps.size}集"
            }
        }
    }

    val sourceChipColor = hashColor(video.categoryName.ifBlank { siteName.ifBlank { "未知" } })

    Column(
        modifier = Modifier.tvCardEffect(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            focusedScale = 1.05f,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(186.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = video.cover,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 右上角来源标签
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(sourceChipColor, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = siteName.ifBlank { video.categoryName.ifBlank { "来源" } },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            // 底部渐变 + 更新状态 + 视频名
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE6000000)),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (updateState.isNotBlank()) {
                        Text(
                            text = updateState,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun hashColor(text: String): Color {
    val hue = (text.hashCode() % 360 + 360) % 360
    return Color.hsv(hue.toFloat(), 0.55f, 0.75f).copy(alpha = 0.92f)
}
