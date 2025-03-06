# AI英语作文评分系统

一个基于Spring Boot和AI大语言模型的英语作文自动评分系统。系统能够对英语作文进行智能分析，提供分数评定、优点分析和改进建议。

## 功能特点

- 智能作文评分（0-100分）
- 详细的优点分析
- 具体的改进建议
- 历史记录查看
- 分页加载
- 响应式界面

## 技术栈

- 后端：
  - Java 21
  - Spring Boot 3.2.3
  - Spring AI 1.0.0-RC1
  - H2 Database
  - Lombok
  - Gradle

- 前端：
  - Bootstrap 5
  - Marked.js (Markdown渲染)

## 快速开始

### 环境要求

- JDK 21+
- Gradle 8.x
- OpenRouter API Key

### 配置

1. 创建`src/main/resources/config/application-secrets.properties`文件：
```properties
openrouter.api-key=your-api-key-here
```

2. 配置`application.properties`：
```properties
# 应用配置
spring.application.name=aiwriting
server.port=8080

# H2配置
spring.datasource.url=jdbc:h2:mem:aiwriting
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# OpenRouter配置
openrouter.base-url=https://openrouter.ai/api/v1
openrouter.model=google/gemma-2-9b-it:free
```

### 运行

```bash
./gradlew bootRun
```

访问 http://localhost:8080 即可使用系统。

## API文档

### 提交作文
```http
POST /api/essays
Content-Type: application/json

{
    "content": "作文内容"
}
```

### 获取历史记录
```http
GET /api/essays/history?page=0&size=10
```

### 获取作文详情
```http
GET /api/essays/{id}
```

## 项目结构

```
src/main/java/com/jinmo/aiwriting/
├── config/          # 配置类
├── controller/      # REST控制器
├── domain/         # 领域模型
│   ├── entity/    # 实体类
│   └── dto/       # 数据传输对象
├── service/        # 业务逻辑
│   ├── ai/        # AI服务相关
│   └── essay/     # 作文服务相关
├── repository/     # 数据访问层
└── common/        # 公共组件
    ├── exception/ # 异常处理
    └── response/  # 响应封装
```

## 开发计划

- [ ] 添加用户认证
- [ ] 支持多种评分模型
- [ ] 添加作文标签/分类
- [ ] 支持批量导入导出
- [ ] 添加数据统计分析


## 许可证

[MIT License](LICENSE) 