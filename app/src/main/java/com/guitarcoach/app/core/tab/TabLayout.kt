package com.guitarcoach.app.core.tab

/**
 * 谱面版面计算（F202 自绘渲染的纯几何部分）：把 TabDocument 摆到横向谱面网格上。
 * 坐标口径：x 沿演奏方向递增；y = topPad + (string-1)*spacing（1 弦在最上）。
 * 小节内音符 x 按 (beat / 4) 固定 4/4 口径铺开（不满小节的谱不再被拉伸），与绘制端共用保证点按判定一致。
 */
object TabLayout {

    data class PlacedNote(
        val x: Float,
        val y: Float,
        val barIndex: Int,      // 全局小节号（0 起，绘制用）
        val noteIndex: Int,
        val note: NoteEvent,
    )

    data class Geometry(val barWidthPx: Float, val stringSpacingPx: Float, val topPadPx: Float, val barPadPx: Float)

    fun layout(doc: TabDocument, g: Geometry): List<PlacedNote> {
        val out = mutableListOf<PlacedNote>()
        var globalBar = 0
        doc.sections.forEach { section ->
            section.bars.forEach { bar ->
                val innerStart = globalBar * g.barWidthPx + g.barPadPx
                val innerWidth = g.barWidthPx - 2 * g.barPadPx
                bar.notes.forEachIndexed { ni, note ->
                    // 同拍多音（和弦）保持竖直对齐；超拍长谱暂不换行（v1 4/4 口径）
                    out += PlacedNote(
                        x = innerStart + (note.beat / 4.0 * innerWidth).toFloat().coerceAtMost(innerStart + innerWidth - g.barPadPx),
                        y = g.topPadPx + (note.string - 1) * g.stringSpacingPx,
                        barIndex = globalBar,
                        noteIndex = ni,
                        note = note,
                    )
                }
                globalBar++
            }
        }
        return out
    }
}
