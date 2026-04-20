package com.voiceos.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.voiceos.utils.AppLogger
import java.util.Locale
import java.util.UUID

/**
 * TtsManager — Text-to-speech feedback for VoiceOS.
 *
 * Gives audible confirmation of every executed command so the user
 * doesn't need to look at the screen to know what happened.
 *
 * Examples:
 *   "Clicking button 3"
 *   "Scrolling down"
 *   "Opening WhatsApp"
 *   "Message sent to Riya"
 *   "Running morning routine"
 *
 * Usage:
 *   TtsManager.getInstance(context).speak("Hello")
 *
 * Call [shutdown] in the owning component's onDestroy.
 */
class TtsManager private constructor(context: Context) {

    private val TAG = "TtsManager"
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pendingQueue = mutableListOf<String>()

    companion object {
        @Volatile private var INSTANCE: TtsManager? = null

        fun getInstance(context: Context): TtsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TtsManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)
                isReady = true
                AppLogger.i(TAG, "TTS engine ready")
                // Flush any queued utterances
                pendingQueue.forEach { speakNow(it) }
                pendingQueue.clear()
            } else {
                AppLogger.e(TAG, "TTS init failed with status: $status")
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                AppLogger.v(TAG, "TTS done: $utteranceId")
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                AppLogger.w(TAG, "TTS error for: $utteranceId")
            }
        })
    }

    /** Speak [text] aloud. Queues if TTS engine is not yet initialised. */
    fun speak(text: String) {
        AppLogger.d(TAG, "speak: \"$text\"")
        if (isReady) speakNow(text) else pendingQueue.add(text)
    }

    /** Stop current speech immediately. */
    fun stop() {
        tts?.stop()
    }

    /** Release resources. Call from owning component's onDestroy. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        INSTANCE = null
        AppLogger.i(TAG, "TTS shut down")
    }

    // ── Private ───────────────────────────────────────────────────────

    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }
}
