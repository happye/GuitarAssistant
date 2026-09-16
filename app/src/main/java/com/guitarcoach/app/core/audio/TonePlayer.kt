package com.guitarcoach.app.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 试听播放器（F202 用户反馈"听不了/太简陋"后重写）：
 * - STREAM 模式（先 play() 后阻塞写数据，最兼容；旧 STATIC 实现从不检查 write 返回值，写失败即无声且无日志）
 * - 单音 [play] 与整段 [playSequence] 共用 ToneRenderer 渲染路径，"听到的"与"换算的"同源
 * - 每次播放独占一个 AudioTrack，新播放自动打断旧的；播完自释放
 */
class TonePlayer {

    private companion object {
        const val LOG_TAG = "GuitarCoach"
        const val SAMPLE_RATE = 44100
    }

    @Volatile private var current: AudioTrack? = null
    @Volatile private var playbackId: Long = 0

    /** 播放进度（毫秒，从 0 计）；非播放态为 -1。整段播放的光标跟随用。 */
    private val _positionMs = MutableStateFlow(-1L)
    val positionMs: StateFlow<Long> = _positionMs

    /** 单音点按：600ms 短促试听。 */
    fun play(midi: Int, durationMs: Long = 600L) {
        playSequence(listOf(ToneRenderer.ToneEvent(0.0, midi, durationMs / 1000.0)))
    }

    /** 整段/整句播放：按时间表一次渲染混合播放。 */
    fun playSequence(events: List<ToneRenderer.ToneEvent>) {
        if (events.isEmpty()) return
        stop()
        val myId = ++playbackId
        // 渲染（混合合成）在后台线程：长谱 100s+ 音频的 DoubleArray 合成不能占 UI 线程（监督员 P2）
        Thread {
            val pcm = ToneRenderer.render(events, SAMPLE_RATE)
            if (playbackId != myId) return@Thread // 渲染期间被新播放/停止取代
            startTrack(pcm, myId)
        }.apply { priority = Thread.NORM_PRIORITY + 1 }.start()
    }

    private fun startTrack(pcm: ShortArray, myId: Long) {

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
            .setBufferSizeInBytes(maxOf(minBuf, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        current = track
        try {
            track.play() // 失败（未初始化等）抛 IllegalStateException，直接释放退出
        } catch (e: IllegalStateException) {
            Log.e(LOG_TAG, "AudioTrack.play() failed (state=${track.state})", e)
            track.release()
            if (current === track) current = null
            return
        }

        Thread {
            try {
                var offset = 0
                while (offset < pcm.size && playbackId == myId) {
                    val written = track.write(pcm, offset, minOf(pcm.size - offset, 16384))
                    if (written < 0) {
                        Log.e(LOG_TAG, "AudioTrack.write failed: $written")
                        break
                    }
                    offset += written
                    _positionMs.value = offset / 2 * 1000L / SAMPLE_RATE // 已写入≈已播放（阻塞写准同步）
                }
                if (playbackId == myId) Thread.sleep(120)
            } catch (e: IllegalStateException) {
                // stop() 已 release track，write 抛异常 = 被打断的正常路径（监督员 P1）
            } catch (e: InterruptedException) {
                // 被新播放打断，正常路径
            } finally {
                runCatching { track.stop() }
                track.release()
                if (current === track) current = null
            }
        }.apply { priority = Thread.NORM_PRIORITY + 1 }.start()
    }

    fun stop() {
        playbackId++
        _positionMs.value = -1L
        current?.let {
            runCatching { it.pause() }
            // release 让阻塞中的 write 立刻返回错误/抛异常，写线程得以走 finally 释放（监督员 P1：只 pause 会永久阻塞+泄漏）
            runCatching { it.release() }
        }
        current = null
    }
}
