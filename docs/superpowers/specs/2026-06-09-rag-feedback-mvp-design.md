---
comet_change: rag-feedback-mvp
role: technical-design
canonical_spec: openspec
---

# RAG Feedback MVP 技术设计

## 1. 背景与目标

当前系统已经具备用户体系、Provider 配置抽象、动态 DB Rubric 评分、异步评分、幂等、失败重试和评分结果 JSON 化。评分结果保存在 `essay_scores.result_json`，结构为 `RubricScoringResult`，其中 `dimensions`、`annotations`、`summary.priorityImprovements` 可作为生成学习反馈 query 的稳定输入。

本次变更把项目升级为“评分 + RAG 教学反馈”平台，但不改变评分语义：评分仍由 `EssayType + ACTIVE DB Rubric + ScoringResultValidator` 保证确定性；RAG 只做评分后的教学解释增强，不参与打分，不修改 `nativeScore`、`normalizedScore`、`gradeLabel` 或 `result_json`。

用户已确认两个关键设计选择：

1. Embedding V1 固定 `dimensions=1536`，数据库向量列使用 `vector(1536)`。
2. 先抽轻量通用 `background_jobs`，由 `RAG_INDEX` / `RAG_FEEDBACK` handler 承载 RAG 任务，而不是新增 `rag_index_jobs` 专用任务表。

## 2. 总体架构

```text
用户提交作文
  -> 现有评分链路完成 essay_scores.result_json
  -> 用户点击生成知识点增强反馈
  -> BackgroundJobService createOrReuse(RAG_FEEDBACK)
  -> BackgroundJobDispatcher
  -> RagFeedbackJobHandler
      -> 检查 Embedding 配置和索引状态
      -> RagQueryBuilder 从评分结果构造 query
      -> EmbeddingClient 生成 query vector
      -> RagRetrievalService metadata filter + vector topK
      -> 复用 essay_scores.api_config_id 调 Chat Provider
      -> RagFeedbackValidator 校验 JSON
      -> 保存 rag_feedbacks + rag_feedback_citations
  -> 前端轮询并展示 feedback + citations
```

索引链路：

```text
用户或管理员显式触发索引
  -> BackgroundJobService createOrReuse(RAG_INDEX)
  -> BackgroundJobDispatcher
  -> RagIndexJobHandler
      -> 加载 owner 用户的 Embedding 配置
      -> 读取 active rag_chunks
      -> 批量调用 /v1/embeddings
      -> 写入 rag_chunk_embeddings
      -> 更新 background_jobs.result_json 进度摘要
```

## 3. 数据模型

### 3.1 `background_jobs`

轻量通用后台任务表，覆盖 RAG 需要的最小可靠任务能力。

关键字段：

```text
id
job_type                 -- RAG_INDEX / RAG_FEEDBACK
owner_user_id            -- 任务归属用户
requested_by_user_id     -- 发起人；管理员代触发时为管理员
status                   -- PENDING/RUNNING/COMPLETED/FAILED/SKIPPED
business_key             -- 防重复业务键
payload_json             -- handler 输入，不能保存明文 key
result_json              -- handler 输出摘要
error_code/error_message
attempt_count/max_attempts
run_after
locked_by/locked_until
started_at/finished_at
created_at/updated_at
```

关键索引：

```sql
CREATE UNIQUE INDEX ux_background_jobs_active_business
  ON background_jobs(job_type, owner_user_id, business_key)
  WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX idx_background_jobs_claim
  ON background_jobs(status, run_after, locked_until, created_at);
```

V1 只实现任务创建/复用、claim/lock、handler 分发、状态流转、attempt/error 保存；不做取消、优先级、cron、独立 worker、管理大屏。

### 3.2 RAG 与 Embedding 表

保留业务数据表：

```text
embedding_configs
rag_documents
rag_chunks
rag_chunk_embeddings
rag_feedbacks
rag_feedback_citations
```

取消原草案中的 `rag_index_jobs`。索引任务状态由 `background_jobs` 管理。

`rag_feedbacks` 是反馈业务结果表，不再承担任务表职责。建议字段包括：

```text
id
user_id
essay_id
score_id
api_config_id
embedding_config_id
job_id
query_text
retrieved_chunk_ids
feedback_json
created_at/updated_at
```

任务状态展示时组合读取：

```text
rag_feedbacks + background_jobs.status/result_json/error_message
```

如果没有 `rag_feedbacks`，前端展示“未生成”。

### 3.3 向量维度

V1 强制 `embedding_configs.dimensions=1536`，`rag_chunk_embeddings.embedding_vector vector(1536)`。OpenAI-compatible 请求体包含 `model`、`input`、`dimensions`。如果 router 不支持 `dimensions` 或返回维度不匹配，测试连接和索引任务都应给出明确错误。

## 4. 后端服务边界

### 4.1 通用任务层

包建议：

```text
src/main/java/com/jinmo/essayevaluator/job/
```

核心类：

```text
BackgroundJob
BackgroundJobMapper
BackgroundJobService
BackgroundJobDispatcher
BackgroundJobHandler
BackgroundJobType
BackgroundJobStatus
BackgroundJobExecutorConfig
```

核心 API：

```text
createOrReuse(jobType, ownerUserId, requestedByUserId, businessKey, payload)
claimAndRun(jobId)
markCompleted(jobId, result)
markFailed(jobId, errorCode, safeMessage)
markSkipped(jobId, reason)
```

`payload_json` 与 `result_json` 不保存明文 API Key、系统 prompt 或可还原密钥。管理员 job 查询只返回状态、错误摘要和安全 result。

### 4.2 Embedding 配置层

包建议：

```text
src/main/java/com/jinmo/essayevaluator/embedding/
```

