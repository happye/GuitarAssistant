package com.guitarcoach.app.core.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import java.util.concurrent.locks.ReentrantLock

/**
 * F602 v2 端侧音源分离（Spleeter 2stems int8 ONNX，Apache-2.0，26MB×2 随 APK）：
 * 立体声波形 → STFT 幅度谱 [2, chunks, 512, 1024] → vocals/accompaniment 双模型 →
 * soft mask（平方比归一）→ mask×原谱 → iSTFT → 双 stem 时域输出。
 *
 * 算法忠实移植 sherpa-onnx csrc/offline-source-separation-spleeter-impl.h（Apache-2.0）；
 * STFT/iSTFT 用本项目纯 Kotlin 实现 [Stft]（参数从模型 metadata 对齐：44100/4096/1024/hann/center=false）。
 * 初版模型随 APK 打包（52.5MB），后续改下载制。
 *
 * 局限（诚实口径）：2stems 只有 vocals/accompaniment——吉他混在伴奏轨里；
 * 分离的价值 = 去掉人声与鼓两大转写干扰源，吉他仍与 bass/keys 混叠（DEBT-010）。
 */
class SpleeterSeparator(context: Context) : AutoCloseable {

    data class Result(val accompaniment: FloatArray, val vocals: FloatArray, val sampleRate: Int)

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val vocalsSession: OrtSession
    private val accompanimentSession: OrtSession

    companion object {
        const val SAMPLE_RATE = 44100 // 模型元数据：输出采样率
        const val N_FFT = 4096
        const val HOP = 1024
        const val CHUNK_FRAMES = 512
        const val BINS = 1024
        private const val EPS = 1e-10f
    }

    init {
        val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
        vocalsSession = env.createSession(loadBytes(context, "spleeter/vocals.int8.onnx"), opts)
        accompanimentSession = env.createSession(loadBytes(context, "spleeter/accompaniment.int8.onnx"), opts)
    }

    private fun loadBytes(context: Context, path: String): ByteArray =
        context.assets.open(path).use { it.readBytes() }

    /**
     * 分离主入口。输入立体声交错 PCM（44100Hz 由调用方重采样保证）；长度 pad 到 CHUNK_FRAMES 倍数。
     * 输出两个 stem（各声道已混缩为单声道交错？——输出为每 stem 的 (L+R)/2 单声道，长度 = 原始样本数）。
     */
    fun separate(interleavedStereo: FloatArray, inputSampleRate: Int): Result {
        // 输入重采样到 44100（模型域）
        val src = if (inputSampleRate != SAMPLE_RATE) {
            val mono = PcmResampler.toMonoRate(
                interleavedStereo.map { (it * 32767).toInt().toShort() }.toShortArray(),
                inputSampleRate, 1, SAMPLE_RATE,
            )
            FloatArray(mono.size) { mono[it] / 32768f }
        } else {
            interleavedStereo
        }

        // STFT（双声道各一）
        val half = src.size / 2
        val l = FloatArray(half) { src[it * 2] }
        val r = FloatArray(half) { src[it * 2 + 1] }
        val stftL = Stft.stft(l)
        val stftR = Stft.stft(r)

        // pad 帧数到 512 倍数
        var numFrames = stftL.numFrames
        val pad = (CHUNK_FRAMES - numFrames % CHUNK_FRAMES).let { if (it == CHUNK_FRAMES) 0 else it }
        if (pad > 0) numFrames += pad
        require(numFrames % CHUNK_FRAMES == 0)
        val chunks = numFrames / CHUNK_FRAMES

        // 幅度谱输入 [2, chunks, 512, 1024]（实部谱即幅度近似——sherpa 实现直接用 sqrt(real²+imag²) 填充）
        val x = FloatArray(2 * numFrames * BINS)
        copySpec(stftL, x, 0, numFrames, pad)
        copySpec(stftR, x, 1, numFrames, pad)

        val shape = longArrayOf(2, chunks.toLong(), CHUNK_FRAMES.toLong(), BINS.toLong())
        val xTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(x), shape)

        val vocalsSpec = FloatArray(x.size)
        val accompanimentSpec = FloatArray(x.size)

