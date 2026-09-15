package com.guitarcoach.app.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * 节拍器（F105）：AudioTrack 流式写入合成拍点音。
 * 时基 = 音频硬件时钟（固定采样率流式写入），BPM 变更从下一拍生效，天然无漂移。
 */
class MetronomeEngine(private val scope: CoroutineScope) {

    private companion object {
        const val LOG_TAG = "GuitarCoach"
    }

    private var job: Job? = null

    @Volatile private var bpm: Int = 100
    @Volatile private var beatsPerBar: Int = 4
    @Volatile private var beatIndex: Long = 0

    val isRunning: Boolean get() = job?.isActive == true

    fun update(bpm: Int, beatsPerBar: Int) {
        require(bpm in 40..240) { "BPM 必须在 40-240" }
        this.bpm = bpm
        this.beatsPerBar = beatsPerBar
    }

    fun start() {
        if (isRunning) return
        val sampleRate = 44100
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, sampleRate * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        beatIndex = 0
        job = scope.launch(Dispatchers.IO) {
            try {
                track.play()
                while (isActive) {
                    val beatSamples = sampleRate * 60 / bpm
                    val accent = beatIndex % beatsPerBar == 0L
                    val click = generateClick(sampleRate, beatSamples, accent)
                    track.write(click, 0, click.size, AudioTrack.WRITE_BLOCKING)
                    beatIndex++
                }
            } catch (e: Exception) {
                // 音频设备异常（被占用/拔出等）不静默死亡，留可排查日志
                Log.e(LOG_TAG, "metronome loop error", e)
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun generateClick(sampleRate: Int, beatSamples: Int, accent: Boolean): ShortArray {
        val out = ShortArray(beatSamples)
        val clickLen = min(beatSamples, sampleRate * 60 / 1000) // 60ms 点击
        val freq = if (accent) 1200.0 else 800.0
        for (i in 0 until clickLen) {
            val envelope = exp(-i.toDouble() / (sampleRate * 0.008)) // ~8ms 衰减
            out[i] = (sin(2 * PI * freq * i / sampleRate) * envelope * Short.MAX_VALUE * 0.8).toInt().toShort()
        }
        return out
    }
}
