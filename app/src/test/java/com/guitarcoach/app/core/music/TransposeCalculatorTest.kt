package com.guitarcoach.app.core.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * F206 验收用例（feature_list）：已知输入算已知输出，≥10 组全对。
 * 核心口径用例：C 调的歌用 G 调指法 → 变调夹夹 5 品。
 */
class TransposeCalculatorTest {

    // ---- 音名解析 ----

    @Test
    fun `音名解析支持升降号与非法拒绝`() {
        assertEquals(0, TransposeCalculator.parseNote("C"))
        assertEquals(1, TransposeCalculator.parseNote("C#"))
        assertEquals(1, TransposeCalculator.parseNote("Db"))
        assertEquals(10, TransposeCalculator.parseNote("Bb"))
        assertEquals(10, TransposeCalculator.parseNote("A#"))
        assertEquals(11, TransposeCalculator.parseNote("B"))
        // 小写与 unicode 记号归一
        assertEquals(6, TransposeCalculator.parseNote("f#"))
        assertEquals(3, TransposeCalculator.parseNote("E♭"))
        // 非法
        assertNull(TransposeCalculator.parseNote("H"))
        assertNull(TransposeCalculator.parseNote(""))
        assertNull(TransposeCalculator.parseNote("C##"))
        assertNull(TransposeCalculator.parseNote("1"))
    }

    // ---- 移调半音差 ----

    @Test
    fun `移调半音差顺时针计算`() {
        assertEquals(7, TransposeCalculator.semitonesUp("C", "G")) // C→G 上移 7 半音
        assertEquals(5, TransposeCalculator.semitonesUp("G", "C")) // 反向 5 半音（下移 7 的等价）
        assertEquals(0, TransposeCalculator.semitonesUp("C", "C"))
        assertEquals(5, TransposeCalculator.semitonesUp("F", "Bb"))
        assertEquals(6, TransposeCalculator.semitonesUp("B", "F"))
        assertNull(TransposeCalculator.semitonesUp("X", "C"))
        assertNull(TransposeCalculator.semitonesUp("C", "H"))
    }

    // ---- 和弦转调：根音与后缀 ----

    @Test
    fun `和弦转调只动根音后缀原样保留`() {
        val c2g = 7
        assertEquals("G", TransposeCalculator.transposeChord("C", c2g))
        assertEquals("D", TransposeCalculator.transposeChord("G", c2g))
        assertEquals("Em", TransposeCalculator.transposeChord("Am", c2g))
        assertEquals("C", TransposeCalculator.transposeChord("F", c2g))
        assertEquals("Bm", TransposeCalculator.transposeChord("Em", c2g))
        assertEquals("Cmaj7", TransposeCalculator.transposeChord("Fmaj7", c2g))
        assertEquals("Esus4", TransposeCalculator.transposeChord("Dsus4", 2))
        assertEquals("Dadd9", TransposeCalculator.transposeChord("Cadd9", 2))
        assertEquals("Fdim", TransposeCalculator.transposeChord("Bbdim", 7))
        assertEquals("G#", TransposeCalculator.transposeChord("Eb", 5))
    }

    @Test
    fun `斜杠和弦根音低音都转`() {
        assertEquals("G/D", TransposeCalculator.transposeChord("C/G", 7))
        assertEquals("Dm/F", TransposeCalculator.transposeChord("Am/C", 5))
        assertEquals("A/C#", TransposeCalculator.transposeChord("F/A", 4))
        // 低音不是合法音名 → 整体失败
        assertNull(TransposeCalculator.transposeChord("C/X", 7))
        assertNull(TransposeCalculator.transposeChord("C/", 7))
    }

    @Test
    fun `典型进行整体转调C调到G调`() {
        // C G Am F —— 7 半音
        val out = TransposeCalculator.transposeChords(listOf("C", "G", "Am", "F"), 7)
        assertEquals(listOf("G", "D", "Em", "C"), out)
        // 含无法识别的和弦时逐条标 null，不整批失败
        val mixed = TransposeCalculator.transposeChords(listOf("C", "Hm", "G"), 7)
        assertEquals(listOf("G", null, "D"), mixed)
    }

    @Test
    fun `非法和弦与空串返回null`() {
        assertNull(TransposeCalculator.transposeChord("", 3))
        assertNull(TransposeCalculator.transposeChord("X7", 3))
        assertNull(TransposeCalculator.transposeChord("123", 3))
    }

    @Test
    fun `十二半音回到原调`() {
        assertEquals("Am", TransposeCalculator.transposeChord("Am", 12))
        assertEquals("F#m7", TransposeCalculator.transposeChord("F#m7", 24))
    }

    // ---- 变调夹 ----

    @Test
    fun `核心验收用例C调用G指法夹5品`() {
        assertEquals(5, TransposeCalculator.capoFret("C", "G"))
        // 形状调 + 品数 = 原调，闭环互证
        assertEquals("C", TransposeCalculator.soundingKey("G", 5))
    }

    @Test
    fun `变调夹品数组用例`() {
        assertEquals(2, TransposeCalculator.capoFret("D", "C"))
        assertEquals(2, TransposeCalculator.capoFret("E", "D"))
        assertEquals(1, TransposeCalculator.capoFret("F", "E"))
        assertEquals(2, TransposeCalculator.capoFret("A", "G"))
        assertEquals(3, TransposeCalculator.capoFret("Bb", "G"))
        assertEquals(0, TransposeCalculator.capoFret("G", "G")) // 同调不用夹
        assertEquals(0, TransposeCalculator.capoFret("C", "C"))
        assertNull(TransposeCalculator.capoFret("Q", "G"))
    }

    @Test
    fun `夹品后实际调与反查指法调`() {
        assertEquals("C", TransposeCalculator.soundingKey("G", 5))
        assertEquals("D", TransposeCalculator.soundingKey("C", 2))
        assertEquals("E", TransposeCalculator.soundingKey("D", 2))
        assertEquals("E", TransposeCalculator.soundingKey("E", 0))
        // 反查：C 调夹 5 品 → 用 G 指法；夹 0 品 → 用 C 指法
        assertEquals("G", TransposeCalculator.shapeKeyForCapo("C", 5))
        assertEquals("C", TransposeCalculator.shapeKeyForCapo("C", 0))
        assertNull(TransposeCalculator.soundingKey("Q", 5))
    }
}
