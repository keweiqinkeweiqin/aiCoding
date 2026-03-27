# AI 研判模块 — 技术设计文档

## 概述

AI 研判模块为「华尔街之眼」情报详情页提供个性化投研分析能力。核心流程：用户在情报详情页触发研判 → 后端加载情报数据 + 用户画像 → 组装 Prompt 调用 Qwen3.5-flash → 解析结构化 JSON → 持久化并返回前端渲染。

模块包含以下核心能力：
- 同步/SSE 流式 AI 研判生成
- 研判结果缓存（1 小时有效期）
- 研判历史记录查询
- 情报详情页前端（detail.html）：画像卡片、影响分析、操作建议、相关情报推荐

技术栈沿用项目现有方案：Spring Boot 3.4 + JDK 21 + Spring Data JPA + H2 + RestTemplate 调用 OpenAI 兼容协议 LLM。

## 架构

```mermaid
graph TD
    subgraph 前端
        DETAIL[detail.html 情报详情页]
    end

    subgraph Controller
        AC[AnalysisController<br>/api/analysis/*]
        IC[IntelligenceController<br>/api/intelligences/*]
    end

    subgraph Service
        AS[AnalysisService]
        IS[IntelligenceService<br>已有]
        PS[ProfileService<br>已有]
        VS[VectorSearchService<br>已有]
    end

    subgraph External
        LLM[Qwen3.5-flash<br>OpenAI 兼容协议]
    end

    subgraph Storage
        H2[(H2 Database)]
    end

    DETAIL -->|POST /api/analysis/generate| AC
    DETAIL -->|GET /api/analysis/stream| AC
    DETAIL -->|GET /api/analysis/history| AC
    DETAIL -->|GET /api/intelligences/{id}| IC
    DETAIL -->|GET /api/intel/{id}/related| IC

    AC --> AS
    AS --> IS
    AS --> PS
    AS --> LLM
    AS --> H2
    IC --> IS
    IC --> VS
    VS --> H2
```

请求流程：

1. 用户打开 `detail.html?id={intelligenceId}`，页面加载情报详情 + 用户画像 + 相关情报
2. 用户点击「生成完整报告」→ `POST /api/analysis/generate` → AnalysisService 检查缓存 → 命中则直接返回，未命中则调用 LLM
3. 或使用 SSE 流式：`GET /api/analysis/stream` → SseEmitter 逐段推送 analysis/impacts/suggestion/risks
4. 研判结果持久化到 `analysis_records` 表

## 组件与接口

### AnalysisController（新建）

路径前缀：`/api/analysis`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/generate` | 同步生成 AI 研判，入参 `{userId, articleId}` |
| GET | `/stream?userId=&articleId=` | SSE 流式生成研判 |
| GET | `/history?userId=` | 查询研判历史记录 |

认证方式：所有接口通过 `Authorization: Bearer {token}` header 鉴权，使用 `AuthService.resolveUser()` 解析用户。

### AnalysisService（新建）

核心服务类，职责：

```java
@Service
public class AnalysisService {

    // 同步生成研判
    public Map<String, Object> generateAnalysis(Long userId, Long articleId);

    // SSE 流式生成研判
    public void streamAnalysis(Long userId, Long articleId, SseEmitter emitter);

    // 查询研判历史
    public List<AnalysisRecord> getHistory(Long userId);

    // 构建 system prompt
    private String buildSystemPrompt();

    // 构建 user prompt（含情报 + 画像上下文）
    private String buildUserPrompt(Intelligence intel, UserProfile profile, List<UserHolding> holdings);

    // 调用 LLM（复用 SmartQueryService 的 callLlm 模式）
    private String callLlm(String systemPrompt, String userPrompt);

    // 解析 LLM 返回的 JSON
    private Map<String, Object> parseLlmResponse(String raw);

    // 检查缓存（1 小时内同一用户+同一情报）
    private Optional<AnalysisRecord> findCachedRecord(Long userId, Long articleId);
}
```

依赖注入：
- `IntelligenceRepository`：加载情报实体
- `UserRepository`：验证用户存在
- `UserProfileRepository`：加载用户画像
- `UserHoldingRepository`：加载用户持仓
- `AnalysisRecordRepository`：持久化/查询研判记录
- `RestTemplate`：调用 LLM API
- `@Value("${spring.ai.openai.base-url}")` / `@Value("${spring.ai.openai.api-key}")`：LLM 配置

### IntelligenceController 扩展（已有）

新增端点：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/intel/{id}/related?limit=5` | 基于 Embedding 余弦相似度返回相关情报 |

