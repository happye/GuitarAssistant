package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guitarcoach.app.core.audio.TonePlayer
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.core.tab.TabLayout
import com.guitarcoach.app.core.tab.midi
import kotlin.math.roundToInt

/**
 * F202 谱面渲染（自绘 Canvas 版）：横向滚动六线谱网格，点按音符试听。
 * 频率唯一来源 TabDocument.midiToFreq —— 点按发声与换算口径一致（验收标准）。
 * alphaTab 精渲染为后续升级路径（引入需改 gradle 配置，待用户确认）。
 */
@Composable
fun TabRenderPanel(doc: TabDocument, modifier: Modifier = Modifier) {
    val tonePlayer = remember { TonePlayer() }
    DisposableEffect(Unit) { onDispose { tonePlayer.stop() } }

    val textMeasurer = rememberTextMeasurer()
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val density = LocalDensity.current
    val barWidth = with(density) { 200.dp.toPx() }
    val spacing = with(density) { 18.dp.toPx() }
    val topPad = with(density) { 26.dp.toPx() }
    val barPad = with(density) { 14.dp.toPx() }
    val geometry = TabLayout.Geometry(barWidth, spacing, topPad, barPad)
    val placed = remember(doc, barWidth, spacing, topPad, barPad) { TabLayout.layout(doc, geometry) }

    val barCount = doc.sections.sumOf { it.bars.size }.coerceAtLeast(1)
    val canvasWidthDp = with(density) { (barCount * barWidth).toDp() }
    val canvasHeightDp = with(density) { (topPad + 6 * spacing + 8.dp.toPx()).toDp() }
    val tapRadius = with(density) { 22.dp.toPx() }

    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val noteColor = MaterialTheme.colorScheme.primary
    val selColor = MaterialTheme.colorScheme.tertiary
    val noteTextColor = Color.White

    Column(modifier) {
        Text(
            "点按音符可试听（音高按标准调弦换算）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .width(canvasWidthDp)
                .height(canvasHeightDp)
                .pointerInput(doc) {
                    detectTapGestures { pos ->
                        val hit = placed.minByOrNull { p -> (p.x - pos.x) * (p.x - pos.x) + (p.y - pos.y) * (p.y - pos.y) }
                        if (hit != null) {
                            val d2 = (hit.x - pos.x) * (hit.x - pos.x) + (hit.y - pos.y) * (hit.y - pos.y)
                            if (d2 <= tapRadius * tapRadius) {
                                tonePlayer.play(hit.note.midi())
                                selected = hit.barIndex to hit.noteIndex
                            }
                        }
                    }
                },
        ) {
            // 六根琴弦（1 弦在最上）
            for (s in 0..5) {
                val y = topPad + s * spacing
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
            }
            // 小节线与小节号
            for (b in 0..barCount) {
                val x = b * barWidth
                drawLine(
                    lineColor,
                    Offset(x, topPad - 6.dp.toPx()),
                    Offset(x, topPad + 5 * spacing + 6.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            for (b in 0 until barCount) {
                val label = textMeasurer.measure("${b + 1}", TextStyle(fontSize = 10.sp, color = lineColor))
                drawText(label, topLeft = Offset(b * barWidth + 3.dp.toPx(), 1.dp.toPx()))
            }
            // 音符：品号圆点，选中的高亮
            placed.forEach { p ->
                val isSel = selected == p.barIndex to p.noteIndex
                drawCircle(color = if (isSel) selColor else noteColor, radius = 9.dp.toPx(), center = Offset(p.x, p.y))
                val label = textMeasurer.measure(
                    "${p.note.fret}",
                    TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = noteTextColor),
                )
                drawText(label, topLeft = Offset(p.x - label.size.width / 2f, p.y - label.size.height / 2f))
            }
        }
    }
}
