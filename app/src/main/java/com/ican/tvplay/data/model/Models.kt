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
)
