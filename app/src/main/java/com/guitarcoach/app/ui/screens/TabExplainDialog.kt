package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** F201 拍谱讲解对话框：explainTabImage 的流式输出展示。生成中不可关闭（关闭即中断流式，M2 简化处理）。 */
@Composable
internal fun TabExplainDialog(text: String, loading: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("这张谱怎么弹") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .padding(4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text(if (loading) "生成中…" else "关闭") }
        },
    )
}
