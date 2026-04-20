import { query } from '../config/database';

export async function recordDailyUsage(userId: string): Promise<void> {
  await query(
    `INSERT INTO command_usage (user_id, date, count)
     VALUES ($1, CURRENT_DATE, 1)
     ON CONFLICT (user_id, date)
     DO UPDATE SET count = command_usage.count + 1`,
    [userId]
  );
}

export async function getDailyUsage(userId: string): Promise<number> {
  const rows = await query<{ count: number }>(
    `SELECT count
     FROM command_usage
     WHERE user_id = $1 AND date = CURRENT_DATE`,
    [userId]
  );

  return rows[0]?.count ?? 0;
}
