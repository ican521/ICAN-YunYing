package com.ican.tvplay.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "HotWord"

/** 360 影视热搜榜返回结构（对应 fongmi Word 模型，这里只取标题） */
@Serializable
private data class HotRank(val data: List<HotItem> = emptyList())

@Serializable
private data class HotItem(val title: String = "")

/**
 * 搜索页热搜词来源：360 影视热搜榜。
 * 与 fongmi VodFragment / SearchFragment 同款接口与 Referer；原始响应体交给上层缓存
 * （fongmi Setting.putHot/getHot 同款：先读缓存立即展示，网络刷新成功后回写）。
 */
object HotWordSource {

    private const val RANK_URL = "https://api.web.360kan.com/v1/rank?cat=1"
    private const val REFERER = "https://www.360kan.com/rank/general"

    /** 每次最多采纳的热搜词条数 */
    const val LIMIT = 12

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .dns(FallbackDns)
        .connectionPool(VodApiClient.sharedPool)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 拉取榜单原始响应体；失败返回 null（上层保留缓存） */
    suspend fun fetch(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(RANK_URL)
                .header("Referer", REFERER)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }.takeIf { it.isNotBlank() }
        }.onFailure { Log.e(TAG, "fetch hot rank fail", it) }.getOrNull()
    }

    /** 解析榜单响应体为前 [LIMIT] 条热搜词；解析失败返回空列表 */
    fun parse(body: String): List<String> {
        if (body.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<HotRank>(body).data
                .map { it.title.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(LIMIT)
        }.onFailure { Log.e(TAG, "parse hot rank fail", it) }.getOrDefault(emptyList())
    }
}
