import { getSocketServer } from '../websocket/socketServer';
import { CommandStep, StructuredCommand } from '../models/structuredCommand';
import { logger } from '../utils/logger';

export interface MacroExecutionOptions {
  macroId: string;
  userId: string;
  deviceId: string;
  steps: CommandStep[];
  delayMs: number;
  maxRetries: number;
}

export interface MacroExecutionResult {
  success: boolean;
  runAt: string;
  deviceId: string;
  failedStepIndex?: number;
  errors: string[];
}

const wait = async (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms));

function toStructured(step: CommandStep): StructuredCommand {
  return {
    intent: step.action,
    confidence: 1,
    steps: [step],
    raw: `macro-step:${step.action}`,
  };
}

export class MacroExecutor {
  async execute(options: MacroExecutionOptions): Promise<MacroExecutionResult> {
    const socketServer = getSocketServer();
    if (!socketServer) {
      return {
        success: false,
        runAt: new Date().toISOString(),
        deviceId: options.deviceId,
        errors: ['WebSocket server is not available'],
      };
    }

    const errors: string[] = [];

    for (let index = 0; index < options.steps.length; index += 1) {
      const step = options.steps[index];
      let delivered = false;

      for (let attempt = 0; attempt <= options.maxRetries; attempt += 1) {
        const payload = {
          commandId: `macro:${options.macroId}:${index + 1}`,
          source: 'macro',
          structured: toStructured(step),
        };

        delivered = socketServer.sendToDevice(options.deviceId, 'execute_command', payload);
        if (delivered) break;

        const retryInfo = `step=${index + 1}, attempt=${attempt + 1}`;
        logger.warn('Macro step delivery failed', {
          macroId: options.macroId,
          userId: options.userId,
          deviceId: options.deviceId,
          retryInfo,
        });

        if (attempt < options.maxRetries) {
          await wait(500);
        }
      }

      if (!delivered) {
        errors.push(`Step ${index + 1} could not be delivered`);
        return {
          success: false,
          runAt: new Date().toISOString(),
          deviceId: options.deviceId,
          failedStepIndex: index,
          errors,
        };
      }

      if (index < options.steps.length - 1 && options.delayMs > 0) {
        await wait(options.delayMs);
      }
    }

    return {
      success: true,
      runAt: new Date().toISOString(),
      deviceId: options.deviceId,
      errors,
    };
  }
}
