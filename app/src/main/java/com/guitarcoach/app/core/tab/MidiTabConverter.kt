package com.guitarcoach.app.core.tab

/**
 * F603 MIDI 音符事件 → 弦/品分配 → TabDocument。
 *
 * - 每个音的候选 = 所有能让 fret 落在 0~24 的弦；无候选（超出音域）的音**跳过不抛**——
 *   混音素材的贝斯/鼓检出低于低 E 是常态，毁掉整个转写不可接受（用户实测根修）
 * - DP 选全局最优：代价 = 换弦 + 品位移动 + 高把位惩罚（低把位优先，与扒谱主打简单 riff 匹配）
 * - 节拍量化：拍位吸附到 1/4 拍网格（v1；onset 端侧检测 F602 后接入）
 * 弦号口径与 TabDocument 一致：1 = 高音 E。
 */
object MidiTabConverter {

    data class MidiNote(val midi: Int, val timeSec: Double, val durationSec: Double = 0.5, val amplitude: Double = 1.0)

    data class PlacedNote(val string: Int, val fret: Int, val beat: Double, val durationBeats: Double, val amplitude: Double = 1.0)

    /** 拍位量化：吸附到 grid（默认 1/4 拍）。 */
    fun quantize(beat: Double, grid: Double = 0.25): Double =
        (kotlin.math.round(beat / grid) * grid).coerceAtLeast(0.0)

    /** 换算 + 分配。bpm 用于把秒转拍。 */
    private data class Cand(val string: Int, val fret: Int)

    fun convert(notes: List<MidiNote>, bpm: Int): List<PlacedNote> {
        if (notes.isEmpty()) return emptyList()
        val secPerBeat = 60.0 / bpm

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

        return usableNotes.mapIndexed { i, n ->
            val c = candidateLists[i][path[i]]
            PlacedNote(
                string = c.string,
                fret = c.fret,
                beat = quantize(n.timeSec / secPerBeat),
                durationBeats = (n.durationSec / secPerBeat),
                amplitude = n.amplitude,
            )
        }
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
