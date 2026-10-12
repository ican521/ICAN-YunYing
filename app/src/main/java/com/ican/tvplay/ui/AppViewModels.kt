package com.ican.tvplay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ican.tvplay.data.AppContainer
import com.ican.tvplay.data.ThemeMode
import com.ican.tvplay.data.local.FavoriteEntity
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.data.model.VideoCategory
import com.ican.tvplay.data.remote.TvBoxSite
import com.ican.tvplay.ui.components.siteShortName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 全局：主题模式 / 动态取色 / 主题色板 / 全局模糊 / 接口配置状态 */
class AppViewModel(container: AppContainer) : ViewModel() {
    private val settings = container.settingsRepository
    private val videoRepository = container.videoRepository

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
    val themeColor: StateFlow<Int> = settings.themeColor
    val enableBlur: StateFlow<Boolean> = settings.enableBlur

    /** 接口配置是否已就绪（已导入并成功加载站点） */
    val configReady: StateFlow<Boolean> = videoRepository.currentSite
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
}

/** 首页：分类切换 + 各分区视频 + 站源切换 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.videoRepository

    /** 分类列表：响应式，配置异步加载完成后自动推送 */
    val categories: StateFlow<List<VideoCategory>> = repo.categoriesFlow

    /** 当前站源名；未加载成功时为「未配置」 */
    val siteName: StateFlow<String> = repo.currentSite
        .map { it?.site?.name?.takeIf { n -> n.isNotBlank() } ?: "未配置" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "未配置")

    /** 当前站源 key（用于站点选择对话框高亮） */
    val currentSiteKey: StateFlow<String> = repo.currentSite
        .map { it?.site?.key.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = selectedCategoryId.asStateFlow()

    val sections: StateFlow<List<Pair<VideoCategory, List<Video>>>> =
        combine(selectedCategoryId, repo.categoriesFlow, repo.homeCacheVersion) { categoryId, _, _ -> categoryId }
            .flatMapLatest { categoryId -> repo.homeSectionsFlow(categoryId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(id: String?) {
        selectedCategoryId.value = id
    }

    /** 配置中全部可切换站点 */
    suspend fun listSites() = repo.listSites()

    /** 切换站源：仓库层会保存选择并重新加载分类，sections 随 categoriesFlow 自动刷新 */
    fun switchSite(siteKey: String) {
        viewModelScope.launch { repo.switchSite(siteKey) }
    }

    /** 按分类 id 独立取该分类下视频（每页独立加载，不依赖 selectedCategory 选中态） */
    suspend fun getCategoryVideos(categoryId: String): List<Video> = repo.getVideos(categoryId)
}

/** 详情：视频信息 + 收藏态 + 播放进度 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.videoRepository
    private val favoriteDao = container.favoriteDao
    private val historyDao = container.historyDao

    private val videoId = MutableStateFlow<String?>(null)

    val video: StateFlow<Video?> = videoId
        .filterNotNull()
        .map { repo.getVideo(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isFavorite: StateFlow<Boolean> = videoId
        .filterNotNull()
        .flatMapLatest { favoriteDao.observeIsFavorite(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val history: StateFlow<HistoryEntity?> = videoId
        .filterNotNull()
        .flatMapLatest { historyDao.observeOne(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(id: String) {
        if (videoId.value != id) videoId.value = id
    }

    fun toggleFavorite(video: Video) {
        viewModelScope.launch {
            val current = favoriteDao.isFavorite(video.id)
            if (current) {
                favoriteDao.delete(video.id)
            } else {
                // 记录视频所在站源：点击收藏时按此站源拉详情并播放
                val loaded = repo.currentSite.value
                val siteKey = loaded?.site?.key.orEmpty()
                val siteName = video.sourceName.ifBlank {
                    siteShortName(loaded?.site?.name.orEmpty(), siteKey)
                }
                favoriteDao.upsert(
                    FavoriteEntity(
                        videoId = video.id,
                        title = video.title,
                        cover = video.cover,
                        categoryName = video.categoryName,
                        addedAt = System.currentTimeMillis(),
                        sourceKey = siteKey,
                        sourceName = siteName,
                    ),
                )
            }
        }
    }
}

/** 搜索：关键词防抖 + 历史记录 + 推荐词 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.videoRepository
    private val settings = container.settingsRepository

    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query.asStateFlow()

    val results: StateFlow<List<Video>> = query
        .debounce(250)
        .mapLatest { q ->
            if (q.isBlank()) emptyList()
            else {
                settings.addSearchHistory(q)
                repo.search(q)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<String>> = settings.searchHistory

    /** 热门推荐词（空查询时显示，fongmi 式） */
    val hotWords = listOf("仙逆", "狂飙", "三体", "庆余年", "繁花", "凡人修仙传", "长风渡")

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** 记录一条搜索历史（搜索首页提交 / 结果页改词搜索都会调用） */
    fun addHistory(keyword: String) {
        val q = keyword.trim()
        if (q.isNotEmpty()) settings.addSearchHistory(q)
    }

    fun clearHistory() {
        settings.clearSearchHistory()
    }

    fun removeHistory(keyword: String) {
        settings.removeSearchHistory(keyword)
    }
}

/** 收藏 / 播放历史列表 */
class CollectionViewModel(container: AppContainer) : ViewModel() {
    private val favoriteDao = container.favoriteDao
    private val historyDao = container.historyDao
    private val settings = container.settingsRepository
    private val spiderManager = container.spiderManager
    private val apiClient = com.ican.tvplay.data.remote.VodApiClient()

    val favorites = favoriteDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val histories = historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 收藏点击播放的准备结果：带选集的完整视频 + 所在站点信息（播放页延迟解析需要） */
    data class PreparedFavoritePlay(
        val video: Video,
        val siteKey: String,
        val site: TvBoxSite?,
        val jarSpec: String,
        val firstFlag: String,
    )

    /**
     * 按记录的站源 key 拉取完整详情（含选集），返回可缓存进播放器的数据（收藏/历史通用）。
     * 旧记录没有 sourceKey 时兜底当前站；站源已失效或详情拉取失败返回 null。
     */
    suspend fun preparePlay(
        videoId: String,
        sourceKey: String,
        sourceName: String,
    ): PreparedFavoritePlay? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val configUrl = settings.configUrl.value
            if (configUrl.isBlank()) return@withContext null

            val config = apiClient.loadConfig(configUrl) ?: return@withContext null
            val site = config.sites.firstOrNull { it.key == sourceKey }
                ?: config.sites.firstOrNull { it.key == settings.siteKey.value }
                ?: return@withContext null

            val jarSpec = if (site.isSpider()) site.jar.ifBlank { config.spider } else ""
            val item = runCatching {
                if (site.isSpider()) {
                    val spider = spiderManager.getSpider(site, jarSpec)
                    if (spider != null) {
                        com.ican.tvplay.data.remote.VodParse.parseVodListJson(
                            spiderManager.detailContent(spider, listOf(videoId)),
                        ).firstOrNull()
                    } else null
                } else {
                    apiClient.detail(site, videoId)
                }
            }.getOrNull() ?: return@withContext null

            val video = with(com.ican.tvplay.data.remote.VodParse) {
                item.toVideo(withEpisodes = true)
            }.copy(sourceName = sourceName)
            val firstFlag = video.playSources.firstOrNull()?.flag ?: video.playFrom
            PreparedFavoritePlay(video, site.key, site, jarSpec, firstFlag)
        }

    /** 点击收藏卡片：按收藏记录的站源 key 拉详情 */
    suspend fun prepareFavoritePlay(entity: FavoriteEntity): PreparedFavoritePlay? =
        preparePlay(entity.videoId, entity.sourceKey, entity.sourceName)

    /** 点击历史卡片：按历史记录的站源 key 拉详情并续播 */
    suspend fun prepareHistoryPlay(entity: HistoryEntity): PreparedFavoritePlay? =
        preparePlay(entity.videoId, entity.sourceKey, entity.sourceName)

    fun removeFavorite(videoId: String) {
        viewModelScope.launch { favoriteDao.delete(videoId) }
    }

    fun removeHistory(videoId: String) {
        viewModelScope.launch { historyDao.delete(videoId) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyDao.clear() }
    }

    fun clearFavorites() {
        viewModelScope.launch { favoriteDao.clear() }
    }
}

