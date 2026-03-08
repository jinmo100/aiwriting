# AI英语作文评分系统 v2.0

一个基于 Spring Boot 3.4 + Vue 3 + LangChain4j 的现代化AI英语作文评分系统。

## ✨ 特性

- 🤖 **多模型支持** - 支持 OpenAI、Claude、Gemini、DeepSeek 等多种AI模型
- 📊 **多维度评分** - 从内容、语言、结构、连贯性四个维度评分
- 💡 **详细反馈** - 提供优点、建议、错误标注等详细反馈
- 🔧 **灵活配置** - 用户可自定义API配置，支持多模型切换
- 🐳 **容器化部署** - 完整的Docker支持，一键部署

## 🛠️ 技术栈

### 后端
- Java 21
- Spring Boot 3.4.2
- MyBatis-Plus 3.5.9 (含 mybatis-plus-jsqlparser 分页插件)
- LangChain4j 0.36.2
- PostgreSQL 16 / H2 Database (开发环境)

### 前端
- Vue 3.4
- TypeScript 5
- Element Plus 2.6
- Vite 5

## 📦 快速开始

### 前置要求
- JDK 21+
- Node.js 18+
- PostgreSQL 16+
- Docker & Docker Compose（可选）

### 方式一：Docker部署（推荐）

```bash
# 1. 克隆项目
git clone <repository-url>
cd aiwriting

# 2. 构建后端
./gradlew build

# 3. 启动所有服务
docker-compose up -d

# 4. 访问应用
# 前端：http://localhost
# 后端：http://localhost:8080
# API文档：http://localhost:8080/swagger-ui.html
```

### 方式二：本地开发

#### 后端启动

```bash
# 1. 创建数据库
psql -U postgres
CREATE DATABASE aiwriting;

# 2. 执行初始化脚本
psql -U postgres -d aiwriting -f src/main/resources/db/init.sql

# 3. 启动后端
./gradlew bootRun
```

#### 前端启动

```bash
# 1. 进入前端目录
cd frontend

# 2. 安装依赖
npm install

# 3. 启动开发服务器
npm run dev

# 4. 访问 http://localhost:3000
```

## 📖 使用指南

### 1. 配置API

首次使用需要配置AI模型的API：

1. 访问"API配置"页面
2. 点击"新增配置"
3. 填写配置信息：
   - **配置名称**: 自定义名称，如"OpenRouter Gemma"
   - **提供商**: 选择提供商（OpenRouter、OpenAI等）
   - **API Base URL**: API地址
   - **API Key**: 你的API密钥
   - **模型名称**: 使用的模型名称
   - **设为默认**: 勾选后作为默认配置

#### 推荐配置

**OpenRouter（免费）**
- Base URL: `https://openrouter.ai/api/v1`
- Model: `google/gemma-2-9b-it:free`
- 获取API Key: https://openrouter.ai/keys

**OpenAI**
- Base URL: `https://api.openai.com/v1`
- Model: `gpt-4o` 或 `gpt-4o-mini`

### 2. 提交作文

1. 访问"提交作文"页面
2. 输入英语作文内容（50-10000字）
3. 选择作文类型（可选）
4. 选择API配置（可选，默认使用默认配置）
5. 点击"提交评分"

### 3. 查看结果

评分完成后，系统会展示：
- **总分**: 0-100分
- **各维度分数**: 内容、语言、结构、连贯性
- **优点分析**: 文章的优点
- **改进建议**: 具体的改进建议
- **错误标注**: 语法、词汇等错误
- **详细评价**: 整体评价

## 🗂️ 项目结构

