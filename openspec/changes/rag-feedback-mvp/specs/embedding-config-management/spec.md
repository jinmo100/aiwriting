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
