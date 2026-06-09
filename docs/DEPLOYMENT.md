# 发布部署指南

目标：用户在 VPS 或任意 Docker 主机上部署时，只拉取 GHCR 预构建镜像，不需要在服务器上运行 `npm install`、`npm run build`、Gradle 或本地 JDK。

## 最短路径

在服务器准备好 Docker 和 Docker Compose 后：

```bash
git clone https://github.com/jinmo100/essay-evaluator.git
cd essay-evaluator
cp .env.release.example .env
```

编辑 `.env`，至少修改：

- `POSTGRES_PASSWORD`
- `ESSAY_EVALUATOR_SECRET_KEY`
- 如放在 HTTPS 反向代理后面，把 `ESSAY_EVALUATOR_SESSION_COOKIE_SECURE=true`

启动：

```bash
docker compose -f docker-compose.release.yml --env-file .env pull
docker compose -f docker-compose.release.yml --env-file .env up -d
```

默认访问：

```text
http://<server-ip>:8088
```

## 这个项目必须依赖 PostgreSQL 和 Redis

后端运行必须连接：

- PostgreSQL：保存用户、作文、Rubric、评分结果、Provider 配置和 AI 调用日志。
- Redis：保存 Spring Session，并承担幂等/防重复提交的快速缓存。

`docker-compose.release.yml` 已默认内置 `pgvector/pgvector:pg16` 和 `redis:7-alpine`，并且 **不把 5432/6379 发布到宿主机**，普通用户可以一条 compose 命令直接启动。

已有 PostgreSQL/Redis 的用户可以：

1. 复制一份 `docker-compose.release.yml` 自行修改；
2. 或在 `.env` 中覆盖 `DB_HOST`、`DB_PORT`、`REDIS_URL`，并按需移除内置 `postgres` / `redis` 服务。

## pgvector 扩展要求

RAG 知识库会通过 Flyway 执行 `CREATE EXTENSION IF NOT EXISTS vector;`，并创建 `vector(1536)` 向量列。

- Docker release：`docker-compose.release.yml` 已使用 `pgvector/pgvector:pg16`，容器内已包含 pgvector 扩展，正常情况下无需额外安装。
- VPS/宿主机 PostgreSQL：如果你改为连接宿主机或云数据库，必须先由 PostgreSQL 超级用户或具备扩展创建权限的管理员安装并创建 `vector` 扩展，再启动应用执行迁移。
- Windows 本机调试：如果 `.env.dev.local` 指向本机 PostgreSQL，也需要先安装匹配版本的 pgvector，并用管理员账号在目标库执行 `CREATE EXTENSION IF NOT EXISTS vector;`。

### 从旧 release 内置 PostgreSQL 升级到 pgvector

较早的 release compose 使用 `postgres:16-alpine`，当前 release compose 使用 `pgvector/pgvector:pg16`。两者同属 PostgreSQL 16，但直接复用既有 `postgres-data` volume 仍可能受到镜像基底、locale/collation 或已有数据目录状态影响。

已有部署升级前请先：

1. 备份数据库，至少保留一次可恢复的 `pg_dump` 或 volume 备份。
2. 在测试环境或临时服务器上用同一份备份验证 `pgvector/pgvector:pg16` 能正常启动并完成 Flyway 迁移。
3. 如果直接复用旧 volume 启动失败、collation/locale 警告无法确认，或需要更可控的升级路径，优先使用 `pg_dump` 导出旧库，再恢复到全新的 `pgvector/pgvector:pg16` 容器数据目录。

示例（宿主机或自管数据库）：

```sql
-- 使用 postgres 超级用户或数据库管理员连接目标数据库后执行
CREATE EXTENSION IF NOT EXISTS vector;
```

如果目标数据库没有 pgvector，后端启动时会在 Flyway V11 迁移阶段失败；这属于数据库运行环境缺失，应先修复 PostgreSQL/pgvector，而不是绕过迁移。

## 镜像与版本标签

发布镜像：

- `ghcr.io/jinmo100/essay-evaluator-backend`
- `ghcr.io/jinmo100/essay-evaluator-frontend`

标签语义：

- `latest = 最新稳定版`：只在 `v*` git tag 发布成功后更新。
- `edge = main 分支最新版`：每次 `main` 分支 push 后更新，适合试用最新代码。
- `v2.3.0 = 固定版本`：稳定可复现部署，推荐生产环境固定到具体版本。
- `main-<sha> = main 分支精确提交版本`：用于排查、回滚和复现。

示例：固定到 v2.3.0：

```env
APP_VERSION=v2.3.0
```

## 域名、HTTPS 与反向代理

本项目只提供通用 Docker 入口，不强制指定 Nginx、Caddy、Traefik 或云厂商方案。

常见做法：

1. 让宿主机反向代理监听 `80/443`。
2. Compose 里的前端只监听本机地址：

   ```env
   FRONTEND_BIND=127.0.0.1
   FRONTEND_PORT=18080
   ESSAY_EVALUATOR_SESSION_COOKIE_SECURE=true
   ```

3. 反向代理把域名流量转发到 `http://127.0.0.1:18080`。

如果只是 IP + HTTP 直连测试，保持：

```env
ESSAY_EVALUATOR_SESSION_COOKIE_SECURE=false
```

否则浏览器不会在 HTTP 下保存 `Secure` Cookie，登录态会表现为“不流畅”或反复掉线。

## 首次账号与注册开关

默认：

```env
ESSAY_EVALUATOR_REGISTRATION_ENABLED=true
```

首次启动后，先在页面注册自己的账号。若不希望继续开放注册，改成：

```env
ESSAY_EVALUATOR_REGISTRATION_ENABLED=false
```

然后重启：

```bash
docker compose -f docker-compose.release.yml --env-file .env up -d
```

## 更新与回滚

如果现有部署来自旧版 `postgres:16-alpine` 内置数据库，请先阅读上文“从旧 release 内置 PostgreSQL 升级到 pgvector”，完成备份和测试验证后再执行更新命令。

更新到当前稳定版：

```bash
docker compose -f docker-compose.release.yml --env-file .env pull
docker compose -f docker-compose.release.yml --env-file .env up -d
```

回滚到固定版本：

```env
APP_VERSION=v2.3.0
```

再执行：

```bash
docker compose -f docker-compose.release.yml --env-file .env pull
docker compose -f docker-compose.release.yml --env-file .env up -d
```

## GHCR 拉取排查

发布工作流会把镜像推送到 GHCR，并尽量把 packages 设置为 Public。若服务器 `docker pull` 提示未授权：

1. 先确认镜像包在 GitHub Packages 页面是 Public。
2. 确认 `APP_VERSION` 标签存在，例如 `latest`、`edge`、`v2.3.0`。
3. 如果使用私有包，先在服务器执行 `docker login ghcr.io`。

## 发布工作流

`.github/workflows/publish-images.yml` 会在以下事件运行：

- push 到 `main`：运行后端测试、评分 benchmark、前端构建，然后发布 `edge` 和 `main-<sha>`。
- push `v*` tag：同样先验收，再发布 `latest` 和对应 `vX.Y.Z`。

本地没有 Docker 也不影响发布；最终镜像由 GitHub Actions 构建并推送。
