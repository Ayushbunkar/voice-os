import nodemailer from 'nodemailer';
import { env } from '../config/env';
import { logger } from '../utils/logger';

let transporter: nodemailer.Transporter | null = null;

function getTransporter(): nodemailer.Transporter | null {
  if (transporter) return transporter;

  if (!env.smtp.host || !env.smtp.user || !env.smtp.pass) {
    return null;
  }

  transporter = nodemailer.createTransport({
    host: env.smtp.host,
    port: env.smtp.port,
    secure: env.smtp.port === 465,
    auth: {
      user: env.smtp.user,
      pass: env.smtp.pass,
    },
  });

  return transporter;
}

export async function sendSubscriptionEmail(
  to: string,
  plan: string,
  status: string
): Promise<void> {
  const mailer = getTransporter();
  if (!mailer) {
    logger.debug('SMTP not configured, skipping subscription email', { to, plan, status });
    return;
  }

  const prettyPlan = plan.toUpperCase();
  await mailer.sendMail({
    from: env.smtp.from,
    to,
    subject: `VoiceOS subscription update: ${prettyPlan}`,
    text: `Your VoiceOS Cloud subscription is now ${status} on the ${prettyPlan} plan.`,
    html: `<p>Your VoiceOS Cloud subscription is now <b>${status}</b> on the <b>${prettyPlan}</b> plan.</p>`,
  });
}
