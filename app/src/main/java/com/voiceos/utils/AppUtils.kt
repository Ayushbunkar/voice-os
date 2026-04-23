package com.voiceos.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.voiceos.service.VoiceAccessibilityService

/**
 * AppUtils — Optimized for maximum execution speed.
 */
object AppUtils {

    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun requestOverlayPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openAccessibilitySettings(context: Context) {
        val service = ComponentName(context, VoiceAccessibilityService::class.java).flattenToString()
        // Fast direct intent for API 31+ with fallback
        val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
            putExtra("android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME", service)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(intent) }.isFailure) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, VoiceAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled?.contains(expected, ignoreCase = true) == true
    }

    /**
     * Ultra-fast app launcher using Intent resolution instead of package listing.
     */
    fun launchApp(context: Context, appName: String): Boolean {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(mainIntent, 0)
        
        val target = list.firstOrNull { it.loadLabel(pm).toString().contains(appName, true) }
        return target?.let {
            pm.getLaunchIntentForPackage(it.activityInfo.packageName)?.let { intent ->
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        } ?: false
    }

    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
