# 华尔街之眼 — AI 投研情报引擎 技术方案 V2

> 基于交互稿完善，覆盖全部页面模块的完整技术方案

---

## 1. 产品概述

聚焦 AI 与科技投资领域的智能情报引擎。从 10 个异构信息源自动采集新闻和行情数据，通过 LLM 结构化提取 + Embedding 向量化 + SimHash 去重 + 四维置信度评估，再通过事件聚类将多条新闻聚合为「情报」，结合用户画像生成个性化投研分析。

技术栈：Spring Boot 3.4 + JDK 21 + Spring AI + H2 + 内存向量缓存

### 1.0 核心概念：情报 vs 新闻

```
新闻 (NewsArticle)  →  采集的原始数据，每条来自单一信息源
        ↓ SimHash 标题聚类（汉明距离 ≤ 10）
情报 (Intelligence)  →  多条报道同一事件的新闻聚合而成的分析单元
```

| 维度 | 新闻 (NewsArticle) | 情报 (Intelligence) |
|------|-------------------|-------------------|
| 粒度 | 单条原始新闻 | 同一事件的多条新闻聚合 |
| 来源 | 单一信息源 | 多个信息源交叉验证 |
| 内容 | 原始采集内容 | LLM 综合多条新闻生成的分析 |
| 置信度 | 单条新闻的四维评分 | 聚合后的综合置信度（取最高 or 加权） |
| 前端展示 | 详情页「信息来源」列表中的每一项 | 首页情报卡片、详情页主体 |

生命周期：
1. 采集 → 新闻入库 `news_articles`
2. 聚类 → SimHash 标题匹配，归入已有情报或创建新情报
3. 生成 → LLM 基于聚合的新闻生成情报标题/摘要/正文
4. 展示 → 前端查询 `intelligences` 表

### 1.1 交互稿页面清单

| 页面 | 说明 | 对应后端模块 |
|------|------|-------------|
| 首页 | 问候栏+快速操作+市场概览+情报列表 | HomeController, IntelligenceController |
| 情报详情页 | 聚合正文+多来源+个性化影响+操作建议+相关情报 | IntelligenceController |
| 市场洞察页 | 市场概览+行业热度+事件时间轴+研报 | InsightController |
| 搜索页 | 搜索框+历史+热门+结果列表 | SearchController |
| 用户画像设置页 | 投资者类型+周期+领域+持仓 | ProfileController |
| 个人中心页 | 用户信息+统计+会员+菜单 | UserController |
| 登录注册页 | 邮箱登录+注册+第三方OAuth | AuthController |
| 注册页 | 邮箱+昵称+密码+协议 | AuthController |

### 1.2 页面导航关系（基于交互稿交互数据）

```mermaid
graph LR
    Home[首页] -->|搜索按钮| Search[搜索页]
    Home -->|画像标签| Profile[用户画像设置]
    Home -->|查看详情| Detail[情报详情页]
    Home -->|底部导航-洞察| Insight[市场洞察页]
    Home -->|底部导航-我的| Me[个人中心页]
    Detail -->|返回| Home
    Detail -->|修改画像| Profile
    Insight -->|底部导航-首页| Home
    Insight -->|底部导航-我的| Me
    Me -->|底部导航-首页| Home
    Me -->|底部导航-洞察| Insight
    Me -->|编辑画像| Profile
    Me -->|退出登录| Login[登录页]
    Login -->|注册| Register[注册页]
    Register -->|返回| Login
    Search -->|返回/取消| Home
    Search -->|查看详情| Detail
    Profile -->|返回| Me
    Profile -->|保存| Me
```

---

## 2. 系统架构（V2 完整版）

