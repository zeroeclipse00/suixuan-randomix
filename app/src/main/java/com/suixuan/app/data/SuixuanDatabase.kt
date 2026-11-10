package com.suixuan.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wheel_draft")
data class DraftEntity(
    @PrimaryKey val singletonId: Int = 1,
    val revision: Long,
    val itemsJson: String,
)

@Entity(tableName = "draw_history", indices = [Index(value = ["drawId"], unique = true)])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val drawId: String,
    val snapshotJson: String,
    val committedAtMillis: Long,
)

@Dao
interface SuixuanDao {
    @Query("SELECT * FROM wheel_draft WHERE singletonId = 1")
    fun observeDraft(): Flow<DraftEntity?>

    @Query("SELECT * FROM wheel_draft WHERE singletonId = 1")
    suspend fun getDraft(): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun writeDraft(draft: DraftEntity)

    @Query("SELECT * FROM draw_history ORDER BY sequence DESC")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM draw_history WHERE drawId = :drawId LIMIT 1")
    suspend fun find(drawId: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHistory(record: HistoryEntity): Long

    @Query("DELETE FROM draw_history WHERE sequence NOT IN (SELECT sequence FROM draw_history ORDER BY sequence DESC LIMIT :limit)")
    suspend fun trimHistory(limit: Int)

    @Query("DELETE FROM draw_history")
    suspend fun clearHistory()
}

@Database(entities = [DraftEntity::class, HistoryEntity::class], version = 1, exportSchema = true)
abstract class SuixuanDatabase : RoomDatabase() {
    abstract fun dao(): SuixuanDao

    companion object {
        fun open(context: Context, name: String = "suixuan.db"): SuixuanDatabase =
            Room.databaseBuilder(context.applicationContext, SuixuanDatabase::class.java, name)
                .build()
    }
}
