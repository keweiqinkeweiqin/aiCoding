# 实现计划：AI 研判模块（ai-analysis-module）

## 概述

基于需求文档和设计文档，将 AI 研判模块拆分为后端数据层、核心服务层、API 控制器层、前端详情页四个阶段，逐步实现并集成。后端使用 Java（Spring Boot 3.4 + JDK 21），前端使用原生 HTML/CSS/JS。每个任务增量构建，确保无孤立代码。

## 任务

- [x] 1. 创建 AnalysisRecord 实体与 Repository
  - [x] 1.1 创建 AnalysisRecord 实体类
    - 在 `src/main/java/com/example/demo/model/` 下新建 `AnalysisRecord.java`
    - 包含字段：id、userId、newsArticleId、analysisText（CLOB）、investmentStyle、createdAt
    - 使用 `@PrePersist` 自动设置 createdAt
    - _需求：3.2, 1.8_
  - [x] 1.2 创建 AnalysisRecordRepository 接口
    - 在 `src/main/java/com/example/demo/repository/` 下新建 `AnalysisRecordRepository.java`
    - 实现 `findFirstByUserIdAndNewsArticleIdOrderByCreatedAtDesc` 方法（缓存查询）
    - 实现 `findByUserIdOrderByCreatedAtDesc` 方法（历史查询）
    - _需求：3.1, 8.1_

- [x] 2. 实现 AnalysisService 核心服务
  - [x] 2.1 创建 AnalysisService 基础结构与 Prompt 构建
    - 在 `src/main/java/com/example/demo/service/` 下新建 `AnalysisService.java`
    - 注入 IntelligenceRepository、UserRepository、UserProfileRepository、UserHoldingRepository、AnalysisRecordRepository、RestTemplate
    - 注入 LLM 配置：`@Value("${spring.ai.openai.base-url}")` 和 `@Value("${spring.ai.openai.api-key}")`
    - 实现 `buildSystemPrompt()` 方法：包含角色定义（华尔街之眼 AI 投研助手）、严格 JSON 格式要求、影响等级定义（重大影响/中等影响/正面影响）、波动预估要求、风险提示要求
    - 实现 `buildUserPrompt(Intelligence, UserProfile, List<UserHolding>)` 方法：包含情报标题、摘要、关联股票、情感倾向、标签，以及用户投资者类型、投资周期、关注领域、持仓列表（含 stockCode、stockName、positionRatio、costPrice）
    - _需求：9.1, 9.2, 9.3_
  - [ ]* 2.2 编写属性测试：System Prompt 完整性
    - **属性 8：System Prompt 完整性**
    - 验证 `buildSystemPrompt()` 返回字符串包含"华尔街之眼"、"AI 投研助手"、"JSON"、"重大影响"、"中等影响"、"正面影响"
    - **验证需求：9.1, 9.3**
  - [ ]* 2.3 编写属性测试：User Prompt 包含完整上下文字段
    - **属性 2：User Prompt 包含完整上下文字段**
    - 随机生成 Intelligence（含 title、summary、relatedStocks、sentiment、tags）+ UserProfile（含 investorType、investmentCycle、focusAreas）+ UserHolding 列表（含 stockCode、stockName、positionRatio、costPrice），验证 `buildUserPrompt` 输出包含所有字段值
    - **验证需求：1.1, 1.4, 9.2**
  - [x] 2.4 实现 callLlm 与 parseLlmResponse 方法
    - 实现 `callLlm(String systemPrompt, String userPrompt)` 方法，复用 SmartQueryService 的 RestTemplate + OpenAI 兼容协议模式，设置 temperature=0.3
    - 实现 `parseLlmResponse(String raw)` 方法，解析 LLM 返回的 JSON 为 Map，包含 analysis、impacts、suggestion、risks、userContext 五个字段；若解析失败则尝试正则提取 JSON 子串，仍失败则返回降级响应
    - _需求：1.5, 1.7, 9.4_
  - [ ]* 2.5 编写属性测试：研判结果 JSON 结构完整性
    - **属性 3：研判结果 JSON 结构完整性**
    - 随机生成合法的研判 JSON 字符串（含 analysis、impacts 数组、suggestion、risks 数组、userContext），验证 `parseLlmResponse` 解析后包含全部五个顶层字段，且 impacts 每项含 stock/impact/level/volatility/revenueImpact/longTermImpact
    - **验证需求：1.5, 1.6**
  - [x] 2.6 实现 generateAnalysis 同步研判方法
    - 实现 `generateAnalysis(Long userId, Long articleId)` 方法
    - 校验 Intelligence 和 User 是否存在，不存在则抛出异常（附带"情报不存在"或"用户不存在"消息）
    - 实现缓存逻辑：查询 AnalysisRecord，若存在且 createdAt 距当前不超过 1 小时则直接返回缓存
    - 加载 UserProfile 和 UserHolding，构建 Prompt，调用 LLM，解析结果
    - LLM 调用失败时返回降级响应（analysis="AI 分析服务暂时不可用"，impacts/risks 为空列表）
    - 成功后持久化 AnalysisRecord（记录 userId、newsArticleId、analysisText、investmentStyle、createdAt）
    - _需求：1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 8.1, 8.2_
  - [ ]* 2.7 编写属性测试：缓存有效期阈值
    - **属性 7：缓存有效期阈值**
    - 随机生成不同时间戳的 AnalysisRecord，验证 1 小时内缓存命中不调用 LLM，超过 1 小时则重新调用
    - **验证需求：8.1, 8.2**
  - [x] 2.8 实现 streamAnalysis SSE 流式研判方法
    - 实现 `streamAnalysis(Long userId, Long articleId, SseEmitter emitter)` 方法
    - 在异步线程中调用 LLM，按 section 顺序（analysis → impacts → suggestion → risks）通过 `emitter.send()` 推送 JSON 事件
    - 完成后发送 `[DONE]` 事件并调用 `emitter.complete()`
    - 完成后将完整结果拼接并持久化为 AnalysisRecord
    - _需求：2.1, 2.2, 2.3, 2.5_
  - [x] 2.9 实现 getHistory 研判历史查询方法
    - 实现 `getHistory(Long userId)` 方法，返回该用户所有 AnalysisRecord 列表，按 createdAt 降序
    - 无记录时返回空列表
    - _需求：3.1, 3.2, 3.3_

