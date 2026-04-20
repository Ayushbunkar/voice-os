/// <reference types="jest" />

import { describe, expect, it } from '@jest/globals';
import request from 'supertest';
import { createApp } from '../../src/app';

describe('Billing plans API', () => {
  const app = createApp();

  it('returns available plans', async () => {
    const res = await request(app).get('/api/v1/billing/plans');

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(Array.isArray(res.body.plans)).toBe(true);
  });
});
