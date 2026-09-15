package com.guitarcoach.app.core.coach

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseSpeechTest {

    private val phrase = PhraseExplain(
        index = 2,
        summary = "低音下行，像走路。",
        steps = listOf(PhraseExplain.Step(bar = 1, string = 6, fret = 0, finger = 0, pick = "下拨", text = "空弦拨一下")),
        terms = listOf(PhraseExplain.Term("空弦", "左手不按只拨弦")),
        difficulty = "节奏均匀",
        practice = "60 BPM 慢练",
    )

    @Test
    fun `朗读文本包含关键内容且无JSON痕迹`() {
        val text = phraseSpeechText(phrase)
        assertTrue(text.contains("低音下行"))
        assertTrue(text.contains("6弦0品"))
        assertTrue(text.contains("空弦，就是左手不按只拨弦"))
        assertTrue(text.contains("60 BPM 慢练"))
        assertFalse(text.contains("{"))
        assertFalse(text.contains("\""))
    }

    @Test
    fun `整段朗读按乐句拼接`() {
        val report = PhraseCoach.Report(
            phrases = listOf(phrase, phrase.copy(index = 3, summary = "回到主音")),
            issues = emptyList(),
            coveNotes = emptyList(),
            rounds = 1,
        )
        val text = reportSpeechText(report)
        assertTrue(text.contains("乐句2"))
        assertTrue(text.contains("乐句3"))
        assertTrue(text.contains("回到主音"))
    }
}
