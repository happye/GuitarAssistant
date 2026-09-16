package com.guitarcoach.app.core.audio

/**
 * F401 riff 跟练判定 + F403 视听互验内核：目标音序列 vs 实测音序列。
 *
 * 对齐口径：目标拍位按 BPM 换算成秒；每个实测音就近配对未命中的目标音（容差内），
 * 配对后比 midi：低了/高了分别标记（以音频为准——F403 的"你按的可能不是你以为的品位"提示）；
 * 未配对目标 = 漏弹，未配对实测 = 多弹。纯 JVM，全部可单测。
 */
object RiffMatcher {

    data class TargetNote(val midi: Int, val beat: Double)
    data class DetectedNote(val midi: Int, val timeSec: Double)

    enum class Status { HIT, WRONG_HIGH, WRONG_LOW, MISS }

    data class TargetResult(val target: TargetNote, val status: Status, val detectedMidi: Int?)

    data class MatchResult(
        val perTarget: List<TargetResult>,
        val extraNotes: List<DetectedNote>,
    ) {
        val hitCount: Int get() = perTarget.count { it.status == Status.HIT }
        val accuracy: Double get() = if (perTarget.isEmpty()) 0.0 else hitCount * 1.0 / perTarget.size
        /** F403 提示：至少有一个音"以为按对了但音高不符"。 */
        val hasPitchMismatch: Boolean get() = perTarget.any { it.status == Status.WRONG_HIGH || it.status == Status.WRONG_LOW }
    }

    fun match(
        target: List<TargetNote>,
        detected: List<DetectedNote>,
        bpm: Int,
        toleranceBeats: Double = 0.5,
    ): MatchResult {
        if (target.isEmpty()) return MatchResult(emptyList(), detected)
        val secPerBeat = 60.0 / bpm
        val window = toleranceBeats * secPerBeat

        data class Slot(val note: TargetNote, val timeSec: Double, var hit: DetectedNote? = null)

        val slots = target.map { Slot(it, it.beat * secPerBeat) }
        val extras = mutableListOf<DetectedNote>()

        for (d in detected.sortedBy { it.timeSec }) {
            val best = slots
                .filter { it.hit == null }
                .minByOrNull { kotlin.math.abs(it.timeSec - d.timeSec) }
            if (best != null && kotlin.math.abs(best.timeSec - d.timeSec) <= window) {
                best.hit = d
            } else {
                extras += d
            }
        }

        val perTarget = slots.map { s ->
            val d = s.hit
            val status = when {
                d == null -> Status.MISS
                d.midi < s.note.midi -> Status.WRONG_LOW
                d.midi > s.note.midi -> Status.WRONG_HIGH
                else -> Status.HIT
            }
            TargetResult(s.note, status, d?.midi)
        }
        return MatchResult(perTarget, extras)
    }

    /** F403 用户可读提示：以音频为准，提示检查按弦位置。 */
    fun mismatchHint(result: MatchResult): String? {
        val wrong = result.perTarget.firstOrNull { it.status == Status.WRONG_HIGH || it.status == Status.WRONG_LOW } ?: return null
        val direction = if (wrong.status == Status.WRONG_LOW) "偏低" else "偏高"
        return "第 ${wrong.target.beat.toInt() + 1} 拍的音$direction 了（想弹 ${wrong.target.midi}，实测 ${wrong.detectedMidi}）——相信耳朵：检查是不是按错弦或错品"
    }
}
