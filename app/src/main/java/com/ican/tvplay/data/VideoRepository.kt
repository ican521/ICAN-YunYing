package com.ican.tvplay.data

import com.ican.tvplay.data.model.Episode
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.data.model.VideoCategory

/**
 * 视频仓库。当前为内置 Mock 数据，用于先跑通全部页面与交互；
 * 后续在此对接自定义视频源（Spider / CMS 接口），UI 层无需改动。
 */
class VideoRepository {

    fun getCategories(): List<VideoCategory> = Categories

    suspend fun getHomeSections(categoryId: String?): List<Pair<VideoCategory, List<Video>>> {
        val cats = if (categoryId == null) Categories else Categories.filter { it.id == categoryId }
        return cats.map { cat -> cat to videos.filter { it.categoryId == cat.id } }
    }

    suspend fun getVideos(categoryId: String): List<Video> =
        videos.filter { it.categoryId == categoryId }

    suspend fun getVideo(videoId: String): Video? = videos.firstOrNull { it.id == videoId }

    suspend fun search(query: String): List<Video> {
        val key = query.trim()
        if (key.isEmpty()) return emptyList()
        return videos.filter { it.title.contains(key, ignoreCase = true) }
    }

    // ---------------------------------------------------------------------
    // Mock 数据
    // ---------------------------------------------------------------------

    private companion object {

        const val SAMPLE_URL =
            "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"

        val Categories = listOf(
            VideoCategory("movie", "电影"),
            VideoCategory("tv", "剧集"),
            VideoCategory("variety", "综艺"),
            VideoCategory("anime", "动漫"),
            VideoCategory("doc", "纪录片"),
            VideoCategory("short", "短剧"),
        )

        private val TITLE_POOL = listOf(
            "星际回响", "暗夜追踪", "云海之南", "时光便利店", "霓虹猎手", "盛夏方程式",
            "迷雾档案", "逆风飞行", "长安十二时辰记", "山海异闻", "量子爱恋", "无声证人",
            "极地星尘", "风味老街", "江湖再见", "像素王国", "深海来信", "追风的人",
            "古城密码", "云端之上", "心跳节拍", "银河铁道夜", "麦田守望", "逐光少年",
            "黑白禁区", "花开半夏", "荒野直播间", "机甲纪元", "纸上人间", "潮汐物语",
            "长夜余火", "镜中孤城", "周末狂想曲", "轨道尽头", "青空下的约定", "暗河传",
            "时光胶囊", "山月不知心底事", "浮生半日", "宇宙探索编辑部日记", "风过林梢",
            "第七号房间", "流光之城", "破晓行动", "人间烟火录", "极速人生",
        )

        private val REGIONS = listOf("中国大陆", "中国香港", "美国", "日本", "英国", "韩国")
        private val TAG_POOL = listOf(
            "科幻", "悬疑", "动作", "爱情", "喜剧", "纪录片", "热血", "治愈",
            "犯罪", "冒险", "奇幻", "历史", "真人秀", "青春",
        )

        val videos: List<Video> = buildList {
            var seq = 0
            Categories.forEach { cat ->
                repeat(8) { i ->
                    val id = "${cat.id}_$i"
                    val episodeCount = when (cat.id) {
                        "movie" -> 1
                        "doc" -> 6
                        "variety" -> 12
                        "short" -> 20
                        "anime" -> 24
                        else -> 36
                    }
                    val episodes = (0 until episodeCount).map { index ->
                        Episode(
                            index = index,
                            title = if (episodeCount == 1) "正片" else "第${index + 1}集",
                            playUrl = SAMPLE_URL,
                        )
                    }
                    val titleIndex = (seq) % TITLE_POOL.size
                    seq++
                    add(
                        Video(
                            id = id,
                            title = TITLE_POOL[titleIndex],
                            cover = "https://picsum.photos/seed/ican$id/400/560",
                            categoryId = cat.id,
                            categoryName = cat.name,
                            year = 2020 + (i % 6),
                            rating = "%.1f".format(7.0 + (i % 30) / 10.0),
                            region = REGIONS[(i + cat.id.hashCode()) % REGIONS.size],
                            description = "《${TITLE_POOL[titleIndex]}》是一部${cat.name}作品，" +
                                "讲述了主人公在命运的交叉口做出抉择，并在旅途中寻得真相与自我的故事。" +
                                "画面考究、节奏紧凑，为观众带来沉浸的视听体验。",
                            tags = listOf(TAG_POOL[i % TAG_POOL.size], TAG_POOL[(i + 5) % TAG_POOL.size]),
                            episodes = episodes,
                        ),
                    )
                }
            }
        }
    }
}
