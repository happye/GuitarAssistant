package com.guitarcoach.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.core.tab.ExtractResult
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.core.tab.TextTabParser
import com.guitarcoach.app.core.tab.globalBarNumber
import com.guitarcoach.app.core.tab.withBar
import com.guitarcoach.app.core.vision.FrameCodec
import com.guitarcoach.app.data.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 识谱工作台（F104/F201/F202/F203/F204/F205/F207/F208 的宿主）：
 *  - 拍谱路径：拍照/相册 → FrameCodec → LlmTabExtractor → TabDocument（TabPhotoCard）
 *  - 文本谱路径：粘贴 UG 风格六线谱 → TextTabParser
 *  - GP 路径：.gp3~.gp8 → alphaTab → TabDocument（TabImportPanel）
 *  - 展示：列表（可编辑）/ 谱面渲染（点按试听）；讲解基于结构化数据
 * 展示组件拆分在 TabPhotoCard / TabResultList / TabRenderPanel / TabEditPanel / TabImportPanel。
 */
@Composable
fun TabStudioScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // —— 文本谱路径（F104）——
    var pasteText by remember { mutableStateOf("") }
    var document by remember { mutableStateOf<TabDocument?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // —— 拍谱路径（F201）——
    var extracting by remember { mutableStateOf(false) }
    var extractError by remember { mutableStateOf<String?>(null) }
    var extracted by remember { mutableStateOf<ExtractResult?>(null) }
    var imageBase64 by remember { mutableStateOf<String?>(null) } // 仅用于 F208 原图对照（讲解不看图，F204）
    var explainText by remember { mutableStateOf<String?>(null) }
    var explaining by remember { mutableStateOf(false) }

    // —— 展示与编辑 ——
    var editTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) } // F203：sectionIndex to barIndex
    var showRender by remember { mutableStateOf(false) } // F202：展示模式（列表 / 谱面渲染）

    fun extractFrom(uri: Uri) {
        scope.launch {
            extracting = true
            extractError = null
            extracted = null
            explainText = null
            try {
                val base64 = withContext(Dispatchers.IO) {
                    val bitmap = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: throw IllegalArgumentException("图片读取失败，请重试")
                    FrameCodec.toBase64Jpeg(bitmap).also { bitmap.recycle() }
                }
                imageBase64 = base64
                extracted = container.tabExtractor.extract(base64)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                extractError = e.message ?: "识谱失败，请重试"
            } finally {
                extracting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 输入区（可滚动，占剩余空间但不强占——结果列表出现时对半分）
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("识谱工作台", style = MaterialTheme.typography.headlineSmall)
            Text(
                "拍一张谱（或从相册选图）→ AI 识别成结构化谱面 → 逐小节展示与讲解；也支持粘贴文本谱与导入 GP 文件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PhotoExtractCard(
                container = container,
                extracting = extracting,
                extractError = extractError,
                extracted = extracted,
                imageBase64 = imageBase64,
                explaining = explaining,
                onExtract = { extractFrom(it) },
                onExplain = {
                    explaining = true
                    explainText = ""
                    scope.launch {
                        try {
                            val doc = extracted?.document
                            if (doc != null) {
                                container.coach.explainTabDocument(doc).collect { delta ->
                                    explainText = (explainText ?: "") + delta
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            explainText = (explainText ?: "") + "\n\n❌ ${e.message ?: "讲解失败"}"
                        } finally {
                            explaining = false
                        }
                    }
                },
            )

            OutlinedTextField(
                value = pasteText,
                onValueChange = { pasteText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 160.dp), // 限高：文本在框内滚动，防止撑爆页面（用户反馈 bug）
                placeholder = {
                    Text(
                        "或粘贴六线谱，例如：\ne|--------3---|\nB|------3---3-|\nG|----0-------|\nD|--0---------|\nA|------------|\nE|------------|",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        document = null
                        error = null
                        try {
                            document = TextTabParser.parse(pasteText)
                        } catch (e: Exception) {
                            error = e.message ?: "解析失败"
                        }
                    },
                    enabled = pasteText.isNotBlank(),
                ) { Text("解析文本谱") }
                // F205：Guitar Pro 文件导入
                GpImportButton(
                    onDocument = { doc ->
                        extracted = null
                        imageBase64 = null
                        explainText = null
                        error = null
                        document = doc
                    },
                    onError = { error = it },
                )
            }

            error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("❌ $it", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            // M6 扒谱（实验）：抽 PCM 备转写；F602 转写引擎接入后生成谱面
            TranscribeSection(container = container)
        }

        // 展示（拍谱结果优先，其次文本谱/GP 结果）；F203 编辑回写；F202 谱面渲染与点按试听；F502 曲库
        (extracted?.document ?: document)?.let { doc ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilterChip(selected = !showRender, onClick = { showRender = false }, label = { Text("列表") })
                FilterChip(selected = showRender, onClick = { showRender = true }, label = { Text("谱面（点按试听）") })
                SongLibrarySection(
                    container = container,
                    currentDoc = doc,
                    onLoad = { loaded ->
                        extracted = null
                        imageBase64 = null
                        explainText = null
                        document = loaded
                        showRender = false
                    },
                )
            }
            if (showRender) {
                TabRenderPanel(doc, modifier = Modifier.weight(1f))
            } else {
                ParsedTabList(doc, modifier = Modifier.weight(1f), onEditBar = { s, b -> editTarget = s to b })
            }
        }
    }

    editTarget?.let { (si, bi) ->
        (extracted?.document ?: document)?.let { doc ->
            TabBarEditDialog(
                sectionName = doc.sections[si].name,
                barNumber = doc.globalBarNumber(si, bi),
                initial = doc.sections[si].bars[bi],
                onSave = { newBar ->
                    val newDoc = doc.withBar(si, bi, newBar)
                    if (extracted != null) {
                        extracted = extracted?.copy(document = newDoc, warnings = emptyList())
                    } else {
                        document = newDoc
                    }
                    explainText = null // 旧讲解基于修正前数据，作废
                    editTarget = null
                },
                onDismiss = { editTarget = null },
            )
        }
    }

    explainText?.let { text ->
        TabExplainDialog(text = text.ifBlank { "…" }, loading = explaining, onDismiss = { if (!explaining) explainText = null })
    }
}
