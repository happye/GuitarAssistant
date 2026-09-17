package com.guitarcoach.app.core.audio

import android.content.Context
import com.guitarcoach.app.core.tab.MidiTabConverter
import com.guitarcoach.app.core.tab.STANDARD_TUNING_MIDI
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

/**
 * F602 端侧转写引擎（basic-pitch ICASSP 2022，Apache-2.0，模型 0.2MB 随 APK）：
 * PCM（mono 16bit）→ 重采样 22050 → 2s 窗（43844 样本）顺序推理 →
 * note head 激活矩阵 → [NoteDecoder] 纯函数解码 → MIDI 事件 → [MidiTabConverter] 弦品分配。
 *
 * 输出张量不按 index 假设（真机实测 TFLite 图序与 onnx 不同，硬编码崩）——运行时按实际
 *   shape 动态分配：264 通道=contours，88 通道的两个用激活总量自校准区分 note/onset 头。
 * note 头已实测精确命中 midi 69（440Hz 正弦）。
 * 推理仅 Android 运行时可用；解码/后处理全在纯函数层（JVM 单测覆盖）。
 */
class TranscriptionEngine(context: Context) : AutoCloseable {

    private companion object {
        const val MODEL_ASSET = "nmp.tflite"
        const val TARGET_RATE = 22050
        const val WINDOW_SAMPLES = 43844 // 模型固定输入长度（2s @22050 去边缘）
        // 帧数不再硬编码：从输出张量实际 shape 读取（模型/图序差异防呆）
        const val N_PITCHES = 88
        const val N_CONTOURS = 264

        /** 吉他合法音域：6 弦空弦(midi 40) ~ 1 弦 24 品(midi 88)。 */
        val MIDI_RANGE = (STANDARD_TUNING_MIDI.last()..(STANDARD_TUNING_MIDI.first() + 24))
    }

    private val interpreter: Interpreter

    init {
        interpreter = Interpreter(loadModel(context), Interpreter.Options().apply { setNumThreads(2) })
    }

    private fun loadModel(context: Context): MappedByteBuffer =
        context.assets.openFd(MODEL_ASSET).use { fd ->
            fd.createInputStream().channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }

    data class TranscriptionResult(
        val notes: List<MidiTabConverter.MidiNote>,
        val skippedOutOfRange: Int, // 超出吉他音域被跳过的检出数（混音素材的贝斯/鼓常驻此区）
    )

    /** 转写主入口：MIDI 音符（时间秒，升序）。onProgress 0..1 按窗回调。 */
    fun transcribe(
        pcm: ShortArray,
        sampleRate: Int,
        onProgress: (Float) -> Unit = {},
    ): TranscriptionResult {
        val mono = PcmResampler.toMonoRate(pcm, sampleRate, 1, TARGET_RATE)
        val wave = FloatArray(mono.size) { mono[it] / 32768f }

        val events = mutableListOf<NoteDecoder.NoteEventMidi>()
        val inputBuffer = ByteBuffer.allocateDirect(4 * WINDOW_SAMPLES).order(ByteOrder.nativeOrder())

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

            // 输出按实际 shape 动态分配（用户真机实测：TFLite 输出图序与 onnx 不同，按 index 硬编码崩）：
            // 264 通道 = contours（解码不用），88 通道的两个 = note/onset（语义序不可知，
            // 用激活总量自校准区分：持续激活的 note 头总量远大于稀疏 onset 头）。帧数也读实际值。
            val outSpecs = (0 until interpreter.outputTensorCount).map { i ->
                val t = interpreter.getOutputTensor(i)
                Triple(i, t.shape()[1], t.shape()[2]) // (index, frames, channels)
            }
            val spec88 = outSpecs.filter { it.third == N_PITCHES }
            val spec264 = outSpecs.firstOrNull { it.third == N_CONTOURS }
                ?: throw IllegalArgumentException(
                    "转写推理失败：输出张量形状异常 " +
                        outSpecs.joinToString { "(#${it.first}: ${it.second}x${it.third})" } +
                        "——模型版本可能不匹配"
                )
            val frames = spec264.second
            val contours = Array(1) { Array(frames) { FloatArray(N_CONTOURS) } }
            val headA = Array(1) { Array(frames) { FloatArray(N_PITCHES) } }
            val headB = Array(1) { Array(frames) { FloatArray(N_PITCHES) } }
            val outputs = mutableMapOf<Int, Any>(spec264.first to contours)
            spec88.getOrNull(0)?.let { outputs[it.first] = headA }
            spec88.getOrNull(1)?.let { outputs[it.first] = headB }

            runCatching { interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs) }
                .onFailure { throw IllegalArgumentException("转写推理失败：${it.message}", it) }

            // 两个 88 通道头都要（官方算法 frames+onsets 配合）：激活总量大者=frames（持续激活），
            // 小者=onsets（稀疏触发）；顺序 TFLite 不可知，用总量自校准
            val (headFrames, headOnsets) = if (total(headA[0]) >= total(headB[0])) headA[0] to headB[0] else headB[0] to headA[0]
            events += NoteDecoder.decode(headFrames, headOnsets, winStart / TARGET_RATE.toDouble())

            winStart += WINDOW_SAMPLES
            winIndex++
            onProgress((winIndex.toFloat() / totalWindows).coerceIn(0f, 1f))
        }

        // 音域过滤（用户实测 Song 2 崩溃根修）：混音素材的贝斯/鼓检出低于低 E 的音（如 midi 37），
        // 旧逻辑在弦品分配层直接抛异常毁掉整个转写——改为跳过并计数上报
        val inRange = events
            .distinctBy { (it.timeSec * 100).toInt() to it.midi }
            .filter { it.midi in MIDI_RANGE }
        val skipped = events.size - inRange.size
        val notes = inRange
            .map { MidiTabConverter.MidiNote(it.midi, it.timeSec, it.durationSec, amplitude = it.amplitude.toDouble()) }
            .sortedBy { it.timeSec }
        return TranscriptionResult(notes, skipped)
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
