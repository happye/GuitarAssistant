package com.guitarcoach.app.core.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 和弦库数据准确性：公认标准指法抽查 + 横按平移口径。 */
class ChordLibraryTest {

    @Test
    fun `开放和弦标准把位`() {
        fun frets(name: String) = ChordLibrary.find(name)!!.frets
        assertEquals(listOf(-1, 3, 2, 0, 1, 0), frets("C"))
        assertEquals(listOf(-1, 0, 2, 2, 1, 0), frets("Am"))
        assertEquals(listOf(0, 2, 2, 1, 0, 0), frets("E"))
        assertEquals(listOf(0, 2, 2, 0, 0, 0), frets("Em"))
        assertEquals(listOf(-1, -1, 0, 2, 3, 2), frets("D"))
        assertEquals(listOf(-1, -1, 0, 2, 3, 1), frets("Dm"))
        assertEquals(listOf(3, 2, 0, 0, 0, 3), frets("G"))
        assertEquals(listOf(-1, 2, 1, 2, 0, 2), frets("B7"))
    }

    @Test
    fun `横按按品位平移`() {
        assertEquals(listOf(1, 3, 3, 2, 1, 1), ChordLibrary.find("F")!!.frets)
        assertEquals(listOf(2, 4, 4, 3, 5, 2), ChordLibrary.find("F#m")!!.frets)
        assertEquals(listOf(2, 4, 4, 3, 2, 2), ChordLibrary.find("B")!!.frets)
        assertEquals(1, ChordLibrary.find("F")!!.barreFret)
    }

    @Test
    fun `强力和弦根音弦同品三连`() {
        val g5 = ChordLibrary.find("G5")!!
        assertEquals(listOf(3, 3, 3, -1, -1, -1), g5.frets) // 6/5/4 弦 3 品
        val d5 = ChordLibrary.find("D5")!!
        assertEquals(listOf(-1, 5, 5, 5, -1, -1), d5.frets) // 5/4/3 弦 5 品
    }

    @Test
    fun `查询大小写不敏感且全部形状合法`() {
        assertEquals("Am", ChordLibrary.find("am")!!.name)
        for (c in ChordLibrary.ALL) {
            assertEquals("和弦 ${c.name} 品数应为 6", 6, c.frets.size)
            assertTrue("和弦 ${c.name} 品位越界", c.frets.all { it == -1 || it in 0..15 })
            assertTrue("和弦 ${c.name} 音符数", c.frets.count { it >= 0 } in 2..6)
        }
    }
}
