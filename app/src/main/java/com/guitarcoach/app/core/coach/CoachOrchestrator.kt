package com.guitarcoach.app.core.coach

import com.guitarcoach.app.core.llm.ChatSpec
import com.guitarcoach.app.core.llm.EncodedImage
import com.guitarcoach.app.core.llm.LlmClient
import com.guitarcoach.app.core.llm.LlmException
import com.guitarcoach.app.core.llm.ModelRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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

    /** 拍谱讲解：流式输出，边生成边显示（视觉链）。 */
    fun explainTabImage(imageBase64: String, question: String = ""): Flow<String> =
        streamWithFallback(chain = { router.vision() }) {
            ChatSpec(
                system = CoachPrompts.TAB_EXPLAIN,
                user = question.ifBlank { "这张谱怎么弹？请讲解并给我练习建议。" },
                images = listOf(EncodedImage(imageBase64)),
                maxTokens = 1500,
                temperature = 0.5,
            )
        }

    /** 乐理问答：流式输出，支持多轮历史（快答链）。 */
    fun askTheory(question: String, history: List<Pair<String, String>> = emptyList()): Flow<String> =
        streamWithFallback(chain = { router.fastText() }) {
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
        val raw = completeWithFallback(router.vision()) {
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

    // ---------- 内部：模型链 + 备份降级 ----------

    /** 流式 + 逐级备份。chain 为 suspend 提供者（路由读配置需要挂起）。
     *  降级契约：仅在「尚未向下游发射任何内容」时才换链重试；已经吐过内容再失败则直接抛错给 UI，
     *  否则备份链从头重生成会与已显示的半截话拼接成乱文（UI 是追加渲染）。 */
    private fun streamWithFallback(chain: suspend () -> List<LlmClient>, specFactory: () -> ChatSpec): Flow<String> = flow {
        val clients = chain()
        var emitted = false
        var lastError: Exception? = null
        for (client in clients) {
            try {
                client.stream(specFactory()).collect { delta ->
                    emitted = true
                    emit(delta)
                }
                return@flow
            } catch (e: Exception) {
                lastError = e
                if (emitted) throw e
                // 一个字都没吐：静默换链上下一家
            }
        }
        throw lastError ?: LlmException("没有可用的模型（请到首页检查 API Key 配置）")
    }

    /** 非流式 + 逐级备份。 */
    private suspend fun completeWithFallback(clients: List<LlmClient>, specFactory: () -> ChatSpec): String {
        var lastError: Exception? = null
        for (client in clients) {
            try {
                return client.complete(specFactory())
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: LlmException("没有可用的模型（请到首页检查 API Key 配置）")
    }
}
