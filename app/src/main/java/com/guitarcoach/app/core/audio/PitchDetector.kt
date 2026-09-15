package com.guitarcoach.app.core.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

data class PitchResult(val frequency: Double, val clarity: Double)

/**
 * McLeod Pitch Method（MPM）音高检测 —— 调音器与后续 bend/vibrato 曲线追踪的基础算法。
 * 参考：Phil McLeod & Geoff Wyvill, "A Better Way to Tune the Guitar" (2009)。
 *
 * 实现为朴素 O(n²) 差分函数：输入 4096 点 @44.1kHz 时单次约 800 万次乘加，
 * 近十年的手机 CPU 完全可负担（每 ~46ms 分析一帧）。M4 阶段如需更低功耗可换 FFT 实现。
 */
object PitchDetector {

    private const val CLARITY_THRESHOLD = 0.85

    /** 电吉他常用音域（低音空弦 E2 附近 ~ 22 品高音区），范围外直接丢弃以抗噪。 */
    private const val MIN_HZ = 60.0
    private const val MAX_HZ = 1400.0

    fun detect(buffer: FloatArray, sampleRate: Int): PitchResult? {
        val n = buffer.size
        val maxTau = n / 2
        val nsdf = DoubleArray(maxTau)

        for (tau in 1 until maxTau) {
            var acf = 0.0
            var div = 0.0
            for (i in 0 until n - tau) {
                val a = buffer[i].toDouble()
                val b = buffer[i + tau].toDouble()
                acf += a * b
                div += a * a + b * b
            }
            nsdf[tau] = if (div > 0.0) 2.0 * acf / div else 0.0
        }

        // MPM 关键规则：取「第一个」高于阈值的峰，避免倍频（八度）错误
        var tauEst = -1
        for (tau in 2 until maxTau - 1) {
            val v = nsdf[tau]
            if (v > CLARITY_THRESHOLD && v >= nsdf[tau - 1] && v > nsdf[tau + 1]) {
                tauEst = tau
                break
            }
        }
        if (tauEst < 0) return null

        // 抛物线插值细化峰位，提升低频弦的音分精度
        val y1 = nsdf[tauEst - 1]
        val y2 = nsdf[tauEst]
        val y3 = nsdf[tauEst + 1]
        val denom = y1 - 2 * y2 + y3
        val shift = if (abs(denom) > 1e-12) 0.5 * (y1 - y3) / denom else 0.0
        val freq = sampleRate / (tauEst + shift)

        if (freq < MIN_HZ || freq > MAX_HZ) return null
        return PitchResult(frequency = freq, clarity = y2)
    }

    fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    fun freqToMidi(frequency: Double): Int =
        (69 + 12 * (ln(frequency / 440.0) / ln(2.0))).roundToInt()

    /** 实测频率相对标准音的音分差（负 = 偏低，正 = 偏高）。 */
    fun centsOff(frequency: Double, midi: Int): Double =
        1200.0 * (ln(frequency / midiToFreq(midi)) / ln(2.0))
}
