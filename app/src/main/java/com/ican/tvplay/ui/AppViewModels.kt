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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 热搜榜不可用（无缓存/首次启动/离线）时的兜底推荐词 */
private val DEFAULT_HOT_WORDS = listOf("仙逆", "狂飙", "三体", "庆余年", "繁花", "凡人修仙传", "长风渡")

/** 全局：主题模式 / 主题色板 / 全局模糊 / 接口配置状态 */
class AppViewModel(container: AppContainer) : ViewModel() {
    private val settings = container.settingsRepository
    private val videoRepository = container.videoRepository

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val themeColor: StateFlow<Int> = settings.themeColor
    val enableBlur: StateFlow<Boolean> = settings.enableBlur
    val bottomBarBlurEnabled: StateFlow<Boolean> = settings.bottomBarBlurEnabled
    val topBarBlurEnabled: StateFlow<Boolean> = settings.topBarBlurEnabled
    val configReady: StateFlow<Boolean> = videoRepository.currentSite
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    /** 链接解析播放（fongmi PUSH 链路：push_agent spider 展开网盘链接，失败回退本地直链） */
    suspend fun processPushUrl(rawText: String): String? = videoRepository.processPushUrl(rawText)
}

/** 首页：分类切换 + 各分区视频 + 站源切换 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.videoRepository
    val settings = container.settingsRepository

    /** 首页网格布局大小（小/中/大） */
    val homeLayoutSize: StateFlow<com.ican.tvplay.data.HomeLayoutSize> =
        container.settingsRepository.homeLayoutSize

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

    /** 网盘目录钻取：按文件夹 id 取子目录内容（fongmi openFolder 同款） */
    suspend fun getFolderVideos(folderId: String): List<Video> = repo.getFolderVideos(folderId)

    /**
     * 执行当前站点 action 卡片的 jar 操作（网盘配置等），完成后回调 msg（空串不提示）。
     */
    fun executeDriveAction(action: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val msg = runCatching { repo.executeSiteAction(action) }.getOrDefault("")
            onDone(msg)
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

    /**
     * 热门推荐词（空查询时显示）：本地缓存优先立即展示（fongmi Setting.getHot 同款），
     * 网络榜单由 [refreshHotWords] 在进入搜索页时后台刷新并回写缓存；
     * 无缓存时用固定兜底词，避免首屏空白。
     */
    val hotWords: Flow<List<String>> = settings.hotWordsJson.map { cached ->
        com.ican.tvplay.data.remote.HotWordSource.parse(cached).ifEmpty { DEFAULT_HOT_WORDS }
    }

    /** 拉取最新热搜榜（360 影视热搜榜，取前 12 条）并回写缓存；失败静默保留现有词表 */
    suspend fun refreshHotWords() {
        val body = com.ican.tvplay.data.remote.HotWordSource.fetch() ?: return
        if (com.ican.tvplay.data.remote.HotWordSource.parse(body).isNotEmpty()) {
            settings.setHotWordsJson(body)
        }
    }

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
    private val deviceDao = container.deviceDao
    private val settings = container.settingsRepository
    private val spiderManager = container.spiderManager
    private val apiClient = com.ican.tvplay.data.remote.VodApiClient()

    val favorites = favoriteDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val histories = historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 已发现的局域网同步设备（fongmi Device.getAll） */
    val devices = deviceDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 收藏同步模式：0=双向 1=上传 2=下载（fongmi Setting.sync_mode） */
    val syncMode: StateFlow<Int> = settings.syncMode

    /** 点击模式按钮循环切换：双向 → 上传 → 下载 */
    fun cycleSyncMode() {
        settings.setSyncMode((syncMode.value + 1) % 3)
    }

    /** 清空设备表并重新扫描局域网（fongmi SyncDialog.onRefresh）；挂起函数，完成后 UI 收尾 */
    suspend fun rescanDevices(context: android.content.Context) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            deviceDao.clear()
            com.ican.tvplay.data.FavoriteSync.scan(context)
        }
    }

    /** 网格布局大小（小/中/大）：收藏/历史页列数跟随「设置-首页布局大小」，与首页同一规则 */
    val homeLayoutSize: StateFlow<com.ican.tvplay.data.HomeLayoutSize> = settings.homeLayoutSize

    /** 收藏点击播放的准备结果：带选集的完整视频 + 所在站点信息（播放页延迟解析需要） */
    data class PreparedFavoritePlay(
        val video: Video,
        val siteKey: String,
        val site: TvBoxSite?,
        val jarSpec: String,
        val firstFlag: String,
    )

    /**
     * 收藏/历史点击播放（fongmi 详情页异步加载语义 + 搜索页同款「先进骨架再补详情」）：
     * 只做站点定位（配置已全局缓存，毫秒级），把元数据空壳视频直接交给播放页；
     * 完整详情（选集/线路）由 PlayerScreen 的 needAsyncDetail 分支在页内异步补拉，
     * 慢站源（如玩偶哥哥网盘类 spider）也能秒进播放页并看到骨架。
     * 站源已失效返回 null。
     */
    private suspend fun preparePlayShell(
        videoId: String,
        title: String,
        cover: String,
        categoryName: String,
        sourceKey: String,
        sourceName: String,
        lineFlag: String = "",
    ): PreparedFavoritePlay? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val configUrl = settings.configUrl.value
            if (configUrl.isBlank()) return@withContext null

            val config = apiClient.loadConfig(configUrl) ?: return@withContext null
            val site = config.sites.firstOrNull { it.key == sourceKey }
                ?: config.sites.firstOrNull { it.key == settings.siteKey.value }
                ?: return@withContext null

            val jarSpec = if (site.isSpider()) site.jar.ifBlank { config.spider } else ""
            // 元数据空壳：playSources/episodes 为空，触发播放页 needAsyncDetail 补详情
            val video = Video(
                id = videoId,
                title = title,
                cover = cover,
                categoryId = "",
                categoryName = categoryName,
                year = 0,
                rating = "",
                region = "",
                description = "",
                tags = emptyList(),
                episodes = emptyList(),
                playFrom = "",
                playSources = emptyList(),
                sourceName = sourceName,
            )
            PreparedFavoritePlay(video, site.key, site, jarSpec, firstFlag = lineFlag)
        }

    /** 点击收藏卡片：元数据先进播放页，详情页内补拉。
     *  若该视频有历史记录，复用其上次所选线路（lineFlag）。 */
    suspend fun prepareFavoritePlay(entity: FavoriteEntity): PreparedFavoritePlay? {
        // 收藏本身不存线路：查历史表取上次播放所选线路，无则留空兜底首条
        val rememberedFlag = runCatching {
            historyDao.observeOne(entity.videoId).first()?.lineFlag.orEmpty()
        }.getOrDefault("")
        return preparePlayShell(
            entity.videoId, entity.title, entity.cover, entity.categoryName,
            entity.sourceKey, entity.sourceName, rememberedFlag,
        )
    }

    /** 点击历史卡片：元数据先进播放页，详情页内补拉（续播进度播放页读历史表）。
     *  恢复历史记录中保存的线路。 */
    suspend fun prepareHistoryPlay(entity: HistoryEntity): PreparedFavoritePlay? =
        preparePlayShell(
            entity.videoId, entity.title, entity.cover, entity.categoryName,
            entity.sourceKey, entity.sourceName, entity.lineFlag,
        )

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

