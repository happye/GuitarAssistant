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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.guitarcoach.app.core.audio.TunerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs

private val STRING_LABELS = listOf(
    "1 弦 · 高音E", "2 弦 · B", "3 弦 · G", "4 弦 · D", "5 弦 · A", "6 弦 · 低音E",
)

@Composable
fun PracticeScreen() {
    val context = LocalContext.current
    val engine = remember { TunerEngine(CoroutineScope(Dispatchers.Default)) }
    val reading by engine.reading.collectAsState()
    var running by remember { mutableStateOf(false) }

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
            "M0 已就绪：调音器。节拍器、伴奏、跟弹将在 M1/M2 加入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
                    reading?.let { "最接近：${STRING_LABELS[it.stringHint - 1]}" } ?: " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    }
}
