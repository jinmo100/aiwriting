# AI 英语作文评分系统 - 当前路线图

更新时间：2026-06-07

> 旧 MVP 已完成并演进为 v2.0 多 Provider 架构。本文档保留“后续路线图”用途，不再描述旧的 Repository/JPA/ModelFactory 方案。

## 当前架构基线

```text
Vue 3 + Element Plus 前端
  -> Spring Boot REST API
  -> MyBatis-Plus
  -> PostgreSQL + Flyway
  -> Redis + Caffeine
  -> AIProviderAdapter
  -> LangChain4j Provider Models
```

## 已完成

- Vue 前端四个核心页面：配置、提交、结果、历史。
- API 配置 CRUD 和默认配置。
- Provider 抽象层：OpenAI Chat Completions、OpenAI Responses、Anthropic Messages、Gemini Generate Content。
- API Key AES-GCM 加密存储，旧明文字段兼容读取。
- dev reveal 开关与前端 reveal 隐藏策略。
- 连接测试、结构化输出测试。
- 模型列表拉取和 Redis 缓存。
- ChatModel Caffeine 指纹缓存。
- Redis Provider 配置失效通知。
- Flyway schema 管理。
- 作文评分、结构化校验、repair retry。
- 作文 `wordCount` 保存。
- 本地私有配置下完成核心链路端到端验证。

## 数据库表

当前 schema 由 Flyway 管理：

```text
src/main/resources/db/migration/V1__init_schema.sql
src/main/resources/db/migration/V2__provider_config_abstraction.sql
```

核心表：

- `api_configs`
- `essays`
- `essay_scores`

## 当前 API

### 配置管理

- `GET /api/configs/security-policy`
- `POST /api/configs`
- `GET /api/configs`
- `GET /api/configs/{id}`
- `PUT /api/configs/{id}`
- `DELETE /api/configs/{id}`
- `PUT /api/configs/{id}/default`
- `POST /api/configs/{id}/reveal-api-key`
- `POST /api/configs/test-connection`
- `POST /api/configs/{id}/test-connection`
- `POST /api/configs/test-structured-output`
- `POST /api/configs/{id}/test-structured-output`
- `POST /api/configs/models/fetch`
- `POST /api/configs/{id}/models/fetch`

### 作文评分

- `POST /api/essays/submit`
- `GET /api/essays/history`
- `GET /api/essays/{id}`

## 下一阶段优先级

### P1：评分任务状态机

目标：避免同步评分长事务占用数据库连接。

建议状态：

```text
PENDING -> SCORING -> COMPLETED
                  -> FAILED
```

### P1：统一错误响应

将异常响应统一为 `ApiResponse` 风格，降低前端兼容成本。

### P1：认证和权限

- 管理 API 配置需要认证。
- reveal API Key 需要管理员/本人权限。
- 作文历史需要按用户隔离。

### P2：历史列表增强

历史页展示：总分、模型名、评分状态、评分时间、处理耗时。

### P2：用量与成本统计

保存 input tokens、output tokens、total tokens、provider request id、模型耗时。

### P2：前端性能优化

- Element Plus 按需导入。
- Vite manualChunks。
- 路由级和组件级进一步拆包。

### P3：批量评分和报告

- 批量上传作文。
- 学习报告。
- 进步趋势分析。

## 验证命令

```powershell
.\gradlew.bat test --no-daemon --rerun-tasks
cd frontend
npm run build
```
