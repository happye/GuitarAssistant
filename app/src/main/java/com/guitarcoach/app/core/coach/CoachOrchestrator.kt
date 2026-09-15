package com.guitarcoach.app.core.coach

import com.guitarcoach.app.core.llm.ChatSpec
import com.guitarcoach.app.core.llm.EncodedImage
import com.guitarcoach.app.core.llm.LlmFallback
import com.guitarcoach.app.core.llm.ModelRouter
import com.guitarcoach.app.core.tab.TabCompact
import com.guitarcoach.app.core.tab.TabDocument
import kotlinx.coroutines.flow.Flow

/**
 * 教练编排器：把「感知（拍谱 / 关键帧 / 提问）→ 模型 → 输出（讲解 / JSON 点评）」串起来。
 * UI 只跟它对话，不直接碰模型客户端 —— 将来换模型、加缓存、加语音播报都只改这一层。
 *
 * 每条链路都带备份降级：主模型失败（限流/网络/未配置）自动换链上下一家重试，
 * 链序由 ModelRouter 决定（文本 DeepSeek 优先因为便宜，视觉 DeepSeek 视觉版主选）。
 *
 * 实测纪律（2026-09-15）：DeepSeek 的思考开关靠选 id（deepseek-chat=关 / deepseek-flash=开），
 * enable_thinking 参数静默无效、reasoning_effort 会给 deepseek-chat 强制开思考——
 * 所以本层不传任何思考控制参数，需要不同形态时换链。
 */
class CoachOrchestrator(private val router: ModelRouter) {

    /** 拍谱讲解（F204）：基于已识别的结构化谱面数据（不重新看图），同一份谱两次讲解口径一致。 */
    fun explainTabDocument(doc: TabDocument, question: String = ""): Flow<String> =
        LlmFallback.streamWithFallback(chain = { router.fastText() }) {
            ChatSpec(
                system = CoachPrompts.TAB_EXPLAIN_STRUCTURED,
                user = buildString {
                    append("谱面结构化数据：\n").append(TabCompact.doc(doc))
                    append("\n\n学生问题：").append(question.ifBlank { "这张谱怎么弹？请讲解并给我练习建议。" })
                },
                maxTokens = 1500,
                temperature = 0.3,
            )
        }

    /** 乐理问答：流式输出，支持多轮历史（快答链）。 */
    fun askTheory(question: String, history: List<Pair<String, String>> = emptyList()): Flow<String> =
        LlmFallback.streamWithFallback(chain = { router.fastText() }) {
            ChatSpec(
                system = CoachPrompts.THEORY_QA,
                user = question,
                history = history,
                maxTokens = 1024,
                temperature = 0.7,
            )
        }

    /** 手势点评：关键帧批量送审，返回结构化反馈（视觉链）。 */
    suspend fun reviewHandFrames(framesBase64: List<String>, note: String = ""): CoachFeedback? {
        require(framesBase64.isNotEmpty()) { "至少需要一帧关键帧" }
        val raw = LlmFallback.completeWithFallback(router.vision()) {
            ChatSpec(
                system = CoachPrompts.HAND_COACH,
                user = if (note.isBlank()) "请点评我的手型。"
                else "背景说明：$note\n请点评我的手型。",
                images = framesBase64.map { EncodedImage(it) },
                maxTokens = 1024,
                temperature = 0.3,
                jsonMode = true,
            )
        }
        return CoachFeedback.parse(raw)
    }

    // ---------- 内部：模型链 + 备份降级（原语在 core/llm/LlmFallback，供识谱等管线共用） ----------
}
