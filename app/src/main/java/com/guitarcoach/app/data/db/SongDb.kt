package com.guitarcoach.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 曲库（F502）：TabDocument JSON 持久化 + 进度标记。 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val tuning: String,
    val tempo: Int,
    val contentJson: String,   // TabDocument 的 JSON（谱面唯一模型的持久化形态）
    val progress: String,      // learning / mastered / shelved
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface SongDao {

    @Insert
    suspend fun insert(song: SongEntity): Long

    @Update
    suspend fun update(song: SongEntity)

    @Query("SELECT * FROM songs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun delete(id: Long)
}
