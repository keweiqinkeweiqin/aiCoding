# 华尔街之眼 — API 对接文档 V3

> 基于实际代码实现，2026-03-27 更新
> Base URL: `http://{host}:8080`
> 认证方式: Header `X-User-Id: {userId}`（登录后获取）

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

Header: `X-User-Id: 1`

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

Header: `X-User-Id: 1`

Request:
```json
{ "nickname": "NewName" }
```

Response:
```json
{ "code": 200, "message": "updated" }
```

---

## 二、用户画像模块 `/api/profile`

### 2.1 GET /api/profile — 获取简易画像

Header: `X-User-Id: 1`

Response:
```json
{
  "code": 200,
  "data": {
    "userId": 1,
    "investorType": "growth",
    "investmentCycle": "medium",
    "focusAreas": "AI,chip,robot",
    "holdings": "NVDA,TSM"
  }
}
```

investorType: `conservative` | `balanced` | `growth`
investmentCycle: `short` | `medium` | `long`
focusAreas/holdings: 逗号分隔字符串

### 2.2 PUT /api/profile — 保存简易画像

Header: `X-User-Id: 1`

Request:
```json
{
  "investorType": "growth",
  "investmentCycle": "medium",
  "focusAreas": "AI,chip,robot",
  "holdings": "NVDA,TSM"
}
```

Response:
```json
{ "code": 200, "message": "saved" }
```

### 2.3 GET /api/profile/detail — 获取完整画像（含持仓详情）

Header: `X-User-Id: 1`

Response:
```json
{
  "code": 200,
  "data": {
    "investorType": "growth",
    "investmentCycle": "medium",
    "focusAreas": ["AI", "chip", "robot"],
    "holdings": [
      {
        "stockCode": "NVDA",
        "stockName": "NVIDIA",
        "sector": "semiconductor",
        "positionRatio": 0.3,
        "costPrice": 120.5
      }
    ]
  }
}
```

### 2.4 PUT /api/profile/save — 保存完整画像

Header: `X-User-Id: 1`

Request:
```json
{
  "investorType": "growth",
  "investmentCycle": "medium",
  "focusAreas": ["AI", "chip", "robot"]
}
```

### 2.5 POST /api/profile/holdings — 添加持仓

Header: `X-User-Id: 1`

Request:
```json
{
  "stockCode": "NVDA",
  "stockName": "NVIDIA",
  "sector": "semiconductor",
  "positionRatio": 0.3,
  "costPrice": 120.5
}
```

### 2.6 DELETE /api/profile/holdings/{stockCode} — 删除持仓

Header: `X-User-Id: 1`

Example: `DELETE /api/profile/holdings/NVDA`

### 2.7 GET /api/profile/focus-options — 可选关注领域

Header: `X-User-Id: 1`

Response:
```json
{
  "code": 200,
  "data": [
    { "id": "ai_chip", "name": "AI芯片", "selected": true },
    { "id": "cloud", "name": "云计算", "selected": false },
    { "id": "semiconductor", "name": "半导体", "selected": true },
    { "id": "llm", "name": "大模型", "selected": false },
    { "id": "aigc", "name": "AIGC应用", "selected": false },
    { "id": "autonomous", "name": "自动驾驶", "selected": false },
    { "id": "robot", "name": "机器人", "selected": true },
    { "id": "quantum", "name": "量子计算", "selected": false },
    { "id": "biotech", "name": "生物科技", "selected": false },
    { "id": "new_energy", "name": "新能源", "selected": false }
  ]
}
```

---

## 三、情报模块 `/api/intelligences`（核心）

### 3.1 GET /api/intelligences — 情报列表（个性化排序）

Header: `X-User-Id: 1`（自动读取画像排序）
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
    ]
  }
}
```

content 生成逻辑：
- 如果 LLM 可用: 基于多条新闻综合生成分析文章
- 如果 LLM 不可用: 拼接关联新闻内容，按来源分段

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

## 四、AI 研判分析 `/api/analysis`

### 4.1 POST /api/analysis/generate — 同步生成 AI 研判

Header: `X-User-Id: 1`

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

Header: `X-User-Id: 1`
Params: `?articleId=1`

返回 SSE 事件流，逐步推送分析结果。

### 4.3 GET /api/analysis/history — 研判历史

Header: `X-User-Id: 1`

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
  └── UserHolding (stockCode, stockName, sector, positionRatio, costPrice)
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
| 首页上半部分 | GET /api/home | 问候栏 + 快速操作 + 市场概览 |
| 市场洞察页 | GET /api/insight/overview | 市场情绪指数 + 趋势图 |
| 市场洞察页 | GET /api/insight/sectors | 行业热度榜 |
| 市场洞察页 | GET /api/insight/events | 热门事件时间轴 |
| 市场洞察页 | GET /api/insight/reports | 热门研报 |
| 搜索页 | POST /api/search | 搜索情报 |
| 搜索页 | GET /api/search/history | 搜索历史 |
| 搜索页 | GET /api/search/trending | 热门搜索 |
| 个人中心页 | GET /api/user/center | 用户统计 + 会员信息 |
| 个人中心页 | GET /api/user/favorites | 收藏列表 |
| 个人中心页 | GET /api/user/history | 浏览历史 |
| 情报详情页 | POST /api/intelligences/{id}/feedback | 认同/不认同 |
| 情报详情页 | POST /api/intelligences/{id}/favorite | 收藏/取消 |
| 情报详情页 | POST /api/intelligences/{id}/report | 生成完整报告 |
