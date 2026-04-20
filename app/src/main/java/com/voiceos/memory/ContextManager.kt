package com.voiceos.memory

import android.content.Context
import android.content.SharedPreferences
import com.voiceos.model.Command
import com.voiceos.utils.AppLogger

/**
 * ContextManager — Persistent short-term memory for the VoiceOS assistant.
 *
 * Stores the most recently used values so the assistant can handle
 * context-aware commands like:
 *   • "scroll more"  →  remembers last scroll direction
 *   • "send another message"  →  remembers last contact + message
 *   • "open it again"  →  remembers last opened app
 *
 * Data is backed by SharedPreferences so it survives process restarts.
 */
class ContextManager private constructor(context: Context) {

    private val TAG = "ContextManager"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("voiceos_context", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_APP = "last_app"
        private const val KEY_LAST_CONTACT = "last_contact"
        private const val KEY_LAST_MESSAGE = "last_message"
        private const val KEY_LAST_SCROLL = "last_scroll"
        private const val KEY_LAST_ACTION = "last_action"
        private const val KEY_LAST_MACRO = "last_macro"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_TOKEN = "device_token"

        @Volatile
        private var INSTANCE: ContextManager? = null

        @Volatile
        private var appContext: Context? = null

        fun init(context: Context) {
            appContext = context.applicationContext
            getInstance(context)
        }

        private fun sessionPrefs(): SharedPreferences {
            val ctx = appContext
                ?: throw IllegalStateException("ContextManager.init(context) must be called before session methods")
            return ctx.getSharedPreferences("voiceos_session", Context.MODE_PRIVATE)
        }

        fun getInstance(context: Context): ContextManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ContextManager(context.applicationContext).also { INSTANCE = it }
            }

        fun saveAuthToken(token: String) {
            sessionPrefs().edit().putString(KEY_AUTH_TOKEN, token).apply()
        }

        fun getAuthToken(): String? = sessionPrefs().getString(KEY_AUTH_TOKEN, null)

        fun clearAuthToken() {
            sessionPrefs().edit().remove(KEY_AUTH_TOKEN).apply()
        }

        fun saveDeviceId(deviceId: String) {
            sessionPrefs().edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }

        fun getDeviceId(): String? = sessionPrefs().getString(KEY_DEVICE_ID, null)

        fun saveDeviceToken(deviceToken: String) {
            sessionPrefs().edit().putString(KEY_DEVICE_TOKEN, deviceToken).apply()
        }

        fun getDeviceToken(): String? = sessionPrefs().getString(KEY_DEVICE_TOKEN, null)
    }

    // ── Persisted properties ──────────────────────────────────────────

    var lastApp: String?
        get() = prefs.getString(KEY_LAST_APP, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_APP, value).apply()
            AppLogger.d(TAG, "Remembered lastApp=$value")
        }

    var lastContact: String?
        get() = prefs.getString(KEY_LAST_CONTACT, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_CONTACT, value).apply()
            AppLogger.d(TAG, "Remembered lastContact=$value")
        }

    var lastMessage: String?
        get() = prefs.getString(KEY_LAST_MESSAGE, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_MESSAGE, value).apply()
        }

    var lastScrollDirection: Command.ScrollDirection?
        get() {
            val s = prefs.getString(KEY_LAST_SCROLL, null) ?: return null
            return runCatching { Command.ScrollDirection.valueOf(s) }.getOrNull()
        }
        set(value) {
            prefs.edit().putString(KEY_LAST_SCROLL, value?.name).apply()
        }

    var lastAction: String?
        get() = prefs.getString(KEY_LAST_ACTION, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_ACTION, value).apply()
        }

    var lastMacro: String?
        get() = prefs.getString(KEY_LAST_MACRO, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_MACRO, value).apply()
        }

    // ── Convenience updaters ──────────────────────────────────────────

    /** Call this whenever a SendMessage command is executed. */
    fun rememberMessage(contact: String, message: String, app: String) {
        lastContact = contact
        lastMessage = message
        lastApp = app
        lastAction = "send_message"
    }

    /** Call this whenever a scroll is performed. */
    fun rememberScroll(direction: Command.ScrollDirection) {
        lastScrollDirection = direction
        lastAction = "scroll"
    }

    /** Call this whenever an app is opened. */
    fun rememberApp(appName: String) {
        lastApp = appName
        lastAction = "open_app"
    }

    /** Dump current context state to log for debugging. */
    fun dump() {
        AppLogger.d(TAG, """
            Context dump:
              lastApp=$lastApp
              lastContact=$lastContact
              lastMessage=$lastMessage
              lastScroll=$lastScrollDirection
              lastAction=$lastAction
        """.trimIndent())
    }
}
