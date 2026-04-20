# Device Simulation Guide

## Goal

Simulate Android/desktop clients connected to VoiceOS Cloud for command routing and realtime status testing.

## Local Setup

1. Start backend and dependencies.
2. Start web dashboard.
3. Login and create at least one device record using /device/connect.
4. Open websocket client for device simulation.

## Minimal Socket Event Contract

Client sends:
- authenticate { token }
- device_register { deviceId }
- command_ack { commandId, accepted }
- command_result { commandId, status, error? }

Server sends:
- execute_command { commandId, source, structured }

## Suggested Simulator Behavior

1. Authenticate JWT.
2. Register device ID.
3. On execute_command:
   - immediately emit command_ack
   - perform simulated delay
   - emit command_result success/failed

## Validation Targets

- Dashboard remote command status updates in real time.
- Command history reflects status and model metadata.
- Macro execution logs appear in macro_runs table.
