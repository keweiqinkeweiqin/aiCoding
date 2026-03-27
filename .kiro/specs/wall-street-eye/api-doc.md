# 华尔街之眼 — 前后端 API 对接文档

> 基于交互稿页面模块划分，对应后端已有接口 + 需新增接口

---

## 〇、核心概念：情报 vs 新闻

本系统中「情报」和「新闻」是两个不同层级的概念：

| 概念 | 说明 | 对应实体 |
|------|------|---------|
| 新闻 (NewsArticle) | 单条原始新闻，来自某一个信息源 | `news_articles` 表（已有） |
| 情报 (Intelligence) | 多条报道同一事件的新闻聚合而成 | `intelligences` 表（🆕 新增） |

关系：`Intelligence 1 : N NewsArticle`

- 采集到的每条新闻仍然存入 `news_articles`
- 采集后通过 SimHash 标题聚类（汉明距离 ≤ 10），将报道同一事件的新闻归入同一条情报
- 首页「情报列表」查的是 `Intelligence`，不是直接查 `NewsArticle`
- 情报详情页展示聚合后的分析内容 + 多个信息来源（每个来源对应一条 NewsArticle）
- 如果某条新闻没有匹配到其他来源，则单独生成一条情报（1:1）

---

## 一、通用约定

| 项目 | 说明 |
|------|------|
| Base URL | `http://localhost:8080/api` |
| Content-Type | `application/json` |
| 时间格式 | `yyyy-MM-dd'T'HH:mm:ss` (ISO 8601) |
| 分页参数 | `page` (从0开始), `size` (默认20) |
| 认证方式 | Bearer Token (JWT)，Header: `Authorization: Bearer {token}` |

通用错误响应：
```json
{
  "code": 400,
  "message": "错误描述",
  "data": null
}
```

---

## 二、模块一：用户认证（登录/注册页）

> 交互稿页面：华尔街之眼-登录注册页、华尔街之眼-注册页
> 状态：🆕 需新增

### 2.1 用户注册

```
POST /api/auth/register
```

Request:
```json
{
  "email": "user@example.com",
  "nickname": "用户昵称",
  "password": "密码(8-20位)"
}
```

Response (200):
```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "nickname": "用户昵称",
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

### 2.2 用户登录

```
POST /api/auth/login
```

Request:
```json
{
  "email": "user@example.com",
  "password": "密码",
  "rememberMe": false
}
```

Response (200):
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "nickname": "用户昵称",
    "memberLevel": "pro",
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 86400
  }
}
```

### 2.3 第三方登录（微信/QQ/手机号）

```
POST /api/auth/oauth
```

Request:
```json
{
  "provider": "wechat",
  "code": "授权码"
}
```

### 2.4 退出登录

```
POST /api/auth/logout
```

---

## 三、模块二：首页

> 交互稿页面：华尔街之眼-首页
> 包含：用户问候栏、快速操作区、市场概览、情报列表

### 3.1 获取首页聚合数据

```
GET /api/home
```

Response (200):
```json
{
  "code": 200,
  "data": {
    "greeting": {
      "nickname": "用户昵称",
      "profileTag": "AI科技投资者",
      "marketStatus": "📈 今日AI板块整体上涨2.3%"
    },
    "quickActions": [
      { "id": "collect", "name": "采集新闻", "icon": "refresh" },
      { "id": "query", "name": "智能问答", "icon": "chat" },
      { "id": "market", "name": "行情数据", "icon": "chart" },
      { "id": "profile", "name": "我的画像", "icon": "user" }
    ]
  }
}
```

### 3.2 获取市场概览（已有，需扩展）

> 对应交互稿「市场概览卡片」：显示市场情绪指数、涨跌比、趋势图

```
GET /api/market/overview
```

Response (200):
```json
{
  "code": 200,
  "data": {
    "sentimentIndex": 72,
    "sentimentLabel": "偏多",
    "changeRatio": "+2.3%",
    "changeDirection": "up",
    "trendData": [
      { "time": "09:30", "value": 65 },
      { "time": "10:00", "value": 68 },
      { "time": "10:30", "value": 72 }
    ],
    "updatedAt": "2026-03-27T10:30:00"
  }
}
```

