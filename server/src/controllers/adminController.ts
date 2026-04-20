import { NextFunction, Request, Response } from 'express';
import { analyticsService } from '../services/analyticsService';

export const adminController = {
  async overview(_req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const [overview, recentErrors] = await Promise.all([
        analyticsService.getAdminOverview(),
        analyticsService.getRecentErrors(25),
      ]);

      res.json({
        success: true,
        data: {
          overview,
          recentErrors,
        },
      });
    } catch (err) {
      next(err);
    }
  },
};
