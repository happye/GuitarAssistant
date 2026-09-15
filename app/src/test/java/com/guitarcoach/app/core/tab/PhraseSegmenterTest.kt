package com.guitarcoach.app.core.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseSegmenterTest {

    private fun section(name: String, barCount: Int, notesInFirstBar: List<NoteEvent> = emptyList()) =
        TabSection(name, (0 until barCount).map { if (it == 0) TabBar(notesInFirstBar) else TabBar(emptyList()) })

    @Test
    fun `八小节切成两段四小节`() {
        val doc = TabDocument(sections = listOf(section("Main", 8)))
        val phrases = PhraseSegmenter.segment(doc)
        assertEquals(listOf(4, 4), phrases.map { it.bars.size })
        assertEquals(listOf(1, 5), phrases.map { it.barStart })
        assertEquals(listOf(1, 2), phrases.map { it.index })
        assertEquals(listOf(4, 8), phrases.map { it.barEnd })
    }

    @Test
    fun `五小节回吐成三加二`() {
        val doc = TabDocument(sections = listOf(section("Main", 5)))
        val phrases = PhraseSegmenter.segment(doc)
        assertEquals(listOf(3, 2), phrases.map { it.bars.size })
        assertEquals(listOf(1, 4), phrases.map { it.barStart })
    }

    @Test
    fun `三小节单独成句不回吐`() {
        val doc = TabDocument(sections = listOf(section("Main", 3)))
        assertEquals(listOf(3), PhraseSegmenter.segment(doc).map { it.bars.size })
    }

    @Test
    fun `单小节段落允许单小节乐句`() {
        val doc = TabDocument(sections = listOf(section("Intro", 1)))
        val phrases = PhraseSegmenter.segment(doc)
        assertEquals(listOf(1), phrases.map { it.bars.size })
        assertEquals("Intro", phrases[0].sectionName)
    }

    @Test
    fun `九小节切成四加三加二`() {
        val doc = TabDocument(sections = listOf(section("Main", 9)))
        assertEquals(listOf(4, 3, 2), PhraseSegmenter.segment(doc).map { it.bars.size })
    }

    @Test
    fun `跨段落全局小节号连续`() {
        val doc = TabDocument(sections = listOf(section("Verse", 4), section("Chorus", 2)))
        val phrases = PhraseSegmenter.segment(doc)
        assertEquals(2, phrases.size)
        assertEquals("Verse", phrases[0].sectionName)
        assertEquals(1, phrases[0].barStart)
        assertEquals("Chorus", phrases[1].sectionName)
        assertEquals(5, phrases[1].barStart)
        assertEquals(6, phrases[1].barEnd)
    }

    @Test
    fun `音符聚合带全局小节号`() {
        val notes = listOf(NoteEvent(string = 6, fret = 0, beat = 0.0), NoteEvent(string = 5, fret = 2, beat = 1.0))
        val doc = TabDocument(sections = listOf(section("Verse", 4), section("Chorus", 2, notesInFirstBar = notes)))
        val phrase2 = PhraseSegmenter.segment(doc)[1]
        val bars = phrase2.notes.map { it.first }
        assertEquals(listOf(5, 5), bars)
        assertEquals(6, phrase2.notes[0].second.string)
        assertEquals(2, phrase2.notes[1].second.fret)
    }

    @Test
    fun `空谱面返回空乐句表`() {
        assertTrue(PhraseSegmenter.segment(TabDocument()).isEmpty())
    }
}
