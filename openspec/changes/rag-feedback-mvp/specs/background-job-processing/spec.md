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
