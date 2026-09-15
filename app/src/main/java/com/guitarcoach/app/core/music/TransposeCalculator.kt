package com.guitarcoach.app.core.music

/**
 * F206 移调/变调夹计算器：纯乐理换算，无 Android / LLM 依赖，全部逻辑可 JVM 单测。
 *
 * 口径：
 * - pitch class 用 0-11 表示，C=0（与 midiToName 的 SHARP_NAMES 同源思路）
 * - "上移 N 半音" = 新调 - 原调 的非负差（0-11）；下移 X 等价于上移 12-X
 * - 变调夹：想用 S 调指法弹出 K 调 → 夹 (K-S) mod 12 品（0 = 不用夹）
 *   例：C 调的歌用 G 调指法 → 夹 5 品（feature_list F206 验收用例）
 */
object TransposeCalculator {

    private val SHARP = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** 音名（含常见升降号等价写法）→ pitch class。键统一大写，支持单个 #/b（含 ♯/♭）。 */
    private val ALIAS = mapOf(
        "C" to 0, "B#" to 0,
        "C#" to 1, "DB" to 1,
        "D" to 2,
        "D#" to 3, "EB" to 3,
        "E" to 4, "FB" to 4,
        "E#" to 5, "F" to 5,
        "F#" to 6, "GB" to 6,
        "G" to 7,
        "G#" to 8, "AB" to 8,
        "A" to 9,
        "A#" to 10, "BB" to 10,
        "B" to 11, "CB" to 11,
    )

    /** 解析音名（1-2 字符）→ 0-11；非法输入返回 null。 */
    fun parseNote(raw: String): Int? {
        val s = raw.trim()
            .replace('♯', '#')
            .replace('♭', 'b')
            .uppercase()
        if (s.isEmpty() || s.length > 2) return null
        return ALIAS[s]
    }

    /** pitch class → 音名（升号偏好，与 midiToName 一致）。 */
    fun noteName(pc: Int): String = SHARP[((pc % 12) + 12) % 12]

    /** 原调 → 新调 的上移半音数（0-11）；任一调非法返回 null。 */
    fun semitonesUp(fromKey: String, toKey: String): Int? {
        val from = parseNote(fromKey) ?: return null
        val to = parseNote(toKey) ?: return null
        return (to - from).mod(12)
    }

    /**
     * 和弦转调：只转根音与斜杠低音，后缀（m/7/maj7/sus4/add9/…）原样保留。
     * 解析失败返回 null（调用方决定展示方式，不在核心层吞错）。
     */
    fun transposeChord(chord: String, semitones: Int): String? {
        val s = chord.trim()
        if (s.isEmpty()) return null
        val slash = s.indexOf('/')
        val rootPart = if (slash >= 0) s.substring(0, slash) else s
        val bassPart = if (slash >= 0) s.substring(slash + 1) else null
        if (bassPart?.isEmpty() == true) return null

        val (rootPc, rootLen) = parseRootAt(rootPart, 0) ?: return null
        val sb = StringBuilder(noteName((rootPc + semitones).mod(12)))
        sb.append(rootPart.substring(rootLen))
        if (bassPart != null) {
            val (bassPc, bassLen) = parseRootAt(bassPart, 0) ?: return null
            sb.append('/').append(noteName((bassPc + semitones).mod(12)))
            sb.append(bassPart.substring(bassLen))
        }
        return sb.toString()
    }

    /** 批量转调：逐条解析，解析失败保留 null 便于调用方标注是哪一个不认识。 */
    fun transposeChords(chords: List<String>, semitones: Int): List<String?> =
        chords.map { transposeChord(it, semitones) }

    /** 变调夹：原调 originalKey 想用 shapeKey 调指法 → 应夹品数（0 = 不用夹）。 */
    fun capoFret(originalKey: String, shapeKey: String): Int? {
        val orig = parseNote(originalKey) ?: return null
        val shape = parseNote(shapeKey) ?: return null
        return (orig - shape).mod(12)
    }

    /** 夹 fret 品用 shapeKey 指法 → 实际听到的调。 */
    fun soundingKey(shapeKey: String, fret: Int): String? {
        val shape = parseNote(shapeKey) ?: return null
        return noteName((shape + fret).mod(12))
    }

    /** 反查：原调 originalKey 夹 fret 品 → 应该用哪调指法（形状调）。 */
    fun shapeKeyForCapo(originalKey: String, fret: Int): String? {
        val orig = parseNote(originalKey) ?: return null
        return noteName((orig - fret).mod(12))
    }

    /** 从 start 起解析"字母+可选一个升降号"，返回 pitch class 与消耗字符数。 */
    private fun parseRootAt(s: String, start: Int): Pair<Int, Int>? {
        if (start >= s.length) return null
        val letter = s[start].uppercaseChar()
        if (letter !in 'A'..'G') return null
        val second = s.getOrNull(start + 1)
        val accidental = when (second) {
            '#', '♯' -> "#"
            'b', '♭' -> "b"
            else -> null
        }
        // ALIAS 键全大写（"BB"），字母已大写、降号也转大写再拼
        val pc = ALIAS["$letter${accidental?.uppercase() ?: ""}"] ?: return null
        return pc to (if (accidental != null) 2 else 1)
    }
}
