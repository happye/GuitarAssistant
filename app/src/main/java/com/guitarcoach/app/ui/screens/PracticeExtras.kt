package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.core.audio.MetronomeEngine
import com.guitarcoach.app.data.PracticeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** F105 节拍器卡片：40-240 BPM、可选拍数，音频硬件时基无漂移。 */
@Composable
internal fun MetronomeCard() {
    val engine = remember { MetronomeEngine(CoroutineScope(Dispatchers.Default)) }
    var bpm by rememberSaveable { mutableIntStateOf(100) }
    var beats by rememberSaveable { mutableIntStateOf(4) }
    var subdivide by rememberSaveable { mutableIntStateOf(1) } // F705 细分
    var countIn by rememberSaveable { mutableStateOf(false) } // F705 预备拍
    var running by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { engine.stop() } }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("节拍器", style = MaterialTheme.typography.titleMedium)
            Text(
                "$bpm BPM · $beats 拍/小节" + if (running) " · ▶ 运行中" else "",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Slider(
                value = bpm.toFloat(),
                onValueChange = {
                    bpm = it.toInt()
                    engine.update(bpm, beats, subdivision, countIn = countIn)
                },
                valueRange = 40f..240f,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 4, 5, 6).forEach { b ->
                    FilterChip(
                        selected = beats == b,
                        onClick = {
                            beats = b
                            engine.update(bpm, b, subdivision, countIn = countIn)
                        },
                        label = { Text("$b 拍") },
                    )
                }
            }
            Button(
                onClick = {
                    if (running) {
                        engine.stop()
                        running = false
                    } else {
                        engine.update(bpm, beats, subdivision, countIn = countIn)
                        engine.start()
                        running = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (running) "停止" else "开始打拍") }
        }
    }
}

/** F106 练习计时卡片：开始/结束并落库。 */
@Composable
internal fun PracticeTimerCard(repo: PracticeRepository) {
    val scope = rememberCoroutineScope()
    var startedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var elapsed by remember { mutableIntStateOf(0) }
    var showSave by remember { mutableStateOf(false) }

    LaunchedEffect(startedAt) {
        while (startedAt != null) {
            elapsed = ((System.currentTimeMillis() - startedAt!!) / 1000).toInt()
            delay(1000)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("练习计时", style = MaterialTheme.typography.titleMedium)
            Text(
                if (startedAt == null) "未开始" else formatElapsed(elapsed),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Button(
                onClick = {
                    if (startedAt == null) {
                        startedAt = System.currentTimeMillis()
                        elapsed = 0
                    } else {
                        showSave = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (startedAt == null) "开始练习" else "结束并记录") }
        }
    }

    if (showSave && startedAt != null) {
        val start = startedAt!!
        SavePracticeDialog(
            durationSeconds = elapsed,
            onDismiss = {
                showSave = false
                startedAt = null
            },
            onSave = { content, note ->
                scope.launch {
                    // 结束时按真实时间重算时长（UI 的 elapsed 每秒刷新，最多差 1 秒）
                    val seconds = ((System.currentTimeMillis() - start) / 1000).toInt()
                    repo.save(startedAt = start, durationSeconds = seconds, content = content, note = note)
                }
                showSave = false
                startedAt = null
            },
        )
    }
}

@Composable
private fun SavePracticeDialog(
    durationSeconds: Int,
    onDismiss: () -> Unit,
    onSave: (content: String, note: String) -> Unit,
) {
    var content by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存练习记录（${formatElapsed(durationSeconds)}）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("练了什么（如：爬格子 + Smoke on the Water riff）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（问题/心得，可留空）") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(content.trim(), note.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("放弃") } },
    )
}

internal fun formatElapsed(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
