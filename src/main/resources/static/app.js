// ============================================================
// 华尔街之眼 — 调试面板 (app.js) — v2 rewrite
// ============================================================

const API = '';
let currentUserId = 1;

// === Utility ===

function esc(s) {
  if (!s) return '';
  const d = document.createElement('div');
  d.textContent = s;
  return d.innerHTML;
}

function timeAgo(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  const now = new Date();
  const diff = Math.floor((now - d) / 1000);
  if (diff < 60) return '刚刚';
  if (diff < 3600) return Math.floor(diff / 60) + '分钟前';
  if (diff < 86400) return Math.floor(diff / 3600) + '小时前';
  return Math.floor(diff / 86400) + '天前';
}

function scoreColor(score) {
  if (score >= 0.8) return '#22c55e';
  if (score >= 0.5) return '#f59e0b';
  return '#ef4444';
}

function priorityBadge(p) {
  if (p === 'high') return '<span class="badge badge-high">🔴 重要</span>';
  if (p === 'medium') return '<span class="badge badge-medium">🟡 一般</span>';
  return '<span class="badge badge-low">⚪ 低</span>';
}

function sentimentBadge(s) {
  if (s === 'positive') return '<span class="badge badge-positive">📈 积极</span>';
  if (s === 'negative') return '<span class="badge badge-negative">📉 消极</span>';
  return '<span class="badge badge-neutral">➖ 中性</span>';
}

function credibilityTag(tag) {
  const colors = { '权威': '#065f46', '可信': '#92400e', '存疑': '#991b1b' };
  return `<span style="font-size:11px;padding:2px 8px;border-radius:10px;background:${colors[tag] || '#991b1b'};color:#fff">${esc(tag)}</span>`;
}

function renderTags(tagsStr) {
  if (!tagsStr) return '';
  return tagsStr.split(',').map(t => `<span class="tag">${esc(t.trim())}</span>`).join('');
}

// ============================================================
// Tab Switching
// ============================================================

const TAB_MAP = {
  collect: '采集', news: '新闻', intel: '情报', analysis: '研判',
  profile: '画像', query: '问答', market: '行情', logs: '日志'
};

function switchTab(name) {
  document.querySelectorAll('.tab').forEach(t => {
    t.classList.toggle('active', t.textContent.includes(TAB_MAP[name]));
  });
  document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
  document.getElementById('panel-' + name).classList.add('active');

  if (name === 'news') loadNews();
  if (name === 'intel') loadIntelligences();
  if (name === 'analysis') loadAnalysisHistory();
  if (name === 'profile') { loadUserList(); loadProfile(); }
  if (name === 'market') loadMarket();
  if (name === 'logs') { startLogPolling(); loadLogs(); }
  else stopLogPolling();
}

// ============================================================
// Stats
// ============================================================

async function loadStats() {
  try {
    const r = await fetch(API + '/api/stats');
    const d = await r.json();
    document.getElementById('statNews').textContent = d.totalNews ?? '-';
    document.getElementById('statVector').textContent = d.vectorCacheSize ?? '-';
    document.getElementById('statMarket').textContent = d.totalMarket ?? '-';
    try {
      const r2 = await fetch(API + '/api/intelligences?userId=' + currentUserId + '&hours=720&page=0&size=1&scene=admin');
      const d2 = await r2.json();
      document.getElementById('statIntel').textContent = d2.data?.totalElements ?? '-';
    } catch (_) {
      document.getElementById('statIntel').textContent = '-';
    }
  } catch (e) { console.error('Stats load failed', e); }
}

// ============================================================
// 1. 数据采集 Panel
// ============================================================

function initCollectPanel() {
  document.getElementById('panel-collect').innerHTML = `
    <div class="section-title">📡 数据采集控制</div>
    <div style="display:flex;gap:10px;margin-bottom:16px;flex-wrap:wrap">
      <button onclick="collectNews()" class="btn btn-primary" id="btnCollectNews">📡 采集新闻</button>
      <button onclick="collectMarket()" class="btn btn-success" id="btnCollectMarket">📈 采集行情</button>
      <button onclick="document.getElementById('collectLog').textContent=''" class="btn btn-ghost">🗑️ 清空日志</button>
    </div>
    <div id="collectLog" class="log-console" style="min-height:200px;max-height:500px"></div>
  `;
}

function logCollect(msg) {
  const el = document.getElementById('collectLog');
  if (!el) return;
  const time = new Date().toLocaleTimeString();
  el.textContent += `[${time}] ${msg}\n`;
  el.scrollTop = el.scrollHeight;
}

async function collectNews() {
  const btn = document.getElementById('btnCollectNews');
  btn.disabled = true;
  logCollect('开始采集新闻...');
  try {
    const r = await fetch(API + '/api/news/collect', { method: 'POST' });
    const d = await r.json();
    logCollect(`✅ 新闻采集完成: 总采集${d.collected}条, 去重${d.deduplicated}条, 入库${d.stored}条`);
    if (d.sources) {
      d.sources.forEach(s => {
        const icon = s.stored > 0 ? '📥' : (s.collected > 0 ? '🔄' : '⚠️');
        logCollect(`   ${icon} ${s.name}: 采集${s.collected} → 去重${s.deduplicated} → 入库${s.stored}`);
      });
    }
    loadStats();
  } catch (e) { logCollect('❌ 采集失败: ' + e.message); }
  btn.disabled = false;
}

async function collectMarket() {
  const btn = document.getElementById('btnCollectMarket');
  btn.disabled = true;
  logCollect('开始采集行情...');
  try {
    const r = await fetch(API + '/api/market/collect', { method: 'POST' });
    const d = await r.json();
    logCollect(`✅ 行情采集完成: 采集${d.collected}条, 入库${d.stored}条`);
    loadStats();
  } catch (e) { logCollect('❌ 采集失败: ' + e.message); }
  btn.disabled = false;
}

// ============================================================
// 2. 新闻列表 Panel
// ============================================================

async function loadNews() {
  const panel = document.getElementById('panel-news');
  panel.innerHTML = '<div class="loading">加载中</div>';
  try {
    const r = await fetch(API + '/api/news?hours=72');
    const news = await r.json();
    if (!news.length) {
      panel.innerHTML = '<div class="empty-state"><div class="icon">📰</div><div class="text">暂无新闻，请先采集</div></div>';
      return;
    }
    let html = `<div class="section-title">📰 新闻列表 <span style="font-size:13px;color:#8890b5;font-weight:400">(${news.length}条)</span></div>`;
    html += news.map(n => {
      const score = n.credibilityScore ?? 0;
      const sc = scoreColor(score);
      const barW = Math.round(score * 100);
      const levelBg = n.credibilityLevel === 'authoritative' ? '#065f46' : n.credibilityLevel === 'normal' ? '#92400e' : '#991b1b';
      return `
      <div class="card">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin-bottom:8px">
          <div style="font-size:15px;color:#e0e0e0;font-weight:600;flex:1">${esc(n.title)}</div>
          <span style="font-size:11px;padding:3px 10px;border-radius:12px;background:${levelBg};color:#fff;white-space:nowrap">${n.credibilityLevel || 'unknown'} ${score ? score.toFixed(2) : ''}</span>
        </div>
        <div style="font-size:12px;color:#6b7280;margin-bottom:8px;display:flex;gap:8px;flex-wrap:wrap">
          <span>${esc(n.sourceName || '')}</span>
          ${n.sentiment ? '<span>情感: ' + n.sentiment + '</span>' : ''}
          ${n.tags ? '<span>' + renderTags(n.tags) + '</span>' : ''}
          <span>${timeAgo(n.collectedAt)}</span>
        </div>
        <div style="font-size:13px;color:#9ca3af;line-height:1.6;margin-bottom:10px">${esc((n.summary || '').substring(0, 200))}</div>
        <details>
          <summary style="font-size:12px;color:#3b82f6;cursor:pointer;margin-bottom:8px">展开完整内容</summary>
          <div style="font-size:13px;color:#9ca3af;line-height:1.8;padding:12px;background:#0a0e27;border-radius:8px;max-height:400px;overflow-y:auto;white-space:pre-wrap">${esc(n.content || '无内容')}</div>
        </details>
        ${score > 0 ? `
        <div style="margin-top:10px;padding-top:10px;border-top:1px solid #2a3070">
          <div style="display:flex;gap:16px;font-size:11px;margin-bottom:6px;color:#6b7280">
            <span>来源 <b style="color:${sc}">${(n.sourceCredibility || 0).toFixed(2)}</b></span>
            <span>LLM <b style="color:${sc}">${(n.llmCredibility || 0).toFixed(2)}</b></span>
            <span>时效 <b style="color:${sc}">${(n.freshnessCredibility || 0).toFixed(2)}</b></span>
            <span>交叉 <b style="color:${sc}">${(n.crossCredibility || 0).toFixed(2)}</b></span>
          </div>
          <div class="score-bar"><div class="score-bar-fill" style="background:${sc};width:${barW}%"></div></div>
        </div>` : ''}
        <div style="margin-top:8px;display:flex;gap:12px;align-items:center">
          ${n.embeddingJson ? '<span style="font-size:11px;color:#059669">✅ 已向量化</span>' : '<span style="font-size:11px;color:#6b7280">⏳ 未向量化</span>'}
          ${n.sourceUrl ? `<a href="${esc(n.sourceUrl)}" target="_blank" style="font-size:11px;color:#3b82f6;text-decoration:none">原文链接 ↗</a>` : ''}
        </div>
      </div>`;
    }).join('');
    panel.innerHTML = html;
  } catch (e) { panel.innerHTML = `<div class="empty-state"><div class="icon">❌</div><div class="text">加载失败: ${e.message}</div></div>`; }
}

