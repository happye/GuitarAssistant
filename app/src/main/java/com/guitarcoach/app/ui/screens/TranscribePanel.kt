package com.guitarcoach.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.data.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * F601+F602 扒谱入口（P0-1 重构后 UI 只剩交互与状态）：选音频 → TranscriptionController 全管线
 * （抽取→分离→逐拍→转写→清洗→量化→弦品→和弦级）→ TabDocument 进识谱工作台。
 * 主打干净单音/分解和弦；失真与混音素材默认附和弦级参考（D3 口径，不承诺逐音符质量）。
 */
@Composable
internal fun TranscribeSection(container: AppContainer, onDocument: (TabDocument) -> Unit) {
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var bpmText by remember { mutableStateOf("") } // 默认空=自动检测（F706）；只有用户手输过才跳过检测
    var bpmManuallyEdited by remember { mutableStateOf(false) } // 区分机器预填与用户手输（对抗审查 P1：防跨曲 BPM 污染）
    var enhance by remember { mutableStateOf(true) } // 吉他聚焦预处理：分离人声/鼓（混音素材建议开）
    var truncated by remember { mutableStateOf(0) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        android.util.Log.d("GuitarCoach", "扒谱: 选择器返回 uri=$uri")
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        error = null
        status = null
        scope.launch {
            try {
                val manualBpm = if (bpmManuallyEdited && bpmText.isNotBlank()) {
                    bpmText.toIntOrNull()?.coerceIn(40, 300) ?: 120
                } else null
                val outcome = container.transcription.transcribe(uri, manualBpm, enhance) { p -> progressText = p }
                if (manualBpm == null) bpmText = outcome.doc.tempo.toString() // 检出值展示（机器预填不锁检测，L025）
                truncated = outcome.truncated
                status = buildString {
                    append("转写完成：${outcome.doc.sections.sumOf { s -> s.bars.sumOf { b -> b.notes.size } }} 个音符已进谱面")
                    if (outcome.truncated > 0) append("（仅取前 2 分钟主干，其余 ${outcome.truncated} 个音符未入谱）")
                    if (outcome.skippedOutOfRange > 0) append("\n⚠ 已忽略 ${outcome.skippedOutOfRange} 个超出吉他音域的检出（混音素材的贝斯/鼓常见）")
                    if (outcome.material == com.guitarcoach.app.core.audio.InputClassifier.Material.MIXED) {
                        append("\n检测到混音/密集素材：已附和弦级参考（和弦行显示在谱面上方）")
                    }
                }
                onDocument(outcome.doc)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "扒谱失败，请重试"
            } finally {
                working = false
                progressText = null
            }
        }
    }

    Card(modifier = Modifier.padding(0.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("扒谱（端侧转写）", style = MaterialTheme.typography.titleSmall)
            Text(
                "选一段干净的单音音频 → 端侧转写 → 自动生成六线谱。主打清音单音/分解和弦；失真与混音素材默认附和弦级参考，不承诺逐音符质量。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    android.util.Log.d("GuitarCoach", "扒谱: 点击选音频转写 (working=$working)")
                    launcher.launch(arrayOf("*/*"))
                },
                enabled = !working,
            ) {
                Text(if (working) (progressText ?: "处理中…") else "选音频转写")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Checkbox(checked = enhance, onCheckedChange = { enhance = it })
                Text(
                    "分离人声/鼓（Spleeter 端侧，混音素材建议开）",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = bpmText,
                onValueChange = {
                    bpmText = it.filter { c -> c.isDigit() }.take(3)
                    bpmManuallyEdited = true
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("BPM（留空=自动检测）") },
                singleLine = true,
            )
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            error?.let { Text("❌ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}
