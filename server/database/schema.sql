-- ============================================================
-- VoiceOS Cloud — PostgreSQL Schema
-- Run manually or via: ts-node src/config/migrate.ts
-- ============================================================

-- Extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ── Users ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  name          TEXT,
  plan          TEXT NOT NULL DEFAULT 'free' CHECK (plan IN ('free', 'pro', 'enterprise')),
  is_verified   BOOLEAN NOT NULL DEFAULT FALSE,
  is_admin      BOOLEAN NOT NULL DEFAULT FALSE,
  avatar_url    TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Devices ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS devices (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_name   TEXT NOT NULL,
  device_type   TEXT NOT NULL DEFAULT 'android' CHECK (device_type IN ('android', 'desktop', 'web')),
  device_token  TEXT UNIQUE,                        -- for push / socket identification
  status        TEXT NOT NULL DEFAULT 'offline' CHECK (status IN ('online', 'offline')),
  last_seen_at  TIMESTAMPTZ,
  metadata      JSONB DEFAULT '{}',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_devices_user_id ON devices(user_id);
CREATE INDEX IF NOT EXISTS idx_devices_token   ON devices(device_token);

CREATE TRIGGER trg_devices_updated_at
  BEFORE UPDATE ON devices
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Commands ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS commands (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id     UUID REFERENCES devices(id) ON DELETE SET NULL,
  input_text    TEXT NOT NULL,
  output_json   JSONB,
  status        TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'processing', 'success', 'failed')),
  error_message TEXT,
  model_used    TEXT,
  latency_ms    INTEGER,
  is_audio      BOOLEAN NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_commands_user_id    ON commands(user_id);
CREATE INDEX IF NOT EXISTS idx_commands_created_at ON commands(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_commands_status     ON commands(status);

-- ── Macros ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS macros (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,
  description   TEXT,
  steps         JSONB NOT NULL DEFAULT '[]',
  delay_ms      INTEGER NOT NULL DEFAULT 1500,
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(user_id, name)
);
CREATE INDEX IF NOT EXISTS idx_macros_user_id ON macros(user_id);

CREATE TRIGGER trg_macros_updated_at
  BEFORE UPDATE ON macros
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Subscriptions ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subscriptions (
  id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id               UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  plan                  TEXT NOT NULL CHECK (plan IN ('free', 'pro', 'enterprise')),
  status                TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'cancelled', 'past_due', 'trialing')),
  stripe_customer_id    TEXT,
  stripe_subscription_id TEXT,
  razorpay_sub_id       TEXT,
  current_period_start  TIMESTAMPTZ,
  current_period_end    TIMESTAMPTZ,
  cancel_at_period_end  BOOLEAN NOT NULL DEFAULT FALSE,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_stripe  ON subscriptions(stripe_customer_id);

-- Backfill unique constraint for environments created before this schema update
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'subscriptions_user_id_key'
  ) THEN
    ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_user_id_key UNIQUE (user_id);
  END IF;
END $$;

CREATE TRIGGER trg_subs_updated_at
  BEFORE UPDATE ON subscriptions
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Command Usage (daily rolling counter) ─────────────────────────────────
CREATE TABLE IF NOT EXISTS command_usage (
  id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date       DATE NOT NULL DEFAULT CURRENT_DATE,
  count      INTEGER NOT NULL DEFAULT 0,
  UNIQUE(user_id, date)
);
CREATE INDEX IF NOT EXISTS idx_usage_user_date ON command_usage(user_id, date);

-- ── Macro Execution Runs ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS macro_runs (
  id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  macro_id           UUID NOT NULL REFERENCES macros(id) ON DELETE CASCADE,
  device_id          UUID REFERENCES devices(id) ON DELETE SET NULL,
  status             TEXT NOT NULL CHECK (status IN ('success', 'failed')),
  failed_step_index  INTEGER,
  error_log          JSONB,
  executed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_macro_runs_user_time ON macro_runs(user_id, executed_at DESC);
CREATE INDEX IF NOT EXISTS idx_macro_runs_macro     ON macro_runs(macro_id);

-- ── Analytics Events ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS analytics_events (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
  event_type  TEXT NOT NULL,
  payload     JSONB NOT NULL DEFAULT '{}',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_analytics_events_user_time ON analytics_events(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_analytics_events_type      ON analytics_events(event_type);

-- ── Webhook Events (idempotency log) ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS webhook_events (
  id          TEXT PRIMARY KEY,      -- Stripe/Razorpay event ID
  provider    TEXT NOT NULL,
  type        TEXT NOT NULL,
  payload     JSONB NOT NULL,
  processed   BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
