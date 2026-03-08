# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个基于Spring Boot 3.2.3和Java 21的AI英语作文评分系统。系统通过OpenRouter API集成大语言模型（默认使用Google Gemma 2 9B），对英语作文进行智能分析，提供分数评定、优点分析和改进建议。

## 核心技术栈

- **Java 21** with GraalVM (支持Native Image编译)
- **Spring Boot 3.2.3** (Web, JPA, Validation, Thymeleaf)
- **Spring AI 0.8.0** (AI服务集成)
- **H2 Database** (内存数据库)
- **Gradle 8.x** (构建工具)
- **Lombok** (代码简化)

## 常用命令

### 开发运行
```bash
# 使用Gradle运行（推荐）
./gradlew bootRun

# 使用脚本运行
chmod +x run.sh
./run.sh
```

### 构建和测试
```bash
# 编译项目
./gradlew build

# 运行测试
./gradlew test

# 清理构建
./gradlew clean
```

### Native Image构建
```bash
# 构建Native Image（需要GraalVM）
./gradlew nativeCompile

# 运行Native Image
./build/native/nativeCompile/aiwriting
```

### 访问应用
- 主页: http://localhost:8080
- H2 Console: http://localhost:8080/h2-console

## 架构设计

### 分层架构
项目采用经典的分层架构，严格遵循职责分离：

1. **Controller层** (`controller/`): REST API端点，处理HTTP请求/响应
2. **Service层** (`service/`): 业务逻辑处理
   - `service/ai/`: AI服务相关（OpenRouter客户端、作文分析）
   - `service/`: 作文业务逻辑
3.**Domain层** (`domain/`): 领域模型
   - `domain/entity/`: JPA实体类
   - `domain/dto/`: 数据传输对象（使用record）
4.**Config层** (`config/`): 配置类（OpenRouter、重试策略等）
5.**Common层** (`common/`): 公共组件
   - `common/exception/`: 异常定义和全局异常处理
   - `common/response/`: 统一响应封装

### AI服务集成架构

**核心组件**:
- `OpenRouterClient`: 实现Spring AI的`ChatClient`接口，封装OpenRouter API调用
- `EssayAIService`: 作文分析服务，包含语言检测、JSON清理、UTF-8编码处理
- `ModelRequestStrategy`: 策略模式支持多模型（GPT、Gemini/Gemma）

**重试机制**: 使用Spring Retry，配置在`RetryConfig`中
- 最大重试次数: 3次
- 退避策略: 指数退避（初始2秒，倍数2，最大10秒）

**Prompt工程**:
- 系统使用中文反馈的专业评分Prompt（见`EssayAIService.analyzeEssay()`）
- 要求AI返回结构化JSON格式（score, strengths, suggestions）
- 包含语言检测和错误处理逻辑

### 异常处理体系

全局异常处理器 `GlobalExceptionHandler` 统一处理：
- `AIServiceException`: AI服务异常（支持JSON格式错误消息）
- `ResourceNotFoundException`: 资源不存在异常
- `MethodArgumentNotValidException`: 参数验证异常
- `ConstraintViolationException`: 约束验证异常
- 通用异常: 返回500错误

所有异常响应格式统一为：
```json
{
  "error": "ERROR_CODE",
  "message": "错误消息",
  "details": {
    "suggestion": "建议"
  }
}
```

### 编码和字符集处理

项目特别注重UTF-8编码处理（针对中文反馈）：
- JVM参数配置UTF-8编码（`build.gradle`）
- Spring配置强制UTF-8（`application.properties`）
- OpenRouter客户端设置Accept-Charset头
- EssayAIService包含编码检测和转换逻辑

## 配置管理

### 必需配置
创建 `src/main/resources/config/application-secrets.properties`:
```properties
openrouter.api-key=your-api-key-here
```

### 主要配置项 (`application.properties`)
- `openrouter.model`: AI模型选择（默认google/gemma-2-9b-it:free）
- `spring.ai.retry.*`: 重试策略配置
- JVM参数: 使用分代ZGC垃圾收集器，最大堆内存2GB

## 开发规范（来自.cursorrules）

### 实体类规范
- 使用`@Entity`和`@Data`注解
- ID使用Long类型配合`@GeneratedValue`
- 必须包含`createdAt`字段，使用`@PrePersist`自动设置

### DTO规范
- 使用Java record定义
- 提供`fromEntity()`静态方法进行转换
- 分别定义Request和Response DTO

### 服务层规范
- 接口和实现分离
- 使用`@Transactional`管理事务
- 异常必须转换为自定义业务异常
- AI服务调用必须有重试机制

### 控制器规范
- 使用`@RestController`和`@RequestMapping`
- 统一返回`ResponseEntity`
- 请求参数使用`@Valid`验证

## 性能优化

### JVM优化
- 启用分代ZGC (`-XX:+UseZGC -XX:+ZGenerational`)
- 指针压缩 (`-XX:+UseCompressedOops`)
- OOM时自动生成堆转储 (`-XX:+HeapDumpOnOutOfMemoryError`)

### Native Image支持
- 配置GraalVM Native Build Tools插件
- 启用AOT编译 (`spring.aot.enabled=true`)
- 移除YAML支持以减小体积

## API端点

### 提交作文评分
```
POST /api/essays
Content-Type: application/json

{
  "content": "英语作文内容"
}
```

### 获取历史记录（分页）
```
GET /api/essays/history?page=0&size=10
```

### 获取作文详情
```
GET /api/essays/{id}
```

## 注意事项

1. **语言检测**: 系统只接受英语作文，通过`isEnglishText()`方法检测（允许20%非英语字符）
2. **API Key安全**: 不要将API Key提交到版本控制，使用`application-secrets.properties`
3. **编码问题**: 如遇到中文乱码，检查JVM参数和Spring编码配置
4. **重试策略**: AI服务调用失败会自动重试，注意日志中的重试次数
5. **数据库**: 开发环境使用H2内存数据库，重启后数据丢失

## 开发计划

当前待实现功能（见README.md）：
- 重构错误处理体系，使用统一异常对象
- 重构前端，不再使用Bootstrap
- 支持一键多模型切换
- 支持MySQL数据库
- 添加用户认证
