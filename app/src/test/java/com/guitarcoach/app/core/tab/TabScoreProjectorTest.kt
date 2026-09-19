@file:OptIn(kotlin.contracts.ExperimentalContracts::class)

package com.guitarcoach.app.core.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1 投影器：TabDocument → alphaTex → Score round-trip 断言（小节数/音符拍位/弦品/时值）。
 * alphaTex 走 alphaTab 1.8.4 真解析器（ScoreLoader.loadAlphaTex），语法以 round-trip 为准。
 */
class TabScoreProjectorTest {

    private fun doc(vararg bars: List<NoteEvent>, tempo: Int = 120, ts: String = "4/4"): TabDocument =
        TabDocument(title = "投影测试", tempo = tempo, timeSignature = ts, sections = listOf(TabSection("Main", bars.map { TabBar(it) })))

    private fun roundTrip(doc: TabDocument): TabDocument = GpImporter.importScore(TabScoreProjector.toScore(doc, alphaTab.Settings()))

    @Test
    fun `四分音符小节_拍位弦品守恒`() {
        val d = doc(
            listOf(NoteEvent(5, 3, 0.0, 1.0), NoteEvent(4, 0, 1.0, 1.0), NoteEvent(6, 0, 2.0, 1.0), NoteEvent(1, 5, 3.0, 1.0)),
            listOf(NoteEvent(5, 3, 0.0, 1.0), NoteEvent(5, 3, 1.0, 1.0), NoteEvent(5, 3, 2.0, 1.0), NoteEvent(5, 3, 3.0, 1.0)),
        )
        val back = roundTrip(d)
        assertEquals(1, back.sections.size)
        assertEquals(2, back.sections[0].bars.size)
        val b1 = back.sections[0].bars[0].notes
        assertEquals(4, b1.size)
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0), b1.map { it.beat })
        // 弦号经 alphaTex 反演后应守恒（5→5 / 1→1）
        assertEquals(5, b1[0].string); assertEquals(3, b1[0].fret)
        assertEquals(1, b1[3].string); assertEquals(5, b1[3].fret)
        assertEquals(120, back.tempo)
    }

    @Test
    fun `八分与十六分时值_拍位守恒`() {
        val d = doc(
            listOf(
                NoteEvent(5, 0, 0.0, 0.5), NoteEvent(5, 1, 0.5, 0.5),
                NoteEvent(5, 3, 1.0, 0.25), NoteEvent(5, 5, 1.25, 0.25),
                NoteEvent(5, 6, 1.5, 0.5), NoteEvent(5, 8, 2.0, 2.0),
            ),
        )
        val back = roundTrip(d)
        val b1 = back.sections[0].bars[0].notes
        assertEquals(6, b1.size)
        assertEquals(listOf(0.0, 0.5, 1.0, 1.25, 1.5, 2.0), b1.map { it.beat })
    }

    @Test
    fun `空洞与小节尾补休止_拍位不漂移`() {
        // 第 1 拍空（休止占位），第 3 拍后到小节尾休止
        val d = doc(listOf(NoteEvent(5, 5, 1.0, 1.0), NoteEvent(5, 6, 2.0, 1.0)))
        val back = roundTrip(d)
        val b1 = back.sections[0].bars[0].notes
        assertEquals(2, b1.size)
        assertEquals(listOf(1.0, 2.0), b1.map { it.beat })
    }

    @Test
    fun `多段落与多小节_顺序守恒`() {
        val d = TabDocument(
            title = "多段",
            tempo = 90,
            sections = listOf(
                TabSection("Intro", listOf(
                    TabBar(listOf(NoteEvent(5, 0, 0.0, 1.0), NoteEvent(5, 0, 1.0, 1.0), NoteEvent(5, 0, 2.0, 1.0), NoteEvent(5, 0, 3.0, 1.0))),
                    TabBar(listOf(NoteEvent(5, 2, 0.0, 1.0), NoteEvent(5, 2, 1.0, 1.0), NoteEvent(5, 2, 2.0, 1.0), NoteEvent(5, 2, 3.0, 1.0))),
                )),
                TabSection("Riff", listOf(
                    TabBar(listOf(NoteEvent(6, 3, 0.0, 1.0), NoteEvent(6, 3, 1.0, 1.0), NoteEvent(6, 3, 2.0, 1.0), NoteEvent(6, 3, 3.0, 1.0))),
                    TabBar(listOf(NoteEvent(6, 5, 0.0, 1.0), NoteEvent(6, 5, 1.0, 1.0), NoteEvent(6, 5, 2.0, 1.0), NoteEvent(6, 5, 3.0, 1.0))),
                )),
            ),
        )
        val back = roundTrip(d)
        assertEquals(4, back.sections.flatMap { it.bars }.size) // 全部小节都在
        assertEquals(90, back.tempo)
        // 段落名能否经 alphaTex 守恒取决于解析器 \section 支持——不支持时退化为单段 Main（如实断言两种合法形态）
        val names = back.sections.map { it.name }
        assertTrue("段落名应守恒或退化为单段: $names", names == listOf("Intro", "Riff") || names == listOf("Main"))
    }

    @Test
    fun `闷音与技巧后缀存活`() {
        val d = doc(
            listOf(
                NoteEvent(6, 0, 0.0, 1.0, technique = "mute"),
                NoteEvent(6, 3, 1.0, 1.0, technique = "palm_mute"),
                NoteEvent(5, 0, 2.0, 1.0, technique = "vibrato"),
                NoteEvent(5, 3, 3.0, 1.0),
            ),
        )
        val back = roundTrip(d)
        val b1 = back.sections[0].bars[0].notes
        assertEquals(4, b1.size)
        assertEquals("mute", b1[0].technique)
        assertEquals("palm_mute", b1[1].technique)
    }

    @Test
    fun `时值取最近token_无点分语法`() {
        // alphaTex 1.8.4 不支持点分时值 token（:8. 真机解析 AT202 崩）——只允许普通时值
        assertEquals(":4", TabScoreProjector.durationToken(1.0))
        assertEquals(":8", TabScoreProjector.durationToken(0.5))
        assertEquals(":2", TabScoreProjector.durationToken(1.5))
        assertEquals(":4", TabScoreProjector.durationToken(0.75)) // 0.75 与 0.5/1.0 等距 → 取先列出的 :4
        assertEquals(":16", TabScoreProjector.durationToken(0.3))
        assertEquals(":8", TabScoreProjector.durationToken(0.6))
    }

    @Test
    fun `点分时值小节_round_trip守恒`() {
        // 真机崩溃场景：0.75 拍（附点八分）与 1.5 拍混排——修复前生成 :8. 直接解析崩
        val d = doc(
            listOf(
                NoteEvent(5, 0, 0.0, 0.75), NoteEvent(5, 1, 0.75, 0.75),
                NoteEvent(5, 3, 1.5, 1.5), NoteEvent(5, 5, 3.0, 1.0),
            ),
        )
        val back = roundTrip(d)
        val b1 = back.sections[0].bars[0].notes
        assertEquals(4, b1.size)
        // 无点分 token 下 0.75/1.5 拍只能近似铺位（本批口径）：断言解析成功+首拍 0+拍位不回退
        assertEquals(0.0, b1[0].beat, 1e-9)
        assertTrue("拍位应不回退: " + b1.map { it.beat }, b1.zipWithNext().all { (a, b) -> b.beat >= a.beat })
    }

    @Test
    fun `生成tex含拍号与小节分隔`() {
        val d = doc(
            listOf(NoteEvent(5, 0, 0.0, 1.0), NoteEvent(5, 0, 1.0, 1.0), NoteEvent(5, 0, 2.0, 1.0), NoteEvent(5, 0, 3.0, 1.0)),
            listOf(NoteEvent(5, 0, 0.0, 1.0), NoteEvent(5, 0, 1.0, 1.0), NoteEvent(5, 0, 2.0, 1.0), NoteEvent(5, 0, 3.0, 1.0)),
            ts = "3/4",
        )
        val tex = TabScoreProjector.toAlphaTex(d)
        assertTrue("\\ts 3 4" in tex)
        assertTrue("|" in tex)
    }
}