### 3.3 获取情报列表（🆕 需新增）

> 对应交互稿「情报列表」：查询的是聚合后的情报（Intelligence），不是原始新闻
> 每条情报卡片含：优先级标签、主来源、时间、标题、摘要、影响分析、置信度、来源数

```
GET /api/intelligences?hours=24&page=0&size=20
```

Response (200):
```json
{
  "code": 200,
  "data": {
    "content": [
      {
        "id": 1,
        "priority": "high",
        "title": "美国商务部宣布最新AI芯片出口管制新政",
        "summary": "新规将进一步限制高性能AI芯片对中国市场的出口，涉及H100、A100等多款NVIDIA主力产品...",
        "primarySource": "SEC Filing",
        "credibilityLevel": "authoritative",
        "credibilityScore": 0.95,
        "sourceCount": 3,
        "sentiment": "negative",
        "sentimentScore": 0.15,
        "relatedStocks": "NVDA,AMD,TSM",
        "tags": "AI,芯片,出口管制",
        "impactBrief": {
          "icon": "💡",
          "title": "对你的影响",
          "content": "你持有的NVIDIA股票预计受此影响短期波动幅度约±8%，建议关注国内AI芯片替代标的。"
        },
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

说明：
- `sourceCount` = 该情报聚合了多少条不同来源的新闻
- `primarySource` = 置信度最高的那条新闻的来源名
- `impactBrief` = 列表页的简化影响分析（基于用户持仓模板匹配，不调 LLM）
- `latestArticleTime` = 聚合的新闻中最新的一条的时间

---

## 四、模块三：情报详情页

> 交互稿页面：华尔街之眼-情报详情页
> 包含：文章头部、信息来源、正文、个性化影响分析、操作建议、相关情报

### 4.1 获取情报详情（🆕 需新增）

> 情报详情 = 聚合后的分析内容 + 多个信息来源（每个来源是一条 NewsArticle）

```
GET /api/intelligences/{id}
```

Response (200):
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "priority": "high",
    "credibilityLevel": "authoritative",
    "credibilityScore": 0.95,
    "sourceCount": 3,
    "title": "美国商务部宣布最新AI芯片出口管制新政",
    "latestArticleTime": "2026-03-27T09:15:00",
    "readTime": "约3分钟",
    "primarySource": "SEC Filing",
    "content": "LLM基于多条新闻综合生成的分析正文...",
    "sources": [
      {
        "articleId": 101,
        "sourceName": "美国商务部官方公告",
        "credibilityTag": "权威",
        "title": "Bureau of Industry and Security: New AI Chip Export Controls",
        "sourceUrl": "https://..."
      },
      {
        "articleId": 102,
        "sourceName": "NVIDIA SEC 8-K 文件",
        "credibilityTag": "权威",
        "title": "NVIDIA Corporation Form 8-K: Export Regulation Impact",
        "sourceUrl": "https://..."
      },
      {
        "articleId": 103,
        "sourceName": "路透社独家报道",
        "credibilityTag": "一般",
        "title": "Exclusive: US tightens AI chip export curbs targeting China",
        "sourceUrl": "https://..."
      }
    ],
    "personalizedImpact": {
      "userProfile": {
        "investorType": [
          { "label": "关注领域", "tags": ["AI", "芯片", "机器人"] },
          { "label": "投资风格", "tags": ["成长型", "中长线"] },
          { "label": "风险偏好", "tags": ["中高风险"] }
        ]
      },
      "impacts": [
        {
          "level": "高影响",
          "title": "对您持仓的直接影响",
          "details": [
            "您持有的NVDA可能受此影响短期波动±8%",
            "数据中心业务中国区收入可能下降15-20%",
            "建议关注国内AI芯片替代标的"
          ]
        },
        {
          "level": "中影响",
          "title": "产业链传导效应",
          "details": [
            "台积电3nm产能利用率将进一步提升",
            "国内AI芯片替代逻辑可能受到压制"
          ]
        }
      ]
    },
    "actionSuggestion": {
      "summary": "建议关注，短期观望",
      "actions": [
        "1. 关注NVDA财报后的量价表现",
        "2. 可适当布局AI算力ETF分散风险",
        "3. 留意国内芯片板块的联动反应"
      ]
    },
    "relatedIntelligences": [
      {
        "id": 5,
        "title": "台积电3nm产能满载，AI芯片需求旺盛",
        "summary": "台积电最新财报显示...",
        "primarySource": "36Kr",
        "sourceCount": 2,
        "latestArticleTime": "2026-03-26T14:00:00",
        "thumbnailUrl": null
      }
    ]
  }
}
```

