# 英作评析（Essay Evaluator） v2.3

**英作评析（Essay Evaluator）** 是一套基于 **Spring Boot 3.4.2 + Java 21 + Vue 3 + LangChain4j 1.16.1** 的英语作文智能评分系统。当前版本已经从旧的固定四维评分升级为 **作文类型驱动 + DB Rubric + 动态评分报告**，并保留多 Provider AI 配置能力。

## 当前状态

- 后端：Spring Boot 3.4.2、Java 21、MyBatis-Plus、Flyway、PostgreSQL、Redis、LangChain4j。
- 前端：Vue 3、TypeScript、Element Plus、Vite、Pinia、Axios。
- 数据库：PostgreSQL 是 dev/prod 基线；H2 不作为日常开发数据库。
- Schema：Flyway 管理，`src/main/resources/db/init.sql` 仅为 Docker 兼容 no-op。
- 评分：旧四维字段已废除，评分结果统一保存为 `RubricScoringResult` JSON。
- 用户系统：账号密码注册/登录，Redis Session + HttpOnly Cookie；作文、历史和 API 配置按用户隔离。
- 失败治理：AI 调用失败会分类保存，结果页显示中文友好失败原因、尝试次数，并支持可重试失败直接重试。

## 发布部署（推荐给 VPS / 生产）

项目现在提供 GHCR 预构建镜像作为最终发布产物，部署主机不需要安装 Node.js、npm、Gradle 或 JDK：

- 后端镜像：`ghcr.io/jinmo100/essay-evaluator-backend`
- 前端镜像：`ghcr.io/jinmo100/essay-evaluator-frontend`
- 一键启动入口：`docker-compose.release.yml`
- 环境变量模板：`.env.release.example`
- 完整部署说明：`docs/DEPLOYMENT.md`

最短路径：

```bash
cp .env.release.example .env
docker compose -f docker-compose.release.yml --env-file .env pull
docker compose -f docker-compose.release.yml --env-file .env up -d
```

发布版 compose 默认包含 PostgreSQL 和 Redis，且不把数据库/Redis 端口暴露到宿主机；已有 PostgreSQL/Redis 的用户可以按 `docs/DEPLOYMENT.md` 修改 compose 或覆盖连接变量。域名、HTTPS 和反向代理方案由部署者自选，常见做法是设置 `FRONTEND_BIND=127.0.0.1` 后由宿主机反向代理转发。

## 核心功能

- 多 Provider 协议适配：`OPENAI_CHAT_COMPLETIONS`、`OPENAI_RESPONSES`、`ANTHROPIC_MESSAGES`、`GEMINI_GENERATE_CONTENT`。
- API 配置管理：用户私有配置，新增、编辑、删除、设置默认配置、连接测试、结构化输出测试、模型列表拉取。
- API Key 加密存储：新建/更新写入 `api_key_encrypted`；用户可显式查看自己的私有配置完整 Key，接口不缓存、不记录明文日志。
- 作文类型驱动评分：支持通用、初中、中考、高中、高考、CET4、CET6、IELTS Task 1/2、TOEFL Independent；TOEFL Integrated 已 seed 但暂缓开放。
- DB Rubric：`rubric_profiles` / `rubric_versions` / `rubric_dimensions` 保存 ACTIVE 评分标准。
- 动态评分结果：原生分、100 分换算、等级、置信度、动态维度、证据、改进建议、片段问题、批注高亮、输入分析、同水平提升版范文。
- 输入防御：基础 PASS/WARN/REJECT、prompt injection 检测、隐私/高风险敏感内容、emoji/特殊符号/控制字符/零宽字符/非英文比例检查。
- 异步评分与幂等：提交后立即返回 `SCORING`，后台评分；`idempotencyKey` + `contentHash` 通过用户维度 Redis/DB 防重复提交。
- AI 调用透明度：评分调用写入 `ai_invocation_logs`，结果页展示真实 Provider、Endpoint、Model、Token、AI Thinking 用时和可选预计费用。
- 失败分类与重试：区分配置错误、Provider 超时/限流/拒绝、AI 响应格式异常、内容拒绝等；可重试失败最多重试 3 次。
- 评分一致性基准集：内置 24 篇离线样例（中考/高考加权），可生成不调用真实 Provider 的软门禁报告。
- 多版本修改闭环：支持基于历史作文提交修改版，保存 `essayGroupId` / `versionNo` / `parentEssayId`。
- 个人学习看板：展示提交总数、完成/失败/评分中数量、平均分、最高分、近 7/30/90 天提交数和作文类型分布。
- 前端中文友好：中文作文类型、题目/任务要求输入、AI Thinking 等待提示、动态维度结果页、增强历史页。

## 本地开发启动

### 1. 准备 PostgreSQL / Redis

默认开发配置连接本机服务：

```text
PostgreSQL: localhost:5432 / database=essay_evaluator / user=essay_evaluator
Redis:      redis://localhost:6379/0
```

### 2. 准备本地 env 文件

```powershell
Copy-Item .env.dev.example .env.dev.local
```

