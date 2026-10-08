package com.ican.tvplay.data

import android.content.Context
import android.util.Log
import com.ican.tvplay.data.model.Episode
import com.ican.tvplay.data.model.PlayLine
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.data.model.VideoCategory
import com.ican.tvplay.data.remote.LoadedSite
import com.ican.tvplay.data.remote.SpiderManager
import com.ican.tvplay.data.remote.TvBoxSite
import com.ican.tvplay.data.remote.VodApiClient
import com.ican.tvplay.data.remote.VodClass
import com.ican.tvplay.data.remote.VodItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val REPO_TAG = "VodApi"

/** 一次播放所需的 URL 与请求头 */
data class PlaySource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * 视频仓库：从 TVBox 采集站接口拉取真实数据。
 * - 数据来源：用户在设置页导入的 config_url
 * - 站点类型自动分发：
 *   - type=3 + api=csp_Xxx → spider jar（[SpiderManager]）
 *   - type 0/1 + api 含 provide/vod → HTTP 采集站（[VodApiClient]）
 * - 配置未导入 / 加载失败时返回空数据，UI 显示空态
 */
class VideoRepository(
    appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val apiClient: VodApiClient = VodApiClient(),
    private val spiderManager: SpiderManager = SpiderManager(appContext),
) {

    /** 当前生效的站点；null 表示未导入配置或加载失败 */
    private val _currentSite = MutableStateFlow<LoadedSite?>(null)
    val currentSite: StateFlow<LoadedSite?> = _currentSite.asStateFlow()

    /** 当前分类 */
    private val _categories = MutableStateFlow<List<VideoCategory>>(emptyList())
    val categoriesFlow: StateFlow<List<VideoCategory>> = _categories.asStateFlow()

    /** 是否已就绪（已加载到站点） */
    val isReady: Boolean get() = _currentSite.value != null

    private val loadMutex = Mutex()

    private val rawJson = Json { ignoreUnknownKeys = true }

    /** 当前站点对应的 Spider 实例（仅 type=3 时非空） */
    @Volatile
    private var currentSpider: Any? = null

    /** App 启动 / 用户导入配置后调用：拉取 config_url 并加载站点与分类 */
    suspend fun ensureLoaded() {
        loadMutex.withLock {
            if (_currentSite.value != null) return
            reload()
        }
    }

    /** 强制重新加载（导入新配置后调用） */
    suspend fun reload() {
        val url = settingsRepository.configUrl.value
        if (url.isBlank()) {
            _currentSite.value = null
            _categories.value = emptyList()
            currentSpider = null
            return
        }
        val loaded = apiClient.loadHomeSite(url, settingsRepository.siteKey.value)
        _currentSite.value = loaded
        if (loaded == null) {
            _categories.value = emptyList()
            currentSpider = null
            return
        }

        if (loaded.site.isSpider()) {
            // spider 站：先加载 spider，再用 homeContent 拿分类
            val spider = spiderManager.getSpider(loaded.site, loaded.jarSpec)
            currentSpider = spider
            if (spider == null) {
                Log.e(REPO_TAG, "spider load fail, fallback empty")
                _categories.value = emptyList()
                return
            }
            val homeJson = spiderManager.homeContent(spider, filter = true)
            val classes = parseClasses(homeJson)
            Log.d(REPO_TAG, "spider homeContent classes=${classes.size}")
            _categories.value = classes.map {
                VideoCategory(id = it.typeId, name = it.typeName)
            }
        } else {
            // HTTP 采集站
            currentSpider = null
            _categories.value = apiClient.homeClasses(loaded.site).map {
                VideoCategory(id = it.typeId, name = it.typeName)
            }
        }
    }

    fun getCategories(): List<VideoCategory> = _categories.value

    /** 配置中全部可切换站点（spider + HTTP） */
    suspend fun listSites(): List<TvBoxSite> {
        val url = settingsRepository.configUrl.value
        if (url.isBlank()) return emptyList()
        return apiClient.listSites(url)
    }

    /** 切换站点：记录选择并重新加载分类 */
    suspend fun switchSite(siteKey: String) {
        settingsRepository.setSiteKey(siteKey)
        loadMutex.withLock { reload() }
    }

    /**
     * 首页分区：categoryId == null 表示「全部」→ 返回所有分类各加载首页视频；
     * 否则只返回选中分类的视频列表（单 section）。
     */
    suspend fun getHomeSections(categoryId: String?): List<Pair<VideoCategory, List<Video>>> {
        ensureLoaded()
        val loaded = _currentSite.value ?: return emptyList()
        val cats = _categories.value
        if (cats.isEmpty()) return emptyList()

        return if (categoryId == null) {
            cats.map { cat -> cat to loadCategoryVideos(loaded, cat, page = 1) }
        } else {
            val cat = cats.firstOrNull { it.id == categoryId } ?: return emptyList()
            listOf(cat to loadCategoryVideos(loaded, cat, page = 1))
        }
    }

    suspend fun getVideos(categoryId: String): List<Video> {
        ensureLoaded()
        val loaded = _currentSite.value ?: return emptyList()
        val cat = _categories.value.firstOrNull { it.id == categoryId } ?: return emptyList()
        return loadCategoryVideos(loaded, cat, page = 1)
    }

    /** 详情：补全选集 / 播放地址等 */
    suspend fun getVideo(videoId: String): Video? {
        ensureLoaded()
        val loaded = _currentSite.value ?: return null
        val item = fetchDetail(loaded, videoId) ?: return null
        val catName = item.typeName.ifBlank { "未知" }
        return item.toVideo(VideoCategory(id = "", name = catName), withEpisodes = true)
    }

    suspend fun search(query: String): List<Video> {
        ensureLoaded()
        val key = query.trim()
        if (key.isEmpty()) return emptyList()
        val loaded = _currentSite.value ?: return emptyList()
        val items = if (loaded.site.isSpider()) {
            val spider = currentSpider ?: return emptyList()
            val json = spiderManager.searchContent(spider, key, quick = true)
            parseList(json)
        } else {
            apiClient.search(loaded.site, key)
        }
        return items.map {
            it.toVideo(VideoCategory(id = "", name = it.typeName.ifBlank { "未知" }))
        }
    }

    /**
     * 播放前调用：spider 站经 playerContent 解析真实 URL（可能带 header）；
     * HTTP 站直接返回原 url。flag 为所选线路标识，缺省用视频首源。
     */
    suspend fun resolvePlaySource(video: Video, episode: Episode, flag: String? = null): PlaySource {
        val loaded = _currentSite.value ?: return PlaySource(episode.playUrl)
        if (!loaded.site.isSpider()) return PlaySource(episode.playUrl)
        val spider = currentSpider ?: return PlaySource(episode.playUrl)
        val resolvedFlag = flag?.takeIf { it.isNotBlank() }
            ?: video.playFrom.ifBlank { loaded.site.key }
        val json = spiderManager.playerContent(spider, resolvedFlag, episode.playUrl)
        if (json.isBlank()) return PlaySource(episode.playUrl)
        return runCatching {
            val obj = rawJson.parseToJsonElement(json).jsonObject
            val url = obj["url"]?.jsonPrimitive?.content ?: episode.playUrl
            // header 可能是 JSON 对象 {"User-Agent": "...", "Referer": "..."} 或字符串
            val headers = mutableMapOf<String, String>()
            obj["header"]?.let { el ->
                runCatching {
                    el.jsonObject.entries.forEach { (k, v) ->
                        headers[k] = v.jsonPrimitive.content
                    }
                }
            }
            // parse=1 表示需解析（暂不实现，按原样返回）
            val parse = obj["parse"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            Log.d(REPO_TAG, "playerContent url=$url parse=$parse headers=${headers.keys}")
            PlaySource(url = url.ifBlank { episode.playUrl }, headers = headers)
        }.onFailure { Log.e(REPO_TAG, "parse playerContent fail", it) }
            .getOrDefault(PlaySource(episode.playUrl))
    }

    // ---- 内部：按站点类型分发数据加载 ----

    private suspend fun loadCategoryVideos(
        loaded: LoadedSite,
        cat: VideoCategory,
        page: Int,
    ): List<Video> {
        return if (loaded.site.isSpider()) {
            val spider = currentSpider ?: return emptyList()
            val json = spiderManager.categoryContent(spider, cat.id, page, filter = true)
            parseList(json).map { it.toVideo(cat) }
        } else {
            apiClient.categoryVideos(loaded.site, cat.id, page).map { it.toVideo(cat) }
        }
    }

    private suspend fun fetchDetail(loaded: LoadedSite, vodId: String): VodItem? {
        return if (loaded.site.isSpider()) {
            val spider = currentSpider ?: return null
            val json = spiderManager.detailContent(spider, listOf(vodId))
            parseList(json).firstOrNull()
        } else {
            apiClient.detail(loaded.site, vodId)
        }
    }

    /** spider homeContent / categoryContent / searchContent 返回 JSON 中的 list 数组 */
    private fun parseList(json: String): List<VodItem> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = rawJson.parseToJsonElement(json).jsonObject
            val arr = root["list"]?.jsonArray ?: return emptyList()
            arr.mapNotNull { el ->
                runCatching { rawJson.decodeFromJsonElement(VodItem.serializer(), el) }.getOrNull()
            }
        }.onFailure { Log.e(REPO_TAG, "parseList fail", it) }.getOrDefault(emptyList())
    }

    /** spider homeContent 返回 JSON 中的 class 数组 */
    private fun parseClasses(json: String): List<VodClass> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = rawJson.parseToJsonElement(json).jsonObject
            val arr = root["class"]?.jsonArray ?: return emptyList()
            arr.mapNotNull { el ->
                runCatching { rawJson.decodeFromJsonElement(VodClass.serializer(), el) }.getOrNull()
            }
        }.onFailure { Log.e(REPO_TAG, "parseClasses fail", it) }.getOrDefault(emptyList())
    }

    /** 把采集站返回的 VodItem 映射为项目 Video 模型 */
    private fun VodItem.toVideo(category: VideoCategory, withEpisodes: Boolean = false): Video {
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
            categoryId = category.id,
            categoryName = if (typeName.isNotBlank()) typeName else category.name,
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

    /**
     * 解析 vod_play_from / vod_play_url：
     * 多线路用 $$$ 分隔（两者一一对应），单线路内选集用 # 分隔，
     * 每集格式 `第01集$https://...m3u8`。
     * 线路数多于选集组时以选集组为准；线路名为空时回退为「线路N」。
     */
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