```mermaid
graph TD
    subgraph 前端-移动端
        HOME[首页]
        DETAIL[情报详情页]
        INSIGHT[市场洞察页]
        SEARCH[搜索页]
        PROFILE[用户画像设置]
        ME[个人中心]
        AUTH[登录/注册]
    end
    subgraph API-Gateway
        HC[HomeController]
        NDC[IntelligenceController]
        IC[InsightController]
        SC[SearchController]
        PC[ProfileController]
        UC[UserController]
        AC[AuthController]
        NC[NewsController-已有]
    end
    subgraph Service
        NCS[NewsCollectorService-已有]
        SQS[SmartQueryService-已有]
        CS[CredibilityService-已有]
        DS[DeduplicationService-已有]
        ECS[EventClusterService-新增]
        SS[SearchService-新增]
        PS[ProfileService-新增]
        US[UserService-新增]
        AS[AuthService-新增]
        IS[InsightService-新增]
        IAS[ImpactAnalysisService-新增]
    end
    subgraph Collector-已有
        RSS[RssCollector]
        MDC[MarketDataCollector]
    end
    subgraph Embedding-已有
        EC[EmbeddingClient]
        VSS[VectorSearchService]
    end
    subgraph External
        RSSHUB[RSSHub 10个源]
        MAIRUI[麦蕊智数API]
        LLM[GPT-5.4 / Qwen3.5-flash]
        EMBAPI[Qwen-text-embedding-v4]
    end
    subgraph Storage
        H2[(H2 Database)]
        MEM[内存向量缓存]
    end

    HOME --> HC
    DETAIL --> NDC
    INSIGHT --> IC
    SEARCH --> SC
    PROFILE --> PC
    ME --> UC
    AUTH --> AC

    HC --> NCS
    HC --> IS
    NDC --> NCS
    NDC --> IAS
    NDC --> VSS
    IC --> IS
    IC --> NCS
    SC --> SS
    SC --> VSS
    PC --> PS
    UC --> US
    AC --> AS

    NCS --> RSS --> RSSHUB
    NCS --> DS
    NCS --> CS
    NCS --> EC
    NCS --> VSS
    NCS -->|GPT-5.4| LLM
    SQS --> VSS
    SQS -->|Qwen3.5-flash| LLM
    SS --> VSS
    IAS -->|Qwen3.5-flash| LLM
    EC --> EMBAPI
    VSS --> MEM
    MDC --> MAIRUI
    NCS --> H2
    MDC --> H2
    VSS --> H2
    PS --> H2
    US --> H2
    AS --> H2
    SS --> H2
    IS --> H2
```

---

## 3. 数据模型（V2 新增实体）

### 3.1 已有实体（保持不变）

- `NewsArticle` — 单条原始新闻（含置信度、情感、Embedding等20+字段）
- `MarketData` — 行情数据（股票代码、价格、涨跌幅等）

### 3.2 新增核心实体：Intelligence（情报）

```mermaid
classDiagram
    class Intelligence {
        -Long id
        -String title
        -String summary
        -String content
        -String priority
        -String primarySource
        -String credibilityLevel
        -Double credibilityScore
        -Integer sourceCount
        -String sentiment
        -Double sentimentScore
        -String relatedStocks
        -String tags
        -String embeddingJson
        -LocalDateTime latestArticleTime
        -LocalDateTime createdAt
        -LocalDateTime updatedAt
    }

    class IntelligenceArticle {
        -Long id
        -Long intelligenceId
        -Long articleId
        -Boolean isPrimary
        -LocalDateTime addedAt
    }

    Intelligence "1" --> "*" IntelligenceArticle
    IntelligenceArticle "*" --> "1" NewsArticle
```

说明：
- `Intelligence` 是面向用户展示的聚合实体
- `IntelligenceArticle` 是关联表，记录哪些新闻属于哪条情报
- `isPrimary` 标记置信度最高的那条新闻为主来源
- 一条新闻只能属于一条情报（多对一）
- `content` 字段存储 LLM 基于多条新闻综合生成的分析正文

### 3.3 新增用户相关实体

```mermaid
classDiagram
    class User {
        -Long id
        -String email
        -String nickname
        -String passwordHash
        -String avatar
        -String memberLevel
        -LocalDateTime memberExpireAt
        -LocalDateTime createdAt
        -LocalDateTime lastLoginAt
    }

    class UserProfile {
        -Long id
        -Long userId
        -String investorType
        -String investmentCycle
        -String focusAreas
        -LocalDateTime updatedAt
    }

    class UserHolding {
        -Long id
        -Long userId
        -String stockCode
        -String stockName
        -String sector
        -LocalDateTime addedAt
    }

    class UserFavorite {
        -Long id
        -Long userId
        -Long newsId
        -LocalDateTime createdAt
    }

    class NewsFeedback {
        -Long id
        -Long userId
        -Long newsId
        -String feedbackType
        -LocalDateTime createdAt
    }

    class SearchHistory {
        -Long id
        -Long userId
        -String keyword
        -LocalDateTime searchedAt
    }

    class TrendingSearch {
        -Long id
        -String keyword
        -Integer searchCount
        -LocalDate date
    }

    User "1" --> "*" UserProfile
    User "1" --> "*" UserHolding
    User "1" --> "*" UserFavorite
    User "1" --> "*" NewsFeedback
    User "1" --> "*" SearchHistory
    UserFavorite "*" --> "1" Intelligence
    NewsFeedback "*" --> "1" Intelligence
```

