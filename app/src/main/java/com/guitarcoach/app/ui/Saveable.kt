package com.guitarcoach.app.ui

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * rememberSaveable 的 JSON 存取器（Bug 反馈#1：切 tab 丢状态）。
 * 复杂对象（TabDocument 等）以 JSON 字符串进 Bundle；恢复失败（版本演化脏数据）静默回退 null/空。
 */
private val saveableJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

fun <T> jsonSaver(serializer: KSerializer<T>): Saver<T, String> = Saver(
    // save/restore 对称防御：类型图将来出现不可序列化字段时，状态保存阶段不能崩（监督员 P2）
    save = { runCatching { saveableJson.encodeToString(serializer, it) }.getOrDefault("") },
    restore = { runCatching { saveableJson.decodeFromString(serializer, it) }.getOrNull() },
)

fun <T> nullableJsonSaver(serializer: KSerializer<T>): Saver<T?, String> = Saver(
    save = { it?.let { v -> runCatching { saveableJson.encodeToString(serializer, v) }.getOrDefault("") } ?: "" },
    restore = { if (it.isBlank()) null else runCatching { saveableJson.decodeFromString(serializer, it) }.getOrNull() },
)

fun <T> jsonListSaver(serializer: KSerializer<T>): Saver<List<T>, String> = Saver(
    save = { runCatching { saveableJson.encodeToString(ListSerializer(serializer), it) }.getOrDefault("") },
    restore = { runCatching { saveableJson.decodeFromString(ListSerializer(serializer), it) }.getOrNull() ?: emptyList() },
)
