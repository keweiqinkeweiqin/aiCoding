# 华尔街之眼 — API 对接文档 V3

> 基于实际代码实现，2026-03-27 更新
> Base URL: `http://{host}:8080`
> 认证方式: Query param `userId={userId}`（登录后获取）

## 认证流程

1. 调 `POST /api/auth/login` 传手机号，返回 `userId`
2. 前端存储 `userId`，后续请求在 Header 中带 `userId query param: {userId}`
3. 不传 `userId` 的接口也能用，但个性化功能（排序/分析）不生效

需要 userId query param 的接口汇总：

| 接口 | userId query param 作用 | 不传时行为 |
|------|---------------|-----------|
| GET /api/home | 读取昵称+画像标签 | 返回 Guest |
| GET /api/intelligences | 读取画像做个性化排序 | 按时间倒序（不排序） |
| GET /api/intelligences/{id} | 调 LLM 生成个性化分析 | personalizedAnalysis=null |
| GET /api/profile | 读取画像 | 返回空 |
| PUT /api/profile | 保存画像 | 默认 userId=1 |
| GET /api/auth/me | 获取用户信息 | 401 |
| PUT /api/auth/me | 修改昵称 | 404 |
| POST /api/analysis/generate | 生成 AI 研判 | 401 |
| GET /api/analysis/stream | SSE 流式研判 | 返回错误 |
| GET /api/analysis/history | 研判历史 | 401 |

---

## 一、认证模块 `/api/auth`

### 1.1 POST /api/auth/login — 手机号登录（自动注册）

Request:
```json
{
  "phone": "13800138000",
  "nickname": "Tom"
}
```
nickname 可选，不传则自动生成 `user_8000`

Response (已有用户):
```json
{
  "code": 200,
  "data": {
    "userId": 1,
    "phone": "13800138000",
    "nickname": "Tom",
    "isNew": false
  }
}
```

Response (新用户自动注册):
```json
{
  "code": 200,
  "data": {
    "userId": 2,
    "phone": "13900139000",
    "nickname": "user_9000",
    "isNew": true
  }
}
```

### 1.2 GET /api/auth/me — 获取当前用户信息

Param: `?userId=1`

Response:
```json
{
  "code": 200,
  "data": {
    "userId": 1,
    "phone": "13800138000",
    "nickname": "Tom"
  }
}
```

### 1.3 PUT /api/auth/me — 修改昵称

Param: `?userId=1`

Request:
```json
{ "nickname": "NewName" }
```

Response:
```json
{ "code": 200, "message": "updated" }
```

---

## 一B、首页模块 `/api/home`

### 1B.1 GET /api/home — 首页聚合数据

Param: `?userId=1`（可选，不传返回 Guest）

Response:
```json
{
  "code": 200,
  "data": {
    "greeting": {
      "nickname": "Tom",
      "profileTag": "Growth | AI",
      "marketStatus": "Market sentiment is bullish, 56 intel tracked"
    },
    "quickActions": [
      { "id": "collect", "name": "Collect News", "icon": "refresh" },
      { "id": "query", "name": "Smart Q&A", "icon": "chat" },
      { "id": "market", "name": "Market Data", "icon": "chart" },
      { "id": "profile", "name": "My Profile", "icon": "user" }
    ],
    "marketOverview": {
      "sentimentIndex": 72,
      "sentimentLabel": "Bullish",
      "totalIntelligences": 56,
      "positiveCount": 40,
      "negativeCount": 8,
      "neutralCount": 8,
      "stockUp": 30,
      "stockDown": 15,
      "stockFlat": 5,
      "avgChangePercent": 1.23,
      "hotTags": [
        { "tag": "AI", "count": 28 },
        { "tag": "chip", "count": 15 },
        { "tag": "semiconductor", "count": 10 }
      ]
    }
  }
}
```

