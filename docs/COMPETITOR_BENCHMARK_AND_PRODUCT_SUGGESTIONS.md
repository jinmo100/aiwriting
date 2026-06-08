# 同类 AI 写作/作文评分产品对标与追加建议

更新时间：2026-06-08

## 1. 对标目标

当前项目是一个 英作评析，已具备：

- API 配置管理和多 Provider 协议适配。
- API Key 加密存储、连接测试、结构化输出测试。
- 作文类型驱动评分：初中/中考/高中/高考/CET/IELTS/TOEFL Independent 等。
- DB Rubric 与版本化评分标准。
- 异步作文提交和 AI Thinking 等待态。
- Redis/DB 幂等与防重复提交。
- 用户账号、Redis Session + HttpOnly Cookie、用户维度数据隔离。
- 用户自带 Provider/API/模型配置，结果页透明展示 Provider、Endpoint、Model、Token、usageSource 和预计费用。
- 动态维度分数、原生分、100 分换算、等级、置信度。
- 优点分析、优先改进建议、片段级错误标注。
- 输入防御、安全分析、prompt injection 和敏感内容规则。
- AI 失败分类、中文错误提示、最多 3 次受控重试。
- 24 篇离线评分一致性基准集与软门禁报告。
- 逐句/片段批注 quote 高亮与同水平提升版范文展示。
- 多版本作文修改链：`parentEssayId`、`essayGroupId`、`versionNo`。
- 个人学习看板：提交数、平均/最高分、近期活跃和类型分布。
- 历史记录和动态结果页。

本文件参考同类型产品与项目能力，补充产品、技术、教学和商业化方向建议。

对标对象包括：

- Grammarly
- ETS e-rater / Criterion
- Cambridge Write & Improve
- QuillBot
- ProWritingAid
- Turnitin Draft Coach
- 通用 LLM 写作助手类产品

## 1.1 已决事项落地更新

2026-06-07 已确认并落地的方向：

- 中文用户优先，题目/任务要求 `taskPrompt` 可以是中文，结果页中文友好。
- 覆盖初中、高中用户，新增初中/中考/高中/高考作文类型。
- 彻底废除旧四维字段，统一动态 RubricScoringResult。
- Rubric 与类型特定 prompt instruction 放 DB，通过版本发布迭代。
- 对非英文比例、超长/过短、特殊符号、emoji、prompt injection、隐私和高风险敏感内容做 PASS/WARN/REJECT。
- 评分主链路异步化，用 `idempotencyKey` + `contentHash` + Redis/DB 防重复提交。
- “loading”文案改为更能体现模型分析过程的 `AI Thinking`。
- 写作辅助工具箱、Rubric 管理后台、多版本作文等放后续阶段。

2026-06-08 运行验收补充：

- 使用临时本机 PostgreSQL 18.4 + Redis 8.8 运行时启动后端，Flyway schema 已迁移到 `V9`。
- 前端 dev server 在 `127.0.0.1:5173` 启动成功，`/login` 可访问。
- 已通过 smoke test 覆盖注册、Cookie Session 下当前用户、dashboard 空账号汇总、当前用户配置空列表。
- 未调用真实 AI Provider；真实评分端到端仍依赖用户先配置自己的 Provider/API Key。

## 2. 同类产品能力对标

### 2.1 Grammarly

定位：

- 通用英文写作助手。
- 面向邮件、文档、网页输入框、办公场景。

核心能力：

- 语法、拼写、标点检查。
- 清晰度、简洁度、语气建议。
- 改写建议。
- 生成式 AI 写作辅助。
- 风格和受众适配。
- 浏览器插件、桌面端、Office集成。
- 高级版本包含抄袭检测等能力。

可借鉴点：

- 不只给分，还要提供“可点击的一处处修改建议”。
- 建议应该分类型：语法、拼写、表达、语气、清晰度、冗余。
- 用户体验上应接近“边写边改”，而不是只在提交后一次性给结果。
- 可提供“接受建议/忽略建议”的交互。

对当前项目启发：

- 当前项目更像“一次性作文批改”，还不是“写作助手”。
- 后续可增加句子级和词级批注，让用户能直接看到哪里错、怎么改、为什么改。

### 2.2 ETS e-rater / Criterion

定位：

- 面向标准化考试和教育机构的自动作文评分。
- 更强调评分一致性、评分维度、教学反馈。

