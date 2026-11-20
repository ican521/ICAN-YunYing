package com.ican.tvplay.data.model

import kotlinx.serialization.Serializable

/** 视频分类 */
@Serializable
data class VideoCategory(
    val id: String,
    val name: String,
    /** 采集站 class 里的 type_flag（OK猫 sortData.flag）：'1' 文件夹模式、'2' 带缩略图文件夹模式，空=普通分类 */
    val typeFlag: String = "",
)

/** 单集（电影通常只有一个「正片」） */
@Serializable
data class Episode(
    val index: Int,
    val title: String,
    val playUrl: String,
)

/** 一条播放线路（vod_play_from 以 $$$ 分隔的其中一条） */
@Serializable
data class PlayLine(
    /** 线路标识（传给 spider playerContent 的 flag） */
    val flag: String,
    val episodes: List<Episode>,
)

/** 视频条目 */
@Serializable
data class Video(
    val id: String,
    val title: String,
    val cover: String,
    val categoryId: String,
    val categoryName: String,
    val year: Int,
    val rating: String,
    val region: String,
    val description: String,
    val tags: List<String>,
    val episodes: List<Episode>,
    /** 第一源的线路标识（vod_play_from），spider 站 playerContent 需要 */
    val playFrom: String = "",
    /** 视频所属站源显示名（搜索左侧栏选中的站源，仅取 | 前名称），播放页站源行用 */
    val sourceName: String = "",
    /** 全部播放线路（vod_play_from / vod_play_url 按 $$$ 分隔完整保留） */
    val playSources: List<PlayLine> = emptyList(),
    /** 网盘配置动作标识（fongmi Vod.action）：非空表示此卡片为网盘配置入口，点击弹出配置弹窗而非进入播放页 */
    val action: String = "",
    /** 网盘文件夹标记（fongmi Vod.isFolder）：true 表示点击后进入该目录，而不是直接播放 */
    val isFolder: Boolean = false,
    /** 带缩略图的目录（vod_tag == "cover"）：点击同样进入目录，但用横版宽卡渲染（对齐 OK猫 flag '2'） */
    val isCoverFolder: Boolean = false,
    /** 条目自带卡片样式类型（fongmi Vod.style.type: rect/oval/list），空 = 无信号 */
    val cardStyleType: String = "",
    /** 条目自带宽高比（fongmi Vod.style.ratio / land / circle 折算），0 = 无信号 */
    val cardStyleRatio: Float = 0f,
)
