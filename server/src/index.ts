import http from 'http';
import { env } from './config/env';
import { connectDatabase } from './config/database';
import { connectRedis } from './config/redis';
import { initSocketServer } from './websocket/socketServer';
import { logger } from './utils/logger';
import { createApp, logBoot } from './app';

const app = createApp();
const server = http.createServer(app);

async function listenOnAvailablePort(preferredPort: number, retries = 5): Promise<number> {
  return new Promise((resolve, reject) => {
    const tryListen = (port: number, remainingRetries: number) => {
      const onError = (error: NodeJS.ErrnoException) => {
        server.off('listening', onListening);

        if (error.code === 'EADDRINUSE' && env.isDev && remainingRetries > 0) {
          const nextPort = port + 1;
          logger.warn(`Port ${port} is in use. Retrying on port ${nextPort}...`);
          tryListen(nextPort, remainingRetries - 1);
          return;
        }

        reject(error);
      };

      const onListening = () => {
        server.off('error', onError);
        resolve(port);
      };

      server.once('error', onError);
      server.once('listening', onListening);
      server.listen(port);
    };

    tryListen(preferredPort, retries);
  });
}

// ── Server Startup ──────────────────────────────────────────────────────────

export async function startServer(): Promise<void> {
  try {
    // 1. Connect Redis
    await connectRedis();

    // 2. Connect PostgreSQL
    await connectDatabase();

    // 3. Initialize WebSockets
    initSocketServer(server);
    logBoot(env.nodeEnv);

    // 4. Start HTTP Server
    const listeningPort = await listenOnAvailablePort(env.port);
    logger.info(`VoiceOS Server running on port ${listeningPort} in ${env.nodeEnv} mode`);

  } catch (error) {
    logger.error('Failed to start server', { error: (error as Error).message });
    process.exit(1);
  }
}

// Handle graceful shutdown
const shutdown = () => {
  logger.info('Shutting down server gracefully...');
  server.close(() => {
    logger.info('HTTP server closed');
    process.exit(0);
  });
  // Force close after 10s
  setTimeout(() => process.exit(1), 10000).unref();
};

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);

// Start
if (require.main === module) {
  startServer();
}
