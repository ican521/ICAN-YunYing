package com.ican.tvplay.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "SuggestWord"

/** 爱奇艺搜索建议响应：与 fongmi 一致（data[].name） */
@Serializable
private data class SuggestResult(val data: List<SuggestItem> = emptyList())

@Serializable
private data class SuggestItem(val name: String = "")

/**
 * 搜索页「输入关键词时的推荐」来源：爱奇艺搜索建议接口（与 fongmi 一致）。
 * 空关键词不走此源（由 HotWordSource 提供热搜）；输入关键词时实时返回关联词列表。
 */
object SuggestWordSource {

    private const val URL = "https://suggest.video.iqiyi.com/?if=mobile&key="
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private const val LIMIT = 12

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .dns(FallbackDns)
        .connectionPool(VodApiClient.sharedPool)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(keyword: String): List<String> = withContext(Dispatchers.IO) {
        val key = keyword.trim()
        if (key.isEmpty()) return@withContext emptyList()
        runCatching {
            val request = Request.Builder()
                .url(URL + URLEncoder.encode(key, "UTF-8"))
                .header("User-Agent", UA)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                json.decodeFromString<SuggestResult>(body).data
                    .map { it.name.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(LIMIT)
            }
        }.onFailure { Log.e(TAG, "fetch suggest fail for: $key", it) }.getOrDefault(emptyList())
    }
}
