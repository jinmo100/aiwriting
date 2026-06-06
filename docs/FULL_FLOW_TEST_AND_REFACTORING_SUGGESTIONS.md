# 完整流程测试记录与优化建议

更新时间：2026-06-07

## 1. 最新测试结论

核心链路已在本地私有配置下通过端到端验证：

```text
Vue 前端（Vite）
  -> /api 代理
  -> Spring Boot 后端
  -> PostgreSQL
  -> Redis
  -> LangChain4j Provider Adapter
  -> Provider API
  -> 评分结果入库
  -> 结果页/历史页展示
```

已验证范围：

- 后端：`http://127.0.0.1:8080`
- 前端：`http://127.0.0.1:5173`
- Provider 类型：`OPENAI_RESPONSES`
- 连接测试：通过
- 结构化输出测试：通过
- API 提交评分：通过
- 前端提交评分：通过
- 结果页：通过
- 历史页：通过
- 后端测试：通过
- 前端构建：通过

具体数据库地址、Redis 密码、Provider Base URL、模型名和 API Key 属于本地私有配置，不写入公开文档。

## 2. 启动步骤

### 2.1 准备本地环境

```powershell
Copy-Item .env.dev.example .env.dev.local
# 填写 .env.dev.local 后：
.\scripts\start-backend-dev.ps1
```

默认连接本机 PostgreSQL/Redis。远端数据库/Redis 通过 `.env.dev.local` 覆盖；SSH 隧道只在显式加 `-WithTunnel` 时启动。

### 2.2 启动后端

```powershell
.\gradlew.bat bootRun
```

### 2.3 启动前端

```powershell
cd frontend
npm run dev -- --host 127.0.0.1 --port 5173
```

## 3. 配置验证规则

Provider 配置应遵守：

- `providerType` 表示协议适配器，不表示品牌。
- `baseUrl` 填 API 根地址，不填具体 endpoint。
- `modelName` 可手动填写，也可通过“获取模型列表”选择。
- 新建配置 API Key 必填；编辑配置 API Key 留空表示保留旧值。
- 普通接口不返回完整 API Key。

典型示例：

```text
providerType = OPENAI_RESPONSES
baseUrl      = https://api.openai.com/v1
modelName    = <your-model>
```

后端会按 Provider 类型自行拼接 endpoint。

## 4. API 流程验证

### 4.1 读取配置

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/api/configs
```

预期：返回配置列表，`hasApiKey=true/false`，只显示 `apiKeyPreview`，不返回完整 Key。

### 4.2 测试连接

```powershell
Invoke-WebRequest -UseBasicParsing -Method Post http://127.0.0.1:8080/api/configs/{id}/test-connection
```

预期：`success=true` 且记录延迟、测试状态。

### 4.3 测试结构化输出

```powershell
Invoke-WebRequest -UseBasicParsing -Method Post http://127.0.0.1:8080/api/configs/{id}/test-structured-output
```

预期：`jsonValid=true`、`schemaValid=true`。

### 4.4 提交作文评分

接口：

```text
POST /api/essays/submit
```

已确认：

- 作文成功保存。
- `wordCount` 已写入。
- AI 评分成功。
- 评分详情成功入库。
- 详情接口可查询。
- 历史接口可查询。

## 5. 前端流程验证

1. 打开 `http://127.0.0.1:5173`。
2. 进入“API配置”。
3. 新建或确认默认配置测试通过。
4. 进入“提交作文”。
5. 输入英文作文。
6. 点击“提交评分”。
7. 跳转到 `/result/{essayId}`。
8. 结果页展示总分、维度分、模型、优点、建议、详细评价。
9. 点击“查看历史”。
10. 历史页展示最近提交记录。

## 6. 构建验证

### 后端

```powershell
.\gradlew.bat test --no-daemon --rerun-tasks
```

结果：通过。

### 前端

```powershell
cd frontend
npm run build
```

结果：通过。Vite chunk 体积警告属于后续性能优化项。

## 7. 已完成的优化

- Provider 抽象层落地。
- API Key AES-GCM 加密存储。
- dev reveal 受环境开关控制。
- 前端 reveal 按后端安全策略显示/隐藏。
- 创建配置 Key 必填、编辑配置 Key 可空保留。
- 测试连接和测试结构化输出。
- 模型列表拉取与 Redis 缓存。
- Caffeine ChatModel 指纹缓存。
- Redis 配置失效通知。
- Flyway 接管 schema。
- 作文评分结构化校验和 repair retry。
- 保存作文时写入 `wordCount`。
- 开发启动脚本改为默认本地服务，SSH 隧道显式可选。

## 8. 后续建议

- 评分流程异步化/状态机化。
- 统一错误响应。
- 历史页增强评分摘要。
- API Key reveal 接入认证和权限。
- 前端性能优化。