编辑 `.env.dev.local`，填入本机真实值，例如 `DEV_DB_PASSWORD`、`DEV_REDIS_URL`、`ESSAY_EVALUATOR_SECRET_KEY`。该文件已被 `.gitignore` 忽略，不能提交真实密码、API Key、远端地址或 SSH Key 路径。

如需做真实 Provider 的本地验收，请在 `.env.dev.local` 额外填写测试专用配置，避免使用假 key 或本地 stub 掩盖问题：

```env
E2E_PROVIDER_TYPE=OPENAI_CHAT_COMPLETIONS
E2E_PROVIDER_BASE_URL=https://your-provider.example/v1
E2E_PROVIDER_API_KEY=your-test-key
E2E_PROVIDER_MODEL=your-test-model
```

运行验收约束：

- 可以使用 `.env.dev.local` 中的真实数据库、Redis 和 Provider Key；当前开发数据不重要。
- 不要为了跑通验收自行安装本机 PostgreSQL/Redis，也不要绕开 `.env.dev.local` 临时搭替代运行时。
- Provider 相关验收优先使用 `.env.dev.local` 中的 `E2E_PROVIDER_*` 真实测试配置；不要用本地 stub 替代“获取模型列表 / 测试连接 / 结构化输出 / 实际评分”链路。
- 如果 `.env.dev.local` 指向的服务不可达，应优先启动/修复对应真实服务或 SSH 隧道，并明确报告阻塞。

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

1. 注册并登录账号。
2. 在“API配置”中新增或确认当前账号的默认配置。
3. 进入“提交作文”。
4. 选择作文类型；除“通用英语作文”外，需要填写 `taskPrompt`。
5. 输入主要由英文构成的作文正文。
6. 提交后页面显示 `AI Thinking` 等待态。
7. 后端创建 `SCORING` 任务并返回 `essayId`，前端进入结果页轮询。
8. 后台任务加载对应 ACTIVE Rubric，构建隔离 prompt，调用 AI。
9. 后端按 Rubric 维度重新计算原生分、换算分和等级，保存 `result_json`。
10. 结果页动态展示维度、证据、建议、片段问题、原文高亮、同水平提升版范文、模型/Token/费用明细和原文。
11. 如果后台评分失败，结果页展示错误类型、中文说明、尝试次数；可重试失败可直接重新进入 `AI Thinking`。
12. 用户可在结果页点击“提交修改版”，新提交会作为同一作文组的 v2/v3 保存。
13. 历史页只展示当前用户的作文记录和版本号。

### 提交请求示例

