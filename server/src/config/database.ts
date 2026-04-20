import { Pool, PoolClient } from 'pg';
import { env } from './env';
import { logger } from '../utils/logger';

/**
 * PostgreSQL connection pool.
 * All queries go through this single pool instance.
 */
export const pool = new Pool({
  connectionString: env.db.url,
  ssl: env.db.ssl ? { rejectUnauthorized: false } : false,
  max: 20,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 2_000,
});

pool.on('connect', () => logger.debug('PostgreSQL: new client connected'));
pool.on('error', (err) => logger.error('PostgreSQL pool error', { error: err.message }));

/** Execute a parameterised query. */
export async function query<T = unknown>(
  sql: string,
  values?: unknown[]
): Promise<T[]> {
  const start = Date.now();
  try {
    const res = await pool.query(sql, values);
    logger.debug('DB query', { sql: sql.slice(0, 80), duration: Date.now() - start });
    return res.rows as T[];
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    logger.error('DB query error', { sql: sql.slice(0, 80), error: msg });
    throw err;
  }
}

/** Execute multiple queries inside a single transaction. */
export async function withTransaction<T>(
  fn: (client: PoolClient) => Promise<T>
): Promise<T> {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await fn(client);
    await client.query('COMMIT');
    return result;
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

/** Test the connection on startup. */
export async function connectDatabase(): Promise<void> {
  const rows = await query<{ now: Date }>('SELECT NOW() as now');
  logger.info(`PostgreSQL connected — server time: ${rows[0].now}`);
}
