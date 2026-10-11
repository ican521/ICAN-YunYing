package com.ican.tvplay.data.remote

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.lang.reflect.Method

/**
 * fongmi 同款本地代理服务器：NanoHTTPD 在 9978~9998 端口起 HTTP 服务，
 * 供 spider jar 的 playerContent 返回的 127.0.0.1/proxy 地址中转拉流。
 *
 * jar 生成的代理 URL 示例：
 *   http://127.0.0.1:port/proxy?do=m3u8&url=真实地址
 * 请求打到本服务后，把参数透传给 jar 的 com.github.catvod.spider.Proxy.proxy(Map)，
 * 由 jar 去请求真实地址并返回 {状态码, MIME, InputStream}，本服务再流式回传。
 */
object LocalProxyServer {

    private const val TAG = "LocalProxy"
    private const val START_PORT = 9978
    private const val END_PORT = 9999

    private var server: ProxyServer? = null
    @Volatile
    private var spiderManager: SpiderManager? = null

    val isRunning: Boolean get() = server != null

    fun start(manager: SpiderManager) {
        if (server != null) return
        spiderManager = manager
        for (port in START_PORT..END_PORT) {
            try {
                val s = ProxyServer(port)
                s.start(500)
                com.github.catvod.Proxy.set(port)
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
        spiderManager = null
    }

    private class ProxyServer(port: Int) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            val url = session.uri.trim()
            if (!url.startsWith("/proxy")) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }

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
