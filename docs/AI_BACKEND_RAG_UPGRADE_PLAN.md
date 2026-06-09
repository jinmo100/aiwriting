# 修改计划文档：AI 应用后端增强与 RAG Feedback MVP

更新时间：2026-06-09

> Comet 设计阶段更新：已确认采用方案 B，先抽轻量通用 `background_jobs`，由 `RAG_INDEX` / `RAG_FEEDBACK` handler 承载索引与反馈生成任务；不再新增 `rag_index_jobs` 专用任务表。

## 1. 修改目标

项目目标从“AI 作文评分系统”升级为：

> **生产级 AI 应用后端项目：在现有动态 Rubric 评分链路基础上，补齐 RAG 教学反馈、管理员索引运维、Embedding 用户配置、pgvector 部署、LLM Eval、可靠任务和安全治理。**

面向岗位：

```text
3 年以上 Java 后端 -> AI 应用后端
```

第一阶段优先落地：

```text
RAG Feedback MVP
```

核心价值：

```text
评分标准由 DB Rubric 保证确定性。
RAG 不参与打分，只用于检索语法/写作知识卡，增强教学反馈解释性。
```

## 2. 已确认的 RAG V1 决策

### 2.1 做什么

- 新增独立 `embedding_configs`，由用户自己配置 Embedding Provider。
- 第一版只支持 OpenAI-compatible `/v1/embeddings`。
- 知识库先由 Flyway seed 约 30 条知识卡。
- 使用 PostgreSQL + pgvector 做向量检索。
- 用户手动触发索引，不自动消耗用户 Embedding Key。
- 同一知识卡按用户/embeddingConfig 分别索引。
- 检索方式：metadata filter + vector topK。
- RAG feedback 是评分完成后的独立异步任务，不阻塞主评分链路。
- RAG feedback 生成复用本次评分使用的 Chat Provider。
- Query 从评分结果自动构造，用户不手写 query。
- Feedback 保存结构化 JSON。
- Citations 对用户暴露短片段、标题、类型和相关原因。
- 管理员接口 V1 只做索引运维，不做知识卡 CRUD。
- 先新增轻量通用 `background_jobs`，RAG 索引与反馈生成分别作为 `RAG_INDEX` / `RAG_FEEDBACK` 任务 handler。

### 2.2 不做什么

- 不用 RAG 覆盖评分 Rubric。
- 不做范文库。
- 不做知识卡 CRUD。
- 不做复杂 hybrid search。
- 不把 embedding 向量写进 Flyway migration。
- 不在用户未确认时自动构建索引。
- 不把真实 API Key 写入仓库或文档。

## 3. 目标架构

```text
用户提交作文
  -> EssayService 创建 SCORING
  -> 主评分异步完成
  -> 保存 RubricScoringResult
  -> 用户点击生成知识点增强反馈
  -> 检查默认 Embedding 配置和索引状态
  -> 若无索引：提示用户手动构建知识索引
  -> 若有索引：RagRetrievalService 检索知识卡
  -> RagFeedbackService 复用本次评分 Chat Provider 生成结构化 feedback
  -> 保存 rag_feedbacks + rag_feedback_citations
  -> 结果页展示 RAG feedback 与引用知识点
```

## 4. pgvector 部署计划

### 4.1 Docker release

当前 release compose 使用：

```yaml
image: postgres:16-alpine
```

RAG 阶段建议替换为：

```yaml
image: pgvector/pgvector:pg16
```

Flyway migration 中执行：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 4.2 VPS 已有 PostgreSQL

如果 VPS 使用宿主机 PostgreSQL，需要先安装 pgvector 扩展，再在业务数据库中执行：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

注意事项：

- 数据库用户需要有创建 extension 权限，或由超级用户预先创建。
- 部署文档必须说明 pgvector 安装步骤。
- 若 embedding 维度为 3072，需要确认 pgvector 索引能力；V1 优先建议使用 1536 维输出。

### 4.3 Embedding 维度建议

第一版建议：

```text
model: text-embedding-3-large
requested dimensions: 1536
```

原因：

