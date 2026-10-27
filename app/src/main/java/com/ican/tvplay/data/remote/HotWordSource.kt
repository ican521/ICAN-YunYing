package com.ican.tvplay.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "HotWord"

/** 缓存体结构：与旧 360 榜单同构（data[].title），历史缓存仍可被 [HotWordSource.parse] 解析 */
@Serializable
private data class HotRank(val data: List<HotItem> = emptyList())

@Serializable
private data class HotItem(val title: String = "")

/** 豆瓣搜索接口返回（电影 / 剧集共用） */
@Serializable
private data class DoubanList(val subjects: List<DoubanSubject> = emptyList())

@Serializable
private data class DoubanSubject(val title: String = "")

/** 猫眼「正在热映」列表 */
@Serializable
private data class MaoyanList(val movieList: List<MaoyanMovie> = emptyList())

@Serializable
private data class MaoyanMovie(val nm: String = "")

/**
 * 搜索页热搜词来源：豆瓣在播热门剧集 + 豆瓣热门电影榜 + 猫眼正在热映混合。
 * 三类来源并行抓取，按 5 / 4 / 3 配额合并去重；某类失败时由其余来源补足 12 条。
 * 结果编码为旧 360 榜单同构的缓存体（data[].title），先读缓存立即展示，刷新成功后回写。
 */
object HotWordSource {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    /** 剧集：热门 + 推荐序（当下在播为主） */
    private const val DOUBAN_TV_URL =
        "https://movie.douban.com/j/search_subjects?type=tv&tag=%E7%83%AD%E9%97%A8" +
            "&sort=recommend&page_limit=12&page_start=0"

    /** 电影：热门 + 排行序（知名度较高） */
    private const val DOUBAN_MOVIE_URL =
        "https://movie.douban.com/j/search_subjects?type=movie&tag=%E7%83%AD%E9%97%A8" +
            "&sort=rank&page_limit=12&page_start=0"

    private const val DOUBAN_REFERER = "https://movie.douban.com/"
    private const val MAOYAN_URL = "https://m.maoyan.com/ajax/movieOnInfoList"
    private const val MAOYAN_REFERER = "https://m.maoyan.com/"

    /** 每次最多采纳的热搜词条数 */
    const val LIMIT = 12

    /** 三类来源的初始配额 */
    private const val TV_QUOTA = 5
    private const val MOVIE_QUOTA = 4
    private const val MAOYAN_QUOTA = 3

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

    /** 并行抓取三类来源并合并为缓存体；全部失败返回 null（上层保留现有缓存） */
    suspend fun fetch(): String? = withContext(Dispatchers.IO) {
        val merged = coroutineScope {
            val tvDeferred = async { fetchDoubanTv() }
            val movieDeferred = async { fetchDoubanMovie() }
            val maoyanDeferred = async { fetchMaoyan() }
            merge(tvDeferred.await(), movieDeferred.await(), maoyanDeferred.await())
        }
        merged.takeIf { it.isNotEmpty() }
            ?.let { json.encodeToString(HotRank(it.map { title -> HotItem(title) })) }
    }

    /** 豆瓣剧集热门榜标题 */
    private suspend fun fetchDoubanTv(): List<String> = fetchJson(DOUBAN_TV_URL, DOUBAN_REFERER) { body ->
        json.decodeFromString<DoubanList>(body).subjects.map { it.title }
    }

    /** 豆瓣电影热门榜标题 */
    private suspend fun fetchDoubanMovie(): List<String> = fetchJson(DOUBAN_MOVIE_URL, DOUBAN_REFERER) { body ->
        json.decodeFromString<DoubanList>(body).subjects.map { it.title }
    }

    /** 猫眼正在热映片名 */
    private suspend fun fetchMaoyan(): List<String> = fetchJson(MAOYAN_URL, MAOYAN_REFERER) { body ->
        json.decodeFromString<MaoyanList>(body).movieList.map { it.nm }
    }

    /** 单接口抓取 + 解析；失败记日志并返回空列表（不阻断其余来源） */
    private suspend fun fetchJson(
        url: String,
        referer: String,
        decode: (String) -> List<String>,
    ): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", referer)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                decode(response.body?.string().orEmpty())
            }
        }.onFailure { Log.e(TAG, "fetch hot word fail: $url", it) }.getOrDefault(emptyList())
    }

    /**
     * 先按配额合并（剧集 5 / 电影 4 / 猫眼 3），不足 12 条时依次用各来源剩余词补位；
     * 去重、去空后截断到 [LIMIT]。
     */
    private fun merge(tv: List<String>, movie: List<String>, maoyan: List<String>): List<String> {
        val primary = tv.take(TV_QUOTA) + movie.take(MOVIE_QUOTA) + maoyan.take(MAOYAN_QUOTA)
        val extra = tv.drop(TV_QUOTA) + movie.drop(MOVIE_QUOTA) + maoyan.drop(MAOYAN_QUOTA)
        return (primary + extra)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(LIMIT)
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