---

## 4. 完整类图（V2）

```mermaid
classDiagram
    %% === 已有类（保持不变） ===
    class NewsArticle {
        -Long id
        -String title
        -String content
        -String summary
        -String sourceUrl
        -String sourceName
        -String sourceType
        -String credibilityLevel
        -Double credibilityScore
        -Double sourceCredibility
        -Double llmCredibility
        -Double freshnessCredibility
        -Double crossCredibility
        -String sentiment
        -Double sentimentScore
        -String relatedStocks
        -String tags
        -String contentHash
        -Boolean isDuplicate
        -String embeddingJson
        -LocalDateTime publishedAt
        -LocalDateTime collectedAt
    }

    class MarketData {
        -Long id
        -String stockCode
        -String stockName
        -Double currentPrice
        -Double changePercent
        -Double volume
        -Double turnoverRate
        -Double peRatio
        -String sector
        -LocalDateTime dataTime
        -LocalDateTime collectedAt
    }

    class RssCollector {
        +List~RssItem~ collect(url, sourceName)
    }

    class MarketDataCollector {
        +CollectResult collectAll()
    }

    class NewsCollectorService {
        +CollectResult collectAll()
    }

    class SmartQueryService {
        +QueryResult query(userQuestion)
    }

    class DeduplicationService {
        +String checkDuplicate(title, url, content)
        +long simHash(text)
        +int hammingDistance(a, b)
    }

    class CredibilityService {
        +CredibilityResult assess(article)
    }

    class EmbeddingClient {
        +float[] embed(text)
    }

    class VectorSearchService {
        +List~Long~ semanticSearch(queryText, topK)
        +void addVector(articleId, vector)
    }

    class NewsController {
        +collectNews()
        +listNews(hours)
        +collectMarket()
        +listMarket()
        +smartQuery(body)
        +stats()
        +logs(count)
    }

    %% === V2 新增类 ===
    class AuthController {
        +register(body)
        +login(body)
        +oauth(body)
        +logout()
    }

    class HomeController {
        +getHomeData()
        +getMarketOverview()
    }

    class NewsDetailController {
        +getDetail(id)
        +submitFeedback(id, body)
        +toggleFavorite(id, body)
        +generateReport(id)
    }

    class InsightController {
        +getOverview(period)
        +getSectors(limit)
        +getEvents(limit)
        +getReports(page, size)
    }

    class SearchController {
        +search(body)
        +getHistory(limit)
        +clearHistory()
        +getTrending(limit)
    }

    class ProfileController {
        +getProfile()
        +saveProfile(body)
        +addHolding(body)
        +removeHolding(stockCode)
        +getFocusOptions()
    }

    class UserController {
        +getCenterData()
        +getFavorites(page, size)
        +getReadHistory(page, size)
    }

    class AuthService {
        +register(email, nickname, password)
        +login(email, password)
        +generateToken(user)
        +validateToken(token)
    }

    class ProfileService {
        +getProfile(userId)
        +saveProfile(userId, profile)
        +addHolding(userId, holding)
        +removeHolding(userId, stockCode)
    }

    class SearchService {
        +search(keyword, page, size, sortBy)
        +getHistory(userId, limit)
        +clearHistory(userId)
        +getTrending(limit)
        +recordSearch(userId, keyword)
    }

    class InsightService {
        +getMarketOverview(period)
        +getSectorHeat(limit)
        +getHotEvents(limit)
        +getHotReports(page, size)
    }

    class ImpactAnalysisService {
        +analyzePersonalImpact(article, userProfile)
        +generateActionSuggestion(article, userProfile)
    }

    class UserService {
        +getCenterData(userId)
        +getFavorites(userId, page, size)
        +getReadHistory(userId, page, size)
    }

    %% === 关系 ===
    AuthController --> AuthService
    HomeController --> NewsCollectorService
    HomeController --> InsightService
    NewsDetailController --> ImpactAnalysisService
    NewsDetailController --> VectorSearchService
    InsightController --> InsightService
    SearchController --> SearchService
    SearchController --> VectorSearchService
    ProfileController --> ProfileService
    UserController --> UserService

    NewsCollectorService --> RssCollector
    NewsCollectorService --> DeduplicationService
    NewsCollectorService --> CredibilityService
    NewsCollectorService --> EmbeddingClient
    NewsCollectorService --> VectorSearchService
    SmartQueryService --> VectorSearchService
    SearchService --> VectorSearchService
    ImpactAnalysisService --> ProfileService
    InsightService --> NewsCollectorService
```

