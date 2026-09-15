package com.guitarcoach.app.data

import com.guitarcoach.app.core.coach.PhraseCoach
import com.guitarcoach.app.core.coach.PhraseExplain
import com.guitarcoach.app.core.tab.NoteEvent
import com.guitarcoach.app.core.tab.TabBar
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.core.tab.TabSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhraseCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val doc = TabDocument(
        sections = listOf(TabSection("Main", listOf(TabBar(listOf(NoteEvent(6, 0, 0.0)))))),
    )

    private val report = PhraseCoach.Report(
        phrases = listOf(
            PhraseExplain(
                index = 1,
                summary = "低音起步",
                steps = listOf(PhraseExplain.Step(bar = 1, string = 6, fret = 0, finger = 0, text = "空弦")),
            ),
        ),
        issues = emptyList(),
        coveNotes = emptyList(),
        rounds = 1,
    )

    @Test
    fun `保存后加载往返一致`() {
        val cache = PhraseCache(tmp.root)
        cache.save(doc, report)
        assertEquals(report, cache.load(doc))
    }

    @Test
    fun `谱面编辑后内容寻址自动失效`() {
        val cache = PhraseCache(tmp.root)
        cache.save(doc, report)
        val edited = doc.copy(tempo = 91)
        assertNull(cache.load(edited))
        // 原键仍命中
        assertEquals(report, cache.load(doc))
    }

    @Test
    fun `损坏的缓存文件静默返回null不崩溃`() {
        val cache = PhraseCache(tmp.root)
        val file = java.io.File(tmp.root, "phrase_cache/${cache.key(doc)}.json")
        file.parentFile.mkdirs()
        file.writeText("不是JSON{{{")
        assertNull(cache.load(doc))
        assertTrue(file.exists())
    }

    @Test
    fun `未保存时加载为null`() {
        assertNull(PhraseCache(tmp.root).load(doc))
    }
}
