# 华尔街之眼 — 前端 API 对接文档

> Base URL: `http://localhost:8080`  
> 版本：v1.0 | 日期：2026-03-27  
> 认证方式：用户名 + 密码（无第三方登录）

---

## 全局约定

### 鉴权

除登录、注册外，所有接口需在 Header 携带：

```
Authorization: Bearer {token}
```

Token 无效或缺失返回 `401`，前端应跳转登录页。

### 统一错误格式

```json
{ "error": "错误描述", "code": 400 }
```

| 状态码 | 说明 |
|-------|------|
| 200 | 成功 |
| 204 | 成功（无返回体） |
| 400 | 参数错误 / 业务异常 |
| 401 | 未登录 / Token 无效 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 一、认证模块

### 1.1 用户注册

```
POST /api/auth/register
```

鉴权：无

**入参 (Body JSON)：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 用户名，长度 3~50 |
| password | String | 是 | 密码，长度 >= 6 |
| nickname | String | 否 | 昵称，长度 <= 50 |

```json
{
  "username": "zhangsan",
  "password": "password123",
  "nickname": "张三"
}
```

**出参：**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID |
| token | String | 登录凭证，后续请求需携带 |
| nickname | String | 昵称 |
| avatarUrl | String | 头像 URL（注册时为 null） |
| hasProfile | Boolean | 是否已设置画像（注册时为 false） |

```json
{
  "userId": 1,
  "token": "MTo3MTcxMjM0NTY3ODk6emhhbmdzYW4=",
  "nickname": "张三",
  "avatarUrl": null,
  "hasProfile": false
}
```

**错误场景：**
- `400` — `"用户名已存在"`
- `400` — `"密码长度不能小于6位"`

---

### 1.2 用户登录

```
POST /api/auth/login
```

鉴权：无

**入参 (Body JSON)：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| phone | String | 是 | 手机号 |
| password | String | 是 | 密码 |

```json
{
  "phone": "13800138000",
  "password": "password123"
}
```

**出参：**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID |
| token | String | 登录凭证 |
| nickname | String | 昵称 |
| avatarUrl | String\|null | 头像 URL |
| hasProfile | Boolean | 是否已设置画像（用于判断是否跳转画像引导页） |

```json
{
  "userId": 1,
  "token": "MTo3MTcxMjM0NTY3ODk6emhhbmdzYW4=",
  "nickname": "张三",
  "avatarUrl": null,
  "hasProfile": true
}
```

**错误场景：**
- `400` — `"手机号或密码错误"`

**前端逻辑：**
- 登录成功后将 `token` 和 `userId` 存入 localStorage
- 若 `hasProfile === false`，跳转用户画像设置页引导填写
- 若 `hasProfile === true`，跳转首页

---

### 1.3 获取当前用户信息

```
GET /api/auth/me
```

鉴权：需要 Token

**入参：** 无（通过 Header 中的 Token 识别用户）

**出参：**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 用户 ID |
| username | String | 用户名 |
| nickname | String | 昵称 |
| avatarUrl | String\|null | 头像 URL |
| phone | String\|null | 手机号 |
| email | String\|null | 邮箱 |
| createdAt | String | 注册时间 ISO 格式 |
| lastLoginAt | String\|null | 最后登录时间 |

```json
{
  "id": 1,
  "username": "zhangsan",
  "nickname": "张三",
  "avatarUrl": null,
  "phone": null,
  "email": null,
  "createdAt": "2026-03-26T10:00:00",
  "lastLoginAt": "2026-03-27T08:30:00"
}
```

---

## 二、情报模块

### 2.1 情报列表（分页 + 筛选）

```
GET /api/intel
```

鉴权：需要 Token

