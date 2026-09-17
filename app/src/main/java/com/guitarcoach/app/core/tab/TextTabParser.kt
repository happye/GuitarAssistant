package com.guitarcoach.app.core.tab

/**
 * ASCII 六线谱解析器 v2（Ultimate Guitar 风格；用户实测反馈"解析出来的谱是错的"后重写）。
 *
 * v1 三个根因级 bug（均有真实 UG 谱触发）：
 * 1. 行判定过松：任何以 e/E/a-D/g-B 开头的行都当成弦行 → 和弦行「Am x02210」、说明行「Dropped D tuning」
 *    被误认成 A/D 弦行，里面的数字全变假音符。v2 要求：弦行必须含「|」且数据区首字符为 -/数字/技巧符。
 * 2. 拍位漂移：v2.1 按小节线切段（段边界取最长行的「|」绝对列，各弦数据按列归属），每段一个
 *    TabBar，beat = 段内列偏移/4（16 分网格）从 0 起算——与渲染端 4/4 口径严格对齐。
 * 3. 重复标记污染：「(x2)」「x2」「x02210」里的数字被当音符。v2 跳过 x 前缀数字。
 *
 * 已知限制：h/p/s/b 技巧记号不转 technique（目标音位置正确）；1 字符=16 分网格为近似拍位；
 * 单段列数上限 [MAX_SEGMENT_COLS]（防无竖线病态长行把 beat/播放时值撑爆，对抗审查 P1）。
 */
object TextTabParser {

    // 行首标签 → 弦号。小写 e 视为高音 E（1 弦），大写 E 视为低音 E（6 弦）。
    private val STRING_LABELS = mapOf(
        'e' to 1, 'B' to 2, 'G' to 3, 'D' to 4, 'A' to 5, 'E' to 6,
    )

    /** 弦行：标签后必须跟「|」进入数据区（UG 惯例），否则视为和弦名/说明行拒绝。 */
    private val lineRegex = Regex("^([eEaAdDgGbB])\\|(.*)$")

    /** 数据区合法首字符：横线（最常见）、数字（空弦紧贴标签）、技巧字符、空格后接上述。 */
    private val dataStartRegex = Regex("^[-0-9hpbsbHBSPB~/\\\\ ]")

    /** 单段（小节）最大列数：4/4 十六分网格满拍=64 列，512 列已是 8 小节病态值，防爆炸 cap。 */
    private const val MAX_SEGMENT_COLS = 512

    fun parse(text: String): TabDocument {
        val blocks = splitIntoBlocks(text)
        val bars = mutableListOf<TabBar>()

        for (block in blocks) {
            val byString = block.associate { (string, body) -> string to body }
            if (byString.size < 4) continue // 至少 4 根弦的行才认为是谱块

            // 段边界 = 最长行的「|」绝对列（各弦数据按列归属，不要求每行竖线完全对齐）；
            // 每段一个 TabBar，beat = 段内列偏移/4 从 0 起（与渲染端 4/4 口径对齐——监督员 P1 真修）
            val baseBody = byString.values.maxByOrNull { it.length }!!
            val boundaries = baseBody.withIndex().filter { it.value == '|' }.map { it.index } + listOf(baseBody.length)
            val notes = mutableListOf<NoteEvent>()
            var segStart = 0
            for (segEndRaw in boundaries) {
                val segEnd = minOf(segEndRaw, segStart + MAX_SEGMENT_COLS)
                if (segEnd > segStart) {
                    val limit = minOf(segEnd, byString.values.maxOf { it.length })
                    for ((string, body) in byString) {
                        var col = segStart
                        while (col < minOf(limit, body.length)) {
                            val c = body[col]
                            if (c.isDigit()) {
                                var end = col
                                while (end < minOf(limit, body.length) && body[end].isDigit()) end++ // 多位数不跨段界
                                val prev = if (col > segStart) body[col - 1] else ' '
                                val isRepeatMark = prev == 'x' || prev == 'X'
                                val value = body.substring(col, end).toIntOrNull() ?: 99
                                if (!isRepeatMark && value in 0..24) {
                                    notes.add(NoteEvent(string = string, fret = value, beat = (col - segStart) / 4.0))
                                }
                                col = end
                            } else {
                                col++
                            }
                        }
                    }
                    bars.add(TabBar(notes.sortedWith(compareBy({ it.beat }, { it.string }))))
                    notes.clear()
                }
                segStart = segEnd + 1
            }
        }

        if (bars.isEmpty()) throw IllegalArgumentException("没有识别到六线谱内容：弦行需要「弦名|----」格式（如 e|--------3---|），且至少 4 根弦")

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
        var seen = mutableSetOf<Int>()

        for (raw in text.lines()) {
            val line = raw.trim()
            val match = lineRegex.find(line)
            val label = match?.groupValues?.get(1)
            val string = label?.let { STRING_LABELS[it[0]] }
            val looksLikeTab = match != null && string != null && dataStartRegex.containsMatchIn(match.groupValues[2])
            if (looksLikeTab) {
                if (string in seen) {
                    blocks.add(current.toList())
                    current = mutableListOf()
                    seen = mutableSetOf()
                }
                current.add(string to match.groupValues[2])
                seen.add(string)
            } else if (current.isNotEmpty()) {
                blocks.add(current.toList())
                current = mutableListOf()
                seen = mutableSetOf()
            }
        }
        if (current.isNotEmpty()) blocks.add(current.toList())
        return blocks
    }
}
