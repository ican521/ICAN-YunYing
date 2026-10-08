package com.ican.tvplay.data

import android.content.Context
import android.util.Log
import com.ican.tvplay.data.model.Episode
import com.ican.tvplay.data.model.PlayLine
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.data.model.VideoCategory
import com.ican.tvplay.data.remote.JxParser
import com.ican.tvplay.data.remote.LoadedSite
import com.ican.tvplay.data.remote.SpiderManager
import com.ican.tvplay.data.remote.TvBoxSite
import com.ican.tvplay.data.remote.VodApiClient
import com.ican.tvplay.data.remote.VodClass
import com.ican.tvplay.data.remote.VodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

    /** 首屏分类视频缓存（key = siteKey|catId）——fongmi 式预加载：
     *  导入配置/切站点后预热写入，进入首页直接命中，秒出卡片 */
    private val homeCache = java.util.concurrent.ConcurrentHashMap<String, List<Video>>()

    /** 详情缓存（videoId → Video）：重复进详情页秒开，reload 时清空 */
    private val detailCache = java.util.concurrent.ConcurrentHashMap<String, Video>()

    /** 首页磁盘缓存：进程被杀后冷启动秒出上次数据（内存缓存随进程丢失，磁盘不丢） */
    private val diskCacheFile: java.io.File by lazy {
        java.io.File(appContext.filesDir, "home_cache.json")
    }

    /** 内存缓存被后台预热刷新后的版本号；首页监听它实现静默无感刷新 */
    private val _homeCacheVersion = kotlinx.coroutines.flow.MutableStateFlow(0)
    val homeCacheVersion: kotlinx.coroutines.flow.StateFlow<Int> = _homeCacheVersion.asStateFlow()

    /** 冷启动：把上次的磁盘缓存读回内存（站点不匹配则丢弃） */
    private fun loadDiskCacheIntoMemory(siteKey: String) {
        runCatching {
            if (!diskCacheFile.exists()) return
            val cache = rawJson.decodeFromString<HomeDiskCache>(diskCacheFile.readText())
            if (cache.siteKey != siteKey) {
                diskCacheFile.delete()
                return
            }
            cache.sections.forEach { s ->
                homeCache["${siteKey}|${s.catId}"] = s.videos
            }
            Log.d(REPO_TAG, "home disk cache loaded sections=${cache.sections.size}")
        }.onFailure { Log.d(REPO_TAG, "home disk cache miss: ${it.message}") }
    }

    /** 预热完成后把全部分类首屏数据落盘，供下次冷启动秒出 */
    private suspend fun persistHomeCache(siteKey: String, cats: List<VideoCategory>) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val sections = cats.mapNotNull { cat ->
                    homeCache["${siteKey}|${cat.id}"]?.let {
                        HomeDiskSection(catId = cat.id, catName = cat.name, videos = it)
                    }
                }
                if (sections.isEmpty()) return@withContext
                diskCacheFile.writeText(
                    rawJson.encodeToString(
                        HomeDiskCache.serializer(),
                        HomeDiskCache(siteKey = siteKey, updatedAt = System.currentTimeMillis(), sections = sections),
                    ),
                )
                Log.d(REPO_TAG, "home disk cache persisted sections=${sections.size}")
            }
        }

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
        // 站点/配置变更后旧缓存全部作废
        homeCache.clear()
        detailCache.clear()
        diskCacheFile.delete()
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
        // 分类就绪后把上次落盘的首页数据读回内存（冷启动秒出）
        loadDiskCacheIntoMemory(loaded.site.key)
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
            // 「全部」分区：所有分类并行加载（fongmi 同款），不再串行排队
            coroutineScope {
                cats.map { cat ->
                    async { cat to loadCategoryVideos(loaded, cat, page = 1) }
                }.awaitAll()
            }
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

    /** 详情：补全选集 / 播放地址等（带内存缓存，重复进详情秒开） */
    suspend fun getVideo(videoId: String): Video? {
        detailCache[videoId]?.let { return it }
        ensureLoaded()
        val loaded = _currentSite.value ?: return null
        val item = fetchDetail(loaded, videoId) ?: return null
        val catName = item.typeName.ifBlank { "未知" }
        return item.toVideo(VideoCategory(id = "", name = catName), withEpisodes = true)
            .also { detailCache[videoId] = it }
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
            val rawUrl = obj["url"]?.jsonPrimitive?.content ?: episode.playUrl
            // header 可能是 JSON 对象 {"User-Agent": "...", "Referer": "..."} 或字符串
            val headers = mutableMapOf<String, String>()
            obj["header"]?.let { el ->
                runCatching {
                    el.jsonObject.entries.forEach { (k, v) ->
                        headers[k] = v.jsonPrimitive.content
                    }
                }
            }
            val parse = obj["parse"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val jx = obj["jx"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            Log.d(REPO_TAG, "playerContent rawUrl=$rawUrl parse=$parse jx=$jx headers=${headers.keys}")
            // parse=1/2 或 jx=1 → 需 jx 解析（很多 spider 返回的是解析页 URL 而非真实流）
            val finalUrl = if (JxParser.needsParse(rawUrl, parse, jx)) {
                JxParser.resolve(rawUrl, headers).ifBlank { rawUrl }
            } else {
                rawUrl
            }
            PlaySource(url = finalUrl.ifBlank { episode.playUrl }, headers = headers)
        }.onFailure { Log.e(REPO_TAG, "parse playerContent fail", it) }
            .getOrDefault(PlaySource(episode.playUrl))
    }

    // ---- 内部：按站点类型分发数据加载 ----

    private suspend fun loadCategoryVideos(
        loaded: LoadedSite,
        cat: VideoCategory,
        page: Int,
    ): List<Video> {
        // 首页第一页走缓存（预热的收益点）；翻页不缓存
        if (page == 1) {
            val key = "${loaded.site.key}|${cat.id}"
            homeCache[key]?.let { return it }
            val list = fetchCategoryVideos(loaded, cat)
            homeCache[key] = list
            return list
        }
        return fetchCategoryVideos(loaded, cat)
    }

    private suspend fun fetchCategoryVideos(
        loaded: LoadedSite,
        cat: VideoCategory,
    ): List<Video> {
        return if (loaded.site.isSpider()) {
            val spider = currentSpider ?: return emptyList()
            val json = spiderManager.categoryContent(spider, cat.id, 1, filter = true)
            parseList(json).map { it.toVideo(cat) }
        } else {
            apiClient.categoryVideos(loaded.site, cat.id, 1).map { it.toVideo(cat) }
        }
    }

    /**
     * 后台预热首页各分类首屏进缓存（fongmi 式：导入配置/切站点后立即预加载）。
     * 完成后首页卡片直接命中缓存，秒出。
     */
    suspend fun prewarmHome() {
        ensureLoaded()
        val loaded = _currentSite.value ?: return
        val cats = _categories.value
        if (cats.isEmpty()) return
        coroutineScope {
            cats.map { cat -> async {
                // 强制网络刷新（不走 loadCategoryVideos 的缓存命中），刷新后写入内存
                runCatching { fetchCategoryVideos(loaded, cat) }
                    .getOrNull()
                    ?.let { homeCache["${loaded.site.key}|${cat.id}"] = it }
            } }.awaitAll()
        }
        persistHomeCache(loaded.site.key, cats)
        _homeCacheVersion.value++
        Log.d(REPO_TAG, "prewarmHome done cats=${cats.size}")
    }

    /** 并发预下载全部站点 spider jar（fongmi 式：导入配置后即预热，后续搜索/详情/播放零首次开销） */
    suspend fun prewarmJars() {
        val url = settingsRepository.configUrl.value
        if (url.isBlank()) return
        val config = apiClient.loadConfig(url) ?: return
        val specs = config.sites.filter { it.isSpider() }.map { it.jar.ifBlank { config.spider } }
        spiderManager.prewarmJars(specs)
    }

    /**
     * 流式首页分区：每个分类加载完成立即推送一次累积列表（fongmi 式边加载边显示），
     * 快分类先上屏，不被慢分类拖住。缓存命中时几乎瞬时全部推送。
     */
    fun homeSectionsFlow(categoryId: String?): Flow<List<Pair<VideoCategory, List<Video>>>> = flow {
        ensureLoaded()
        val loaded = _currentSite.value ?: return@flow
        val cats = _categories.value
        if (cats.isEmpty()) return@flow
        val target = if (categoryId == null) cats else listOfNotNull(cats.firstOrNull { it.id == categoryId })
        if (target.isEmpty()) return@flow

        val channel = kotlinx.coroutines.channels.Channel<Pair<VideoCategory, List<Video>>>(
            kotlinx.coroutines.channels.Channel.UNLIMITED,
        )
        coroutineScope {
            // 生产者：全部分类并行加载
            launch {
                target.map { cat -> launch { channel.send(cat to loadCategoryVideos(loaded, cat, page = 1)) } }
                    .joinAll()
                channel.close()
            }
            // 消费：到达即累积发射（emit 在 flow 协程内，合法）
            val acc = ArrayList<Pair<VideoCategory, List<Video>>>(target.size)
            for (entry in channel) {
                acc.add(entry)
                emit(acc.toList())
            }
        }
    }.flowOn(Dispatchers.IO)

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

/** 首页磁盘缓存：单个分类的首屏视频列表 */
@kotlinx.serialization.Serializable
private data class HomeDiskSection(
    val catId: String,
    val catName: String,
    val videos: List<Video>,
)

/** 首页磁盘缓存：冷启动秒出上次数据（进程被杀后内存缓存丢失，磁盘不丢） */
@kotlinx.serialization.Serializable
private data class HomeDiskCache(
    val siteKey: String,
    val updatedAt: Long,
    val sections: List<HomeDiskSection>,
)
