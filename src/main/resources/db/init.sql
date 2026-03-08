-- 创建数据库（如果使用PostgreSQL需要先创建）
-- CREATE DATABASE aiwriting;

-- 创建api_configs表
CREATE TABLE IF NOT EXISTS api_configs (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(255) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建essays表
CREATE TABLE IF NOT EXISTS essays (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    word_count INTEGER,
    essay_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建essay_scores表
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

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_essays_created_at ON essays(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_scores_essay_id ON essay_scores(essay_id);
CREATE INDEX IF NOT EXISTS idx_configs_default ON api_configs(is_default);

-- 插入示例API配置（可选）
-- INSERT INTO api_configs (config_name, provider, base_url, api_key, model_name, is_default)
-- VALUES ('OpenRouter Gemma', 'openrouter', 'https://openrouter.ai/api/v1', 'your-api-key', 'google/gemma-2-9b-it:free', true);