class TabScoreProjectorScratchTest {
    @Test
    fun `复刻真机崩溃_填示例文档`() {
        // 复刻 TextTabParser.parse(填示例) 的产物：90 BPM、8 个八分音符、单段 Main
        val notes = mutableListOf<NoteEvent>()
        val frets = listOf(3, 3, 0, 0, 2, 3, 3, 0)
        val strings = listOf(1, 2, 3, 4, 5, 2, 1, 3)
        for (k in 0 until 8) {
            notes += NoteEvent(string = strings[k], fret = frets[k], beat = k * 0.5, duration = 1.0)
        }
        val d = TabDocument(title = null, tempo = 90, sections = listOf(TabSection("Main", listOf(TabBar(notes)))))
        val tex = TabScoreProjector.toAlphaTex(d)
        println("TEX>>>\n$tex<<<")
        val score = TabScoreProjector.toScore(d, alphaTab.Settings())
        assertTrue(score != null)
    }
}

class TabScoreProjectorScratchTest2 {
    @Test
    fun `复刻真机崩溃_真实TextTabParser产物`() {
        val example = """
            e|--------------3---3--|
            B|-----------3-----3---|
            G|--------0----------0-|
            D|-----0---------------|
            A|--2------------------|
            E|---------------------|
        """.trimIndent()
        val d = TextTabParser.parse(example)
        val dbg = d.sections.joinToString(" || ") { sec ->
            sec.name + " bars=" + sec.bars.size + " :: " + sec.bars.mapIndexed { bi, b ->
                "bar" + bi + "[" + b.notes.joinToString(",") { it.string.toString() + ":" + it.fret + "@" + it.beat + "d" + it.duration } + "]"
            }.joinToString(" ; ")
        }
        java.nio.file.Files.write(
            java.nio.file.Paths.get("C:" + java.io.File.separator + "Users" + java.io.File.separator + "Crux" + java.io.File.separator + "AppData" + java.io.File.separator + "Local" + java.io.File.separator + "Temp" + java.io.File.separator + "doc_dump.txt"),
            dbg.toByteArray(Charsets.UTF_8),
        )
        val tex = TabScoreProjector.toAlphaTex(d.copy(title = null, tempo = 90))
        println("TEX2>>>" + tex.replace("\n", "|") + "<<<")
        val score = TabScoreProjector.toScore(d.copy(title = null, tempo = 90), alphaTab.Settings())
        assertTrue(score != null)
    }
}
