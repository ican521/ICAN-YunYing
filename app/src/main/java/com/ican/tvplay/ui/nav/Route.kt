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

    /**
     * 搜索页（搜索入口与首页热词/卡片直达结果共用同一页）：
     * initialQuery 为空 → 历史 + 推荐态；非空 → 直接进入跨站源结果态
     */
    @Parcelize
    @Serializable
    data class Search(val initialQuery: String = "") : Route

    @Parcelize
    @Serializable
    data object History : Route

    /** 追番更新页：收藏页入口进入，分「今日更新 / 待看更新」两组 */
    @Parcelize
    @Serializable
    data object Updates : Route

    /** 更新提醒配置页：为收藏的视频设置每周更新日 */
    @Parcelize
    @Serializable
    data object UpdateSchedule : Route
}
