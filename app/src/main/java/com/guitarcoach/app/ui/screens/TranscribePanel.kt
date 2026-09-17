package com.guitarcoach.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.core.audio.AudioPcmExtractor
import com.guitarcoach.app.core.audio.TempoDetector
import com.guitarcoach.app.core.audio.TranscriptionEngine
import com.guitarcoach.app.core.tab.MidiTabConverter
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.data.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * F601+F602 扒谱入口：选音频/视频 → 抽 22050 单声道 PCM → 端侧转写（basic-pitch TFLite）→
 * 弦品分配 → TabDocument 直接进识谱工作台（可编辑/试听/存曲库）。
 * 主打干净单音/分解和弦；失真与混音素材不承诺质量（如实口径）。
 */
@Composable
internal fun TranscribeSection(container: AppContainer, onDocument: (TabDocument) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var bpmText by remember { mutableStateOf("120") }
    var enhance by remember { mutableStateOf(true) } // 吉他聚焦预处理：中央消除+带通（混音素材建议开）
    var truncated by remember { mutableStateOf(0) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        error = null
        status = null
        scope.launch {
            try {
                val (doc, skippedOutOfRange) = withContext(Dispatchers.IO) {
                    val pcm = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        AudioPcmExtractor().extractToMono(it.fileDescriptor, targetRate = 22050, enhance = enhance)
                    } ?: throw IllegalArgumentException("文件读取失败，请重试")
                    // F706 BPM 自动检测：检出即预填并采用；用户手输值优先生效
                    val bpm = if (bpmText.isBlank()) {
                        (TempoDetector.detect(pcm.pcm, pcm.sampleRate) ?: 120).also { bpmText = it.toString() }
                    } else {
                        bpmText.toIntOrNull()?.coerceIn(40, 300) ?: 120
                    }
                    progressText = "转写中…"
                    val engine = TranscriptionEngine(context)
                    val result = try {
                        engine.transcribe(pcm.pcm, pcm.sampleRate) { p ->
                            progressText = "转写中… ${(p * 100).toInt()}%"
                        }
                    } finally {
                        engine.close()
                    }
                    val midiNotes = result.notes
                    if (midiNotes.isEmpty()) throw IllegalArgumentException("没有转写出音符——试试更干净的单音素材")
                    // 时长截断（前 2 分钟）：长曲先出主干；截断必须告知用户（局限如实标注，监督员 P2）
                    val kept = midiNotes.filter { it.timeSec < 120 }
                    truncated = midiNotes.size - kept.size
                    val placed = MidiTabConverter.convert(kept, bpm)
                    val doc = MidiTabConverter.toTabDocument(placed, bpm = bpm, title = "扒谱 " + uri.lastPathSegment?.substringAfterLast('/')?.take(24).orEmpty())
                    doc to result.skippedOutOfRange
                }
                onDocument(doc)
                status = "转写完成：${doc.sections.sumOf { s -> s.bars.sumOf { b -> b.notes.size } }} 个音符已进谱面" +
                    (if (truncated > 0) "（仅取前 2 分钟主干，其余 $truncated 个音符未入谱）" else "") +
                    (if (skippedOutOfRange > 0) "\n⚠ 已忽略 $skippedOutOfRange 个超出吉他音域的检出（混音素材的贝斯/鼓常见）" else "")
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
                "选一段干净的单音音频 → 端侧转写 → 自动生成六线谱。主打清音单音/分解和弦；失真与混音素材不承诺质量。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(arrayOf("*/*")) }, enabled = !working) {
                    Text(if (working) (progressText ?: "处理中…") else "选音频转写")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    androidx.compose.material3.Checkbox(checked = enhance, onCheckedChange = { enhance = it })
                    Text(
                        "吉他聚焦（去人声/压鼓，混音素材建议开）",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = bpmText,
                    onValueChange = { bpmText = it.filter { c -> c.isDigit() }.take(3) },
                    modifier = Modifier.padding(0.dp),
                    label = { Text("BPM（留空=自动检测）") },
                    singleLine = true,
                )
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            error?.let { Text("❌ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

