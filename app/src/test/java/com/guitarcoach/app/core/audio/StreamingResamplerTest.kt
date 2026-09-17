package com.guitarcoach.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 流式重采样与一次性重采样口径一致（分块边界不漂移）。 */
class StreamingResamplerTest {

    private fun sine(rate: Int, seconds: Double, freq: Double = 220.0): ShortArray =
        ShortArray((rate * seconds).toInt()) { i -> (1000 * kotlin.math.sin(2.0 * Math.PI * freq * i / rate)).toInt().toShort() }

    @Test
    fun `分块喂入与一次性输出一致`() {
        val input = sine(44100, 2.0)
        val expected = PcmResampler.toMonoRate(input, 44100, 1, 16000)

        val r = StreamingResampler(44100, 1, 16000)
        val outs = mutableListOf<ShortArray>()
        var offset = 0
        while (offset < input.size) {
            val end = (offset + 4096).coerceAtMost(input.size)
            r.feed(input.copyOfRange(offset, end)) { outs += it }
            offset = end
        }
        val actual = outs.reduce { a, b -> a + b }

        // 长度一致（±1 帧），逐点误差 ≤ 1（浮点边界）
        assertTrue(kotlin.math.abs(actual.size - expected.size) <= 1)
        val n = minOf(actual.size, expected.size)
        for (i in 0 until n) {
            assertTrue("sample $i: ${actual[i]} vs ${expected[i]}", kotlin.math.abs(actual[i] - expected[i]) <= 1)
        }
    }

    @Test
    fun `立体声分块混缩降采样时长守恒`() {
        val input = ShortArray(44100 * 2) { 500 } // 1 秒立体声恒定值
        val r = StreamingResampler(44100, 2, 16000)
        val outs = mutableListOf<ShortArray>()
        var offset = 0
        while (offset < input.size) {
            val end = (offset + 1024).coerceAtMost(input.size)
            r.feed(input.copyOfRange(offset, end)) { outs += it }
            offset = end
        }
        val actual = outs.reduce { a, b -> a + b }
        assertEquals(16000, actual.size)
        assertTrue(actual.all { it.toInt() in 490..510 })
    }

    @Test
    fun `空块与短块不崩溃`() {
        val r = StreamingResampler(44100, 1, 16000)
        r.feed(ShortArray(0)) { }
        r.feed(shortArrayOf(100)) { }
        r.feed(ShortArray(1000) { 100 }) { }
    }
}

class ToneRendererLimitTest {
    @Test
    fun `超600秒音频拒绝渲染不巨量分配`() {
        val events = listOf(ToneRenderer.ToneEvent(0.0, 40, 601.0))
        val e = try {
            ToneRenderer.render(events, 44100)
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        org.junit.Assert.assertNotNull("应抛 IAE", e)
        org.junit.Assert.assertTrue(e!!.message!!.contains("时长超限"))
    }

    @Test
    fun `正常时长渲染不受影响`() {
        val pcm = ToneRenderer.render(listOf(ToneRenderer.ToneEvent(0.0, 69, 0.5)), 44100)
        org.junit.Assert.assertEquals(22050, pcm.size)
    }
}