```
aiwriting/
├── src/main/java/com/jinmo/aiwriting/
│   ├── ai/              # AI服务（LangChain4j）
│   │   ├── AIService.java           # AI评分核心服务
│   │   ├── ModelFactory.java        # 动态模型工厂
│   │   └── prompt/                  # Prompt模板
│   ├── controller/      # REST API控制器
│   │   ├── ApiConfigController.java # API配置管理
│   │   └── EssayController.java     # 作文评分接口
│   ├── service/         # 业务逻辑层
│   │   ├── ApiConfigService.java    # API配置服务
│   │   └── EssayService.java        # 作文服务
│   ├── mapper/          # MyBatis-Plus Mapper
│   │   ├── ApiConfigMapper.java
│   │   ├── EssayMapper.java
│   │   └── EssayScoreMapper.java
│   ├── domain/          # 实体和DTO
│   │   ├── entity/      # 实体类
│   │   └── dto/         # 数据传输对象
│   ├── config/          # 配置类
│   │   ├── MybatisPlusConfig.java   # MyBatis-Plus配置（含分页插件）
│   │   ├── WebConfig.java           # CORS配置
│   │   ├── JacksonConfig.java       # JSON序列化配置
│   │   ├── OpenApiConfig.java       # API文档配置
│   │   └── MyMetaObjectHandler.java # 自动填充处理器
│   └── common/          # 公共组件
│       ├── exception/   # 异常定义
│       └── response/    # 统一响应
├── src/main/resources/
│   ├── application.yml              # 主配置文件
│   ├── application-dev.yml          # 开发环境配置
│   ├── application-prod.yml         # 生产环境配置
│   └── db/init.sql                  # 数据库初始化脚本
├── frontend/                        # Vue前端
│   ├── src/
│   │   ├── views/       # 页面组件
│   │   ├── api/         # API请求模块
│   │   ├── stores/      # Pinia状态管理
│   │   └── types/       # TypeScript类型定义
│   └── vite.config.ts
├── docs/                            # 项目文档
│   ├── MVP_PLAN.md                  # MVP计划文档
│   ├── 编译错误修复文档.md            # 第一轮修复记录
│   ├── 编译错误完整修复记录.md        # 完整修复记录
│   └── 如何生成编译日志.md            # 编译调试指南
├── docker-compose.yml               # Docker编排配置
├── Dockerfile.backend               # 后端容器配置
├── Dockerfile.frontend              # 前端容器配置
├── build.gradle                     # Gradle构建配置
└── README.md                        # 项目说明文档
```

## 🔌 API文档

启动后端后访问：http://localhost:8080/swagger-ui.html

### 主要接口

- `POST /api/configs` - 创建API配置
- `GET /api/configs` - 获取所有配置
- `PUT /api/configs/{id}` - 更新配置
- `DELETE /api/configs/{id}` - 删除配置
- `PUT /api/configs/{id}/default` - 设置默认配置

- `POST /api/essays/submit` - 提交作文评分
- `GET /api/essays/history` - 获取历史记录
- `GET /api/essays/{id}` - 获取作文详情

## 🔧 配置说明

### 后端配置

`src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/aiwriting
    username: aiwriting
    password: aiwriting123

# MyBatis-Plus配置
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 开发环境SQL日志
  global-config:
    db-config:
      id-type: auto  # 主键自增
```

### 开发环境配置

开发环境使用H2内存数据库，无需安装PostgreSQL：

`src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:aiwriting_dev;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
    username: sa
    password:
    driver-class-name: org.h2.Driver
  h2:
    console:
      enabled: true
      path: /h2-console
```

### 前端配置

`frontend/.env.development`:

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

## 📝 开发计划

- [ ] 用户系统和认证
- [ ] Redis缓存优化
- [ ] 句子级错误标注增强
- [ ] 学习报告生成
- [ ] 进步趋势分析
- [ ] 批量评分功能

## 🔥 最近更新

### v2.0.0 (2026-03-08)

#### 架构重构
- ✅ 从JPA迁移到MyBatis-Plus 3.5.9
- ✅ 集成LangChain4j 0.36.2，支持多模型切换
- ✅ 重构AI服务，实现动态模型工厂模式
- ✅ 实现用户自定义API配置功能

#### 依赖更新
- ✅ MyBatis-Plus 3.5.9 (含 mybatis-plus-jsqlparser 分页插件)
- ✅ Spring Boot 3.4.2
- ✅ LangChain4j 0.36.2
- ✅ H2 Database支持（开发环境）

#### 配置优化
- ✅ 新增JacksonConfig - 统一JSON序列化配置
- ✅ 新增OpenApiConfig - Swagger UI文档配置
- ✅ 优化MybatisPlusConfig - 分页插件配置
- ✅ 完善异常处理体系

#### 文档完善
- ✅ MVP架构设计文档
- ✅ 编译错误修复完整记录
- ✅ 编译调试指南

详细更新日志请查看 `docs/` 目录。

## 🐛 故障排除

### 编译问题

如遇到编译错误，请参考：
- `docs/如何生成编译日志.md` - 编译调试指南
- `docs/编译错误完整修复记录.md` - 常见问题解决方案

### 常见问题

**Q: MyBatis-Plus分页插件报错找不到类？**

A: MyBatis-Plus 3.5.9版本将分页插件分离到独立依赖，需添加：
```gradle
implementation 'com.baomidou:mybatis-plus-jsqlparser:3.5.9'
```

**Q: H2数据库控制台如何访问？**

A: 开发环境下访问 http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:aiwriting_dev`
- 用户名: `sa`
- 密码: 空

**Q: 如何配置多模型切换？**

A: 在"API配置"页面添加不同的配置，提交作文时选择对应配置即可。

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！
