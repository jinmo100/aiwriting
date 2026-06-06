# AI 英语作文评分系统 v2.0

基于 **Spring Boot 3.4.2 + Java 21 + Vue 3 + LangChain4j 1.16.1** 的 AI 英语作文评分系统。项目已从单一 OpenAI-compatible 调用演进为 **AI Provider 抽象层**，支持多协议 Provider、API Key 加密存储、模型列表拉取、连接测试、结构化输出测试和作文评分历史管理。

## 当前状态

- 后端：Spring Boot 3.4.2、Java 21、MyBatis-Plus 3.5.9、Flyway、PostgreSQL、Redis、LangChain4j 1.16.1。
- 前端：Vue 3、TypeScript、Element Plus、Vite、Pinia、Axios。
- 开发环境默认：本机 PostgreSQL + 本机 Redis；不再使用 H2 作为日常开发数据库。
- 可选环境：如数据库/Redis 在远端，可通过本地 `.env.dev.local` 覆盖连接参数，必要时手动启用 SSH 隧道脚本。
- 本地访问：
  - 后端：`http://127.0.0.1:8080`
  - 前端开发服务：`http://127.0.0.1:5173`
  - Swagger UI：`http://127.0.0.1:8080/swagger-ui.html`

## 核心功能

- 多 Provider 协议适配：`OPENAI_CHAT_COMPLETIONS`、`OPENAI_RESPONSES`、`ANTHROPIC_MESSAGES`、`GEMINI_GENERATE_CONTENT`。
- API 配置管理：新增、编辑、删除、设置默认配置。
- API Key 加密存储：新建/更新配置写入 `api_key_encrypted`，旧 `api_key` 字段仅兼容读取。
- dev 环境可控 reveal：普通接口只返回 `hasApiKey` 和 `apiKeyPreview`。
- Provider 测试：测试连接、测试结构化输出。
- 模型列表拉取：支持已保存配置和未保存配置；Redis 缓存模型列表。
- AI 评分：内容、语言、结构、连贯性四维评分，返回优点、建议、错误标注、详细评价。
- 结构化输出校验：JSON 解析、本地字段校验、失败后 repair retry 一次。
- 历史记录：保存作文、`wordCount`、作文类型、评分结果和处理耗时。

## 技术栈

### 后端

- Java 21（通过 Gradle Toolchain 声明；仓库不固定个人 JDK 路径）
- Spring Boot 3.4.2
- MyBatis-Plus 3.5.9
- Flyway 10.x
- PostgreSQL
- Redis / Spring Data Redis
- LangChain4j 1.16.1 + OpenAI Official beta adapter
- Caffeine 本地缓存
- SpringDoc OpenAPI
- Lombok

### 前端

- Vue 3
- TypeScript 5
- Element Plus
- Vite 5
- Pinia
- Axios

## 本地开发启动

### 1. 准备 PostgreSQL / Redis

默认开发配置连接本机服务：

```text
PostgreSQL: localhost:5432 / database=aiwriting / user=aiwriting
Redis:      redis://localhost:6379/0
```

可以使用本机安装、Docker Compose 或自行准备的数据库。H2 控制台默认关闭，H2 不再作为当前开发链路。

### 2. 准备本地 env 文件

```powershell
Copy-Item .env.dev.example .env.dev.local
# 编辑 .env.dev.local，填入本机真实 DEV_DB_PASSWORD、DEV_REDIS_URL、AIWRITING_SECRET_KEY 等
```

`.env.dev.local` 已被 `.gitignore` 忽略，不要提交真实密码、API Key、私有远端地址或 SSH Key 路径。

### 3. 启动后端

```powershell
.\scripts\start-backend-dev.ps1
```

该脚本会加载 `.env.dev.local`（如果存在），然后执行 `./gradlew.bat bootRun`。也可以直接运行：

```powershell
.\gradlew.bat bootRun
```

如需远端数据库/Redis，可在 `.env.dev.local` 中设置 `DEV_DB_HOST`、`DEV_DB_PORT`、`DEV_REDIS_URL` 等。只有明确需要 SSH 转发时才使用：

```powershell
.\scripts\start-backend-dev.ps1 -WithTunnel
# 或单独启动隧道：
.\scripts\start-vps-postgres-tunnel.ps1 -TunnelHost example.com -TunnelUser ubuntu -IdentityFile C:\path\to\id_rsa
```

隧道脚本没有仓库默认主机、用户名或私钥路径；这些值必须通过参数或环境变量显式提供。

### 4. 启动前端

```powershell
cd frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```

访问：`http://127.0.0.1:5173`

## 配置 Provider

进入“API配置”页面新增配置：