---

## 5. 各模块详细设计

### 5.1 用户认证模块（AuthService）

对应页面：登录注册页、注册页

技术方案：
- JWT Token 认证，存储在 H2 的 `users` 表
- 密码使用 BCrypt 加密存储
- Token 有效期：普通登录 24h，记住我 7d
- 第三方登录预留 OAuth2 接口（微信/QQ/手机号）

数据表：
```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar VARCHAR(500),
    member_level VARCHAR(20) DEFAULT 'free',
    member_expire_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP
);
```

### 5.2 首页模块（HomeController）

对应页面：首页

组成部分：
1. 用户问候栏 — 昵称 + 画像标签 + 市场状态一句话
2. 快速操作区 — 4个操作入口（采集新闻、智能问答、行情数据、我的画像）
3. 市场概览卡片 — 情绪指数 + 涨跌比 + 趋势图
4. 情报列表 — 按时间倒序，每条含优先级/来源/摘要/影响分析/置信度

数据来源：
- 问候栏：`UserProfile` + `InsightService.getMarketOverview()`
- 市场概览：`MarketData` 聚合计算 + `NewsArticle` 情感统计
- 情报列表：`Intelligence` 表（聚合后的情报），通过 `GET /api/intelligences` 接口

情绪指数计算逻辑：
```
sentimentIndex = (positiveCount / totalCount) * 100
```
基于最近24h情报的 sentiment 字段统计。

### 5.3 情报聚类模块（EventClusterService）🆕 核心新增

这是连接「新闻采集」和「情报展示」的关键模块。

聚类触发时机：
- 每次新闻采集完成后自动触发
- 定时任务每30分钟全量重新聚类

聚类算法流程：
```
1. 取最近48h内所有未归类的 NewsArticle
2. 对每条新闻计算标题 SimHash 指纹
3. 与已有 Intelligence 的标题 SimHash 比较（汉明距离 ≤ 10）
4. 如果匹配到已有情报 → 将新闻关联到该情报，更新情报元数据
5. 如果未匹配 → 创建新的 Intelligence，该新闻为主来源
6. 对新创建/更新的情报，调用 LLM 生成综合标题和摘要
```

情报元数据更新逻辑：
```
intelligence.sourceCount = 关联的不同来源新闻数
intelligence.credibilityScore = MAX(所有关联新闻的 credibilityScore)
intelligence.credibilityLevel = 基于 credibilityScore 判定
intelligence.primarySource = credibilityScore 最高的新闻的 sourceName
intelligence.sentiment = 多数投票（positive/negative/neutral）
intelligence.latestArticleTime = MAX(所有关联新闻的 collectedAt)
intelligence.relatedStocks = 合并所有关联新闻的 relatedStocks 去重
intelligence.tags = 合并所有关联新闻的 tags 去重
```

优先级计算：
```
if (credibilityScore >= 0.8 && sourceCount >= 2) → "high"（重要）
else if (credibilityScore >= 0.5) → "medium"（一般）
else → "low"（低）
```

