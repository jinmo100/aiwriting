# 数据库与 Redis 开发环境配置

当前项目使用 **PostgreSQL + Redis** 作为开发和生产基线。H2 不再作为日常开发数据库。仓库默认面向普通本地开发者：连接本机 PostgreSQL/Redis，不默认依赖远端主机或 SSH 隧道。

## 默认连接

```text
PostgreSQL: localhost:5432 / database=aiwriting / user=aiwriting
Redis:      redis://localhost:6379/0
```

`application-dev.yml` 默认读取：

```text
DEV_DB_HOST=localhost
DEV_DB_PORT=5432
DEV_DB_NAME=aiwriting
DEV_DB_USER=aiwriting
DEV_DB_PASSWORD=
DEV_REDIS_URL=redis://localhost:6379/0
```

真实密码、Redis URL、Provider API Key、应用加密主密钥应写入 `.env.dev.local` 或系统环境变量，不能提交到仓库。

## 启动步骤

```powershell
Copy-Item .env.dev.example .env.dev.local
.\scripts\start-backend-dev.ps1
```

如需远端数据库/Redis，可在 `.env.dev.local` 中显式填写隧道参数，然后运行：

```powershell
.\scripts\start-backend-dev.ps1 -WithTunnel
```

隧道脚本不会在仓库中保存默认远端地址、用户名或私钥路径。

## Schema 管理

Schema 由 Flyway 管理：

```text
src/main/resources/db/migration/V1__init_schema.sql
src/main/resources/db/migration/V2__provider_config_abstraction.sql
src/main/resources/db/migration/V3__replace_legacy_scoring_schema.sql
src/main/resources/db/migration/V4__seed_rubric_profiles.sql
src/main/resources/db/migration/V5__async_scoring_idempotency.sql
```

说明：

- `V3` 会破坏性重建 `essays` / `essay_scores`，旧四维历史数据不保留。
- `V4` 创建 `rubric_profiles` / `rubric_versions` / `rubric_dimensions` 并 seed V1 ACTIVE Rubric。
- `V5` 增加异步评分和幂等字段/索引：`idempotency_key`、`content_hash`、`essay_scores.updated_at`，并允许 `result_json` 在 `SCORING` 阶段为空。
- `src/main/resources/db/init.sql` 为 Docker 兼容 no-op，不创建表，避免 Flyway baseline 跳过真实迁移。

## 当前关键表

### essays

```text
id
essay_type
task_prompt
content
word_count
char_count
input_analysis_json
safety_analysis_json
idempotency_key
content_hash
created_at
```

### essay_scores

```text
id
essay_id
api_config_id
scoring_status
rubric_type
rubric_version
native_score
native_score_display
normalized_score
grade_label
confidence_level
result_json
ai_model
tokens_used
processing_time
error_code
error_message
created_at
updated_at
```

### Rubric

```text
rubric_profiles
rubric_versions
rubric_dimensions
```

Rubric 使用版本发布模型：`DRAFT` / `ACTIVE` / `ARCHIVED`。当前后端只读取 `ACTIVE`。

## Redis 用途

当前 Redis 用于：

- Provider 模型列表缓存。
- 多实例 Provider 配置缓存失效消息。
- `idempotencyKey` 快速幂等门禁。
- `contentHash` 短期防重复提交。

幂等 key：

```text
aiwriting:idempotency:{idempotencyKey}
aiwriting:content-submission:{contentHash}
```

Redis 只保存摘要状态，不保存作文正文；PostgreSQL 仍是最终权威记录。第一版 `AI Thinking` 状态由数据库轮询返回，不强依赖 Redis。

## 异步评分与幂等数据流

```text
POST /api/essays/submit
  -> 写入 essays(idempotency_key, content_hash)
  -> 写入 essay_scores(scoring_status=SCORING, result_json=NULL)
  -> 返回 essayId / scoreId / scoringStatus

后台 scoringTaskExecutor
  -> 成功：essay_scores.scoring_status=COMPLETED，写入 result_json 和分数摘要字段
  -> 失败：essay_scores.scoring_status=FAILED，写入 error_code / error_message

GET /api/essays/{id}
  -> 前端结果页轮询展示 SCORING / COMPLETED / FAILED
```

无用户系统阶段 `idempotency_key` 全局唯一；未来引入用户系统后应改为用户维度唯一。

## 常见问题

### 后端启动时报 PostgreSQL connection refused

检查本机 PostgreSQL 是否启动，或检查 `.env.dev.local` 中的 `DEV_DB_HOST` / `DEV_DB_PORT` 是否正确。如果使用 SSH 隧道，请加 `-WithTunnel` 并确认隧道参数完整。

### Redis 连接失败

检查 `DEV_REDIS_URL`。默认 `aiwriting.idempotency.redis-required=false`，Redis 不可用时会降级到 PostgreSQL 的 `idempotency_key` 唯一索引和 `content_hash` 查询，不向用户暴露 Redis 故障。若生产环境改为 `redis-required=true`，Redis 不可用时应返回“系统繁忙，请稍后再试”。

### Flyway 校验失败

如果是本地开发库且历史数据不重要，可重建数据库后重新运行迁移；本轮 `V3` 本身就是破坏性迁移。
