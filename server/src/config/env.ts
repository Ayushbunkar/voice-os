import dotenv from 'dotenv';
import path from 'path';

dotenv.config({ path: path.resolve(__dirname, '../../.env') });

const required = [
  'JWT_SECRET',
  'DATABASE_URL',
  'REDIS_URL',
  'OPENAI_API_KEY',
];

required.forEach((key) => {
  if (!process.env[key]) {
    throw new Error(`Missing required environment variable: ${key}`);
  }
});

export const env = {
  nodeEnv:    process.env.NODE_ENV || 'development',
  port:       parseInt(process.env.PORT || '5000', 10),
  apiVersion: process.env.API_VERSION || 'v1',
  auth: {
    devBypass: process.env.AUTH_DEV_BYPASS === 'true',
    bypassUserId: process.env.AUTH_DEV_BYPASS_USER_ID || 'dev-user',
    bypassEmail: process.env.AUTH_DEV_BYPASS_EMAIL || 'dev@voiceos.local',
    bypassPlan: process.env.AUTH_DEV_BYPASS_PLAN || 'enterprise',
    bypassIsAdmin: process.env.AUTH_DEV_BYPASS_IS_ADMIN === 'true',
  },

  jwt: {
    secret:         process.env.JWT_SECRET as string,
    expiresIn:      process.env.JWT_EXPIRES_IN || '7d',
    refreshExpires: process.env.JWT_REFRESH_EXPIRES_IN || '30d',
  },

  db: {
    url:      process.env.DATABASE_URL as string,
    ssl:      process.env.DB_SSL === 'true',
  },

  redis: {
    url:      process.env.REDIS_URL as string,
    password: process.env.REDIS_PASSWORD,
  },

  openai: {
    apiKey:      process.env.OPENAI_API_KEY as string,
    model:       process.env.OPENAI_MODEL || 'gpt-4o-mini',
    whisperModel:process.env.OPENAI_WHISPER_MODEL || 'whisper-1',
    maxTokens:   parseInt(process.env.OPENAI_MAX_TOKENS || '500', 10),
    temperature: parseFloat(process.env.OPENAI_TEMPERATURE || '0.2'),
  },

  stripe: {
    secretKey:      process.env.STRIPE_SECRET_KEY || '',
    webhookSecret:  process.env.STRIPE_WEBHOOK_SECRET || '',
    proPriceId:     process.env.STRIPE_PRICE_PRO_MONTHLY || '',
    enterprisePriceId: process.env.STRIPE_PRICE_ENTERPRISE_MONTHLY || '',
  },

  razorpay: {
    keyId:     process.env.RAZORPAY_KEY_ID || '',
    keySecret: process.env.RAZORPAY_KEY_SECRET || '',
    webhookSecret: process.env.RAZORPAY_WEBHOOK_SECRET || '',
    proPlanId: process.env.RAZORPAY_PLAN_PRO_MONTHLY || '',
    enterprisePlanId: process.env.RAZORPAY_PLAN_ENTERPRISE_MONTHLY || '',
  },

  frontendUrl:         process.env.FRONTEND_URL || 'http://localhost:3000',
  appDownloadUrl:      process.env.ANDROID_APP_DOWNLOAD_URL || '',

  smtp: {
    host: process.env.SMTP_HOST || '',
    port: parseInt(process.env.SMTP_PORT || '587', 10),
    user: process.env.SMTP_USER || '',
    pass: process.env.SMTP_PASS || '',
    from: process.env.EMAIL_FROM || 'noreply@voiceos.app',
  },

  rateLimit: {
    windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '900000', 10),
    max:      parseInt(process.env.RATE_LIMIT_MAX || '100', 10),
    aiMax:    parseInt(process.env.AI_RATE_LIMIT_MAX || '20', 10),
  },

  plans: {
    freeCommandsPerDay:       parseInt(process.env.FREE_COMMANDS_PER_DAY || '50', 10),
    proCommandsPerDay:        parseInt(process.env.PRO_COMMANDS_PER_DAY || '1000', 10),
    enterpriseCommandsPerDay: parseInt(process.env.ENTERPRISE_COMMANDS_PER_DAY || '999999', 10),
  },

  log: {
    level: process.env.LOG_LEVEL || 'info',
    file:  process.env.LOG_FILE || 'logs/app.log',
  },

  isProd: process.env.NODE_ENV === 'production',
  isDev:  process.env.NODE_ENV === 'development',
} as const;
