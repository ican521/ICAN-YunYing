package com.ican.tvplay.ui.components

import com.ican.tvplay.data.model.Episode
import com.ican.tvplay.data.model.PlayLine
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.player.core.Players

/**
 * 链接解析播放工具（照搬 fongmi PushActivity + Sniffer + Vod.setFlags/Flag.setEpisodes）。
 *
 * fongmi 流程：
 * 1. `Sniffer.getUrl(text)`：JSON 对象（`{` 开头）或含 `$`（TVBox 多集格式）→ 原文透传；
 *    否则 AI_PUSH 正则提取首个链接；都不匹配 → 原文返回（交给播放器自行失败）。
 * 2. `SiteApi.detailContent(PUSH)`：vod.playUrl = 文本原文，`vod.setFlags()` 按线路/剧集拆分。
 * 3. `Flag.setEpisodes()`：`#` 分集、`$` 分「名称$url」、无 `$` 自动编号 01/02...
 * 4. `playerContent(PUSH)`：直放（Video extractor 仅剥 `video://` 前缀）。
 */
object UrlPlayHelper {

    /** fongmi Sniffer.AI_PUSH 同款：匹配推送文本中的首个链接（含 magnet/ed2k/video 协议） */
    private val AI_PUSH = Regex("(https?|thunder|magnet|ed2k|video):\\S+")

    /**
     * fongmi `Sniffer.getUrl` 同款：
     * JSON 对象（`{` 开头）或含 `$`（TVBox 多集格式「名称$url#名称$url」）→ 原文返回；
     * 否则提取首个链接；都不匹配 → 原文返回。
     */
    fun getUrl(text: String): String {
        val t = text.trim()
        if (t.startsWith("{") || t.contains("$")) return t
        return AI_PUSH.find(t)?.value ?: t
    }

    /**
     * fongmi `Vod.setFlags()` + `Flag.setEpisodes()` 同款拆分：
     * `$$$` 分线路（推送场景 playFrom 固定单线路，仅取第一段）、`#` 分集、`$` 分「名称$url」。
     * 无 `$` 的片段自动按序号命名 01/02...
     */
    private fun parseEpisodes(playUrl: String): List<Episode> {
        // fongmi PUSH 的 playFrom 是单一线路名（R.string.push），setFlags 只取 playUrls[0]
        val firstLine = playUrl.split("$$$")[0].trim()
        if (firstLine.isEmpty()) return emptyList()
        val parts = if (firstLine.contains("#")) firstLine.split("#") else listOf(firstLine)
        val episodes = mutableListOf<Episode>()
        parts.forEachIndexed { i, part ->
            val split = part.split("$", limit = 2)
            val number = "%02d".format(i + 1)
            val name: String
            val link: String
            if (split.size > 1) {
                name = split[0].trim().ifEmpty { number }
                link = split[1].trim()
            } else {
                name = number
                link = part.trim()
            }
            // fongmi Video extractor：剥掉 video:// 前缀
            val finalUrl = if (link.startsWith("video://")) link.substring(8) else link
            // fongmi: if (!getEpisodes().contains(episode)) add —— 按名称+地址去重
            if (finalUrl.isNotEmpty() && episodes.none { it.title == name && it.playUrl == finalUrl }) {
                episodes += Episode(index = episodes.size, title = name, playUrl = finalUrl)
            }
        }
        return episodes
    }

    /**
     * 构造推送播放上下文（fongmi `SiteApi.detailContent` PUSH 分支同款）：
     * 生成临时 Video（playFrom=直链单线路），缓存到 `Players.cachedVideo`，返回 videoId 供导航。
     * 不设置 pendingPreStartSpec：多集文本需要逐集直放，由 PlayerScreen 对无站点缓存视频走直放。
     */
    fun preparePushPlay(text: String): String? {
        val playUrl = getUrl(text)
        val episodes = parseEpisodes(playUrl)
        if (episodes.isEmpty()) return null
        val videoId = "url_${System.currentTimeMillis()}"
        // fongmi PUSH 的 vod.name = 文本原文；这里用短名避免 UI 溢出
        val title = if (episodes.size > 1) "推送播放"
        else playUrl.substringAfterLast('/').substringBefore('?').ifBlank { "推送播放" }
        val line = PlayLine(flag = "直链", episodes = episodes)
        val video = Video(
            id = videoId,
            title = title,
            cover = "",
            categoryId = "0",
            categoryName = "推送播放",
            year = 0,
            rating = "",
            region = "",
            description = "推送：$playUrl",
            tags = emptyList(),
            episodes = episodes,
            playFrom = "直链",
            playSources = listOf(line),
        )
        Players.cacheVideo(video, siteKey = "", site = null, jarSpec = "")
        return videoId
    }
}
