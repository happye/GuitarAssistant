package com.guitarcoach.app.core.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F302 阈值标定：合成"正常手型"不误报，"折腕/塌指/拇指异常/出框"分别命中。
 * 坐标系：y 向下（图像口径），手竖直朝上摆放。
 */
class PostureRulesTest {

    private fun pt(x: Float, y: Float) = PostureRules.Pt(x, y)

    /** 正常手：腕在下方，四指向上伸展，拇指在侧。返回 21 点（未用的点随便放在合理位置）。 */
    private fun normalHand(): MutableList<PostureRules.Pt> {
        val pts = MutableList(21) { pt(0.5f, 0.8f) }
        pts[0] = pt(0.5f, 0.90f)   // wrist
        pts[5] = pt(0.44f, 0.76f)  // index MCP
        pts[6] = pt(0.44f, 0.66f)  // index PIP
        pts[8] = pt(0.44f, 0.52f)  // index TIP
        pts[9] = pt(0.50f, 0.75f)  // middle MCP
        pts[10] = pt(0.50f, 0.64f) // middle PIP
        pts[12] = pt(0.50f, 0.50f) // middle TIP
        pts[13] = pt(0.56f, 0.76f) // ring MCP
        pts[16] = pt(0.56f, 0.54f) // ring TIP
        pts[17] = pt(0.61f, 0.78f) // pinky MCP
        pts[20] = pt(0.62f, 0.60f) // pinky TIP
        pts[4] = pt(0.36f, 0.82f)  // thumb TIP（虎口侧）
        return pts
    }

    @Test
    fun `正常手型不误报`() {
        val alerts = PostureRules.check(normalHand())
        assertEquals(emptyList<PostureRules.Alert>(), alerts)
        assertTrue(PostureRules.allInFrame(listOf(normalHand())))
    }

    @Test
    fun `折腕手型命中弯折警报`() {
        val hand = normalHand()
        // 中指 PIP 向侧面折，使 腕-MCP-PIP 夹角 < 150°
        hand[10] = pt(0.66f, 0.70f)
        val alerts = PostureRules.check(hand)
        assertTrue(alerts.any { it.rule == "wrist_bend" })
    }

    @Test
    fun `塌指命中塌陷警报`() {
        val hand = normalHand()
        // 食指指尖收回到掌边（指尖到手腕距离 / 掌长 < 1.1）
        hand[8] = pt(0.46f, 0.80f)
        val alerts = PostureRules.check(hand)
        assertTrue(alerts.any { it.rule == "finger_collapse" && it.message.contains("食指") })
    }

    @Test
    fun `拇指外张与藏掌后分别命中`() {
        val out = normalHand()
        out[4] = pt(0.15f, 0.95f)
        assertTrue(PostureRules.check(out).any { it.rule == "thumb_out" })

        val hidden = normalHand()
        hidden[4] = pt(0.46f, 0.765f)
        assertTrue(PostureRules.check(hidden).any { it.rule == "thumb_hidden" })
    }

    @Test
    fun `警报最多两条且高危优先`() {
        val hand = normalHand()
        hand[10] = pt(0.66f, 0.70f)  // 折腕
        hand[8] = pt(0.46f, 0.80f)   // 塌指（high）
        hand[4] = pt(0.15f, 0.95f)   // 拇指外张
        val alerts = PostureRules.check(hand)
        assertEquals(2, alerts.size)
        assertEquals("finger_collapse", alerts[0].rule) // high 排最前
    }

    @Test
    fun `出框检测`() {
        val hand = normalHand()
        hand[20] = pt(1.05f, 0.60f)
        assertTrue(!PostureRules.allInFrame(listOf(hand)))
    }

    @Test
    fun `点数不足直接放行不误报`() {
        assertEquals(emptyList<PostureRules.Alert>(), PostureRules.check(normalHand().take(10)))
    }
}
