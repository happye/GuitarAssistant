@file:OptIn(kotlin.contracts.ExperimentalContracts::class)

package com.guitarcoach.app.core.tab

import alphaTab.Settings
import alphaTab.importer.ScoreLoader

/**
 * A1（重构方案 §1.2）：TabDocument → alphaTex → alphaTab Score 的一次性渲染投影。
 * 红线可守：Score 只投影不持久化不回写；文档变更即整体重建。
 *
 * alphaTex 路线复用 F205 已验证的解析路径（ScoreLoader.loadAlphaTex，GpImporterTest 有先例），
 * 比手搓 model 对象树稳且可单测（round-trip 断言）。
 *
 * 口径：alphaTex 的 `品.弦` 弦号从低音 E 起数（.1=低E=TabDocument 6 弦）——生成时做 7-string 反演。
 * 技巧记号 v1 只投影 x（闷音）/{pm}/{v}；h/p/s/b 需音对语义，TabDocument 未存对端，不投影（自绘谱面仍显示）。
 */
object TabScoreProjector {

    /** 时值（拍）→ alphaTex 时值 token。1.8.4 解析器不支持点分 token（:8. → AT202 崩，真机实测），只用普通时值。 */
    private val DURATION_TOKENS: List<Pair<Double, String>> = listOf(
        4.0 to ":1", 2.0 to ":2", 1.0 to ":4", 0.5 to ":8", 0.25 to ":16", 0.125 to ":32",
    )

    fun durationToken(beats: Double): String {
        require(beats > 0) { "时值必须 > 0" }
        var best = DURATION_TOKENS.first()
        var bestDiff = Double.MAX_VALUE
        for ((b, t) in DURATION_TOKENS) {
            val diff = kotlin.math.abs(beats - b)
            if (diff < bestDiff - 1e-9) { // 严格更近才替换：平手取普通时值
                bestDiff = diff
                best = b to t
            }
        }
        return best.second
    }

    private fun tokenBeats(token: String): Double = when (token) {
        ":1" -> 4.0; ":2" -> 2.0; ":4" -> 1.0; ":8" -> 0.5; ":16" -> 0.25; ":32" -> 0.125
        else -> 1.0
    }

    /** TabDocument → alphaTex 文本。 */
    fun toAlphaTex(doc: TabDocument): String {
        val sb = StringBuilder()
        sb.append("\\title ").append(quote(doc.title ?: "未命名")).append('\n')
        sb.append("\\tempo ").append(doc.tempo.coerceIn(1, 300)).append('\n')
        val beatsPerBar = parseTimeSignature(doc.timeSignature)?.first ?: 4
        parseTimeSignature(doc.timeSignature)?.let { (n, d) -> sb.append("\\ts ").append(n).append(' ').append(d).append('\n') }
        // 首段名放头部（放 "." 之后会产生空小节，实测）；后续段名随小节分隔符内联
        doc.sections.firstOrNull()?.let { sb.append("\\section ").append(quote(it.name.ifBlank { "Main" })).append('\n') }
        sb.append(".\n")
        doc.sections.forEachIndexed { si, section ->
            section.bars.forEachIndexed { bi, bar ->
                val firstOfDoc = si == 0 && bi == 0
                if (!firstOfDoc) sb.append("| ")
                if (bi == 0 && si > 0) sb.append("\\section ").append(quote(section.name.ifBlank { "Main" })).append(' ')
                emitBar(sb, bar, beatsPerBar)
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    /** 单小节 → alphaTex 音符序列（时值前缀只在切换时写；空洞与小节尾用休止补齐保持拍网格）。 */
    private fun emitBar(sb: StringBuilder, bar: TabBar, beatsPerBar: Int) {
        val notes = bar.notes.sortedBy { it.beat }
        var cursor = 0.0
        var lastToken: String? = null
        fun use(token: String) {
            if (token != lastToken) { sb.append(token).append(' '); lastToken = token }
        }
        fun emitGap(gap: Double) {
            var covered = 0.0
            while (gap - covered > 0.124) {
                val t = durationToken(gap - covered)
                use(t)
                sb.append("r ")
                covered += tokenBeats(t)
            }
        }
        notes.forEachIndexed { ni, note ->
            val beat = note.beat.coerceIn(0.0, beatsPerBar.toDouble())
            if (beat > cursor + 0.124) emitGap(beat - cursor)
            var dur = note.duration.coerceAtMost(beatsPerBar - beat).coerceAtLeast(0.125)
            val next = notes.getOrNull(ni + 1)?.beat
            if (next != null) dur = dur.coerceAtMost(next - beat).coerceAtLeast(0.125)
            val token = durationToken(dur)
            use(token)
            val texString = 7 - note.string.coerceIn(1, 6) // 反演：TabDocument 1=高E → alphaTex .6
            sb.append(if (note.technique == "mute") "x" else note.fret.coerceIn(0, 30).toString())
            sb.append('.').append(texString)
            techniqueSuffix(note)?.let { sb.append(it) }
            sb.append(' ')
            cursor = beat + tokenBeats(token)
        }
        emitGap(beatsPerBar - cursor)
    }

    private fun techniqueSuffix(note: NoteEvent): String? = when (note.technique) {
        "palm_mute" -> "{pm}"
        "vibrato" -> "{v}"
        else -> null
    }

    private fun parseTimeSignature(ts: String): Pair<Int, Int>? {
        val m = Regex("(\\d+)\\s*/\\s*(\\d+)").find(ts) ?: return null
        val n = m.groupValues[1].toInt()
        val d = m.groupValues[2].toInt()
        return if (n in 1..32 && d in listOf(2, 4, 8, 16)) n to d else null
    }

    private fun quote(s: String): String = "\"" + s.replace("\"", "'") + "\""

    /** TabDocument → alphaTab Score（渲染投影）。 */
    fun toScore(doc: TabDocument, settings: Settings): alphaTab.model.Score =
        ScoreLoader.loadAlphaTex(toAlphaTex(doc), settings)
            ?: throw IllegalArgumentException("谱面投影失败：alphaTex 解析为空")
}
