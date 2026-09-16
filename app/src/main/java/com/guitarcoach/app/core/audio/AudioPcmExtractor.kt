package com.guitarcoach.app.core.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.FileDescriptor

/**
 * F601：音/视频文件 → 抽音频轨 → MediaCodec 解码 PCM → 重采样到目标采样率单声道。
 * 只用平台 API（MediaExtractor/MediaCodec），不引入 FFmpeg-kit（已退役）。
 * 解码在调用方线程执行（UI 侧放 Dispatchers.IO）；mp3/m4a/mp4 由系统解码器支持。
 */
class AudioPcmExtractor {

    data class PcmData(val pcm: ShortArray, val sampleRate: Int) {
        val durationSeconds: Double get() = if (sampleRate == 0) 0.0 else pcm.size.toDouble() / sampleRate
    }

    fun extractToMono(fd: FileDescriptor, targetRate: Int = 16000): PcmData {
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

            val raw = ArrayList<ShortArray>(256) // 按块累积原生数组，避免 Short 装箱 OOM（监督员 P1）
            var totalSamples = 0
            var inputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var inputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
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
                            raw += samples
                            totalSamples += samples.size
                            if (totalSamples > 10L * 60 * 48000) { // >10 分钟（按 48k 折算）直接拒绝
                                throw IllegalArgumentException("文件太长（超过 10 分钟），扒谱 v1 只支持短素材")
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val of = codec.outputFormat
                            inputSampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            inputChannels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }

            val all = ShortArray(totalSamples)
            var offset = 0
            raw.forEach { chunk ->
                System.arraycopy(chunk, 0, all, offset, chunk.size)
                offset += chunk.size
            }
            val pcm = PcmResampler.toMonoRate(all, inputSampleRate, inputChannels, targetRate)
            Log.i("GuitarCoach", "extract done: ${pcm.size} samples @ $targetRate Hz")
            return PcmData(pcm, targetRate)
        } finally {
            extractor.release()
        }
    }
}
