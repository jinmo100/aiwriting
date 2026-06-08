# 英作评析项目现状报告

更新时间：2026-06-08

## 总体结论

当前项目处于 **v2.3 用户系统、调用透明度与失败重试阶段 1 已落地**：

- 后端：Spring Boot 3.4.2 + Java 21 + MyBatis-Plus + Flyway + PostgreSQL + Redis + LangChain4j。
- 前端：Vue 3 + TypeScript + Element Plus + Vite。
- AI 调用：通过 Provider Adapter 抽象支持多协议 Provider。
- 评分结构：旧四维字段已废除，统一使用 `RubricScoringResult`。
- Rubric：评分标准保存在 DB，使用 `DRAFT/ACTIVE/ARCHIVED` 版本模型；当前由 Flyway seed V1 ACTIVE。
- 评分执行：提交接口已异步化，先返回 `SCORING`，后台完成后结果页轮询展示。
- 幂等：`idempotencyKey` + `contentHash` 使用用户维度 Redis 快速缓存和 PostgreSQL 唯一索引兜底。
- 用户系统：账号密码注册/登录，Redis Session + HttpOnly Cookie，业务接口要求登录。
- AI 调用日志：评分调用写入 `ai_invocation_logs`，结果页展示真实 Provider、Endpoint、Model、Token、usageSource、AI Thinking 用时和可选预计费用。
- 失败治理：AI 评分失败按类型分类，保存结构化失败详情；结果页展示中文友好错误、尝试次数，并允许可重试失败直接重试。
- 评分可信度：已建立 24 篇离线评分一致性基准集和软门禁报告生成任务，后续用于 Prompt/Rubric/Provider 变更回归。
- 学习反馈：评分结果结构已支持 `annotations[].quote` 和 `referenceEssay`；结果页可对可定位片段高亮，并展示同水平提升版范文。
- 修改闭环：已支持 `parentEssayId` 提交修改版，作文保存 `essayGroupId` / `versionNo` / `parentEssayId` 版本链。
- 学习看板：已新增 `/api/dashboard/summary` 和前端 `/dashboard`，展示个人提交、分数、近期活跃和类型分布。

## 已实现能力

### Provider 抽象

支持：

```text
OPENAI_CHAT_COMPLETIONS
OPENAI_RESPONSES
ANTHROPIC_MESSAGES
GEMINI_GENERATE_CONTENT
```

业务层通过 `AIProviderAdapter` / `AIProviderResult` 调用模型，不直接暴露 LangChain4j 具体实现。

### 用户系统与访问控制

已新增：

- `users` 表，支持 `USER` / `ADMIN`、`ACTIVE` / `DISABLED`。
- `POST /api/auth/register`、`POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/auth/me`。
- Spring Security + Redis Session + HttpOnly Cookie。
- 注册、登录、API Key reveal、Provider 测试、作文提交接入 Redis 轻量限流。
- 前端登录/注册/个人中心、Axios `withCredentials`、路由守卫。
- 评分、历史、配置、Provider 测试均要求登录；匿名评分不再兼容。

### API Key 安全

- 新增/更新配置时写入 `api_key_encrypted`。
- 旧 `api_key` 字段仅兼容读取。
- 普通列表/详情只返回 `hasApiKey` 和 `apiKeyPreview`。
- 用户可显式 reveal 自己的 `PRIVATE` 配置完整 Key；普通用户不能 reveal 他人或公共配置 Key；响应使用 `Cache-Control: no-store`。

### Rubric 动态评分

当前流程：

```text
EssayController
  -> EssayService 创建 SCORING 任务
  -> Redis/DB 幂等检查
  -> scoringTaskExecutor 后台执行
  -> RubricService 加载 ACTIVE Rubric
  -> AIService 构建 safety shell + DB rubric prompt
  -> ProviderAdapterRegistry
  -> AIProviderAdapter.generate()
  -> ScoringResultValidator.validateAndNormalize()
  -> repair retry（必要时一次）
  -> 保存 Essay / EssayScore.result_json / scoring_status
```

保存信息包括：

