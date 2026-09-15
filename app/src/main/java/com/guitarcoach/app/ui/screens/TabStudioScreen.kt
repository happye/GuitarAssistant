package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.core.tab.TextTabParser
import com.guitarcoach.app.core.tab.midi
import com.guitarcoach.app.core.tab.midiToName

/**
 * F102 识谱工作台（M1 阶段）：粘贴 ASCII 六线谱 → TextTabParser 解析 → 结构化展示。
 * 每颗音标注「弦-品-实际音名」，帮助初学者把谱面和指板上位置对上号。
 * 拍谱识谱（视觉链）与 Guitar Pro 导入在 M2 接入；播放/试听在 M1 后续特性。
 */
@Composable
fun TabStudioScreen() {
    var pasteText by remember { mutableStateOf("") }
    var document by remember { mutableStateOf<TabDocument?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("识谱工作台", style = MaterialTheme.typography.headlineSmall)
        Text(
            "M1 先支持粘贴文本六线谱（Ultimate Guitar 风格）；拍谱识谱在 M2 接入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = pasteText,
            onValueChange = { pasteText = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            placeholder = {
                Text(
                    "粘贴六线谱，例如：\ne|--------3---|\nB|------3---3-|\nG|----0-------|\nD|--0---------|\nA|------------|\nE|------------|",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    document = null
                    error = null
                    try {
                        document = TextTabParser.parse(pasteText)
                    } catch (e: Exception) {
                        error = e.message ?: "解析失败"
                    }
                },
                enabled = pasteText.isNotBlank(),
            ) { Text("解析") }
        }

        error?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("❌ $it", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        document?.let { doc ->
            ParsedTabList(doc, modifier = Modifier.weight(1f))
        }
    }
}

/** 解析结果列表：摘要卡 + 逐小节卡。整体一个 LazyColumn，避免嵌套滚动。 */
@Composable
private fun ParsedTabList(doc: TabDocument, modifier: Modifier = Modifier) {
    val totalNotes = doc.sections.sumOf { section -> section.bars.sumOf { it.notes.size } }
    val rows = doc.sections.flatMap { section ->
        section.bars.mapIndexed { barIndex, bar -> Triple(section.name, barIndex, bar) }
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
                    "没有识别到音符——检查粘贴内容是否为 6 行弦线 + 数字的格式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // key 用扁平索引：段落名可能重复（两段都叫 Main），不能拿 名字#小节号 当 key
        itemsIndexed(rows, key = { i, _ -> "bar$i" }) { _, row ->
            val (sectionName, barIndex, bar) = row
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("$sectionName · 第 ${barIndex + 1} 小节", style = MaterialTheme.typography.titleSmall)
                    if (bar.notes.isEmpty()) {
                        Text(
                            "（空小节）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            bar.notes.joinToString("  ") { note ->
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