实现：从 `Intelligence.embeddingJson` 提取向量，与其他情报向量计算余弦相似度，返回 Top N。

### detail.html（新建前端页面）

原生 HTML/CSS/JS 页面，放置于 `src/main/resources/static/detail.html`。

页面结构：
1. 情报头部：优先级标签 + 置信度 + 来源数 + 标题 + 时间
2. 正文区域：情报 content 渲染
3. 用户画像卡片：关注领域标签 + 持仓列表 + 投资者类型 + 「修改画像」链接
4. AI 研判区域：「生成完整报告」按钮 + 加载指示器 + 报告展示区
5. 影响分析卡片列表：每只股票一张卡片，颜色区分影响等级
6. 操作建议区域：suggestion + risks + userContext
7. 相关情报推荐：卡片列表，点击跳转

前端 JS 逻辑：
- 从 URL 参数获取 `id`，从 `localStorage` 获取 `token` 和 `userId`
- 页面加载时并行请求：情报详情、用户画像、相关情报
- 点击生成报告 → `fetch POST /api/analysis/generate`
- SSE 流式可选：`new EventSource('/api/analysis/stream?userId=&articleId=')`

## 数据模型

### AnalysisRecord（新建实体）

```java
@Entity
@Table(name = "analysis_records")
public class AnalysisRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long newsArticleId;       // 对应 Intelligence.id

    @Column(columnDefinition = "CLOB")
    private String analysisText;      // 完整 JSON 字符串

    @Column(length = 30)
    private String investmentStyle;   // 生成时的投资风格快照

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
```

DDL：
```sql
CREATE TABLE analysis_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    news_article_id BIGINT NOT NULL,
    analysis_text CLOB,
    investment_style VARCHAR(30),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_analysis_user_article ON analysis_records(user_id, news_article_id);
```

### AnalysisRecordRepository（新建）

```java
public interface AnalysisRecordRepository extends JpaRepository<AnalysisRecord, Long> {

    // 查找缓存：同一用户+同一情报，按创建时间降序
    Optional<AnalysisRecord> findFirstByUserIdAndNewsArticleIdOrderByCreatedAtDesc(
        Long userId, Long newsArticleId);

    // 查询用户历史
    List<AnalysisRecord> findByUserIdOrderByCreatedAtDesc(Long userId);
}
```

### LLM 请求/响应结构

请求体（复用 SmartQueryService.callLlm 模式）：
```json
{
  "model": "Qwen3.5-flash",
  "messages": [
    {"role": "system", "content": "<system_prompt>"},
    {"role": "user", "content": "<user_prompt>"}
  ],
  "temperature": 0.3
}
```

LLM 期望返回的 JSON 结构：
```json
{
  "analysis": "研判文本，200字以内",
  "impacts": [
    {
      "stock": "NVDA",
      "impact": "影响分析文本",
      "level": "重大影响",
      "volatility": "预计波动 +3%~5%",
      "revenueImpact": "营收影响分析",
      "longTermImpact": "长期影响分析"
    }
  ],
  "suggestion": "操作建议文本",
  "risks": ["风险提示1", "风险提示2"],
  "userContext": "基于您的画像生成（保守型 / 关注AI芯片 / 持有NVDA）"
}
```

### Prompt 模板

System Prompt：
```
你是「华尔街之眼」AI 投研助手，专注 AI 与科技投资领域的个性化研判分析。

你必须严格按以下 JSON 格式返回结果，不要包含任何其他文本：
{
  "analysis": "情报研判文本，200字以内，概述核心影响",
  "impacts": [
    {
      "stock": "股票代码",
      "impact": "对该股票的影响分析",
      "level": "重大影响|中等影响|正面影响",
      "volatility": "预计波动幅度描述",
      "revenueImpact": "对营收的影响分析",
      "longTermImpact": "长期影响分析"
    }
  ],
  "suggestion": "基于用户画像的操作建议",
  "risks": ["风险提示1", "风险提示2"],
  "userContext": "说明该建议基于何种画像生成"
}

分析维度要求：
- 影响等级定义：重大影响（直接影响核心业务）、中等影响（间接关联）、正面影响（利好因素）
- 波动预估：给出具体百分比区间
- 风险提示：至少给出2条具体风险
- 操作建议：结合用户投资者类型和风险偏好给出
```