- `essay_type`
- `task_prompt`
- `content`
- `word_count` / `char_count`
- `idempotency_key` / `content_hash`
- `essay_group_id` / `version_no` / `parent_essay_id`
- `rubric_type` / `rubric_version`
- `native_score` / `native_score_display`
- `normalized_score`
- `grade_label`
- `confidence_level`
- `result_json`
- 模型名、tokens、处理耗时、状态、错误码/错误信息、尝试次数、结构化失败详情

### AI 调用日志与用量透明度

已新增：

- `ai_invocation_logs`：保存评分链路每次 Provider 调用的安全化观测字段。
- `AIService.ProviderInvocation`：区分 `SCORING` / `JSON_REPAIR`。
- 调用日志记录 `attempt_no`，重试后的调用归入对应尝试次数。
- Provider 返回 token usage 时标记 `PROVIDER`；未返回时本地粗略估算并标记 `LOCAL_ESTIMATE`。
- API 配置支持可选 `inputTokenPricePerMillion`、`outputTokenPricePerMillion`、`currency`。
- 结果页展示 Provider、Endpoint、Model、输入/输出/总 Token、调用次数、AI Thinking 用时和预计费用；可折叠查看调用明细。
- 当前阶段先覆盖作文评分调用；Provider 连接测试/模型拉取调用日志后续补齐。

### 前端体验

- 中文作文类型选择。
- 除 `GENERAL` 外强制填写题目/任务要求。
- `TOEFL_INTEGRATED` 灰显禁用。
- 提交时展示 `AI Thinking` 等待文案。
- 结果页按动态维度展示分数、证据、建议、片段问题、输入质量提示、AI 调用用量和原文。
- 原文区根据 `quote` / `original` / `context` 做 exact / case-insensitive / whitespace-normalized 匹配高亮；无法定位的建议保留在批注列表。
- 支持展示 `referenceEssay` 同水平提升版范文及说明 notes。
- 失败页展示错误类型、中文错误、可重试状态、尝试次数；可重试失败可点击重试重新进入 `AI Thinking`。
- 历史页展示提交时间、作文类型、题目摘要、字数、原生分、换算分、等级、置信度、状态、模型。

### 输入防御与安全分析

已接入基础规则：

- `EssayInputAnalyzer`：类型可用性、`taskPrompt` 必填、词数/字符硬上限、明显过短、emoji、特殊符号、控制字符、零宽字符、异常重复字符、非英文比例。
- `SafetyAnalyzer`：prompt injection、身份证/银行卡/手机号/邮箱、部分高风险自伤/违法/未成年人安全/学术诚信/仇恨煽动规则。
- REJECT：不调用 AI，直接返回业务错误。
- WARN：继续评分，但写入 `inputAnalysis` / `safetyNotice`，并降低 confidence。

### 评分一致性基准集

已新增：

- `src/test/resources/benchmark/scoring-baseline-v1.json`：24 篇离线样例。
- 分布：中考 6、高考 6、CET4 3、CET6 3、IELTS Task 2 3、GENERAL 3。
- 每篇样例包含 `essayType`、`taskPrompt`、作文正文、期望 100 分换算区间、等级区间和关注标签。
- `ScoringBenchmarkDatasetTest` 固化样例数量、类型分布、题目/正文/期望区间质量。
- `.\gradlew.bat scoringBenchmarkReport` 生成 `build/reports/scoring-benchmark/report.md`。
- 当前阶段是软门禁：不会调用真实 Provider，不消耗用户 API Key，不因模型分数波动阻断 CI。

### 多版本作文修改闭环

阶段 1 已新增：

- `EssaySubmitRequest.parentEssayId`：从结果页“提交修改版”进入提交页时携带。
- `essays.essay_group_id`：同一题目/同一作文修改链的分组 id。
- `essays.version_no`：同组内版本号，根作文为 v1，修改版递增。
- `essays.parent_essay_id`：直接来源版本。
- 修改版仍使用 `idempotencyKey` 防重复点击，但不会使用 `contentHash` 命中旧版本，避免同内容修改版被误复用。
- 历史页和结果页展示版本号；前后对比视图后续补齐。

### 个人学习看板

阶段 1 已新增：

