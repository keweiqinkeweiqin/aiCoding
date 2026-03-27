# 需求文档：AI 研判模块（ai-analysis-module）

## 简介

AI 研判模块是「华尔街之眼」投研情报引擎的核心分析功能。用户在情报详情页中，系统结合情报内容与用户投资画像（关注领域、持仓标的、风险偏好），通过 LLM 生成个性化的持仓影响分析、操作建议和完整投研报告。同时基于语义相似度推荐相关情报，形成完整的研判闭环。

## 术语表

- **Analysis_Service**：AI 研判后端服务，负责组装 Prompt、调用 LLM、解析返回结果并持久化
- **Analysis_Controller**：AI 研判 API 控制器，暴露 `/api/analysis/*` 端点
- **Detail_Page**：情报详情前端页面，展示情报正文及 AI 研判模块 UI
- **LLM_Client**：大语言模型调用客户端，通过 OpenAI 兼容协议调用 Qwen3.5-flash
- **Intelligence**：情报实体，由多条新闻聚类生成的分析单元，含 title/summary/content/relatedStocks/tags/sentiment 等字段
- **UserProfile**：用户投资画像，含 investorType（投资者类型）、investmentCycle（投资周期）、focusAreas（关注领域）
- **UserHolding**：用户持仓记录，含 stockCode（股票代码）、stockName（股票名称）、sector（板块）、positionRatio（仓位比例）、costPrice（成本价）
- **Impact_Item**：单只股票的影响分析条目，含影响等级、波动预估、营收影响、长期影响等
- **Analysis_Record**：研判历史记录实体，持久化每次生成的研判结果
- **SSE**：Server-Sent Events，服务端推送事件流，用于流式输出研判内容
- **Vector_Search**：基于 Embedding 余弦相似度的语义搜索服务

## 需求

### 需求 1：同步生成 AI 研判

**用户故事：** 作为投资者，我希望在情报详情页获取基于我的投资画像的个性化研判分析，以便快速了解该情报对我持仓的影响和操作方向。

#### 验收标准

1. WHEN 用户提交研判请求（userId + articleId），THE Analysis_Service SHALL 加载对应的 Intelligence 实体和该用户的 UserProfile 及 UserHolding 列表
2. IF Intelligence 实体不存在，THEN THE Analysis_Controller SHALL 返回 HTTP 400 及错误消息"情报不存在"
3. IF 用户不存在，THEN THE Analysis_Controller SHALL 返回 HTTP 400 及错误消息"用户不存在"
4. WHEN Intelligence 和 UserProfile 数据加载完成，THE Analysis_Service SHALL 构建包含情报摘要、关联股票、标签、情感倾向以及用户投资者类型、关注领域、持仓列表的 Prompt，调用 LLM_Client 生成研判结果
5. THE Analysis_Service SHALL 将 LLM 返回内容解析为结构化 JSON，包含 analysis（研判文本，200 字以内）、impacts（持仓影响列表）、suggestion（操作建议）、risks（风险提示列表）、userContext（画像上下文描述）五个字段
6. WHEN impacts 列表中包含用户持仓的股票，THE Analysis_Service SHALL 为每只匹配的股票标注影响等级（重大影响/中等影响/正面影响），并包含预计波动幅度、营收影响、长期影响的分析文本
7. IF LLM_Client 调用失败，THEN THE Analysis_Service SHALL 返回包含降级文案的响应，analysis 字段设为"AI 分析服务暂时不可用"，impacts 和 risks 设为空列表
8. WHEN 研判结果生成成功，THE Analysis_Service SHALL 将结果持久化为 Analysis_Record，记录 userId、newsArticleId、analysisText（完整 JSON）、investmentStyle 和 createdAt

### 需求 2：SSE 流式生成 AI 研判

**用户故事：** 作为投资者，我希望研判内容能以流式方式逐段展示，以便在等待完整结果时就能开始阅读分析内容。

#### 验收标准

1. WHEN 用户请求流式研判（GET /api/analysis/stream?userId=&articleId=），THE Analysis_Controller SHALL 返回 Content-Type 为 text/event-stream 的 SSE 响应
2. THE Analysis_Service SHALL 按 section 顺序推送研判内容，section 值依次为 analysis、impacts、suggestion、risks，每条 SSE 事件的 data 为 JSON 格式 {"section":"...","content":"..."}
3. WHEN 所有内容推送完毕，THE Analysis_Controller SHALL 发送 data: [DONE] 事件并关闭连接
4. IF LLM_Client 调用超时超过 60 秒，THEN THE Analysis_Controller SHALL 发送错误事件并关闭连接
5. WHEN SSE 流式研判完成，THE Analysis_Service SHALL 将完整结果拼接后持久化为 Analysis_Record

### 需求 3：研判历史记录

**用户故事：** 作为投资者，我希望查看过往的研判记录，以便回顾和对比不同时期的分析结论。

#### 验收标准

