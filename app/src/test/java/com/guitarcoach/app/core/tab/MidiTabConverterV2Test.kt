package com.guitarcoach.app.core.tab

import com.guitarcoach.app.core.music.BeatGrid
import com.guitarcoach.app.core.music.TimedNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 弦品转换 v2（重构方案 §2.3）：逐拍网格插值量化 + 自适应分辨率 + 吸附容差。
 * 回归口径：恒速网格下与旧 convert(notes, bpm) 等价。
 */
class MidiTabConverterV2Test {

    private fun grid120(): BeatGrid = BeatGrid(120.0, 4, DoubleArray(60) { it * 0.5 }) // 0..29.5s

    private fun n(midi: Int, t: Double, dur: Double = 0.5, amp: Double = 0.8) =
        TimedNote(midi = midi, timeSec = t, durationSec = dur, amplitude = amp)

    @Test
    fun `恒速网格_落在拍上的音吸附为整数拍`() {
        // midi 64（E4）在 1弦4品 或 2弦9品…；只验证拍位量化
        val placed = MidiTabConverter.convert(listOf(n(64, 1.0), n(64, 2.0)), grid120())
        assertEquals(listOf(2.0, 4.0), placed.map { it.beat })
        assertTrue(placed.all { it.snapped })
    }

    @Test
    fun `拍间偏差在容差内吸附_超差保留分数并降级`() {
        val placed = MidiTabConverter.convert(
            listOf(n(64, 1.03), n(64, 2.2)), // 2.06 拍（可吸）；4.4 拍（偏 0.4 超容差）
            grid120(),
        )
        assertEquals(2.0, placed[0].beat, 1e-9)
        assertTrue(placed[0].snapped)
        assertEquals(4.4, placed[1].beat, 1e-9)
        assertFalse(placed[1].snapped) // 低置信标记，不给网格"洗白"
    }

    @Test
    fun `IOI自适应_8分音符素材拿半拍网格`() {
        // 8 分音符流（120BPM 时拍距 0.25s = 0.5 拍）
        val notes = (0 until 8).map { k -> n(64, 0.25 * k + 0.25) } // 拍位 1,1.5,2,2.5...
        val placed = MidiTabConverter.convert(notes, grid120())
        assertEquals(listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0), placed.map { it.beat })
        assertTrue(placed.all { it.snapped })
    }

    @Test
    fun `渐进漂移素材_量化跟着实测拍走`() {
        // 恒速网格在此素材上第 100 拍时漂移 ~2 拍；实测网格必须吸收
        var t = 0.0
        val times = ArrayList<Double>()
        val beatTimes = ArrayList<Double>()
        for (k in 0 until 100) {
            beatTimes.add(t)
            times.add(t)
            t += 0.5 * (1.0 + 0.02 * k / 100.0)
        }
        val grid = BeatGrid(120.0, 4, beatTimes.toDoubleArray())
        val placed = MidiTabConverter.convert(times.map { n(64, it) }, grid)
        // 每个音应吸附到自身所在整数拍（误差 ≤ 0.05 拍生成时已保证在容差内）
        placed.forEachIndexed { i, p ->
            assertEquals(i.toDouble(), p.beat, 0.13)
            assertTrue("漂移音 $i 应可吸附", p.snapped)
        }
    }

    @Test
    fun `旧接口与恒速网格新接口等价`() {
        val midiNotes = listOf(MidiTabConverter.MidiNote(64, 1.0, 0.5), MidiTabConverter.MidiNote(67, 3.0, 0.5))
        val old = MidiTabConverter.convert(midiNotes, 120)
        val new = MidiTabConverter.convert(
            midiNotes.map { TimedNote(it.midi, it.timeSec, it.durationSec, it.amplitude) },
            BeatGrid.constant(120, 8.0),
        )
        assertEquals(old.map { it.string to it.fret }, new.map { it.string to it.fret })
        assertEquals(old.map { it.beat }, new.map { it.beat })
    }

    @Test
    fun `超域音跳过不抛`() {
        val placed = MidiTabConverter.convert(listOf(n(30, 0.0), n(64, 1.0)), grid120()) // midi 30 超域
        assertEquals(1, placed.size)
        assertEquals(2.0, placed[0].beat, 1e-9)
    }
}
