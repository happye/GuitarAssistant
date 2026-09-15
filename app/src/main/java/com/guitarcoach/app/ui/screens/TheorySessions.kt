package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.data.db.ConversationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** F107 会话列表：切换 / 新建 / 重命名 / 删除（删除带二次确认，级联删消息不可恢复）。 */
@Composable
internal fun SessionsDialog(
    conversations: List<ConversationEntity>,
    currentId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
    onRename: (ConversationEntity) -> Unit,
    onDelete: (ConversationEntity) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var deleteTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("会话记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onNew) { Text("+ 新会话") }
                if (conversations.isEmpty()) {
                    Text(
                        "还没有历史会话",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    items(conversations, key = { it.id }) { conversation ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(conversation.id) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    (if (conversation.id == currentId) "● " else "") + conversation.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    dateFormat.format(Date(conversation.updatedAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onRename(conversation) }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "重命名")
                            }
                            IconButton(onClick = { deleteTarget = conversation }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除会话") },
            text = { Text("确定删除「${target.title}」吗？其中的全部消息将一并删除，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
internal fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text("会话名称") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.text.trim().isNotEmpty(),
                onClick = { onConfirm(value.text.trim()) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
