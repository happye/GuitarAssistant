package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.data.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * F503 音色向导：描述想要的音色 → 深思链推荐效果器参数并逐条解释（流式）。
 */
@Composable
internal fun ToneWizardDialog(container: AppContainer, onDismiss: () -> Unit) {
    var request by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("音色向导") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "说说你想要的音色：像哪个乐队/哪首歌，或者直接描述感觉。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = request,
                    onValueChange = { request = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如：想要 Metallica 那种失真") },
                    singleLine = true,
                )
                Button(
                    enabled = !running && request.isNotBlank(),
                    onClick = {
                        running = true
                        error = null
                        answer = ""
                        scope.launch {
                            try {
                                container.coach.toneWizard(request).collect { delta ->
                                    answer = (answer ?: "") + delta
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "生成失败，请重试"
                            } finally {
                                running = false
                            }
                        }
                    },
                ) { Text(if (running) "生成中…" else "要参数") }
                answer?.let {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(it.ifBlank { "…" }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                error?.let { Text("❌ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}
