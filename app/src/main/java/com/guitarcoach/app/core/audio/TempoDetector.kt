package com.guitarcoach.app.core.audio

/**
 * BPM 自动检测（F706，扒谱闭环补全：用户不再手输 BPM）：能量包络 → onset 强度 → 自相关 → 最强周期。
 * 纯函数 JVM 可测。算法：512 样本 hop 的 RMS 包络 → 半波整流差分（onset strength）→
 * 对 60-250 BPM 对应 lag 区间做自相关，取峰 → 八度折叠到 70-180（可听主拍区间）。
 */
object TempoDetector {

    const val SAMPLE_RATE = 22050
    private const val HOP = 512

    fun detect(pcm: ShortArray, sampleRate: Int): Int? {
        require(sampleRate > 0)
        if (pcm.size < sampleRate * 3) return null // <3 秒不可靠

        // 1) 重采样到 22050 统一口径
        val mono = PcmResampler.toMonoRate(pcm, sampleRate, 1, SAMPLE_RATE)

        // 2) RMS 能量包络
        val frames = mono.size / HOP
        if (frames < 16) return null
        val envelope = DoubleArray(frames)
        for (f in 0 until frames) {
            var sum = 0.0
            for (i in 0 until HOP) {
                val v = mono[f * HOP + i].toDouble()
                sum += v * v
            }
            envelope[f] = kotlin.math.sqrt(sum / HOP)
        }

        // 3) onset 强度：半波整流差分
        val onset = DoubleArray(frames)
        for (f in 1 until frames) {
            val d = envelope[f] - envelope[f - 1]
            onset[f] = if (d > 0) d else 0.0
        }
        val mean = onset.drop(1).average()
        if (mean < 1e-6) return null // 近静音

        // 4) 自相关：lag 对应 BPM 区间 60-250
        val fps = SAMPLE_RATE.toDouble() / HOP
        val minLag = (fps * 60.0 / 250.0).toInt().coerceAtLeast(1)
        val maxLag = (fps * 60.0 / 60.0).toInt().coerceAtMost(frames / 2)
        if (maxLag <= minLag) return null

        var bestLag = -1
        var bestScore = -1.0
        for (lag in minLag..maxLag) {
            var sum = 0.0
            for (f in 1 until frames - lag) sum += onset[f] * onset[f + lag]
            // 稍偏置更慢 BPM（lag 大 = 周期长；慢侧更接近音乐主拍的感知先验）
            val score = sum * (1.0 + 0.08 * (lag - minLag) / (maxLag - minLag))
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag < 0) return null

        // 5) BPM + 八度折叠到 70-180
        var bpm = fps * 60.0 / bestLag
        while (bpm < 70) bpm *= 2
        while (bpm > 180) bpm /= 2
        return bpm.toInt().coerceIn(40, 240)
    }
}
