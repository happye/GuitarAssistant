package com.guitarcoach.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.guitarcoach.app.data.db.AppDatabase
import com.guitarcoach.app.data.db.ConversationEntity
import com.guitarcoach.app.data.db.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 对话历史仓库（F107）：TheoryScreen 只与此层交互，不直接触碰 Room。
 * 流式回复期间 UI 在内存累积，结束时一次性落库，避免逐 delta 写库。
 *
 * 容错：流式进行中用户可能删除该会话（外键级联已删消息），随后的落库会触发
 * 外键约束异常——此时写入目标已无意义，集中在此静默丢弃，避免异常从协程逃逸崩溃。
 */
class ChatRepository(private val db: AppDatabase) {

    private val conversations = db.conversationDao()
    private val messages = db.messageDao()

    fun observeConversations(): Flow<List<ConversationEntity>> = conversations.observeAll()

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
        messages.observeByConversation(conversationId)

    suspend fun createConversation(title: String): Long {
        val now = System.currentTimeMillis()
        return conversations.insert(ConversationEntity(title = title, createdAt = now, updatedAt = now))
    }

    suspend fun renameConversation(id: Long, title: String) {
        conversations.rename(id, title, System.currentTimeMillis())
    }

    suspend fun deleteConversation(conversation: ConversationEntity) {
        conversations.delete(conversation)
    }

    /** 追加一条消息并把会话的 updatedAt 顶到最新（单事务，避免中途断开留半态）。 */
    suspend fun appendMessage(conversationId: Long, role: String, content: String) {
        try {
            db.withTransaction {
                messages.insertAll(
                    listOf(
                        MessageEntity(
                            conversationId = conversationId,
                            role = role,
                            content = content,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                )
                conversations.touch(conversationId, System.currentTimeMillis())
            }
        } catch (_: SQLiteConstraintException) {
            // 会话在流式期间被删除：写入目标已不存在，静默丢弃
        }
    }
}
