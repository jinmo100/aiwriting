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
