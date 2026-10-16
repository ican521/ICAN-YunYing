package com.ican.tvplay.util

import android.content.Context
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import org.json.JSONObject

/**
 * 海报地址解析，逻辑照搬 fongmi ImgUtil.getUrl()。
 *
 * spider / 采集站返回的 vod_pic 可能携带 fongmi 私有的请求头后缀：
 *   https://img1.doubanio.com/xxx.jpg@Referer=https://api.douban.com/@User-Agent=Mozilla/5.0 ...
 * 其中 @Headers= 后是一段 JSON，@Cookie= / @Referer= / @User-Agent= 为单值，
 * 各段以 @ 结束。必须剥离后缀并把请求头注入图片请求，否则：
 *  1. 整串脏 URL 路径非法，图床无法识别；
 *  2. 豆瓣等防盗链图床会直接返回 403/418。
 *
 * 返回值直接作为 Coil AsyncImage 的 model：
 *  - 无后缀的普通地址 / data: URI 原样返回字符串；
 *  - 带后缀的地址返回携带 NetworkHeaders 的 ImageRequest；
 *  - 空地址返回 null。
 */
fun posterModel(context: Context, raw: String?): Any? {
    val url = raw?.trim().orEmpty()
    if (url.isEmpty()) return null
    if (url.startsWith("data:")) return url

    var hasSuffix = false
    val headers = NetworkHeaders.Builder()

    fun suffixValue(marker: String): String? {
        if (!url.contains(marker)) return null
        return url.substringAfter(marker).substringBefore("@")
    }

    // @Headers={"Referer":"https://...","User-Agent":"..."}
    suffixValue("@Headers=")?.takeIf { it.isNotBlank() }?.let { json ->
        hasSuffix = true
        runCatching {
            val obj = JSONObject(json)
            for (key in obj.keys()) {
                headers.add(fixHeaderName(key), obj.optString(key))
            }
        }
    }
    suffixValue("@Cookie=")?.takeIf { it.isNotBlank() }?.let {
        hasSuffix = true
        headers.add("Cookie", it)
    }
    suffixValue("@Referer=")?.takeIf { it.isNotBlank() }?.let {
        hasSuffix = true
        headers.add("Referer", it)
    }
    suffixValue("@User-Agent=")?.takeIf { it.isNotBlank() }?.let {
        hasSuffix = true
        headers.add("User-Agent", it)
    }

    if (!hasSuffix) return url
    val cleanUrl = url.substringBefore("@")
    if (cleanUrl.isEmpty()) return null

    return ImageRequest.Builder(context)
        .data(cleanUrl)
        .httpHeaders(headers.build())
        .build()
}

/** 与 fongmi UrlUtil.fixHeader 一致：标准请求头统一大小写，其余原样透传 */
private fun fixHeaderName(name: String): String = when {
    name.equals("User-Agent", ignoreCase = true) -> "User-Agent"
    name.equals("Referer", ignoreCase = true) -> "Referer"
    name.equals("Cookie", ignoreCase = true) -> "Cookie"
    else -> name
}
