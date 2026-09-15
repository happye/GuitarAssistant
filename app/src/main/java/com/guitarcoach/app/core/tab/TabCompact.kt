package com.guitarcoach.app.core.tab

/**
 * 谱面的紧凑 JSON 序列化：喂给提示词时控制 token（全局小节号连续，notes=[弦,品,起始拍]）。
 * 展示/讲解/审计共用同一口径：bar 1 起、跨段落连续，与 PhraseSegmenter/FingeringSolver 一致。
 */
object TabCompact {

    fun doc(d: TabDocument): String = buildString {
        append("""{"tuning":"${d.tuning.replace("\"", "'")}","tempo":${d.tempo},"bars":[""")
        var globalBar = 1
        d.sections.forEach { section ->
            section.bars.forEach { bar ->
                if (globalBar > 1) append(",")
                append("""{"bar":$globalBar,"notes":[""")
                bar.notes.forEachIndexed { i, note ->
                    if (i > 0) append(",")
                    append("[${note.string},${note.fret},${note.beat}]")
                }
                append("]}")
                globalBar++
            }
        }
        append("]}")
    }
}
