import { Router } from 'express';
import { adminController } from '../controllers/adminController';
import { authenticate, requireAdmin } from '../middleware/auth';

const router = Router();
router.use(authenticate, requireAdmin);
router.get('/overview', adminController.overview);

export default router;