数据表：
```sql
CREATE TABLE intelligences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    summary VARCHAR(1000),
    content CLOB,
    priority VARCHAR(20),
    primary_source VARCHAR(100),
    credibility_level VARCHAR(20),
    credibility_score DOUBLE,
    source_count INT DEFAULT 1,
    sentiment VARCHAR(20),
    sentiment_score DOUBLE,
    related_stocks VARCHAR(500),
    tags VARCHAR(500),
    title_simhash BIGINT,
    embedding_json CLOB,
    latest_article_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE intelligence_articles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    intelligence_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (intelligence_id) REFERENCES intelligences(id),
    FOREIGN KEY (article_id) REFERENCES news_articles(id),
    UNIQUE (article_id)
);
```

### 5.4 情报详情模块（IntelligenceController + ImpactAnalysisService）

对应页面：情报详情页

这是交互稿中最复杂的页面，包含：

1. 文章头部 — 优先级标签 + 置信度 + 来源数 + 标题 + 时间 + 阅读时间 + 主来源名
2. 信息来源列表 — 该情报聚合的所有 NewsArticle，每条含可信度标签 + 查看原文链接
3. 正文内容 — LLM 基于多条新闻综合生成的分析文章
4. 个性化影响分析（核心功能）：
   - 用户画像卡片（关注领域/投资风格/风险偏好）
   - 影响项列表（高/中/低影响 + 详细分析）
5. 操作建议 — LLM 生成的投资建议
6. 相关情报 — 语义相似的其他情报（通过 Embedding 搜索 Intelligence）

正文生成流程：
```
获取情报关联的所有 NewsArticle
→ 按 credibilityScore 降序排列
→ 组装 Prompt：
    "基于以下{N}条来自不同来源的新闻报道，
     综合生成一篇结构化的投研分析文章。
     要求：标注信息来源、区分事实与观点、包含数据引用"
→ 调用 GPT-5.4 生成
→ 缓存到 intelligence.content 字段
```

个性化影响分析流程：
```
获取情报数据 + 用户画像
→ ImpactAnalysisService.analyzePersonalImpact(intelligence, profile)
→ 调用 Qwen3.5-flash LLM，Prompt 包含：
    - 情报摘要、标签、关联股票
    - 用户关注领域、持仓、投资风格
→ 返回结构化影响分析 JSON
→ 缓存结果(同一用户+同一情报 1小时内不重复调用)
```

信息来源列表：
```
SELECT na.* FROM news_articles na
JOIN intelligence_articles ia ON na.id = ia.article_id
WHERE ia.intelligence_id = {intelligenceId}
ORDER BY na.credibility_score DESC
```

### 5.4 市场洞察模块（InsightController + InsightService）

对应页面：市场洞察页

组成部分：
1. 市场概览（扩展版）— 3个数据项（情绪指数/涨跌比/成交量）+ 情绪趋势图
2. 行业热度榜 — 基于新闻标签聚合，按热度排序 Top5
3. 热门事件时间轴 — 按时间排列的重大事件，含影响标签
4. 热门研报 — 来自东方财富研报源的研报列表

行业热度计算逻辑：
```
1. 从最近24h的 NewsArticle.tags 字段提取所有标签
2. 按标签分组计数 → 得到每个行业/领域的新闻数
3. 结合 MarketData 中对应板块的涨跌幅
4. heatScore = newsCount * 0.6 + abs(changePercent) * 10 * 0.4
5. 按 heatScore 降序排列
```

热门事件提取逻辑：
```
1. 从最近24h的 Intelligence 中，按 credibilityScore 降序取 Top
2. 情报本身已经是聚类后的结果，无需再聚类
3. 基于 sentiment 和 sourceCount 生成影响标签
```

### 5.5 搜索模块（SearchController + SearchService）

对应页面：搜索页

搜索流程：
```
用户输入关键词 → SearchService.search()
    → VectorSearchService.semanticSearch(keyword, 50) 语义检索
    → 同时做关键词模糊匹配（title LIKE %keyword%）
    → 合并去重，按 sortBy 排序
    → 分页返回
```

搜索历史：
- 存储在 `search_history` 表，按用户+时间
- 同一关键词只保留最新一条
- 最多保留 50 条

热门搜索：
- `trending_search` 表按天聚合
- 每次搜索 +1 计数
- 取当天 Top 5

### 5.6 用户画像模块（ProfileController + ProfileService）

对应页面：用户画像设置页

