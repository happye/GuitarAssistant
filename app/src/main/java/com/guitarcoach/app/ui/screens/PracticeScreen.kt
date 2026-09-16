package com.guitarcoach.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.guitarcoach.app.core.audio.TunerEngine
import com.guitarcoach.app.core.tab.midiToName
import com.guitarcoach.app.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs

@Composable
fun PracticeScreen(container: AppContainer) {
    val context = LocalContext.current
    val engine = remember { TunerEngine(CoroutineScope(Dispatchers.Default)) }
    val reading by engine.reading.collectAsState()
    var running by remember { mutableStateOf(false) }
    var showCoach by remember { mutableStateOf(false) }
    var tuningName by rememberSaveable { mutableStateOf("E 标准") }
    // F704 调弦预设：循环切换，运行中也即时生效
    fun cycleTuning() {
        val all = com.guitarcoach.app.core.music.Tunings.ALL
        val idx = all.indexOfFirst { it.name == tuningName }
        val next = all[(idx + 1).mod(all.size)]
        engine.tuning = next
        tuningName = next.name
    }

    if (showCoach) {
        PostureCoachScreen(container = container, onBack = { showCoach = false })
        return
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            engine.start()
            running = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { engine.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("练习室", style = MaterialTheme.typography.headlineSmall)
        Text(
            "调音器 · 节拍器 · 练习计时 · 视觉教练（实验）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("视觉教练（实验）", style = MaterialTheme.typography.titleSmall)
                Text(
                    "后置相机实时看手型：折腕/塌指/拇指位置即时警报，每两分钟给一条语音点评。支架斜放对准双手。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { showCoach = true }) { Text("开始视觉教练") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("调音器", style = MaterialTheme.typography.titleMedium)
                Text(
                    reading?.noteName ?: "—",
                    fontSize = 72.sp,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                // F704+ 音分表盘：指针 ±50 音分映射 ±90°，±5 音分内绿区
                TunerMeter(cents = reading?.cents, modifier = Modifier.fillMaxWidth().height(84.dp))
                Text(
                    reading?.let { "%.1f Hz".format(it.frequency) }
                        ?: if (running) "正在聆听…" else "点「开始调音」后拨一根弦",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    reading?.let {
                        when {
                            abs(it.cents) <= 5 -> "✔ 已调准"
                            it.cents < 0 -> "偏低 %.0f 音分，请拧紧".format(-it.cents)
                            else -> "偏高 +%.0f 音分，请放松".format(it.cents)
                        }
                    } ?: " ",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    reading?.let { "最接近：${it.stringHint} 弦（${midiToName(engine.tuning.midiLowFirst[it.stringHint - 1])}）· ${it.tuningName}" } ?: "当前调弦：$tuningName（点击按钮切换）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { cycleTuning() }) { Text("调弦：$tuningName") }
                Button(
                    onClick = {
                        if (!hasPermission) {
                            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (running) {
                            engine.stop()
                            running = false
                        } else {
                            engine.start()
                            running = true
                        }
                    },
                ) {
                    Text(if (running) "停止" else "开始调音")
                }
            }
        }

        MetronomeCard()
        PracticeTimerCard(container.practiceRepository)
        RiffPracticeCard()
    }
}

/** 调音音分表盘：半圆弧 + 刻度 + 指针；±5 音分内指针与弧为绿色（已调准）。 */
@Composable
private fun TunerMeter(cents: Double?, modifier: Modifier = Modifier) {
    val arcColor = MaterialTheme.colorScheme.onSurfaceVariant
    val goodColor = MaterialTheme.colorScheme.primary
    val needleColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height * 0.92f
        val radius = size.height * 0.78f
        // 弧：-90°..90°（以正上为 0）
        drawArc(
            arcColor.copy(alpha = 0.5f),
            startAngle = -180f, sweepAngle = 180f, useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f),
            topLeft = Offset(cx - radius, cy - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        )
        // 绿区 ±5 音分（-9°..9°，以正上为中心）
        drawArc(
            goodColor,
            startAngle = -99f, sweepAngle = 18f, useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10f),
            topLeft = Offset(cx - radius, cy - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        )
        // 刻度（-50/-25/0/25/50）：c=0 → 正上，±50 → ±90°（与绿区同坐标系，监督员 P1 修正）
        for (c in listOf(-50, -25, 0, 25, 50)) {
            val a = Math.toRadians(c / 50.0 * 90)
            val r1 = radius - 8f
            val r2 = radius + 8f
            drawLine(
                arcColor,
                Offset((cx + r1 * kotlin.math.sin(a)).toFloat(), (cy - r1 * kotlin.math.cos(a)).toFloat()),
                Offset((cx + r2 * kotlin.math.sin(a)).toFloat(), (cy - r2 * kotlin.math.cos(a)).toFloat()),
                strokeWidth = 3f,
            )
        }
        // 指针
        cents?.let { c ->
            val a = Math.toRadians((c / 50.0).coerceIn(-1.0, 1.0) * 90)
            val color = if (kotlin.math.abs(c) <= 5) goodColor else needleColor
            drawLine(
                color,
                Offset(cx, cy),
                Offset((cx + radius * 0.86 * kotlin.math.sin(a)).toFloat(), (cy - radius * 0.86 * kotlin.math.cos(a)).toFloat()),
                strokeWidth = 6f,
            )
            drawCircle(color, radius = 8f, center = Offset(cx, cy))
        }
    }
}