// ============================================================
// 3. 情报中心 Panel
// ============================================================

let expandedIntelId = null;

function initIntelPanel() {
  document.getElementById('panel-intel').innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <div class="section-title" style="margin-bottom:0">🔍 情报中心</div>
      <div style="display:flex;gap:8px">
        <button onclick="triggerCluster()" class="btn btn-purple" id="btnCluster">🔄 手动聚类</button>
        <button onclick="refreshContent()" class="btn btn-success" id="btnRefresh">🔃 刷新正文</button>
        <button onclick="loadIntelligences()" class="btn btn-ghost">刷新</button>
      </div>
    </div>
    <div id="intelList" style="min-height:200px"></div>
  `;
}

async function triggerCluster() {
  const btn = document.getElementById('btnCluster');
  btn.disabled = true;
  btn.textContent = '⏳ 聚类中...';
  try {
    const r = await fetch(API + '/api/intelligences/cluster', { method: 'POST' });
    const d = await r.json();
    logCollect && logCollect(`聚类完成: 新建${d.data.created}, 合并${d.data.merged}`);
    loadIntelligences();
    loadStats();
  } catch (e) { alert('聚类失败: ' + e.message); }
  btn.disabled = false;
  btn.textContent = '🔄 手动聚类';
}

async function refreshContent() {
  const btn = document.getElementById('btnRefresh');
  btn.disabled = true;
  btn.textContent = '⏳ 清除中...';
  try {
    const r = await fetch(API + '/api/intelligences/refresh-content', { method: 'POST' });
    const d = await r.json();
    alert('已清空 ' + d.data.cleared + ' 条情报的正文缓存，下次查看详情时将重新生成');
    loadIntelligences();
  } catch (e) { alert('刷新失败: ' + e.message); }
  btn.disabled = false;
  btn.textContent = '🔃 刷新正文';
}

async function loadIntelligences() {
  const el = document.getElementById('intelList');
  if (!el) return;
  el.innerHTML = '<div class="loading">加载中</div>';
  expandedIntelId = null;
  try {
    const r = await fetch(API + '/api/intelligences?userId=' + currentUserId + '&hours=72&page=0&size=50&scene=admin');
    const d = await r.json();
    const items = d.data?.content;
    if (!items || !items.length) {
      el.innerHTML = '<div class="empty-state"><div class="icon">🔍</div><div class="text">暂无情报，请先采集新闻并执行聚类</div></div>';
      return;
    }
    el.innerHTML = items.map(i => renderIntelCard(i)).join('');
  } catch (e) { el.innerHTML = `<div class="empty-state"><div class="icon">❌</div><div class="text">加载失败: ${e.message}</div></div>`; }
}

function renderIntelCard(i) {
  const score = i.credibilityScore ?? 0;
  const sc = scoreColor(score);
  const barW = Math.round(score * 100);
  return `
  <div class="card card-clickable" id="intel-card-${i.id}" onclick="toggleIntelDetail(${i.id})">
    <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:8px">
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
        ${priorityBadge(i.priority)}
        <span style="font-size:11px;color:#6b7280">${esc(i.primarySource || '')}</span>
      </div>
      <span style="font-size:11px;color:#6b7280;white-space:nowrap">${timeAgo(i.latestArticleTime)}</span>
    </div>
    <div style="font-size:15px;color:#e0e0e0;font-weight:600;margin-bottom:6px">${esc(i.title)}</div>
    <div style="font-size:13px;color:#9ca3af;margin-bottom:10px;line-height:1.6">${esc((i.summary || '').substring(0, 180))}</div>
    <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap;font-size:12px">
      <div style="display:flex;align-items:center;gap:6px;min-width:120px">
        <span style="color:${sc};font-weight:600">${score.toFixed(2)}</span>
        <div class="score-bar" style="max-width:80px"><div class="score-bar-fill" style="background:${sc};width:${barW}%"></div></div>
      </div>
      <span style="color:#8890b5">${i.sourceCount || 1} 来源</span>
      ${sentimentBadge(i.sentiment)}
      ${i.tags ? renderTags(i.tags) : ''}
    </div>
    <div id="intel-detail-${i.id}"></div>
  </div>`;
}

async function toggleIntelDetail(id) {
  const detailEl = document.getElementById('intel-detail-' + id);
  if (!detailEl) return;

  if (expandedIntelId === id) {
    detailEl.innerHTML = '';
    expandedIntelId = null;
    return;
  }

  if (expandedIntelId !== null) {
    const prev = document.getElementById('intel-detail-' + expandedIntelId);
    if (prev) prev.innerHTML = '';
  }
  expandedIntelId = id;

  // Render skeleton with API call status panel + content slots
  detailEl.innerHTML = `<div class="intel-detail-inline" onclick="event.stopPropagation()">
    <div style="margin-bottom:16px;padding:14px;background:#0d1235;border:1px solid #2a3070;border-radius:10px">
      <div style="font-size:13px;color:#ffd700;margin-bottom:10px;font-weight:600">🔌 接口调用状态</div>
      <div id="api-status-${id}" style="display:flex;flex-direction:column;gap:6px">
        <div id="api-st-detail-${id}" class="api-status-row">⏳ <code>GET /api/intelligences/${id}</code> <span class="api-tag">基础详情</span></div>
        <div id="api-st-related-${id}" class="api-status-row">⏳ <code>GET /api/intelligences/${id}/related</code> <span class="api-tag">相关情报</span></div>
        <div id="api-st-analysis-${id}" class="api-status-row">⏳ <code>GET /api/intelligences/${id}/analysis</code> <span class="api-tag">个性化分析 (LLM)</span></div>
      </div>
    </div>
    <div id="intel-detail-content-${id}"></div>
    <div id="intel-sources-${id}"></div>
    <div id="intel-related-${id}"></div>
    <div id="intel-pa-${id}"></div>
  </div>`;

  // Fire all 3 API calls in parallel
  fetchIntelDetail(id);
  fetchIntelRelated(id);
  fetchIntelAnalysis(id);
}

function updateApiStatus(id, key, status, ms, error) {
  const el = document.getElementById('api-st-' + key + '-' + id);
  if (!el) return;
  if (status === 'ok') {
    el.innerHTML = el.innerHTML.replace(/^⏳/, '✅').replace(/⏳/, '✅');
    el.style.color = '#22c55e';
    el.innerHTML += ` <span style="color:#6b7280;font-size:11px">${ms}ms</span>`;
  } else {
    el.innerHTML = el.innerHTML.replace(/^⏳/, '❌').replace(/⏳/, '❌');
    el.style.color = '#ef4444';
    el.innerHTML += ` <span style="color:#ef4444;font-size:11px">${error || 'failed'} (${ms}ms)</span>`;
  }
}

async function fetchIntelDetail(id) {
  const t0 = performance.now();
  try {
    const r = await fetch(API + '/api/intelligences/' + id + '?userId=' + currentUserId);
    const ms = Math.round(performance.now() - t0);
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const detail = (await r.json()).data;
    updateApiStatus(id, 'detail', 'ok', ms);
    renderIntelDetailContent(id, detail);
  } catch (e) {
    const ms = Math.round(performance.now() - t0);
    updateApiStatus(id, 'detail', 'err', ms, e.message);
    const el = document.getElementById('intel-detail-content-' + id);
    if (el) el.innerHTML = `<div style="color:#ef4444;font-size:13px;padding:8px">基础详情加载失败: ${esc(e.message)}</div>`;
  }
}

function renderIntelDetailContent(id, detail) {
  const el = document.getElementById('intel-detail-content-' + id);
  if (!el) return;
  let html = '';

  // Content (LLM generated body)
  if (detail.content) {
    html += `<div style="font-size:14px;color:#e0e0e0;line-height:1.8;margin-bottom:16px;padding:14px;background:#0a0e27;border-radius:8px;white-space:pre-wrap;max-height:400px;overflow-y:auto">${esc(detail.content)}</div>`;
  }

  // Related stocks
  if (detail.relatedStocks) {
    html += `<div style="margin-bottom:12px">
      <span style="font-size:12px;color:#8890b5;margin-right:8px">关联股票:</span>
      ${detail.relatedStocks.split(',').map(s => `<span class="tag" style="color:#fbbf24;border:1px solid rgba(251,191,36,0.3)">${esc(s.trim())}</span>`).join('')}
    </div>`;
  }

  el.innerHTML = html;

  // Sources section
  const srcEl = document.getElementById('intel-sources-' + id);
  if (srcEl && detail.sources && detail.sources.length) {
    let srcHtml = `<div style="margin-bottom:16px">
      <div style="font-size:13px;color:#7c3aed;margin-bottom:8px;font-weight:600">📎 信息来源 (${detail.sources.length})</div>`;
    detail.sources.forEach(s => {
      srcHtml += `<div style="background:#0a0e27;border:1px solid #2a3070;border-radius:8px;padding:10px 14px;margin-bottom:6px;display:flex;justify-content:space-between;align-items:center">
        <div style="display:flex;gap:8px;align-items:center">
          ${credibilityTag(s.credibilityTag)}
          <span style="font-size:13px;color:#e0e0e0">${esc(s.sourceName || '')}</span>
          <span style="font-size:11px;color:#6b7280">— ${esc(s.title || '')}</span>
        </div>
        ${s.sourceUrl ? `<a href="${esc(s.sourceUrl)}" target="_blank" style="font-size:12px;color:#3b82f6;text-decoration:none;white-space:nowrap" onclick="event.stopPropagation()">查看原文 ↗</a>` : ''}
      </div>`;
    });
    srcHtml += '</div>';
    srcEl.innerHTML = srcHtml;
  }
}

async function fetchIntelRelated(id) {
  const t0 = performance.now();
  try {
    const r = await fetch(API + '/api/intelligences/' + id + '/related?limit=5');
    const ms = Math.round(performance.now() - t0);
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const d = await r.json();
    const items = d.data || [];
    updateApiStatus(id, 'related', 'ok', ms);
    renderIntelRelated(id, items);
  } catch (e) {
    const ms = Math.round(performance.now() - t0);
    updateApiStatus(id, 'related', 'err', ms, e.message);
  }
}

function renderIntelRelated(id, items) {
  const el = document.getElementById('intel-related-' + id);
  if (!el || !items.length) return;
  let html = `<div style="margin-bottom:16px">
    <div style="font-size:13px;color:#059669;margin-bottom:8px;font-weight:600">📎 相关情报 (${items.length})</div>`;
  items.forEach(i => {
    html += `<div style="background:#0a0e27;border:1px solid #2a3070;border-radius:8px;padding:10px 14px;margin-bottom:6px;cursor:pointer" onclick="event.stopPropagation();toggleIntelDetail(${i.id})">
      <div style="font-size:13px;color:#e0e0e0;font-weight:600">${esc(i.title || '')}</div>
      <div style="font-size:11px;color:#6b7280;margin-top:4px;display:flex;gap:8px">
        <span>${esc(i.primarySource || '')}</span>
        <span>${timeAgo(i.latestArticleTime)}</span>
      </div>
    </div>`;
  });
  html += '</div>';
  el.innerHTML = html;
}

async function fetchIntelAnalysis(id) {
  const t0 = performance.now();
  const el = document.getElementById('intel-pa-' + id);
  if (el) el.innerHTML = '<div style="color:#8890b5;font-size:12px;padding:8px">⏳ 等待个性化分析...</div>';
  try {
    const r = await fetch(API + '/api/intelligences/' + id + '/analysis?userId=' + currentUserId);
    const ms = Math.round(performance.now() - t0);
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const d = await r.json();
    const pa = d.data || {};
    updateApiStatus(id, 'analysis', 'ok', ms);
    renderIntelPersonalizedAnalysis(id, pa);
  } catch (e) {
    const ms = Math.round(performance.now() - t0);
    updateApiStatus(id, 'analysis', 'err', ms, e.message);
    if (el) el.innerHTML = `<div style="color:#ef4444;font-size:12px;padding:8px">个性化分析失败: ${esc(e.message)}</div>`;
  }
}

function renderIntelPersonalizedAnalysis(id, pa) {
  const el = document.getElementById('intel-pa-' + id);
  if (!el) return;
  const hasAnalysis = pa.analysis || (pa.impacts && pa.impacts.length) || pa.suggestion;
  if (!hasAnalysis && !pa.userProfile) {
    el.innerHTML = '';
    return;
  }

  let html = `<div style="margin-bottom:16px;padding:16px;background:rgba(124,58,237,0.08);border:1px solid rgba(124,58,237,0.3);border-radius:10px">`;
  html += `<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
    <div style="font-size:14px;color:#c4b5fd;font-weight:600">🎯 个性化研判</div>
    <button onclick="generateIntelAnalysis(${id})" class="btn btn-purple" id="btnIntelAnalysis-${id}" style="font-size:12px;padding:5px 14px">${hasAnalysis ? '🔄 重新研判' : '🧠 生成研判'}</button>
  </div>`;

  // User profile card
  if (pa.userProfile) {
    const up = pa.userProfile;
    html += `<div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:12px;font-size:12px">`;
    if (up.investorType) html += `<span class="tag tag-active">类型: ${esc(up.investorType)}</span>`;
    if (up.investmentCycle) html += `<span class="tag tag-active">周期: ${esc(up.investmentCycle)}</span>`;
    if (up.focusAreas && up.focusAreas.length) html += `<span class="tag tag-active">关注: ${esc(Array.isArray(up.focusAreas) ? up.focusAreas.join(',') : up.focusAreas)}</span>`;
    html += `</div>`;
  }

  html += `<div id="intel-analysis-content-${id}">`;
  if (hasAnalysis) {
    if (pa.impacts && pa.impacts.length) {
      html += `<div style="margin-bottom:12px"><div style="font-size:13px;color:#ffd700;margin-bottom:8px;font-weight:600">📊 影响分析</div>`;
      pa.impacts.forEach(imp => {
        html += `<div class="impact-card">
          <div class="impact-stock">${esc(imp.stock || imp.stockCode || '')}</div>
          <div class="impact-row"><span class="impact-label">影响:</span> <span style="color:#e0e0e0">${esc(imp.impact || imp.description || '')}</span></div>
          ${imp.level ? `<div class="impact-row"><span class="impact-label">级别:</span> <span style="color:#fbbf24">${esc(imp.level)}</span></div>` : ''}
          ${imp.volatility ? `<div class="impact-row"><span class="impact-label">波动:</span> <span>${esc(imp.volatility)}</span></div>` : ''}
        </div>`;
      });
      html += `</div>`;
    }
    if (pa.suggestion) {
      html += `<div style="margin-bottom:12px;padding:10px;background:rgba(34,197,94,0.1);border-radius:8px;border-left:3px solid #22c55e">
        <div style="font-size:12px;color:#22c55e;margin-bottom:4px;font-weight:600">💡 投资建议</div>
        <div style="font-size:13px;color:#e0e0e0">${esc(pa.suggestion)}</div>
      </div>`;
    }
    if (pa.risks && pa.risks.length) {
      html += `<div style="padding:10px;background:rgba(239,68,68,0.1);border-radius:8px;border-left:3px solid #ef4444">
        <div style="font-size:12px;color:#ef4444;margin-bottom:6px;font-weight:600">⚠️ 风险提示</div>
        ${pa.risks.map(r => `<div style="font-size:12px;color:#f87171;padding:2px 0">• ${esc(r)}</div>`).join('')}
      </div>`;
    }
    if (pa.analysis) {
      html += `<div style="font-size:13px;color:#e0e0e0;line-height:1.7;white-space:pre-wrap;margin-top:12px;padding:10px;background:#0a0e27;border-radius:8px">${esc(pa.analysis)}</div>`;
    }
  } else {
    html += `<div style="color:#8890b5;font-size:13px;padding:8px 0">暂无研判结果，点击上方按钮生成</div>`;
  }
  html += `</div></div>`;
  el.innerHTML = html;
}

