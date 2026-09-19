package com.guitarcoach.app.core.audio

import com.guitarcoach.app.core.music.BeatGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 逐拍跟踪（重构方案 §2.1）：合成节拍序列 → 每个真实拍时刻 locate 后应贴近整数拍位。
 * 误差上限口径 = 半个 hop 的拍位折算（120BPM 时 23ms/500ms ≈ 0.046 拍）+ DP 局部权衡
 * 与脉冲 onset 帧量化，断言取 0.10（旧链固定 BPM 换算在同素材漂移累积数拍，改善 40 倍量级）。
 */
class BeatTrackerTest {

    private val rate = TempoDetector.SAMPLE_RATE

    /** 在给定拍时刻序列上放 10ms 脉冲（可加重音）。 */
    private fun pulseAudioAt(beatTimesSec: List<Double>, totalSec: Double, accentEvery: Int = 0): ShortArray {
        val total = (rate * totalSec).toInt()
        val out = ShortArray(total)
        val pulseLen = (rate * 0.01).toInt()
        beatTimesSec.forEachIndexed { i, t ->
            val amp = if (accentEvery > 0 && i % accentEvery == 0) 0.95 else 0.6
            val pos = (t * rate).toInt()
            for (k in 0 until pulseLen) {
                if (pos + k < total) out[pos + k] = (Short.MAX_VALUE * amp).toInt().toShort()
            }
        }
        return out
    }

    private fun constantBeats(bpm: Double, totalSec: Double): List<Double> {
        val period = 60.0 / bpm
        return generateSequence(0.0) { it + period }.takeWhile { it < totalSec }.toList()
    }

    @Test
    fun `恒速120BPM_拍位对齐`() {
        val times = constantBeats(120.0, 20.0)
        val grid = BeatTracker.track(pulseAudioAt(times, 20.0), rate)
        assertNotNull(grid)
        val g = grid!!
        assertEquals(120.0, g.bpm, 3.0)
        for (t in times) {
            val beat = g.locate(t)
            val err = abs(beat - kotlin.math.round(beat))
            assertTrue("拍 $t 误差 $err", err <= 0.10)
        }
    }

    @Test
    fun `渐进漂移+正弦摆动_拍位仍对齐`() {
        // 旧链（固定 BPM 换算）在此素材上到曲尾必然漂移数拍；新链必须逐拍跟上
        val period0 = 0.5 // 120 BPM
        val n = 100
        var t = 0.0
        val times = ArrayList<Double>(n)
        for (k in 0 until n) {
            times.add(t)
            val jitter = 1.0 + 0.02 * k / n + 0.03 * sin(2 * PI * k / 40.0)
            t += period0 * jitter
        }
        val grid = BeatTracker.track(pulseAudioAt(times, t + 2.0), rate)
        assertNotNull(grid)
        val g = grid!!
        for (k in 2 until n - 2) { // 首尾各留 2 拍边界余量
            val beat = g.locate(times[k])
            val err = abs(beat - k)
            assertTrue("拍 $k (t=${times[k]}) 误差 $err", err <= 0.10)
        }
    }

    @Test
    fun `4拍强音_判出44拍号`() {
        val times = constantBeats(120.0, 24.0)
        val grid = BeatTracker.track(pulseAudioAt(times, 24.0, accentEvery = 4), rate)
        assertNotNull(grid)
        assertEquals(4, grid!!.beatsPerBar)
    }

    @Test
    fun `过短音频返回null`() {
        val times = constantBeats(120.0, 2.0)
        assertNull(BeatTracker.track(pulseAudioAt(times, 2.0), rate))
    }

    @Test
    fun `locate_越界外推与插值`() {
        val grid = BeatGrid(120.0, 4, doubleArrayOf(0.0, 0.5, 1.0, 1.5, 2.0, 2.5))
        assertEquals(0.5, grid.locate(0.25), 1e-9)
        assertEquals(1.0, grid.locate(0.5), 1e-9)
        assertEquals(2.0, grid.locate(1.0), 1e-9)
        assertEquals(6.0, grid.locate(3.0), 1e-9) // 末尾外推（2.5 → 3.0 用局部周期 0.5）
        assertEquals(0.0, grid.locate(0.0), 1e-9) // 首拍边界
    }
}
