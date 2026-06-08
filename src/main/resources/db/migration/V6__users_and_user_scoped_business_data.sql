-- User system and user-scoped business data.
-- Existing MVP business data is intentionally discarded per product decision.

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(60),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at TIMESTAMP,
    password_changed_at TIMESTAMP,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_username ON users(username);
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_status_created_at ON users(status, created_at DESC);

TRUNCATE TABLE essay_scores, essays, api_configs RESTART IDENTITY CASCADE;

ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS allow_public_use BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS input_token_price_per_million DECIMAL(12,6);
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS output_token_price_per_million DECIMAL(12,6);
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS currency VARCHAR(12);

CREATE INDEX IF NOT EXISTS idx_configs_owner_created_at ON api_configs(owner_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_configs_visibility ON api_configs(visibility, allow_public_use);
CREATE UNIQUE INDEX IF NOT EXISTS ux_configs_owner_default
    ON api_configs(owner_user_id)
    WHERE is_default = true AND owner_user_id IS NOT NULL;

ALTER TABLE essays ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE essays ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE essays ADD CONSTRAINT fk_essays_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

DROP INDEX IF EXISTS ux_essays_idempotency_key;
CREATE UNIQUE INDEX IF NOT EXISTS ux_essays_user_id_idempotency_key
    ON essays(user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_essays_user_created_at ON essays(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_essays_user_content_hash_created_at
    ON essays(user_id, content_hash, created_at DESC)
    WHERE content_hash IS NOT NULL;
