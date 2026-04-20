package com.voiceos.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.voiceos.utils.AppLogger
import java.util.Locale

/**
 * VoiceRecognizer — Wraps Android's [SpeechRecognizer] for continuous voice input.
 *
 * Usage:
 *   1. Construct with a context (use application context to avoid leaks).
 *   2. Set [onResult] and [onError] callbacks.
 *   3. Call [startListening] to begin. [stopListening] to stop.
 *   4. Call [destroy] when done (e.g., in onDestroy).
 *
 * The recogniser automatically restarts after each result when [continuousMode] = true.
 */
class VoiceRecognizer(private val context: Context) {

    private val TAG = "VoiceRecognizer"

    /** Callback triggered with the best recognised text. */
    var onResult: ((String) -> Unit)? = null

    /** Callback triggered when recognition fails. */
    var onError: ((Int) -> Unit)? = null

    /** Callback for partial (real-time) results — optional. */
    var onPartialResult: ((String) -> Unit)? = null

    /** Callback for listening state changes. */
    var onListeningStateChanged: ((Boolean) -> Unit)? = null

    /** When true the recogniser restarts after each utterance. */
    var continuousMode: Boolean = true

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldRestart = false

    private val recognizerIntent: Intent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Keeps listening a bit longer before giving up
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }
    }

    /** Returns whether the device supports speech recognition. */
    fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    /** Begin listening for voice input. Safe to call repeatedly. */
    fun startListening() {
        if (isListening) {
            AppLogger.d(TAG, "Already listening — skipping start")
            return
        }
        if (!isAvailable()) {
            AppLogger.e(TAG, "SpeechRecognizer not available on this device")
            onError?.invoke(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        shouldRestart = continuousMode
        initRecognizer()
        speechRecognizer?.startListening(recognizerIntent)
        isListening = true
        onListeningStateChanged?.invoke(true)
        AppLogger.i(TAG, "Listening started")
    }

    /** Stop listening and cancel any pending restarts. */
    fun stopListening() {
        shouldRestart = false
        speechRecognizer?.stopListening()
        isListening = false
        onListeningStateChanged?.invoke(false)
        AppLogger.i(TAG, "Listening stopped")
    }

    /** Release resources. Call from the owning component's onDestroy. */
    fun destroy() {
        shouldRestart = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        AppLogger.i(TAG, "Recognizer destroyed")
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private fun initRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
    }

    private val listener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            AppLogger.v(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            AppLogger.v(TAG, "Speech begun")
        }

        override fun onRmsChanged(rmsdB: Float) { /* volume meter — ignored */ }

        override fun onBufferReceived(buffer: ByteArray?) { /* raw audio — ignored */ }

        override fun onEndOfSpeech() {
            AppLogger.v(TAG, "End of speech detected")
            isListening = false
        }

        override fun onError(error: Int) {
            AppLogger.w(TAG, "Recognition error: ${errorName(error)}")
            isListening = false
            onError?.invoke(error)
            // Auto-restart on transient errors
            if (shouldRestart && (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                AppLogger.d(TAG, "Auto-restarting after error")
                startListening()
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull()
            if (best != null) {
                AppLogger.i(TAG, "Result: \"$best\"")
                onResult?.invoke(best)
            }
            // Restart loop for continuous mode
            if (shouldRestart) startListening()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (partial != null) {
                AppLogger.v(TAG, "Partial: \"$partial\"")
                onPartialResult?.invoke(partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) { /* unused */ }
    }

    private fun errorName(code: Int) = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        else -> "UNKNOWN($code)"
    }
}
