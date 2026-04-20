import { createClient, RedisClientType } from 'redis';
import { env } from './env';
import { logger } from '../utils/logger';

let redisClient: RedisClientType | null = null;

type LocalCacheEntry = {
  value: string;
  expiresAt: number | null;
};

type LocalCounterEntry = {
  count: number;
  expiresAt: number;
};

const localCache = new Map<string, LocalCacheEntry>();
const localCounters = new Map<string, LocalCounterEntry>();

function isRedisReady(): boolean {
  return Boolean(redisClient?.isOpen);
}

function getLocalCacheValue(key: string): string | null {
  const entry = localCache.get(key);
  if (!entry) return null;
  if (entry.expiresAt !== null && entry.expiresAt <= Date.now()) {
    localCache.delete(key);
    return null;
  }
  return entry.value;
}

function setLocalCacheValue(key: string, value: string, ttlSeconds: number): void {
  localCache.set(key, {
    value,
    expiresAt: ttlSeconds > 0 ? Date.now() + ttlSeconds * 1000 : null,
  });
}

function incrLocalCounter(key: string, ttlSeconds: number): number {
  const now = Date.now();
  const current = localCounters.get(key);

  if (!current || current.expiresAt <= now) {
    localCounters.set(key, { count: 1, expiresAt: now + ttlSeconds * 1000 });
    return 1;
  }

  current.count += 1;
  localCounters.set(key, current);
  return current.count;
}

export async function connectRedis(): Promise<any> {
  try {
    redisClient = createClient({
      url: env.redis.url,
      password: env.redis.password,
      socket: {
        connectTimeout: 5000,
        reconnectStrategy: (retries: number) => {
          // Keep retries bounded to avoid startup loops.
          if (retries > 5) return false;
          return Math.min(retries * 250, 2000);
        },
      },
    });

    redisClient.on('connect', () => logger.info('Redis: connected'));
    redisClient.on('ready', () => logger.info('Redis: ready'));
    redisClient.on('error', (err: any) => {
      const readableError =
        (typeof err?.message === 'string' && err.message.trim().length > 0 && err.message) ||
        err?.code ||
        err?.name ||
        String(err);

      logger.error('Redis error', {
        error: readableError,
        code: err?.code,
      });
    });
    redisClient.on('reconnecting', () => logger.warn('Redis: reconnecting...'));

    await redisClient.connect();
    return redisClient;
  } catch (err: any) {
    const errorMessage =
      (typeof err?.message === 'string' && err.message.trim().length > 0 && err.message) ||
      err?.code ||
      err?.name ||
      String(err);

    if (env.isProd) {
      throw new Error(`Redis startup failed: ${errorMessage}`);
    }

    logger.warn('Redis unavailable in development; falling back to in-memory cache/rate limiter', {
      error: errorMessage,
    });
    redisClient = null;
    return null;
  }
}

export function getRedis(): any {
  if (!isRedisReady()) throw new Error('Redis is not connected');
  return redisClient;
}

// ── Cache helpers ──────────────────────────────────────────────────────────

export async function cacheGet<T>(key: string): Promise<T | null> {
  const raw = isRedisReady()
    ? await getRedis().get(key)
    : getLocalCacheValue(key);
  return raw ? (JSON.parse(raw) as T) : null;
}

export async function cacheSet(key: string, value: unknown, ttlSeconds = 300): Promise<void> {
  if (isRedisReady()) {
    await getRedis().setEx(key, ttlSeconds, JSON.stringify(value));
    return;
  }

  setLocalCacheValue(key, JSON.stringify(value), ttlSeconds);
}

export async function cacheDel(key: string): Promise<void> {
  if (isRedisReady()) {
    await getRedis().del(key);
    return;
  }

  localCache.delete(key);
}

/** Increment a counter and return the new value. Used for rate limiting. */
export async function incrWithExpiry(key: string, ttlSeconds: number): Promise<number> {
  if (isRedisReady()) {
    const redis = getRedis();
    const count = await redis.incr(key);
    if (count === 1) await redis.expire(key, ttlSeconds);
    return count;
  }

  return incrLocalCounter(key, ttlSeconds);
}