greeting 说明：
- `nickname`: 用户昵称，未登录返回 "Guest"
- `profileTag`: 从画像提取的标签（投资者类型 + 前两个关注领域）
- `marketStatus`: 基于最近24h情报情感分布生成的一句话

marketOverview 说明：
- `sentimentIndex`: 情绪指数 0-100（positive占比 * 100）
- `sentimentLabel`: Bullish(>=70) / Mixed(>=40) / Bearish(<40)
- `stockUp/Down/Flat`: 行情涨跌平统计
- `avgChangePercent`: 平均涨跌幅
- `hotTags`: 最近24h情报中出现最多的标签 Top 10

---

## 二、用户画像模块 `/api/profile`

> 用户画像与持仓合并为一张表 `user_profiles`，只提供一个保存接口。

### 2.1 PUT /api/profile/save — 保存完整画像（含持仓）

Param: `?userId=1`

Request:
```json
{
  "investorType": "growth",
  "investmentCycle": "medium",
  "focusAreas": ["AI", "chip", "robot"],
  "holdings": ["英伟达", "台积电"]
}
```

Response:
```json
{ "code": 200, "message": "saved" }
```

字段说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| investorType | string | 投资者类型：`conservative` / `balanced` / `growth` |
| investmentCycle | string | 投资周期：`short` / `medium` / `long` |
| focusAreas | string[] | 关注领域，如 `["AI", "chip", "robot"]` |
| holdings | string[] | 持仓股票名称，如 `["英伟达", "台积电"]` |

- 新用户首次调用自动创建画像
- 已有用户调用则更新（按 userId upsert）
- 所有字段均为可选，只更新传入的字段

---

## 三、情报模块 `/api/intelligences`（核心）

### 3.1 GET /api/intelligences — 情报列表（个性化排序）

Param: `?userId=1`（自动读取画像排序）
Params: `?hours=24&page=0&size=20`

Response:
```json
{
  "code": 200,
  "data": {
    "content": [
      {
        "id": 1,
        "priority": "high",
        "title": "英伟达发布新一代AI芯片B300",
        "summary": "英伟达在GTC大会上发布B300芯片...",
        "primarySource": "财联社",
        "credibilityLevel": "authoritative",
        "credibilityScore": 0.87,
        "sourceCount": 3,
        "sentiment": "positive",
        "sentimentScore": 0.85,
        "relatedStocks": "NVDA,TSM",
        "tags": "AI,chip",
        "latestArticleTime": "2026-03-27T09:15:00",
        "createdAt": "2026-03-27T09:20:00"
      }
    ],
    "totalElements": 56,
    "totalPages": 3,
    "currentPage": 0
  }
}
```

排序逻辑：
- 标签匹配用户关注领域: +10分/个
- 关联股票匹配用户持仓: +20分/个
- 高优先级(high): +5分
- 多来源(sourceCount>1): +3分
- 同分按时间倒序

### 3.2 GET /api/intelligences/{id} — 情报详情

Param: `?userId=1`（可选，传了会返回个性化分析）