- `GET /api/dashboard/summary`：当前用户学习汇总。
- 指标：总提交、已完成、失败、评分中、平均 100 分换算、最高分。
- 活跃度：近 7/30/90 天提交数量。
- 类型分布：按作文类型统计数量。
- 前端 `/dashboard` 页面和导航入口。
- 后续补齐：高频错误、薄弱 Rubric 维度、分数趋势图、最近提升点。

### 异步评分与幂等

已实现：

- `POST /api/essays/submit` 创建 `SCORING` 记录后立即返回。
- 事务提交后由 `scoringTaskExecutor` 后台执行 AI 评分。
- `essay_scores.result_json` 允许为空；完成后写入完整 `RubricScoringResult`。
- `idempotency_key` 唯一索引兜底同 key 重复提交。
- `content_hash` 记录同内容提交，近期相同内容会复用正在评分或已完成记录。
- Redis key：
  - `essay-evaluator:user:{userId}:idempotency:{idempotencyKey}`
  - `essay-evaluator:user:{userId}:content-submission:{contentHash}`
- Redis 不可用默认降级 DB，不向用户暴露 Redis 故障。
- 前端提交生成 `idempotencyKey`，结果页对 `SCORING` 状态自动轮询。
- `POST /api/essays/{id}/retry` 仅允许当前用户的 `FAILED` 评分重试；可重试错误最多 3 次。

## 作文类型

```text
GENERAL
JUNIOR_GENERAL
JUNIOR_ZHONGKAO
SENIOR_GENERAL
SENIOR_GAOKAO
CET4
CET6
IELTS_TASK_1
IELTS_TASK_2
TOEFL_INDEPENDENT
TOEFL_INTEGRATED（seed 但禁用）
```

## 数据库与迁移

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

`V3` 破坏性重建作文和评分表，旧四维历史数据不保留。`V4` 创建 Rubric 表并写入 V1 ACTIVE 评分标准。`V5` 增加异步评分和幂等支持：`idempotency_key`、`content_hash`、`essay_scores.updated_at`、`result_json` 可空和相关索引。`V6` 增加用户系统，清空旧业务数据，并将 `api_configs`、`essays` 和幂等唯一索引切到用户维度。`V7` 增加 AI 调用日志表，用于用户侧 token/cost 展示和后续调用观测。`V8` 为 `essay_scores` 增加 `attempt_count` 与 `failure_detail_json`，支持失败分类和受控重试。`V9` 为 `essays` 增加版本链字段，支持修改版闭环。

`src/main/resources/db/init.sql` 是 Docker 兼容 no-op；schema 只由 Flyway 管理。

## 开发环境

```powershell
.\scripts\start-backend-dev.ps1
cd frontend
npm run dev -- --host 127.0.0.1 --port 5173
```

默认数据服务：

```text
PostgreSQL: localhost:5432
Redis:      redis://localhost:6379/0
```

远端数据库/Redis 通过 `.env.dev.local` 覆盖；SSH 隧道需要显式启用。

## 当前验证

```powershell
.\gradlew.bat test
.\gradlew.bat scoringBenchmarkReport
cd frontend
npm run build
```

当前结果：

- 后端测试通过。
- 评分一致性基准报告生成通过。
- 前端构建通过。
- 后端当前代码可启动。
- 当前迁移已到 `V9`，schema 由 Flyway 管理。
- `/api/essays/history` 返回 200。
- 异步提交流程返回 `SCORING`，重复 `idempotencyKey` 返回同一 `essayId`。
- 同内容不同 `idempotencyKey` 会通过 `contentHash` 复用已完成结果。
- 缺少必填 `taskPrompt` 的非 GENERAL 类型提交会在调用 AI 前返回 400。
- 疑似 prompt injection 输入会在调用 AI 前返回 400。

Vite chunk 体积警告属于性能优化项，不阻塞功能。

### 2026-06-07 最新验收快照

后端运行地址：`http://127.0.0.1:8080`；前端运行地址：`http://localhost:5173`。

