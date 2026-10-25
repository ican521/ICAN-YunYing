package com.ican.tvplay.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** 收藏记录 */
@Serializable
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val cover: String,
    val categoryName: String,
    val addedAt: Long,
    /** 视频所在站源 key：点击收藏时按此站源拉详情并播放（空 = 旧数据，兜底当前站） */
    val sourceKey: String = "",
    /** 站源显示名（仅取 | 前名称），收藏卡片右上角标签用 */
    val sourceName: String = "",
    /** 追更：最近一次检测到的总集数（0 = 尚未检测） */
    val episodeCount: Int = 0,
    /** 追更：已确认看过 / 已忽略的集数基线（前 N 集视为已看） */
    val ackEpisodeCount: Int = 0,
    /** 追更：最近一次集数增加的时间（0 = 从未增加） */
    val lastUpdatedAt: Long = 0,
    /** 追更：最近一次成功检测时间（0 = 从未检测） */
    val lastCheckedAt: Long = 0,
)

/** 播放历史（含播放进度） */
@Serializable
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val cover: String,
    val categoryName: String,
    val episodeIndex: Int,
    val episodeTitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /** 片头标记：开播自动 seek 到该位置（fongmi: History.opening，按剧存储） */
    val openingMs: Long = 0,
    /** 片尾标记：距结尾的时长，播到该点自动切下一集（fongmi: History.ending） */
    val endingMs: Long = 0,
    /** 视频所在站源 key：点击历史时按此站源拉详情并续播（空 = 旧数据，兜底当前站） */
    val sourceKey: String = "",
    /** 站源显示名（仅取 | 前名称），历史卡片与播放页站源行用 */
    val sourceName: String = "",
    /** 上次播放所选线路 flag：从历史续播时恢复同一条线路 */
    val lineFlag: String = "",
)

/** 局域网同步设备（fongmi Device 精简版：uuid/name/ip/type，type 0=电视 1=手机） */
@Serializable
@Entity(
    tableName = "devices",
    indices = [androidx.room.Index(value = ["uuid", "name"], unique = true)],
)
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uuid: String = "",
    val name: String = "",
    /** 完整地址，含协议与端口，如 http://192.168.1.5:9978 */
    val ip: String = "",
    val type: Int = 0,
)

@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices")
    fun observeAll(): Flow<List<DeviceEntity>>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: DeviceEntity)

    @Query("DELETE FROM devices")
    suspend fun clear()
}

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :videoId)")
    fun observeIsFavorite(videoId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :videoId)")
    suspend fun isFavorite(videoId: String): Boolean

    /** 追更检测：一次性取全部收藏快照 */
    @Query("SELECT * FROM favorites")
    suspend fun getAll(): List<FavoriteEntity>

    /** 追更检测：写回集数/已看基线/更新时间 */
    @Query(
        "UPDATE favorites SET episodeCount = :episodeCount, ackEpisodeCount = :ackEpisodeCount, " +
            "lastUpdatedAt = :lastUpdatedAt, lastCheckedAt = :lastCheckedAt WHERE videoId = :videoId",
    )
    suspend fun updateTracking(
        videoId: String,
        episodeCount: Int,
        ackEpisodeCount: Int,
        lastUpdatedAt: Long,
        lastCheckedAt: Long,
    )

    /** 标记单部已读：把已看基线推到当前总集数 */
    @Query("UPDATE favorites SET ackEpisodeCount = episodeCount WHERE videoId = :videoId")
    suspend fun acknowledge(videoId: String)

    /** 全部标记已读 */
    @Query("UPDATE favorites SET ackEpisodeCount = episodeCount")
    suspend fun acknowledgeAll()

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE videoId = :videoId LIMIT 1")
    fun observeOne(videoId: String): Flow<HistoryEntity?>

    @Upsert
    suspend fun upsert(history: HistoryEntity)

    @Query("DELETE FROM history WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Database(
    entities = [FavoriteEntity::class, HistoryEntity::class, DeviceEntity::class],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun deviceDao(): DeviceDao

    companion object {
        /** v1→v2：history 表增加片头/片尾标记列 */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN openingMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE history ADD COLUMN endingMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2→v3：favorites 表增加站源 key/显示名列（旧收藏留空，播放时兜底当前站） */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorites ADD COLUMN sourceKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE favorites ADD COLUMN sourceName TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3→v4：history 表增加站源 key/显示名列（旧历史留空，续播时兜底当前站） */
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN sourceKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE history ADD COLUMN sourceName TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4→v5：新增局域网同步设备表（fongmi Device） */
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS devices (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "uuid TEXT NOT NULL DEFAULT '', " +
                        "name TEXT NOT NULL DEFAULT '', " +
                        "ip TEXT NOT NULL DEFAULT '', " +
                        "type INTEGER NOT NULL DEFAULT 0)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_devices_uuid_name ON devices (uuid, name)")
            }
        }

        /** v5→v6：history 表增加 lineFlag 列（上次播放所选线路，旧数据留空兜底首条线路） */
        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN lineFlag TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v6→v7：favorites 表增加追更字段（集数/已看基线/更新时间/检测时间，旧数据从 0 开始建基线） */
        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorites ADD COLUMN episodeCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE favorites ADD COLUMN ackEpisodeCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE favorites ADD COLUMN lastUpdatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE favorites ADD COLUMN lastCheckedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "tvplay.db",
            )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                )
                .fallbackToDestructiveMigration()
                .build()

        /** 全局单例（fongmi AppDatabase.get 同款）：UI、同步、备份共用同一连接，
         *  保证写入能触发对方 Room Flow 的失效通知，避免各开各的连接导致 WAL 不可见。 */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(context.applicationContext).also { INSTANCE = it }
            }
    }
}
