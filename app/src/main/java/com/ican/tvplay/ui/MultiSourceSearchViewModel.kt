package com.ican.tvplay.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ican.tvplay.data.PlaySource
import com.ican.tvplay.data.model.Episode
import com.ican.tvplay.data.model.PlayLine
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.data.remote.SpiderManager
import com.ican.tvplay.data.remote.TvBoxSite
import com.ican.tvplay.data.remote.VodApiClient
import com.ican.tvplay.data.remote.VodItem
import com.ican.tvplay.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 跨站源搜索 ViewModel：
 * - 直接使用 VodApiClient / SpiderManager 绕过 VideoRepository 的单站点 currentSite 状态
 * - 一次性加载配置里全部可用站点，并行搜索关键词
 * - selectedIndex = -1 表示「全部」站源合并视图；0..N 表示选中的具体站源
 */
class MultiSourceSearchViewModel(appContext: Context) : ViewModel() {

    private val apiClient = VodApiClient()
    private val spiderManager = SpiderManager(appContext.applicationContext)
    private val settings = SettingsRepository(appContext.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "MultiSourceSearch"
        const val ALL_SITES_INDEX = -1
    }

    /** 条目：一个站点 + 它的 jarSpec + 搜索结果视频列表 */
    data class SiteEntry(
        val site: TvBoxSite,
        val jarSpec: String,
        val videos: List<Video>,
    )

    /** 展示用条目：Video + 它来自哪个站源（全部视图下每条视频独立带站源标签） */
    data class DisplayVideo(
        val video: Video,
        val siteName: String,
        val siteKey: String,
    )

    private val _sites = MutableStateFlow<List<SiteEntry>>(emptyList())
    val sites: StateFlow<List<SiteEntry>> = _sites.asStateFlow()

    /** 站源总数（用于左侧「全部」条目显示总条数） */
    val totalVideoCount: Int get() = _sites.value.sumOf { it.videos.size }

    private val _selectedIndex = MutableStateFlow(ALL_SITES_INDEX)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 显示的视频（带站源信息） */
    private val _displayVideos = MutableStateFlow<List<DisplayVideo>>(emptyList())
    val displayVideos: StateFlow<List<DisplayVideo>> = _displayVideos.asStateFlow()

    fun selectSite(index: Int) {
        _selectedIndex.value = index
        refreshDisplay(index)
    }

    private fun refreshDisplay(index: Int) {
        _displayVideos.value = if (index == ALL_SITES_INDEX) {
            _sites.value.flatMap { entry ->
                entry.videos.map { v ->
                    DisplayVideo(
                        video = v,
                        siteName = entry.site.name.ifBlank { entry.site.key },
                        siteKey = entry.site.key,
                    )
                }
            }
        } else {
            _sites.value.getOrNull(index)?.let { entry ->
                entry.videos.map { v ->
                    DisplayVideo(
                        video = v,
                        siteName = entry.site.name.ifBlank { entry.site.key },
                        siteKey = entry.site.key,
                    )
                }
            }.orEmpty()
        }
    }

    /**
     * 跨全部站点并行搜索。每个站点独立处理，一个失败不影响其他。
     */
    fun searchAll(keyword: String) {
        val key = keyword.trim()
        _query.value = key
        if (key.isEmpty()) {
            _sites.value = emptyList()
            _displayVideos.value = emptyList()
            return
        }

        viewModelScope.launch {
            _loading.value = true
            try {
                val configUrl = settings.configUrl.value
                if (configUrl.isBlank()) {
                    _sites.value = emptyList()
                    _displayVideos.value = emptyList()
                    return@launch
                }

                val config = apiClient.loadConfig(configUrl)
                if (config == null) {
                    _sites.value = emptyList()
                    _displayVideos.value = emptyList()
                    return@launch
                }

                val validSites = config.sites.filter { it.isSpider() || it.api.startsWith("http") }

                // 并行搜索全部站点
                val entries = validSites.map { site ->
                    async(Dispatchers.IO) {
                        val jarSpec = if (site.isSpider()) site.jar.ifBlank { config.spider } else ""
                        val vodItems = runCatching {
                            if (site.isSpider()) {
                                val spider = spiderManager.getSpider(site, jarSpec)
                                if (spider != null) {
                                    val json = spiderManager.searchContent(spider, key, quick = true)
                                    parseVodListJson(json)
                                } else emptyList()
                            } else {
                                apiClient.search(site, key)
                            }
                        }.onFailure { Log.e(TAG, "search site=${site.name} fail", it) }
                            .getOrDefault(emptyList())

                        SiteEntry(site, jarSpec, vodItems.map { it.toVideo() })
                    }
                }.awaitAll().filter { it.videos.isNotEmpty() }

                _sites.value = entries
                _selectedIndex.value = ALL_SITES_INDEX
                refreshDisplay(ALL_SITES_INDEX)
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * 选中站点后拉取完整详情（带 episodes / playSources），用于进入播放页
     */
    suspend fun getDetail(siteKey: String, videoId: String): Video? =
        withContext(Dispatchers.IO) {
            val entry = _sites.value.firstOrNull { it.site.key == siteKey }
                ?: return@withContext null
            val item = runCatching {
                if (entry.site.isSpider()) {
                    val spider = spiderManager.getSpider(entry.site, entry.jarSpec)
                    if (spider != null) {
                        val json = spiderManager.detailContent(spider, listOf(videoId))
                        parseVodListJson(json).firstOrNull()
                    } else null
                } else {
                    apiClient.detail(entry.site, videoId)
                }
            }.getOrNull()
            item?.toVideo(withEpisodes = true)
        }

    /**
     * 对指定站点解析播放地址：spider 走 playerContent，HTTP 直接返回原 URL
     */
    suspend fun resolvePlaySourceForSite(siteKey: String, playUrl: String, flag: String): PlaySource? =
        withContext(Dispatchers.IO) {
            val entry = _sites.value.firstOrNull { it.site.key == siteKey }
                ?: return@withContext null
            if (!entry.site.isSpider()) return@withContext PlaySource(playUrl)
            val spider = spiderManager.getSpider(entry.site, entry.jarSpec)
                ?: return@withContext PlaySource(playUrl)
            val jsonStr = spiderManager.playerContent(spider, flag.ifBlank { entry.site.key }, playUrl)
            if (jsonStr.isBlank()) return@withContext PlaySource(playUrl)
            runCatching {
                val obj = json.parseToJsonElement(jsonStr).jsonObject
                val url = obj["url"]?.jsonPrimitive?.content ?: playUrl
                val headers = mutableMapOf<String, String>()
                obj["header"]?.let { el ->
                    runCatching {
                        el.jsonObject.entries.forEach { (k, v) ->
                            headers[k] = v.jsonPrimitive.content
                        }
                    }
                }
                PlaySource(url = url.ifBlank { playUrl }, headers = headers)
            }.getOrNull()
        }

    // ========== 内部解析 ==========

    private fun parseVodListJson(jsonStr: String): List<VodItem> {
        if (jsonStr.isBlank()) return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(jsonStr).jsonObject
            val arr = root["list"]?.jsonArray ?: return@runCatching emptyList<VodItem>()
            arr.mapNotNull { el ->
                runCatching { json.decodeFromJsonElement(VodItem.serializer(), el) }.getOrNull()
            }
        }.onFailure { Log.e(TAG, "parseVodListJson fail", it) }.getOrDefault(emptyList())
    }

    private fun VodItem.toVideo(withEpisodes: Boolean = false): Video {
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
        )
    }

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
