package com.guitarcoach.app.core.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageAuditorTest {

    private val expected = listOf(
        NoteRef(1, 6, 0),
        NoteRef(1, 5, 2),
        NoteRef(2, 4, 0),
    )

    @Test
    fun `完全覆盖通过`() {
        val issue = CoverageAuditor.check(expected, expected)
        assertTrue(issue.clean)
        assertTrue(issue.missing.isEmpty() && issue.extra.isEmpty())
    }

    @Test
    fun `漏音检出`() {
        val issue = CoverageAuditor.check(expected, expected.dropLast(1))
        assertFalse(issue.clean)
        assertEquals(listOf(NoteRef(2, 4, 0)), issue.missing)
        assertTrue(issue.extra.isEmpty())
    }

    @Test
    fun `重复讲检出为多余`() {
        val covered = expected + NoteRef(1, 6, 0)
        val issue = CoverageAuditor.check(expected, covered)
        assertFalse(issue.clean)
        assertTrue(issue.missing.isEmpty())
        assertEquals(listOf(NoteRef(1, 6, 0)), issue.extra)
    }

    @Test
    fun `讲谱面没有的音检出`() {
        val issue = CoverageAuditor.check(expected, expected + NoteRef(2, 1, 12))
        assertEquals(listOf(NoteRef(2, 1, 12)), issue.extra)
    }

    @Test
    fun `多重集：谱面同音两次覆盖一次算漏`() {
        val doc = listOf(NoteRef(1, 6, 0), NoteRef(1, 6, 0), NoteRef(2, 5, 3))
        val issue = CoverageAuditor.check(doc, listOf(NoteRef(1, 6, 0), NoteRef(2, 5, 3)))
        assertEquals(listOf(NoteRef(1, 6, 0)), issue.missing)
    }

    @Test
    fun `多重集：同音两次覆盖两次通过`() {
        val doc = listOf(NoteRef(1, 6, 0), NoteRef(1, 6, 0))
        assertTrue(CoverageAuditor.check(doc, doc).clean)
    }
}