        val inputName = vocalsSession.inputNames.first()
        val outputName = vocalsSession.outputNames.first()
        vocalsSession.run(mapOf(inputName to xTensor)).use { res ->
            val t = res.get(0) as OnnxTensor
            t.floatBuffer?.let { fb -> fb.get(vocalsSpec, 0, minOf(fb.remaining(), vocalsSpec.size)) }
        }
        xTensor.close()
        val xTensor2 = OnnxTensor.createTensor(env, FloatBuffer.wrap(x), shape)
        val inputName2 = accompanimentSession.inputNames.first()
        val outputName2 = accompanimentSession.outputNames.first()
        accompanimentSession.run(mapOf(inputName2 to xTensor2)).use { res ->
            val t = res.get(0) as OnnxTensor
            t.floatBuffer?.let { fb -> fb.get(accompanimentSpec, 0, minOf(fb.remaining(), accompanimentSpec.size)) }
        }
        xTensor2.close()

        // soft mask（官方平方比归一）
        val vocalsMask = FloatArray(x.size)
        val accMask = FloatArray(x.size)
        for (i in x.indices) {
            val v = vocalsSpec[i] * vocalsSpec[i]
            val a = accompanimentSpec[i] * accompanimentSpec[i]
            val sum = v + a + EPS
            vocalsMask[i] = ((v + EPS / 2) / sum)
            accMask[i] = ((a + EPS / 2) / sum)
        }

        // mask × 原 STFT 复数谱 → iSTFT → 每 stem 单声道（双声道取均值）
        val accMono = istftStereoMean(accMask, stftL, stftR, numFrames, pad)
        val vocMono = istftStereoMean(vocalsMask, stftL, stftR, numFrames, pad)
        return Result(accMono, vocMono, SAMPLE_RATE)
    }

    private fun copySpec(stft: Stft.StftResult, x: FloatArray, channel: Int, numFrames: Int, pad: Int) {
        // sherpa 官方实现：模型输入是幅度谱 x = sqrt(real² + imag²)；pad 帧复制最后真实帧
        val chanOff = channel * numFrames * BINS
        for (t in 0 until numFrames) {
            val src = if (t < stft.numFrames) t else stft.numFrames - 1
            for (b in 0 until BINS) {
                val r = stft.real[src * BINS + b].toDouble()
                val i = stft.imag[src * BINS + b].toDouble()
                x[chanOff + t * BINS + b] = kotlin.math.sqrt(r * r + i * i).toFloat()
            }
        }
    }

    private fun istftStereoMean(mask: FloatArray, stftL: Stft.StftResult, stftR: Stft.StftResult, numFrames: Int, pad: Int): FloatArray {
        val maskedLReal = FloatArray(stftL.real.size)
        val maskedLImag = FloatArray(stftL.real.size)
        val maskedRReal = FloatArray(stftR.real.size)
        val maskedRImag = FloatArray(stftR.real.size)
        // mask 布局 [2][numFrames][1024]：L 段偏移 0、R 段偏移 numFrames*BINS；只回写真实帧
        val chanStride = numFrames * BINS
        for (t in 0 until stftL.numFrames) {
            for (b in 0 until BINS) {
                val m = mask[t * BINS + b]
                maskedLReal[t * BINS + b] = stftL.real[t * BINS + b] * m
                maskedLImag[t * BINS + b] = stftL.imag[t * BINS + b] * m
                val mR = mask[chanStride + t * BINS + b]
                maskedRReal[t * BINS + b] = stftR.real[t * BINS + b] * mR
                maskedRImag[t * BINS + b] = stftR.imag[t * BINS + b] * mR
            }
        }
        val outL = Stft.istft(maskedLReal, maskedLImag, stftL.numFrames)
        val outR = Stft.istft(maskedRReal, maskedRImag, stftR.numFrames)
        return FloatArray(minOf(outL.size, outR.size)) { (outL[it] + outR[it]) / 2f }
    }

    private fun flatten(arr: Array<*>, dst: FloatArray) {
        var idx = 0
        fun rec(a: Any?) {
            when (a) {
                is FloatArray -> {
                    for (v in a) if (idx < dst.size) dst[idx++] = v
                }
                is Array<*> -> for (e in a) rec(e)
            }
        }
        rec(arr)
    }

    private val lock = ReentrantLock()

    override fun close() {
        lock.lock()
        try {
            runCatching { vocalsSession.close() }
            runCatching { accompanimentSession.close() }
        } finally {
            lock.unlock()
        }
    }
}
