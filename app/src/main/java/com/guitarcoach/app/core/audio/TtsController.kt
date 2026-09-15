package com.guitarcoach.app.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 系统 TTS 封装（F208 整段朗读/音符级点读）：中文零成本播报。
 * 初始化异步完成，未就绪时 speak 静默忽略（调用方 UI 有就绪态提示）。
 */
class TtsController(context: Context) {

    private companion object {
        const val LOG_TAG = "GuitarCoach"
    }

    private var tts: TextToSpeech? = null

    @Volatile var isReady: Boolean = false
        private set

    @Volatile var languageAvailable: Boolean = true
        private set

    private val initListener = TextToSpeech.OnInitListener { status ->
        isReady = status == TextToSpeech.SUCCESS
        if (isReady) {
            runCatching {
                val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                languageAvailable = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            }.onFailure { Log.e(LOG_TAG, "tts setLanguage failed", it) }
        } else {
            Log.e(LOG_TAG, "tts init failed: status=$status")
        }
    }

    init {
        tts = TextToSpeech(context.applicationContext, initListener)
    }

    /** 朗读一段文本；重复调用会打断上一段。 */
    fun speak(text: String) {
        if (!isReady || text.isBlank()) return
        tts?.speak(text.take(2000), TextToSpeech.QUEUE_FLUSH, null, "guitarcoach-utterance")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
