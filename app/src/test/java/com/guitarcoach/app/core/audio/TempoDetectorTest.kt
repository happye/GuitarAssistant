package com.guitarcoach.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** F706 BPM 检测：合成节拍序列（每拍一个衰减脉冲）应检出准确 BPM（±2）。 */
class TempoDetectorTest {

    private val rate = TempoDetector.SAMPLE_RATE

    /** 生成 durationSec 秒、bpm 的脉冲音频（每拍 10ms 脉冲）。 */
    private fun pulseAudio(bpm: Int, durationSec: Double): ShortArray {
        val total = (rate * durationSec).toInt()
        val out = ShortArray(total)
        val beatLen = (rate * 60.0 / bpm).toInt()
        val pulseLen = (rate * 0.01).toInt()
        var pos = 0
        while (pos < total) {
            for (i in 0 until pulseLen) {
                if (pos + i < total) out[pos + i] = (Short.MAX_VALUE * 0.9).toInt().toShort()
            }
            pos += beatLen
        }
        return out
    }

    @Test
    fun `合成120BPM脉冲检出准确`() {
        val detected = TempoDetector.detect(pulseAudio(120, 12.0), rate)
        assertNotNull(detected)
        assertEquals(120.0, detected!!.toDouble(), 2.0)
    }

    @Test
    fun `合成90BPM与160BPM`() {
        assertEquals(90.0, TempoDetector.detect(pulseAudio(90, 14.0), rate)!!.toDouble(), 2.0)
        assertEquals(160.0, TempoDetector.detect(pulseAudio(160, 10.0), rate)!!.toDouble(), 2.0)
    }

    @Test
    fun `过短音频返回null`() {
        assertNull(TempoDetector.detect(pulseAudio(120, 1.0), rate))
    }

    @Test
    fun `静音返回null`() {
        assertNull(TempoDetector.detect(ShortArray(rate * 5), rate))
    }

    @Test
    fun `重采样口径_44100输入也能检`() {
        val detected = TempoDetector.detect(pulseAudio(120, 12.0), 44100)
        assertNotNull(detected)
        assertEquals(120.0, detected!!.toDouble(), 2.0)
    }
}
