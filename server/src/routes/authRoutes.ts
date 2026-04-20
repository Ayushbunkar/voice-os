import { Router } from 'express';
import { authController } from '../controllers/authController';
import { authenticate } from '../middleware/auth';
import { validateRequest } from '../middleware/validation';
import { authLoginValidator, authRegisterValidator } from '../middleware/requestValidators';

const router = Router();
router.post('/register', authRegisterValidator, validateRequest, authController.register);
router.post('/login', authLoginValidator, validateRequest, authController.login);
router.get('/me',        authenticate, (req, res, next) => authController.me(req, res, next));
export default router;
