package com.guitarcoach.app.core.tab

import com.guitarcoach.app.core.coach.CoachPrompts
import com.guitarcoach.app.core.llm.ChatSpec
import com.guitarcoach.app.core.llm.EncodedImage
import com.guitarcoach.app.core.llm.LlmFallback
import kotlinx.serialization.json.Json

/**
 * 用多模态模型（视觉链主选 DeepSeek 视觉版，见 ModelRouter）把「拍下来的六线谱图片」转成结构化 [TabDocument]。
 *
 * 关键设计：不让模型直接“讲谱”，而是先产出严格 JSON，App 做合法性校验后入库；
 * 讲解再基于结构化数据分层进行 —— 准确性可校验、谱面可回放、讲解可重试。
 */
class LlmTabExtractor(private val visionChain: suspend () -> List<com.guitarcoach.app.core.llm.LlmClient>) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun extract(imageBase64: String): ExtractResult {
        val raw = LlmFallback.completeWithFallback(visionChain()) {
            ChatSpec(
                system = CoachPrompts.TAB_TO_JSON,
                user = "请识别这张吉他六线谱图片，只输出 JSON。",
                images = listOf(EncodedImage(imageBase64)),
                maxTokens = 4096,
                temperature = 0.1,
                jsonMode = true,
            )
        }
        val doc = json.decodeFromString<TabDocument>(extractJsonBlock(raw))
        return clean(doc)
    }

    /**
     * 识谱 + CoVe 式自校验（用户反馈#4：准确性不足）：第一遍识别 → 把原图与初次 JSON
     * 再送视觉模型逐小节核对 → 用修正版。第二遍失败/不可解析时回退第一遍结果（降级不阻断）。
     */
    suspend fun extractVerified(imageBase64: String): ExtractResult {
        val first = extract(imageBase64)
        val n1 = first.document.sections.sumOf { s -> s.bars.sumOf { it.notes.size } }
        // 超长谱（>150 音符）核对输出必被 maxTokens 截断，白花一次视觉调用——跳过二次 pass
        if (n1 > 150) return first.copy(warnings = first.warnings + "谱较长（$n1 个音符），跳过自校验")
        val second = runCatching {
            val raw = LlmFallback.completeWithFallback(visionChain()) {
                ChatSpec(
                    system = CoachPrompts.TAB_VERIFY,
                    user = "原图见附件。初次识别 JSON：\n" +
                        json.encodeToString(TabDocument.serializer(), first.document) +
                        "\n请对照原图核对并输出修正后的完整 JSON。",
                    images = listOf(EncodedImage(imageBase64)),
                    // 修正版 JSON 与初次等长：按音符数估 token（每音符约 20-40），防截断
                    maxTokens = (n1 * 60).coerceAtLeast(4096).coerceAtMost(8192),
                    temperature = 0.1,
                    jsonMode = true,
                )
            }
            val doc = json.decodeFromString<TabDocument>(extractJsonBlock(raw))
            clean(doc)
        }.getOrElse { return first }
        // 修正版显著缩水（核对反而漏音）时保守回退第一遍
        val n2 = second.document.sections.sumOf { s -> s.bars.sumOf { it.notes.size } }
        return if (n1 > 0 && n2 < n1 / 2) {
            first.copy(warnings = first.warnings + "自校验结果异常（音符数 $n2 < $n1），已采用初次识别")
        } else {
            second.copy(warnings = second.warnings + "已对照原图完成逐小节自校验")
        }
    }

    /** 从模型回复中抠出 JSON（容忍代码块围栏与多余说明文字）。 */
    private fun extractJsonBlock(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw IllegalArgumentException("模型未返回 JSON，原始回复：${text.take(200)}")
        }
        return text.substring(start, end + 1)
    }

    /** 过滤非法音符并给出告警，而不是让整个解析失败。 */
    private fun clean(doc: TabDocument): ExtractResult {
        val warnings = mutableListOf<String>()
        var dropped = 0
        val cleaned = doc.copy(
            sections = doc.sections.map { section ->
                section.copy(bars = section.bars.map { bar ->
                    bar.copy(notes = bar.notes.filter { note ->
                        val ok = note.string in 1..6 && note.fret in 0..24
                        if (!ok) dropped++
                        ok
                    })
                })
            }
        )
        if (dropped > 0) warnings.add("识别结果中 $dropped 个音符超出合法范围（弦 1~6 / 品 0~24），已忽略，建议重拍更清晰的照片")
        if (cleaned.sections.all { it.bars.isEmpty() }) warnings.add("没有识别到有效音符")
        return ExtractResult(cleaned, warnings)
    }
}

@kotlinx.serialization.Serializable
data class ExtractResult(val document: TabDocument, val warnings: List<String>)
