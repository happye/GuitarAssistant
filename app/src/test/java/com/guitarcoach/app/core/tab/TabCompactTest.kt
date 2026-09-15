package com.guitarcoach.app.core.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabCompactTest {

    @Test
    fun `跨段落全局小节号连续且字段齐全`() {
        val doc = TabDocument(
            tuning = "Drop D",
            tempo = 120,
            sections = listOf(
                TabSection("Main", listOf(TabBar(listOf(NoteEvent(6, 0, 0.0), NoteEvent(5, 2, 1.0))))),
                TabSection("Bridge", listOf(TabBar(listOf(NoteEvent(4, 2, 0.5))))),
            ),
        )
        val json = TabCompact.doc(doc)
        assertTrue(json.contains("\"tuning\":\"Drop D\""))
        assertTrue(json.contains("\"tempo\":120"))
        // 第 2 个小节来自 Bridge 段，全局小节号应为 2
        assertTrue(json.contains("""{"bar":2,"notes":[[4,2,0.5]]}"""))
        assertTrue(json.contains("[[6,0,0.0],[5,2,1.0]]"))
    }

    @Test
    fun `引号兜底转义不破坏JSON`() {
        val doc = TabDocument(tuning = "Drop\"D\"", sections = emptyList())
        val json = TabCompact.doc(doc)
        // 解析不炸即通过（无 serializer 依赖，手工构造的字符串只做形状校验）
        assertTrue(json.startsWith("""{"tuning":"Drop'D'""""))
        assertEquals(0, json.count { it == '"' } % 2) // 引号成对
    }
}
