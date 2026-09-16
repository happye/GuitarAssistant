package com.guitarcoach.app.core.vision

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * F302 姿势规则警报（0ms 层）：MediaPipe 手部 21 点 → 高置信度问题警报。
 *
 * 口径（L005/L006）：只报高置信度问题；单次 ≤2 条；单目不判品（正确性真相源是音频）。
 * 折腕近似说明：手部 21 点没有肘关节，方案 §8.2 的"肘-腕-食指 MCP 角"在此降级为
 * "腕-中指MCP-中指PIP 手背弯折角"——手背与指节夹角过小即手腕弯折；如需真实肘角需引入
 * 全身姿态估计（M3 后期评估）。阈值用合成手型单测标定（PostureRulesTest）。
 */
object PostureRules {

    // MediaPipe 手部 21 点索引
    private const val WRIST = 0
    private const val THUMB_TIP = 4
    private const val INDEX_MCP = 5
    private const val INDEX_PIP = 6
    private const val INDEX_TIP = 8
    private const val MIDDLE_MCP = 9
    private const val MIDDLE_PIP = 10
    private const val MIDDLE_TIP = 12
    private const val RING_TIP = 16
    private const val PINKY_TIP = 20

    data class Pt(val x: Float, val y: Float)

    data class Alert(val rule: String, val severity: String, val message: String)

    /** 单手警报。palm 为 21 点（MediaPipe 索引序）。返回 ≤2 条，按严重度排序。 */
    fun check(palm: List<Pt>): List<Alert> {
        if (palm.size < 21) return emptyList()
        val palmSize = dist(palm[WRIST], palm[MIDDLE_MCP]).coerceAtLeast(1e-4f)
        val alerts = mutableListOf<Alert>()

        // 1) 手背弯折（折腕近似）：腕-MCP-PIP 夹角 < 150°
        val bendAngle = angleAt(palm[MIDDLE_MCP], palm[WRIST], palm[MIDDLE_PIP])
        if (bendAngle < WRIST_BEND_DEG) {
            alerts += Alert(
                "wrist_bend", "medium",
                "手腕弯折（手背不直）——放松手腕，让手背与小臂大致平齐",
            )
        }

        // 2) 指尖塌陷：四指里最严重的一根（指尖到手腕距离 / 掌长 < 阈值）
        val fingers = listOf(
            Triple("食指", INDEX_TIP, INDEX_PIP),
            Triple("中指", MIDDLE_TIP, MIDDLE_PIP),
            Triple("无名指", RING_TIP, 14),
            Triple("小指", PINKY_TIP, 18),
        )
        var worst: Alert? = null
        var worstRatio = Float.MAX_VALUE
        for ((name, tip, pip) in fingers) {
            val ratio = dist(palm[tip], palm[WRIST]) / palmSize
            if (ratio < COLLAPSE_RATIO && ratio < worstRatio) {
                worstRatio = ratio
                worst = Alert("finger_collapse", "high", "$name 塌陷——指尖立起来，用指尖顶部垂直按弦")
            }
        }
        worst?.let { alerts += it }

        // 3) 拇指位置：拇指尖与食指根的距离 / 掌长，过大=外张、过小=藏掌后
        val thumbRatio = dist(palm[THUMB_TIP], palm[INDEX_MCP]) / palmSize
        when {
            thumbRatio > THUMB_OUT_RATIO -> alerts += Alert(
                "thumb_out", "medium",
                "拇指过于外张——拇指轻贴琴颈背后中线，起支点作用",
            )
            thumbRatio < THUMB_HIDDEN_RATIO -> alerts += Alert(
                "thumb_hidden", "medium",
                "拇指藏到掌后了——虎口留出空间，拇指背对琴颈",
            )
        }

        return alerts.sortedByDescending { it.severity == "high" }.take(2)
    }

    /** 双手是否都在画面内（留 margin，出框给引导不硬猜）。 */
    fun allInFrame(hands: List<List<Pt>>, margin: Float = 0.02f): Boolean =
        hands.all { hand -> hand.all { it.x >= -margin && it.x <= 1 + margin && it.y >= -margin && it.y <= 1 + margin } }

    private fun dist(a: Pt, b: Pt): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    /** 以 center 为顶点、ca/cb 为两边的夹角（度）。 */
    private fun angleAt(center: Pt, ca: Pt, cb: Pt): Float {
        val v1x = ca.x - center.x
        val v1y = ca.y - center.y
        val v2x = cb.x - center.x
        val v2y = cb.y - center.y
        val dot = v1x * v2x + v1y * v2y
        val n = (sqrt(v1x * v1x + v1y * v1y) * sqrt(v2x * v2x + v2y * v2y)).coerceAtLeast(1e-6f)
        return Math.toDegrees(acos((dot / n).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }

    // 阈值（合成手型单测标定；真机验收后按误报率微调）
    private const val WRIST_BEND_DEG = 150f
    private const val COLLAPSE_RATIO = 1.1f
    private const val THUMB_OUT_RATIO = 1.8f
    private const val THUMB_HIDDEN_RATIO = 0.25f
}
