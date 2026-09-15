package com.guitarcoach.app.core.coach

import com.guitarcoach.app.core.llm.ChatSpec
import com.guitarcoach.app.core.llm.LlmClient
import com.guitarcoach.app.core.tab.NoteEvent
import com.guitarcoach.app.core.tab.TabBar
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.core.tab.TabSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F207+F209 管线集成测试：用假 LlmClient 脚本化回复，验证
 * DP 参考指法注入提示词、LLM 指法过物理规则校验并触发重写、CoVe 不一致进报告。
 */
class PhraseCoachPipelineTest {

    private class FakeLlmClient(private val replies: ArrayDeque<String>) : LlmClient {
        override val name = "fake"
        val prompts = mutableListOf<String>()
        override suspend fun isConfigured() = true
        override fun stream(spec: ChatSpec): Flow<String> = throw UnsupportedOperationException()
        override suspend fun complete(spec: ChatSpec): String {
            prompts += spec.user
            return replies.removeFirst()
        }
    }

    // 2 小节 3 音：bar1 两个同拍音 + bar2 一个音 → 恰好 1 个乐句
    private val doc = TabDocument(
        sections = listOf(
            TabSection(
                "Main",
                listOf(
                    TabBar(listOf(NoteEvent(6, 0, 0.0), NoteEvent(5, 2, 0.0))),
                    TabBar(listOf(NoteEvent(4, 2, 1.0))),
                ),
            ),
        ),
    )

    private fun explainJson(finger65: Int, finger52: Int, finger42: Int) = """
        {"index":1,"summary":"低音起步。",
         "steps":[{"bar":1,"beat":0.0,"string":6,"fret":0,"finger":$finger65,"pick":"下拨","text":"空弦"},
                  {"bar":1,"beat":0.0,"string":5,"fret":2,"finger":$finger52,"pick":"下拨","text":"2品"},
                  {"bar":2,"beat":1.0,"string":4,"fret":2,"finger":$finger42,"pick":"上拨","text":"2品"}],
         "terms":[],"difficulty":"无","practice":"慢练"}
    """.trimIndent()

    private val coveJson = """{"bars":[{"bar":1,"notes":[[6,0],[5,2]]},{"bar":2,"notes":[[4,2]]}]}"""

    @Test
    fun `合法讲解一轮通过且提示词含DP参考指法`() = runBlocking {
        val fake = FakeLlmClient(ArrayDeque(listOf(explainJson(0, 1, 1), coveJson)))
        val report = PhraseCoach({ listOf(fake) }).explain(doc)
        assertTrue(report.auditClean)
        assertEquals(1, report.rounds)
        assertTrue(fake.prompts[0].contains("参考指法"))
        assertTrue(fake.prompts[0].contains("6弦0品=指0"))
    }

    @Test
    fun `同拍同指跨品被规则校验拦截并重写通过`() = runBlocking {
        // 第一轮 6弦0(空弦)用指1 且与 5弦2 同指同拍 → 规则拦截；第二轮改对
        val fake = FakeLlmClient(ArrayDeque(listOf(explainJson(1, 1, 1), explainJson(0, 1, 1), coveJson)))
        val report = PhraseCoach({ listOf(fake) }).explain(doc)
        assertTrue(report.auditClean)
        assertEquals(2, report.rounds)
    }

    @Test
    fun `CoVe重列与谱面不一致进报告`() = runBlocking {
        val badCove = """{"bars":[{"bar":1,"notes":[[6,0]]},{"bar":2,"notes":[[4,2]]}]}"""
        val fake = FakeLlmClient(ArrayDeque(listOf(explainJson(0, 1, 1), badCove)))
        val report = PhraseCoach({ listOf(fake) }).explain(doc)
        assertTrue(report.coveNotes.any { it.contains("少") })
    }
}