说明：
- `sources` 数组 = 聚合到这条情报下的所有原始新闻，每条可「查看原文」
- `content` = LLM 基于多条新闻综合生成的分析文章（不是某一条新闻的原文）
- `relatedIntelligences` = 语义相似的其他情报（不是新闻）
- `personalizedImpact` = 基于用户画像的个性化影响分析（调 LLM 生成）

### 4.2 情报反馈（认同/不认同）（🆕 需新增）

> 对应交互稿底部「认同/不认同」按钮

```
POST /api/intelligences/{id}/feedback
```

Request:
```json
{
  "type": "agree"
}
```
type 枚举: `agree` | `disagree`

### 4.3 收藏/取消收藏（🆕 需新增）

```
POST /api/intelligences/{id}/favorite
```

Request:
```json
{
  "action": "add"
}
```
action 枚举: `add` | `remove`

### 4.4 生成完整报告（🆕 需新增）

> 对应交互稿「操作建议」区域的「生成完整报告」按钮

```
POST /api/intelligences/{id}/report
```

Response (200):
```json
{
  "code": 200,
  "data": {
    "reportId": "rpt_20260327_001",
    "downloadUrl": "/api/reports/rpt_20260327_001/download",
    "generatedAt": "2026-03-27T10:35:00"
  }
}
```

---

## 五、模块四：市场洞察页

> 交互稿页面：华尔街之眼-市场洞察页
> 包含：市场概览（扩展版）、行业热度榜、热门事件时间轴、热门研报

### 5.1 获取市场洞察聚合数据

```
GET /api/insight/overview?period=today
```

period 枚举: `today` | `week` | `month`

Response (200):
```json
{
  "code": 200,
  "data": {
    "marketOverview": {
      "sentimentIndex": 72,
      "sentimentLabel": "偏多",
      "changeRatio": "+2.3%",
      "changeDirection": "up",
      "volumeIndex": 85,
      "volumeLabel": "放量",
      "trendData": [
        { "time": "09:30", "value": 65 },
        { "time": "10:00", "value": 68 }
      ]
    }
  }
}
```

### 5.2 获取行业热度榜（🆕 需新增）

> 对应交互稿「行业热度榜模块」：排名、行业名、新闻数、涨跌幅、热度值

```
GET /api/insight/sectors?limit=5
```

Response (200):
```json
{
  "code": 200,
  "data": [
    {
      "rank": 1,
      "sectorName": "AI大模型",
      "newsCount": 28,
      "changePercent": "+3.2%",
      "heatScore": 96
    },
    {
      "rank": 2,
      "sectorName": "半导体",
      "newsCount": 15,
      "changePercent": "+1.8%",
      "heatScore": 82
    }
  ]
}
```

### 5.3 获取热门事件时间轴（🆕 需新增）

> 对应交互稿「热门事件模块」：时间点、事件标题、摘要、影响标签

```
GET /api/insight/events?limit=10
```

