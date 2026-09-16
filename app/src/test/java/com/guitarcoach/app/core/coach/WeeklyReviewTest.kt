package com.guitarcoach.app.core.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReviewTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    private fun rec(daysAgo: Long, seconds: Int, content: String) =
        WeeklyReview.Record(now - daysAgo * day, seconds, content)

    @Test
    fun `七天窗口内统计天数次数与时长`() {
        val stats = WeeklyReview.summarize(
            records = listOf(
                rec(0, 600, "爬格子"),
                rec(0, 300, "音阶"),
                rec(2, 900, "爬格子"),
                rec(9, 3600, "窗口外的旧记录"),
            ),
            nowMs = now,
        )
        assertEquals(2, stats.daysPracticed)
        assertEquals(3, stats.sessions)
        assertEquals(30, stats.totalMinutes)
        // 内容按累计时长排序，窗口外的不计入
        assertEquals("爬格子（2 次，约 25 分钟）", stats.contentLines.first())
    }

    @Test
    fun `空记录给模型明确信号防编造`() {
        val stats = WeeklyReview.summarize(emptyList(), nowMs = now)
        assertTrue(stats.toPromptText().contains("没有任何练习记录"))
        assertEquals(0, stats.daysPracticed)
    }

    @Test
    fun `提示文本引用真实数字`() {
        val text = WeeklyReview.summarize(listOf(rec(1, 1200, "曲库里的歌")), nowMs = now).toPromptText()
        assertTrue(text.contains("练了 1 天"))
        assertTrue(text.contains("累计 20 分钟"))
        assertTrue(text.contains("曲库里的歌"))
    }
}
