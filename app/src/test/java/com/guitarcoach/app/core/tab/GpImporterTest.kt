@file:OptIn(kotlin.contracts.ExperimentalContracts::class)

package com.guitarcoach.app.core.tab

import alphaTab.Settings
import alphaTab.importer.ScoreLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F205：GP 导入映射测试。alphaTex 走 alphaTab 同一 Score 模型 → GpImporter.importScore，
 * 映射逻辑全覆盖；GP 二进制端到端（真实 .gp3~.gp8 文件）真机验收时补测。
 *
 * 口径（实测确认）：alphaTex 语法音符为 `品.弦` 且弦号从低音 E 起往上数（.1=低E=TabDocument 的 6 弦）；
 * alphaTab 模型 note.string 与 TabDocument 同为 1=高音E，映射无需反演。
 */
class GpImporterTest {

    private fun texToDoc(tex: String): TabDocument =
        GpImporter.importScore(ScoreLoader.loadAlphaTex(tex, Settings()))

    @Test
    fun `alphaTex标题速度小节与音符映射`() {
        val doc = texToDoc("""\title "导入测试" \tempo 120 . 1.2 3.2 0.1 5.1 | 3.1 3.1 3.1 3.1""")
        assertEquals("导入测试", doc.title)
        assertEquals(120, doc.tempo)
        assertEquals(1, doc.sections.size)
        assertEquals(2, doc.sections[0].bars.size)
        val bar1 = doc.sections[0].bars[0]
        assertEquals(4, bar1.notes.size)
        assertEquals(0.0, bar1.notes[0].beat, 1e-9)
        // alphaTex .2 = A 弦 = TabDocument 5 弦；.1 = 低 E = 6 弦
        assertEquals(5, bar1.notes[0].string)
        assertEquals(1, bar1.notes[0].fret)
        assertEquals(6, bar1.notes[2].string)
        assertEquals(0, bar1.notes[2].fret)
        // 拍位按 quarter 递增（4/4 小节内 4 个四分音符）
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0), bar1.notes.map { it.beat })
    }

    @Test
    fun `空弦与高把位映射不越界`() {
        val doc = texToDoc("""\title "T" . 0.1 0.1 0.1 0.1 | 3.6 3.6 3.6 3.6""")
        val notes = doc.sections.flatMap { it.bars.flatMap { b -> b.notes } }
        assertEquals(8, notes.size)
        // .1 = 低 E（TabDocument 6 弦）空弦
        assertEquals(6, notes.first().string)
        assertEquals(0, notes.first().fret)
        // .6 = 高 E（TabDocument 1 弦）3 品
        assertEquals(1, notes.last().string)
        assertEquals(3, notes.last().fret)
        assertTrue(notes.all { it.string in 1..6 && it.fret in 0..24 })
    }

    @Test
    fun `非法输入抛可读错误`() {
        val e = runCatching { GpImporter.import(ByteArray(0)) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
        assertTrue(e!!.message!!.contains("解析失败"))
    }
}
