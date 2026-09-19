package com.guitarcoach.app.core.audio

import com.guitarcoach.app.core.music.TimedNote

/**
 * InputClassifier（重构方案 §1.1 路由层，v1 确定性启发式）：
 * 从转写音符统计特征分类素材类型，决定输出分级（音符级 vs 和弦级+音符级）。
 * v1 用音符统计（复音率/音域跨度/密度）；谱平坦度等音频特征留待激活矩阵旁路接通后升级。
 */
object InputClassifier {

    enum class Material { CLEAN_MONO, CLEAN_POLY, MIXED }

    data class Stats(
        val polyphonyRatio: Double, // 处于 ≥2 音叠放中的音符占比（40ms 簇）
        val pitchSpread: Int,       // 音域跨度（半音）
        val notesPerSecond: Double,
    )

    fun stats(notes: List<TimedNote>): Stats {
        if (notes.isEmpty()) return Stats(0.0, 0, 0.0)
        val span = notes.maxOf { it.midi } - notes.minOf { it.midi }
        val durationSec = (notes.maxOf { it.timeSec + it.durationSec } - notes.first().timeSec).coerceAtLeast(0.1)
        // 复音：与相邻音起始差 <40ms 的音算叠放
        var poly = 0
        for (i in notes.indices) {
            val t = notes[i].timeSec
            val hasNeighbor = (i > 0 && t - notes[i - 1].timeSec < 0.04) ||
                (i < notes.size - 1 && notes[i + 1].timeSec - t < 0.04)
            if (hasNeighbor) poly++
        }
        return Stats(
            polyphonyRatio = poly.toDouble() / notes.size,
            pitchSpread = span,
            notesPerSecond = notes.size / durationSec,
        )
    }

    /**
     * 分类口径（阶段 0）：
     * - 复音率 ≥0.25 且跨度 ≥19 半音 → MIXED（多乐器/密集失真，默认配和弦级）
     * - 复音率 ≥0.15 → CLEAN_POLY（分解和弦/双音，音符级为主）
     * - 其余 → CLEAN_MONO（单音/riff，音符级）
     */
    fun classify(notes: List<TimedNote>): Material {
        val s = stats(notes)
        return when {
            s.polyphonyRatio >= 0.25 && s.pitchSpread >= 19 -> Material.MIXED
            s.polyphonyRatio >= 0.15 -> Material.CLEAN_POLY
            else -> Material.CLEAN_MONO
        }
    }
}