核心能力：

- 自动作文评分。
- 语法、用法、机械错误检测。
- 写作质量特征分析。
- 与评分量表结合。
- 对教师和学生提供结构化反馈。

可借鉴点：

- 评分必须稳定、可解释、可复核。
- 不同考试类型应使用不同评分量表。
- 需要有可追踪的 rubric，而不只是让模型自由发挥。
- 应支持教师/管理员查看学生进步。

对当前项目启发：

- 当前 Prompt 写死在代码里，评分标准也较通用。
- 后续应将 IELTS、TOEFL、CET-4、CET-6 等评分标准配置化。
- 每次评分应保存所用 rubric 版本，便于复盘和对比。

### 2.3 Cambridge Write & Improve

定位：

- 面向英语学习者的在线写作练习和即时反馈工具。
- 强调 CEFR 等级、反复修改、学习进步。

核心能力：

- 用户提交作文后获得即时反馈。
- 给出大致英语水平等级。
- 支持多次修改和重新提交。
- 反馈面向学习者，强调提高。

可借鉴点：

- 评分结果应支持“版本迭代”：初稿、二稿、三稿。
- 反馈不只告诉错误，还要告诉用户下一次如何提高。
- 可建立进步曲线，而不是孤立地看单篇作文。

对当前项目启发：

- 当前只有历史列表，没有同一篇作文的多版本修改记录。
- 后续可增加“根据建议修改后再次评分”的闭环。

### 2.4 QuillBot

定位：

- 改写、语法检查、总结、引用生成等写作工具集合。

核心能力：

- Paraphraser 改写。
- Grammar Checker。
- Summarizer。
- Citation Generator。
- Plagiarism Checker。
- 多种写作模式和语气。

可借鉴点：

- 写作产品可以拆成多个独立工具，而不是只有作文评分。
- 改写能力可以细分模式：正式、简洁、学术、流畅、扩展。
- 用户往往需要“马上可用的改写结果”。

对当前项目启发：

- 可以增加“句子润色”“段落改写”“扩写论据”“生成提纲”等辅助功能。
- 作文评分前可以先提供“写作前工具”，评分后提供“修改工具”。

### 2.5 ProWritingAid

定位：

- 面向长文作者、学生、专业写作者的写作分析工具。

核心能力：

- 语法检查。
- 风格建议。
- 可读性分析。
- 重复词、句式变化、被动语态等报告。
- 长文结构分析。

可借鉴点：

- 报告维度可以非常细。
- 不同用户关注点不同：学生看分数，作者看风格，教师看进步。
- 长文分析需要结构化报告，而不只是几条建议。

对当前项目启发：

- 增加“写作报告”页：
  - 词汇多样性。
  - 平均句长。
  - 复杂句比例。
  - 连接词使用。
  - 重复词统计。
  - 被动语态比例。
  - 可读性指标。

### 2.6 Turnitin Draft Coach

定位：

- 学术写作辅导工具。
- 强调相似度、引用、语法反馈。

核心能力：

- Similarity Check。
- Citation Check。
- Grammar Guide。
- 与学生写作流程结合。

可借鉴点：

- 学术场景下，抄袭/引用/来源质量非常重要。
- 写作反馈不仅是语言问题，也包括学术诚信。

对当前项目启发：

- 如果未来面向大学生或学术写作，应增加：
  - 引用格式检查。
  - 参考文献检查。
  - 相似度提示。
  - AI 生成痕迹提醒。

## 3. 当前项目与成熟产品的差距

### 3.1 产品能力差距

当前项目已有“提交后异步评分 + 动态 Rubric 报告”，但仍缺少：

- 写作前辅助。
- 写作中实时检查。
- 正文内逐句高亮和接受/忽略建议交互。
- 多版本迭代。
- 学习进步追踪。
- 教师/班级视角。
- 地区化/校本化 Rubric 管理。
- 更可复核的评分校准和人工抽检机制。

### 3.2 技术能力差距

当前项目已补齐 Provider 抽象、响应校验、数据库 migration、API Key 加密、异步评分、基础幂等、用户维度隔离、评分调用 token/cost 观测、AI 失败分类与受控重试阶段 1、离线评分一致性基准集阶段 1；后续仍缺少：

