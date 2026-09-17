package com.guitarcoach.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** STFT/iSTFT round-trip：正弦经变换-逆变换后频率与波形还原（分离管线的地基）。 */
class StftTest {

    @Test
    fun `正弦信号round-trip还原`() {
        val sr = 44100
        val freq = 220.0
        val samples = FloatArray(Stft.HOP * 30 + Stft.WIN) { i ->
            (sin(2 * PI * freq * i / sr) * 0.5).toFloat()
        }
        val stft = Stft.stft(samples)
        assertTrue(stft.numFrames >= 28)
        val restored = Stft.istft(stft.real, stft.imag, stft.numFrames)
        // 中段（避开窗边界）逐点误差 < 0.01
        val from = Stft.WIN
        val to = restored.size - Stft.WIN
        for (i in from until to) {
            assertEquals("i=$i", samples[i].toDouble(), restored[i].toDouble(), 0.01)
        }
    }

    @Test
    fun `正弦的频谱峰落在正确bin`() {
        val sr = 44100
        val freq = 440.0
        val samples = FloatArray(Stft.WIN) { i -> (sin(2 * PI * freq * i / sr) * 0.8).toFloat() }
        val stft = Stft.stft(samples)
        val expectedBin = (freq / sr * Stft.N_FFT).toInt() // 40.6 → bin 40 或 41
        var maxBin = 0
        var maxMag = 0.0
        for (b in 0 until Stft.BINS) {
            val m = Stft.magnitude(stft.real, stft.imag, 0, b)
            if (m > maxMag) { maxMag = m; maxBin = b }
        }
        assertTrue("峰 bin=$maxMag@bin$maxBin 期望~$expectedBin", kotlin.math.abs(maxBin - expectedBin) <= 1)
    }
}
