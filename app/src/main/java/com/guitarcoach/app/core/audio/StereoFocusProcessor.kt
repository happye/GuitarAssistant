package com.guitarcoach.app.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 立体声吉他聚焦预处理器（F602 转写增强，纯 DSP 零模型，JVM 可测）：
 *
 * 1. **中央消除**（mid/side 变换）：人声与贝斯在混音中通常居中（mid），双轨吉他惯例偏两侧（side）。
 *    输出 = (1-strength)·mid + side——居中成分按强度压低，偏侧吉他保留。
 *    局限（诚实口径）：居中录制的单轨吉他同样被压；中央消除 ≠ 乐器分离。
 * 2. **带通聚焦**：RBJ biquad 级联 HPF 80Hz + LPF 1600Hz——压鼓的高频瞬态与超低频，
 *    吉他基频（82Hz~1.3kHz）全通。
 *
 * 处理位置：MediaCodec 解码出立体声交错帧之后、StreamingResampler 混缩之前（混缩后再做就晚了）。
 */
class StereoFocusProcessor(sampleRate: Int, private val strength: Double = 0.8) {

    init {
        require(strength in 0.0..1.0) { "strength 须在 0..1" }
    }

    // RBJ biquad（direct form I，流式有状态）
    private class Biquad(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
        private val nb0 = b0 / a0
        private val nb1 = b1 / a0
        private val nb2 = b2 / a0
        private val na1 = a1 / a0
        private val na2 = a2 / a0
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(x: Double): Double {
            val y = nb0 * x + nb1 * x1 + nb2 * x2 - na1 * y1 - na2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }

        companion object {
            /** RBJ cookbook 高通。 */
            fun highpass(sampleRate: Int, freq: Double): Biquad {
                val w = 2 * PI * freq / sampleRate
                val cw = cos(w)
                val alpha = sin(w) / (2 * sqrt(2.0) / 2) // Q = 0.707
                val a = 1 / (1 + alpha)
                return Biquad(a, -2 * a * cw, a * (1 - alpha), 1 + alpha, -2 * cw, 1 - alpha)
            }

            /** RBJ cookbook 低通。 */
            fun lowpass(sampleRate: Int, freq: Double): Biquad {
                val w = 2 * PI * freq / sampleRate
                val cw = cos(w)
                val alpha = sin(w) / (2 * sqrt(2.0) / 2)
                val a = 1 / (1 + alpha)
                return Biquad(a * (1 - cw) / 2, a * (1 - cw), a * (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
            }
        }
    }

    private val hp = Biquad.highpass(sampleRate, 80.0)
    private val lp = Biquad.lowpass(sampleRate, 1600.0)

    /**
     * 处理一个立体声交错块（L R L R...），返回可交给重采样/混缩的交错块。
     * 单声道块（channels==1）无法做中央消除，仅过带通。
     */
    fun process(interleaved: ShortArray, channels: Int): ShortArray {
        if (channels < 2) return bandpassMono(interleaved)
        val out = ShortArray(interleaved.size)
        var i = 0
        while (i + 1 < interleaved.size) {
            val l = interleaved[i] / 32768.0
            val r = interleaved[i + 1] / 32768.0
            val mid = (l + r) / 2.0
            val side = (l - r) / 2.0
            // 中央压低 + side 全保留 → 单声道聚焦信号（相位问题在 mono 化时天然消解）
            val focused = (1.0 - strength) * mid + side
            val bp = lp.process(hp.process(focused))
            val v = (bp * 32767.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            out[i] = v
            out[i + 1] = v
            i += 2
        }
        return out
    }

    private fun bandpassMono(interleaved: ShortArray): ShortArray {
        val out = ShortArray(interleaved.size)
        for (i in interleaved.indices) {
            val bp = lp.process(hp.process(interleaved[i] / 32768.0))
            out[i] = (bp * 32767.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }
}