表单字段（对应交互稿）：

| 字段 | 选项 | 存储 |
|------|------|------|
| 投资者类型 | 保守型/均衡型/成长型 | user_profiles.investor_type |
| 投资周期 | 短线(<1月)/中线(1-6月)/长线(>6月) | user_profiles.investment_cycle |
| 关注领域 | AI/芯片/机器人/新能源车/... (多选) | user_profiles.focus_areas (逗号分隔) |
| 我的持仓 | 股票列表 (增删) | user_holdings 表 |

数据表：
```sql
CREATE TABLE user_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    investor_type VARCHAR(20),
    investment_cycle VARCHAR(20),
    focus_areas VARCHAR(500),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE user_holdings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(100),
    sector VARCHAR(50),
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

### 5.7 个人中心模块（UserController + UserService）

对应页面：个人中心页

组成部分：
1. 用户信息 — 头像 + 昵称 + 会员标签 + 编辑按钮
2. 数据统计 — 已读数/收藏数/报告数
3. 会员卡片 — 等级 + 进度条 + 权益说明
4. 功能菜单：
   - 投资画像设置 → 跳转画像设置页
   - 消息通知 → 通知列表
   - 数据导出 → 导出报告
   - 深色模式 → 主题切换
5. 设置菜单：
   - 账号安全
   - 帮助与反馈
   - 关于我们
6. 退出登录按钮

---

## 6. 新增文件清单

### 6.1 Controller 层
```
src/main/java/com/example/demo/controller/
├── NewsController.java          (已有，保持兼容)
├── AuthController.java          (新增)
├── HomeController.java          (新增)
├── IntelligenceController.java  (新增 — 情报列表+详情+反馈+收藏)
├── InsightController.java       (新增)
├── SearchController.java        (新增)
├── ProfileController.java       (新增)
└── UserController.java          (新增)
```

### 6.2 Service 层
```
src/main/java/com/example/demo/service/
├── NewsCollectorService.java    (已有，需扩展：采集后触发聚类)
├── SmartQueryService.java       (已有)
├── CredibilityService.java      (已有)
├── DeduplicationService.java    (已有)
├── EventClusterService.java     (新增 — 核心：新闻→情报聚类)
├── IntelligenceService.java     (新增 — 情报CRUD+正文生成)
├── AuthService.java             (新增)
├── ProfileService.java          (新增)
├── SearchService.java           (新增)
├── InsightService.java          (新增)
├── ImpactAnalysisService.java   (新增)
└── UserService.java             (新增)
```

### 6.3 Model 层
```
src/main/java/com/example/demo/model/
├── NewsArticle.java             (已有)
├── MarketData.java              (已有)
├── Intelligence.java            (新增 — 情报实体)
├── IntelligenceArticle.java     (新增 — 情报-新闻关联)
├── User.java                    (新增)
├── UserProfile.java             (新增)
├── UserHolding.java             (新增)
├── UserFavorite.java            (新增 — 收藏情报)
├── NewsFeedback.java            (新增 — 情报反馈)
├── SearchHistory.java           (新增)
└── TrendingSearch.java          (新增)
```

### 6.4 Repository 层
```
src/main/java/com/example/demo/repository/
├── NewsArticleRepository.java       (已有)
├── MarketDataRepository.java        (已有)
├── IntelligenceRepository.java      (新增)
├── IntelligenceArticleRepository.java (新增)
├── UserRepository.java              (新增)
├── UserProfileRepository.java       (新增)
├── UserHoldingRepository.java       (新增)
├── UserFavoriteRepository.java      (新增)
├── NewsFeedbackRepository.java      (新增)
├── SearchHistoryRepository.java     (新增)
└── TrendingSearchRepository.java    (新增)
```

---

## 7. 定时任务（SchedulerConfig 扩展）

| 任务 | 频率 | 说明 | 状态 |
|------|------|------|------|
| 新闻采集 | 每15分钟 | 10个RSS源采集+去重+LLM提取+Embedding | ✅ 已有 |
| 行情采集 | 每5分钟 | 麦蕊智数API采集A股行情 | ✅ 已有 |
| 事件聚类 | 采集后+每30分钟 | SimHash聚类新闻→生成/更新情报 | 🆕 核心新增 |
| 情报正文生成 | 聚类后触发 | LLM综合多条新闻生成情报正文 | 🆕 核心新增 |
| 热门搜索统计 | 每小时 | 聚合搜索记录生成热门榜 | 🆕 新增 |
| 行业热度计算 | 每30分钟 | 基于情报标签+行情数据计算热度 | 🆕 新增 |

---

## 8. 外部依赖

| 依赖 | 用途 | 模型/版本 | 状态 |
|------|------|----------|------|
| RSSHub | 10个新闻源采集 | rsshub.chn.moe | ✅ 已接入 |
| 麦蕊智数API | A股行情数据 | REST API | ✅ 已接入 |
| GPT-5.4 | 新闻结构化提取(LLM) | OpenAI兼容 | ✅ 已接入 |
| Qwen3.5-flash | 智能问答+影响分析(LLM) | OpenAI兼容 | ✅ 已接入 |
| Qwen-text-embedding-v4 | 文本向量化 | 1536维 | ✅ 已接入 |
| Spring Security | JWT认证 | 6.x | 🆕 需引入 |
| BCrypt | 密码加密 | Spring Security内置 | 🆕 需引入 |

---

## 9. 核心算法说明

### 9.1 四级去重（已实现）
1. URL精确匹配
2. SimHash标题指纹（汉明距离≤2）
3. 内容MD5哈希
4. SimHash内容指纹（汉明距离≤3）

### 9.2 四维置信度评估（已实现）
- 来源权威性 (30%) — 基于来源名称的预设分数
- LLM情感置信度 (25%) — sentimentScore 偏离0.5的程度
- 时效性 (20%) — 基于采集时间的衰减函数
- 交叉验证 (25%) — 同一事件被不同来源报道的数量

### 9.3 语义搜索（已实现）
- Embedding: Qwen-text-embedding-v4 (1536维)
- 相似度: 余弦相似度
- 存储: ConcurrentHashMap 内存缓存 + H2持久化

### 9.4 事件聚类算法（新增 — 核心）

将多条报道同一事件的新闻聚合为一条情报：

```
输入: 新采集的 NewsArticle 列表
输出: 创建/更新 Intelligence 记录

