/// <reference types="jest" />

import { describe, expect, it } from '@jest/globals';
import request from 'supertest';
import { createApp } from '../../src/app';

describe('Auth Validation', () => {
  const app = createApp();

  it('rejects invalid register payload', async () => {
    const res = await request(app)
      .post('/api/v1/auth/register')
      .send({ email: 'invalid-email', password: '123' });

    expect(res.status).toBe(422);
    expect(res.body.success).toBe(false);
    expect(res.body.message).toBe('Validation failed');
  });

  it('rejects empty login payload', async () => {
    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({});

    expect(res.status).toBe(422);
    expect(res.body.success).toBe(false);
  });
});
