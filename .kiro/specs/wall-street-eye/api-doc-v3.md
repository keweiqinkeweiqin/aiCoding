# API V3 - Based on Actual Implementation (2026-03-27)

> Auth: Header `X-User-Id: {userId}` | Base: `http://{host}:8080`

## Auth
| Method | Path | Desc | Status |
|--------|------|------|--------|
| POST | /api/auth/login | Phone login (auto-register) `{"phone":"..."}` | Done |
| GET | /api/auth/me | Current user info | Done |
| PUT | /api/auth/me | Update nickname `{"nickname":"..."}` | Done |

## Profile
| Method | Path | Desc | Status |
|--------|------|------|--------|
| GET | /api/profile | Simple profile (focusAreas, holdings as CSV) | Done |
| PUT | /api/profile | Save simple profile | Done |
| GET | /api/profile/detail | Full profile with holdings array | Done |
| PUT | /api/profile/save | Save with focusAreas as array | Done |
| POST | /api/profile/holdings | Add holding `{"stockCode":"NVDA"}` | Done |
| DELETE | /api/profile/holdings/{code} | Remove holding | Done |
| GET | /api/profile/focus-options | Available focus area tags | Done |

## Intelligence (Core)
| Method | Path | Desc | Status |
|--------|------|------|--------|
| GET | /api/intelligences | List (personalized sort by profile) `?hours=24&page=0&size=20` | Done |
| GET | /api/intelligences/{id} | Detail (content + sources + readTime) | Done |
| GET | /api/intelligences/{id}/related | Related intelligences `?limit=5` | Done |
| POST | /api/intelligences/cluster | Manual clustering (debug) | Done |

## AI Analysis
| Method | Path | Desc | Status |
|--------|------|------|--------|
| POST | /api/analysis/generate | Sync AI analysis `{"articleId":1}` | Done |
| GET | /api/analysis/stream | SSE streaming `?articleId=1` | Done |
| GET | /api/analysis/history | Analysis history | Done |

## Data Collection
| Method | Path | Desc | Status |
|--------|------|------|--------|
| POST | /api/news/collect | Trigger news collection | Done |
| GET | /api/news | Raw news list `?hours=24` | Done |
| POST | /api/market/collect | Trigger market data collection | Done |
| GET | /api/market | Market data list | Done |

## Query & System
| Method | Path | Desc | Status |
|--------|------|------|--------|
| POST | /api/query | Smart Q&A `{"question":"..."}` | Done |
| GET | /api/stats | System stats | Done |
| GET | /api/logs | Real-time logs `?count=50` | Done |

## Not Yet Implemented
| Module | Path | UI Page |
|--------|------|---------|
| Home aggregate | GET /api/home | Homepage greeting + quick actions |
| Market insight | /api/insight/* | Market insight page |
| Search | /api/search/* | Search page |
| Favorites | /api/user/favorites | Personal center |
| Read history | /api/user/history | Personal center |
