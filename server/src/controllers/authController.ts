import { Request, Response, NextFunction } from 'express';
import bcrypt from 'bcryptjs';
import { query } from '../config/database';
import { signToken } from '../middleware/auth';
import { logger } from '../utils/logger';

interface UserRow {
  id: string; email: string; password_hash: string;
  name: string; plan: string; is_verified: boolean; is_admin: boolean;
}

export const authController = {

  /** POST /api/v1/auth/register */
  async register(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { email, password, name } = req.body;
      const existing = await query<UserRow>('SELECT id FROM users WHERE email = $1', [email]);
      if (existing.length > 0) {
        res.status(409).json({ success: false, message: 'Email already registered' });
        return;
      }
      const hash = await bcrypt.hash(password, 12);
      const rows = await query<UserRow>(
        `INSERT INTO users (email, password_hash, name)
         VALUES ($1, $2, $3) RETURNING id, email, name, plan, is_verified, is_admin`,
        [email.toLowerCase().trim(), hash, name?.trim() ?? '']
      );
      const user = rows[0];
      const token = signToken({ userId: user.id, email: user.email, plan: user.plan, isAdmin: user.is_admin });
      logger.info('User registered', { userId: user.id, email: user.email });
      res.status(201).json({ success: true, token, user: { id: user.id, email: user.email, name, plan: user.plan, isAdmin: user.is_admin } });
    } catch (err) { next(err); }
  },

  /** POST /api/v1/auth/login */
  async login(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { email, password } = req.body;
      const rows = await query<UserRow>(
        'SELECT id, email, password_hash, name, plan, is_admin FROM users WHERE email = $1',
        [email.toLowerCase().trim()]
      );
      const user = rows[0];
      if (!user || !(await bcrypt.compare(password, user.password_hash))) {
        res.status(401).json({ success: false, message: 'Invalid credentials' });
        return;
      }
      const token = signToken({ userId: user.id, email: user.email, plan: user.plan, isAdmin: user.is_admin });
      logger.info('User logged in', { userId: user.id });
      res.json({ success: true, token, user: { id: user.id, email: user.email, name: user.name, plan: user.plan, isAdmin: user.is_admin } });
    } catch (err) { next(err); }
  },

  /** GET /api/v1/auth/me */
  async me(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const rows = await query<UserRow>(
        'SELECT id, email, name, plan, is_verified, is_admin, created_at FROM users WHERE id = $1',
        [req.user!.userId]
      );
      if (!rows[0]) { res.status(404).json({ success: false, message: 'User not found' }); return; }
      res.json({ success: true, user: rows[0] });
    } catch (err) { next(err); }
  },
};
