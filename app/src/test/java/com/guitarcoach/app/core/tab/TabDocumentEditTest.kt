package com.guitarcoach.app.core.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TabDocumentEditTest {

    private val doc = TabDocument(
        sections = listOf(
            TabSection("Main", listOf(TabBar(listOf(NoteEvent(6, 0, 0.0))), TabBar(listOf(NoteEvent(5, 2, 0.0))))),
            TabSection("Bridge", listOf(TabBar(listOf(NoteEvent(4, 2, 0.0))))),
        ),
    )

    @Test
    fun `makeNote合法性边界`() {
        assertNotNull(makeNote(1, 0, 0.0))
        assertNotNull(makeNote(6, 24, 3.5))
        assertNull(makeNote(0, 1, 0.0))
        assertNull(makeNote(7, 1, 0.0))
        assertNull(makeNote(1, 25, 0.0))
        assertNull(makeNote(1, -1, 0.0))
        assertNull(makeNote(1, 3, -0.5))
        assertNull(makeNote(1, 3, Double.NaN))
    }

    @Test
    fun `withBar替换后原对象不变`() {
        val newBar = TabBar(listOf(NoteEvent(1, 5, 0.0)))
        val edited = doc.withBar(0, 1, newBar)
        // 新对象生效
        assertEquals(newBar, edited.sections[0].bars[1])
        // 原对象不变（data class copy + 可变列表局部化）
        assertEquals(listOf(NoteEvent(5, 2, 0.0)), doc.sections[0].bars[1].notes)
        // 其他段落引用保留
        assertEquals(doc.sections[1], edited.sections[1])
    }

    @Test
    fun `withBar越界抛可读错误`() {
        val e1 = assertThrows(IllegalArgumentException::class.java) { doc.withBar(5, 0, TabBar()) }
        assertTrue(e1.message!!.contains("段落"))
        val e2 = assertThrows(IllegalArgumentException::class.java) { doc.withBar(0, 9, TabBar()) }
        assertTrue(e2.message!!.contains("小节"))
    }

    @Test
    fun `全局小节号跨段落连续`() {
        assertEquals(1, doc.globalBarNumber(0, 0))
        assertEquals(2, doc.globalBarNumber(0, 1))
        assertEquals(3, doc.globalBarNumber(1, 0))
    }
}
