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
import com.ican.tvplay.data.remote.VodStyle
import com.ican.tvplay.data.remote.parsePlayerUrl
import com.ican.tvplay.data.remote.parseStyle
import com.ican.tvplay.data.remote.styleOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val REPO_TAG = "VodApi"

/** 详情磁盘缓存有效期：24h 内同片再进直接命中（fongmi 式详情缓存，首次仍需网络） */
private const val DETAIL_DISK_TTL_MS = 24L * 60 * 60 * 1000

/**
 * 首页「推荐」虚拟分类 id（对标 OK猫 的 my0「主页」）：
 * 卡片来自 homeContent 自带 list，不额外发 categoryContent，
 * 所以切源时「分类 + 首屏卡片」在同一个请求里一起到，不用再等一次分类请求。
 */
const val HOME_REC_CATEGORY_ID = "my0"

/** 首页「推荐」虚拟分类名称（顶部 Tab 文案） */
private const val HOME_REC_CATEGORY_NAME = "推荐"

/** 一次播放所需的 URL 与请求头 */
data class PlaySource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    /** 可选画质列表（fongmi Url.values）：>1 条时播放页才显示「画质」行 */
    val qualities: List<com.ican.tvplay.data.remote.PlayQuality> = emptyList(),
    /** 当前选中的画质下标（fongmi Url.position） */
    val qualityPosition: Int = 0,
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

    /** 切站代数：每次 switchSite +1；旧加载在慢步骤之间检查代数，被更新的切站取代后立即作废 */
    private val siteLoadGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    /** 当前正在执行的切站协程，用于「最新一次切站优先」时取消旧的加载 */
    private var siteReloadJob: kotlinx.coroutines.Job? = null

    /** 详情优先：用户开始拉详情/播放时，取消仍在跑的追番更新检测，让出网络/IO。
     *  首页全量预热已下线（分类只在被选中时加载），这里不再需要取消预热 */
    fun yieldForDetail() {
        FavoriteUpdateChecker.cancelRunningCheck()
    }

    /** 首屏分类视频缓存（key = siteKey|catId）：
     *  分类按需加载，加载成功后进内存 + 防抖落盘，回切同一分类 / 下次冷启动直接命中 */
    private val homeCache = java.util.concurrent.ConcurrentHashMap<String, List<Video>>()

    /** 详情缓存（videoId → Video）：重复进详情页秒开，reload 时清空 */
    private val detailCache = java.util.concurrent.ConcurrentHashMap<String, Video>()

    /** 首页磁盘缓存：进程被杀后冷启动秒出上次数据（内存缓存随进程丢失，磁盘不丢） */
    private val diskCacheFile: java.io.File by lazy {
        java.io.File(appContext.filesDir, "home_cache.json")
    }

    /** 配置+分类磁盘缓存：冷启动毫秒级恢复分类列表，网络全链路只在后台刷新（fongmi VodConfig 同款策略） */
    private val configFile: java.io.File by lazy {
        java.io.File(appContext.filesDir, "config_cache.json")
    }

    @kotlinx.serialization.Serializable
    private data class ConfigDiskCache(
        val siteKey: String,
        val configUrl: String,
        val loaded: com.ican.tvplay.data.remote.LoadedSite,
        val categories: List<VideoCategory>,
    )

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
                // 空分区不入缓存：历史上「spider 未就绪时取了空数据并落盘」会污染磁盘缓存，
                // 导致之后每次冷启动首页都命中空列表（表现为首次启动没有卡片）
                if (s.videos.isNotEmpty()) homeCache["${siteKey}|${s.catId}"] = s.videos
            }
            Log.d(REPO_TAG, "home disk cache loaded sections=${cache.sections.size}")
        }.onFailure { Log.d(REPO_TAG, "home disk cache miss: ${it.message}") }
    }

    /** 后台落盘协程 + 防抖句柄：分类页真正加载成功后才写盘，不做任何额外网络请求 */
    private val houseKeepingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistHomeJob: kotlinx.coroutines.Job? = null

    /** 按需落盘（防抖 800ms）：连续切几个分类只写最后一次，避免频繁 IO */
    private fun schedulePersistHomeCache(siteKey: String) {
        persistHomeJob?.cancel()
        persistHomeJob = houseKeepingScope.launch {
            delay(800)
            persistHomeCache(siteKey)
        }
    }

    /** 把该站已加载的分类首屏数据落盘，供下次冷启动秒出（只写内存里已有的，不触发请求） */
    private suspend fun persistHomeCache(siteKey: String) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val catNames = _categories.value.associate { it.id to it.name }
                val prefix = "$siteKey|"
                val sections = homeCache.keys
                    .filter { it.startsWith(prefix) }
                    .mapNotNull { key ->
                        val catId = key.removePrefix(prefix)
                        val videos = homeCache[key]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                        HomeDiskSection(catId = catId, catName = catNames[catId] ?: catId, videos = videos)
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

    // isLenient + coerceInputValues：兼容网盘站源返回的类型不规范字段
    // （如 vod_id 直接给数字），否则单条解码失败会被静默丢掉 —— 表现为「文件夹解析不出来」
    private val rawJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** 当前站点对应的 Spider 实例（仅 type=3 时非空） */
    @Volatile
    private var currentSpider: Any? = null

    /** App 启动 / 业务兜底：先读配置缓存毫秒级恢复分类，网络全链路随后刷新（fongmi VodConfig 策略） */
    suspend fun ensureLoaded() {
        // 快路径（锁外）：配置缓存已恢复时直接放行，不让 sections/预热排队等后台网络刷新
        if (_currentSite.value != null) return
        loadMutex.withLock {
            if (_currentSite.value != null) return
            if (tryRestoreConfigCache()) {
                Log.d("Startup", "config cache restored, network refresh starts")
                refreshFromNetwork(preserveState = true)
            } else {
                refreshFromNetwork(preserveState = false)
            }
        }
    }

    /** 强制重新加载（导入新配置后调用）：缓存全部作废重建 */
    suspend fun reload() {
        loadMutex.withLock {
            homeCache.clear()
            detailCache.clear()
            diskCacheFile.delete()
            configFile.delete()
            refreshFromNetwork(preserveState = false)
        }
    }

    /** 冷启动：从磁盘恢复「站点+分类+首页数据」（毫秒级），失败返回 false */
    private fun tryRestoreConfigCache(): Boolean = runCatching {
        if (!configFile.exists()) return false
        val cache = rawJson.decodeFromString<ConfigDiskCache>(configFile.readText())
        if (cache.configUrl != settingsRepository.configUrl.value) {
            configFile.delete()
            return false
        }
        _currentSite.value = cache.loaded
        _categories.value = cache.categories
        // spider 实例需运行时加载，缓存恢复不含；首页数据命中 homeCache 不依赖它
        currentSpider = null
        loadDiskCacheIntoMemory(cache.siteKey)
        _homeCacheVersion.value++
        Log.d("Startup", "config cache restored cats=${cache.categories.size}")
        true
    }.getOrDefault(false)

    /** 网络全链路：配置下载 → spider → 分类。preserveState=true 时失败/为空都保留现有缓存态 */
    private suspend fun refreshFromNetwork(preserveState: Boolean) {
        val url = settingsRepository.configUrl.value
        if (url.isBlank()) {
            if (!preserveState) {
                _currentSite.value = null
                _categories.value = emptyList()
                currentSpider = null
            }
            return
        }
        val loaded = run {
            Log.d("Startup", "reload: loadHomeSite begin")
            val r = apiClient.loadHomeSite(url, settingsRepository.siteKey.value)
            Log.d("Startup", "reload: loadHomeSite done site=${r?.site?.key}")
            r
        }
        if (loaded == null) {
            Log.d("Startup", "reload: loadHomeSite failed, preserveState=$preserveState")
            if (!preserveState) {
                _categories.value = emptyList()
                currentSpider = null
            }
            return
        }
        _currentSite.value = loaded
        if (loaded.site.isSpider()) {
            // spider 站：先加载 spider，再用 homeContent 拿分类
            Log.d("Startup", "reload: getSpider begin")
            val spider = spiderManager.getSpider(loaded.site, loaded.jarSpec)
            Log.d("Startup", "reload: getSpider done ok=${spider != null}")
            currentSpider = spider
            if (spider == null) {
                Log.e(REPO_TAG, "spider load fail, fallback empty")
                if (!preserveState) _categories.value = emptyList()
                return
            }
            Log.d("Startup", "reload: homeContent begin")
            val homeJson = spiderManager.homeContent(spider, filter = true)
            val classes = parseClasses(homeJson)
            Log.d("Startup", "reload: homeContent done classes=${classes.size}")
            val rec = parseRecVideos(homeJson)
            // 分类就绪后把上次落盘的首页数据读回内存（冷启动秒出）；先读盘再发布，避免旧的推荐卡片覆盖新抓的
            loadDiskCacheIntoMemory(loaded.site.key)
            publishCategories(loaded.site.key, classes.map {
                VideoCategory(id = it.typeId, name = it.typeName, typeFlag = it.typeFlag)
            }, rec)
            Log.d("Startup", "reload: categories published size=${_categories.value.size} rec=${rec.size}")
        } else {
            // HTTP 采集站
            currentSpider = null
            val (classes, recList) = apiClient.homeClassesAndList(loaded.site)
            val rec = recList.map {
                it.toVideo(VideoCategory(HOME_REC_CATEGORY_ID, HOME_REC_CATEGORY_NAME))
            }
            loadDiskCacheIntoMemory(loaded.site.key)
            publishCategories(loaded.site.key, classes.map {
                VideoCategory(id = it.typeId, name = it.typeName, typeFlag = it.typeFlag)
            }, rec)
        }
        persistConfigCache(loaded)
    }

    /** 站点+分类落盘，供下次冷启动毫秒级恢复 */
    private fun persistConfigCache(loaded: com.ican.tvplay.data.remote.LoadedSite) {
        runCatching {
            configFile.writeText(
                rawJson.encodeToString(
                    ConfigDiskCache.serializer(),
                    ConfigDiskCache(
                        siteKey = loaded.site.key,
                        configUrl = settingsRepository.configUrl.value,
                        loaded = loaded,
                        categories = _categories.value,
                    ),
                ),
            )
        }
    }

    fun getCategories(): List<VideoCategory> = _categories.value

    /** homeContent 自带 list → 「推荐」卡片（对标 OK猫 my0「主页」，随分类同一次请求返回） */
    private fun parseRecVideos(json: String): List<Video> {
        val items = parseList(json)
        if (items.isEmpty()) return emptyList()
        Log.d(
            REPO_TAG,
            "rec list n=${items.size} blankId=${items.count { it.vodId.isBlank() }} dup=${items.size - items.map { it.vodId }.toSet().size}",
        )
        return items.map { it.toVideo(VideoCategory(HOME_REC_CATEGORY_ID, HOME_REC_CATEGORY_NAME)) }
    }

    /**
     * 发布分类列表：有推荐卡片时把「推荐」作为第一个 tab，并在发布时直接把卡片落进首屏缓存，
     * 于是首屏那一页不发 categoryContent，切源的就绪时间 = homeContent 一次请求。
     */
    private fun publishCategories(siteKey: String, cats: List<VideoCategory>, rec: List<Video>) {
        if (rec.isNotEmpty()) {
            homeCache["$siteKey|$HOME_REC_CATEGORY_ID"] = rec
            // 通知页面：首屏缓存换了新数据（冷启动缓存恢复后又拿到网络推荐位时，页面要重取一次）
            _homeCacheVersion.value++
            // 推荐位随 homeContent 一起到，直接落盘（不产生额外请求），下次冷启动可秒出
            schedulePersistHomeCache(siteKey)
        }
        _categories.value = if (rec.isEmpty()) {
            cats
        } else {
            listOf(VideoCategory(id = HOME_REC_CATEGORY_ID, name = HOME_REC_CATEGORY_NAME)) + cats
        }
    }

    /** 配置中全部可切换站点（spider + HTTP） */
    suspend fun listSites(): List<TvBoxSite> {
        val url = settingsRepository.configUrl.value
        if (url.isBlank()) return emptyList()
        return apiClient.listSites(url)
    }

    /** 播放失败自动换源候选：站点 + jar 规格 + 带选集的完整视频 */
    data class FallbackCandidate(
        val site: TvBoxSite,
        val jarSpec: String,
        val video: Video,
    )

    /**
     * 播放失败后跨站源找同名影片（fongmi VodFallbackPolicy 语义）：
     * 仅搜「参与搜索 + 允许换源」的站点，排除已失败站点，片名精确匹配，首个命中即返回。
     */
    suspend fun searchFallbackCandidate(
        title: String,
        excludeSiteKeys: Set<String>,
    ): FallbackCandidate? = coroutineScope {
        val url = settingsRepository.configUrl.value
        if (url.isBlank()) return@coroutineScope null
        val config = apiClient.loadConfig(url) ?: return@coroutineScope null
        val candidates = config.sites.filter { s ->
            (s.isSpider() || s.api.startsWith("http")) &&
                s.key !in excludeSiteKeys &&
                settingsRepository.isSiteSearchable(s) &&
                settingsRepository.isSiteChangeable(s)
        }
        val channel = kotlinx.coroutines.channels.Channel<FallbackCandidate>(
            kotlinx.coroutines.channels.Channel.UNLIMITED,
        )
        val jobs = candidates.map { site ->
            launch(Dispatchers.IO) {
                runCatching {
                    val jarSpec = if (site.isSpider()) site.jar.ifBlank { config.spider } else ""
                    val items = if (site.isSpider()) {
                        val spider = spiderManager.getSpider(site, jarSpec) ?: return@runCatching
                        parseList(spiderManager.searchContent(spider, title, quick = true))
                    } else {
                        apiClient.search(site, title)
                    }
                    val hit = items.firstOrNull { it.vodName == title } ?: return@runCatching
                    val detail = if (site.isSpider()) {
                        val spider = spiderManager.getSpider(site, jarSpec) ?: return@runCatching
                        parseList(spiderManager.detailContent(spider, listOf(hit.vodId))).firstOrNull()
                    } else {
                        apiClient.detail(site, hit.vodId)
                    } ?: return@runCatching
                    val video = detail.toVideo(
                        VideoCategory(id = "", name = detail.typeName.ifBlank { "未知" }),
                        withEpisodes = true,
                    )
                    if (video.playSources.isNotEmpty() || video.episodes.isNotEmpty()) {
                        channel.send(FallbackCandidate(site, jarSpec, video))
                    }
                }.onFailure { Log.e(REPO_TAG, "fallback search site=${site.name} fail", it) }
            }
        }
        var result: FallbackCandidate? = null
        try {
            result = runCatching {
                kotlinx.coroutines.withTimeout(20_000L) { channel.receive() }
            }.getOrNull()
        } finally {
            jobs.forEach { it.cancel() }
            channel.close()
        }
        result
    }

    /**
     * 链接解析播放（照搬 fongmi SiteApi.detailContent / playerContent 的 PUSH 分支）：
     * 1. Sniffer.getUrl 提取链接；
     * 2. 配置中存在 key="push_agent" 的 spider 站 → 构造元数据空壳 Video（id=链接）并缓存，
     *    完整详情（网盘多集展开）交由播放页 needAsyncDetail 异步补拉，慢站源也能秒进播放页；
     * 3. 无该站 → 本地构造直链 Vod 兜底（fongmi site.isEmpty() && PUSH 分支）。
     * 返回缓存后的 videoId（站点信息一并缓存，播放页经 playerContent 解析真实地址），null 表示无法解析。
     */
    suspend fun processPushUrl(rawText: String): String? =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val url = com.ican.tvplay.ui.components.UrlPlayHelper.getUrl(rawText)
            val configUrl = settingsRepository.configUrl.value
            if (configUrl.isNotBlank()) {
                runCatching {
                    val config = apiClient.loadConfig(configUrl)
                    val pushSite = config?.sites?.firstOrNull { it.key == "push_agent" && it.isSpider() }
                    if (config != null && pushSite != null) {
                        val jarSpec = pushSite.jar.ifBlank { config.spider }
                        val spider = spiderManager.getSpider(pushSite, jarSpec)
                        if (spider != null) {
                            // 元数据空壳：id=原始链接，播放页 needAsyncDetail 会调用
                            // detailContent(spider, listOf(url)) 展开网盘多集——慢站源秒进播放页
                            val video = com.ican.tvplay.data.model.Video(
                                id = url,
                                title = "推送播放",
                                cover = "",
                                categoryId = "",
                                categoryName = "推送播放",
                                year = 0,
                                rating = "",
                                region = "",
                                description = "推送：$url",
                                tags = emptyList(),
                                episodes = emptyList(),
                                playFrom = "",
                                playSources = emptyList(),
                                sourceName = pushSite.name,
                            )
                            com.ican.tvplay.player.core.Players.cacheVideo(video, pushSite.key, pushSite, jarSpec)
                            Log.d(REPO_TAG, "processPushUrl shell ready, defer detail to PlayerScreen")
                            return@withContext url
                        }
                    }
                }.onFailure { Log.e(REPO_TAG, "processPushUrl spider setup fail", it) }
            }
            // 无 push_agent 站 / spider 初始化失败：本地直链拆分（fongmi site.isEmpty() 分支同款）
            com.ican.tvplay.ui.components.UrlPlayHelper.preparePushPlay(rawText)
        }

    /**
     * 切换站点：最新一次优先——取消上一次还没跑完的切站加载，立即开始新源加载，不排队等旧源慢接口。
     * 返回 true = 新站分类已就绪；false = 失败或被更新的切站取代（站点身份已确认时停留在新站空态）。
     */
    suspend fun switchSite(siteKey: String): Boolean {
        settingsRepository.setSiteKey(siteKey)
        val gen = siteLoadGeneration.incrementAndGet()
        siteReloadJob?.cancel()
        siteReloadJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        return reloadLatest(gen)
    }

    /**
     * 最新一次切站生效的加载：慢步骤（下载配置 / getSpider / homeContent / 解析）全部在 loadMutex 之外执行，
     * 每步之间检查代数；只有仍是最新代数时才提交状态，避免旧站慢接口覆盖新站结果。
     */
    private suspend fun reloadLatest(gen: Int): Boolean {
        // 快速段：清缓存（持锁，与冷启动 ensureLoaded / 导入配置 reload 互斥）
        loadMutex.withLock {
            if (!isLatestSiteLoad(gen)) return false
            homeCache.clear()
            detailCache.clear()
            diskCacheFile.delete()
            configFile.delete()
        }

        val url = settingsRepository.configUrl.value
        if (!isLatestSiteLoad(gen)) return false
        if (url.isBlank()) {
            commitLatestSiteLoad(gen) {
                _currentSite.value = null
                _categories.value = emptyList()
                currentSpider = null
            }
            return false
        }

        Log.d("Startup", "switch: loadHomeSite begin")
        val loaded = apiClient.loadHomeSite(url, settingsRepository.siteKey.value)
        Log.d("Startup", "switch: loadHomeSite done site=${loaded?.site?.key}")
        if (!isLatestSiteLoad(gen)) return false
        if (loaded == null) {
            // 连配置都没读到，目标站身份无从确认：保持原站原内容不动（失败即回滚）
            Log.w(REPO_TAG, "switch: loadHomeSite failed, keep current site")
            return false
        }

        // 乐观提交站点身份：站源列表高亮 / 顶栏归属立即与用户的点击一致，
        // 分类等 homeContent 回来再补；失败了就停在新站空态，不再出现「内容没了、选中还是旧源」
        commitLatestSiteLoad(gen) {
            _currentSite.value = loaded
            _categories.value = emptyList()
            currentSpider = null
        }

        if (loaded.site.isSpider()) {
            Log.d("Startup", "switch: getSpider begin")
            val spider = spiderManager.getSpider(loaded.site, loaded.jarSpec)
            Log.d("Startup", "switch: getSpider done ok=${spider != null}")
            if (!isLatestSiteLoad(gen)) return false
            if (spider == null) {
                Log.e(REPO_TAG, "switch: spider load fail, stay empty")
                return false
            }
            Log.d("Startup", "switch: homeContent begin")
            val homeJson = spiderManager.homeContent(spider, filter = true)
            if (!isLatestSiteLoad(gen)) return false
            val cats = parseClasses(homeJson).map { VideoCategory(id = it.typeId, name = it.typeName, typeFlag = it.typeFlag) }
            // 首页推荐卡片：和分类同一次请求拿到，切源不必再等一次 categoryContent
            val rec = parseRecVideos(homeJson)
            // 空分类且无推荐卡片视为该源不可用：不写回分类，也不覆盖配置缓存，下次启动仍回到上一个可用源
            if (cats.isEmpty() && rec.isEmpty()) return false
            commitLatestSiteLoad(gen) {
                currentSpider = spider
                _currentSite.value = loaded
                loadDiskCacheIntoMemory(loaded.site.key)
                publishCategories(loaded.site.key, cats, rec)
                persistConfigCache(loaded)
            }
            Log.d("Startup", "switch: categories published size=${cats.size} rec=${rec.size}")
            return true
        } else {
            val (classes, recList) = apiClient.homeClassesAndList(loaded.site)
            if (!isLatestSiteLoad(gen)) return false
            val cats = classes.map { VideoCategory(id = it.typeId, name = it.typeName, typeFlag = it.typeFlag) }
            val rec = recList.map { it.toVideo(VideoCategory(HOME_REC_CATEGORY_ID, HOME_REC_CATEGORY_NAME)) }
            if (cats.isEmpty() && rec.isEmpty()) return false
            commitLatestSiteLoad(gen) {
                currentSpider = null
                _currentSite.value = loaded
                loadDiskCacheIntoMemory(loaded.site.key)
                publishCategories(loaded.site.key, cats, rec)
                persistConfigCache(loaded)
            }
            Log.d("Startup", "switch: categories published size=${cats.size} rec=${rec.size}")
            return true
        }
    }

    private fun isLatestSiteLoad(gen: Int): Boolean = gen == siteLoadGeneration.get()

    private suspend inline fun commitLatestSiteLoad(gen: Int, block: () -> Unit) {
        loadMutex.withLock {
            if (isLatestSiteLoad(gen)) block()
        }
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

    /**
     * 网盘目录钻取（fongmi FolderFragment.openFolder 同款）：
     * 用文件夹自身 id 当 tid 调 spider 的 categoryContent 取子目录内容 ——
     * 子目录 id 不在分类表里，所以不能复用 [getVideos]。
     * 文件夹内无筛选条件，filter 传 false。
     */
    suspend fun getFolderVideos(folderId: String): List<Video> {
        val tid = folderId.trim()
        if (tid.isEmpty()) return emptyList()
        ensureLoaded()
        val loaded = _currentSite.value ?: return emptyList()
        if (!loaded.site.isSpider()) return emptyList()
        val spider = currentSpider ?: return emptyList()
        val json = spiderManager.categoryContent(spider, tid, 1, filter = false)
        val list = parseList(json).map { it.toVideo(VideoCategory(id = tid, name = it.typeName.ifBlank { tid })) }
        Log.d(
            REPO_TAG,
            "folder[$tid] n=${list.size} " +
                list.take(8).joinToString { "${it.title}(${it.id})${if (it.isFolder) "[dir]" else ""}" },
        )
        return list
    }

    /** 详情：补全选集 / 播放地址等（内存 + 24h 磁盘缓存，重复进详情秒开） */
    suspend fun getVideo(videoId: String): Video? {
        detailCache[videoId]?.let { return it }
        // 详情优先：取消追番更新检测，让出网络与 IO（冷启动首次提速）
        yieldForDetail()
        ensureLoaded()
        val loaded = _currentSite.value ?: return null
        readDetailDiskCache(loaded.site.key, videoId)?.let {
            detailCache[videoId] = it
            return it
        }
        val item = fetchDetail(loaded, videoId) ?: return null
        val catName = item.typeName.ifBlank { "未知" }
        return item.toVideo(VideoCategory(id = "", name = catName), withEpisodes = true)
            // 站源名回填：详情接口通常不带站点信息，缺了就会在详情页回退显示成线路名（如「刷刷刷」）
            .copy(
                sourceName = com.ican.tvplay.ui.components.siteShortName(
                    loaded.site.name, loaded.site.key,
                ),
            )
            .also {
                detailCache[videoId] = it
                writeDetailDiskCache(loaded.site.key, videoId, it)
            }
    }

    /**
     * 按 sourceKey 直接定位站点与 jarSpec（只读配置缓存，不触发 ensureLoaded 的整站首页初始化）。
     * 供历史/收藏直达详情：跳过「配置刷新 + homeContent + 分类」的串行等待，与搜索卡片同链路。
     */
    suspend fun findSiteForDirectDetail(sourceKey: String): Pair<TvBoxSite, String>? {
        val url = settingsRepository.configUrl.value
        if (url.isBlank()) return null
        val config = apiClient.loadConfig(url) ?: return null
        val site = config.sites.firstOrNull { it.key == sourceKey } ?: return null
        val jarSpec = if (site.isSpider()) site.jar.ifBlank { config.spider } else ""
        return site to jarSpec
    }

    // ---- 详情磁盘缓存：key=siteKey|videoId（MD5 文件名），mtime 超 24h 自动失效 ----

    private val detailDiskDir by lazy {
        java.io.File(appContext.applicationContext.filesDir, "detail_cache").apply { mkdirs() }
    }

    fun readDetailDiskCache(siteKey: String, videoId: String): Video? {
        val file = detailDiskFile(siteKey, videoId)
        if (!file.exists()) return null
        if (System.currentTimeMillis() - file.lastModified() > DETAIL_DISK_TTL_MS) {
            file.delete()
            return null
        }
        return runCatching {
            rawJson.decodeFromString(Video.serializer(), file.readText())
        }.getOrNull()
    }

    fun writeDetailDiskCache(siteKey: String, videoId: String, video: Video) {
        runCatching {
            detailDiskFile(siteKey, videoId).writeText(
                rawJson.encodeToString(Video.serializer(), video),
            )
        }
    }

    private fun detailDiskFile(siteKey: String, videoId: String): java.io.File {
        val digest = java.security.MessageDigest.getInstance("MD5")
            .digest("$siteKey|$videoId".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return java.io.File(detailDiskDir, "$digest.json")
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
     * 执行当前站点的 action 卡片操作（fongmi SiteApi.action 同款）：
     * spider 站调 jar 的 action()（jar 内部弹扫码/输入流程）。
     * 返回 JSON 中的 msg 供 Toast，空串表示无提示。
     */
    suspend fun executeSiteAction(action: String): String {
        val loaded = _currentSite.value ?: return ""
        if (!loaded.site.isSpider()) return ""
        val spider = currentSpider
            ?: spiderManager.getSpider(loaded.site, loaded.jarSpec)
            ?: return ""
        val jsonStr = spiderManager.action(spider, action)
        if (jsonStr.isBlank()) return ""
        return runCatching {
            rawJson.parseToJsonElement(jsonStr).jsonObject["msg"]?.jsonPrimitive?.content.orEmpty()
        }.getOrDefault("")
    }

    /**
     * 播放前调用：spider 站经 playerContent 解析真实 URL（可能带 header）；
     * HTTP 站直接返回原 url。flag 为所选线路标识，缺省用视频首源。
     */
    suspend fun resolvePlaySource(
        video: Video,
        episode: Episode,
        flag: String? = null,
        qualityPosition: Int = -1,
    ): PlaySource {
        val loaded = _currentSite.value ?: return PlaySource(episode.playUrl)
        // 站点级 header（fongmi Site.header）：HTTP 站直接拉流也要带上，否则防盗链站 403/空
        val siteHeaders = loaded.site.headerMap()
        if (!loaded.site.isSpider()) {
            // type=4：播放地址要再经 api?play=&flag= 换一次（fongmi loadPlayerContent type 4 分支）
            if (loaded.site.type == 4) {
                val json = apiClient.playerContentHttp(
                    loaded.site,
                    flag?.takeIf { it.isNotBlank() } ?: video.playFrom,
                    episode.playUrl,
                )
                if (!json.isNullOrBlank()) {
                    val content = parsePlayerContent(json, episode.playUrl, siteHeaders, qualityPosition)
                    if (content.url.isNotBlank()) return content
                }
            }
            return PlaySource(episode.playUrl, siteHeaders)
        }
        val spider = currentSpider ?: return PlaySource(episode.playUrl, siteHeaders)
        val resolvedFlag = flag?.takeIf { it.isNotBlank() }
            ?: video.playFrom.ifBlank { loaded.site.key }
        val json = spiderManager.playerContent(spider, resolvedFlag, episode.playUrl)
        if (json.isBlank()) return PlaySource(episode.playUrl, siteHeaders)
        return parsePlayerContent(json, episode.playUrl, siteHeaders, qualityPosition)
    }

    /**
     * 解析 playerContent 返回的 JSON → 可播放地址 + 请求头。
     * 对齐 fongmi Result：url 支持字符串/扁平数组/values 对象（[parsePlayerUrl]），
     * header 为站点 header 与返回 header 的并集（返回的优先）。
     */
    private suspend fun parsePlayerContent(
        json: String,
        fallbackUrl: String,
        siteHeaders: Map<String, String>,
        qualityPosition: Int = -1,
    ): PlaySource {
        return runCatching {
            val obj = rawJson.parseToJsonElement(json).jsonObject
            // fongmi Result.Url：全部画质 + 当前 position（切画质时由调用方指定）
            val qualities = com.ican.tvplay.data.remote.parsePlayerQualities(obj["url"])
            val pos = (if (qualityPosition >= 0) qualityPosition else com.ican.tvplay.data.remote.parsePlayerPosition(obj["url"]))
                .coerceIn(0, (qualities.size - 1).coerceAtLeast(0))
            val rawUrl = com.ican.tvplay.data.remote.parsePlayerUrl(obj["url"], pos).ifBlank { fallbackUrl }
            val headers = siteHeaders.toMutableMap()
            obj["header"]?.let { el ->
                runCatching {
                    when (el) {
                        is kotlinx.serialization.json.JsonObject -> el.entries.forEach { (k, v) ->
                            (v as? kotlinx.serialization.json.JsonPrimitive)?.let { h -> headers[k] = h.content }
                        }
                        is kotlinx.serialization.json.JsonArray -> el.mapNotNull {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                        }.forEach { line ->
                            val idx = line.indexOf(':')
                            if (idx > 0) headers[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
                        }
                        else -> Unit
                    }
                }
            }
            val parse = obj["parse"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val jx = obj["jx"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            Log.d(REPO_TAG, "playerContent url=${rawUrl.take(120)} parse=$parse jx=$jx headers=${headers.keys}")
            // parse=1/2 或 jx=1 → 需 jx 解析（很多 spider 返回的是解析页 URL 而非真实流）
            val finalUrl = if (JxParser.needsParse(rawUrl, parse, jx)) {
                JxParser.resolve(rawUrl, headers).ifBlank { rawUrl }
            } else {
                rawUrl
            }
            PlaySource(url = finalUrl.ifBlank { fallbackUrl }, headers = headers, qualities = qualities, qualityPosition = pos)
        }.onFailure { Log.e(REPO_TAG, "parse playerContent fail", it) }
            .getOrDefault(PlaySource(fallbackUrl, siteHeaders))
    }

    // ---- 内部：按站点类型分发数据加载 ----

    private suspend fun loadCategoryVideos(
        loaded: LoadedSite,
        cat: VideoCategory,
        page: Int,
    ): List<Video> {
        // 首页第一页走内存缓存（只有真正访问过的分类才在里面）；翻页不缓存
        val list = if (page == 1) {
            val key = "${loaded.site.key}|${cat.id}"
            // 空结果不入缓存：spider 未就绪 / 网络异常时取到空列表若被缓存，
            // 之后首页会一直命中空缓存（历史 bug：首次启动首页没有视频卡片）
            homeCache[key] ?: fetchCategoryVideos(loaded, cat)
                .also {
                    if (it.isNotEmpty()) {
                        homeCache[key] = it
                        // 按需落盘：只把真正加载成功的分类写盘（不额外发请求），供下次冷启动秒出
                        schedulePersistHomeCache(loaded.site.key)
                    }
                }
        } else {
            fetchCategoryVideos(loaded, cat)
        }
        Log.d(
            REPO_TAG,
            "category[${cat.id}] n=${list.size} " +
                list.take(8).joinToString { "${it.title}(${it.id})${if (it.isFolder) "[dir]" else ""}" },
        )
        return list
    }

    private suspend fun fetchCategoryVideos(
        loaded: LoadedSite,
        cat: VideoCategory,
    ): List<Video> {
        // 「推荐」是虚拟分类，卡片只来自 homeContent 本地缓存；缓存未命中就返回空，绝不发 categoryContent
        if (cat.id == HOME_REC_CATEGORY_ID) return emptyList()
        return if (loaded.site.isSpider()) {
            // 冷启动时「配置+分类」先由磁盘缓存恢复，spider 仍在后台加载（[ensureLoaded] 网络刷新）。
            // 此时直接取会拿到空列表，历史上还会把空列表写进缓存与磁盘 → 之后每次启动首页都空白。
            // 这里等启动刷新（持 loadMutex）走完再取，spider 就绪后正常返回数据
            val spider = currentSpider ?: run {
                loadMutex.withLock { }
                currentSpider
            } ?: return emptyList()
            val json = spiderManager.categoryContent(spider, cat.id, 1, filter = true)
            parseList(json).map { it.toVideo(cat) }
        } else {
            apiClient.categoryVideos(loaded.site, cat.id, 1).map { it.toVideo(cat) }
        }
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
     * 冷启动单独预热「玩偶哥哥」spider：该站首次 detailContent 要叠加 jar 加载 + spider 初始化
     * + 网盘多次跳转，常超过播放页 15s 详情超时（超时后底层请求仍在跑，重试才进得去）。
     * 启动时后台完成实例化与初始化，首次点击进入即可复用已初始化的 spider。
     * 仅初始化该站（api 含 Wogg 或名称含「玩偶」），其他站点保持按需加载。
     */
    suspend fun prewarmWoggSpider() = withContext(Dispatchers.IO) {
        val url = settingsRepository.configUrl.value
        if (url.isBlank()) return@withContext
        runCatching {
            val config = apiClient.loadConfig(url) ?: return@withContext
            val site = config.sites.firstOrNull {
                it.isSpider() && (it.api.contains("Wogg", ignoreCase = true) || it.name.contains("玩偶"))
            } ?: return@withContext
            spiderManager.getSpider(site, site.jar.ifBlank { config.spider })
        }.onFailure { Log.w(REPO_TAG, "prewarmWoggSpider failed", it) }
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
                runCatching { rawJson.decodeFromJsonElement(VodItem.serializer(), el) }
                    // 单条解码失败不再静默丢掉（网盘文件夹丢了就点不进去），
                    // 打日志 + 按字段宽松兜底，保证条目不缺
                    .onFailure {
                        Log.e(REPO_TAG, "parseList item fail: ${el.toString().take(400)}", it)
                        Log.d(REPO_TAG, "parseList item json=${el.toString().take(400)}")
                    }
                    .getOrElse { lenientVodItem(el) }
            }
        }.onFailure { Log.e(REPO_TAG, "parseList fail", it) }.getOrDefault(emptyList())
    }

    /** 兜底解析：严格解码失败时逐字段宽松取值，避免整条被丢弃 */
    private fun lenientVodItem(el: JsonElement): VodItem? {
        val obj = el as? JsonObject ?: return null
        fun s(key: String) = (obj[key] as? JsonPrimitive)?.content.orEmpty()
        return VodItem(
            vodId = s("vod_id"),
            vodName = s("vod_name"),
            vodPic = s("vod_pic"),
            vodRemarks = s("vod_remarks"),
            vodYear = s("vod_year"),
            vodArea = s("vod_area"),
            vodContent = s("vod_content"),
            vodPlayFrom = s("vod_play_from"),
            vodPlayUrl = s("vod_play_url"),
            typeName = s("type_name"),
            action = s("action"),
            vodTag = s("vod_tag"),
            cate = obj["cate"],
            style = obj["style"],
            land = (obj["land"] as? JsonPrimitive)?.content?.toIntOrNull(),
            circle = (obj["circle"] as? JsonPrimitive)?.content?.toIntOrNull(),
            ratio = (obj["ratio"] as? JsonPrimitive)?.content?.toDoubleOrNull(),
        )
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
            action = action,
            // 目录判定对齐 fongmi Vod.isFolder()：vod_tag == "folder"/"cover" 或带 cate 目录元数据
            isFolder = isFolderItem(),
            // cover 类型目录用横版宽卡渲染（OK猫 flag '2'）
            isCoverFolder = isCoverItem(),
            // 条目自带样式（fongmi Vod.getStyle）：优先 style 字段，其次 land/circle/ratio 折算
            cardStyleType = (style?.let { parseStyle(it) } ?: styleOf(land, circle, ratio))?.normalizedType().orEmpty(),
            cardStyleRatio = (style?.let { parseStyle(it) } ?: styleOf(land, circle, ratio))?.effectiveRatio() ?: 0f,
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
