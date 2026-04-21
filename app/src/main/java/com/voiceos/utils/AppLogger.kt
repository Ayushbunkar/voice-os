package com.voiceos.utils

import android.os.Build
import android.util.Log

/**
 * AppLogger — Centralised logging wrapper.
 *
 * Benefits over raw Log calls:
 *  - Single on/off switch for production builds.
 *  - Consistent tag prefix so logs are easy to filter: `adb logcat -s VoiceOS`
 *  - Timestamps included in verbose output.
 */
object AppLogger {

    private const val PREFIX = "VoiceOS"
    private const val LEGACY_MAX_TAG_LENGTH = 23
    private var enabled = true

    private fun buildTag(tag: String): String {
        val rawTag = "$PREFIX/$tag"
        // Android <= 7.x throws IllegalArgumentException when tag length > 23.
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && rawTag.length > LEGACY_MAX_TAG_LENGTH) {
            rawTag.take(LEGACY_MAX_TAG_LENGTH)
        } else {
            rawTag
        }
    }

    /** Call once from [com.voiceos.VoiceOSApp.onCreate]. */
    fun init(loggingEnabled: Boolean = true) {
        enabled = loggingEnabled
    }

    fun v(tag: String, msg: String) {
        if (enabled) Log.v(buildTag(tag), msg)
    }

    fun d(tag: String, msg: String) {
        if (enabled) Log.d(buildTag(tag), msg)
    }

    fun i(tag: String, msg: String) {
        if (enabled) Log.i(buildTag(tag), msg)
    }

    fun w(tag: String, msg: String, throwable: Throwable? = null) {
        if (enabled) {
            if (throwable != null) Log.w(buildTag(tag), msg, throwable)
            else Log.w(buildTag(tag), msg)
        }
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (enabled) {
            if (throwable != null) Log.e(buildTag(tag), msg, throwable)
            else Log.e(buildTag(tag), msg)
        }
    }
}
