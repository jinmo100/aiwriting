# AI Provider 抽象设计

更新时间：2026-06-06

实施状态：2026-06-07 已基本落地到当前代码库。已实现 Provider Adapter、LangChain4j 1.16.1 动态模型工厂、API Key AES-GCM 加密、测试连接、结构化输出测试、模型列表 Redis 缓存、Caffeine ChatModel 指纹缓存、Redis 配置失效通知、Flyway migration、作文评分结构化校验与 repair retry。后续重点转向评分任务状态机、统一错误响应、认证权限和用量统计。

## 1. 目标

本设计用于将当前项目的 AI 调用从“单一 OpenAI-compatible 临时实现”升级为稳定、可扩展、可测试的 Provider 抽象层。

首版目标：

- 支持 4 类主流协议：
  - `OPENAI_CHAT_COMPLETIONS`
  - `OPENAI_RESPONSES`
  - `ANTHROPIC_MESSAGES`
  - `GEMINI_GENERATE_CONTENT`
- 使用 LangChain4j 作为底层成熟模型调用框架。
- 项目自身保留 Provider Adapter 抽象，业务层不直接依赖 LangChain4j 具体 Provider 类。
- 支持配置页测试连接、测试结构化输出、获取模型列表。
- 支持 API Key 加密存储和 dev 环境可控 reveal。
- 为后续模型参数自定义、多模型切换、异步评分、成本统计预留扩展点。

## 2. 非目标

首版暂不做：

- 模型 token streaming 输出。
- Azure OpenAI、Ollama、New API、CherryIN、Bedrock、Mistral 等次阶段 Provider。
- 通用 HTTP AI 网关能力。
- 用户自定义 endpoint path。
- 完整模型测试历史表。
- 生产环境无认证的完整 API Key 回显。

## 3. ProviderType 定义

`provider_type` 表示“协议适配器”，不是品牌名称。

```java
public enum ProviderType {
    OPENAI_CHAT_COMPLETIONS,
    OPENAI_RESPONSES,
    ANTHROPIC_MESSAGES,
    GEMINI_GENERATE_CONTENT
}
```

数据库使用：

```sql
provider_type VARCHAR(50) NOT NULL
```

不使用 PostgreSQL enum，便于后续扩展新 Provider 类型。

展示名称与协议类型分离：

```text
config_name      用户自定义配置名称，例如“晚霞公益站”
provider_label   展示标签，例如 OpenAI / OpenRouter / Gemini / Anthropic
provider_type    真正控制调用逻辑的协议适配器枚举
```

## 4. base_url 与 endpoint 规则

`base_url` 统一存 API 根地址，不存完整 endpoint。

示例：

```text
OPENAI_CHAT_COMPLETIONS
base_url = https://api.openai.com/v1
实际请求 = {base_url}/chat/completions

OPENAI_RESPONSES
base_url = https://api.openai.com/v1
实际请求 = {base_url}/responses

ANTHROPIC_MESSAGES
base_url = https://api.anthropic.com/v1
实际请求 = {base_url}/messages

GEMINI_GENERATE_CONTENT
base_url = https://generativelanguage.googleapis.com/v1beta
实际请求 = {base_url}/models/{model_name}:generateContent
```

首版不开放 `endpoint_path_override`。

前端只提示填写规则，不自动填默认 `base_url`，因为实际网关和公益站地址可能五花八门。

后端保存时需要规范化：

- 去掉末尾 `/`。
- 如果用户误填 `/chat/completions`、`/responses`、`/messages`、`/models/{model}:generateContent`，应提示或规范化为根地址。
- 不允许空值。

## 5. LangChain4j 适配策略

底层使用 LangChain4j，不自研完整 HTTP 调用层。

建议版本：

```gradle
ext {
    langchain4jVersion = '1.16.1'
    langchain4jBetaVersion = '1.16.1-beta26'
}

implementation "dev.langchain4j:langchain4j:${langchain4jVersion}"
implementation "dev.langchain4j:langchain4j-open-ai:${langchain4jVersion}"
implementation "dev.langchain4j:langchain4j-anthropic:${langchain4jVersion}"
implementation "dev.langchain4j:langchain4j-google-ai-gemini:${langchain4jVersion}"
implementation "dev.langchain4j:langchain4j-open-ai-official:${langchain4jBetaVersion}"
implementation "com.github.ben-manes.caffeine:caffeine"
implementation "org.springframework.boot:spring-boot-starter-data-redis"
```

