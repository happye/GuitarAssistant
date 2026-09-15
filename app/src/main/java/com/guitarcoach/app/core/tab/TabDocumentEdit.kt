package com.guitarcoach.app.core.tab

/**
 * TabDocument 的编辑原语（F203）：识别结果手动修正后回写唯一谱面模型。
 * 纯数据变换，UI 只负责收集输入，合法性校验在这里做。
 */

/** 校验并构造音符：弦 1~6、品 0~24、拍 ≥0；非法返回 null（调用方提示用户）。 */
fun makeNote(string: Int, fret: Int, beat: Double, technique: String? = null): NoteEvent? {
    if (string !in 1..6) return null
    if (fret !in 0..24) return null
    if (beat.isNaN() || beat < 0.0) return null
    return NoteEvent(string = string, fret = fret, beat = beat, technique = technique)
}

/** 替换指定段落/小节的小节内容（索引越界抛 IllegalArgumentException）。 */
fun TabDocument.withBar(sectionIndex: Int, barIndex: Int, newBar: TabBar): TabDocument {
    val section = sections.getOrNull(sectionIndex)
        ?: throw IllegalArgumentException("段落索引越界：$sectionIndex")
    val bars = section.bars.toMutableList()
    if (barIndex !in bars.indices) throw IllegalArgumentException("小节索引越界：$barIndex")
    bars[barIndex] = newBar
    val newSections = sections.toMutableList()
    newSections[sectionIndex] = section.copy(bars = bars)
    return copy(sections = newSections)
}

/** 全曲全局小节号（1 起，跨段落连续）：第 sectionIndex 段第 barIndex 小节的编号。 */
fun TabDocument.globalBarNumber(sectionIndex: Int, barIndex: Int): Int {
    var n = 1
    for (s in 0 until sectionIndex) n += sections[s].bars.size
    return n + barIndex
}
