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
