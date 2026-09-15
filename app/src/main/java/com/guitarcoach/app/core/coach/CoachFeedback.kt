package com.guitarcoach.app.core.coach

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 手势点评的结构化反馈（HAND_COACH 提示词约定的输出）。 */
@Serializable
data class CoachFeedback(
    val overall: String = "",
    val issues: List<Issue> = emptyList(),
    val encouragement: String = "",
) {
    @Serializable
    data class Issue(
        val part: String = "",
        val severity: String = "medium",
        val what: String = "",
        val why: String = "",
        val fix: String = "",
        val drill: String = "",
    )

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /** 从模型原始回复解析；解析失败返回 null（UI 侧降级显示原文）。 */
        fun parse(raw: String): CoachFeedback? {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return runCatching {
                json.decodeFromString<CoachFeedback>(raw.substring(start, end + 1))
            }.getOrNull()
        }
    }
}
