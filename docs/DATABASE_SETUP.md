# 数据库与 Redis 开发环境配置

当前项目使用 **PostgreSQL + Redis** 作为开发和生产基线，H2 不再作为日常开发数据库。仓库默认面向普通本地开发者：连接本机 PostgreSQL/Redis，不默认依赖远端主机或 SSH 隧道。

## 默认连接

```text
PostgreSQL: localhost:5432 / database=aiwriting / user=aiwriting
Redis:      redis://localhost:6379/0
```

配置位置：

```text
src/main/resources/application.yml
src/main/resources/application-dev.yml
src/main/resources/application-prod.yml
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

> 文档不记录真实密码。请把本机密码、Redis URL、Provider API Key、应用加密主密钥写入 `.env.dev.local` 或系统环境变量。

## 启动步骤

### 1. 准备本地 env 文件

```powershell
Copy-Item .env.dev.example .env.dev.local
```

编辑 `.env.dev.local`，填入真实值：

```text
DEV_DB_PASSWORD=...
DEV_REDIS_URL=...
AIWRITING_SECRET_KEY=...
```

`.env.dev.local` 已加入 `.gitignore`，不要提交真实密码。

### 2. 启动后端开发环境

```powershell
.\scripts\start-backend-dev.ps1
```

该脚本会：

1. 如果存在 `.env.dev.local`，加载其中的环境变量。
2. 默认不启动 SSH 隧道。
3. 执行 `./gradlew.bat bootRun`。

也可以手动启动：

```powershell
.\gradlew.bat bootRun
```

### 3. 可选：SSH 隧道

如果你的 PostgreSQL/Redis 部署在远端，先在 `.env.dev.local` 中显式填写隧道参数：

```text
DEV_TUNNEL_HOST=example.com
DEV_TUNNEL_USER=ubuntu
DEV_TUNNEL_IDENTITY_FILE=C:\path\to\id_rsa
DEV_TUNNEL_POSTGRES_LOCAL_PORT=5432
DEV_TUNNEL_POSTGRES_REMOTE_PORT=5432
DEV_TUNNEL_REDIS_LOCAL_PORT=6379
DEV_TUNNEL_REDIS_REMOTE_PORT=6379
```

然后运行：

```powershell
.\scripts\start-backend-dev.ps1 -WithTunnel
```

或单独启动隧道：

```powershell
.\scripts\start-vps-postgres-tunnel.ps1 -TunnelHost example.com -TunnelUser ubuntu -IdentityFile C:\path\to\id_rsa
```

隧道脚本不会在仓库中保存默认远端地址、用户名或私钥路径；缺少这些参数时会直接报错。

### 4. 验证服务

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/api/configs/security-policy
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/api/configs
```

## Schema 管理

当前使用 Flyway 管理 PostgreSQL schema：

```text
src/main/resources/db/migration/V1__init_schema.sql
src/main/resources/db/migration/V2__provider_config_abstraction.sql
```

dev/prod 配置中：

```yaml
spring:
  sql:
    init:
      mode: never
  flyway:
    enabled: true
    baseline-on-migrate: true
```

`src/main/resources/db/init.sql` 仍保留为兼容初始化参考，不再作为 dev schema 演进主路径。

## Redis 用途

当前 Redis 用于：

- Provider 模型列表缓存。
- 多实例 Provider 配置缓存失效消息。

Redis 不缓存 `ChatModel` Java 对象；`ChatModel` 使用 JVM 内 Caffeine 缓存，Redis 只负责跨实例协调和可序列化数据。

## 常见问题

### 后端启动时报 PostgreSQL connection refused

检查本机 PostgreSQL 是否启动，或检查 `.env.dev.local` 中的 `DEV_DB_HOST` / `DEV_DB_PORT` 是否正确。如果使用 SSH 隧道，请加 `-WithTunnel` 并确认隧道参数完整。

### Redis 连接失败

检查 `DEV_REDIS_URL`。如果 Redis 只作为缓存，部分功能可能降级；但模型列表缓存和跨实例失效会受影响。

### Gradle daemon 启动失败

先停止残留 daemon：

```powershell
.\gradlew.bat --stop
.\gradlew.bat test --no-daemon
```
