package com.guitarcoach.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.guitarcoach.app.core.tab.ExtractResult
import com.guitarcoach.app.core.tab.TabBar
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.core.tab.TextTabParser
import com.guitarcoach.app.core.tab.globalBarNumber
import com.guitarcoach.app.core.tab.midi
import com.guitarcoach.app.core.tab.midiToName
import com.guitarcoach.app.core.tab.withBar
import com.guitarcoach.app.core.vision.FrameCodec
import com.guitarcoach.app.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * F201 识谱工作台：
 *  - 文本谱路径（F104）：粘贴 UG 风格六线谱 → TextTabParser
 *  - 拍谱路径（F201）：拍照/相册 → FrameCodec 压缩 → LlmTabExtractor（视觉链）
 *    → 严格 JSON → 合法性过滤 → TabDocument
 *  - 讲解（F204）：基于识别出的结构化数据流式讲解（识别与讲解分离）；逐句讲解见 F207
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
    var explainText by remember { mutableStateOf<String?>(null) }
    var explaining by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    // F203：正在编辑的小节（sectionIndex to barIndex）
    var editTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }

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
                extracted = container.tabExtractor.extract(base64)
            } catch (e: Exception) {
                extractError = e.message ?: "识谱失败，请重试"
            } finally {
                extracting = false
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingCaptureUri?.let { extractFrom(it) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { extractFrom(it) }
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCaptureUri = uri
        cameraLauncher.launch(uri)
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
            "拍一张谱（或从相册选图）→ AI 识别成结构化谱面 → 逐小节展示与讲解；也支持粘贴文本谱。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // —— 拍谱路径 ——
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("拍谱识谱", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { launchCamera() }, enabled = !extracting) { Text("拍照") }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !extracting,
                    ) { Text("相册选图") }
                    if (extracting) CircularProgressIndicator(Modifier.padding(top = 12.dp))
                }
                extractError?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("❌ $it", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                extracted?.let { result ->
                    result.warnings.forEach { warning ->
                        Text(
                            "⚠️ $warning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // F204：讲解基于识别出的结构化数据（不重新看图），同谱讲解口径一致
                    TextButton(
                        enabled = !explaining,
                        onClick = {
                            explaining = true
                            explainText = ""
                            scope.launch {
                                try {
                                    container.coach.explainTabDocument(result.document).collect { delta ->
                                        explainText = (explainText ?: "") + delta
                                    }
                                } catch (e: Exception) {
                                    explainText = (explainText ?: "") + "\n\n❌ ${e.message ?: "讲解失败"}"
                                } finally {
                                    explaining = false
                                }
                            }
                        },
                    ) { Text(if (explaining) "讲解生成中…" else "让 AI 讲解这张谱怎么弹") }
                    // F207：逐句大白话讲解（只基于已识别的结构化谱面，带覆盖审计）
                    if (result.document.sections.any { it.bars.isNotEmpty() }) {
                        PhraseCoachSection(container = container, doc = result.document)
                    }
                }
            }
        }

        // —— 文本谱路径 ——
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
        }

        // 展示（拍谱结果优先，其次文本谱结果）；F203 支持逐小节编辑回写
        (extracted?.document ?: document)?.let { doc ->
            ParsedTabList(doc, modifier = Modifier.weight(1f), onEditBar = { s, b -> editTarget = s to b })
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

/** 解析结果列表：摘要卡 + 逐小节卡。整体一个 LazyColumn，避免嵌套滚动。 */
@Composable
private fun ParsedTabList(
    doc: TabDocument,
    modifier: Modifier = Modifier,
    onEditBar: (sectionIndex: Int, barIndex: Int) -> Unit = { _, _ -> },
) {
    val totalNotes = doc.sections.sumOf { section -> section.bars.sumOf { it.notes.size } }
    val rows = doc.sections.flatMapIndexed { si, section ->
        section.bars.mapIndexed { bi, bar -> BarRow(si, bi, section.name, doc.globalBarNumber(si, bi), bar) }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "summary") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    doc.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    Text("调弦：${doc.tuning} · 速度：${doc.tempo} BPM", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "共 ${doc.sections.size} 个段落 · ${rows.size} 个小节 · $totalNotes 个音符",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (totalNotes == 0) {
            item(key = "empty-hint") {
                Text(
                    "没有识别到音符——检查谱面是否清晰，或换一张重试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // key 用扁平索引：段落名可能重复（两段都叫 Main），不能拿 名字#小节号 当 key
        itemsIndexed(rows, key = { i, _ -> "bar$i" }) { _, row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${row.sectionName} · 第 ${row.globalNumber} 小节", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { onEditBar(row.sectionIndex, row.barIndex) }) { Text("编辑") }
                    }
                    if (row.bar.notes.isEmpty()) {
                        Text(
                            "（空小节）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            row.bar.notes.joinToString("  ") { note ->
                                "${note.string}弦${note.fret}品(${midiToName(note.midi())})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private data class BarRow(
    val sectionIndex: Int,
    val barIndex: Int,
    val sectionName: String,
    val globalNumber: Int,
    val bar: TabBar,
)
