## 1. 数据库与部署基础

- [x] 1.1 新增 `V11__rag_knowledge_base.sql`，启用 pgvector extension 并创建 `background_jobs`、RAG/Embedding 相关表
- [x] 1.2 为 V11 新增表、字段、关键约束补充中文 `COMMENT ON TABLE/COLUMN`
- [x] 1.3 Seed 约 30 条英语语法、写作、考试策略知识卡，不在 migration 中写入向量
- [x] 1.4 为 `background_jobs` active business key、claim 查询、Embedding 默认配置、RAG chunk/embedding/feedback/citation 增加约束和索引
- [x] 1.5 将 release Docker PostgreSQL 镜像切换到 `pgvector/pgvector:pg16`
- [x] 1.6 更新部署文档，说明 Docker 和宿主机 PostgreSQL 的 pgvector 安装/权限要求

## 2. 通用后台任务框架

- [x] 2.1 新增 `job` 包下 `BackgroundJob` entity、mapper、dto、`BackgroundJobType`、`BackgroundJobStatus`
- [x] 2.2 实现 `BackgroundJobService.createOrReuse`、active business key 防重复和 payload/result 安全保存
- [x] 2.3 实现 claim/lock、markCompleted、markFailed、markSkipped、attempt/error/result 状态流转
- [x] 2.4 实现 `BackgroundJobHandler` SPI 和 `BackgroundJobDispatcher`，按 job type 分发到 handler
- [x] 2.5 新增 `BackgroundJobExecutorConfig`，与现有评分 executor 隔离
- [x] 2.6 实现有限管理员 job 查询接口，返回状态、错误摘要和安全 result，不暴露敏感 payload
- [x] 2.7 补充 `BackgroundJobServiceTest`，覆盖防重复、claim/lock、状态流转和失败保存

## 3. Embedding 配置后端

- [x] 3.1 新增 `embedding` 包下 entity、dto、mapper、enum 和基础目录结构
- [x] 3.2 实现 `EmbeddingConfigService` 的创建、列表、详情、更新、删除、默认配置逻辑
- [x] 3.3 复用 `ApiKeyEncryptionService` 加密保存 Embedding API Key，并在响应中只返回 `hasApiKey` 和 preview
- [x] 3.4 实现 OpenAI-compatible `EmbeddingClient`，支持 `/v1/embeddings` 请求、1536 维强校验和错误分类
- [x] 3.5 实现未保存配置和已保存配置的 Embedding 连接测试，并记录最近测试状态
- [x] 3.6 新增 `EmbeddingConfigController` 暴露 `/api/embedding-configs/**` API
- [x] 3.7 补充 `EmbeddingConfigServiceTest` 和 `OpenAiCompatibleEmbeddingClientTest`

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
