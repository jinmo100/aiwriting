# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) and Codex-like agents when working with code in this repository.

## 项目概述

**英作评析（Essay Evaluator）** 是一个基于 **Spring Boot 3.4.2 + Java 21 + Vue 3 + LangChain4j** 的英语作文智能评分系统。当前项目使用 **PostgreSQL + Redis**，通过 Flyway 管理 schema，并通过多 Provider 抽象层调用不同 AI API。

评分系统已从旧的固定四维字段升级为 **作文类型驱动 + DB Rubric + 动态 RubricScoringResult**：

```text
EssayType + taskPrompt + ACTIVE DB Rubric + dimensions + annotations + nativeScore + normalizedScore
```

旧字段已废除，不要在新业务中使用：

```text
overallScore
contentScore
languageScore
structureScore
coherenceScore
detailedFeedback
```

## 核心技术栈

- Java 21（Gradle Toolchain；不要写入个人 JDK 路径）
- Spring Boot 3.4.2
- MyBatis-Plus 3.5.9
- Flyway
- PostgreSQL（dev/prod 基线）
- Redis / Spring Data Redis
- LangChain4j 1.16.1
- Vue 3 + TypeScript + Element Plus + Vite

## 常用命令

### 后端

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
.\scripts\start-backend-dev.ps1
```

### 前端

```powershell
cd frontend
npm run dev -- --host 127.0.0.1 --port 5173
npm run build
```

### 访问

```text
后端：http://127.0.0.1:8080
前端：http://127.0.0.1:5173
Swagger：http://127.0.0.1:8080/swagger-ui.html
```

## 配置与安全

- `.env.dev.local` 保存本机真实密码、Redis URL、Provider API Key、`ESSAY_EVALUATOR_SECRET_KEY`；该文件已被 `.gitignore` 忽略。
- 仓库公开，不要提交个人 VPS、私钥路径、真实密码、真实 API Key 或个人 JDK 路径。
- 开发默认连接本机 PostgreSQL/Redis；SSH 隧道只是显式可选方案。
- H2 不作为开发或运行数据库；不要恢复 H2 内存库链路。
- `src/main/resources/db/init.sql` 是 Docker 兼容 no-op；schema 由 Flyway migrations 管理。

## 数据库迁移

当前迁移：

```text
V1__init_schema.sql
V2__provider_config_abstraction.sql
V3__replace_legacy_scoring_schema.sql
V4__seed_rubric_profiles.sql
V5__async_scoring_idempotency.sql
```

`V3` 破坏性重建 `essays` / `essay_scores`，旧历史数据不保留。`V4` 创建并 seed：

```text
rubric_profiles
rubric_versions
rubric_dimensions
```

`V5` 增加异步评分和幂等支持：`idempotency_key`、`content_hash`、`essay_scores.updated_at`，并允许 `SCORING` 状态下 `result_json` 为空。

## 作文类型

稳定 type code：

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
TOEFL_INTEGRATED
```

规则：

- `TOEFL_INTEGRATED` seed 但禁用。
- 除 `GENERAL` 外，`taskPrompt` 必填。
- `taskPrompt` 可以是中文；`essayContent` 必须主要是英文。
- 已接入输入防御和安全分析：过短/过长、非英文比例、emoji/特殊符号、控制字符、零宽字符、prompt injection、隐私和高风险敏感内容。

## 关键后端结构

```text
controller/EssayController.java
service/EssayService.java
service/RubricService.java
service/analysis/*
service/idempotency/*
ai/AIService.java
ai/ScoringResultValidator.java
ai/prompt/ScoringPrompt.java
domain/dto/RubricScoringResult.java
domain/enums/EssayType.java
domain/entity/Rubric*.java
mapper/Rubric*.java
```

### 评分链路

```text
EssayController
  -> EssayService 创建 SCORING 任务
  -> Redis/DB 幂等检查
  -> scoringTaskExecutor 后台执行
  -> RubricService.getActiveRubric()
  -> AIService.scoreEssay()
  -> ProviderAdapterRegistry
  -> AIProviderAdapter.generate()
  -> ScoringResultValidator.validateAndNormalize()
  -> essay_scores.result_json / scoring_status
```

AI 可以给出维度分和反馈，但后端会按 DB Rubric 重新计算：

- `nativeScore`
- `normalizedScore`
- `gradeLabel`
- 维度 label/maxScore/rubric 信息

## API

### 提交作文评分

```text
POST /api/essays/submit
```

```json
{
  "essayType": "SENIOR_GAOKAO",
  "taskPrompt": "题目/任务要求",
  "content": "English essay content",
  "configId": 1,
  "idempotencyKey": "client-generated-uuid"
}
```

### 历史记录

```text
GET /api/essays/history?page=0&size=10
```

### 详情

```text
GET /api/essays/{id}
```

## 当前验收快照

- `./gradlew.bat test` 通过。
- `cd frontend && npm run build` 通过。
- 后端可启动，Flyway schema 已到 v5。
- `/api/essays/history` 返回 200。
- 运行态样例：`essayId=2` 从 `SCORING` 轮询到 `COMPLETED`，`GENERAL/GENERAL_V1`，动态维度 4 项，`91/100`。
- 重复 `idempotencyKey` 返回同一 `essayId`；同内容不同 key 可通过 `contentHash` 复用结果。
- 缺少必填 `taskPrompt`、疑似 prompt injection 等 REJECT 输入会在调用 AI 前返回 400。
- Playwright 严格验收覆盖提交页、历史页、结果页，无 Vue warning / pageerror。

## 开发注意事项

1. 不要恢复旧四维评分字段。
2. Rubric 内容第一版通过 Flyway seed 入库；不要把完整评分标准硬编码到业务逻辑中。
3. Prompt 安全外壳、JSON Schema、输入防御逻辑属于代码，不允许 DB Rubric 覆盖。
4. `taskPrompt` 和 `essayContent` 都是不可信用户输入，prompt 中必须隔离处理。
5. 修改评分结构后必须同时更新前端类型、结果页和历史页。
6. 提交流程是异步主流程：提交先返回 `SCORING`，结果页轮询，后台完成后返回 `COMPLETED` 或 `FAILED`。
7. 幂等必须同时考虑 Redis 快速缓存和 PostgreSQL 唯一索引兜底。
8. 提交前运行：

```powershell
.\gradlew.bat test
cd frontend
npm run build
```
