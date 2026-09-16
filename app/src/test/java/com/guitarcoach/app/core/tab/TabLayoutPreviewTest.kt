package com.guitarcoach.app.core.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 谱面布局视觉自检（用户要求"对 UI 界面进行预览测试"）：按渲染端同口径把 TabLayout 真实输出
 * 画成 PNG（纯 Kotlin 编码，PngWriter；unit-test 无 AWT），供多模态检查偏移/对齐/小节映射；
 * 同时做程序化断言（品数块落在对应弦线 ±0.02 拍位网格、在所属小节框内、1 弦在最上）。
 */
class TabLayoutPreviewTest {

    private val scale = 3
    private val barWidth = 240f
    private val spacing = 18f
    private val topPad = 34f
    private val barPad = 16f
    private val gutter = 20f

    private class Canvas(val w: Int, val h: Int) {
        val px = IntArray(w * h) { 0xFFF8F4E9.toInt() } // 米白纸面

        fun set(x: Int, y: Int, c: Int) {
            if (x in 0 until w && y in 0 until h) px[y * w + x] = c
        }

        fun rect(x0: Float, y0: Float, x1: Float, y1: Float, c: Int, fill: Boolean = true) {
            val xi0 = x0.toInt().coerceIn(0, w - 1)
            val yi0 = y0.toInt().coerceIn(0, h - 1)
            val xi1 = x1.toInt().coerceIn(0, w - 1)
            val yi1 = y1.toInt().coerceIn(0, h - 1)
            for (y in yi0..yi1) for (x in xi0..xi1) {
                if (fill || y == yi0 || y == yi1 || x == xi0 || x == xi1) set(x, y, c)
            }
        }

