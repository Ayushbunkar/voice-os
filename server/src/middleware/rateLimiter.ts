import { Request, Response, NextFunction } from 'express';
import { incrWithExpiry } from '../config/redis';
import { env } from '../config/env';
import { logger } from '../utils/logger';

/**
 * Per-user daily command limit enforcer.
 * Uses Redis INCR + EXPIRE for atomic, distributed counting.
 *
 * Limits by plan:
 *   free       → 50  commands/day
 *   pro        → 1000 commands/day
 *   enterprise → unlimited
 */
export async function commandRateLimit(
  req: Request, res: Response, next: NextFunction
): Promise<void> {
  if (!req.user) { next(); return; }

  const { userId, plan } = req.user;
  const today = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
  const key = `cmd_limit:${userId}:${today}`;

  // Seconds until midnight UTC
  const now = new Date();
  const midnight = new Date(now);
  midnight.setUTCHours(24, 0, 0, 0);
  const ttl = Math.floor((midnight.getTime() - now.getTime()) / 1000);

  const limits: Record<string, number> = {
    free:       env.plans.freeCommandsPerDay,
    pro:        env.plans.proCommandsPerDay,
    enterprise: env.plans.enterpriseCommandsPerDay,
  };
  const limit = limits[plan] ?? env.plans.freeCommandsPerDay;

  try {
    const count = await incrWithExpiry(key, ttl);

    // Attach headers so clients can show usage
    res.setHeader('X-RateLimit-Limit', limit);
    res.setHeader('X-RateLimit-Remaining', Math.max(0, limit - count));

    if (count > limit) {
      logger.warn('Command rate limit exceeded', { userId, plan, count, limit });
      res.status(429).json({
        success: false,
        message: `Daily command limit reached (${limit}). Upgrade your plan for more.`,
        resetAt: midnight.toISOString(),
      });
      return;
    }

    next();
  } catch (err) {
    // Redis failure should not block commands — degrade gracefully
    logger.error('Rate limit check failed', { error: (err as Error).message });
    next();
  }
}
