package com.guitarcoach.app.core.audio

import com.guitarcoach.app.core.music.TimedNote

/**
 * 音符清洗（重构方案 §2.2，确定性规则，转写端"近同时假音叠一列"的根修）。
 * 全部规则作用于弦品 DP 之前；顺序：R4 低置信 → R1 同音合并 → R2 泛音鬼影 → R3 近同时成组。
 * 每条规则命中计数随结果返回（接 logcat 分离诊断先例）。
 */
object NoteCleaner {

    data class Result(
        val notes: List<TimedNote>,
        val droppedLowAmp: Int,
        val mergedSamePitch: Int,
        val droppedGhosts: Int,
        val chordGroups: Int,
    )

    private val GHOST_RATIOS = setOf(12, 7, 19) // 相对基音的半音差（八度/五度/八度+五度）

    /** @param secPerBeat 用于把 R1 的 0.25 拍窗口换算秒；null = 仅用 60ms 固定窗。notes 须按时间升序。 */
    fun clean(notes: List<TimedNote>, secPerBeat: Double? = null): Result {
        if (notes.isEmpty()) return Result(notes, 0, 0, 0, 0)
        require(notes.zipWithNext().all { (a, b) -> a.timeSec <= b.timeSec }) { "notes 必须按时间升序" }

        // R4 低置信过滤：amp < 0.25×全曲中位
        val amps = notes.map { it.amplitude }.sorted()
        val median = amps[amps.size / 2]
        val ampFloor = 0.25 * median
        val kept = notes.filter { it.amplitude >= ampFloor }
        val droppedLowAmp = notes.size - kept.size

        // R1 同音合并：同 midi、Δt < max(60ms, 0.25 拍) → 并为最早者并延展时长
        // （v1 简化：无激活矩阵谷值校验——activation 旁路接通后补，重构方案已标注）
        val mergeWinMs = if (secPerBeat != null) maxOf(60.0, 0.25 * secPerBeat * 1000.0) else 60.0
        val merged = ArrayList<TimedNote>(kept.size)
        var mergedSamePitch = 0
        for (n in kept) {
            val last = merged.lastOrNull()
            if (last != null && last.midi == n.midi && (n.timeSec - last.timeSec) * 1000 < mergeWinMs) {
                merged[merged.size - 1] = last.copy(
                    durationSec = maxOf(last.durationSec, n.timeSec + n.durationSec - last.timeSec),
                    amplitude = maxOf(last.amplitude, n.amplitude),
                )
                mergedSamePitch++
            } else {
                merged.add(n)
            }
        }

        // R2 泛音鬼影：q 是 p 的 {+12,+7,+19} 倍频假音（amp 显著更弱、起始贴近、时值不更长）→ 删 q
        val byMidi = merged.groupBy { it.midi }
        val ghostFlags = BooleanArray(merged.size)
        val midiToFirstIdx = HashMap<Int, Int>() // midi -> 该音第一个未删实例索引
        merged.forEachIndexed { i, n -> midiToFirstIdx.putIfAbsent(n.midi, i) }
        var droppedGhosts = 0
        merged.forEachIndexed { i, q ->
            if (ghostFlags[i]) return@forEachIndexed
            for (interval in GHOST_RATIOS) {
                val pIdx = midiToFirstIdx[q.midi - interval] ?: continue
                val p = merged[pIdx]
                if (q.amplitude < 0.35 * p.amplitude &&
                    (q.timeSec - p.timeSec) * 1000 < 50.0 &&
                    q.timeSec >= p.timeSec - 1e-9 &&
                    q.durationSec <= p.durationSec + 0.1
                ) {
                    ghostFlags[i] = true
                    droppedGhosts++
                    break
                }
            }
        }
        val unghosted = merged.filterIndexed { i, _ -> !ghostFlags[i] }

        // R3 近同时成组：起始差 <35ms（扫弦物理窗）聚簇；簇内同 midi 保留最响；
        // 全簇标同一 groupId（弦品 DP 整组枚举用）
        val grouped = ArrayList<TimedNote>(unghosted.size)
        var chordGroups = 0
        var i = 0
        while (i < unghosted.size) {
            var j = i + 1
            while (j < unghosted.size && (unghosted[j].timeSec - unghosted[j - 1].timeSec) * 1000 < 35.0) j++
            val cluster = unghosted.subList(i, j)
            val dedup = HashMap<Int, TimedNote>() // midi -> 最响
            for (n in cluster) {
                val cur = dedup[n.midi]
                if (cur == null || n.amplitude > cur.amplitude) dedup[n.midi] = n
            }
            val fusedCount = cluster.size - dedup.size
            if (dedup.size > 1) {
                val gid = chordGroups++
                dedup.values.forEach { grouped.add(it.copy(groupId = gid)) }
            } else {
                grouped.addAll(dedup.values)
            }
            i = j
            // fusedCount 不单列：并入 chordGroups 口径的规模体现（组号数=和弦组数）
        }
        val result = grouped.sortedBy { it.timeSec }
        return Result(result, droppedLowAmp, mergedSamePitch, droppedGhosts, chordGroups)
    }
}
