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
- GraalVM CE 21+ (推荐使用最新版本)
- Gradle 8.x
- OpenRouter API Key ([点此获取](https://openrouter.ai/keys))

### 配置

#### JVM配置

项目默认启用了分代ZGC垃圾收集器以获得更好的性能：

```properties
# JVM参数
-XX:+UseZGC              # 启用ZGC
-XX:+ZGenerational       # 启用分代ZGC
-Xmx2g                   # 设置最大堆内存为2GB
-XX:+UseCompressedOops   # 启用指针压缩
```

> 注意：这些参数已在build.gradle中配置，无需手动设置。

#### 获取API Key

1. 访问 [OpenRouter Gemma 2 9B API页面](https://openrouter.ai/google/gemma-2-9b-it:free/api)
2. 点击"Create API key"按钮
3. 登录或注册OpenRouter账号
4. 创建并复制生成的API Key

1. 创建`src/main/resources/config/application-secrets.properties`文件：

```properties
openrouter.api-key=your-api-key-here
```

> 注意：Gemma 2 9B (free)模型目前对输入和输出tokens都是免费的。

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

#### 使用Gradle运行

```bash
./gradlew bootRun
```

#### 使用脚本运行（推荐）

```bash
# 添加执行权限
chmod +x run.sh
# 运行
./run.sh
```

#### 构建Native Image（可选）

```bash
# 构建Native Image
./gradlew nativeCompile

# 运行Native Image
./build/native/nativeCompile/aiwriting
```

> 注意：Native Image构建需要安装GraalVM和native-image工具。

访问 <http://localhost:8080> 即可使用系统。

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

## 性能优化

### GC优化

- 使用分代ZGC实现低延迟垃圾回收
- 启用指针压缩减少内存占用
- 自动生成堆转储文件便于问题诊断

### Native Image优化

- 支持GraalVM Native Image构建
- 显著减少启动时间和内存占用
- 提供更好的容器化支持

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

- [ ] 支持一键多模型切换
- [ ] 支持 MySQL 数据库
- [ ] 添加用户认证
- [ ] 支持多种评分模型
- [ ] 添加作文标签/分类
- [ ] 支持批量导入导出
- [ ] 添加数据统计分析
- [ ] 优化Native Image构建配置
- [ ] 添加性能监控和指标收集

## 许可证

[MIT License](LICENSE)