- 1536 维向量更省存储和索引成本。
- 更容易兼容 pgvector ANN 索引。
- 如果 OpenAI-compatible router 不支持 `dimensions` 参数，再退回默认维度并调整表结构/索引策略。

## 5. 数据库修改计划

新增 Flyway：

```text
src/main/resources/db/migration/V11__rag_knowledge_base.sql
```

### 5.1 `embedding_configs`

用途：用户独立配置 Embedding Provider。

字段建议：

```text
id BIGSERIAL PRIMARY KEY
owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
config_name VARCHAR(100) NOT NULL
provider_type VARCHAR(50) NOT NULL              -- V1: OPENAI_EMBEDDINGS
provider_label VARCHAR(100)
base_url VARCHAR(500) NOT NULL                  -- 例如 https://example.com/v1
api_key_encrypted TEXT
model_name VARCHAR(160) NOT NULL
dimensions INTEGER NOT NULL                    -- 建议 1536
timeout_seconds INTEGER NOT NULL DEFAULT 60
input_token_price_per_million DECIMAL(12,6)
currency VARCHAR(12)
 is_default BOOLEAN NOT NULL DEFAULT FALSE
 last_test_status VARCHAR(30)
 last_test_error_code VARCHAR(80)
 last_test_message TEXT
 last_test_latency_ms INTEGER
 last_tested_at TIMESTAMP
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
```

索引：

```sql
CREATE INDEX idx_embedding_configs_owner_created_at ON embedding_configs(owner_user_id, created_at DESC);
CREATE UNIQUE INDEX ux_embedding_configs_owner_default
  ON embedding_configs(owner_user_id)
  WHERE is_default = true;
```

### 5.2 `rag_documents`

用途：知识卡文档元数据。

字段建议：

```text
id BIGSERIAL PRIMARY KEY
document_type VARCHAR(50) NOT NULL              -- GRAMMAR_KNOWLEDGE / WRITING_SKILL / EXAM_STRATEGY
title VARCHAR(200) NOT NULL
source_type VARCHAR(50) NOT NULL                -- INTERNAL_SEED
source_title VARCHAR(200)
essay_type VARCHAR(50)                          -- null 表示通用
skill_tag VARCHAR(80) NOT NULL
level_tag VARCHAR(50)
version VARCHAR(50) NOT NULL DEFAULT 'RAG_KB_V1'
is_active BOOLEAN NOT NULL DEFAULT TRUE
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
```

### 5.3 `rag_chunks`

用途：具体可检索知识片段。

字段建议：

```text
id BIGSERIAL PRIMARY KEY
document_id BIGINT NOT NULL REFERENCES rag_documents(id) ON DELETE CASCADE
chunk_no INTEGER NOT NULL
content TEXT NOT NULL
content_hash VARCHAR(64) NOT NULL
metadata_json TEXT
is_active BOOLEAN NOT NULL DEFAULT TRUE
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
```

索引：

```sql
CREATE UNIQUE INDEX ux_rag_chunks_document_chunk_no ON rag_chunks(document_id, chunk_no);
CREATE INDEX idx_rag_chunks_active ON rag_chunks(is_active);
```

### 5.4 `rag_chunk_embeddings`

用途：按用户/Embedding 配置保存知识卡向量。

字段建议：

```text
id BIGSERIAL PRIMARY KEY
user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
embedding_config_id BIGINT NOT NULL REFERENCES embedding_configs(id) ON DELETE CASCADE
chunk_id BIGINT NOT NULL REFERENCES rag_chunks(id) ON DELETE CASCADE
embedding_model VARCHAR(160) NOT NULL
embedding_dimension INTEGER NOT NULL
embedding_version VARCHAR(80) NOT NULL
content_hash VARCHAR(64) NOT NULL
embedding_vector vector(1536) NOT NULL
indexed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
```

索引：

```sql
CREATE UNIQUE INDEX ux_rag_embeddings_user_config_chunk_version
  ON rag_chunk_embeddings(user_id, embedding_config_id, chunk_id, embedding_version);

CREATE INDEX idx_rag_embeddings_user_config
  ON rag_chunk_embeddings(user_id, embedding_config_id);
```

