-- Scoring failure details and controlled retry support.

ALTER TABLE essay_scores ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 1;
ALTER TABLE essay_scores ADD COLUMN IF NOT EXISTS failure_detail_json TEXT;

CREATE INDEX IF NOT EXISTS idx_scores_attempt_status
    ON essay_scores(scoring_status, attempt_count, updated_at DESC);