- Provider 测试、模型拉取、Rubric 测试等非评分调用的观测日志。
- 更细的队列级错误、持久化错误和 Provider 熔断/退避治理。
- 审计日志和更系统的脱敏日志。
- 后台任务队列、超时治理、SSE/WebSocket 推送。
- 自动化 E2E 测试和 CI 固化。
- 真实 Provider 回放、分数漂移检测和回归评测闭环。

### 3.3 教学能力差距

当前反馈已经能指出优点和建议，但还缺少：

- 为什么错。
- 如何改。
- 对应语法知识点。
- 练习题推荐。
- 同类错误复现统计。
- 学习者长期能力画像。

## 4. 建议新增产品模块

### 4.1 作文评分模块增强

当前：

```text
原生分 + 100 分换算 + 动态 Rubric 维度 + 置信度 + 证据 + 建议 + 片段级错误标注
```

后续建议扩展为：

```text
总分
维度分
评分等级
Rubric 对照解释
逐句批注
错误类型统计
修改后范文
下一步练习建议
```

推荐后续增强字段/模块：

```text
sentence_annotations_with_offsets
error_summary
rewrite_suggestion
next_practice
score_calibration_notes
attempt_version
```

### 4.2 Rubric 管理模块

不同作文类型评分标准不同：

- IELTS Task 1
- IELTS Task 2
- TOEFL Independent Writing
- CET-4
- CET-6
- 高考英语作文
- 通用议论文
- 邮件/应用文

建议建立 `rubrics` 表：

```text
id
name
exam_type
version
dimensions
max_score
prompt_template
active
created_at
updated_at
```

收益：

- Prompt 不再硬编码。
- 评分标准可更新。
- 每次评分可记录 rubric 版本。
- 后续支持教师自定义评分标准。

### 4.3 多版本作文修改模块

学习写作的核心不是“一次评分”，而是“修改后变好”。

建议设计：

```text
essay_group
  ├── draft v1
  ├── draft v2
  └── draft v3
```

功能：

- 初稿评分。
- 根据建议修改。
- 再次提交。
- 对比两个版本：
  - 总分变化。
  - 错误减少。
  - 句式提升。
  - 词汇提升。

### 4.4 逐句批注模块

参考 Grammarly 的交互体验，建议将错误标注从表格升级为正文内批注。

功能：

- 高亮错误句子。
- 鼠标悬浮展示解释。
- 点击查看修改建议。
- 支持接受/忽略建议。
- 按错误类型筛选。

错误类型建议：

```text
GRAMMAR
SPELLING
PUNCTUATION
VOCABULARY
COLLOCATION
STYLE
CLARITY
COHERENCE
TASK_RESPONSE
STRUCTURE
```

### 4.5 写作报告模块

参考 ProWritingAid，增加可量化指标：

- Word count。
- Sentence count。
- Average sentence length。
- Vocabulary diversity。
- Repeated words。
- Transition words。
- Complex sentence ratio。
- Passive voice count。
- Readability score。
- Topic relevance。

这些指标中一部分可以不用 AI，直接本地计算，降低成本并提高稳定性。

### 4.6 学习进步分析模块

建议新增个人维度统计：

- 最近 7/30/90 天提交次数。
- 平均分趋势。
- 各维度分趋势。
- 高频错误类型。
- 已改善错误类型。
- 最弱能力项。
- 推荐练习方向。

页面：

```text
/dashboard
/progress
/error-analysis
```

### 4.7 写作辅助工具箱

参考 QuillBot，可增加：

- 句子润色。
- 段落改写。
- 扩写论据。
- 生成提纲。
- 生成范文。
- 降重改写。
- 语气转换。
- 学术表达转换。

注意：

- 面向学习场景时，应区分“辅助学习”和“代写”。
- 可设计成“给提示和解释”，而不是直接替学生完成全文。

## 5. 建议新增技术模块

### 5.1 AI Provider 抽象

详细设计见：[AI Provider 抽象设计](./AI_PROVIDER_ABSTRACTION_DESIGN.md)。

Provider 抽象层已经落地；后续重点是继续提升协议适配质量、观测能力和降级策略。

建议抽象：

```java
interface AIClient {
    RubricScoringResult score(RubricScoringRequest request, ApiConfig config);
}

class OpenAIChatCompletionsClient implements AIClient {}
class OpenAIResponsesClient implements AIClient {}
class OpenRouterClient implements AIClient {}
class ClaudeClient implements AIClient {}
class GeminiClient implements AIClient {}
```