算法:
for each article in newArticles:
    titleHash = DeduplicationService.simHash(article.title)
    matched = false

    // 尝试匹配已有情报
    for each intel in recentIntelligences (48h内):
        if hammingDistance(titleHash, intel.titleSimhash) <= 10:
            // 匹配成功，关联到已有情报
            createIntelligenceArticle(intel.id, article.id)
            updateIntelligenceMetadata(intel)  // 更新来源数、置信度等
            matched = true
            break

    if not matched:
        // 创建新情报
        intel = new Intelligence()
        intel.title = article.title
        intel.summary = article.summary
        intel.titleSimhash = titleHash
        intel.primarySource = article.sourceName
        // ... 复制其他字段
        save(intel)
        createIntelligenceArticle(intel.id, article.id, isPrimary=true)

// 对新创建/更新的情报，异步调 LLM 生成综合正文
for each updatedIntel in updatedIntelligences:
    if updatedIntel.sourceCount >= 2:
        generateContent(updatedIntel)  // LLM 综合生成
```

注意：
- 去重用汉明距离 ≤ 2（严格），聚类用 ≤ 10（宽松）
- 去重是为了过滤完全相同的新闻，聚类是为了识别同一事件的不同报道
- 已有的 `CredibilityService.assessCrossValidation()` 中的 ≤ 10 逻辑可以复用

### 9.5 个性化影响分析（新增）
```
输入: Intelligence + UserProfile
→ 构建 Prompt:
    "基于用户画像(投资者类型/关注领域/持仓)，
     分析这条情报对用户的个性化影响，
     返回JSON格式的影响分析和操作建议"
→ 调用 Qwen3.5-flash
→ 解析返回的结构化 JSON
→ 缓存结果(同一用户+同一情报 1小时内不重复调用)
```

列表页简化影响分析（不调 LLM）：
```
匹配 intelligence.relatedStocks 与用户持仓
→ 如果命中：生成模板文本 "你持有的{stockName}可能受此影响..."
→ 如果未命中：不显示影响分析区域
```