// ============================================================
// 3.1 情报内联研判（异步调用LLM）
// ============================================================

async function generateIntelAnalysis(intelId) {
  const btn = document.getElementById('btnIntelAnalysis-' + intelId);
  const contentEl = document.getElementById('intel-analysis-content-' + intelId);
  if (!btn || !contentEl) return;

  btn.disabled = true;
  btn.textContent = '⏳ 分析中...';
  contentEl.innerHTML = '<div class="loading">正在调用AI生成研判，请稍候...</div>';

  try {
    const r = await fetch(API + '/api/analysis/generate?userId=' + currentUserId, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ articleId: intelId })
    });
    if (!r.ok) {
      const err = await r.json().catch(() => ({}));
      throw new Error(err.error || err.message || '请求失败 (' + r.status + ')');
    }
    const d = await r.json();

    let html = '';
    if (d.impacts && d.impacts.length) {
      html += `<div style="margin-bottom:12px"><div style="font-size:13px;color:#ffd700;margin-bottom:8px;font-weight:600">📊 影响分析</div>`;
      d.impacts.forEach(imp => {
        html += `<div class="impact-card">
          <div class="impact-stock">${esc(imp.stock || '')}</div>
          <div class="impact-row"><span class="impact-label">影响:</span> <span style="color:#e0e0e0">${esc(imp.impact || '')}</span></div>
          ${imp.level ? `<div class="impact-row"><span class="impact-label">级别:</span> <span style="color:#fbbf24">${esc(imp.level)}</span></div>` : ''}
          ${imp.volatility ? `<div class="impact-row"><span class="impact-label">波动:</span> <span>${esc(imp.volatility)}</span></div>` : ''}
        </div>`;
      });
      html += `</div>`;
    }
    if (d.suggestion) {
      html += `<div style="margin-bottom:12px;padding:10px;background:rgba(34,197,94,0.1);border-radius:8px;border-left:3px solid #22c55e">
        <div style="font-size:12px;color:#22c55e;margin-bottom:4px;font-weight:600">💡 投资建议</div>
        <div style="font-size:13px;color:#e0e0e0">${esc(d.suggestion)}</div>
      </div>`;
    }
    if (d.risks && d.risks.length) {
      html += `<div style="padding:10px;background:rgba(239,68,68,0.1);border-radius:8px;border-left:3px solid #ef4444">
        <div style="font-size:12px;color:#ef4444;margin-bottom:6px;font-weight:600">⚠️ 风险提示</div>
        ${d.risks.map(r => `<div style="font-size:12px;color:#f87171;padding:2px 0">• ${esc(r)}</div>`).join('')}
      </div>`;
    }
    if (d.analysis && !d.impacts?.length && !d.suggestion) {
      html += `<div style="font-size:13px;color:#e0e0e0;line-height:1.7;white-space:pre-wrap">${esc(d.analysis)}</div>`;
    }
    if (!html) html = '<div style="color:#8890b5;font-size:13px">AI 未返回有效分析结果</div>';

    contentEl.innerHTML = html;
    btn.textContent = '🔄 重新研判';
  } catch (e) {
    contentEl.innerHTML = `<div style="color:#ef4444;font-size:13px">❌ 研判失败: ${esc(e.message)}</div>`;
    btn.textContent = '🧠 重试研判';
  }
  btn.disabled = false;
}

