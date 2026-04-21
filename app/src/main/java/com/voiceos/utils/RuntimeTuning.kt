package com.voiceos.utils

import android.app.ActivityManager
import android.content.Context
import kotlin.math.roundToLong

data class RuntimeTuningProfile(
    val tier: String,
    val cpuCores: Int,
    val memoryClassMb: Int,
    val isLowRamDevice: Boolean,
    val accessibilityDebounceMs: Long,
    val whatsAppPollMs: Long,
    val timeoutScale: Float
)

/**
 * Runtime tuning profile based on the current device capability.
 * This keeps slow devices stable while allowing faster devices to feel snappier.
 */
object RuntimeTuning {

    private const val TAG = "RuntimeTuning"

    @Volatile
    private var cachedProfile: RuntimeTuningProfile? = null

    fun get(context: Context): RuntimeTuningProfile {
        cachedProfile?.let { return it }

        val appContext = context.applicationContext
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val memClass = activityManager.memoryClass.coerceAtLeast(64)
        val lowRam = activityManager.isLowRamDevice

        val tier = when {
            lowRam || memClass <= 192 || cores <= 4 -> "low"
            memClass >= 256 && cores >= 8 -> "high"
            else -> "mid"
        }

        val profile = when (tier) {
            "low" -> RuntimeTuningProfile(
                tier = tier,
                cpuCores = cores,
                memoryClassMb = memClass,
                isLowRamDevice = lowRam,
                accessibilityDebounceMs = 320L,
                whatsAppPollMs = 160L,
                timeoutScale = 1.20f
            )
            "high" -> RuntimeTuningProfile(
                tier = tier,
                cpuCores = cores,
                memoryClassMb = memClass,
                isLowRamDevice = lowRam,
                accessibilityDebounceMs = 180L,
                whatsAppPollMs = 90L,
                timeoutScale = 0.90f
            )
            else -> RuntimeTuningProfile(
                tier = tier,
                cpuCores = cores,
                memoryClassMb = memClass,
                isLowRamDevice = lowRam,
                accessibilityDebounceMs = 240L,
                whatsAppPollMs = 120L,
                timeoutScale = 1.0f
            )
        }

        AppLogger.i(
            TAG,
            "profile tier=${profile.tier} cores=${profile.cpuCores} memClassMb=${profile.memoryClassMb} " +
                "lowRam=${profile.isLowRamDevice} debounce=${profile.accessibilityDebounceMs} poll=${profile.whatsAppPollMs}"
        )

        cachedProfile = profile
        return profile
    }

    fun scaleTimeout(baseMs: Long, profile: RuntimeTuningProfile): Long {
        return (baseMs * profile.timeoutScale).roundToLong().coerceAtLeast(300L)
    }
}