/** 追番更新：收藏剧集的集数变化检测结果 + 启动弹窗 */
class UpdatesViewModel(container: AppContainer) : ViewModel() {
    private val favoriteDao = container.favoriteDao
    private val historyDao = container.historyDao
    private val checker = container.favoriteUpdateChecker
    private val settings = container.settingsRepository
    private val apiClient = com.ican.tvplay.data.remote.VodApiClient()

    /** 手动「重新检查」进行中 */
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** 全部有更新待看的收藏（今日更新 + 以前已更新未看） */
    val allUpdates: StateFlow<List<com.ican.tvplay.data.FavoriteUpdateItem>> =
        combine(favoriteDao.observeAll(), historyDao.observeAll()) { favorites, histories ->
            com.ican.tvplay.data.FavoriteUpdates.compute(favorites, histories)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 今日更新（lastUpdatedAt 落在今天且仍有未看集） */
    val todayUpdates: StateFlow<List<com.ican.tvplay.data.FavoriteUpdateItem>> = allUpdates
        .map { list -> list.filter { it.updatedToday } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 以前更新过但仍未看的 */
    val pendingUpdates: StateFlow<List<com.ican.tvplay.data.FavoriteUpdateItem>> = allUpdates
        .map { list -> list.filterNot { it.updatedToday } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 点击更新条目跳播放页所需：元数据空壳 + 站源（完整选集由播放页异步补拉） */
    data class PreparedUpdatePlay(
        val video: Video,
        val siteKey: String,
        val site: TvBoxSite?,
        val jarSpec: String,
        val flag: String,
    )

    suspend fun preparePlay(item: com.ican.tvplay.data.FavoriteUpdateItem): PreparedUpdatePlay? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val configUrl = settings.configUrl.value
            if (configUrl.isBlank()) return@withContext null
            val config = apiClient.loadConfig(configUrl) ?: return@withContext null
            val favorite = item.favorite
            val site = config.sites.firstOrNull { it.key == favorite.sourceKey }
                ?: config.sites.firstOrNull { it.key == settings.siteKey.value }
                ?: return@withContext null
            val jarSpec = if (site.isSpider()) site.jar.ifBlank { config.spider } else ""
            val video = Video(
                id = favorite.videoId,
                title = favorite.title,
                cover = favorite.cover,
                categoryId = "",
                categoryName = favorite.categoryName,
                year = 0,
                rating = "",
                region = "",
                description = "",
                tags = emptyList(),
                episodes = emptyList(),
                playFrom = "",
                playSources = emptyList(),
                sourceName = favorite.sourceName,
            )
            PreparedUpdatePlay(
                video = video,
                siteKey = site.key,
                site = site,
                jarSpec = jarSpec,
                flag = item.history?.lineFlag.orEmpty(),
            )
        }

    /** 手动重新检查（忽略频率节流） */
    fun manualCheck() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            runCatching { checker.check(force = true) }
            _checking.value = false
        }
    }

