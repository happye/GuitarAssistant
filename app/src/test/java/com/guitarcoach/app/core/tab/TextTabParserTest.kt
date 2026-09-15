package com.guitarcoach.app.core.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTabParserTest {

    private val sample = """
        e|--------------3---3--|
        B|-----------3-----3---|
        G|--------0----------0-|
        D|-----0---------------|
        A|--2------------------|
        E|---------------------|
    """.trimIndent()

    @Test
    fun `标准六线谱解析出弦品结构`() {
        val doc = TextTabParser.parse(sample)
        assertEquals(1, doc.sections.size)
        val bar = doc.sections[0].bars[0]
        // 6 根弦各贡献音符（E 行无数字不算）
        assertTrue(bar.notes.any { it.string == 1 && it.fret == 3 }) // e 行
        assertTrue(bar.notes.any { it.string == 2 && it.fret == 3 }) // B 行
        assertTrue(bar.notes.any { it.string == 3 && it.fret == 0 }) // G 行
        assertTrue(bar.notes.any { it.string == 4 && it.fret == 0 }) // D 行
        assertTrue(bar.notes.any { it.string == 5 && it.fret == 2 }) // A 行
    }

    @Test
    fun `音高换算符合标准调弦`() {
        // 1弦3品 = E4 + 3 半音 = G4
        val g4 = NoteEvent(string = 1, fret = 3, beat = 0.0).midi()
        assertEquals(67, g4)
        assertEquals("G4", midiToName(g4))
        // 6弦空弦 = E2
        assertEquals(40, NoteEvent(string = 6, fret = 0, beat = 0.0).midi())
        assertEquals("E2", midiToName(40))
    }

    @Test
    fun `非法品数在解析时被忽略`() {
        val dirty = sample.replace("A|--2-", "A|--99") // 99 品超出合法范围
        val doc = TextTabParser.parse(dirty)
        val bar = doc.sections[0].bars[0]
        assertTrue(bar.notes.none { it.fret !in 0..24 })
        assertTrue(bar.notes.none { it.string == 5 }) // 5 弦只剩非法数字，应被丢弃
    }

    @Test
    fun `非谱文本抛可读错误`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            TextTabParser.parse("歌词第一行\n歌词第二行")
        }
        assertTrue(e.message!!.contains("六线谱"))
    }
}
