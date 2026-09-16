package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guitarcoach.app.core.audio.TonePlayer
import com.guitarcoach.app.core.audio.ToneRenderer
import com.guitarcoach.app.core.music.ChordLibrary
import com.guitarcoach.app.core.music.ChordLibrary.ChordShape
import com.guitarcoach.app.core.music.midiToFreq

/** 标准调弦 6→1 弦开弦 midi（和弦试听换算）。 */
private val OPEN_MIDI_LOW_FIRST = listOf(40, 45, 50, 55, 59, 64)

/** 和弦库弹窗（乐理页入口）。 */
@Composable
internal fun ChordLibraryDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("和弦库") },
        text = { ChordLibraryPanel() },
    )
}

/**
 * F-new 和弦库（集百家之长·参考 GuitarTuna/UG）：网格卡片 + 指板图 + 点按琶音试听。
 * 28 个常用形状（开放/横按/强力和弦），数据与测试在 core/music/ChordLibrary。
 */
@Composable
internal fun ChordLibraryPanel() {
    val tonePlayer = remember { TonePlayer() }
    DisposableEffect(Unit) { onDispose { tonePlayer.stop() } }
    var selected by remember { mutableStateOf<ChordShape?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "点卡片听琶音；长按信息卡看指法。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ChordLibrary.ALL, key = { it.name }) { chord ->
                Card(
                    modifier = Modifier
                        .aspectRatio(0.9f)
                        .padding(0.dp),
                    onClick = {
                        selected = chord
                        // 琶音：低音→高音，90ms 间隔
                        val events = chord.fretsHighFirst
                            .mapIndexedNotNull { i, fret ->
                                if (fret >= 0) ToneRenderer.ToneEvent(i * 0.09, OPEN_MIDI_LOW_FIRST.reversed()[i] + fret) else null
                            }
                        tonePlayer.playSequence(events)
                    },
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(chord.name, style = MaterialTheme.typography.titleSmall)
                        ChordDiagram(chord, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                }
            }
        }
        selected?.let { chord ->
            Card {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${chord.name} 指法（6→1 弦）", style = MaterialTheme.typography.titleSmall)
                    Text(
                        chord.frets.mapIndexed { i, f -> if (f < 0) "x" else "${if (chord.barreFret > 0 && f == chord.barreFret) chord.fingers[i].takeIf { it > 0 }?.toString() ?: f.toString() else f}" }
                            .joinToString(" "),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                    )
                    if (chord.barreFret > 0) Text("第 ${chord.barreFret} 品横按", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 和弦指板图：6 弦 × 4 品网格 + 按弦点 + 不弹弦 x + 横按条。 */
@Composable
private fun ChordDiagram(chord: ChordShape, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotColor = MaterialTheme.colorScheme.primary
    val barreColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    Canvas(modifier = modifier) {
        val strings = 6
        val frets = 4
        val left = size.width * 0.14f
        val right = size.width * 0.92f
        val top = size.height * 0.12f
        val bottom = size.height * 0.9f
        val colStep = (right - left) / (strings - 1)
        val rowStep = (bottom - top) / frets

        // 品格横线 + 弦竖线
        for (f in 0..frets) {
            val y = top + f * rowStep
            drawLine(lineColor, Offset(left, y), Offset(right, y), if (f == 0) 5f else 2f)
        }
        for (s in 0 until strings) {
            val x = left + s * colStep
            drawLine(lineColor, Offset(x, top), Offset(x, bottom), 2f)
        }
        // 横按条
        if (chord.barreFret > 0) {
            val row = chord.barreFret - 0.5f
            val y = top + row * rowStep
            val playedStrings = (0 until strings).filter { chord.frets[it] >= 0 }
            val first = playedStrings.min()
            val last = playedStrings.max()
            drawLine(
                barreColor,
                Offset(left + first * colStep, y),
                Offset(left + last * colStep, y),
                strokeWidth = rowStep * 0.42f,
            )
        }
        // 按弦点 / 不弹 x
        chord.frets.forEachIndexed { s, fret ->
            val x = left + s * colStep
            if (fret < 0) {
                drawLine(Color.Gray, Offset(x - 5f, top - 9f), Offset(x + 5f, top - 2f), 2f)
                drawLine(Color.Gray, Offset(x - 5f, top - 2f), Offset(x + 5f, top - 9f), 2f)
            } else if (fret == 0) {
                drawCircle(lineColor, radius = 5f, center = Offset(x, top - 6f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            } else {
                val row = fret - 0.5f
                val y = top + row * rowStep
                drawCircle(dotColor, radius = rowStep * 0.3f, center = Offset(x, y))
            }
        }
    }
}