配置字段建议：

```text
provider
endpoint_type
base_url
model_name
api_key_encrypted
temperature
max_tokens
timeout_seconds
```

### 5.2 Prompt 模板系统

当前 Prompt 是 Java 字符串。

建议：

- Prompt 模板放数据库或资源文件。
- 每个模板有版本号。
- 每次评分保存所用模板版本。
- 支持 A/B 测试。

模板变量：

```text
essay_content
essay_type
rubric
target_level
language
output_schema
```

### 5.3 结构化输出校验

建议为 AI 返回建立 JSON Schema。

校验内容：

- 必填字段。
- 分数范围。
- 数组长度。
- 错误类型枚举。
- 总分与分项分合理性。

失败处理：

1. 尝试 JSON 修复。
2. 重新请求模型按 schema 修复。
3. 仍失败则保存原始响应和错误状态。

### 5.4 异步评分架构

当前已将同步评分改为异步，主链路为：

```text
POST /api/essays/submit
  -> 返回 essayId + scoreId + status=SCORING

后台评分
  -> status=COMPLETED / FAILED

GET /api/essays/{id}
  -> 查询状态和结果
```

前端体验：

- 提交后进入结果页。
- 展示评分进度。
- 轮询或 SSE 获取结果。

已获得收益：

- 前端不怕 60 秒超时。
- 后端事务更短。
- 可以配合 `idempotencyKey` / `contentHash` 防重复提交。

后续优化：

- 后台任务队列。
- 轮询超时提示。
- SSE/WebSocket 推送。
- 队列级重试、Provider 退避/熔断和持久化失败分类。

### 5.5 成本与用量统计

阶段 1 已新增 `ai_invocation_logs`，评分调用已覆盖：

```text
tokens_input
tokens_output
estimated_cost
provider_latency
request_id
error_code
```

后续应把同一套日志扩展到 Provider 连接测试、模型列表拉取、Rubric 测试和管理后台操作。

用途：

- 分析模型成本。
- 比较模型质量。
- 定位慢请求。
- 做用户限额。

### 5.6 安全与合规

建议：

- API Key 加密存储。
- 日志脱敏。
- 禁止将 API Key 返回给前端。
- 配置变更审计。
- 敏感字段统一 `@JsonIgnore` 或响应 DTO 过滤。
- 生产环境关闭 Swagger 或加认证。

## 6. 建议新增前端体验

### 6.1 结果页改造

当前结果页可以展示基础信息。

建议增加：

- 分数雷达图。
- 维度解释。
- 错误类型分布图。
- 正文高亮批注。
- 一键复制修改建议。
- 修改后再次评分按钮。

### 6.2 历史页改造

建议展示：

- 总分。
- 作文类型。
- 使用模型。
- 评分耗时。
- 创建时间。
- 状态。
- 操作：查看、再次评分、对比版本、删除。

### 6.3 配置页改造

建议：

- Endpoint 类型选择：
  - Chat Completions
  - Responses
  - OpenRouter
  - Claude
  - Gemini
- “测试连接”按钮。
- “测试模型输出格式”按钮。
- API Key 不回显但可保留。
- 显示最近一次调用状态。

## 7. 建议的里程碑路线图

### M1：安全和稳定性

目标：确保当前 MVP 可安全使用。

任务：

- 关闭 API Key 明文日志。
- API Key trim + 加密。
- AI 调用错误分类。
- 配置页增加测试连接。
- 固化 E2E 测试。

### M2：AI 调用架构重构

目标：支持多端点、多模型稳定接入。

任务：

- 增加 `endpoint_type`。
- 拆分 AI client。
- Prompt 模板版本化。
- JSON Schema 校验。

### M3：作文学习闭环

目标：从“评分工具”变成“学习工具”。

任务：

- 多版本作文。
- 修改前后对比。
- 逐句批注。
- 错误类型统计。
- 个性化改进建议。

### M4：数据与教学分析

目标：支持长期学习和教师场景。

任务：

- 学习进步 dashboard。
- 高频错误分析。
- 班级/学生管理。
- Rubric 管理。

### M5：商业化/产品化

目标：形成可持续产品。

任务：

- 用户系统。
- 套餐与调用额度。
- 成本统计。
- 多租户隔离。
- 部署和监控。

