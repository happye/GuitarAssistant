package com.guitarcoach.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** 立体声吉他聚焦预处理：中央消除、带通、单声道降级路径。 */
class StereoFocusProcessorTest {

    private fun stereo(l: ShortArray, r: ShortArray): ShortArray {
        val out = ShortArray(l.size * 2)
        for (i in l.indices) {
            out[i * 2] = l[i]
            out[i * 2 + 1] = r[i]
        }
        return out
    }

    @Test
    fun `纯中央信号被按强度压低`() {
        val sr = 22050
        val n = 4410 // 0.2s
        // L = R = 200Hz 正弦（纯 mid，模拟居中人声/贝斯）
        val tone = ShortArray(n) { i -> (sin(2 * PI * 200 * i / sr) * 12000).toInt().toShort() }
        val proc = StereoFocusProcessor(sr, strength = 0.8)
        val out = proc.process(stereo(tone, tone), 2)

        var inPeak = 0.0
        var outPeak = 0.0
        for (i in 0 until n) {
            inPeak = maxOf(inPeak, kotlin.math.abs(tone[i].toDouble()))
            outPeak = maxOf(outPeak, kotlin.math.abs(out[i * 2].toDouble()))
        }
        // 带通 200Hz 在通带内不衰减；压低主要来自中央消除 (1-0.8)=0.2 倍（含滤波器暂态余量）
        assertTrue("in=$inPeak out=$outPeak", outPeak < inPeak * 0.4)
    }

    @Test
    fun `纯侧信号不受中央消除影响`() {
        val sr = 22050
        val n = 4410
        // L = x, R = -x（纯 side，模拟偏侧双轨吉他）
        val tone = ShortArray(n) { i -> (sin(2 * PI * 300 * i / sr) * 12000).toInt().toShort() }
        val proc = StereoFocusProcessor(sr, strength = 0.8)
        val out = proc.process(stereo(tone, ShortArray(n) { (-tone[it]).toShort() }), 2)

        var outPeak = 0.0
        for (i in 0 until n) outPeak = maxOf(outPeak, kotlin.math.abs(out[i * 2].toDouble()))
        // side 全保留（300Hz 在通带内），仅过滤暂态
        assertTrue("outPeak=$outPeak", outPeak > 6000)
    }

    @Test
    fun `带通衰减超频段`() {
        val sr = 22050
        val n = 8820
        val proc = StereoFocusProcessor(sr)
        // 5kHz（鼓镲高频区）应被 LPF 1600 显著衰减
        val hi = ShortArray(n) { i -> (sin(2 * PI * 5000 * i / sr) * 12000).toInt().toShort() }
        val out = proc.process(stereo(hi, hi), 2)
        var outPeak = 0.0
        for (i in 1000 until n) outPeak = maxOf(outPeak, kotlin.math.abs(out[i * 2].toDouble()))
        assertTrue("5kHz outPeak=$outPeak", outPeak < 3000)
    }

    @Test
    fun `单声道降级路径仅带通不崩`() {
        val proc = StereoFocusProcessor(22050)
        val mono = ShortArray(4410) { i -> (sin(2 * PI * 200 * i / 22050) * 12000).toShort() }
        val out = proc.process(mono, 1)
        assertEquals(mono.size, out.size)
    }
}
