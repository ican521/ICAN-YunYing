package com.ican.tvplay.data.remote

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "Spider"

/**
 * TVBox Spider（csp_Xxx）加载器：
 * - 下载伪装成 .jpg 的 jar 到私有目录（去除 `;md5;xxx` 后缀，可选 md5 校验）
 * - DexClassLoader 反射加载 `com.github.catvod.spider.Xxx`
 * - 反射调用 Spider 基类的 init/homeContent/categoryContent/detailContent/searchContent/playerContent
 *
 * 参考：D:\fongmi-tv-mycustom\app\src\main\java\com\fongmi\android\tv\api\loader\JarLoader.java
 */
class SpiderManager(private val appContext: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** key = md5(jarUrl) → 已加载的 DexClassLoader */
    private val loaders = ConcurrentHashMap<String, DexClassLoader>()

    /** key = md5(jarUrl)|siteKey → 已初始化的 Spider 实例（反射持有） */
    private val spiders = ConcurrentHashMap<String, Any>()

    private val locks = ConcurrentHashMap<String, Any>()

    private val jarDir: File by lazy {
        File(appContext.filesDir, "spider_jars").apply { mkdirs() }
    }
    private val dexOutDir: File by lazy {
        File(appContext.codeCacheDir, "spider_dex").apply { mkdirs() }
    }

    /**
     * 获取指定站点的 Spider 实例；失败返回 null（上层降级为空态）。
     * @param site 站点配置（需 type=3 且 api=csp_Xxx）
     * @param jarSpec 完整 spider 字段（可能含 `;md5;xxx` 后缀）
     */
    suspend fun getSpider(site: TvBoxSite, jarSpec: String): Any? {
        if (site.type != 3 || !site.api.startsWith("csp_")) return null
        if (jarSpec.isBlank()) return null
        val jarUrl = jarSpec.substringBefore(";md5;").trim()
        if (jarUrl.isBlank()) return null
        val spKey = md5(jarUrl) + "|" + site.key
        spiders[spKey]?.let { return it }
        val loader = ensureLoader(jarSpec) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val className = "com.github.catvod.spider." + site.api.removePrefix("csp_")
                Log.d(TAG, "loadSpider class=$className key=${site.key}")
                val cls = loader.loadClass(className)
                val instance = cls.getDeclaredConstructor().newInstance()
                val extString = site.extAsString()
                // 优先 init(Context, String)；失败回退 init(Context)
                runCatching {
                    cls.getMethod("init", Context::class.java, String::class.java)
                        .invoke(instance, appContext, extString)
                }.onFailure { t1 ->
                    Log.w(TAG, "init(ctx, ext) fail, fallback init(ctx)", t1)
                    runCatching {
                        cls.getMethod("init", Context::class.java).invoke(instance, appContext)
                    }.onFailure { t2 ->
                        Log.e(TAG, "init(ctx) fail", t2)
                    }
                }
                spiders[spKey] = instance
                instance
            }.onFailure { Log.e(TAG, "getSpider fail", it) }.getOrNull()
        }
    }

    /** homeContent(boolean filter) → JSON {class, list, filters} */
    suspend fun homeContent(spider: Any, filter: Boolean = true): String = withContext(Dispatchers.IO) {
        runCatching {
            spider.javaClass.getMethod("homeContent", Boolean::class.javaPrimitiveType)
                .invoke(spider, filter) as? String ?: ""
        }.onFailure { Log.e(TAG, "homeContent fail", it) }.getOrDefault("")
    }

    /** categoryContent(tid, pg, filter, HashMap extend) → JSON {list, page, pagecount, ...} */
    suspend fun categoryContent(
        spider: Any,
        tid: String,
        pg: Int,
        filter: Boolean = true,
    ): String = withContext(Dispatchers.IO) {
        runCatching {
            spider.javaClass.getMethod(
                "categoryContent",
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType,
                HashMap::class.java,
            ).invoke(spider, tid, pg.toString(), filter, HashMap<String, String>()) as? String ?: ""
        }.onFailure { Log.e(TAG, "categoryContent fail tid=$tid pg=$pg", it) }.getOrDefault("")
    }

    /** detailContent(List ids) → JSON {list} */
    suspend fun detailContent(spider: Any, ids: List<String>): String = withContext(Dispatchers.IO) {
        runCatching {
            spider.javaClass.getMethod("detailContent", List::class.java)
                .invoke(spider, ids) as? String ?: ""
        }.onFailure { Log.e(TAG, "detailContent fail", it) }.getOrDefault("")
    }

    /** searchContent(key, quick) → JSON {list} */
    suspend fun searchContent(spider: Any, key: String, quick: Boolean = true): String =
        withContext(Dispatchers.IO) {
            runCatching {
                spider.javaClass.getMethod(
                    "searchContent",
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                ).invoke(spider, key, quick) as? String ?: ""
            }.onFailure { Log.e(TAG, "searchContent fail key=$key", it) }.getOrDefault("")
        }

    /** playerContent(flag, id, List vipFlags) → JSON {url, header, parse, jx} */
    suspend fun playerContent(spider: Any, flag: String, id: String): String =
        withContext(Dispatchers.IO) {
            runCatching {
                spider.javaClass.getMethod(
                    "playerContent",
                    String::class.java,
                    String::class.java,
                    List::class.java,
                ).invoke(spider, flag, id, emptyList<String>()) as? String ?: ""
            }.onFailure { Log.e(TAG, "playerContent fail flag=$flag id=$id", it) }.getOrDefault("")
        }

    // ---- 内部：jar 下载 / 校验 / 加载 ----

    private suspend fun ensureLoader(jarSpec: String): DexClassLoader? = withContext(Dispatchers.IO) {
        runCatching {
            val jarUrl = jarSpec.substringBefore(";md5;").trim()
            val expectedMd5 = jarSpec.substringAfter(";md5;", "").trim().lowercase()
            val loaderKey = md5(jarUrl)
            loaders[loaderKey]?.let { return@withContext it }

            val lock = locks.computeIfAbsent(loaderKey) { Any() }
            synchronized(lock) {
                loaders[loaderKey]?.let { return@synchronized it }
                val jarFile = File(jarDir, "$loaderKey.jar")

                val cacheValid = jarFile.exists() &&
                    (expectedMd5.isEmpty() || md5(jarFile).equals(expectedMd5, ignoreCase = true))
                if (!cacheValid) {
                    Log.d(TAG, "download jar url=$jarUrl expectMd5=$expectedMd5")
                    runCatching { download(jarUrl, jarFile) }
                        .onFailure {
                            Log.e(TAG, "download jar fail", it)
                            jarFile.delete()
                            return@synchronized null
                        }
                    if (expectedMd5.isNotEmpty()) {
                        val actual = md5(jarFile)
                        if (!actual.equals(expectedMd5, ignoreCase = true)) {
                            Log.e(TAG, "jar md5 mismatch expect=$expectedMd5 actual=$actual")
                            jarFile.delete()
                            return@synchronized null
                        }
                    }
                } else {
                    Log.d(TAG, "use cached jar ${jarFile.absolutePath}")
                }

                // Android 14+ (API 34) 要求动态加载的 jar/apk 为只读
                jarFile.setReadOnly()

                val loader = DexClassLoader(
                    jarFile.absolutePath,
                    dexOutDir.absolutePath,
                    null,
                    appContext.classLoader,
                )
                loaders[loaderKey] = loader
                Log.d(TAG, "jar loaded: ${jarFile.absolutePath}")
                loader
            }
        }.onFailure { Log.e(TAG, "ensureLoader fail", it) }.getOrNull()
    }

    private fun download(url: String, dest: File) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} $url")
            val body = resp.body ?: throw IllegalStateException("empty body $url")
            dest.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
    }

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun md5(file: File): String =
        MessageDigest.getInstance("MD5")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
}
