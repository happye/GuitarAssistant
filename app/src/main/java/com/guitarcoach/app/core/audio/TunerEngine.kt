package com.guitarcoach.app.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.guitarcoach.app.core.music.Tunings
import com.guitarcoach.app.core.tab.midiToName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

data class TunerReading(
    val frequency: Double,
    val noteName: String,
    val midi: Int,
    val cents: Double,   // 负 = 偏低，正 = 偏高
    val stringHint: Int, // 当前调弦下最接近的弦（1~6）
    val tuningName: String,
)

/** 调音器：麦克风采样 → MPM 测频 → 与标准音对比。调用方需先取得 RECORD_AUDIO 权限。 */
class TunerEngine(private val scope: CoroutineScope) {

    private val _reading = MutableStateFlow<TunerReading?>(null)
    val reading: StateFlow<TunerReading?> = _reading

    /** 当前调弦（F704）：运行中可切换，下一帧生效。 */
    @Volatile var tuning: Tunings.Tuning = Tunings.STANDARD

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        job = scope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val frameSize = 4096
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT
            )
            // 麦克风被占用（通话/其它 App 录音）时 build 抛异常或 startRecording 静默失败——
            // 不设防则异常逃逸崩溃或 n<frameSize 空转打满 CPU（对抗审查 P1）
            val record = try {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf, frameSize * 4))
                    .build()
            } catch (e: Exception) {
                android.util.Log.e("GuitarCoach", "AudioRecord init failed (mic busy?)", e)
                _reading.value = null
                return@launch
            }

            val buffer = FloatArray(frameSize)
            try {
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    android.util.Log.e("GuitarCoach", "startRecording silent fail (state=${record.recordingState})")
                    return@launch
                }
                var shortReads = 0
                while (isActive) {
                    val n = record.read(buffer, 0, frameSize, AudioRecord.READ_BLOCKING)
                    if (n < frameSize) {
                        if (++shortReads > 20) { // 连续短读 ≈1s：录音源失效，退出而非空转
                            android.util.Log.e("GuitarCoach", "AudioRecord persistent short reads, stop")
                            break
                        }
                        continue
                    }
                    shortReads = 0
                    val pitch = PitchDetector.detect(buffer, sampleRate) ?: continue
                    val midi = PitchDetector.freqToMidi(pitch.frequency)
                    _reading.value = TunerReading(
                        frequency = pitch.frequency,
                        noteName = midiToName(midi),
                        midi = midi,
                        cents = PitchDetector.centsOff(pitch.frequency, midi),
                        stringHint = nearestString(midi),
                        tuningName = tuning.name,
                    )
                }
            } finally {
                runCatching { record.stop() }
                record.release()
                _reading.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _reading.value = null
    }

    private fun nearestString(midi: Int): Int {
        val t = tuning
        var best = 1
        var bestDist = Int.MAX_VALUE
        for (s in 1..6) {
            val d = abs(midi - t.midiLowFirst[s - 1])
            if (d < bestDist) {
                bestDist = d
                best = s
            }
        }
        return best
    }
}
