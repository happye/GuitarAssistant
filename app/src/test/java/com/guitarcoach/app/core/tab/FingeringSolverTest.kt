package com.guitarcoach.app.core.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F209 验收（feature_list）：爬格子 / C-Am-G-F 等 ≥10 组用例指法符合常识；
 * 非法指法被规则校验拦截（同品异弦同指仅限合法横按的物理口径）。
 */
class FingeringSolverTest {

    private fun note(string: Int, fret: Int, beat: Double = 0.0) = NoteEvent(string = string, fret = fret, beat = beat)

    private fun melody(vararg frets: Int, string: Int = 6): List<FingeringSolver.Slot> =
        frets.mapIndexed { i, f -> FingeringSolver.Slot(bar = 1, beat = i.toDouble(), notes = listOf(note(string, f))) }

    private fun chord(vararg pairs: Pair<Int, Int>): FingeringSolver.Slot =
        FingeringSolver.Slot(bar = 1, beat = 0.0, notes = pairs.map { note(it.first, it.second) }.sortedBy { it.string })

    private fun fingersOf(assignment: FingeringSolver.Assignment): List<Int> = assignment.items.map { it.finger }

    // ---- 旋律 ----

    @Test
    fun `爬格子1到4顺序指法`() {
        val result = FingeringSolver.solve(melody(1, 2, 3, 4))
        assertEquals(listOf(1, 2, 3, 4), result.map { fingersOf(it).single() })
    }

    @Test
    fun `爬格子逆行4到1`() {
        val result = FingeringSolver.solve(melody(4, 3, 2, 1))
        assertEquals(listOf(4, 3, 2, 1), result.map { fingersOf(it).single() })
    }

    @Test
    fun `空弦一律指0`() {
        val result = FingeringSolver.solve(melody(0, 0, 0))
        assertEquals(listOf(0, 0, 0), result.map { fingersOf(it).single() })
    }

    @Test
    fun `旋律跳品时换指优于同指远移`() {
        // 6弦 3品 → 8品：换指（如 1→4）应优于同一根手指滑 5 品
        val result = FingeringSolver.solve(melody(3, 8))
        val fingers = result.map { fingersOf(it).single() }
        assertTrue("期望换指，实际 $fingers", fingers[0] != fingers[1])
    }

    // ---- 和弦 ----

    @Test
    fun `C和弦常识指法`() {
        // x32010：1弦0 2弦1 4弦2 5弦3
        val a = FingeringSolver.solve(listOf(chord(1 to 0, 2 to 1, 4 to 2, 5 to 3))).single()
        assertEquals(listOf(0, 1, 2, 3), fingersOf(a))
    }

    @Test
    fun `Am和弦常识指法`() {
        // x02210：1弦0 2弦1 3弦2 4弦2 5弦0
        val a = FingeringSolver.solve(listOf(chord(1 to 0, 2 to 1, 3 to 2, 4 to 2, 5 to 0))).single()
        assertEquals(listOf(0, 1, 2, 3, 0), fingersOf(a))
    }

    @Test
    fun `E和弦常识指法`() {
        // 022100：3弦1 4弦2 5弦2
        val a = FingeringSolver.solve(listOf(chord(6 to 0, 5 to 2, 4 to 2, 3 to 1, 2 to 0, 1 to 0))).single()
        assertEquals(listOf(0, 0, 1, 2, 3, 0), fingersOf(a))
    }

    @Test
    fun `Em和弦常识指法`() {
        // 022000：5弦2 4弦3——食指/中指与中指/无名指两种按法都属常识
        val a = FingeringSolver.solve(listOf(chord(6 to 0, 5 to 2, 4 to 3, 3 to 0, 2 to 0, 1 to 0))).single()
        assertTrue(fingersOf(a) in setOf(listOf(0, 0, 0, 2, 1, 0), listOf(0, 0, 0, 3, 2, 0)))
    }

    @Test
    fun `G和弦常识指法`() {
        // 320003：5弦2=食指；1弦3/6弦3 用中指或无名指都是常见按法
        val a = FingeringSolver.solve(listOf(chord(1 to 3, 5 to 2, 6 to 3))).single()
        assertTrue(fingersOf(a) in setOf(listOf(3, 1, 2), listOf(2, 1, 3)))
    }

    @Test
    fun `F大横按共享指且校验通过`() {
        // 133211：1/2/6弦1品共享一根手指（全横按），3弦2 4弦3 5弦3
        val a = FingeringSolver.solve(listOf(chord(1 to 1, 2 to 1, 3 to 2, 4 to 3, 5 to 3, 6 to 1))).single()
        assertEquals(listOf(1, 1, 2, 3, 4, 1), fingersOf(a))
        assertEquals(1, a.position)
        assertTrue(FingeringSolver.validate(listOf(a)).isEmpty())
    }