向量索引建议后续压测后选择：

```sql
-- pgvector HNSW 示例，具体 opclass 依赖距离函数选择
CREATE INDEX idx_rag_embeddings_vector_hnsw
  ON rag_chunk_embeddings USING hnsw (embedding_vector vector_cosine_ops);
```

### 5.5 `background_jobs`

用途：轻量通用后台任务表，统一承载 RAG 索引和 RAG Feedback 生成任务。

字段建议：

```text
id BIGSERIAL PRIMARY KEY
job_type VARCHAR(50) NOT NULL                  -- RAG_INDEX / RAG_FEEDBACK
owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
requested_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL
status VARCHAR(30) NOT NULL                    -- PENDING/RUNNING/COMPLETED/FAILED/SKIPPED
business_key VARCHAR(200) NOT NULL
payload_json TEXT
result_json TEXT
error_code VARCHAR(80)
error_message TEXT
attempt_count INTEGER NOT NULL DEFAULT 0
max_attempts INTEGER NOT NULL DEFAULT 3
run_after TIMESTAMP DEFAULT CURRENT_TIMESTAMP
locked_by VARCHAR(120)
locked_until TIMESTAMP
started_at TIMESTAMP
finished_at TIMESTAMP
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
```

索引建议：

```sql
CREATE UNIQUE INDEX ux_background_jobs_active_business
  ON background_jobs(job_type, owner_user_id, business_key)
  WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX idx_background_jobs_claim
  ON background_jobs(status, run_after, locked_until, created_at);
```

### 5.6 `rag_feedbacks`

用途：评分后的 RAG 教学反馈业务结果；任务状态由 `background_jobs` 管理。

字段建议：

```text
id BIGSERIAL PRIMARY KEY
user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
essay_id BIGINT NOT NULL REFERENCES essays(id) ON DELETE CASCADE
score_id BIGINT NOT NULL REFERENCES essay_scores(id) ON DELETE CASCADE
api_config_id BIGINT REFERENCES api_configs(id) ON DELETE SET NULL
embedding_config_id BIGINT REFERENCES embedding_configs(id) ON DELETE SET NULL
job_id BIGINT REFERENCES background_jobs(id) ON DELETE SET NULL
query_text TEXT
retrieved_chunk_ids TEXT
feedback_json TEXT
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
```

唯一约束建议：

```sql
CREATE UNIQUE INDEX ux_rag_feedback_score_config
  ON rag_feedbacks(score_id, embedding_config_id)
  WHERE embedding_config_id IS NOT NULL;
```

### 5.7 `rag_feedback_citations`

用途：RAG feedback 的引用知识点。

字段建议：

```text
id BIGSERIAL PRIMARY KEY
feedback_id BIGINT NOT NULL REFERENCES rag_feedbacks(id) ON DELETE CASCADE
chunk_id BIGINT REFERENCES rag_chunks(id) ON DELETE SET NULL
source_title VARCHAR(200)
source_type VARCHAR(50)
snippet TEXT
relevance_score DOUBLE PRECISION
rank_no INTEGER NOT NULL
reason TEXT
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
```

## 6. 知识卡 seed 计划

总量：约 30 条。

### 6.1 语法类 15 条

```text
主谓一致
时态一致
冠词使用
介词搭配
名词单复数
代词指代
句子残缺
逗号拼接
从句连接
非谓语动词
比较结构
被动语态
拼写与词形
中式英语表达
标点与大小写
```

### 6.2 写作类 10 条

```text
段落主题句
论点展开
例证支持
连接词使用
总分总结构
结尾总结
任务回应
语气与对象
词汇多样性
句式多样性
```

### 6.3 考试类型类 5 条

```text
高考书信/邮件任务完成
中考要点覆盖
CET 议论文结构
IELTS Task 2 Task Response
TOEFL Independent 观点展开
```

## 7. 后端模块计划

### 7.1 Embedding 配置模块

新增包建议：

```text
src/main/java/com/jinmo/essayevaluator/embedding/
```

核心类：

