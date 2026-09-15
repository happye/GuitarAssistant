package com.guitarcoach.app.core.llm

/**
 * 任务 → 模型链路由（2026-09-15 真实 key 实测后定稿，过程见 docs/模型API接入手册.md）：
 *
 *  - 视觉任务：DeepSeek 视觉实验版主选（实测识别成功）→ DeepSeek 普通版兜底（实测也接受图片）。
 *    方舟版 GLM 元数据标注仅文本输入，不进视觉链（开通后如实测支持图片再补入）。
 *  - 快速问答：DeepSeek 非思考（deepseek-chat，快且省）→ 方舟 GLM → DeepSeek 思考。
 *  - 深度任务（规划/复盘）：DeepSeek 思考（deepseek-flash）→ 方舟 GLM。
 *
 * 扩展方式：新增提供方时在 AppContainer 加一个 LlmClient 实例并插入对应链即可，
 * 编排层只面对 List<LlmClient>，不感知具体厂商。
 */
class ModelRouter(
    private val arkGlm: LlmClient,
    private val dsFast: LlmClient,
    private val dsReason: LlmClient,
    private val dsVision: LlmClient,
) {
    /** 快速问答链（速度优先，价格从低到高）。 */
    suspend fun fastText(): List<LlmClient> = listOf(dsFast, arkGlm, dsReason)

    /** 深度任务链（质量优先）。 */
    suspend fun deepText(): List<LlmClient> = listOf(dsReason, arkGlm)

    /** 视觉任务链。 */
    suspend fun vision(): List<LlmClient> = listOf(dsVision, dsFast)
}