**入参 (Query Params)：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 0 | 页码，从 0 开始 |
| size | int | 否 | 20 | 每页条数 |
| sourceType | String | 否 | - | 来源类型筛选：`rss` / `api` |
| credibilityLevel | String | 否 | - | 置信度等级：`authoritative` / `normal` / `questionable` |
| tag | String | 否 | - | 标签筛选，如 `AI芯片` |
| keyword | String | 否 | - | 搜索关键词（匹配标题和摘要） |
| userId | Long | 否 | - | 传入则按用户画像相关度排序 |
| sort | String | 否 | `collectedAt,desc` | 排序：`collectedAt,desc` / `credibilityScore,desc` |

**示例请求：**
```
GET /api/intel?page=0&size=20&tag=AI芯片&userId=1&sort=collectedAt,desc
```

**出参：**

| 字段 | 类型 | 说明 |
|------|------|------|
| content | Array | 情报列表 |
| totalElements | Long | 总条数 |
| totalPages | int | 总页数 |
| number | int | 当前页码 |

**content 数组中每个元素：**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 情报 ID |
| title | String | 标题 |
| summary | String | 摘要 |
| sourceName | String | 来源名称（如"财联社"） |
| sourceType | String | 来源类型 `rss` / `api` |
| credibilityLevel | String | 置信度等级：`authoritative` / `normal` / `questionable` |
| credibilityScore | Double | 置信度分数 0.0~1.0 |
| sentiment | String | 情感倾向：`positive` / `negative` / `neutral` |
| sentimentScore | Double | 情感分数 |
| relatedStocks | String | 关联股票，逗号分隔，如 `"NVDA,MSFT"` |
| tags | String | 标签，逗号分隔，如 `"AI芯片,数据中心,财报"` |
| publishedAt | String | 发布时间 ISO 格式 |
| collectedAt | String | 采集时间 ISO 格式 |

```json
{
  "content": [
    {
      "id": 1,
      "title": "NVIDIA Q4财报超预期",
      "summary": "数据中心收入同比增长40%...",
      "sourceName": "财联社",
      "sourceType": "rss",
      "credibilityLevel": "authoritative",
      "credibilityScore": 0.95,
      "sentiment": "positive",
      "sentimentScore": 0.85,
      "relatedStocks": "NVDA,MSFT",
      "tags": "AI芯片,数据中心,财报",
      "publishedAt": "2026-03-26T14:28:00",
      "collectedAt": "2026-03-26T14:30:00"
    }
  ],
  "totalElements": 247,
  "totalPages": 13,
  "number": 0
}
```

---

### 2.2 今日重点情报

```
GET /api/intel/highlights
```

鉴权：需要 Token

**入参 (Query Params)：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| userId | Long | 否 | - | 传入则按用户画像匹配度排序 |
| limit | int | 否 | 10 | 返回条数 |

**出参：** 数组，每个元素同 2.1 的 content 元素结构

```json
[
  {
    "id": 1,
    "title": "NVIDIA Q4财报超预期",
    "summary": "...",
    "credibilityLevel": "authoritative",
    "credibilityScore": 0.95,
    "sentiment": "positive",
    "relatedStocks": "NVDA,MSFT",
    "tags": "AI芯片,财报",
    "collectedAt": "2026-03-26T14:30:00"
  }
]
```

---

### 2.3 搜索情报

```
GET /api/intel/search
```

鉴权：需要 Token

**入参 (Query Params)：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| keyword | String | 是 | - | 搜索关键词 |
| limit | int | 否 | 20 | 返回条数上限 |

**出参：** 数组，结构同 2.1 的 content 元素

---

### 2.4 情报详情

```
GET /api/intel/{id}
```

鉴权：需要 Token

**入参 (Path)：**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 情报 ID |

**出参：**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 情报 ID |
| title | String | 标题 |
| content | String | 完整正文（HTML 富文本） |
| summary | String | 摘要 |
| sourceName | String | 来源名称 |
| sourceUrl | String | 原文链接 |
| sourceType | String | 来源类型 |
| credibilityLevel | String | 置信度等级 |
| credibilityScore | Double | 综合置信度 0.0~1.0 |
| sourceCredibility | Double | 来源权威性分数 |
| llmCredibility | Double | LLM 评估分数 |
| freshnessCredibility | Double | 时效性分数 |
| crossCredibility | Double | 交叉验证分数 |
| sentiment | String | 情感倾向 |
| sentimentScore | Double | 情感分数 |
| relatedStocks | String | 关联股票，逗号分隔 |
| tags | String | 标签，逗号分隔 |
| publishedAt | String | 发布时间 |
| collectedAt | String | 采集时间 |

