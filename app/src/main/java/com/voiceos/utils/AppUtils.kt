package com.voiceos.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast

/**
 * AppUtils — Collection of static utility helpers used across the project.
 */
object AppUtils {

    private const val TAG = "AppUtils"

    // ─── Permission helpers ────────────────────────────────────────────────

    /**
     * Returns true if Android's "draw over other apps" permission is granted.
     * Required for the floating widget and the overlay numbering system.
     */
    fun hasOverlayPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /**
     * Opens the system overlay permission screen so the user can grant it.
     */
    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    /**
     * Opens the Accessibility settings screen so the user can enable
     * the VoiceOS accessibility service.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    // ─── App launcher helper ───────────────────────────────────────────────

    /**
     * Attempts to launch an installed app whose label contains [appName] (case-insensitive).
     * Returns true on success, false if no matching app was found.
     */
    fun launchApp(context: Context, appName: String): Boolean {
        AppLogger.i(TAG, "Attempting to launch app: $appName")
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val match = packages.firstOrNull { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString()
            label.contains(appName, ignoreCase = true)
        }

        return if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                AppLogger.i(TAG, "Launched: ${match.packageName}")
                true
            } else {
                AppLogger.w(TAG, "No launch intent for package: ${match.packageName}")
                showToast(context, "Cannot launch ${match.packageName}")
                false
            }
        } else {
            AppLogger.w(TAG, "No installed app matching: $appName")
            showToast(context, "App not found: $appName")
            false
        }
    }

    // ─── UI helpers ────────────────────────────────────────────────────────

    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
