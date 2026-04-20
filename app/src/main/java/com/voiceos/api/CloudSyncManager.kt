package com.voiceos.api
import android.os.Build
import com.voiceos.memory.ContextManager
import com.voiceos.utils.AppLogger
import java.util.UUID

/**
 * CloudSyncManager centralizes Android -> VoiceOS Cloud API operations.
 *
 * Responsibilities:
 * - JWT login/signup persistence
 * - Device registration and stable token handling
 * - Text command submission
 * - Macro sync from cloud
 */
object CloudSyncManager {
    private const val TAG = "CloudSyncManager"

    suspend fun login(email: String, password: String): LoginResponse {
        val response = ApiClient.api.login(LoginRequest(email = email.trim(), password = password))
        if (response.success && !response.token.isNullOrBlank()) {
            ContextManager.saveAuthToken(response.token)
        }
        return response
    }

    suspend fun register(email: String, password: String, name: String): LoginResponse {
        val response = ApiClient.api.register(
            RegisterRequest(
                email = email.trim(),
                password = password,
                name = name.trim()
            )
        )
        if (response.success && !response.token.isNullOrBlank()) {
            ContextManager.saveAuthToken(response.token)
        }
        return response
    }

    suspend fun connectCurrentDevice(): ConnectResponse {
        val existingToken = ContextManager.getDeviceToken()
        val deviceToken = if (existingToken.isNullOrBlank()) {
            UUID.randomUUID().toString().also { ContextManager.saveDeviceToken(it) }
        } else {
            existingToken
        }

        val response = ApiClient.api.connectDevice(
            ConnectDeviceRequest(
                deviceName = Build.MODEL ?: "Android Device",
                deviceType = "android",
                deviceToken = deviceToken
            )
        )

        if (response.success) {
            val cloudDeviceId = response.device?.id
            if (!cloudDeviceId.isNullOrBlank()) {
                ContextManager.saveDeviceId(cloudDeviceId)
            }
            AppLogger.i(TAG, "Device synced with cloud")
        } else {
            AppLogger.w(TAG, "Device sync failed: ${response.message}")
        }

        return response
    }

    suspend fun sendTextCommand(input: String, contextHints: Map<String, String>? = null): CommandResponse {
        return ApiClient.api.processTextCommand(
            TextCommandRequest(
                input = input,
                deviceId = ContextManager.getDeviceId(),
                context = contextHints
            )
        )
    }

    suspend fun fetchCloudMacros(): List<MacroDto> {
        val response = ApiClient.api.getMacros()
        return if (response.success) response.macros else emptyList()
    }

    fun logout() {
        ContextManager.clearAuthToken()
    }
}
