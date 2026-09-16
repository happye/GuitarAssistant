package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.core.tab.TabBar
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.core.tab.globalBarNumber
import com.guitarcoach.app.core.tab.midi
import com.guitarcoach.app.core.tab.midiToName

/** 解析结果列表：摘要卡 + 逐小节卡（F203 编辑入口挂在这里）。整体一个 LazyColumn，避免嵌套滚动。 */
@Composable
internal fun ParsedTabList(
    doc: TabDocument,
    modifier: Modifier = Modifier,
    onEditBar: (sectionIndex: Int, barIndex: Int) -> Unit = { _, _ -> },
) {
    val totalNotes = doc.sections.sumOf { section -> section.bars.sumOf { it.notes.size } }
    val rows = doc.sections.flatMapIndexed { si, section ->
        section.bars.mapIndexed { bi, bar -> BarRow(si, bi, section.name, doc.globalBarNumber(si, bi), bar) }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "summary") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    doc.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    Text("调弦：${doc.tuning} · 速度：${doc.tempo} BPM", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "共 ${doc.sections.size} 个段落 · ${rows.size} 个小节 · $totalNotes 个音符",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (totalNotes == 0) {
            item(key = "empty-hint") {
                Text(
                    "没有识别到音符——检查谱面是否清晰，或换一张重试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // key 用扁平索引：段落名可能重复（两段都叫 Main），不能拿 名字#小节号 当 key
        itemsIndexed(rows, key = { i, _ -> "bar$i" }) { _, row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${row.sectionName} · 第 ${row.globalNumber} 小节", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { onEditBar(row.sectionIndex, row.barIndex) }) { Text("编辑") }
                    }
                    if (row.bar.notes.isEmpty()) {
                        Text(
                            "（空小节）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            row.bar.notes.joinToString("  ") { note ->
                                "${note.string}弦${note.fret}品(${midiToName(note.midi())})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private data class BarRow(
    val sectionIndex: Int,
    val barIndex: Int,
    val sectionName: String,
    val globalNumber: Int,
    val bar: TabBar,
)
