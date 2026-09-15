package com.guitarcoach.app.core.llm

/** 从模型回复里抠 JSON 本体：容忍 Markdown 围栏与前后闲话。 */
object JsonBlocks {

    fun extract(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw IllegalArgumentException("模型未返回 JSON，原始回复：${text.take(200)}")
        }
        return text.substring(start, end + 1)
    }
}
