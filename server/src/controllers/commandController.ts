import { Request, Response, NextFunction } from 'express';
import { query } from '../config/database';
import { cacheGet, cacheSet } from '../config/redis';
import { parseCommandWithLLM, quickClassify } from '../ai/llmService';
import { transcribeAudio, validateAudioFile } from '../ai/whisperService';
import { getSocketServer } from '../websocket/socketServer';
import { logger } from '../utils/logger';
import { v4 as uuidv4 } from 'uuid';
import { StructuredCommand } from '../models/structuredCommand';
import { recordDailyUsage } from '../services/commandUsageService';

interface CommandRow { id: string; input_text: string; output_json: StructuredCommand; status: string; created_at: string; }

export const commandController = {

  /** POST /api/v1/commands — text command */
  async processText(req: Request, res: Response, next: NextFunction): Promise<void> {
    const commandId = uuidv4();
    const start = Date.now();
    try {
      const { deviceId, context } = req.body;
      const inputText = String(req.body.input ?? req.body.command ?? '').trim();
      if (!inputText) {
        res.status(400).json({ success: false, message: 'input is required' });
        return;
      }

      const userId = req.user!.userId;
      const isAudio = Boolean(req.body.isAudio);

      // Try quick rule-based parse first (no API cost)
      let structured = quickClassify(inputText);
      let modelUsed = 'rule-based';

      if (!structured) {
        // Check cache
        const cacheKey = `cmd:${Buffer.from(inputText.toLowerCase()).toString('base64').slice(0, 40)}`;
        const cached = await cacheGet<StructuredCommand>(cacheKey);
        if (cached) {
          structured = cached;
          modelUsed = 'cache';
        } else {
          structured = await parseCommandWithLLM(inputText, context);
          modelUsed = 'llm';
          await cacheSet(cacheKey, structured, 3600); // 1 hour cache
        }
      }

      const latency = Date.now() - start;

      // Persist to DB
      await query(
        `INSERT INTO commands (id, user_id, device_id, input_text, output_json, status, model_used, latency_ms, is_audio)
         VALUES ($1,$2,$3,$4,$5,'success',$6,$7,$8)`,
        [commandId, userId, deviceId || null, inputText, JSON.stringify(structured), modelUsed, latency, isAudio]
      );

      await recordDailyUsage(userId);

      await query(
        `INSERT INTO analytics_events (user_id, event_type, payload)
         VALUES ($1, 'command_processed', $2)`,
        [
          userId,
          JSON.stringify({
            commandId,
            deviceId: deviceId || null,
            modelUsed,
            latency,
            isAudio,
            intent: structured.intent,
          }),
        ]
      ).catch(() => undefined);

      // Push to device via WebSocket if device is connected
      let delivered = false;
      if (deviceId) {
        delivered =
          getSocketServer()?.sendToDevice(deviceId, 'execute_command', {
            commandId,
            source: 'api',
            structured,
          }) ?? false;
      }

      res.json({ success: true, commandId, structured, latency, modelUsed, delivered });
    } catch (err) {
      await query(`UPDATE commands SET status='failed', error_message=$1 WHERE id=$2`,
        [(err as Error).message, commandId]).catch(() => {});
      next(err);
    }
  },

  /** POST /api/v1/commands/audio — audio command via Whisper */
  async processAudio(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const file = req.file;
      if (!file) { res.status(400).json({ success: false, message: 'Audio file required' }); return; }
      validateAudioFile(file.mimetype, file.size);

      const text = await transcribeAudio(file.path);
      req.body.input = text;
      req.body.isAudio = true;

      return commandController.processText(req, res, next);
    } catch (err) { next(err); }
  },

  /** GET /api/v1/commands/history */
  async history(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { page = 1, limit = 20 } = req.query;
      const safeLimit = Math.min(Number(limit), 100);
      const offset = (Number(page) - 1) * safeLimit;
      const rows = await query<CommandRow>(
        `SELECT id, input_text, output_json, status, model_used, latency_ms, created_at
         FROM commands WHERE user_id = $1
         ORDER BY created_at DESC LIMIT $2 OFFSET $3`,
        [req.user!.userId, safeLimit, offset]
      );
      const [{ count }] = await query<{ count: string }>(
        'SELECT COUNT(*) FROM commands WHERE user_id = $1', [req.user!.userId]
      );
      res.json({ success: true, data: rows, total: parseInt(count, 10), page: Number(page), limit: safeLimit });
    } catch (err) { next(err); }
  },
};
