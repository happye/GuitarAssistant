package com.guitarcoach.app.core.audio

import com.guitarcoach.app.core.music.TimedNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 音符清洗规则（重构方案 §2.2）：R4 低置信 / R1 同音合并 / R2 泛音鬼影 / R3 近同时成组。 */
class NoteCleanerTest {

    private fun n(midi: Int, t: Double, dur: Double = 0.5, amp: Double = 0.8) =
        TimedNote(midi = midi, timeSec = t, durationSec = dur, amplitude = amp)

    @Test
    fun `R4_低置信音删除并计数`() {
        // 中位 amp=0.8 → 阈值 0.2；0.15 被删，0.8/0.9 保留
        val r = NoteCleaner.clean(listOf(n(60, 0.0, amp = 0.8), n(62, 0.5, amp = 0.15), n(64, 1.0, amp = 0.9)))
        assertEquals(listOf(60, 64), r.notes.map { it.midi })
        assertEquals(1, r.droppedLowAmp)
    }

    @Test
    fun `R1_同音近邻合并`() {
        // 60ms 窗内同 midi 两音 → 并为一个，时长延展
        val r = NoteCleaner.clean(listOf(n(60, 0.0, dur = 0.3), n(60, 0.04, dur = 0.3), n(64, 1.0)))
        assertEquals(2, r.notes.size)
        assertEquals(1, r.mergedSamePitch)
        val first = r.notes.first()
        assertEquals(60, first.midi)
        assertTrue(first.durationSec >= 0.3) // 0.04+0.3-0.0 = 0.34
        assertEquals(0.34, first.durationSec, 1e-9)
    }

    @Test
    fun `R1_拍窗随BPM扩展`() {
        // 60 BPM：0.25 拍 = 250ms → 200ms 间隔同音也应合并
        val r = NoteCleaner.clean(listOf(n(60, 0.0, dur = 0.1), n(60, 0.2, dur = 0.1)), secPerBeat = 1.0)
        assertEquals(1, r.notes.size)
    }

    @Test
    fun `R2_八度鬼影删除_真高音保留`() {
        // p=60(amp 0.9) 与 q=72(amp 0.2, 30ms 后)：鬼影删
        val withGhost = listOf(n(60, 0.0, amp = 0.9, dur = 0.5), n(72, 0.03, amp = 0.2, dur = 0.4), n(64, 1.0))
        val r1 = NoteCleaner.clean(withGhost)
        assertEquals(listOf(60, 64), r1.notes.map { it.midi })
        assertEquals(1, r1.droppedGhosts)
        // q 更响 → 是真音，保留
        val realHigh = listOf(n(60, 0.0, amp = 0.3, dur = 0.5), n(72, 0.03, amp = 0.9, dur = 0.4), n(64, 1.0))
        val r2 = NoteCleaner.clean(realHigh)
        assertEquals(listOf(60, 72, 64), r2.notes.map { it.midi })
    }

    @Test
    fun `R2_时间窗外的八度音不删`() {
        // 隔 1s 的 +12 音是正常旋律，不是鬼影
        val r = NoteCleaner.clean(listOf(n(60, 0.0, amp = 0.9), n(72, 1.0, amp = 0.2), n(64, 2.0)))
        assertEquals(listOf(60, 72, 64), r.notes.map { it.midi })
    }

    @Test
    fun `R3_近同时音成组_同音留最响`() {
        // 30ms 内：60 出现两次（0.3/0.9）+ 64 → 组内 60 留最响，全组同 groupId
        val r = NoteCleaner.clean(listOf(n(60, 0.0, amp = 0.3), n(60, 0.02, amp = 0.9), n(64, 0.03), n(67, 1.0)))
        assertEquals(3, r.notes.size)
        val group = r.notes.filter { it.groupId >= 0 }
        assertEquals(setOf(60, 64), group.map { it.midi }.toSet())
        assertTrue(group.all { it.groupId == group[0].groupId })
        assertEquals(0.9, group.first { it.midi == 60 }.amplitude, 1e-9)
        assertEquals(1, r.chordGroups)
        assertEquals(-1, r.notes.first { it.midi == 67 }.groupId)
    }

    @Test
    fun `空输入与保序`() {
        assertEquals(0, NoteCleaner.clean(emptyList()).notes.size)
        val r = NoteCleaner.clean(listOf(n(60, 0.1), n(62, 0.5)))
        assertEquals(listOf(60, 62), r.notes.map { it.midi })
        // 未排序输入被 require 拒绝
        val unsorted = runCatching { NoteCleaner.clean(listOf(n(62, 0.5), n(60, 0.1))) }
        assertTrue(unsorted.isFailure)
    }
}
