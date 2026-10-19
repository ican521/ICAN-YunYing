package com.ican.tvplay.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.room.withTransaction
import com.ican.tvplay.data.local.AppDatabase
import com.ican.tvplay.data.local.FavoriteEntity
import com.ican.tvplay.data.local.HistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 备份与恢复管理器（照搬 fongmi BackupManager / Backup / Prefers 方案）。
 *
 * 核心对齐点：
 * 1. fongmi `Backup.prefers` 用 `Map<String, ?>` 保留原始类型（Gson 序列化），
 *    这里用 `Map<String, JsonElement>` 等价实现 —— Boolean/Int/Float/StringSet 不再被 toString() 毁掉；
 * 2. 恢复时分派写回（fongmi `Prefers.put(key, obj)` 同款：按运行时类型 putString/putBoolean/putFloat/putInt），
 *    修复旧版恢复后重启 getBoolean()/getInt() 抛 ClassCastException 闪退的问题；
 * 3. 恢复顺序与 fongmi `Backup.restore()` 一致：先清库 → 写数据 → 最后写 prefs。
 *
 * 备份内容：SharedPreferences 全部设置 + 收藏表 + 历史表。
 * JSON 格式，通过 SAF 由用户选择文件位置。
 */
object BackupManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class BackupData(
        val version: Int = 2,
        val timestamp: Long = System.currentTimeMillis(),
        /** 保留原始类型的 prefs（fongmi: Map<String, ?>） */
        val prefers: Map<String, JsonElement> = emptyMap(),
        val favorites: List<FavoriteEntity> = emptyList(),
        val history: List<HistoryEntity> = emptyList(),
    )

    /** SharedPreferences 原始值 → JsonElement（保留类型，等价 fongmi Gson 直接序列化 Object） */
    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Int -> JsonPrimitive(this)
        is Long -> JsonPrimitive(this)
        is Float -> JsonPrimitive(this)
        is Double -> JsonPrimitive(this)
        is Set<*> -> JsonArray(mapNotNull { (it as? String)?.let(::JsonPrimitive) })
        else -> JsonPrimitive(toString())
    }

    /**
     * JsonElement → 分派写回 SharedPreferences（照搬 fongmi `Prefers.put` 的类型分派）。
     * JsonNull 跳过（fongmi: `if (obj == null) return`）。
     */
    private fun SharedPreferences.Editor.putTyped(key: String, el: JsonElement) {
        when (el) {
            is JsonNull -> Unit
            // StringSet（fongmi 无此类型，此处对应我们的 search_history / drive_configs）
            is JsonArray -> putStringSet(
                key,
                el.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.toSet(),
            )
            is JsonPrimitive -> when {
                el.isString -> putString(key, el.content)
                el.booleanOrNull != null -> putBoolean(key, el.booleanOrNull!!)
                // fongmi: Number 含 '.' → Float，否则 Int
                el.content.contains('.') -> el.floatOrNull?.let { putFloat(key, it) }
                    ?: el.content.toDoubleOrNull()?.let { putFloat(key, it.toFloat()) }
                    ?: Unit
                el.intOrNull != null -> putInt(key, el.intOrNull!!)
                // 超出 Int 范围的数字兜底 Long（fongmi 会截断为 int，这里更稳）
                el.longOrNull != null -> putLong(key, el.longOrNull!!)
                else -> Unit
            }
            // prefs 不会出现 JsonObject，忽略
            else -> Unit
        }
    }

    /** 导出备份到指定 Uri（SAF 返回的文件） */
    suspend fun backup(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.applicationContext
                .getSharedPreferences("tvplay_settings", Context.MODE_PRIVATE)
            // 保留原始类型（fongmi: backup.setPrefers(Prefers.getPrefers().getAll())）
            val prefersMap = prefs.all.mapValues { (_, v) -> v.toJsonElement() }

            val db = AppDatabase.get(context.applicationContext)
            val favorites = db.favoriteDao().observeAll().first()
            val history = db.historyDao().observeAll().first()

            val data = BackupData(
                prefers = prefersMap,
                favorites = favorites,
                history = history,
            )

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    /** 从指定 Uri 恢复备份（顺序照搬 fongmi `Backup.restore()`：先清库写数据，最后写 prefs） */
    suspend fun restore(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@runCatching false
            val data = json.decodeFromString(BackupData.serializer(), content)

            // 恢复数据库：先清空再写入（fongmi: clearAllTables() 后 insertOrUpdate）
            val db = AppDatabase.get(context.applicationContext)
            db.withTransaction {
                db.favoriteDao().clear()
                db.historyDao().clear()
                data.favorites.forEach { db.favoriteDao().upsert(it) }
                data.history.forEach { db.historyDao().upsert(it) }
            }

            // 恢复 SharedPreferences：按类型分派写回（fongmi: Prefers.put(key, entry.getValue())）
            val prefs = context.applicationContext
                .getSharedPreferences("tvplay_settings", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            data.prefers.forEach { (k, v) -> editor.putTyped(k, v) }
            editor.apply()
            true
        }.getOrDefault(false)
    }
}