// ============================================================
// 4. AI研判 Panel
// ============================================================

function initAnalysisPanel() {
  document.getElementById('panel-analysis').innerHTML = `
    <div class="section-title">🧠 AI研判分析</div>
    <div style="display:flex;gap:10px;margin-bottom:20px;align-items:flex-end;flex-wrap:wrap">
      <div style="flex:1;min-width:160px">
        <label style="font-size:12px;color:#8890b5;display:block;margin-bottom:6px">情报ID</label>
        <input id="analysisArticleId" type="number" class="input" placeholder="输入情报ID（如 1）" min="1">
      </div>
      <div style="min-width:100px">
        <label style="font-size:12px;color:#8890b5;display:block;margin-bottom:6px">用户ID</label>
        <input id="analysisUserId" type="number" class="input" placeholder="userId" style="width:100px">
      </div>
      <button onclick="generateAnalysis()" class="btn btn-purple" id="btnAnalysis">🧠 生成研判</button>
      <button onclick="loadAnalysisHistory()" class="btn btn-ghost">刷新历史</button>
    </div>
    <div id="analysisResult" style="margin-bottom:24px"></div>
    <div style="border-top:1px solid #2a3070;padding-top:16px">
      <div style="font-size:14px;color:#8890b5;margin-bottom:12px;font-weight:500">📋 研判历史</div>
      <div id="analysisHistory"></div>
    </div>
  `;
  // Sync userId input with currentUserId
  const uidInput = document.getElementById('analysisUserId');
  if (uidInput) uidInput.value = currentUserId;
}

async function generateAnalysis() {
  const idInput = document.getElementById('analysisArticleId');
  const articleId = parseInt(idInput.value);
  if (!articleId || articleId < 1) { alert('请输入有效的情报ID'); return; }

  const uidInput = document.getElementById('analysisUserId');
  const uid = parseInt(uidInput.value) || currentUserId;

  const btn = document.getElementById('btnAnalysis');
  const result = document.getElementById('analysisResult');
  btn.disabled = true;
  btn.textContent = '⏳ 分析中...';
  result.innerHTML = '<div class="loading">正在调用AI生成研判分析，请稍候</div>';

  try {
    const r = await fetch(API + '/api/analysis/generate?userId=' + uid, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ articleId })
    });
    if (!r.ok) {
      const err = await r.json().catch(() => ({}));
      throw new Error(err.error || err.message || '请求失败 (' + r.status + ')');
    }
    const d = await r.json();
    result.innerHTML = renderAnalysisResult(d);
    loadAnalysisHistory();
  } catch (e) {
    result.innerHTML = `<div class="card" style="border-color:#ef4444"><div style="color:#ef4444">❌ 研判失败: ${esc(e.message)}</div></div>`;
  }
  btn.disabled = false;
  btn.textContent = '🧠 生成研判';
}

function renderAnalysisResult(d) {
  let html = '<div class="card" style="border-color:#7c3aed">';

  if (d.analysis) {
    html += `<div style="font-size:14px;color:#e0e0e0;line-height:1.8;margin-bottom:16px;padding:14px;background:#0a0e27;border-radius:8px;white-space:pre-wrap">${esc(d.analysis)}</div>`;
  }

  if (d.userContext) {
    html += `<div style="font-size:12px;color:#8890b5;margin-bottom:16px;padding:8px 12px;background:rgba(124,58,237,0.1);border-radius:6px;border-left:3px solid #7c3aed">👤 ${esc(d.userContext)}</div>`;
  }

  if (d.impacts && d.impacts.length) {
    html += '<div style="margin-bottom:16px"><div style="font-size:14px;color:#ffd700;margin-bottom:10px;font-weight:600">📊 个股影响分析</div>';
    d.impacts.forEach(imp => {
      html += `<div class="impact-card">
        <div class="impact-stock">${esc(imp.stock)}</div>
        <div class="impact-row"><span class="impact-label">影响:</span> <span style="color:#e0e0e0">${esc(imp.impact)}</span></div>
        <div class="impact-row"><span class="impact-label">级别:</span> <span style="color:#fbbf24">${esc(imp.level)}</span></div>
        ${imp.volatility ? `<div class="impact-row"><span class="impact-label">波动:</span> <span>${esc(imp.volatility)}</span></div>` : ''}
        ${imp.revenueImpact ? `<div class="impact-row"><span class="impact-label">营收:</span> <span>${esc(imp.revenueImpact)}</span></div>` : ''}
        ${imp.longTermImpact ? `<div class="impact-row"><span class="impact-label">长期:</span> <span>${esc(imp.longTermImpact)}</span></div>` : ''}
      </div>`;
    });
    html += '</div>';
  }

  if (d.suggestion) {
    html += `<div style="margin-bottom:12px;padding:12px;background:rgba(34,197,94,0.1);border-radius:8px;border-left:3px solid #22c55e">
      <div style="font-size:12px;color:#22c55e;margin-bottom:4px;font-weight:600">💡 投资建议</div>
      <div style="font-size:14px;color:#e0e0e0">${esc(d.suggestion)}</div>
    </div>`;
  }

  if (d.risks && d.risks.length) {
    html += `<div style="padding:12px;background:rgba(239,68,68,0.1);border-radius:8px;border-left:3px solid #ef4444">
      <div style="font-size:12px;color:#ef4444;margin-bottom:6px;font-weight:600">⚠️ 风险提示</div>
      ${d.risks.map(r => `<div style="font-size:13px;color:#f87171;padding:2px 0">• ${esc(r)}</div>`).join('')}
    </div>`;
  }

  html += '</div>';
  return html;
}

