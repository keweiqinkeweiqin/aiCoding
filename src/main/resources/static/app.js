const API = '';

// --- Tab switching ---
function switchTab(name) {
  document.querySelectorAll('.tab').forEach((t, i) => {
    t.classList.toggle('active', t.textContent.includes(
      {collect:'采集',news:'新闻',query:'问答',market:'行情'}[name]));
  });
  document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
  document.getElementById('panel-' + name).classList.add('active');
  if (name === 'news') loadNews();
  if (name === 'market') loadMarket();
}

// --- Stats ---
async function loadStats() {
  try {
    const r = await fetch(API + '/api/stats');
    const d = await r.json();
    document.getElementById('statNews').textContent = '新闻: ' + d.totalNews;
    document.getElementById('statVector').textContent = '向量: ' + d.vectorCacheSize;
    document.getElementById('statMarket').textContent = '行情: ' + d.totalMarket;
  } catch(e) { console.error(e); }
}

// --- Collect Panel ---
function initCollectPanel() {
  document.getElementById('panel-collect').innerHTML = `
    <h3 style="color:#ffd700;margin-bottom:16px">数据采集控制</h3>
    <div style="display:flex;gap:12px;margin-bottom:16px">
      <button onclick="collectNews()" style="padding:10px 24px;background:#2563eb;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:14px">📡 采集新闻</button>
      <button onclick="collectMarket()" style="padding:10px 24px;background:#059669;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:14px">📈 采集行情</button>
    </div>
    <div id="collectLog" style="background:#0a0e27;border:1px solid #2a3070;border-radius:6px;padding:16px;min-height:200px;font-family:monospace;font-size:13px;white-space:pre-wrap;max-height:400px;overflow-y:auto"></div>
  `;
}

function logCollect(msg) {
  const el = document.getElementById('collectLog');
  const time = new Date().toLocaleTimeString();
  el.textContent += `[${time}] ${msg}\n`;
  el.scrollTop = el.scrollHeight;
}

async function collectNews() {
  logCollect('开始采集新闻...');
  try {
    const r = await fetch(API + '/api/news/collect', {method:'POST'});
    const d = await r.json();
    logCollect(`✅ 新闻采集完成: 采集${d.collected}条, 去重${d.deduplicated}条, 入库${d.stored}条`);
    loadStats();
  } catch(e) { logCollect('❌ 采集失败: ' + e.message); }
}

async function collectMarket() {
  logCollect('开始采集行情...');
  try {
    const r = await fetch(API + '/api/market/collect', {method:'POST'});
    const d = await r.json();
    logCollect(`✅ 行情采集完成: 采集${d.collected}条, 入库${d.stored}条`);
    loadStats();
  } catch(e) { logCollect('❌ 采集失败: ' + e.message); }
}

// --- News Panel ---
async function loadNews() {
  const panel = document.getElementById('panel-news');
  panel.innerHTML = '<p style="color:#8890b5">加载中...</p>';
  try {
    const r = await fetch(API + '/api/news?hours=72');
    const news = await r.json();
    if (!news.length) { panel.innerHTML = '<p style="color:#8890b5">暂无新闻，请先采集</p>'; return; }
    panel.innerHTML = `<h3 style="color:#ffd700;margin-bottom:16px">新闻列表 (${news.length}条)</h3>` +
      news.map(n => `
        <div style="background:#151a40;border:1px solid #2a3070;border-radius:8px;padding:14px;margin-bottom:10px">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
            <strong style="color:#e0e0e0;font-size:15px">${esc(n.title)}</strong>
            <span style="font-size:11px;padding:3px 8px;border-radius:10px;background:${
              n.credibilityLevel==='authoritative'?'#065f46':n.credibilityLevel==='normal'?'#92400e':'#991b1b'
            };color:#fff">${n.credibilityLevel||'unknown'}</span>
          </div>
          <div style="font-size:12px;color:#6b7280;margin-bottom:6px">${esc(n.sourceName||'')} | ${n.collectedAt||''}</div>
          <div style="font-size:13px;color:#9ca3af;line-height:1.5">${esc((n.summary||'').substring(0,200))}</div>
          ${n.embeddingJson ? '<span style="font-size:11px;color:#059669">✅ 已向量化</span>' : '<span style="font-size:11px;color:#6b7280">⏳ 未向量化</span>'}
          ${n.sourceUrl ? ` | <a href="${esc(n.sourceUrl)}" target="_blank" style="font-size:11px;color:#3b82f6">原文链接</a>` : ''}
        </div>
      `).join('');
  } catch(e) { panel.innerHTML = '<p style="color:#ef4444">加载失败: '+e.message+'</p>'; }
}

function esc(s) {
  if (!s) return '';
  const d = document.createElement('div'); d.textContent = s; return d.innerHTML;
}

