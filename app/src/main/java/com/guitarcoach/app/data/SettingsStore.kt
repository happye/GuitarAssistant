package com.guitarcoach.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.guitarcoach.app.core.llm.ProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "coach_settings")

/**
 * 四个逻辑客户端的配置：
 *  - ark       火山方舟 · GLM（文本备份；调用前需在方舟控制台开通模型，见 docs/模型API接入手册.md）
 *  - dsFast    DeepSeek 非思考形态（快答，速度优先）—— 实测思考开关靠选 id，不靠参数
 *  - dsReason  DeepSeek 思考形态（规划/复盘等深度任务）
 *  - dsVision  DeepSeek 视觉实验版（识谱/看手型主力，实测可识别图片）
 */
data class AppSettings(
    val ark: ProviderConfig,
    val dsFast: ProviderConfig,
    val dsReason: ProviderConfig,
    val dsVision: ProviderConfig,
)

/**
 * 模型连接配置。个人使用场景：密钥只存本机 DataStore，绝不随谱面图片一起上传；
 * 若未来公开发布，改为自建轻量代理转发，密钥不出服务端。
 */
class SettingsStore(private val context: Context) {

    private val kArkBase = stringPreferencesKey("ark_base")
    private val kArkModel = stringPreferencesKey("ark_model")
    private val kArkKey = stringPreferencesKey("ark_key")
    private val kDsBase = stringPreferencesKey("ds_base")
    private val kDsKey = stringPreferencesKey("ds_key")
    private val kDsModelFast = stringPreferencesKey("ds_model_fast")
    private val kDsModelReason = stringPreferencesKey("ds_model_reason")
    private val kDsModelVision = stringPreferencesKey("ds_model_vision")

    val config: Flow<AppSettings> = context.dataStore.data.map { p ->
        val dsBase = p[kDsBase] ?: DEFAULT_DS_BASE
        val dsKey = p[kDsKey] ?: ""
        AppSettings(
            ark = ProviderConfig(
                name = "方舟GLM",
                baseUrl = p[kArkBase] ?: DEFAULT_ARK_BASE,
                modelId = p[kArkModel] ?: DEFAULT_ARK_MODEL,
                apiKey = p[kArkKey] ?: "",
            ),
            dsFast = ProviderConfig(
                name = "DeepSeek快答",
                baseUrl = dsBase,
                modelId = p[kDsModelFast] ?: DEFAULT_DS_MODEL_FAST,
                apiKey = dsKey,
            ),
            dsReason = ProviderConfig(
                name = "DeepSeek深思",
                baseUrl = dsBase,
                modelId = p[kDsModelReason] ?: DEFAULT_DS_MODEL_REASON,
                apiKey = dsKey,
            ),
            dsVision = ProviderConfig(
                name = "DeepSeek视觉",
                baseUrl = dsBase,
                modelId = p[kDsModelVision] ?: DEFAULT_DS_MODEL_VISION,
                apiKey = dsKey,
            ),
        )
    }

    suspend fun current(): AppSettings = config.first()

    suspend fun saveArk(baseUrl: String, modelId: String, apiKey: String) {
        context.dataStore.edit { p ->
            p[kArkBase] = baseUrl.trim()
            p[kArkModel] = modelId.trim()
            p[kArkKey] = apiKey.trim()
        }
    }

    suspend fun saveDeepseek(baseUrl: String, apiKey: String, modelFast: String, modelReason: String, modelVision: String) {
        context.dataStore.edit { p ->
            p[kDsBase] = baseUrl.trim()
            p[kDsKey] = apiKey.trim()
            p[kDsModelFast] = modelFast.trim()
            p[kDsModelReason] = modelReason.trim()
            p[kDsModelVision] = modelVision.trim()
        }
    }

    companion object {
        // 2026-09-15 真实 key 实测结果（过程与依据详见 docs/模型API接入手册.md）：
        //  - 火山方舟：端点/Bearer 鉴权已实测正确；模型 id 必须用带日期后缀的全名（点号版 404）；
        //    调用前需在方舟控制台「开通管理」里开通该模型，否则报 ModelNotOpen（伪装成 404）
        //  - DeepSeek：deepseek-flash=思考开、deepseek-chat=思考关（同一模型两种形态）；
        //    视觉用 -vision-exp（实测识别图片成功）；enable_thinking 参数实测静默无效，勿依赖
        const val DEFAULT_ARK_BASE = "https://ark.cn-beijing.volces.com/api/v3"
        const val DEFAULT_ARK_MODEL = "glm-5-3-flash-260828"
        const val DEFAULT_DS_BASE = "https://api.deepseek.com"
        const val DEFAULT_DS_MODEL_FAST = "deepseek-chat"
        const val DEFAULT_DS_MODEL_REASON = "deepseek-flash"
        const val DEFAULT_DS_MODEL_VISION = "deepseek-v4-flash-vision-exp"
    }
}
