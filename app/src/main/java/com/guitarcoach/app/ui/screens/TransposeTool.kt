package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guitarcoach.app.core.music.TransposeCalculator

/**
 * F206 移调/变调夹计算器对话框：纯本地乐理换算，不走模型、不联网。
 * 两个模式：移调（原调→新调 + 和弦进行换算）、变调夹（原调+指法调→夹几品，反查用哪调指法）。
 */
@Composable
fun TransposeDialog(onDismiss: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("移调 / 变调夹计算器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TabRow(selectedTabIndex = mode) {
                    Tab(selected = mode == 0, onClick = { mode = 0 }, text = { Text("移调") })
                    Tab(selected = mode == 1, onClick = { mode = 1 }, text = { Text("变调夹") })
                }
                if (mode == 0) TransposeMode() else CapoMode()
            }
        },
    )
}

@Composable
private fun TransposeMode() {
    var fromKey by remember { mutableStateOf("C") }
    var toKey by remember { mutableStateOf("G") }
    var chords by remember { mutableStateOf("") }

    val diff = TransposeCalculator.semitonesUp(fromKey, toKey)
    KeyField(label = "原调", value = fromKey, onValueChange = { fromKey = it })
    KeyField(label = "新调", value = toKey, onValueChange = { toKey = it })

    when (diff) {
        null -> HintText("两个调都填合法音名（如 C、F#、Bb）后自动换算")
        0 -> HintText("同调，无需移调")
        else -> HintText("上移 $diff 半音（等价于下移 ${12 - diff} 半音）")
    }

    OutlinedTextField(
        value = chords,
        onValueChange = { chords = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("和弦进行（可选，空格分隔，如 C G Am F）") },
        singleLine = true,
    )
    if (chords.isNotBlank() && diff != null) {
        val tokens = chords.trim().split(Regex("\\s+"))
        val out = TransposeCalculator.transposeChords(tokens, diff)
        val unknown = out.withIndex().filter { it.value == null }.map { tokens[it.index] }
        if (unknown.isEmpty()) {
            ResultText(out.filterNotNull().joinToString("  "))
        } else {
            HintText("有 ${unknown.size} 个不认识（${unknown.joinToString("、")}），已跳过")
        }
    }
}

@Composable
private fun CapoMode() {
    var originalKey by remember { mutableStateOf("C") }
    var shapeKey by remember { mutableStateOf("G") }
    var fretText by remember { mutableStateOf("") }

    KeyField(label = "歌曲原调", value = originalKey, onValueChange = { originalKey = it })
    KeyField(label = "想用的指法调", value = shapeKey, onValueChange = { shapeKey = it })
    when (val fret = TransposeCalculator.capoFret(originalKey, shapeKey)) {
        null -> HintText("两个调都填合法音名后自动换算")
        0 -> HintText("同调，不用夹")
        else -> HintText("夹第 $fret 品，用 $shapeKey 调指法 = ${originalKey.uppercase()} 调效果")
    }

    OutlinedTextField(
        value = fretText,
        onValueChange = { fretText = it.filter { c -> c.isDigit() }.take(2) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("反查：已夹了几品？（可选）") },
        singleLine = true,
    )
    val fret = fretText.toIntOrNull()
    if (fret != null && fret in 1..11) {
        val shape = TransposeCalculator.shapeKeyForCapo(originalKey, fret)
        val sound = TransposeCalculator.soundingKey(shape ?: "", fret)
        if (shape != null && sound != null) {
            ResultText("夹 $fret 品时用 $shape 调指法（弹出来是 $sound 调）")
        }
    }
}

/** 调名输入框：非空但非法时标红。 */
@Composable
private fun KeyField(label: String, value: String, onValueChange: (String) -> Unit) {
    val invalid = value.isNotBlank() && TransposeCalculator.parseNote(value) == null
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = invalid,
        supportingText = if (invalid) {
            { Text("不认识这个调名，试试 C / F# / Bb") }
        } else {
            null
        },
        singleLine = true,
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ResultText(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
