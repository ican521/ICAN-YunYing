package com.ican.tvplay.data.model

/** 视频分类 */
data class VideoCategory(
    val id: String,
    val name: String,
)

/** 单集（电影通常只有一个「正片」） */
data class Episode(
    val index: Int,
    val title: String,
    val playUrl: String,
)

/** 一条播放线路（vod_play_from 以 $$$ 分隔的其中一条） */
data class PlayLine(
    /** 线路标识（传给 spider playerContent 的 flag） */
    val flag: String,
    val episodes: List<Episode>,
)

/** 视频条目 */
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
)
