import { Router, raw } from 'express';
import { paymentController } from '../controllers/paymentController';
import { authenticate } from '../middleware/auth';
import { validateRequest } from '../middleware/validation';
import { billingCheckoutValidator, billingPortalValidator } from '../middleware/requestValidators';

const router = Router();

// Webhook needs raw body for signature verification
router.post('/webhook', raw({ type: 'application/json' }), paymentController.stripeWebhook);
router.post('/webhook/razorpay', raw({ type: 'application/json' }), paymentController.razorpayWebhook);

router.get('/plans',       paymentController.getPlans);
router.post('/checkout', authenticate, billingCheckoutValidator, validateRequest, paymentController.createCheckout);
router.post('/checkout/razorpay', authenticate, billingCheckoutValidator, validateRequest, paymentController.createRazorpaySubscription);
router.post('/portal', authenticate, billingPortalValidator, validateRequest, paymentController.createPortal);
export default router;
