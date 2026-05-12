import OpenAI from 'openai';
import fs from 'fs';
import { env } from '../config/env';
import { logger } from '../utils/logger';

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

/**
 * transcribeAudio — Sends an audio file to Groq (primary) or OpenAI (fallback) Whisper.
 */
export async function transcribeAudio(
  filePath: string,
  language = 'en'
): Promise<string> {
  logger.debug('Whisper: transcribing', { filePath, language });
  const start = Date.now();

  try {
    // Try Groq first for extreme speed and cost efficiency
    if (env.groq.apiKey) {
      try {
        const groq = getGroq();
        const transcription = await groq.audio.transcriptions.create({
          model: env.groq.whisperModel,
          file: fs.createReadStream(filePath),
          language,
          response_format: 'text',
        });
        const text = (transcription as unknown as string).trim();
        logger.info('Whisper: Groq success', { text: text.slice(0, 80), latency: Date.now() - start });
        return text;
      } catch (groqErr: any) {
        logger.warn('Whisper: Groq failed, falling back to OpenAI', { error: groqErr.message });
      }
    }

    // Fallback to OpenAI
    const openai = getOpenAI();
    const transcription = await openai.audio.transcriptions.create({
      model: env.openai.whisperModel,
      file: fs.createReadStream(filePath),
      language,
      response_format: 'text',
    });

    const text = (transcription as unknown as string).trim();
    logger.info('Whisper: OpenAI success', { text: text.slice(0, 80), latency: Date.now() - start });
    return text;

  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    logger.error('Whisper: transcription failed', { error: msg });
    throw new Error(`Audio transcription failed: ${msg}`);

  } finally {
    // Always clean up the temp file
    try { fs.unlinkSync(filePath); } catch { /* already deleted */ }
  }
}

/**
 * validateAudioFile — Basic sanity checks before sending to Whisper.
 * Whisper accepts: flac, m4a, mp3, mp4, mpeg, mpga, oga, ogg, wav, webm
 */
export function validateAudioFile(mimetype: string, sizeBytes: number): void {
  const ALLOWED_TYPES = [
    'audio/webm', 'audio/mp4', 'audio/mpeg', 'audio/wav', 'audio/ogg',
    'audio/flac', 'audio/x-m4a', 'video/webm', // browser MediaRecorder output
  ];
  const MAX_SIZE = 25 * 1024 * 1024; // Whisper 25 MB limit

  if (!ALLOWED_TYPES.includes(mimetype)) {
    throw new Error(`Unsupported audio type: ${mimetype}`);
  }
  if (sizeBytes > MAX_SIZE) {
    throw new Error(`Audio file too large: ${(sizeBytes / 1_048_576).toFixed(1)} MB (max 25 MB)`);
  }
}
