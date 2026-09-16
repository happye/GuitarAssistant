package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.data.AppContainer
import com.guitarcoach.app.data.db.MessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STREAMING_BUBBLE_ID = -1L

/**
 * F101+F107 乐理聊天页：流式输出 + 多轮历史 + 多会话管理（Room 持久化）。
 * 流式期间文本在内存累积（streamingText），结束/中断时一次性落库——避免逐 delta 写库。
 * 会话列表/重命名/删除见 TheorySessions.kt。
 */
@Composable
fun TheoryScreen(container: AppContainer) {
    val repo = container.chatRepository
    val scope = rememberCoroutineScope()
    val conversations by repo.observeConversations().collectAsState(initial = emptyList())
    var currentId by rememberSaveable { mutableStateOf<Long?>(null) }
    val messages by repo.observeMessages(currentId ?: NO_CONVERSATION).collectAsState(initial = emptyList())
    var streamingText by rememberSaveable { mutableStateOf<String?>(null) }
    var streamingConvId by rememberSaveable { mutableStateOf<Long?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }
    var showSessions by rememberSaveable { mutableStateOf(false) }
    var showCards by rememberSaveable { mutableStateOf(false) }
    var showFretboard by rememberSaveable { mutableStateOf(false) }
    var showTranspose by rememberSaveable { mutableStateOf(false) }
    var showToneWizard by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<com.guitarcoach.app.data.db.ConversationEntity?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, streamingText?.length) {
        val count = messages.size + if (streaming && streamingText != null) 1 else 0
        if (count > 0) listState.scrollToItem(count - 1)
    }

    fun send() {
        val question = input.trim()
        if (question.isEmpty() || streaming) return
        input = ""
        scope.launch {
            // 历史取自落库消息（不含本轮）；流式回复结束/中断后整段落库
            val history = messages
                .filter { it.role == "user" || it.role == "assistant" }
                .filter { it.content.isNotBlank() }
                .map { it.role to it.content }

            var convId = currentId
            if (convId == null) {
                convId = repo.createConversation(title = question.take(24))
                currentId = convId
            }
            val conversationId = convId
            repo.appendMessage(conversationId, "user", question)

            streaming = true
            streamingText = null
            streamingConvId = conversationId
            try {
                val sb = StringBuilder()
                container.coach.askTheory(question, history).collect { delta ->
                    sb.append(delta)
                    streamingText = sb.toString()
                }
                repo.appendMessage(conversationId, "assistant", sb.toString().ifBlank { "（空回复）" })
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    streamingText?.takeIf { it.isNotBlank() }?.let {
                        repo.appendMessage(conversationId, "assistant", it)
                    }
                }
                throw e
            } catch (e: Exception) {
                val partial = streamingText ?: ""
                val text = if (partial.isBlank()) "❌ ${e.message ?: "未知错误"}"
                else "$partial\n\n❌ 中断了：${e.message ?: "未知错误"}（可重发）"
                repo.appendMessage(conversationId, "assistant", text)
            } finally {
                streaming = false
                streamingText = null
                streamingConvId = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("乐理教练", style = MaterialTheme.typography.headlineSmall)
                Text(
                    currentId?.let { id -> conversations.firstOrNull { it.id == id }?.title ?: "当前会话" } ?: "新会话（发送后自动创建）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { showSessions = true }) { Text("会话 (${conversations.size})") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = { showCards = true }) { Text("概念卡片") }
            TextButton(onClick = { showFretboard = true }) { Text("指板可视化") }
            TextButton(onClick = { showTranspose = true }) { Text("移调/变调夹") }
            TextButton(onClick = { showToneWizard = true }) { Text("音色向导") }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty() && streamingText == null) {
                item(key = "suggestions") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "降key和升key是什么意思？",
                            "变调夹夹3品是什么效果？",
                            "强力和弦怎么按？",
                        ).forEach { suggestion ->
                            AssistChip(onClick = { input = suggestion }, label = { Text(suggestion) })
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                ChatBubbleView(bubble = message.toBubble())
            }
            streamingText?.let { text ->
                // 流式气泡只属于发起它的会话：中途切换会话时不在新会话里显示旧回复
                if (streaming && (currentId == null || currentId == streamingConvId)) {
                    item(key = STREAMING_BUBBLE_ID) {
                        ChatBubbleView(ChatBubble(STREAMING_BUBBLE_ID.toInt(), "assistant", text.ifBlank { "…" }))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("问点什么，比如：降key是什么意思？") },
                maxLines = 4,
            )
            Button(
                onClick = { send() },
                enabled = !streaming && input.isNotBlank(),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Text(if (streaming) "…" else "发送")
            }
        }
    }

    if (showSessions) {
        SessionsDialog(
            conversations = conversations,
            currentId = currentId,
            onDismiss = { showSessions = false },
            onSelect = {
                currentId = it
                showSessions = false
            },
            onNew = {
                currentId = null
                showSessions = false
            },
            onRename = {
                renameTarget = it
                showSessions = false
            },
            onDelete = { conversation ->
                scope.launch {
                    repo.deleteConversation(conversation)
                    if (currentId == conversation.id) currentId = null
                }
            },
        )
    }

    if (showCards) {
        ConceptCardsDialog(onDismiss = { showCards = false })
    }
    if (showFretboard) {
        FretboardDialog(onDismiss = { showFretboard = false })
    }
    if (showTranspose) {
        TransposeDialog(onDismiss = { showTranspose = false })
    }
    if (showToneWizard) {
        ToneWizardDialog(container = container, onDismiss = { showToneWizard = false })
    }
    renameTarget?.let { target ->
        RenameDialog(
            initial = target.title,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                scope.launch { repo.renameConversation(target.id, newTitle) }
                renameTarget = null
            },
        )
    }
}

private fun MessageEntity.toBubble() = ChatBubble(id = id.toInt(), role = role, text = content)

@Composable
private fun ChatBubbleView(bubble: ChatBubble) {
    val isUser = bubble.role == "user"
    val isError = bubble.role == "error"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = when {
                isError -> MaterialTheme.colorScheme.errorContainer
                isUser -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = when {
                isError -> MaterialTheme.colorScheme.onErrorContainer
                isUser -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                bubble.text.ifBlank { "…" },
                modifier = Modifier
                    .padding(10.dp)
                    .widthIn(max = 300.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private data class ChatBubble(val id: Int, val role: String, val text: String)

private const val NO_CONVERSATION = -1L