```json
{
  "id": 101,
  "title": "NVIDIA Q4财报超预期，数据中心收入同比增长40%",
  "content": "NVIDIA 于北京时间3月26日发布2026财年Q4财报...",
  "summary": "数据中心收入同比增长40%，超分析师预期",
  "sourceName": "财联社",
  "sourceUrl": "https://www.cls.cn/detail/123456",
  "sourceType": "rss",
  "credibilityLevel": "authoritative",
  "credibilityScore": 0.95,
  "sourceCredibility": 0.95,
  "llmCredibility": 0.88,
  "freshnessCredibility": 1.0,
  "crossCredibility": 0.85,
  "sentiment": "positive",
  "sentimentScore": 0.85,
  "relatedStocks": "NVDA,MSFT,AMD",
  "tags": "AI芯片,数据中心,财报,GPU",
  "publishedAt": "2026-03-26T14:28:00",
  "collectedAt": "2026-03-26T14:30:00"
}
```

**错误场景：**
- `404` — 情报不存在

---

### 2.5 相关情报推荐

```
GET /api/intel/{id}/related
```

鉴权：需要 Token

**入参：**

| 参数 | 位置 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|------|--------|------|
| id | Path | Long | 是 | - | 当前情报 ID |
| limit | Query | int | 否 | 5 | 返回条数 |

**出参：** 数组，结构同 2.1 的 content 元素（不含 content 正文字段）

```json
[
  {
    "id": 205,
    "title": "AMD发布MI400芯片，剑指NVIDIA数据中心市场",
    "summary": "...",
    "credibilityLevel": "normal",
    "credibilityScore": 0.72,
    "sourceName": "36氪",
    "tags": "AI芯片,AMD,数据中心",
    "collectedAt": "2026-03-26T16:00:00"
  }
]
```

---

## 三、AI 研判模块

### 3.1 同步生成 AI 研判

```
POST /api/analysis/generate
```

鉴权：需要 Token

**入参 (Body JSON)：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户 ID |
| articleId | Long | 是 | 情报 ID |

```json
{
  "userId": 1,
  "articleId": 101
}
```

**出参：**

| 字段 | 类型 | 说明 |
|------|------|------|
| analysis | String | 情报研判文本（200字内） |
| impacts | Array | 持仓影响评估列表 |
| impacts[].stock | String | 股票代码 |
| impacts[].impact | String | 影响分析文本 |
| suggestion | String | 操作建议 |
| risks | Array\<String\> | 风险提示列表 |
| userContext | String | 画像上下文描述 |

```json
{
  "analysis": "NVIDIA本季财报表现强劲，数据中心业务持续受益于AI训练和推理需求增长...",
  "impacts": [
    {
      "stock": "NVDA",
      "impact": "财报利好，短期看涨，您的持仓浮盈约104%"
    }
  ],
  "suggestion": "【持有观望】建议继续持有，不追高。若回调至$880附近可考虑小幅加仓。",
  "risks": [
    "当前PE较高，注意估值回撤风险",
    "关注下季度指引是否持续超预期"
  ],
  "userContext": "基于您的画像生成（保守型 / 关注AI芯片 / 持有NVDA）"
}
```

**错误场景：**
- `400` — `"情报不存在"`
- `400` — `"用户不存在"`
- `500` — LLM 调用失败时返回降级文案

---

### 3.2 SSE 流式生成 AI 研判

```
GET /api/analysis/stream
```

鉴权：需要 Token

**入参 (Query Params)：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户 ID |
| articleId | Long | 是 | 情报 ID |

**响应类型：** `text/event-stream`（SSE）

**出参格式：** 逐条推送 `data:` 事件

每条事件的 data 为 JSON：

