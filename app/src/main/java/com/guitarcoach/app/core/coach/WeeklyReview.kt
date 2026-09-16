package com.guitarcoach.app.core.coach

import kotlin.math.roundToInt

/**
 * F501 周复盘统计（纯函数）：练习记录 → 给模型的统计文本。
 * 只引用真实数据（days/分钟/内容），不编造；7 天窗口按自然日（本地时区）计。
 */
object WeeklyReview {

    data class Record(val startedAt: Long, val durationSeconds: Int, val content: String)

    data class Stats(
        val daysPracticed: Int,
        val sessions: Int,
        val totalMinutes: Int,
        val contentLines: List<String>,
    ) {
        fun toPromptText(): String = buildString {
            append("本周统计：练了 $daysPracticed 天，共 $sessions 次，累计 $totalMinutes 分钟。")
            if (contentLines.isEmpty()) {
                append("没有任何练习记录。")
            } else {
                append("练的内容：")
                contentLines.forEach { append("\n- ").append(it) }
            }
        }
    }

    fun summarize(records: List<Record>, nowMs: Long, daysBack: Int = 7): Stats {
        val dayMs = 24L * 60 * 60 * 1000
        val windowStart = nowMs - daysBack * dayMs
        val inWindow = records.filter { it.startedAt >= windowStart && it.startedAt <= nowMs }
        // 自然日按本地时区（KDoc 口径），不用 UTC epoch-day
        val days = inWindow
            .map { java.time.Instant.ofEpochMilli(it.startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
            .toSet().size
        val totalSeconds = inWindow.sumOf { it.durationSeconds }
        val content = inWindow
            .groupBy { it.content }
            .entries
            .sortedByDescending { e -> e.value.sumOf { it.durationSeconds } }
            .map { (name, sessions) ->
                val minutes = (sessions.sumOf { it.durationSeconds } / 60.0).roundToInt()
                "$name（${sessions.size} 次，约 $minutes 分钟）"
            }
        return Stats(
            daysPracticed = days,
            sessions = inWindow.size,
            totalMinutes = totalSeconds / 60,
            contentLines = content,
        )
    }
}
