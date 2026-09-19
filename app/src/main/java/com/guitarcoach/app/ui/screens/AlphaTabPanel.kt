@file:OptIn(kotlin.contracts.ExperimentalContracts::class)

package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import alphaTab.AlphaTabView
import alphaTab.synth.PlayerState
import com.guitarcoach.app.core.tab.TabDocument
import com.guitarcoach.app.core.tab.TabScoreProjector

/**
 * A2+A3（重构方案 §1.2）：alphaTab 专业谱面（Compose AndroidView 封装）。
 * - TabDocument → alphaTex → Score 一次性投影（TabScoreProjector，doc 变更整体重建）
 * - AlphaSynth 波表音源播放（AAR 自带 sonivox.sf2，零配置）；内置光标跟随
 * - KS 试听（TonePlayer）保留在自绘面板，两路互不影响
 */
@Composable
fun AlphaTabPanel(doc: TabDocument, modifier: Modifier = Modifier) {
    var playing by remember { mutableStateOf(false) }
    var viewRef by remember { mutableStateOf<AlphaTabView?>(null) }
    val barCount = doc.sections.sumOf { it.bars.size }

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "调弦 ${doc.tuning} · ${doc.tempo} BPM · $barCount 小节",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { viewRef?.api?.playPause() },
                enabled = viewRef != null,
            ) { Text(if (playing) "■ 停止" else "▶ 播放") }
        }
        if (doc.chordTrack.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "和弦: " + doc.chordTrack.take(16).joinToString(" ") { c ->
                        val barNo = (c.beat / 4).toInt() + 1
                        "$barNo:${c.display}"
                    } + if (doc.chordTrack.size > 16) " …" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        AndroidView(
            factory = { ctx ->
                AlphaTabView(ctx, null).apply { // XML 风格构造器（内部 inflate alphatab_view.xml）
                    // 纸面：renderer 默认出浅色纸面，view 底色给白保证对比（深色 App 内嵌"纸"是常规形态）
                    setBackgroundColor(android.graphics.Color.WHITE)
                    // 投影失败不崩 App：落日志（tex 全文+诊断）并交由上层回退自绘视图
                    val score = try {
                        TabScoreProjector.toScore(doc, settings)
                    } catch (e: Throwable) {
                        // alphaTab 的 AlphaTabError 直接继承 Throwable（不是 Exception）——必须 catch Throwable
                        val texDump = TabScoreProjector.toAlphaTex(doc)
                        android.util.Log.e("GuitarCoach", "专业谱面投影失败 tex=<<<$texDump>>>", e)
                        throw e
                    }
                    val track = score.tracks.firstOrNull()
                    if (track != null) tracks = listOf(track)
                    api.playerStateChanged.on { st ->
                        playing = st.state == PlayerState.Playing
                    }
                    // F202 规格点按试听：点哪个音符听哪个（AlphaSynth 单音）
                    api.noteMouseDown.on { note -> api.playNote(note) }
                    viewRef = this
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
            update = { v ->
                if (viewRef !== v) viewRef = v
            },
        )
    }
    DisposableEffect(doc) {
        onDispose {
            // 离开组合（切模式/换文档）即停播，防音频线程残留
            runCatching { viewRef?.api?.stop() }
            viewRef = null
        }
    }
}
