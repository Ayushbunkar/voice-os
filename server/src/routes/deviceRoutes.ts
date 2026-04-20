import { Router } from 'express';
import { deviceController } from '../controllers/deviceController';
import { authenticate } from '../middleware/auth';
import { validateRequest } from '../middleware/validation';
import { deviceCommandValidator, deviceConnectValidator } from '../middleware/requestValidators';

const router = Router();
router.use(authenticate);
router.get('/',             deviceController.list);
router.post('/connect', deviceConnectValidator, validateRequest, deviceController.connect);
router.delete('/:id',       deviceController.remove);
router.post('/:id/command', deviceCommandValidator, validateRequest, deviceController.sendRemoteCommand);
export default router;