/** 设置 */
class SettingsViewModel(container: AppContainer) : ViewModel() {
    private val settings = container.settingsRepository
    private val videoRepository = container.videoRepository

    init {
        // 启动即后台预热：已导入过配置时，加载站点/分类并把首页首屏拉进缓存，
        // 用户切到首页时数据已就绪（fongmi 式启动预加载）
        viewModelScope.launch { videoRepository.prewarmHome() }
    }

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
    val themeColor: StateFlow<Int> = settings.themeColor
    val enableBlur: StateFlow<Boolean> = settings.enableBlur
    val playerBackgroundPlay: StateFlow<Int> = settings.playerBackgroundPlay
    val configUrl: StateFlow<String> = settings.configUrl

    /** 当前站源名；未加载成功时为「未配置」 */
    val siteName: StateFlow<String> = videoRepository.currentSite
        .map { it?.site?.name?.takeIf { n -> n.isNotBlank() } ?: "未配置" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "未配置")

    /** 当前站源 key（用于站点选择对话框高亮） */
    val currentSiteKey: StateFlow<String> = videoRepository.currentSite
        .map { it?.site?.key.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
    fun setDynamicColor(enabled: Boolean) = settings.setDynamicColor(enabled)
    fun setThemeColor(color: Int) = settings.setThemeColor(color)
    fun setEnableBlur(enabled: Boolean) = settings.setEnableBlur(enabled)
    fun setPlayerBackgroundPlay(mode: Int) = settings.setPlayerBackgroundPlay(mode)

    /** 保存配置 URL 后立即重新拉取站点与分类，并后台预热首页各分类首屏（fongmi 式） */
    fun setConfigUrl(url: String) {
        settings.setConfigUrl(url)
        viewModelScope.launch {
            videoRepository.reload()
            launch { videoRepository.prewarmHome() }
            launch { videoRepository.prewarmJars() }
        }
    }

    /** 配置中全部可切换站点 */
    suspend fun listSites() = videoRepository.listSites()

    /** 站源「参与搜索」开关（fongmi 同款；返回切换后是否开启；配置锁定的站点返回 false 且不变） */
    fun toggleSiteSearchable(site: TvBoxSite): Boolean = settings.toggleSiteSearchable(site)

    /** 站源「播放失败自动换源」开关 */
    fun toggleSiteChangeable(site: TvBoxSite): Boolean = settings.toggleSiteChangeable(site)

    /** 开关是否开启（弹窗图标着色用） */
    fun isSiteSearchable(site: TvBoxSite): Boolean = settings.isSiteSearchable(site)
    fun isSiteChangeable(site: TvBoxSite): Boolean = settings.isSiteChangeable(site)

    /** 开关版本号：弹窗 collect 后每次切换自动重组刷新图标 */
    val siteToggleVersion: StateFlow<Int> = settings.siteToggleVersion

    /** 切换站源：仓库层会保存选择并重新加载分类，随后后台预热首页首屏 */
    fun switchSite(siteKey: String) {
        viewModelScope.launch {
            videoRepository.switchSite(siteKey)
            launch { videoRepository.prewarmHome() }
        }
    }
}
