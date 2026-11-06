package com.ican.tvplay.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.player.core.PlaySpec
import com.ican.tvplay.player.core.Players
import com.ican.tvplay.ui.MultiSourceSearchViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.BlurredBar
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.EmptyState
import com.ican.tvplay.ui.components.LocalEnableTopBarBlur
import com.ican.tvplay.ui.components.MiuixBackButton
import com.ican.tvplay.ui.components.PosterImage
import com.ican.tvplay.ui.components.pageHorizontalPadding
import com.ican.tvplay.ui.components.rememberBlurBackdrop
import com.ican.tvplay.ui.components.siteShortName
import com.ican.tvplay.ui.components.topBarPadding
import com.ican.tvplay.ui.components.tvCardEffect
import com.ican.tvplay.ui.theme.LightOnSurface
import com.ican.tvplay.ui.theme.PosterBadgeScrim
import com.ican.tvplay.ui.theme.PosterTitleScrim
import com.ican.tvplay.ui.theme.SelectionFillAlpha
import com.ican.tvplay.ui.theme.Spacing
import com.ican.tvplay.ui.theme.SquircleShape
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** 结果卡片逐个渐显：相邻卡片启动间隔（毫秒） */
private const val CARD_FADE_STAGGER_MILLIS = 60L

/** 结果卡片错峰索引上限：超出的卡片不再累加延迟 */
private const val CARD_FADE_MAX_STAGGER_INDEX = 6

/** 淡入时长（毫秒），只做 0 → 1，不做淡出 */
private const val ITEM_FADE_IN_MILLIS = 200

/** 站源胶囊逐个淡入的间隔（毫秒） */
private const val SITE_CHIP_FADE_STAGGER_MILLIS = 60

/**
 * 跨站源搜索结果区（左侧站源筛选 + 右侧视频卡片网格），供合并后的搜索页复用：
 * - 左侧：「全部」+ 配置里全部可用站源
 * - 右侧：默认展示所有站源合并的视频卡片网格；点击左侧筛选单个站源
 * - 卡片比例与首页一致 (3:4)，左上角站源标签，底部显示更新状态 / 备注 / 标题
 * 调用方负责顶栏与顶部间距，本组合函数保持结果页原有的边距与尺寸基准
 */