// --- Query Panel ---
function initQueryPanel() {
  document.getElementById('panel-query').innerHTML = `
    <h3 style="color:#ffd700;margin-bottom:16px">智能问答（语义检索 + LLM推理）</h3>
    <div style="display:flex;gap:10px;margin-bottom:16px">
      <input id="queryInput" type="text" placeholder="输入问题，如：英伟达最近有什么利好消息？" 
        style="flex:1;padding:12px 16px;background:#0a0e27;border:1px solid #2a3070;border-radius:6px;color:#e0e0e0;font-size:14px;outline:none"
        onkeydown="if(event.key==='Enter')doQuery()">
      <button onclick="doQuery()" id="queryBtn"
        style="padding:12px 24px;background:#7c3aed;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:14px;white-space:nowrap">🔍 分析</button>
    </div>
    <div style="display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap">
      <button onclick="setQuery('AI芯片行业最近有什么重大变化？')" class="qbtn">AI芯片动态</button>
      <button onclick="setQuery('大模型领域最近有哪些值得关注的事件？')" class="qbtn">大模型热点</button>
      <button onclick="setQuery('科技股最近走势如何？有什么投资机会？')" class="qbtn">科技股分析</button>
      <button onclick="setQuery('最近有哪些利空消息需要注意？')" class="qbtn">风险提示</button>
    </div>
    <style>.qbtn{padding:6px 14px;background:#1e2555;border:1px solid #2a3070;border-radius:16px;color:#8890b5;cursor:pointer;font-size:12px;transition:all .2s}.qbtn:hover{border-color:#7c3aed;color:#c4b5fd}</style>
    <div id="queryResult" style="background:#0a0e27;border:1px solid #2a3070;border-radius:6px;padding:20px;min-height:200px"></div>
  `;
}

function setQuery(q) { document.getElementById('queryInput').value = q; }

async function doQuery() {
  const input = document.getElementById('queryInput');
  const q = input.value.trim();
  if (!q) return;
  const result = document.getElementById('queryResult');
  const btn = document.getElementById('queryBtn');
  btn.disabled = true; btn.textContent = '⏳ 分析中...';
  result.innerHTML = '<p style="color:#8890b5">正在语义检索相关新闻并调用AI分析，请稍候...</p>';
  try {
    const r = await fetch(API + '/api/query', {
      method: 'POST', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({question: q})
    });
    const d = await r.json();
    let html = `<div style="margin-bottom:16px;padding:16px;background:#151a40;border-radius:8px;border-left:3px solid #7c3aed">
      <div style="font-size:13px;color:#7c3aed;margin-bottom:8px">AI 分析结果（匹配${d.matchedCount}条相关新闻）</div>
      <div style="font-size:14px;line-height:1.8;color:#e0e0e0;white-space:pre-wrap">${esc(d.answer)}</div>
    </div>`;
    if (d.relatedNews && d.relatedNews.length) {
      html += '<div style="font-size:13px;color:#8890b5;margin-bottom:8px">📎 参考新闻来源：</div>';
      d.relatedNews.forEach(n => {
        html += `<div style="font-size:12px;color:#6b7280;padding:4px 0">• [${esc(n.sourceName)}] ${esc(n.title)} <a href="${esc(n.sourceUrl)}" target="_blank" style="color:#3b82f6">↗</a></div>`;
      });
    }
    result.innerHTML = html;
  } catch(e) { result.innerHTML = '<p style="color:#ef4444">查询失败: '+e.message+'</p>'; }
  btn.disabled = false; btn.textContent = '🔍 分析';
}

// --- Market Panel ---
async function loadMarket() {
  const panel = document.getElementById('panel-market');
  panel.innerHTML = '<p style="color:#8890b5">加载中...</p>';
  try {
    const r = await fetch(API + '/api/market');
    const data = await r.json();
    if (!data.length) { panel.innerHTML = '<p style="color:#8890b5">暂无行情数据，请先采集（需配置麦蕊智数licence）</p>'; return; }
    let html = '<h3 style="color:#ffd700;margin-bottom:16px">AI概念股行情</h3>';
    html += '<table style="width:100%;border-collapse:collapse;font-size:13px"><thead><tr style="color:#8890b5;border-bottom:1px solid #2a3070">';
    html += '<th style="padding:8px;text-align:left">代码</th><th style="text-align:left">名称</th><th style="text-align:right">价格</th><th style="text-align:right">涨跌幅</th><th style="text-align:right">成交量</th><th style="text-align:right">换手率</th><th style="text-align:right">市盈率</th></tr></thead><tbody>';
    data.forEach(d => {
      const color = d.changePercent > 0 ? '#ef4444' : d.changePercent < 0 ? '#22c55e' : '#9ca3af';
      html += `<tr style="border-bottom:1px solid #1a1f4e">
        <td style="padding:8px">${d.stockCode||''}</td>
        <td>${d.stockName||''}</td>
        <td style="text-align:right">${d.currentPrice!=null?d.currentPrice.toFixed(2):'-'}</td>
        <td style="text-align:right;color:${color}">${d.changePercent!=null?(d.changePercent>0?'+':'')+d.changePercent.toFixed(2)+'%':'-'}</td>
        <td style="text-align:right">${d.volume!=null?Math.round(d.volume).toLocaleString():'-'}</td>
        <td style="text-align:right">${d.turnoverRate!=null?d.turnoverRate.toFixed(2)+'%':'-'}</td>
        <td style="text-align:right">${d.peRatio!=null?d.peRatio.toFixed(1):'-'}</td>
      </tr>`;
    });
    html += '</tbody></table>';
    panel.innerHTML = html;
  } catch(e) { panel.innerHTML = '<p style="color:#ef4444">加载失败: '+e.message+'</p>'; }
}

// --- Init ---
initCollectPanel();
initQueryPanel();
loadStats();
setInterval(loadStats, 30000);
