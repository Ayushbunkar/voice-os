import { query } from '../config/database';

export interface UserAnalyticsSummary {
  totalCommands: number;
  failedCommands: number;
  commandsToday: number;
  activeDevices: number;
  totalMacros: number;
  successRate: number;
}

export interface AdminOverview {
  users: number;
  devices: number;
  commands: number;
  commands24h: number;
  errors24h: number;
  activeSubscriptions: number;
}

function parseCount(value: string | number | null | undefined): number {
  if (typeof value === 'number') return value;
  if (typeof value === 'string') return parseInt(value, 10) || 0;
  return 0;
}

export const analyticsService = {
  async getUserSummary(userId: string): Promise<UserAnalyticsSummary> {
    const [commandAgg] = await query<{ total: string; failed: string }>(
      `SELECT
         COUNT(*)::text AS total,
         COUNT(*) FILTER (WHERE status = 'failed')::text AS failed
       FROM commands
       WHERE user_id = $1`,
      [userId]
    );

    const [todayRow] = await query<{ count: number }>(
      'SELECT count FROM command_usage WHERE user_id = $1 AND date = CURRENT_DATE',
      [userId]
    );

    const [activeDevices] = await query<{ count: string }>(
      `SELECT COUNT(*)::text AS count
       FROM devices
       WHERE user_id = $1 AND status = 'online'`,
      [userId]
    );

    const [macroCount] = await query<{ count: string }>(
      'SELECT COUNT(*)::text AS count FROM macros WHERE user_id = $1',
      [userId]
    );

    const totalCommands = parseCount(commandAgg?.total);
    const failedCommands = parseCount(commandAgg?.failed);
    const successRate = totalCommands > 0
      ? Number((((totalCommands - failedCommands) / totalCommands) * 100).toFixed(2))
      : 0;

    return {
      totalCommands,
      failedCommands,
      commandsToday: todayRow?.count ?? 0,
      activeDevices: parseCount(activeDevices?.count),
      totalMacros: parseCount(macroCount?.count),
      successRate,
    };
  },

  async getAdminOverview(): Promise<AdminOverview> {
    const [users] = await query<{ count: string }>('SELECT COUNT(*)::text AS count FROM users');
    const [devices] = await query<{ count: string }>('SELECT COUNT(*)::text AS count FROM devices');
    const [commands] = await query<{ count: string }>('SELECT COUNT(*)::text AS count FROM commands');

    const [commands24h] = await query<{ count: string }>(
      `SELECT COUNT(*)::text AS count
       FROM commands
       WHERE created_at >= NOW() - INTERVAL '24 hours'`
    );

    const [errors24h] = await query<{ count: string }>(
      `SELECT COUNT(*)::text AS count
       FROM commands
       WHERE created_at >= NOW() - INTERVAL '24 hours' AND status = 'failed'`
    );

    const [activeSubs] = await query<{ count: string }>(
      `SELECT COUNT(*)::text AS count
       FROM subscriptions
       WHERE status IN ('active', 'trialing')`
    );

    return {
      users: parseCount(users?.count),
      devices: parseCount(devices?.count),
      commands: parseCount(commands?.count),
      commands24h: parseCount(commands24h?.count),
      errors24h: parseCount(errors24h?.count),
      activeSubscriptions: parseCount(activeSubs?.count),
    };
  },

  async getRecentErrors(limit = 20): Promise<Array<{ id: string; input_text: string; error_message: string; created_at: string }>> {
    return query(
      `SELECT id, input_text, error_message, created_at
       FROM commands
       WHERE status = 'failed'
       ORDER BY created_at DESC
       LIMIT $1`,
      [limit]
    );
  },
};
