package com.guitarcoach.app.data

import androidx.room.withTransaction
import com.guitarcoach.app.data.db.AppDatabase
import com.guitarcoach.app.data.db.PracticeRecordEntity
import kotlinx.coroutines.flow.Flow

/** 练习记录仓库（F106）：计时入口在练习室，记录列表在「我的」。 */
class PracticeRepository(private val db: AppDatabase) {

    private val dao = db.practiceRecordDao()

    fun observeRecords(): Flow<List<PracticeRecordEntity>> = dao.observeAll()

    suspend fun save(startedAt: Long, durationSeconds: Int, content: String, note: String) {
        db.withTransaction {
            dao.insert(
                PracticeRecordEntity(
                    startedAt = startedAt,
                    endedAt = startedAt + durationSeconds * 1000L,
                    durationSeconds = durationSeconds,
                    content = content.ifBlank { "自由练习" },
                    note = note,
                )
            )
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
