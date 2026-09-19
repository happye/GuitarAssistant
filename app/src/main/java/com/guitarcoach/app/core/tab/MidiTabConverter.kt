package com.guitarcoach.app.core.tab

import com.guitarcoach.app.core.music.BeatGrid
import com.guitarcoach.app.core.music.TimedNote

/**
 * F603 MIDI 音符事件 → 节拍量化 → 弦/品分配 → TabDocument。
 *
 * v2（重构方案 §2.3-2.4）：
 * - 拍位来自 [BeatGrid]（逐拍实测插值），不再用固定 BPM 一次换算——曲内速度波动天然吸收，
 *   BPM 检出误差不再随曲长线性累积（真机实证：129 vs 真实 132 → 旧链漂移 ~5 拍/百拍）
 * - 网格分辨率自适应（IOI 中位数 → 1/16 / 1/8 / 1/4），吸附容差 0.12 拍，超差不强吸并标记
 * - 每个音的候选 = 所有能让 fret 落在 0~24 的弦；无候选（超出音域）的音**跳过不抛**——
 *   混音素材的贝斯/鼓检出低于低 E 是常态，毁掉整个转写不可接受（用户实测根修）
 * - DP 选全局最优：代价 = 换弦 + 品位移动 + 高把位惩罚（低把位优先，与扒谱主打简单 riff 匹配）
 * 弦号口径与 TabDocument 一致：1 = 高音 E。
 */
object MidiTabConverter {

    data class MidiNote(val midi: Int, val timeSec: Double, val durationSec: Double = 0.5, val amplitude: Double = 1.0)

    data class PlacedNote(
        val string: Int,
        val fret: Int,
        val beat: Double,
        val durationBeats: Double,
        val amplitude: Double = 1.0,
        val snapped: Boolean = true, // v2：false = 超出吸附容差（低置信，UI 热图用）
    )

    /** 拍位量化：吸附到 grid（默认 1/4 拍）。旧接口，供文本谱等固定网格场景。 */
    fun quantize(beat: Double, grid: Double = 0.25): Double =
        (kotlin.math.round(beat / grid) * grid).coerceAtLeast(0.0)

    private data class Cand(val string: Int, val fret: Int)

    /** 换算 + 分配。bpm 用于把秒转拍（旧接口：固定 BPM 均匀网格，等价于恒速 BeatGrid）。 */
    fun convert(notes: List<MidiNote>, bpm: Int): List<PlacedNote> =
        convert(notes.map { TimedNote(it.midi, it.timeSec, it.durationSec, it.amplitude) }, uniformGrid(bpm, notes))

