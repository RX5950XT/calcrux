package com.calcrux.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Entity ────────────────────────────────────────────────────────────────────

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expression: String,
    val result: String,
    /** Tab that produced this entry: "calculator", "convert", "loan" */
    val source: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

// ── DAO ───────────────────────────────────────────────────────────────────────

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestampMs DESC")
    fun allEntries(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE expression LIKE '%' || :q || '%' OR result LIKE '%' || :q || '%' ORDER BY timestampMs DESC")
    fun search(q: String): Flow<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry)

    @Delete
    suspend fun delete(entry: HistoryEntry)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(entities = [HistoryEntry::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