    /** 标记单部已读：已看基线推到当前总集数 */
    fun markRead(videoId: String) {
        viewModelScope.launch { favoriteDao.acknowledge(videoId) }
    }

    /** 全部标记已读 */
    fun markAllRead() {
        viewModelScope.launch { favoriteDao.acknowledgeAll() }
    }

    /**
     * 启动检测：按频率节流执行一次。
     * 只有「今天有更新且今天还没弹过」才返回非空列表（面板内容含今日更新 + 以前更新未看）；
     * 空列表 = 不弹面板。
     */
    suspend fun startupTodayUpdates(): List<com.ican.tvplay.data.FavoriteUpdateItem> {
        runCatching { checker.check(force = false) }
        if (!settings.favoriteUpdateStartupPopup.value) return emptyList()
        val today = com.ican.tvplay.data.FavoriteUpdates.todayKey()
        if (settings.favoriteUpdatePopupDate() == today) return emptyList()
        val favorites = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            favoriteDao.getAll()
        }
        val histories = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            historyDao.observeAll().first()
        }
        val all = com.ican.tvplay.data.FavoriteUpdates.compute(favorites, histories)
        return if (all.any { it.updatedToday }) all else emptyList()
    }

    /** 记录今天已弹过面板（每天只弹一次） */
    fun markPopupShown() {
        settings.setFavoriteUpdatePopupDate(com.ican.tvplay.data.FavoriteUpdates.todayKey())
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
    val themeColor: StateFlow<Int> = settings.themeColor
    val enableBlur: StateFlow<Boolean> = settings.enableBlur
    val bottomBarBlurEnabled: StateFlow<Boolean> = settings.bottomBarBlurEnabled
    val topBarBlurEnabled: StateFlow<Boolean> = settings.topBarBlurEnabled
    val enablePredictiveBack: StateFlow<Boolean> = settings.enablePredictiveBack
    val homeLayoutSize: StateFlow<com.ican.tvplay.data.HomeLayoutSize> = settings.homeLayoutSize
    val playerBackgroundPlay: StateFlow<Int> = settings.playerBackgroundPlay
    val configUrl: StateFlow<String> = settings.configUrl
    val favoriteUpdateCheckEnabled: StateFlow<Boolean> = settings.favoriteUpdateCheckEnabled
    val favoriteUpdateIntervalHours: StateFlow<Int> = settings.favoriteUpdateIntervalHours
    val favoriteUpdateStartupPopup: StateFlow<Boolean> = settings.favoriteUpdateStartupPopup

    /** 当前站源名；未加载成功时为「未配置」 */
    val siteName: StateFlow<String> = videoRepository.currentSite
        .map { it?.site?.name?.takeIf { n -> n.isNotBlank() } ?: "未配置" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "未配置")

    /** 当前站源 key（用于站点选择对话框高亮） */
    val currentSiteKey: StateFlow<String> = videoRepository.currentSite
        .map { it?.site?.key.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
    fun setThemeColor(color: Int) = settings.setThemeColor(color)
    fun setEnableBlur(enabled: Boolean) = settings.setEnableBlur(enabled)
    fun setBottomBarBlurEnabled(enabled: Boolean) = settings.setBottomBarBlurEnabled(enabled)
    fun setTopBarBlurEnabled(enabled: Boolean) = settings.setTopBarBlurEnabled(enabled)
    fun setEnablePredictiveBack(enabled: Boolean) = settings.setEnablePredictiveBack(enabled)
    fun setHomeLayoutSize(size: com.ican.tvplay.data.HomeLayoutSize) =
        settings.setHomeLayoutSize(size)
    fun setPlayerBackgroundPlay(mode: Int) = settings.setPlayerBackgroundPlay(mode)
    fun setFavoriteUpdateCheckEnabled(enabled: Boolean) =
        settings.setFavoriteUpdateCheckEnabled(enabled)
    fun setFavoriteUpdateIntervalHours(hours: Int) = settings.setFavoriteUpdateIntervalHours(hours)
    fun setFavoriteUpdateStartupPopup(enabled: Boolean) =
        settings.setFavoriteUpdateStartupPopup(enabled)

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

    /**
     * 恢复备份成功后免重启生效（fongmi SettingFragment 恢复成功后 initConfig() 同款）：
     * 重读全部 SharedPreferences 设置到 StateFlow，并重载接口配置/站点/分类，预热首页与 jar。
     * 收藏/历史由 Room Flow 自动刷新。
     */
    fun applyRestoredBackup() {
        settings.reloadFromPrefs()
        viewModelScope.launch {
            videoRepository.reload()
            launch { videoRepository.prewarmHome() }
            launch { videoRepository.prewarmJars() }
        }
    }
}
