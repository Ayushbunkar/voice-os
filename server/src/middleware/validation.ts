import { NextFunction, Request, Response } from 'express';
import { validationResult } from 'express-validator';

export function validateRequest(req: Request, res: Response, next: NextFunction): void {
  const errors = validationResult(req);
  if (errors.isEmpty()) {
    next();
    return;
  }

  res.status(422).json({
    success: false,
    message: 'Validation failed',
    errors: errors.array().map((err) => ({
      field: err.type === 'field' ? err.path : 'request',
      message: err.msg,
    })),
  });
}
