package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.core.coach.PhraseCoach
import com.guitarcoach.app.core.coach.PhraseExplain
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.data.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * F207 逐句大白话讲解：拍谱结果卡片内的入口 + 结果对话框。
 * 讲解只基于已识别的 TabDocument（结构化优先，路线 B），底部展示覆盖审计摘要；
 * F208 将升级为乐句卡片点读（原图对照/音符级点读/TTS/本地缓存）。
 */
@Composable
fun PhraseCoachSection(container: AppContainer, doc: TabDocument) {
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<PhraseCoach.Report?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column {
        TextButton(
            enabled = !running,
            onClick = {
                running = true
                error = null
                finished = 0
                scope.launch {
                    try {
                        val result = container.phraseCoach.explain(doc) { _, done, _ -> finished = done }
                        report = result
                        showDialog = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "讲解失败，请重试"
                    } finally {
                        running = false
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
            PhraseReportDialog(report = r, onDismiss = { showDialog = false })
        }
    }
}

@Composable
private fun PhraseReportDialog(report: PhraseCoach.Report, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("逐句大白话讲解") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(report.phrases, key = { it.index }) { phrase ->
                    PhraseCard(phrase)
                    HorizontalDivider()
                }
                item(key = "audit") { AuditFooter(report) }
            }
        },
    )
}

@Composable
private fun PhraseCard(phrase: PhraseExplain) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "乐句 #${phrase.index}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(phrase.summary, style = MaterialTheme.typography.bodyMedium)
        phrase.steps.forEach { step ->
            Text(
                "第${step.bar}小节 ${step.string}弦${step.fret}品 · ${fingerLabel(step.finger)} · ${step.pick.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (step.text.isNotBlank()) {
                Text(
                    step.text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        phrase.terms.forEach { term ->
            Text(
                "术语「${term.term}」：${term.plain}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (phrase.difficulty.isNotBlank()) Text("难点：${phrase.difficulty}", style = MaterialTheme.typography.bodySmall)
        if (phrase.practice.isNotBlank()) Text("练习：${phrase.practice}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AuditFooter(report: PhraseCoach.Report) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (report.auditClean) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "✅ 覆盖审计通过：0 漏音 0 重复（${report.rounds} 轮）",
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Text(
                "⚠️ 覆盖审计未全过（重写 ${report.rounds} 轮后仍有 ${report.issues.size} 句未过），建议重试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            report.issues.forEach { issue ->
                Text(
                    "乐句 #${issue.phraseIndex}：漏 ${issue.missing.size} 处、多 ${issue.extra.size} 处${if (issue.badFinger) "、指法字段缺失" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        report.coveNotes.forEach { note ->
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun fingerLabel(finger: Int): String = when (finger) {
    0 -> "空弦"
    in 1..4 -> "指$finger"
    else -> "指法—"
}
