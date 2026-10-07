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

    /** 预测性返回：返回手势进度跟随滑入滑出转场 */
    private val _predictiveBack = MutableStateFlow(prefs.getBoolean(KEY_PREDICTIVE_BACK, true))
    val predictiveBack: StateFlow<Boolean> = _predictiveBack.asStateFlow()

    fun setPredictiveBack(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREDICTIVE_BACK, enabled).apply()
        _predictiveBack.value = enabled
    }

    /** 导入配置的 URL 链接（仅保存，不解析） */
    private val _configUrl = MutableStateFlow(prefs.getString(KEY_CONFIG_URL, null).orEmpty())
    val configUrl: StateFlow<String> = _configUrl.asStateFlow()

    fun setConfigUrl(url: String) {
        prefs.edit().putString(KEY_CONFIG_URL, url).apply()
        _configUrl.value = url
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_THEME_COLOR = "theme_color"
        const val KEY_ENABLE_BLUR = "enable_blur"
        const val KEY_PREDICTIVE_BACK = "predictive_back"
        const val KEY_CONFIG_URL = "config_url"
    }
}