1. WHEN 用户请求研判历史（GET /api/analysis/history?userId=），THE Analysis_Controller SHALL 返回该用户的所有 Analysis_Record 列表，按 createdAt 降序排列
2. THE Analysis_Record SHALL 包含 id、userId、newsArticleId、analysisText、investmentStyle、createdAt 字段
3. IF 用户无历史记录，THEN THE Analysis_Controller SHALL 返回空数组

### 需求 4：个性化影响分析区域展示

**用户故事：** 作为投资者，我希望在情报详情页看到我的投资画像摘要和逐只股票的影响分析，以便直观了解情报与我的投资组合的关联。

#### 验收标准

1. WHEN 情报详情页加载完成，THE Detail_Page SHALL 展示用户投资画像卡片，包含关注领域标签列表、持仓标的列表、风险偏好（投资者类型）
2. THE Detail_Page SHALL 在画像卡片上提供"修改画像"入口，点击后跳转至 profile.html 页面
3. WHEN AI 研判结果返回，THE Detail_Page SHALL 为 impacts 列表中的每只股票渲染一个影响分析卡片
4. THE Detail_Page SHALL 为每个影响分析卡片标注影响等级标签，使用不同颜色区分：重大影响使用红色、中等影响使用橙色、正面影响使用绿色
5. THE Detail_Page SHALL 在每个影响分析卡片中展示预计波动幅度、营收影响、长期影响等分析文本

### 需求 5：操作建议区域展示

**用户故事：** 作为投资者，我希望获得基于我的投资风格和风险偏好的个性化操作建议，以便辅助投资决策。

#### 验收标准

1. WHEN AI 研判结果返回，THE Detail_Page SHALL 在操作建议区域展示 suggestion 字段内容
2. THE Detail_Page SHALL 在操作建议区域展示 risks 列表中的每条风险提示
3. THE Detail_Page SHALL 展示 userContext 字段内容，说明该建议基于何种画像生成

### 需求 6：生成完整投研报告

**用户故事：** 作为投资者，我希望一键生成完整的投研分析报告，以便保存或分享深度分析内容。

#### 验收标准

1. WHEN 用户点击"生成完整报告"按钮，THE Detail_Page SHALL 调用 POST /api/analysis/generate 接口请求完整研判
2. WHILE 报告生成中，THE Detail_Page SHALL 显示加载状态指示器，禁用生成按钮防止重复提交
3. WHEN 报告生成完成，THE Detail_Page SHALL 将完整报告内容渲染在报告展示区域
4. IF 报告生成失败，THEN THE Detail_Page SHALL 显示错误提示并恢复按钮可用状态

### 需求 7：相关情报推荐

**用户故事：** 作为投资者，我希望在情报详情页看到语义相关的其他情报推荐，以便发现更多相关投资信息。

#### 验收标准

1. WHEN 情报详情页加载完成，THE Detail_Page SHALL 调用 GET /api/intel/{id}/related?limit=5 接口获取相关情报列表
2. THE Detail_Page SHALL 展示相关情报卡片列表，每张卡片包含标题、摘要、来源名称、置信度标签
3. WHEN 用户点击相关情报卡片，THE Detail_Page SHALL 导航至该情报的详情页
4. IF 无相关情报，THEN THE Detail_Page SHALL 隐藏相关情报推荐区域

### 需求 8：研判结果缓存

**用户故事：** 作为系统运维人员，我希望同一用户对同一情报的研判结果在短时间内被缓存复用，以便减少 LLM 调用次数和响应延迟。

#### 验收标准

1. WHEN 用户请求研判且 Analysis_Record 中存在该用户对该情报的记录且记录创建时间距当前不超过 1 小时，THE Analysis_Service SHALL 直接返回缓存的研判结果而不调用 LLM_Client
2. WHEN 缓存的研判记录超过 1 小时，THE Analysis_Service SHALL 重新调用 LLM_Client 生成新的研判结果并更新 Analysis_Record

### 需求 9：研判 Prompt 工程

**用户故事：** 作为系统开发者，我希望研判 Prompt 结构清晰且包含完整上下文，以便 LLM 生成高质量的个性化分析。

#### 验收标准

1. THE Analysis_Service SHALL 构建的 system prompt 包含角色定义（华尔街之眼 AI 投研助手）、输出格式要求（严格 JSON）、分析维度说明（影响等级定义、波动预估要求、风险提示要求）
2. THE Analysis_Service SHALL 构建的 user prompt 包含情报标题、情报摘要、情报关联股票、情报情感倾向、用户投资者类型、用户投资周期、用户关注领域列表、用户持仓列表（含股票代码、名称、仓位比例、成本价）
3. THE Analysis_Service SHALL 在 Prompt 中要求 LLM 返回严格的 JSON 格式，包含 analysis、impacts（数组，每项含 stock/impact/level/volatility/revenueImpact/longTermImpact）、suggestion、risks（数组）、userContext 字段
4. THE Analysis_Service SHALL 设置 LLM 调用温度参数为 0.3，以保证分析结果的稳定性和一致性
