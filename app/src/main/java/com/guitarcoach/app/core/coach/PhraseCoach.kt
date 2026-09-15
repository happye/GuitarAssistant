package com.guitarcoach.app.core.coach

import com.guitarcoach.app.core.llm.ChatSpec
import com.guitarcoach.app.core.llm.JsonBlocks
import com.guitarcoach.app.core.llm.LlmClient
import com.guitarcoach.app.core.llm.LlmFallback
import com.guitarcoach.app.core.tab.CoverageAuditor
import com.guitarcoach.app.core.tab.FingeringSolver
import com.guitarcoach.app.core.tab.NoteRef
import com.guitarcoach.app.core.tab.Phrase
import com.guitarcoach.app.core.tab.PhraseSegmenter
import com.guitarcoach.app.core.tab.TabDocument
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ---------- 管线 ----------

/**
 * F207 逐句大白话讲解管线（路线 B：结构化优先，讲解只基于 TabDocument，不看图）。
 *
 * 流程：确定性乐句分段（PhraseSegmenter）→ 逐句生成结构化讲解（JSON，含 finger/pick）
 * → 程序化覆盖审计（CoverageAuditor 多重集比对：0 漏音 0 重复）→ 未过则带具体漏音反馈重写
 * （最多 3 轮）→ CoVe 交叉核对：剥离讲解、仅凭谱面让模型重列音符清单与谱面比对（报告性质）。
 *
 * 链路错误（网络/模型不可用）直接抛出，不烧重试轮；解析/校验失败按"该句未过审计"处理进重写。
 */
class PhraseCoach(private val textChain: suspend () -> List<LlmClient>) {

    @Serializable
    internal data class CoveOut(val bars: List<CoveBar> = emptyList())

    @Serializable
    internal data class CoveBar(val bar: Int, val notes: List<List<Int>> = emptyList())

    @Serializable
    data class AuditIssue(
        val phraseIndex: Int,
        val missing: List<NoteRef>,
        val extra: List<NoteRef>,
        val badFinger: Boolean,
        val ruleViolations: List<String> = emptyList(),
    )

