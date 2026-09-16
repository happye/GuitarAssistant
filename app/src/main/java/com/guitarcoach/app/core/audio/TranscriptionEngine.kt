package com.guitarcoach.app.core.audio

import android.content.Context
import com.guitarcoach.app.core.tab.MidiTabConverter
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

/**
 * F602 端侧转写引擎（basic-pitch ICASSP 2022，Apache-2.0，模型 0.2MB 随 APK）：
 * PCM（mono 16bit）→ 重采样 22050 → 2s 窗（43844 样本）顺序推理 →
 * note head 激活矩阵 → [NoteDecoder] 纯函数解码 → MIDI 事件 → [MidiTabConverter] 弦品分配。
 *
 * 输出张量顺序（与 onnx 同图序，2026-09-17 onnxruntime 实测）：
 *   index 0 = contours [1,172,264]，index 1 = notes [1,172,88]，index 2 = onsets [1,172,88]。
 * notes 头已实测精确命中 midi 69（440Hz 正弦）；运行时再用激活总量双保险自校准。
 * 推理仅 Android 运行时可用；解码/后处理全在纯函数层（JVM 单测覆盖）。
 */
class TranscriptionEngine(context: Context) : AutoCloseable {

    private companion object {
        const val MODEL_ASSET = "nmp.tflite"
        const val TARGET_RATE = 22050
        const val WINDOW_SAMPLES = 43844 // 模型固定输入长度（2s @22050 去边缘）
        const val FRAMES_PER_WIN = 172   // 43844/256 hop ≈ 171.3 → 172（实测确认）
        const val N_PITCHES = 88
        const val N_CONTOURS = 264
    }

    private val interpreter: Interpreter

    init {
        interpreter = Interpreter(loadModel(context), Interpreter.Options().apply { setNumThreads(2) })
    }

    private fun loadModel(context: Context): MappedByteBuffer =
        context.assets.openFd(MODEL_ASSET).use { fd ->
            fd.createInputStream().channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }

    /** 转写主入口：MIDI 音符（时间秒，升序）。onProgress 0..1 按窗回调。 */
    fun transcribe(
        pcm: ShortArray,
        sampleRate: Int,
        onProgress: (Float) -> Unit = {},
    ): List<MidiTabConverter.MidiNote> {
        val mono = PcmResampler.toMonoRate(pcm, sampleRate, 1, TARGET_RATE)
        val wave = FloatArray(mono.size) { mono[it] / 32768f }

        val events = mutableListOf<NoteDecoder.NoteEventMidi>()
        val inputBuffer = ByteBuffer.allocateDirect(4 * WINDOW_SAMPLES).order(ByteOrder.nativeOrder())
        val contours = Array(1) { Array(FRAMES_PER_WIN) { FloatArray(N_CONTOURS) } }
        val notes = Array(1) { Array(FRAMES_PER_WIN) { FloatArray(N_PITCHES) } }
        val onsets = Array(1) { Array(FRAMES_PER_WIN) { FloatArray(N_PITCHES) } }
        val outputs = mapOf(0 to contours, 1 to notes, 2 to onsets)

        val totalWindows = ((wave.size + WINDOW_SAMPLES - 1) / WINDOW_SAMPLES).coerceAtLeast(1)
        var winStart = 0
        var winIndex = 0
        while (winStart < wave.size) {
            val len = minOf(WINDOW_SAMPLES, wave.size - winStart)
            inputBuffer.rewind()
            for (i in 0 until WINDOW_SAMPLES) {
                inputBuffer.putFloat(if (i < len) wave[winStart + i] else 0f) // 尾窗零填充
            }
            inputBuffer.rewind()
            runCatching { interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs) }
                .onFailure { throw IllegalArgumentException("转写推理失败：${it.message}", it) }

            val head = if (total(notes[0]) >= total(onsets[0])) {
                notes[0] // 双保险：激活总量大者为 notes（持续激活 > 稀疏 onset）
            } else {
                onsets[0]
            }
            events += NoteDecoder.decode(head, winStart / TARGET_RATE.toDouble())

            winStart += WINDOW_SAMPLES
            winIndex++
            onProgress((winIndex.toFloat() / totalWindows).coerceIn(0f, 1f))
        }

        return events
            .distinctBy { (it.timeSec * 100).toInt() to it.midi }
            .map { MidiTabConverter.MidiNote(it.midi, it.timeSec, it.durationSec) }
            .sortedBy { it.timeSec }
    }

    override fun close() {
        interpreter.close()
    }

    private fun total(m: Array<FloatArray>): Double {
        var s = 0.0
        for (row in m) for (v in row) s += v
        return s
    }
}