Response:
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "priority": "high",
    "credibilityLevel": "authoritative",
    "credibilityScore": 0.87,
    "sourceCount": 3,
    "title": "英伟达发布新一代AI芯片B300",
    "summary": "英伟达在GTC大会上发布B300芯片...",
    "content": "综合多条新闻生成的分析正文...",
    "primarySource": "财联社",
    "sentiment": "positive",
    "sentimentScore": 0.85,
    "relatedStocks": "NVDA,TSM",
    "tags": "AI,chip",
    "latestArticleTime": "2026-03-27T09:15:00",
    "readTime": "3 min",
    "sources": [
      {
        "articleId": 101,
        "sourceName": "财联社",
        "credibilityTag": "权威",
        "title": "英伟达GTC大会：B300芯片正式发布",
        "sourceUrl": "https://..."
      },
      {
        "articleId": 102,
        "sourceName": "华尔街见闻",
        "credibilityTag": "可信",
        "title": "英伟达新芯片B300性能跃升",
        "sourceUrl": "https://..."
      },
      {
        "articleId": 103,
        "sourceName": "36Kr资讯",
        "credibilityTag": "可信",
        "title": "NVIDIA B300: AI算力再升级",
        "sourceUrl": "https://..."
      }
    ],
    "relatedIntelligences": [
      {
        "id": 5,
        "title": "台积电3nm产能满载，AI芯片需求旺盛",
        "summary": "台积电最新财报显示...",
        "primarySource": "36Kr资讯",
        "sourceCount": 2,
        "credibilityScore": 0.75,
        "latestArticleTime": "2026-03-26T14:00:00"
      }
    ],
    "personalizedAnalysis": {
      "userProfile": {
        "investorType": "growth",
        "investmentCycle": "medium",
        "focusAreas": ["AI芯片", "云计算", "半导体"],
        "holdings": ["NVDA", "AMD"]
      },
      "analysis": "基于多来源交叉验证，此次芯片出口管制将直接影响...",
      "impacts": [
        {
          "stock": "NVDA",
          "impact": "中国区数据中心业务收入预计下降15-20%",
          "level": "重大影响",
          "volatility": "短期波动±8%",
          "revenueImpact": "2024财年整体营收影响-3%~-5%",
          "longTermImpact": "中国市场份额可能逐步被国产芯片替代"
        },
        {
          "stock": "AMD",
          "impact": "MI300系列芯片出口同样受限",
          "level": "中等影响",
          "volatility": "短期波动±3%"
        }
      ],
      "suggestion": "短期观望NVDA，关注国产AI芯片替代标的（寒武纪、海光信息）",
      "risks": ["政策进一步收紧风险", "国产替代进度不及预期"],
      "userContext": "基于成长型投资者画像，关注AI芯片/云计算领域，持仓NVDA/AMD"
    }
  }
}
```

content 生成逻辑：
- 多来源（>=2条新闻）: LLM 综合生成中文分析文章（300-500字）
- 单来源或 LLM 失败: 使用原始新闻内容
- 生成后缓存，下次不重复调用

personalizedAnalysis 说明：
- 需要传 `userId` 参数且用户有画像才会生成
- 不传或无画像时返回 `null`
- 结构包含三部分：
  - `userProfile`: 用户画像卡片（investorType/investmentCycle/focusAreas数组/holdings数组）
  - `impacts`: LLM 基于用户持仓逐个分析的个股影响（level/volatility/revenueImpact/longTermImpact）
  - `suggestion` + `risks`: LLM 基于画像生成的操作建议和风险提示
- `analysis`: LLM 综合研判文本
- `userContext`: 说明该分析基于何种画像生成

sources 说明：
- 返回所有关联的原始新闻（不去重）
- sourceCount = sources.length
- 按置信度降序排列

### 3.3 GET /api/intelligences/{id}/related — 相关情报

Params: `?limit=5`

Response:
```json
{
  "code": 200,
  "data": [
    {
      "id": 5,
      "title": "台积电3nm产能满载",
      "summary": "...",
      "primarySource": "36Kr资讯",
      "sourceCount": 2,
      "credibilityScore": 0.75,
      "latestArticleTime": "2026-03-26T14:00:00"
    }
  ]
}
```

### 3.4 POST /api/intelligences/cluster — 手动触发聚类（调试）

Response:
```json
{
  "code": 200,
  "data": { "created": 15, "merged": 3 }
}
```

聚类算法：
1. Embedding 余弦相似度 >= 0.85 → 合并到已有情报
2. SimHash 汉明距离 <= 20 → 合并（fallback）
3. 都不匹配 → 创建新情报

---

## 三B、市场洞察 `/api/insight`（聚合接口）

### 3B.1 GET /api/insight — 洞察页全部数据

Params: `?days=7`（默认7天）

一次返回四个模块：overview + sectors + events + reports

Response:
```json
{
  "code": 200,
  "data": {
    "overview": {
      "sentimentIndex": 68,
      "sentimentLabel": "Greed",
      "totalIntelligences": 156,
      "positiveCount": 106,
      "negativeCount": 25,
      "aiHeatPercent": 92,
      "stockUp": 30,
      "stockDown": 15,
      "trendData": [
        { "date": "2026-03-21", "value": 55 },
        { "date": "2026-03-27", "value": 68 }
      ]
    },
    "sectors": [
      { "rank": 1, "name": "AI", "intelCount": 32, "heatScore": 96 },
      { "rank": 2, "name": "chip", "intelCount": 24, "heatScore": 92 }
    ],
    "events": [
      {
        "id": 1,
        "time": "2026-03-27T08:32:00",
        "title": "美国商务部宣布最新AI芯片出口管制新政",
        "summary": "...",
        "sourceCount": 3,
        "impactTag": "major",
        "sentiment": "negative",
        "tags": "AI,chip"
      }
    ],
    "reports": [
      {
        "id": 101,
        "title": "2023年全球AI芯片行业深度报告",
        "source": "东方财富研报",
        "sourceUrl": "https://...",
        "publishedAt": "2026-03-25T10:00:00",
        "summary": "..."
      }
    ]
  }
}
```

overview 说明：
- `sentimentIndex`: 0-100，基于 positive 占比
- `sentimentLabel`: Extreme Greed(>=75) / Greed(>=55) / Neutral(>=45) / Fear(>=25) / Extreme Fear(<25)
- `aiHeatPercent`: AI 相关情报占比
- `trendData`: 每天的情绪指数，用于绘制趋势图

sectors 说明：
- 基于 Intelligence.tags 聚合计数
- `heatScore` = min(99, intelCount * 10)

events 说明：
- 按时间倒序的情报列表
- `impactTag`: major(高优先级) / moderate(高置信度) / positive(积极情感) / minor(其他)

reports 说明：
- 从研报类新闻源筛选

---

## 四、AI 研判分析 `/api/analysis`

### 4.1 POST /api/analysis/generate — 同步生成 AI 研判

Param: `?userId=1`

Request:
```json
{ "articleId": 1 }
```

Response:
```json
{
  "analysis": "情报研判文本，概述核心影响...",
  "impacts": [
    {
      "stock": "NVDA",
      "impact": "B300芯片发布利好数据中心业务",
      "level": "重大影响",
      "volatility": "短期波动±5%",
      "revenueImpact": "数据中心营收预计增长20%",
      "longTermImpact": "巩固AI算力龙头地位"
    }
  ],
  "suggestion": "建议持有，关注量能变化",
  "risks": ["芯片出口管制风险", "竞品追赶风险"],
  "userContext": "基于成长型投资者画像，关注AI/芯片领域"
}
```

### 4.2 GET /api/analysis/stream — SSE 流式生成

Param: `?userId=1`
Params: `?articleId=1`

返回 SSE 事件流，逐步推送分析结果。

### 4.3 GET /api/analysis/history — 研判历史

Param: `?userId=1`

Response:
```json
[
  {
    "id": 1,
    "userId": 1,
    "newsArticleId": 101,
    "analysisText": "研判结果JSON...",
    "investmentStyle": "growth",
    "createdAt": "2026-03-27T10:30:00"
  }
]
```

---

## 五、数据采集 `/api/news`, `/api/market`

### 5.1 POST /api/news/collect — 触发新闻采集

采集流程: RSS拉取 → 四级去重 → LLM结构化提取 → Embedding向量化 → 置信度评估 → 事件聚类

Response:
```json
{
  "collected": 120,
  "deduplicated": 45,
  "stored": 75,
  "sources": [
    { "name": "财联社", "collected": 15, "deduplicated": 3, "stored": 12 },
    { "name": "华尔街见闻", "collected": 10, "deduplicated": 2, "stored": 8 }
  ]
}
```

RSS 来源（10个）:
36Kr资讯, 36Kr快讯, 财联社, 财联社电报, 华尔街见闻, 金十数据, 格隆汇, 第一财经, 东方财富研报, IT之家AI

### 5.2 GET /api/news — 原始新闻列表

Params: `?hours=24`

返回 NewsArticle 数组，含 title, content, summary, sourceName, credibilityLevel, credibilityScore, sentiment, sentimentScore, relatedStocks, tags, sourceUrl, collectedAt 等字段。

### 5.3 POST /api/market/collect — 触发行情采集

Response:
```json
{ "collected": 50, "stored": 50 }
```

### 5.4 GET /api/market — 行情列表

返回 MarketData 数组，含 stockCode, stockName, currentPrice, changePercent, volume, turnoverRate, peRatio, sector, dataTime 等字段。

---

## 六、智能问答 `/api/query`

### 6.1 POST /api/query — 语义检索 + LLM 推理

Request:
```json
{ "question": "AI芯片行业最近有什么重大变化？" }
```

Response:
```json
{
  "answer": "基于15条相关新闻的AI分析结果...",
  "matchedCount": 15,
  "relatedNews": [
    {
      "id": 101,
      "title": "英伟达发布B300芯片",
      "sourceName": "财联社",
      "credibilityLevel": "authoritative",
      "sourceUrl": "https://..."
    }
  ]
}
```

---

## 七、系统 `/api/stats`, `/api/logs`

### 7.1 GET /api/stats — 系统状态

Response:
```json
{
  "totalNews": 1250,
  "totalMarket": 50,
  "vectorCacheSize": 1180
}
```

### 7.2 GET /api/logs — 实时日志

Params: `?count=50`

Response:
```json
{
  "logs": ["2026-03-27 10:30:00 INFO ...", "..."],
  "total": 500
}
```

---

## 八、数据模型

### 8.1 实体关系

```
User (phone, nickname)
  └── UserProfile (investorType, investmentCycle, focusAreas, holdings)
  └── AnalysisRecord (newsArticleId, analysisText, investmentStyle)