- API 健康度：`GET /api/essays/history?page=0&size=1` 返回 200。
- 新提交样例：`essayId=2`，初始状态 `SCORING`，结果页轮询后变为 `COMPLETED`。
- 评分结果：`rubricType=GENERAL`，`rubricVersion=GENERAL_V1`，动态维度 4 项，`nativeScoreDisplay=91/100`，`normalizedScore=91`。
- 幂等验证：同一 `idempotencyKey` 重复请求返回 `essayId=2`；同内容不同 key 通过 `contentHash` 复用 `essayId=2`。
- 输入防御验证：prompt injection 样例返回 400；非 `GENERAL` 缺失 `taskPrompt` 返回 400，且均未进入 AI 调用。
- 前端严格验收：Playwright 覆盖提交页、历史页、结果页；验证高考类型可选、TOEFL Integrated 暂缓开放、历史记录和结果页动态字段展示正常；无 Vue warning / pageerror。
- 验收截图：`C:/tmp/essay-evaluator-submit.png`、`C:/tmp/essay-evaluator-history.png`、`C:/tmp/essay-evaluator-result-2.png`。

## 已决事项边界

- 初中/高中用户是核心用户之一，作文类型必须覆盖初中、中考、高中、高考。
- 旧四维字段彻底废除，现有少量历史数据可删除，不做兼容层。
- Rubric 和类型特定 prompt instruction 进入 DB；安全外壳、输出 schema、输入防御和分数重算留在代码。
- `taskPrompt` 面向中文用户可用中文；作文正文必须主要由英文构成。
- 对恶意或异常输入采用 PASS/WARN/REJECT：过长/过短、非英文比例、emoji/特殊符号、prompt injection、隐私和高风险敏感内容。
- 异步评分是主流程，前端使用 `AI Thinking` 等待态；幂等依赖用户维度 Redis 快速缓存和 PostgreSQL 兜底。
- 公开仓库不保存个人 VPS/隧道/密码/API Key/JDK 路径，默认配置面向普通本机开发。

## 下一阶段已决优先级（2026-06-08）

下一阶段以 **用户系统 + 数据隔离 + 调用透明度 + 评分可信度** 为主线，优先级如下：

1. 最小用户系统阶段 1 已落地：注册、登录、登出、当前用户，Redis Session + HttpOnly Cookie，USER/ADMIN，基础限流。
2. 业务数据按用户隔离阶段 1 已落地：作文、评分、配置按当前用户过滤；幂等键改为用户内唯一。
3. 用户自带模型配置透明化阶段 1 已落地：私有配置为主，预留管理员 PUBLIC 配置；无可用配置时禁止提交并引导配置；允许用户显式查看自己的完整 API Key。
4. AI 调用日志与 token/cost 展示阶段 1 已落地：评分调用记录 provider、endpoint、model、tokens、latency、requestId、usageSource、estimatedCost；用户侧展示汇总和安全明细。
5. AI 失败分类与重试阶段 1 已落地：结构化 failure detail，中文友好错误；仅允许 FAILED 同作文重试，最多 3 次。
6. 结果页异步体验阶段 1 已部分落地：AI Thinking、手动刷新、失败重试、尝试次数和 token/耗时展示；轮询超时提示和 SSE/WebSocket 推送后续补齐。
7. 评分一致性基准集阶段 1 已落地：第一版 24 篇，中考/高考加权；软门禁生成报告，暂不硬阻断 CI，后续接入真实 Provider 回放。
8. 逐句批注与参考范文阶段 1 已部分落地：quote 匹配高亮、未定位建议列表、一个同水平提升版范文；复制建议/标记状态后续补齐。
9. 多版本作文修改闭环阶段 1 已落地：同题再次提交为新版本，保存版本链并展示版本号；前后分数、维度、错误变化对比后续补齐。
10. 个人学习进步 dashboard 阶段 1 已落地：提交数、平均/最高分、7/30/90 天活跃、作文类型分布；高频错误、薄弱维度和趋势图后续补齐，教师/班级后置。

## 仍需关注

- P1：继续细化安全策略，降低误伤并补充更多上下文判断。
- P2：统一错误响应为 `ApiResponse` 风格。
- P1：Provider 测试/模型拉取调用日志补齐。
- P2：Rubric 管理后台与版本发布流程。
- P3：Element Plus 按需导入和 Vite manualChunks。