Provider 映射：

```text
OPENAI_CHAT_COMPLETIONS
  -> OpenAiChatModel

OPENAI_RESPONSES
  -> OpenAiOfficialResponsesChatModel
  -> 注意：Responses API 在 LangChain4j 中仍属于 experimental/beta，必须隔离在 adapter 中。

ANTHROPIC_MESSAGES
  -> AnthropicChatModel

GEMINI_GENERATE_CONTENT
  -> GoogleAiGeminiChatModel
```

不要使用 Spring Boot Starter 自动注入固定模型实例，因为项目配置来自数据库，需要运行时动态选择 Provider、base_url、key、模型和参数。

## 6. Provider Adapter 抽象

Adapter 是通用文本生成接口，不绑定作文评分业务。

```java
public interface AIProviderAdapter {
    ProviderType providerType();

    AIProviderResult generate(AIProviderRequest request, ApiConfig config);

    default boolean supportsStreaming() {
        return false;
    }
}
```

请求对象：

```java
public record AIProviderRequest(
    String systemPrompt,
    String userPrompt,
    String responseSchemaName,
    String responseSchemaJson,
    Map<String, Object> requestOptions
) {}
```

返回对象使用项目自定义类型，不向业务层暴露 LangChain4j `ChatResponse`：

```java
public record AIProviderResult(
    String text,
    String rawResponse,
    String providerRequestId,
    String modelName,
    Integer inputTokens,
    Integer outputTokens,
    Integer totalTokens,
    Long latencyMillis,
    ProviderType providerType
) {}
```

建议实现类：

```text
OpenAiChatCompletionsAdapter
OpenAiResponsesAdapter
AnthropicMessagesAdapter
GeminiGenerateContentAdapter
ProviderAdapterRegistry
LangChainChatModelFactory
```

作文评分调用链：

```text
EssayScoringService
  -> 构建 prompt/schema
  -> ProviderAdapterRegistry 选择 adapter
  -> adapter.generate()
  -> 得到 AIProviderResult.text
  -> JSON 清理、解析、校验、repair retry
```

## 7. ApiConfig 字段设计

建议 `api_configs` 首版字段：

```text
id
config_name
provider_type
provider_label
base_url
api_key_encrypted
model_name
temperature
max_tokens
timeout_seconds
model_parameters_json
is_default
last_test_status
last_test_error_code
last_test_message
last_test_latency_ms
last_tested_at
created_at
updated_at
```

兼容旧字段：

```text
provider  -> 迁移为 provider_label 或逐步废弃
api_key   -> 旧明文字段，读取时兼容，新增/更新时不再写明文
```

通用模型参数独立列：

```text
temperature
max_tokens
timeout_seconds
```

高级参数使用 JSON 扩展字段：

```text
model_parameters_json
```

示例：

```json
{
  "top_p": 0.9,
  "presence_penalty": 0,
  "frequency_penalty": 0,
  "stop": ["END"],
  "thinking_budget": 1024,
  "response_mime_type": "application/json"
}
```

高级参数策略：

- 保存时宽松。
- 调用时按 `provider_type` 白名单过滤。
- 未支持参数忽略并记录 warning。
- 不完全透传未知参数。

## 8. API Key 加密与 reveal

第一版采用应用层对称加密：

```text
算法：AES-GCM
数据库：只存 api_key_encrypted
开发环境：加密主密钥可明文写 application-dev.yml
生产环境：加密主密钥来自环境变量，例如 AIWRITING_SECRET_KEY
```

读取策略：

- 优先读取并解密 `api_key_encrypted`。
- 若旧数据只有 `api_key`，运行时兼容读取。
- 新增/更新配置时只写密文。

普通配置接口永不返回完整 API Key，只返回：

```json
{
  "hasApiKey": true,
  "apiKeyPreview": "sk-...abcd"
}
```

