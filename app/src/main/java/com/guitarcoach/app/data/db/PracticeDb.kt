package com.guitarcoach.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 练习记录（F106）：一次计时练习一条，重启不丢。 */
@Entity(tableName = "practice_records")
data class PracticeRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Int,
    val content: String,
    val note: String,
)

@Dao
interface PracticeRecordDao {

    @Insert
    suspend fun insert(record: PracticeRecordEntity): Long

    @Query("SELECT * FROM practice_records ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<PracticeRecordEntity>>

    @Query("DELETE FROM practice_records WHERE id = :id")
    suspend fun delete(id: Long)
}
