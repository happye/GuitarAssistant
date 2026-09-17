package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.data.AppSettings
import com.guitarcoach.app.data.AppContainer
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<AppSettings?>(null) }
    var editing by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        settings = container.settings.current()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🎸 吉他学习助手", style = MaterialTheme.typography.headlineMedium)
        Text(
            "私人电吉他教练：识谱 · 纠手型 · 讲乐理",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing = true }) { Text("模型设置") }
            OutlinedButton(
                enabled = !testing && settings != null,
                onClick = {
                    scope.launch {
                        testing = true
                        testResult = try {
                            val sb = StringBuilder()
                            container.coach
                                .askTheory("请用一句话回答：标准调弦下6弦空弦是什么音？")
                                .collect { sb.append(it) }
                            "✅ ${sb.toString().ifBlank { "(空回复)" }}"
                        } catch (e: Exception) {
                            "❌ ${e.message}"
                        }
                        testing = false
                    }
                },
            ) { Text(if (testing) "测试中…" else "测试连通") }
        }
        testResult?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        settings?.let { s ->
            ProviderCard(
                title = "${s.ark.name} — 文本备份（需在方舟控制台开通模型）",
                modelLines = listOf(s.ark.modelId),
                baseUrl = s.ark.baseUrl,
                configured = s.ark.isConfigured,
            )
            ProviderCard(
                title = "DeepSeek — 主力（快答 / 深思 / 视觉）",
                modelLines = listOf(s.dsFast.modelId, s.dsReason.modelId, s.dsVision.modelId),
                baseUrl = s.dsFast.baseUrl,
                configured = s.dsFast.isConfigured,
            )
        }

        // F707 AI 今日练习单（集百家之长 Top7：LLM+Room 组合）
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("AI 今日练习单（实验）", style = MaterialTheme.typography.titleSmall)
                Text(
                    "结合最近一周练习记录与曲库在学曲目，安排今天 2~4 个练习项。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var planText by rememberSaveable { mutableStateOf<String?>(null) }
                var planning by remember { mutableStateOf(false) }
                var planError by rememberSaveable { mutableStateOf<String?>(null) }
                val planScope = rememberCoroutineScope()
                Button(
                    enabled = !planning,
                    onClick = {
                        planning = true
                        planError = null
                        planText = ""
                        planScope.launch {
                            try {
                                val stats = withContext(Dispatchers.IO) {
                                    val recent = container.practiceRepository.observeRecords().first()
                                    val songs = container.songRepository.observeAll().first()
                                        .filter { it.progress != com.guitarcoach.app.data.SongRepository.PROGRESS_SHELVED }
                                        .take(10)
                                        .joinToString("、") { "${it.title}（${com.guitarcoach.app.data.SongRepository.PROGRESS_LABELS[it.progress]}）" }
                                    com.guitarcoach.app.core.coach.WeeklyReview.summarize(
                                        recent.map { com.guitarcoach.app.core.coach.WeeklyReview.Record(it.startedAt, it.durationSeconds, it.content) },
                                        nowMs = System.currentTimeMillis(),
                                    ).toPromptText() + if (songs.isBlank()) "" else "\n曲库在学：$songs"
                                }
                                container.coach.practicePlan(stats).collect { delta ->
                                    if (delta.isNotEmpty()) planText = (planText ?: "") + delta
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                planError = e.message ?: "生成失败，请重试"
                            } finally {
                                planning = false
                            }
                        }
                    },
                ) { Text(if (planning) "安排中…" else "生成今日练习单") }
                planText?.let {
                    Text(it.ifBlank { "…" }, style = MaterialTheme.typography.bodyMedium)
                }
                planError?.let { Text("❌ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("功能与进度（v${com.guitarcoach.app.BuildConfig.VERSION_NAME}）", style = MaterialTheme.typography.titleMedium)
                Text("✅ 已可用：识谱（拍谱/粘贴/GP 导入）· 谱面渲染与点按试听 · 逐句讲解 · 乐理问答 · 移调计算器 · 指板可视化 · 调音器 · 节拍器 · 练习记录 · 视觉教练 · 跟练判定 · 曲库 · AI 周复盘 · 音色向导", style = MaterialTheme.typography.bodySmall)
                Text("⏳ 待真机验收：以上功能在小米 14 上逐项确认后进入正式版（装 GitHub Releases 最新包即可参与）", style = MaterialTheme.typography.bodySmall)
                Text("🔬 后续：音频转谱（F602 转写引擎接入中）、五线谱双谱渲染、按弦手位精纠", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("提示", style = MaterialTheme.typography.titleSmall)
                Text(
                    "在「模型设置」里填入火山方舟与 DeepSeek 的 API Key（只存本机）。两家互为备份：任一失败自动切换另一家。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (editing && settings != null) {
        SettingsDialog(
            initial = settings!!,
            onDismiss = { editing = false },
            onSave = { ark, ds ->
                editing = false
                scope.launch {
                    container.settings.saveArk(ark.first, ark.second, ark.third)
                    container.settings.saveDeepseek(ds.first, ds.second, ds.third, ds.fourth, ds.fifth)
                    settings = container.settings.current()
                }
            },
        )
    }
}

@Composable
private fun ProviderCard(title: String, modelLines: List<String>, baseUrl: String, configured: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            modelLines.forEach { model ->
                Text("模型：$model", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "接口：$baseUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (configured) "状态：✅ 已配置 Key" else "状态：⚠️ 未配置 Key",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private data class SettingsForm(
    val arkBase: String, val arkModel: String, val arkKey: String,
    val dsBase: String, val dsKey: String,
    val dsFast: String, val dsReason: String, val dsVision: String,
)

@Composable
private fun SettingsDialog(
    initial: AppSettings,
    onDismiss: () -> Unit,
    onSave: (Triple<String, String, String>, Quintuple<String, String, String, String, String>) -> Unit,
) {
    var form by remember {
        mutableStateOf(
            SettingsForm(
                arkBase = initial.ark.baseUrl,
                arkModel = initial.ark.modelId,
                arkKey = initial.ark.apiKey,
                dsBase = initial.dsFast.baseUrl,
                dsKey = initial.dsFast.apiKey,
                dsFast = initial.dsFast.modelId,
                dsReason = initial.dsReason.modelId,
                dsVision = initial.dsVision.modelId,
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模型设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("火山方舟 · GLM（文本备份）", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(value = form.arkBase, onValueChange = { form = form.copy(arkBase = it) }, label = { Text("Base URL") }, singleLine = true)
                OutlinedTextField(value = form.arkModel, onValueChange = { form = form.copy(arkModel = it) }, label = { Text("模型 ID") }, singleLine = true)
                OutlinedTextField(
                    value = form.arkKey,
                    onValueChange = { form = form.copy(arkKey = it) },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Text("DeepSeek（主力）", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(value = form.dsBase, onValueChange = { form = form.copy(dsBase = it) }, label = { Text("Base URL") }, singleLine = true)
                OutlinedTextField(
                    value = form.dsKey,
                    onValueChange = { form = form.copy(dsKey = it) },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(value = form.dsFast, onValueChange = { form = form.copy(dsFast = it) }, label = { Text("快答模型（非思考）") }, singleLine = true)
                OutlinedTextField(value = form.dsReason, onValueChange = { form = form.copy(dsReason = it) }, label = { Text("深思模型（思考）") }, singleLine = true)
                OutlinedTextField(value = form.dsVision, onValueChange = { form = form.copy(dsVision = it) }, label = { Text("视觉模型") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    Triple(form.arkBase, form.arkModel, form.arkKey),
                    Quintuple(form.dsBase, form.dsKey, form.dsFast, form.dsReason, form.dsVision),
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 轻量五元组（Kotlin 标准库没有内置 Quintuple）。 */
data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
