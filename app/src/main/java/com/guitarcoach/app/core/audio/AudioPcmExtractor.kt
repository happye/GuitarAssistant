package com.guitarcoach.app.core.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.FileDescriptor
import java.io.OutputStream

/**
 * F601：音/视频文件 → 抽音频轨 → MediaCodec 解码 PCM → 流式重采样 → 目标采样率单声道。
 * 只用平台 API（MediaExtractor/MediaCodec），不引入 FFmpeg-kit（已退役）。
 *
 * 长音频修复（用户反馈）：解码块 → [StreamingResampler] 增量重采样 → 直写输出流，
 * 全程不驻留全曲 PCM（旧实现全量拼接在 5 分钟曲目上逼近堆上限）。
 * 时长上限 30 分钟（16k/16bit 单声道 ≈ 57MB 缓存文件）；解码在调用方线程执行（UI 侧放 Dispatchers.IO）。
 */
class AudioPcmExtractor {

    data class Result(val durationSeconds: Double, val sampleRate: Int, val bytes: Long)
    data class MonoResult(val pcm: ShortArray, val sampleRate: Int) {
        val durationSeconds: Double get() = if (sampleRate == 0) 0.0 else pcm.size.toDouble() / sampleRate
    }

    companion object {
        const val MAX_SECONDS = 30L * 60        // 落盘路径上限
        const val MAX_SECONDS_IN_MEMORY = 10L * 60 // 内存路径（转写）上限
    }

    /** 内存版（F602 转写路径）：抽+重采样到目标率。上限 10 分钟在解码循环内即检查（对抗审查 P1：
     *  旧版全量解码完才查——10-30 分钟素材先驻留 ~240MB 再被拒，OOM 而非友好报错）。 */
    fun extractToMono(fd: FileDescriptor, targetRate: Int = 22050): MonoResult {
        val bytes = java.io.ByteArrayOutputStream(1 shl 20)
        return extractToMono(fd, bytes, targetRate, maxSeconds = MAX_SECONDS_IN_MEMORY).let { r ->
            val data = bytes.toByteArray()
            val pcm = ShortArray(data.size / 2)
            var i = 0
            var off = 0
            while (i < pcm.size) {
                pcm[i] = ((data[off].toInt() and 0xFF) or (data[off + 1].toInt() shl 8)).toShort()
                i++
                off += 2
            }
            MonoResult(pcm, r.sampleRate)
        }
    }

    fun extractToMono(fd: FileDescriptor, output: OutputStream, targetRate: Int = 16000, maxSeconds: Long = MAX_SECONDS): Result {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(fd)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrack = i
                    format = f
                    break
                }
            }
            if (audioTrack < 0 || format == null) {
                throw IllegalArgumentException("文件里没有音频轨")
            }
            extractor.selectTrack(audioTrack)
            val inputMime = format.getString(MediaFormat.KEY_MIME)!!
            val codec = MediaCodec.createDecoderByType(inputMime)
            codec.configure(format, null, null, 0)
            codec.start()

            var inputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var inputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val resampler = StreamingResampler(inputSampleRate, inputChannels, targetRate)

            val scratch = ByteArray(64 * 1024)
            var totalOutBytes = 0L
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            try {
                while (!sawOutputEos) {
                    if (!sawInputEos) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIdx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIdx)!!
                            val shortBuf = outBuf.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val samples = ShortArray(shortBuf.remaining())
                            shortBuf.get(samples)
                            resampler.feed(samples) { outChunk ->
                                // ShortArray → LE bytes → 直写文件（复用 scratch，避免每块分配）
                                var i = 0
                                val n = outChunk.size
                                while (i < n) {
                                    val write = minOf(n - i, scratch.size / 2)
                                    var j = 0
                                    for (k in i until i + write) {
                                        scratch[j++] = (outChunk[k].toInt() and 0xFF).toByte()
                                        scratch[j++] = ((outChunk[k].toInt() shr 8) and 0xFF).toByte()
                                    }
                                    output.write(scratch, 0, j)
                                    totalOutBytes += j
                                    i += write
                                }
                            }
                            val seconds = totalOutBytes / 2.0 / targetRate
                            if (seconds > maxSeconds) {
                                throw IllegalArgumentException("文件太长（超过 ${maxSeconds / 60} 分钟），请截取后再试")
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val of = codec.outputFormat
                            inputSampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            inputChannels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            // 输出格式中途变化的文件（罕见）：v1 直接报错而非静默拼接错数据
                            if (inputSampleRate != resampler.inputRate || inputChannels != resampler.channels) {
                                throw IllegalArgumentException("该文件的音频格式在中途变化，暂不支持")
                            }
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }

            val duration = totalOutBytes / 2.0 / targetRate
            Log.i("GuitarCoach", "extract done: ${"%.1f".format(duration)}s @ $targetRate Hz, $totalOutBytes bytes")
            return Result(duration, targetRate, totalOutBytes)
        } finally {
            extractor.release()
        }
    }
}
