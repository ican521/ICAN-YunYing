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

/** 收藏记录 */
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
)

/** 播放历史（含播放进度） */
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
)

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :videoId)")
    fun observeIsFavorite(videoId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :videoId)")
    suspend fun isFavorite(videoId: String): Boolean

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
    entities = [FavoriteEntity::class, HistoryEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao

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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "tvplay.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
