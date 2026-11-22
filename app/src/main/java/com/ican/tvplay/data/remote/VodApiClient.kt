package com.ican.tvplay.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
    /** 配置声明的 VIP 解析平台列表（fongmi VodConfig.getFlags），必须原样传给 jar 的 playerContent */
    val flags: List<String> = emptyList(),
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
    /** 站点声明的卡片样式（fongmi Site.style）：原样保留 JSON，解析失败不影响站点加载 */
    val style: JsonElement? = null,
    /** 站点级请求头（fongmi Site.header）：对象 {"UA":"..."} 或数组 ["UA: ..."] */
    val header: JsonElement? = null,
) {
    /** 是否 spider 站（type=3；jar 类蜘蛛的 api 形如 csp_Xxx） */
    fun isSpider(): Boolean = type == 3

    /** jar 蜘蛛：api 为 csp_Xxx（js/py/node 蜘蛛的 api 不是这个形状，暂不支持） */
    fun isJarSpider(): Boolean = type == 3 && api.startsWith("csp_")

    /** ext 字段统一为字符串：字符串原样返回，对象/数组序列化为 JSON 字符串 */
    fun extAsString(): String = when (val e = ext) {
        null -> ""
        is JsonPrimitive -> if (e.isString) e.content else e.toString()
        else -> e.toString()
    }

    /** 站点声明的卡片样式（fongmi Site.style）：解析失败/未声明都返回 null */
    fun cardStyle(): VodStyle? = style?.let { el -> parseStyle(el) }

    /**
     * 站点级请求头（fongmi Site.getHeader）：
     * 支持对象 {"User-Agent":"..."} 与数组 ["User-Agent: ...", ...] 两种写法
     */
    fun headerMap(): Map<String, String> = runCatching {
        when (val h = header) {
            is JsonObject -> h.entries.mapNotNull { (k, v) ->
                (v as? JsonPrimitive)?.takeIf { it.isString }?.let { k to it.content }
            }.toMap()
            is JsonArray -> h.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .mapNotNull { line ->
                    val idx = line.indexOf(':')
                    if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                }.toMap()
            else -> emptyMap()
        }
    }.getOrDefault(emptyMap())
}

/** 分类（ac=class 返回的 class 数组元素；对齐 fongmi bean/Class 的全部字段） */
@Serializable
data class VodClass(
    /** fongmi: @SerializedName(value="type_id", alternate="id") */
    @SerialName("type_id") val typeId: String = "",
    /** fongmi: @SerializedName(value="type_name", alternate="name") */
    @SerialName("type_name") val typeName: String = "",
    /** 页面模式标记（OK猫 sortData.flag）：'1' 文件夹模式、'2' 带缩略图文件夹模式 */
    @SerialName("type_flag") val typeFlag: String = "",
    /** 分类自带样式（fongmi Class.getStyle）：land/circle/ratio 或 style 字段 */
    val style: JsonElement? = null,
    val land: Int? = null,
    val circle: Int? = null,
    val ratio: Double? = null,
) {
    /** fongmi Class.getStyle()：本分类声明的卡片样式，无信号返回 null */
    fun cardStyle(): VodStyle? = style?.let { parseStyle(it) } ?: styleOf(land, circle, ratio)
}

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
    /** 站源记录的最后更新时刻（形如 2026-11-06 12:00:00），部分站源不返回 */
    @SerialName("vod_time") val vodTime: String = "",
    @SerialName("action") val action: String = "",
    /** 目录标记（fongmi Vod.vod_tag）：等于 "folder" 时该卡片是网盘文件夹，点击进入子目录 */
    @SerialName("vod_tag") val vodTag: String = "",
    /** 目录元数据（fongmi Vod.cate）：非空同样表示文件夹（部分网盘站源用 cate 而非 vod_tag 标记） */
    @SerialName("cate") val cate: JsonElement? = null,
    /** 条目自带样式（fongmi Vod.style）：原样保留 JSON，交给 parseStyle 解析 */
    val style: JsonElement? = null,
    /** fongmi Vod.land：1 = 横版宽卡（默认 4:3） */
    val land: Int? = null,
    /** fongmi Vod.circle：1 = 圆形头像卡 */
    val circle: Int? = null,
    /** fongmi Vod.ratio：宽高比，0.75 = 3:4 海报，1.33 = 4:3 宽卡 */
    val ratio: Double? = null,
) {
    /** fongmi Vod.isFolder()：只有 vod_tag == "folder" 或带 cate 目录元数据，点击后进子目录 */
    fun isFolderItem(): Boolean = vodTag == "folder" || (cate != null && cate.toString() != "null")

    /** vod_tag == "cover"：带缩略图的目录，fongmi 里不参与目录判定，仅用于区分展示 */
    fun isCoverItem(): Boolean = vodTag == "cover"
}