| 字段 | 类型 | 说明 |
|------|------|------|
| section | String | 当前段落类型：`analysis` / `impacts` / `suggestion` / `risks` |
| content | String | 当前推送的文本片段 |

```
data: {"section":"analysis","content":"NVIDIA本季财报表现强劲，"}

data: {"section":"analysis","content":"数据中心业务持续受益于AI训练和推理需求增长。"}

data: {"section":"impacts","content":"对您持有的NVDA（500股，成本$450）：当前利好..."}

data: {"section":"suggestion","content":"【持有观望】建议继续持有，不追高。"}

data: {"section":"risks","content":"1. 当前PE较高，注意估值回撤风险\n2. 关注下季度指引"}

data: [DONE]
```

**前端对接示例：**
```javascript
const eventSource = new EventSource(
  `/api/analysis/stream?userId=${userId}&articleId=${articleId}`
);

eventSource.onmessage = (event) => {
  if (event.data === '[DONE]') {
    eventSource.close();
    return;
  }
  const chunk = JSON.parse(event.data);
  // chunk.section: 'analysis' | 'impacts' | 'suggestion' | 'risks'
  // chunk.content: 文本片段，追加渲染即可
};

eventSource.onerror = () => {
  eventSource.close();
};
```

**超时：** 60 秒

---

### 3.3 研判历史记录

```
GET /api/analysis/history
```

鉴权：需要 Token

**入参 (Query Params)：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户 ID |

**出参：** 数组

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 记录 ID |
| userId | Long | 用户 ID |
| newsArticleId | Long | 关联情报 ID |
| analysisText | String | 研判全文 |
| investmentStyle | String | 生成时的投资风格 |
| createdAt | String | 生成时间 |

```json
[
  {
    "id": 1,
    "userId": 1,
    "newsArticleId": 101,
    "analysisText": "{\"analysis\":\"...\",\"impacts\":[...],\"suggestion\":\"...\",\"risks\":[...]}",
    "investmentStyle": "CONSERVATIVE",
    "createdAt": "2026-03-26T15:00:00"
  }
]
```

---

## 四、用户画像模块

### 4.1 获取用户画像

```
GET /api/profile/{userId}
```

鉴权：需要 Token

**入参 (Path)：**

| 参数 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID |

**出参：**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID |
| nickname | String | 昵称 |
| avatarUrl | String\|null | 头像 URL |
| stats | Object | 统计数据 |
| stats.holdingCount | int | 持仓股票数 |
| stats.watchCount | int | 关注股票数 |
| stats.intelCount | int | 今日相关情报数 |
| profile | Object | 画像偏好 |
| profile.focusAreas | Array\<String\> | 关注领域 |
| profile.investmentStyle | String | 投资风格：`CONSERVATIVE` / `BALANCED` / `AGGRESSIVE` |
| profile.riskTolerance | String | 风险偏好：`LOW` / `MEDIUM` / `HIGH` |
| profile.investmentHorizon | String | 投资周期：`SHORT_TERM` / `MEDIUM_TERM` / `LONG_TERM` |
| profile.analysisType | String | 分析偏好：`FUNDAMENTAL` / `TECHNICAL` / `MIXED` |
| profile.industryPreferences | Array\<String\> | 行业偏好 |
| profile.hotEvents | Array\<String\> | 关注的热门事件 |
| holdings | Array | 持仓列表 |
| holdings[].id | Long | 持仓记录 ID |
| holdings[].stockCode | String | 股票代码 |
| holdings[].stockName | String | 股票名称 |
| holdings[].quantity | int | 持仓数量 |
| holdings[].avgCost | Double | 持仓成本 |
| holdings[].sector | String | 所属板块 |
| watchStocks | Array | 关注股票列表 |
| watchStocks[].id | Long | 记录 ID |
| watchStocks[].stockCode | String | 股票代码 |
| watchStocks[].stockName | String | 股票名称 |
| watchStocks[].sector | String | 所属板块 |

