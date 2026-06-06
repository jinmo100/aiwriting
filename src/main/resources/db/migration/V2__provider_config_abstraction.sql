-- Provider abstraction schema evolution. Safe for existing dev databases created by init.sql.

ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS provider_type VARCHAR(50);
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS provider_label VARCHAR(100);
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS api_key_encrypted TEXT;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS temperature DECIMAL(4,2) DEFAULT 0.3;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS max_tokens INTEGER DEFAULT 2048;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS timeout_seconds INTEGER DEFAULT 60;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS model_parameters_json TEXT;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS last_test_status VARCHAR(20);
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS last_test_error_code VARCHAR(50);
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS last_test_message VARCHAR(500);
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS last_test_latency_ms INTEGER;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS last_tested_at TIMESTAMP;

UPDATE api_configs
SET provider_label = COALESCE(provider_label, provider)
WHERE provider_label IS NULL;

UPDATE api_configs
SET provider_type = COALESCE(provider_type, 'OPENAI_CHAT_COMPLETIONS')
WHERE provider_type IS NULL;

ALTER TABLE api_configs ALTER COLUMN provider_type SET DEFAULT 'OPENAI_CHAT_COMPLETIONS';
ALTER TABLE api_configs ALTER COLUMN provider_type SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_configs_provider_type ON api_configs(provider_type);