完整 Key 回显使用专用接口：

```http
POST /api/configs/{id}/reveal-api-key
```

策略：

- dev 环境可通过配置开关允许。
- prod 默认禁用。
- 等用户认证上线后，prod 再按管理员/本人权限开放。
- 前端不把完整 Key 写入 localStorage/sessionStorage，不打印日志，弹窗关闭即清空。

配置建议：

```yaml
aiwriting:
  security:
    allow-api-key-reveal: true
```

## 9. 创建与更新配置 DTO

创建配置时 `apiKey` 必填。

更新配置时 `apiKey` 可空：

- 空值表示保留旧 Key。
- 非空表示替换并重新加密。

建议拆分 DTO：

```java
record ApiConfigCreateRequest(
    String configName,
    ProviderType providerType,
    String providerLabel,
    String baseUrl,
    String apiKey,
    String modelName,
    BigDecimal temperature,
    Integer maxTokens,
    Integer timeoutSeconds,
    String modelParametersJson,
    Boolean isDefault
) {}
```

```java
record ApiConfigUpdateRequest(
    String configName,
    ProviderType providerType,
    String providerLabel,
    String baseUrl,
    String apiKey,
    String modelName,
    BigDecimal temperature,
    Integer maxTokens,
    Integer timeoutSeconds,
    String modelParametersJson,
    Boolean isDefault
) {}
```

前端 API Key 输入框提示：

```text
已保存 API Key；留空表示不修改，填写新 Key 表示替换。
```

## 10. ChatModel 缓存策略

采用混合缓存：

```text
Caffeine：缓存 JVM 内 LangChain4j ChatModel 实例。
Redis：用于分布式失效、配置版本、任务状态、模型列表缓存、后续异步队列和限流。
```

Redis 不直接缓存 `ChatModel` 实例，因为它是 Java 运行时对象，不适合序列化，也不应扩大 API Key 泄露面。

Caffeine 缓存 key 使用配置指纹，不用单纯 `configId`：

```java
public record ChatModelCacheKey(
    ProviderType providerType,
    String baseUrl,
    String modelName,
    String apiKeyHash,
    BigDecimal temperature,
    Integer maxTokens,
    Integer timeoutSeconds,
    String modelParametersHash
) {}
```

建议策略：

```text
最大容量：100
TTL：30 分钟未访问自动移除
更新/删除配置：主动 invalidate 旧指纹
apiKeyHash：SHA-256 明文 key
modelParametersHash：SHA-256 规范化 JSON
```

多实例部署时：

```text
配置更新
  -> PostgreSQL 更新 api_configs
  -> Redis 发布 provider-config-invalidated:{configId}
  -> 各 JVM 清本地 Caffeine
```

## 11. Redis 用途

Redis 连接地址、密码等环境相关值应通过 `.env.dev.local` 或系统环境变量注入，不写入公开仓库、日志或前端。

用途：

- 模型列表缓存。
- 多实例缓存失效通知。
- 后续异步评分任务状态。
- 后续限流、配额、用量统计临时缓存。
- 后续队列或轻量任务调度协调。

默认开发环境连接本机 PostgreSQL/Redis。若团队成员需要远端数据库，可显式配置 SSH 隧道参数并同时转发 PostgreSQL 与 Redis；仓库不保存默认远端主机、用户名或私钥路径。

## 12. 测试连接与结构化输出测试

配置页首版新增两个动作。

### 12.1 测试连接

```http
POST /api/configs/test-connection
POST /api/configs/{id}/test-connection
```

测试 prompt：

```text
Reply with exactly: OK
```

返回：

```json
{
  "success": true,
  "providerType": "OPENAI_CHAT_COMPLETIONS",
  "modelName": "gpt-4o-mini",
  "latencyMillis": 1234,
  "message": "连接成功"
}
```

### 12.2 测试结构化输出

```http
POST /api/configs/test-structured-output
POST /api/configs/{id}/test-structured-output
```

要求模型返回固定 JSON：

```json
{
  "status": "ok",
  "score": 1
}
```

后端解析并校验。

返回：

