package com.guitarcoach.app.core.audio

/**
 * F601 线性重采样（纯函数）：任意采样率/声道 → 目标采样率单声道 16bit PCM。
 * 扒谱管线（M6）用：转写模型需要固定采样率单声道输入。
 * 已知限制：线性插值降采样无抗混叠低通，16k 输出带镜像噪声——真机验收若转写质量差优先怀疑这里。
 */
object PcmResampler {

    /**
     * @param input 16bit PCM 交错采样（-32768..32767）
     * @param inputRate 原采样率
     * @param channels 原声道数（>1 时取各声道平均混缩为单声道）
     * @param targetRate 目标采样率（如 22050 / 16000）
     * @return 单声道 16bit PCM
     */
    fun toMonoRate(input: ShortArray, inputRate: Int, channels: Int, targetRate: Int): ShortArray {
        require(inputRate > 0 && targetRate > 0) { "采样率必须为正" }
        require(channels >= 1) { "声道数至少为 1" }
        if (inputRate == targetRate && channels == 1) return input.copyOf()

        // 1) 混缩单声道
        val mono = if (channels > 1) {
            val frames = input.size / channels
            ShortArray(frames) { f ->
                var sum = 0
                for (c in 0 until channels) sum += input[f * channels + c].toInt()
                (sum / channels).toShort()
            }
        } else {
            input
        }

        // 2) 线性插值重采样
        if (inputRate == targetRate) return mono
        val ratio = inputRate.toDouble() / targetRate
        val outFrames = (mono.size / ratio).toInt().coerceAtLeast(0)
        val out = ShortArray(outFrames)
        for (i in 0 until outFrames) {
            val src = i * ratio
            val i0 = src.toInt()
            val frac = src - i0
            val s0 = mono[i0].toInt()
            val s1 = mono[(i0 + 1).coerceAtMost(mono.size - 1)].toInt()
            out[i] = (s0 + (s1 - s0) * frac).roundToShortCompat()
        }
        return out
    }

    private fun Double.roundToShortCompat(): Short =
        kotlin.math.round(this).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}

/**
 * 流式重采样（F601 长音频修复）：解码块喂入 → 输出块回调直写文件，全程不驻留全曲 PCM。
 * 与 [toMonoRate] 同一套线性插值口径：跨块用上一块尾部样本衔接，输出全局对齐（无逐块相位漂移）。
 */
class StreamingResampler(val inputRate: Int, val channels: Int, private val targetRate: Int) {

    private val ratio = inputRate.toDouble() / targetRate
    private var inCount = 0L     // 已消费的单声道帧数
    private var outCount = 0L    // 已产出的输出帧数
    private var tailBuffer: DoubleArray = DoubleArray(0) // 上一块 mono（跨块插值可能用到倒数第 2 帧）

    fun feed(input: ShortArray, sink: (ShortArray) -> Unit) {
        require(channels >= 1)
        val mono = if (channels > 1) {
            val frames = input.size / channels
            DoubleArray(frames) { f ->
                var sum = 0
                for (c in 0 until channels) sum += input[f * channels + c].toInt()
                sum.toDouble() / channels
            }
        } else {
            DoubleArray(input.size) { input[it].toDouble() }
        }
        if (mono.isEmpty()) return // 空块不刷新 tailBuffer，防跨块插值拿 0 长尾（监督员 P2）
        val total = inCount + mono.size
        val outs = ShortArray((mono.size / ratio + 2).toInt().coerceAtLeast(0))
        var oi = 0
        var f = outCount * ratio
        while (f + 1 <= total - 1) {
            val i0 = f.toLong()
            val frac = f - i0
            val s0 = sampleAt(i0, mono)
            val s1 = sampleAt(i0 + 1, mono)
            outs[oi++] = (s0 + (s1 - s0) * frac).roundToShortCompat()
            outCount++
            f = outCount * ratio
        }
        if (oi > 0) sink(outs.copyOf(oi))
        tailBuffer = mono
        inCount = total

    }

    /** 全局帧索引取值：< inCount 落在上一块（尾对齐），否则在当前块。 */
    private fun sampleAt(globalIndex: Long, mono: DoubleArray): Double =
        if (globalIndex < inCount) {
            tailBuffer[(tailBuffer.size - (inCount - globalIndex)).toInt().coerceIn(0, tailBuffer.size - 1)]
        } else {
            mono[(globalIndex - inCount).toInt()]
        }

    private fun Double.roundToShortCompat(): Short =
        kotlin.math.round(this).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}
