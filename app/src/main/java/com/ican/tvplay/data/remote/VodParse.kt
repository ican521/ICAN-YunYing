package com.ican.tvplay.data.remote

import android.util.Log
import com.ican.tvplay.data.model.Episode
import com.ican.tvplay.data.model.PlayLine
import com.ican.tvplay.data.model.Video
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * VodItem JSON / Video 映射的共享工具：跨站源搜索与收藏播放共用，避免两处重复解析逻辑。
 */
object VodParse {
    private val json = Json { ignoreUnknownKeys = true }

    /** 解析 spider searchContent/detailContent 返回的 {list:[...]} JSON */
    fun parseVodListJson(jsonStr: String): List<VodItem> {
        if (jsonStr.isBlank()) return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(jsonStr).jsonObject
            val arr = root["list"]?.jsonArray ?: return@runCatching emptyList<VodItem>()
            arr.mapNotNull { el ->
                runCatching { json.decodeFromJsonElement(VodItem.serializer(), el) }.getOrNull()
            }
        }.onFailure { Log.e("VodParse", "parseVodListJson fail", it) }.getOrDefault(emptyList())
    }

    /** VodItem → Video（withEpisodes=true 时解析全部播放线路与选集） */
    fun VodItem.toVideo(withEpisodes: Boolean = false): Video {
        val playSources = if (withEpisodes && vodPlayUrl.isNotBlank()) {
            parsePlayLines(vodPlayFrom, vodPlayUrl)
        } else {
            emptyList()
        }
        val firstPlayFrom = vodPlayFrom.split("$$$").firstOrNull()?.trim().orEmpty()
        return Video(
            id = vodId,
            title = vodName,
            cover = vodPic,
            categoryId = "",
            categoryName = typeName.ifBlank { "未知" },
            year = vodYear.toIntOrNull() ?: 0,
            rating = vodRemarks,
            region = vodArea,
            description = vodContent.trim(),
            tags = emptyList(),
            episodes = playSources.firstOrNull()?.episodes.orEmpty(),
            playFrom = firstPlayFrom,
            playSources = playSources,
            action = action,
        )
    }

    /** vod_play_from / vod_play_url 按 $$$ 分组解析为多线路 */
    private fun parsePlayLines(playFrom: String, playUrl: String): List<PlayLine> {
        val flags = playFrom.split("$$$").map { it.trim() }
        val urlGroups = playUrl.split("$$$")
        return urlGroups.mapIndexedNotNull { groupIndex, group ->
            val episodes = group.split("#")
                .mapIndexedNotNull { index, segment ->
                    val parts = segment.split("$")
                    if (parts.size < 2) return@mapIndexedNotNull null
                    val title = parts[0].trim().ifBlank { "第${index + 1}集" }
                    val url = parts.subList(1, parts.size).joinToString("$").trim()
                    if (url.isEmpty()) return@mapIndexedNotNull null
                    Episode(index = index, title = title, playUrl = url)
                }
            if (episodes.isEmpty()) return@mapIndexedNotNull null
            val flag = flags.getOrNull(groupIndex)?.takeIf { it.isNotBlank() }
                ?: "线路${groupIndex + 1}"
            PlayLine(flag = flag, episodes = episodes)
        }
    }
}
