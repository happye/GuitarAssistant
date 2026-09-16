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
import com.guitarcoach.app.data.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * F601 扒谱入口（M6 v1）：选音频/视频 → 抽音频轨解码 → 16k 单声道 PCM 落缓存。
 * 转写（F602，basic-pitch ONNX）接入后此 PCM 直接喂模型；F603 的弦品分配内核已就绪。
 */
@Composable
internal fun TranscribeSection(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        error = null
        scope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val pcm = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        AudioPcmExtractor().extractToMono(it.fileDescriptor, targetRate = 16000)
                    } ?: throw IllegalArgumentException("文件读取失败，请重试")
                    // 落缓存：F602 转写引擎直接消费该文件
                    val out = File(context.cacheDir, "transcribe_${System.currentTimeMillis()}.pcm")
                    out.outputStream().use { s ->
                        val bytes = ByteArray(pcm.pcm.size * 2)
                        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer().put(pcm.pcm)
                        s.write(bytes)
                    }
                    "%.1f".format(pcm.durationSeconds) to out
                }
                status = "已抽出 ${info.first} 的 16kHz 单声道 PCM（${info.second.length() / 1024} KB）——转写引擎接入后即可生成谱面"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "抽取失败，请重试"
            } finally {
                working = false
            }
        }
    }

    Card(modifier = Modifier.padding(0.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("扒谱（实验）", style = MaterialTheme.typography.titleSmall)
            Text(
                "选一段干净的单音音频/视频，抽出 PCM 备给端侧转写。主打清音单音；失真与混音素材不承诺质量。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { launcher.launch(arrayOf("*/*")) }, enabled = !working) {
                    Text(if (working) "抽取中…" else "选音频/视频")
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            error?.let { Text("❌ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}