async function loadAnalysisHistory() {
  const el = document.getElementById('analysisHistory');
  if (!el) return;
  el.innerHTML = '<div class="loading">加载中</div>';
  try {
    const r = await fetch(API + '/api/analysis/history?userId=' + currentUserId);
    if (!r.ok) throw new Error('请求失败');
    const records = await r.json();
    if (!records.length) {
      el.innerHTML = '<div class="empty-state"><div class="icon">📋</div><div class="text">暂无研判记录</div></div>';
      return;
    }
    el.innerHTML = records.map(rec => {
      let parsed = null;
      try { parsed = JSON.parse(rec.analysisText); } catch (_) {}
      const summary = parsed
        ? (parsed.suggestion || parsed.analysis || '').substring(0, 100)
        : (rec.analysisText || '').substring(0, 100);
      return `
      <div class="card card-clickable" onclick="toggleHistoryDetail(this)">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
          <span style="font-size:13px;color:#e0e0e0">情报 #${rec.newsArticleId}</span>
          <span style="font-size:11px;color:#6b7280">${timeAgo(rec.createdAt)}</span>
        </div>
        <div style="font-size:12px;color:#9ca3af">${esc(summary)}...</div>
        <div style="font-size:11px;color:#8890b5;margin-top:4px">风格: ${esc(rec.investmentStyle || '-')}</div>
        <div class="history-detail-slot" style="display:none" data-raw="${esc(rec.analysisText || '')}"></div>
      </div>`;
    }).join('');
  } catch (e) { el.innerHTML = `<div style="color:#ef4444;font-size:13px">加载失败: ${e.message}</div>`; }
}

function toggleHistoryDetail(cardEl) {
  const slot = cardEl.querySelector('.history-detail-slot');
  if (slot.style.display !== 'none') {
    slot.style.display = 'none';
    slot.innerHTML = '';
    return;
  }
  const rawText = slot.dataset.raw;
  slot.style.display = 'block';
  try {
    const d = JSON.parse(rawText);
    slot.innerHTML = `<div style="margin-top:10px;padding-top:10px;border-top:1px solid #2a3070">${renderAnalysisResult(d)}</div>`;
  } catch (_) {
    slot.innerHTML = `<div style="margin-top:10px;padding:12px;background:#0a0e27;border-radius:8px;font-size:13px;color:#9ca3af;white-space:pre-wrap;max-height:300px;overflow-y:auto">${esc(rawText)}</div>`;
  }
}

// ============================================================
// 5. 用户画像 Panel (upgraded with holdings management)
// ============================================================

const INVESTOR_TYPES = [
  { value: 'conservative', label: '🛡️ 保守型' },
  { value: 'balanced', label: '⚖️ 均衡型' },
  { value: 'growth', label: '🚀 成长型' }
];

const INVESTMENT_CYCLES = [
  { value: 'short', label: '短线 (<1月)' },
  { value: 'medium', label: '中线 (1-6月)' },
  { value: 'long', label: '长线 (>6月)' }
];

const FOCUS_OPTIONS = [
  { id: 'AI', name: 'AI' },
  { id: 'AI芯片', name: 'AI芯片' },
  { id: '云计算', name: '云计算' },
  { id: '半导体', name: '半导体' },
  { id: '大模型', name: '大模型' },
  { id: 'AIGC应用', name: 'AIGC应用' },
  { id: '自动驾驶', name: '自动驾驶' },
  { id: '机器人', name: '机器人' },
  { id: '量子计算', name: '量子计算' },
  { id: '生物科技', name: '生物科技' },
  { id: '新能源', name: '新能源' }
];

let profileData = {};
let holdingsList = [];

