package com.voiceos

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.voiceos.automation.AutomationEngine
import com.voiceos.api.CloudSyncManager
import com.voiceos.memory.ContextManager
import com.voiceos.utils.AppLogger
import com.voiceos.utils.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * VoiceOSApp — Phase 2 Application class.
 *
 * Phase 2 additions:
 *   • Pre-warms [ContextManager] singleton
 *   • Pre-warms [AutomationEngine] (loads macros from disk)
 *   • Pre-warms [TtsManager] (TTS engine init takes ~500ms)
 */
class VoiceOSApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID_FLOATING = "voiceos_floating_widget"
        const val CHANNEL_NAME_FLOATING = "Floating Assistant"
    }

    override fun onCreate() {
        super.onCreate()

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e("VoiceOSApp", "Uncaught exception on ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        // Initialise logging first
        AppLogger.init(loggingEnabled = true)
        AppLogger.i("VoiceOSApp", "=== VoiceOS Phase 2 starting ===")

        // Create notification channels (required API 26+)
        runCatching { createNotificationChannels() }
            .onFailure { AppLogger.e("VoiceOSApp", "Notification channel init failed", it) }

        // Pre-warm singletons so first voice command has no cold-start delay.
        // Each component is isolated so one failure does not kill process startup.
        runCatching { ContextManager.init(this) }
            .onFailure { AppLogger.e("VoiceOSApp", "ContextManager init failed", it) }

        runCatching { AutomationEngine.getInstance(this) }
            .onFailure { AppLogger.e("VoiceOSApp", "AutomationEngine init failed", it) }

        runCatching { TtsManager.getInstance(this) }
            .onFailure { AppLogger.e("VoiceOSApp", "TtsManager init failed", it) }

        // Phase 4: Init Networking & WebSockets
        runCatching { com.voiceos.api.ApiClient.init(this) }
            .onFailure { AppLogger.e("VoiceOSApp", "ApiClient init failed", it) }

        val token = runCatching { ContextManager.getAuthToken() }
            .onFailure { AppLogger.e("VoiceOSApp", "Token read failed", it) }
            .getOrNull()

        if (!token.isNullOrBlank()) {
            appScope.launch {
                runCatching { CloudSyncManager.connectCurrentDevice() }
                    .onFailure { AppLogger.w("VoiceOSApp", "Device sync failed: ${it.message}") }
                runCatching { com.voiceos.api.SocketManager.init(this@VoiceOSApp) }
                    .onFailure { AppLogger.w("VoiceOSApp", "Socket init failed: ${it.message}") }
            }
        } else {
            AppLogger.i("VoiceOSApp", "No auth token yet; cloud sync will start after login")
        }

        AppLogger.i("VoiceOSApp", "Singletons pre-warmed, Networking initialized")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_FLOATING,
                CHANNEL_NAME_FLOATING,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the VoiceOS floating assistant alive"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
            AppLogger.i("VoiceOSApp", "Notification channel created")
        }
    }
}
