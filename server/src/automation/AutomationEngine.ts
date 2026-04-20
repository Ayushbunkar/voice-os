import { query } from '../config/database';
import { CommandStep } from '../models/structuredCommand';
import { MacroExecutor } from './MacroExecutor';

interface MacroRow {
  id: string;
  user_id: string;
  name: string;
  steps: CommandStep[];
  delay_ms: number;
  is_active: boolean;
}

interface DeviceRow {
  id: string;
  status: string;
}

export class AutomationEngine {
  private executor = new MacroExecutor();

  async executeMacroForUser(
    userId: string,
    macroId: string,
    preferredDeviceId?: string,
    maxRetries = 1
  ): Promise<{
    macroId: string;
    macroName: string;
    deviceId: string;
    success: boolean;
    errors: string[];
    runAt: string;
  }> {
    const macroRows = await query<MacroRow>(
      `SELECT id, user_id, name, steps, delay_ms, is_active
       FROM macros
       WHERE id = $1 AND user_id = $2`,
      [macroId, userId]
    );

    const macro = macroRows[0];
    if (!macro) {
      throw new Error('Macro not found');
    }

    if (!macro.is_active) {
      throw new Error('Macro is disabled');
    }

    const device = await this.resolveDevice(userId, preferredDeviceId);

    const steps = Array.isArray(macro.steps) ? macro.steps : [];
    if (steps.length === 0) {
      throw new Error('Macro has no executable steps');
    }

    const result = await this.executor.execute({
      macroId: macro.id,
      userId,
      deviceId: device.id,
      steps,
      delayMs: macro.delay_ms,
      maxRetries,
    });

    await query(
      `INSERT INTO macro_runs (user_id, macro_id, device_id, status, failed_step_index, error_log, executed_at)
       VALUES ($1, $2, $3, $4, $5, $6, NOW())`,
      [
        userId,
        macro.id,
        device.id,
        result.success ? 'success' : 'failed',
        result.failedStepIndex ?? null,
        result.errors.length > 0 ? JSON.stringify(result.errors) : null,
      ]
    );

    return {
      macroId: macro.id,
      macroName: macro.name,
      deviceId: device.id,
      success: result.success,
      errors: result.errors,
      runAt: result.runAt,
    };
  }

  private async resolveDevice(userId: string, preferredDeviceId?: string): Promise<DeviceRow> {
    if (preferredDeviceId) {
      const rows = await query<DeviceRow>(
        `SELECT id, status
         FROM devices
         WHERE id = $1 AND user_id = $2`,
        [preferredDeviceId, userId]
      );

      if (!rows[0]) {
        throw new Error('Device not found for this user');
      }

      if (rows[0].status !== 'online') {
        throw new Error('Selected device is offline');
      }

      return rows[0];
    }

    const rows = await query<DeviceRow>(
      `SELECT id, status
       FROM devices
       WHERE user_id = $1 AND status = 'online'
       ORDER BY last_seen_at DESC
       LIMIT 1`,
      [userId]
    );

    if (!rows[0]) {
      throw new Error('No online device available to execute macro');
    }

    return rows[0];
  }
}

export const automationEngine = new AutomationEngine();
