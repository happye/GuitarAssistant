package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.core.audio.TtsController
import com.guitarcoach.app.core.coach.PhraseCoach
import com.guitarcoach.app.core.coach.PhraseExplain
import com.guitarcoach.app.core.coach.phraseSpeechText
import com.guitarcoach.app.core.coach.reportSpeechText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap

/**
 * F208 乐句卡片流：原图对照 + 整段朗读 + 逐句卡片（可朗读）+ 音符级点读兜底 + 覆盖审计摘要。
 */
@Composable
internal fun PhraseReportDialog(
    report: PhraseCoach.Report,
    fromCache: Boolean,
    imageBase64: String?,
    tts: TtsController,
    onDismiss: () -> Unit,
) {
    var stepTarget by remember { mutableStateOf<PhraseExplain.Step?>(null) }
    val ttsReady by tts.isReady.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("逐句大白话讲解") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fromCache) {
                    Text(
                        "已从本地缓存加载，可离线回看",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (imageBase64 != null) {
                    OriginalImage(imageBase64)
                    Text("↑ 原图对照", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { tts.speak(reportSpeechText(report)) }) { Text("整段朗读") }
                    TextButton(onClick = { tts.stop() }) { Text("停止") }
                }
                if (!ttsReady) {
                    Text(
                        "语音播报初始化中或系统 TTS 不可用（中文语音包需在系统设置安装）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(report.phrases, key = { it.index }) { phrase ->
                        PhraseCard(phrase = phrase, tts = tts, onStepClick = { stepTarget = it })
                        HorizontalDivider()
                    }
                    item(key = "audit") { AuditFooter(report) }
                }
            }
        },
    )

    stepTarget?.let { step ->
        StepDetailDialog(step = step, tts = tts, onDismiss = { stepTarget = null })
    }
}

/** 原图缩略图（F208 对照用；讲解本身仍只基于结构化数据，不看图）。 */
@Composable
private fun OriginalImage(imageBase64: String) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imageBase64) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "识别用原图",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun PhraseCard(phrase: PhraseExplain, tts: TtsController, onStepClick: (PhraseExplain.Step) -> Unit) {
    val bars = phrase.steps.map { it.bar }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "乐句 #${phrase.index}" + if (bars.isNotEmpty()) " · 第 ${bars.min()}~${bars.max()} 小节" else "",
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = { tts.speak(phraseSpeechText(phrase)) }) { Text("朗读本句") }
        }
        Text(phrase.summary, style = MaterialTheme.typography.bodyMedium)
        Text("（点任意一行听该音讲法）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        phrase.steps.forEach { step ->
            Text(
                "第${step.bar}小节 ${step.string}弦${step.fret}品 · ${fingerLabel(step.finger)} · ${step.pick.ifBlank { "—" }}｜${step.text.ifBlank { "点按查看" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onStepClick(step) },
            )
        }
        phrase.terms.forEach { term ->
            Text("术语「${term.term}」：${term.plain}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (phrase.difficulty.isNotBlank()) Text("难点：${phrase.difficulty}", style = MaterialTheme.typography.bodySmall)
        if (phrase.practice.isNotBlank()) Text("练习：${phrase.practice}", style = MaterialTheme.typography.bodySmall)
    }
}

/** 音符级点读兜底：点单个音符只讲该音（弦/品/手指 + 右手 + 说明），可朗读。 */
@Composable
private fun StepDetailDialog(step: PhraseExplain.Step, tts: TtsController, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("第${step.bar}小节 ${step.string}弦${step.fret}品") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("按弦手指：${fingerLabel(step.finger)}　右手：${step.pick.ifBlank { "—" }}", style = MaterialTheme.typography.bodyMedium)
                Text(step.text.ifBlank { "（这句没有更多说明）" }, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { tts.speak("${step.string}弦${step.fret}品。${step.text}") }) { Text("朗读") }
            }
        },
    )
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
                    "乐句 #${issue.phraseIndex}：漏 ${issue.missing.size} 处、多 ${issue.extra.size} 处" +
                        if (issue.ruleViolations.isNotEmpty()) "、指法违规 ${issue.ruleViolations.size} 条" else "",
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