## 8. 下一阶段最值得优先做的 10 个改动

2026-06-08 讨论结论：下一阶段目标从“单纯评分体验增强”升级为 **产品化底座 + 评分可信度 + 学习闭环**。由于用户会自己填写 API 与模型，用户系统、数据隔离、调用透明度成为所有后续能力的前置依赖。

### 8.1 优先级排序

1. **最小用户系统（阶段 1 已落地）**
   - 注册、登录、登出、当前用户。
   - Redis server-side session，Session ID 通过 `HttpOnly Cookie` 保存。
   - 开放注册，通过配置开关控制。
   - 角色先做 `USER` / `ADMIN`。
   - 注册、登录、reveal-key、提交评分、Provider 测试接入 Redis 轻量限流。

2. **业务数据按用户隔离（阶段 1 已落地）**
   - `essays.user_id`、`api_configs.owner_user_id`、AI 调用日志 `user_id`。
   - 历史、详情、配置、调用明细全部按当前用户过滤。
   - 越权访问详情统一返回 `404`，避免暴露 ID 是否存在。
   - `idempotencyKey` 从全局唯一调整为 `UNIQUE(user_id, idempotency_key)`。
   - 用户系统上线后不保留匿名评分兼容。

3. **用户自带模型配置透明化（阶段 1 已落地）**
   - API 配置以用户私有 `PRIVATE` 为主，预留管理员 `PUBLIC` 配置。
   - 提交评分前必须有可用配置；没有配置时引导用户先添加配置。
   - 注册后不创建空配置，跳转配置页并提供 Provider 模板引导。
   - 用户可以显式查看自己 `PRIVATE` 配置的完整 API Key，不要求二次输入密码。
   - 前端不把完整 Key 写入 `localStorage` / `sessionStorage`，后端不打印明文；`PUBLIC` 配置不向普通用户 reveal 完整 Key。

4. **AI 调用日志与 token/cost 展示（阶段 1 已落地）**
   - 阶段 1 已落地：新增 `ai_invocation_logs` 明细表，记录作文评分调用。
   - 后续补齐 Provider 测试、模型拉取、Rubric 测试等非评分调用。
   - 字段覆盖 provider、endpoint type、model、requestId、latency、input/output/total tokens、usageSource、estimatedCost、currency、failureCode。
   - 用户侧默认展示评分汇总，也可折叠查看安全化调用明细。
   - Provider 返回 usage 时优先使用精确 token；拿不到时允许本地估算，但必须标记“约/估算”。
   - 预计费用由用户在 API 配置中填写输入/输出 token 单价和币种后计算；未配置单价时只展示 token。

5. **AI 失败分类与可重试机制（阶段 1 已落地）**
   - 已区分配置错误、Provider 超时、限流、内容拒绝、AI 响应格式异常、通用 Provider 错误、输入拒绝等。
   - 后端保存 `failure_code`、`failure_message`、`failure_detail_json`；原始错误脱敏/截断后只进结构化详情。
   - 用户侧展示中文友好失败摘要、错误类型、是否可重试和尝试次数。
   - 已增加 `POST /api/essays/{id}/retry`，第一版只允许 `FAILED -> SCORING`。
   - 同一作文最多重试 3 次，不允许 `COMPLETED` 直接重新评分；AI 调用日志按 `attempt_no` 记录。
   - 后续可继续细化：持久化失败分类、队列级重试、Provider 熔断/退避。

6. **结果页异步体验增强（阶段 1 部分落地）**
   - 已保留并强化 `AI Thinking` 状态。
   - 已增加手动刷新、失败后的重新评分按钮、评分尝试次数。
   - 已展示 AI Thinking 用时、模型、token 消耗、失败原因。
   - Provider 未返回 usage 时页面明确显示“约/估算”或“未返回用量”。
   - 后续补齐：轮询超时提示、SSE/WebSocket 推送、长时间等待时的更细状态说明。