```text
EmbeddingConfigService
EmbeddingConfigController
EmbeddingConfigMapper
EmbeddingConfig entity/dto
EmbeddingProviderType
EmbeddingClient
OpenAiCompatibleEmbeddingClient
EmbeddingTestService
```

API：

```text
POST   /api/embedding-configs
GET    /api/embedding-configs
GET    /api/embedding-configs/{id}
PUT    /api/embedding-configs/{id}
DELETE /api/embedding-configs/{id}
PUT    /api/embedding-configs/{id}/default
POST   /api/embedding-configs/test
POST   /api/embedding-configs/{id}/test
```

请求示例：

```json
{
  "configName": "我的 Embedding",
  "providerType": "OPENAI_EMBEDDINGS",
  "baseUrl": "https://example.com/v1",
  "apiKey": "只在请求中传输，不在响应中返回",
  "modelName": "text-embedding-3-large",
  "dimensions": 1536,
  "timeoutSeconds": 60,
  "isDefault": true
}
```

安全要求：

- API Key 加密保存，复用或抽象现有 `ApiKeyEncryptionService`。
- 列表/详情只返回 `hasApiKey` 和 `apiKeyPreview`。
- 不记录明文 key。
- 用户只能访问自己的 Embedding 配置。

### 7.2 RAG 索引模块

新增包建议：

```text
src/main/java/com/jinmo/essayevaluator/rag/
```

核心类：

```text
RagIndexingService
RagIndexJobService
RagIndexController
AdminRagIndexController
RagKnowledgeMapper
RagChunkEmbeddingMapper
```

用户 API：

```text
GET  /api/rag/index/my-status
POST /api/rag/index/rebuild-my
```

管理员 API：

```text
GET  /api/admin/rag/index/status
POST /api/admin/rag/index/rebuild
```

用户索引请求：

```json
{
  "embeddingConfigId": 1,
  "force": false,
  "limit": 50
}
```

管理员索引请求：

```json
{
  "userId": 1,
  "embeddingConfigId": 2,
  "force": false,
  "limit": 50
}
```

执行策略：

- V1 可使用独立 `ragTaskExecutor` 异步执行。
- 接口只创建 job 并返回 jobId，不长时间阻塞 HTTP。
- 同一用户同一 embeddingConfig 同时只能有一个 RUNNING/PENDING job。
- 失败保存 `error_code/error_message`。
- 管理员触发用户索引必须记录 `requested_by_user_id`。

### 7.3 RAG 检索模块

核心类：

```text
RagQueryBuilder
RagRetrievalService
RagRetrievedChunk
```

Query 构造规则：

```text
essayType + displayName
低分维度：score/maxScore <= 0.75，最多 3 个
annotations：最多 5 个
summary.priorityImprovements：最多 3 条
taskPrompt 摘要
```

检索规则：

```text
metadata filter：active / essayType 通用或匹配 / documentType / skillTag
vector topK：3~5
距离函数：cosine
```

### 7.4 RAG Feedback 模块

核心类：

```text
RagFeedbackService
RagFeedbackController
RagFeedbackPrompt
RagFeedbackValidator
```

用户 API：

```text
GET  /api/rag/feedbacks/{essayId}
POST /api/rag/feedbacks/{essayId}/generate
POST /api/rag/feedbacks/{essayId}/retry
```

状态：

```text
PENDING
RETRIEVING
GENERATING
COMPLETED
FAILED
SKIPPED
```

生成策略：

- 不阻塞主评分链路。
- 使用用户本次评分对应的 `api_config_id` 调 Chat Provider。
- 若无默认 Embedding 配置：`SKIPPED`，提示用户配置。
- 若索引不足：`SKIPPED` 或 `FAILED_INDEX_MISSING`，提示用户构建索引。
- 保存 `feedback_json` 和 citations。

Feedback JSON schema：

```json
{
  "overall": "string",
  "items": [
    {
      "title": "string",
      "problem": "string",
      "whyItMatters": "string",
      "howToImprove": "string",
      "example": {
        "before": "string",
        "after": "string"
      },
      "citationIds": [1]
    }
  ],
  "nextPractice": ["string"]
}
```