function initProfilePanel() {
  document.getElementById('panel-profile').innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px">
      <div class="section-title" style="margin-bottom:0">👤 用户管理 & 画像</div>
      <button onclick="saveProfile()" class="btn btn-primary" id="btnSaveProfile">💾 保存画像</button>
    </div>

    <!-- User selector row -->
    <div style="margin-bottom:16px;padding:14px;background:#151a40;border:1px solid #2a3070;border-radius:10px">
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
        <span style="font-size:12px;color:#8890b5">当前用户:</span>
        <input id="currentUserIdInput" class="input" style="width:100px" value="${currentUserId}" placeholder="userId">
        <button onclick="loadProfileForUser()" class="btn btn-ghost">📥 Load</button>
        <button onclick="loginNewUser()" class="btn btn-purple">📱 Login</button>
        <button onclick="loadUserList()" class="btn btn-ghost">👥 用户列表</button>
      </div>
    </div>

    <!-- User list (loaded on demand) -->
    <div id="userListSection" style="margin-bottom:16px"></div>

    <!-- Nickname editing -->
    <div style="margin-bottom:16px;padding:14px;background:#151a40;border:1px solid #2a3070;border-radius:10px">
      <div class="profile-field" style="margin-bottom:0">
        <label>昵称</label>
        <div style="display:flex;gap:8px;align-items:center">
          <input id="profileNickname" class="input" style="width:240px" placeholder="输入昵称">
        </div>
      </div>
    </div>

    <!-- Profile form -->
    <div class="profile-grid">
      <div>
        <div class="profile-field">
          <label>投资者类型</label>
          <div id="profileInvestorType" style="display:flex;gap:8px;flex-wrap:wrap"></div>
        </div>
        <div class="profile-field">
          <label>投资周期</label>
          <div id="profileCycle" style="display:flex;gap:8px;flex-wrap:wrap"></div>
        </div>
      </div>
      <div>
        <div class="profile-field">
          <label>关注领域（点击切换，支持自定义）</label>
          <div id="profileFocusAreas" style="display:flex;gap:6px;flex-wrap:wrap"></div>
          <div style="display:flex;gap:8px;margin-top:8px;align-items:center">
            <input id="customFocusInput" class="input" style="width:160px" placeholder="自定义领域">
            <button onclick="addCustomFocus()" class="btn btn-ghost" style="padding:6px 12px;font-size:12px">+ 添加</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Holdings management -->
    <div style="margin-top:20px;padding:16px;background:#151a40;border:1px solid #2a3070;border-radius:10px">
      <div style="font-size:14px;color:#ffd700;margin-bottom:12px;font-weight:600">📦 持仓管理</div>
      <div style="display:flex;gap:8px;margin-bottom:12px;align-items:flex-end;flex-wrap:wrap">
        <div>
          <label style="font-size:11px;color:#8890b5;display:block;margin-bottom:4px">股票代码</label>
          <input id="holdingCodeInput" class="input" style="width:100px" placeholder="如 NVDA">
        </div>
        <div>
          <label style="font-size:11px;color:#8890b5;display:block;margin-bottom:4px">股票名称</label>
          <input id="holdingNameInput" class="input" style="width:120px" placeholder="如 英伟达">
        </div>
        <div>
          <label style="font-size:11px;color:#8890b5;display:block;margin-bottom:4px">所属行业</label>
          <input id="holdingSectorInput" class="input" style="width:120px" placeholder="如 半导体">
        </div>
        <div>
          <label style="font-size:11px;color:#8890b5;display:block;margin-bottom:4px">持仓占比(%)</label>
          <input id="holdingPercentInput" class="input" style="width:90px" type="number" step="0.1" placeholder="如 40">
        </div>
        <div>
          <label style="font-size:11px;color:#8890b5;display:block;margin-bottom:4px">成本价($)</label>
          <input id="holdingCostInput" class="input" style="width:90px" type="number" step="0.01" placeholder="如 320">
        </div>
        <button onclick="addHolding()" class="btn btn-success">➕ 添加</button>
      </div>
      <div id="holdingsListContainer"></div>
    </div>

    <div id="profileStatus" style="margin-top:12px;font-size:13px"></div>
  `;
}

async function loadUserList() {
  const el = document.getElementById('userListSection');
  if (!el) return;
  el.innerHTML = '<div class="loading">加载用户列表</div>';
  try {
    const r = await fetch(API + '/api/auth/users');
    const d = await r.json();
    const users = d.data || d || [];
    if (!users.length) {
      el.innerHTML = '<div style="color:#8890b5;font-size:13px">暂无用户，请先登录创建</div>';
      return;
    }
    el.innerHTML = '<div style="font-size:13px;color:#8890b5;margin-bottom:8px">已注册用户 (' + users.length + ')</div>' +
      '<div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:8px">' +
      users.map(u => `<div class="card card-clickable" style="min-width:180px;flex:0 1 auto;padding:10px 14px" onclick="selectUser(${u.userId || u.id})">
        <div style="font-size:14px;color:#e0e0e0;font-weight:600">${esc(u.nickname || u.phone)} <span style="font-size:11px;color:#6b7280">#${u.userId || u.id}</span></div>
        <div style="font-size:12px;color:#6b7280;margin-top:2px">${esc(u.phone || '')}</div>
        <div style="font-size:11px;color:#6b7280;margin-top:2px">${timeAgo(u.lastLoginAt || u.createdAt)}</div>
      </div>`).join('') + '</div>';
  } catch (e) { el.innerHTML = '<div style="color:#ef4444;font-size:13px">加载失败: ' + e.message + '</div>'; }
}

function selectUser(id) {
  currentUserId = id;
  const input = document.getElementById('currentUserIdInput');
  if (input) input.value = id;
  // Also sync analysis panel userId
  const aInput = document.getElementById('analysisUserId');
  if (aInput) aInput.value = id;
  loadProfile();
  loadHoldings();
}

function loadProfileForUser() {
  const input = document.getElementById('currentUserIdInput');
  const id = parseInt(input.value);
  if (!id || id < 1) { alert('请输入有效的用户ID'); return; }
  currentUserId = id;
  // Sync analysis panel
  const aInput = document.getElementById('analysisUserId');
  if (aInput) aInput.value = id;
  loadProfile();
  loadHoldings();
}

async function loginNewUser() {
  const phone = prompt('输入手机号:');
  if (!phone) return;
  const nickname = prompt('昵称 (可选):') || '';
  try {
    const r = await fetch(API + '/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone, nickname: nickname || undefined })
    });
    const d = await r.json();
    const userData = d.data || d;
    if (userData && (userData.userId || userData.id)) {
      const uid = userData.userId || userData.id;
      alert((userData.isNew ? '注册成功' : '登录成功') + ': userId=' + uid + ', nickname=' + (userData.nickname || ''));
      currentUserId = uid;
      const input = document.getElementById('currentUserIdInput');
      if (input) input.value = uid;
      const aInput = document.getElementById('analysisUserId');
      if (aInput) aInput.value = uid;
      loadProfile();
      loadHoldings();
      loadUserList();
    }
  } catch (e) { alert('登录失败: ' + e.message); }
}

async function loadProfile() {
  try {
    const r = await fetch(API + '/api/profile?userId=' + currentUserId);
    const d = await r.json();
    profileData = d.data || d || {};
  } catch (e) {
    profileData = {};
  }
  // Load nickname
  try {
    const r = await fetch(API + '/api/auth/me?userId=' + currentUserId);
    const d = await r.json();
    const nickInput = document.getElementById('profileNickname');
    if (nickInput && d.data) nickInput.value = d.data.nickname || '';
  } catch (_) {}
  renderProfileForm();
  loadHoldings();
}

function renderProfileForm() {
  const p = profileData;

  const typeEl = document.getElementById('profileInvestorType');
  if (typeEl) {
    typeEl.innerHTML = INVESTOR_TYPES.map(t =>
      `<span class="tag tag-toggle ${p.investorType === t.value ? 'tag-active' : ''}" onclick="selectInvestorType('${t.value}')">${t.label}</span>`
    ).join('');
  }

  const cycleEl = document.getElementById('profileCycle');
  if (cycleEl) {
    cycleEl.innerHTML = INVESTMENT_CYCLES.map(c =>
      `<span class="tag tag-toggle ${p.investmentCycle === c.value ? 'tag-active' : ''}" onclick="selectCycle('${c.value}')">${c.label}</span>`
    ).join('');
  }

  const focusEl = document.getElementById('profileFocusAreas');
  if (focusEl) {
    const selected = (p.focusAreas || '').split(',').map(s => s.trim()).filter(Boolean);
    const presetIds = FOCUS_OPTIONS.map(f => f.id);
    // 预设选项
    let html = FOCUS_OPTIONS.map(f =>
      `<span class="tag tag-toggle ${selected.includes(f.id) ? 'tag-active' : ''}" onclick="toggleFocus('${f.id}')">${f.name}</span>`
    ).join('');
    // 用户自定义的（不在预设列表中的）
    selected.filter(s => !presetIds.includes(s)).forEach(s => {
      html += `<span class="tag tag-toggle tag-active" onclick="toggleFocus('${esc(s)}')">${esc(s)} ✕</span>`;
    });
    focusEl.innerHTML = html;
  }
}

function selectInvestorType(val) {
  profileData.investorType = val;
  renderProfileForm();
}

function selectCycle(val) {
  profileData.investmentCycle = val;
  renderProfileForm();
}

function toggleFocus(id) {
  const current = (profileData.focusAreas || '').split(',').map(s => s.trim()).filter(Boolean);
  const idx = current.indexOf(id);
  if (idx >= 0) current.splice(idx, 1);
  else current.push(id);
  profileData.focusAreas = current.join(',');
  renderProfileForm();
}

function addCustomFocus() {
  const input = document.getElementById('customFocusInput');
  const val = (input.value || '').trim();
  if (!val) return;
  const current = (profileData.focusAreas || '').split(',').map(s => s.trim()).filter(Boolean);
  if (!current.includes(val)) {
    current.push(val);
    profileData.focusAreas = current.join(',');
  }
  input.value = '';
  renderProfileForm();
}

async function saveProfile() {
  const btn = document.getElementById('btnSaveProfile');
  const status = document.getElementById('profileStatus');
  btn.disabled = true;
  btn.textContent = '⏳ 保存中...';

  try {
    // Save nickname
    const nickname = (document.getElementById('profileNickname')?.value || '').trim();
    if (nickname) {
      await fetch(API + '/api/auth/me?userId=' + currentUserId, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nickname })
      });
    }

    // Save profile
    const r = await fetch(API + '/api/profile?userId=' + currentUserId, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        investorType: profileData.investorType || '',
        investmentCycle: profileData.investmentCycle || '',
        focusAreas: profileData.focusAreas || ''
      })
    });
    await r.json();
    status.innerHTML = '<span style="color:#22c55e">✅ 画像已保存</span>';
    setTimeout(() => { if (status) status.innerHTML = ''; }, 3000);
    // Refresh user list to show updated nickname
    loadUserList();
  } catch (e) {
    status.innerHTML = `<span style="color:#ef4444">❌ 保存失败: ${e.message}</span>`;
  }
  btn.disabled = false;
  btn.textContent = '💾 保存画像';
}

// --- Holdings CRUD ---

async function loadHoldings() {
  const el = document.getElementById('holdingsListContainer');
  if (!el) return;
  el.innerHTML = '<div class="loading">加载持仓</div>';
  try {
    const r = await fetch(API + '/api/profile/holdings?userId=' + currentUserId);
    const d = await r.json();
    holdingsList = d.data || d || [];
    renderHoldings();
  } catch (e) {
    holdingsList = [];
    el.innerHTML = '<div style="color:#6b7280;font-size:12px">加载持仓失败: ' + e.message + '</div>';
  }
}

function renderHoldings() {
  const el = document.getElementById('holdingsListContainer');
  if (!el) return;
  if (!holdingsList.length) {
    el.innerHTML = '<div style="color:#6b7280;font-size:13px;padding:8px 0">暂无持仓，请添加</div>';
    return;
  }
  el.innerHTML = '<div style="display:flex;flex-wrap:wrap;gap:8px">' +
    holdingsList.map(h => {
      const hId = h.id || h.holdingId;
      const pct = h.percentage != null ? `持仓: ${h.percentage}%` : '';
      const cost = h.costPrice != null ? `成本: $${h.costPrice}` : '';
      const extra = [pct, cost].filter(Boolean).join(' · ');
      return `<div class="card" style="display:flex;align-items:center;gap:10px;padding:8px 14px;min-width:220px;flex:0 1 auto">
        <div style="flex:1">
          <div style="font-size:14px;color:#ffd700;font-weight:600">${esc(h.stockCode)}</div>
          <div style="font-size:12px;color:#e0e0e0">${esc(h.stockName || '')}</div>
          ${h.sector ? `<div style="font-size:11px;color:#6b7280">${esc(h.sector)}</div>` : ''}
          ${extra ? `<div style="font-size:11px;color:#8890b5;margin-top:2px">${extra}</div>` : ''}
        </div>
        <button onclick="deleteHolding(${hId})" class="btn btn-ghost" style="padding:4px 10px;font-size:12px;color:#ef4444;border-color:#ef4444">✕</button>
      </div>`;
    }).join('') + '</div>';
}

async function addHolding() {
  const codeInput = document.getElementById('holdingCodeInput');
  const nameInput = document.getElementById('holdingNameInput');
  const sectorInput = document.getElementById('holdingSectorInput');
  const percentInput = document.getElementById('holdingPercentInput');
  const costInput = document.getElementById('holdingCostInput');
  const stockCode = (codeInput.value || '').trim().toUpperCase();
  const stockName = (nameInput.value || '').trim();
  const sector = (sectorInput.value || '').trim();
  const percentage = (percentInput.value || '').trim();
  const costPrice = (costInput.value || '').trim();

  if (!stockCode) { alert('请输入股票代码'); return; }
  if (!stockName) { alert('请输入股票名称'); return; }

  try {
    const body = { stockCode, stockName };
    if (sector) body.sector = sector;
    if (percentage) body.percentage = percentage;
    if (costPrice) body.costPrice = costPrice;
    const r = await fetch(API + '/api/profile/holdings?userId=' + currentUserId, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    if (!r.ok) {
      const err = await r.json().catch(() => ({}));
      throw new Error(err.error || err.message || '添加失败');
    }
    codeInput.value = '';
    nameInput.value = '';
    sectorInput.value = '';
    percentInput.value = '';
    costInput.value = '';
    loadHoldings();
  } catch (e) { alert('添加持仓失败: ' + e.message); }
}

async function deleteHolding(holdingId) {
  if (!confirm('确认删除该持仓？')) return;
  try {
    const r = await fetch(API + '/api/profile/holdings/' + holdingId, { method: 'DELETE' });
    if (!r.ok) throw new Error('删除失败');
    loadHoldings();
  } catch (e) { alert('删除失败: ' + e.message); }
}

// ============================================================
// 6. 智能问答 Panel
// ============================================================

function initQueryPanel() {
  document.getElementById('panel-query').innerHTML = `
    <div class="section-title">🤖 智能问答 <span style="font-size:12px;color:#8890b5;font-weight:400">语义检索 + LLM流式推理</span></div>
    <div style="display:flex;gap:10px;margin-bottom:16px">
      <input id="queryInput" type="text" class="input" placeholder="输入问题，如：英伟达最近有什么利好消息？"
        onkeydown="if(event.key==='Enter')doQuery()" style="flex:1">
      <button onclick="doQuery()" id="queryBtn" class="btn btn-purple">🔍 分析</button>
      <button onclick="abortQuery()" id="queryAbortBtn" class="btn btn-ghost" style="display:none">⏹ 停止</button>
    </div>
    <div style="display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap">
      <button onclick="setQuery('AI芯片行业最近有什么重大变化？')" class="qbtn">AI芯片动态</button>
      <button onclick="setQuery('大模型领域最近有哪些值得关注的事件？')" class="qbtn">大模型热点</button>
      <button onclick="setQuery('科技股最近走势如何？有什么投资机会？')" class="qbtn">科技股分析</button>
      <button onclick="setQuery('最近有哪些利空消息需要注意？')" class="qbtn">风险提示</button>
    </div>
    <div id="queryResult" style="min-height:200px"></div>
  `;
}

/** 将 LLM 纯文本转为带格式的 HTML */
function formatLlmAnswer(text) {
  if (!text) return '';
  // 先 escape HTML
  let s = esc(text);
  // **bold**
  s = s.replace(/\*\*([^*]+)\*\*/g, '<strong style="color:#ffd700">$1</strong>');
  // ### / ## / # headings (行首)
  s = s.replace(/^###\s*(.+)$/gm, '<div style="font-size:14px;font-weight:700;color:#c4b5fd;margin:14px 0 6px">$1</div>');
  s = s.replace(/^##\s*(.+)$/gm, '<div style="font-size:15px;font-weight:700;color:#c4b5fd;margin:16px 0 8px">$1</div>');
  s = s.replace(/^#\s*(.+)$/gm, '<div style="font-size:16px;font-weight:700;color:#ffd700;margin:18px 0 8px">$1</div>');
  // 无序列表 - / • / ·
  s = s.replace(/^[\-•·]\s+(.+)$/gm, '<div style="padding-left:16px;position:relative;margin:3px 0"><span style="position:absolute;left:0;color:#7c3aed">•</span>$1</div>');
  // 有序列表 1. 2. 3.
  s = s.replace(/^(\d+)\.\s+(.+)$/gm, '<div style="padding-left:20px;position:relative;margin:3px 0"><span style="position:absolute;left:0;color:#fbbf24;font-weight:600;font-size:12px">$1.</span>$2</div>');
  // 空行 → 段落间距
  s = s.replace(/\n\n+/g, '<div style="height:10px"></div>');
  // 单换行
  s = s.replace(/\n/g, '<br>');
  return s;
}

function setQuery(q) { document.getElementById('queryInput').value = q; }

let currentQuerySource = null;

function abortQuery() {
  if (currentQuerySource) {
    currentQuerySource.close();
    currentQuerySource = null;
  }
  const btn = document.getElementById('queryBtn');
  const abortBtn = document.getElementById('queryAbortBtn');
  btn.disabled = false;
  btn.textContent = '🔍 分析';
  abortBtn.style.display = 'none';
}

async function doQuery() {
  const input = document.getElementById('queryInput');
  const q = input.value.trim();
  if (!q) return;
  const result = document.getElementById('queryResult');
  const btn = document.getElementById('queryBtn');
  const abortBtn = document.getElementById('queryAbortBtn');
  btn.disabled = true;
  btn.textContent = '⏳ 检索中...';
  abortBtn.style.display = '';

  // Render skeleton
  result.innerHTML = `
    <div id="queryMeta" style="margin-bottom:12px">
      <div class="loading">正在语义检索相关新闻...</div>
    </div>
    <div id="queryThinkingBlock" style="display:none;margin-bottom:12px">
      <details id="queryThinkingDetails" open>
        <summary style="font-size:13px;color:#f59e0b;cursor:pointer;padding:8px 14px;background:rgba(245,158,11,0.08);border:1px solid rgba(245,158,11,0.2);border-radius:10px 10px 0 0;user-select:none;display:flex;align-items:center;gap:6px">
          <span>💭</span> <span id="queryThinkingLabel">思考中...</span>
        </summary>
        <div id="queryThinkingText" style="padding:12px 16px;font-size:12px;line-height:1.7;color:#9ca3af;background:#0d1235;border:1px solid rgba(245,158,11,0.15);border-top:none;border-radius:0 0 10px 10px;max-height:300px;overflow-y:auto;white-space:pre-wrap"></div>
      </details>
    </div>
    <div style="background:#151a40;border:1px solid #2a3070;border-radius:12px;overflow:hidden;margin-bottom:16px">
      <div style="padding:12px 16px;background:linear-gradient(135deg,rgba(124,58,237,0.15),rgba(99,102,241,0.08));border-bottom:1px solid #2a3070;display:flex;align-items:center;gap:8px">
        <span style="font-size:16px">🤖</span>
        <span style="font-size:13px;color:#c4b5fd;font-weight:600" id="queryAnswerLabel">AI 分析中...</span>
      </div>
      <div id="queryAnswerText" style="padding:16px 20px;font-size:14px;line-height:1.85;color:#e0e0e0;min-height:60px"><span class="loading" style="padding:0">等待 LLM 响应</span></div>
    </div>
    <div id="queryRelatedNews"></div>
  `;

  const url = API + '/api/query/stream?question=' + encodeURIComponent(q);
  const eventSource = new EventSource(url);
  currentQuerySource = eventSource;
  let answerText = '';
  let reasoningText = '';
  let firstToken = true;
  let firstReasoning = true;
  let matchedCount = 0;

  eventSource.addEventListener('meta', function(e) {
    try {
      const meta = JSON.parse(e.data);
      matchedCount = meta.matchedCount || 0;
      const metaEl = document.getElementById('queryMeta');
      metaEl.innerHTML = `<div style="display:flex;align-items:center;gap:8px;padding:10px 14px;background:rgba(124,58,237,0.08);border:1px solid rgba(124,58,237,0.2);border-radius:10px">
        <span style="font-size:14px">🔍</span>
        <span style="font-size:13px;color:#c4b5fd">语义匹配到 <b style="color:#ffd700">${meta.matchedCount}</b> 条相关情报，正在生成分析...</span>
      </div>`;
      btn.textContent = '⏳ 生成中...';

      // Render related news as collapsible cards
      if (meta.relatedNews && meta.relatedNews.length) {
        const newsEl = document.getElementById('queryRelatedNews');
        let html = `<details style="margin-top:4px">
          <summary style="font-size:13px;color:#8890b5;cursor:pointer;padding:8px 0;user-select:none;display:flex;align-items:center;gap:6px">
            <span>📎</span> 参考情报来源 (${meta.relatedNews.length}条聚合情报)
          </summary>
          <div style="display:grid;gap:6px;padding:8px 0">`;
        meta.relatedNews.forEach(function(n) {
          const credColor = n.credibilityLevel === 'authoritative' ? '#065f46' : n.credibilityLevel === 'normal' ? '#92400e' : '#6b7280';
          const srcCount = n.sourceCount > 1 ? ' · ' + n.sourceCount + '源' : '';
          const relevance = n.relevance ? n.relevance + '%' : '';
          html += `<div style="display:flex;align-items:center;gap:10px;padding:8px 12px;background:#0d1235;border:1px solid #1e2555;border-radius:8px">
            <span style="font-size:10px;padding:2px 8px;border-radius:10px;background:${credColor};color:#fff;white-space:nowrap">${esc(n.credibilityLevel || '')}</span>
            <span style="font-size:12px;color:#8890b5;white-space:nowrap">${esc(n.sourceName)}${srcCount}</span>
            <span style="font-size:12px;color:#e0e0e0;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(n.title)}</span>
            ${relevance ? '<span style="font-size:11px;color:#fbbf24;white-space:nowrap">' + relevance + '</span>' : ''}
            ${n.sourceUrl ? '<a href="' + esc(n.sourceUrl) + '" target="_blank" style="color:#3b82f6;text-decoration:none;font-size:11px;white-space:nowrap">原文↗</a>' : ''}
          </div>`;
        });
        html += '</div></details>';
        newsEl.innerHTML = html;
      }
    } catch (err) { console.warn('meta parse error', err); }
  });

  eventSource.addEventListener('reasoning', function(e) {
    const block = document.getElementById('queryThinkingBlock');
    if (firstReasoning) {
      block.style.display = 'block';
      btn.textContent = '⏳ 思考中...';
      firstReasoning = false;
    }
    reasoningText += e.data;
    const textEl = document.getElementById('queryThinkingText');
    textEl.textContent = reasoningText;
    textEl.scrollTop = textEl.scrollHeight;
  });

  eventSource.addEventListener('token', function(e) {
    if (firstToken) {
      document.getElementById('queryAnswerText').innerHTML = '';
      // Collapse thinking when answer starts
      if (!firstReasoning) {
        const details = document.getElementById('queryThinkingDetails');
        if (details) details.removeAttribute('open');
        document.getElementById('queryThinkingLabel').textContent = '思考过程（点击展开）';
      }
      btn.textContent = '⏳ 输出中...';
      firstToken = false;
    }
    answerText += e.data;
    document.getElementById('queryAnswerText').innerHTML = formatLlmAnswer(answerText) + '<span style="display:inline-block;width:2px;height:14px;background:#7c3aed;margin-left:2px;vertical-align:middle;animation:blink 1s step-end infinite"></span>';
  });

  eventSource.addEventListener('done', function() {
    eventSource.close();
    currentQuerySource = null;
    btn.disabled = false;
    btn.textContent = '🔍 分析';
    abortBtn.style.display = 'none';
    // Final render without cursor
    document.getElementById('queryAnswerText').innerHTML = formatLlmAnswer(answerText);
    document.getElementById('queryAnswerLabel').textContent = 'AI 分析结果（基于 ' + matchedCount + ' 条情报）';
    // Finalize thinking block label
    if (!firstReasoning) {
      document.getElementById('queryThinkingLabel').textContent = '思考过程（' + reasoningText.length + '字）';
    }
    // Update meta bar
    const metaEl = document.getElementById('queryMeta');
    if (metaEl) {
      metaEl.innerHTML = `<div style="display:flex;align-items:center;gap:8px;padding:10px 14px;background:rgba(34,197,94,0.08);border:1px solid rgba(34,197,94,0.2);border-radius:10px">
        <span style="font-size:14px">✅</span>
        <span style="font-size:13px;color:#4ade80">分析完成，基于 <b style="color:#ffd700">${matchedCount}</b> 条情报生成</span>
      </div>`;
    }
  });

  eventSource.addEventListener('error', function(e) {
    eventSource.close();
    currentQuerySource = null;
    btn.disabled = false;
    btn.textContent = '🔍 分析';
    abortBtn.style.display = 'none';
    if (!answerText) {
      document.getElementById('queryAnswerText').innerHTML = '<span style="color:#ef4444">查询失败，请重试</span>';
    }
  });

  eventSource.onerror = function() {
    // EventSource auto-reconnects; we close on first error to avoid loops
    eventSource.close();
    currentQuerySource = null;
    btn.disabled = false;
    btn.textContent = '🔍 分析';
    abortBtn.style.display = 'none';
    if (!answerText) {
      document.getElementById('queryAnswerText').innerHTML = '<span style="color:#ef4444">连接中断，请重试</span>';
    }
  };
}

// ============================================================
// 7. 行情数据 Panel
// ============================================================

async function loadMarket() {
  const panel = document.getElementById('panel-market');
  panel.innerHTML = '<div class="loading">加载中</div>';
  try {
    const r = await fetch(API + '/api/market');
    const data = await r.json();
    if (!data.length) {
      panel.innerHTML = '<div class="empty-state"><div class="icon">📈</div><div class="text">暂无行情数据，请先采集</div></div>';
      return;
    }
    let html = `<div class="section-title">📈 AI概念股行情</div>
    <div style="overflow-x:auto">
    <table style="width:100%;border-collapse:collapse;font-size:13px">
      <thead><tr style="color:#8890b5;border-bottom:2px solid #2a3070">
        <th style="padding:10px 8px;text-align:left">代码</th>
        <th style="text-align:left">名称</th>
        <th style="text-align:right">价格</th>
        <th style="text-align:right">涨跌幅</th>
        <th style="text-align:right">成交量</th>
        <th style="text-align:right">换手率</th>
        <th style="text-align:right">市盈率</th>
      </tr></thead><tbody>`;
    data.forEach(d => {
      const color = d.changePercent > 0 ? '#ef4444' : d.changePercent < 0 ? '#22c55e' : '#9ca3af';
      const bg = d.changePercent > 0 ? 'rgba(239,68,68,0.05)' : d.changePercent < 0 ? 'rgba(34,197,94,0.05)' : 'transparent';
      html += `<tr style="border-bottom:1px solid #1a1f4e;background:${bg}">
        <td style="padding:10px 8px;font-weight:600">${d.stockCode || ''}</td>
        <td>${d.stockName || ''}</td>
        <td style="text-align:right;font-weight:600">${d.currentPrice != null ? d.currentPrice.toFixed(2) : '-'}</td>
        <td style="text-align:right;color:${color};font-weight:600">${d.changePercent != null ? (d.changePercent > 0 ? '+' : '') + d.changePercent.toFixed(2) + '%' : '-'}</td>
        <td style="text-align:right">${d.volume != null ? Math.round(d.volume).toLocaleString() : '-'}</td>
        <td style="text-align:right">${d.turnoverRate != null ? d.turnoverRate.toFixed(2) + '%' : '-'}</td>
        <td style="text-align:right">${d.peRatio != null ? d.peRatio.toFixed(1) : '-'}</td>
      </tr>`;
    });
    html += '</tbody></table></div>';
    panel.innerHTML = html;
  } catch (e) { panel.innerHTML = `<div class="empty-state"><div class="icon">❌</div><div class="text">加载失败: ${e.message}</div></div>`; }
}

// ============================================================
// 8. 实时日志 Panel
// ============================================================

let logInterval = null;

function initLogsPanel() {
  document.getElementById('panel-logs').innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
      <div class="section-title" style="margin-bottom:0">📋 实时日志</div>
      <div style="display:flex;gap:10px;align-items:center">
        <label style="font-size:12px;color:#8890b5;cursor:pointer;display:flex;align-items:center;gap:4px">
          <input type="checkbox" id="logAutoScroll" checked> 自动滚动
        </label>
        <button onclick="loadLogs()" class="btn btn-ghost" style="padding:6px 14px">刷新</button>
      </div>
    </div>
    <div id="logContent" class="log-console" style="height:520px"></div>
  `;
}

async function loadLogs() {
  try {
    const r = await fetch(API + '/api/logs?count=100');
    const d = await r.json();
    const el = document.getElementById('logContent');
    if (!el) return;
    el.innerHTML = d.logs.map(line => {
      let color = '#9ca3af';
      if (line.includes('ERROR')) color = '#ef4444';
      else if (line.includes('WARN')) color = '#f59e0b';
      else if (line.includes('INFO') && (line.includes('完成') || line.includes('成功') || line.includes('Done'))) color = '#22c55e';
      return `<span style="color:${color}">${esc(line)}</span>`;
    }).join('\n');
    if (document.getElementById('logAutoScroll')?.checked) {
      el.scrollTop = el.scrollHeight;
    }
  } catch (e) { console.error('日志加载失败', e); }
}

function startLogPolling() {
  if (!logInterval) logInterval = setInterval(loadLogs, 2000);
}

function stopLogPolling() {
  if (logInterval) { clearInterval(logInterval); logInterval = null; }
}

// ============================================================
// Initialization
// ============================================================

initCollectPanel();
initQueryPanel();
initIntelPanel();
initAnalysisPanel();
initProfilePanel();
initLogsPanel();
loadStats();
setInterval(loadStats, 30000);
