import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { env } from '../config/env';
import { logger } from '../utils/logger';

export interface JwtPayload {
  userId: string;
  email: string;
  plan: string;
  isAdmin?: boolean;
}

declare global {
  namespace Express {
    interface Request {
      user?: JwtPayload;
    }
  }
}

/** Verify JWT and attach decoded payload to req.user. */
export function authenticate(req: Request, res: Response, next: NextFunction): void {
  const useDevBypass = env.isDev && env.auth.devBypass;

  try {
    const header = req.headers.authorization;
    if (!header?.startsWith('Bearer ')) {
      if (useDevBypass) {
        req.user = {
          userId: env.auth.bypassUserId,
          email: env.auth.bypassEmail,
          plan: env.auth.bypassPlan,
          isAdmin: env.auth.bypassIsAdmin,
        };
        next();
        return;
      }

      res.status(401).json({ success: false, message: 'No token provided' });
      return;
    }

    const token = header.slice(7);
    const payload = jwt.verify(token, env.jwt.secret) as JwtPayload;
    req.user = payload;
    next();

  } catch (err) {
    if (useDevBypass) {
      req.user = {
        userId: env.auth.bypassUserId,
        email: env.auth.bypassEmail,
        plan: env.auth.bypassPlan,
        isAdmin: env.auth.bypassIsAdmin,
      };
      next();
      return;
    }

    logger.warn('Auth failure', { url: req.url, error: (err as Error).message });
    res.status(401).json({ success: false, message: 'Invalid or expired token' });
  }
}

/** Require the user to be on a specific plan or higher. */
export function requirePlan(...plans: string[]) {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (!req.user || !plans.includes(req.user.plan)) {
      res.status(403).json({
        success: false,
        message: `This feature requires one of: ${plans.join(', ')} plan`,
      });
      return;
    }
    next();
  };
}

/** Admin-only guard. */
export function requireAdmin(req: Request, res: Response, next: NextFunction): void {
  if (req.user?.isAdmin !== true) {
    res.status(403).json({ success: false, message: 'Admin access required' });
    return;
  }
  next();
}

/** Sign a JWT access token. */
export function signToken(payload: JwtPayload): string {
  return jwt.sign(payload, env.jwt.secret, { expiresIn: env.jwt.expiresIn } as jwt.SignOptions);
}
