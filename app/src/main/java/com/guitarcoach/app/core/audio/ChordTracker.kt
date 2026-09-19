package com.guitarcoach.app.core.audio

import com.guitarcoach.app.core.music.BeatGrid
import com.guitarcoach.app.core.music.TimedNote
import com.guitarcoach.app.core.tab.ChordEvent

/**
 * ChordTracker（重构方案 §2.5）：音符序列 → 和弦级轨道（混音素材的默认输出，D3 口径）。
 * 确定性算法：拍同步 chroma → 模板匹配（12 根音 × 7 质量，余弦）→ 贪心分段 → 短段吸收。
 * v1 从转写音符统计 chroma（转写引擎激活矩阵旁路留待后续——不阻塞和弦级可用性）。
 */
object ChordTracker {

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** 和弦模板：音程集合（半音）。"5"=强力和弦（根+五度）。 */
    private val TEMPLATES: Map<String, Set<Int>> = mapOf(
        "5" to setOf(0, 7),
        "maj" to setOf(0, 4, 7),
        "min" to setOf(0, 3, 7),
        "7" to setOf(0, 4, 7, 10),
        "maj7" to setOf(0, 4, 7, 11),
        "m7" to setOf(0, 3, 7, 10),
        "sus4" to setOf(0, 5, 7),
    )

    /** 短于此（拍）的和弦段视为过渡噪声，吸收进近关系邻段。 */
    private const val MIN_SEGMENT_BEATS = 1.0

    /**
     * 音符 → 和弦事件序列。notes 须升序；grid 提供拍位换算；windowBeats 决定分析窗。
     */
    fun detect(notes: List<TimedNote>, grid: BeatGrid, windowBeats: Double = 1.0): List<ChordEvent> {
        if (notes.isEmpty()) return emptyList()
        require(windowBeats > 0)

        val totalBeats = grid.locate(notes.maxOf { it.timeSec + it.durationSec }) + windowBeats
        val windowCount = (totalBeats / windowBeats).toInt().coerceAtLeast(1)

        // 1) 每窗 chroma（12 维，幅度加权；跨窗音符按覆盖窗分摊）
        val chromas = Array(windowCount) { DoubleArray(12) }
        for (n in notes) {
            val w0 = (grid.locate(n.timeSec) / windowBeats).toInt().coerceIn(0, windowCount - 1)
            // 结尾拍开区间：恰好落在窗界的结束不渗入下一窗（渗入会凭空造出过渡"和弦"）
            val w1 = (kotlin.math.ceil(grid.locate(n.timeSec + n.durationSec) / windowBeats) - 1)
                .toInt().coerceIn(w0, windowCount - 1)
            val pc = ((n.midi % 12) + 12) % 12
            val weight = n.amplitude.coerceAtLeast(0.05) / (w1 - w0 + 1)
            for (w in w0..w1) chromas[w][pc] += weight
        }

        // 2) 逐窗模板匹配（余弦相似度；静音窗 = null）
        data class Cand(val root: Int, val quality: String, val score: Double)
        val cands = Array(windowCount) { w ->
            val c = chromas[w]
            val energy = kotlin.math.sqrt(c.sumOf { it * it })
            if (energy < 1e-9) null else {
                var bestRoot = 0
                var bestQuality = "maj"
                var bestScore = -1.0
                for (root in 0 until 12) {
                    for ((quality, template) in TEMPLATES) {
                        var dot = 0.0
                        for (iv in template) dot += c[(root + iv) % 12]
                        val score = dot / (energy * kotlin.math.sqrt(template.size.toDouble()))
                        // 平手取更丰富模板（C 大三度与 C5 完全同分时选 maj，避免幂和弦偏置）
                        if (score > bestScore + 1e-9 ||
                            (kotlin.math.abs(score - bestScore) <= 1e-9 && template.size > TEMPLATES[bestQuality]!!.size)
                        ) {
                            bestScore = score; bestRoot = root; bestQuality = quality
                        }
                    }
                }
                Cand(bestRoot, bestQuality, bestScore)
            }
        }

        // 3) 贪心分段：同型延续，变型即断段
        data class Seg(val startW: Int, var endW: Int, val root: Int, val quality: String, var scoreSum: Double, var count: Int)
        val segs = mutableListOf<Seg>()
        for (w in 0 until windowCount) {
            val c = cands[w] ?: continue
            val last = segs.lastOrNull()
            if (last != null && last.root == c.root && last.quality == c.quality && last.endW == w) {
                last.endW = w + 1; last.scoreSum += c.score; last.count++
            } else {
                segs += Seg(w, w + 1, c.root, c.quality, c.score, 1)
            }
        }

        // 4) 短段吸收：时长 < MIN 的段并入相邻近关系段（根音距离 ≤2 或同根异型），否则保留
        val absorbed = mutableListOf<Seg>()
        for (seg in segs) {
            val durBeats = (seg.endW - seg.startW) * windowBeats
            val prev = absorbed.lastOrNull()
            if (durBeats <= MIN_SEGMENT_BEATS && prev != null && near(prev.root, seg.root)) {
                prev.endW = seg.endW
            } else {
                absorbed += seg
            }
        }

        return absorbed.map { s ->
            ChordEvent(
                beat = s.startW * windowBeats,
                durationBeats = (s.endW - s.startW) * windowBeats,
                root = NOTE_NAMES[s.root],
                quality = s.quality,
                display = NOTE_NAMES[s.root] + qualitySuffix(s.quality),
                confidence = (s.scoreSum / s.count).toFloat().coerceIn(0f, 1f),
            )
        }
    }

    private fun near(a: Int, b: Int): Boolean =
        a == b || ((a - b + 12) % 12) <= 2 || ((b - a + 12) % 12) <= 2

    private fun qualitySuffix(q: String): String = when (q) {
        "maj" -> ""
        "min" -> "m"
        else -> q // "5"/"7"/"maj7"/"m7"/"sus4" 直接作后缀
    }
}
