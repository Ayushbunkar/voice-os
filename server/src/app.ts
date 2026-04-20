import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import compression from 'compression';
import { rateLimit } from 'express-rate-limit';

import { env } from './config/env';
import { logger, httpLogger } from './utils/logger';
import { errorHandler } from './middleware/errorHandler';
import { authenticate } from './middleware/auth';
import { commandRateLimit } from './middleware/rateLimiter';
import { validateRequest } from './middleware/validation';
import { commandProcessValidator, deviceConnectValidator } from './middleware/requestValidators';
import { commandController } from './controllers/commandController';
import { deviceController } from './controllers/deviceController';

import authRoutes from './routes/authRoutes';
import commandRoutes from './routes/commandRoutes';
import deviceRoutes from './routes/deviceRoutes';
import macroRoutes from './routes/macroRoutes';
import paymentRoutes from './routes/paymentRoutes';
import analyticsRoutes from './routes/analyticsRoutes';
import adminRoutes from './routes/adminRoutes';

export const apiPrefix = `/api/${env.apiVersion}`;

export function createApp(): express.Express {
  const app = express();

  app.use(helmet());

  const allowedOrigins = [env.frontendUrl, 'voiceos://app', 'http://localhost:3000'];
  app.use(cors({
    origin: (origin, callback) => {
      if (!origin || allowedOrigins.includes(origin)) {
        callback(null, true);
      } else {
        callback(new Error('Not allowed by CORS'));
      }
    },
    credentials: true,
  }));

  app.use(compression());

  const limiter = rateLimit({
    windowMs: env.rateLimit.windowMs,
    max: env.rateLimit.max,
    standardHeaders: true,
    legacyHeaders: false,
    message: { success: false, message: 'Too many requests, please try again later.' },
  });
  app.use(limiter);

  app.use(`${apiPrefix}/billing/webhook`, express.raw({ type: 'application/json' }));
  app.use(express.json({ limit: '5mb' }));
  app.use(express.urlencoded({ extended: true, limit: '5mb' }));

  app.use((req, res, next) => {
    const start = Date.now();
    res.on('finish', () => {
      httpLogger(req.method, req.originalUrl, res.statusCode, Date.now() - start);
    });
    next();
  });

  app.get('/health', (_req, res) => res.json({ status: 'ok', time: new Date() }));

  app.use(`${apiPrefix}/auth`, authRoutes);
  app.use(`${apiPrefix}/commands`, commandRoutes);
  app.use(`${apiPrefix}/devices`, deviceRoutes);
  app.use(`${apiPrefix}/macros`, macroRoutes);
  app.use(`${apiPrefix}/billing`, paymentRoutes);
  app.use(`${apiPrefix}/analytics`, analyticsRoutes);
  app.use(`${apiPrefix}/admin`, adminRoutes);

  app.post(`${apiPrefix}/command`, authenticate, commandRateLimit, commandProcessValidator, validateRequest, commandController.processText);
  app.post(`${apiPrefix}/device/connect`, authenticate, deviceConnectValidator, validateRequest, deviceController.connect);

  app.use(errorHandler);

  return app;
}

export function logBoot(mode: string): void {
  logger.info(`VoiceOS app middleware booted (${mode})`);
}