```json
{
  "success": true,
  "jsonValid": true,
  "schemaValid": true,
  "latencyMillis": 1500
}
```

测试摘要保存到 `api_configs`：

```text
last_test_status
last_test_error_code
last_test_message
last_test_latency_ms
last_tested_at
```

不保存：

- 完整 prompt。
- 完整 response。
- 完整异常堆栈。
- 完整 API Key。

## 13. 模型列表拉取与缓存

模型名支持手动填写，也支持从远端模型列表选择。

未保存配置：

```http
POST /api/configs/models/fetch
```

请求：

```json
{
  "providerType": "OPENAI_CHAT_COMPLETIONS",
  "baseUrl": "https://example.com/v1",
  "apiKey": "sk-xxx",
  "forceRefresh": false
}
```

已保存配置：

```http
POST /api/configs/{id}/models/fetch
```

后端解密已保存 API Key 后请求模型列表。

统一返回：

```json
{
  "success": true,
  "fromCache": false,
  "models": [
    {
      "id": "gpt-4o-mini",
      "displayName": "gpt-4o-mini",
      "ownedBy": "openai"
    }
  ],
  "latencyMillis": 800
}
```

模型列表端点：

```text
OpenAI / OpenAI-compatible: GET {base_url}/models
OpenAI Responses:          GET {base_url}/models
Anthropic:                 GET {base_url}/models
Gemini:                    GET {base_url}/models
```

Redis 缓存策略：

```text
key：aiwriting:provider-models:{providerType}:{baseUrlHash}:{apiKeyHash}
TTL：10 分钟
value：规范化后的模型列表 JSON
forceRefresh=true 时绕过缓存重新拉取
```

不缓存：

- 明文 API Key。
- 完整原始响应。

## 14. 结构化输出与校验

首版不启用模型 token streaming。

作文评分走非流式完整响应：

```text
AIProviderAdapter.generate()
  -> 返回完整 AIProviderResult.text
  -> 后端解析和校验
```

结构化输出策略：

```text
优先使用 LangChain4j ResponseFormat / JSON Schema
后端本地强校验
失败允许一次 repair retry
```

校验内容：

- JSON 可解析。
- 必填字段存在。
- 分数范围合法。
- 错误类型枚举合法。
- 数组字段类型合法。
- 业务规则合理。

失败流程：

```text
第一次生成失败
  -> 构建 repair prompt，附原始输出和 schema 要求
  -> 再请求一次
  -> 仍失败则返回 STRUCTURED_OUTPUT_FAILED
```

Provider Adapter 负责“尽量让模型输出 JSON”。

业务服务负责“判断 JSON 是否可信”。

## 15. 错误分类与重试策略

统一错误码：

```text
AUTH_ERROR
MODEL_NOT_FOUND
INVALID_BASE_URL
INVALID_PROVIDER_CONFIG
RATE_LIMIT
NETWORK_TIMEOUT
NETWORK_ERROR
PROVIDER_5XX
STRUCTURED_OUTPUT_FAILED
CONTENT_POLICY_BLOCKED
UNKNOWN_ERROR
```

统一异常：

```java
public class AIProviderException extends RuntimeException {
    private ProviderType providerType;
    private AIProviderErrorCode errorCode;
    private String safeMessage;
}
```

重试策略：

```text
可重试：
- NETWORK_TIMEOUT
- NETWORK_ERROR
- PROVIDER_5XX
- RATE_LIMIT

不可重试：
- AUTH_ERROR
- MODEL_NOT_FOUND
- INVALID_BASE_URL
- INVALID_PROVIDER_CONFIG
- CONTENT_POLICY_BLOCKED

STRUCTURED_OUTPUT_FAILED：
- 不重复原请求
- 走 repair prompt 一次
```

建议退避：

```text
普通瞬时错误：最多 2 次，1s -> 3s
RATE_LIMIT：最多 2 次，2s -> 6s
```

## 16. Flyway Migration 计划

引入 Flyway，后续数据库结构统一走 migration，不再依赖 `init.sql` 演进 dev PostgreSQL。

建议依赖：

```gradle
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'
```

配置：

