package com.voiceos.api

import android.content.Context
import com.google.gson.Gson
import com.voiceos.commands.CommandHandler
import com.voiceos.memory.ContextManager
import com.voiceos.utils.AppLogger
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

object SocketManager {
    private const val TAG = "SocketManager"
    // Using IP of host for Android Emulators
    private const val SOCKET_URL = "http://10.0.2.2:5000"

    private var socket: Socket? = null
    private var appContext: Context? = null
    private var commandHandler: CommandHandler? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val gson = Gson()

    fun init(context: Context) {
        appContext = context.applicationContext
        commandHandler = CommandHandler(context.applicationContext)

        val token = ContextManager.getAuthToken()
        if (token == null) {
            AppLogger.w(TAG, "Cannot start Socket, no JWT token available.")
            return
        }

        val deviceId = ContextManager.getDeviceId()
        if (deviceId.isNullOrBlank()) {
            AppLogger.w(TAG, "Cannot start Socket, no cloud deviceId available. Run device connect first.")
            return
        }

        try {
            val options = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = 10
            }
            
            socket = IO.socket(SOCKET_URL, options)
            setupListeners(token, deviceId)
            socket?.connect()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Socket setup failed: ${e.message}")
        }
    }

    private fun setupListeners(token: String, deviceId: String) {
        val s = socket ?: return

        s.on(Socket.EVENT_CONNECT) {
            AppLogger.i(TAG, "Socket Connected. Authenticating...")
            
            // 1. Authenticate with JWT
            val authJson = JSONObject().put("token", token)
            s.emit("authenticate", authJson)
        }

        s.on("authenticated") {
            AppLogger.i(TAG, "Socket Authenticated! Registering device...")

            val regJson = JSONObject().put("deviceId", deviceId)
            s.emit("device_register", regJson)
        }

        // 3. Listen for commands originating from the Next.js Web Dashboard
        s.on("execute_command") { args ->
            if (args.isEmpty()) return@on
            
            try {
                val rawPayload = args[0].toString()
                val payloadJson = JSONObject(rawPayload)
                val commandId = payloadJson.optString("commandId", "socket-${System.currentTimeMillis()}")
                val structuredJson = if (payloadJson.has("structured")) {
                    payloadJson.getJSONObject("structured").toString()
                } else {
                    rawPayload
                }

                AppLogger.i(TAG, "Received Cloud Command: $structuredJson")

                val dto = gson.fromJson(structuredJson, CommandDto::class.java)
                val internalCommand = dto.toInternalCommand()

                s.emit("command_ack", JSONObject()
                    .put("commandId", commandId)
                    .put("accepted", true))

                // Execute via Android routing
                scope.launch(Dispatchers.Main) {
                    try {
                        commandHandler?.execute(internalCommand)
                        s.emit("command_result", JSONObject()
                            .put("commandId", commandId)
                            .put("status", "success"))
                    } catch (e: Exception) {
                        s.emit("command_result", JSONObject()
                            .put("commandId", commandId)
                            .put("status", "failed")
                            .put("error", e.message ?: "Execution failed"))
                    }
                }

            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to parse remote command: ${e.message}")
            }
        }

        s.on(Socket.EVENT_DISCONNECT) {
            AppLogger.w(TAG, "Socket disconnected")
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
