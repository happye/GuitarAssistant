package com.guitarcoach.app.core.audio

import com.guitarcoach.app.core.music.BeatGrid
import kotlin.math.exp
import kotlin.math.ln

/**
 * 逐拍跟踪（Ellis 2007 DP 算法的纯 Kotlin 实现，重构方案 §2.1）：
 * onset 包络 + BPM 先验周期 + 动态规划全局最优拍序列 → 回溯得每拍实测时刻。
 *
 * 根修目标：旧链用固定 BPM 一次换算（timeSec/secPerBeat），BPM 误差随曲长线性累积；
 * 本算法每个音的拍位由其左右两侧的实测拍插值决定，BPM 只作 DP 先验不作换算基准。
 *
 * 转移代价 = -TIGHTNESS·ln²(Δt/period)（Ellis 口径，允许每拍 ±5% 级局部偏差、容忍渐进漂移）；
 * 相位锚定 t=0（扒谱口径：第 1 小节起于 0）。
 */
object BeatTracker {

    private const val TIGHTNESS = 100.0 // Ellis 默认：每拍间隔对先验周期的对数偏差惩罚
    private val BAR_LENGTHS = intArrayOf(2, 3, 4, 6)

    fun track(pcm: ShortArray, sampleRate: Int): BeatGrid? {
        val bpmPrior = TempoDetector.detect(pcm, sampleRate) ?: return null
        val mono = PcmResampler.toMonoRate(pcm, sampleRate, 1, TempoDetector.SAMPLE_RATE)
        val onset = TempoDetector.onsetEnvelope(mono, smooth = true)
        val frames = onset.size
        if (frames < 16) return null

        // 归一化（95 分位），DP 分数与惩罚同量纲
        val sorted = onset.sorted()
        val p95 = sorted[(sorted.size * 0.95).toInt().coerceIn(0, sorted.size - 1)].coerceAtLeast(1e-9)
        val o = DoubleArray(frames) { (onset[it] / p95).coerceAtMost(2.0) }

        val fps = TempoDetector.SAMPLE_RATE.toDouble() / TempoDetector.HOP
        val period = 60.0 / bpmPrior * fps // 先验拍周期（帧）

        // DP：cumscore[i] = o[i] + max(锚点项, max_j cumscore[j] - T·ln²((i-j)/P))
        val cumscore = DoubleArray(frames)
        val back = IntArray(frames) { Int.MIN_VALUE } // -1 = t=0 锚点
        val lo = (period * 0.5).toInt().coerceAtLeast(1)
        val hi = (period * 2.0).toInt()
        var globalBest = 0
        for (i in 0 until frames) {
            var best = 0.0
            var bestJ = Int.MIN_VALUE
            if (i > 0) {
                // t=0 锚点（首拍允许从 0 起链，扒谱口径第 1 小节对齐 0）
                val anchor = -TIGHTNESS * ln((i + 1).toDouble() / period) * ln((i + 1).toDouble() / period)
                best = anchor
                bestJ = -1
            }
            val jMin = (i - hi).coerceAtLeast(0)
            val jMax = (i - lo).coerceAtMost(i - 1)
            for (j in jMin..jMax) {
                val d = (i - j).toDouble() / period
                val score = cumscore[j] - TIGHTNESS * ln(d) * ln(d)
                if (score > best) {
                    best = score
                    bestJ = j
                }
            }
            cumscore[i] = o[i] + best
            back[i] = bestJ
            if (i >= frames - period.toInt().coerceAtLeast(1) && cumscore[i] > cumscore[globalBest]) {
                globalBest = i // 末尾一个周期内取最强拍作回溯起点
            }
        }

        // 回溯（链从最强末拍回到锚点）
        val chain = ArrayDeque<Int>()
        var cur = globalBest
        while (cur >= 0 && chain.size < frames) {
            chain.addFirst(cur)
            cur = back[cur]
        }
        if (chain.isEmpty()) return null

        // 帧时刻 → 秒；按周期向前回填至 [0, P)
        val periodSec = 60.0 / bpmPrior
        var firstSec = chain.first() / fps
        while (firstSec > periodSec) firstSec -= periodSec
        val beats = DoubleArray(chain.size + 1)
        beats[0] = 0.0
        for (k in chain.indices) beats[k + 1] = chain[k] / fps

        val (barLen, _) = inferMeter(beats, o, fps)
        return BeatGrid(bpmPrior.toDouble(), barLen, beats)
    }

    /** 强拍周期+相位：拍同步 onset 在 lag∈{2,3,4,6} 上自相关取最优（无强拍差异时默认 4/4 相位 0）。 */
    private fun inferMeter(beats: DoubleArray, onsetNorm: DoubleArray, fps: Double): Pair<Int, Double> {
        val n = beats.size
        val strength = DoubleArray(n) { k ->
            val lo = ((beats[k] - 0.25 * (if (k < n - 1) beats[k + 1] - beats[k] else 0.5)) * fps)
                .toInt().coerceIn(0, onsetNorm.size - 1)
            val hi = ((beats[k] + 0.25 * (if (k < n - 1) beats[k + 1] - beats[k] else 0.5)) * fps)
                .toInt().coerceIn(lo, onsetNorm.size - 1)
            var m = 0.0
            for (f in lo..hi) m = maxOf(m, onsetNorm[f])
            m
        }
        var bestLen = 4
        var bestPhase = 0.0
        var bestScore = -1.0
        for (len in BAR_LENGTHS) {
            if (n / len < 4) continue
            for (phase in 0 until len) {
                var sum = 0.0
                var count = 0
                var k = phase
                while (k < n) {
                    sum += strength[k]
                    count++
                    k += len
                }
                val score = sum / count // 均值口径：长度不同的 lag 公平比较
                if (score > bestScore) {
                    bestScore = score
                    bestLen = len
                    bestPhase = phase.toDouble()
                }
            }
        }
        return bestLen to bestPhase
    }
}