7. **评分一致性基准集（阶段 1 已落地）**
   - 第一版已准备 24 篇离线样例：中考 6、高考 6、CET4 3、CET6 3、IELTS Task 2 3、GENERAL 3。
   - 中考/高考样例覆盖不同任务形态，如书信/邮件、通知/倡议、记叙经历、观点表达等。
   - 每篇记录题目、作文、期望 100 分换算区间、等级区间、关注标签。
   - 已用测试固化样例数量、类型分布、题目/正文/期望区间基础质量。
   - `.\gradlew.bat scoringBenchmarkReport` 生成 `build/reports/scoring-benchmark/report.md`。
   - 当前作为软门禁：不调用真实 Provider，不消耗用户 API Key，生成报告但暂不硬阻断 CI。
   - 后续再加入真实 Provider 回放、分数漂移检测、关键反馈点校验和人工复核入口。

8. **逐句批注与参考范文（阶段 1 部分落地）**
   - 已让模型结构返回 `annotations[].quote`，前端用 `quote` / `original` / `context` 做文本匹配高亮，不强依赖模型 offset。
   - 已实现匹配策略：exact match -> case-insensitive match -> whitespace-normalized match -> 未定位建议列表。
   - 批注卡片展示错误类型、严重程度、问题说明、修改建议、原因解释和高亮定位文本。
   - 已新增 `referenceEssay`，结果页可展示 1 个“同水平提升版”参考范文，强调学习参考，不做一键替换。
   - 后续补齐：复制修改建议、标记已读/暂不处理、quote 漂移检测。

9. **多版本作文修改闭环（阶段 1 部分落地）**
   - 已建立 `essay_group_id`、`version_no`、`parent_essay_id` 模型。
   - 用户可从结果页基于当前作文再次提交修改版，形成 v1/v2/v3。
   - 修改版只用 `idempotencyKey` 防重复点击，不用 `contentHash` 误复用旧版本。
   - 历史页和结果页已展示版本号。
   - 后续补齐：对比分数、维度、错误数量、高频错误变化。
   - 第一版不做复杂在线编辑器、逐条接受建议、草稿自动保存。

10. **个人学习进步 dashboard（阶段 1 部分落地）**
    - 第一版只面向个人学生，不做教师/班级。
    - 已展示总提交、已完成、失败、评分中、平均分、最高分、7/30/90 天提交次数、作文类型分布。
    - 已新增 `/api/dashboard/summary` 和前端 `/dashboard` 导航入口。
    - 后续补齐：平均分趋势、高频错误、最弱 Rubric 维度、最近提升点。

### 8.2 用户系统已决边界

- 认证方式：账号密码 + Spring Security + Redis Session + `HttpOnly Cookie`；第一版不用 JWT 放 `localStorage`。
- 注册策略：开放注册，但通过 `essay-evaluator.auth.registration-enabled` 控制；`ADMIN` 只能由初始化机制或数据库创建，不能前端注册。
- 初始化管理员：通过环境变量可选创建 admin；仓库不写默认密码。
- 邮箱能力：第一版不做邮箱验证和找回密码，但预留 `email_verified`、`email_verified_at`、`password_changed_at`。
- 前端登录态：轻量 `authStore` / composable + Axios `withCredentials` + 路由守卫，不为第一版引入复杂状态管理。
- 访问规则：评分、历史、配置、调用日志、dashboard 要求登录；首页、登录、注册、安全策略等基础接口可公开访问。
- 旧数据：现有历史数据不重要，用户系统 migration 可以清空旧作文、评分、配置数据并重建用户归属字段。

### 8.3 API 配置与透明用量已决边界

- 用户自己填写 API、Provider、endpoint type、model，因此结果页应清楚展示真实 Provider、模型和 token 消耗。
- 不把模型包装成不透明“评分引擎”。
- 用户可查看自己私有配置的完整 API Key，但必须显式点击 reveal；不要求重新输入密码。
- token 统计优先使用 Provider 返回的精确 usage；失败、流式、中转或兼容接口缺失 usage 时才估算。
- 费用展示可选，取决于用户是否在配置中填写单价；不硬编码官方价格。
- 明细日志对开发排查有用，用户侧只展示安全化明细，不展示原始 prompt、原始 response、原始错误全文和 API Key。

## 9. 参考来源

- Grammarly 官方产品功能说明：https://www.grammarly.com/
- ETS e-rater / Criterion 相关说明：https://www.ets.org/
- Cambridge Write & Improve：https://writeandimprove.com/
- QuillBot 官方功能说明：https://quillbot.com/
- ProWritingAid 官方功能说明：https://prowritingaid.com/
- Turnitin Draft Coach：https://www.turnitin.com/products/draft-coach
