package com.guitarcoach.app.core.music

import kotlin.math.pow

/** MIDI 音高 → 频率（Hz），A4=440 基准。tab（展示）与 audio（试听）共用，中立纯乐理。 */
fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)
