package com.guitarcoach.app.data

import android.content.Context
import android.net.Uri
import com.guitarcoach.app.core.audio.AudioPcmExtractor
import com.guitarcoach.app.core.audio.BeatTracker
import com.guitarcoach.app.core.audio.ChordTracker
import com.guitarcoach.app.core.audio.InputClassifier
import com.guitarcoach.app.core.audio.NoteCleaner
import com.guitarcoach.app.core.audio.SpleeterSeparator
import com.guitarcoach.app.core.audio.TranscriptionEngine
import com.guitarcoach.app.core.music.BeatGrid
import com.guitarcoach.app.core.tab.MidiTabConverter
import com.guitarcoach.app.core.tab.TabDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * P0-1（重构方案 §1.1）：扒谱管线编排器（data 层）——引擎构造与算法链全部从 UI 迁出（修 V4 违规）。
 * 管线：PCM 抽取 →（按需）分离 → 逐拍跟踪 → 转写 → 清洗 → 量化+弦品 DP → 分类 → 和弦级（混音）→ TabDocument。
 * 每步诊断打 logcat（tag=GuitarCoach），UI 只收进度字符串与结果。
 */
class TranscriptionController(private val context: Context) {

    data class Outcome(
        val doc: TabDocument,
        val skippedOutOfRange: Int,
        val truncated: Int,
        val material: InputClassifier.Material,
        val notesKept: Int,
    )

    suspend fun transcribe(
        uri: Uri,
        manualBpm: Int?,
        enhance: Boolean,
        onProgress: (String) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        val (pcm, sampleRate) = extractPcm(uri, enhance, onProgress)

        // 时序层 v2：手输 BPM → 恒速网格；否则逐拍跟踪（曲内漂移吸收，L024/E011 前置）
        onProgress("检测节拍中…")
        val grid = manualBpm?.let { BeatGrid.constant(it, pcmDurationSec(pcm)) }
            ?: BeatTracker.track(pcm, sampleRate)
            ?: BeatGrid.constant(120, pcmDurationSec(pcm)) // 跟踪失败回退，不预填假值（L025）
        android.util.Log.d("GuitarCoach", "节拍诊断: bpm=%.1f beats/bar=%d 手输=%b".format(grid.bpm, grid.beatsPerBar, manualBpm != null))

        onProgress("转写中…")
        val engine = TranscriptionEngine(context)
        val notes = try {
            engine.transcribe(pcm, sampleRate) { p -> onProgress("转写中… ${(p * 100).toInt()}%") }
        } finally {
            engine.close()
        }
        if (notes.notes.isEmpty()) throw IllegalArgumentException("没有转写出音符——试试更干净的单音素材")
        // 时长截断（前 2 分钟）：长曲先出主干（局限如实标注）
        val kept = notes.notes.filter { it.timeSec < 120 }
        val truncated = notes.notes.size - kept.size

        val cleaned = NoteCleaner.clean(kept, 60.0 / grid.bpm)
        android.util.Log.d(
            "GuitarCoach",
            "时序诊断: 低置信删=%d 同音并=%d 鬼影删=%d 和弦组=%d".format(
                cleaned.droppedLowAmp, cleaned.mergedSamePitch, cleaned.droppedGhosts, cleaned.chordGroups,
            ),
        )

        val placed = MidiTabConverter.convert(cleaned.notes, grid)
        val material = InputClassifier.classify(cleaned.notes)

        val doc = MidiTabConverter.toTabDocument(
            placed,
            bpm = kotlin.math.round(grid.bpm).toInt(),
            title = "扒谱 " + uri.lastPathSegment?.substringAfterLast('/')?.take(24).orEmpty(),
        )
        // P0-7：混音素材默认配和弦级（D3 口径）；干净素材和弦轨也生成但 UI 弱展示
        val chords = ChordTracker.detect(cleaned.notes, grid)
        val finalDoc = doc.copy(chordTrack = chords)
        android.util.Log.d(
            "GuitarCoach",
            "分级诊断: 素材=%s 和弦段=%d 首段=%s".format(
                material, chords.size, chords.firstOrNull()?.display ?: "—",
            ),
        )
        Outcome(finalDoc, notes.skippedOutOfRange, truncated, material, kept.size)
    }

    private fun pcmDurationSec(pcm: ShortArray): Double = 124.0.coerceAtLeast(pcm.size / 22050.0)

    /** 双路径抽取：enhance 开 = Spleeter 分离 → 伴奏轨（混音素材）；关 = 直通 mono（干净素材）。 */
    private fun extractPcm(
        uri: Uri,
        enhance: Boolean,
        onProgress: (String) -> Unit,
    ): Pair<ShortArray, Int> {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            return if (enhance) {
                val stereo = AudioPcmExtractor().extractStereoFloat(fd.fileDescriptor)
                val separator = SpleeterSeparator(context)
                try {
                    onProgress("分离人声/鼓中…")
                    val sep = separator.separate(stereo.samples, stereo.sampleRate) { p ->
                        onProgress("分离人声/鼓中… ${(p * 100).toInt()}%")
                    }
                    val rmsV = kotlin.math.sqrt(sep.vocals.map { x -> x * x * 1e6 }.average())
                    val rmsA = kotlin.math.sqrt(sep.accompaniment.map { x -> x * x * 1e6 }.average())
                    android.util.Log.d("GuitarCoach", "分离诊断: vocals RMS=%.2f, accompaniment RMS=%.2f".format(rmsV, rmsA))
                    val shortPcm = ShortArray(sep.accompaniment.size) { i ->
                        (sep.accompaniment[i] * 32767).toInt().coerceIn(-32768, 32767).toShort()
                    }
                    shortPcm to sep.sampleRate
                } finally {
                    separator.close()
                }
            } else {
                val mono = AudioPcmExtractor().extractToMono(fd.fileDescriptor, targetRate = 22050, enhance = false)
                mono.pcm to mono.sampleRate
            }
        } ?: throw IllegalArgumentException("文件读取失败，请重试")
    }
}
