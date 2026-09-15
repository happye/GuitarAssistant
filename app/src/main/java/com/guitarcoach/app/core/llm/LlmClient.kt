package com.guitarcoach.app.core.llm

import kotlinx.coroutines.flow.Flow

/**
 * 统一的模型客户端接口。
 * 当前实现是 OpenAI 兼容 HTTP（覆盖智谱 GLM 与 DeepSeek）；
 * 将来可新增实现：本地小模型、实时语音接口（GLM-Realtime 类）等，上层代码不变。
 */
interface LlmClient {
    val name: String

    /** 该提供方是否已配置好 API Key。 */
    suspend fun isConfigured(): Boolean

    /** 流式对话，逐段产出文本增量。 */
    fun stream(spec: ChatSpec): Flow<String>

    /** 非流式，一次性取全量回复。 */
    suspend fun complete(spec: ChatSpec): String
}

class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)
