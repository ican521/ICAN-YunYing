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
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "tvplay.db",
            ).fallbackToDestructiveMigration().build()
    }
}
