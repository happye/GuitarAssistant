package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.guitarcoach.app.core.audio.TonePlayer
import com.guitarcoach.app.core.audio.ToneRenderer
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.core.tab.TabLayout
import com.guitarcoach.app.core.tab.midi

/**
 * F202 谱面渲染 v2（用户反馈#4 观感提升）：参考 Songsterr / alphaTab 的 TAB 记谱惯例精修自绘——
 * 品数直接落线（白底块护读）、按 4/4 拍位比例水平展开、拍位刻度、段落名条、弦名标签、
 * 技巧记号（H/P/S/B/PM/M/V/A）、终止双线、选中高亮。频率唯一来源 TabDocument.midiToFreq。
 * alphaTab 完整渲染引擎（AlphaSkia 原生链 + Bravura 字体）列为 DEBT 评估项，v1 不引入。
 */
@Composable
fun TabRenderPanel(doc: TabDocument, modifier: Modifier = Modifier) {
    val tonePlayer = remember { TonePlayer() }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { tonePlayer.stop() } }

    val textMeasurer = rememberTextMeasurer()
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var playing by remember { mutableStateOf(false) } // F202 试听升级：整段播放
    var playJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) } // 旧复位协程取消，防状态竞态（监督员 P2）
    val positionMs by tonePlayer.positionMs.collectAsState() // 播放光标（-1 = 未播放）
    val density = LocalDensity.current
    val barWidth = with(density) { 240.dp.toPx() }
    val spacing = with(density) { 18.dp.toPx() }
    val topPad = with(density) { 34.dp.toPx() }   // 段落名条 + 技巧记号空间
    val barPad = with(density) { 16.dp.toPx() }
    val nameGutter = with(density) { 20.dp.toPx() } // 行首弦名区
    val geometry = TabLayout.Geometry(barWidth, spacing, topPad, barPad)
    // gutter 同源偏移（监督员 P1）：TabLayout 不知道行首弦名区，绘制与点按判定统一在此加 gutter，
    // 保证"看见的"与"点到的"同坐标系
    val placed = remember(doc, barWidth, spacing, topPad, barPad) {
        TabLayout.layout(doc, geometry).map { it.copy(x = it.x + nameGutter) }
    }

    val barCount = doc.sections.sumOf { it.bars.size }.coerceAtLeast(1)
    val canvasWidthDp = with(density) { (barCount * barWidth + nameGutter).toDp() }
    val canvasHeightDp = with(density) { (topPad + 6 * spacing + 12.dp.toPx()).toDp() }
    val tapRadius = with(density) { 24.dp.toPx() }

    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val beatTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val noteBg = MaterialTheme.colorScheme.surface
    val noteFg = MaterialTheme.colorScheme.onSurface
    val selColor = MaterialTheme.colorScheme.primary
    val sectionBg = MaterialTheme.colorScheme.secondaryContainer
    val sectionFg = MaterialTheme.colorScheme.onSecondaryContainer

    // 段落名（段落首小节号 → 名称）
    val sectionLabels = remember(doc) {
        var bar = 0
        doc.sections.mapNotNull { section ->
            val start = bar
            bar += section.bars.size
            if (section.name.isNotBlank() && section.bars.isNotEmpty()) start to section.name else null
        }
    }

    // 整段播放时间表：全局小节号 × 4 拍 + 小节内拍位，BPM 换算秒
    val playEvents = remember(doc) {
        val secPerBeat = 60.0 / doc.tempo
        var globalBar = 0
        buildList {
            doc.sections.forEach { section ->
                section.bars.forEach { bar ->
                    bar.notes.forEach { note ->
                        // 时长真实化：按谱面 duration 发声（转写/编辑产物有真实时值），缺省 0.6s
                        add(ToneRenderer.ToneEvent(timeSec = (globalBar * 4 + note.beat) * secPerBeat, midi = note.midi(), durationSec = (note.duration * secPerBeat).coerceIn(0.15, 2.0)))
                    }
                    globalBar++
                }
            }
        }
    }

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                "调弦 ${doc.tuning} · ${doc.tempo} BPM · ${barCount} 小节",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    playJob?.cancel()
                    if (playing) {
                        tonePlayer.stop()
                        playing = false
                    } else {
                        tonePlayer.playSequence(playEvents)
                        playing = true
                        playJob = scope.launch {
                            withContext(Dispatchers.Default) {
                                val totalMs = playEvents.maxOf { it.timeSec + it.durationSec } * 1000
                                kotlinx.coroutines.delay(totalMs.toLong() + 200)
                            }
                            playing = false
                        }
                    }
                },
                enabled = playEvents.isNotEmpty(),
            ) { Text(if (playing) "■ 停止" else "▶ 播放整段") }
        }
        Text(
            "点按音符试听单音",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val scrollState = rememberScrollState()
        var lastScrolledBar by remember { mutableStateOf(-1) }
        LaunchedEffect(positionMs) {
            if (positionMs >= 0) {
                val secPerBeat = 60.0 / doc.tempo
                val curBar = ((positionMs / 1000.0 / secPerBeat) / 4).toInt().coerceIn(0, barCount - 1)
                if (curBar != lastScrolledBar) { // 同小节内不重复滚（高频进度天然节流，监督员 P2）
                    lastScrolledBar = curBar
                    scrollState.animateScrollTo((nameGutter + curBar * barWidth - 80f).toInt().coerceAtLeast(0))
                }
            } else {
                lastScrolledBar = -1
            }
        }
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
            Canvas(
                modifier = Modifier
                    .horizontalScroll(scrollState)
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
                val gutter = nameGutter
                val strings = listOf("e", "B", "G", "D", "A", "E")

                // 行首弦名（1 弦在最上）
                strings.forEachIndexed { i, name ->
                    drawLabel(textMeasurer, name, Offset(4.dp.toPx(), topPad + i * spacing), 10.sp, lineColor, bold = true)
                }

                // 段落名条
                sectionLabels.forEach { (bar, name) ->
                    val label = textMeasurer.measure(name, TextStyle(fontSize = 11.sp, color = sectionFg, fontWeight = FontWeight.SemiBold))
                    val padH = 6.dp.toPx()
                    val rectW = label.size.width + padH * 2
                    val rectH = label.size.height + 4.dp.toPx()
                    drawRoundRect(sectionBg, topLeft = Offset(gutter + bar * barWidth, 2.dp.toPx()), size = Size(rectW, rectH), cornerRadius = CornerRadius(6.dp.toPx()))
                    drawText(label, topLeft = Offset(gutter + bar * barWidth + padH, 4.dp.toPx()))
                }

                // 拍位刻度（每拍一条浅竖线）
                for (b in 0 until barCount) {
                    for (t in 1..3) {
                        val x = gutter + b * barWidth + barPad + t / 4f * (barWidth - 2 * barPad) // 拍位刻度（网格坐标系，非音符系）
                        drawLine(beatTickColor, Offset(x, topPad - 4.dp.toPx()), Offset(x, topPad + 5 * spacing + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
                    }
                }

                // 六根谱线（1 弦在最上）
                for (s in 0..5) {
                    val y = topPad + s * spacing
                    drawLine(lineColor, Offset(gutter, y), Offset(gutter + barCount * barWidth, y), strokeWidth = 1.2.dp.toPx()) // 弦名行起
                }

                // 播放光标：当前小节整框高亮（位置来自 TonePlayer 进度流）
                if (positionMs >= 0) {
                    val secPerBeat = 60.0 / doc.tempo
                    val curBeat = positionMs / 1000.0 / secPerBeat
                    val curBar = (curBeat / 4).toInt().coerceIn(0, barCount - 1)
                    val x0 = gutter + curBar * barWidth
                    drawRoundRect(
                        Color(0x334CD964),
                        topLeft = Offset(x0 + 1f, topPad - 8f),
                        size = androidx.compose.ui.geometry.Size(barWidth - 2f, 5 * spacing + 16f),
                        cornerRadius = CornerRadius(6f),
                    )
                }

                // 小节线 + 小节号 + 终止双线
                for (b in 0..barCount) {
                    val x = gutter + b * barWidth
                    drawLine(lineColor, Offset(x, topPad - 8.dp.toPx()), Offset(x, topPad + 5 * spacing + 8.dp.toPx()), strokeWidth = if (b == barCount) 2.dp.toPx() else 1.5.dp.toPx())
                    if (b == barCount) {
                        drawLine(lineColor, Offset(x - 4.dp.toPx(), topPad - 8.dp.toPx()), Offset(x - 4.dp.toPx(), topPad + 5 * spacing + 8.dp.toPx()), strokeWidth = 2.dp.toPx())
                    }
                }
                for (b in 0 until barCount) {
                    drawLabel(textMeasurer, "${b + 1}", Offset(gutter + b * barWidth + 3.dp.toPx(), topPad - 20.dp.toPx()), 9.sp, lineColor)
                }

                // 音符：品数落线（底块护读），同拍多音自然竖直对齐
                placed.forEach { p ->
                    val isSel = selected == p.barIndex to p.noteIndex
                    val label = textMeasurer.measure(
                        "${p.note.fret}",
                        TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else noteFg, fontFamily = FontFamily.Monospace),
                    )
                    val padH = 4.dp.toPx()
                    val w = label.size.width + padH * 2
                    val h = label.size.height + 2.dp.toPx()
                    val bg = when {
                        isSel -> selColor
                        p.note.lowConfidence -> Color(0x55E53935) // P0-8 置信度热图：低置信音红色警示
                        else -> noteBg
                    }
                    drawRoundRect(
                        bg,
                        topLeft = Offset(p.x - w / 2, p.y - h / 2),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                    if (isSel) {
                        drawRoundRect(
                            selColor,
                            topLeft = Offset(p.x - w / 2 - 2.dp.toPx(), p.y - h / 2 - 2.dp.toPx()),
                            size = Size(w + 4.dp.toPx(), h + 4.dp.toPx()),
                            cornerRadius = CornerRadius(5.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                    drawText(label, topLeft = Offset(p.x - label.size.width / 2f, p.y - label.size.height / 2f))

                    // 技巧记号（音上方）
                    p.note.technique?.let { tech ->
                        drawLabel(textMeasurer, techMark(tech), Offset(p.x - 6.dp.toPx(), p.y - spacing * 0.72f), 9.sp, selColor, bold = true)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawLabel(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    at: Offset,
    size: androidx.compose.ui.unit.TextUnit,
    color: Color,
    bold: Boolean = false,
) {
    val measured = textMeasurer.measure(text, TextStyle(fontSize = size, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    drawText(measured, topLeft = at)
}

private fun techMark(technique: String): String = when (technique) {
    "hammer_on" -> "H"
    "pull_off" -> "P"
    "slide" -> "S"
    "bend" -> "B"
    "palm_mute" -> "PM"
    "mute" -> "M"
    "vibrato" -> "V"
    "harmonic" -> "A"
    else -> ""
}
