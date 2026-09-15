package com.guitarcoach.app.core.tab

/** 音符引用：全局小节号（1 起）+ 弦 + 品。 */
data class NoteRef(val bar: Int, val string: Int, val fret: Int)

/**
 * CoVe 覆盖审计（F207）：讲解 steps 引用的音符多重集 vs 谱面音符多重集。
 * 漏音 = 谱面有而讲解没覆盖；多余 = 讲解覆盖次数超过谱面出现次数（含重复讲、讲谱面没有的音）。
 * 纯集合计算，"不漏音"从软约束变成可程序化校验。
 */
object CoverageAuditor {

    data class Issue(val missing: List<NoteRef>, val extra: List<NoteRef>) {
        val clean: Boolean get() = missing.isEmpty() && extra.isEmpty()
    }

    fun check(expected: List<NoteRef>, covered: List<NoteRef>): Issue {
        val expectedCount = expected.groupingBy { it }.eachCount()
        val coveredCount = covered.groupingBy { it }.eachCount()
        val missing = expectedCount.flatMap { (ref, count) ->
            List((count - coveredCount.getOrDefault(ref, 0)).coerceAtLeast(0)) { ref }
        }
        val extra = coveredCount.flatMap { (ref, count) ->
            List((count - expectedCount.getOrDefault(ref, 0)).coerceAtLeast(0)) { ref }
        }
        return Issue(missing, extra)
    }
}
