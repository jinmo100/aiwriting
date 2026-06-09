# Comet Design Handoff

- Change: rag-feedback-mvp
- Phase: design
- Mode: compact
- Context hash: f353e4ebb7a7bd9895028be002a1c2bf6670309329253fcfbc0f184522d47a27

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/rag-feedback-mvp/proposal.md

- Source: openspec/changes/rag-feedback-mvp/proposal.md
- Lines: 1-39
- SHA256: 4940315d6a70a919c4349d1dd394f3310d22b12e194031d0a38b58a8bdf2d3d0

```md
## Why

现有系统已经具备动态 DB Rubric 评分链路，但评分结果主要停留在维度分、批注和总体反馈层面，缺少可追溯的知识点解释与后续练习建议。下一阶段需要把项目从“AI 作文评分系统”升级为更接近生产级 AI 应用后端的“评分 + RAG 教学反馈”平台，同时保留 DB Rubric 对评分确定性的控制。

RAG Feedback MVP 的目标是：评分仍由现有 Rubric 链路负责，RAG 只作为评分完成后的独立教学反馈增强层，通过用户自配 Embedding、知识卡索引、向量检索和 citations，生成可解释、可失败隔离、可运维的学习反馈。

## What Changes

- 新增用户维度的 Embedding 配置能力，第一版支持 OpenAI-compatible `/v1/embeddings`，API Key 加密保存且响应不返回明文。
- 新增 RAG 知识库基础表、知识卡 seed、pgvector 向量存储和用户/Embedding 配置维度的知识卡索引。
- 新增轻量通用 `background_jobs` 任务框架，用于承载 RAG 索引和 RAG Feedback 生成任务，具备防重复、锁、attempt、失败记录和后续多实例恢复基础。
- 新增用户手动触发的知识索引任务，以及管理员受保护的索引状态查看和代触发重建能力。
- 新增从评分结果构造 RAG query、按 essayType/metadata 过滤并执行 vector topK 检索的能力。
- 新增评分后手动生成 RAG 教学反馈的异步流程，保存结构化 `feedback_json` 和 citations；失败或缺配置时不影响主评分结果。
- 新增前端 Embedding 配置页、知识索引状态入口和作文结果页 RAG Feedback 展示区块。
- 更新 Docker/release 部署和文档，明确 pgvector 要求；Flyway migration 包含中文表/字段注释。

## Capabilities

### New Capabilities

- `embedding-config-management`: 用户管理 OpenAI-compatible Embedding 配置、测试连接、设置默认配置，并确保密钥安全和用户隔离。
- `background-job-processing`: 系统提供轻量通用后台任务框架，统一承载 RAG 索引和反馈生成任务。
- `rag-knowledge-indexing`: 系统维护内置知识卡，用户或管理员显式触发知识索引任务，并按 user/config/version 隔离向量索引。
- `rag-feedback-generation`: 评分完成后基于评分结果检索知识卡并生成结构化教学反馈，反馈包含 citations 且不参与评分。
- `rag-feedback-frontend`: 前端展示 Embedding 配置、索引状态和作文结果页的 RAG 教学反馈。

### Modified Capabilities

- 无。现有作文评分、Rubric 计算、历史详情和 Provider Chat 配置的核心评分语义不改变；RAG 作为独立后置增强链路接入。

## Impact

- 后端：新增 `embedding`、`rag` 相关 service/controller/mapper/entity/dto/prompt/validator；补充 `CurrentUserService.requireAdmin()` 和 `/api/admin/rag/**` 权限边界。
- 数据库：新增 Flyway migration `V11__rag_knowledge_base.sql`，创建 `background_jobs`、`embedding_configs`、`rag_documents`、`rag_chunks`、`rag_chunk_embeddings`、`rag_feedbacks`、`rag_feedback_citations` 等表，并启用 pgvector。
- 异步任务：新增通用后台任务 executor/dispatcher/handler，RAG 索引与反馈生成以 job handler 形式运行，不阻塞现有评分 executor。
- AI Provider：复用现有 Chat Provider 生成反馈；新增 OpenAI-compatible Embedding 调用客户端。
- 前端：新增 Embedding 配置页、导航入口、索引状态组件和评分结果页 RAG Feedback 区块。
- 部署/测试：release PostgreSQL 镜像切换到 pgvector 版本；文档补充宿主机 pgvector 安装说明；新增后端单测/集成测试和前端 build 验收。
```

## openspec/changes/rag-feedback-mvp/design.md

- Source: openspec/changes/rag-feedback-mvp/design.md
- Lines: 1-196
- SHA256: ab5d99089513db7db8a650ed675c19f5c257cb04f7d08ec7b53eb4cda8d14764

[TRUNCATED]

```md
## Context

当前项目已完成用户体系、Provider 配置抽象、动态 Rubric 评分、异步评分、评分失败重试和作文版本链。评分结果保存在 `essay_scores.result_json`，结构为 `RubricScoringResult`，包含 `dimensions`、`annotations`、`summary.priorityImprovements` 等可用于构造学习反馈 query 的信息。

现有系统的关键约束：

- 评分确定性由 `EssayType + ACTIVE DB Rubric + ScoringResultValidator` 保证，旧四维字段已废除，不能恢复。
- `taskPrompt` 和 `essayContent` 均是不可信用户输入，进入 prompt 前必须隔离；RAG 反馈同样不能把用户输入当系统指令。
- 用户 API 配置采用 BYOK 模式，API Key 已有 `ApiKeyEncryptionService` AES-GCM 加密能力；Embedding Key 应复用同一安全边界。
- 评分是异步主流程，RAG 必须作为评分完成后的独立后置任务，不能阻塞提交作文和评分结果查询。
- 当前 `SecurityConfig` 只要求登录，已有 `CurrentUserService.isAdmin()` 但缺少强制管理员方法；管理员 RAG 运维接口必须补显式权限保护。

目标数据流：

```text
用户提交作文
  -> 现有评分链路完成 essay_scores.result_json
  -> 用户点击生成知识点增强反馈
  -> BackgroundJobService 创建/复用 RAG_FEEDBACK job
  -> BackgroundJobDispatcher 分发到 RagFeedbackJobHandler
  -> 检查默认 Embedding 配置和索引状态
  -> RagQueryBuilder 从评分结果构造 query
  -> OpenAI-compatible Embedding Client 生成 query vector
  -> RagRetrievalService metadata filter + vector topK
  -> RagFeedbackService 复用本次评分 api_config_id 对应 Chat Provider
  -> RagFeedbackValidator 校验结构化 JSON
  -> 保存 rag_feedbacks + rag_feedback_citations
  -> 前端展示反馈和引用知识点
```

## Goals / Non-Goals

**Goals:**

- 新增用户可管理的 OpenAI-compatible Embedding 配置，支持测试、默认配置、密钥加密保存和用户隔离。
- 新增 PostgreSQL + pgvector 知识卡向量存储，并通过 Flyway seed 约 30 条英语语法/写作/考试知识卡。
- 新增轻量通用 `background_jobs`，统一承载 RAG 索引与 RAG Feedback 生成任务，具备防重复、锁、attempt 和失败记录。
- 新增用户显式触发的索引任务，按 `user_id + embedding_config_id + embedding_version + content_hash` 隔离向量。
- 新增管理员 RAG 索引运维 API，管理员只能查看/触发索引，不允许读取用户密钥明文。
- 新增评分后手动生成 RAG Feedback 的异步链路，支持 `SKIPPED/FAILED/COMPLETED` 状态和 retry。
- 前端提供 Embedding 配置页、知识索引状态入口和结果页 RAG Feedback 展示。
- 测试覆盖配置隔离、索引任务防重复、query 构造、feedback JSON 校验和基础检索 eval。

**Non-Goals:**

- RAG 不参与打分，不修改 `nativeScore`、`normalizedScore`、Rubric 维度分和评分状态语义。
- V1 不做知识卡 CRUD、审核、版本发布后台；知识卡只由 Flyway seed。
- V1 不做范文库、复杂 hybrid search、reranker 或自动隐式索引。
- V1 不做完整任务平台；`background_jobs` 只覆盖 RAG 所需的 create/reuse、claim/lock、dispatch、attempt、failed/skipped/completed 状态，不做取消、优先级、cron、独立 worker 或管理大屏。
- V1 不向用户暴露 embedding vector、完整 `metadata_json`、系统 prompt 或内部索引结构。

## Decisions

### 1. Embedding 配置独立于 Chat Provider 配置

**决策：** 新增 `embedding_configs`，不复用 `api_configs`。

**原因：**

- Chat completion 和 embedding 的 endpoint、model、价格、维度、测试逻辑不同。
- 用户可能用不同供应商分别处理评分和向量检索。
- 独立表可避免污染现有 Provider Chat 模型缓存和评分配置语义。

**替代方案：** 在 `api_configs` 增加 embedding 字段。该方案短期省表，但会让 Chat 配置响应、默认配置和模型测试逻辑变得模糊，后续难以维护。

### 2. V1 只支持 OpenAI-compatible `/v1/embeddings`

**决策：** `EmbeddingProviderType` 第一版仅实现 `OPENAI_EMBEDDINGS`，请求体包含 `model`、`input` 和可选/配置化 `dimensions`。

**原因：**

- 与当前 BYOK/OpenAI-compatible Provider 生态一致，能快速覆盖大多数 router。
- 降低 LangChain4j embedding 模型适配差异带来的不确定性。

**兼容处理：** 如果某些兼容路由不支持 `dimensions`，测试连接应返回明确错误；后续可通过配置项允许不传 dimensions，但 V1 数据库向量列先固定为 `vector(1536)`。

### 3. pgvector 表结构固定 V1 维度为 1536

**决策：** `rag_chunk_embeddings.embedding_vector vector(1536)`，推荐 `text-embedding-3-large` requested dimensions 1536。

```

Full source: openspec/changes/rag-feedback-mvp/design.md

## openspec/changes/rag-feedback-mvp/tasks.md

- Source: openspec/changes/rag-feedback-mvp/tasks.md
- Lines: 1-68
- SHA256: 2bac3eefd8e0ad674dd91a904a900bd4574016998c344e1902d148a7cc33ce3d

```md
## 1. 数据库与部署基础

- [ ] 1.1 新增 `V11__rag_knowledge_base.sql`，启用 pgvector extension 并创建 `background_jobs`、RAG/Embedding 相关表
- [ ] 1.2 为 V11 新增表、字段、关键约束补充中文 `COMMENT ON TABLE/COLUMN`
- [ ] 1.3 Seed 约 30 条英语语法、写作、考试策略知识卡，不在 migration 中写入向量
- [ ] 1.4 为 `background_jobs` active business key、claim 查询、Embedding 默认配置、RAG chunk/embedding/feedback/citation 增加约束和索引
- [ ] 1.5 将 release Docker PostgreSQL 镜像切换到 `pgvector/pgvector:pg16`
- [ ] 1.6 更新部署文档，说明 Docker 和宿主机 PostgreSQL 的 pgvector 安装/权限要求

## 2. 通用后台任务框架

- [ ] 2.1 新增 `job` 包下 `BackgroundJob` entity、mapper、dto、`BackgroundJobType`、`BackgroundJobStatus`
- [ ] 2.2 实现 `BackgroundJobService.createOrReuse`、active business key 防重复和 payload/result 安全保存
- [ ] 2.3 实现 claim/lock、markCompleted、markFailed、markSkipped、attempt/error/result 状态流转
- [ ] 2.4 实现 `BackgroundJobHandler` SPI 和 `BackgroundJobDispatcher`，按 job type 分发到 handler
- [ ] 2.5 新增 `BackgroundJobExecutorConfig`，与现有评分 executor 隔离
- [ ] 2.6 实现有限管理员 job 查询接口，返回状态、错误摘要和安全 result，不暴露敏感 payload
- [ ] 2.7 补充 `BackgroundJobServiceTest`，覆盖防重复、claim/lock、状态流转和失败保存

## 3. Embedding 配置后端

- [ ] 3.1 新增 `embedding` 包下 entity、dto、mapper、enum 和基础目录结构
- [ ] 3.2 实现 `EmbeddingConfigService` 的创建、列表、详情、更新、删除、默认配置逻辑
- [ ] 3.3 复用 `ApiKeyEncryptionService` 加密保存 Embedding API Key，并在响应中只返回 `hasApiKey` 和 preview
- [ ] 3.4 实现 OpenAI-compatible `EmbeddingClient`，支持 `/v1/embeddings` 请求、1536 维强校验和错误分类
- [ ] 3.5 实现未保存配置和已保存配置的 Embedding 连接测试，并记录最近测试状态
- [ ] 3.6 新增 `EmbeddingConfigController` 暴露 `/api/embedding-configs/**` API
- [ ] 3.7 补充 `EmbeddingConfigServiceTest` 和 `OpenAiCompatibleEmbeddingClientTest`

## 4. RAG 索引任务后端

- [ ] 4.1 新增 `rag` 包下知识库、chunk embedding、索引状态 mapper/entity/dto
- [ ] 4.2 实现 `RagIndexJobHandler`，通过 `RAG_INDEX` background job 执行索引
- [ ] 4.3 实现批量读取 active chunks、调用 EmbeddingClient、写入 `rag_chunk_embeddings`
- [ ] 4.4 在 `background_jobs.result_json` 中记录 totalChunks、processedChunks、failedChunks、embeddingVersion 和错误摘要
- [ ] 4.5 实现用户索引状态查询和 `POST /api/rag/index/rebuild-my`
- [ ] 4.6 新增 `CurrentUserService.requireAdmin()` 并保护 `/api/admin/rag/index/**`
- [ ] 4.7 实现管理员索引状态查询和代用户触发索引，并记录 `requestedByUserId`
- [ ] 4.8 补充 `RagIndexJobHandlerTest` 和用户 A/B 索引隔离测试

## 5. RAG 检索与 Feedback 后端

- [ ] 5.1 新增 `RagQueryBuilder`，从 `RubricScoringResult` 的低分维度、annotations、priorityImprovements 和 taskPrompt 摘要构造 query
- [ ] 5.2 新增 `RagRetrievalService`，实现 metadata filter + pgvector cosine topK 检索
- [ ] 5.3 新增 `RagFeedbackPrompt`，隔离评分 JSON、作文内容、任务要求和 citations 等不可信上下文
- [ ] 5.4 新增 `RagFeedbackValidator`，校验 `overall/items/nextPractice/citationIds` 结构和数量约束
- [ ] 5.5 实现 `RagFeedbackJobHandler`，通过 `RAG_FEEDBACK` background job 执行检索、生成、保存 feedback/citations
- [ ] 5.6 实现 `RagFeedbackController`，暴露查询、生成和重试 API
- [ ] 5.7 实现缺少 Embedding 配置、索引缺失、Chat Provider 失败等 `SKIPPED/FAILED` 状态
- [ ] 5.8 补充 `RagQueryBuilderTest`、`RagFeedbackValidatorTest` 和基础 retrieval eval 测试

## 6. 前端交互

- [ ] 6.1 新增前端 Embedding 配置 API 类型和请求封装
- [ ] 6.2 新增 `EmbeddingConfigView.vue`，支持新增、编辑、删除、测试连接、设置默认配置
- [ ] 6.3 在导航中加入“Embedding 配置”入口，并保持现有 API 配置、提交作文、历史记录入口可用
- [ ] 6.4 在 Embedding 配置页展示知识索引状态、进度、失败信息和“构建我的知识索引”入口
- [ ] 6.5 新增 RAG Feedback 前端 API 类型和请求封装
- [ ] 6.6 在作文结果页新增“知识点增强反馈”区块，处理未配置、未索引、生成中、完成、失败和重试状态
- [ ] 6.7 前端仅展示 citation 安全字段，不展示 embedding vector、metadata_json、系统 prompt 或过长内部原文

## 7. 端到端验收与收尾

- [ ] 7.1 运行 `.\gradlew.bat test` 并修复后端测试失败
- [ ] 7.2 运行 `cd frontend && npm run build` 并修复前端构建失败
- [ ] 7.3 使用 `.env.dev.local` 指向的真实 PostgreSQL/Redis/Provider 配置验证 Flyway V11、索引和反馈生成主流程
- [ ] 7.4 检查 OpenSpec specs 与实现一致，补充必要文档说明和用户可见中文文案
- [ ] 7.5 每完成稳定小功能提交一次，最终汇总变更、风险和剩余后续项
```

## openspec/changes/rag-feedback-mvp/specs/background-job-processing/spec.md

- Source: openspec/changes/rag-feedback-mvp/specs/background-job-processing/spec.md
- Lines: 1-64
- SHA256: d31beb6bde9f54fdde25c4b67f7cedd4de103965b6fb4dc26ffdfb350c5d52b8

```md
## ADDED Requirements

### Requirement: 通用后台任务持久化
系统 SHALL 提供轻量通用 `background_jobs` 持久化任务模型，用于统一承载 RAG 索引和 RAG Feedback 生成任务。

#### Scenario: 创建后台任务
- **WHEN** 业务服务请求创建 `RAG_INDEX` 或 `RAG_FEEDBACK` 任务
- **THEN** 系统 SHALL 保存任务类型、归属用户、发起用户、业务键、payload、状态、attempt、runAfter、lock、开始/结束时间和错误字段

#### Scenario: 任务 payload 不暴露敏感信息
- **WHEN** 系统保存 `payload_json` 或 `result_json`
- **THEN** 系统 MUST NOT 保存明文 API Key、系统 prompt 或可还原用户密钥的敏感字段

### Requirement: Active business key 防重复
系统 SHALL 使用 `job_type + owner_user_id + business_key` 防止同一业务任务并发重复执行。

#### Scenario: 复用待执行任务
- **WHEN** 同一用户、同一 job type、同一 business key 已存在 `PENDING` 或 `RUNNING` 任务
- **THEN** 系统 SHALL 返回现有任务或拒绝创建重复任务，MUST NOT 创建第二个 active 任务

#### Scenario: 已完成任务后允许重新创建
- **WHEN** 旧任务状态为 `COMPLETED`、`FAILED` 或 `SKIPPED`
- **THEN** 系统 SHALL 允许在业务规则需要时创建新的任务

### Requirement: 任务 claim 与锁
系统 SHALL 支持任务 claim 和锁字段，为当前应用内异步执行以及后续多实例恢复提供基础。

#### Scenario: claim 可执行任务
- **WHEN** 存在 `PENDING` 且 `run_after` 到期的任务
- **THEN** 系统 SHALL 将任务标记为 `RUNNING`，写入 `locked_by`、`locked_until` 和 `started_at`

#### Scenario: 跳过未到期或被锁任务
- **WHEN** 任务 `run_after` 未到期或 `locked_until` 仍有效
- **THEN** 系统 MUST NOT 将该任务分配给新的执行者

### Requirement: Handler 分发
系统 SHALL 根据 `job_type` 将任务分发给对应的 `BackgroundJobHandler`，并在 handler 完成后统一更新任务状态。

#### Scenario: RAG 索引任务分发
- **WHEN** dispatcher 接收到 `job_type=RAG_INDEX`
- **THEN** 系统 SHALL 调用 `RagIndexJobHandler`

#### Scenario: RAG Feedback 任务分发
- **WHEN** dispatcher 接收到 `job_type=RAG_FEEDBACK`
- **THEN** 系统 SHALL 调用 `RagFeedbackJobHandler`

#### Scenario: 未知任务类型
- **WHEN** dispatcher 接收到没有 handler 的 job type
- **THEN** 系统 SHALL 将任务标记为 `FAILED` 并保存安全错误信息

### Requirement: 任务状态和失败记录
系统 SHALL 统一管理 `PENDING`、`RUNNING`、`COMPLETED`、`FAILED`、`SKIPPED` 状态，并保存 attempt、错误码、错误消息和结果摘要。

#### Scenario: 任务完成
- **WHEN** handler 成功完成业务处理
- **THEN** 系统 SHALL 将任务标记为 `COMPLETED`，保存 `result_json` 和 `finished_at`

#### Scenario: 任务失败
- **WHEN** handler 抛出可展示的业务错误或外部依赖错误
- **THEN** 系统 SHALL 增加 `attempt_count`，保存标准化 `error_code` 和对用户安全的 `error_message`

#### Scenario: 任务跳过
- **WHEN** handler 判断缺少用户配置、索引缺失或前置条件不满足但不属于系统异常
- **THEN** 系统 SHALL 将任务标记为 `SKIPPED`，并在 `result_json` 中保存用户可行动的原因
```

## openspec/changes/rag-feedback-mvp/specs/embedding-config-management/spec.md

- Source: openspec/changes/rag-feedback-mvp/specs/embedding-config-management/spec.md
- Lines: 1-53
- SHA256: a5a4f21cd089969a566e3905233b5f89a82b09bfb2c708a473ac72e4e0eba135

```md
## ADDED Requirements

### Requirement: 用户管理自己的 Embedding 配置
系统 SHALL 允许已登录用户创建、查看、更新和删除自己的 Embedding 配置，并且 SHALL 阻止用户访问或修改他人的 Embedding 配置。

#### Scenario: 创建 Embedding 配置
- **WHEN** 已登录用户提交包含 `configName`、`providerType=OPENAI_EMBEDDINGS`、`baseUrl`、`apiKey`、`modelName`、`dimensions=1536` 的创建请求
- **THEN** 系统 SHALL 创建归属于当前用户的 Embedding 配置并返回不含明文 API Key 的响应

#### Scenario: 用户隔离
- **WHEN** 用户 A 请求读取、更新、删除用户 B 的 Embedding 配置
- **THEN** 系统 SHALL 返回配置不存在或无权访问的业务错误，并且 MUST NOT 暴露用户 B 的配置内容

#### Scenario: 删除配置
- **WHEN** 用户删除自己的 Embedding 配置
- **THEN** 系统 SHALL 删除该配置，并通过数据库级联或业务逻辑清理对应的用户级索引数据

### Requirement: Embedding API Key 安全保存
系统 SHALL 使用应用层加密保存 Embedding API Key，并且所有列表、详情、测试结果响应 MUST NOT 返回明文 API Key。

#### Scenario: 响应只显示 Key 状态
- **WHEN** 用户查询 Embedding 配置列表或详情
- **THEN** 系统 SHALL 只返回 `hasApiKey` 和脱敏 preview，MUST NOT 返回 `apiKey` 或可还原的密文

#### Scenario: 更新时保留旧 Key
- **WHEN** 用户更新 Embedding 配置但没有提交新的 `apiKey`
- **THEN** 系统 SHALL 保留原加密 API Key 并更新其他字段

### Requirement: 默认 Embedding 配置唯一
系统 SHALL 支持用户设置一个默认 Embedding 配置，并保证同一用户同时最多只有一个默认 Embedding 配置。

#### Scenario: 设置默认配置
- **WHEN** 用户将自己的某个 Embedding 配置设置为默认
- **THEN** 系统 SHALL 将该配置设为默认，并取消该用户其他 Embedding 配置的默认标记

#### Scenario: 默认配置按用户隔离
- **WHEN** 用户 A 设置默认 Embedding 配置
- **THEN** 系统 MUST NOT 修改用户 B 的默认 Embedding 配置

### Requirement: OpenAI-compatible Embedding 连接测试
系统 SHALL 支持使用未保存配置和已保存配置测试 OpenAI-compatible `/v1/embeddings` 连接，并记录最近一次测试状态。

#### Scenario: 未保存配置测试
- **WHEN** 用户提交未保存的 Embedding 连接测试请求
- **THEN** 系统 SHALL 调用目标 `/v1/embeddings`，请求体包含 `model`、测试 `input` 和 `dimensions`，并返回成功状态、延迟或标准化错误信息

#### Scenario: 已保存配置测试
- **WHEN** 用户测试自己的已保存 Embedding 配置
- **THEN** 系统 SHALL 解密 API Key 后发起测试，并保存 `lastTestStatus`、`lastTestMessage`、`lastTestLatencyMs` 和 `lastTestedAt`

#### Scenario: dimensions 不符合 V1 约束
- **WHEN** 用户创建或测试 `dimensions` 不是 1536 的配置
- **THEN** 系统 SHALL 拒绝请求并提示 V1 仅支持 1536 维向量
```

## openspec/changes/rag-feedback-mvp/specs/rag-feedback-frontend/spec.md

- Source: openspec/changes/rag-feedback-mvp/specs/rag-feedback-frontend/spec.md
- Lines: 1-61
- SHA256: 0e74122ea44f4569cba1e9cb6b729bb1853b4c6878cde13f7d74c0ec698ce382

```md
## ADDED Requirements

### Requirement: 前端提供 Embedding 配置页
前端 SHALL 提供 Embedding 配置管理页面，允许用户新增、编辑、删除、测试和设置默认 Embedding 配置。

#### Scenario: 新增 Embedding 配置
- **WHEN** 用户在 Embedding 配置页填写 OpenAI-compatible base URL、API Key、模型名和 1536 维参数并提交
- **THEN** 前端 SHALL 调用后端创建接口，并在成功后刷新配置列表且不在页面持久展示明文 API Key

#### Scenario: 测试连接
- **WHEN** 用户点击测试未保存或已保存的 Embedding 配置
- **THEN** 前端 SHALL 展示测试中、成功、失败、延迟和错误消息状态

#### Scenario: 设置默认配置
- **WHEN** 用户将某个 Embedding 配置设为默认
- **THEN** 前端 SHALL 调用默认配置接口，并在列表中只显示一个默认标记

### Requirement: 前端展示知识索引状态和触发入口
前端 SHALL 在 Embedding 配置相关页面展示当前用户知识索引状态，并提供显式构建或重建入口。

#### Scenario: 未构建索引
- **WHEN** 用户尚未为默认 Embedding 配置构建知识索引
- **THEN** 前端 SHALL 展示“未构建”状态和“构建我的知识索引”按钮

#### Scenario: 构建中索引
- **WHEN** 索引任务处于 `PENDING` 或 `RUNNING`
- **THEN** 前端 SHALL 展示 processedChunks、totalChunks、failedChunks 和更新时间，并避免重复触发并发任务

#### Scenario: 索引失败
- **WHEN** 索引任务状态为 `FAILED`
- **THEN** 前端 SHALL 展示安全错误信息，并允许用户重新触发构建

### Requirement: 结果页展示 RAG Feedback 区块
前端 SHALL 在作文评分结果页新增“知识点增强反馈”区块，展示反馈状态、结构化反馈内容和引用知识点。

#### Scenario: 无 Embedding 配置
- **WHEN** 后端返回 feedback `SKIPPED` 或错误码表示未配置 Embedding
- **THEN** 前端 SHALL 提示用户先配置 Embedding，并提供跳转到 Embedding 配置页的入口

#### Scenario: 无索引
- **WHEN** 后端返回索引缺失状态
- **THEN** 前端 SHALL 提示用户先构建知识索引，并提供触发索引的入口

#### Scenario: 生成中
- **WHEN** feedback 状态为 `PENDING`、`RETRIEVING` 或 `GENERATING`
- **THEN** 前端 SHALL 展示“知识点分析中”并轮询最新状态

#### Scenario: 生成完成
- **WHEN** feedback 状态为 `COMPLETED`
- **THEN** 前端 SHALL 展示 `overall`、`items`、`nextPractice` 和 citation 的标题、类型、短片段、相关原因

#### Scenario: 生成失败
- **WHEN** feedback 状态为 `FAILED`
- **THEN** 前端 SHALL 提示“不会影响评分结果”，展示安全错误信息，并提供重试按钮

### Requirement: 前端不暴露内部 RAG 细节
前端 SHALL 只展示面向用户的 RAG Feedback 内容和 citation 摘要，MUST NOT 展示 embedding vector、完整 metadata、系统 prompt 或过长内部原文。

#### Scenario: Citation 安全展示
- **WHEN** 前端渲染引用知识点
- **THEN** 前端 SHALL 只展示 `sourceTitle`、`sourceType`、`snippet`、`rankNo` 和 `reason`
```

## openspec/changes/rag-feedback-mvp/specs/rag-feedback-generation/spec.md

- Source: openspec/changes/rag-feedback-mvp/specs/rag-feedback-generation/spec.md
- Lines: 1-83
- SHA256: 702a97071c8556989a0a37fbf96278cdc741b692348381d7d5bbb4953ca1e6c3

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 从评分结果构造 RAG Query
系统 SHALL 从已完成评分的 `RubricScoringResult` 自动构造 RAG query，用户 MUST NOT 手写 query。

#### Scenario: 低分维度进入 query
- **WHEN** 评分结果中存在 `score / maxScore <= 0.75` 的维度
- **THEN** 系统 SHALL 最多选择 3 个低分维度的 label、reason 和 improvement 纳入 query

#### Scenario: 批注和改进建议进入 query
- **WHEN** 评分结果包含 annotations 和 `summary.priorityImprovements`
- **THEN** 系统 SHALL 最多选择 5 条 annotations 和 3 条 priority improvements 纳入 query

#### Scenario: 任务要求摘要进入 query
- **WHEN** 作文类型不是 `GENERAL` 且存在 `taskPrompt`
- **THEN** 系统 SHALL 将安全截断后的任务要求摘要纳入 query，并继续将其视为不可信用户输入

### Requirement: Metadata filter + vector topK 检索
系统 SHALL 使用当前用户的 Embedding 配置生成 query vector，并通过 metadata filter 与 pgvector cosine topK 检索知识卡 chunk。

#### Scenario: 正常检索
- **WHEN** 用户已有可用 Embedding 配置和对应知识索引
- **THEN** 系统 SHALL 仅检索当前用户当前配置下 active chunk embedding，并返回 topK 3 到 5 条结果

#### Scenario: EssayType 过滤
- **WHEN** 知识卡包含 `essayType` 元数据
- **THEN** 系统 SHALL 优先允许通用知识卡和与当前作文类型匹配的知识卡参与检索

#### Scenario: 索引缺失
- **WHEN** 当前用户当前 Embedding 配置没有可用索引
- **THEN** 系统 SHALL 不调用 Chat Provider 生成反馈，并返回需要先构建知识索引的状态

### Requirement: RAG Feedback 独立异步生成
系统 SHALL 在评分完成后由用户手动触发 RAG Feedback 生成，并通过 `background_jobs` 保存独立任务状态、结果摘要或失败原因。

#### Scenario: 创建反馈任务
- **WHEN** 用户对已完成评分的作文请求 `POST /api/rag/feedbacks/{essayId}/generate`
- **THEN** 系统 SHALL 创建或复用该 `scoreId + embeddingConfigId` 对应 `businessKey` 的 `RAG_FEEDBACK` 任务，立即返回当前状态，并由后台执行检索和生成

#### Scenario: 评分未完成
- **WHEN** 用户对仍处于 `SCORING`、`FAILED` 或没有评分结果的作文请求生成 RAG Feedback
- **THEN** 系统 SHALL 拒绝生成并提示需等待评分完成或先修复评分失败

#### Scenario: 缺少默认 Embedding 配置
- **WHEN** 用户未传入 `embeddingConfigId` 且没有默认 Embedding 配置
- **THEN** 系统 SHALL 将 `RAG_FEEDBACK` 任务标记为 `SKIPPED` 或返回可跳过状态，并提示用户先配置 Embedding

### Requirement: Feedback 生成复用本次评分 Chat Provider
系统 SHALL 使用本次评分记录的 `api_config_id` 调用 Chat Provider 生成 RAG Feedback，而不是引入新的 Chat Provider 选择。

#### Scenario: 使用评分配置生成
- **WHEN** feedback 任务进入 `GENERATING`
- **THEN** 系统 SHALL 使用对应 `essay_scores.api_config_id` 加载可用 Chat Provider，并传入评分结果摘要、检索 citations 和安全隔离后的作文上下文

#### Scenario: Chat Provider 不可用
- **WHEN** 本次评分对应的 Chat Provider 配置不存在、不可访问或调用失败
- **THEN** 系统 SHALL 将 feedback 任务标记为 `FAILED` 并保存标准化错误信息，MUST NOT 修改原评分结果

### Requirement: Feedback JSON 和 citations 可验证
系统 SHALL 校验 LLM 返回的 RAG Feedback JSON，保存结构化 feedback 和 citations，并保证每个反馈 item 至少绑定一个 citation。

#### Scenario: 合法 feedback
- **WHEN** LLM 返回包含 `overall`、1 到 5 个 `items`、1 到 3 条 `nextPractice` 且每个 item 有 citation 的 JSON
- **THEN** 系统 SHALL 保存 `rag_feedbacks.feedback_json`、`rag_feedback_citations`，并将对应 `background_jobs` 任务标记为 `COMPLETED`

#### Scenario: feedback 缺少 citation
- **WHEN** LLM 返回的某个 item 没有绑定 citation
- **THEN** 系统 SHALL 判定结果无效并将对应 `background_jobs` 任务标记为 `FAILED`

#### Scenario: 查询 feedback
- **WHEN** 用户请求 `GET /api/rag/feedbacks/{essayId}`
- **THEN** 系统 SHALL 只返回该用户该作文的 feedback 状态、结构化内容和 citation 展示字段，MUST NOT 暴露 embedding vector、完整 metadata 或系统 prompt

### Requirement: RAG Feedback 可重试且不影响评分
系统 SHALL 允许用户重试失败的 RAG Feedback 任务，并保证 RAG 任务状态变化不影响 `essay_scores` 的评分状态和分数。

#### Scenario: 重试失败反馈
- **WHEN** 用户请求 `POST /api/rag/feedbacks/{essayId}/retry` 且最近 feedback 状态为 `FAILED` 或可重试的 `SKIPPED`
- **THEN** 系统 SHALL 创建新的 `RAG_FEEDBACK` 任务或按业务规则复用可重试任务，并重新进入异步生成流程

```

Full source: openspec/changes/rag-feedback-mvp/specs/rag-feedback-generation/spec.md

## openspec/changes/rag-feedback-mvp/specs/rag-knowledge-indexing/spec.md

- Source: openspec/changes/rag-feedback-mvp/specs/rag-knowledge-indexing/spec.md
- Lines: 1-64
- SHA256: 6e880be687c0eeb50d9f9091e3835ac5c4fb9f1d5df216c86d9d13922a088023

```md
## ADDED Requirements

### Requirement: RAG 知识库和 pgvector schema
系统 SHALL 通过 Flyway 创建 RAG 知识库、chunk、embedding、feedback、citation 和通用 background job 相关表，并启用 PostgreSQL pgvector 扩展。

#### Scenario: Flyway 创建 RAG schema
- **WHEN** 应用在支持 pgvector 的 PostgreSQL 上启动并执行 Flyway migration
- **THEN** 系统 SHALL 创建 `background_jobs`、`embedding_configs`、`rag_documents`、`rag_chunks`、`rag_chunk_embeddings`、`rag_feedbacks`、`rag_feedback_citations` 表，并为新增表和字段提供中文注释

#### Scenario: Seed 知识卡
- **WHEN** V11 migration 完成
- **THEN** 系统 SHALL seed 约 30 条内部英语语法、写作和考试策略知识卡，并且 MUST NOT 在 migration 中写入 embedding 向量

### Requirement: 用户显式触发知识索引
系统 SHALL 仅在用户显式请求时为该用户和指定 Embedding 配置构建知识索引，MUST NOT 在用户未确认时自动消耗用户 Embedding Key。

#### Scenario: 用户触发索引
- **WHEN** 已登录用户请求 `POST /api/rag/index/rebuild-my` 并指定自己的 `embeddingConfigId`
- **THEN** 系统 SHALL 创建或复用 `job_type=RAG_INDEX` 的 `background_jobs` 任务并立即返回 job 状态，后台异步生成并保存该用户该配置下的知识卡向量

#### Scenario: 默认配置索引
- **WHEN** 用户未显式传入 `embeddingConfigId` 但已有默认 Embedding 配置
- **THEN** 系统 SHALL 使用当前用户的默认 Embedding 配置创建或复用 `RAG_INDEX` 任务

#### Scenario: 缺少 Embedding 配置
- **WHEN** 用户触发索引但既未指定可用配置也没有默认 Embedding 配置
- **THEN** 系统 SHALL 拒绝创建任务并返回需要先配置 Embedding 的错误

### Requirement: 索引任务防重复和进度可见
系统 SHALL 通过 `background_jobs` 防止同一用户同一 Embedding 配置同时存在多个 `PENDING` 或 `RUNNING` 索引任务，并提供进度、失败数和错误信息查询。

#### Scenario: 重复索引任务
- **WHEN** 同一用户同一 Embedding 配置和知识库版本已有 `PENDING` 或 `RUNNING` 的 `RAG_INDEX` 任务
- **THEN** 系统 SHALL 返回现有任务状态或拒绝重复创建，MUST NOT 创建并发重复任务

#### Scenario: 索引进度更新
- **WHEN** 后台任务完成部分 chunk embedding 写入
- **THEN** 系统 SHALL 在 `background_jobs.result_json` 中更新 `processedChunks`、`failedChunks`、`totalChunks` 和 `embeddingVersion`

#### Scenario: 索引失败保存
- **WHEN** Embedding Provider 调用失败或写入数据库失败
- **THEN** 系统 SHALL 将 job 标记为 `FAILED`，保存标准化 `errorCode` 和对用户安全的 `errorMessage`

### Requirement: 向量索引按用户和配置隔离
系统 SHALL 按 `user_id`、`embedding_config_id`、`chunk_id` 和 `embedding_version` 唯一保存 chunk embedding，并在检索时强制使用当前用户和配置条件。

#### Scenario: 用户索引隔离
- **WHEN** 用户 A 和用户 B 使用不同 Embedding 配置索引同一知识卡 chunk
- **THEN** 系统 SHALL 保存两份相互隔离的 embedding 记录，并且任一用户的检索 MUST NOT 返回另一用户的 embedding 记录

#### Scenario: 强制重建索引
- **WHEN** 用户以 `force=true` 触发索引
- **THEN** 系统 SHALL 重新生成该用户该配置的目标 chunk embedding，并确保后续检索使用最新 `contentHash` 与 `embeddingVersion`

### Requirement: 管理员索引运维接口受保护
系统 SHALL 提供管理员查看索引状态和代用户触发索引的接口，并且 SHALL 要求当前用户具备 ADMIN 角色。

#### Scenario: 非管理员访问管理员接口
- **WHEN** 非管理员用户请求 `/api/admin/rag/index/**`
- **THEN** 系统 SHALL 拒绝请求并返回权限不足错误

#### Scenario: 管理员代触发索引
- **WHEN** 管理员为指定 `userId` 和 `embeddingConfigId` 触发索引
- **THEN** 系统 SHALL 创建或复用 `RAG_INDEX` 任务并记录 `requestedByUserId` 为管理员用户 id
```

