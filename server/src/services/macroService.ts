import { query } from '../config/database';
import { CommandStep } from '../models/structuredCommand';

export interface MacroRecord {
  id: string;
  user_id: string;
  name: string;
  description: string;
  steps: CommandStep[];
  delay_ms: number;
  is_active: boolean;
  created_at: string;
  updated_at: string;
}

export const macroService = {
  async listByUser(userId: string): Promise<MacroRecord[]> {
    return query<MacroRecord>(
      `SELECT id, user_id, name, description, steps, delay_ms, is_active, created_at, updated_at
       FROM macros
       WHERE user_id = $1
       ORDER BY updated_at DESC`,
      [userId]
    );
  },

  async getByIdForUser(userId: string, macroId: string): Promise<MacroRecord | null> {
    const rows = await query<MacroRecord>(
      `SELECT id, user_id, name, description, steps, delay_ms, is_active, created_at, updated_at
       FROM macros
       WHERE id = $1 AND user_id = $2`,
      [macroId, userId]
    );

    return rows[0] ?? null;
  },
};
