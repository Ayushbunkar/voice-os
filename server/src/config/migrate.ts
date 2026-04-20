import fs from 'fs';
import path from 'path';
import { pool } from './database';
import { logger } from '../utils/logger';
import { env } from './env';

async function migrate() {
  logger.info('Starting schema migration...');
  try {
    const schemaPath = path.resolve(__dirname, '../../database/schema.sql');
    const sql = fs.readFileSync(schemaPath, 'utf8');

    await pool.query(sql);

    logger.info('Schema migration completed successfully.');
  } catch (err: any) {
    logger.error('Migration failed:', err);
  } finally {
    await pool.end();
  }
}

// Execute if run directly
if (require.main === module) {
  migrate();
}
