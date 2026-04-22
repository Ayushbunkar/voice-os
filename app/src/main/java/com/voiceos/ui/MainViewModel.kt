package com.voiceos.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.voiceos.utils.AppUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * MainViewModel — single source of truth for the Compose UI.
 *
 * Holds:
 *   • Permission states (mic / accessibility / overlay)
 *   • AI mode toggle
 *   • Continuous listening toggle
 *   • Listening / processing flags driven by FloatingWidgetService
 *   • Last executed command text (for the Command Log chip)
 *
 * All state is exposed as [StateFlow] so Compose can collect it
 * with `collectAsStateWithLifecycle`.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx: Context get() = getApplication<Application>().applicationContext

    // ── UI state data class ───────────────────────────────────────────

    data class UiState(
        val isMicGranted: Boolean = false,
        val isAccessibilityEnabled: Boolean = false,
        val isOverlayGranted: Boolean = false,
        val isAiModeEnabled: Boolean = false,
        val isContinuousListening: Boolean = false,
        val isListening: Boolean = false,
        val isProcessing: Boolean = false,
        val lastCommand: String = ""
    ) {
        val allPermissionsGranted: Boolean
            get() = isMicGranted && isAccessibilityEnabled && isOverlayGranted
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ── Permission refresh ────────────────────────────────────────────

    /**
     * Call from onResume to re-check all runtime permissions.
     * Each check is cheap (no IPC), safe to call frequently.
     */
    fun refreshPermissions(
        isMicGranted: Boolean
    ) {
        _uiState.update { current ->
            current.copy(
                isMicGranted = isMicGranted,
                isAccessibilityEnabled = AppUtils.isAccessibilityServiceEnabled(ctx),
                isOverlayGranted = AppUtils.hasOverlayPermission(ctx)
            )
        }
    }

    // ── Navigation / permission actions ──────────────────────────────

    fun openAccessibilitySettings() {
        AppUtils.openAccessibilitySettings(ctx)
    }

    fun requestOverlayPermission() {
        AppUtils.requestOverlayPermission(ctx)
    }

    // ── Control panel toggles ─────────────────────────────────────────

    fun toggleAiMode() {
        _uiState.update { it.copy(isAiModeEnabled = !it.isAiModeEnabled) }
    }

    fun toggleContinuousListening() {
        _uiState.update { it.copy(isContinuousListening = !it.isContinuousListening) }
    }

    // ── Assistant launch ──────────────────────────────────────────────

    /**
        * Starts the FloatingWidgetService and keeps the app screen visible.
     */
    fun launchAssistant() {
        val intent = Intent(ctx, FloatingWidgetService::class.java).apply {
            putExtra(FloatingWidgetService.EXTRA_AI_MODE, _uiState.value.isAiModeEnabled)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startForegroundService(intent)

        // Keep the app in foreground to avoid "app closed" confusion during setup/testing.
        AppUtils.showToast(ctx, "Assistant started")
    }

    // ── Command log (called by FloatingWidgetService via broadcast) ───

    fun onCommandExecuted(commandText: String) {
        _uiState.update { it.copy(lastCommand = commandText) }
    }

    fun setListeningState(listening: Boolean) {
        _uiState.update { it.copy(isListening = listening, isProcessing = false) }
    }

    fun setProcessingState(processing: Boolean) {
        _uiState.update { it.copy(isProcessing = processing, isListening = false) }
    }
}
