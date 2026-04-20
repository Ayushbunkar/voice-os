import { Router } from 'express';
import multer from 'multer';
import { commandController } from '../controllers/commandController';
import { authenticate } from '../middleware/auth';
import { commandRateLimit } from '../middleware/rateLimiter';
import { validateRequest } from '../middleware/validation';
import { commandHistoryValidator, commandProcessValidator } from '../middleware/requestValidators';

const upload = multer({ dest: 'uploads/', limits: { fileSize: 25 * 1024 * 1024 } });
const router = Router();

router.use(authenticate, commandRateLimit);
router.post('/', commandProcessValidator, validateRequest, commandController.processText);
router.post('/audio',   upload.single('audio'), commandController.processAudio);
router.get('/history', commandHistoryValidator, validateRequest, commandController.history);
export default router;
