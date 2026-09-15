package com.guitarcoach.app.core.tab

import com.guitarcoach.app.core.music.midiToFreq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabLayoutTest {

    private fun note(string: Int, fret: Int, beat: Double) = NoteEvent(string = string, fret = fret, beat = beat)

    private val doc = TabDocument(
        sections = listOf(
            TabSection("Main", listOf(TabBar(listOf(note(1, 3, 0.0), note(2, 3, 2.0))), TabBar(listOf(note(6, 0, 0.5))))),
        ),
    )

    private val g = TabLayout.Geometry(barWidthPx = 200f, stringSpacingPx = 20f, topPadPx = 30f, barPadPx = 12f)

    @Test
    fun `y坐标按弦号排布1弦在最上`() {
        val placed = TabLayout.layout(doc, g)
        val s1 = placed.first { it.note.string == 1 }
        val s2 = placed.first { it.note.string == 2 }
        val s6 = placed.first { it.note.string == 6 }
        assertEquals(30f, s1.y, 1e-4f)
        assertEquals(50f, s2.y, 1e-4f)
        assertEquals(130f, s6.y, 1e-4f)
    }

    @Test
    fun `x坐标按拍比例铺开且第二小节平移一个宽度`() {
        val placed = TabLayout.layout(doc, g)
        val bar1 = placed.filter { it.barIndex == 0 }.sortedBy { it.note.beat }
        // beat 0 → 内区左缘 = 0*200+12 = 12
        assertEquals(12f, bar1[0].x, 1e-4f)
        // beat 2 / maxBeat 2 = 1.0 → 内区右缘 = 12 + 176 = 188
        assertEquals(188f, bar1[1].x, 1e-4f)
        // 第二小节 beat 0.5：maxBeat 下限 1.0 → 比例 0.5 → x = 200 + 12 + 88 = 300
        val bar2 = placed.first { it.barIndex == 1 }
        assertEquals(300f, bar2.x, 1e-4f)
    }

    @Test
    fun `空小节不产生音符且索引连续`() {
        val withEmpty = doc.copy(sections = listOf(TabSection("Main", listOf(TabBar(emptyList()), doc.sections[0].bars[0]))))
        val placed = TabLayout.layout(withEmpty, g)
        assertTrue(placed.size == 2)
        assertTrue(placed.all { it.barIndex == 1 })
    }

    @Test
    fun `midi到频率换算A4为440`() {
        assertEquals(440.0, midiToFreq(69), 1e-9)
        assertEquals(82.407, midiToFreq(40), 0.01) // 6弦空弦 E2
        assertEquals(329.628, midiToFreq(64), 0.01) // 1弦空弦 E4
    }
}