Intelligence (title, summary, content, priority, credibilityScore, sourceCount, sentiment, tags)
  └── IntelligenceArticle (articleId, isPrimary)
        └── NewsArticle (title, content, sourceName, credibilityScore, sentiment, tags, embeddingJson)

MarketData (stockCode, stockName, currentPrice, changePercent, volume)
```

### 8.2 置信度评估（四维）

| 维度 | 权重 | 说明 |
|------|------|------|
| 来源权威性 | 30% | 基于来源名预设分数 |
| LLM 情感置信度 | 25% | sentimentScore 偏离 0.5 的程度 |
| 时效性 | 20% | 基于采集时间的衰减函数 |
| 交叉验证 | 25% | 同一事件被不同来源报道的数量 |

credibilityLevel: `authoritative`(>=0.8) | `normal`(>=0.5) | `questionable`(<0.5)

---

## 九、未实现接口（对应交互稿页面）

| 交互稿页面 | 需要的接口 | 说明 |
|-----------|-----------|------|
| 搜索页 | POST /api/search | 搜索情报 |
| 搜索页 | GET /api/search/history | 搜索历史 |
| 搜索页 | GET /api/search/trending | 热门搜索 |
| 个人中心页 | GET /api/user/center | 用户统计 + 会员信息 |
| 个人中心页 | GET /api/user/favorites | 收藏列表 |
| 个人中心页 | GET /api/user/history | 浏览历史 |
| 情报详情页 | POST /api/intelligences/{id}/feedback | 认同/不认同 |
| 情报详情页 | POST /api/intelligences/{id}/favorite | 收藏/取消 |
| 情报详情页 | POST /api/intelligences/{id}/report | 生成完整报告 |
