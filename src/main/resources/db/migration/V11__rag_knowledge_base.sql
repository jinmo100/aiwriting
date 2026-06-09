CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS background_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_type VARCHAR(50) NOT NULL,
    owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL,
    business_key VARCHAR(200) NOT NULL,
    payload_json TEXT,
    result_json TEXT,
    error_code VARCHAR(80),
    error_message TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    run_after TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_by VARCHAR(120),
    locked_until TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_background_jobs_active_business
    ON background_jobs(job_type, owner_user_id, business_key)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX idx_background_jobs_claim
    ON background_jobs(status, run_after, locked_until, created_at);

CREATE TABLE IF NOT EXISTS embedding_configs (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    config_name VARCHAR(100) NOT NULL,
    provider_type VARCHAR(50) NOT NULL,
    provider_label VARCHAR(100),
    base_url VARCHAR(500) NOT NULL,
    api_key_encrypted TEXT,
    model_name VARCHAR(160) NOT NULL,
    dimensions INTEGER NOT NULL,
    timeout_seconds INTEGER NOT NULL DEFAULT 60,
    input_token_price_per_million DECIMAL(12,6),
    currency VARCHAR(12),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    last_test_status VARCHAR(30),
    last_test_error_code VARCHAR(80),
    last_test_message TEXT,
    last_test_latency_ms INTEGER,
    last_tested_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_embedding_configs_dimensions_1536 CHECK (dimensions = 1536)
);

CREATE INDEX idx_embedding_configs_owner_created_at
    ON embedding_configs(owner_user_id, created_at DESC);

CREATE UNIQUE INDEX ux_embedding_configs_owner_default
    ON embedding_configs(owner_user_id)
    WHERE is_default = true;

CREATE TABLE IF NOT EXISTS rag_documents (
    id BIGSERIAL PRIMARY KEY,
    document_type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_title VARCHAR(200),
    essay_type VARCHAR(50),
    skill_tag VARCHAR(80) NOT NULL,
    level_tag VARCHAR(50),
    version VARCHAR(50) NOT NULL DEFAULT 'RAG_KB_V1',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rag_documents_active_type
    ON rag_documents(is_active, document_type, essay_type);

CREATE INDEX idx_rag_documents_skill_tag
    ON rag_documents(skill_tag);

CREATE TABLE IF NOT EXISTS rag_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES rag_documents(id) ON DELETE CASCADE,
    chunk_no INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    metadata_json TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_rag_chunks_document_chunk_no
    ON rag_chunks(document_id, chunk_no);

CREATE INDEX idx_rag_chunks_active
    ON rag_chunks(is_active);

CREATE INDEX idx_rag_chunks_content_hash
    ON rag_chunks(content_hash);

CREATE TABLE IF NOT EXISTS rag_chunk_embeddings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    embedding_config_id BIGINT NOT NULL REFERENCES embedding_configs(id) ON DELETE CASCADE,
    chunk_id BIGINT NOT NULL REFERENCES rag_chunks(id) ON DELETE CASCADE,
    embedding_model VARCHAR(160) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    embedding_version VARCHAR(80) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding_vector vector(1536) NOT NULL,
    indexed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_rag_chunk_embeddings_dimension_1536 CHECK (embedding_dimension = 1536)
);

CREATE UNIQUE INDEX ux_rag_embeddings_user_config_chunk_version
    ON rag_chunk_embeddings(user_id, embedding_config_id, chunk_id, embedding_version);

CREATE INDEX idx_rag_embeddings_user_config
    ON rag_chunk_embeddings(user_id, embedding_config_id);

CREATE INDEX idx_rag_embeddings_chunk
    ON rag_chunk_embeddings(chunk_id);

CREATE TABLE IF NOT EXISTS rag_feedbacks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    essay_id BIGINT NOT NULL REFERENCES essays(id) ON DELETE CASCADE,
    score_id BIGINT NOT NULL REFERENCES essay_scores(id) ON DELETE CASCADE,
    api_config_id BIGINT REFERENCES api_configs(id) ON DELETE SET NULL,
    embedding_config_id BIGINT REFERENCES embedding_configs(id) ON DELETE SET NULL,
    job_id BIGINT REFERENCES background_jobs(id) ON DELETE SET NULL,
    query_text TEXT,
    retrieved_chunk_ids TEXT,
    feedback_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_rag_feedback_score_config
    ON rag_feedbacks(score_id, embedding_config_id)
    WHERE embedding_config_id IS NOT NULL;

CREATE INDEX idx_rag_feedbacks_user_essay_created_at
    ON rag_feedbacks(user_id, essay_id, created_at DESC);

