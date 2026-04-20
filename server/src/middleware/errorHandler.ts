import { Request, Response, NextFunction } from 'express';
import { logger } from '../utils/logger';

export function errorHandler(err: any, req: Request, res: Response, next: NextFunction): void {
  // Check if it's a known HTTP error
  const statusCode = err.statusCode || err.status || 500;
  const message = err.message || 'Internal Server Error';

  // Only log if it's 500
  if (statusCode >= 500) {
    logger.error('Unhandled Exception', {
      error: err.message,
      stack: err.stack,
      url: req.originalUrl,
      method: req.method,
    });
  } else {
    logger.warn('Client Error', {
      error: err.message,
      url: req.originalUrl,
      method: req.method,
      statusCode,
    });
  }

  res.status(statusCode).json({
    success: false,
    message,
    ...(process.env.NODE_ENV === 'development' && { stack: err.stack }),
  });
}
