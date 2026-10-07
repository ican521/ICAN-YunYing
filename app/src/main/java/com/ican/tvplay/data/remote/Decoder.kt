package com.ican.tvplay.data.remote

import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets

private const val DECODER_TAG = "VodApi"

/**
 * TVBox 配置解码：
 * - 已是 JSON（以 { 或 [ 开头）→ 原样返回
 * - 伪装 JPEG 中的 base64（含 `[A-Za-z0-9]{8}\*\*` 标记）→ 提取标记后偏移 10 起做 base64 解码
 * 参考：D:\fongmi-tv-mycustom\app\src\main\java\com\fongmi\android\tv\api\Decoder.java
 */
object Decoder {

    private val MARKER_REGEX = Regex("[A-Za-z0-9]{8}\\*\\*")
    private const val BOM = '\uFEFF'

    /** 把任意响应体解码为 JSON 字符串；失败原样返回（由上层 JSON 解析报错并降级） */
    fun decode(raw: String): String {
        if (raw.isEmpty()) return raw
        // 去 BOM
        val trimmed = raw.trimStart(BOM).trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return stripComments(trimmed)
        if (raw.contains("**")) {
            val extracted = extract(raw)
            if (extracted.isNotEmpty()) {
                return try {
                    val decoded = String(Base64.decode(extracted, Base64.DEFAULT), StandardCharsets.UTF_8)
                        .trimStart(BOM)
                        .trim()
                    stripComments(decoded)
                } catch (t: Throwable) {
                    Log.e(DECODER_TAG, "Decoder base64 fail", t)
                    raw
                }
            }
        }
        return raw
    }

    /** 去除 JSON 中的行注释和块注释（TVBox 配置常见） */
    private fun stripComments(json: String): String {
        val sb = StringBuilder(json.length)
        var inString = false
        var inEscape = false
        var inLineComment = false
        var inBlockComment = false
        var i = 0
        while (i < json.length) {
            val c = json[i]
            when {
                inLineComment -> {
                    if (c == '\n' || c == '\r') {
                        inLineComment = false
                        sb.append(c)
                    }
                }
                inBlockComment -> {
                    if (c == '*' && i + 1 < json.length && json[i + 1] == '/') {
                        inBlockComment = false
                        i++ // skip /
                    }
                }
                inEscape -> {
                    sb.append(c)
                    inEscape = false
                }
                inString -> {
                    if (c == '\\') {
                        inEscape = true
                        sb.append(c)
                    } else if (c == '"') {
                        inString = false
                        sb.append(c)
                    } else {
                        sb.append(c)
                    }
                }
                c == '"' -> {
                    inString = true
                    sb.append(c)
                }
                c == '/' && i + 1 < json.length && json[i + 1] == '/' -> {
                    inLineComment = true
                    i++ // skip second /
                }
                c == '/' && i + 1 < json.length && json[i + 1] == '*' -> {
                    inBlockComment = true
                    i++ // skip *
                }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun extract(data: String): String {
        val m = MARKER_REGEX.find(data) ?: return ""
        val start = data.indexOf(m.value) + 10
        return if (start < data.length) data.substring(start) else ""
    }
}
