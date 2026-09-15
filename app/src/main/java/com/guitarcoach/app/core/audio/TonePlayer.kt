package com.guitarcoach.app.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.guitarcoach.app.core.music.midiToFreq
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 点按试听（F202）：单音短促合成（基频 + 2/3 次谐波 + 指数衰减，吉他感）。
 * 每次 play 独立 AudioTrack 短生命周期，播完即释放；重复点击会打断上一次。
 * 频率唯一来源是 TabDocument.midiToFreq，保证"听到的"与"换算的"一致。
 */
class TonePlayer {

    private companion object {
        const val LOG_TAG = "GuitarCoach"
        const val SAMPLE_RATE = 44100
    }

    @Volatile private var current: AudioTrack? = null

    fun play(midi: Int, durationMs: Long = 600L) {
        stop()
        val freq = midiToFreq(midi)
        val samples = (SAMPLE_RATE * durationMs / 1000).toInt()
        val pcm = ShortArray(samples)
        for (i in 0 until samples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-3.0 * i / samples) // 全长指数衰减
            val v = sin(2 * PI * freq * t) + 0.5 * sin(4 * PI * freq * t) + 0.25 * sin(6 * PI * freq * t)
            pcm[i] = (v / 1.75 * envelope * Short.MAX_VALUE * 0.6).toInt().toShort()
        }
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, samples * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        current = track
        Thread {
            try {
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(durationMs + 120)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "tone player error", e)
            } finally {
                runCatching { track.stop() }
                track.release()
                if (current === track) current = null
            }
        }.start()
    }

    fun stop() {
        current?.let { runCatching { it.pause(); it.flush() } }
        current = null
    }
}
