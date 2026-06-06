# AI 英语作文评分系统项目现状报告

更新时间：2026-06-07

## 1. 总体结论

当前项目已经演进到 **v2.0 多 Provider 架构**：

- 后端：Spring Boot 3.4.2 + Java 21 + MyBatis-Plus + Flyway + PostgreSQL + Redis + LangChain4j 1.16.1。
- 前端：Vue 3 + TypeScript + Element Plus + Vite。
- AI 调用：通过项目自有 Provider Adapter 抽象隔离 LangChain4j 具体模型类。
- dev 环境：默认连接本机 PostgreSQL/Redis；敏感配置通过本地 `.env.dev.local` 注入；SSH 隧道仅作为显式可选方案。
- 核心链路已验证：配置测试 -> 结构化输出测试 -> 作文评分 -> 结果页 -> 历史页。

## 2. 当前已实现能力

### Provider 抽象

支持四类协议：

```text
OPENAI_CHAT_COMPLETIONS
OPENAI_RESPONSES
ANTHROPIC_MESSAGES
GEMINI_GENERATE_CONTENT
```

核心文件：

```text
src/main/java/com/jinmo/aiwriting/ai/provider/AIProviderAdapter.java
src/main/java/com/jinmo/aiwriting/ai/provider/ProviderAdapterRegistry.java
src/main/java/com/jinmo/aiwriting/ai/provider/LangChainChatModelFactory.java
src/main/java/com/jinmo/aiwriting/ai/provider/*Adapter.java
```

业务层只依赖 `AIProviderAdapter` / `AIProviderResult`，不直接暴露 LangChain4j `ChatResponse`。

### API Key 安全

- 新增/更新配置时写入 `api_key_encrypted`。
- 加密服务：`src/main/java/com/jinmo/aiwriting/security/ApiKeyEncryptionService.java`。
- 旧 `api_key` 字段仅兼容读取。
- 普通配置接口只返回 `hasApiKey` 和 `apiKeyPreview`。
- 完整 Key reveal 使用独立接口，并受 `aiwriting.security.allow-api-key-reveal` 控制。

### 配置页能力

已支持：创建/更新/删除配置、设置默认配置、测试连接、测试结构化输出、拉取远端模型列表、编辑时保留旧 API Key、按安全策略隐藏 reveal 按钮。

### 缓存与 Redis

- `ChatModel` 使用 Caffeine 本地缓存，key 为配置指纹。
- Redis 用于模型列表缓存。
- Provider 配置更新/删除时发布 Redis 失效消息，其他 JVM 可按 configId 清理本地 Caffeine 指纹索引。

### 作文评分

流程：

```text
EssayController
  -> EssayService
  -> AIService
  -> ProviderAdapterRegistry
  -> AIProviderAdapter.generate()
  -> ScoringResultValidator
  -> repair retry（必要时一次）
  -> 保存 Essay / EssayScore
```

当前保存字段包括：作文内容、`wordCount`、作文类型、总分和四项维度分、优点、建议、错误、详细反馈、模型名和处理耗时。

## 3. 开发环境

后端：

```powershell
.\scripts\start-backend-dev.ps1
```

前端：

```powershell
cd frontend
npm run dev -- --host 127.0.0.1 --port 5173
```

访问：

```text
前端：http://127.0.0.1:5173
后端：http://127.0.0.1:8080
Swagger：http://127.0.0.1:8080/swagger-ui.html
```

默认数据服务：

```text
PostgreSQL: localhost:5432
Redis:      redis://localhost:6379/0
```

远端数据库/Redis 通过 `.env.dev.local` 覆盖连接参数；SSH 隧道需要显式传参或配置 `DEV_TUNNEL_*`。

schema 由 Flyway 管理：

```text
src/main/resources/db/migration/V1__init_schema.sql
src/main/resources/db/migration/V2__provider_config_abstraction.sql
```

## 4. 当前 API 清单

### 配置管理

| 方法 | 路径 | 功能 |
|---|---|---|
| GET | `/api/configs/security-policy` | 获取配置页安全策略 |
| POST | `/api/configs` | 创建配置 |
| GET | `/api/configs` | 获取全部配置 |
| GET | `/api/configs/{id}` | 获取配置详情 |
| PUT | `/api/configs/{id}` | 更新配置 |
| DELETE | `/api/configs/{id}` | 删除配置 |
| PUT | `/api/configs/{id}/default` | 设置默认配置 |
| POST | `/api/configs/{id}/reveal-api-key` | reveal 完整 Key（受环境控制） |
| POST | `/api/configs/test-connection` | 未保存配置测试连接 |
| POST | `/api/configs/{id}/test-connection` | 已保存配置测试连接 |
| POST | `/api/configs/test-structured-output` | 未保存配置测试结构化输出 |
| POST | `/api/configs/{id}/test-structured-output` | 已保存配置测试结构化输出 |
| POST | `/api/configs/models/fetch` | 未保存配置获取模型列表 |
| POST | `/api/configs/{id}/models/fetch` | 已保存配置获取模型列表 |

### 作文评分

| 方法 | 路径 | 功能 |
|---|---|---|
| POST | `/api/essays/submit` | 提交作文并评分 |
| GET | `/api/essays/history` | 分页获取历史记录 |
| GET | `/api/essays/{id}` | 获取作文详情和评分 |

## 5. 最近验证记录

2026-06-07 已完成：

- 后端测试通过。
- 前端构建通过。
- 本地私有配置下完成“配置测试 -> 结构化输出测试 -> 作文评分 -> 结果页 -> 历史页”的端到端验证。
- 作文提交后会写入 `wordCount`。

## 6. 构建和测试

```powershell
.\gradlew.bat test --no-daemon --rerun-tasks
cd frontend
npm run build
```

当前结果：后端 25 个测试通过；前端构建通过。Vite 主 chunk 体积警告属于性能优化项，不阻塞功能。

## 7. 仍需关注

- P1：评分流程状态机化，避免同步远程 AI 调用长期占用数据库连接。
- P1：统一错误响应为 `ApiResponse` 风格。
- P2：历史列表增加总分、模型名、评分耗时、评分状态。
- P2：生产接入认证/权限控制，reveal 完整 Key 默认保持禁用。
- P3：Element Plus 按需导入和 Vite manualChunks。
