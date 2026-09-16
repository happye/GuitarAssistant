package com.guitarcoach.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F601 重采样口径：时长守恒（±1 帧）、单声道混缩、幅值不越界。 */
class PcmResamplerTest {

    @Test
    fun `同率单声道原样拷贝`() {
        val input = shortArrayOf(0, 100, -100, 500)
        val out = PcmResampler.toMonoRate(input, inputRate = 44100, channels = 1, targetRate = 44100)
        assertTrue(out.contentEquals(input))
    }

    @Test
    fun `立体声混缩为单声道`() {
        val input = shortArrayOf(100, 300, -200, 200)
        val out = PcmResampler.toMonoRate(input, inputRate = 44100, channels = 2, targetRate = 44100)
        assertEquals(2, out.size)
        assertEquals(200, out[0].toInt())
        assertEquals(0, out[1].toInt())
    }

    @Test
    fun `44100到22050时长减半且直流保持`() {
        // 1 秒恒定 1000 的 44.1k 信号 → 0.5 秒恒定 ~1000
        val input = ShortArray(44100) { 1000 }
        val out = PcmResampler.toMonoRate(input, inputRate = 44100, channels = 1, targetRate = 22050)
        assertEquals(22050, out.size)
        assertTrue(out.all { it.toInt() in 990..1010 })
    }

    @Test
    fun `升采样到16000时长守恒`() {
        val input = ShortArray(44100) { 0 } // 1 秒静音
        val out = PcmResampler.toMonoRate(input, inputRate = 44100, channels = 1, targetRate = 16000)
        assertEquals(16000, out.size)
    }

    @Test
    fun `正弦波重采样幅值不越界`() {
        val input = ShortArray(4410) { i -> (1000 * kotlin.math.sin(2.0 * Math.PI * 440 * i / 44100)).toInt().toShort() }
        val out = PcmResampler.toMonoRate(input, inputRate = 44100, channels = 1, targetRate = 16000)
        assertTrue(out.all { kotlin.math.abs(it.toInt()) <= 1000 })
        assertEquals(1600, out.size)
    }
}
