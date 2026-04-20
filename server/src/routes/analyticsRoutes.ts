import { Router } from 'express';
import { analyticsController } from '../controllers/analyticsController';
import { authenticate } from '../middleware/auth';

const router = Router();
router.use(authenticate);
router.get('/summary', analyticsController.summary);

export default router;
