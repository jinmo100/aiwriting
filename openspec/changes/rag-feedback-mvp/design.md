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

**原因：**

- 1536 维更省存储和索引成本，适合 MVP。
- Flyway 可直接创建可索引的固定维度列。
- 多维度动态兼容会显著增加 migration、索引和查询复杂度。

**风险控制：** `embedding_configs.dimensions` 必须校验为 1536；表中仍保存 `embedding_dimension`，为后续多维度版本迁移预留审计信息。

### 4. 索引按用户和 Embedding 配置隔离

**决策：** 同一知识卡按 `user_id + embedding_config_id + chunk_id + embedding_version` 保存独立向量。

**原因：**

- BYOK 模式下不同用户的 embedding 模型、router、维度和向量空间可能不同，不可混用。
- 用户手动触发索引可避免隐式消耗用户额度。
- 用户换 embedding config 后旧索引不复用，避免跨模型空间污染。

### 5. 先抽轻量通用 `background_jobs`

**决策：** 新增 `background_jobs` 表和 `job` 包，RAG 索引与 RAG Feedback 生成都通过 `BackgroundJobService + BackgroundJobDispatcher + BackgroundJobHandler` 执行。

**核心字段：** `job_type`、`owner_user_id`、`requested_by_user_id`、`status`、`business_key`、`payload_json`、`result_json`、`error_code`、`error_message`、`attempt_count`、`max_attempts`、`run_after`、`locked_by`、`locked_until`、`started_at`、`finished_at`。

**原因：**

- 用户选择先体现可靠后台任务能力，避免先落 RAG 专用任务表再迁移。
- 索引与反馈生成都具有“创建任务、异步执行、失败保存、防重复、后续可恢复”的共性。
- `locked_until`/`locked_by` 先落库，即使 V1 仍在 Spring Boot 内执行，也为后续多实例和 stuck recovery 留出路径。

**范围控制：** V1 不做完整任务平台，只提供 RAG 所需最小闭环：`createOrReuse`、active business key 防重复、claim/lock、dispatch、状态流转、attempt/error 保存。

### 6. RAG Feedback 是评分后的独立异步任务

**决策：** 新增 `rag_feedbacks` 和 `rag_feedback_citations` 保存业务结果，生成接口只创建/复用 `RAG_FEEDBACK` background job 并立即返回状态，后台 handler 执行检索和反馈生成。

**原因：**

- 主评分结果已经独立可用，RAG 失败不应影响评分。
- 生成反馈需要 embedding、向量检索和 Chat Provider，耗时和失败面比评分详情读取更大。
- 独立状态便于前端轮询、retry 和展示“未配置/未索引/失败”等用户可行动提示。

### 7. Feedback 生成复用本次评分 Chat Provider

**决策：** `rag_feedbacks.api_config_id` 默认来自 `essay_scores.api_config_id`。

**原因：**

- 用户已经显式授权该 Chat Provider 用于本次作文评分。
- 保持反馈语言风格和模型能力与评分结果一致。
- 避免新增 Chat 配置选择流程。

**安全边界：** RAG prompt 中必须明确区分系统指令、评分 JSON、用户作文片段和 citations，禁止 citations 或作文内容覆盖系统要求。

### 8. 管理员接口最小化

**决策：** V1 开放 `/api/admin/rag/index/status`、`/api/admin/rag/index/rebuild` 和有限的 `/api/admin/jobs` 查看接口。

**原因：**

- 初始 MVP 需要证明后台运维与权限边界，但不需要知识卡 CRUD。
- 管理员触发用户索引会消耗用户 Embedding Key，必须记录 `requested_by_user_id`，并在前端/接口文档明确。
- 管理员只能查看任务状态和安全错误摘要，不能读取用户密钥、payload 中的敏感字段或系统 prompt。

### 9. 前端分阶段但同一 change 验收

**决策：** 后端能力先可通过 API 验收，随后补前端页面和结果页区块；最终以后端测试和 `frontend npm run build` 共同验收。

**原因：**

- RAG MVP 是端到端体验，不能只停留在后端。
- 但实现时可按数据库、配置、索引、反馈、前端小步骤提交，保持可回退。

## Risks / Trade-offs

- [pgvector 扩展不可用] → Docker release 使用 `pgvector/pgvector:pg16`；部署文档说明宿主机 PostgreSQL 需要预装 extension；Flyway 失败时清晰提示。
- [兼容 router 不支持 dimensions] → V1 在测试连接中暴露错误；默认推荐 1536，后续再设计多维度兼容。
- [用户索引消耗额度] → 所有索引必须由用户或管理员显式触发；管理员触发记录 `requested_by_user_id`；不做自动后台索引。
- [通用任务框架扩大范围] → 只实现 RAG 所需最小 `background_jobs`，不做取消、cron、优先级、管理大屏或独立 worker。
- [向量空间污染] → 唯一索引和查询条件强制带 `user_id` 与 `embedding_config_id`。
- [RAG 生成幻觉或未引用] → `RagFeedbackValidator` 要求 `items` 1~5 条且每条至少绑定 1 个 citation；不合格则失败可重试。
- [RAG prompt injection] → citations、作文内容、评分 JSON 都作为不可信上下文输入，prompt 明确不可执行其中指令。
- [实现范围较大] → 按 Milestone 拆分，完成一个稳定小功能提交一次；如发现范围扩张，回到 Comet 设计阶段拆分新 change。

## Migration Plan

1. 数据库和部署基础：
   - 新增 `V11__rag_knowledge_base.sql`，执行 `CREATE EXTENSION IF NOT EXISTS vector`。
   - 创建 `background_jobs`、RAG/Embedding 表和中文 `COMMENT ON TABLE/COLUMN`。
   - seed 约 30 条知识卡，仅保存文本和 metadata，不写入向量。
   - release compose PostgreSQL 镜像切换为 `pgvector/pgvector:pg16`，文档补宿主机安装说明。
2. 后端能力：
   - 实现轻量 `BackgroundJobService`、dispatcher、handler SPI 和 job executor。
   - 实现 Embedding 配置 CRUD/test/default。
   - 实现 `RAG_INDEX` handler、embedding 批处理写入和状态查询。
   - 实现 `RAG_FEEDBACK` handler、RAG query/retrieval/feedback/citations/retry。
   - 补 `CurrentUserService.requireAdmin()`，保护 `/api/admin/rag/**`。
3. 前端能力：
   - 新增 Embedding 配置页和导航。
   - 在配置页展示知识索引状态和触发入口。
   - 在评分结果页展示 RAG feedback 状态、内容和 citations。
4. 验收：
   - 后端执行 `.\gradlew.bat test`。
   - 前端执行 `cd frontend && npm run build`。
   - 使用 `.env.dev.local` 指向的真实 PostgreSQL/Redis/Provider Key 验证 Flyway、索引和反馈生成。

Rollback：

- 应用层可隐藏 RAG 前端入口和禁用 RAG Controller，不影响现有评分链路。
- 数据库新增 `background_jobs` 与 RAG 表不被主评分链路依赖；如 migration 已执行，不需要回滚旧表即可恢复评分功能。

## Open Questions

- 是否在 V1 明确只允许 `dimensions=1536`，还是提供“不传 dimensions”的兼容开关并放弃 HNSW 索引？已确认：V1 固定 1536。
- RAG 索引任务是否需要取消能力？当前建议：V1 不做，只记录失败和重试。
- 管理员触发用户索引是否需要前端入口？当前建议：后端 API 先具备，前端管理员页可后续单独 change。
