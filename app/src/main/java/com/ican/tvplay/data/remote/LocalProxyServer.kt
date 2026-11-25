package com.ican.tvplay.data.remote

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Method
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

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

    /** 代理请求最大并发线程数：正常播放（m3u8 播放列表+分片+字幕）同时只占几个，留足余量 */
    private const val MAX_PROXY_THREADS = 12

    /** jar 自带本地代理的端口探测区间（catvod spider 默认从 6677 起） */
    private const val JAR_PORT_START = 6677
    private const val JAR_PORT_END = 6699

    /** 端口探测连接超时：小超时快速扫一遍区间 */
    private const val JAR_PROBE_TIMEOUT_MS = 300

    /** 转发到 jar 的连接超时（5-10s 区间取 8s）；读超时为 0：长视频流不能被读超时掐断 */
    private const val FORWARD_CONNECT_TIMEOUT_MS = 8000L
    private const val FORWARD_READ_TIMEOUT_MS = 0L

    /** 上游未给 Content-Type 时的兜底 MIME */
    private const val FALLBACK_MIME = "application/octet-stream"

    /** 本机回环地址 */
    private const val LOCAL_HOST = "127.0.0.1"

    /** 上游不可达时返回的网关错误码 */
    private const val BAD_GATEWAY_CODE = 502

    private var server: ProxyServer? = null
    @Volatile
    private var spiderManager: SpiderManager? = null
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var boundPort: Int = -1

    /** 探测到的 jar 本地代理端口，未探测到为 -1 */
    @Volatile
    private var cachedJarPort: Int = -1

    /** 转发专用 OkHttpClient（不复用 catvod 网关卡的 client），懒加载复用连接池 */
    @Volatile
    private var forwardClient: OkHttpClient? = null

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
                // 线程池限流：NanoHTTPD 默认每个请求裸开线程，遇到拉死源不退出的 jar 代理
                // 会线程无限堆积（栈+缓冲吃光堆内存导致 OOM 闪退）。固定上限后请求排队，
                // 并发数可控，坏线路反复请求也撑不爆内存
                s.setAsyncRunner(BoundedAsyncRunner(MAX_PROXY_THREADS))
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
        cachedJarPort = -1
    }

    /**
     * 探测 jar 自带本地代理端口（[JAR_PORT_START]..[JAR_PORT_END]）：首个可连通端口即缓存；
     * 跳过本服务自己的监听端口，避免把自己当 jar 造成回环。探测失败返回 -1。
     */
    private fun detectJarPort(): Int {
        synchronized(this) {
            cachedJarPort.takeIf { it > 0 }?.let { return it }
            val ownPort = boundPort
            for (port in JAR_PORT_START..JAR_PORT_END) {
                if (port == ownPort) continue
                var socket: Socket? = null
                try {
                    socket = Socket()
                    socket.connect(InetSocketAddress(LOCAL_HOST, port), JAR_PROBE_TIMEOUT_MS)
                    cachedJarPort = port
                    Log.i(TAG, "jar proxy detected at port=$port")
                    return port
                } catch (_: IOException) {
                    runCatching { socket?.close() }
                }
            }
            Log.w(TAG, "jar proxy port not found in $JAR_PORT_START..$JAR_PORT_END")
            return -1
        }
    }

    /** 转发专用客户端：连接超时 8s、读超时 0（长流不断），不设 callTimeout */
    private fun getForwardClient(): OkHttpClient {
        forwardClient?.let { return it }
        synchronized(this) {
            forwardClient?.let { return it }
            val client = OkHttpClient.Builder()
                .connectTimeout(FORWARD_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(FORWARD_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
            forwardClient = client
            return client
        }
    }

    /**
     * 有界线程池（替换 NanoHTTPD 默认的每请求裸开线程 DefaultAsyncRunner）：
     * 并发请求超过 [maxThreads] 时在队列里等待，线程数永远不超限。
     */
    private class BoundedAsyncRunner(maxThreads: Int) : NanoHTTPD.AsyncRunner {

        private val pool = java.util.concurrent.ThreadPoolExecutor(
            maxThreads, maxThreads,
            60L, java.util.concurrent.TimeUnit.SECONDS,
            java.util.concurrent.LinkedBlockingQueue(),
        ) { r -> Thread(r, "NanoHttpd Request Processor").apply { isDaemon = true } }

        private val running = java.util.concurrent.CopyOnWriteArrayList<NanoHTTPD.ClientHandler>()

        override fun closeAll() {
            running.forEach { runCatching { it.close() } }
            pool.shutdownNow()
        }

        override fun closed(clientHandler: NanoHTTPD.ClientHandler) {
            running.remove(clientHandler)
        }

        override fun exec(clientHandler: NanoHTTPD.ClientHandler) {
            running.add(clientHandler)
            try {
                pool.execute(clientHandler)
            } catch (t: Throwable) {
                running.remove(clientHandler)
                runCatching { clientHandler.close() }
            }
        }
    }

    /**
     * 包装上游响应流：EOF 或 close 时关闭 ResponseBody，避免连接泄漏占满连接池/线程池。
     * OkHttp 的 body.close() 可重复调用，重复关闭安全。
     */
    private class ClosingBodyInputStream(
        private val delegate: InputStream,
        private val body: ResponseBody,
    ) : InputStream() {

        override fun read(): Int {
            val read = delegate.read()
            if (read == -1) closeBody()
            return read
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = delegate.read(b, off, len)
            if (read == -1) closeBody()
            return read
        }

        override fun close() {
            try {
                delegate.close()
            } finally {
                closeBody()
            }
        }

        private fun closeBody() {
            runCatching { body.close() }
        }
    }

    /**
     * 必须显式绑 IPv4 通配地址：NanoHTTPD(port) 只传端口时，绑定的是 IPv6 通配 [::]，
     * 在部分设备上是 v6only 监听（/proc/net/tcp 无 9978，只有 tcp6 有），
     * 导致局域网 IPv4 扫描（192.168.x.x:9978/device）永远连不上、收藏同步发现不到本机。
     * 指定 hostname="0.0.0.0" 后 socket 绑 IPv4，扫描与 127.0.0.1 代理均正常。
     */
    private class ProxyServer(port: Int) : NanoHTTPD("0.0.0.0", port) {

        override fun serve(session: IHTTPSession): Response {
            return when {
                // fongmi /device：返回本机信息（局域网设备发现）
                session.uri.trim().startsWith("/device") -> deviceResponse()
                // fongmi /action：目前仅实现 do=sync&type=keep（收藏同步接收端）
                session.uri.trim().startsWith("/action") -> actionResponse(session)
                // jar playerContent 直连本地代理的流地址（/proxy/play/...）：转发到 jar 端口，
                // 不能落到下面的 /proxy?do= 透传（那是 jar 自己的代理协议，不认识 play 路径）
                session.uri.trim().startsWith("/proxy/play") -> forwardPlayProxy(session)
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

        /**
         * /proxy/play/... 流转发入口：先按缓存端口走一次；IOException 时清缓存重探一次再重试，
         * 只重试一次。端口未知/连不上返回 502，不回落到 proxyResponse 的 /proxy?do= 透传。
         */
        private fun forwardPlayProxy(session: IHTTPSession): Response {
            var port = cachedJarPort.takeIf { it > 0 } ?: detectJarPort()
            if (port <= 0) {
                return badGatewayResponse("Jar proxy unreachable")
            }
            return try {
                forwardToJar(session, port)
            } catch (e: IOException) {
                Log.w(TAG, "forward play proxy failed, re-probe once: ${e.message}")
                cachedJarPort = -1
                port = detectJarPort()
                if (port <= 0) return badGatewayResponse("Jar proxy unreachable")
                forwardToJar(session, port)
            }
        }

        /** 单次转发：拼回原 path + query，透传 Range/UA/Referer/Accept，响应体流式回传 */
        private fun forwardToJar(session: IHTTPSession, port: Int): Response {
            val path = session.uri.trim()
            val query = session.queryParameterString
            val targetUrl = buildString {
                append("http://").append(LOCAL_HOST).append(':').append(port).append(path)
                if (!query.isNullOrEmpty()) append('?').append(query)
            }
            val requestBuilder = Request.Builder().url(targetUrl).get()
                .header("Accept-Encoding", "identity")
            listOf("range", "user-agent", "referer", "accept").forEach { name ->
                forwardedHeader(session, name)?.let { requestBuilder.header(name, it) }
            }
            val response = getForwardClient().newCall(requestBuilder.build()).execute()
            val body = response.body
            if (body == null) {
                runCatching { response.close() }
                return newFixedLengthResponse(toStatus(response.code), FALLBACK_MIME, "Empty body")
            }
            val mime = body.contentType()?.toString()?.ifBlank { FALLBACK_MIME } ?: FALLBACK_MIME
            val wrapped = ClosingBodyInputStream(body.byteStream(), body)
            val length = body.contentLength()
            val upstream = if (length >= 0) {
                newFixedLengthResponse(toStatus(response.code), mime, wrapped, length)
            } else {
                newChunkedResponse(toStatus(response.code), mime, wrapped)
            }
            // 206 分段响应必须原样带出 Content-Range；Accept-Ranges/Content-Length 有则透传
            response.header("Content-Range")?.let { upstream.addHeader("Content-Range", it) }
            response.header("Accept-Ranges")?.let { upstream.addHeader("Accept-Ranges", it) }
            if (length < 0) {
                response.header("Content-Length")?.let { upstream.addHeader("Content-Length", it) }
            }
            return upstream
        }

        /** 忽略大小写取请求头（NanoHTTPD 2.3.1 入库前已转小写，这里双保险） */
        private fun forwardedHeader(session: IHTTPSession, name: String): String? {
            val lower = name.lowercase()
            return session.headers.entries.firstOrNull { it.key.lowercase() == lower }?.value
        }

        private fun badGatewayResponse(message: String): Response =
            newFixedLengthResponse(toStatus(BAD_GATEWAY_CODE), MIME_PLAINTEXT, message)

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