@Composable
fun MultiSourceSearchResults(
    vm: MultiSourceSearchViewModel,
    onPlay: (videoId: String, episodeIndex: Int, flag: String, preInitialized: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val sites by vm.sites.collectAsStateWithLifecycle()
    val selectedIndex by vm.selectedIndex.collectAsStateWithLifecycle()
    val displayVideos by vm.displayVideos.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val searchedQuery by vm.query.collectAsStateWithLifecycle()

    // 已淡入的元素 key 集合：由父层统一计时，元素滑出屏幕被回收再滑回来时不会重播动画
    var revealedChips by remember(searchedQuery) { mutableStateOf<Set<String>>(emptySet()) }
    var revealedCards by remember(searchedQuery) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(searchedQuery, sites.size) {
        val keys = listOf("all") + sites.indices.map { "site_$it" }
        keys.forEachIndexed { idx, key ->
            if (key !in revealedChips) {
                if (idx > 0) delay(SITE_CHIP_FADE_STAGGER_MILLIS.toLong())
                revealedChips = revealedChips + key
            }
        }
    }
    LaunchedEffect(searchedQuery, displayVideos.size) {
        val base = System.currentTimeMillis()
        val keys = displayVideos.indices.map { "card_$it" }
            .filter { it !in revealedCards }
            .forEachIndexed { idx, key ->
                // 前若干张错峰，其后同批一起出现；用绝对时间计算，避免逐个累加
                val wait = base + idx.coerceAtMost(CARD_FADE_MAX_STAGGER_INDEX) * CARD_FADE_STAGGER_MILLIS - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                revealedCards = revealedCards + key
            }
    }

    // ========== 主体：左站源列表 + 右卡片网格（左右边距与顶栏同一基准）==========
    Row(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = pageHorizontalPadding()),
    ) {
            // 左侧站源列表（纵向；与右侧网格第一行卡片顶部齐平）
            // imePadding：窗口禁用系统避让后，键盘弹出时列表底部仍可滚动到键盘上方
            LazyColumn(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.siteListItemGap),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = topPadding, bottom = Spacing.contentBottomInset),
            ) {
                // 「全部」合并视图入口（仅在有搜索词时显示；未搜索时本页不出现）
                if (searchedQuery.isNotBlank()) {
                    item(key = "all") {
                        RevealedFade(visible = "all" in revealedChips) {
                            SiteSourceChip(
                                name = "全部",
                                selected = selectedIndex == MultiSourceSearchViewModel.ALL_SITES_INDEX,
                                onClick = {
                                    vm.selectSite(MultiSourceSearchViewModel.ALL_SITES_INDEX)
                                },
                            )
                        }
                    }
                }
                // === 具体站源（固定宽度胶囊，仅取 | 前名称，不显示条数）===
                // 加载结束后按顺序错峰淡入：第一个先出现，后面的逐个跟上
                itemsIndexed(sites, key = { idx, _ -> "site_$idx" }, contentType = { _, _ -> "site" }) { idx, entry ->
                    RevealedFade(visible = "site_$idx" in revealedChips) {
                        SiteSourceChip(
                            name = siteShortName(entry.site.name, entry.site.key),
                            selected = idx == selectedIndex,
                            onClick = {
                                vm.selectSite(idx)
                            },
                        )
                    }
                }
            }

            // 站源列表与卡片网格之间的间距
            Spacer(modifier = Modifier.width(Spacing.searchGridGap))

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

                if (displayVideos.isEmpty()) {
                    // 搜索结束仍无结果 → 空状态；加载中保持空白，等卡片直接渐显
                    if (!loading) EmptyState()
                } else {
                    LazyVerticalGrid(
                        columns = columns,
                        // imePadding：键盘弹出时网格底部内容不被遮挡，可滚动到键盘上方
                        modifier = Modifier.fillMaxSize().imePadding(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = topPadding, bottom = Spacing.contentBottomInset),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.searchGridGap),
                        verticalArrangement = Arrangement.spacedBy(Spacing.searchGridGap),
                    ) {
                        itemsIndexed(
                            displayVideos,
                            key = { idx, _ -> "card_$idx" },
                            contentType = { _, _ -> "video" },
                        ) { index, dv ->
                            // 真实卡片按站源胶囊同款节奏逐个渐显；滑回屏幕不再重播
                            RevealedFade(visible = "card_$index" in revealedCards) {
                                CrossSourceVideoCard(
                                    video = dv.video,
                                    siteName = dv.siteName,
                                    onClick = {
                                        // fongmi 同款：action 卡片直接执行 jar action → Toast 返回 msg
                                        if (dv.video.action.isNotBlank()) {
                                            vm.executeAction(dv.siteKey, dv.video.action) { msg ->
                                                if (msg.isNotBlank()) {
                                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            handlePlayClick(vm, dv.video, dv.siteKey, onPlay)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
    }
}



/**
 * 点击卡片的播放流程（不阻塞等详情接口）：
 * 1. 缓存搜索元数据 + 站点信息（搜索接口不含选集，详情由播放页进入后异步补齐）
 * 2. 立即跳转 PlayerScreen——详情接口慢的站源也能秒进播放页，加载期间黑底转圈
 * 播放地址解析同样下沉到播放页（某些网盘 jar 在 playerContent 时触发扫码弹窗，
 * 必须进入播放页后再解析，PlayerScreen 用 cached.site/jarSpec 延迟解析）
 */
private fun handlePlayClick(
    vm: MultiSourceSearchViewModel,
    video: Video,
    siteKey: String,
    onPlay: (String, Int, String, Boolean) -> Unit,
) {
    val entry = vm.siteEntry(siteKey) ?: return
    Players.cacheVideo(video, siteKey, entry.site, entry.jarSpec)

    // preInit=false: 让 PlayerScreen 自己补详情并调 Players.start()，此时 SurfaceView 已 attach，时序正确
    onPlay(video.id, 0, "", false)
}

// ========== 左侧站源 chip ==========

/**
 * 站源胶囊淡入：由父层统一决定何时可见；alpha 只从 0 → 1，不做淡出。
 */
@Composable
private fun RevealedFade(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = ITEM_FADE_IN_MILLIS),
        label = "itemFadeIn",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
    ) {
        content()
    }
}

@Composable
private fun SiteSourceChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 未选中底色：仅浅色模式用 miuix surfaceContainerHigh（KernelSU 搜索栏同款 #E8E8E8），
    // 深色模式保持项目 DarkSurfaceVariant（#242424 全局板块色）不变
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = SelectionFillAlpha)
    } else if (com.ican.tvplay.ui.theme.LocalDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MiuixTheme.colorScheme.surfaceContainerHigh
    }
    val fg = if (selected) MaterialTheme.colorScheme.primary
    else MiuixTheme.colorScheme.onSurfaceVariantSummary
    val shape = RoundedCornerShape(percent = 50)

    // 固定宽度：所有胶囊等宽，不因名字长短变化；无水平外边距，左缘与返回圆钮（页面边距 16dp）精确对齐。
    // 与搜索首页词条胶囊同几何：固定 40dp 高（文字居中），左右 16dp、上下 0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // pressable 在外层：按压缩放跟随 Sink 动画（与右侧视频卡片同序）；
            // 站源列表自带选中态，蒙层关闭（pressEffect=false），选中色即状态反馈
            .pressable(
                interactionSource = null,
                indication = SinkFeedback(),
                delay = null,
            )
            .tvCardEffect(
                onClick = onClick,
                shape = shape,
                focusedScale = 1.05f,
                glow = false,
                pressEffect = false,
            )
            .background(bg, shape)
            .height(Spacing.chipHeight)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
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
    val (_, tags) = buildVideoStatus(video)

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
                shape = SquircleShape(Spacing.corner),
                focusedScale = 1.03f,
                glow = false,
                // 按压蒙层固定浅色模式同款浅黑：深色模式与浅色表现一致
                pressColor = LightOnSurface,
            ),
        insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
        ) {
            PosterImage(
                title = video.title,
                cover = video.cover,
                modifier = Modifier.fillMaxSize(),
            )

            // 左上角：站源标签（黑底「已完结/更新至」状态胶囊已按需求移除）
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.posterOverlayMargin)
                    .background(PosterBadgeScrim, RoundedCornerShape(percent = 50))
                    .padding(horizontal = Spacing.posterBadgePaddingH, vertical = Spacing.posterBadgePaddingV),
            ) {
                Text(
                    text = siteName.ifBlank { video.categoryName.ifBlank { "来源" } },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 底部渐变只铺在标题这一块，标签胶囊移到渐变层之上，不被渐变阴影盖住
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                // 标签行（全部标签文字合并进同一个胶囊，省空间）
                if (tags.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            // 往下压 1dp：让「标签→标题」视觉间距（约 8–9dp）
                            // 接近「标题→卡片底边」的约 10dp
                            .offset(y = 1.dp)
                            // 左边距 8dp：与左上角站源胶囊的 8dp 对齐
                            .padding(start = Spacing.posterOverlayMargin, end = Spacing.posterOverlayMargin, top = 4.dp)
                            .background(
                                PosterBadgeScrim,
                                RoundedCornerShape(percent = 50),
                            )
                            .padding(horizontal = Spacing.posterBadgePaddingH, vertical = Spacing.posterBadgePaddingV),
                    ) {
                        Text(
                            text = tags.take(3).joinToString(" "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, PosterTitleScrim),
                            ),
                        )
                        // 高度不变（上下各 6dp），纯黑 80% → 全透明渐变
                        .padding(
                            start = Spacing.posterTitlePaddingH,
                            end = Spacing.posterTitlePaddingH,
                            bottom = Spacing.posterTitlePaddingV,
                            top = Spacing.posterTitlePaddingV,
                        ),
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
