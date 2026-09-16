package com.guitarcoach.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
