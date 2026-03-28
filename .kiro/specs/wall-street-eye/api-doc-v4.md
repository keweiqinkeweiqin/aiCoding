# 华尔街之眼 — API 对接文档 V4

> Base URL: `http://{host}:8080`
> 用户标识: Query param `userId={id}`（登录后获取）
> focusAreas 保存时支持字符串 `"AI,chip"` 或数组 `["AI","chip"]` 两种格式

---

# 第一部分：C 端接口（移动端/前端页面）

---

## 1. 登录注册页

### POST /api/auth/login — 手机号登录（自动注册）

Request:
```json
{ "phone": "13800138000", "nickname": "Tom" }
```

Response:
```json
{
  "code": 200,
  "data": { "userId": 1, "phone": "13800138000", "nickname": "Tom", "isNew": false }
}
```

---

## 2. 首页

### GET /api/home — 首页聚合数据

Params: `?userId=1`

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
      "stockUp": 30,
      "stockDown": 15,
      "avgChangePercent": 1.23,
      "hotTags": [{ "tag": "AI", "count": 28 }]
    }
  }
}
```

### GET /api/intelligences — 情报列表（个性化排序）

Params: `?userId=1&hours=24&page=0&size=20`

Response:
```json
{
  "code": 200,
  "data": {
    "content": [{
      "id": 1, "priority": "high",
      "title": "英伟达发布新一代AI芯片B300",
      "summary": "...", "primarySource": "财联社",
      "credibilityLevel": "authoritative", "credibilityScore": 0.87,
      "sourceCount": 3, "sentiment": "positive",
      "relatedStocks": "NVDA,TSM", "tags": "AI,chip",
      "latestArticleTime": "2026-03-27T09:15:00"
    }],
    "totalElements": 56, "totalPages": 3, "currentPage": 0
  }
}
```

---

## 3. 情报详情页

### GET /api/intelligences/{id} — 情报详情（含个性化分析）

Params: `?userId=1`

Response:
```json
{
  "code": 200,
  "data": {
    "id": 1, "priority": "high", "title": "...", "summary": "...",
    "content": "LLM综合生成的分析正文...",
    "primarySource": "财联社",
    "credibilityLevel": "authoritative", "credibilityScore": 0.87,
    "sourceCount": 3, "sentiment": "positive",
    "relatedStocks": "NVDA,TSM", "tags": "AI,chip",
    "latestArticleTime": "...", "readTime": "3 min",
    "sources": [
      { "articleId": 101, "sourceName": "财联社", "credibilityTag": "权威",
        "title": "英伟达GTC大会：B300芯片正式发布", "sourceUrl": "https://..." },
      { "articleId": 102, "sourceName": "华尔街见闻", "credibilityTag": "可信",
        "title": "英伟达新芯片B300性能跃升", "sourceUrl": "https://..." }
    ],
    "relatedIntelligences": [
      { "id": 5, "title": "台积电3nm产能满载", "primarySource": "36Kr",
        "sourceCount": 2, "credibilityScore": 0.75 }
    ],
    "personalizedAnalysis": {
      "userProfile": {
        "investorType": "growth", "investmentCycle": "medium",
        "focusAreas": ["AI芯片", "云计算"], "holdings": ["NVDA", "AMD"]
      },
      "analysis": "基于多来源交叉验证...",
      "impacts": [
        { "stock": "NVDA", "impact": "中国区收入预计下降15-20%",
          "level": "重大影响", "volatility": "±8%" }
      ],
      "suggestion": "短期观望NVDA，关注国产AI芯片替代标的",
      "risks": ["政策进一步收紧", "国产替代进度不及预期"],
      "userContext": "基于成长型投资者画像"
    }
  }
}
```

personalizedAnalysis: 传 userId 且有画像时返回，否则 null。

---

## 4. 市场洞察页

### GET /api/insight — 洞察聚合数据

Params: `?days=7`

Response:
```json
{
  "code": 200,
  "data": {
    "overview": {
      "sentimentIndex": 68, "sentimentLabel": "Greed",
      "totalIntelligences": 156, "positiveCount": 106, "negativeCount": 25,
      "aiHeatPercent": 92, "stockUp": 30, "stockDown": 15,
      "trendData": [{ "date": "2026-03-21", "value": 55 }, { "date": "2026-03-27", "value": 68 }]
    },
    "sectors": [
      { "rank": 1, "name": "AI", "intelCount": 32, "heatScore": 96 }
    ],
    "events": [
      { "id": 1, "time": "2026-03-27T08:32:00", "title": "...",
        "sourceCount": 3, "impactTag": "major", "sentiment": "negative" }
    ],
    "reports": [
      { "id": 101, "title": "AI芯片行业深度报告", "source": "东方财富研报",
        "sourceUrl": "https://...", "publishedAt": "2026-03-25T10:00:00" }
    ]
  }
}
```

---

## 5. 用户画像设置页

### GET /api/profile/full — 查询完整画像

Params: `?userId=1`

Response:
```json
{
  "code": 200,
  "data": {
    "investorType": "growth",
    "investmentCycle": "medium",
    "focusAreas": ["AI", "chip", "robot"],
    "holdings": "NVDA,TSM"
  }
}
```

### PUT /api/profile — 保存画像

Params: `?userId=1`

Request（focusAreas 支持数组或逗号字符串）:
```json
{
  "investorType": "growth",
  "investmentCycle": "medium",
  "focusAreas": ["AI", "chip", "robot"],
  "holdings": "NVDA,TSM"
}
```

### GET /api/auth/me — 获取用户信息

Params: `?userId=1`

### PUT /api/auth/me — 修改昵称

Params: `?userId=1`  Body: `{"nickname":"NewName"}`

---

## 6. 智能问答（搜索页复用）

### POST /api/query — 语义检索 + LLM 推理

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
    { "id": 101, "title": "...", "sourceName": "财联社", "sourceUrl": "https://..." }
  ]
}
```