        fun line(x0: Float, y0: Float, x1: Float, y1: Float, c: Int, thick: Int = 1) {
            val steps = (kotlin.math.max(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0)) * 2).toInt() + 1
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val x = (x0 + (x1 - x0) * t)
                val y = (y0 + (y1 - y0) * t)
                for (dy in 0 until thick) for (dx in 0 until thick) set(x.toInt() + dx, y.toInt() + dy, c)
            }
        }

        /** 3x5 像素字形放大绘制（数字 + 弦名字母 eBGDAE）。 */
        fun text(s: String, x: Float, y: Float, c: Int, scalePx: Int = 2) {
            var cx = x
            for (ch in s) {
                val glyph = GLYPHS[ch.uppercaseChar()] ?: GLYPHS['?'] ?: continue
                for ((r, row) in glyph.withIndex()) {
                    for ((col, bit) in row.withIndex()) {
                        if (bit == '1') {
                            for (dy in 0 until scalePx) for (dx in 0 until scalePx) {
                                set((cx + col * scalePx).toInt(), (y + r * scalePx).toInt() + dy, c)
                            }
                        }
                    }
                }
                cx += 4 * scalePx
            }
        }
    }

    private fun color(argb: Long) = argb.toInt()

    @Test
    fun `渲染布局预览图`() {
        val green = color(0x1B6E4A)
        val lineC = color(0x666666)
        val tickC = color(0xBBBBAA)

        val doc = TabDocument(
            title = "预览", tempo = 90,
            sections = listOf(
                TabSection("Intro", listOf(
                    TabBar(listOf(n(6, 0, 0.0), n(5, 2, 1.0), n(4, 2, 2.0), n(3, 1, 3.0), n(2, 10, 3.5), n(1, 0, 3.75))),
                    TabBar(listOf(n(6, 3, 0.0), n(6, 5, 1.0), n(5, 3, 1.0), n(5, 5, 2.0), n(4, 5, 2.5), n(3, 5, 3.0))),
                )),
                TabSection("Chorus", listOf(
                    TabBar(listOf(n(1, 12, 0.0), n(1, 14, 0.5), n(1, 15, 1.0), n(2, 13, 1.5), n(3, 12, 2.0), n(4, 10, 3.0))),
                    TabBar(listOf(n(6, 0, 0.0), n(5, 0, 1.0), n(4, 0, 2.0), n(3, 0, 3.0))),
                )),
            ),
        )
        val geometry = TabLayout.Geometry(barWidth, spacing, topPad, barPad)
        val placed = TabLayout.layout(doc, geometry).map { it.copy(x = it.x + gutter) } // 渲染端同源偏移

        val barCount = doc.sections.sumOf { it.bars.size }
        // 1:1 逻辑尺寸画布（绘制坐标即逻辑坐标），字形用 scale 放大；旧版乘 scale 导致内容只占左 1/3
        val cv = Canvas((barCount * barWidth + gutter).toInt() + 8, (topPad + 6 * spacing + 12).toInt() + 8)

        // 段落名条
        var barIdx = 0
        doc.sections.forEach { section ->
            cv.rect(gutter + barIdx * barWidth, 4f, gutter + barIdx * barWidth + 90f, 20f, color(0xDDE8D0))
            cv.text(section.name, gutter + barIdx * barWidth + 6f, 8f, color(0x333333), scalePx = scale)
            barIdx += section.bars.size
        }
        // 拍位刻度
        for (b in 0 until barCount) for (t in 1..3) {
            val x = gutter + b * barWidth + barPad + t / 4f * (barWidth - 2 * barPad)
            cv.line(x, topPad - 4f, x, topPad + 5 * spacing + 4f, tickC)
        }
        // 谱线 + 弦名 + 小节线
        val strings = listOf("e", "B", "G", "D", "A", "E")
        for (s in 0..5) {
            val y = topPad + s * spacing
            cv.line(gutter, y, gutter + barCount * barWidth, y, lineC, thick = scale / 2)
            cv.text(strings[s], 4f, y - 3f, color(0x444444), scalePx = scale)
        }
        for (b in 0..barCount) {
            val x = gutter + b * barWidth
            cv.line(x, topPad - 8f, x, topPad + 5 * spacing + 8f, lineC, thick = scale / 2)
            if (b == barCount) cv.line(x - 4f, topPad - 8f, x - 4f, topPad + 5 * spacing + 8f, lineC, thick = scale / 2)
            cv.text("${b + 1}", gutter + b * barWidth + 3f, topPad - 18f, lineC, scalePx = scale)
        }
        // 品数块 + 品数（渲染端同口径）
        placed.forEach { p ->
            cv.rect(p.x - 8f, p.y - 6.5f, p.x + 8f, p.y + 6.5f, green)
            cv.text("${p.note.fret}", p.x - 5f, p.y - 4f, 0xFFFFFFFF.toInt(), scalePx = 2)
        }

        val out = File(System.getProperty("java.io.tmpdir"), "tab-layout-preview.png")
        PngWriter.write(out, cv.w, cv.h, cv.px)
        println("PREVIEW: ${out.absolutePath}")

        // 程序化断言：品数块中心 y 精确落在对应弦线上；x 在所属小节框内；1 弦在最上
        placed.forEach { p ->
            val rel = (p.y - topPad) / spacing
            assertEquals("弦线对齐失败: note=$p rel=$rel", p.note.string - 1f, rel, 0.02f)
            val barX = gutter + p.barIndex * barWidth
            assertTrue("小节框外: $p", p.x >= barX && p.x <= barX + barWidth)
        }
        val s1 = placed.first { it.note.string == 1 }
        val s6 = placed.first { it.note.string == 6 }
        assertTrue("1 弦应在最上（y 更小）", s1.y < s6.y)
        assertTrue("1 弦与 6 弦间距 = 5×spacing", kotlin.math.abs((s6.y - s1.y) - 5 * spacing) < 0.01f)
    }

    private fun n(string: Int, fret: Int, beat: Double) = NoteEvent(string = string, fret = fret, beat = beat)

    private companion object {
        // 3x5 像素字形（0-9 + E/B/G/D/A/给个 ? 兜底）
        val GLYPHS: Map<Char, Array<String>> = mapOf(
            '0' to arrayOf("111", "101", "101", "101", "111"),
            '1' to arrayOf("010", "110", "010", "010", "111"),
            '2' to arrayOf("111", "001", "111", "100", "111"),
            '3' to arrayOf("111", "001", "111", "001", "111"),
            '4' to arrayOf("101", "101", "111", "001", "001"),
            '5' to arrayOf("111", "100", "111", "001", "111"),
            '6' to arrayOf("111", "100", "111", "101", "111"),
            '7' to arrayOf("111", "001", "010", "010", "010"),
            '8' to arrayOf("111", "101", "111", "101", "111"),
            '9' to arrayOf("111", "101", "111", "001", "111"),
            'E' to arrayOf("111", "100", "111", "100", "111"),
            'B' to arrayOf("111", "101", "110", "101", "111"),
            'G' to arrayOf("111", "100", "101", "101", "111"),
            'D' to arrayOf("110", "101", "101", "101", "110"),
            'A' to arrayOf("111", "101", "111", "101", "101"),
            'I' to arrayOf("111", "010", "010", "010", "111"),
            'N' to arrayOf("101", "111", "111", "111", "101"),
            'T' to arrayOf("111", "010", "010", "010", "010"),
            'R' to arrayOf("111", "101", "110", "101", "101"),
            'O' to arrayOf("111", "101", "101", "101", "111"),
            'C' to arrayOf("111", "100", "100", "100", "111"),
            'H' to arrayOf("101", "101", "111", "101", "101"),
            'U' to arrayOf("101", "101", "101", "101", "111"),
            'S' to arrayOf("111", "100", "111", "001", "111"),
            '?' to arrayOf("111", "001", "010", "000", "010"),
        )
    }
}
