package com.guitarcoach.app.core.audio

/**
 * F602 音符解码器 v2（纯函数，JVM 可测）：basic-pitch 双头激活矩阵 → MIDI 音符事件。
 *
 * **官方算法移植**（basic-pitch 0.4.0 note_creation.py 的 output_to_notes_polyphonic +
 * get_infered_onsets，Apache-2.0；用户实测 v1 简单阈值版"扒谱烂到极致"后重写）。
 * v1 → v2 的精度关键差异：
 * 1. onset 头与 frame 差分推断的 onset 取 max（官方默认 infer_onsets）——v1 只用单头
 * 2. onset 局部峰值检测（argrelmax 等价：上下帧都小于才触发）——v1 是整段过阈值
 * 3. 能量消耗机制（remaining_energy）：同一 pitch 被一个音符消耗后归零，防同音重复触发——v1 靠 distinctBy 粗去重
 * 4. min_note_len = 11 帧（官方默认，≈128ms）——v1 是 3 帧（碎音来源之一）
 * 5. 输出 amplitude（onset 峰值 0..1）→ 播放音量层次
 */
object NoteDecoder {

    const val FRAME_RATE = 22050.0 / 256.0
    const val MIDI_BASE = 21 // A0
    const val ONSET_THRESH = 0.5f
    const val FRAME_THRESH = 0.3f
    // 吉他场景 min_note_len：官方 11 帧(128ms)在单音 riff 下仍碎，实测取向 180~250ms（调研建议）取 186ms
    const val MIN_NOTE_FRAMES = 16
    const val PITCH_BIN_MIN = 19  // midi 40 = 6 弦空弦（bin = midi - 21）
    const val PITCH_BIN_MAX = 67  // midi 88 = 1 弦 24 品
    const val ENERGY_TOL = 11       // 能量低于阈值的容忍帧数（官方默认）
    private const val N_DIFF = 2    // 差分推断 onset 的阶数（官方默认）

    data class NoteEventMidi(val timeSec: Double, val midi: Int, val durationSec: Double, val amplitude: Float = 1f)

    /**
     * 官方算法解码。
     * @param frames note 头激活矩阵 [帧数][88]（0..1）
     * @param onsets onset 头激活矩阵 [帧数][88]（0..1）
     * @param winStartSec 本窗口在整段音频中的起始秒
     */
    fun decode(
        frames: Array<FloatArray>,
        onsets: Array<FloatArray>,
        winStartSec: Double,
    ): List<NoteEventMidi> {
        val nFrames = minOf(frames.size, onsets.size)
        if (nFrames == 0) return emptyList()

        // 1) 差分推断 onset（官方 get_infered_onsets：n 阶差分取 min，负值归零，rescale 到 onset 峰值，取 max）
        val inferred = Array(nFrames) { FloatArray(88) }
        var maxOnset = 0f
        for (f in 0 until nFrames) for (p in 0 until 88) maxOnset = maxOf(maxOnset, onsets[f][p])
        var maxDiff = 0f
        val diffs = Array(N_DIFF) { Array(nFrames) { FloatArray(88) } }
        for (n in 1..N_DIFF) {
            for (f in n until nFrames) for (p in 0 until 88) {
                val d = frames[f][p] - frames[f - n][p]
                diffs[n - 1][f][p] = if (d > 0) d else 0f
            }
            for (f in 0 until nFrames) for (p in 0 until 88) {
                if (f < n) diffs[n - 1][f][p] = 0f
                if (diffs[n - 1][f][p] > maxDiff) maxDiff = diffs[n - 1][f][p]
            }
        }
        if (maxDiff > 0f) {
            // rescale 基准 = max(onset 峰, diff 峰)：官方用 onset 峰，但 onsets 全零时除零且推断失效——
            // 用两者较大值保持量纲一致且纯 frame 推断可用（onset 缺失场景）
            val scale = maxOf(maxOnset, maxDiff) / maxDiff
            for (f in 0 until nFrames) for (p in 0 until 88) {
                inferred[f][p] = maxOf(onsets[f][p], diffs[N_DIFF - 1][f][p] * scale)
            }
        } else {
            for (f in 0 until nFrames) for (p in 0 until 88) inferred[f][p] = onsets[f][p]
        }

        // 2) 局部峰值：平台取左沿（v > 前帧 && v >= 后帧）——二阶差分会让 onset 平台拖 1 帧，
        //    严格 argrelmax 会把 onset 定位到平台末端 +1 帧（实证偏差 11.6ms），取左沿才是真实起点
        val peaks = Array(nFrames) { FloatArray(88) }
        for (p in 0 until 88) {
            for (f in 1 until nFrames - 1) {
                val v = inferred[f][p]
                if (v > inferred[f - 1][p] && v >= inferred[f + 1][p]) peaks[f][p] = v
            }
            if (nFrames > 1 && inferred[0][p] > 0f && inferred[0][p] >= inferred[1][p]) peaks[0][p] = inferred[0][p]
        }

        // 3) 从后往前扫 onset 峰值（官方口径：倒序保证 remaining_energy 消耗语义正确）
        val remaining = Array(nFrames) { FloatArray(88) }
        for (f in 0 until nFrames) for (p in 0 until 88) remaining[f][p] = frames[f][p]

        val out = mutableListOf<NoteEventMidi>()
        // 音域约束在解码层收口（官方 constrain_frequency 等价）：域外 bin 不产峰，省得先产再丢
        for (p in PITCH_BIN_MIN..PITCH_BIN_MAX) {
            val onsetsP = Array(nFrames) { peaks[it][p] }
            val onsetIdx = (nFrames - 1 downTo 0).filter { onsetsP[it] >= ONSET_THRESH }
            for (start in onsetIdx) {
                if (start >= nFrames - 1) continue
                // 4) 向后找结束：frame 低于阈值连续 ENERGY_TOL 帧则结束（回退到连续段前）
                var i = start + 1
                var k = 0
                while (i < nFrames - 1 && k < ENERGY_TOL) {
                    if (remaining[i][p] < FRAME_THRESH) k++ else k = 0
                    i++
                }
                i -= k
                // 5) min_note_len 过滤（官方 11 帧：碎音主防）
                if (i - start <= MIN_NOTE_FRAMES) continue
                // 6) 能量消耗：该 pitch 该区间归零，后面的 onset 不再重复吃同一段能量
                for (f in start until i) remaining[f][p] = 0f
                out += NoteEventMidi(
                    timeSec = winStartSec + start / FRAME_RATE,
                    midi = MIDI_BASE + p,
                    durationSec = (i - start) / FRAME_RATE,
                    amplitude = onsetsP[start],
                )
            }
        }
        return out.sortedBy { it.timeSec }
    }
}
