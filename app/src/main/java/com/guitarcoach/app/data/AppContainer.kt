package com.guitarcoach.app.data

import android.content.Context
import com.guitarcoach.app.core.coach.CoachOrchestrator
import com.guitarcoach.app.core.llm.ModelRouter
import com.guitarcoach.app.core.llm.OpenAiCompatClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** 手工依赖容器。四个逻辑客户端共享两个真实账号（方舟 / DeepSeek 官方）。 */
class AppContainer(context: Context) {

    val settings = SettingsStore(context.applicationContext)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 每次请求都读取最新配置 → 设置页改完立即生效，无需重启 App
    private val arkGlm = OpenAiCompatClient("方舟GLM") { settings.current().ark }
    private val dsFast = OpenAiCompatClient("DeepSeek快答") { settings.current().dsFast }
    private val dsReason = OpenAiCompatClient("DeepSeek深思") { settings.current().dsReason }
    private val dsVision = OpenAiCompatClient("DeepSeek视觉") { settings.current().dsVision }

    val router = ModelRouter(
        arkGlm = arkGlm,
        dsFast = dsFast,
        dsReason = dsReason,
        dsVision = dsVision,
    )
    val coach = CoachOrchestrator(router)

    fun shutdown() {
        appScope.cancel()
    }
}
