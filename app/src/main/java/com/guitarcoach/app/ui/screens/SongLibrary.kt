package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.data.AppContainer
import com.guitarcoach.app.data.SongEntry
import com.guitarcoach.app.data.SongRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * F502 曲库：识谱工作台的「曲库」入口 + 管理对话框。
 * 保存当前谱面（可命名）、按标题搜索、点进度条循环切换（在学→已会→搁置）、加载、删除。
 */
@Composable
internal fun SongLibrarySection(
    container: AppContainer,
    currentDoc: TabDocument?,
    onLoad: (TabDocument) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    TextButton(onClick = { showDialog = true }) { Text("曲库") }
    if (showDialog) {
        SongLibraryDialog(container = container, currentDoc = currentDoc, onLoad = onLoad, onDismiss = { showDialog = false })
    }
}

@Composable
private fun SongLibraryDialog(
    container: AppContainer,
    currentDoc: TabDocument?,
    onLoad: (TabDocument) -> Unit,
    onDismiss: () -> Unit,
) {
    val entries by container.songRepository.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var searchText by rememberSaveable { mutableStateOf("") }
    var saveTitle by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) } // 删除确认（对抗审查 P2：误触不可逆）
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除曲目？") },
            text = { Text("将从曲库中永久删除该曲目及其进度标记。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.songRepository.delete(target) }
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("曲库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentDoc != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = saveTitle,
                            onValueChange = { saveTitle = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("当前谱面存为…（${currentDoc.title ?: "未命名"}）") },
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    val id = container.songRepository.save(currentDoc, saveTitle.ifBlank { currentDoc.title ?: "" })
                                    saveTitle = ""
                                    message = "已存入曲库（#${id}），重启不丢"
                                }
                            },
                        ) { Text("保存") }
                    }
                }

                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("按标题搜索") },
                    singleLine = true,
                )

                val filtered = entries.filter { it.title.contains(searchText.trim(), ignoreCase = true) }
                if (filtered.isEmpty()) {
                    Text("曲库还是空的——识别或导入一段谱后存进来", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                var renameTarget by remember { mutableStateOf<SongEntry?>(null) }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        SongRow(
                            onLongPress = { renameTarget = entry },
                            entry = entry,
                            dateFormat = dateFormat,
                            onLoad = {
                                val doc = entry.document
                                if (doc == null) {
                                    message = "这条记录解析失败，无法加载"
                                } else {
                                    onLoad(doc)
                                    onDismiss()
                                }
                            },
                            onCycleProgress = {
                                val next = when (entry.progress) {
                                    SongRepository.PROGRESS_LEARNING -> SongRepository.PROGRESS_MASTERED
                                    SongRepository.PROGRESS_MASTERED -> SongRepository.PROGRESS_SHELVED
                                    else -> SongRepository.PROGRESS_LEARNING
                                }
                                scope.launch { container.songRepository.updateProgress(entry.id, next) }
                            },
                            onDelete = { deleteTarget = entry.id },
                        )
                    }
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                renameTarget?.let { target ->
                    var newName by remember { mutableStateOf(target.title) }
                    AlertDialog(
                        onDismissRequest = { renameTarget = null },
                        confirmButton = {
                            TextButton(onClick = {
                                scope.launch { container.songRepository.rename(target.id, newName) }
                                renameTarget = null
                            }) { Text("保存") }
                        },
                        dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
                        title = { Text("重命名") },
                        text = {
                            OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
                        },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    entry: SongEntry,
    dateFormat: SimpleDateFormat,
    onLoad: () -> Unit,
    onCycleProgress: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onLoad, onLongClick = onLongPress)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall)
            Text(
                "${SongRepository.PROGRESS_LABELS[entry.progress]} · ${entry.tuning} · ${dateFormat.format(Date(entry.updatedAt))}" +
                    if (entry.document == null) " · ⚠ 数据损坏" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onCycleProgress) { Text(SongRepository.PROGRESS_LABELS[entry.progress] ?: "在学") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除")
        }
    }
}
