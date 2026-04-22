package com.voiceos.ui

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import androidx.core.app.NotificationCompat
import com.voiceos.R
import com.voiceos.VoiceOSApp
import com.voiceos.commands.CommandHandler
import com.voiceos.commands.CommandRouter
import com.voiceos.memory.ContextManager
import com.voiceos.utils.AppLogger
import com.voiceos.utils.AppUtils
import com.voiceos.utils.TtsManager
import com.voiceos.voice.VoiceRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FloatingWidgetService — Phase 2 upgraded foreground service.
 *
 * Phase 2 additions:
 *   • Uses [CommandRouter] instead of [CommandParser] directly
 *   • Supports wake-word detection ("hey voiceos")
 *   • [TtsManager] provides voice feedback
 *   • Continuous listening toggle via AI mode flag
 *   • Visual state: idle (blue) / listening (red) / processing (yellow)
 */
class FloatingWidgetService : Service() {

    private val TAG = "FloatingWidgetService"

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var voiceRecognizer: VoiceRecognizer
    private var ttsManager: TtsManager? = null

    private val commandRouter by lazy(LazyThreadSafetyMode.NONE) {
        CommandRouter(applicationContext)
    }

    private val commandHandler by lazy(LazyThreadSafetyMode.NONE) {
        CommandHandler(applicationContext)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isListening = false

    /** When true → continuous listening + wake-word filter is active. */
    var aiModeEnabled: Boolean = false

    companion object {
        const val EXTRA_AI_MODE = "extra_ai_mode"
        private const val WAKE_WORD = "hey voiceos"
        private const val WAKE_WORD_ALT = "voiceos"
    }

    // ── Lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        voiceRecognizer = VoiceRecognizer(applicationContext).apply {
            continuousMode = aiModeEnabled
        }

        setupVoiceRecognizer()
        addFloatingView()
        startForegroundNotification()

        AppLogger.i(TAG, "FloatingWidgetService started (AI=$aiModeEnabled)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        aiModeEnabled = intent?.getBooleanExtra(EXTRA_AI_MODE, false) ?: false
        voiceRecognizer.continuousMode = aiModeEnabled
        if (aiModeEnabled && !isListening) {
            voiceRecognizer.startListening()
        } else if (!aiModeEnabled && isListening) {
            voiceRecognizer.stopListening()
        }
        AppLogger.d(TAG, "AI mode = $aiModeEnabled")
        return START_STICKY
    }

    override fun onDestroy() {
        voiceRecognizer.destroy()
        ttsManager?.stop()
        runCatching { windowManager.removeView(floatingView) }
        AppLogger.i(TAG, "FloatingWidgetService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Floating View ─────────────────────────────────────────────────

    private fun addFloatingView() {
        floatingView = LayoutInflater.from(this)
            .inflate(R.layout.layout_floating_widget, null)

        windowManager.addView(floatingView, buildFloatingParams())

        // Start the breathing animation
        floatingView.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.floating_pulse)
        )

        setupDragAndTap()
    }

    private fun setupDragAndTap() {
        var initX = 0; var initY = 0
        var touchX = 0f; var touchY = 0f
        val params = floatingView.layoutParams as WindowManager.LayoutParams

        floatingView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    touchX = event.rawX; touchY = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (event.rawX - touchX).toInt()
                    params.y = initY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (dx * dx + dy * dy < (dpToPx(10).toLong() * dpToPx(10))) {
                        v.performClick()
                        toggleListening()
                    }
                    true
                }
                else -> false
            }
        }
        floatingView.setOnClickListener { /* handled by touch */ }
    }

    private fun buildFloatingParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            dpToPx(64), dpToPx(64), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).also {
            it.gravity = Gravity.TOP or Gravity.START
            it.x = 0; it.y = 220
        }
    }

    // ── Voice recognizer wiring ───────────────────────────────────────

    private fun setupVoiceRecognizer() {
        voiceRecognizer.onResult = { text ->
            isListening = false
            setButtonState(ButtonState.PROCESSING)
            AppLogger.i(TAG, "Voice result: \"$text\"")

            // Wake-word filter (only in continuous / AI mode)
            if (aiModeEnabled) {
                handleContinuousResult(text)
            } else {
                dispatchCommand(text)
            }
        }

        voiceRecognizer.onError = { code ->
            isListening = false
            setButtonState(ButtonState.IDLE)
            AppLogger.w(TAG, "Voice error: $code")
        }

        voiceRecognizer.onListeningStateChanged = { listening ->
            isListening = listening
            setButtonState(if (listening) ButtonState.LISTENING else ButtonState.IDLE)
        }

        // Start continuous mode automatically if AI mode
        if (aiModeEnabled) voiceRecognizer.startListening()
    }

    /**
     * In continuous mode all speech is listened to constantly.
     * Only process commands that start with the wake word.
     */
    private fun handleContinuousResult(text: String) {
        val lower = text.lowercase().trim()
        val hasWakeWord = lower.startsWith(WAKE_WORD) || lower.startsWith(WAKE_WORD_ALT)

        if (hasWakeWord) {
            // Strip wake word and process the actual command
            val command = lower
                .removePrefix(WAKE_WORD)
                .removePrefix(WAKE_WORD_ALT)
                .trim()

            if (command.isNotEmpty()) {
                AppLogger.i(TAG, "Wake word detected, command: \"$command\"")
                tts().speak("Processing")
                dispatchCommand(command)
            } else {
                tts().speak("Yes? Say your command.")
            }
        } else {
            AppLogger.d(TAG, "No wake word in: \"$lower\" — ignored")
        }
    }

    private fun dispatchCommand(text: String) {
        scope.launch(Dispatchers.Default) {
            val command = commandRouter.route(text)
            launch(Dispatchers.Main) {
                commandHandler.execute(command)
                setButtonState(ButtonState.IDLE)
            }
        }
    }

    private fun toggleListening() {
        if (isListening) {
            voiceRecognizer.stopListening()
            setButtonState(ButtonState.IDLE)
        } else {
            voiceRecognizer.startListening()
            setButtonState(ButtonState.LISTENING)
        }
    }

    // ── Button state ──────────────────────────────────────────────────

    private enum class ButtonState { IDLE, LISTENING, PROCESSING }

    private fun setButtonState(state: ButtonState) {
        val bg = when (state) {
            ButtonState.IDLE        -> R.drawable.bg_mic_idle
            ButtonState.LISTENING   -> R.drawable.bg_mic_active
            ButtonState.PROCESSING  -> R.drawable.bg_mic_processing
        }
        runCatching { floatingView.setBackgroundResource(bg) }
    }

    // ── Notification ──────────────────────────────────────────────────

    private fun startForegroundNotification() {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat
            .Builder(this, VoiceOSApp.CHANNEL_ID_FLOATING)
            .setContentTitle("VoiceOS Active")
            .setContentText(if (aiModeEnabled) "AI mode ON — say \"Hey VoiceOS\"" else "Tap mic to speak")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    private fun tts(): TtsManager {
        val existing = ttsManager
        if (existing != null) return existing
        val created = TtsManager.getInstance(applicationContext)
        ttsManager = created
        return created
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