---

## 7. 未实现的 C 端接口

| 页面 | 接口 | 说明 |
|------|------|------|
| 搜索页 | POST /api/search | 搜索情报（目前可用 /api/query 替代） |
| 搜索页 | GET /api/search/history | 搜索历史 |
| 搜索页 | GET /api/search/trending | 热门搜索 |
| 个人中心 | GET /api/user/center | 用户统计+会员 |
| 个人中心 | GET /api/user/favorites | 收藏列表 |
| 情报详情 | POST /api/intelligences/{id}/feedback | 认同/不认同 |
| 情报详情 | POST /api/intelligences/{id}/favorite | 收藏/取消 |
| 情报详情 | POST /api/intelligences/{id}/report | 生成完整报告 |

---
---

# 第二部分：控制面板接口（调试/管理）

---

## 1. 数据采集

### POST /api/news/collect — 触发新闻采集

Response:
```json
{
  "collected": 120, "deduplicated": 45, "stored": 75,
  "sources": [{ "name": "财联社", "collected": 15, "deduplicated": 3, "stored": 12 }]
}
```

数据源（24个）：20个RSS + 天聚数据API + NewsAPI + 麦蕊智数 + Tushare

### POST /api/market/collect — 触发行情采集

Response: `{ "collected": 50, "stored": 50 }`

### POST /api/intelligences/cluster — 手动触发事件聚类

Response: `{ "code": 200, "data": { "created": 15, "merged": 3 } }`

---

## 2. 数据查询

### GET /api/news — 原始新闻列表

Params: `?hours=24`

### GET /api/market — 行情数据列表

### GET /api/stats — 系统状态

Response: `{ "totalNews": 1250, "totalMarket": 50, "vectorCacheSize": 1180 }`

### GET /api/logs — 实时日志

Params: `?count=50`

---

## 3. 用户管理

### GET /api/auth/users — 用户列表

Response:
```json
{
  "code": 200,
  "data": [
    { "userId": 1, "phone": "13800138000", "nickname": "Tom",
      "createdAt": "...", "lastLoginAt": "..." }
  ]
}
```

### GET /api/auth/user-detail — 用户详情（含画像）

Params: `?userId=1`

Response:
```json
{
  "code": 200,
  "data": {
    "userId": 1, "phone": "13800138000", "nickname": "Tom",
    "investorType": "growth", "investmentCycle": "medium",
    "focusAreas": "AI,chip", "holdings": "NVDA,TSM"
  }
}
```

### GET /api/profile — 查询画像（简版，逗号分隔）

Params: `?userId=1`

---

## 4. AI 研判（调试）

### POST /api/analysis/generate — 同步生成研判

Params: `?userId=1`  Body: `{"articleId":1}`

### GET /api/analysis/stream — SSE 流式生成

Params: `?userId=1&articleId=1`

### GET /api/analysis/history — 研判历史

Params: `?userId=1`