CREATE INDEX idx_rag_feedbacks_job_id
    ON rag_feedbacks(job_id)
    WHERE job_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS rag_feedback_citations (
    id BIGSERIAL PRIMARY KEY,
    feedback_id BIGINT NOT NULL REFERENCES rag_feedbacks(id) ON DELETE CASCADE,
    chunk_id BIGINT REFERENCES rag_chunks(id) ON DELETE SET NULL,
    source_title VARCHAR(200),
    source_type VARCHAR(50),
    snippet TEXT,
    relevance_score DOUBLE PRECISION,
    rank_no INTEGER NOT NULL,
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_rag_feedback_citations_feedback_rank
    ON rag_feedback_citations(feedback_id, rank_no);

CREATE INDEX idx_rag_feedback_citations_chunk
    ON rag_feedback_citations(chunk_id)
    WHERE chunk_id IS NOT NULL;

COMMENT ON TABLE background_jobs IS '轻量通用后台任务表，用于统一承载 RAG 索引和 RAG Feedback 生成任务';
COMMENT ON COLUMN background_jobs.id IS '后台任务主键';
COMMENT ON COLUMN background_jobs.job_type IS '任务类型，例如 RAG_INDEX 或 RAG_FEEDBACK';
COMMENT ON COLUMN background_jobs.owner_user_id IS '任务归属用户 ID，用于用户数据隔离';
COMMENT ON COLUMN background_jobs.requested_by_user_id IS '任务发起用户 ID，管理员代触发时记录管理员用户';
COMMENT ON COLUMN background_jobs.status IS '任务状态：PENDING、RUNNING、COMPLETED、FAILED 或 SKIPPED';
COMMENT ON COLUMN background_jobs.business_key IS '同一任务类型和用户维度下的业务去重键，仅允许一个待执行或执行中任务';
COMMENT ON COLUMN background_jobs.payload_json IS '任务处理输入 JSON，不得保存明文密钥或系统 prompt 等敏感信息';
COMMENT ON COLUMN background_jobs.result_json IS '任务处理结果摘要 JSON，用于展示进度或安全结果';
COMMENT ON COLUMN background_jobs.error_code IS '标准化错误码，用于前端和运维分类';
COMMENT ON COLUMN background_jobs.error_message IS '对用户安全的错误信息，不包含敏感上下文';
COMMENT ON COLUMN background_jobs.attempt_count IS '任务已尝试执行次数';
COMMENT ON COLUMN background_jobs.max_attempts IS '任务允许的最大尝试次数';
COMMENT ON COLUMN background_jobs.run_after IS '任务最早可被领取执行的时间';
COMMENT ON COLUMN background_jobs.locked_by IS '当前领取任务的执行器标识';
COMMENT ON COLUMN background_jobs.locked_until IS '任务锁过期时间，便于后续多实例恢复';
COMMENT ON COLUMN background_jobs.started_at IS '任务开始执行时间';
COMMENT ON COLUMN background_jobs.finished_at IS '任务完成、失败或跳过时间';
COMMENT ON COLUMN background_jobs.created_at IS '任务创建时间';
COMMENT ON COLUMN background_jobs.updated_at IS '任务最近更新时间';

COMMENT ON TABLE embedding_configs IS '用户自管理的 Embedding Provider 配置表，API Key 仅加密保存';
COMMENT ON COLUMN embedding_configs.id IS 'Embedding 配置主键';
COMMENT ON COLUMN embedding_configs.owner_user_id IS '配置归属用户 ID，用于阻止跨用户访问';
COMMENT ON COLUMN embedding_configs.config_name IS '用户可见的配置名称';
COMMENT ON COLUMN embedding_configs.provider_type IS 'Embedding Provider 类型，V1 支持 OPENAI_EMBEDDINGS';
COMMENT ON COLUMN embedding_configs.provider_label IS '用户可见的 Provider 标签或备注';
COMMENT ON COLUMN embedding_configs.base_url IS 'OpenAI-compatible Embedding API 基础地址';
COMMENT ON COLUMN embedding_configs.api_key_encrypted IS '应用层加密后的 Embedding API Key，响应中不得返回';
COMMENT ON COLUMN embedding_configs.model_name IS 'Embedding 模型名称';
COMMENT ON COLUMN embedding_configs.dimensions IS 'Embedding 向量维度，V1 固定为 1536';
COMMENT ON COLUMN embedding_configs.timeout_seconds IS '调用 Embedding Provider 的超时时间秒数';
COMMENT ON COLUMN embedding_configs.input_token_price_per_million IS '每百万输入 token 的估算价格，用于后续成本展示';
COMMENT ON COLUMN embedding_configs.currency IS '价格币种代码';
COMMENT ON COLUMN embedding_configs.is_default IS '是否为当前用户默认 Embedding 配置，同一用户最多一个';
COMMENT ON COLUMN embedding_configs.last_test_status IS '最近一次连接测试状态';
COMMENT ON COLUMN embedding_configs.last_test_error_code IS '最近一次连接测试标准化错误码';
COMMENT ON COLUMN embedding_configs.last_test_message IS '最近一次连接测试的安全提示信息';
COMMENT ON COLUMN embedding_configs.last_test_latency_ms IS '最近一次连接测试耗时毫秒数';
COMMENT ON COLUMN embedding_configs.last_tested_at IS '最近一次连接测试时间';
COMMENT ON COLUMN embedding_configs.created_at IS '配置创建时间';
COMMENT ON COLUMN embedding_configs.updated_at IS '配置最近更新时间';

COMMENT ON TABLE rag_documents IS '内置 RAG 知识卡文档元数据表';
COMMENT ON COLUMN rag_documents.id IS '知识卡文档主键';
COMMENT ON COLUMN rag_documents.document_type IS '文档类型：语法知识、写作技能或考试策略';
COMMENT ON COLUMN rag_documents.title IS '知识卡标题';
COMMENT ON COLUMN rag_documents.source_type IS '来源类型，内置 seed 使用 INTERNAL_SEED';
COMMENT ON COLUMN rag_documents.source_title IS '来源标题或引用展示标题';
COMMENT ON COLUMN rag_documents.essay_type IS '适用作文类型，NULL 表示通用知识卡';
COMMENT ON COLUMN rag_documents.skill_tag IS '稳定技能标签，用于检索过滤和版本管理';
COMMENT ON COLUMN rag_documents.level_tag IS '适用水平或考试标签';
COMMENT ON COLUMN rag_documents.version IS '知识库版本号，用于索引重建和追踪';
COMMENT ON COLUMN rag_documents.is_active IS '知识卡是否启用，禁用后不参与索引和检索';
COMMENT ON COLUMN rag_documents.created_at IS '知识卡创建时间';
COMMENT ON COLUMN rag_documents.updated_at IS '知识卡最近更新时间';

COMMENT ON TABLE rag_chunks IS 'RAG 知识卡可检索文本片段表';
COMMENT ON COLUMN rag_chunks.id IS '知识片段主键';
COMMENT ON COLUMN rag_chunks.document_id IS '所属知识卡文档 ID';
COMMENT ON COLUMN rag_chunks.chunk_no IS '同一文档内的片段序号';
COMMENT ON COLUMN rag_chunks.content IS '用于 Embedding 和引用展示的知识片段正文';
COMMENT ON COLUMN rag_chunks.content_hash IS '知识片段正文哈希，用于判断索引是否需要重建';
COMMENT ON COLUMN rag_chunks.metadata_json IS '片段检索元数据 JSON，包含 skillTag、levelTag、documentType 等稳定字段';
COMMENT ON COLUMN rag_chunks.is_active IS '片段是否启用，禁用后不参与索引和检索';
COMMENT ON COLUMN rag_chunks.created_at IS '片段创建时间';
COMMENT ON COLUMN rag_chunks.updated_at IS '片段最近更新时间';

COMMENT ON TABLE rag_chunk_embeddings IS '按用户和 Embedding 配置隔离保存的知识片段向量表';
COMMENT ON COLUMN rag_chunk_embeddings.id IS '知识片段向量主键';
COMMENT ON COLUMN rag_chunk_embeddings.user_id IS '向量归属用户 ID，检索时必须强制过滤';
COMMENT ON COLUMN rag_chunk_embeddings.embedding_config_id IS '生成该向量使用的 Embedding 配置 ID';
COMMENT ON COLUMN rag_chunk_embeddings.chunk_id IS '对应的知识片段 ID';
COMMENT ON COLUMN rag_chunk_embeddings.embedding_model IS '生成向量使用的模型名称';
COMMENT ON COLUMN rag_chunk_embeddings.embedding_dimension IS '实际保存的向量维度，V1 固定为 1536';
COMMENT ON COLUMN rag_chunk_embeddings.embedding_version IS '索引版本标识，用于强制重建和版本隔离';
COMMENT ON COLUMN rag_chunk_embeddings.content_hash IS '生成该向量时使用的知识片段正文哈希';
COMMENT ON COLUMN rag_chunk_embeddings.embedding_vector IS 'pgvector 1536 维向量，不得返回给前端';
COMMENT ON COLUMN rag_chunk_embeddings.indexed_at IS '向量生成并写入索引的时间';
COMMENT ON COLUMN rag_chunk_embeddings.created_at IS '向量记录创建时间';
COMMENT ON COLUMN rag_chunk_embeddings.updated_at IS '向量记录最近更新时间';

COMMENT ON TABLE rag_feedbacks IS '评分后的 RAG 教学反馈业务结果表，任务状态由 background_jobs 管理';
COMMENT ON COLUMN rag_feedbacks.id IS 'RAG Feedback 主键';
COMMENT ON COLUMN rag_feedbacks.user_id IS '反馈归属用户 ID，用于用户数据隔离';
COMMENT ON COLUMN rag_feedbacks.essay_id IS '反馈关联的作文 ID';
COMMENT ON COLUMN rag_feedbacks.score_id IS '反馈关联的评分结果 ID';
COMMENT ON COLUMN rag_feedbacks.api_config_id IS '生成反馈复用的 Chat Provider 配置 ID';
COMMENT ON COLUMN rag_feedbacks.embedding_config_id IS '检索知识时使用的 Embedding 配置 ID';
COMMENT ON COLUMN rag_feedbacks.job_id IS '生成该反馈的后台任务 ID';
COMMENT ON COLUMN rag_feedbacks.query_text IS '由评分结果构造出的 RAG 检索 query，不是用户手写输入';
COMMENT ON COLUMN rag_feedbacks.retrieved_chunk_ids IS '本次生成检索命中的知识片段 ID 列表 JSON';
COMMENT ON COLUMN rag_feedbacks.feedback_json IS '校验通过后的结构化 RAG 教学反馈 JSON';
COMMENT ON COLUMN rag_feedbacks.created_at IS '反馈创建时间';
COMMENT ON COLUMN rag_feedbacks.updated_at IS '反馈最近更新时间';

COMMENT ON TABLE rag_feedback_citations IS 'RAG Feedback 引用知识点快照表';
COMMENT ON COLUMN rag_feedback_citations.id IS '引用记录主键';
COMMENT ON COLUMN rag_feedback_citations.feedback_id IS '所属 RAG Feedback ID';
COMMENT ON COLUMN rag_feedback_citations.chunk_id IS '引用的知识片段 ID，片段删除后保留展示快照';
COMMENT ON COLUMN rag_feedback_citations.source_title IS '引用来源标题的安全展示快照';
COMMENT ON COLUMN rag_feedback_citations.source_type IS '引用来源类型的安全展示快照';
COMMENT ON COLUMN rag_feedback_citations.snippet IS '引用片段摘要，前端仅展示安全短文本';
COMMENT ON COLUMN rag_feedback_citations.relevance_score IS '检索相关性分数或距离转换分';
COMMENT ON COLUMN rag_feedback_citations.rank_no IS '本次反馈中的引用排序序号';
COMMENT ON COLUMN rag_feedback_citations.reason IS '该引用支持反馈项的简短原因';
COMMENT ON COLUMN rag_feedback_citations.created_at IS '引用记录创建时间';

COMMENT ON INDEX ux_background_jobs_active_business IS '同一任务类型、归属用户和业务键下仅允许一个待执行或执行中任务';
COMMENT ON INDEX idx_background_jobs_claim IS '后台任务领取查询索引，按状态、可运行时间、锁和创建时间过滤';
COMMENT ON INDEX idx_embedding_configs_owner_created_at IS '按用户查询 Embedding 配置列表的排序索引';
COMMENT ON INDEX ux_embedding_configs_owner_default IS '同一用户最多一个默认 Embedding 配置';
COMMENT ON INDEX idx_rag_documents_active_type IS '按启用状态、文档类型和作文类型过滤知识卡';
COMMENT ON INDEX idx_rag_documents_skill_tag IS '按稳定技能标签定位知识卡';
COMMENT ON INDEX ux_rag_chunks_document_chunk_no IS '同一知识卡文档内 chunk 序号唯一';
COMMENT ON INDEX idx_rag_chunks_active IS '按启用状态过滤知识片段';
COMMENT ON INDEX idx_rag_chunks_content_hash IS '按正文哈希定位需要重建索引的片段';
COMMENT ON INDEX ux_rag_embeddings_user_config_chunk_version IS '按用户、Embedding 配置、知识片段和版本唯一保存向量';
COMMENT ON INDEX idx_rag_embeddings_user_config IS '按用户和 Embedding 配置查询可用向量索引';
COMMENT ON INDEX idx_rag_embeddings_chunk IS '按知识片段定位相关向量记录';
COMMENT ON INDEX ux_rag_feedback_score_config IS '同一评分和 Embedding 配置最多保存一份 RAG Feedback';
COMMENT ON INDEX idx_rag_feedbacks_user_essay_created_at IS '按用户和作文查询反馈记录的排序索引';
COMMENT ON INDEX idx_rag_feedbacks_job_id IS '按后台任务定位反馈记录';
COMMENT ON INDEX ux_rag_feedback_citations_feedback_rank IS '同一反馈内引用排序序号唯一';
COMMENT ON INDEX idx_rag_feedback_citations_chunk IS '按知识片段查询引用记录';

WITH seed_cards(document_type, title, source_type, source_title, essay_type, skill_tag, level_tag, version, content, content_hash, metadata_json) AS (
    VALUES
    ('GRAMMAR_KNOWLEDGE', '主谓一致', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'subject_verb_agreement', 'GENERAL', 'RAG_KB_V1', '主谓一致：英语句子的谓语动词必须和主语在人称和数上保持一致。A singular subject needs a singular verb, while plural subjects need plural verbs. 例：The student writes every day; The students write every day. 检查长主语、插入语和 there be 结构时尤其要回到真正主语。', '9561ae1b2128b35cce942ecc4c63e9cdad6daffda6a6bbaa6e8201901ed3c337', '{"skillTag":"subject_verb_agreement","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '时态一致', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'tense_consistency', 'GENERAL', 'RAG_KB_V1', '时态一致：叙述同一时间线上的动作时，应避免无理由地在 present, past 和 future 之间跳动。If you describe a past experience, keep the main events in the past tense unless a fact is always true. 修改时先确定时间轴，再统一动词形式。', '8fae0241dfeeb82d22fd2d65429cb339d4a8bcbc141ae2f00396eba715024707', '{"skillTag":"tense_consistency","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '冠词使用', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'article_usage', 'GENERAL', 'RAG_KB_V1', '冠词使用：Use a or an for one nonspecific countable noun, and use the when the reader knows which one you mean. 不可数名词和复数泛指通常不加冠词，如 People need clean water. 写作中重点检查单数可数名词前是否缺少冠词。', '8453371d4b8f5de0e82156920d3eb0ff4a819f6dacbe326e19c4b7fc251d60fa', '{"skillTag":"article_usage","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '介词搭配', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'preposition_collocation', 'GENERAL', 'RAG_KB_V1', '介词搭配：Many English verbs, adjectives and nouns require fixed prepositions, such as depend on, be interested in, reason for and solution to. 介词错误会影响表达自然度。遇到中式直译时，优先查常见搭配，而不是逐字翻译。', '4c6fd31eeb976a52a73764aea277b8219e4c0e8b9e670ba7881164513128aa68', '{"skillTag":"preposition_collocation","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '名词单复数', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'noun_number', 'GENERAL', 'RAG_KB_V1', '名词单复数：Countable nouns need correct singular or plural forms. Use plural nouns after many, several, a number of and numbers larger than one. 不可数名词如 advice, information, homework 通常不用复数。检查量词和限定词可以帮助发现数的错误。', 'c48966a0bf93b18fb5871d6afc110a0c8f748beba38a1fdc549f7c0889151840', '{"skillTag":"noun_number","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '代词指代', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'pronoun_reference', 'GENERAL', 'RAG_KB_V1', '代词指代：A pronoun should clearly refer to one noun and match it in number and gender where relevant. Avoid vague this, it or they when the reader cannot identify the noun. 如果一个句子里有多个名词，必要时重复关键词比使用含糊代词更清楚。', '333ca020cd785f6c14b5f37dfe696ca300e8a44a9e4848c43a5e4d7e60a75ed4', '{"skillTag":"pronoun_reference","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '句子残缺', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'sentence_fragment', 'GENERAL', 'RAG_KB_V1', '句子残缺：A complete sentence normally needs a subject and a finite verb, and it should express a complete idea. Because I was tired is not complete unless it connects to a main clause. 修改方法是补主句、补谓语，或把片段并入前后句。', '166e68426d6317690152e508b8e6a8b064bcaf62ecf95a1993e3490a9ca16d7d', '{"skillTag":"sentence_fragment","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '逗号拼接', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'comma_splice', 'GENERAL', 'RAG_KB_V1', '逗号拼接：Do not join two complete sentences only with a comma. 错误：The weather was bad, we stayed at home. 可改为 The weather was bad, so we stayed at home; The weather was bad. We stayed at home; 或使用分号。', 'e5c896ab1aaca18c3966d8d307ba286ca832cf73d04d5397a9481d38b0526862', '{"skillTag":"comma_splice","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '从句连接', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'clause_linking', 'GENERAL', 'RAG_KB_V1', '从句连接：Subordinate clauses need clear linking words such as because, although, if, when, who and which. 不要把中文逻辑直接堆叠成多个英文小句。先判断关系是原因、让步、条件、时间还是定语，再选择合适连接词。', '30a4b923b29023a2064770fe6737d189e6564ae7587c6124144d4c919eb6e275', '{"skillTag":"clause_linking","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '非谓语动词', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'nonfinite_verbs', 'GENERAL', 'RAG_KB_V1', '非谓语动词：Use infinitives, gerunds and participles to make sentences concise, but ensure their logical subject is clear. 例：To improve my English, I read aloud every day. 避免 dangling modifier，如 Walking to school, the rain started.', '64f34a10b123cd036aa9763c0713ee61c3a35f6db6d1baf3a578bc3eb263b5bc', '{"skillTag":"nonfinite_verbs","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '比较结构', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'comparison_structure', 'GENERAL', 'RAG_KB_V1', '比较结构：Comparisons must be parallel and complete. Use comparative plus than, superlative with the, and avoid mixing more with -er. 例：This plan is better than the old one. 比较对象要同类，避免 My school is better than you.', '613bdcb4b274fb4d96b09852f84f7483e4f40171240379d9db341ce2c9c6937f', '{"skillTag":"comparison_structure","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '被动语态', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'passive_voice', 'GENERAL', 'RAG_KB_V1', '被动语态：Use be plus past participle when the receiver of an action is more important than the doer. 例：The problem was solved quickly. 不要把主动语态误写成 be plus base verb；同时避免过度使用被动导致表达笨重。', '64442bb538cba36ea3196aaac46e52010095183a33d30ed9b95ed79e37b8f13a', '{"skillTag":"passive_voice","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '拼写与词形', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'spelling_word_form', 'GENERAL', 'RAG_KB_V1', '拼写与词形：Check spelling, part of speech and word family. 例：success is a noun, successful is an adjective, successfully is an adverb. 作文中常见问题是把形容词当副词、名词当动词，或因拼写错误影响可读性。', '2fa71551358b77b93a7247e97bb4c3fd985be8a7ee1cd6b5f273480e22a3493c', '{"skillTag":"spelling_word_form","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '中式英语表达', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'chinglish_expression', 'GENERAL', 'RAG_KB_V1', '中式英语表达：Avoid translating Chinese word order or collocations directly into English. For example, learn knowledge is less natural than acquire knowledge or learn something. 优先使用英语常见搭配、简单直接的主谓宾结构和自然连接。', '20ac54c40a477074b70f74ed149293fd2a3c2d4b07ab6c6a8a360cdb0743f1b9', '{"skillTag":"chinglish_expression","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('GRAMMAR_KNOWLEDGE', '标点与大小写', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'punctuation_capitalization', 'GENERAL', 'RAG_KB_V1', '标点与大小写：Capitalize the first word of a sentence, the pronoun I, proper nouns and titles where needed. Use English punctuation consistently, including commas, periods and question marks. 标点错误会让句子边界不清，修改时先按完整句划分。', 'db4bfb479eb2f460d055377137a11dd0ffacc13cc02a3854b57e7907c55b47d5', '{"skillTag":"punctuation_capitalization","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}'),
    ('WRITING_SKILL', '段落主题句', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'paragraph_topic_sentence', 'GENERAL', 'RAG_KB_V1', '段落主题句：A body paragraph should usually begin with a topic sentence that states its main idea. The following sentences should explain, support or illustrate that idea. 如果段落只有例子没有中心句，读者会难以判断论点。', '229a96dad20b3a9c9986efc9a11ea29bb63db54cfdaa1942958763bbddb57113', '{"skillTag":"paragraph_topic_sentence","levelTag":"GENERAL","documentType":"WRITING_SKILL"}'),
    ('WRITING_SKILL', '论点展开', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'argument_development', 'GENERAL', 'RAG_KB_V1', '论点展开：A strong paragraph moves from claim to explanation to consequence. 不要只写 I think it is good. 说明 why it matters, how it works, and what result it brings. 每个主要观点至少给出一层原因或影响。', '528113d2e03435d4347fe0da73a045ba991d0be7cf0d8a27181d8f4e82dcd7c7', '{"skillTag":"argument_development","levelTag":"GENERAL","documentType":"WRITING_SKILL"}'),
    ('WRITING_SKILL', '例证支持', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'evidence_support', 'GENERAL', 'RAG_KB_V1', '例证支持：Examples make an argument concrete. Use personal experience, school life, social observations or simple facts, but connect each example back to the claim. 例子不能独立堆放，结尾要点明它证明了什么。', 'ce6e51b4dc968ada4532bbf2390ca03a151716565a3c5ab1ca43e43406ff6d1e', '{"skillTag":"evidence_support","levelTag":"GENERAL","documentType":"WRITING_SKILL"}'),
    ('WRITING_SKILL', '连接词使用', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'transition_use', 'GENERAL', 'RAG_KB_V1', '连接词使用：Transitions show logic, such as first, however, therefore, for example and in addition. Use them to clarify relationships, not to decorate every sentence. 连接词过少会显得跳跃，过多会显得机械。', '5806da1bf6a6e3f25940a1c4c525939a4ee52fb903bbdadd3d8c03a21587285a', '{"skillTag":"transition_use","levelTag":"GENERAL","documentType":"WRITING_SKILL"}'),
    ('WRITING_SKILL', '总分总结构', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'intro_body_conclusion', 'GENERAL', 'RAG_KB_V1', '总分总结构：Many exam essays work well with introduction, body paragraphs and conclusion. The introduction answers the topic, body paragraphs provide reasons and examples, and the conclusion restates the main message. 结构清晰比堆砌复杂句更重要。', '48d049f6b7f4379b7fff27e1eb17a087b0fa5217d3d4b2662d731b012e341368', '{"skillTag":"intro_body_conclusion","levelTag":"GENERAL","documentType":"WRITING_SKILL"}'),
    ('WRITING_SKILL', '结尾总结', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'conclusion_summary', 'GENERAL', 'RAG_KB_V1', '结尾总结：A conclusion should not introduce a completely new argument. Restate your position, summarize the strongest reason, and give a final implication or call to action. 简短有力的结尾能提升完整度。', '8a8927296e8e230372531eac320f0a0406edcabd3245f25c21885d3096a156eb', '{"skillTag":"conclusion_summary","levelTag":"GENERAL","documentType":"WRITING_SKILL"}'),
    ('WRITING_SKILL', '任务回应', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'task_response', 'GENERAL', 'RAG_KB_V1', '任务回应：Always answer the exact task. If the prompt asks for reasons, give reasons; if it asks for suggestions, provide actionable suggestions. 写完后逐条对照题目要求，确认没有漏点、偏题或只泛泛而谈。', '73db316e1746642c1f102666faad02efb298bdeeb64c122be4fc859400686854', '{"skillTag":"task_response","levelTag":"GENERAL","documentType":"WRITING_SKILL"}'),
    ('WRITING_SKILL', '语气与对象', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'tone_audience', 'GENERAL', 'RAG_KB_V1', '语气与对象：Choose tone according to audience and genre. A letter to a teacher should be polite and specific; an argumentative essay can be formal and balanced. 避免在正式写作中过度口语化，如 gonna, wanna 或夸张网络表达。', 'e9985fa4df2935051f6325339c1c15d15c476dd6702435c6d001539163765ef0', '{"skillTag":"tone_audience","levelTag":"GENERAL","documentType":"WRITING_SKILL"}'),
    ('WRITING_SKILL', '词汇多样性', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'lexical_variety', 'GENERAL', 'RAG_KB_V1', '词汇多样性：Use varied but accurate vocabulary. Replacing every simple word with a difficult word can cause unnatural expression. 优先替换高频重复词，如 important can become essential, valuable or meaningful when context fits.', '23ef5924c3ca4ae2d528d9d91ebb1f21e653000f2ab0786c21179865f77af144', '{"skillTag":"lexical_variety","levelTag":"GENERAL","documentType":"WRITING_SKILL"}'),
    ('WRITING_SKILL', '句式多样性', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', NULL, 'sentence_variety', 'GENERAL', 'RAG_KB_V1', '句式多样性：Combine simple, compound and complex sentences to improve rhythm. Use clauses and participial phrases only when they are correct and clear. 不要为了复杂而复杂；准确、清楚、多样三者要平衡。', 'da2f0fc681296ce1a1d2466d894e94fc6c61637332ed1151beb61fc11bb8072e', '{"skillTag":"sentence_variety","levelTag":"GENERAL","documentType":"WRITING_SKILL"}'),
    ('EXAM_STRATEGY', '高考书信/邮件任务完成', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', 'SENIOR_GAOKAO', 'gaokao_email_task_completion', 'SENIOR_GAOKAO', 'RAG_KB_V1', '高考书信或邮件：先识别身份、对象、目的和要点。开头礼貌说明写信目的，中间逐条覆盖题目要求，结尾表达期待或感谢。注意格式、语气和字数，避免只写模板句而遗漏核心任务。', '8709d2a5c38e1a560bfb95ca26c91770c601470f86fed3f659f42280caa796ce', '{"skillTag":"gaokao_email_task_completion","levelTag":"SENIOR_GAOKAO","documentType":"EXAM_STRATEGY","essayType":"SENIOR_GAOKAO"}'),
    ('EXAM_STRATEGY', '中考要点覆盖', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', 'JUNIOR_ZHONGKAO', 'zhongkao_key_points', 'JUNIOR_ZHONGKAO', 'RAG_KB_V1', '中考作文：题目给出的中文要点必须完整覆盖，并用简单准确的英文表达。每个要点可以扩展一小句原因、感受或例子。优先保证不漏点、时态正确、句子完整，再追求高级表达。', '1dc7be68723a094f5c9768a8c6b524be27dcb952c85053030bd8e891f3992161', '{"skillTag":"zhongkao_key_points","levelTag":"JUNIOR_ZHONGKAO","documentType":"EXAM_STRATEGY","essayType":"JUNIOR_ZHONGKAO"}'),
    ('EXAM_STRATEGY', 'CET 议论文结构', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', 'CET4', 'cet_argument_structure', 'CET', 'RAG_KB_V1', 'CET 议论文：开头明确立场或现象，中间用 two or three reasons 展开，并配合例子或解释，结尾总结观点并提出建议。保持 academic but clear 的语气，避免只背模板而缺少针对题目的论证。', '810667566928837220f4ca04d039db0074c2dbb1f1c89ef41b3fd23a9fd1e377', '{"skillTag":"cet_argument_structure","levelTag":"CET","documentType":"EXAM_STRATEGY","essayType":"CET4"}'),
    ('EXAM_STRATEGY', 'IELTS Task 2 Task Response', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', 'IELTS_TASK_2', 'ielts_task2_response', 'IELTS_TASK_2', 'RAG_KB_V1', 'IELTS Task 2：Task Response requires a clear position, full coverage of all parts of the question, and relevant developed ideas. Do not memorize a generic essay. Address key words in the prompt, organize paragraphs around main ideas, and make examples directly support your position.', 'b7d1467a99ed1720e69e16844fde33f15de1420b514192d058b341cb479f55e2', '{"skillTag":"ielts_task2_response","levelTag":"IELTS_TASK_2","documentType":"EXAM_STRATEGY","essayType":"IELTS_TASK_2"}'),
    ('EXAM_STRATEGY', 'TOEFL Independent 观点展开', 'INTERNAL_SEED', '内置 RAG 知识卡 V1', 'TOEFL_INDEPENDENT', 'toefl_independent_development', 'TOEFL_INDEPENDENT', 'RAG_KB_V1', 'TOEFL Independent writing：Choose a clear preference or opinion, then develop it with specific reasons and personal examples. Each body paragraph should connect example details to the claim. Fluency, coherence and relevant support matter more than using rare vocabulary.', '681b7af8045b0e2bef2803d9c42979394110b6b56a71ab05f8ec56f3b5a679d4', '{"skillTag":"toefl_independent_development","levelTag":"TOEFL_INDEPENDENT","documentType":"EXAM_STRATEGY","essayType":"TOEFL_INDEPENDENT"}')
), inserted_documents AS (
    INSERT INTO rag_documents (
        document_type,
        title,
        source_type,
        source_title,
        essay_type,
        skill_tag,
        level_tag,
        version,
        is_active
    )
    SELECT
        document_type,
        title,
        source_type,
        source_title,
        essay_type,
        skill_tag,
        level_tag,
        version,
        TRUE
    FROM seed_cards
    RETURNING id, document_type, title, skill_tag, version
)
INSERT INTO rag_chunks (
    document_id,
    chunk_no,
    content,
    content_hash,
    metadata_json,
    is_active
)
SELECT
    inserted_documents.id,
    1,
    seed_cards.content,
    seed_cards.content_hash,
    seed_cards.metadata_json,
    TRUE
FROM seed_cards
JOIN inserted_documents
    ON inserted_documents.document_type = seed_cards.document_type
    AND inserted_documents.title = seed_cards.title
    AND inserted_documents.skill_tag = seed_cards.skill_tag
    AND inserted_documents.version = seed_cards.version;
