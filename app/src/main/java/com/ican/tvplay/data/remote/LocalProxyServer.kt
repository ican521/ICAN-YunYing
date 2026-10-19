package com.ican.tvplay.data.remote

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.lang.reflect.Method
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * fongmi 同款本地代理服务器：NanoHTTPD 在 9978~9998 端口起 HTTP 服务，
 * 供 spider jar 的 playerContent 返回的 127.0.0.1/proxy 地址中转拉流。
 *
 * jar 生成的代理 URL 示例：
 *   http://127.0.0.1:port/proxy?do=m3u8&url=真实地址
 * 请求打到本服务后，把参数透传给 jar 的 com.github.catvod.spider.Proxy.proxy(Map)，
 * 由 jar 去请求真实地址并返回 {状态码, MIME, InputStream}，本服务再流式回传。
 *
 * 同时照搬 fongmi Server 的 /device 与 /action?do=sync 接口，用于局域网收藏同步。
 */
object LocalProxyServer {

    private const val TAG = "LocalProxy"
    private const val START_PORT = 9978
    private const val END_PORT = 9999

    private var server: ProxyServer? = null
    @Volatile
    private var spiderManager: SpiderManager? = null
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var boundPort: Int = -1

    val isRunning: Boolean get() = server != null

    /** 本机局域网完整地址（fongmi Server.getAddress）：http://ip:port；未运行返回空串 */
    fun address(): String {
        val port = boundPort
        if (port <= 0) return ""
        val ip = localIp()
        return if (ip.isBlank()) "" else "http://$ip:$port"
    }

    /** 本机局域网 IPv4：优先 WiFi/热点网卡，过滤掉蜂窝数据网卡（rmnet/ccmni 等），
     *  避免手机开热点时返回蜂窝 IP 导致对端连不上 */
    fun localIp(): String = runCatching {
        getHostAddress("wlan").ifEmpty {
            getHostAddress("softap").ifEmpty {
                getHostAddress("ap").ifEmpty {
                    getHostAddress("eth").ifEmpty {
                        getWifiAddress().ifEmpty { getLanHostAddress() }
                    }
                }
            }
        }
    }.getOrDefault("")

