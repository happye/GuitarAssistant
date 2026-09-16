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

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        error = null
        status = null
        scope.launch {
            try {
                val doc = withContext(Dispatchers.IO) {
                    val bpm = bpmText.toIntOrNull()?.coerceIn(40, 300) ?: 120
                    val pcm = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        AudioPcmExtractor().extractToMono(it.fileDescriptor, targetRate = 22050)
                    } ?: throw IllegalArgumentException("文件读取失败，请重试")
                    progressText = "转写中…"
                    val engine = TranscriptionEngine(context)
                    val midiNotes = try {
                        engine.transcribe(pcm.pcm, pcm.sampleRate) { p ->
                            progressText = "转写中… ${(p * 100).toInt()}%"
                        }
                    } finally {
                        engine.close()
                    }
                    if (midiNotes.isEmpty()) throw IllegalArgumentException("没有转写出音符——试试更干净的单音素材")
                    // 时长截断（前 2 分钟）：长曲先出主干；截断必须告知用户（局限如实标注，监督员 P2）
                    val trimmed = midiNotes.filter { it.timeSec < 120 }
                    val truncated = midiNotes.size - trimmed.size
                    val placed = MidiTabConverter.convert(trimmed, bpm)
                    MidiTabConverter.toTabDocument(placed, bpm = bpm, title = "扒谱 " + uri.lastPathSegment?.substringAfterLast('/')?.take(24).orEmpty())
                }
                onDocument(doc)
                status = "转写完成：${doc.sections.sumOf { s -> s.bars.sumOf { b -> b.notes.size } }} 个音符已进谱面" +
                    if (truncated > 0) "（仅取前 2 分钟主干，其余 ${truncated} 个音符未入谱）" else ""
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
                OutlinedTextField(
                    value = bpmText,
                    onValueChange = { bpmText = it.filter { c -> c.isDigit() }.take(3) },
                    modifier = Modifier.padding(0.dp),
                    label = { Text("BPM") },
                    singleLine = true,
                )
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            error?.let { Text("❌ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

