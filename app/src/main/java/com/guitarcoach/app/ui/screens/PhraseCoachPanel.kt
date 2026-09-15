package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.guitarcoach.app.core.audio.TtsController
import com.guitarcoach.app.core.coach.PhraseCoach
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.data.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * F207+F208 逐句大白话讲解入口：缓存优先（离线回看），未命中走 PhraseCoach 管线并落缓存；
 * 结果对话框提供原图对照、整段朗读、乐句卡片与音符级点读（见 PhraseCards.kt）。
 */
@Composable
fun PhraseCoachSection(container: AppContainer, doc: TabDocument, imageBase64: String? = null) {
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<PhraseCoach.Report?>(null) }
    var fromCache by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val tts = remember { TtsController(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    val scope = rememberCoroutineScope()

    Column {
        TextButton(
            enabled = !running,
            onClick = {
                tts.stop()
                val cached = container.phraseCache.load(doc)
                if (cached != null) {
                    report = cached
                    fromCache = true
                    showDialog = true
                } else {
                    running = true
                    error = null
                    finished = 0
                    scope.launch {
                        try {
                            val result = container.phraseCoach.explain(doc) { _, done, _ -> finished = done }
                            container.phraseCache.save(doc, result)
                            report = result
                            fromCache = false
                            showDialog = true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "讲解失败，请重试"
                        } finally {
                            running = false
                        }
                    }
                }
            },
        ) {
            Text(if (running) "逐句讲解生成中…（$finished）" else "逐句大白话讲解")
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    if (showDialog) {
        report?.let { r ->
            PhraseReportDialog(
                report = r,
                fromCache = fromCache,
                imageBase64 = imageBase64,
                tts = tts,
                onDismiss = {
                    tts.stop()
                    showDialog = false
                },
            )
        }
    }
}
