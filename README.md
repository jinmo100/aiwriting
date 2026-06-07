# AI 英语作文评分系统 v2.1

基于 **Spring Boot 3.4.2 + Java 21 + Vue 3 + LangChain4j 1.16.1** 的 AI 英语作文评分系统。当前版本已经从旧的固定四维评分升级为 **作文类型驱动 + DB Rubric + 动态评分报告**，并保留多 Provider AI 配置能力。

## 当前状态

- 后端：Spring Boot 3.4.2、Java 21、MyBatis-Plus、Flyway、PostgreSQL、Redis、LangChain4j。
- 前端：Vue 3、TypeScript、Element Plus、Vite、Pinia、Axios。
- 数据库：PostgreSQL 是 dev/prod 基线；H2 不作为日常开发数据库。
- Schema：Flyway 管理，`src/main/resources/db/init.sql` 仅为 Docker 兼容 no-op。
- 评分：旧四维字段已废除，评分结果统一保存为 `RubricScoringResult` JSON。

## 核心功能

- 多 Provider 协议适配：`OPENAI_CHAT_COMPLETIONS`、`OPENAI_RESPONSES`、`ANTHROPIC_MESSAGES`、`GEMINI_GENERATE_CONTENT`。
- API 配置管理：新增、编辑、删除、设置默认配置、连接测试、结构化输出测试、模型列表拉取。
- API Key 加密存储：新建/更新写入 `api_key_encrypted`，普通接口不返回完整 Key。
- 作文类型驱动评分：支持通用、初中、中考、高中、高考、CET4、CET6、IELTS Task 1/2、TOEFL Independent；TOEFL Integrated 已 seed 但暂缓开放。
- DB Rubric：`rubric_profiles` / `rubric_versions` / `rubric_dimensions` 保存 ACTIVE 评分标准。
- 动态评分结果：原生分、100 分换算、等级、置信度、动态维度、证据、改进建议、片段问题、输入分析。
- 输入防御：基础 PASS/WARN/REJECT、prompt injection 检测、隐私/高风险敏感内容、emoji/特殊符号/控制字符/零宽字符/非英文比例检查。
- 异步评分与幂等：提交后立即返回 `SCORING`，后台评分；`idempotencyKey` + `contentHash` 通过 Redis/DB 防重复提交。
- 前端中文友好：中文作文类型、题目/任务要求输入、AI Thinking 等待提示、动态维度结果页、增强历史页。

## 本地开发启动

### 1. 准备 PostgreSQL / Redis

默认开发配置连接本机服务：

```text
PostgreSQL: localhost:5432 / database=aiwriting / user=aiwriting
Redis:      redis://localhost:6379/0
```

### 2. 准备本地 env 文件

```powershell
Copy-Item .env.dev.example .env.dev.local
```

编辑 `.env.dev.local`，填入本机真实值，例如 `DEV_DB_PASSWORD`、`DEV_REDIS_URL`、`AIWRITING_SECRET_KEY`。该文件已被 `.gitignore` 忽略，不能提交真实密码、API Key、远端地址或 SSH Key 路径。

### 3. 启动后端

```powershell
.\scripts\start-backend-dev.ps1
```

或：

```powershell
.\gradlew.bat bootRun
```

如数据库/Redis 在远端，可在 `.env.dev.local` 覆盖连接参数；只有明确需要 SSH 转发时才使用：

```powershell
.\scripts\start-backend-dev.ps1 -WithTunnel
```

### 4. 启动前端

```powershell
cd frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```

访问：

```text
后端：http://127.0.0.1:8080
前端：http://127.0.0.1:5173
Swagger：http://127.0.0.1:8080/swagger-ui.html
```

## 作文评分流程

1. 在“API配置”中新增或确认默认配置。
2. 进入“提交作文”。
3. 选择作文类型；除“通用英语作文”外，需要填写 `taskPrompt`。
4. 输入主要由英文构成的作文正文。
5. 提交后页面显示 `AI Thinking` 等待态。
6. 后端创建 `SCORING` 任务并返回 `essayId`，前端进入结果页轮询。
7. 后台任务加载对应 ACTIVE Rubric，构建隔离 prompt，调用 AI。
8. 后端按 Rubric 维度重新计算原生分、换算分和等级，保存 `result_json`。
9. 结果页动态展示维度、证据、建议、片段问题和原文。
10. 历史页展示作文类型、题目摘要、词数、原生分、换算分、等级、置信度、状态、模型。

