package com.guitarcoach.app.core.music

/**
 * 带时值音符（中立时序模型）：core/audio 转写产出 → core/tab 弦品分配消费。
 * 独立于 MidiTabConverter.MidiNote 的原因：core/audio 不得反向依赖 core/tab（架构红线 3）。
 */
data class TimedNote(
    val midi: Int,
    val timeSec: Double,
    val durationSec: Double = 0.5,
    val amplitude: Double = 1.0,
    /** 近同时和弦组号（NoteCleaner R3 产出，同组音在弦品 DP 中整组枚举）；-1 = 独立音。 */
    val groupId: Int = -1,
)