    @Serializable
    data class Report(
        val phrases: List<PhraseExplain>,
        val issues: List<AuditIssue>,
        val coveNotes: List<String>,
        val rounds: Int,
    ) {
        val auditClean: Boolean get() = issues.isEmpty()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /**
     * 全曲逐句讲解。onPhrase(刚完成的乐句, 已完成数, 总数) 用于进度展示；
     * 取消透传（L011）：调用方协程取消时中断并抛 CancellationException。
     */
    suspend fun explain(
        doc: TabDocument,
        onPhrase: suspend (PhraseExplain, Int, Int) -> Unit = { _, _, _ -> },
    ): Report {
        val phrases = PhraseSegmenter.segment(doc)
        if (phrases.isEmpty()) throw IllegalArgumentException("谱面没有可讲解的小节")

        val current = linkedMapOf<Int, PhraseExplain>()
        val feedback = mutableMapOf<Int, String>()
        var lastIssues: List<AuditIssue> = emptyList()
        var rounds = 0
        for (round in 1..MAX_ROUNDS) {
            rounds = round
            val failed = lastIssues.map { it.phraseIndex }.toSet()
            val todo = if (round == 1) phrases else phrases.filter { it.index in failed }
            for (phrase in todo) {
                val explain = generate(phrase, feedback[phrase.index])
                current[phrase.index] = explain
                onPhrase(explain, current.size, phrases.size)
            }
            lastIssues = phrases.mapNotNull { audit(it, current[it.index]) }
            if (lastIssues.isEmpty()) break
            lastIssues.forEach { feedback[it.phraseIndex] = feedbackFor(it) }
        }

        val report = Report(
            phrases = current.values.sortedBy { it.index },
            issues = lastIssues,
            coveNotes = coveCheck(doc),
            rounds = rounds,
        )
        return report
    }

    private suspend fun generate(phrase: Phrase, feedback: String?): PhraseExplain {
        val user = buildString {
            append("乐句谱面数据：").append('\n').append(phraseJson(phrase))
            // F209：DP 算出的参考指法注入提示词（计算失败则静默跳过，不阻塞讲解）
            dpReference(phrase)?.let { append("\n\n参考指法（动态规划计算，供参考）：").append(it) }
            if (!feedback.isNullOrBlank()) {
                append("\n\n上一版讲解未通过覆盖审计：").append(feedback)
                append("\n请重写：逐个核对 bar/string/fret，与谱面一一对应，漏的补上、多讲的删掉。")
            }
        }
        val raw = LlmFallback.completeWithFallback(textChain()) {
            ChatSpec(
                system = CoachPrompts.PHRASE_EXPLAIN,
                user = user,
                maxTokens = 2000,
                temperature = 0.3,
                jsonMode = true,
            )
        }
        return try {
            PhraseExplainCodec.parse(raw, phrase)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 解析/校验失败 → 空壳交给审计拦截，下一轮带反馈重写；不在这里烧死循环
            PhraseExplain(index = phrase.index, summary = "（生成失败：${e.message?.take(80) ?: "未知错误"}）")
        }
    }

    private fun audit(phrase: Phrase, explain: PhraseExplain?): AuditIssue? {
        val expected = phrase.notes.map { (bar, note) -> NoteRef(bar, note.string, note.fret) }
        if (explain == null) return AuditIssue(phrase.index, expected, emptyList(), false)
        val issue = CoverageAuditor.check(expected, explain.steps.map { NoteRef(it.bar, it.string, it.fret) })
        val badFinger = explain.steps.any {
            it.finger !in 0..4 || it.string !in 1..6 || it.fret !in 0..24 || it.bar !in phrase.barStart..phrase.barEnd
        }
        // F209：LLM 指法必须过物理规则校验（同拍同指跨品、横按压住低品等）
        val ruleViolations = ruleViolations(explain)
        return if (issue.clean && !badFinger && ruleViolations.isEmpty()) {
            null
        } else {
            AuditIssue(phrase.index, issue.missing, issue.extra, badFinger, ruleViolations)
        }
    }

    private fun ruleViolations(explain: PhraseExplain): List<String> =
        explain.steps
            .groupBy { it.bar to Math.round(it.beat * 1000.0) }
            .map { (key, steps) ->
                FingeringSolver.Assignment(
                    bar = key.first,
                    beat = key.second / 1000.0,
                    items = steps.map { FingeringSolver.Item(it.string, it.fret, it.finger) },
                )
            }
            .flatMap { FingeringSolver.validate(listOf(it)) }

    private fun feedbackFor(issue: AuditIssue): String = buildString {
        if (issue.missing.isNotEmpty()) {
            append("漏讲：").append(issue.missing.take(8).joinToString("、") { it.label() })
            if (issue.missing.size > 8) append(" 等 8+ 处")
        }
        if (issue.extra.isNotEmpty()) {
            if (isNotEmpty()) append("；")
            append("多讲/重复：").append(issue.extra.take(8).joinToString("、") { it.label() })
        }
        if (issue.badFinger) {
            if (isNotEmpty()) append("；")
            append("部分 step 的 finger 缺失（须 0~4）或弦/品/小节号非法")
        }
        issue.ruleViolations.take(2).forEach {
            if (isNotEmpty()) append("；")
            append("指法违规：").append(it)
        }
    }

    /** CoVe：仅凭谱面数据重列清单与谱面比对（报告性质；程序审计才是重写驱动）。 */
    private suspend fun coveCheck(doc: TabDocument): List<String> {
        val expected = expectedRefs(doc)
        return try {
            val raw = LlmFallback.completeWithFallback(textChain()) {
                ChatSpec(
                    system = CoachPrompts.COVE_LIST,
                    user = "谱面数据：\n${docJson(doc)}",
                    maxTokens = 2000,
                    temperature = 0.0,
                    jsonMode = true,
                )
            }
            val out = json.decodeFromString<CoveOut>(JsonBlocks.extract(raw))
            val relisted = out.bars.flatMap { bar ->
                bar.notes.mapNotNull { pair ->
                    val s = pair.getOrNull(0)
                    val f = pair.getOrNull(1)
                    if (s == null || f == null) null else NoteRef(bar.bar, s, f)
                }
            }
            val diff = CoverageAuditor.check(expected, relisted)
            buildList {
                if (diff.missing.isNotEmpty()) add("CoVe 重列比谱面少 ${diff.missing.size} 处（${diff.missing.take(3).joinToString { it.label() }}）——读谱可能不完整，建议重试")
                if (diff.extra.isNotEmpty()) add("CoVe 重列比谱面多 ${diff.extra.size} 处（${diff.extra.take(3).joinToString { it.label() }}）")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            listOf("CoVe 审计未完成：${e.message?.take(80) ?: "未知错误"}")
        }
    }

    // ---------- 紧凑谱面 JSON（控制 token；section 名做引号兜底转义） ----------

    /** 乐句内的同拍分组（全局小节号口径，与讲解步骤一致）。 */
    private fun phraseSlots(p: Phrase): List<FingeringSolver.Slot> =
        p.bars.flatMapIndexed { bi, bar ->
            bar.notes
                .groupBy { Math.round(it.beat * 1000.0) }
                .toSortedMap()
                .map { (_, notes) ->
                    FingeringSolver.Slot(p.barStart + bi, notes.minOf { it.beat }, notes.sortedBy { it.string })
                }
        }

    /** F209：DP 参考指法文本；无解或越界时返回 null（讲解不强依赖）。 */
    private fun dpReference(p: Phrase): String? = runCatching {
        FingeringSolver.solve(phraseSlots(p))
            .joinToString("；") { a ->
                "第${a.bar}小节:" + a.items.joinToString(" ") { "${it.string}弦${it.fret}品=指${it.finger}" }
            }
            .take(400)
    }.getOrNull()

    private fun phraseJson(p: Phrase): String = buildString {
        append("""{"section":"${p.sectionName.replace("\"", "'")}","barStart":${p.barStart},"barEnd":${p.barEnd},"bars":[""")
        p.bars.forEachIndexed { bi, bar ->
            if (bi > 0) append(",")
            append("""{"bar":${p.barStart + bi},"notes":[""")
            bar.notes.forEachIndexed { ni, note ->
                if (ni > 0) append(",")
                append("[${note.string},${note.fret},${note.beat}]")
            }
            append("]}")
        }
        append("]}")
    }

    private fun docJson(doc: TabDocument): String {
        val bars = StringBuilder()
        var globalBar = 1
        doc.sections.forEach { section ->
            section.bars.forEach { bar ->
                if (bars.isNotEmpty()) bars.append(",")
                bars.append("""{"bar":${globalBar},"notes":[""")
                bar.notes.forEachIndexed { ni, note ->
                    if (ni > 0) bars.append(",")
                    bars.append("[${note.string},${note.fret}]")
                }
                bars.append("]}")
                globalBar++
            }
        }
        return """{"bars":[$bars]}"""
    }

    private fun expectedRefs(doc: TabDocument): List<NoteRef> {
        val refs = mutableListOf<NoteRef>()
        var globalBar = 1
        doc.sections.forEach { section ->
            section.bars.forEach { bar ->
                bar.notes.forEach { refs += NoteRef(globalBar, it.string, it.fret) }
                globalBar++
            }
        }
        return refs
    }

    private companion object {
        const val MAX_ROUNDS = 3
    }
}

private fun NoteRef.label(): String = "第${bar}小节${string}弦${fret}品"
