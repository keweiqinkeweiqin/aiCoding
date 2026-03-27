# 华尔街之眼 — AI 投研情报引擎 技术方案

## 1. 产品概述

聚焦 AI 与科技投资领域的智能情报引擎。从 10 个异构信息源自动采集新闻和行情数据，通过 LLM 结构化提取 + Embedding 向量化 + SimHash 去重 + 四维置信度评估，结合用户画像生成个性化投研分析。

技术栈：Spring Boot 3.4 + JDK 21 + Spring AI + H2 + 内存向量缓存

## 2. 系统架构

```mermaid
graph TD
    subgraph 前端
        UI[调试面板 HTML/JS]
    end
    subgraph Controller
        NC[NewsController]
    end
    subgraph Service
        NCS[NewsCollectorService]
        SQS[SmartQueryService]
        CS[CredibilityService]
        DS[DeduplicationService]
    end
    subgraph Collector
        RSS[RssCollector]
        MDC[MarketDataCollector]
    end
    subgraph Embedding
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

    UI --> NC
    NC --> NCS
    NC --> SQS
    NC --> MDC
    NCS --> RSS --> RSSHUB
    NCS --> DS
    NCS --> CS
    NCS --> EC
    NCS --> VSS
    NCS -->|GPT-5.4| LLM
    SQS --> VSS
    SQS -->|Qwen3.5-flash| LLM
    EC --> EMBAPI
    VSS --> MEM
    MDC --> MAIRUI
    NCS --> H2
    MDC --> H2
    VSS --> H2
```

## 3. 类图

```mermaid
classDiagram
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
        -String getText(parent, tagName)
        -String getLink(parent)
    }

    class MarketDataCollector {
        +CollectResult collectAll()
    }

    class NewsCollectorService {
        +CollectResult collectAll()
        -void assessCredibilityForRecent()
        -String stripHtml(html)
    }

    class SmartQueryService {
        +QueryResult query(userQuestion)
        -String callLlm(systemPrompt, userPrompt)
    }

    class DeduplicationService {
        +String checkDuplicate(title, url, content)
        +long simHash(text)
        +int hammingDistance(a, b)
        +String computeContentHash(content)
    }

    class CredibilityService {
        +CredibilityResult assess(article)
        -double assessSource(article)
        -double assessLlmConfidence(article)
        -double assessFreshness(article)
        -double assessCrossValidation(article)
    }

    class EmbeddingClient {
        +float[] embed(text)
        +List~float[]~ embedBatch(texts)
    }

    class VectorSearchService {
        +void loadFromDatabase()
        +void addVector(articleId, vector)
        +List~Long~ semanticSearch(queryText, topK)
        +Long findSemanticDuplicate(text, threshold)
        +int cacheSize()
        +double cosineSimilarity(a, b)$
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

    class SchedulerConfig {
        +collectNews() @Scheduled 15min
        +collectMarketData() @Scheduled 5min
    }

    NewsController --> NewsCollectorService
    NewsController --> SmartQueryService
    NewsController --> MarketDataCollector
    NewsCollectorService --> RssCollector
    NewsCollectorService --> DeduplicationService
    NewsCollectorService --> CredibilityService
    NewsCollectorService --> EmbeddingClient
    NewsCollectorService --> VectorSearchService
    SmartQueryService --> VectorSearchService
    CredibilityService --> DeduplicationService
    VectorSearchService --> EmbeddingClient
    SchedulerConfig --> NewsCollectorService
    SchedulerConfig --> MarketDataCollector
```
