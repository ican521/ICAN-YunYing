package com.ican.tvplay.data

import com.ican.tvplay.data.model.Episode
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.data.model.VideoCategory
import com.ican.tvplay.data.remote.TvBoxSite
import com.ican.tvplay.data.remote.VodApiClient
import com.ican.tvplay.data.remote.VodItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 视频仓库：从 TVBox 采集站接口拉取真实数据。
 * 数据来源：用户在设置页导入的 config_url，解析出第一个 provide/vod 类型站点作为当前源。
 * 配置未导入 / 加载失败时返回空数据，UI 显示空态。
 */
class VideoRepository(
    private val settingsRepository: SettingsRepository,
    private val apiClient: VodApiClient = VodApiClient(),
) {

    /** 当前可用站点；null 表示未导入配置或加载失败 */
    private val _currentSite = MutableStateFlow<TvBoxSite?>(null)
    val currentSite: StateFlow<TvBoxSite?> = _currentSite.asStateFlow()

    /** 当前分类（ac=class 返回） */
    private val _categories = MutableStateFlow<List<VideoCategory>>(emptyList())
    val categoriesFlow: StateFlow<List<VideoCategory>> = _categories.asStateFlow()

    /** 是否已就绪（已加载到站点） */
    val isReady: Boolean get() = _currentSite.value != null

    private val loadMutex = Mutex()

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
            return
        }
        val site = apiClient.loadHomeSite(url)
        _currentSite.value = site
        _categories.value = if (site != null) {
            apiClient.homeClasses(site).map { VideoCategory(id = it.typeId, name = it.typeName) }
        } else {
            emptyList()
        }
    }

    fun getCategories(): List<VideoCategory> = _categories.value

    /**
     * 首页分区：categoryId == null 表示「全部」→ 返回所有分类各加载首页视频；
     * 否则只返回选中分类的视频列表（单 section）。
     */
    suspend fun getHomeSections(categoryId: String?): List<Pair<VideoCategory, List<Video>>> {
        ensureLoaded()
        val site = _currentSite.value ?: return emptyList()
        val cats = _categories.value
        if (cats.isEmpty()) return emptyList()

        return if (categoryId == null) {
            // 全部分类各取首页数据
            cats.map { cat ->
                cat to apiClient.categoryVideos(site, cat.id, page = 1)
                    .map { it.toVideo(cat) }
            }
        } else {
            val cat = cats.firstOrNull { it.id == categoryId } ?: return emptyList()
            listOf(cat to apiClient.categoryVideos(site, cat.id, page = 1).map { it.toVideo(cat) })
        }
    }

    suspend fun getVideos(categoryId: String): List<Video> {
        ensureLoaded()
        val site = _currentSite.value ?: return emptyList()
        val cat = _categories.value.firstOrNull { it.id == categoryId } ?: return emptyList()
        return apiClient.categoryVideos(site, cat.id, page = 1).map { it.toVideo(cat) }
    }

    /** 详情：拉取 ac=detail 补全选集 / 播放地址等 */
    suspend fun getVideo(videoId: String): Video? {
        ensureLoaded()
        val site = _currentSite.value ?: return null
        val item = apiClient.detail(site, videoId) ?: return null
        // 详情接口可能不带 type_name，回退用「未知」
        val catName = item.typeName.ifBlank { "未知" }
        return item.toVideo(VideoCategory(id = "", name = catName), withEpisodes = true)
    }

    suspend fun search(query: String): List<Video> {
        ensureLoaded()
        val key = query.trim()
        if (key.isEmpty()) return emptyList()
        val site = _currentSite.value ?: return emptyList()
        return apiClient.search(site, key).map {
            it.toVideo(VideoCategory(id = "", name = it.typeName.ifBlank { "未知" }))
        }
    }

    /** 把采集站返回的 VodItem 映射为项目 Video 模型 */
    private fun VodItem.toVideo(category: VideoCategory, withEpisodes: Boolean = false): Video {
        val episodes = if (withEpisodes && vodPlayUrl.isNotBlank()) {
            parseEpisodes(vodPlayFrom, vodPlayUrl)
        } else {
            emptyList()
        }
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
            episodes = episodes,
        )
    }

    /**
     * 解析 vod_play_url：
     * 多源用 $$$ 分隔（与 vod_play_from 一一对应），单源内用 # 分隔集数，
     * 每集格式 `第01集$https://...m3u8`。
     * 这里仅取第一个源的选集列表。
     */
    private fun parseEpisodes(playFrom: String, playUrl: String): List<Episode> {
        val firstSource = playUrl.split("$$$").firstOrNull().orEmpty()
        return firstSource.split("#")
            .mapIndexedNotNull { index, segment ->
                val parts = segment.split("$")
                if (parts.size < 2) return@mapIndexedNotNull null
                val title = parts[0].trim().ifBlank { "第${index + 1}集" }
                val url = parts.subList(1, parts.size).joinToString("$").trim()
                if (url.isEmpty()) return@mapIndexedNotNull null
                Episode(index = index, title = title, playUrl = url)
            }
    }
}
