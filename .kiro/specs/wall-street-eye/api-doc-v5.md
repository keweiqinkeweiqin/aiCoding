# 华尔街之眼 — API 对接文档 V5

> Base URL: `http://{host}:8080`
> 用户标识: Query param `userId={id}`（登录后获取）
> 数组字段统一返回 JSON 数组格式

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

Params: `?userId=1`（可选，不传或传 0 则为访客）

Response:
```json
{
  "code": 200,
  "data": {
    "greeting": {
      "nickname": "Tom",
      "profileTag": "成长型 | AI",
      "marketStatus": "市场情绪偏多，已追踪56条情报"
    },
    "quickActions": [
      { "id": "collect", "name": "采集新闻", "icon": "refresh" },
      { "id": "query", "name": "智能问答", "icon": "chat" },
      { "id": "market", "name": "行情数据", "icon": "chart" },
      { "id": "profile", "name": "我的画像", "icon": "user" }
    ],
    "marketOverview": {
      "sentimentIndex": 72,
      "sentimentLabel": "偏多",
      "totalIntelligences": 56,
      "positiveCount": 40,
      "negativeCount": 8,
      "neutralCount": 8,
      "stockUp": 30,
      "stockDown": 15,
      "stockFlat": 5,
      "avgChangePercent": 1.23,
      "hotTags": [{ "tag": "AI", "count": 28 }]
    }
  }
}
```

说明：
- 未登录时 nickname 为"访客"，profileTag 为空
- sentimentLabel 取值：偏多 / 中性 / 偏空
- marketStatus 取值：市场情绪偏多/偏空/中性 + 情报数量

---

## 3. 情报列表

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

## 4. 情报详情页

### GET /api/intelligences/{id} — 情报详情（含个性化分析）

Params: `?userId=1`（传 userId 触发个性化分析，不传则 personalizedAnalysis 为 null）

Response:
```json
{
  "code": 200,
  "data": {
    "id": 1, "priority": "high", "title": "...", "summary": "...",
    "content": "LLM综合生成的分析正文...",
    "primarySource": "财联社",
    "credibilityLevel": "authoritative", "credibilityScore": 0.87,
    "sourceCount": 3, "sentiment": "positive", "sentimentScore": 0.8,
    "relatedStocks": "NVDA,TSM", "tags": "AI,chip",
    "latestArticleTime": "2026-03-27T09:15:00", "readTime": "3 min",
    "sources": [
      { "articleId": 101, "sourceName": "财联社", "credibilityTag": "权威",
        "title": "英伟达GTC大会：B300芯片正式发布", "sourceUrl": "https://..." },
      { "articleId": 102, "sourceName": "华尔街见闻", "credibilityTag": "可信",
        "title": "英伟达新芯片B300性能跃升", "sourceUrl": "https://..." }
    ],
    "relatedIntelligences": [
      { "id": 5, "title": "台积电3nm产能满载", "summary": "...",
        "primarySource": "36Kr", "sourceCount": 2, "credibilityScore": 0.75,
        "latestArticleTime": "2026-03-26T14:00:00" }
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

说明：
- personalizedAnalysis：传 userId 且用户存在时返回，否则 null
- LLM 分析有 15 秒超时保护，超时时 analysis 返回"分析超时，请稍后重试"
- 已有缓存（1 小时内）时直接返回缓存，不重复调用 LLM

---

## 5. 市场洞察页

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

## 6. 用户画像

### GET /api/profile — 查询画像（含持仓）

Params: `?userId=1`

Response:
```json
{
  "code": 200,
  "data": {
    "investorType": "growth",
    "investmentCycle": "medium",
    "focusAreas": ["AI", "chip", "robot"],
    "holdings": [
      { "id": 1, "stockCode": "NVDA", "stockName": "英伟达", "sector": "semiconductor" },
      { "id": 2, "stockCode": "TSM", "stockName": "台积电", "sector": "semiconductor" }
    ]
  }
}
```

说明：
- focusAreas：字符串数组
- holdings：对象数组，包含 id/stockCode/stockName/sector
- userId=0 或不传时返回空对象 `{}`
- `/api/profile/full` 为别名，行为完全一致

### PUT /api/profile — 保存画像（含持仓全量替换）

Params: `?userId=1`

Request:
```json
{
  "investorType": "growth",
  "investmentCycle": "medium",
  "focusAreas": ["AI", "chip", "robot"],
  "holdings": [
    { "stockCode": "NVDA", "stockName": "英伟达", "sector": "semiconductor" },
    { "stockCode": "TSM", "stockName": "台积电", "sector": "semiconductor" }
  ]
}
```

Response:
```json
{ "code": 200, "message": "saved" }
```

说明：
- focusAreas：接受字符串数组 `["AI","chip"]` 或逗号分隔字符串 `"AI,chip"`
- holdings：接受对象数组，全量替换（先删后增）；也兼容旧的逗号分隔字符串格式
- 所有字段均为可选，只更新传入的字段

### GET /api/auth/me — 获取用户信息

Params: `?userId=1`

Response:
```json
{
  "code": 200,
  "data": { "userId": 1, "phone": "13800138000", "nickname": "Tom" }
}
```

### PUT /api/auth/me — 修改昵称

Params: `?userId=1`

Request:
```json
{ "nickname": "新昵称" }
```

Response:
```json
{ "code": 200, "message": "updated" }
```

---

## 7. 智能问答

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

## 8. 未实现的 C 端接口

| 页面 | 接口 | 说明 |
|------|------|------|
| 搜索页 | POST /api/search | 搜索情报（目前可用 /api/query 替代） |
| 搜索页 | GET /api/search/history | 搜索历史 |
| 搜索页 | GET /api/search/trending | 热门搜索 |
| 个人中心 | GET /api/user/center | 用户统计+会员 |
| 个人中心 | GET /api/user/favorites | 收藏列表 |
| 情报详情 | POST /api/intelligences/{id}/feedback | 认同/不认同 |
| 情报详情 | POST /api/intelligences/{id}/favorite | 收藏/取消 |

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

说明：定时任务已启用，新闻每 15 分钟自动采集，行情每 5 分钟自动采集。

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
      "createdAt": "2026-03-20T10:00:00", "lastLoginAt": "2026-03-28T08:30:00" }
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
    "createdAt": "...", "lastLoginAt": "...",
    "investorType": "growth", "investmentCycle": "medium",
    "focusAreas": "AI,chip", "holdings": "NVDA,TSM"
  }
}
```

---

## 4. 持仓管理（单条操作）

### GET /api/profile/holdings — 查询持仓列表

Params: `?userId=1`

Response:
```json
{
  "code": 200,
  "data": [
    { "id": 1, "stockCode": "NVDA", "stockName": "英伟达", "sector": "semiconductor" },
    { "id": 2, "stockCode": "TSM", "stockName": "台积电", "sector": "semiconductor" }
  ]
}
```

### POST /api/profile/holdings — 添加单条持仓

Params: `?userId=1`

Request:
```json
{ "stockCode": "NVDA", "stockName": "英伟达", "sector": "semiconductor" }
```

Response:
```json
{ "code": 200, "message": "added", "id": 3 }
```

### DELETE /api/profile/holdings/{id} — 删除单条持仓

Response:
```json
{ "code": 200, "message": "deleted" }
```

---

## 5. AI 研判（调试）

### POST /api/analysis/generate — 同步生成研判

Params: `?userId=1`

Request:
```json
{ "articleId": 1 }
```

### GET /api/analysis/stream — SSE 流式生成

Params: `?userId=1&articleId=1`

### GET /api/analysis/history — 研判历史

Params: `?userId=1`
