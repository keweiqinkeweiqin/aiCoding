const API = '';

// --- Tab switching ---
function switchTab(name) {
  document.querySelectorAll('.tab').forEach((t, i) => {
    const map = {collect:'采集',news:'新闻',intel:'情报',query:'问答',market:'行情',logs:'日志'};
    t.classList.toggle('active', t.textContent.includes(map[name]));
  });
  document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
  document.getElementById('panel-' + name).classList.add('active');
  if (name === 'news') loadNews();
  if (name === 'intel') loadIntelligences();
  if (name === 'market') loadMarket();
  if (name === 'logs') { startLogPolling(); loadLogs(); }
  else stopLogPolling();
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
    logCollect(`✅ 新闻采集完成: 总采集${d.collected}条, 去重${d.deduplicated}条, 入库${d.stored}条`);
    if (d.sources) {
      d.sources.forEach(s => {
        const icon = s.stored > 0 ? '📥' : (s.collected > 0 ? '🔄' : '⚠️');
        logCollect(`   ${icon} ${s.name}: 采集${s.collected} → 去重${s.deduplicated} → 入库${s.stored}`);
      });
    }
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
      news.map(n => {
        const score = n.credibilityScore != null ? n.credibilityScore : 0;
        const scoreColor = score >= 0.8 ? '#22c55e' : score >= 0.5 ? '#f59e0b' : '#ef4444';
        const barWidth = Math.round(score * 100);
        return `
        <div style="background:#151a40;border:1px solid #2a3070;border-radius:8px;padding:14px;margin-bottom:10px">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
            <strong style="color:#e0e0e0;font-size:15px">${esc(n.title)}</strong>
            <span style="font-size:11px;padding:3px 8px;border-radius:10px;background:${
              n.credibilityLevel==='authoritative'?'#065f46':n.credibilityLevel==='normal'?'#92400e':'#991b1b'
            };color:#fff">${n.credibilityLevel||'unknown'} ${score ? score.toFixed(2) : ''}</span>
          </div>
          <div style="font-size:12px;color:#6b7280;margin-bottom:6px">
            ${esc(n.sourceName||'')} | ${n.sentiment ? '情感:'+n.sentiment : ''} ${n.tags ? '| 标签:'+esc(n.tags) : ''} | ${n.collectedAt||''}
          </div>
          <div style="font-size:13px;color:#9ca3af;line-height:1.5;margin-bottom:8px">${esc((n.summary||'').substring(0,200))}</div>
          <details style="margin-bottom:8px">
            <summary style="font-size:12px;color:#3b82f6;cursor:pointer;margin-bottom:6px">展开完整内容</summary>
            <div style="font-size:13px;color:#9ca3af;line-height:1.8;padding:10px;background:#0a0e27;border-radius:6px;max-height:400px;overflow-y:auto;white-space:pre-wrap">${esc(n.content||'无内容')}</div>
          </details>
          ${score > 0 ? `
          <div style="font-size:11px;color:#6b7280;margin-bottom:4px">置信度明细：</div>
          <div style="display:flex;gap:12px;font-size:11px;margin-bottom:6px">
            <span>来源 <span style="color:${scoreColor}">${(n.sourceCredibility||0).toFixed(2)}</span></span>
            <span>LLM <span style="color:${scoreColor}">${(n.llmCredibility||0).toFixed(2)}</span></span>
            <span>时效 <span style="color:${scoreColor}">${(n.freshnessCredibility||0).toFixed(2)}</span></span>
            <span>交叉验证 <span style="color:${scoreColor}">${(n.crossCredibility||0).toFixed(2)}</span></span>
          </div>
          <div style="background:#0a0e27;border-radius:3px;height:4px;overflow:hidden">
            <div style="background:${scoreColor};height:100%;width:${barWidth}%;transition:width .3s"></div>
          </div>` : ''}
          <div style="margin-top:6px">
            ${n.embeddingJson ? '<span style="font-size:11px;color:#059669">✅ 已向量化</span>' : '<span style="font-size:11px;color:#6b7280">⏳ 未向量化</span>'}
            ${n.sourceUrl ? ` | <a href="${esc(n.sourceUrl)}" target="_blank" style="font-size:11px;color:#3b82f6">原文链接</a>` : ''}
          </div>
        </div>`;
      }).join('');
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

// --- Intelligence Panel ---
function initIntelPanel() {
  document.getElementById('panel-intel').innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <h3 style="color:#ffd700">情报中心</h3>
      <div style="display:flex;gap:8px">
        <button onclick="triggerCluster()" style="padding:8px 16px;background:#7c3aed;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:13px">🔄 手动聚类</button>
        <button onclick="loadIntelligences()" style="padding:8px 16px;background:#1e2555;border:1px solid #2a3070;border-radius:6px;color:#8890b5;cursor:pointer;font-size:13px">刷新</button>
      </div>
    </div>
    <div id="intelList" style="min-height:200px"></div>
    <div id="intelDetail" style="display:none;margin-top:16px"></div>
  `;
}

async function triggerCluster() {
  try {
    const r = await fetch(API + '/api/intelligences/cluster', {method:'POST'});
    const d = await r.json();
    alert('Clustering done: created=' + d.data.created + ', merged=' + d.data.merged);
    loadIntelligences();
  } catch(e) { alert('Cluster failed: ' + e.message); }
}

async function loadIntelligences() {
  const el = document.getElementById('intelList');
  el.innerHTML = '<p style="color:#8890b5">Loading...</p>';
  document.getElementById('intelDetail').style.display = 'none';
  try {
    const r = await fetch(API + '/api/intelligences?hours=72&page=0&size=50');
    const d = await r.json();
    const items = d.data.content;
    if (!items || !items.length) { el.innerHTML = '<p style="color:#8890b5">No intelligences yet. Collect news first, then run clustering.</p>'; return; }
    el.innerHTML = items.map(i => {
      const pColor = i.priority==='high'?'#ef4444':i.priority==='medium'?'#f59e0b':'#6b7280';
      const pLabel = i.priority==='high'?'重要':i.priority==='medium'?'一般':'低';
      const sColor = i.sentiment==='positive'?'#22c55e':i.sentiment==='negative'?'#ef4444':'#9ca3af';
      const score = i.credibilityScore != null ? i.credibilityScore : 0;
      const scoreColor = score >= 0.8 ? '#22c55e' : score >= 0.5 ? '#f59e0b' : '#ef4444';
      return `
      <div onclick="loadIntelDetail(${i.id})" style="background:#151a40;border:1px solid #2a3070;border-radius:8px;padding:14px;margin-bottom:10px;cursor:pointer;transition:border-color .2s" onmouseover="this.style.borderColor='#7c3aed'" onmouseout="this.style.borderColor='#2a3070'">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
          <div style="display:flex;gap:8px;align-items:center">
            <span style="font-size:11px;padding:2px 8px;border-radius:4px;background:${pColor}20;color:${pColor}">${pLabel}</span>
            <span style="font-size:11px;color:#6b7280">${esc(i.primarySource||'')}</span>
          </div>
          <span style="font-size:11px;color:#6b7280">${i.latestArticleTime ? new Date(i.latestArticleTime).toLocaleString() : ''}</span>
        </div>
        <div style="font-size:15px;color:#e0e0e0;font-weight:600;margin-bottom:6px">${esc(i.title)}</div>
        <div style="font-size:13px;color:#9ca3af;margin-bottom:8px;line-height:1.5">${esc((i.summary||'').substring(0,150))}</div>
        <div style="display:flex;gap:12px;align-items:center;font-size:11px">
          <span style="padding:2px 8px;border-radius:10px;background:#065f4620;color:${scoreColor}">Score ${score.toFixed(2)}</span>
          <span style="color:#8890b5">${i.sourceCount||1} sources</span>
          <span style="color:${sColor}">${i.sentiment||'neutral'}</span>
          ${i.tags ? '<span style="color:#6b7280">'+esc(i.tags)+'</span>' : ''}
        </div>
      </div>`;
    }).join('');
  } catch(e) { el.innerHTML = '<p style="color:#ef4444">Load failed: '+e.message+'</p>'; }
}

async function loadIntelDetail(id) {
  const el = document.getElementById('intelDetail');
  el.style.display = 'block';
  el.innerHTML = '<p style="color:#8890b5">Loading detail...</p>';
  el.scrollIntoView({behavior:'smooth'});
  try {
    const r = await fetch(API + '/api/intelligences/' + id);
    const d = await r.json();
    const i = d.data;
    let html = `
    <div style="background:#151a40;border:1px solid #7c3aed;border-radius:8px;padding:20px">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
        <h3 style="color:#ffd700;font-size:18px">${esc(i.title)}</h3>
        <button onclick="document.getElementById('intelDetail').style.display='none'" style="background:none;border:none;color:#6b7280;cursor:pointer;font-size:18px">✕</button>
      </div>
      <div style="display:flex;gap:12px;margin-bottom:16px;font-size:12px;color:#8890b5">
        <span>${esc(i.primarySource||'')}</span>
        <span>${i.readTime||''}</span>
        <span>${i.sourceCount||1} sources</span>
        <span style="color:${i.credibilityScore>=0.8?'#22c55e':'#f59e0b'}">Score ${(i.credibilityScore||0).toFixed(2)}</span>
      </div>`;
    if (i.summary) {
      html += `<div style="font-size:14px;color:#c0c0c0;line-height:1.6;margin-bottom:16px;padding:12px;background:#0a0e27;border-radius:6px">${esc(i.summary)}</div>`;
    }
    if (i.content) {
      html += `<div style="font-size:14px;color:#e0e0e0;line-height:1.8;margin-bottom:16px;white-space:pre-wrap">${esc(i.content)}</div>`;
    }
    if (i.sources && i.sources.length) {
      html += '<div style="margin-bottom:16px"><div style="font-size:14px;color:#7c3aed;margin-bottom:8px;font-weight:600">📎 Sources (' + i.sources.length + ')</div>';
      i.sources.forEach(s => {
        const tagColor = s.credibilityTag==='权威'?'#065f46':s.credibilityTag==='可信'?'#92400e':'#991b1b';
        html += `<div style="background:#0a0e27;border:1px solid #2a3070;border-radius:6px;padding:10px;margin-bottom:6px;display:flex;justify-content:space-between;align-items:center">
          <div style="display:flex;gap:8px;align-items:center">
            <span style="font-size:11px;padding:2px 6px;border-radius:10px;background:${tagColor};color:#fff">${esc(s.credibilityTag)}</span>
            <span style="font-size:13px;color:#e0e0e0">${esc(s.sourceName||'')}</span>
          </div>
          ${s.sourceUrl ? '<a href="'+esc(s.sourceUrl)+'" target="_blank" style="font-size:12px;color:#3b82f6">View ↗</a>' : ''}
        </div>`;
      });
      html += '</div>';
    }
    html += `<div style="display:flex;gap:8px;flex-wrap:wrap;font-size:11px">
      ${i.relatedStocks ? '<span style="color:#f59e0b">Stocks: '+esc(i.relatedStocks)+'</span>' : ''}
      ${i.tags ? '<span style="color:#8890b5">Tags: '+esc(i.tags)+'</span>' : ''}
    </div></div>`;
    el.innerHTML = html;
  } catch(e) { el.innerHTML = '<p style="color:#ef4444">Detail load failed: '+e.message+'</p>'; }
}

// --- Init ---
initCollectPanel();
initQueryPanel();
initIntelPanel();
initLogsPanel();
loadStats();
setInterval(loadStats, 30000);

// --- Logs Panel ---
let logInterval = null;

function initLogsPanel() {
  document.getElementById('panel-logs').innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
      <h3 style="color:#ffd700">实时日志（自动刷新）</h3>
      <div style="display:flex;gap:8px;align-items:center">
        <label style="font-size:12px;color:#8890b5"><input type="checkbox" id="logAutoScroll" checked> 自动滚动</label>
        <button onclick="loadLogs()" style="padding:6px 14px;background:#1e2555;border:1px solid #2a3070;border-radius:6px;color:#8890b5;cursor:pointer;font-size:12px">刷新</button>
      </div>
    </div>
    <div id="logContent" style="background:#0a0e27;border:1px solid #2a3070;border-radius:6px;padding:12px;height:500px;overflow-y:auto;font-family:'Menlo','Monaco',monospace;font-size:12px;line-height:1.6;white-space:pre-wrap;color:#9ca3af"></div>
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
      else if (line.includes('INFO') && (line.includes('完成') || line.includes('成功'))) color = '#22c55e';
      return `<span style="color:${color}">${esc(line)}</span>`;
    }).join('\n');
    if (document.getElementById('logAutoScroll')?.checked) {
      el.scrollTop = el.scrollHeight;
    }
  } catch(e) { console.error('日志加载失败', e); }
}

function startLogPolling() {
  if (!logInterval) logInterval = setInterval(loadLogs, 2000);
}

function stopLogPolling() {
  if (logInterval) { clearInterval(logInterval); logInterval = null; }
}
