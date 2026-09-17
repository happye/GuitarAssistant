package com.guitarcoach.app.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 纯 Kotlin STFT/iSTFT（Spleeter 分离管线用，F602）。
 * 参数与模型 metadata 对齐：n_fft=4096、hop=1024、win=4096、hann、center=false。
 * 频点只取前 [BINS] 个（模型输入维度 1024，舍弃高频余量 bin）。
 * center=false：不 padding，帧数 = floor((N-win)/hop)+1。
 * FFT：迭代 radix-2 Cooley-Tukey（4096=2^12），整曲毫秒级。
 */
object Stft {

    const val N_FFT = 4096
    const val HOP = 1024
    const val WIN = 4096
    const val BINS = 1024

    class StftResult(val real: FloatArray, val imag: FloatArray, val numFrames: Int)

    private fun hann(): DoubleArray {
        // periodic hann（分母 N，与 kaldi-native-fbank/librosa 一致）
        val w = DoubleArray(WIN)
        for (i in 0 until WIN) w[i] = 0.5 - 0.5 * cos(2 * PI * i / WIN)
        return w
    }

    private val window by lazy { hann() }

    /** 就地 radix-2 FFT（N 为 2 的幂）。re/im 为复数数组。 */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        // 位反转重排
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j and bit.inv()
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wRe = cos(angle)
            val wIm = sin(angle)
            var start = 0
            while (start < n) {
                var curRe = 1.0
                var curIm = 0.0
                val half = len / 2
                for (k in 0 until half) {
                    val uRe = re[start + k]
                    val uIm = im[start + k]
                    val vRe = re[start + k + half] * curRe - im[start + k + half] * curIm
                    val vIm = re[start + k + half] * curIm + im[start + k + half] * curRe
                    re[start + k] = uRe + vRe
                    im[start + k] = uIm + vIm
                    re[start + k + half] = uRe - vRe
                    im[start + k + half] = uIm - vIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                start += len
            }
            len = len shl 1
        }
    }

    /** 就地逆 FFT。 */
    private fun ifft(re: DoubleArray, im: DoubleArray) {
        // 共轭法：IFFT(x) = conj(FFT(conj(x)))/N
        val n = re.size
        for (i in 0 until n) im[i] = -im[i]
        fft(re, im)
        for (i in 0 until n) {
            re[i] /= n
            im[i] = -im[i] / n
        }
    }

    fun numFramesFor(samples: Int): Int =
        if (samples < WIN) 0 else (samples - WIN) / HOP + 1

    /** 实信号 STFT → real/imag 扁平数组 [numFrames × BINS]。 */
    fun stft(samples: FloatArray): StftResult {
        val numFrames = numFramesFor(samples.size)
        val real = FloatArray(numFrames * BINS)
        val imag = FloatArray(numFrames * BINS)
        val re = DoubleArray(N_FFT)
        val im = DoubleArray(N_FFT)
        for (t in 0 until numFrames) {
            val off = t * HOP
            for (i in 0 until N_FFT) {
                val s = if (off + i < samples.size) samples[off + i].toDouble() else 0.0
                re[i] = s * window[i]
                im[i] = 0.0
            }
            fft(re, im)
            for (k in 0 until BINS) {
                real[t * BINS + k] = re[k].toFloat()
                imag[t * BINS + k] = im[k].toFloat()
            }
        }
        return StftResult(real, imag, numFrames)
    }

    /** iSTFT：mask 后的 real/imag → 时域样本（加权 overlap-add，hann² 归一）。 */
    fun istft(real: FloatArray, imag: FloatArray, numFrames: Int): FloatArray {
        val outLen = (numFrames - 1) * HOP + WIN
        val out = DoubleArray(outLen)
        val winSum = DoubleArray(outLen)
        val re = DoubleArray(N_FFT)
        val im = DoubleArray(N_FFT)
        for (t in 0 until numFrames) {
            val off = t * HOP
            for (k in 0 until BINS) {
                re[k] = real[t * BINS + k].toDouble()
                im[k] = imag[t * BINS + k].toDouble()
            }
            // 共轭对称补全后半频点（丢弃的高频 bins 置 0——能量集中低频段，音频影响可忽略）
            for (k in BINS + 1 until N_FFT) {
                re[k] = re[N_FFT - k]
                im[k] = -im[N_FFT - k]
            }
            ifft(re, im)
            for (i in 0 until N_FFT) {
                val w = window[i]
                out[off + i] += re[i] * w
                winSum[off + i] += w * w
            }
        }
        val outF = FloatArray(outLen)
        for (i in 0 until outLen) {
            outF[i] = if (winSum[i] > 1e-8) (out[i] / winSum[i]).toFloat() else 0f
        }
        return outF
    }

    /** 幅度辅助：供测试断言。 */
    fun magnitude(real: FloatArray, imag: FloatArray, frame: Int, bin: Int): Double {
        val r = real[frame * BINS + bin].toDouble()
        val i = imag[frame * BINS + bin].toDouble()
        return sqrt(r * r + i * i)
    }
}
