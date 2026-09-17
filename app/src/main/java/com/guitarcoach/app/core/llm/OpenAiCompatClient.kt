package com.guitarcoach.app.core.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容协议客户端。
 *
 * 智谱 BigModel（GLM 系列）与 DeepSeek 官方 API 都兼容 `/chat/completions` + SSE 流式，
 * 因此一个实现即可覆盖两家，差异只在 baseUrl 与 model id（均来自 ProviderConfig）。
 * 若以后接入非 OpenAI 兼容的服务（如聚合平台、实时接口），另写 LlmClient 实现即可。
 */
class OpenAiCompatClient(
    override val name: String,
    private val configProvider: suspend () -> ProviderConfig,
    // 思考型模型冷启动可达 5s+，读超时放宽到 120s；callTimeout 兜底防止无限挂起
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build(),
) : LlmClient {

    override suspend fun isConfigured(): Boolean = configProvider().isConfigured

    override fun stream(spec: ChatSpec): Flow<String> = flow {
        val cfg = configProvider()
        if (!cfg.isConfigured) {
            throw LlmException("$name 未配置 API Key，请到「首页 → 模型设置」填写")
        }
        try {
            callStream(cfg, spec, useJsonMode = spec.jsonMode).collect { emit(it) }
        } catch (e: JsonModeUnsupportedException) {
            // 服务端不认 response_format 时降级为纯提示词约束重试
            callStream(cfg, spec, useJsonMode = false).collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(spec: ChatSpec): String {
        val sb = StringBuilder()
        stream(spec).collect { sb.append(it) }
        return sb.toString()
    }

    private class JsonModeUnsupportedException(message: String) : Exception(message)

    private fun callStream(cfg: ProviderConfig, spec: ChatSpec, useJsonMode: Boolean): Flow<String> = flow {
        val payload = buildBody(cfg, spec, useJsonMode).toString()
        val request = Request.Builder()
            .url(cfg.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                if (resp.code == 400 && useJsonMode) {
                    throw JsonModeUnsupportedException(err.take(400))
                }
                throw LlmException("$name HTTP ${resp.code}: ${err.take(400)}")
            }
            val source = resp.body?.source() ?: throw LlmException("$name 返回空响应体")
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") break
                val obj = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                obj["error"]?.let {
                    throw LlmException("$name 流式返回错误: ${it.toString().take(300)}")
                }
                // OpenAI 兼容流式结构：choices[0].delta.content
                val delta = obj["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("delta")?.jsonObject
                    ?.get("content") as? JsonPrimitive ?: continue
                // 思考模型（deepseek-flash）思考阶段 content 为 JSON null——JsonNull 是 JsonPrimitive
                // 子类且 content 属性即字面 "null"。只按类型过滤：字符串比较会误杀 jsonMode 下
                // 模型合法输出的 null 值 token（如 {"issues":null}），导致 JSON 损坏（对抗审查 P1）
                if (delta is JsonNull) continue
                if (delta.content.isNotEmpty()) emit(delta.content)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildBody(cfg: ProviderConfig, spec: ChatSpec, useJsonMode: Boolean): JsonObject =
        buildJsonObject {
            put("model", cfg.modelId)
            put("stream", true)
            put("max_tokens", spec.maxTokens)
            spec.temperature?.let { put("temperature", it) }
            if (useJsonMode && spec.jsonMode) {
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
            // 注意：思考控制不靠参数靠选模型 id（deepseek-chat=关 / deepseek-flash=开，实测 L002），
            // 因此这里刻意不发送 enable_thinking / reasoning_effort，防止误用。
            put("messages", buildJsonArray {
                spec.system?.let { sys ->
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", sys)
                    })
                }
                spec.history.forEach { (role, content) ->
                    add(buildJsonObject {
                        put("role", role)
                        put("content", content)
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    if (spec.images.isEmpty()) {
                        put("content", spec.user)
                    } else {
                        // 多模态消息：文本 + 若干 base64 图片（GLM-4V 起的标准写法，DeepSeek 视觉版同构）
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", spec.user)
                            })
                            spec.images.forEach { img ->
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject {
                                        put("url", "data:${img.mime};base64,${img.base64}")
                                    })
                                })
                            }
                        })
                    }
                })
            })
        }
}
