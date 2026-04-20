import { Request, Response, NextFunction } from 'express';
import Stripe from 'stripe';
import Razorpay from 'razorpay';
import crypto from 'crypto';
import { query } from '../config/database';
import { env } from '../config/env';
import { logger } from '../utils/logger';
import { sendSubscriptionEmail } from '../services/emailService';

const stripe = env.stripe.secretKey
  ? new Stripe(env.stripe.secretKey, { apiVersion: '2024-04-10' as Stripe.LatestApiVersion })
  : null;

const razorpay = env.razorpay.keyId && env.razorpay.keySecret
  ? new Razorpay({ key_id: env.razorpay.keyId, key_secret: env.razorpay.keySecret })
  : null;

function resolveStripePlan(priceId: string): 'pro' | 'enterprise' {
  return priceId === env.stripe.proPriceId ? 'pro' : 'enterprise';
}

function resolveRazorpayPlan(planId: string): 'pro' | 'enterprise' {
  return planId === env.razorpay.proPlanId ? 'pro' : 'enterprise';
}

async function getUserEmail(userId: string): Promise<string | null> {
  const rows = await query<{ email: string }>('SELECT email FROM users WHERE id = $1 LIMIT 1', [userId]);
  return rows[0]?.email ?? null;
}

export const paymentController = {

  /** GET /api/v1/billing/plans — return available plans */
  async getPlans(_req: Request, res: Response): Promise<void> {
    res.json({
      success: true,
      plans: [
        {
          id: 'free',
          name: 'Free',
          price: 0,
          currency: 'USD',
          commandsPerDay: env.plans.freeCommandsPerDay,
          features: ['50 commands/day', '1 device', 'Basic AI'],
          providers: [],
        },
        {
          id: 'pro',
          name: 'Pro',
          price: 999,
          currency: 'USD',
          priceId: env.stripe.proPriceId,
          razorpayPlanId: env.razorpay.proPlanId,
          commandsPerDay: env.plans.proCommandsPerDay,
          features: ['1000 commands/day', '5 devices', 'GPT-4 AI', 'Cloud macros', 'Priority support'],
          providers: ['stripe', 'razorpay'],
        },
        {
          id: 'enterprise',
          name: 'Enterprise',
          price: 4999,
          currency: 'USD',
          priceId: env.stripe.enterprisePriceId,
          razorpayPlanId: env.razorpay.enterprisePlanId,
          commandsPerDay: env.plans.enterpriseCommandsPerDay,
          features: ['Unlimited commands', 'Unlimited devices', 'Custom AI', 'Dedicated support', 'SLA'],
          providers: ['stripe', 'razorpay'],
        },
      ],
    });
  },

  /** POST /api/v1/billing/checkout — create Stripe or Razorpay checkout */
  async createCheckout(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const provider = req.body.provider || 'stripe';
      if (provider === 'razorpay') {
        await paymentController.createRazorpaySubscription(req, res, next);
        return;
      }

      if (!stripe) { res.status(503).json({ success: false, message: 'Payment provider not configured' }); return; }
      const { priceId } = req.body;
      const userId = req.user!.userId;
      const email = req.user!.email;

      // Get or create Stripe customer
      let customerId: string | undefined;
      const rows = await query<{ stripe_customer_id: string }>(
        'SELECT stripe_customer_id FROM subscriptions WHERE user_id = $1 LIMIT 1', [userId]
      );
      customerId = rows[0]?.stripe_customer_id;

      if (!customerId) {
        const customer = await stripe.customers.create({ email, metadata: { userId } });
        customerId = customer.id;
      }

      const session = await stripe.checkout.sessions.create({
        customer: customerId,
        payment_method_types: ['card'],
        line_items: [{ price: priceId, quantity: 1 }],
        mode: 'subscription',
        success_url: `${env.frontendUrl}/billing?success=true`,
        cancel_url: `${env.frontendUrl}/billing?cancelled=true`,
        metadata: { userId },
        allow_promotion_codes: true,
      });

      res.json({ success: true, provider: 'stripe', url: session.url });
    } catch (err) { next(err); }
  },

  /** POST /api/v1/billing/checkout/razorpay — create Razorpay subscription */
  async createRazorpaySubscription(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      if (!razorpay) {
        res.status(503).json({ success: false, message: 'Razorpay is not configured' });
        return;
      }

      const plan = req.body.plan === 'enterprise' ? 'enterprise' : 'pro';
      const planId = plan === 'enterprise' ? env.razorpay.enterprisePlanId : env.razorpay.proPlanId;
      if (!planId) {
        res.status(400).json({ success: false, message: `Razorpay plan ID missing for ${plan}` });
        return;
      }

      const subscription = await razorpay.subscriptions.create({
        plan_id: planId,
        total_count: 12,
        customer_notify: 1,
        notes: {
          userId: req.user!.userId,
          email: req.user!.email,
        },
      });

      res.json({
        success: true,
        provider: 'razorpay',
        keyId: env.razorpay.keyId,
        plan,
        subscriptionId: subscription.id,
        shortUrl: (subscription as unknown as { short_url?: string }).short_url ?? null,
      });
    } catch (err) {
      next(err);
    }
  },

  /** POST /api/v1/billing/portal — Stripe customer portal */
  async createPortal(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      if (req.body.provider === 'razorpay') {
        res.status(400).json({ success: false, message: 'Razorpay does not support customer portal sessions in this API' });
        return;
      }

      if (!stripe) { res.status(503).json({ success: false, message: 'Payment not configured' }); return; }
      const rows = await query<{ stripe_customer_id: string }>(
        'SELECT stripe_customer_id FROM subscriptions WHERE user_id = $1 LIMIT 1', [req.user!.userId]
      );
      if (!rows[0]?.stripe_customer_id) {
        res.status(400).json({ success: false, message: 'No active subscription found' }); return;
      }
      const session = await stripe.billingPortal.sessions.create({
        customer: rows[0].stripe_customer_id,
        return_url: `${env.frontendUrl}/billing`,
      });
      res.json({ success: true, provider: 'stripe', url: session.url });
    } catch (err) { next(err); }
  },

  /** POST /api/v1/billing/webhook — Stripe webhook handler */
  async stripeWebhook(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      if (!stripe) { res.status(503).send('Not configured'); return; }
      const sig = req.headers['stripe-signature'] as string;
      let event: Stripe.Event;
      try {
        event = stripe.webhooks.constructEvent(req.body as Buffer, sig, env.stripe.webhookSecret);
      } catch {
        res.status(400).send('Webhook signature invalid');
        return;
      }

      // Idempotency check
      const existing = await query('SELECT id FROM webhook_events WHERE id = $1', [event.id]);
      if (existing.length > 0) { res.json({ received: true }); return; }
      await query('INSERT INTO webhook_events (id, provider, type, payload) VALUES ($1,$2,$3,$4)',
        [event.id, 'stripe', event.type, JSON.stringify(event.data)]);

      switch (event.type) {
        case 'customer.subscription.created':
        case 'customer.subscription.updated': {
          const sub = event.data.object as Stripe.Subscription;
          const userId = sub.metadata.userId || (sub.customer as string);
          const plan = resolveStripePlan(sub.items.data[0]?.price.id || '');
          await query(`UPDATE users SET plan = $1 WHERE id = $2`, [plan, userId]);
          await query(
            `INSERT INTO subscriptions (user_id, plan, status, stripe_customer_id, stripe_subscription_id, current_period_start, current_period_end)
             VALUES ($1,$2,$3,$4,$5, to_timestamp($6), to_timestamp($7))
             ON CONFLICT (user_id) WHERE user_id = $1 DO UPDATE SET plan=$2, status=$3, current_period_end=to_timestamp($7)`,
            [userId, plan, sub.status, sub.customer as string, sub.id,
             (sub as unknown as { current_period_start: number }).current_period_start,
             (sub as unknown as { current_period_end: number }).current_period_end]
          );

          const userEmail = await getUserEmail(userId);
          if (userEmail) {
            await sendSubscriptionEmail(userEmail, plan, sub.status).catch(() => undefined);
          }

          logger.info('Subscription active', { userId, plan });
          break;
        }
        case 'customer.subscription.deleted': {
          const sub = event.data.object as Stripe.Subscription;
          const userId = sub.metadata.userId;
          if (userId) {
            await query(`UPDATE users SET plan = 'free' WHERE id = $1`, [userId]);
            await query(
              `UPDATE subscriptions
               SET status = 'cancelled', plan = 'free', updated_at = NOW()
               WHERE user_id = $1`,
              [userId]
            );

            const userEmail = await getUserEmail(userId);
            if (userEmail) {
              await sendSubscriptionEmail(userEmail, 'free', 'cancelled').catch(() => undefined);
            }
          }
          break;
        }
      }

      await query('UPDATE webhook_events SET processed = TRUE WHERE id = $1', [event.id]);
      res.json({ received: true });
    } catch (err) { next(err); }
  },

  /** POST /api/v1/billing/webhook/razorpay — Razorpay webhook handler */
  async razorpayWebhook(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      if (!razorpay || !env.razorpay.webhookSecret) {
        res.status(503).send('Not configured');
        return;
      }

      const signature = req.headers['x-razorpay-signature'] as string | undefined;
      const eventIdHeader = req.headers['x-razorpay-event-id'] as string | undefined;

      const payloadString = Buffer.isBuffer(req.body)
        ? req.body.toString('utf8')
        : JSON.stringify(req.body);

      const expectedSignature = crypto
        .createHmac('sha256', env.razorpay.webhookSecret)
        .update(payloadString)
        .digest('hex');

      if (!signature || signature !== expectedSignature) {
        res.status(400).send('Webhook signature invalid');
        return;
      }

      const event = JSON.parse(payloadString) as {
        event: string;
        payload?: {
          subscription?: {
            entity?: {
              id: string;
              status: string;
              plan_id?: string;
              customer_id?: string;
              current_start?: number;
              current_end?: number;
              notes?: { userId?: string };
            };
          };
        };
      };

      const eventId = eventIdHeader || `razorpay-${Date.now()}`;

      const existing = await query('SELECT id FROM webhook_events WHERE id = $1', [eventId]);
      if (existing.length > 0) {
        res.json({ received: true });
        return;
      }

      await query(
        'INSERT INTO webhook_events (id, provider, type, payload) VALUES ($1,$2,$3,$4)',
        [eventId, 'razorpay', event.event, JSON.stringify(event)]
      );

      const entity = event.payload?.subscription?.entity;
      const userId = entity?.notes?.userId;

      if (entity && userId) {
        const plan = resolveRazorpayPlan(entity.plan_id || '');
        const statusMap: Record<string, string> = {
          active: 'active',
          authenticated: 'trialing',
          created: 'trialing',
          cancelled: 'cancelled',
          halted: 'past_due',
        };
        const normalizedStatus = statusMap[entity.status] || 'active';

        await query(`UPDATE users SET plan = $1 WHERE id = $2`, [plan, userId]);
        await query(
          `INSERT INTO subscriptions (user_id, plan, status, razorpay_sub_id, current_period_start, current_period_end)
           VALUES ($1,$2,$3,$4,to_timestamp($5),to_timestamp($6))
           ON CONFLICT (user_id) WHERE user_id = $1
           DO UPDATE SET
             plan = $2,
             status = $3,
             razorpay_sub_id = $4,
             current_period_start = to_timestamp($5),
             current_period_end = to_timestamp($6),
             updated_at = NOW()`,
          [
            userId,
            plan,
            normalizedStatus,
            entity.id,
            entity.current_start || Math.floor(Date.now() / 1000),
            entity.current_end || Math.floor(Date.now() / 1000),
          ]
        );

        const userEmail = await getUserEmail(userId);
        if (userEmail) {
          await sendSubscriptionEmail(userEmail, plan, normalizedStatus).catch(() => undefined);
        }

        logger.info('Razorpay subscription synced', {
          userId,
          subscriptionId: entity.id,
          status: normalizedStatus,
          plan,
        });
      }

      await query('UPDATE webhook_events SET processed = TRUE WHERE id = $1', [eventId]);
      res.json({ received: true });
    } catch (err) {
      next(err);
    }
  },
};
