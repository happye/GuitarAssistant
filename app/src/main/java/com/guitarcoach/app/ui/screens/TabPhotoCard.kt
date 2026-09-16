package com.guitarcoach.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.guitarcoach.app.core.tab.ExtractResult
import com.guitarcoach.app.data.AppContainer
import java.io.File

/**
 * F201 拍谱卡：拍照/相册入口 + 识别错误展示 + 讲解与逐句讲解入口。
 * 状态由识谱工作台持有，本组件只管相机/相册 launcher 与展示。
 */
@Composable
internal fun PhotoExtractCard(
    container: AppContainer,
    extracting: Boolean,
    extractError: String?,
    extracted: ExtractResult?,
    imageBase64: String?,
    explaining: Boolean,
    onExtract: (Uri) -> Unit,
    onExplain: () -> Unit,
) {
    val context = LocalContext.current
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingCaptureUri?.let { onExtract(it) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onExtract(it) }
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCaptureUri = uri
        cameraLauncher.launch(uri)
    }

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
                TextButton(enabled = !explaining, onClick = onExplain) {
                    Text(if (explaining) "讲解生成中…" else "让 AI 讲解这张谱怎么弹")
                }
                // F207/F208：逐句大白话讲解（只基于已识别的结构化谱面，带覆盖审计与缓存）
                if (result.document.sections.any { it.bars.isNotEmpty() }) {
                    PhraseCoachSection(container = container, doc = result.document, imageBase64 = imageBase64)
                }
            }
        }
    }
}
