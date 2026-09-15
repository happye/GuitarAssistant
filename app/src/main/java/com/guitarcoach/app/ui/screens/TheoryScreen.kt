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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.data.AppContainer
import kotlinx.coroutines.launch

private data class ChatBubble(val id: Int, val role: String, val text: String) // role: user / assistant / error

/**
 * F101 乐理聊天页：流式输出 + 多轮历史。
 * 链路：askTheory（快答链，DeepSeek 非思考优先）——历史以已完成问答对的形式回传给模型。
 * 范围说明：聊天状态用页面内 remember 存活，切 Tab 即重置——F101 限定；M1 后续按需要升级 ViewModel。
 */
@Composable
fun TheoryScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var bubbles by remember { mutableStateOf(listOf<ChatBubble>()) }
    var input by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 消息增长（新气泡或流式追加）时滚到底部；流式期间用瞬时滚动避免动画堆积
    LaunchedEffect(bubbles.size, bubbles.lastOrNull()?.text?.length) {
        if (bubbles.isNotEmpty()) listState.scrollToItem(bubbles.lastIndex)
    }

    fun send() {
        val question = input.trim()
        if (question.isEmpty() || streaming) return
        input = ""
        bubbles = bubbles +
            ChatBubble(id = bubbles.size, role = "user", text = question) +
            ChatBubble(id = bubbles.size + 1, role = "assistant", text = "")
        streaming = true
        // 多轮历史 = 本次问答之前的已完成对话（跳过错误气泡与空占位）
        val history = bubbles.dropLast(2)
            .filter { (it.role == "user" || it.role == "assistant") && it.text.isNotBlank() }
            .map { it.role to it.text }
        scope.launch {
            try {
                container.coach.askTheory(question, history).collect { delta ->
                    val last = bubbles.last()
                    bubbles = bubbles.dropLast(1) + last.copy(text = last.text + delta)
                }
            } catch (e: Exception) {
                // 流式中断：已显示的半截内容保留，错误追加在同一条气泡里（可重发）
                val last = bubbles.lastOrNull()
                val reason = e.message ?: "未知错误"
                bubbles = if (last != null && last.role == "assistant") {
                    bubbles.dropLast(1) + last.copy(text = last.text + "\n\n❌ 中断了：$reason")
                } else {
                    bubbles.dropLast(1) + ChatBubble(id = last?.id ?: bubbles.size, role = "error", text = "❌ $reason")
                }
            } finally {
                streaming = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("乐理教练", style = MaterialTheme.typography.headlineSmall)
        Text(
            "降key升key、变调夹、五度圈、闷音护弦……随便问；术语都会配大白话解释和吉他上的具体操作。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (bubbles.isEmpty()) {
                item {
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
            items(bubbles, key = { it.id }) { bubble -> ChatBubbleView(bubble) }
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
}

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