/**
 * 卡片样式（fongmi Style）：
 * type = rect(矩形，按 ratio) / oval(圆形) / list(单列行)；ratio 为宽高比，缺省按 fongmi 规则补 0.75 或 1.33
 */
@Serializable
data class VodStyle(
    val type: String = "",
    val ratio: Double = 0.0,
) {
    fun normalizedType(): String = type.trim().lowercase().ifEmpty { "rect" }

    /** fongmi Style.getRatio：ratio<=0 时按类型补默认值，上限 4 */
    fun effectiveRatio(): Float {
        val t = normalizedType()
        val r = if (ratio > 0.0) ratio.toFloat() else if (t == "oval") 1.0f else 0.75f
        return r.coerceAtMost(4f)
    }

    fun isList(): Boolean = normalizedType() == "list"

    /** 横版宽卡：矩形且宽高比 > 1（fongmi Style.isLand） */
    fun isLand(): Boolean = normalizedType() == "rect" && effectiveRatio() > 1.0f
}

/** 把 fongmi 的 land/circle/ratio 三件套折算成样式（无信号返回 null） */
fun styleOf(land: Int?, circle: Int?, ratio: Double?): VodStyle? = when {
    land == 1 -> VodStyle(type = "rect", ratio = if (ratio != null && ratio > 0.0) ratio else 1.33)
    circle == 1 -> VodStyle(type = "oval", ratio = if (ratio != null && ratio > 0.0) ratio else 1.0)
    ratio != null && ratio > 0.0 -> VodStyle(type = "rect", ratio = ratio)
    else -> null
}

