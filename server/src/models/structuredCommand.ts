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

export interface CommandStep {
  action: SupportedAction | string;
  app?: string;
  target?: string;
  message?: string;
  direction?: 'up' | 'down';
  index?: number;
  text?: string;
  url?: string;
  delayMs?: number;
  retries?: number;
  metadata?: Record<string, unknown>;
}

export interface StructuredCommand {
  intent: SupportedIntent | string;
  confidence: number;
  steps: CommandStep[];
  raw: string;
}

function toConfidence(value: unknown): number {
  if (typeof value !== 'number' || Number.isNaN(value)) return 0;
  return Math.max(0, Math.min(1, value));
}

export function sanitizeCommandStep(step: Partial<CommandStep>): CommandStep | null {
  if (!step.action || typeof step.action !== 'string') return null;

  const clean: CommandStep = {
    action: step.action,
  };

  if (typeof step.app === 'string') clean.app = step.app.trim().toLowerCase();
  if (typeof step.target === 'string') clean.target = step.target.trim();
  if (typeof step.message === 'string') clean.message = step.message.trim();
  if (step.direction === 'up' || step.direction === 'down') clean.direction = step.direction;
  if (typeof step.index === 'number' && Number.isFinite(step.index)) clean.index = Math.max(0, Math.floor(step.index));
  if (typeof step.text === 'string') clean.text = step.text;
  if (typeof step.url === 'string') clean.url = step.url.trim();
  if (typeof step.delayMs === 'number' && Number.isFinite(step.delayMs)) clean.delayMs = Math.max(0, Math.floor(step.delayMs));
  if (typeof step.retries === 'number' && Number.isFinite(step.retries)) clean.retries = Math.max(0, Math.floor(step.retries));
  if (step.metadata && typeof step.metadata === 'object') clean.metadata = step.metadata;

  return clean;
}

export function normalizeStructuredCommand(
  input: Partial<StructuredCommand> | null | undefined,
  raw: string
): StructuredCommand {
  if (!input || typeof input !== 'object') {
    return { intent: 'UNKNOWN', confidence: 0, steps: [], raw };
  }

  const intent = typeof input.intent === 'string' && input.intent.trim() ? input.intent.trim() : 'UNKNOWN';
  const steps = Array.isArray(input.steps)
    ? input.steps
        .map((step) => sanitizeCommandStep(step as Partial<CommandStep>))
        .filter((step): step is CommandStep => step !== null)
    : [];

  return {
    intent,
    confidence: toConfidence(input.confidence),
    steps,
    raw,
  };
}
