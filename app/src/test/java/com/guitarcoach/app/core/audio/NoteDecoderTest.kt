package com.guitarcoach.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NoteDecoder v2（官方 output_to_notes_polyphonic 移植）行为测试：
 * 锁定官方四大机制——差分推断 onset、局部峰值、能量消耗、min_note_len。
 */
class NoteDecoderTest {

    private fun blankFrames(n: Int) = Array(n) { FloatArray(88) }

    private fun note(midi: Int) = midi - NoteDecoder.MIDI_BASE // pitch bin

    @Test
    fun `onset加frame激活产出正确音符`() {
        val frames = blankFrames(60)
        val onsets = blankFrames(60)
        // pitch = midi 69-21 = 48；帧 10..40 持续激活，帧 10 onset 峰
        for (f in 10..40) frames[f][48] = 0.9f
        onsets[10][48] = 0.8f
        val out = NoteDecoder.decode(frames, onsets, winStartSec = 0.0)
        assertEquals(1, out.size)
        assertEquals(69, out[0].midi)
        assertEquals(10.0 / NoteDecoder.FRAME_RATE, out[0].timeSec, 1e-9)
        // 结束位置：能量低于 0.3 连续 11 帧处回退 → 帧 41 之后全部低于阈值
        val expectedDur = (41 - 10) / NoteDecoder.FRAME_RATE
        assertEquals(expectedDur, out[0].durationSec, 0.05)
        assertEquals(0.9f, out[0].amplitude) // rescale 后差分推断峰 = 0.9（量纲对齐官方）
    }

    @Test
    fun `onset缺失时frame差分推断补位`() {
        // 官方 infer_onsets：frame 从 0 跳到 0.9 的差分被 rescale 成 onset（onsets 全零）
        val frames = blankFrames(60)
        val onsets = blankFrames(60)
        for (f in 20..50) frames[f][48] = 0.9f
        val out = NoteDecoder.decode(frames, onsets, winStartSec = 1.0)
        assertEquals(1, out.size)
        assertEquals(69, out[0].midi)
        assertEquals(1.0 + 20.0 / NoteDecoder.FRAME_RATE, out[0].timeSec, 0.05) // 差分峰在帧 20
    }

    @Test
    fun `短于min_note_len的碎音被过滤`() {
        val frames = blankFrames(60)
        val onsets = blankFrames(60)
        for (f in 10..16) frames[f][48] = 0.9f // 7 帧 < 11 帧
        onsets[10][48] = 0.8f
        assertEquals(0, NoteDecoder.decode(frames, onsets, 0.0).size)
    }

    @Test
    fun `同pitch重叠onset由能量消耗防止重复触发`() {
        val frames = blankFrames(120)
        val onsets = blankFrames(120)
        for (f in 10..80) frames[f][48] = 0.9f // 持续长音
        onsets[10][48] = 0.8f
        onsets[40][48] = 0.8f // 中途又来一个 onset（该 pitch 能量已被第一个音消耗）
        val out = NoteDecoder.decode(frames, onsets, 0.0).filter { it.midi == 69 }
        // 官方倒序语义：晚 onset 先消耗能量，早 onset 被截断在其处——长音被中途 onset 分成两段
        // （这正是"重复拨弦"的正确表达），两段都产出
        assertEquals(2, out.size)
        assertTrue(out[0].timeSec < out[1].timeSec)
        assertTrue("早段应被截断在晚 onset 附近", out[0].durationSec < 40.0 / NoteDecoder.FRAME_RATE)
    }

    @Test
    fun `两个不同pitch各自成音`() {
        val frames = blankFrames(60)
        val onsets = blankFrames(60)
        for (f in 10..40) frames[f][48] = 0.9f // midi 69
        onsets[10][48] = 0.8f
        for (f in 20..50) frames[f][52] = 0.9f // midi 73
        onsets[20][52] = 0.9f
        val out = NoteDecoder.decode(frames, onsets, 0.0)
        assertEquals(listOf(69, 73), out.map { it.midi }.sorted())
    }

    @Test
    fun `全零输入不炸`() {
        assertEquals(0, NoteDecoder.decode(blankFrames(50), blankFrames(50), 0.0).size)
    }
}
