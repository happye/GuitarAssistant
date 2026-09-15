package com.guitarcoach.app.core.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 模型链降级原语（ModelRouter 的链在此消费）。
 * 契约（L010）：流式仅在「未发射任何内容」前才允许换链重试；已发射后失败直接抛错，
 * 由 UI 保留半截内容。取消必须穿透（L011）。
 */
object LlmFallback {

    /** 流式 + 逐级备份。chain 为 suspend 提供者（路由读配置需要挂起）。 */
    fun streamWithFallback(
        chain: suspend () -> List<LlmClient>,
        specFactory: () -> ChatSpec,
    ): Flow<String> = flow {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (emitted) throw e
                // 一个字都没吐：静默换链上下一家
            }
        }
        throw lastError ?: LlmException("没有可用的模型（请到首页检查 API Key 配置）")
    }

    /** 非流式 + 逐级备份。 */
    suspend fun completeWithFallback(
        clients: List<LlmClient>,
        specFactory: () -> ChatSpec,
    ): String {
        var lastError: Exception? = null
        for (client in clients) {
            try {
                return client.complete(specFactory())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: LlmException("没有可用的模型（请到首页检查 API Key 配置）")
    }
}
