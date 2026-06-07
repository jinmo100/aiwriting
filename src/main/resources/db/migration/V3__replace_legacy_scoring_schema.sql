-- Destructive scoring schema replacement for dynamic rubric-based scoring.
-- Existing essay/score history is intentionally discarded per product decision.

DROP TABLE IF EXISTS essay_scores;
DROP TABLE IF EXISTS essays;

CREATE TABLE essays (
    id BIGSERIAL PRIMARY KEY,
    essay_type VARCHAR(50) NOT NULL,
    task_prompt TEXT,
    content TEXT NOT NULL,
    word_count INTEGER NOT NULL,
    char_count INTEGER NOT NULL,
    input_analysis_json TEXT,
    safety_analysis_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE essay_scores (
    id BIGSERIAL PRIMARY KEY,
    essay_id BIGINT NOT NULL REFERENCES essays(id) ON DELETE CASCADE,
    api_config_id BIGINT REFERENCES api_configs(id) ON DELETE SET NULL,

    scoring_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',

    rubric_type VARCHAR(50) NOT NULL,
    rubric_version VARCHAR(50) NOT NULL,

    native_score DECIMAL(6,2),
    native_score_display VARCHAR(50),
    normalized_score DECIMAL(6,2),
    grade_label VARCHAR(50),
    confidence_level VARCHAR(20),

    result_json TEXT NOT NULL,

    ai_model VARCHAR(100),
    tokens_used INTEGER,
    processing_time INTEGER,

    error_code VARCHAR(80),
    error_message TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_essays_created_at ON essays(created_at DESC);
CREATE INDEX idx_essays_type_created_at ON essays(essay_type, created_at DESC);
CREATE INDEX idx_scores_essay_id ON essay_scores(essay_id);
CREATE INDEX idx_scores_status_created_at ON essay_scores(scoring_status, created_at DESC);
CREATE INDEX idx_scores_rubric ON essay_scores(rubric_type, rubric_version);