### 提交请求示例

```json
{
  "essayType": "SENIOR_GAOKAO",
  "taskPrompt": "假定你是李华，请写一封邮件邀请外教参加班级英语角。",
  "content": "Dear Mr. Smith, ...",
  "configId": 1,
  "idempotencyKey": "client-generated-uuid"
}
```

## 作文类型

| code | 中文展示 | 状态 |
|---|---|---|
| `GENERAL` | 通用英语作文 | 可用 |
| `JUNIOR_GENERAL` | 初中英语作文 | 可用 |
| `JUNIOR_ZHONGKAO` | 中考英语作文 | 可用 |
| `SENIOR_GENERAL` | 高中英语作文 | 可用 |
| `SENIOR_GAOKAO` | 高考英语作文 | 可用 |
| `CET4` | 大学英语四级作文 | 可用 |
| `CET6` | 大学英语六级作文 | 可用 |
| `IELTS_TASK_1` | 雅思 Task 1 图表作文 | 可用 |
| `IELTS_TASK_2` | 雅思 Task 2 议论文 | 可用 |
| `TOEFL_INDEPENDENT` | 托福独立写作 | 可用 |
| `TOEFL_INTEGRATED` | 托福综合写作（暂缓开放） | 禁用 |

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
| `POST` | `/api/essays/submit` | 提交作文并异步评分 |
| `GET` | `/api/essays/history?page=0&size=10` | 分页获取历史记录 |
| `GET` | `/api/essays/{id}` | 获取作文详情和动态评分报告 |

## 数据库迁移

当前 Flyway migrations：

```text
V1__init_schema.sql
V2__provider_config_abstraction.sql
V3__replace_legacy_scoring_schema.sql
V4__seed_rubric_profiles.sql
V5__async_scoring_idempotency.sql
```

`V3` 会破坏性重建 `essays` / `essay_scores`，历史旧四维数据不保留。`V4` 创建并 seed Rubric 表。`V5` 增加异步评分和幂等字段/索引。

## 验证命令

```powershell
.\gradlew.bat test
cd frontend
npm run build
```

当前已验证：后端测试通过；前端构建通过。
本地运行验证：后端当前代码可启动，Flyway schema 已到 v5，`/api/essays/history` 返回 200；异步提交流程返回 `SCORING`，重复 `idempotencyKey` 返回同一 `essayId`，同内容 `contentHash` 可复用已完成结果；缺少必填 `taskPrompt` 和疑似 prompt injection 输入会在调用 AI 前返回 400。

### 2026-06-07 验收快照

- API：`/api/essays/history?page=0&size=1` 返回 200。
- 提交链路：新作文 `essayId=2` 从 `SCORING` 轮询到 `COMPLETED`。
- 结果摘要：`GENERAL/GENERAL_V1`，动态维度 4 项，`nativeScoreDisplay=91/100`，`normalizedScore=91`。
- 幂等：同一 `idempotencyKey` 再次提交返回同一 `essayId=2`；同内容不同 key 通过 `contentHash` 复用同一结果。
- 防护：prompt injection 输入、非 `GENERAL` 缺失 `taskPrompt` 均在调用 AI 前返回 400。
- 前端：Playwright 严格验收通过，覆盖 `/submit`、`/history`、`/result/2`，无 Vue warning / pageerror。
- 截图：`C:/tmp/aiwriting-submit.png`、`C:/tmp/aiwriting-history.png`、`C:/tmp/aiwriting-result-2.png`。

## 已决设计边界

- 目标用户包含初中、高中、大学和出国考试用户，因此作文类型必须覆盖不同学段/考试。
- 旧四维字段彻底废除，历史旧数据不做兼容迁移。
- Rubric 和类型特定 prompt instruction 放 DB；安全外壳、输出结构校验、输入防御仍放代码。
- `taskPrompt` 可以是中文，作文正文必须主要为英文。
- 对超长、过短、非英文比例过高、emoji/特殊符号、prompt injection、隐私和高风险敏感内容做 PASS/WARN/REJECT。
- 评分必须异步化，前端显示 `AI Thinking`，后端用 Redis/DB 保证幂等和防重复提交。
- 公开仓库默认连接普通本机 PostgreSQL/Redis；个人 VPS、密码、API Key 只放本地 env。

## 后续计划

- 安全增强：更完整的敏感内容策略运营和可解释安全提示。
- 生产增强：认证/权限、前端 bundle 拆分、Rubric 管理后台。
