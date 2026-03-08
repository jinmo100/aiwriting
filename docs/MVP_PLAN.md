# AI英语作文评分系统 - MVP重构方案

## 📋 文档版本
- 版本：v1.0
- 日期：2026-03-08
- 状态：进行中

## 🎯 项目目标

构建一个现代化的AI英语作文评分系统，核心功能：
1. **多模型支持**：用户可自定义API配置，支持多种AI模型
2. **智能评分**：多维度评分体系（内容、语言、结构、连贯性）
3. **详细反馈**：提供优点、建议和错误分析
4. **历史管理**：查看历史评分记录

## 🏗️ 技术架构

### 后端技术栈
```yaml
语言：Java 21
框架：Spring Boot 3.4.x
数据：Mybatis Plus + PostgreSQL 16
AI集成：LangChain4j 0.36.x
工具：Lombok, SpringDoc OpenAPI 3.x
构建：Gradle 8.x (Kotlin DSL)
```

### 前端技术栈
```yaml
框架：Vue 3.4.x + TypeScript 5.x
构建：Vite 5.x
UI：Element Plus 2.x
状态：Pinia
网络：Axios
```

### 部署方案
- Docker容器化
- docker-compose编排

## 📊 数据库设计

### 表结构

```sql
-- API配置表
CREATE TABLE api_configs (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    provider VARCHAR(50) NOT NULL,           -- openai, anthropic, openrouter, deepseek
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(255) NOT NULL,           -- 加密存储
    model_name VARCHAR(100) NOT NULL,
    is_default BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 作文表
CREATE TABLE essays (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    word_count INTEGER,
    essay_type VARCHAR(50),                  -- IELTS, TOEFL, CET4, CET6
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 评分结果表
CREATE TABLE essay_scores (
    id BIGSERIAL PRIMARY KEY,
    essay_id BIGINT REFERENCES essays(id) ON DELETE CASCADE,
    api_config_id BIGINT REFERENCES api_configs(id),

    -- 各维度分数
    overall_score DECIMAL(5,2),
    content_score DECIMAL(5,2),
    language_score DECIMAL(5,2),
    structure_score DECIMAL(5,2),
    coherence_score DECIMAL(5,2),

    -- 详细反馈
    strengths JSONB,
    suggestions JSONB,
    errors JSONB,
    detailed_feedback TEXT,

    -- 元数据
    ai_model VARCHAR(100),
    tokens_used INTEGER,
    processing_time INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_essays_created_at ON essays(created_at DESC);
CREATE INDEX idx_scores_essay_id ON essay_scores(essay_id);
CREATE INDEX idx_configs_default ON api_configs(is_default);
```

## 📁 项目结构

### 后端结构
```
backend/
├── src/main/java/com/jinmo/aiwriting/
│   ├── AiWritingApplication.java
│   ├── config/                          # 配置类
│   │   ├── WebConfig.java
│   │   └── CorsConfig.java
│   ├── controller/                      # 控制器
│   │   ├── ApiConfigController.java
│   │   └── EssayController.java
│   ├── service/                         # 服务层
│   │   ├── ApiConfigService.java
│   │   ├── EssayService.java
│   │   └── ScoringService.java
│   ├── ai/                              # AI服务
│   │   ├── LangChainService.java
│   │   ├── ModelFactory.java
│   │   └── prompt/
│   │       └── ScoringPrompt.java
│   ├── domain/                          # 领域模型
│   │   ├── entity/
│   │   │   ├── ApiConfig.java
│   │   │   ├── Essay.java
│   │   │   └── EssayScore.java
│   │   └── dto/
│   │       ├── ApiConfigRequest.java
│   │       ├── ApiConfigResponse.java
│   │       ├── EssaySubmitRequest.java
│   │       ├── EssayScoreResponse.java
│   │       └── ScoreDetail.java
│   ├── repository/                      # 数据访问
│   │   ├── ApiConfigRepository.java
│   │   ├── EssayRepository.java
│   │   └── EssayScoreRepository.java
│   └── common/                          # 公共组件
│       ├── exception/
│       │   ├── GlobalExceptionHandler.java
│       │   └── BusinessException.java
│       └── response/
│           └── ApiResponse.java
└── src/main/resources/
    ├── application.yml
    ├── application-dev.yml
    └── application-prod.yml
```

### 前端结构
```
frontend/
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── views/                           # 页面
│   │   ├── ConfigView.vue
│   │   ├── SubmitView.vue
│   │   ├── ResultView.vue
│   │   └── HistoryView.vue
│   ├── components/                      # 组件
│   │   ├── ApiConfigForm.vue
│   │   ├── EssayEditor.vue
│   │   ├── ScoreDisplay.vue
│   │   ├── DimensionScore.vue
│   │   ├── FeedbackList.vue
│   │   └── HistoryList.vue
│   ├── stores/                          # 状态管理
│   │   ├── useConfigStore.ts
│   │   └── useEssayStore.ts
│   ├── api/                             # API请求
│   │   ├── request.ts
│   │   ├── config.ts
│   │   └── essay.ts
│   ├── types/                           # 类型定义
│   │   ├── api.ts
│   │   └── essay.ts
│   └── router/
│       └── index.ts
├── .env.development
├── .env.production
├── vite.config.ts
└── package.json
```

## 🔌 API接口设计

### API配置管理