/** style 允许写成对象 {"type":"list"} 或裸字符串 "list"，两种都容忍 */
fun parseStyle(el: JsonElement): VodStyle? = runCatching {
    when (el) {
        is JsonPrimitive -> if (el.isString && el.content.isNotBlank()) VodStyle(type = el.content) else null
        is JsonObject -> VodStyle(
            type = (el["type"] as? JsonPrimitive)?.content.orEmpty(),
            ratio = (el["ratio"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
        )
        else -> null
    }
}.getOrNull()

/** 一个可选的播放画质（对应 fongmi bean/Url.Value：n 名称、v 地址） */
data class PlayQuality(val name: String, val url: String)

/**
 * playerContent 的 url 字段（对齐 fongmi UrlAdapter + Url）：
 * - 字符串 "https://..." → 单个地址、无名称
 * - 扁平数组 ["原画","https://...","高清","https://..."] → 名称/地址成对
 * - 对象 {"values":[{"n":"原画","v":"https://..."}],"position":0} → 带名称的多画质
 * 数组里也容忍 [{"n":..,"v":..}] 这种对象元素写法（部分非标准 spider）
 */
fun parsePlayerQualities(el: JsonElement?): List<PlayQuality> = runCatching {
    when (el) {
        null -> emptyList()
        is JsonPrimitive -> if (el.isString && el.content.isNotBlank()) {
            listOf(PlayQuality("", el.content))
        } else emptyList()
        is JsonArray -> {
            val first = el.firstOrNull()
            when (first) {
                // 扁平 [name, url, ...]：与 fongmi UrlAdapter.convert 同款，i+1 < size 才成对
                is JsonPrimitive -> buildList {
                    if (el.size == 1) {
                        val only = (el[0] as? JsonPrimitive)?.content.orEmpty()
                        if (only.isNotBlank()) add(PlayQuality("", only))
                    } else {
                        var i = 0
                        while (i + 1 < el.size) {
                            val name = (el[i] as? JsonPrimitive)?.content.orEmpty()
                            val url = (el[i + 1] as? JsonPrimitive)?.content.orEmpty()
                            if (url.isNotBlank()) add(PlayQuality(name, url))
                            i += 2
                        }
                    }
                }
                is JsonObject -> el.mapNotNull { item ->
                    val o = item as? JsonObject ?: return@mapNotNull null
                    val u = (o["v"] as? JsonPrimitive)?.content.orEmpty()
                    if (u.isBlank()) null
                    else PlayQuality((o["n"] as? JsonPrimitive)?.content.orEmpty(), u)
                }
                else -> emptyList()
            }
        }
        is JsonObject -> {
            val values = el["values"]
            if (values is JsonArray && values.isNotEmpty()) {
                values.mapNotNull { item ->
                    val o = item as? JsonObject ?: return@mapNotNull null
                    val u = (o["v"] as? JsonPrimitive)?.content.orEmpty()
                    if (u.isBlank()) null
                    else PlayQuality((o["n"] as? JsonPrimitive)?.content.orEmpty(), u)
                }
            } else {
                val u = (el["v"] as? JsonPrimitive)?.content.orEmpty()
                if (u.isBlank()) emptyList() else listOf(PlayQuality("", u))
            }
        }
        else -> emptyList()
    }
}.getOrDefault(emptyList())

/** url 对象里声明的当前画质下标（fongmi Url.position），数组/字符串形态恒为 0 */
fun parsePlayerPosition(el: JsonElement?): Int = runCatching {
    (el as? JsonObject)?.let { (it["position"] as? JsonPrimitive)?.content?.toIntOrNull() } ?: 0
}.getOrDefault(0)

/**
 * 取当前选中的播放地址；解析不出返回空串
 * @param overridePosition 调用方指定的画质下标（播放页切画质时传入），<0 表示按返回体里的 position
 */
fun parsePlayerUrl(el: JsonElement?, overridePosition: Int = -1): String = runCatching {
    val qualities = parsePlayerQualities(el)
    if (qualities.isEmpty()) return@runCatching ""
    val pos = if (overridePosition >= 0) overridePosition else parsePlayerPosition(el)
    (qualities.getOrNull(pos) ?: qualities.first()).url
}.getOrDefault("")

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
@Serializable
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
        .connectionPool(sharedPool)
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

        /** 全局共享连接池——所有模块（采集站/ spider jar 下载）复用 TCP 连接，减少握手开销 */
        internal val sharedPool = okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES)
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
            // 配置的 flags 必须交给 jar（playerContent 第三个参数）：网盘类 jar 依此决定自己解析
            // 还是把分享描述（share_id/surl/fs_id JSON）丢回给壳；壳不声明支持时夸克线路永远拿不到直链
            com.ican.tvplay.data.remote.SpiderManager.setVipFlags(config.flags)
            Log.d(TAG, "config flags=${config.flags.size} ${config.flags}".take(320))
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
    suspend fun homeClasses(site: TvBoxSite): List<VodClass> = homeClassesAndList(site).first

    /** 拉取首页分类 + 首页推荐位卡片（同一响应里的 list，OK猫 的 my0「主页」来源） */
    suspend fun homeClassesAndList(site: TvBoxSite): Pair<List<VodClass>, List<VodItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                // fongmi SiteApi.homeContent：type=4 只带 filter=true，其它 JSON 站带 ac=class
                val url = if (site.type == 4) {
                    buildUrl(site.api, mapOf("filter" to "true"))
                } else {
                    buildUrl(site.api, mapOf("ac" to "class"))
                }
                Log.d(TAG, "homeClasses url=$url")
                val body = get(url, site.headerMap())
                Log.d(TAG, "homeClasses bytes=${body.length}, head=${body.take(200)}")
                val result = json.decodeFromString<VodListResult>(body)
                Log.d(TAG, "homeClasses classes=${result.classes.size} list=${result.list.size}")
                result.classes to result.list
            }.onFailure { Log.e(TAG, "homeClasses fail", it) }.getOrDefault(emptyList<VodClass>() to emptyList())
        }

    /** 拉取分类下视频列表 */
    suspend fun categoryVideos(site: TvBoxSite, typeId: String, page: Int = 1): List<VodItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                // fongmi ac(type)：type=0 是 videolist，其余（含 type 4）是 detail
                val ac = if (site.type == 0) "videolist" else "detail"
                val params = linkedMapOf("ac" to ac, "t" to typeId, "pg" to page.toString())
                // type=4（ext-api）额外带 ext：fongmi 总是发送 base64(extend)，无筛选时就是 "{}" 的 URL-safe base64
                if (site.type == 4) params["ext"] = "e30"
                val url = buildUrl(site.api, params)
                Log.d(TAG, "categoryVideos url=$url")
                val body = get(url, site.headerMap())
                Log.d(TAG, "categoryVideos bytes=${body.length}, head=${body.take(200)}")
                val result = json.decodeFromString<VodListResult>(body)
                Log.d(TAG, "categoryVideos list=${result.list.size}")
                result.list
            }.onFailure { Log.e(TAG, "categoryVideos fail", it) }.getOrDefault(emptyList())
        }

    /** 搜索：先用 fongmi SiteApi.searchContent 的原始参数形态（wd/quick/extend，不带 ac），
     *  空结果再退回老式 ac=videolist&wd= —— 有些采集站只认其中一种 */
    suspend fun search(site: TvBoxSite, keyword: String): List<VodItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val headers = site.headerMap()
                val fongmiStyle = buildUrl(
                    site.api,
                    linkedMapOf("wd" to keyword, "quick" to "false", "extend" to ""),
                )
                Log.d(TAG, "search url=$fongmiStyle")
                val body = get(fongmiStyle, headers)
                Log.d(TAG, "search bytes=${body.length}")
                val first = runCatching { json.decodeFromString<VodListResult>(body).list }
                    .getOrDefault(emptyList())
                if (first.isNotEmpty()) return@runCatching first

                val ac = if (site.type == 0) "videolist" else "detail"
                val legacy = buildUrl(site.api, linkedMapOf("ac" to ac, "wd" to keyword))
                Log.d(TAG, "search fallback url=$legacy")
                val body2 = get(legacy, headers)
                json.decodeFromString<VodListResult>(body2).list
            }.onFailure { Log.e(TAG, "search fail", it) }.getOrDefault(emptyList())
        }

    /** 详情（ac=detail&ids=） */
    suspend fun detail(site: TvBoxSite, vodId: String): VodItem? = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(site.api, mapOf("ac" to "detail", "ids" to vodId))
            Log.d(TAG, "detail url=$url")
            val body = get(url, site.headerMap())
            Log.d(TAG, "detail bytes=${body.length}, head=${body.take(200)}")
            json.decodeFromString<VodDetailResult>(body).list.firstOrNull()
        }.onFailure { Log.e(TAG, "detail fail", it) }.getOrNull()
    }

    /**
     * type=4 站点的播放解析（fongmi SiteApi.loadPlayerContent 的 type 4 分支）：
     * GET api?play=<选集地址/ID>&flag=<线路> → 返回 {url, header, parse, jx}
     */
    suspend fun playerContentHttp(site: TvBoxSite, flag: String, playId: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildUrl(site.api, mapOf("play" to playId, "flag" to flag))
                Log.d(TAG, "playerContentHttp url=$url")
                val body = get(url, site.headerMap())
                Log.d(TAG, "playerContentHttp bytes=${body.length}, head=${body.take(200)}")
                body
            }.onFailure { Log.e(TAG, "playerContentHttp fail", it) }.getOrNull()
        }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder()
            .url(url)
            // TVBox 服务器按 UA 区分返回：okhttp UA 才返回伪装 JPEG 配置，浏览器 UA 返回 HTML 导航页
            .header("User-Agent", "okhttp/4.12.0")
        // 站点级 header（fongmi Site.header）覆盖默认 UA：有些采集站只认自己的 UA/Referer
        headers.forEach { (k, v) -> builder.header(k, v) }
        val request = builder.build()
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
