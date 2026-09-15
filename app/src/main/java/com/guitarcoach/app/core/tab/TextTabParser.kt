package com.guitarcoach.app.core.tab

/**
 * 极简 ASCII 六线谱解析器（Ultimate Guitar 风格），M1 阶段继续加强。
 *
 * 能处理：
 *   e|--------3---|
 *   B|-----------3|
 *   G|------------|
 *   ...
 *
 * 已知限制（M1 待办）：不映射 h/p/b/s 等技巧记号；小节线仅作视觉参考、不做节拍对齐；
 * 同一块内同一根弦出现两次时只取第一段（长谱会在此处断块）。
 */
object TextTabParser {

    // 行首标签 → 弦号。小写 e 视为高音 E（1 弦），大写 E 视为低音 E（6 弦）。
    private val STRING_LABELS = mapOf(
        'e' to 1, 'B' to 2, 'G' to 3, 'D' to 4, 'A' to 5, 'E' to 6,
    )

    private val lineRegex = Regex("^([eEaAdDgGbB])\\|?(.*)$")

    fun parse(text: String): TabDocument {
        val blocks = splitIntoBlocks(text)
        val bars = mutableListOf<TabBar>()

        for (block in blocks) {
            val byString = block.associate { (string, body) -> string to body }
            if (byString.size < 4) continue // 至少 4 根弦的行才认为是谱块
            val notes = mutableListOf<NoteEvent>()

            // 每根弦独立扫描数字段：整段一次读完，避免逐列扫描把多位数（如 10、12 品）
            // 拆成多次误读；beat 用数字段的起始列位置近似时序。
            val digitRun = Regex("\\d+")
            for ((string, body) in byString) {
                for (match in digitRun.findAll(body)) {
                    val value = match.value.toInt()
                    if (value in 0..24) {
                        notes.add(NoteEvent(string = string, fret = value, beat = match.range.first / 4.0))
                    }
                }
            }
            if (notes.isNotEmpty()) {
                bars.add(TabBar(notes.sortedBy { it.beat }))
            }
        }

        if (bars.isEmpty()) throw IllegalArgumentException("没有识别到六线谱内容，请检查文本格式")

        return TabDocument(
            title = "导入的文本谱",
            tempo = 90,
            sections = listOf(TabSection(name = "Main", bars = bars)),
        )
    }

    /** 把连续的、能被识别为六线谱行的文本切成块；同一弦号再次出现时视为新块。 */
    private fun splitIntoBlocks(text: String): List<List<Pair<Int, String>>> {
        val blocks = mutableListOf<List<Pair<Int, String>>>()
        var current = mutableListOf<Pair<Int, String>>()
        val seen = mutableSetOf<Int>()

        for (raw in text.lines()) {
            val line = raw.trim()
            val match = lineRegex.find(line)
            val label = match?.groupValues?.get(1)
            // STRING_LABELS 以 Char 为键，正则捕获组是单字符 String，转换后查询
            val string = label?.takeIf { it.length == 1 }?.let { STRING_LABELS[it[0]] }
            if (match != null && string != null) {
                if (string in seen) {
                    blocks.add(current.toList())
                    current = mutableListOf()
                    seen.clear()
                }
                current.add(string to match.groupValues[2])
                seen.add(string)
            } else if (current.isNotEmpty()) {
                blocks.add(current.toList())
                current = mutableListOf()
                seen.clear()
            }
        }
        if (current.isNotEmpty()) blocks.add(current.toList())
        return blocks
    }
}
