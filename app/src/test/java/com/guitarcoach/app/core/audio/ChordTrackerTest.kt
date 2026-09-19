package com.guitarcoach.app.core.audio

import com.guitarcoach.app.core.music.BeatGrid
import com.guitarcoach.app.core.music.TimedNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTrackerTest {

    private fun grid120(beats: Int = 16): BeatGrid = BeatGrid(120.0, 4, DoubleArray(beats) { it * 0.5 })
    private fun n(midi: Int, t: Double, dur: Double = 0.9, amp: Double = 0.8) =
        TimedNote(midi = midi, timeSec = t, durationSec = dur, amplitude = amp)

    @Test
    fun `C大三和弦持续_检出C`() {
        // C4(60) E4(64) G4(67) 同时起，持续 4 拍
        val notes = listOf(n(60, 0.0, 2.0), n(64, 0.0, 2.0), n(67, 0.0, 2.0), n(60, 2.0, 2.0), n(64, 2.0, 2.0), n(67, 2.0, 2.0))
        val chords = ChordTracker.detect(notes, grid120())
        assertEquals(1, chords.size)
        assertEquals("C", chords[0].display)
        assertEquals("maj", chords[0].quality)
        assertTrue(chords[0].confidence > 0.5f)
    }

    @Test
    fun `强力和弦_检出根音加5`() {
        // E3(52)+B3(59) power chord 4 拍
        val notes = listOf(n(52, 0.0, 2.0), n(59, 0.0, 2.0), n(52, 2.0, 2.0), n(59, 2.0, 2.0))
        val chords = ChordTracker.detect(notes, grid120())
        assertEquals(1, chords.size)
        assertEquals("E5", chords[0].display)
        assertEquals("5", chords[0].quality)
    }

    @Test
    fun `和弦变化_两段正确切分`() {
        // 前 2 小节 C，后 2 小节 G（G4=67 B4=71 D5=74）
        val notes = mutableListOf<TimedNote>()
        for (bar in 0 until 2) {
            val t = bar * 2.0
            notes += listOf(n(60, t, 2.0), n(64, t, 2.0), n(67, t, 2.0))
        }
        for (bar in 2 until 4) {
            val t = bar * 2.0
            notes += listOf(n(67, t, 2.0), n(71, t, 2.0), n(74, t, 2.0))
        }
        val chords = ChordTracker.detect(notes, grid120(16))
        assertEquals(2, chords.size)
        assertEquals("C", chords[0].display)
        assertEquals("G", chords[1].display)
        assertEquals(0.0, chords[0].beat, 1e-9)
        assertEquals(8.0, chords[1].beat, 1e-9)
    }

    @Test
    fun `静音段不断链_短噪声被吸收`() {
        // C 和弦 + 中间混入 1 个 1 拍的远关系单音（F#5 深色音），不应整段切碎
        val notes = listOf(
            n(60, 0.0, 2.0), n(64, 0.0, 2.0), n(67, 0.0, 2.0),
            n(62, 2.0, 0.5), // D4 短音（近关系噪声，0.5s=1 拍）
            n(60, 3.0, 2.0), n(64, 3.0, 2.0), n(67, 3.0, 2.0),
        )
        val chords = ChordTracker.detect(notes, grid120(16))
        assertTrue("应少于等于 2 段: ${chords.map { it.display }}", chords.size <= 2)
        assertEquals("C", chords.first().display)
    }

    @Test
    fun `空输入_空输出`() {
        assertTrue(ChordTracker.detect(emptyList(), grid120()).isEmpty())
    }
}

class InputClassifierTest {

    private fun n(midi: Int, t: Double, dur: Double = 0.5) = TimedNote(midi, t, dur)

    @Test
    fun `单音riff_判CLEAN_MONO`() {
        val notes = (0 until 20).map { n(45 + (it % 5), it * 0.3) }
        assertEquals(InputClassifier.Material.CLEAN_MONO, InputClassifier.classify(notes))
    }

    @Test
    fun `宽音域高复音_判MIXED`() {
        // 贝斯+鼓式：低音 36-40 与高音 76-84 近同时成对，跨度 >19
        val notes = mutableListOf<TimedNote>()
        for (k in 0 until 20) {
            val t = k * 0.4
            notes += n(36 + (k % 5), t)
            notes += n(76 + (k % 5), t + 0.01)
        }
        assertEquals(InputClassifier.Material.MIXED, InputClassifier.classify(notes))
    }

    @Test
    fun `窄音域双音_判CLEAN_POLY`() {
        // 三度双音（跨度小）
        val notes = mutableListOf<TimedNote>()
        for (k in 0 until 16) {
            val t = k * 0.4
            notes += n(60, t); notes += n(63, t + 0.01)
        }
        assertEquals(InputClassifier.Material.CLEAN_POLY, InputClassifier.classify(notes))
    }
}