```http
POST /api/configs
创建API配置

GET /api/configs
获取所有配置列表

GET /api/configs/{id}
获取配置详情

PUT /api/configs/{id}
更新配置

DELETE /api/configs/{id}
删除配置

PUT /api/configs/{id}/default
设置为默认配置
```

### 作文评分

```http
POST /api/essays/submit
提交作文并评分
请求体：
{
  "content": "作文内容",
  "essayType": "IELTS",        // 可选
  "configId": 1                // 可选，不传则使用默认配置
}

响应：
{
  "essayId": 1,
  "score": {
    "overallScore": 85,
    "contentScore": 28,
    "languageScore": 26,
    "structureScore": 18,
    "coherenceScore": 13,
    "strengths": ["..."],
    "suggestions": ["..."],
    "errors": [...],
    "detailedFeedback": "..."
  },
  "processingTime": 3500
}
```

### 历史记录

```http
GET /api/essays/history?page=0&size=10
获取历史记录（分页）

GET /api/essays/{id}
获取作文详情和评分结果
```

## 🤖 AI模型集成

### 支持的模型提供商

1. **OpenAI**
   - baseUrl: `https://api.openai.com/v1`
   - models: gpt-4o, gpt-4o-mini, gpt-3.5-turbo

2. **Anthropic (Claude)**
   - baseUrl: `https://api.anthropic.com/v1`
   - models: claude-3-5-sonnet-20241022, claude-3-5-haiku-20241022

3. **OpenRouter** (推荐)
   - baseUrl: `https://openrouter.ai/api/v1`
   - models: 支持多种模型，包括免费模型

4. **DeepSeek**
   - baseUrl: `https://api.deepseek.com/v1`
   - models: deepseek-chat, deepseek-coder

### Prompt模板

```java
你是一位专业的英语写作评分专家，拥有20年教学经验。
请对以下英语作文进行全面评分和分析。

评分标准（总分100分）：
1. 内容完整性（30分）：主题明确、论据充分、逻辑清晰
2. 语言准确性（30分）：词汇丰富、语法正确、表达准确
3. 文章结构（20分）：段落分明、层次清晰、首尾呼应
4. 连贯性（20分）：过渡自然、衔接紧密、流畅通顺

请严格按照以下JSON格式返回：
{
  "overallScore": 85,
  "contentScore": 28,
  "languageScore": 26,
  "structureScore": 18,
  "coherenceScore": 13,
  "strengths": [
    "用具体例子说明了观点",
    "词汇使用丰富多样"
  ],
  "suggestions": [
    "建议增加更多过渡词提高连贯性",
    "可以适当使用复合句丰富句式"
  ],
  "errors": [
    {
      "sentence": "He go to school.",
      "type": "GRAMMAR",
      "description": "主谓不一致，应使用goes",
      "correction": "He goes to school."
    }
  ],
  "detailedFeedback": "总体评价..."
}

待评分作文：
{essay_content}
```

## 🚀 实施计划

### 阶段一：后端基础（3天）
- [x] 创建Spring Boot项目
- [ ] 配置Gradle依赖
- [ ] 设计数据库表结构
- [ ] 创建Entity实体类
- [ ] 创建Repository接口

### 阶段二：AI服务集成（2天）
- [ ] 集成LangChain4j
- [ ] 实现ModelFactory
- [ ] 设计评分Prompt
- [ ] 实现评分结果解析

### 阶段三：核心API（2天）
- [ ] 实现API配置管理接口
- [ ] 实现作文提交评分接口
- [ ] 实现历史记录接口
- [ ] 异常处理和统一响应

### 阶段四：前端开发（4天）
- [ ] 创建Vue项目
- [ ] 配置路由和状态管理
- [ ] 实现API配置页面
- [ ] 实现作文提交页面
- [ ] 实现评分展示页面
- [ ] 实现历史记录页面

### 阶段五：部署（1天）
- [ ] 编写Dockerfile
- [ ] 编写docker-compose.yml
- [ ] 测试部署流程
- [ ] 编写部署文档

## 🔐 安全考虑

1. **API Key加密存储**
   - 使用Jasypt加密API Key
   - 传输使用HTTPS

2. **输入验证**
   - 作文长度限制
   - 内容安全检查

3. **频率限制**
   - 单IP请求频率限制
   - 防止API滥用

## 📈 性能优化

1. **异步处理**
   - 评分任务异步执行
   - 使用CompletableFuture

2. **缓存策略**（后续）
   - Redis缓存配置
   - 相似作文缓存

3. **数据库优化**
   - 合理的索引设计
   - 分页查询优化

## 📝 开发规范

### Git提交规范
```
feat: 新功能
fix: 修复bug
docs: 文档更新
style: 代码格式
refactor: 重构
test: 测试
chore: 构建/工具
```

### 分支管理
```
main: 生产分支
develop: 开发分支
feature/*: 功能分支
bugfix/*: 修复分支
```

## 🔄 后续迭代计划

### V1.1
- [ ] 用户系统和认证
- [ ] Redis缓存优化

### V1.2
- [ ] 句子级错误标注
- [ ] 批量评分

### V1.3
- [ ] 学习报告生成
- [ ] 进步趋势分析

## 📚 参考资料

- [Spring Boot官方文档](https://spring.io/projects/spring-boot)
- [LangChain4j文档](https://docs.langchain4j.dev/)
- [Vue 3官方文档](https://vuejs.org/)
- [Element Plus文档](https://element-plus.org/)
- [PostgreSQL文档](https://www.postgresql.org/docs/)
