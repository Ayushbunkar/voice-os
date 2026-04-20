import { body, param, query } from 'express-validator';

export const authRegisterValidator = [
  body('email').isEmail().withMessage('Valid email is required').normalizeEmail(),
  body('password')
    .isString()
    .isLength({ min: 8, max: 128 })
    .withMessage('Password must be 8-128 characters'),
  body('name').optional().isString().trim().isLength({ min: 1, max: 80 }),
];

export const authLoginValidator = [
  body('email').isEmail().withMessage('Valid email is required').normalizeEmail(),
  body('password').isString().isLength({ min: 1 }).withMessage('Password is required'),
];

export const commandProcessValidator = [
  body('input').optional().isString().trim().isLength({ min: 1, max: 2000 }),
  body('command').optional().isString().trim().isLength({ min: 1, max: 2000 }),
  body().custom((value) => {
    const input = typeof value?.input === 'string' ? value.input.trim() : '';
    const command = typeof value?.command === 'string' ? value.command.trim() : '';
    if (!input && !command) {
      throw new Error('Either input or command is required');
    }
    return true;
  }),
  body('deviceId').optional().isUUID().withMessage('deviceId must be a valid UUID'),
  body('context').optional().isObject().withMessage('context must be an object'),
];

export const commandHistoryValidator = [
  query('page').optional().isInt({ min: 1 }).toInt(),
  query('limit').optional().isInt({ min: 1, max: 100 }).toInt(),
];

export const deviceConnectValidator = [
  body('deviceName').isString().trim().isLength({ min: 2, max: 100 }).withMessage('deviceName is required'),
  body('deviceType').optional().isIn(['android', 'desktop', 'web']).withMessage('invalid deviceType'),
  body('deviceToken').optional().isString().trim().isLength({ min: 8, max: 255 }),
];

export const deviceCommandValidator = [
  param('id').isUUID().withMessage('Device id must be a UUID'),
  body('structured').isObject().withMessage('structured command is required'),
];

export const macroCreateValidator = [
  body('name').isString().trim().isLength({ min: 2, max: 80 }).withMessage('name is required'),
  body('description').optional().isString().isLength({ max: 500 }),
  body('steps').isArray({ min: 1 }).withMessage('steps must be a non-empty array'),
  body('delayMs').optional().isInt({ min: 0, max: 120000 }).toInt(),
];

export const macroUpdateValidator = [
  param('id').isUUID().withMessage('Macro id must be UUID'),
  body('name').optional().isString().trim().isLength({ min: 2, max: 80 }),
  body('description').optional().isString().isLength({ max: 500 }),
  body('steps').optional().isArray({ min: 1 }),
  body('delayMs').optional().isInt({ min: 0, max: 120000 }).toInt(),
  body('isActive').optional().isBoolean().toBoolean(),
];

export const macroExecuteValidator = [
  param('id').isUUID().withMessage('Macro id must be UUID'),
  body('deviceId').optional().isUUID().withMessage('deviceId must be UUID'),
  body('maxRetries').optional().isInt({ min: 0, max: 5 }).toInt(),
];

export const billingCheckoutValidator = [
  body('provider').optional().isIn(['stripe', 'razorpay']),
  body('priceId').optional().isString().trim().isLength({ min: 1 }),
  body('plan').optional().isIn(['pro', 'enterprise']),
];

export const billingPortalValidator = [
  body('provider').optional().isIn(['stripe', 'razorpay']),
];
