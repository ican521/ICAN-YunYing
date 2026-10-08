package com.ican.tvplay.data.remote

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        .dns(FallbackDns)
        .connectionPool(VodApiClient.sharedPool)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** spider jar 非线程安全：同一实例的全部反射调用必须串行（fongmi 用单线程执行器，这里每实例一把 Mutex） */
    private val spiderLocks = ConcurrentHashMap<Any, Mutex>()

    private fun lockOf(spider: Any): Mutex = spiderLocks.computeIfAbsent(spider) { Mutex() }

    companion object {
        // 全局缓存：SpiderManager 可能随 ViewModel 重建，jar/classloader/spider 实例
        // 必须跨实例常驻（fongmi 同款做法），否则每次进搜索页全部站点重新加载 init，极慢
        private val locks = ConcurrentHashMap<String, Any>()

        /** key = md5(jarUrl) → 已加载的 DexClassLoader */
        private val loaders = ConcurrentHashMap<String, DexClassLoader>()

        /** key = md5(jarUrl) → guard jar 的 native loader 是否就绪（非 guard jar 记 true） */
        private val guardReady = ConcurrentHashMap<String, Boolean>()

        /** key = md5(jarUrl)|siteKey → 已初始化的 Spider 实例（反射持有） */
        private val spiders = ConcurrentHashMap<String, Any>()

        private var jarDirRef: File? = null
        private var dexOutDirRef: File? = null

        fun jarDir(ctx: Context): File =
            jarDirRef ?: File(ctx.filesDir, "spider_jars").apply { mkdirs() }.also { jarDirRef = it }

        fun dexOutDir(ctx: Context): File =
            dexOutDirRef ?: File(ctx.codeCacheDir, "spider_dex").apply { mkdirs() }.also { dexOutDirRef = it }
    }

    private val jarDir: File by lazy { jarDir(appContext) }
    private val dexOutDir: File by lazy { dexOutDir(appContext) }

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
        val loaderKey = md5(jarUrl)
        val loader = ensureLoader(jarSpec) ?: return null
        // guard jar 的 native loader 未就绪时实例化 Guard 类会触发 JNI SIGABRT，必须跳过
        if (guardReady[loaderKey] == false) {
            Log.e(TAG, "skip guarded spider ${site.api}: native loader unavailable")
            return null
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                // 同一站点实例化串行化：init 不能并发进 jar（并发 init 会触发 jar 内部崩溃）
                val instanceLock = locks.computeIfAbsent("init|$spKey") { Any() }
                synchronized(instanceLock) {
                    spiders[spKey]?.let { return@synchronized it }
                    val className = "com.github.catvod.spider." + site.api.removePrefix("csp_")
                    Log.d(TAG, "loadSpider class=$className key=${site.key}")
                    val cls = loader.loadClass(className)
                    val instance = cls.getDeclaredConstructor().newInstance()
                    Log.d(TAG, "spider instantiated: $className")
                    // 与 fongmi JarLoader 一致：设置 Spider.siteKey 公共字段
                    runCatching {
                        cls.getField("siteKey").set(instance, site.key)
                    }.onFailure { Log.w(TAG, "set siteKey fail", it) }
                    val extString = site.extAsString()
                    // 优先 init(Context, String)；失败回退 init(Context)
                    runCatching {
                        cls.getMethod("init", Context::class.java, String::class.java)
                            .invoke(instance, appContext, extString)
                        Log.d(TAG, "spider init(ctx,ext) done: $className")
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
                }
            }.onFailure { Log.e(TAG, "getSpider fail", it) }.getOrNull()
        }
    }

    /**
     * 并发预下载并加载全部站点 jar（fongmi 式预加载）：导入配置后调用，
     * 之后任意站点的搜索/详情/播放不再有 jar 下载与类加载的首次开销。
     */
    suspend fun prewarmJars(jarSpecs: Collection<String>) {
        val specs = jarSpecs.filter { it.isNotBlank() }.distinct()
        if (specs.isEmpty()) return
        coroutineScope {
            specs.map { spec -> async(Dispatchers.IO) { runCatching { ensureLoader(spec) } } }.awaitAll()
        }
        Log.d(TAG, "prewarmJars done count=${specs.size}")
    }

    /** homeContent(boolean filter) → JSON {class, list, filters} */
    suspend fun homeContent(spider: Any, filter: Boolean = true): String = withContext(Dispatchers.IO) {
        lockOf(spider).withLock {
            runCatching {
                val result = spider.javaClass.getMethod("homeContent", Boolean::class.javaPrimitiveType)
                    .invoke(spider, filter) as? String ?: ""
                Log.d(TAG, "homeContent bytes=${result.length} head=${result.take(120)}")
                result
            }
        }.onFailure { Log.e(TAG, "homeContent fail", it) }.getOrDefault("")
    }

    /** categoryContent(tid, pg, filter, HashMap extend) → JSON {list, page, pagecount, ...} */
    suspend fun categoryContent(
        spider: Any,
        tid: String,
        pg: Int,
        filter: Boolean = true,
    ): String = withContext(Dispatchers.IO) {
        lockOf(spider).withLock {
            runCatching {
                spider.javaClass.getMethod(
                    "categoryContent",
                    String::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    HashMap::class.java,
                ).invoke(spider, tid, pg.toString(), filter, HashMap<String, String>()) as? String ?: ""
            }
        }.onFailure { Log.e(TAG, "categoryContent fail tid=$tid pg=$pg", it) }.getOrDefault("")
    }

    /** detailContent(List ids) → JSON {list} */
    suspend fun detailContent(spider: Any, ids: List<String>): String = withContext(Dispatchers.IO) {
        lockOf(spider).withLock {
            runCatching {
                spider.javaClass.getMethod("detailContent", List::class.java)
                    .invoke(spider, ids) as? String ?: ""
            }
        }.onFailure { Log.e(TAG, "detailContent fail", it) }.getOrDefault("")
    }

    /** searchContent(key, quick) → JSON {list} */
    suspend fun searchContent(spider: Any, key: String, quick: Boolean = true): String =
        withContext(Dispatchers.IO) {
            lockOf(spider).withLock {
                runCatching {
                    spider.javaClass.getMethod(
                        "searchContent",
                        String::class.java,
                        Boolean::class.javaPrimitiveType,
                    ).invoke(spider, key, quick) as? String ?: ""
                }
            }.onFailure { Log.e(TAG, "searchContent fail key=$key", it) }.getOrDefault("")
        }

    /** playerContent(flag, id, List vipFlags) → JSON {url, header, parse, jx} */
    suspend fun playerContent(spider: Any, flag: String, id: String): String =
        withContext(Dispatchers.IO) {
            lockOf(spider).withLock {
                runCatching {
                    spider.javaClass.getMethod(
                        "playerContent",
                        String::class.java,
                        String::class.java,
                        List::class.java,
                    ).invoke(spider, flag, id, emptyList<String>()) as? String ?: ""
                }
            }.onFailure { Log.e(TAG, "playerContent fail flag=$flag id=$id", it) }.getOrDefault("")
        }

    /**
     * 完整播放地址解析：spider 走 playerContent + 可选 jx/parse 二次解析，HTTP 站直接返回原 URL。
     * 供播放页延迟解析使用（某些网盘 jar 在 playerContent 时触发扫码弹窗，须在进入播放页后调用）。
     */
    suspend fun resolvePlayUrl(
        site: TvBoxSite,
        jarSpec: String,
        playUrl: String,
        flag: String,
    ): com.ican.tvplay.data.PlaySource = withContext(Dispatchers.IO) {
        if (!site.isSpider()) return@withContext com.ican.tvplay.data.PlaySource(playUrl)
        val spider = getSpider(site, jarSpec)
            ?: return@withContext com.ican.tvplay.data.PlaySource(playUrl)
        val jsonStr = playerContent(spider, flag.ifBlank { site.key }, playUrl)
        if (jsonStr.isBlank()) return@withContext com.ican.tvplay.data.PlaySource(playUrl)
        runCatching {
            val json = Json { ignoreUnknownKeys = true }
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            val rawUrl = obj["url"]?.jsonPrimitive?.content ?: playUrl
            val headers = mutableMapOf<String, String>()
            obj["header"]?.let { el ->
                runCatching {
                    el.jsonObject.entries.forEach { (k, v) ->
                        headers[k] = v.jsonPrimitive.content
                    }
                }
            }
            val parse = obj["parse"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val jx = obj["jx"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            Log.d(TAG, "resolvePlayUrl rawUrl=$rawUrl parse=$parse jx=$jx")
            val finalUrl = if (com.ican.tvplay.data.remote.JxParser.needsParse(rawUrl, parse, jx)) {
                com.ican.tvplay.data.remote.JxParser.resolve(rawUrl, headers).ifBlank { rawUrl }
            } else {
                rawUrl
            }
            com.ican.tvplay.data.PlaySource(url = finalUrl.ifBlank { playUrl }, headers = headers)
        }.getOrNull() ?: com.ican.tvplay.data.PlaySource(playUrl)
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
                    dexOutDir.absolutePath,
                    appContext.classLoader,
                )
                // 与 fongmi JarLoader 一致：加载后立即调用 com.github.catvod.spider.Init.init(Context)
                // （饭太硬等 guard jar 需要借此初始化 native loader；普通 jar 无该类则忽略）
                invokeInit(loader)
                guardReady[loaderKey] = checkGuardReady(loader, loaderKey)
                loaders[loaderKey] = loader
                Log.d(TAG, "jar loaded: ${jarFile.absolutePath}")
                loader
            }
        }.onFailure { Log.e(TAG, "ensureLoader fail", it) }.getOrNull()
    }

    /** 调用 jar 内 com.github.catvod.spider.Init.init(Context)（静态方法，可选存在） */
    private fun invokeInit(loader: DexClassLoader) {
        // guard jar（饭太硬系）Init.init 会只读打开宿主 App 的 databases/tv 数据库
        // （fongmi/TVBox 宿主的 Room 库名即 tv）；宿主无此文件时 jar 内部错误分支会
        // post 一个携带 null Throwable 的 Runnable 到主线程导致闪退，这里对齐 fongmi 宿主环境
        runCatching {
            appContext.openOrCreateDatabase("tv", Context.MODE_PRIVATE, null).close()
        }.onFailure { Log.w(TAG, "ensure host tv database fail: ${it.message}") }
        runCatching {
            val clz = loader.loadClass("com.github.catvod.spider.Init")
            clz.getMethod("init", Context::class.java).invoke(null, appContext)
            Log.d(TAG, "Init.init done")
        }.onFailure { Log.w(TAG, "Init.init fail (plain jar?) : ${it.javaClass.simpleName}") }
    }

    /**
     * guard jar 就绪检测：jar 内含 com.github.catvod.spider.Init 时（饭太硬系加壳 jar），
     * 其 native getLoader 必须返回非空 DexClassLoader，否则实例化 Guard 类会 JNI 崩溃。
     * 返回 true = 可安全实例化（含非 guard jar）。
     */
    private fun checkGuardReady(loader: DexClassLoader, loaderKey: String): Boolean {
        val initCls = runCatching { loader.loadClass("com.github.catvod.spider.Init") }.getOrNull()
        if (initCls == null) {
            Log.d(TAG, "plain jar (no Init), safe to instantiate")
            return true
        }
        repeat(3) { attempt ->
            val nativeLoader = runCatching { initCls.getMethod("loader").invoke(null) }.getOrNull()
            if (nativeLoader != null) {
                Log.d(TAG, "guard native loader ready (attempt ${attempt + 1}): $nativeLoader")
                return true
            }
            Log.w(TAG, "guard native loader null, retry Init.init (attempt ${attempt + 1})")
            runCatching { initCls.getMethod("init", Context::class.java).invoke(null, appContext) }
            Thread.sleep(300)
        }
        Log.e(TAG, "guard native loader NOT ready for jar $loaderKey (native getLoader 失败，疑似签名/系统版本不兼容)，跳过该 jar 的 Guard 类实例化")
        return false
    }

    private fun download(url: String, dest: File) {
        val req = Request.Builder()
            .url(url)
            // 与 fongmi 默认 UA 一致（okhttp 默认）；某些站点对浏览器 UA 返回伪装 HTML
            .header("User-Agent", "okhttp/4.12.0")
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
