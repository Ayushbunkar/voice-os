# Android API Integration Guide

## Required Endpoints

The Android app now supports these core cloud endpoints:
- POST /api/v1/auth/register
- POST /api/v1/auth/login
- POST /api/v1/device/connect
- POST /api/v1/command
- GET /api/v1/macros
- Socket.IO event channel for real-time execute_command

## Implemented Android Cloud Layer

Files:
- app/src/main/java/com/voiceos/api/VoiceOSApi.kt
- app/src/main/java/com/voiceos/api/NetworkModels.kt
- app/src/main/java/com/voiceos/api/CloudSyncManager.kt
- app/src/main/java/com/voiceos/api/SocketManager.kt
- app/src/main/java/com/voiceos/memory/ContextManager.kt
- app/src/main/java/com/voiceos/VoiceOSApp.kt

## Authentication Flow

1. Call CloudSyncManager.login(email, password) or CloudSyncManager.register(...).
2. On success, JWT is stored by ContextManager.saveAuthToken(...).
3. App startup reads token and syncs device before opening socket.

## Device Identity Flow

Two IDs are stored:
- device_token: stable local token (for device reconnect/upsert)
- device_id: cloud device ID returned by backend (for websocket register and command routing)

CloudSyncManager.connectCurrentDevice() handles this automatically.

## Command Flow

1. UI/voice pipeline resolves text command.
2. CloudSyncManager.sendTextCommand(input, contextHints) sends POST /api/v1/command.
3. Backend returns structured actions and optionally pushes execute_command in real time.

## Macro Sync

Call CloudSyncManager.fetchCloudMacros() to fetch GET /api/v1/macros.

## Real-time Execution

SocketManager now:
- authenticates via JWT
- registers cloud device ID
- receives execute_command envelope
- emits command_ack and command_result back to backend

## Base URL Configuration

ApiClient.kt currently uses:
- http://10.0.2.2:5000/ for emulator

For production, change to your API domain, for example:
- https://api.voiceos.app/

## Integration Notes

- Ensure ContextManager.init(context) runs on app startup.
- Device must be connected at least once to get cloud device ID.
- Keep JWT refresh/login UX in your app screens as needed.
