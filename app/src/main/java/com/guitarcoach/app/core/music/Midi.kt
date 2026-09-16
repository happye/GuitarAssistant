package com.guitarcoach.app.core.music

import kotlin.math.pow

/** MIDI 音高 → 频率（Hz），A4=440 基准。tab（展示）与 audio（试听）共用，中立纯乐理。 */
fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

/** 频率（Hz）→ 最接近的 MIDI 音高；超出可听吉他范围或非法值返回 null。 */
fun midiFromFrequency(freq: Double): Int? {
    if (!freq.isFinite() || freq < 65.0 || freq > 1400.0) return null
    return kotlin.math.round(69.0 + 12.0 * kotlin.math.log2(freq / 440.0)).toInt()
}
