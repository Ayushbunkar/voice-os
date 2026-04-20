import { Server as HttpServer } from 'http';
import { Server as IOServer, Socket } from 'socket.io';
import jwt from 'jsonwebtoken';
import { env } from '../config/env';
import { query } from '../config/database';
import { logger } from '../utils/logger';

interface AuthSocket extends Socket {
  userId?: string;
  deviceId?: string;
}

let ioInstance: VoiceOSSocketServer | null = null;

/**
 * VoiceOSSocketServer — Real-time WebSocket layer.
 *
 * Clients authenticate by sending their JWT as the first message.
 * Devices register their deviceId and are stored in a Map so the
 * HTTP layer can push commands directly to a connected device.
 *
 * Events emitted TO device:
 *   execute_command  { structured }   — run a voice command
 *   macro_execute    { macroName }    — run a named macro
 *
 * Events received FROM device:
 *   authenticate     { token }
 *   device_register  { deviceId }
 *   command_result   { commandId, status, error? }
 *   heartbeat
 */
export class VoiceOSSocketServer {
  private io: IOServer;
  /** deviceId → socket.id */
  private deviceMap = new Map<string, string>();

  constructor(httpServer: HttpServer) {
    this.io = new IOServer(httpServer, {
      cors: {
        origin: [env.frontendUrl, 'voiceos://app'],
        methods: ['GET', 'POST'],
        credentials: true,
      },
      pingTimeout: 30_000,
      pingInterval: 10_000,
    });

    this.io.on('connection', (socket: AuthSocket) => {
      logger.debug('Socket connected', { id: socket.id });

      // ── 1. Authentication ─────────────────────────────────────────
      socket.on('authenticate', ({ token }: { token: string }) => {
        try {
          const payload = jwt.verify(token, env.jwt.secret) as { userId: string };
          socket.userId = payload.userId;
          socket.join(`user:${payload.userId}`);
          socket.emit('authenticated', { userId: payload.userId });
          logger.debug('Socket authenticated', { userId: payload.userId });
        } catch {
          socket.emit('auth_error', { message: 'Invalid token' });
          socket.disconnect();
        }
      });

      // ── 2. Device registration ────────────────────────────────────
      socket.on('device_register', async ({ deviceId }: { deviceId: string }) => {
        if (!socket.userId) { socket.emit('error', { message: 'Not authenticated' }); return; }

        const rows = await query<{ user_id: string }>('SELECT user_id FROM devices WHERE id = $1', [deviceId]);
        if (rows[0] && rows[0].user_id !== socket.userId) {
          socket.emit('error', { message: 'Device ownership mismatch' });
          return;
        }

        socket.deviceId = deviceId;
        socket.join(`device:${deviceId}`);
        this.deviceMap.set(deviceId, socket.id);
        if (rows[0]) {
          await query(`UPDATE devices SET status='online', last_seen_at=NOW() WHERE id=$1`, [deviceId]);
        }
        socket.emit('device_registered', { deviceId });
        logger.info('Device online', { deviceId, userId: socket.userId });
      });

      // ── 2.1 Command ack (device received command envelope) ───────
      socket.on('command_ack', ({ commandId, accepted }: { commandId: string; accepted: boolean }) => {
        if (socket.userId) {
          this.io.to(`user:${socket.userId}`).emit('command_ack', {
            commandId,
            accepted,
            deviceId: socket.deviceId,
          });
        }
      });

      // ── 3. Command result (device → server) ───────────────────────
      socket.on('command_result', async ({ commandId, status, error }: { commandId: string; status: string; error?: string }) => {
        await query(`UPDATE commands SET status=$1, error_message=$2 WHERE id=$3`, [status, error || null, commandId]);
        // Forward status to web dashboard
        if (socket.userId) {
          this.io.to(`user:${socket.userId}`).emit('command_status', {
            commandId,
            status,
            error: error || null,
            deviceId: socket.deviceId,
          });
        }
      });

      // ── 4. Heartbeat ───────────────────────────────────────────────
      socket.on('heartbeat', () => {
        if (socket.deviceId) {
          query(`UPDATE devices SET last_seen_at=NOW() WHERE id=$1`, [socket.deviceId]).catch(() => {});
        }
        socket.emit('heartbeat_ack');
      });

      // ── 5. Disconnect ──────────────────────────────────────────────
      socket.on('disconnect', async () => {
        if (socket.deviceId) {
          this.deviceMap.delete(socket.deviceId);
          await query(`UPDATE devices SET status='offline' WHERE id=$1`, [socket.deviceId]);
          logger.info('Device offline', { deviceId: socket.deviceId });
        }
      });
    });

    ioInstance = this;
    logger.info('WebSocket server initialised');
  }

  /** Push an event to a specific device by its deviceId. Returns true if delivered. */
  sendToDevice(deviceId: string, event: string, data: unknown): boolean {
    const socketId = this.deviceMap.get(deviceId);
    if (!socketId) return false;
    this.io.to(socketId).emit(event, data);
    return true;
  }

  /** Broadcast to all sockets belonging to a user. */
  sendToUser(userId: string, event: string, data: unknown): void {
    this.io.to(`user:${userId}`).emit(event, data);
  }

  getOnlineDeviceCount(): number {
    return this.deviceMap.size;
  }
}

export function getSocketServer(): VoiceOSSocketServer | null {
  return ioInstance;
}

export function initSocketServer(httpServer: HttpServer): VoiceOSSocketServer {
  return new VoiceOSSocketServer(httpServer);
}