```json
{
  "userId": 1,
  "nickname": "张三",
  "avatarUrl": null,
  "stats": {
    "holdingCount": 3,
    "watchCount": 8,
    "intelCount": 47
  },
  "profile": {
    "focusAreas": ["AI芯片", "云计算", "大模型"],
    "investmentStyle": "CONSERVATIVE",
    "riskTolerance": "MEDIUM",
    "investmentHorizon": "MEDIUM_TERM",
    "analysisType": "MIXED",
    "industryPreferences": ["半导体", "云服务", "人工智能"],
    "hotEvents": ["NVIDIA财报", "OpenAI融资"]
  },
  "holdings": [
    {
      "id": 1,
      "stockCode": "NVDA",
      "stockName": "NVIDIA",
      "quantity": 500,
      "avgCost": 450.0,
      "sector": "AI芯片"
    },
    {
      "id": 2,
      "stockCode": "MSFT",
      "stockName": "微软",
      "quantity": 200,
      "avgCost": 380.0,
      "sector": "云计算"
    }
  ],
  "watchStocks": [
    {
      "id": 1,
      "stockCode": "TSMC",
      "stockName": "台积电",
      "sector": "半导体"
    },
    {
      "id": 2,
      "stockCode": "AMD",
      "stockName": "AMD",
      "sector": "AI芯片"
    }
  ]
}
```

---

### 4.2 更新用户画像偏好

```
PUT /api/profile/{userId}
```

鉴权：需要 Token

**入参 (Path)：**

| 参数 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID |

**入参 (Body JSON)：** 所有字段均为可选，只传需要更新的字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| focusAreas | Array\<String\> | 否 | 关注领域，如 `["AI芯片", "云计算"]` |
| investmentStyle | String | 否 | `CONSERVATIVE` / `BALANCED` / `AGGRESSIVE` |
| riskTolerance | String | 否 | `LOW` / `MEDIUM` / `HIGH` |
| investmentHorizon | String | 否 | `SHORT_TERM` / `MEDIUM_TERM` / `LONG_TERM` |
| analysisType | String | 否 | `FUNDAMENTAL` / `TECHNICAL` / `MIXED` |
| industryPreferences | Array\<String\> | 否 | 行业偏好 |
| hotEvents | Array\<String\> | 否 | 关注的热门事件 |

```json
{
  "focusAreas": ["AI芯片", "云计算", "大模型"],
  "investmentStyle": "CONSERVATIVE",
  "riskTolerance": "MEDIUM",
  "investmentHorizon": "MEDIUM_TERM",
  "analysisType": "MIXED",
  "industryPreferences": ["半导体", "云服务"],
  "hotEvents": ["NVIDIA财报"]
}
```

**出参：** 更新后的 UserProfile 对象

```json
{
  "id": 1,
  "userId": 1,
  "focusAreas": "AI芯片,云计算,大模型",
  "investmentStyle": "CONSERVATIVE",
  "riskTolerance": "MEDIUM",
  "investmentHorizon": "MEDIUM_TERM",
  "analysisType": "MIXED",
  "industryPreferences": "半导体,云服务",
  "hotEvents": "NVIDIA财报",
  "createdAt": "2026-03-26T10:00:00",
  "updatedAt": "2026-03-27T09:00:00"
}
```

---

### 4.3 获取持仓列表

```
GET /api/profile/{userId}/holdings
```

鉴权：需要 Token

**入参 (Path)：**

| 参数 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID |

**出参：** 数组

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 持仓记录 ID |
| stockCode | String | 股票代码 |
| stockName | String | 股票名称 |
| quantity | int | 持仓数量 |
| avgCost | Double | 持仓成本 |
| sector | String | 所属板块 |

```json
[
  { "id": 1, "stockCode": "NVDA", "stockName": "NVIDIA", "quantity": 500, "avgCost": 450.0, "sector": "AI芯片" },
  { "id": 2, "stockCode": "MSFT", "stockName": "微软", "quantity": 200, "avgCost": 380.0, "sector": "云计算" }
]
```

---

### 4.4 添加持仓

