import { z } from 'zod';

export const CommandStepSchema = z.object({
  action: z.string(),
  app: z.string().optional(),
  target: z.string().optional(),
  message: z.string().optional(),
  direction: z.enum(['up', 'down']).optional(),
  index: z.number().int().min(0).optional(),
  text: z.string().optional(),
  url: z.string().url().optional(),
  delayMs: z.number().int().min(0).optional(),
  retries: z.number().int().min(0).optional(),
  metadata: z.record(z.unknown()).optional(),
});

export const StructuredCommandSchema = z.object({
  intent: z.string(),
  confidence: z.number().min(0).max(1),
  steps: z.array(CommandStepSchema),
});

export type SupportedIntent =
  | 'CLICK'
  | 'SCROLL'
  | 'GO_BACK'
  | 'OPEN_APP'
  | 'SEND_MESSAGE'
  | 'TYPE_TEXT'
  | 'RUN_MACRO'
  | 'MULTI_STEP'
  | 'UNKNOWN';

export type SupportedAction =
  | 'OPEN_APP'
  | 'CLICK'
  | 'SCROLL_DOWN'
  | 'SCROLL_UP'
  | 'GO_BACK'
  | 'SEND_MESSAGE'
  | 'TYPE_TEXT'
  | 'RUN_MACRO';

export type CommandStep = z.infer<typeof CommandStepSchema>;
export type StructuredCommand = z.infer<typeof StructuredCommandSchema> & { raw: string };

export function normalizeStructuredCommand(
  input: any,
  raw: string
): StructuredCommand {
  try {
    const parsed = StructuredCommandSchema.parse(input);
    return { ...parsed, raw };
  } catch (err) {
    // Fallback if schema fails but we have some partial data
    return {
      intent: typeof input?.intent === 'string' ? input.intent : 'UNKNOWN',
      confidence: typeof input?.confidence === 'number' ? input.confidence : 0,
      steps: Array.isArray(input?.steps) ? input.steps.filter((s: any) => s && s.action) : [],
      raw,
    };
  }
}