User Prompt 模板：
```
## 情报信息
- 标题：{title}
- 摘要：{summary}
- 关联股票：{relatedStocks}
- 情感倾向：{sentiment}
- 标签：{tags}

## 用户投资画像
- 投资者类型：{investorType}
- 投资周期：{investmentCycle}
- 关注领域：{focusAreas}

## 用户持仓
{holdings 列表，每行格式：- {stockCode} {stockName}，仓位 {positionRatio}%，成本 {costPrice}}

请基于以上信息生成个性化研判分析。
```

### SSE 事件格式

每条 SSE 事件：
```
data: {"section":"analysis","content":"..."}
data: {"section":"impacts","content":"..."}
data: {"section":"suggestion","content":"..."}
data: {"section":"risks","content":"..."}
data: [DONE]
```

SSE 实现使用 Spring 的 `SseEmitter`，超时设置 60 秒。AnalysisService 在异步线程中调用 LLM，按 section 顺序通过 `emitter.send()` 推送内容，完成后发送 `[DONE]` 并调用 `emitter.complete()`。

### 缓存策略

缓存判定逻辑：
```java
Optional<AnalysisRecord> cached = repository
    .findFirstByUserIdAndNewsArticleIdOrderByCreatedAtDesc(userId, articleId);

if (cached.isPresent() &&
    cached.get().getCreatedAt().isAfter(LocalDateTime.now().minusHours(1))) {
    // 缓存命中，直接返回 cached.get().getAnalysisText()
} else {
    // 缓存未命中或过期，调用 LLM 生成新结果
}
```

### 相关情报推荐

实现方式：复用 `Intelligence.embeddingJson` 字段中的向量数据，在 IntelligenceController 中新增 `/api/intel/{id}/related` 端点。

```java
// 伪代码
Intelligence current = intelligenceRepository.findById(id);
float[] currentVec = parseEmbedding(current.getEmbeddingJson());
List<Intelligence> allRecent = intelligenceRepository.findRecent();
// 计算余弦相似度，排除自身，取 Top N
```

## 正确性属性

*属性（Property）是指在系统所有合法执行路径中都应成立的特征或行为——本质上是对系统应做什么的形式化陈述。属性是人类可读规格说明与机器可验证正确性保证之间的桥梁。*

### 属性 1：无效输入返回 400 错误

*对于任意*不存在的 userId 或不存在的 articleId，调用 `generateAnalysis` 应返回 HTTP 400 响应，且错误消息分别为"用户不存在"或"情报不存在"。

**验证需求：1.2, 1.3**

### 属性 2：User Prompt 包含完整上下文字段

*对于任意* Intelligence 实体（含 title、summary、relatedStocks、sentiment、tags）和任意 UserProfile（含 investorType、investmentCycle、focusAreas）及任意 UserHolding 列表（含 stockCode、stockName、positionRatio、costPrice），调用 `buildUserPrompt` 生成的 Prompt 字符串应包含所有上述字段的值。

**验证需求：1.1, 1.4, 9.2**

### 属性 3：研判结果 JSON 结构完整性

*对于任意*合法的 LLM 返回 JSON 字符串（包含 analysis、impacts、suggestion、risks、userContext），调用 `parseLlmResponse` 解析后的结果应包含全部五个顶层字段，且 impacts 数组中每个元素应包含 stock、impact、level、volatility、revenueImpact、longTermImpact 六个子字段。

**验证需求：1.5, 1.6**

### 属性 4：研判结果持久化往返

*对于任意*成功生成的研判结果，持久化为 AnalysisRecord 后，通过 `findFirstByUserIdAndNewsArticleIdOrderByCreatedAtDesc` 查询应能获取到相同的 analysisText 内容。

**验证需求：1.8, 2.5**

### 属性 5：SSE 事件 section 顺序

*对于任意* SSE 流式研判的完整事件序列，section 字段的出现顺序应为 analysis → impacts → suggestion → risks，且最后一条事件的 data 应为 `[DONE]`。

**验证需求：2.2, 2.3**

### 属性 6：研判历史完整性与排序

*对于任意*用户的研判历史查询结果，每条 AnalysisRecord 应包含 id、userId、newsArticleId、analysisText、investmentStyle、createdAt 全部字段，且列表按 createdAt 降序排列（即对于列表中任意相邻两条记录，前一条的 createdAt 应大于等于后一条）。

**验证需求：3.1, 3.2**

### 属性 7：缓存有效期阈值

*对于任意*用户和情报组合，若 AnalysisRecord 中存在该组合的记录且 createdAt 距当前时间不超过 1 小时，则 `generateAnalysis` 应返回缓存结果（不调用 LLM）；若记录超过 1 小时或不存在，则应调用 LLM 生成新结果。