- [x] 3. 检查点 — 确保后端服务层编译通过
  - 确保所有代码编译通过，如有问题请向用户确认。

- [x] 4. 实现 AnalysisController API 控制器
  - [x] 4.1 创建 AnalysisController
    - 在 `src/main/java/com/example/demo/controller/` 下新建 `AnalysisController.java`
    - 路径前缀 `/api/analysis`，注入 AnalysisService 和 AuthService
    - 所有接口通过 `@RequestHeader("Authorization")` + `AuthService.resolveUser()` 鉴权，未登录返回 401
    - _需求：1.2, 1.3_
  - [x] 4.2 实现 POST /api/analysis/generate 端点
    - 接收 `{userId, articleId}` JSON body，调用 `AnalysisService.generateAnalysis()`
    - Intelligence 不存在返回 HTTP 400 + "情报不存在"
    - User 不存在返回 HTTP 400 + "用户不存在"
    - 成功返回研判结果 JSON
    - _需求：1.2, 1.3, 6.1_
  - [x] 4.3 实现 GET /api/analysis/stream SSE 端点
    - 接收 `userId` 和 `articleId` 查询参数
    - 返回 `SseEmitter`（超时 60 秒），Content-Type 为 text/event-stream
    - 调用 `AnalysisService.streamAnalysis()` 在异步线程推送内容
    - 超时时发送错误事件并关闭连接
    - _需求：2.1, 2.3, 2.4_
  - [x] 4.4 实现 GET /api/analysis/history 端点
    - 接收 `userId` 查询参数，调用 `AnalysisService.getHistory()`
    - 返回 AnalysisRecord 列表 JSON
    - _需求：3.1, 3.3_
  - [ ]* 4.5 编写属性测试：无效输入返回 400 错误
    - **属性 1：无效输入返回 400 错误**
    - 随机生成不存在的 userId 或 articleId，验证 `generateAnalysis` 返回 HTTP 400 及对应错误消息
    - **验证需求：1.2, 1.3**

- [x] 5. 扩展 IntelligenceController 相关情报推荐接口
  - [x] 5.1 在 IntelligenceController 中新增 GET /api/intel/{id}/related 端点
    - 接收 `limit` 查询参数（默认 5）
    - 从 Intelligence.embeddingJson 提取向量，与其他情报计算余弦相似度，排除自身，返回 Top N
    - 每条结果包含 id、title、summary、primarySource、credibilityLevel
    - 无相关情报时返回空数组
    - _需求：7.1, 7.2, 7.4_

