package com.guitarcoach.app.core.music

/**
 * 调弦预设（F704，参考 GuitarTuna/Fender Tune）：常用电吉他调弦表。
 * midiLowFirst 口径：6 弦→1 弦（低到高），midi 值（E2=40）。
 */
object Tunings {

    data class Tuning(val name: String, val midiLowFirst: List<Int>) {
        init {
            require(midiLowFirst.size == 6) { "调弦必须 6 个值" }
        }
    }

    val STANDARD = Tuning("E 标准", listOf(40, 45, 50, 55, 59, 64))
    val DROP_D = Tuning("Drop D", listOf(38, 45, 50, 55, 59, 64))
    val EB_STANDARD = Tuning("Eb 半步下", listOf(39, 44, 49, 54, 58, 63))
    val D_STANDARD = Tuning("D 全步下", listOf(38, 43, 48, 53, 57, 62))
    val DROP_C = Tuning("Drop C", listOf(36, 43, 48, 53, 57, 62))
    val OPEN_G = Tuning("Open G", listOf(38, 43, 50, 55, 59, 62))
    val DADGAD = Tuning("DADGAD", listOf(38, 45, 50, 55, 57, 62))

    val ALL: List<Tuning> = listOf(STANDARD, DROP_D, EB_STANDARD, D_STANDARD, DROP_C, OPEN_G, DADGAD)

    fun find(name: String): Tuning? = ALL.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}
