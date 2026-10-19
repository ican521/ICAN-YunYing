package com.ican.tvplay.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.room.withTransaction
import com.ican.tvplay.data.local.AppDatabase
import com.ican.tvplay.data.local.DeviceEntity
import com.ican.tvplay.data.local.FavoriteEntity
import com.ican.tvplay.data.remote.LocalProxyServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.FormBody

/**
 * 收藏局域网同步（照搬 fongmi SyncDialog + ScanTask + Action.onSync 实现）：
 * - 扫描局域网 x.x.x.1-255:9978 的 /device 接口发现设备；
 * - 点击设备 POST /action?do=sync&mode={0,1,2}&type=keep，表单带 device/targets/configs；
 * - 接收端 handleSync：mode 0=双向（回传+合并），1=上传（仅合并），2=下载（仅回传）；
 *   force=true 时先清空再写入（fongmi 长按触发）。
 */
object FavoriteSync {

    const val MODE_TWO_WAY = 0
    const val MODE_UPLOAD = 1
    const val MODE_DOWNLOAD = 2

    /** 扫描端口：LocalProxyServer 默认优先占用 9978，单端口扫描保证速度（~6s 完成） */
    private const val SCAN_PORT = 9978
    private const val SCAN_TIMEOUT_MS = 1500L
    private const val SYNC_TIMEOUT_MS = 10_000L
    private const val TAG = "FavoriteSync"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 本机设备信息（fongmi Device.get()） */
    fun localDevice(context: Context): DeviceEntity {
        val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val uuid = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        return DeviceEntity(
            uuid = uuid,
            name = Build.MODEL ?: "Android",
            ip = LocalProxyServer.address(),
            type = if (isTv) 0 else 1,
        )
    }

    fun localDeviceJson(context: Context): String =
        json.encodeToString(DeviceEntity.serializer(), localDevice(context))

    /**
     * 扫描局域网内运行同款应用的设备（照搬 fongmi ScanTask）。
     * 发现的设备写入设备表（UI 通过 Room Flow 自动刷新）并经 [onFound] 回调。
     *
     * 关键修复：
     * 1. 用扫描端实际连上的 IP 覆盖设备自报的 ip（手机热点场景下被扫端常报错 IP）；
     * 2. 扫描端口范围 9978~9981，覆盖服务起在非 9978 端口的情况；
     * 3. 枚举本机全部非回环 IPv4 网段（手机开热点时热点网卡常是 ap0/softap0/wlan1）；
     * 4. 增加日志便于排查。
     */
    suspend fun scan(context: Context, onFound: (DeviceEntity) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            // 本机全部非回环 IPv4 地址与对应 /24 网段前缀
            val localIps = mutableSetOf<String>()
            val bases = mutableSetOf<String>()
            runCatching {
                java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                    .flatMap { it.inetAddresses.toList() }
                    .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
                    .mapNotNull { it.hostAddress }
                    .forEach { ip ->
                        localIps += ip
                        bases += ip.substringBeforeLast('.') + "."
                    }
            }
            if (bases.isEmpty()) {
                android.util.Log.w(TAG, "scan: no local IPv4 found, abort")
                return@withContext
            }
            android.util.Log.d(TAG, "scan: localIps=$localIps, bases=$bases")
            val db = AppDatabase.get(context.applicationContext)
            val client = com.github.catvod.net.OkHttp.client(SCAN_TIMEOUT_MS)
            coroutineScope {
                bases.flatMap { base -> (1..255).map { i -> "$base$i" } }
                    .filter { it !in localIps }
                    .map { ip ->
                        async(Dispatchers.IO) {
                            runCatching {
                                com.github.catvod.net.OkHttp
                                    .newCall(client, "http://$ip:$SCAN_PORT/device", "scan")
                                    .execute().use { res ->
                                        if (!res.isSuccessful) return@runCatching
                                        val body = res.body?.string().orEmpty()
                                        if (body.isBlank()) return@runCatching
                                        val device = runCatching {
                                            json.decodeFromString(DeviceEntity.serializer(), body)
                                        }.getOrNull() ?: return@runCatching
                                        if (device.uuid.isBlank()) {
                                            android.util.Log.w(TAG, "scan: $ip:$SCAN_PORT uuid blank, skip")
                                            return@runCatching
                                        }
                                        // 用扫描端实际连上的地址覆盖设备自报的 ip，
                                        // 避免热点场景下被扫端报错 IP（WiFi/蜂窝地址）导致同步失败
                                        val realIp = "http://$ip:$SCAN_PORT"
                                        val fixed = device.copy(ip = realIp)
                                        db.deviceDao().insertOrUpdate(fixed)
                                        android.util.Log.d(TAG, "scan: found ${fixed.name} @ $realIp (uuid=${fixed.uuid})")
                                        onFound(fixed)
                                    }
                            }
                        }
                    }.awaitAll()
            }
            android.util.Log.d(TAG, "scan: done")
            Unit
        }

