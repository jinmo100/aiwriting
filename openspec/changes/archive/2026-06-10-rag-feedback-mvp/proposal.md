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
