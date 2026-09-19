@file:OptIn(kotlin.contracts.ExperimentalContracts::class)

package com.guitarcoach.app.core.tab

import alphaTab.Settings
import alphaTab.core.ecmaScript.Uint8Array
import alphaTab.importer.ScoreLoader
import alphaTab.model.HarmonicType
import alphaTab.model.MasterBar
import alphaTab.model.Note
import alphaTab.model.Score
import alphaTab.model.Track
import alphaTab.model.VibratoType

/**
 * F205：Guitar Pro（.gp3~.gp8）导入 → [TabDocument]（唯一谱面模型）。
 *
 * 二进制解析交给 alphaTab 1.8.4（MPL-2.0，仅作依赖引入），本类只做 Score → TabDocument 映射：
 * - 取第一根"有弦"轨道（staff.isStringed，自动跳过鼓轨）；多轨 v1 不展开
 * - 段落名取 masterBar.section 的 marker/text，无标记的小节归入上一段（缺省 Main）
 * - 小节内拍位 = (beat.absolutePlaybackStart - masterBar.start) / 960（alphaTab 内部 1 拍=960 tick）
 * - tie 连音只记起点；dead 音记为品 0 + mute；其余技巧按 alphaTab 字段直译
 * - duration 取同拍到下一拍的 tick 差（拍为单位），末拍缺省 1.0
 */
object GpImporter {

    const val TICKS_PER_BEAT = 960.0

    fun import(bytes: ByteArray): TabDocument {
        val score = runCatching {
            // 公开构造只有 UByteArray 版本（sources.jar 实测）
            ScoreLoader.loadScoreFromBytes(Uint8Array(bytes.toUByteArray()), Settings())
        }.getOrElse { throw IllegalArgumentException("Guitar Pro 文件解析失败：${it.message ?: "未知错误"}") }
            ?: throw IllegalArgumentException("Guitar Pro 文件为空或已损坏")

        return importScore(score)
    }

    private fun <T> alphaTab.collections.List<T>.toKotlinList(): List<T> =
        ArrayList<T>(length.toInt()).also { out -> for (item in this) out += item }

    /** Score → TabDocument 映射（alphaTex 测试用同一路径）。 */
    fun importScore(score: Score): TabDocument {
        val track: Track = score.tracks
            .firstOrNull { it.staves.any { st -> st.isStringed } }
            ?: score.tracks.firstOrNull()
            ?: throw IllegalArgumentException("文件里没有可用轨道")
        val staff = track.staves.firstOrNull()
            ?: throw IllegalArgumentException("轨道「${track.name}」没有谱表")

        val sections = mutableListOf<TabSection>()
        var currentName: String? = null
        var currentBars: MutableList<TabBar>? = null

        val masterBars = score.masterBars.toKotlinList()
        val barsByIndex = staff.bars.toKotlinList()
        // V7 修复：拍号取首个小节的分子/分母（此前 3/4、6/8 的 GP 文件被按 4/4 口径切拍位）
        val firstBar = masterBars.firstOrNull()
        val timeSignature = if (firstBar != null && firstBar.timeSignatureDenominator > 0) {
            "${firstBar.timeSignatureNumerator.toInt()}/${firstBar.timeSignatureDenominator.toInt()}"
        } else "4/4"

        for (masterBar in masterBars) {
            val marker = masterBar.section?.marker?.takeIf { it.isNotBlank() }
                ?: masterBar.section?.text?.takeIf { it.isNotBlank() }
            if (marker != null && marker != currentName) {
                currentBars = mutableListOf()
                currentName = marker
                sections += TabSection(marker, currentBars)
            }
            if (currentBars == null) {
                currentBars = mutableListOf()
                currentName = "Main"
                sections += TabSection("Main", currentBars)
            }

            val bar = barsByIndex.getOrNull(masterBar.index.toInt())
            if (bar != null) {
                currentBars += mapBar(masterBar, bar)
            }
        }

        return TabDocument(
            title = score.title ?: track.name,
            tempo = score.tempo.toInt().coerceIn(1, 300),
            tuning = staff.tuningName ?: "E标准调弦",
            timeSignature = timeSignature,
            sections = sections,
        )
    }

    private fun mapBar(masterBar: MasterBar, bar: alphaTab.model.Bar): TabBar {
        val barStartTicks = masterBar.start
        val beats = bar.voices.firstOrNull()?.beats?.toKotlinList() ?: emptyList()
        val notes = mutableListOf<NoteEvent>()
        beats.forEachIndexed { bi, beat ->
            val startBeat = (beat.absolutePlaybackStart - barStartTicks) / TICKS_PER_BEAT
            val nextStart = beats.getOrNull(bi + 1)?.absolutePlaybackStart
            val durationBeats = if (nextStart != null) {
                (nextStart - beat.absolutePlaybackStart) / TICKS_PER_BEAT
            } else {
                1.0
            }
            for (note in beat.notes) {
                if (note.isTieDestination) continue // 连音延续：不重复记音符
                notes += NoteEvent(
                    string = note.string.toInt().coerceIn(1, 6),
                    fret = if (note.isDead) 0 else note.fret.toInt().coerceIn(0, 24),
                    beat = startBeat,
                    duration = durationBeats.takeIf { it > 0 } ?: 1.0,
                    technique = techniqueOf(note),
                )
            }
        }
        return TabBar(notes)
    }

    private fun techniqueOf(note: Note): String? = when {
        note.isDead -> "mute"
        note.isPalmMute -> "palm_mute"
        note.hasBend -> "bend"
        note.vibrato != VibratoType.None -> "vibrato"
        note.harmonicType != HarmonicType.None -> "harmonic"
        note.slideOutType != alphaTab.model.SlideOutType.None -> "slide"
        note.hammerPullDestination != null -> {
            val destination = note.hammerPullDestination
            if (destination != null && destination.fret < note.fret) "pull_off" else "hammer_on"
        }
        else -> null
    }
}
