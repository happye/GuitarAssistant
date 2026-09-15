package com.guitarcoach.app.data

import com.guitarcoach.app.core.coach.PhraseCoach
import com.guitarcoach.app.core.tab.TabCompact
import com.guitarcoach.app.core.tab.TabDocument
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * 逐句讲解本地缓存（F208）：按谱面内容哈希落盘 JSON，离线可回看；谱面被编辑后哈希变化自动失效。
 * 纯文件实现（构造参数传目录，可 JVM 单测），不引新表。
 */
class PhraseCache(private val baseDir: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val dir: File get() = File(baseDir, "phrase_cache").apply { mkdirs() }

    /** 缓存键 = 谱面紧凑 JSON 的 SHA-256（内容寻址：编辑过即新键）。 */
    fun key(doc: TabDocument): String =
        MessageDigest.getInstance("SHA-256")
            .digest(TabCompact.doc(doc).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun load(doc: TabDocument): PhraseCoach.Report? = runCatching {
        val file = File(dir, "${key(doc)}.json")
        if (file.exists()) json.decodeFromString<PhraseCoach.Report>(file.readText(Charsets.UTF_8)) else null
    }.getOrNull()

    fun save(doc: TabDocument, report: PhraseCoach.Report) {
        runCatching {
            File(dir, "${key(doc)}.json").writeText(json.encodeToString(PhraseCoach.Report.serializer(), report))
        }
    }

    fun clear() {
        runCatching { dir.deleteRecursively() }
    }
}
