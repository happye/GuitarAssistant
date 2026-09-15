package com.guitarcoach.app.core.llm

/** 一张已编码好的图片（纯 base64，不带 data: 前缀）。 */
data class EncodedImage(val base64: String, val mime: String = "image/jpeg")

/**
 * 一次对话请求的统一描述。
 * 各厂商 OpenAI 兼容接口的差异（鉴权、图片字段、流式格式）全部在
 * [OpenAiCompatClient] 内部抹平，上层只面对这个 spec。
 */
data class ChatSpec(
    val system: String? = null,
    val user: String,
    val images: List<EncodedImage> = emptyList(),
    /** 简单多轮历史：(role, content)，role 取 "user"/"assistant"。 */
    val history: List<Pair<String, String>> = emptyList(),
    val maxTokens: Int = 2048,
    val temperature: Double? = null,
    /** 要求模型输出 JSON（服务端支持则用 response_format，不支持自动降级为纯提示词约束）。 */
    val jsonMode: Boolean = false,
)

/** 提供方连接配置（全部可在 App 设置页修改，改完即生效）。 */
data class ProviderConfig(
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val apiKey: String,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}
