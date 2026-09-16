package com.guitarcoach.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F602 解码器纯函数验证：合成激活矩阵 → 音符事件（阈值/最短帧/合并/midi 口径）。 */
class NoteDecoderTest {

    private fun head(vararg activationRows: List<Float>): Array<FloatArray> =
        activationRows.map { it.toFloatArray() }.toTypedArray()

    @Test
    fun `440Hz单音激活解码出midi69`() {
        // 模拟 10 帧持续激活 pitch 48（midi 69）
        val row = FloatArray(88).also { it[48] = 0.9f }
        val events = NoteDecoder.decode(Array(10) { row.copyOf() }, winStartSec = 5.0)
        assertEquals(1, events.size)
        assertEquals(69, events[0].midi) // 21 + 48
        assertEquals(5.0, events[0].timeSec, 1e-9)
        assertEquals(10 / NoteDecoder.FRAME_RATE, events[0].durationSec, 1e-6)
    }

    @Test
    fun `低于阈值与短于最短帧的段被丢弃`() {
        val weak = FloatArray(88).also { it[60] = 0.3f } // 低于 0.5
        val short = FloatArray(88).also { it[61] = 0.9f }
        val events = NoteDecoder.decode(listOf(weak, short, weak).map { it.copyOf() }.toTypedArray(), 0.0)
        assertTrue(events.isEmpty()) // short 只 1 帧 < MIN_FRAMES 3
    }

    @Test
    fun `同音相邻段间隙小于60ms被合并`() {
        val on = FloatArray(88).also { it[40] = 0.9f }
        val off = FloatArray(88) // 2 帧间隙（< 60ms）
        val events = NoteDecoder.decode(
            listOf(on, on, on, off, off, on, on, on).map { it.copyOf() }.toTypedArray(),
            0.0,
        )
        assertEquals(1, events.size)
        assertEquals(8 / NoteDecoder.FRAME_RATE, events[0].durationSec, 1e-6)
    }

    @Test
    fun `两个不同音高同时解码`() {
        val row = FloatArray(88).also { it[48] = 0.9f; it[52] = 0.8f } // midi 69 + 73
        val events = NoteDecoder.decode(Array(6) { row.copyOf() }, 0.0)
        assertEquals(listOf(69, 73), events.map { it.midi })
        assertTrue(events.all { it.timeSec == 0.0 })
    }

    @Test
    fun `选头用激活总量`() {
        val sustained = Array(10) { FloatArray(88).also { it[48] = 0.9f } } // 持续激活
        val sparse = Array(10) { f -> FloatArray(88).also { it[48] = if (f == 0) 0.9f else 0.05f } }
        val picked = NoteDecoder.pickNoteHead(listOf(sustained to 88, sparse to 88))
        assertEquals(sustained, picked)
        assertEquals(0.9f, picked[5][48], 1e-6f)
    }
}
