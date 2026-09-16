package com.guitarcoach.app.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.guitarcoach.app.core.tab.GpImporter
import com.guitarcoach.app.core.tab.TabDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * F205：Guitar Pro 文件导入按钮（.gp3~.gp8，文件选择器走 OpenDocument，
 * GP 系列无标准 MIME 所以放宽为 *）。解析在 IO 线程，结果经回调交回识谱工作台展示。
 */
@Composable
fun GpImportButton(onDocument: (TabDocument) -> Unit, onError: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            try {
                val doc = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalArgumentException("文件读取失败，请重试")
                    GpImporter.import(bytes)
                }
                onDocument(doc)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "GP 导入失败，请重试")
            } finally {
                importing = false
            }
        }
    }

    Button(onClick = { launcher.launch(arrayOf("*/*")) }, enabled = !importing, modifier = modifier) {
        Text(if (importing) "GP 解析中…" else "导入 GP 文件")
    }
}