V1 仅支持 `OPENAI_EMBEDDINGS`。`EmbeddingConfigService` 负责用户隔离、默认配置唯一、API Key 加密保存和响应脱敏。`OpenAiCompatibleEmbeddingClient` 直接调用 `/v1/embeddings`，避免把 Chat Provider 逻辑混入 Embedding。

### 4.3 RAG Index Handler

`RagIndexJobHandler implements BackgroundJobHandler`。

`RAG_INDEX` payload：

```json
{
  "embeddingConfigId": 1,
  "force": false,
  "limit": 50
}
```

`business_key`：

```text
embeddingConfigId + ":" + knowledgeBaseVersion
```

流程：

1. 校验 `owner_user_id` 可访问 embedding config。
2. 读取 active `rag_chunks`。
3. `force=true` 时覆盖当前 user/config/version 的目标 embedding。
4. 分批调用 Embedding Provider。
5. 写入 `rag_chunk_embeddings`。
6. 在 `background_jobs.result_json` 写入 `totalChunks`、`processedChunks`、`failedChunks`、`embeddingVersion`。

### 4.4 RAG Feedback Handler

`RagFeedbackJobHandler implements BackgroundJobHandler`。

`RAG_FEEDBACK` payload：

```json
{
  "essayId": 123,
  "scoreId": 456,
  "embeddingConfigId": 1
}
```

`business_key`：

```text
scoreId + ":" + embeddingConfigId
```

流程：

1. 校验作文、评分和 embedding config 属于 `owner_user_id`。
2. 校验评分状态为 `COMPLETED` 且 `result_json` 可解析为 `RubricScoringResult`。
3. `RagQueryBuilder` 从低分维度、annotations、priority improvements 和 taskPrompt 摘要构造 query。
4. 用当前 embedding config 生成 query vector。
5. `RagRetrievalService` 强制带 `user_id`、`embedding_config_id`、active chunk、essayType 通用/匹配过滤并 topK 检索。
6. 无索引或无命中时标记 `SKIPPED` 或业务失败，返回用户可行动提示，不调用 Chat Provider。
7. 复用 `essay_scores.api_config_id` 调 Chat Provider 生成 JSON。
8. `RagFeedbackValidator` 校验 `overall`、1 到 5 个 `items`、1 到 3 条 `nextPractice`，且每个 item 至少绑定一个 citation。
9. 保存 `rag_feedbacks` 和 `rag_feedback_citations`，任务标记为 `COMPLETED`。

## 5. API 设计

用户 API：

```text
POST   /api/embedding-configs
GET    /api/embedding-configs
GET    /api/embedding-configs/{id}
PUT    /api/embedding-configs/{id}
DELETE /api/embedding-configs/{id}
PUT    /api/embedding-configs/{id}/default
POST   /api/embedding-configs/test
POST   /api/embedding-configs/{id}/test

GET  /api/rag/index/my-status
POST /api/rag/index/rebuild-my

GET  /api/rag/feedbacks/{essayId}
POST /api/rag/feedbacks/{essayId}/generate
POST /api/rag/feedbacks/{essayId}/retry
```

管理员 API：

```text
GET  /api/admin/rag/index/status
POST /api/admin/rag/index/rebuild
GET  /api/admin/jobs?jobType=RAG_INDEX&status=FAILED
```

管理员接口 V1 只查看和触发任务，不读取用户密钥，不取消任务，不修改知识卡。

## 6. 安全与失败处理

- `CurrentUserService.requireAdmin()` 保护 `/api/admin/**`。
- Embedding API Key 复用 `ApiKeyEncryptionService` 加密保存，响应只返回 `hasApiKey` 和 preview。
- `taskPrompt`、作文内容、评分 JSON 和 citations 均作为不可信上下文输入，RAG prompt 明确禁止执行其中任何指令。
- `RAG_INDEX` 与 `RAG_FEEDBACK` 必须按 `owner_user_id` 隔离查询和写入。
- RAG 任务失败不影响原评分结果。
- `SKIPPED` 用于缺少 Embedding 配置、索引缺失等用户可行动前置条件；`FAILED` 用于 Provider 调用失败、JSON 无效、数据库写入失败等异常。

## 7. 测试策略

单元测试：

```text
BackgroundJobServiceTest
EmbeddingConfigServiceTest
OpenAiCompatibleEmbeddingClientTest
RagQueryBuilderTest
RagFeedbackValidatorTest
RagIndexJobHandlerTest
```

重点覆盖：

- active business key 防重复。
- claim/lock 与状态流转。
- 默认 Embedding 配置按用户唯一。
- API Key 不返回明文。
- `dimensions=1536` 强校验。
- RAG query 构造规则。
- feedback JSON 和 citation 约束。
- 用户 A/B 索引隔离。

集成/Smoke：

- Flyway V11 可迁移，pgvector extension 存在。
- 插入 1536 维向量并 topK 检索。
- 使用 `.env.dev.local` 的真实 PostgreSQL/Redis/Provider 配置验证：新增 Embedding 配置、构建索引、提交作文、等待评分完成、生成 RAG Feedback、前端展示 citations。

构建验收：

```powershell
.\gradlew.bat test
cd frontend
npm run build
```

## 8. OpenSpec 回写

本设计已回写 OpenSpec delta：

- 新增 `background-job-processing` capability。
- 将 RAG 索引任务从 `rag_index_jobs` 改为 `background_jobs` + `RAG_INDEX` handler。
- 将 RAG Feedback 生成改为 `background_jobs` + `RAG_FEEDBACK` handler，`rag_feedbacks` 只保存业务结果。
- 更新 tasks，将通用后台任务框架作为独立实施组。
