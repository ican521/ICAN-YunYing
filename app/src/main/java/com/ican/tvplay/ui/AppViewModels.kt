package com.ican.tvplay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ican.tvplay.data.AppContainer
import com.ican.tvplay.data.ThemeMode
import com.ican.tvplay.data.local.FavoriteEntity
import com.ican.tvplay.data.local.HistoryEntity
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.data.model.VideoCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** 首页：分类切换 + 各分区视频 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.videoRepository

    val categories: List<VideoCategory> = repo.getCategories()

    private val selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = selectedCategoryId.asStateFlow()

    val sections: StateFlow<List<Pair<VideoCategory, List<Video>>>> =
        selectedCategoryId
            .flatMapLatest { categoryId ->
                kotlinx.coroutines.flow.flow { emit(repo.getHomeSections(categoryId)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(id: String?) {
        selectedCategoryId.value = id
    }
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
                favoriteDao.upsert(
                    FavoriteEntity(
                        videoId = video.id,
                        title = video.title,
                        cover = video.cover,
                        categoryName = video.categoryName,
                        addedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}

/** 搜索：关键词防抖 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.videoRepository

    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query.asStateFlow()

    val results: StateFlow<List<Video>> = query
        .debounce(250)
        .mapLatest { repo.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        query.value = value
    }
}

/** 收藏 / 播放历史列表 */
class CollectionViewModel(container: AppContainer) : ViewModel() {
    private val favoriteDao = container.favoriteDao
    private val historyDao = container.historyDao

    val favorites = favoriteDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val histories = historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeFavorite(videoId: String) {
        viewModelScope.launch { favoriteDao.delete(videoId) }
    }

    fun removeHistory(videoId: String) {
        viewModelScope.launch { historyDao.delete(videoId) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyDao.clear() }
    }
}

/** 设置 */
class SettingsViewModel(container: AppContainer) : ViewModel() {
    private val settings = container.settingsRepository
    private val videoRepository = container.videoRepository

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
    val themeColor: StateFlow<Int> = settings.themeColor
    val enableBlur: StateFlow<Boolean> = settings.enableBlur
    val configUrl: StateFlow<String> = settings.configUrl

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
    fun setDynamicColor(enabled: Boolean) = settings.setDynamicColor(enabled)
    fun setThemeColor(color: Int) = settings.setThemeColor(color)
    fun setEnableBlur(enabled: Boolean) = settings.setEnableBlur(enabled)

    /** 保存配置 URL 后立即重新拉取站点与分类 */
    fun setConfigUrl(url: String) {
        settings.setConfigUrl(url)
        viewModelScope.launch { videoRepository.reload() }
    }
}
