package com.guitarcoach.app.core.tab

/**
 * F209 自动指法分配：动态规划把「弦/品序列」指到左手手指 0~4（0=空弦），并给出把位建议。
 *
 * 模型：
 * - 输入按时序切成「同拍音符组」（和弦 = 一组；旋律 = 单音组，见 [slotsOf]）
 * - 每组枚举手指组合，硬规则过滤（同指多音必须是同品合法横按：跨度内不能有更低品）
 * - 组内代价 = 指序倒置 / 超伸展 / 跨把位铺开 / 横按宽度；组间转移 = 同指移动距离 + 换指伸展检查
 * - 全局最小代价路径即指法方案
 *
 * [validate] 的规则同样用于校验 LLM 生成的指法：同拍同指跨品、横按压住更低品等一律拦截。
 */
object FingeringSolver {

    data class Slot(val bar: Int, val beat: Double, val notes: List<NoteEvent>)

    data class Item(val string: Int, val fret: Int, val finger: Int)

    data class Assignment(val bar: Int, val beat: Double, val items: List<Item>) {
        /** 把位建议：非空弦最低品；全空弦为 null。 */
        val position: Int? get() = items.filter { it.fret > 0 }.minOfOrNull { it.fret }
    }

    // ---------- 入口 ----------

    fun solve(slots: List<Slot>): List<Assignment> {
        if (slots.isEmpty()) return emptyList()
        val choices = slots.map { candidates(it) }
        // dp[i][j] = 第 i 组选第 j 个候选的最小总代价；from 记回溯前驱
        val dp = Array(slots.size) { DoubleArray(choices[it].size) { Double.POSITIVE_INFINITY } }
        val from = Array(slots.size) { IntArray(choices[it].size) { -1 } }
        choices[0].forEachIndexed { j, c -> dp[0][j] = c.cost }
        for (i in 1 until slots.size) {
            choices[i].forEachIndexed { j, cur ->
                choices[i - 1].forEachIndexed { k, prev ->
                    val total = dp[i - 1][k] + transition(prev, cur) + cur.cost
                    if (total < dp[i][j]) {
                        dp[i][j] = total
                        from[i][j] = k
                    }
                }
            }
        }
        // 回溯最小代价路径
        val path = IntArray(slots.size)
        var j = dp.last().indices.minBy { dp.last()[it] }
        for (i in slots.size - 1 downTo 0) {
            path[i] = j
            if (i > 0) j = from[i][j]
        }
        return slots.mapIndexed { i, slot ->
            val choice = choices[i][path[i]]
            Assignment(
                bar = slot.bar,
                beat = slot.beat,
                items = slot.notes.mapIndexed { n, note -> Item(note.string, note.fret, choice.fingers[n]) },
            )
        }
    }

    /** 从 TabDocument 按全局小节号 + 拍分组（拍按 1e-3 精度归并），音符按弦号升序保证确定性。 */
    fun slotsOf(doc: TabDocument): List<Slot> {
        val slots = mutableListOf<Slot>()
        var globalBar = 1
        doc.sections.forEach { section ->
            section.bars.forEach { bar ->
                bar.notes
                    .groupBy { Math.round(it.beat * 1000.0) }
                    .toSortedMap()
                    .forEach { (_, notes) ->
                        slots += Slot(globalBar, notes.minOf { it.beat }, notes.sortedBy { it.string })
                    }
                globalBar++
            }
        }
        return slots
    }

    // ---------- 规则校验（也用于 LLM 生成指法） ----------

    /** 返回违规描述列表；空列表 = 通过。 */
    fun validate(assignments: List<Assignment>): List<String> {
        val violations = mutableListOf<String>()
        assignments.forEach { a ->
            val where = "第${a.bar}小节(${a.beat}拍)"
            a.items.forEach { item ->
                if (item.finger !in 0..4) violations += "$where ${item.string}弦${item.fret}品的手指编号非法（0~4）"
                if (item.fret == 0 && item.finger != 0) violations += "$where ${item.string}弦空弦必须用指 0"
                if (item.fret > 0 && item.finger == 0) violations += "$where ${item.string}弦${item.fret}品不能用指 0（空弦专用）"
            }
            if (a.items.groupBy { it.string }.any { it.value.size > 1 }) {
                violations += "$where 同一弦上出现多个同拍音符"
            }
            violations += barreViolations(a, where)
        }
        return violations
    }

    /** 同拍同指多音的横按合法性：必须同品，且跨度内其他音品位不得低于横按品。 */
    private fun barreViolations(a: Assignment, where: String): List<String> {
        val violations = mutableListOf<String>()
        a.items.filter { it.finger > 0 }.groupBy { it.finger }.forEach { (finger, items) ->
            if (items.size < 2) return@forEach
            val frets = items.map { it.fret }.toSet()
            if (frets.size > 1) {
                violations += "$where 指$finger 同拍按了不同品位（${items.joinToString { "${it.string}弦${it.fret}品" }}），只有横按允许同指多弦"
                return@forEach
            }
            val barreFret = frets.first()
            val span = items.minOf { it.string }..items.maxOf { it.string }
            a.items.filter { it.finger != finger && it.string in span }.forEach { inner ->
                if (inner.fret < barreFret) {
                    violations += "$where 指$finger 的横按（${span.first}~${span.last}弦第${barreFret}品）压住了 ${inner.string}弦${inner.fret}品"
                }
            }
        }
        return violations
    }

    // ---------- 候选枚举与代价 ----------

    private data class Choice(val fingers: List<Int>, val frets: List<Int>, val cost: Double)