    /**
     * 向目标设备发送同步请求（照搬 fongmi SyncDialog.send/buildBody）。
     * @param mode 0=双向 1=上传 2=下载
     * @param force true 时接收端先清空再写入（fongmi 长按触发）
     */
    suspend fun send(context: Context, device: DeviceEntity, mode: Int, force: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val app = context.applicationContext
                val url = buildString {
                    append(device.ip.trimEnd('/'))
                    append("/action?do=sync&mode=").append(mode).append("&type=keep")
                    if (force) append("&force=true")
                }
                val localFavs = favoritesJson(app)
                val favCount = runCatching {
                    json.decodeFromString(ListSerializer(FavoriteEntity.serializer()), localFavs).size
                }.getOrDefault(0)
                android.util.Log.d(TAG, "send: mode=$mode force=$force to ${device.name} @ ${device.ip}, localFavs=$favCount, myIp=${LocalProxyServer.address()}")
                val body = FormBody.Builder()
                    .add("device", localDeviceJson(app))
                    .add("targets", localFavs)
                    .add("configs", configsJson(app))
                    .build()
                com.github.catvod.net.OkHttp
                    .newCall(com.github.catvod.net.OkHttp.client(SYNC_TIMEOUT_MS), url, body)
                    .execute().use {
                        val ok = it.isSuccessful
                        android.util.Log.d(TAG, "send: response success=$ok")
                        ok
                    }
            }.getOrElse {
                android.util.Log.e(TAG, "send failed", it)
                false
            }
        }

    /**
     * 接收端处理（照搬 fongmi Action.onSync/syncKeep，NanoHTTPD 线程内同步调用）。
     * mode=0/2 且带 device 字段：把本机收藏回传给对方（回传表单不带 device，避免双向死循环）；
     * mode=0/1：合并对方收藏到本机，force 时先清空。
     */
    fun handleSync(context: Context, params: Map<String, String>): Boolean {
        if (params["type"] != "keep") return false
        val mode = params["mode"] ?: "0"
        val force = params["force"] == "true"
        val app = context.applicationContext
        val db = AppDatabase.get(app)
        val targetsCount = params["targets"]?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(FavoriteEntity.serializer()), raw).size }.getOrDefault(-1)
        } ?: -1
        android.util.Log.d(TAG, "handleSync: mode=$mode force=$force device=${params["device"]?.take(60)} targets=$targetsCount")

        if ((mode == "0" || mode == "2")) {
            params["device"]?.let { deviceJson ->
                runCatching {
                    json.decodeFromString(DeviceEntity.serializer(), deviceJson)
                }.getOrNull()?.takeIf { it.ip.isNotBlank() }?.let { device ->
                    android.util.Log.d(TAG, "handleSync: sendBack to ${device.name} @ ${device.ip}")
                    runBlocking(Dispatchers.IO) { sendBack(app, db, device) }
                }
            }
        }

        if (mode == "0" || mode == "1") {
            val targets = params["targets"]?.let { raw ->
                runCatching {
                    json.decodeFromString(ListSerializer(FavoriteEntity.serializer()), raw)
                }.getOrNull()
            }.orEmpty()
            runBlocking(Dispatchers.IO) {
                db.withTransaction {
                    if (force) db.favoriteDao().clear()
                    targets.forEach { fav ->
                        if (force || !db.favoriteDao().isFavorite(fav.videoId)) {
                            db.favoriteDao().upsert(fav)
                        }
                    }
                }
            }
            android.util.Log.d(TAG, "handleSync: merged ${targets.size} favorites into local DB")
        }
        return true
    }

    /** 回传本机收藏给请求方（fongmi Action.sendKeep/post：mode 固定 0，表单不带 device） */
    private suspend fun sendBack(context: Context, db: AppDatabase, device: DeviceEntity) {
        val favJson = favoritesJson(context, db)
        val favCount = runCatching {
            json.decodeFromString(ListSerializer(FavoriteEntity.serializer()), favJson).size
        }.getOrDefault(0)
        android.util.Log.d(TAG, "sendBack: ${device.name} @ ${device.ip}, localFavs=$favCount")
        val body = FormBody.Builder()
            .add("targets", favJson)
            .add("configs", configsJson(context))
            .build()
        runCatching {
            com.github.catvod.net.OkHttp
                .newCall(
                    com.github.catvod.net.OkHttp.client(SYNC_TIMEOUT_MS),
                    device.ip.trimEnd('/') + "/action?do=sync&mode=0&type=keep",
                    body,
                )
                .execute().use { res ->
                    android.util.Log.d(TAG, "sendBack: response success=${res.isSuccessful}")
                }
        }.onFailure {
            android.util.Log.e(TAG, "sendBack failed to ${device.ip}", it)
        }
    }

    private suspend fun favoritesJson(context: Context, db: AppDatabase? = null): String {
        val database = db ?: AppDatabase.get(context.applicationContext)
        val favorites = database.favoriteDao().observeAll().first()
        return json.encodeToString(ListSerializer(FavoriteEntity.serializer()), favorites)
    }

    private fun configsJson(context: Context): String {
        val url = SettingsRepository(context.applicationContext).configUrl.value
        val configs = if (url.isBlank()) emptyList() else listOf(url)
        return json.encodeToString(ListSerializer(String.serializer()), configs)
    }
}
