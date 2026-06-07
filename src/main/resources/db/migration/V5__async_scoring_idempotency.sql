-- Async scoring and idempotency support.

ALTER TABLE essays ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160);
ALTER TABLE essays ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

ALTER TABLE essay_scores ALTER COLUMN result_json DROP NOT NULL;
ALTER TABLE essay_scores ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS ux_essays_idempotency_key
    ON essays(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_essays_content_hash_created_at
    ON essays(content_hash, created_at DESC)
    WHERE content_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_scores_status_updated_at
    ON essay_scores(scoring_status, updated_at DESC);
