package com.guitarcoach.app.core.music

/**
 * 和弦库（F-new：集百家之长·和弦图与试听）：常用和弦标准指法。
 * frets 口径：6 弦→1 弦（低到高，x = 不弹，-1 表示）；fingers 同序（0 = 空弦不按）。
 * 数据为公认标准开放/横按把位（乐理常识级，易验证）；点按试听由 UI 层琶音（TonePlayer.playSequence）。
 */
object ChordLibrary {

    data class ChordShape(
        val name: String,
        val frets: List<Int>,   // 6 弦→1 弦；-1 = 不弹
        val fingers: List<Int>, // 同序，0 = 空弦
        val barreFret: Int = 0, // 0 = 无横按；>0 = 该品横按（起点弦由 frets 推断）
    ) {
        /** 1 弦→6 弦顺序的品数（渲染/播放用，TabDocument 同口径）。 */
        val fretsHighFirst: List<Int> get() = frets.reversed()
    }

    val ALL: List<ChordShape> = listOf(
        open("C", listOf(-1, 3, 2, 0, 1, 0), listOf(0, 3, 2, 0, 1, 0)),
        open("A", listOf(-1, 0, 2, 2, 2, 0), listOf(0, 0, 1, 2, 3, 0)),
        open("Am", listOf(-1, 0, 2, 2, 1, 0), listOf(0, 0, 2, 3, 1, 0)),
        open("D", listOf(-1, -1, 0, 2, 3, 2), listOf(0, 0, 0, 1, 3, 2)),
        open("Dm", listOf(-1, -1, 0, 2, 3, 1), listOf(0, 0, 0, 2, 3, 1)),
        open("E", listOf(0, 2, 2, 1, 0, 0), listOf(0, 2, 3, 1, 0, 0)),
        open("Em", listOf(0, 2, 2, 0, 0, 0), listOf(0, 2, 3, 0, 0, 0)),
        barre("F", 1),
        open("G", listOf(3, 2, 0, 0, 0, 3), listOf(3, 2, 0, 0, 0, 4)),
        open("B7", listOf(-1, 2, 1, 2, 0, 2), listOf(0, 2, 1, 3, 0, 4)),
        open("A7", listOf(-1, 0, 2, 0, 2, 0), listOf(0, 0, 2, 0, 3, 0)),
        open("D7", listOf(-1, -1, 0, 2, 1, 2), listOf(0, 0, 0, 2, 1, 3)),
        open("E7", listOf(0, 2, 0, 1, 0, 0), listOf(0, 2, 0, 1, 0, 0)),
        open("Em7", listOf(0, 2, 2, 0, 3, 0), listOf(0, 2, 3, 0, 4, 0)),
        open("Am7", listOf(-1, 0, 2, 0, 1, 0), listOf(0, 0, 2, 0, 1, 0)),
        open("Dm7", listOf(-1, -1, 0, 2, 1, 1), listOf(0, 0, 0, 2, 1, 1)),
        open("Fmaj7", listOf(-1, -1, 3, 2, 1, 0), listOf(0, 0, 3, 2, 1, 0)),
        open("Cadd9", listOf(-1, 3, 2, 0, 3, 3), listOf(0, 2, 1, 0, 3, 4)),
        open("Gsus4", listOf(3, 3, 0, 0, 1, 3), listOf(1, 2, 0, 0, 1, 4)),
        barre("F#m", 2),
        barre("Bm", 2),
        barre("B", 2),
        barre("Cm", 3),
        barre("G#m", 4),
        powerChord("E5", 6, 0),
        powerChord("A5", 5, 0),
        powerChord("D5", 5, 5),
        powerChord("G5", 6, 3),
        powerChord("C5", 5, 3),
    )

    private fun open(name: String, frets: List<Int>, fingers: List<Int>) = ChordShape(name, frets, fingers)

    /** 横按：E 形（6 弦根）随根音移动；m = 小三度（0 2 2 1 3 0 指形）。 */
    private fun barre(name: String, fret: Int): ChordShape {
        val minor = name.endsWith("m") && !name.endsWith("maj")
        val shape = if (minor) listOf(0, 2, 2, 1, 3, 0) else listOf(0, 2, 2, 1, 0, 0)
        val fingers = if (minor) listOf(1, 3, 4, 1, 4, 1) else listOf(1, 3, 4, 1, 0, 1)
        // 形内的 0 = 食指横按弦 → 实际品位 = 横按品；其余品位平移
        val frets = shape.map { f -> if (f == 0) fret else f + fret }
        return ChordShape(name, frets, fingers, barreFret = fret)
    }

    /** 强力和弦：根音弦（6 弦 fret=根 or 5 弦 fret=根）+ 上方两根弦同品（五度+八度）。 */
    private fun powerChord(name: String, rootString: Int, fret: Int): ChordShape {
        // 6 弦根：6/5/4 弦同品；5 弦根：5/4/3 弦同品
        val frets = MutableList(6) { -1 }
        val startIdx = 6 - rootString // 6 弦根 → idx0；5 弦根 → idx1
        for (i in 0 until 3) frets[startIdx + i] = fret
        val fingers = frets.map { if (it >= 0) 1 else 0 }
        return ChordShape(name, frets, fingers)
    }

    /** 和弦名模糊查询（大小写不敏感，支持 "Am7"/"am"）。 */
    fun find(name: String): ChordShape? = ALL.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}
