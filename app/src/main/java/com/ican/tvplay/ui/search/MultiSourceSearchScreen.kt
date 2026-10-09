package com.ican.tvplay.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.player.core.PlaySpec
import com.ican.tvplay.player.core.Players
import com.ican.tvplay.ui.MultiSourceSearchViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.CircleBackButton
import com.ican.tvplay.ui.components.siteShortName
import com.ican.tvplay.ui.components.tvCardEffect
import kotlinx.coroutines.launch
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * 跨站源搜索结果页（图一风格）：
 * - 左侧：「全部」+ 配置里全部可用站源，每个条目显示名称 + 搜索命中数量
 * - 右侧：默认展示所有站源合并的视频卡片网格；点击左侧筛选单个站源
 * - 卡片比例与首页一致 (3:4)，右上角站源标签，底部显示更新状态 / 备注 / 标题
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

    // 首次进入时触发跨站搜索（initialQuery 非空才触发）
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) vm.searchAll(initialQuery)
    }

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

            // 搜索框：与首页顶部胶囊搜索框同款（48dp 高、全圆角、surfaceVariant 底、20dp 图标、bodyLarge 字号）
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
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (queryText.isEmpty()) {
                            Text(
                                text = "跨站源搜索…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
            modifier = Modifier.fillMaxSize(),
        ) {
            // 左侧站源列表（纵向）
            LazyColumn(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 16.dp),
            ) {
                // === 具体站源（固定宽度胶囊，仅取 | 前名称，不显示条数）===
                itemsIndexed(sites, key = { _, entry -> entry.site.key }) { _, entry ->
                    val idx = sites.indexOf(entry)
                    SiteSourceChip(
                        name = siteShortName(entry.site.name, entry.site.key),
                        selected = idx == selectedIndex,
                        onClick = { vm.selectSite(idx) },
                    )
                }
                if (loading) {
                    item {
                        Text(
                            text = "搜索中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillParentMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
            }

            // 右侧卡片网格：手机强制 2 列，大屏（宽 > 640dp）自适应
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val columns = if (maxWidth < 640.dp) {
                    GridCells.Fixed(2)
                } else {
                    GridCells.Adaptive(minSize = 160.dp)
                }

                LazyVerticalGrid(
                    columns = columns,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 4.dp,
                        bottom = 16.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(displayVideos, key = { "${it.video.id}_${it.siteKey}" }) { dv ->
                        CrossSourceVideoCard(
                            video = dv.video,
                            siteName = dv.siteName,
                            onClick = {
                                scope.launch {
                                    handlePlayClick(vm, context, dv.video, dv.siteKey, onPlay)
                                }
                            },
                        )
                    }
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

    // 不在此处预解析播放地址：某些网盘 jar 在 playerContent 时触发扫码弹窗，
    // 必须进入播放页后再解析（PlayerScreen 用 cached.site/jarSpec 延迟解析）
    val entry = vm.siteEntry(siteKey)
    Players.cacheVideo(fullVideo, siteKey, entry?.site, entry?.jarSpec.orEmpty())

    // preInit=false: 让 PlayerScreen 自己调 Players.start()，此时 SurfaceView 已 attach，时序正确
    onPlay(fullVideo.id, 0, firstLine.flag, false)
}

// ========== 左侧站源 chip ==========

@Composable
private fun SiteSourceChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(percent = 50)

    // 固定宽度：所有胶囊等宽，不因名字长短变化
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .fillMaxWidth()
            .height(40.dp)
            .tvCardEffect(onClick = onClick, shape = shape, focusedScale = 1.05f, glow = false)
            .background(bg, shape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ========== 右侧视频卡片 ==========

/**
 * 根据 Video 的 rating (vodRemarks) 和 episodes 生成底部状态标签
 * 返回：主状态文本 + 标签列表
 */
private fun buildVideoStatus(video: Video): Pair<String, List<String>> {
    val tags = mutableListOf<String>()

    // rating 就是 vodRemarks（如 "更新至HD" / "全85集" / "国语" / "4K"）
    video.rating.trim().takeIf { it.isNotBlank() }?.let { r ->
        // 按常见分隔符拆分（空格 / / · / ,）
        r.split(" ", "/", "·", "，", ",").map { it.trim() }.filter { it.isNotBlank() }
            .forEach { part ->
                when {
                    part.contains("完结", ignoreCase = true) -> tags.add("已完结")
                    part.contains("更新") -> tags.add(part)  // "更新至HD" 原样保留
                    part.contains("全") -> tags.add(part)    // "全85集"
                    part.length <= 4 -> tags.add(part)       // "国语" / "4K" / "HD" 等短标签也收进去
                }
            }
    }

    // 从 episodes 推断集数
    val eps = video.episodes
    val firstPlayFrom = video.playSources.firstOrNull()?.episodes.orEmpty()
    val effectiveEps = firstPlayFrom.ifEmpty { eps }

    val mainStatus = when {
        tags.any { it.contains("完结") } -> "已完结"
        effectiveEps.isNotEmpty() -> {
            val last = effectiveEps.lastOrNull()?.title ?: ""
            when {
                last.contains("完结") -> "已完结"
                effectiveEps.size >= 2 -> "更新至第${effectiveEps.size}集"
                else -> ""
            }
        }
        else -> ""
    }

    // 如果 tags 里没有完结信息但有集数，补充
    if (mainStatus.isNotBlank() && tags.none { it.contains("完结") || it.contains("更新") }) {
        // 不再额外加
    }

    return mainStatus to tags.distinct()
}

@Composable
private fun CrossSourceVideoCard(
    video: Video,
    siteName: String,
    onClick: () -> Unit,
) {
    val (mainStatus, tags) = buildVideoStatus(video)
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
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = video.cover,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // 右上角：站源标签 + 状态标签（如果有）
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (mainStatus.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xCC000000), RoundedCornerShape(percent = 50))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = mainStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .background(sourceChipColor, RoundedCornerShape(percent = 50))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = siteName.ifBlank { video.categoryName.ifBlank { "来源" } },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 底部渐变 + 标签 + 标题
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 标签行
                    if (tags.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            tags.take(3).forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color.White.copy(alpha = 0.2f),
                                            RoundedCornerShape(percent = 50),
                                        )
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = video.title,
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

private fun hashColor(text: String): Color {
    val hue = (text.hashCode() % 360 + 360) % 360
    return Color.hsv(hue.toFloat(), 0.55f, 0.75f).copy(alpha = 0.92f)
}
