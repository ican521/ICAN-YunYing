package com.ican.tvplay.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

private const val TAG = "VodApi"

/**
 * TVBox 配置 JSON：
 * - spider：站点共用的 spider jar（csp_Xxx 站点专用），格式 `url[;md5;xxx]`
 * - sites：站点列表，type=3 表示 spider jar；type 0/1 表示 HTTP 采集站
 */
@Serializable
data class TvBoxConfig(
    val spider: String = "",
    val sites: List<TvBoxSite> = emptyList(),
)

@Serializable
data class TvBoxSite(
    val key: String = "",
    val name: String = "",
    val api: String = "",
    val type: Int? = null,
    val jar: String = "",
    val searchable: Int = 1,
    val changeable: Int = 1,
    val ext: JsonElement? = null,
) {
    /** 是否 spider 站（type=3 + api=csp_Xxx） */
    fun isSpider(): Boolean = type == 3 && api.startsWith("csp_")

    /** ext 字段统一为字符串：字符串原样返回，对象/数组序列化为 JSON 字符串 */
    fun extAsString(): String = when (val e = ext) {
        null -> ""
        is JsonPrimitive -> if (e.isString) e.content else e.toString()
        else -> e.toString()
    }
}

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

/** 已加载站点：包含站点本身 + 生效的 spider jar spec（若有） */
data class LoadedSite(
    val site: TvBoxSite,
    val jarSpec: String,
)

