package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guitarcoach.app.core.tab.STANDARD_TUNING_MIDI
import com.guitarcoach.app.core.tab.midiToName

private val PITCH_CLASSES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
private const val FRETS = 12

/**
 * F103 指板可视化：选一个音名，在 6 弦 × 12 品上标出它的全部位置。
 * 音高计算与 TabDocument 同源（STANDARD_TUNING_MIDI + midiToName），保证口径一致。
 */
@Composable
internal fun FretboardDialog(onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf("C") }
    val textMeasurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("指板可视化") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "点一个音名，看它在每一根弦上的位置（0-12 品）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PITCH_CLASSES.forEach { pitch ->
                        FilterChip(
                            selected = selected == pitch,
                            onClick = { selected = pitch },
                            label = { Text(pitch) },
                        )
                    }
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    val leftPad = 14.dp.toPx()
                    val rightPad = 10.dp.toPx()
                    val topPad = 8.dp.toPx()
                    val bottomPad = 26.dp.toPx()
                    val stringGap = (size.height - topPad - bottomPad) / 5f
                    val fretWidth = (size.width - leftPad - rightPad) / FRETS

                    // 品丝（竖线）与品号
                    for (fret in 0..FRETS) {
                        val x = leftPad + fretWidth * fret
                        drawLine(
                            color = lineColor,
                            start = Offset(x, topPad),
                            end = Offset(x, size.height - bottomPad),
                            strokeWidth = if (fret == 0) 5f else 2f, // 0 品 = 琴枕加粗
                            cap = StrokeCap.Round,
                        )
                        if (fret in 1..FRETS) {
                            drawText(
                                textMeasurer = textMeasurer,
                                text = fret.toString(),
                                style = TextStyle(fontSize = 9.sp, color = labelColor),
                                topLeft = Offset(x - fretWidth / 2 - 6f, size.height - bottomPad + 6f),
                            )
                        }
                    }

                    // 弦（横线，1 弦在最上）与音点
                    for (string in 1..6) {
                        val y = topPad + stringGap * (string - 1)
                        drawLine(
                            color = lineColor,
                            start = Offset(leftPad, y),
                            end = Offset(size.width - rightPad, y),
                            // 低音弦更粗：string 6 最粗、1 最细，与真实吉他一致
                        strokeWidth = 1f + (string - 1) * 0.5f,
                            cap = StrokeCap.Round,
                        )
                        for (fret in 0..FRETS) {
                            val midi = STANDARD_TUNING_MIDI[string - 1] + fret
                            val pitch = midiToName(midi).takeWhile { it.isLetter() || it == '#' }
                            if (pitch == selected) {
                                val cx = leftPad + fretWidth * (fret + 0.5f)
                                drawCircle(color = dotColor, radius = 8.dp.toPx() / 2, center = Offset(cx, y))
                            }
                        }
                    }
                }
                Text(
                    "从左到右第 1 格是第 1 品；琴枕（粗线）左侧为空弦 0 品。同音在不同弦上有多处，选你顺手的位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
