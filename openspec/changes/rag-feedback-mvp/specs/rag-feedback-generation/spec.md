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

#### Scenario: RAG 失败不影响评分结果
- **WHEN** RAG Feedback 任务失败
- **THEN** 系统 MUST NOT 修改 `essay_scores.scoring_status`、`nativeScore`、`normalizedScore`、`gradeLabel` 或 `result_json`
