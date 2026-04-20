import { Router } from 'express';
import { macroController } from '../controllers/macroController';
import { authenticate } from '../middleware/auth';
import { validateRequest } from '../middleware/validation';
import { macroCreateValidator, macroExecuteValidator, macroUpdateValidator } from '../middleware/requestValidators';

const router = Router();
router.use(authenticate);
router.get('/',       macroController.list);
router.post('/', macroCreateValidator, validateRequest, macroController.create);
router.put('/:id', macroUpdateValidator, validateRequest, macroController.update);
router.post('/:id/execute', macroExecuteValidator, validateRequest, macroController.execute);
router.delete('/:id', macroController.remove);
export default router;
