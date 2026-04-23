package com.voiceos.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.voiceos.utils.AppLogger
import com.voiceos.utils.RuntimeTuning
import java.util.Locale

/**
 * VoiceRecognizer — Optimized for very fast continuous listening.
 */
class VoiceRecognizer(private val context: Context) {

    private val TAG = "VoiceRecognizer"
    private val timingProfile = resolveTimingProfile()
    private val minRestartIntervalMs = timingProfile.minRestartIntervalMs

    var onResult: ((String) -> Unit)? = null
    var onError: ((Int) -> Unit)? = null
    var onPartialResult: ((String) -> Unit)? = null
    var onListeningStateChanged: ((Boolean) -> Unit)? = null

    var continuousMode: Boolean = true

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldRestart = false
    private var lastStartAtMs = 0L
    private var lastPartialText = ""

    private val recognizerIntent: Intent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

            // Adaptive turn detection tuned for device capability tier.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                timingProfile.minSpeechLengthMs
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                timingProfile.completeSilenceMs
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                timingProfile.possiblyCompleteSilenceMs
            )
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            AppLogger.d(TAG, "Ready")
        }

        override fun onBeginningOfSpeech() {
            AppLogger.d(TAG, "Speech begun")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            AppLogger.w(TAG, "Error: $error")
            onError?.invoke(error)
            
            // Force restart even on error if continuousMode is true
            if (continuousMode) {
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    speechRecognizer?.cancel()
                }
                restartListeningIfNeeded()
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull()
            if (best != null) {
                onResult?.invoke(best)
            }
            
            // Continuous restart
            if (continuousMode) {
                restartListeningIfNeeded()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!partial.isNullOrBlank() && partial != lastPartialText) {
                lastPartialText = partial
                onPartialResult?.invoke(partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        AppLogger.i(
            TAG,
            "timing tier=${timingProfile.tier} restart=${timingProfile.minRestartIntervalMs}ms " +
                "minSpeech=${timingProfile.minSpeechLengthMs}ms completeSilence=${timingProfile.completeSilenceMs}ms"
        )

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }
    }

    fun startListening() {
        if (isListening) return
        val now = System.currentTimeMillis()
        if (now - lastStartAtMs < minRestartIntervalMs) return
        
        if (speechRecognizer == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) return
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }

        shouldRestart = continuousMode
        lastStartAtMs = now
        val recognizer = speechRecognizer ?: run {
            AppLogger.w(TAG, "SpeechRecognizer unavailable during start")
            onListeningStateChanged?.invoke(false)
            return
        }

        val started = runCatching {
            recognizer.startListening(recognizerIntent)
            true
        }.getOrElse { error ->
            AppLogger.w(TAG, "startListening failed: ${error.message}")
            false
        }

        isListening = started
        onListeningStateChanged?.invoke(started)
    }

    fun stopListening() {
        shouldRestart = false
        lastPartialText = ""
        speechRecognizer?.stopListening()
        isListening = false
        onListeningStateChanged?.invoke(false)
    }

    fun destroy() {
        shouldRestart = false
        lastPartialText = ""
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }

    private fun restartListeningIfNeeded() {
        if (!shouldRestart) return
        val now = System.currentTimeMillis()
        if (now - lastStartAtMs < minRestartIntervalMs) return
        startListening()
    }

    private fun resolveTimingProfile(): VoiceTimingProfile {
        val runtimeProfile = RuntimeTuning.get(context)
        return when (runtimeProfile.tier) {
            "high" -> VoiceTimingProfile(
                tier = runtimeProfile.tier,
                minRestartIntervalMs = 150L, // Increased from 70
                minSpeechLengthMs = 300L,    // Increased from 160
                completeSilenceMs = 800L,    // Increased from 240 (too aggressive)
                possiblyCompleteSilenceMs = 500L // Increased from 170
            )
            "low" -> VoiceTimingProfile(
                tier = runtimeProfile.tier,
                minRestartIntervalMs = 300L,
                minSpeechLengthMs = 500L,
                completeSilenceMs = 1500L,
                possiblyCompleteSilenceMs = 1000L
            )
            else -> VoiceTimingProfile(
                tier = runtimeProfile.tier,
                minRestartIntervalMs = 200L,
                minSpeechLengthMs = 400L,
                completeSilenceMs = 1000L,
                possiblyCompleteSilenceMs = 700L
            )
        }
    }

    private data class VoiceTimingProfile(
        val tier: String,
        val minRestartIntervalMs: Long,
        val minSpeechLengthMs: Long,
        val completeSilenceMs: Long,
        val possiblyCompleteSilenceMs: Long
    )
}
