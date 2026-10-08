package com.ican.tvplay.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "JxParser"

/**
 * 解析 Fongmi/TVBox playerContent 返回的 parse=1 / jx=1 模式播放地址。
 * 即 spider 返回的 url 不是真实 m3u8/mp4，而是一个「解析页」URL（如 https://jx.xxx.com/?url=xxx），
 * 需要 GET 该页面 HTML，从中提取真实流地址。
 *
 * 参考 Fongmi: D:\fongmi-tv-mycustom\app\src\main\java\com\fongmi\android\tv\api\JxParser.java
 */
object JxParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** 常见真实流地址特征 */
    private val REAL_STREAM_HINTS = listOf(
        ".m3u8",
        ".m4v",
        ".mp4",
        ".flv",
        ".mpd",   // DASH
        ".ts",
        "video",
        ".php?",
    )

    /** 从 HTML 中提取真实视频地址的正则（按优先级排序） */
    private val STREAM_PATTERNS = listOf(
        // window.url = "https://xxx.m3u8"  (Fongmi/官方 jx 最常见)
        Regex("""window\.url\s*=\s*["']([^"']+?\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
        Regex("""window\.url\s*=\s*["']([^"']+?\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE),
        // player.src = "..."
        Regex("""player\.src\s*=\s*["']([^"']+?\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
        Regex("""player\.src\s*=\s*["']([^"']+?\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE),
        // <source src="...m3u8...">
        Regex("""<source[^>]+src\s*=\s*["']([^"']+?\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
        Regex("""<source[^>]+src\s*=\s*["']([^"']+?\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE),
        // var url = '...m3u8...'
        Regex("""var\s+url\s*=\s*["']([^"']+?\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
        Regex("""var\s+url\s*=\s*["']([^"']+?\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE),
        // http[s]://xxx.m3u8  全文搜索（兜底）
        Regex("""https?://[^\s"'<>]+?\.m3u8[^\s"'<>]*""", RegexOption.IGNORE_CASE),
        Regex("""https?://[^\s"'<>]+?\.mp4[^\s"'<>]*""", RegexOption.IGNORE_CASE),
        Regex("""https?://[^\s"'<>]+?\.flv[^\s"'<>]*""", RegexOption.IGNORE_CASE),
        Regex("""https?://[^\s"'<>]+?\.mpd[^\s"'<>]*""", RegexOption.IGNORE_CASE),
    )

    /** JS 提取 base64 再解码的模式 */
    private val BASE64_PATTERN = Regex(
        """atob\s*\(\s*["']([A-Za-z0-9+/=]{40,})["']\s*\)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 判断 playerContent 返回的 url 是否需要 jx 解析：
     * - parse = 1 / 2 → 强制解析
     * - 或 url 不含任何真实流特征（.m3u8/.mp4/.flv/.mpd）→ 可能是解析页
     */
    fun needsParse(url: String, parseFlag: Int?, jxFlag: Int?): Boolean {
        if (parseFlag == 1 || parseFlag == 2 || jxFlag == 1) return true
        val lower = url.lowercase()
        return REAL_STREAM_HINTS.none { lower.contains(it) } && url.startsWith("http")
    }

    /**
     * 解析真实流地址。
     * @param parseUrl playerContent 返回的 url（可能是 jx 解析页）
     * @param headers  playerContent 返回的 header（用于请求解析页）
     * @return 真实流地址；失败返回原 url
     */
    suspend fun resolve(
        parseUrl: String,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "resolve parseUrl=$parseUrl")
            val requestBuilder = Request.Builder().url(parseUrl)
            // 加 Referer/Origin（很多 jx 站做了防盗链）
            val baseHost = runCatching {
                val u = java.net.URL(parseUrl)
                "${u.protocol}://${u.host}"
            }.getOrDefault("")
            val mergedHeaders = mutableMapOf<String, String>(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile",
            )
            if (baseHost.isNotBlank()) {
                mergedHeaders["Referer"] = "$baseHost/"
                mergedHeaders["Origin"] = baseHost
            }
            mergedHeaders.putAll(headers)
            mergedHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

            client.newCall(requestBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "jx GET failed HTTP ${resp.code}")
                    return@runCatching parseUrl
                }
                val html = resp.body?.string().orEmpty()
                if (html.isEmpty()) {
                    Log.w(TAG, "jx empty body")
                    return@runCatching parseUrl
                }

                // 先尝试各正则模式
                for (pattern in STREAM_PATTERNS) {
                    val m = pattern.find(html)
                    if (m != null) {
                        val candidate = m.groupValues.getOrNull(1)?.trim().orEmpty()
                        if (candidate.isNotEmpty()) {
                            Log.d(TAG, "jx resolved via pattern=$pattern candidate=$candidate")
                            return@runCatching candidate
                        }
                    }
                }

                // 再试 base64 解码（atob("xxx") → 真实地址）
                val b64m = BASE64_PATTERN.find(html)
                if (b64m != null) {
                    val b64 = b64m.groupValues[1]
                    runCatching {
                        val decoded = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                            .toString(Charsets.UTF_8)
                        for (pattern in STREAM_PATTERNS) {
                            val m = pattern.find(decoded)
                            if (m != null) {
                                val candidate = m.groupValues.getOrNull(1)?.trim().orEmpty()
                                if (candidate.isNotEmpty()) {
                                    Log.d(TAG, "jx resolved via base64=$candidate")
                                    return@runCatching candidate
                                }
                            }
                        }
                        // 整个解码结果就是 URL
                        if (decoded.startsWith("http")) {
                            Log.d(TAG, "jx resolved base64-decoded=$decoded")
                            return@runCatching decoded.trim()
                        }
                    }.onFailure { Log.w(TAG, "jx base64 decode fail", it) }
                }

                // 最后兜底：找所有 http 开头的 url，过滤出最长/最像流的
                val allUrls = Regex("""https?://[^\s"'<>\\]+""").findAll(html)
                    .map { it.value.trim().trimEnd(',', ';', ')', ']', '}') }
                    .distinct()
                    .toList()
                Log.d(TAG, "jx allUrls count=${allUrls.size} head=${allUrls.take(3)}")

                // 优先含 m3u8 的
                allUrls.firstOrNull { it.contains(".m3u8", ignoreCase = true) }?.let {
                    Log.d(TAG, "jx fallback m3u8=$it")
                    return@runCatching it
                }
                allUrls.firstOrNull { it.contains(".mp4", ignoreCase = true) }?.let {
                    Log.d(TAG, "jx fallback mp4=$it")
                    return@runCatching it
                }
                allUrls.firstOrNull { it.contains(".flv", ignoreCase = true) }?.let {
                    Log.d(TAG, "jx fallback flv=$it")
                    return@runCatching it
                }

                Log.e(TAG, "jx FAILED to extract stream, ${allUrls.size} candidates found, returning original")
                parseUrl
            }
        }.onFailure { Log.e(TAG, "jx resolve exception", it) }.getOrDefault(parseUrl)
    }
}
