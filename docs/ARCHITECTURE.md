# VoiceOS Cloud Architecture

## Final System Topology

Android App (VoiceOS Assistant)
-> Node.js API Gateway (Express + TypeScript)
-> AI Engine (OpenAI LLM + Whisper)
-> PostgreSQL + Redis
-> Next.js Dashboard
-> WebSocket Layer (Socket.IO)
-> Device Clients (Android/Desktop)

## Step-by-Step Build Map

### Step 1: Backend setup
- Added modular route/controller structure under server/src.
- Added clean architecture layers:
  - server/src/services
  - server/src/models
  - server/src/automation
- Added strong request validation with express-validator.
- Added compatibility aliases for Android contracts:
  - POST /api/v1/command
  - POST /api/v1/device/connect

### Step 2: Database
- PostgreSQL schema includes users, devices, commands, macros, subscriptions, command_usage, webhook_events.
- Added production observability tables:
  - macro_runs
  - analytics_events
- Added unique user subscription constraint for upsert-safe billing sync.

### Step 3: AI module
- server/src/ai/llmService.ts
- server/src/ai/whisperService.ts
- server/src/ai/promptBuilder.ts
- Added strict structured command normalization via server/src/models/structuredCommand.ts.

### Step 4: WebSockets
- server/src/websocket/socketServer.ts handles:
  - JWT socket auth
  - Device registration
  - execute_command push
  - command_ack and command_result events
  - command status forwarding to dashboard

### Step 5: Next.js frontend
- Implemented dashboard routes:
  - /dashboard
  - /dashboard/devices
  - /dashboard/macros
  - /dashboard/history
  - /dashboard/billing
  - /dashboard/admin
- Added auth page:
  - /register
- Added aliases:
  - /devices
  - /macros
  - /history
  - /billing

### Step 6: Deployment
- Dockerfiles for backend and web retained.
- Added production stack:
  - docker-compose.prod.yml
  - infra/nginx/nginx.conf
  - infra/nginx/conf.d/voiceos.conf
- Added GitHub Actions pipeline for build/test/deploy.

## Security Controls
- JWT auth middleware with plan/admin claims.
- Route-level input validation.
- Global and command-specific rate limiting.
- Helmet headers.
- CORS allowlist.
- Stripe and Razorpay webhook signature validation.

## Analytics Coverage
- Command volume and success rate.
- Daily usage counters.
- Recent failures.
- Admin overview endpoint.
