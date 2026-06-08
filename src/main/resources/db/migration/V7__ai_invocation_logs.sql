-- AI invocation observability and user-visible token/cost accounting.

CREATE TABLE IF NOT EXISTS ai_invocation_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    essay_id BIGINT REFERENCES essays(id) ON DELETE CASCADE,
    score_id BIGINT REFERENCES essay_scores(id) ON DELETE CASCADE,
    api_config_id BIGINT REFERENCES api_configs(id) ON DELETE SET NULL,
    attempt_no INTEGER NOT NULL DEFAULT 1,
    purpose VARCHAR(40) NOT NULL,
    provider VARCHAR(80),
    endpoint_type VARCHAR(80),
    model VARCHAR(160),
    provider_request_id VARCHAR(160),
    status VARCHAR(30) NOT NULL,
    latency_ms INTEGER,
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    usage_source VARCHAR(30) NOT NULL DEFAULT 'UNAVAILABLE',
    estimated_cost DECIMAL(18,6),
    currency VARCHAR(12),
    failure_code VARCHAR(80),
    failure_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_invocation_user_created_at
    ON ai_invocation_logs(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_invocation_essay_score
    ON ai_invocation_logs(user_id, essay_id, score_id, created_at);

CREATE INDEX IF NOT EXISTS idx_ai_invocation_status
    ON ai_invocation_logs(status, created_at DESC);
