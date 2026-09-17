package com.guitarcoach.app.core.audio

import com.guitarcoach.app.core.music.midiToFreq
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 试听序列渲染（纯函数，JVM 可测）：音符时间表 → 一段混合 PCM。
 * 每音 = 基频 + 2/3 次谐波 + 指数衰减包络，按时间偏移叠加进总缓冲，最后峰值归一化防削波。
 * "播放整段"（F202 试听升级）与单音点按共用此渲染路径。
 */
object ToneRenderer {

    const val MAX_TOTAL_SECONDS = 600.0 // 10 分钟：病态数据防爆炸上限（正常谱远低于此）

    data class ToneEvent(val timeSec: Double, val midi: Int, val durationSec: Double = 0.6)

    fun render(events: List<ToneEvent>, sampleRate: Int = 44100): ShortArray {
        if (events.isEmpty()) return ShortArray(0)
        val total = events.maxOf { it.timeSec + it.durationSec }
        // 采样数上限（对抗审查 P1：病态长行谱把 total 撑爆 → 巨量分配/Int 溢出，裸线程内未捕获即崩）
        require(total <= MAX_TOTAL_SECONDS) { "音频时长超限（%.0fs > %ds），请检查谱面数据".format(total, MAX_TOTAL_SECONDS.toLong()) }
        val samples = (total * sampleRate).toInt().coerceAtLeast(1)
        val mix = DoubleArray(samples)

        for (ev in events) {
            val freq = midiToFreq(ev.midi)
            if (!freq.isFinite() || freq <= 0) continue
            val start = (ev.timeSec * sampleRate).toInt().coerceIn(0, samples - 1)
            val len = (ev.durationSec * sampleRate).toInt().coerceAtMost(samples - start)
            for (i in 0 until len) {
                val t = i.toDouble() / sampleRate
                val envelope = exp(-3.5 * i / len.coerceAtLeast(1))
                val v = sin(2 * PI * freq * t) + 0.5 * sin(4 * PI * freq * t) + 0.25 * sin(6 * PI * freq * t)
                mix[start + i] += v / 1.75 * envelope * 0.6
            }
        }

        val peak = mix.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6)
        val gain = if (peak > 1.0) 0.92 / peak else 1.0 // 多音叠加防削波
        return ShortArray(samples) { i -> (mix[i] * gain * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
    }
}
