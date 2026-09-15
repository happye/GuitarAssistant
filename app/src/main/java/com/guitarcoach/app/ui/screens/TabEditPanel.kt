package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.core.tab.NoteEvent
import com.guitarcoach.app.core.tab.TabBar
import com.guitarcoach.app.core.tab.makeNote

/**
 * F203 识别结果编辑：单小节音符逐行改弦/品/拍，可增删行；保存回 TabDocument（唯一谱面模型）。
 * technique 字段保留原值不在此编辑（M2 范围：弦/品/小节修正）。
 */
@Composable
fun TabBarEditDialog(
    sectionName: String,
    barNumber: Int,
    initial: TabBar,
    onSave: (TabBar) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = remember(initial) {
        mutableStateListOf<EditRow>().apply {
            addAll(initial.notes.map { EditRow(it.string.toString(), it.fret.toString(), it.beat.toString(), it.technique) })
        }
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑第 $barNumber 小节（$sectionName）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "弦 1~6（1=高音E） · 品 0~24（0=空弦） · 起始拍",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(rows) { i, row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = row.string,
                                onValueChange = { rows[i] = row.copy(string = it.filter(Char::isDigit).take(1)) },
                                modifier = Modifier.width(64.dp),
                                label = { Text("弦") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = row.fret,
                                onValueChange = { rows[i] = row.copy(fret = it.filter(Char::isDigit).take(2)) },
                                modifier = Modifier.width(64.dp),
                                label = { Text("品") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = row.beat,
                                onValueChange = { rows[i] = row.copy(beat = it.filter { c -> c.isDigit() || c == '.' }.take(6)) },
                                modifier = Modifier.width(76.dp),
                                label = { Text("拍") },
                                singleLine = true,
                            )
                            IconButton(onClick = { rows.removeAt(i) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "删除该音")
                            }
                        }
                    }
                }
                TextButton(onClick = { rows.add(EditRow("1", "0", "0.0", null)) }) { Text("+ 加一个音") }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val notes = mutableListOf<NoteEvent>()
                    var badRow = -1
                    rows.forEachIndexed { i, r ->
                        val note = makeNote(
                            string = r.string.toIntOrNull() ?: -1,
                            fret = r.fret.toIntOrNull() ?: -1,
                            beat = r.beat.toDoubleOrNull() ?: -1.0,
                            technique = r.technique,
                        )
                        if (note == null) {
                            if (badRow < 0) badRow = i
                        } else {
                            notes += note
                        }
                    }
                    if (badRow >= 0) {
                        error = "第 ${badRow + 1} 行不合法：弦 1~6、品 0~24、拍 ≥0"
                    } else {
                        onSave(TabBar(notes.sortedBy { it.beat }))
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private data class EditRow(val string: String, val fret: String, val beat: String, val technique: String?)
