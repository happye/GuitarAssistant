package com.guitarcoach.app.core.tab

/**
 * 乐句（F207 讲解默认单元，2~4 小节，段落不足时例外）。
 * barStart 是全曲全局小节号（1 起，跨段落连续），与讲解步骤里的 bar 同一口径。
 */
data class Phrase(
    val index: Int,          // 全曲乐句序号（1 起）
    val sectionName: String,
    val barStart: Int,       // 全局起始小节号
    val bars: List<TabBar>,
) {
    /** 该乐句的全部音符，附带全局小节号：(bar, note)。 */
    val notes: List<Pair<Int, NoteEvent>>
        get() = bars.flatMapIndexed { i, bar -> bar.notes.map { (barStart + i) to it } }

    val barEnd: Int get() = barStart + bars.size - 1
}

/**
 * 确定性乐句分段：每段内按 maxBars 个小节切一组；尾余 1 个小节时前组回吐 1 个
 * （如 5 小节 → 3+2，9 小节 → 4+3+2），避免出现孤零零的 1 小节乐句；
 * 整段只有 1 个小节时允许单小节乐句。纯计算，无模型参与。
 */
object PhraseSegmenter {

    fun segment(doc: TabDocument, maxBars: Int = 4): List<Phrase> {
        require(maxBars >= 2) { "maxBars 至少为 2" }
        val result = mutableListOf<Phrase>()
        var globalBar = 1
        var phraseIndex = 1
        for (section in doc.sections) {
            val bars = section.bars
            var i = 0
            while (i < bars.size) {
                var take = minOf(maxBars, bars.size - i)
                if (bars.size - i - take == 1 && take >= 3) take -= 1 // 留 2 个给末组
                result += Phrase(
                    index = phraseIndex++,
                    sectionName = section.name,
                    barStart = globalBar + i,
                    bars = bars.subList(i, i + take).toList(),
                )
                i += take
            }
            globalBar += bars.size
        }
        return result
    }
}
