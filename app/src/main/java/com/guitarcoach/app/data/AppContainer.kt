package com.guitarcoach.app.data

import android.content.Context
import com.guitarcoach.app.core.coach.CoachOrchestrator
import com.guitarcoach.app.core.llm.ModelRouter
import com.guitarcoach.app.core.llm.OpenAiCompatClient
import com.guitarcoach.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** 手工依赖容器。四个逻辑客户端共享两个真实账号（方舟 / DeepSeek 官方）。 */
class AppContainer(context: Context) {

    val settings = SettingsStore(context.applicationContext)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 每次请求都读取最新配置 → 设置页改完立即生效，无需重启 App
    // （configProvider 用命名参数传递：构造器末位是带默认值的 http，尾 lambda 会被误绑定到它）
    private val arkGlm = OpenAiCompatClient("方舟GLM", configProvider = { settings.current().ark })
    private val dsFast = OpenAiCompatClient("DeepSeek快答", configProvider = { settings.current().dsFast })
    private val dsReason = OpenAiCompatClient("DeepSeek深思", configProvider = { settings.current().dsReason })
    private val dsVision = OpenAiCompatClient("DeepSeek视觉", configProvider = { settings.current().dsVision })

    val router = ModelRouter(
        arkGlm = arkGlm,
        dsFast = dsFast,
        dsReason = dsReason,
        dsVision = dsVision,
    )
    val coach = CoachOrchestrator(router)

    // Room：对话历史（F107）与练习记录（F106）共用一个库
    private val appContext = context.applicationContext
    private val database by lazy { AppDatabase.build(appContext) }
    val chatRepository by lazy { ChatRepository(database) }

    fun shutdown() {
        appScope.cancel()
    }
}