```json
{
  "essayType": "SENIOR_GAOKAO",
  "taskPrompt": "假定你是李华，请写一封邮件邀请外教参加班级英语角。",
  "content": "Dear Mr. Smith, ...",
  "configId": 1,
  "idempotencyKey": "client-generated-uuid",
  "parentEssayId": null
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

### 用户认证

| 方法 | 路径 | 功能 |
|---|---|---|
| `POST` | `/api/auth/register` | 注册并登录 |
| `POST` | `/api/auth/login` | 登录 |
| `POST` | `/api/auth/logout` | 登出 |
| `GET` | `/api/auth/me` | 获取当前用户/登录态 |

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
| `POST` | `/api/essays/{id}/retry` | 重试可重试的失败评分任务 |
| `GET` | `/api/essays/history?page=0&size=10` | 分页获取历史记录 |
| `GET` | `/api/essays/{id}` | 获取作文详情和动态评分报告 |

### 学习看板

| 方法 | 路径 | 功能 |
|---|---|---|
| `GET` | `/api/dashboard/summary` | 获取当前用户学习看板汇总 |

## 数据库迁移

当前 Flyway migrations：

```text
V1__init_schema.sql
V2__provider_config_abstraction.sql
V3__replace_legacy_scoring_schema.sql
V4__seed_rubric_profiles.sql
V5__async_scoring_idempotency.sql
V6__users_and_user_scoped_business_data.sql
V7__ai_invocation_logs.sql
V8__scoring_failure_detail_and_retry.sql
V9__essay_versioning.sql
```

`V3` 会破坏性重建 `essays` / `essay_scores`，历史旧四维数据不保留。`V4` 创建并 seed Rubric 表。`V5` 增加异步评分和幂等字段/索引。`V6` 增加用户系统，清空旧业务数据，并将作文、配置和幂等索引切到用户维度。`V7` 增加 `ai_invocation_logs`，保存评分调用的安全化观测字段和 token/cost 统计。`V8` 增加 `attempt_count` 和 `failure_detail_json`，支持失败分类与受控重试。`V9` 增加作文版本链字段，支持修改版闭环。

## 验证命令

```powershell
.\gradlew.bat test
.\gradlew.bat test --tests com.jinmo.essayevaluator.release.ReleasePackagingTest
.\gradlew.bat scoringBenchmarkReport
cd frontend
npm run build
```

当前已验证：后端测试通过；发布包装契约测试通过；评分基准软门禁报告可生成；前端构建通过；v2.3 运行 smoke test 通过。
本地运行验证：后端当前代码可启动；异步提交流程返回 `SCORING`，重复 `idempotencyKey` 返回同一 `essayId`，同内容 `contentHash` 可复用已完成结果；修改版提交流程只用 `idempotencyKey` 防重复点击，不用 `contentHash` 误命中旧版本；缺少必填 `taskPrompt` 和疑似 prompt injection 输入会在调用 AI 前返回 400。当前迁移已到 `V9`。

### 2026-06-08 v2.3 运行验收快照

本次 smoke test 使用临时本机运行时，不读取或打印 `.env.dev.local` 中的真实 Provider Key / 数据库密码：

- 纠偏说明：这次临时运行时已经清理；后续运行验收不得沿用这种做法，必须使用 `.env.dev.local` 指向的真实数据库/Redis/Provider 配置。
- PostgreSQL 18.4：临时数据目录 `build/runtime-smoke/pgdata`，监听 `127.0.0.1:55432`。
- Redis 8.8：临时监听 `127.0.0.1:56379`。
- 后端：通过临时 `DEV_DB_*` / `DEV_REDIS_URL` 环境变量启动，Flyway 成功迁移到 `V9`，`GET /api/auth/me` 返回 200。
- 前端：`npm.cmd run dev -- --host 127.0.0.1 --port 5173` 启动成功，`GET /login` 返回 200。
- 认证链路：`POST /api/auth/register` 成功创建 smoke 用户，随后同一 cookie session 下 `GET /api/auth/me` 返回 `authenticated=true`。
- 用户隔离基础接口：登录态下 `GET /api/dashboard/summary` 返回空账号汇总，`GET /api/configs` 返回当前用户空配置列表。
- 未调用真实 AI Provider；作文评分端到端仍需要用户先配置自己的 Provider/API Key。

### 2026-06-07 验收快照

- API：`/api/essays/history?page=0&size=1` 返回 200。
- 提交链路：新作文 `essayId=2` 从 `SCORING` 轮询到 `COMPLETED`。
- 结果摘要：`GENERAL/GENERAL_V1`，动态维度 4 项，`nativeScoreDisplay=91/100`，`normalizedScore=91`。
- 幂等：同一 `idempotencyKey` 再次提交返回同一 `essayId=2`；同内容不同 key 通过 `contentHash` 复用同一结果。
- 防护：prompt injection 输入、非 `GENERAL` 缺失 `taskPrompt` 均在调用 AI 前返回 400。
- 前端：Playwright 严格验收通过，覆盖 `/submit`、`/history`、`/result/2`，无 Vue warning / pageerror。
- 截图：`C:/tmp/essay-evaluator-submit.png`、`C:/tmp/essay-evaluator-history.png`、`C:/tmp/essay-evaluator-result-2.png`。

## 已决设计边界

- 目标用户包含初中、高中、大学和出国考试用户，因此作文类型必须覆盖不同学段/考试。
- 旧四维字段彻底废除，历史旧数据不做兼容迁移。
- Rubric 和类型特定 prompt instruction 放 DB；安全外壳、输出结构校验、输入防御仍放代码。
- `taskPrompt` 可以是中文，作文正文必须主要为英文。
- 对超长、过短、非英文比例过高、emoji/特殊符号、prompt injection、隐私和高风险敏感内容做 PASS/WARN/REJECT。
- 评分必须异步化，前端显示 `AI Thinking`，后端用 Redis/DB 保证幂等和防重复提交。
- 公开仓库默认连接普通本机 PostgreSQL/Redis；个人 VPS、密码、API Key 只放本地 env。

## 后续计划

下一阶段优先做产品化底座和评分可信度增强：

1. 用户系统阶段 1 已落地：账号密码、Redis Session + HttpOnly Cookie、开放注册、USER/ADMIN、基础限流。
2. 用户数据隔离阶段 1 已落地：作文、历史、API 配置按当前用户隔离；不保留匿名评分兼容。
3. 用户自带模型配置透明化阶段 1 已落地：展示真实 Provider、Endpoint、Model、Token 消耗；费用由用户配置单价后估算。
4. AI 调用日志阶段 1 已落地：评分调用明细、Token 来源、估算/精确标记和预计费用；Provider 测试日志后续补齐。
5. AI 失败分类与重试阶段 1 已落地：结构化失败详情、中文提示、可重试状态、最多 3 次重试。
6. 结果页体验阶段 1 已落地：AI Thinking、手动刷新、失败重试、尝试次数、模型/Token/耗时展示；轮询超时与推送后续补齐。
7. 评分一致性基准集阶段 1 已落地：24 篇离线样例、类型分布测试、`scoringBenchmarkReport` 软门禁报告；真实 Provider 回放后续接入。
8. 逐句批注与参考范文阶段 1 已部分落地：`quote/original/context` 高亮定位、未定位建议列表、同水平提升版范文展示；复制建议/标记状态后续补齐。
9. 多版本作文修改闭环阶段 1 已落地：`parentEssayId` 提交修改版、`essayGroupId/versionNo` 保存版本链、历史/结果页显示版本；前后对比后续补齐。
10. 个人学习进步 dashboard 阶段 1 已落地：总提交、完成/失败/评分中、平均分、最高分、近 7/30/90 天提交数、类型分布；高频错误和薄弱维度后续补齐。
