package com.voiceos.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceos.ui.theme.VoiceOSTheme
import com.voiceos.utils.AppLogger

/**
 * MainActivity — Phase 3: Jetpack Compose entry point.
 *
 * The activity is now a thin host with three responsibilities:
 *   1. Own the [MainViewModel] and refresh permissions on resume.
 *   2. Handle the microphone permission request result.
 *   3. Call [setContent] to render [MainScreen] inside [VoiceOSTheme].
 *
 * All business logic lives in [MainViewModel] and the service layer.
 * All UI lives in [MainScreen], [PermissionScreen], and [ControlPanel].
 *
 * Note: [MacrosActivity] still uses ViewBinding XML — no change needed there.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // ── ViewModel ─────────────────────────────────────────────────────
    private val viewModel: MainViewModel by viewModels()

    // ── Permission launcher ───────────────────────────────────────────
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        AppLogger.i(TAG, "Microphone permission result: $granted")
        refreshPermissions()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.i(TAG, "MainActivity (Compose) created")

        setContent {
            VoiceOSTheme {
                // Collect state from ViewModel as Compose State
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                MainScreen(
                    uiState = uiState,

                    // ── Permission callbacks ────────────────────────────
                    onGrantMic = { requestMicPermission() },
                    onOpenAccessibility = { viewModel.openAccessibilitySettings() },
                    onGrantOverlay = { viewModel.requestOverlayPermission() },

                    // ── Control panel callbacks ─────────────────────────
                    onToggleAiMode = { viewModel.toggleAiMode() },
                    onToggleContinuousListening = { viewModel.toggleContinuousListening() },

                    // ── Launch button ───────────────────────────────────
                    onLaunch = { viewModel.launchAssistant() },

                    // ── Macros screen ───────────────────────────────────
                    onOpenMacros = {
                        startActivity(Intent(this, MacrosActivity::class.java))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions every time the user returns from Settings
        refreshPermissions()
    }

    // ── Private helpers ───────────────────────────────────────────────

    private fun refreshPermissions() {
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        viewModel.refreshPermissions(isMicGranted = micGranted)
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            refreshPermissions() // already granted, just refresh status
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
