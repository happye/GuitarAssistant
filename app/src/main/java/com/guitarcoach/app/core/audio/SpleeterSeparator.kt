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
    /** 每块样本数：512 帧 × hop + 窗余量（STFT 需要 (512-1)*hop + win 个样本产 512 帧）。 */
    val CHUNK_SAMPLES = (CHUNK_FRAMES - 1) * HOP + N_FFT

    /**
     * 分离主入口（分块流式）：每块 = 512 帧 × hop 1024 = ~11.9s 音频独立 STFT→双模型→mask→iSTFT，
     * 峰值内存 ~50MB——整曲一次性处理的分配累计 700MB+ 必然 OOM 闪退（用户实测根修）。
     * 输入立体声交错 PCM（任意采样率，内部重采样到模型域 44100）。
     * onProgress 0..1 按块回调。
     */
    fun separate(
        interleavedStereo: FloatArray,
        inputSampleRate: Int,
        onProgress: (Float) -> Unit = {},
    ): Result {
        // 重采样到模型域 44100（立体声保持：L/R 各自独立重采样）
        val src: FloatArray = if (inputSampleRate != SAMPLE_RATE) {
            val frames = interleavedStereo.size / 2
            val l = FloatArray(frames) { interleavedStereo[it * 2] }
            val r = FloatArray(frames) { interleavedStereo[it * 2 + 1] }
            val lS = PcmResampler.toMonoRate(floatToShort(l), inputSampleRate, 1, SAMPLE_RATE)
            val rS = PcmResampler.toMonoRate(floatToShort(r), inputSampleRate, 1, SAMPLE_RATE)
            val n = minOf(lS.size, rS.size)
            FloatArray(n * 2) { i -> if (i % 2 == 0) lS[i / 2] / 32768f else rS[i / 2] / 32768f }
        } else {
            interleavedStereo
        }

        val half = src.size / 2
        val l = FloatArray(half) { src[it * 2] }
        val r = FloatArray(half) { src[it * 2 + 1] }

        val totalFrames = Stft.numFramesFor(half)
        if (totalFrames == 0) throw IllegalArgumentException("音频太短，无法分离")
        val chunks = (totalFrames + CHUNK_FRAMES - 1) / CHUNK_FRAMES

        val accMono = FloatArray(half)
        val vocMono = FloatArray(half)

        for (chunkIdx in 0 until chunks) {
            val frameStart = chunkIdx * CHUNK_FRAMES
            val frameCount = minOf(CHUNK_FRAMES, totalFrames - frameStart)
            val sampleStart = frameStart * HOP
            val sampleSpan = (frameCount - 1) * HOP + N_FFT

            val chunkL = FloatArray(sampleSpan) { i -> l.getOrElse(sampleStart + i) { 0f } }
            val chunkR = FloatArray(sampleSpan) { i -> r.getOrElse(sampleStart + i) { 0f } }
            val stftL = Stft.stft(chunkL)
            val stftR = Stft.stft(chunkR)
            val frames = stftL.numFrames.coerceAtMost(frameCount) // 尾块真实帧数可能少于 512

            // 幅度谱输入 [2, 1, 512, 1024]（pad 帧复制最后真实帧）
            val x = FloatArray(2 * CHUNK_FRAMES * BINS)
            copySpecPadded(stftL, x, 0, frames)
            copySpecPadded(stftR, x, 1, frames)
            val shape = longArrayOf(2, 1, CHUNK_FRAMES.toLong(), BINS.toLong())

            val xTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(x), shape)
            val vocalsSpec = FloatArray(x.size)
            val accSpec = FloatArray(x.size)
            val inputName = vocalsSession.inputNames.first()
            val outputName = vocalsSession.outputNames.first()
            vocalsSession.run(mapOf(inputName to xTensor)).use { res ->
                val t = res.get(0) as OnnxTensor
                t.floatBuffer?.let { fb -> fb.get(vocalsSpec, 0, minOf(fb.remaining(), vocalsSpec.size)) }
            }
            val inputName2 = accompanimentSession.inputNames.first()
            val outputName2 = accompanimentSession.outputNames.first()
            accompanimentSession.run(mapOf(inputName2 to xTensor)).use { res ->
                val t = res.get(0) as OnnxTensor
                t.floatBuffer?.let { fb -> fb.get(accSpec, 0, minOf(fb.remaining(), accSpec.size)) }
            }
            xTensor.close()

            // soft mask（官方平方比归一）
            val accMask = FloatArray(x.size)
            for (i in x.indices) {
                val v = vocalsSpec[i] * vocalsSpec[i]
                val a = accSpec[i] * accSpec[i]
                accMask[i] = ((a + EPS / 2) / (v + a + EPS))
            }

            // mask×复数谱 → iSTFT（只取本块前 frameCount*HOP 样本写输出，防尾部窗余量重复累加）
            val accChunk = istftMeanOfMask(accMask, stftL, stftR, frames)
            val vocMask = FloatArray(accMask.size) { 1f - accMask[it] }
            val vocChunk = istftMeanOfMask(vocMask, stftL, stftR, frames)
            val write = minOf(frameCount * HOP, accChunk.size)
            for (i in 0 until write) {
                val gi = sampleStart + i
                if (gi < half) {
                    accMono[gi] = accChunk[i]
                    vocMono[gi] = vocChunk[i]
                }
            }
            onProgress(((chunkIdx + 1).toFloat() / chunks).coerceIn(0f, 1f))
        }

        return Result(accMono, vocMono, SAMPLE_RATE)
    }

    private fun istftMeanOfMask(mask: FloatArray, stftL: Stft.StftResult, stftR: Stft.StftResult, frames: Int): FloatArray {
        val maskedLReal = FloatArray(stftL.real.size)
        val maskedLImag = FloatArray(stftL.real.size)
        val maskedRReal = FloatArray(stftR.real.size)
        val maskedRImag = FloatArray(stftR.real.size)
        val chanStride = CHUNK_FRAMES * BINS
        for (t in 0 until frames) {
            for (b in 0 until BINS) {
                val m = mask[t * BINS + b]
                maskedLReal[t * BINS + b] = stftL.real[t * BINS + b] * m
                maskedLImag[t * BINS + b] = stftL.imag[t * BINS + b] * m
                val mR = mask[chanStride + t * BINS + b]
                maskedRReal[t * BINS + b] = stftR.real[t * BINS + b] * mR
                maskedRImag[t * BINS + b] = stftR.imag[t * BINS + b] * mR
            }
        }
        val outL = Stft.istft(maskedLReal, maskedLImag, frames)
        val outR = Stft.istft(maskedRReal, maskedRImag, frames)
        return FloatArray(minOf(outL.size, outR.size)) { (outL[it] + outR[it]) / 2f }
    }

    private fun floatToShort(f: FloatArray): ShortArray =
        FloatArray(f.size) { f[it] }.let { arr ->
            ShortArray(arr.size) { i -> (arr[i] * 32767).toInt().coerceIn(-32768, 32767).toShort() }
        }

    private fun copySpecPadded(stft: Stft.StftResult, x: FloatArray, channel: Int, realFrames: Int) {
        // 模型输入是幅度谱 x = sqrt(real²+imag²)；块内帧数固定 512，pad 帧复制最后真实帧
        val chanOff = channel * CHUNK_FRAMES * BINS
        for (t in 0 until CHUNK_FRAMES) {
            val src = t.coerceAtMost(realFrames - 1)
            for (b in 0 until BINS) {
                val r = stft.real[src * BINS + b].toDouble()
                val i = stft.imag[src * BINS + b].toDouble()
                x[chanOff + t * BINS + b] = kotlin.math.sqrt(r * r + i * i).toFloat()
            }
        }
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
