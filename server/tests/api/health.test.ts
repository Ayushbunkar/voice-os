/// <reference types="jest" />

import { describe, expect, it } from '@jest/globals';
import request from 'supertest';
import { createApp } from '../../src/app';

describe('Health API', () => {
  const app = createApp();

  it('GET /health returns status ok', async () => {
    const res = await request(app).get('/health');

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');
    expect(res.body).toHaveProperty('time');
  });
});
