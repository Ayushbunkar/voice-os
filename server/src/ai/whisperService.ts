import OpenAI from 'openai';
import fs from 'fs';
import { env } from '../config/env';
import { logger } from '../utils/logger';

let openaiClient: OpenAI | null = null;
function getOpenAI(): OpenAI {
  if (!openaiClient) openaiClient = new OpenAI({ apiKey: env.openai.apiKey });
  return openaiClient;
}

/**
 * transcribeAudio — Sends an audio file to OpenAI Whisper and returns the transcribed text.
 *
 * @param filePath  Absolute path to the temp audio file (webm / mp4 / wav / m4a).
 * @param language  Optional BCP-47 language code (e.g. "en", "hi").
 * @returns         Transcribed text string.
 */
export async function transcribeAudio(
  filePath: string,
  language = 'en'
): Promise<string> {
  logger.debug('Whisper: transcribing', { filePath, language });
  const start = Date.now();

  try {
    const openai = getOpenAI();

    const transcription = await openai.audio.transcriptions.create({
      model: env.openai.whisperModel,
      file: fs.createReadStream(filePath),
      language,
      response_format: 'text',
    });

    const text = (transcription as unknown as string).trim();
    logger.info('Whisper: transcribed', { text: text.slice(0, 80), latency: Date.now() - start });
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
