import { NextFunction, Request, Response } from 'express';
import { analyticsService } from '../services/analyticsService';

export const analyticsController = {
  async summary(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const data = await analyticsService.getUserSummary(req.user!.userId);
      res.json({ success: true, data });
    } catch (err) {
      next(err);
    }
  },
};
