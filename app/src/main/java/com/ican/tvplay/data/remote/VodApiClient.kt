package com.ican.tvplay.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * TVBox 配置 JSON：仅取 sites 数组中第一个可用的 provide/vod 采集站。
 * 仅支持 type 缺省/0/1 的 HTTP JSON 采集站（spider jar 等不实现）。
 */
@Serializable
data class TvBoxConfig(
    val sites: List<TvBoxSite> = emptyList(),
)

@Serializable
data class TvBoxSite(
    val key: String = "",
    val name: String = "",
    val api: String = "",
    val type: Int = 0,
)

/** 分类（ac=class 返回的 class 数组元素） */
@Serializable
data class VodClass(
    @SerialName("type_id") val typeId: String = "",
    @SerialName("type_name") val typeName: String = "",
)

/** 列表项（ac=videolist 返回的 list 数组元素，字段缺省容忍） */
@Serializable
data class VodItem(
    @SerialName("vod_id") val vodId: String = "",
    @SerialName("vod_name") val vodName: String = "",
    @SerialName("vod_pic") val vodPic: String = "",
    @SerialName("vod_remarks") val vodRemarks: String = "",
    @SerialName("vod_year") val vodYear: String = "",
    @SerialName("vod_area") val vodArea: String = "",
    @SerialName("vod_content") val vodContent: String = "",
    @SerialName("vod_play_from") val vodPlayFrom: String = "",
    @SerialName("vod_play_url") val vodPlayUrl: String = "",
    @SerialName("type_name") val typeName: String = "",
)

/** 列表/分类接口返回 */
@Serializable
data class VodListResult(
    @SerialName("class") val classes: List<VodClass> = emptyList(),
    val list: List<VodItem> = emptyList(),
)

/** 详情接口返回 */
@Serializable
data class VodDetailResult(
    val list: List<VodItem> = emptyList(),
)

/** TVBox 采集站 HTTP 客户端 */
class VodApiClient(
    private val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true },
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 拉取配置 URL，返回第一个可用的 provide/vod 站点 */
    suspend fun loadHomeSite(configUrl: String): TvBoxSite? = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(configUrl)
            val config = json.decodeFromString<TvBoxConfig>(body)
            // 仅取 type=0/1（XML/JSON 采集站），本项目仅支持 JSON
            config.sites.firstOrNull { it.api.contains("provide/vod") && it.type <= 1 }
        }.getOrNull()
    }

    /** 拉取首页分类（ac=class） */
    suspend fun homeClasses(site: TvBoxSite): List<VodClass> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(site.api, mapOf("ac" to "class"))
            val body = get(url)
            json.decodeFromString<VodListResult>(body).classes
        }.getOrDefault(emptyList())
    }

    /** 拉取分类下视频列表 */
    suspend fun categoryVideos(site: TvBoxSite, typeId: String, page: Int = 1): List<VodItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildUrl(
                    site.api,
                    mapOf("ac" to "videolist", "t" to typeId, "pg" to page.toString()),
                )
                val body = get(url)
                json.decodeFromString<VodListResult>(body).list
            }.getOrDefault(emptyList())
        }

    /** 搜索（ac=videolist&wd=） */
    suspend fun search(site: TvBoxSite, keyword: String): List<VodItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildUrl(site.api, mapOf("ac" to "videolist", "wd" to keyword))
                val body = get(url)
                json.decodeFromString<VodListResult>(body).list
            }.getOrDefault(emptyList())
        }

    /** 详情（ac=detail&ids=） */
    suspend fun detail(site: TvBoxSite, vodId: String): VodItem? = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(site.api, mapOf("ac" to "detail", "ids" to vodId))
            val body = get(url)
            json.decodeFromString<VodDetailResult>(body).list.firstOrNull()
        }.getOrNull()
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val builder = StringBuilder(base)
        builder.append(if (base.contains("?")) "&" else "?")
        params.entries.forEachIndexed { index, (k, v) ->
            if (index > 0) builder.append('&')
            builder.append(k).append('=').append(java.net.URLEncoder.encode(v, "UTF-8"))
        }
        return builder.toString()
    }
}