Response (200):
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "time": "10:30",
      "date": "2026-03-27",
      "title": "英伟达GTC大会开幕",
      "summary": "黄仁勋发布B300芯片，AI算力再升级",
      "impactTag": "高影响",
      "relatedSector": "AI大模型,半导体"
    }
  ]
}
```

### 5.4 获取热门研报列表（🆕 需新增）

> 对应交互稿「热门研报模块」：标题、来源、页数、发布时间、热度/新标签

```
GET /api/insight/reports?page=0&size=10
```

Response (200):
```json
{
  "code": 200,
  "data": {
    "content": [
      {
        "id": 1,
        "title": "2026年AI产业链深度研究：算力需求持续爆发",
        "source": "东方财富研报",
        "pages": 42,
        "publishedAt": "2026-03-25",
        "tag": "hot",
        "downloadUrl": "/api/reports/1/download"
      }
    ],
    "totalElements": 25
  }
}
```

---

## 六、模块五：搜索页

> 交互稿页面：华尔街之眼-搜索页面
> 包含：搜索框、搜索历史、热门搜索、搜索结果列表

### 6.1 搜索情报（🆕 需新增）

> 对应交互稿搜索结果列表，搜索结果返回的是情报（Intelligence），不是原始新闻

```
POST /api/search
```

Request:
```json
{
  "keyword": "英伟达芯片",
  "page": 0,
  "size": 20,
  "sortBy": "relevance"
}
```
sortBy 枚举: `relevance` | `time` | `credibility`

Response (200):
```json
{
  "code": 200,
  "data": {
    "content": [
      {
        "id": 1,
        "priority": "high",
        "primarySource": "SEC Filing",
        "latestArticleTime": "2026-03-27T09:15:00",
        "title": "美国商务部宣布最新AI芯片出口管制新政",
        "summary": "新规将进一步限制高性能AI芯片对中国市场的出口...",
        "credibilityLevel": "authoritative",
        "credibilityScore": 0.95,
        "sourceCount": 3
      }
    ],
    "totalElements": 42
  }
}
```

### 6.2 获取搜索历史（🆕 需新增）

```
GET /api/search/history?limit=10
```

Response (200):
```json
{
  "code": 200,
  "data": ["英伟达芯片", "AI大模型融资", "半导体出口管制", "OpenAI估值", "机器人概念股"]
}
```

### 6.3 清除搜索历史

```
DELETE /api/search/history
```

### 6.4 获取热门搜索（🆕 需新增）

```
GET /api/search/trending?limit=5
```

Response (200):
```json
{
  "code": 200,
  "data": [
    { "rank": 1, "keyword": "英伟达B300芯片发布", "hot": true },
    { "rank": 2, "keyword": "DeepSeek开源新模型", "hot": true },
    { "rank": 3, "keyword": "半导体出口新规" },
    { "rank": 4, "keyword": "特斯拉机器人量产" },
    { "rank": 5, "keyword": "AI医疗审批加速" }
  ]
}
```

---

## 七、模块六：用户画像设置页

> 交互稿页面：华尔街之眼-用户画像设置页
> 包含：投资者类型、投资周期、关注领域、我的持仓

### 7.1 获取用户画像（🆕 需新增）

```
GET /api/profile
```

Response (200):
```json
{
  "code": 200,
  "data": {
    "investorType": "growth",
    "investmentCycle": "medium",
    "focusAreas": ["AI", "芯片", "机器人"],
    "holdings": [
      { "stockCode": "NVDA", "stockName": "英伟达", "sector": "半导体" },
      { "stockCode": "TSM", "stockName": "台积电", "sector": "半导体" }
    ]
  }
}
```

### 7.2 保存用户画像（🆕 需新增）

```
PUT /api/profile
```

Request:
```json
{
  "investorType": "growth",
  "investmentCycle": "medium",
  "focusAreas": ["AI", "芯片", "机器人", "新能源车"]
}
```

investorType 枚举: `conservative`(保守型) | `balanced`(均衡型) | `growth`(成长型)
investmentCycle 枚举: `short`(短线<1月) | `medium`(中线1-6月) | `long`(长线>6月)

### 7.3 管理持仓（🆕 需新增）

添加持仓:
```
POST /api/profile/holdings
```
```json
{ "stockCode": "AAPL", "stockName": "苹果" }
```

删除持仓:
```
DELETE /api/profile/holdings/{stockCode}
```

### 7.4 获取可选关注领域标签（🆕 需新增）

```
GET /api/profile/focus-options
```

Response (200):
```json
{
  "code": 200,
  "data": [
    { "id": "ai", "name": "AI", "selected": true },
    { "id": "chip", "name": "芯片", "selected": true },
    { "id": "robot", "name": "机器人", "selected": true },
    { "id": "ev", "name": "新能源车", "selected": false },
    { "id": "biotech", "name": "生物科技", "selected": false },
    { "id": "fintech", "name": "金融科技", "selected": false },
    { "id": "cloud", "name": "云计算", "selected": false },
    { "id": "security", "name": "网络安全", "selected": false },
    { "id": "metaverse", "name": "元宇宙", "selected": false },
    { "id": "quantum", "name": "量子计算", "selected": false }
  ]
}
```

---

## 八、模块七：个人中心页

> 交互稿页面：华尔街之眼-个人中心页
> 包含：用户信息、数据统计、会员卡片、功能菜单

### 8.1 获取个人中心数据（🆕 需新增）

```
GET /api/user/center
```

Response (200):
```json
{
  "code": 200,
  "data": {
    "userInfo": {
      "nickname": "用户昵称",
      "avatar": "https://...",
      "memberLevel": "pro",
      "memberExpireAt": "2027-03-27"
    },
    "stats": {
      "readCount": 128,
      "favoriteCount": 23,
      "reportCount": 5
    },
    "membership": {
      "level": "pro",
      "name": "专业版会员",
      "progress": 0.65,
      "progressText": "65/100 积分升级",
      "benefits": ["无限智能问答", "个性化影响分析", "研报下载"]
    }
  }
}
```

### 8.2 获取收藏列表

```
GET /api/user/favorites?page=0&size=20
```

### 8.3 获取浏览历史

```
GET /api/user/history?page=0&size=20
```

---

## 九、模块八：已有接口（保持兼容）

> 以下为项目中已实现的接口，前端可直接对接

### 9.1 手动触发新闻采集 ✅ 已有

```
POST /api/news/collect
```

Response:
```json
{
  "collected": 120,
  "deduplicated": 45,
  "stored": 75,
  "sources": [
    { "name": "财联社", "collected": 15, "deduplicated": 3, "stored": 12 }
  ]
}
```

### 9.2 手动触发行情采集 ✅ 已有

```
POST /api/market/collect
```

Response:
```json
{ "collected": 50, "stored": 50 }
```

### 9.3 获取行情列表 ✅ 已有

```
GET /api/market
```

### 9.4 智能问答 ✅ 已有

```
POST /api/query
```

Request:
```json
{ "question": "最近AI芯片领域有什么重大新闻？" }
```

Response:
```json
{
  "answer": "AI分析结果...",
  "matchedCount": 15,
  "relatedNews": [
    { "id": 1, "title": "...", "sourceName": "...", "credibilityLevel": "...", "sourceUrl": "..." }
  ]
}
```

### 9.5 系统状态 ✅ 已有

```
GET /api/stats
```

### 9.6 实时日志 ✅ 已有

```
GET /api/logs?count=50
```

---

## 十、接口状态汇总

| 模块 | 接口 | 路径 | 状态 |
|------|------|------|------|
| 认证 | 注册/登录/OAuth/退出 | `/api/auth/*` | 🆕 需新增 |
| 首页 | 首页聚合数据 | `GET /api/home` | 🆕 需新增 |
| 首页 | 市场概览 | `GET /api/market/overview` | 🆕 需新增 |
| 首页 | 情报列表 | `GET /api/intelligences` | 🆕 需新增 |
| 详情 | 情报详情 | `GET /api/intelligences/{id}` | 🆕 需新增 |
| 详情 | 反馈/收藏/报告 | `POST /api/intelligences/{id}/*` | 🆕 需新增 |
| 洞察 | 市场洞察聚合 | `GET /api/insight/overview` | 🆕 需新增 |
| 洞察 | 行业热度榜 | `GET /api/insight/sectors` | 🆕 需新增 |
| 洞察 | 热门事件时间轴 | `GET /api/insight/events` | 🆕 需新增 |
| 洞察 | 热门研报 | `GET /api/insight/reports` | 🆕 需新增 |
| 搜索 | 搜索/历史/热门 | `/api/search/*` | 🆕 需新增 |
| 画像 | 用户画像CRUD | `/api/profile/*` | 🆕 需新增 |
| 个人 | 个人中心/收藏/历史 | `/api/user/*` | 🆕 需新增 |
| 采集 | 新闻采集 | `POST /api/news/collect` | ✅ 已有（需扩展：采集后触发聚类） |
| 采集 | 行情采集 | `POST /api/market/collect` | ✅ 已有 |
| 问答 | 智能问答 | `POST /api/query` | ✅ 已有 |
| 系统 | 状态/日志 | `GET /api/stats`, `GET /api/logs` | ✅ 已有 |
