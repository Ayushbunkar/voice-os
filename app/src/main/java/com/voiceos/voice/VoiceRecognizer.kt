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
 * VoiceRecognizer — Optimized for very fast continuous listening.
 */
class VoiceRecognizer(private val context: Context) {

    private val TAG = "VoiceRecognizer"
    private val minRestartIntervalMs = 250L

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
            
            // Fast detection: 600ms of silence triggers a result
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
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
            
            if (shouldRestart) {
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
            
            if (shouldRestart) {
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
        speechRecognizer?.startListening(recognizerIntent)
        isListening = true
        onListeningStateChanged?.invoke(true)
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
}
