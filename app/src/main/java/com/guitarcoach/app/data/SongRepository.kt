package com.guitarcoach.app.data

import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.data.db.SongDao
import com.guitarcoach.app.data.db.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** 曲库条目：解析回 TabDocument 的轻包装（UI 不直接碰 JSON）。 */
data class SongEntry(
    val id: Long,
    val title: String,
    val tuning: String,
    val tempo: Int,
    val progress: String,
    val updatedAt: Long,
    val document: TabDocument?,
)

/**
 * 曲库仓库（F502）：保存 / 搜索（UI 侧过滤）/ 进度标记 / 删除 / 重新加载。
 * JSON 解析失败（历史脏数据）的条目 document 为 null，UI 侧提示不可加载但保留记录。
 */
class SongRepository(private val dao: SongDao) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun observeAll(): Flow<List<SongEntry>> =
        dao.observeAll().map { list -> list.map { it.toEntry() } }

    suspend fun save(document: TabDocument, title: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            SongEntity(
                title = title.ifBlank { "未命名曲目" },
                tuning = document.tuning,
                tempo = document.tempo,
                contentJson = json.encodeToString(TabDocument.serializer(), document),
                progress = PROGRESS_LEARNING,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun updateProgress(id: Long, progress: String) {
        val song = dao.getById(id) ?: return
        dao.update(song.copy(progress = progress, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    private fun SongEntity.toEntry(): SongEntry = SongEntry(
        id = id,
        title = title,
        tuning = tuning,
        tempo = tempo,
        progress = progress,
        updatedAt = updatedAt,
        document = runCatching { json.decodeFromString(TabDocument.serializer(), contentJson) }.getOrNull(),
    )

    companion object {
        const val PROGRESS_LEARNING = "learning"
        const val PROGRESS_MASTERED = "mastered"
        const val PROGRESS_SHELVED = "shelved"

        val PROGRESS_LABELS = mapOf(
            PROGRESS_LEARNING to "在学",
            PROGRESS_MASTERED to "已会",
            PROGRESS_SHELVED to "搁置",
        )
    }
}
