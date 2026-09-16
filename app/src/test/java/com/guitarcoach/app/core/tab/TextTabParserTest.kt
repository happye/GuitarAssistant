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
    fun `多位数品数只读一次不重复`() {
        // 回归用例：逐列扫描版会把 "10" 拆成 10 与 0 两个音符（bug 0a01bf4 前身）
        val multi = """
            e|-------10---|
            B|------------|
            G|------------|
            D|------------|
            A|------------|
            E|------------|
        """.trimIndent()
        val doc = TextTabParser.parse(multi)
        val notes = doc.sections[0].bars[0].notes
        assertEquals(1, notes.count { it.string == 1 })
        val note = notes.first { it.string == 1 }
        assertEquals(10, note.fret)
    }

    @Test
    fun `和弦行与说明行不再误读为弦行`() {
        // v1 bug：任何以 A/D/e 开头的行都被当成弦行，和弦名 Am x02210 的数字全变假音符
        val withChords = """
            Am x02210
            Dropped D tuning
            e|--------------3--|
            B|-----------3-----|
            G|--------0--------|
            D|-----0-----------|
            A|--2--------------|
            E|-----------------|
        """.trimIndent()
        val doc = TextTabParser.parse(withChords)
        val bar = doc.sections[0].bars[0]
        // v1 会从和弦行读出 5 弦 0/2/2/1/0 假音符；v2 不应存在
        assertEquals(0, bar.notes.count { it.string == 5 && it.fret == 1 })
        assertEquals(0, bar.notes.count { it.string == 5 && it.fret == 2 && it.beat > 3 })
        assertTrue(bar.notes.none { it.fret == 1 }) // x02210 里的 1 不应出现（A 行真音是 2 品）
    }

    @Test
    fun `重复标记x2不产生音符`() {
        val repeat = """
            e|---3---3---(x2)--|
            B|-----------(x2)--|
            G|-----------------|
            D|-----------------|
            A|------------x2---|
            E|-----------------|
        """.trimIndent()
        val doc = TextTabParser.parse(repeat)
        val notes = doc.sections[0].bars[0].notes
        assertTrue(notes.none { it.string == 2 }) // B 行只有 (x2)，应无音符
        assertTrue(notes.none { it.string == 5 }) // A 行只有 x2，应无音符
        assertEquals(2, notes.count { it.string == 1 && it.fret == 3 })
    }

    @Test
    fun `拍位按小节归零不跨小节漂移`() {
        val twoBars = """
            e|--3---3---3---3-|--5----5-----|
            B|----------------|-------------|
            G|----------------|-------------|
            D|----------------|-------------|
            A|----------------|-------------|
            E|----------------|-------------|
        """.trimIndent()
        val doc = TextTabParser.parse(twoBars)
        val bars = doc.sections[0].bars
        assertEquals(2, bars.size) // 每个段（小节线之间）= 一个 TabBar，与渲染端 4/4 口径对齐
        assertEquals(listOf(0.5, 1.5, 2.5, 3.5), bars[0].notes.map { it.beat }) // 段内列 2/6/10/14 ÷ 4
        // v1 会把第二小节的 5 品算到 beat≈4.75+（全局列/4 漂移）；v2.1 归零：段内列 2/8 → 0.5/2.0
        assertEquals(listOf(0.5, 1.75), bars[1].notes.map { it.beat }) // 段内列 2/7 ÷ 4
    }

    @Test
    fun `技巧字符行不炸且目标音位置正确`() {
        val tech = """
            e|--3h5----7b9----|
            B|----------------|
            G|----------------|
            D|----------------|
            A|----------------|
            E|----------------|
        """.trimIndent()
        val doc = TextTabParser.parse(tech)
        val notes = doc.sections[0].bars[0].notes.filter { it.string == 1 }
        // 3、5（h 后目标）、7、9 都按位置读出；无技巧转 technique 是已知 v2 限制
        assertEquals(listOf(3, 5, 7, 9), notes.map { it.fret })
    }

    @Test
    fun `非谱文本抛可读错误`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            TextTabParser.parse("歌词第一行\n歌词第二行")
        }
        assertTrue(e.message!!.contains("六线谱"))
    }
}
