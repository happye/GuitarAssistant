package com.guitarcoach.app.core.music

/**
 * 逐拍跟踪产物（中立时序模型）：core/audio BeatTracker 产出 → core/tab 量化消费。
 * 拍时刻来自音频实测（Ellis DP），曲内速度波动天然被吸收——旧链"固定 BPM 一次换算，
 * 误差随曲长线性累积"的根修（重构方案 §2.1，真机实证 BPM 129 vs 真实 132 漂移 5 拍/百拍）。
 */
class BeatGrid(
    val bpm: Double,
    val beatsPerBar: Int,
    /** 每拍实测时刻（秒），升序；首拍≈0（前空拍按周期回填）。 */
    val beatTimesSec: DoubleArray,
) {
    init {
        require(beatsPerBar in 2..6) { "beatsPerBar 越界: $beatsPerBar" }
        require(beatTimesSec.size >= 4) { "拍点过少: ${beatTimesSec.size}" }
        for (i in 1 until beatTimesSec.size) {
            require(beatTimesSec[i] > beatTimesSec[i - 1]) { "拍时刻必须严格递增" }
        }
    }

    val beatCount: Int get() = beatTimesSec.size

    /** 局部拍时长（秒）：第 i 拍与下一拍的实测间隔（末拍用前一间隔）。 */
    fun localPeriodSec(i: Int): Double {
        val idx = i.coerceIn(0, beatTimesSec.size - 2)
        return beatTimesSec[idx + 1] - beatTimesSec[idx]
    }

    /**
     * 音符时刻 → 全局拍位（分数，0 起）。区间内相邻实测拍线性插值；
     * 越界按最近局部周期外推（固定周期近似，仅首尾极短延伸）。
     */
    fun locate(timeSec: Double): Double {
        require(timeSec >= 0) { "timeSec 必须 ≥ 0" }
        val t = beatTimesSec
        if (timeSec <= t[0]) return (timeSec - t[0]) / localPeriodSec(0)
        if (timeSec >= t[t.size - 1]) {
            return (t.size - 1) + (timeSec - t[t.size - 1]) / localPeriodSec(t.size - 1)
        }
        var lo = 0
        var hi = t.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (t[mid] <= timeSec) lo = mid else hi = mid
        }
        return lo + (timeSec - t[lo]) / (t[hi] - t[lo])
    }

    override fun equals(other: Any?): Boolean = other is BeatGrid &&
        bpm == other.bpm && beatsPerBar == other.beatsPerBar && beatTimesSec.contentEquals(other.beatTimesSec)

    override fun hashCode(): Int = bpm.hashCode() * 31 + beatsPerBar * 7 + beatTimesSec.contentHashCode()

    companion object {
        /** 恒速网格（手输 BPM / 回退路径）：从 t=0 按固定拍时长铺满 durationSec。 */
        fun constant(bpm: Int, durationSec: Double): BeatGrid {
            val secPerBeat = 60.0 / bpm
            val count = (durationSec / secPerBeat).toInt().coerceAtLeast(4)
            return BeatGrid(bpm.toDouble(), 4, DoubleArray(count) { it * secPerBeat })
        }
    }
}