    private fun candidates(slot: Slot): List<Choice> {
        val notes = slot.notes
        // 不做"非空弦音 ≤4"的快速拒绝：合法横按（如大横按 F）可让 1 指覆盖多音，
        // 可行性由下面的枚举 + 硬规则判定，无解时 best 保持空
        val domain = notes.map { if (it.fret == 0) listOf(0) else (1..4).toList() }
        val out = mutableListOf<Choice>()
        enumerate(domain, 0, IntArray(notes.size)) { fingers ->
            if (legal(notes, fingers)) {
                out += Choice(fingers.toList(), notes.map { it.fret }, intraCost(notes, fingers))
            }
        }
        if (out.isEmpty()) {
            throw IllegalArgumentException("第${slot.bar}小节(${slot.beat}拍)的音符无法用四指分配（横按也不可行）")
        }
        // 按组内代价排序截断：组间转移差异有限，保留头部候选足够 DP 找到全局优
        return out.sortedBy { it.cost }.take(CANDIDATE_LIMIT)
    }

    private fun enumerate(domain: List<List<Int>>, i: Int, current: IntArray, body: (IntArray) -> Unit) {
        if (i == domain.size) {
            body(current)
            return
        }
        domain[i].forEach { f ->
            current[i] = f
            enumerate(domain, i + 1, current, body)
        }
    }

    /** 硬规则：同指多音 = 同品横按，且跨度内其他音品位不得低于横按品。 */
    private fun legal(notes: List<NoteEvent>, fingers: IntArray): Boolean {
        notes.indices.filter { fingers[it] > 0 }.groupBy { fingers[it] }.forEach { (finger, idxs) ->
            if (idxs.size >= 2) {
                val frets = idxs.map { notes[it].fret }.toSet()
                if (frets.size > 1) return false
                val span = idxs.minOf { notes[it].string }..idxs.maxOf { notes[it].string }
                notes.indices.forEach { k ->
                    if (fingers[k] != finger && notes[k].string in span && notes[k].fret < notes[idxs.first()].fret) return false
                }
            }
        }
        return true
    }

    /** 组内代价：指序倒置 / 压缩式跳指 / 超伸展 / 铺开超把位 / 横按宽度。全空弦组为 0。 */
    private fun intraCost(notes: List<NoteEvent>, fingers: IntArray): Double {
        var cost = 0.0
        val active = notes.indices.filter { fingers[it] > 0 }.sortedBy { fingers[it] }
        if (active.isEmpty()) return 0.0
        for (i in 1 until active.size) {
            val gap = notes[active[i]].fret - notes[active[i - 1]].fret
            val fingerGap = fingers[active[i]] - fingers[active[i - 1]]
            if (gap < 0) cost += 2.0 // 指序倒置：指号越大品位反而越低
            if (gap > fingerGap + 2) cost += 1.5 * (gap - fingerGap - 2) // 超伸展
            if (fingerGap > 1 && gap < fingerGap) cost += 0.5 // 压缩式跳指：跳过手指却没跳过对应品位
        }
        val position = active.minOf { notes[it].fret }
        active.forEach { k -> if (notes[k].fret - position > 3) cost += 1.0 * (notes[k].fret - position - 3) } // 同把位优先
        val nonOpenCount = notes.count { it.fret > 0 }
        notes.indices.filter { fingers[it] > 0 }.groupBy { fingers[it] }.forEach { (_, idxs) ->
            // 横按：平底难度溢价 + 随跨度微增；非空弦音 ≤4 时手指本够分，横按属"偷懒"再加一档——
            // 压住"小横按换衔接顺滑"的全局取巧，保住惯用指形（大横按如 F 不受影响）
            if (idxs.size >= 2) {
                cost += 1.0 + 0.1 * (idxs.maxOf { notes[it].string } - idxs.minOf { notes[it].string })
                if (nonOpenCount <= 4) cost += 0.5
            }
        }
        return cost
    }

    /** 组间转移：同指移动距离；换指时检查方向反转与伸展/收缩是否自然。 */
    private fun transition(prev: Choice, cur: Choice): Double {
        var cost = 0.0
        val prevFretByFinger = HashMap<Int, Int>()
        prev.fingers.forEachIndexed { i, f -> if (f > 0) prevFretByFinger.merge(f, prev.frets[i], ::minOf) }
        // 锚点 = 前一组最高手指的位置（旋律组即唯一手指）
        val anchor = prev.fingers.indices.filter { prev.fingers[it] > 0 }.maxByOrNull { prev.fingers[it] }
        var lastFinger = anchor?.let { prev.fingers[it] } ?: 0
        var lastFret = anchor?.let { prev.frets[it] } ?: 0
        cur.fingers.forEachIndexed { i, f ->
            if (f == 0) return@forEachIndexed
            val fret = cur.frets[i]
            prevFretByFinger[f]?.let { cost += 0.5 * kotlin.math.abs(fret - it) } // 同指移动
            if (lastFinger > 0) {
                val fingerGap = f - lastFinger
                val fretGap = fret - lastFret
                if ((fretGap > 0 && fingerGap < 0) || (fretGap < 0 && fingerGap > 0)) cost += 0.75 // 指动与品动反向
                if (fretGap > fingerGap + 2) cost += 1.5 * (fretGap - fingerGap - 2)
                if (fretGap < fingerGap - 1) cost += 0.5 * (fingerGap - 1 - fretGap)
            }
            lastFinger = f
            lastFret = fret
        }
        return cost
    }

    private const val CANDIDATE_LIMIT = 32
}
