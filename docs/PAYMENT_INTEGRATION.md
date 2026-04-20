# Payment Integration (Stripe + Razorpay)

## Implemented Backend Endpoints

- GET /api/v1/billing/plans
- POST /api/v1/billing/checkout
- POST /api/v1/billing/checkout/razorpay
- POST /api/v1/billing/portal
- POST /api/v1/billing/webhook
- POST /api/v1/billing/webhook/razorpay

## Stripe Setup

Environment variables:
- STRIPE_SECRET_KEY
- STRIPE_WEBHOOK_SECRET
- STRIPE_PRICE_PRO_MONTHLY
- STRIPE_PRICE_ENTERPRISE_MONTHLY

Webhook events handled:
- customer.subscription.created
- customer.subscription.updated
- customer.subscription.deleted

Plan enforcement:
- users.plan is updated to free/pro/enterprise.
- subscriptions table is upserted per user.

## Razorpay Setup

Environment variables:
- RAZORPAY_KEY_ID
- RAZORPAY_KEY_SECRET
- RAZORPAY_WEBHOOK_SECRET
- RAZORPAY_PLAN_PRO_MONTHLY
- RAZORPAY_PLAN_ENTERPRISE_MONTHLY

Checkout:
- POST /billing/checkout with provider=razorpay and plan=pro|enterprise

Webhook validation:
- x-razorpay-signature is validated using HMAC SHA256.
- Webhook idempotency is stored in webhook_events.

## Frontend Billing UX

Dashboard billing page supports provider switching:
- Stripe checkout redirect
- Razorpay subscription short_url redirect
- Stripe billing portal link

## Webhook Delivery Checklist

1. Expose API over HTTPS.
2. Register webhook URL in Stripe/Razorpay dashboard.
3. Point to:
   - https://api.voiceos.app/api/v1/billing/webhook
   - https://api.voiceos.app/api/v1/billing/webhook/razorpay
4. Confirm signatures are configured in environment.

## Failure Handling

- Duplicate webhook events are ignored using webhook_events table.
- Invalid signatures return HTTP 400.
- Missing provider configuration returns HTTP 503.