- [x] 6. 检查点 — 确保后端 API 层编译通过
  - 确保所有代码编译通过，如有问题请向用户确认。

- [x] 7. 创建情报详情前端页面 detail.html
  - [x] 7.1 创建 detail.html 页面基础结构与样式
    - 在 `src/main/resources/static/` 下新建 `detail.html`
    - 沿用项目现有 UI 风格（深色主题 #0a0e27 背景、#ffd700 金色强调、#151a40 卡片背景）
    - 页面结构：情报头部（优先级标签 + 置信度 + 来源数 + 标题 + 时间）、正文区域、用户画像卡片、AI 研判区域、影响分析卡片列表、操作建议区域、相关情报推荐
    - _需求：4.1, 4.2, 5.1, 5.2, 5.3_
  - [x] 7.2 实现情报详情加载与渲染逻辑
    - 从 URL 参数获取 `id`，从 localStorage 获取 `token` 和 `userId`
    - 页面加载时并行请求：GET /api/intelligences/{id}（情报详情）、GET /api/profile（用户画像）、GET /api/intel/{id}/related?limit=5（相关情报）
    - 渲染情报头部信息和正文内容
    - _需求：4.1, 7.1_
  - [x] 7.3 实现用户画像卡片渲染
    - 展示关注领域标签列表、持仓标的列表、投资者类型（风险偏好）
    - 提供"修改画像"入口，点击跳转至 profile.html
    - _需求：4.1, 4.2_
  - [x] 7.4 实现"生成完整报告"按钮与 AI 研判结果渲染
    - 点击按钮调用 POST /api/analysis/generate
    - 生成中显示加载指示器，禁用按钮防止重复提交
    - 成功后渲染完整报告内容
    - 失败时显示错误提示并恢复按钮可用状态
    - _需求：6.1, 6.2, 6.3, 6.4_
  - [x] 7.5 实现影响分析卡片列表渲染
    - 为 impacts 列表中每只股票渲染一个影响分析卡片
    - 影响等级标签颜色区分：重大影响=红色、中等影响=橙色、正面影响=绿色
    - 每张卡片展示预计波动幅度、营收影响、长期影响分析文本
    - _需求：4.3, 4.4, 4.5_
  - [x] 7.6 实现操作建议与风险提示区域渲染
    - 展示 suggestion 字段内容
    - 展示 risks 列表中每条风险提示
    - 展示 userContext 字段内容，说明建议基于何种画像生成
    - _需求：5.1, 5.2, 5.3_
  - [x] 7.7 实现相关情报推荐区域渲染
    - 展示相关情报卡片列表，每张卡片包含标题、摘要、来源名称、置信度标签
    - 点击卡片跳转至该情报的详情页（detail.html?id=xxx）
    - 无相关情报时隐藏推荐区域
    - _需求：7.1, 7.2, 7.3, 7.4_

- [x] 8. 集成与端到端连通
  - [x] 8.1 在现有情报列表页面添加详情页入口
    - 确保情报列表中的情报条目可点击跳转至 detail.html?id={intelligenceId}
    - _需求：7.3_
  - [ ]* 8.2 编写属性测试：研判结果持久化往返
    - **属性 4：研判结果持久化往返**
    - 随机生成研判结果并持久化为 AnalysisRecord，验证通过 Repository 查询能获取到相同的 analysisText
    - **验证需求：1.8, 2.5**
  - [ ]* 8.3 编写属性测试：研判历史完整性与排序
    - **属性 6：研判历史完整性与排序**
    - 随机生成多条 AnalysisRecord，验证查询结果包含全部字段且按 createdAt 降序排列
    - **验证需求：3.1, 3.2**

- [x] 9. 最终检查点 — 确保所有代码编译通过
  - 确保所有代码编译通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的子任务为可选测试任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保需求全覆盖
- 属性测试使用 jqwik 框架，需在 pom.xml 中添加依赖
- 已有实体（Intelligence、UserProfile、UserHolding、User）和服务（IntelligenceService、ProfileService、AuthService、VectorSearchService）直接引用，不重新创建
- LLM 调用复用 SmartQueryService 中的 callLlm 模式（RestTemplate + OpenAI 兼容协议）