约束：

```text
overall 必填
items 1~5 条
每个 item 至少绑定 1 个 citation
nextPractice 1~3 条
```

## 8. 管理员能力计划

当前已有：

```text
UserAccountPrincipal.role
CurrentUserService.isAdmin()
```

需要补：

```text
CurrentUserService.requireAdmin()
/api/admin/** 路由保护
管理员操作审计日志
管理员接口限流
敏感操作错误响应统一
```

管理员接口 V1 范围：

```text
只做 RAG 索引状态查看和触发重建
不做知识卡 CRUD
不做用户密钥 reveal
```

后续可扩展：

```text
知识卡版本发布
知识卡编辑审核
索引任务取消
失败任务批量重试
```

## 9. 前端修改计划

### 9.1 新增 Embedding 配置页

页面建议：

```text
frontend/src/views/EmbeddingConfigView.vue
```

功能：

- 新增/编辑/删除 Embedding 配置。
- 测试连接。
- 设置默认配置。
- 显示模型、维度、最近测试状态。
- 提示 API Key 只加密保存，不在响应中返回。

导航：

```text
API 配置
Embedding 配置
提交作文
历史记录
学习看板
```

### 9.2 索引状态与触发

可放在 Embedding 配置页：

```text
知识索引状态：未构建 / 构建中 / 已完成 / 失败
[构建我的知识索引]
```

需要显示：

```text
processedChunks / totalChunks
failedChunks
lastError
updatedAt
```

### 9.3 结果页 RAG Feedback 区块

在评分结果页新增：

```text
知识点增强反馈
```

状态展示：

```text
未配置 Embedding：去配置
未构建索引：构建我的知识索引
生成中：知识点分析中
完成：展示 overall/items/nextPractice/citations
失败：提示不影响评分结果，允许重试
```

引用展示：

```text
引用知识点：
- 主谓一致｜语法知识卡
  “当主语为第三人称单数时，谓语动词需要使用对应形式……”
  相关原因：匹配到作文中的主谓一致错误。
```

不展示：

```text
embedding vector
完整 metadata_json
系统 prompt
过长内部原文
```

## 10. 测试计划

### 10.1 单元测试

- `EmbeddingConfigServiceTest`
  - 用户隔离。
  - 默认配置唯一。
  - API Key preview。
- `OpenAiCompatibleEmbeddingClientTest`
  - 请求体包含 model/input/dimensions。
  - 错误分类。
- `RagQueryBuilderTest`
  - 从低分维度和 annotations 构造 query。
- `RagFeedbackValidatorTest`
  - JSON schema 字段校验。
- `RagIndexJobServiceTest`
  - 重复 job 防护。
  - 失败保存。

### 10.2 集成测试

建议引入 Testcontainers：

```text
PostgreSQL + pgvector
Redis
```

覆盖：

- Flyway V11 可迁移。
- pgvector extension 存在。
- 插入向量并 topK 检索。
- 用户 A/B 的 embedding 索引互不污染。

### 10.3 RAG Eval V1

新增少量 retrieval eval：

```text
query: 主谓一致错误
expected skill_tag: subject_verb_agreement

query: 论点展开不足
expected skill_tag: argument_development
```

指标：

```text
hit@3
hit@5
```

第一版不要求复杂评测，只要能证明检索链路可回归。

## 11. 安全与合规要求

- 不提交真实 API Key。
- 文档中只写配置格式，不写真实值。
- `.env.dev.local` 继续保持 gitignore。
- Embedding API Key 加密保存。
- 用户索引必须显式触发，避免隐式消耗用户额度。
- 管理员触发用户索引必须记录审计字段。
- RAG citations 不暴露内部索引结构。
- 后续补 CSRF 或明确认证策略。

## 12. 实施里程碑

### Milestone 1：数据库和部署基础

范围：

- `V11__rag_knowledge_base.sql`
- pgvector extension
- seed 30 条知识卡
- release compose 切换 pgvector 镜像
- `docs/DEPLOYMENT.md` 增加 pgvector 部署说明

