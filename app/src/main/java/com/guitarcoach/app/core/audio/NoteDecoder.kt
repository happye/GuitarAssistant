package com.guitarcoach.app.core.audio

/**
 * F602 音符解码器（纯函数，JVM 可测）：basic-pitch note head 激活矩阵 → MIDI 音符事件。
 *
 * 口径（官方 constants.py + 2026-09-17 本机 onnxruntime 实测）：
 * - 帧率 = 22050 / 256 hop ≈ 86.13 fps；音高 bin0 = A0（27.5Hz）= MIDI 21 → midi = 21 + pitch
 * - note head 是三个输出里 88 音高的两个之一；运行时用"激活总量大者为 notes"自校准
 *   （notes 持续激活总量 > onsets 稀疏激活）
 * - 阈值 0.5 起步；连续帧段合并为音符；同音高相邻段间隙 < 60ms 合并（防切碎）
 */
object NoteDecoder {

    const val FRAME_RATE = 22050.0 / 256.0
    const val MIDI_BASE = 21 // A0
    const val THRESHOLD = 0.5f
    const val MIN_FRAMES = 3
    const val MERGE_GAP_SEC = 0.06

    data class NoteEventMidi(val timeSec: Double, val midi: Int, val durationSec: Double)

    /**
     * @param noteHead [frames][88] 激活矩阵（0..1）
     * @param winStartSec 本窗口在整段音频中的起始秒
     */
    fun decode(noteHead: Array<FloatArray>, winStartSec: Double): List<NoteEventMidi> {
        val out = mutableListOf<NoteEventMidi>()
        val frames = noteHead.size
        for (pitch in 0 until 88) {
            var start = -1
            for (f in 0..frames) {
                val on = f < frames && noteHead[f][pitch] >= THRESHOLD
                if (on && start < 0) {
                    start = f
                } else if (!on && start >= 0) {
                    val durFrames = f - start
                    if (durFrames >= MIN_FRAMES) {
                        val t0 = winStartSec + start / FRAME_RATE
                        val dur = durFrames / FRAME_RATE
                        val merged = out.lastOrNull {
                            it.midi == MIDI_BASE + pitch && t0 - (it.timeSec + it.durationSec) < MERGE_GAP_SEC
                        }
                        if (merged != null) {
                            out[out.size - 1] = merged.copy(durationSec = t0 + dur - merged.timeSec)
                        } else {
                            out += NoteEventMidi(t0, MIDI_BASE + pitch, dur)
                        }
                    }
                    start = -1
                }
            }
        }
        return out.sortedBy { it.timeSec }
    }

}