```
POST /api/profile/{userId}/holdings
```

鉴权：需要 Token

**入参 (Path)：**

| 参数 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID |

**入参 (Body JSON)：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| stockCode | String | 是 | 股票代码，如 `"NVDA"` |
| stockName | String | 否 | 股票名称 |
| quantity | int | 否 | 持仓数量 |
| avgCost | Double | 否 | 持仓成本 |
| sector | String | 否 | 所属板块 |

```json
{
  "stockCode": "TSMC",
  "stockName": "台积电",
  "quantity": 300,
  "avgCost": 165.0,
  "sector": "半导体"
}
```

**出参：** 新增的持仓记录

```json
{
  "id": 3,
  "userId": 1,
  "stockCode": "TSMC",
  "stockName": "台积电",
  "quantity": 300,
  "avgCost": 165.0,
  "sector": "半导体",
  "createdAt": "2026-03-27T09:30:00"
}
```

---

### 4.5 更新持仓

```
PUT /api/profile/holdings/{holdingId}
```

鉴权：需要 Token

**入参 (Path)：**

| 参数 | 类型 | 说明 |
|------|------|------|
| holdingId | Long | 持仓记录 ID |

**入参 (Body JSON)：** 同 4.4 的 Body 结构

**出参：** 更新后的持仓记录

---

### 4.6 删除持仓

```
DELETE /api/profile/holdings/{holdingId}
```

鉴权：需要 Token

**入参 (Path)：**

| 参数 | 类型 | 说明 |
|------|------|------|
| holdingId | Long | 持仓记录 ID |

**出参：** 无（HTTP 204）

---

### 4.7 获取关注股票列表

```
GET /api/profile/{userId}/watch-stocks
```

鉴权：需要 Token

**入参 (Path)：**

| 参数 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID |

**出参：** 数组

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 记录 ID |
| stockCode | String | 股票代码 |
| stockName | String | 股票名称 |
| sector | String | 所属板块 |

```json
[
  { "id": 1, "stockCode": "TSMC", "stockName": "台积电", "sector": "半导体" },
  { "id": 2, "stockCode": "AMD", "stockName": "AMD", "sector": "AI芯片" }
]
```

---

### 4.8 添加关注股票

```
POST /api/profile/{userId}/watch-stocks
```

鉴权：需要 Token

**入参 (Path)：**

| 参数 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID |

**入参 (Body JSON)：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| stockCode | String | 是 | 股票代码 |
| stockName | String | 否 | 股票名称 |
| sector | String | 否 | 所属板块 |

```json
{
  "stockCode": "GOOGL",
  "stockName": "谷歌",
  "sector": "云计算"
}
```

**出参：** 新增的关注记录

```json
{
  "id": 3,
  "userId": 1,
  "stockCode": "GOOGL",
  "stockName": "谷歌",
  "sector": "云计算",
  "createdAt": "2026-03-27T10:00:00"
}
```

---

### 4.9 取消关注股票

```
DELETE /api/profile/watch-stocks/{id}
```

鉴权：需要 Token

**入参 (Path)：**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 关注记录 ID |

**出参：** 无（HTTP 204）

---

## 五、情绪指数模块

### 5.1 获取当前情绪指数

```
GET /api/sentiment/current
```

鉴权：需要 Token

**入参：** 无

**出参：**

| 字段 | 类型 | 说明 |
|------|------|------|
| score | int | 情绪指数 0~100 |
| level | String | 情绪等级：`极度恐慌` / `恐慌` / `中性` / `贪婪` / `极度贪婪` |
| changeFromYesterday | int | 较昨日变化 |
| changeFromLastWeek | int | 较上周变化 |
| dimensions | Object | 各维度明细 |
| dimensions.newsSentiment | Double | 新闻情绪得分 |
| dimensions.socialSentiment | Double | 社交情绪得分 |
| dimensions.volatility | Double | 波动率 |
| dimensions.volumeChange | Double | 成交量变化率 |

