import { env } from '../config/env';
import OpenAI from 'openai';
import { logger } from '../utils/logger';
import { buildCommandSystemPrompt } from './promptBuilder';
import { normalizeStructuredCommand, StructuredCommand } from '../models/structuredCommand';
import { LRUCache } from 'lru-cache';

export interface ParseContext {
  lastApp?: string;
  lastContact?: string;
}

// ── Local Cache (Token & Credit Saver) ───────────────────────────────────

const commandCache = new LRUCache<string, StructuredCommand>({
  max: 100, // Cache last 100 unique commands
  ttl: 1000 * 60 * 60, // 1 hour TTL
});

// ── OpenAI client (lazy init so tests can mock) ────────────────────────────

let openaiClient: OpenAI | null = null;
let groqClient: OpenAI | null = null;

function getOpenAI(): OpenAI {
  if (!openaiClient) openaiClient = new OpenAI({ apiKey: env.openai.apiKey });
  return openaiClient;
}

function getGroq(): OpenAI {
  if (!groqClient) {
    groqClient = new OpenAI({
      apiKey: env.groq.apiKey,
      baseURL: 'https://api.groq.com/openai/v1',
    });
  }
  return groqClient;
}

// ── Core function ──────────────────────────────────────────────────────────

/**
 * parseCommandWithLLM — Sends the user's natural-language command to
 * Groq (Llama-3) or OpenAI and receives a structured JSON action plan.
 */
export async function parseCommandWithLLM(
  input: string,
  context?: ParseContext,
  retryCount = 1
): Promise<StructuredCommand> {
  const cacheKey = `${input.toLowerCase()}|${context?.lastApp || ''}`;
  const cached = commandCache.get(cacheKey);
  if (cached) {
    logger.debug('LLM parse: Cache hit', { input });
    return { ...cached, raw: input }; // Update raw to match current input case
  }

  const start = Date.now();
  logger.debug('LLM parse start', { input: input.slice(0, 100) });

  try {
    // Try Groq first for extreme speed
    if (env.groq.apiKey) {
      try {
        const groq = getGroq();
        const response = await groq.chat.completions.create({
          model: env.groq.model,
          max_tokens: env.openai.maxTokens,
          temperature: 0.1,
          response_format: { type: 'json_object' },
          messages: [
            { role: 'system', content: buildCommandSystemPrompt(context) + "\nReturn ONLY JSON." },
            { role: 'user',   content: input },
          ],
        });

        const raw = response.choices[0]?.message?.content ?? '{}';
        const parsed = normalizeStructuredCommand(JSON.parse(raw), input);
        
        if (parsed.intent !== 'UNKNOWN') {
          commandCache.set(cacheKey, parsed);
        }
        
        logger.info('LLM parse: Groq success', { intent: parsed.intent, latency: Date.now() - start });
        return parsed;
      } catch (groqErr: any) {
        logger.warn('LLM parse: Groq failed, falling back', { error: groqErr.message });
      }
    }

    // Fallback to OpenAI
    const openai = getOpenAI();
    const response = await openai.chat.completions.create({
      model: env.openai.model,
      max_tokens: env.openai.maxTokens,
      temperature: 0.1,
      response_format: { type: 'json_object' },
      messages: [
        { role: 'system', content: buildCommandSystemPrompt(context) },
        { role: 'user',   content: input },
      ],
    });

    const raw = response.choices[0]?.message?.content ?? '{}';
    const parsed = normalizeStructuredCommand(JSON.parse(raw), input);
    
    if (parsed.intent !== 'UNKNOWN') {
      commandCache.set(cacheKey, parsed);
    }
    
    logger.info('LLM parse: OpenAI success', { intent: parsed.intent, latency: Date.now() - start });
    return parsed;

  } catch (err: unknown) {
    if (retryCount > 0) {
      logger.warn('LLM parse: retrying...', { input });
      return parseCommandWithLLM(input, context, retryCount - 1);
    }
    const msg = err instanceof Error ? err.message : String(err);
    logger.error('LLM parse failed', { error: msg, input: input.slice(0, 80) });
    return normalizeStructuredCommand(null, input);
  }
}

// ── Simple classification (no API cost) ───────────────────────────────────

const SIMPLE_PATTERNS: Array<{ regex: RegExp; build: (m: RegExpMatchArray) => Partial<StructuredCommand> }> = [
  {
    regex: /^(?:click|tap|press)\s+(\d+)$/i,
    build: (m) => ({
      intent: 'CLICK', confidence: 1,
      steps: [{ action: 'CLICK', index: parseInt(m[1], 10) }],
    }),
  },
  {
    regex: /^scroll\s+(down|up)$/i,
    build: (m) => ({
      intent: 'SCROLL', confidence: 1,
      steps: [{ action: m[1].toUpperCase() === 'DOWN' ? 'SCROLL_DOWN' : 'SCROLL_UP', direction: m[1].toLowerCase() as 'up' | 'down' }],
    }),
  },
  {
    regex: /^(?:go\s+)?back$/i,
    build: (m) => ({ intent: 'GO_BACK', confidence: 1, steps: [{ action: 'GO_BACK' }] }),
  },
  {
    regex: /^(?:open|launch)\s+(.+)$/i,
    build: (m) => ({ intent: 'OPEN_APP', confidence: 1, steps: [{ action: 'OPEN_APP', app: m[1].trim().toLowerCase() }] }),
  },
];

/**
 * Quick rule-based classification for simple commands.
 * Returns null if the input needs LLM-level parsing.
 * Saves API cost for high-frequency simple commands.
 */
export function quickClassify(input: string): StructuredCommand | null {
  const trimmed = input.trim();
  for (const { regex, build } of SIMPLE_PATTERNS) {
    const m = trimmed.match(regex);
    if (m) {
      return normalizeStructuredCommand(build(m), input);
    }
  }
  return null;
}
