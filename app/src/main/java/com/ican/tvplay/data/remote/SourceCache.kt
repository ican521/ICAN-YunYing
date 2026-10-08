package com.ican.tvplay.data.remote

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest

private const val TAG = "SourceCache"

/**
 * 轻量磁盘缓存：给 VodApiClient / MultiSourceSearchViewModel 做 TTL-based 结果缓存，
 * 避免重复网络请求（配置 JSON、搜索结果、详情）。
 *
 * 参考 Fongmi 的 ConfigCache 设计但简化：单文件 per key + 时间戳校验。
 */
class SourceCache(appContext: Context) {

    private val cacheDir: File by lazy {
        File(appContext.cacheDir, "source_cache").apply { mkdirs() }
    }

    private val ttlConfig = 30L * 60 * 1000        // 配置 30 分钟
    private val ttlSearch = 10L * 60 * 1000         // 搜索 10 分钟
    private val ttlDetail = 60L * 60 * 1000         // 详情 1 小时

    enum class Kind(val ttlMs: Long) {
        CONFIG(30L * 60 * 1000),
        SEARCH(10L * 60 * 1000),
        DETAIL(60L * 60 * 1000),
    }

    private fun File.isFresh(ttlMs: Long): Boolean {
        if (!exists()) return false
        val age = System.currentTimeMillis() - lastModified()
        return age in 0 until ttlMs
    }

    fun get(key: String, kind: Kind): String? {
        val file = File(cacheDir, md5(key))
        return if (file.isFresh(kind.ttlMs)) file.readText() else null
    }

    fun put(key: String, kind: Kind, value: String) {
        val file = File(cacheDir, md5(key))
        runCatching { file.writeText(value) }
            .onFailure { Log.w(TAG, "cache put fail key=$key", it) }
    }

    fun invalidate(key: String) {
        File(cacheDir, md5(key)).delete()
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun sizeBytes(): Long = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
