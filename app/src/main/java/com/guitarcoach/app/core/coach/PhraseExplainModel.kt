package com.guitarcoach.app.core.coach

import com.guitarcoach.app.core.llm.ChatSpec
import com.guitarcoach.app.core.llm.JsonBlocks
import com.guitarcoach.app.core.llm.LlmClient
import com.guitarcoach.app.core.llm.LlmFallback
import com.guitarcoach.app.core.tab.CoverageAuditor
import com.guitarcoach.app.core.tab.NoteRef
import com.guitarcoach.app.core.tab.Phrase
import com.guitarcoach.app.core.tab.PhraseSegmenter
import com.guitarcoach.app.core.tab.TabDocument
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ---------- 讲解数据模型（UI 渲染与本地缓存共用） ----------

@Serializable
data class PhraseExplain(
    val index: Int = 0,               // 乐句序号（1 起），解析后以分段器为准覆写
    val summary: String = "",
    val steps: List<Step> = emptyList(),
    val terms: List<Term> = emptyList(),
    val difficulty: String = "",
    val practice: String = "",
) {
    /** finger：0=空弦不按，1~4=食指~小指；-1 表示模型未提供（校验不通过）。 */
    @Serializable
    data class Step(
        val bar: Int,                  // 全曲全局小节号（1 起）
        val beat: Double = 0.0,
        val string: Int,
        val fret: Int,
        val finger: Int = -1,
        val pick: String = "",
        val text: String = "",
    )

    @Serializable
    data class Term(val term: String, val plain: String = "")
}

/** 讲解 JSON 的解析与字段校验（纯函数，可 JVM 单测）。 */
object PhraseExplainCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** 解析模型回复（容忍围栏/闲话）；字段不合法抛 IllegalArgumentException。 */
    fun parse(raw: String, phrase: Phrase): PhraseExplain {
        val explain = json.decodeFromString<PhraseExplain>(JsonBlocks.extract(raw)).copy(index = phrase.index)
        validate(explain, phrase)?.let { throw IllegalArgumentException(it) }
        return explain
    }

    fun validate(explain: PhraseExplain, phrase: Phrase): String? {
        if (explain.summary.isBlank()) return "缺总述 summary"
        if (phrase.notes.isNotEmpty() && explain.steps.isEmpty()) return "steps 为空，没有逐音讲解"
        explain.steps.forEachIndexed { i, s ->
            if (s.finger !in 0..4) return "第${i + 1}个 step 的 finger 缺失或不在 0~4"
            if (s.string !in 1..6) return "第${i + 1}个 step 的弦号非法（1~6）"
            if (s.fret !in 0..24) return "第${i + 1}个 step 的品号非法（0~24）"
            if (s.bar !in phrase.barStart..phrase.barEnd) {
                return "第${i + 1}个 step 的小节号 ${s.bar} 不在本乐句（${phrase.barStart}~${phrase.barEnd}）"
            }
        }
        explain.terms.forEach { t ->
            if (t.term.isBlank() || t.plain.isBlank()) return "术语「${t.term}」缺大白话解释"
        }
        return null
    }
}

/** 乐句 → 可朗读文本（F208 朗读本句）：总述 + 逐音说明 + 难点 + 练习，无 JSON 痕迹。 */
fun phraseSpeechText(p: PhraseExplain): String = buildString {
    append("乐句${p.index}。").append(p.summary).append("。")
    p.steps.forEach { s ->
        append("第${s.bar}小节，${s.string}弦${s.fret}品。")
        if (s.text.isNotBlank()) append(s.text).append("。")
    }
    p.terms.forEach { t -> append("${t.term}，就是${t.plain}。") }
    if (p.difficulty.isNotBlank()) append("注意，${p.difficulty}。")
    if (p.practice.isNotBlank()) append("练习，${p.practice}。")
}

/** 整份报告 → 可朗读文本（F208 整段朗读）。 */
fun reportSpeechText(report: PhraseCoach.Report): String =
    report.phrases.joinToString("。") { phraseSpeechText(it) }