    /** v2 主入口：逐拍网格插值量化 + 弦品 DP。notes 须按时间升序。 */
    fun convert(notes: List<TimedNote>, grid: BeatGrid): List<PlacedNote> {
        if (notes.isEmpty()) return emptyList()
        val secPerBeat = 60.0 / grid.bpm

        // 超域音跳过不抛（用户实测根修：混音素材的贝斯音低于低 E 会毁掉整个转写）——
        // 上层 TranscriptionEngine 已过滤并计数，此处为独立调用兜底
        val candidates = notes.mapNotNull { n ->
            val cands = (1..6).map { s -> Cand(s, n.midi - STANDARD_TUNING_MIDI[s - 1]) }
                .filter { it.fret in 0..24 }
            if (cands.isEmpty()) null else n to cands
        }
        val usableNotes = candidates.map { it.first }
        val candidateLists = candidates.map { it.second }

        // DP：状态 = 候选；代价 = 组内 + 相邻转移
        data class Entry(val cost: Double, val prev: Int)

        if (usableNotes.isEmpty()) return emptyList()
        val dp = Array(usableNotes.size) { Array(candidateLists[it].size) { Entry(Double.MAX_VALUE, -1) } }
        candidateLists[0].forEachIndexed { j, c ->
            dp[0][j] = Entry(baseCost(c), -1)
        }
        for (i in 1 until usableNotes.size) {
            candidateLists[i].forEachIndexed { j, c ->
                var best = Double.MAX_VALUE
                var bestK = -1
                candidateLists[i - 1].forEachIndexed { k, prev ->
                    val total = dp[i - 1][k].cost + transition(prev, c)
                    if (total < best) {
                        best = total
                        bestK = k
                    }
                }
                dp[i][j] = Entry(best + baseCost(c), bestK)
            }
        }

        // 回溯
        val path = IntArray(usableNotes.size)
        var j = dp.last().indices.minBy { dp.last()[it].cost }
        for (i in usableNotes.size - 1 downTo 0) {
            path[i] = j
            if (i > 0) j = dp[i][j].prev
        }

        // 网格分辨率自适应：相邻音符拍距（IOI）中位数 → 1/16(0.25) / 1/8(0.5) / 1/4(1.0)
        val fracBeats = usableNotes.map { grid.locate(it.timeSec) }
        val gridStep = adaptiveStep(fracBeats)

        return usableNotes.mapIndexed { i, n ->
            val c = candidateLists[i][path[i]]
            val frac = fracBeats[i].coerceAtLeast(0.0)
            val snappedBeat = (kotlin.math.round(frac / gridStep) * gridStep)
            val residual = kotlin.math.abs(frac - snappedBeat)
            PlacedNote(
                string = c.string,
                fret = c.fret,
                beat = if (residual <= SNAP_TOLERANCE_BEATS) snappedBeat.coerceAtLeast(0.0) else frac,
                durationBeats = n.durationSec / secPerBeat,
                amplitude = n.amplitude,
                snapped = residual <= SNAP_TOLERANCE_BEATS,
            )
        }
    }

    /** 吸附容差（拍）：超过则不强吸，保留分数拍位并标记低置信（防假音被网格"洗白"）。 */
    const val SNAP_TOLERANCE_BEATS = 0.12

    /** 相邻音符拍距中位数 → 网格步长（拍）。 riff（8/16 分密集）自动拿到细网格。 */
    private fun adaptiveStep(fracBeats: List<Double>): Double {
        val sortedByTime = fracBeats.sorted()
        val iois = sortedByTime.zipWithNext { a, b -> b - a }.filter { it > 1e-6 }.sorted()
        if (iois.isEmpty()) return 0.5
        val median = iois[iois.size / 2]
        return when {
            median < 0.3 -> 0.25
            median < 0.6 -> 0.5
            else -> 1.0
        }
    }

    /** 恒速网格（旧接口桥接）：从 t=0 按 bpm 铺拍到覆盖全部音符。 */
    private fun uniformGrid(bpm: Int, notes: List<MidiNote>): BeatGrid {
        val secPerBeat = 60.0 / bpm
        val last = (notes.maxOfOrNull { it.timeSec + it.durationSec } ?: 1.0) + secPerBeat * 4
        val count = (last / secPerBeat).toInt().coerceAtLeast(4)
        return BeatGrid(bpm.toDouble(), 4, DoubleArray(count) { it * secPerBeat })
    }

    /** 转 TabDocument（单段落 Main，小节按 4/4 切）。 */
    fun toTabDocument(placed: List<PlacedNote>, bpm: Int, title: String): TabDocument {
        if (placed.isEmpty()) return TabDocument(title = title, tempo = bpm)
        val bars = placed
            .groupBy { (it.beat / 4).toInt() }
            .toSortedMap()
            .map { (_, notesInBar) ->
                TabBar(notesInBar.sortedBy { it.beat }.map {
                    NoteEvent(string = it.string, fret = it.fret, beat = it.beat % 4, duration = it.durationBeats)
                })
            }
        return TabDocument(title = title, tempo = bpm, sections = listOf(TabSection("Main", bars)))
    }

    private fun baseCost(c: Cand): Double = if (c.fret > 12) 0.5 * (c.fret - 12) else 0.0 // 低把位优先

    private fun transition(prev: Cand, cur: Cand): Double =
        0.4 * kotlin.math.abs(cur.string - prev.string) + 0.15 * kotlin.math.abs(cur.fret - prev.fret)
}
