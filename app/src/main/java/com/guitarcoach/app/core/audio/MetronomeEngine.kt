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
    @Volatile private var subdivision: Int = 1 // F705：每拍细分 1/2/3/4
    @Volatile private var accentPattern: String = "x" // F705：重音 pattern（按小节拍循环，x=重音）
    @Volatile private var countIn: Boolean = false // F705：预备拍（1 小节高音计数）

    val isRunning: Boolean get() = job?.isActive == true

    fun update(bpm: Int, beatsPerBar: Int, subdivision: Int = this.subdivision, accentPattern: String? = null, countIn: Boolean = this.countIn) {
        require(bpm in 40..240) { "BPM 必须在 40-240" }
        require(subdivision in 1..4) { "细分 1-4" }
        this.bpm = bpm
        this.beatsPerBar = beatsPerBar
        this.subdivision = subdivision
        accentPattern?.let { if (it.isNotBlank()) this.accentPattern = it }
        this.countIn = countIn
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
                if (countIn) {
                    // F705 预备拍：1 小节 1500Hz 高音计数，与正拍无缝衔接（同 track 顺序写）
                    val beatSamples = sampleRate * 60 / bpm
                    repeat(beatsPerBar) {
                        val click = generateClick(sampleRate, beatSamples, accent = true, freq = 1500.0, gain = 0.5)
                        track.write(click, 0, click.size, AudioTrack.WRITE_BLOCKING)
                    }
                }
                while (isActive) {
                    val beatSamples = sampleRate * 60 / bpm
                    val sub = subdivision.coerceAtLeast(1)
                    val pattern = accentPattern
                    val accent = pattern[if (pattern.length == 1) 0 else (beatIndex % pattern.length).toInt()] == 'x'
                    for (s in 0 until sub) {
                        val subSamples = beatSamples / sub
                        val click = if (s == 0) {
                            generateClick(sampleRate, subSamples, accent)
                        } else {
                            generateClick(sampleRate, subSamples, accent = false, gain = 0.35) // 弱子拍
                        }
                        track.write(click, 0, click.size, AudioTrack.WRITE_BLOCKING)
                    }
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

    private fun generateClick(sampleRate: Int, beatSamples: Int, accent: Boolean, freq: Double = if (accent) 1200.0 else 800.0, gain: Double = 0.8): ShortArray {
        val out = ShortArray(beatSamples)
        val clickLen = min(beatSamples, sampleRate * 60 / 1000) // 60ms 点击
        for (i in 0 until clickLen) {
            val envelope = exp(-i.toDouble() / (sampleRate * 0.008)) // ~8ms 衰减
            out[i] = (sin(2 * PI * freq * i / sampleRate) * envelope * Short.MAX_VALUE * gain).toInt().toShort()
        }
        return out
    }
}
