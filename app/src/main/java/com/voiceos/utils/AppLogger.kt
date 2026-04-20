package com.voiceos.utils

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
    private var enabled = true

    /** Call once from [com.voiceos.VoiceOSApp.onCreate]. */
    fun init(loggingEnabled: Boolean = true) {
        enabled = loggingEnabled
    }

    fun v(tag: String, msg: String) {
        if (enabled) Log.v("$PREFIX/$tag", msg)
    }

    fun d(tag: String, msg: String) {
        if (enabled) Log.d("$PREFIX/$tag", msg)
    }

    fun i(tag: String, msg: String) {
        if (enabled) Log.i("$PREFIX/$tag", msg)
    }

    fun w(tag: String, msg: String, throwable: Throwable? = null) {
        if (enabled) {
            if (throwable != null) Log.w("$PREFIX/$tag", msg, throwable)
            else Log.w("$PREFIX/$tag", msg)
        }
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (enabled) {
            if (throwable != null) Log.e("$PREFIX/$tag", msg, throwable)
            else Log.e("$PREFIX/$tag", msg)
        }
    }
}
