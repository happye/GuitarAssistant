package com.guitarcoach.app.core.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** F603：MIDI → 弦/品 DP + 节拍量化 + TabDocument 组装。 */
class MidiTabConverterTest {

    private fun note(midi: Int, timeSec: Double, durationSec: Double = 0.5) =
        MidiTabConverter.MidiNote(midi, timeSec, durationSec)

    @Test
    fun `单音映射到标准调弦正确弦品`() {
        val placed = MidiTabConverter.convert(listOf(note(40, 0.0)), bpm = 60)
        assertEquals(1, placed.size)
        assertEquals(6, placed[0].string) // E2 = 低 E 空弦
        assertEquals(0, placed[0].fret)
    }

    @Test
    fun `爬格子按低把位同弦分配`() {
        // 低 E 弦 1-2-3-4 品（41~44），DP 应全留在 6 弦（换弦代价 > 同弦移品）
        val placed = MidiTabConverter.convert(listOf(note(41, 0.0), note(42, 0.5), note(43, 1.0), note(44, 1.5)), bpm = 120)
        assertTrue(placed.all { it.string == 6 })
        assertEquals(listOf(1, 2, 3, 4), placed.map { it.fret })
    }

    @Test
    fun `高低音分散时选弦权衡合理`() {
        // 40（低E空弦）→ 64（高E空弦）：只能分别是 6 弦与 1 弦
        val placed = MidiTabConverter.convert(listOf(note(40, 0.0), note(64, 1.0)), bpm = 60)
        assertEquals(6, placed[0].string)
        assertEquals(1, placed[1].string)
    }

    @Test
    fun `节拍量化到四分之拍网格`() {
        assertEquals(1.0, MidiTabConverter.quantize(0.98), 1e-9)
        assertEquals(0.75, MidiTabConverter.quantize(0.8), 1e-9)
        assertEquals(0.0, MidiTabConverter.quantize(-0.3), 1e-9)
        val placed = MidiTabConverter.convert(listOf(note(40, 0.47)), bpm = 60) // 0.47 拍 → 0.5
        assertEquals(0.5, placed[0].beat, 1e-9)
    }

    @Test
    fun `转TabDocument按四拍切小节`() {
        val placed = MidiTabConverter.convert(
            listOf(note(40, 0.0), note(41, 0.5), note(42, 2.1), note(43, 4.2)),
            bpm = 60,
        )
        val doc = MidiTabConverter.toTabDocument(placed, bpm = 60, title = "扒谱测试")
        assertEquals("扒谱测试", doc.title)
        assertEquals(60, doc.tempo)
        assertEquals(2, doc.sections[0].bars.size) // beat 4.5 → 第 2 小节
        assertEquals(3, doc.sections[0].bars[0].notes.size)
        assertEquals(1, doc.sections[0].bars[1].notes.size)
    }

    @Test
    fun `超出音域抛可读错误`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            MidiTabConverter.convert(listOf(note(20, 0.0)), bpm = 60)
        }
        assertTrue(e.message!!.contains("音域"))
    }
}