验收：

```powershell
.\gradlew.bat test
```

并在真实 PostgreSQL/pgvector 环境验证 Flyway 迁移。

### Milestone 2：Embedding 配置后端

范围：

- `embedding_configs` entity/mapper/dto/service/controller
- OpenAI-compatible embedding test
- 默认配置和用户隔离
- API Key 加密与 preview

验收：

```text
用户可新增 embedding 配置
用户可测试连接
用户可设置默认配置
用户不可访问他人配置
```

### Milestone 3：RAG 索引任务

范围：

- `background_jobs`
- `RAG_INDEX` handler
- 用户触发索引
- 管理员触发索引
- 批量 embedding 写入 `rag_chunk_embeddings`
- 索引状态查询

验收：

```text
用户手动触发后生成向量
重复触发受控
失败可见
管理员接口需要 ADMIN
```

### Milestone 4：RAG 检索和 Feedback

范围：

- Query builder
- vector topK retrieval
- citations 保存
- feedback 结构化生成
- retry/failed/skipped 状态

验收：

```text
评分完成后可手动生成 RAG feedback
无 Embedding 配置时提示 SKIPPED
无索引时提示先构建索引
有索引时生成结构化 feedback + citations
```

### Milestone 5：前端展示

范围：

- Embedding 配置页
- 索引状态页块
- 结果页 RAG feedback 区块

验收：

```powershell
cd frontend
npm run build
```

并通过浏览器手工验证主流程。

### Milestone 6：LLM Eval 与可靠任务后续增强

范围：

- retrieval eval
- Provider 回放评分 eval
- reliable background jobs
- Actuator/Micrometer/Prometheus
- CSRF/reveal 安全修复

## 13. 风险与取舍

### 13.1 用户自配 Embedding 的风险

风险：

- 不同用户向量空间不兼容。
- 索引成本由用户承担，必须显式确认。
- 用户换模型后旧索引不可复用。

应对：

- `rag_chunk_embeddings` 按 user/config/version 隔离。
- 手动触发索引。
- 记录 embedding model/dimension/version/contentHash。
- 提供重建索引入口。

### 13.2 pgvector 运维风险

风险：

- VPS PostgreSQL 未安装 pgvector。
- 应用数据库用户无创建 extension 权限。
- 高维向量索引限制。

应对：

- Docker release 使用 pgvector 镜像。
- 部署文档明确宿主机安装步骤。
- V1 优先 1536 维。

### 13.3 RAG 生成质量风险

风险：

- 检索命中不准。
- LLM 生成未引用知识卡。
- RAG 失败影响用户体验。

应对：

- RAG 不阻塞主评分链路。
- feedback 每项必须绑定 citation。
- retrieval eval 固化常见 query。
- 失败状态友好展示，不影响评分结果。

## 14. 推荐优先级总表

```text
P0：安全修复
  - allowApiKeyReveal 真正生效
  - /api/admin/** 权限保护
  - CSRF 或认证策略修复

P1：RAG Feedback MVP
  - pgvector
  - embedding_configs
  - user-level index
  - retrieval
  - structured feedback

P1：LLM Eval
  - scoring replay
  - drift report
  - repair rate
  - cost/latency

P2：可靠异步任务
  - lease
  - stuck recovery
  - retry backoff
  - multi-instance safe

P2：可观测性
  - Actuator
  - Micrometer
  - Prometheus
  - traceId
```

## 15. 面试表达中的计划总结

> 下一阶段我会把项目从“LLM 评分链路”升级为“LLM + RAG 的教学反馈平台”。评分仍由 DB Rubric 保证确定性，RAG 只作为教学反馈增强层。由于当前产品是 BYOK 模式，Embedding 也由用户自配，用户手动确认构建索引，平台不隐式消耗用户额度。索引按 user/config/version 隔离，检索使用 metadata filter + vector topK，反馈生成复用用户本次评分 Provider，并保存结构化 JSON 和 citations。这个改造能体现 AI 应用后端中的模型配置、向量索引、异步任务、权限安全、成本边界和可解释性设计。

