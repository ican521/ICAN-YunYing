package com.ican.tvplay.player.core

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 播放前源类型探测（仿 fongmi ContentTypeResolvingMediaSource 的解析思路）：
 * 未标注 format 的直链先读 16KB 头部或 Content-Type，判定 m3u8 / MPD / MP4 等，
 * 避免慢站、代理地址因容器识别失败而无法起播。
 */
@OptIn(UnstableApi::class)
internal object ContentTypeProbe {

    /** 最多嗅探的字节点数（m3u8 只需几十字节，MP4 需 12 字节即可判定 ftyp） */
    private const val SNIFF_BYTES = 16 * 1024

    /** 探测超时：与 fongmi 一致保持短超时，失败时退回原有「不指定格式」逻辑 */
    private const val TIMEOUT_MS = 4_000L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** URL 扩展名可直接判定时跳过探测请求 */
    fun fromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return when {
            path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".mp4") || path.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
            path.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            path.endsWith(".flv") -> MimeTypes.VIDEO_FLV
            path.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
            else -> null
        }
    }

    /**
     * 读取少量字节判定容器类型；网络异常或超时返回 null，由播放器自行识别。
     * 必须在 IO 线程调用。
     */
    fun probe(url: String, headers: Map<String, String>): String? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val request = runCatching {
            Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${SNIFF_BYTES - 1}")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build()
        }.getOrNull() ?: return null

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val head = response.body?.byteStream()?.use { readHead(it) } ?: ByteArray(0)
                sniff(head) ?: contentTypeToMime(
                    response.header("Content-Type")?.substringBefore(';')?.trim(),
                )
            }
        }.getOrNull()
    }

    private fun readHead(input: InputStream): ByteArray {
        val buffer = ByteArray(SNIFF_BYTES)
        var read = 0
        while (read < buffer.size) {
            val count = input.read(buffer, read, buffer.size - read)
            if (count <= 0) break
            read += count
        }
        return if (read == buffer.size) buffer else buffer.copyOf(read)
    }

    /** 按 fongmi ContentTypeResolvingMediaSource 的判定顺序嗅探文件头 */
    private fun sniff(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null

        // 跳过 UTF-8 BOM，播放列表可能以 BOM 开头
        val start = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) 3 else 0
        val headLength = minOf(bytes.size - start, 4096)
        val head = String(bytes, start, headLength, Charsets.UTF_8)
        val trimmed = head.trimStart()

        if (trimmed.startsWith("#EXTM3U")) return MimeTypes.APPLICATION_M3U8
        if (head.contains("<SmoothStreamingMedia")) return MimeTypes.APPLICATION_SS
        if (head.contains("<MPD")) return MimeTypes.APPLICATION_MPD

        if (bytes.size >= 12 && String(bytes, 4, 4, Charsets.ISO_8859_1) == "ftyp") {
            return MimeTypes.VIDEO_MP4
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
        ) {
            return MimeTypes.VIDEO_MATROSKA
        }
        if (bytes.size >= 3 &&
            bytes[0] == 'F'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'V'.code.toByte()
        ) {
            return MimeTypes.VIDEO_FLV
        }
        // TS 至少连续三个 188 字节同步位，避免把普通二进制误判为 TS
        if (bytes.size > 376 &&
            bytes[0] == 0x47.toByte() && bytes[188] == 0x47.toByte() && bytes[376] == 0x47.toByte()
        ) {
            return MimeTypes.VIDEO_MP2T
        }
        return null
    }

    private fun contentTypeToMime(contentType: String?): String? {
        return when (contentType?.lowercase(Locale.ROOT)) {
            "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl" ->
                MimeTypes.APPLICATION_M3U8
            "application/dash+xml" -> MimeTypes.APPLICATION_MPD
            "application/vnd.ms-sstr+xml" -> MimeTypes.APPLICATION_SS
            "video/mp4", "video/quicktime" -> MimeTypes.VIDEO_MP4
            "video/x-flv" -> MimeTypes.VIDEO_FLV
            "video/mp2t", "video/mpegts" -> MimeTypes.VIDEO_MP2T
            "video/x-matroska", "video/matroska" -> MimeTypes.VIDEO_MATROSKA
            else -> null
        }
    }
}
