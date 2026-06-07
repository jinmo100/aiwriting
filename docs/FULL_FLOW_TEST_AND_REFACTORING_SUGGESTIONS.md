# 完整流程测试记录与优化建议

更新时间：2026-06-07

## 最新结论

当前主链路已升级为动态 Rubric + 异步评分：

```text
Vue 前端（Vite）
  -> /api 代理
  -> POST /api/essays/submit
  -> Spring Boot 后端创建 SCORING 任务
  -> Redis/DB 幂等检查
  -> scoringTaskExecutor 后台评分
  -> PostgreSQL / Flyway Rubric
  -> LangChain4j Provider Adapter
  -> Provider API
  -> RubricScoringResult 入库
  -> 结果页轮询并动态展示
```

当前本地验证范围：

- 后端测试：通过。
- 前端构建：通过。
- 前端页面：提交页、结果页、历史页已适配动态 Rubric。
- 数据库：V3/V4/V5 migrations 已加入并在本地 PostgreSQL 成功迁移到 schema v5，Rubric V1 seed 已覆盖所有已决作文类型。
- 运行验证：后端当前代码可启动，`/api/essays/history` 通过；提交接口先返回 `SCORING`，结果页轮询到 `COMPLETED`。
- 幂等验证：重复 `idempotencyKey` 返回同一 `essayId`；相同内容不同 key 可通过 `contentHash` 复用近期已完成结果。
- 安全验证：缺少必填 `taskPrompt` 和疑似 prompt injection 输入会在调用 AI 前返回 400。

具体数据库地址、Redis 密码、Provider Base URL、模型名和 API Key 属于本地私有配置，不写入公开文档。

## 最新端到端验收快照

验收时间：2026-06-07。

运行环境：

```text
后端：http://127.0.0.1:8080
前端：http://localhost:5173
```

关键结论：

- `GET /api/essays/history?page=0&size=1` 返回 200。
- `POST /api/essays/submit` 新建 `essayId=2`，首个响应为 `SCORING`。
- 结果页轮询后任务完成为 `COMPLETED`。
- 完成结果为 `GENERAL/GENERAL_V1`，动态维度 4 项，`nativeScoreDisplay=91/100`，`normalizedScore=91`。
- 同一 `idempotencyKey` 重复提交返回同一 `essayId=2`。
- 同内容不同 `idempotencyKey` 通过 `contentHash` 复用同一 `essayId=2`。
- prompt injection 样例返回 400；非 `GENERAL` 缺失 `taskPrompt` 返回 400。
- Playwright 严格验收覆盖 `/submit`、`/history`、`/result/2`，无 Vue warning / pageerror。
- 截图留存：`C:/tmp/essay-evaluator-submit.png`、`C:/tmp/essay-evaluator-history.png`、`C:/tmp/essay-evaluator-result-2.png`。

## 启动步骤

### 1. 后端

```powershell
Copy-Item .env.dev.example .env.dev.local
# 编辑 .env.dev.local 后：
.\scripts\start-backend-dev.ps1
```

默认连接本机 PostgreSQL/Redis。远端数据库/Redis 通过 `.env.dev.local` 覆盖；SSH 隧道只在显式加 `-WithTunnel` 时启动。

### 2. 前端

```powershell
cd frontend
npm run dev -- --host 127.0.0.1 --port 5173
```

访问 `http://127.0.0.1:5173`。

## 配置验证规则

Provider 配置应遵守：

- `providerType` 表示协议适配器，不表示品牌。
- `baseUrl` 填 API 根地址，不填具体 endpoint。
- `modelName` 可手动填写，也可通过“获取模型列表”选择。
- 新建配置 API Key 必填；编辑配置 API Key 留空表示保留旧值。
- 普通接口不返回完整 API Key。

## 作文评分 API 验证

### 提交作文

```text
POST /api/essays/submit
```

示例：

```json
{
  "essayType": "IELTS_TASK_2",
  "taskPrompt": "Some people think schools should teach financial skills. To what extent do you agree or disagree?",
  "content": "In modern society, financial literacy has become increasingly important...",
  "configId": 1,
  "idempotencyKey": "client-generated-uuid"
}
```

预期：

- 立即生成 `essayId` / `scoreId`。
- 保存 `essay_type`、`task_prompt`、`word_count`、`char_count`、`idempotency_key`、`content_hash`。
- 初始返回 `scoringStatus=SCORING`，此时 `result_json` 可以为空。
- 后台评分完成后 `scoringStatus=COMPLETED`，保存 `result_json`。
- 完成态返回 `nativeScore`、`normalizedScore`、`rubric`、`gradeLabel`、`confidence`、`dimensions`、`annotations`、`summary`。
- 失败态返回 `scoringStatus=FAILED`、`errorCode`、`errorMessage`。

### 获取历史

```text
GET /api/essays/history?page=0&size=10
```

预期历史列包含：提交时间、作文类型、题目摘要、作文字数、原生分、换算分、等级、置信度、状态、模型。

### 获取详情

```text
GET /api/essays/{id}
```

预期返回 `EssayScoreResponse`，其中 `result` 为动态 `RubricScoringResult`。

## 前端验证步骤

1. 打开 `http://127.0.0.1:5173`。
2. 进入“API配置”，确认默认配置存在且测试通过。
3. 进入“提交作文”。
4. 选择作文类型；除“通用英语作文”外填写题目/任务要求。
5. 输入英语作文正文。
6. 点击“提交评分”，观察 `AI Thinking` 等待提示。
7. 跳转 `/result/{essayId}` 后检查：
   - `SCORING` 时展示 `AI Thinking` 卡片并自动轮询。
   - `COMPLETED` 后展示顶部总览。
   - 动态评分维度。
   - 主要优点。
   - 优先改进建议。
   - 逐句/片段问题。
   - 输入质量/安全提示。
   - 原文。
   - `FAILED` 时展示失败原因和可重试提示。
8. 进入“历史记录”，检查新列展示和筛选。

## 构建验证

```powershell
.\gradlew.bat test
cd frontend
npm run build
```

当前结果：通过。Vite chunk 体积警告属于后续性能优化项。

## 已完成优化

- Provider 抽象层落地。
- API Key AES-GCM 加密存储。
- dev reveal 受环境开关控制。
- 测试连接和测试结构化输出。
- 模型列表拉取与 Redis 缓存。
- Caffeine ChatModel 指纹缓存。
- Redis 配置失效通知。
- Flyway 接管 schema。
- V3 破坏性重建作文/评分表，旧四维废除。
- V4 seed DB Rubric。
- 作文类型中文化和扩展。
- taskPrompt 支持。
- 动态 RubricScoringResult。
- 服务端按维度重新计算原生分/换算分/等级。
- 前端提交、结果、历史页动态化。
- 基础输入防御和安全分析。
- WARN 输入降低 confidence 并在结果页展示提示。
- REJECT 输入不调用 AI。
- V5 异步评分迁移。
- `idempotencyKey` + `contentHash` + Redis/DB 防重复提交。
- 前端 sessionStorage pending submission。
- 结果页 `SCORING` 轮询和 `FAILED` 展示。

## 后续建议

- 继续细化安全策略，减少误伤并增加上下文判断。
- 统一错误响应。
- Rubric 管理后台和版本发布界面。
- 结果页轮询超时/手动刷新提示，后续可升级 SSE/WebSocket。
- 前端性能优化。
