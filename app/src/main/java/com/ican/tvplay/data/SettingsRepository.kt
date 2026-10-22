package com.ican.tvplay.data

import android.content.Context
import com.ican.tvplay.data.remote.TvBoxSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 主题模式：跟随系统 / 浅色 / 深色 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 首页网格布局大小：小（手机3列/平板~7列）/ 中（手机2列/平板~5列）/ 大（仅平板可选，~3列） */
enum class HomeLayoutSize { SMALL, MEDIUM, LARGE }

/** 后台播放模式（fongmi「后台播放」三态） */
const val BACKGROUND_OFF = 0 // 关闭：退出播放页即停止（默认）
const val BACKGROUND_ON = 1  // 开启：后台续播 + 通知栏控制
const val BACKGROUND_PIP = 2 // 画中画：开启基础上，全屏播放切后台自动进 PiP

/**
 * 应用设置。初始骨架阶段使用 SharedPreferences 持久化，
 * 后续新增设置项可继续在此扩展。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tvplay_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    /** 主题色板种子色，0 表示默认（品牌蓝） */
    private val _themeColor = MutableStateFlow(prefs.getInt(KEY_THEME_COLOR, 0))
    val themeColor: StateFlow<Int> = _themeColor.asStateFlow()

    fun setThemeColor(color: Int) {
        prefs.edit().putInt(KEY_THEME_COLOR, color).apply()
        _themeColor.value = color
    }

    /** 全局模糊（液态玻璃底栏），默认关闭 */
    private val _enableBlur = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_BLUR, false))
    val enableBlur: StateFlow<Boolean> = _enableBlur.asStateFlow()

    /**
     * 液态玻璃开关。与底部毛玻璃互斥：打开液态玻璃时自动关闭底部毛玻璃；
     * 关闭液态玻璃不影响底部毛玻璃（允许两个都关 = 纯色胶囊）。
     */
    fun setEnableBlur(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_BLUR, enabled).apply()
        _enableBlur.value = enabled
        if (enabled && _bottomBarBlurEnabled.value) {
            prefs.edit().putBoolean(KEY_BOTTOM_BAR_BLUR, false).apply()
            _bottomBarBlurEnabled.value = false
        }
    }

    /** 底部胶囊毛玻璃（与顶栏同款 textureBlur，独立于液态玻璃），默认关闭；与液态玻璃互斥 */
    private val _bottomBarBlurEnabled = MutableStateFlow(prefs.getBoolean(KEY_BOTTOM_BAR_BLUR, false))
    val bottomBarBlurEnabled: StateFlow<Boolean> = _bottomBarBlurEnabled.asStateFlow()

    /** 底部毛玻璃开关。与液态玻璃互斥：打开时自动关闭液态玻璃；关闭不影响液态玻璃 */
    fun setBottomBarBlurEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BOTTOM_BAR_BLUR, enabled).apply()
        _bottomBarBlurEnabled.value = enabled
        if (enabled && _enableBlur.value) {
            prefs.edit().putBoolean(KEY_ENABLE_BLUR, false).apply()
            _enableBlur.value = false
        }
    }

    /** 顶栏毛玻璃（独立于全局模糊开关，互不影响），默认开启 */
    private val _topBarBlurEnabled = MutableStateFlow(prefs.getBoolean(KEY_TOP_BAR_BLUR, true))
    val topBarBlurEnabled: StateFlow<Boolean> = _topBarBlurEnabled.asStateFlow()

    fun setTopBarBlurEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TOP_BAR_BLUR, enabled).apply()
        _topBarBlurEnabled.value = enabled
    }

    /** 预测性返回手势（KernelSU 同款，默认关闭；切换后立即生效，需 recreate Activity） */
    private val _enablePredictiveBack = MutableStateFlow(prefs.getBoolean(KEY_PREDICTIVE_BACK, false))
    val enablePredictiveBack: StateFlow<Boolean> = _enablePredictiveBack.asStateFlow()

    fun setEnablePredictiveBack(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREDICTIVE_BACK, enabled).apply()
        _enablePredictiveBack.value = enabled
    }

    /** 首页网格布局大小（默认中） */
    private val _homeLayoutSize = MutableStateFlow(
        runCatching {
            HomeLayoutSize.valueOf(
                prefs.getString(KEY_HOME_LAYOUT_SIZE, null) ?: HomeLayoutSize.MEDIUM.name,
            )
        }.getOrDefault(HomeLayoutSize.MEDIUM),
    )
    val homeLayoutSize: StateFlow<HomeLayoutSize> = _homeLayoutSize.asStateFlow()

    fun setHomeLayoutSize(size: HomeLayoutSize) {
        prefs.edit().putString(KEY_HOME_LAYOUT_SIZE, size.name).apply()
        _homeLayoutSize.value = size
    }

    /** 导入配置的 URL 链接（仅保存，不解析） */
    private val _configUrl = MutableStateFlow(prefs.getString(KEY_CONFIG_URL, null).orEmpty())
    val configUrl: StateFlow<String> = _configUrl.asStateFlow()

    fun setConfigUrl(url: String) {
        prefs.edit().putString(KEY_CONFIG_URL, url).apply()
        _configUrl.value = url
    }

    /** 用户手动选择的站点 key；空表示按默认策略自动选择 */
    private val _siteKey = MutableStateFlow(prefs.getString(KEY_SITE_KEY, null).orEmpty())
    val siteKey: StateFlow<String> = _siteKey.asStateFlow()

    fun setSiteKey(key: String) {
        prefs.edit().putString(KEY_SITE_KEY, key).apply()
        _siteKey.value = key
    }

    // ---- 站源开关（fongmi Site.searchable/changeable 同款；0=配置锁定关，1=开，2=用户关） ----

    private fun effectiveFlag(configValue: Int, override: Int): Boolean {
        if (configValue == 0) return false          // 配置不允许该能力，锁定关
        if (override != 0) return override == 1     // 用户覆盖：1=开 2=关
        return true                                  // 默认开（配置 1/2 都视为默认可用）
    }

    /** 站内是否可搜索（含本地 override 生效后的值） */
    fun isSiteSearchable(site: TvBoxSite): Boolean =
        effectiveFlag(site.searchable, prefs.getInt(KEY_SITE_SEARCHABLE_PREFIX + site.key, 0))

    /** 播放失败时是否可自动换源 */
    fun isSiteChangeable(site: TvBoxSite): Boolean =
        effectiveFlag(site.changeable, prefs.getInt(KEY_SITE_CHANGEABLE_PREFIX + site.key, 0))

    /** 切换 searchable override：配置为0（锁定）时不变，返回 false */
    fun toggleSiteSearchable(site: TvBoxSite): Boolean {
        if (site.searchable == 0) return false
        val key = KEY_SITE_SEARCHABLE_PREFIX + site.key
        val next = if (isSiteSearchable(site)) 2 else 1
        prefs.edit().putInt(key, next).apply()
        _siteToggleVersion.value += 1
        return next == 1
    }

    /** 切换 changeable override：配置为0（锁定）时不变，返回 false */
    fun toggleSiteChangeable(site: TvBoxSite): Boolean {
        if (site.changeable == 0) return false
        val key = KEY_SITE_CHANGEABLE_PREFIX + site.key
        val next = if (isSiteChangeable(site)) 2 else 1
        prefs.edit().putInt(key, next).apply()
        _siteToggleVersion.value += 1
        return next == 1
    }

    /** 每次开关切换自增：供弹窗/UI 触发重组刷新 */
    private val _siteToggleVersion = MutableStateFlow(0)
    val siteToggleVersion: StateFlow<Int> = _siteToggleVersion.asStateFlow()

    // ---- 播放器设置 ----

    /** 倍速（0.5x - 3.0x） */
    private val _playerSpeed = MutableStateFlow(prefs.getFloat(KEY_PLAYER_SPEED, 1.0f))
    val playerSpeed: StateFlow<Float> = _playerSpeed.asStateFlow()

    fun setPlayerSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_PLAYER_SPEED, speed).apply()
        _playerSpeed.value = speed
    }

    /** 画面缩放模式名（Players.ScaleMode） */
    private val _playerScale = MutableStateFlow(prefs.getString(KEY_PLAYER_SCALE, null).orEmpty())
    val playerScale: StateFlow<String> = _playerScale.asStateFlow()

    fun setPlayerScale(scale: String) {
        prefs.edit().putString(KEY_PLAYER_SCALE, scale).apply()
        _playerScale.value = scale
    }

    /** 播放内核：EXO / MPV（默认 EXO，仿 fongmi PlayerSetting.player_engine） */
    private val _playerEngine = MutableStateFlow(prefs.getString(KEY_PLAYER_ENGINE, null).orEmpty())
    val playerEngine: StateFlow<String> = _playerEngine.asStateFlow()

    fun setPlayerEngine(engine: String) {
        prefs.edit().putString(KEY_PLAYER_ENGINE, engine).apply()
        _playerEngine.value = engine
    }

    /** 缓冲档位：1/2/3（默认缓冲时长的倍率，仿 fongmi PlayerSetting.getBuffer） */
    private val _playerBuffer = MutableStateFlow(prefs.getInt(KEY_PLAYER_BUFFER, 1))
    val playerBuffer: StateFlow<Int> = _playerBuffer.asStateFlow()

    fun setPlayerBuffer(tier: Int) {
        prefs.edit().putInt(KEY_PLAYER_BUFFER, tier).apply()
        _playerBuffer.value = tier
    }

    /** 后台播放（fongmi 同款三态）：关闭 / 开启（后台续播+通知栏） / 画中画 */
    private val _playerBackgroundPlay = MutableStateFlow(
        prefs.getInt(KEY_PLAYER_BACKGROUND, BACKGROUND_OFF),
    )
    val playerBackgroundPlay: StateFlow<Int> = _playerBackgroundPlay.asStateFlow()

    fun setPlayerBackgroundPlay(mode: Int) {
        prefs.edit().putInt(KEY_PLAYER_BACKGROUND, mode).apply()
        _playerBackgroundPlay.value = mode
    }

    /** 收藏同步模式（fongmi Setting.sync_mode）：0=双向 1=上传 2=下载 */
    private val _syncMode = MutableStateFlow(prefs.getInt(KEY_SYNC_MODE, 0))
    val syncMode: StateFlow<Int> = _syncMode.asStateFlow()

    fun setSyncMode(mode: Int) {
        prefs.edit().putInt(KEY_SYNC_MODE, mode).apply()
        _syncMode.value = mode
    }

    // ---- 搜索历史 ----

    private val _searchHistory = MutableStateFlow(
        runCatching {
            prefs.getStringSet(KEY_SEARCH_HISTORY, emptySet()).orEmpty().toList()
        }.getOrDefault(emptyList()),
    )
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    fun addSearchHistory(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val updated = (_searchHistory.value.filter { it != q } + q).take(MAX_SEARCH_HISTORY)
        prefs.edit().putStringSet(KEY_SEARCH_HISTORY, updated.toSet()).apply()
        _searchHistory.value = updated
    }

    fun clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
        _searchHistory.value = emptyList()
    }

    fun removeSearchHistory(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val updated = _searchHistory.value.filter { it != q }
        prefs.edit().putStringSet(KEY_SEARCH_HISTORY, updated.toSet()).apply()
        _searchHistory.value = updated
    }

    // ---- 热搜词缓存（fongmi Setting.hot：先读缓存立即展示，网络刷新成功后回写）----

    private val _hotWordsJson = MutableStateFlow(prefs.getString(KEY_HOT, null).orEmpty())
    val hotWordsJson: StateFlow<String> = _hotWordsJson.asStateFlow()

    fun setHotWordsJson(hot: String) {
        if (hot.isBlank()) return
        prefs.edit().putString(KEY_HOT, hot).apply()
        _hotWordsJson.value = hot
    }

    /**
     * 从 SharedPreferences 重新读取全部设置到 StateFlow（恢复备份后调用，免重启生效，
     * 对齐 fongmi SettingFragment 恢复成功后 initConfig() 的重载语义）。
     */
    fun reloadFromPrefs() {
        _themeMode.value = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        _themeColor.value = prefs.getInt(KEY_THEME_COLOR, 0)
        _enableBlur.value = prefs.getBoolean(KEY_ENABLE_BLUR, false)
        _bottomBarBlurEnabled.value = prefs.getBoolean(KEY_BOTTOM_BAR_BLUR, false)
        _topBarBlurEnabled.value = prefs.getBoolean(KEY_TOP_BAR_BLUR, true)
        _enablePredictiveBack.value = prefs.getBoolean(KEY_PREDICTIVE_BACK, false)
        _homeLayoutSize.value = runCatching {
            HomeLayoutSize.valueOf(
                prefs.getString(KEY_HOME_LAYOUT_SIZE, null) ?: HomeLayoutSize.MEDIUM.name,
            )
        }.getOrDefault(HomeLayoutSize.MEDIUM)
        _configUrl.value = prefs.getString(KEY_CONFIG_URL, null).orEmpty()
        _siteKey.value = prefs.getString(KEY_SITE_KEY, null).orEmpty()
        _playerSpeed.value = prefs.getFloat(KEY_PLAYER_SPEED, 1.0f)
        _playerScale.value = prefs.getString(KEY_PLAYER_SCALE, null).orEmpty()
        _playerEngine.value = prefs.getString(KEY_PLAYER_ENGINE, null).orEmpty()
        _playerBuffer.value = prefs.getInt(KEY_PLAYER_BUFFER, 1)
        _playerBackgroundPlay.value = prefs.getInt(KEY_PLAYER_BACKGROUND, BACKGROUND_OFF)
        _syncMode.value = prefs.getInt(KEY_SYNC_MODE, 0)
        _searchHistory.value = runCatching {
            prefs.getStringSet(KEY_SEARCH_HISTORY, emptySet()).orEmpty().toList()
        }.getOrDefault(emptyList())
        _hotWordsJson.value = prefs.getString(KEY_HOT, null).orEmpty()
        _siteToggleVersion.value += 1
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_THEME_COLOR = "theme_color"
        const val KEY_ENABLE_BLUR = "enable_blur"
        const val KEY_BOTTOM_BAR_BLUR = "bottom_bar_blur"
        const val KEY_TOP_BAR_BLUR = "top_bar_blur"
        const val KEY_PREDICTIVE_BACK = "enable_predictive_back"
        const val KEY_HOME_LAYOUT_SIZE = "home_layout_size"
        const val KEY_CONFIG_URL = "config_url"
        const val KEY_SITE_KEY = "site_key"
        const val KEY_SITE_SEARCHABLE_PREFIX = "site_searchable_"
        const val KEY_SITE_CHANGEABLE_PREFIX = "site_changeable_"
        const val KEY_PLAYER_SPEED = "player_speed"
        const val KEY_PLAYER_SCALE = "player_scale"
        const val KEY_PLAYER_ENGINE = "player_engine"
        const val KEY_PLAYER_BUFFER = "player_buffer"
        const val KEY_PLAYER_BACKGROUND = "player_background"
        const val KEY_SYNC_MODE = "sync_mode"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val KEY_HOT = "hot"
        const val MAX_SEARCH_HISTORY = 10
    }
}
