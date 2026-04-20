import { Request, Response, NextFunction } from 'express';
import { query } from '../config/database';
import { getSocketServer } from '../websocket/socketServer';
import { logger } from '../utils/logger';
import { v4 as uuidv4 } from 'uuid';

interface DeviceRow {
  id: string; user_id: string; device_name: string;
  device_type: string; status: string; last_seen_at: string; device_token: string;
}

export const deviceController = {

  /** POST /api/v1/devices/connect — register or update a device */
  async connect(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { deviceName, deviceType = 'android', deviceToken } = req.body;
      const userId = req.user!.userId;
      const token = (typeof deviceToken === 'string' && deviceToken.trim()) ? deviceToken.trim() : uuidv4();

      // A device token can never be claimed by another account.
      const existingByToken = await query<{ user_id: string }>(
        'SELECT user_id FROM devices WHERE device_token = $1 LIMIT 1',
        [token]
      );

      if (existingByToken[0] && existingByToken[0].user_id !== userId) {
        res.status(409).json({ success: false, message: 'This device token is already linked to another account' });
        return;
      }

      // Enforce plan-level device limits only for new device tokens.
      if (!existingByToken[0]) {
        const planLimits: Record<string, number> = {
          free: 1,
          pro: 5,
          enterprise: 999999,
        };

        const plan = req.user!.plan;
        const limit = planLimits[plan] ?? planLimits.free;
        const countRows = await query<{ count: string }>(
          'SELECT COUNT(*)::text AS count FROM devices WHERE user_id = $1',
          [userId]
        );

        const currentDevices = parseInt(countRows[0]?.count ?? '0', 10);
        if (currentDevices >= limit) {
          res.status(403).json({
            success: false,
            message: `Your ${plan} plan supports up to ${limit} device(s). Upgrade to add more devices.`,
          });
          return;
        }
      }

      // Upsert: if device_token already exists, update; otherwise insert
      const rows = await query<DeviceRow>(
        `INSERT INTO devices (id, user_id, device_name, device_type, device_token, status, last_seen_at)
         VALUES ($1, $2, $3, $4, $5, 'online', NOW())
         ON CONFLICT (device_token) DO UPDATE
           SET user_id = EXCLUDED.user_id,
               device_name = EXCLUDED.device_name,
               device_type = EXCLUDED.device_type,
               status = 'online',
               last_seen_at = NOW()
         RETURNING *`,
        [uuidv4(), userId, deviceName, deviceType, token]
      );

      logger.info('Device connected', { userId, deviceId: rows[0].id, deviceType });
      res.json({ success: true, device: rows[0] });
    } catch (err) { next(err); }
  },

  /** GET /api/v1/devices — list user's devices */
  async list(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const rows = await query<DeviceRow>(
        `SELECT id, device_name, device_type, status, last_seen_at, device_token
         FROM devices WHERE user_id = $1 ORDER BY last_seen_at DESC`,
        [req.user!.userId]
      );
      res.json({ success: true, devices: rows });
    } catch (err) { next(err); }
  },

  /** DELETE /api/v1/devices/:id — remove a device */
  async remove(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      await query('DELETE FROM devices WHERE id = $1 AND user_id = $2', [req.params.id, req.user!.userId]);
      res.json({ success: true, message: 'Device removed' });
    } catch (err) { next(err); }
  },

  /** POST /api/v1/devices/:id/command — send remote command to device */
  async sendRemoteCommand(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { id: deviceId } = req.params;
      const { structured } = req.body;

      const rows = await query<DeviceRow>(
        'SELECT device_token FROM devices WHERE id = $1 AND user_id = $2',
        [deviceId, req.user!.userId]
      );
      if (!rows[0]) { res.status(404).json({ success: false, message: 'Device not found' }); return; }

      const sent = getSocketServer()?.sendToDevice(deviceId, 'execute_command', {
        commandId: `remote:${Date.now()}`,
        source: 'dashboard',
        structured,
      }) ?? false;
      res.json({ success: true, delivered: sent });
    } catch (err) { next(err); }
  },
};
