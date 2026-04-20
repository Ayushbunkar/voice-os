import winston from 'winston';
import path from 'path';
import fs from 'fs';
import { env } from '../config/env';

// Ensure logs directory exists
const logsDir = path.dirname(env.log.file);
if (!fs.existsSync(logsDir)) fs.mkdirSync(logsDir, { recursive: true });

const { combine, timestamp, json, colorize, printf, errors } = winston.format;

const consoleFormat = printf(({ level, message, timestamp, stack, ...meta }) => {
  const metaStr = Object.keys(meta).length ? ` ${JSON.stringify(meta)}` : '';
  return `${timestamp} [${level}] ${stack || message}${metaStr}`;
});

export const logger = winston.createLogger({
  level: env.log.level,
  format: combine(errors({ stack: true }), timestamp(), json()),
  defaultMeta: { service: 'voiceos-api' },
  transports: [
    // Structured JSON to file
    new winston.transports.File({ filename: env.log.file }),
    new winston.transports.File({ filename: 'logs/error.log', level: 'error' }),
  ],
});

// Pretty console output in development
if (env.isDev) {
  logger.add(
    new winston.transports.Console({
      format: combine(colorize(), timestamp({ format: 'HH:mm:ss' }), consoleFormat),
    })
  );
}

// HTTP request logger middleware compatible string
export function httpLogger(method: string, url: string, status: number, ms: number): void {
  logger.info('HTTP', { method, url, status, ms });
}
