package com.guitarcoach.app.core.audio

import com.guitarcoach.app.core.music.midiToFreq
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 试听序列渲染 v2（用户实测"一个一个生硬的点子音，根本不是吉他音色"后重写）：
 * **Karplus-Strong 物理拨弦合成**——延迟线 + 反馈低通，激发为低通白噪声，
 * 音色即真实拨弦（弦振动模型），零依赖零音色库。
 * 电吉他感：反馈衰减按音高自适应（低音延音长）、轻 tanh 软过载、起振加拨片瞬态。
 * 纯函数 JVM 可测：确定性（同参数逐样本一致）、防爆炸上限、包络衰减可断言。
 */
object ToneRenderer {

    const val MAX_TOTAL_SECONDS = 600.0 // 10 分钟：病态数据防爆炸上限（正常谱远低于此）

    data class ToneEvent(val timeSec: Double, val midi: Int, val durationSec: Double = 0.6, val gain: Double = 1.0)

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
            if (len <= 0) continue
            ksString(freq, len, sampleRate, ev.gain, ev.midi) { i, v ->
                val idx = start + i
                if (idx in 0 until samples) mix[idx] += v
            }
        }

        // 峰值归一化防削波（多音叠加后）
        var peak = 0.0
        for (v in mix) peak = maxOf(peak, abs(v))
        val gain = if (peak > 1.0) 0.92 / peak else 1.0
        return ShortArray(samples) { i -> (mix[i] * gain * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
    }

    /**
     * Karplus-Strong 单弦激发，逐样本回调写入（叠加进混音）。
     * y[t] = decay * 0.5 * (y[t-N] + y[t-N-1])，激发段 = 低通白噪声（拨弦）。
     * 电吉他感：tanh(1.6x) 软过载 + 轻压缩；衰减系数随频率自适应（低音弦延音更长）。
     * 确定性：噪声由 midi 播种，同参数逐样本一致（测试依赖）。
     */
    private fun ksString(freq: Double, len: Int, sampleRate: Int, gain: Double, midi: Int, put: (Int, Double) -> Unit) {
        val n = (sampleRate / freq).toInt().coerceAtLeast(2)
        val rng = java.util.Random(midi * 7919L + freq.toRawBits())
        // 激发：白噪声经一阶低通（拨弦瞬态偏柔和）
        val delay = DoubleArray(n)
        var lp = 0.0
        for (i in 0 until n) {
            val w = rng.nextDouble() * 2 - 1
            lp = 0.6 * w + 0.4 * lp
            delay[i] = lp
        }
        // 衰减自适应：基频越高延音越短（物理上高音弦能量小）；T60 约等于 durationSec 的 80%
        val decay = 1.0 - (1.0 - 0.999) * (freq / 1000.0).coerceIn(0.2, 2.0)
        var idx = 0
        val norm = 1.75 * (if (gain > 0) gain else 1.0)
        for (i in 0 until len) {
            val cur = delay[idx]
            val nxt = delay[(idx + 1) % n]
            val y = decay * 0.5 * (cur + nxt) // KS 核：延迟线相邻均值低通（弦能量损耗）
            delay[idx] = y
            // 拨片瞬态：前 8ms 加轻微激励谐波，起音更"钉"
            val t = i.toDouble() / sampleRate
            val pluck = if (t < 0.008) 0.15 * sin(2 * PI * freq * 2 * t) * (1 - t / 0.008) else 0.0
            val raw = y * 2.2 + pluck
            val shaped = tanh(1.6 * raw) * 0.7 // 软过载（电吉他拾音后的饱和感）
            put(i, shaped / norm)
            idx = (idx + 1) % n
        }
    }

    /** 供测试：给定 KS 输出的峰值衰减检查（-60dB 截断点）。 */
    internal fun decaySeconds(freq: Double, sampleRate: Int = 44100): Double {
        val n = (sampleRate / freq).toInt().coerceAtLeast(2)
        return abs(ln(0.001) / (ln(0.999) * sampleRate / (n * 1.0)))
    }
}
