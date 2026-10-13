package com.ican.tvplay.ui.nav

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * miuix-nav 类型安全导航键（照搬 KernelSU navigation3/Routes.kt）。
 * 每个目的地都是 NavKey（data object/data class），可在返回栈中保存/恢复。
 */
@Serializable
sealed interface Route : NavKey, Parcelable {
    /** 一级页面容器（HorizontalPager：首页/收藏/设置） */
    @Parcelize
    @Serializable
    data object Main : Route

    @Parcelize
    @Serializable
    data object ColorPalette : Route

    @Parcelize
    @Serializable
    data object Search : Route

    /** 跨站源搜索结果页：query 是搜索关键词 */
    @Parcelize
    @Serializable
    data class MultiSearch(val query: String) : Route

    @Parcelize
    @Serializable
    data object History : Route

    @Parcelize
    @Serializable
    data class Player(
        val videoId: String,
        val episodeIndex: Int,
        val flag: String = "",
        val preInit: Boolean = false,
    ) : Route
}
