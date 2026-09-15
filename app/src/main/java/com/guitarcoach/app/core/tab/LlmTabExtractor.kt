package com.guitarcoach.app.core.tab

import com.guitarcoach.app.core.coach.CoachPrompts
import com.guitarcoach.app.core.llm.ChatSpec
import com.guitarcoach.app.core.llm.EncodedImage
import com.guitarcoach.app.core.llm.LlmClient
import kotlinx.serialization.json.Json

/**
 * 用多模态模型（GLM-5.3-Flash）把「拍下来的六线谱图片」转成结构化 [TabDocument]。
 *
 * 关键设计：不让模型直接“讲谱”，而是先产出严格 JSON，App 做合法性校验后入库；
 * 讲解再基于结构化数据分层进行 —— 准确性可校验、谱面可回放、讲解可重试。
 */
class LlmTabExtractor(private val visionClient: suspend () -> LlmClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun extract(imageBase64: String): ExtractResult {
        val client = visionClient()
        val raw = client.complete(
            ChatSpec(
                system = CoachPrompts.TAB_TO_JSON,
                user = "请识别这张吉他六线谱图片，只输出 JSON。",
                images = listOf(EncodedImage(imageBase64)),
                maxTokens = 4096,
                temperature = 0.1,
                jsonMode = true,
            )
        )
        val doc = json.decodeFromString<TabDocument>(extractJsonBlock(raw))
        return clean(doc)
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

data class ExtractResult(val document: TabDocument, val warnings: List<String>)
