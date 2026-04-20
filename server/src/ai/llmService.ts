import { env } from '../config/env';
import OpenAI from 'openai';
import { logger } from '../utils/logger';
import { buildCommandSystemPrompt } from './promptBuilder';
import { normalizeStructuredCommand, StructuredCommand } from '../models/structuredCommand';

export interface ParseContext {
  lastApp?: string;
  lastContact?: string;
}

// ── OpenAI client (lazy init so tests can mock) ────────────────────────────

let openaiClient: OpenAI | null = null;

function getOpenAI(): OpenAI {
  if (!openaiClient) {
    openaiClient = new OpenAI({ apiKey: env.openai.apiKey });
  }
  return openaiClient;
}

// ── Core function ──────────────────────────────────────────────────────────

/**
 * parseCommandWithLLM — Sends the user's natural-language command to
 * GPT-4o-mini and receives a structured JSON action plan.
 *
 * Example:
 *   in:  "Send hello to Riya on WhatsApp and scroll down"
 *   out: { intent: "MULTI_STEP", steps: [...], confidence: 0.97 }
 *
 * Falls back to a rule-based parse if the API call fails.
 */
export async function parseCommandWithLLM(
  input: string,
  context?: ParseContext
): Promise<StructuredCommand> {
  const start = Date.now();
  logger.debug('LLM parse start', { input: input.slice(0, 100) });

  try {
    const openai = getOpenAI();

    const response = await openai.chat.completions.create({
      model: env.openai.model,
      max_tokens: env.openai.maxTokens,
      temperature: env.openai.temperature,
      response_format: { type: 'json_object' },
      messages: [
        { role: 'system', content: buildCommandSystemPrompt(context) },
        { role: 'user',   content: input },
      ],
    });

    const raw = response.choices[0]?.message?.content ?? '{}';
    const parsed = normalizeStructuredCommand(JSON.parse(raw) as Partial<StructuredCommand>, input);

    logger.info('LLM parse success', {
      input: input.slice(0, 60),
      intent: parsed.intent,
      steps: parsed.steps?.length,
      latency: Date.now() - start,
    });

    return parsed;

  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    logger.error('LLM parse failed', { error: msg, input: input.slice(0, 80) });

    // Fallback: return a basic unknown command so the caller can handle it
    return normalizeStructuredCommand(null, input);
  }
}

// ── Simple classification (no API cost) ───────────────────────────────────

const SIMPLE_PATTERNS: Array<{ regex: RegExp; build: (m: RegExpMatchArray) => StructuredCommand }> = [
  {
    regex: /^(?:click|tap|press)\s+(\d+)$/i,
    build: (m) => ({
      intent: 'CLICK', confidence: 1, raw: m[0],
      steps: [{ action: 'CLICK', index: parseInt(m[1], 10) }],
    }),
  },
  {
    regex: /^scroll\s+(down|up)$/i,
    build: (m) => ({
      intent: 'SCROLL', confidence: 1, raw: m[0],
      steps: [{ action: `SCROLL_${m[1].toUpperCase()}` as string, direction: m[1].toLowerCase() as 'up' | 'down' }],
    }),
  },
  {
    regex: /^(?:go\s+)?back$/i,
    build: (m) => ({ intent: 'GO_BACK', confidence: 1, raw: m[0], steps: [{ action: 'GO_BACK' }] }),
  },
  {
    regex: /^(?:open|launch)\s+(.+)$/i,
    build: (m) => ({ intent: 'OPEN_APP', confidence: 1, raw: m[0], steps: [{ action: 'OPEN_APP', app: m[1].trim() }] }),
  },
];

/**
 * Quick rule-based classification for simple commands.
 * Returns null if the input needs LLM-level parsing.
 * Saves API cost for high-frequency simple commands.
 */
export function quickClassify(input: string): StructuredCommand | null {
  const trimmed = input.trim().toLowerCase();
  for (const { regex, build } of SIMPLE_PATTERNS) {
    const m = trimmed.match(regex);
    if (m) {
      const parsed = build(m);
      return normalizeStructuredCommand(parsed, input);
    }
  }
  return null;
}
