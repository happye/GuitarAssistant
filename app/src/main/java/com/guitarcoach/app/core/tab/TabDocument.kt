package com.guitarcoach.app.core.tab

import kotlinx.serialization.Serializable

/**
 * 谱面的统一数据模型：文本 Tab 解析、Guitar Pro 导入、LLM 视觉识谱，全部汇入这一结构；
 * 之后的渲染、播放、讲解、练习记录都基于它。
 *
 * 约定：string 1 = 最细的高音 E 弦（六线谱从上往下第 1 条线），string 6 = 最粗的低音 E 弦。
 */
@Serializable
data class TabDocument(
    val title: String? = null,
    val tempo: Int = 90,
    val tuning: String = "E标准调弦",
    val sections: List<TabSection> = emptyList(),
)

@Serializable
data class TabSection(val name: String = "Main", val bars: List<TabBar> = emptyList())

@Serializable
data class TabBar(val notes: List<NoteEvent> = emptyList())

@Serializable
data class NoteEvent(
    val string: Int,               // 1~6（1=高音E）
    val fret: Int,                 // 0 = 空弦
    val beat: Double,              // 小节内起始拍（0 起）
    val duration: Double = 1.0,    // 占几拍
    val technique: String? = null, // palm_mute / hammer_on / pull_off / slide / bend / vibrato / mute / harmonic
)

/** 标准调弦下 1~6 弦空弦的 MIDI 音高：E4 B3 G3 D3 A2 E2。 */
val STANDARD_TUNING_MIDI = intArrayOf(64, 59, 55, 50, 45, 40)

fun NoteEvent.midi(): Int = STANDARD_TUNING_MIDI[string - 1] + fret

private val SHARP_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

fun midiToName(midi: Int): String = SHARP_NAMES[((midi % 12) + 12) % 12] + (midi / 12 - 1)