```yaml
spring:
  sql:
    init:
      mode: never
  flyway:
    enabled: true
    baseline-on-migrate: true
```

建议文件：

```text
src/main/resources/db/migration/V1__init_schema.sql
src/main/resources/db/migration/V2__provider_config_abstraction.sql
```

`V2` 使用兼容迁移：

```sql
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS provider_type VARCHAR(50);
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS provider_label VARCHAR(100);
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS api_key_encrypted TEXT;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS temperature DECIMAL(4,2) DEFAULT 0.3;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS max_tokens INTEGER DEFAULT 2048;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS timeout_seconds INTEGER DEFAULT 60;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS model_parameters_json TEXT;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS last_test_status VARCHAR(20);
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS last_test_error_code VARCHAR(50);
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS last_test_message VARCHAR(500);
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS last_test_latency_ms INTEGER;
ALTER TABLE api_configs ADD COLUMN IF NOT EXISTS last_tested_at TIMESTAMP;
```

旧数据迁移建议：

```text
provider_type：按 provider/base_url 推断，无法推断则默认 OPENAI_CHAT_COMPLETIONS
provider_label：从旧 provider 复制
api_key_encrypted：由应用启动后兼容读取旧 api_key，后续单独任务加密回填
```

## 17. 前端交互规则

配置页：

- Provider 类型下拉：显示 OpenAI、OpenAI-Response、Gemini、Anthropic。
- `base_url` 由用户填写，不自动填默认值。
- 根据 Provider 类型展示提示：不要填写具体 endpoint。
- `model_name` 支持手动输入。
- 提供“获取模型列表”按钮。
- 获取后显示下拉列表，用户选择后填入模型名。
- 支持 force refresh。
- 提供“测试连接”按钮。
- 提供“测试输出格式”按钮。
- API Key 编辑时：留空表示不修改，填写表示替换。
- dev 环境可显示“查看完整 Key”按钮，prod 默认隐藏或禁用。

## 18. 实施步骤建议

建议按以下顺序实施：

1. 引入 Flyway，接管当前 schema。
2. 增加 ProviderType、ApiConfig 新字段、DTO 拆分。
3. 增加 API Key 加密服务和 reveal endpoint。
4. 升级 LangChain4j 到 `1.16.1`，添加 Anthropic/Gemini/OpenAI Official 依赖。
5. 实现 Provider Adapter、Registry、LangChainChatModelFactory。
6. 实现 Caffeine ChatModel 缓存与配置指纹。
7. 接入 Redis，用于模型列表缓存和后续失效通知。
8. 实现测试连接、测试结构化输出。
9. 实现模型列表拉取与 Redis 缓存。
10. 重构作文评分服务使用 Provider Adapter。
11. 增加结构化输出本地校验与 repair retry。
12. 前端配置页改造。
13. 添加单元测试和基础集成测试。

## 19. 已确认决策清单

- `provider_type` 表示协议适配器，不表示品牌。
- 首版支持四类：OpenAI Chat Completions、OpenAI Responses、Claude Messages、Gemini generateContent。
- `base_url` 存根地址，不存完整 endpoint。
- 不开放 endpoint path override。
- 使用 LangChain4j，不自研完整 HTTP 调用层。
- Adapter 返回项目自定义 `AIProviderResult`。
- Adapter 是通用 generate，不绑定作文评分。
- 首版不启用模型 token streaming。
- 通用参数独立列，高级参数 JSON 扩展字段。
- 高级参数保存宽松，调用按 Provider 白名单过滤。
- ChatModel 实例用 Caffeine 本地缓存。
- Redis 不缓存 ChatModel，只做模型列表缓存、失效通知和后续任务状态等。
- API Key AES-GCM 加密存储。
- 普通接口不返回完整 Key，reveal 独立接口 dev 可用、prod 默认禁用。
- 创建配置 key 必填，更新配置 key 可空且空表示保留。
- 配置页新增测试连接与测试结构化输出。
- 模型列表可远端拉取，也允许自定义填写。
- 模型列表 Redis 缓存 10 分钟，支持 force refresh。
- 引入 Flyway 管理 schema 演进。