```json
{
  "score": 68,
  "level": "贪婪",
  "changeFromYesterday": 5,
  "changeFromLastWeek": 12,
  "dimensions": {
    "newsSentiment": 0.62,
    "socialSentiment": 0.58,
    "volatility": 18.5,
    "volumeChange": 0.25
  }
}
```

---

## 六、枚举值速查

### 置信度等级 credibilityLevel

| 值 | 含义 | 前端展示 |
|----|------|---------|
| `authoritative` | 权威 | 🟢 绿色标签 |
| `normal` | 一般 | 🟡 橙色标签 |
| `questionable` | 存疑 | 🔴 红色标签 |

### 情感倾向 sentiment

| 值 | 含义 |
|----|------|
| `positive` | 正面/利好 |
| `negative` | 负面/利空 |
| `neutral` | 中性 |

### 投资风格 investmentStyle

| 值 | 含义 |
|----|------|
| `CONSERVATIVE` | 保守型 |
| `BALANCED` | 均衡型 |
| `AGGRESSIVE` | 激进型 |

### 风险偏好 riskTolerance

| 值 | 含义 |
|----|------|
| `LOW` | 低风险 |
| `MEDIUM` | 中风险 |
| `HIGH` | 高风险 |

### 投资周期 investmentHorizon

| 值 | 含义 |
|----|------|
| `SHORT_TERM` | 短线 |
| `MEDIUM_TERM` | 中线 |
| `LONG_TERM` | 长线 |

### 分析偏好 analysisType

| 值 | 含义 |
|----|------|
| `FUNDAMENTAL` | 基本面 |
| `TECHNICAL` | 技术面 |
| `MIXED` | 综合 |

### 情绪等级对照

| 分数区间 | 等级 | 颜色 |
|---------|------|------|
| 0~25 | 极度恐慌 | #B71C1C 深红 |
| 25~45 | 恐慌 | #E53935 红 |
| 45~55 | 中性 | #9E9E9E 灰 |
| 55~75 | 贪婪 | #43A047 绿 |
| 75~100 | 极度贪婪 | #1B5E20 深绿 |

---

## 七、接口总览

| # | 方法 | 路径 | 说明 | 鉴权 |
|---|------|------|------|------|
| 1 | POST | `/api/auth/register` | 用户注册 | 无 |
| 2 | POST | `/api/auth/login` | 用户登录 | 无 |
| 3 | GET | `/api/auth/me` | 获取当前用户 | Token |
| 4 | GET | `/api/intel` | 情报列表（分页筛选） | Token |
| 5 | GET | `/api/intel/highlights` | 今日重点情报 | Token |
| 6 | GET | `/api/intel/search` | 搜索情报 | Token |
| 7 | GET | `/api/intel/{id}` | 情报详情 | Token |
| 8 | GET | `/api/intel/{id}/related` | 相关情报推荐 | Token |
| 9 | POST | `/api/analysis/generate` | 同步生成 AI 研判 | Token |
| 10 | GET | `/api/analysis/stream` | SSE 流式 AI 研判 | Token |
| 11 | GET | `/api/analysis/history` | 研判历史记录 | Token |
| 12 | GET | `/api/profile/{userId}` | 获取用户画像 | Token |
| 13 | PUT | `/api/profile/{userId}` | 更新画像偏好 | Token |
| 14 | GET | `/api/profile/{userId}/holdings` | 获取持仓列表 | Token |
| 15 | POST | `/api/profile/{userId}/holdings` | 添加持仓 | Token |
| 16 | PUT | `/api/profile/holdings/{id}` | 更新持仓 | Token |
| 17 | DELETE | `/api/profile/holdings/{id}` | 删除持仓 | Token |
| 18 | GET | `/api/profile/{userId}/watch-stocks` | 获取关注股票 | Token |
| 19 | POST | `/api/profile/{userId}/watch-stocks` | 添加关注股票 | Token |
| 20 | DELETE | `/api/profile/watch-stocks/{id}` | 取消关注股票 | Token |
| 21 | GET | `/api/sentiment/current` | 当前情绪指数 | Token |