/** TVBox 采集站 HTTP 客户端 */
class VodApiClient(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    },
) {
    private val client = OkHttpClient.Builder()
        .dns(FallbackDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .eventListener(object : EventListener() {
            override fun callStart(call: Call) { Log.d(TAG, ">> callStart ${call.request().url}") }
            override fun dnsStart(call: Call, domainName: String) { Log.d(TAG, ">> dnsStart $domainName") }
            override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
                Log.d(TAG, ">> dnsEnd $domainName -> ${inetAddressList.map { it.hostAddress }}")
            }
            override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                Log.d(TAG, ">> connectStart ${inetSocketAddress.address}:${inetSocketAddress.port}")
            }
            override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
                Log.d(TAG, ">> connectEnd ${inetSocketAddress.address}:${inetSocketAddress.port} protocol=$protocol")
            }
            override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
                Log.e(TAG, ">> connectFailed ${inetSocketAddress.address}:${inetSocketAddress.port} err=${ioe.message}")
            }
            override fun requestHeadersStart(call: Call) { Log.d(TAG, ">> requestHeadersStart") }
            override fun requestHeadersEnd(call: Call, request: Request) { Log.d(TAG, ">> requestHeadersEnd") }
            override fun requestBodyStart(call: Call) { Log.d(TAG, ">> requestBodyStart") }
            override fun requestBodyEnd(call: Call, byteCount: Long) { Log.d(TAG, ">> requestBodyEnd bytes=$byteCount") }
            override fun responseHeadersStart(call: Call) { Log.d(TAG, ">> responseHeadersStart") }
            override fun responseHeadersEnd(call: Call, response: Response) { Log.d(TAG, ">> responseHeadersEnd code=${response.code}") }
            override fun responseBodyStart(call: Call) { Log.d(TAG, ">> responseBodyStart") }
            override fun responseBodyEnd(call: Call, byteCount: Long) { Log.d(TAG, ">> responseBodyEnd bytes=$byteCount") }
            override fun callEnd(call: Call) { Log.d(TAG, ">> callEnd") }
            override fun callFailed(call: Call, ioe: IOException) { Log.e(TAG, ">> callFailed err=${ioe.javaClass.simpleName}: ${ioe.message}") }
        })
        .build()

    companion object {
        /** 全局共享的已加载配置（configUrl to config）——所有 VodApiClient 实例复用，
         *  与 fongmi 一致：配置只下载解析一次常驻内存，避免每次进搜索页/首页重复下载 */
        @Volatile
        private var cachedConfig: Pair<String, TvBoxConfig>? = null
    }

    /** 拉取并解码配置（带全局缓存）；失败返回 null */
    suspend fun loadConfig(configUrl: String): TvBoxConfig? = withContext(Dispatchers.IO) {
        cachedConfig?.takeIf { it.first == configUrl }?.second?.let { return@withContext it }
        runCatching {
            Log.d(TAG, "loadConfig: $configUrl")
            val body = get(configUrl)
            Log.d(TAG, "config bytes=${body.length}, head=${body.take(200)}")
            val decoded = Decoder.decode(body)
            Log.d(TAG, "decoded bytes=${decoded.length}, head=${decoded.take(200)}")
            val config = json.decodeFromString<TvBoxConfig>(decoded)
            Log.d(TAG, "sites count=${config.sites.size}, spider=${config.spider.take(80)}")
            config.sites.forEachIndexed { i, s ->
                Log.d(TAG, "site[$i] key=${s.key} name=${s.name} type=${s.type} api=${s.api.take(60)}")
            }
            cachedConfig = configUrl to config
            config
        }.onFailure {
            Log.e(TAG, "loadConfig fail: ${it.javaClass.simpleName}: ${it.message}", it)
        }.getOrNull()
    }

    /** 配置中全部可用站点（spider 站 + HTTP 采集站），供用户手动切换 */
    suspend fun listSites(configUrl: String): List<TvBoxSite> {
        val config = loadConfig(configUrl) ?: return emptyList()
        return config.sites.filter { it.isSpider() || it.api.startsWith("http") }
    }

    /**
     * 拉取配置 URL，返回当前生效的站点。
     * - 响应经 [Decoder] 解码（支持伪装 JPEG 中的 base64）
     * - preferredKey 命中可用站点时直接使用；否则站点优先选 type=3 且 api=csp_Xxx 的 spider 站；
     *   fallback 为 type 0/1 的 HTTP 采集站
     */
    suspend fun loadHomeSite(configUrl: String, preferredKey: String? = null): LoadedSite? =
        withContext(Dispatchers.IO) {
            runCatching {
                val config = loadConfig(configUrl) ?: return@runCatching null

                // 用户手动指定的站点优先
                if (!preferredKey.isNullOrBlank()) {
                    config.sites.firstOrNull { s ->
                        s.key == preferredKey && (s.isSpider() || s.api.startsWith("http"))
                    }?.let { s ->
                        val jar = if (s.isSpider()) s.jar.ifBlank { config.spider } else ""
                        Log.d(TAG, "chosen preferred=${s.name} api=${s.api.take(60)}")
                        return@runCatching LoadedSite(s, jar)
                    }
                    Log.w(TAG, "preferred site key=$preferredKey not found, fallback auto")
                }

                // 优先纯内容 spider 站（searchable=changeable=1），避免选中云盘/配置类站点
                val spiderSite = config.sites.firstOrNull { s ->
                    s.isSpider() && s.searchable == 1 && s.changeable == 1 &&
                        (s.jar.isNotBlank() || config.spider.isNotBlank())
                } ?: config.sites.firstOrNull { s ->
                    s.isSpider() && (s.searchable != 0 || s.changeable != 0) &&
                        (s.jar.isNotBlank() || config.spider.isNotBlank())
                }
                if (spiderSite != null) {
                    val jar = spiderSite.jar.ifBlank { config.spider }
                    Log.d(TAG, "chosen spider=${spiderSite.name} api=${spiderSite.api} jar=${jar.take(80)}")
                    return@runCatching LoadedSite(spiderSite, jar)
                }

                // fallback：纯 HTTP JSON 采集站
                val httpSite = config.sites.firstOrNull { s ->
                    val t = s.type
                    s.api.contains("provide/vod") && (t == null || t <= 1)
                } ?: config.sites.firstOrNull { s ->
                    val t = s.type
                    s.api.startsWith("http") && (t == null || t <= 1)
                }
                Log.d(TAG, "chosen http=${httpSite?.name ?: "<none>"} api=${httpSite?.api ?: ""}")
                httpSite?.let { LoadedSite(it, "") }
            }.onFailure {
                Log.e(TAG, "loadHomeSite fail: ${it.javaClass.simpleName}: ${it.message}", it)
            }.getOrNull()
        }

    /** 拉取首页分类（ac=class） */
    suspend fun homeClasses(site: TvBoxSite): List<VodClass> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(site.api, mapOf("ac" to "class"))
            Log.d(TAG, "homeClasses url=$url")
            val body = get(url)
            Log.d(TAG, "homeClasses bytes=${body.length}, head=${body.take(200)}")
            val result = json.decodeFromString<VodListResult>(body)
            Log.d(TAG, "homeClasses classes=${result.classes.size} list=${result.list.size}")
            result.classes
        }.onFailure { Log.e(TAG, "homeClasses fail", it) }.getOrDefault(emptyList())
    }

    /** 拉取分类下视频列表 */
    suspend fun categoryVideos(site: TvBoxSite, typeId: String, page: Int = 1): List<VodItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildUrl(
                    site.api,
                    mapOf("ac" to "videolist", "t" to typeId, "pg" to page.toString()),
                )
                Log.d(TAG, "categoryVideos url=$url")
                val body = get(url)
                Log.d(TAG, "categoryVideos bytes=${body.length}, head=${body.take(200)}")
                val result = json.decodeFromString<VodListResult>(body)
                Log.d(TAG, "categoryVideos list=${result.list.size}")
                result.list
            }.onFailure { Log.e(TAG, "categoryVideos fail", it) }.getOrDefault(emptyList())
        }

    /** 搜索（ac=videolist&wd=） */
    suspend fun search(site: TvBoxSite, keyword: String): List<VodItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildUrl(site.api, mapOf("ac" to "videolist", "wd" to keyword))
                Log.d(TAG, "search url=$url")
                val body = get(url)
                Log.d(TAG, "search bytes=${body.length}")
                json.decodeFromString<VodListResult>(body).list
            }.onFailure { Log.e(TAG, "search fail", it) }.getOrDefault(emptyList())
        }

    /** 详情（ac=detail&ids=） */
    suspend fun detail(site: TvBoxSite, vodId: String): VodItem? = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(site.api, mapOf("ac" to "detail", "ids" to vodId))
            Log.d(TAG, "detail url=$url")
            val body = get(url)
            Log.d(TAG, "detail bytes=${body.length}, head=${body.take(200)}")
            json.decodeFromString<VodDetailResult>(body).list.firstOrNull()
        }.onFailure { Log.e(TAG, "detail fail", it) }.getOrNull()
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            // TVBox 服务器按 UA 区分返回：okhttp UA 才返回伪装 JPEG 配置，浏览器 UA 返回 HTML 导航页
            .header("User-Agent", "okhttp/4.12.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for $url")
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