**验证需求：8.1, 8.2**

### 属性 8：System Prompt 完整性

*对于任意*调用 `buildSystemPrompt()`，返回的字符串应包含角色定义（"华尔街之眼"和"AI 投研助手"）、JSON 格式要求（"JSON"）、影响等级定义（"重大影响"、"中等影响"、"正面影响"）。

**验证需求：9.1, 9.3**

## 错误处理

### LLM 调用失败降级

当 LLM 调用抛出异常时，AnalysisService 捕获异常并返回降级响应：

```java
try {
    String raw = callLlm(systemPrompt, userPrompt);
    return parseLlmResponse(raw);
} catch (Exception e) {
    log.error("LLM 调用失败: {}", e.getMessage());
    return Map.of(
        "analysis", "AI 分析服务暂时不可用",
        "impacts", List.of(),
        "suggestion", "暂无操作建议",
        "risks", List.of(),
        "userContext", ""
    );
}
```

### SSE 超时处理

SseEmitter 设置 60 秒超时：

```java
SseEmitter emitter = new SseEmitter(60_000L);
emitter.onTimeout(() -> {
    emitter.send(SseEmitter.event().data("{\"error\":\"请求超时\"}"));
    emitter.complete();
});
```

### 输入校验错误

| 场景 | HTTP 状态码 | 错误消息 |
|------|------------|---------|
| Intelligence 不存在 | 400 | "情报不存在" |
| User 不存在 | 400 | "用户不存在" |
| Token 无效/缺失 | 401 | "未登录" |
| LLM 返回非法 JSON | 500 | 降级为默认响应 |

### LLM 返回 JSON 解析失败

当 LLM 返回的内容无法解析为合法 JSON 时，`parseLlmResponse` 应尝试提取 JSON 子串（正则匹配 `\{.*\}`），若仍失败则返回降级响应。

## 测试策略

### 双轨测试方法

本模块采用单元测试 + 属性测试互补的策略：

- **单元测试**：验证具体示例、边界条件、错误处理
- **属性测试**：验证跨所有输入的通用属性

### 属性测试配置

- 属性测试库：**jqwik**（JUnit 5 原生集成的 Java 属性测试框架）
- 每个属性测试最少运行 **100 次迭代**
- 每个属性测试必须通过注释引用设计文档中的属性编号
- 标注格式：`// Feature: ai-analysis-module, Property {N}: {属性标题}`
- 每个正确性属性由**单个**属性测试实现

### 属性测试计划

| 属性 | 测试类 | 生成器 |
|------|--------|--------|
| 属性 1：无效输入返回 400 | AnalysisServicePropertyTest | 随机生成不存在的 userId/articleId |
| 属性 2：Prompt 包含完整上下文 | PromptBuilderPropertyTest | 随机生成 Intelligence + UserProfile + UserHolding |
| 属性 3：JSON 结构完整性 | JsonParserPropertyTest | 随机生成合法的研判 JSON 字符串 |
| 属性 4：持久化往返 | AnalysisRecordPropertyTest | 随机生成 AnalysisRecord 并持久化/查询 |
| 属性 5：SSE section 顺序 | SseStreamPropertyTest | 随机生成研判内容，验证 SSE 事件顺序 |
| 属性 6：历史完整性与排序 | HistoryPropertyTest | 随机生成多条 AnalysisRecord，验证查询结果 |
| 属性 7：缓存有效期阈值 | CachePropertyTest | 随机生成不同时间戳的缓存记录 |
| 属性 8：System Prompt 完整性 | PromptBuilderPropertyTest | 调用 buildSystemPrompt() 验证关键词 |

### 单元测试计划

| 测试场景 | 测试类 | 说明 |
|---------|--------|------|
| LLM 调用失败降级 | AnalysisServiceTest | Mock LLM 抛异常，验证降级响应结构 |
| SSE 超时处理 | AnalysisControllerTest | 模拟 60 秒超时，验证错误事件 |
| 空历史记录返回空数组 | AnalysisServiceTest | 新用户查询历史，验证返回 `[]` |
| SSE Content-Type | AnalysisControllerTest | 验证响应头 `text/event-stream` |
| LLM 温度参数 0.3 | AnalysisServiceTest | 验证 LLM 请求体中 temperature=0.3 |
| JSON 解析容错 | AnalysisServiceTest | 输入非法 JSON，验证降级处理 |

### Maven 依赖

```xml
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <version>1.9.1</version>
    <scope>test</scope>
</dependency>
```