    /** fongmi Util.getHostAddress：按网卡名前缀匹配（空串=任意），取首个非回环 IPv4 */
    private fun getHostAddress(keyword: String): String = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { keyword.isEmpty() || it.name.startsWith(keyword) }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress.orEmpty()
    }.getOrDefault("")

    /** 兜底：取首个非回环 IPv4，但跳过蜂窝数据网卡（rmnet/ccmni/dummy） */
    private fun getLanHostAddress(): String = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter {
                val n = it.name
                !n.startsWith("rmnet") && !n.startsWith("ccmni") &&
                    !n.startsWith("dummy") && !n.startsWith("v4-")
            }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress.orEmpty()
    }.getOrDefault("")

    /** fongmi Util.getWifiAddress：从 WifiManager 连接信息取 IP（int 小端序转点分） */
    private fun getWifiAddress(): String = runCatching {
        val context = appContext ?: return ""
        val manager = context.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            ?: return ""
        val ip = manager.connectionInfo.ipAddress
        if (ip == 0) "" else "%d.%d.%d.%d".format(ip and 0xFF, ip shr 8 and 0xFF, ip shr 16 and 0xFF, ip shr 24 and 0xFF)
    }.getOrDefault("")

    fun start(context: Context, manager: SpiderManager) {
        if (server != null) return
        spiderManager = manager
        appContext = context.applicationContext
        for (port in START_PORT..END_PORT) {
            try {
                val s = ProxyServer(port)
                s.start(500)
                com.github.catvod.Proxy.set(port)
                boundPort = port
                server = s
                Log.i(TAG, "proxy server started at port=$port")
                return
            } catch (_: Throwable) {
                // 端口被占用，尝试下一个
            }
        }
        Log.e(TAG, "failed to start proxy server on any port $START_PORT..$END_PORT")
    }

    fun stop() {
        try {
            server?.stop()
        } catch (_: Throwable) {}
        server = null
        com.github.catvod.Proxy.set(-1)
        boundPort = -1
        spiderManager = null
        appContext = null
    }

    private class ProxyServer(port: Int) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            return when {
                // fongmi /device：返回本机信息（局域网设备发现）
                session.uri.trim().startsWith("/device") -> deviceResponse()
                // fongmi /action：目前仅实现 do=sync&type=keep（收藏同步接收端）
                session.uri.trim().startsWith("/action") -> actionResponse(session)
                session.uri.trim().startsWith("/proxy") -> proxyResponse(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }
        }

        private fun deviceResponse(): Response {
            val context = appContext
                ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Not ready")
            val json = com.ican.tvplay.data.FavoriteSync.localDeviceJson(context)
            return newFixedLengthResponse(Response.Status.OK, "application/json", json)
        }

        private fun actionResponse(session: IHTTPSession): Response {
            val context = appContext
                ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Not ready")
            return try {
                // fongmi Nano.parse：先 parseBody 再读 parms。
                // application/x-www-form-urlencoded 的 POST 字段由 parseBody 写入 session.parms，
                // 若先读 parms 再 parseBody，device/targets/configs 会丢失导致同步空转。
                if (session.method == Method.POST) {
                    runCatching { session.parseBody(mutableMapOf()) }
                }
                val params = session.parms.toMutableMap()
                if (params["do"] != "sync") {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
                }
                val ok = com.ican.tvplay.data.FavoriteSync.handleSync(context, params)
                newFixedLengthResponse(
                    if (ok) Response.Status.OK else Response.Status.BAD_REQUEST,
                    MIME_PLAINTEXT,
                    if (ok) "ok" else "bad request",
                )
            } catch (t: Throwable) {
                Log.e(TAG, "action error", t)
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    t.message ?: "Action error",
                )
            }
        }

        private fun proxyResponse(session: IHTTPSession): Response {
            return try {
                val params = mutableMapOf<String, String>()
                params.putAll(session.parms)
                params.putAll(session.headers)

                if (session.method == Method.POST) {
                    try {
                        val body = mutableMapOf<String, String>()
                        session.parseBody(body)
                        params.putAll(body)
                    } catch (_: Exception) {}
                }

                val manager = spiderManager
                    ?: return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        MIME_PLAINTEXT,
                        "Proxy not ready",
                    )

                val result = manager.callProxy(params)
                    ?: return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        MIME_PLAINTEXT,
                        "Proxy empty",
                    )

                createResponse(result)
            } catch (t: Throwable) {
                Log.e(TAG, "proxy error", t)
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    t.message ?: "Proxy error",
                )
            }
        }

        private fun createResponse(result: Array<*>): Response {
            if (result.isEmpty()) {
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "Invalid proxy response",
                )
            }
            if (result[0] is Response) return result[0] as Response
            if (result.size < 3) {
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "Invalid proxy response",
                )
            }
            val statusCode = (result[0] as Number).toInt()
            val mimeType = result[1] as String
            val inputStream = result[2] as InputStream
            val response = newChunkedResponse(toStatus(statusCode), mimeType, inputStream)
            if (result.size > 3 && result[3] != null) {
                @Suppress("UNCHECKED_CAST")
                val headers = result[3] as Map<String, String>
                headers.forEach { (k, v) -> response.addHeader(k, v) }
            }
            return response
        }

        private fun toStatus(code: Int): Response.IStatus {
            Response.Status.lookup(code)?.let { return it }
            return if (code in 100..599) {
                object : Response.IStatus {
                    override fun getDescription(): String = "$code Proxy Status"
                    override fun getRequestStatus(): Int = code
                }
            } else {
                Response.Status.INTERNAL_ERROR
            }
        }
    }
}
