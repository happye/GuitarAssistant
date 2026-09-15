package com.guitarcoach.app.core.coach

import com.guitarcoach.app.core.tab.NoteEvent
import com.guitarcoach.app.core.tab.Phrase
import com.guitarcoach.app.core.tab.TabBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseExplainCodecTest {

    // 乐句 = 全曲第 3~4 小节，共 3 个音
    private val phrase = Phrase(
        index = 2,
        sectionName = "Main",
        barStart = 3,
        bars = listOf(
            TabBar(listOf(NoteEvent(string = 6, fret = 0, beat = 0.0), NoteEvent(string = 5, fret = 2, beat = 1.0))),
            TabBar(listOf(NoteEvent(string = 4, fret = 0, beat = 0.0))),
        ),
    )

    private val validJson = """
        ```json
        {"index":9,"summary":"低音下行，像走路一样。",
         "steps":[{"bar":3,"beat":0.0,"string":6,"fret":0,"finger":0,"pick":"下拨","text":"空弦E，拨一下就行"},
                  {"bar":3,"beat":1.0,"string":5,"fret":2,"finger":1,"pick":"下拨","text":"食指按2品"},
                  {"bar":4,"beat":0.0,"string":4,"fret":0,"finger":0,"pick":"上拨","text":"空弦D"}],
         "terms":[{"term":"空弦","plain":"左手不按，只拨弦"}],
         "difficulty":"换弦时保持节奏均匀","practice":"60 BPM 每音一拍，循环 8 遍"}
        ```
    """.trimIndent()

    @Test
    fun `合法讲解解析通过且index以分段器为准`() {
        val explain = PhraseExplainCodec.parse(validJson, phrase)
        assertEquals(2, explain.index)
        assertEquals(3, explain.steps.size)
        assertNull(PhraseExplainCodec.validate(explain, phrase))
        assertEquals(0, explain.steps[0].finger)
        assertEquals("食指按2品", explain.steps[1].text)
    }

    @Test
    fun `缺finger被拦截`() {
        // finger 缺省 -1（模型没给）→ 校验必须拦下
        val ex = PhraseExplain(index = 2, summary = "总述", steps = listOf(PhraseExplain.Step(bar = 3, string = 6, fret = 0)))
        val error = PhraseExplainCodec.validate(ex, phrase)
        assertTrue(error!!.contains("finger"))
    }

    @Test
    fun `steps为空被拦截`() {
        val empty = """{"index":2,"summary":"总述","steps":[],"terms":[],"difficulty":"d","practice":"p"}"""
        val e = assertThrows(IllegalArgumentException::class.java) { PhraseExplainCodec.parse(empty, phrase) }
        assertTrue(e.message!!.contains("steps 为空"))
    }

    @Test
    fun `术语缺大白话解释被拦截`() {
        val badTerm = """{"index":2,"summary":"总述","steps":[{"bar":3,"string":6,"fret":0,"finger":0}],"terms":[{"term":"闷音","plain":""}],"difficulty":"d","practice":"p"}"""
        val e = assertThrows(IllegalArgumentException::class.java) { PhraseExplainCodec.parse(badTerm, phrase) }
        assertTrue(e.message!!.contains("闷音"))
    }

    @Test
    fun `小节号越界被拦截`() {
        val outOfRange = """{"index":2,"summary":"总述","steps":[{"bar":9,"string":6,"fret":0,"finger":0}],"terms":[],"difficulty":"d","practice":"p"}"""
        val e = assertThrows(IllegalArgumentException::class.java) { PhraseExplainCodec.parse(outOfRange, phrase) }
        assertTrue(e.message!!.contains("小节号"))
    }

    @Test
    fun `没有JSON本体抛可读错误`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            PhraseExplainCodec.parse("抱歉，我讲不了", phrase)
        }
        assertTrue(e.message!!.contains("JSON"))
    }
}
