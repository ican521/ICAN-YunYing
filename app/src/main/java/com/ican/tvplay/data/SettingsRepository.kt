package com.ican.tvplay.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 主题模式：跟随系统 / 浅色 / 深色 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

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

    /** 动态取色（仅 Android 12+ 生效，低版本忽略并回退品牌色） */
    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    /** 主题色板种子色，0 表示默认（品牌蓝） */
    private val _themeColor = MutableStateFlow(prefs.getInt(KEY_THEME_COLOR, 0))
    val themeColor: StateFlow<Int> = _themeColor.asStateFlow()

    fun setThemeColor(color: Int) {
        prefs.edit().putInt(KEY_THEME_COLOR, color).apply()
        _themeColor.value = color
    }

    /** 全局模糊（液态玻璃底栏） */
    private val _enableBlur = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_BLUR, true))
    val enableBlur: StateFlow<Boolean> = _enableBlur.asStateFlow()

    fun setEnableBlur(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_BLUR, enabled).apply()
        _enableBlur.value = enabled
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

    /** 解码内核：HARD / SOFT（ffmpeg 软解） */
    private val _playerDecode = MutableStateFlow(prefs.getString(KEY_PLAYER_DECODE, null).orEmpty())
    val playerDecode: StateFlow<String> = _playerDecode.asStateFlow()

    fun setPlayerDecode(decode: String) {
        prefs.edit().putString(KEY_PLAYER_DECODE, decode).apply()
        _playerDecode.value = decode
    }

    /** 缓冲档位：1/2/3（默认缓冲时长的倍率，仿 fongmi PlayerSetting.getBuffer） */
    private val _playerBuffer = MutableStateFlow(prefs.getInt(KEY_PLAYER_BUFFER, 1))
    val playerBuffer: StateFlow<Int> = _playerBuffer.asStateFlow()

    fun setPlayerBuffer(tier: Int) {
        prefs.edit().putInt(KEY_PLAYER_BUFFER, tier).apply()
        _playerBuffer.value = tier
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_THEME_COLOR = "theme_color"
        const val KEY_ENABLE_BLUR = "enable_blur"
        const val KEY_CONFIG_URL = "config_url"
        const val KEY_SITE_KEY = "site_key"
        const val KEY_PLAYER_SPEED = "player_speed"
        const val KEY_PLAYER_SCALE = "player_scale"
        const val KEY_PLAYER_DECODE = "player_decode"
        const val KEY_PLAYER_BUFFER = "player_buffer"
    }
}