    @Test
    fun `强力和弦根音加五音`() {
        // 6弦3品(根音)=食指；5弦5品(五音)用中指或无名指（1-2 与 1-3 形都常见）
        val a = FingeringSolver.solve(listOf(chord(6 to 3, 5 to 5))).single()
        assertTrue(fingersOf(a) in setOf(listOf(2, 1), listOf(3, 1)))
    }

    @Test
    fun `C-Am-G-F进行全程可解且各组符合常识`() {
        val slots = listOf(
            chord(1 to 0, 2 to 1, 4 to 2, 5 to 3),          // C
            chord(1 to 0, 2 to 1, 3 to 2, 4 to 2, 5 to 0),  // Am
            chord(1 to 3, 5 to 2, 6 to 3),                  // G
            chord(1 to 1, 2 to 1, 3 to 2, 4 to 3, 5 to 3, 6 to 1), // F
        )
        val result = FingeringSolver.solve(slots)
        assertEquals(listOf(0, 1, 2, 3), fingersOf(result[0]))
        assertEquals(listOf(0, 1, 2, 3, 0), fingersOf(result[1]))
        // G：1/2/3 指标准形，或整体上移一格的 2/3/4 指（Am→G 声部连接惯用技巧）
        assertTrue(fingersOf(result[2]) in setOf(listOf(3, 1, 2), listOf(2, 1, 3), listOf(3, 2, 4)))
        assertEquals(listOf(1, 1, 2, 3, 4, 1), fingersOf(result[3]))
        assertTrue(FingeringSolver.validate(result).isEmpty())
    }

    @Test
    fun `从TabDocument分槽全局小节号与拍归并`() {
        val doc = TabDocument(
            sections = listOf(
                TabSection("Main", listOf(TabBar(listOf(note(6, 0, 0.0), note(5, 2, 0.0), note(4, 2, 1.0))))),
            ),
        )
        val slots = FingeringSolver.slotsOf(doc)
        assertEquals(2, slots.size)
        assertEquals(1, slots[0].bar)
        assertEquals(0.0, slots[0].beat, 1e-9)
        assertEquals(2, slots[0].notes.size) // 同拍两音归为一组
        assertEquals(1, slots[1].bar)
    }

    // ---- 无解与非法拦截 ----

    @Test
    fun `五个不同品无横按可行时无解`() {
        // 1,3,5,7,9 品各一音：需要 5 根手指，横按也救不了（品位互不相同）
        val e = assertThrows(IllegalArgumentException::class.java) {
            FingeringSolver.solve(listOf(chord(6 to 1, 5 to 3, 4 to 5, 3 to 7, 2 to 9)))
        }
        assertTrue(e.message!!.contains("四指"))
    }

    @Test
    fun `同拍同指不同品被拦截`() {
        val bad = FingeringSolver.Assignment(
            bar = 1, beat = 0.0,
            items = listOf(
                FingeringSolver.Item(6, 3, 1),
                FingeringSolver.Item(5, 5, 1),
            ),
        )
        val violations = FingeringSolver.validate(listOf(bad))
        assertTrue(violations.any { it.contains("不同品位") })
    }

    @Test
    fun `横按压住跨度内更低品被拦截`() {
        // 指1 横按 6~1 弦第5品，但 2 弦弹空弦（0 < 5）→ 物理不可能
        val bad = FingeringSolver.Assignment(
            bar = 1, beat = 0.0,
            items = listOf(
                FingeringSolver.Item(1, 5, 1),
                FingeringSolver.Item(2, 0, 0),
                FingeringSolver.Item(6, 5, 1),
            ),
        )
        val violations = FingeringSolver.validate(listOf(bad))
        assertTrue(violations.any { it.contains("压住") })
    }

    @Test
    fun `空弦指0约束双向拦截`() {
        val bad = FingeringSolver.Assignment(
            bar = 1, beat = 0.0,
            items = listOf(
                FingeringSolver.Item(6, 0, 2), // 空弦用了指2
                FingeringSolver.Item(5, 3, 0), // 非空弦用了指0
            ),
        )
        val violations = FingeringSolver.validate(listOf(bad))
        assertEquals(2, violations.size)
    }

    @Test
    fun `同弦同拍多音被拦截`() {
        val bad = FingeringSolver.Assignment(
            bar = 1, beat = 0.0,
            items = listOf(
                FingeringSolver.Item(5, 3, 1),
                FingeringSolver.Item(5, 5, 2),
            ),
        )
        val violations = FingeringSolver.validate(listOf(bad))
        assertTrue(violations.any { it.contains("同一弦") })
    }

    @Test
    fun `把位建议取非空弦最低品`() {
        val a = FingeringSolver.solve(listOf(chord(1 to 0, 2 to 1, 4 to 2, 5 to 3))).single()
        assertEquals(1, a.position)
        val open = FingeringSolver.solve(listOf(chord(6 to 0, 5 to 0))).single()
        assertEquals(null, open.position)
    }
}