- 配置名称：自定义展示名称，例如 `OpenRouter`、`OpenAI`、`Gemini`。
- Provider 类型：选择协议适配器。
- Provider 名称：展示标签，可填写品牌或站点名。
- API Base URL：只填写 API 根地址，不填写具体 endpoint。
- API Key：创建时必填；编辑时留空表示保留旧 Key。
- 模型名称：可手动输入，也可点击“获取模型列表”。
- Temperature / Max Tokens / Timeout：通用模型参数。
- 高级参数 JSON：按 Provider 白名单过滤后使用。

| Provider 类型 | Base URL 示例 | 说明 |
|---|---|---|
| `OPENAI_CHAT_COMPLETIONS` | `https://api.openai.com/v1` | 后端调用 `{baseUrl}/chat/completions` |
| `OPENAI_RESPONSES` | `https://api.openai.com/v1` | 后端调用 `{baseUrl}/responses` |
| `ANTHROPIC_MESSAGES` | `https://api.anthropic.com/v1` | 后端调用 `{baseUrl}/messages` |
| `GEMINI_GENERATE_CONTENT` | `https://generativelanguage.googleapis.com/v1beta` | 后端调用 `{baseUrl}/models/{model}:generateContent` |

## 作文评分流程

1. 在“API配置”中确认默认配置测试通过。
2. 进入“提交作文”页面。
3. 输入 50-10000 字符英文作文。
4. 可选作文类型：IELTS、TOEFL、CET4、CET6。
5. 提交评分。
6. 系统保存作文、计算 `wordCount`、调用 AI、保存评分结果。
7. 跳转结果页，展示总分、维度分、优点、建议、错误标注和详细评价。
8. 历史页可查看已评分作文并进入详情。

## API 清单

### 配置管理

| 方法 | 路径 | 功能 |
|---|---|---|
| `GET` | `/api/configs/security-policy` | 获取配置页安全策略 |
| `POST` | `/api/configs` | 创建配置 |
| `GET` | `/api/configs` | 获取全部配置 |
| `GET` | `/api/configs/{id}` | 获取配置详情 |
| `PUT` | `/api/configs/{id}` | 更新配置 |
| `DELETE` | `/api/configs/{id}` | 删除配置 |
| `PUT` | `/api/configs/{id}/default` | 设置默认配置 |
| `POST` | `/api/configs/{id}/reveal-api-key` | reveal 完整 Key（受环境开关控制） |
| `POST` | `/api/configs/test-connection` | 使用未保存配置测试连接 |
| `POST` | `/api/configs/{id}/test-connection` | 使用已保存配置测试连接 |
| `POST` | `/api/configs/test-structured-output` | 使用未保存配置测试结构化输出 |
| `POST` | `/api/configs/{id}/test-structured-output` | 使用已保存配置测试结构化输出 |
| `POST` | `/api/configs/models/fetch` | 使用未保存配置拉取模型列表 |
| `POST` | `/api/configs/{id}/models/fetch` | 使用已保存配置拉取模型列表 |

### 作文评分

| 方法 | 路径 | 功能 |
|---|---|---|
| `POST` | `/api/essays/submit` | 提交作文并评分 |
| `GET` | `/api/essays/history?page=1&size=10` | 分页获取历史记录 |
| `GET` | `/api/essays/{id}` | 获取作文详情和评分 |

## 项目结构

```text
aiwriting/
├── src/main/java/com/jinmo/aiwriting/
│   ├── ai/                            # AI Service、评分校验、Provider 抽象与 Adapter
│   ├── controller/                    # REST API
│   ├── service/                       # 业务服务
│   ├── security/                      # API Key AES-GCM 加密
│   ├── mapper/                        # MyBatis-Plus Mapper
│   ├── domain/entity/                 # 实体
│   ├── domain/dto/                    # DTO
│   ├── config/                        # MyBatis、Jackson、OpenAPI、Web 配置
│   └── common/                        # 统一响应与异常
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   └── db/migration/                  # Flyway migrations
├── frontend/                          # Vue 前端
├── docs/                              # 设计、状态、流程与数据库文档
├── scripts/                           # 本地开发辅助脚本
├── docker-compose.yml
└── build.gradle
```

## 数据库迁移

当前使用 Flyway 管理 schema：

```text
src/main/resources/db/migration/V1__init_schema.sql
src/main/resources/db/migration/V2__provider_config_abstraction.sql
```

`src/main/resources/db/init.sql` 仅作为兼容/初始化参考，不再作为 dev schema 演进主路径。

## 验证命令

```powershell
# 后端测试
.\gradlew.bat test --no-daemon --rerun-tasks

# 前端构建
cd frontend
npm run build
```

最近一次本地验证：后端 25 个测试通过；前端构建通过；核心链路在本地私有配置下完成过端到端验证。

## 后续计划

- 统一错误响应结构。
- 评分流程状态机化，避免长事务包住 AI 远程调用。
- 历史列表增加评分摘要、模型名、评分状态。
- 前端 bundle 拆分和 Element Plus 按需优化。
- 生产环境接入认证/权限控制后再开放受控 API Key reveal。
