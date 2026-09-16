package com.guitarcoach.app.core.audio

import com.guitarcoach.app.core.audio.RiffMatcher.DetectedNote
import com.guitarcoach.app.core.audio.RiffMatcher.Status
import com.guitarcoach.app.core.audio.RiffMatcher.TargetNote
import com.guitarcoach.app.core.music.midiFromFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F401/F403 验收：简单 riff 判对；错音指出第几拍、高了还是低了；漏音/多弹检出。测试用 60bpm（1 拍=1 秒）。 */
class RiffMatcherTest {

    // 目标：爬格子 E 弦 1-2-3-4 品（MIDI 41-43-45-47? E2+1=41, +2=42, +3=43, +4=44）@120bpm
    private val target = listOf(
        TargetNote(41, 0.0), TargetNote(42, 1.0), TargetNote(43, 2.0), TargetNote(44, 3.0),
    )

    @Test
    fun `全部弹对判HIT`() {
        val detected = listOf(
            DetectedNote(41, 0.05), DetectedNote(42, 1.02), DetectedNote(43, 2.0), DetectedNote(44, 3.1),
        )
        val r = RiffMatcher.match(target, detected, bpm = 60)
        assertEquals(listOf(Status.HIT, Status.HIT, Status.HIT, Status.HIT), r.perTarget.map { it.status })
        assertEquals(1.0, r.accuracy, 1e-9)
        assertTrue(r.extraNotes.isEmpty())
        assertNull(RiffMatcher.mismatchHint(r))
    }

    @Test
    fun `错音指出第几拍且分高低`() {
        // 第 2 拍弹成 43（高了 1 半音）；第 3 拍弹成 42（低了）
        val detected = listOf(
            DetectedNote(41, 0.0), DetectedNote(43, 1.0), DetectedNote(42, 2.0), DetectedNote(44, 3.0),
        )
        val r = RiffMatcher.match(target, detected, bpm = 60)
        assertEquals(Status.WRONG_HIGH, r.perTarget[1].status)
        assertEquals(Status.WRONG_LOW, r.perTarget[2].status)
        assertTrue(r.hasPitchMismatch)
        val hint = RiffMatcher.mismatchHint(r)
        assertTrue(hint!!.contains("偏高"))
        assertTrue(hint.contains("检查"))
    }

    @Test
    fun `漏音与多弹检出`() {
        val detected = listOf(
            DetectedNote(41, 0.0), DetectedNote(44, 3.0), DetectedNote(60, 3.6),
        )
        val r = RiffMatcher.match(target, detected, bpm = 60)
        assertEquals(Status.MISS, r.perTarget[1].status)
        assertEquals(Status.MISS, r.perTarget[2].status)
        assertEquals(1, r.extraNotes.size)
        assertEquals(0.5, r.accuracy, 1e-9)
    }

    @Test
    fun `容差外就近不误配`() {
        // 目标 4 拍只有 1 个音且落在 10 拍处：全部 MISS，音进 extra
        val detected = listOf(DetectedNote(41, 10.0))
        val r = RiffMatcher.match(target, detected, bpm = 60)
        assertTrue(r.perTarget.all { it.status == Status.MISS })
        assertEquals(1, r.extraNotes.size)
    }

    @Test
    fun `空目标返回空结果`() {
        val r = RiffMatcher.match(emptyList(), listOf(DetectedNote(41, 0.0)), bpm = 60)
        assertTrue(r.perTarget.isEmpty())
        assertEquals(1, r.extraNotes.size)
    }

    @Test
    fun `频率到midi换算口径`() {
        assertEquals(69, midiFromFrequency(440.0))
        assertEquals(40, midiFromFrequency(82.41)) // 低E
        assertEquals(64, midiFromFrequency(329.63)) // 高E
        // 四舍五入边界（截断会把整段频域标低一个半音——监督员 P1 回归用例）
        assertEquals(45, midiFromFrequency(109.0))
        assertEquals(44, midiFromFrequency(103.0))
        assertNull(midiFromFrequency(30.0))
        assertNull(midiFromFrequency(Double.NaN))
    }
}
