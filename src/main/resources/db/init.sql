-- Legacy fallback schema. PostgreSQL/dev schema evolution is managed by Flyway in db/migration.

CREATE TABLE IF NOT EXISTS api_configs (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    provider VARCHAR(50),
    provider_type VARCHAR(50) NOT NULL DEFAULT 'OPENAI_CHAT_COMPLETIONS',
    provider_label VARCHAR(100),
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(255),
    api_key_encrypted TEXT,
    model_name VARCHAR(100) NOT NULL,
    temperature DECIMAL(4,2) DEFAULT 0.3,
    max_tokens INTEGER DEFAULT 2048,
    timeout_seconds INTEGER DEFAULT 60,
    model_parameters_json TEXT,
    is_default BOOLEAN DEFAULT FALSE,
    last_test_status VARCHAR(20),
    last_test_error_code VARCHAR(50),
    last_test_message VARCHAR(500),
    last_test_latency_ms INTEGER,
    last_tested_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS essays (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    word_count INTEGER,
    essay_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS essay_scores (
    id BIGSERIAL PRIMARY KEY,
    essay_id BIGINT NOT NULL REFERENCES essays(id) ON DELETE CASCADE,
    api_config_id BIGINT REFERENCES api_configs(id) ON DELETE SET NULL,
    overall_score DECIMAL(5,2),
    content_score DECIMAL(5,2),
    language_score DECIMAL(5,2),
    structure_score DECIMAL(5,2),
    coherence_score DECIMAL(5,2),
    strengths TEXT,
    suggestions TEXT,
    errors TEXT,
    detailed_feedback TEXT,
    ai_model VARCHAR(100),
    tokens_used INTEGER,
    processing_time INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_essays_created_at ON essays(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_scores_essay_id ON essay_scores(essay_id);
CREATE INDEX IF NOT EXISTS idx_configs_default ON api_configs(is_default);
CREATE INDEX IF NOT EXISTS idx_configs_provider_type ON api_configs(provider_type);
