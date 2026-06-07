# AI 英语作文评分系统项目现状报告

更新时间：2026-06-07

## 总体结论

当前项目处于 **v2.1 Rubric 动态评分阶段 1/2/3 已落地**：

- 后端：Spring Boot 3.4.2 + Java 21 + MyBatis-Plus + Flyway + PostgreSQL + Redis + LangChain4j。
- 前端：Vue 3 + TypeScript + Element Plus + Vite。
- AI 调用：通过 Provider Adapter 抽象支持多协议 Provider。
- 评分结构：旧四维字段已废除，统一使用 `RubricScoringResult`。
- Rubric：评分标准保存在 DB，使用 `DRAFT/ACTIVE/ARCHIVED` 版本模型；当前由 Flyway seed V1 ACTIVE。
- 评分执行：提交接口已异步化，先返回 `SCORING`，后台完成后结果页轮询展示。
- 幂等：`idempotencyKey` + `contentHash` 使用 Redis 快速缓存和 PostgreSQL 兜底。

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

### API Key 安全

- 新增/更新配置时写入 `api_key_encrypted`。
- 旧 `api_key` 字段仅兼容读取。
- 普通接口只返回 `hasApiKey` 和 `apiKeyPreview`。
- 完整 Key reveal 受 `aiwriting.security.allow-api-key-reveal` 控制。

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
- `rubric_type` / `rubric_version`
- `native_score` / `native_score_display`
- `normalized_score`
- `grade_label`
- `confidence_level`
- `result_json`
- 模型名、tokens、处理耗时、状态、错误码/错误信息

### 前端体验

- 中文作文类型选择。
- 除 `GENERAL` 外强制填写题目/任务要求。
- `TOEFL_INTEGRATED` 灰显禁用。
- 提交时展示 `AI Thinking` 等待文案。
- 结果页按动态维度展示分数、证据、建议、片段问题、输入质量提示和原文。
- 历史页展示提交时间、作文类型、题目摘要、字数、原生分、换算分、等级、置信度、状态、模型。

### 输入防御与安全分析

已接入基础规则：

- `EssayInputAnalyzer`：类型可用性、`taskPrompt` 必填、词数/字符硬上限、明显过短、emoji、特殊符号、控制字符、零宽字符、异常重复字符、非英文比例。
- `SafetyAnalyzer`：prompt injection、身份证/银行卡/手机号/邮箱、部分高风险自伤/违法/未成年人安全/学术诚信/仇恨煽动规则。
- REJECT：不调用 AI，直接返回业务错误。
- WARN：继续评分，但写入 `inputAnalysis` / `safetyNotice`，并降低 confidence。

### 异步评分与幂等

已实现：

- `POST /api/essays/submit` 创建 `SCORING` 记录后立即返回。
- 事务提交后由 `scoringTaskExecutor` 后台执行 AI 评分。
- `essay_scores.result_json` 允许为空；完成后写入完整 `RubricScoringResult`。
- `idempotency_key` 唯一索引兜底同 key 重复提交。
- `content_hash` 记录同内容提交，近期相同内容会复用正在评分或已完成记录。
- Redis key：
  - `aiwriting:idempotency:{idempotencyKey}`
  - `aiwriting:content-submission:{contentHash}`
- Redis 不可用默认降级 DB，不向用户暴露 Redis 故障。
- 前端提交生成 `idempotencyKey`，结果页对 `SCORING` 状态自动轮询。

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
```

`V3` 破坏性重建作文和评分表，旧四维历史数据不保留。`V4` 创建 Rubric 表并写入 V1 ACTIVE 评分标准。`V5` 增加异步评分和幂等支持：`idempotency_key`、`content_hash`、`essay_scores.updated_at`、`result_json` 可空和相关索引。

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
cd frontend
npm run build
```

当前结果：

- 后端测试通过。
- 前端构建通过。
- 后端当前代码可启动。
- Flyway 已在本地 PostgreSQL 成功迁移到 schema v5。
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
- 验收截图：`C:/tmp/aiwriting-submit.png`、`C:/tmp/aiwriting-history.png`、`C:/tmp/aiwriting-result-2.png`。

## 已决事项边界

- 初中/高中用户是核心用户之一，作文类型必须覆盖初中、中考、高中、高考。
- 旧四维字段彻底废除，现有少量历史数据可删除，不做兼容层。
- Rubric 和类型特定 prompt instruction 进入 DB；安全外壳、输出 schema、输入防御和分数重算留在代码。
- `taskPrompt` 面向中文用户可用中文；作文正文必须主要由英文构成。
- 对恶意或异常输入采用 PASS/WARN/REJECT：过长/过短、非英文比例、emoji/特殊符号、prompt injection、隐私和高风险敏感内容。
- 异步评分是主流程，前端使用 `AI Thinking` 等待态；幂等依赖 Redis 快速缓存和 PostgreSQL 兜底。
- 公开仓库不保存个人 VPS/隧道/密码/API Key/JDK 路径，默认配置面向普通本机开发。

## 仍需关注

- P1：继续细化安全策略，降低误伤并补充更多上下文判断。
- P2：统一错误响应为 `ApiResponse` 风格。
- P2：Rubric 管理后台与版本发布流程。
- P3：Element Plus 按需导入和 Vite manualChunks。
