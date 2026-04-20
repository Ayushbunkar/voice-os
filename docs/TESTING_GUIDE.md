# Testing Guide

## Backend API Tests (Jest + Supertest)

Implemented test files:
- server/tests/api/health.test.ts
- server/tests/api/auth.validation.test.ts
- server/tests/api/billing.plans.test.ts

Run commands:
- cd server
- npm run test:api
- npm run test

## What Is Covered

- Health endpoint availability
- Auth validation guard behavior (422 on malformed payloads)
- Billing plans endpoint shape and success response

## Recommended Next Tests

1. Auth success login/register with seeded test DB.
2. Command processing with mocked OpenAI API.
3. Macro execution flow and websocket delivery.
4. Billing webhook signature verification tests.

## E2E Test Strategy

Recommended stack:
- API e2e: supertest + ephemeral Postgres/Redis via docker compose
- Web e2e: Playwright
- Android/device simulation: emulator + websocket mock driver

## Device Simulation Checklist

1. Start backend and web locally.
2. Login from dashboard.
3. Connect Android app (CloudSyncManager + SocketManager).
4. Send remote command from dashboard devices page.
5. Verify command_ack and command_result events in server logs.
6. Verify command history updates in /dashboard/history.

## CI Integration

GitHub Actions workflow is at:
- .github/workflows/ci-cd.yml

Pipeline stages:
- backend build + tests
- web build
- docker image builds
- deploy (main branch)
