package com.voiceos.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.voiceos.ui.theme.VoiceOSTheme
import com.voiceos.utils.AppUtils
import com.voiceos.utils.AppLogger

/**
 * MainActivity — Host for the VoiceOS UI.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VoiceOSTheme {
                val uiState by viewModel.uiState.collectAsState()

                MainScreen(
                    uiState = uiState,
                    onGrantMic = { requestMicPermission() },
                    onOpenAccessibility = { AppUtils.openAccessibilitySettings(this) },
                    onGrantOverlay = { AppUtils.requestOverlayPermission(this) },
                    onToggleAiMode = { viewModel.toggleAiMode() },
                    onToggleContinuousListening = { viewModel.toggleContinuousListening() },
                    onLaunch = { viewModel.launchAssistant() },
                    onStopAssistant = { viewModel.stopAssistant() },
                    onOpenMacros = {
                        startActivity(Intent(this, MacrosActivity::class.java))
                    },
                    onExitApp = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.refreshPermissions(isMicGranted = micGranted)
    }

    private fun requestMicPermission() {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
