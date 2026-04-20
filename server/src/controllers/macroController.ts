import { Request, Response, NextFunction } from 'express';
import { query } from '../config/database';
import { logger } from '../utils/logger';
import { v4 as uuidv4 } from 'uuid';
import { automationEngine } from '../automation/AutomationEngine';
import { macroService } from '../services/macroService';
import { CommandStep } from '../models/structuredCommand';

interface MacroRow {
  id: string; user_id: string; name: string;
  description: string; steps: CommandStep[]; delay_ms: number; is_active: boolean;
  created_at: string; updated_at: string;
}

export const macroController = {

  /** GET /api/v1/macros — list all macros for the user */
  async list(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const rows = await macroService.listByUser(req.user!.userId);
      res.json({ success: true, macros: rows });
    } catch (err) { next(err); }
  },

  /** POST /api/v1/macros — create a new macro */
  async create(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { name, description, steps, delayMs = 1500 } = req.body;
      const userId = req.user!.userId;

      const rows = await query<MacroRow>(
        `INSERT INTO macros (id, user_id, name, description, steps, delay_ms)
         VALUES ($1,$2,$3,$4,$5,$6)
         RETURNING *`,
        [uuidv4(), userId, name.toLowerCase().trim(), description || '', JSON.stringify(steps), delayMs]
      );
      logger.info('Macro created', { userId, name });
      res.status(201).json({ success: true, macro: rows[0] });
    } catch (err: unknown) {
      if ((err as { code?: string }).code === '23505') {
        res.status(409).json({ success: false, message: 'A macro with this name already exists' });
        return;
      }
      next(err);
    }
  },

  /** PUT /api/v1/macros/:id — update a macro */
  async update(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { name, description, steps, delayMs, isActive } = req.body;
      const rows = await query<MacroRow>(
        `UPDATE macros SET
           name = COALESCE($1, name),
           description = COALESCE($2, description),
           steps = COALESCE($3::jsonb, steps),
           delay_ms = COALESCE($4, delay_ms),
           is_active = COALESCE($5, is_active)
         WHERE id = $6 AND user_id = $7
         RETURNING *`,
        [
          name?.toLowerCase().trim() ?? null,
          description ?? null,
          steps ? JSON.stringify(steps) : null,
          delayMs ?? null,
          isActive ?? null,
          req.params.id,
          req.user!.userId,
        ]
      );
      if (!rows[0]) { res.status(404).json({ success: false, message: 'Macro not found' }); return; }
      res.json({ success: true, macro: rows[0] });
    } catch (err) { next(err); }
  },

  /** DELETE /api/v1/macros/:id */
  async remove(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      await query('DELETE FROM macros WHERE id = $1 AND user_id = $2', [req.params.id, req.user!.userId]);
      res.json({ success: true, message: 'Macro deleted' });
    } catch (err) { next(err); }
  },

  /** POST /api/v1/macros/:id/execute */
  async execute(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { deviceId, maxRetries = 1 } = req.body;
      const data = await automationEngine.executeMacroForUser(
        req.user!.userId,
        req.params.id,
        deviceId,
        Number(maxRetries)
      );

      res.json({
        success: true,
        data,
      });
    } catch (err) {
      next(err);
    }
  },
};
