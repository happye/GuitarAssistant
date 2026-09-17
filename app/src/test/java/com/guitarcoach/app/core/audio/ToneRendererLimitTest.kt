package com.guitarcoach.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class ToneRendererLimitTest {
    @Test
    fun `超600秒音频拒绝渲染不巨量分配`() {
        val events = listOf(ToneRenderer.ToneEvent(0.0, 40, 601.0))
        val e = try {
            ToneRenderer.render(events, 44100)
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        org.junit.Assert.assertNotNull("应抛 IAE", e)
        org.junit.Assert.assertTrue(e!!.message!!.contains("时长超限"))
    }

    @Test
    fun `正常时长渲染不受影响`() {
        val pcm = ToneRenderer.render(listOf(ToneRenderer.ToneEvent(0.0, 69, 0.5)), 44100)
        org.junit.Assert.assertEquals(22050, pcm.size)
    }
}
