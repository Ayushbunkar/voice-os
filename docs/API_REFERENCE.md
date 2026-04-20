# API Reference (Core)

Base URL:
- /api/v1

## Auth

- POST /auth/register
- POST /auth/login
- GET /auth/me

## Commands

- POST /commands
- POST /commands/audio
- GET /commands/history

Compatibility alias:
- POST /command

## Devices

- GET /devices
- POST /devices/connect
- DELETE /devices/:id
- POST /devices/:id/command

Compatibility alias:
- POST /device/connect

## Macros

- GET /macros
- POST /macros
- PUT /macros/:id
- DELETE /macros/:id
- POST /macros/:id/execute

## Billing

- GET /billing/plans
- POST /billing/checkout
- POST /billing/checkout/razorpay
- POST /billing/portal
- POST /billing/webhook
- POST /billing/webhook/razorpay

## Analytics

- GET /analytics/summary

## Admin

- GET /admin/overview
