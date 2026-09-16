package com.guitarcoach.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.guitarcoach.app.core.audio.RiffMatcher
import com.guitarcoach.app.core.audio.TunerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 内置跟练素材（M4 v1）：名称 + BPM + 目标音（midi, 拍位）。 */
private data class RiffPreset(val name: String, val bpm: Int, val notes: List<RiffMatcher.TargetNote>)

private val PRESETS = listOf(
    RiffPreset(
        "爬格子（低E弦 1-2-3-4 品）", 120,
        listOf(41, 42, 43, 44).mapIndexed { i, midi -> RiffMatcher.TargetNote(midi, i.toDouble()) },
    ),
    RiffPreset(
        "Am 琶音", 100,
        listOf(45, 52, 57, 60, 64).mapIndexed { i, midi -> RiffMatcher.TargetNote(midi, i.toDouble()) },
    ),
)

private const val MIN_NOTE_MS = 200L // 同音保持 ≥200ms 才算一个音符（抗抖动）

/**
 * M4 跟练判定（F401/F402/F403 v1）：麦克风实测 → 音符量化 → 目标对齐判定；
 * 同屏画音高曲线（无稳定音高的段 = 灰色，可能闷音/没按实）；错音以音频为准提示检查指法。
 */
@Composable
internal fun RiffPracticeCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { TunerEngine(CoroutineScope(Dispatchers.Default)) }
    DisposableEffect(Unit) { onDispose { engine.stop() } }
    val reading by engine.reading.collectAsState()

    var presetIndex by rememberSaveable { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var startAt by remember { mutableStateOf(0L) }
    val samples = remember { mutableStateListOf<Pair<Long, Int?>>() } // (墙钟ms, midi|null)
    var result by remember { mutableStateOf<RiffMatcher.MatchResult?>(null) }
    var collectJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var unstableCount by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    fun stopAndJudge() {
        collectJob?.cancel()
        collectJob = null
        engine.stop()
        recording = false
        val preset = PRESETS[presetIndex]
        val detected = mutableListOf<RiffMatcher.DetectedNote>()
        var runMidi: Int? = null
        var runStart = 0L
        var unstable = 0
        for ((t, midi) in samples) {
            if (midi != null && midi == runMidi) continue
            if (runMidi != null && t - runStart >= MIN_NOTE_MS) {
                detected += RiffMatcher.DetectedNote(runMidi, (runStart - startAt) / 1000.0)
            }
            runMidi = midi
            runStart = t
            if (midi == null) unstable++
        }
        if (runMidi != null && (samples.lastOrNull()?.first ?: 0) - runStart >= MIN_NOTE_MS) {
            detected += RiffMatcher.DetectedNote(runMidi, (runStart - startAt) / 1000.0)
        }
        unstableCount = unstable
        val m = RiffMatcher.match(preset.notes, detected, preset.bpm)
        result = m
        error = null
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("跟练判定（实验）", style = MaterialTheme.typography.titleSmall)
            Text(
                "选一段素材 → 点开始 → 跟着拍子弹 → 点判定。错音以耳朵听到的为准（视听互验）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PRESETS.forEachIndexed { i, preset ->
                    AssistChip(
                        onClick = { presetIndex = i; result = null },
                        label = { Text(preset.name) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!hasPermission) {
                            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@Button
                        }
                        samples.clear()
                        result = null
                        startAt = System.currentTimeMillis()
                        engine.start()
                        recording = true
                        collectJob?.cancel()
                        collectJob = scope.launch {
                            engine.reading.collect { r ->
                                if (recording) samples += System.currentTimeMillis() to r?.midi
                            }
                        }
                    },
                    enabled = !recording,
                ) { Text("开始聆听") }
                OutlinedButton(onClick = { stopAndJudge() }, enabled = recording) { Text("判定") }
            }
            recording.let {
                Text(
                    if (recording) "聆听中…当前 ${reading?.noteName ?: "—"}" else " ",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            result?.let { m ->
                val preset = PRESETS[presetIndex]
                val stars = when {
                    m.accuracy >= 0.999 -> 3
                    m.accuracy >= 0.8 -> 2
                    m.accuracy >= 0.6 -> 1
                    else -> 0
                }
                // 连击：顺序最长连续 HIT
                var streak = 0
                var bestStreak = 0
                for (t in m.perTarget) {
                    if (t.status == RiffMatcher.Status.HIT) { streak++; if (streak > bestStreak) bestStreak = streak } else streak = 0
                }
                Text(
                    "命中 ${m.hitCount}/${m.perTarget.size}（${(m.accuracy * 100).toInt()}%）" +
                        " · " + "★".repeat(stars) + "☆".repeat(3 - stars) +
                        " · 最长连击 $bestStreak" +
                        if (m.extraNotes.isNotEmpty()) " · 多弹 ${m.extraNotes.size} 个音" else "",
                    style = MaterialTheme.typography.titleSmall,
                )
                // 速度训练提示（Top2）：高命中率就渐进提速
                if (m.accuracy >= 0.9) {
                    val nextBpm = (preset.bpm * 1.1).toInt().coerceAtMost(240)
                    Text(
                        "全对！下次把速度提到 $nextBpm BPM 再练（渐进提速）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                m.perTarget.forEach { t ->
                    val label = when (t.status) {
                        RiffMatcher.Status.HIT -> "✔"
                        RiffMatcher.Status.WRONG_HIGH -> "↑偏高"
                        RiffMatcher.Status.WRONG_LOW -> "↓偏低"
                        RiffMatcher.Status.MISS -> "✘漏"
                    }
                    Text(
                        "第 ${t.target.beat.toInt() + 1} 拍（${t.target.midi}）：$label",
                        style = MaterialTheme.typography.bodySmall,
                        color = when (t.status) {
                            RiffMatcher.Status.HIT -> MaterialTheme.colorScheme.primary
                            RiffMatcher.Status.MISS -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.error
                        },
                    )
                }
                RiffMatcher.mismatchHint(m)?.let { hint ->
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (unstableCount > 3) {
                    Text(
                        "有 ${unstableCount} 段没听到稳定音高（可能闷音/没按实）——曲线上以灰色标出",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            error?.let { Text("❌ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

            // F402 音高曲线（灰段 = 无稳定音高）
            if (samples.isNotEmpty()) {
                PitchCurve(samples, startAt)
            }
        }
    }
}

@Composable
private fun PitchCurve(samples: List<Pair<Long, Int?>>, startAt: Long) {
    val midiLow = 40f
    val midiHigh = 72f
    val totalMs = ((samples.lastOrNull()?.first ?: startAt) - startAt).coerceAtLeast(1L)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(vertical = 4.dp),
    ) {
        // 音高参考网格线（E2/A2/D3/G3/B3/E4）
        listOf(40, 45, 50, 55, 59, 64).forEach { midi ->
            val y = size.height * (1f - (midi - midiLow) / (midiHigh - midiLow))
            drawLine(Color(0x22888888), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
        }
        for ((t, midi) in samples) {
            val x = (t - startAt).toFloat() / totalMs * size.width
            val y = midi?.let {
                size.height * (1f - (it - midiLow) / (midiHigh - midiLow))
            }
            if (y != null) {
                drawCircle(Color(0xFF4CD964), radius = 3f, center = Offset(x, y))
            } else {
                drawCircle(Color(0x44888888), radius = 3f, center = Offset(x, size.height * 0.92f))
            }
        }
    }
}
