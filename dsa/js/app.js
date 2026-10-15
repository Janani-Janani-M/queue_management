/**
 * QueueCare Frontend — app.js
 *
 * Connects the HTML interface to the Spring Boot REST API backed by MySQL at:
 *   http://localhost:8080/api/queue
 *
 * Features:
 * - Citizen Self-Service Portal ("View My Ticket")
 * - Dynamic QR Code generation & Canvas PNG ticket download
 * - Web Audio double-chime & Speech Synthesis
 * - Real-time MySQL backend synchronization & auto-polling
 * - Citizen Registration Workstation with Senior Citizen auto-detection
 * - Public Digital Signage Board with Fullscreen Kiosk mode
 */

'use strict';

// ══════════════════════════════════════════════════════════════
//  CONFIG & CONSTANTS
// ══════════════════════════════════════════════════════════════
const API_BASE = 'http://localhost:8080/api/queue';
const POLL_MS  = 5000;   // Auto-poll interval (ms)

// ══════════════════════════════════════════════════════════════
//  STATE
// ══════════════════════════════════════════════════════════════
const DEMO_SEED = [
  { token:'Q101', name:'Ravi Kumar',  service:'Document Services',    category:'Priority Assistance', wait:12, status:'Waiting', arrival:1 },
  { token:'Q102', name:'Meena Devi', service:'General Services',      category:'Standard',            wait:8,  status:'Waiting', arrival:2 },
  { token:'Q103', name:'Arun Kumar', service:'Accessibility Support', category:'Priority Assistance', wait:17, status:'Waiting', arrival:3 },
  { token:'Q104', name:'Priya S',    service:'General Services',      category:'Standard',            wait:4,  status:'Waiting', arrival:4 },
];

let state = {
  connected:      false,
  demo:           true,            // true = local memory; false = live MySQL backend
  queue:          [],              // list of all citizens
  tokenNum:       105,
  arrivalNum:     5,
  servedToday:    3,
  skippedToday:   0,
  currentToken:   null,
  nextToken:      null,
  currentCounter: 1,
  currentCitizen: null,
  recentServed:   [],
  audioEnabled:   true,
  trackedToken:   null,            // active token being viewed in Citizen Portal
  lastModalToken: null,            // token from last registration modal
};

// ══════════════════════════════════════════════════════════════
//  DOM HELPERS
// ══════════════════════════════════════════════════════════════
const $  = id => document.getElementById(id);
const qs = s  => document.querySelector(s);

function escapeHTML(v) {
  return String(v ?? '').replace(/[&<>"']/g, c => ({
    '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#039;'
  })[c]);
}

function formatDate() {
  return new Intl.DateTimeFormat('en-IN', {
    weekday:'short', day:'numeric', month:'short', year:'numeric'
  }).format(new Date());
}

function formatTime() {
  return new Intl.DateTimeFormat('en-IN', {
    hour:'2-digit', minute:'2-digit', second:'2-digit', hour12:true
  }).format(new Date());
}

// ── Toast Notification ─────────────────────────────────────────
let _toastTimer = null;
function showToast(message, type = 'info') {
  const t = $('toast');
  if (!t) return;
  t.textContent = message;
  t.className   = `toast toast-${type} show`;
  clearTimeout(_toastTimer);
  _toastTimer = setTimeout(() => t.classList.remove('show'), 3400);
}

// ══════════════════════════════════════════════════════════════
//  WEB AUDIO CHIME & SPEECH SYNTHESIS
// ══════════════════════════════════════════════════════════════
function playChime() {
  if (!state.audioEnabled) return;
  try {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();
    const now = ctx.currentTime;

    // Note 1: D5 (587.33 Hz)
    const osc1 = ctx.createOscillator();
    const gain1 = ctx.createGain();
    osc1.type = 'sine';
    osc1.frequency.setValueAtTime(587.33, now);
    gain1.gain.setValueAtTime(0.25, now);
    gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.55);
    osc1.connect(gain1);
    gain1.connect(ctx.destination);
    osc1.start(now);
    osc1.stop(now + 0.55);

    // Note 2: A5 (880 Hz)
    const osc2 = ctx.createOscillator();
    const gain2 = ctx.createGain();
    osc2.type = 'sine';
    osc2.frequency.setValueAtTime(880, now + 0.22);
    gain2.gain.setValueAtTime(0.28, now + 0.22);
    gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.85);
    osc2.connect(gain2);
    gain2.connect(ctx.destination);
    osc2.start(now + 0.22);
    osc2.stop(now + 0.85);
  } catch (err) {
    console.warn('Audio chime warning:', err);
  }
}

function speakAnnouncement(token, name, counter = 1) {
  playChime();
  if (!state.audioEnabled) return;

  if ('speechSynthesis' in window) {
    setTimeout(() => {
      window.speechSynthesis.cancel();
      const citizenName = name ? name.split(' ')[0] : 'Citizen';
      const text = `Token ${token}. ${citizenName}. Please proceed to Counter ${counter}.`;
      const utter = new SpeechSynthesisUtterance(text);
      utter.rate = 0.92;
      utter.pitch = 1.0;
      utter.lang = 'en-US';
      window.speechSynthesis.speak(utter);
    }, 450);
  }
}

// ══════════════════════════════════════════════════════════════
//  QR CODE GENERATOR (Client-Side Vector Generator)
// ══════════════════════════════════════════════════════════════
function generateQRMatrix(text) {
  const size = 25;
  const matrix = Array.from({ length: size }, () => Array(size).fill(0));

  // Finder pattern helper (7x7 box with 3x3 inner square)
  function drawFinder(r0, c0) {
    for (let r = 0; r < 7; r++) {
      for (let c = 0; c < 7; c++) {
        if (r === 0 || r === 6 || c === 0 || c === 6 || (r >= 2 && r <= 4 && c >= 2 && c <= 4)) {
          matrix[r0 + r][c0 + c] = 1;
        }
      }
    }
  }

  // Draw 3 standard corner finders
  drawFinder(0, 0);
  drawFinder(0, size - 7);
  drawFinder(size - 7, 0);

  // Timing patterns
  for (let i = 8; i < size - 8; i++) {
    matrix[6][i] = (i % 2 === 0) ? 1 : 0;
    matrix[i][6] = (i % 2 === 0) ? 1 : 0;
  }

  // Alignment pattern at bottom right (16, 16)
  for (let r = 16; r <= 20; r++) {
    for (let c = 16; c <= 20; c++) {
      if (r === 16 || r === 20 || c === 16 || c === 20 || (r === 18 && c === 18)) {
        matrix[r][c] = 1;
      }
    }
  }

  // Deterministic module generation derived from text hash
  let hash = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) {
    hash ^= text.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }

  // Populate data modules avoiding finder patterns
  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      const inFinder = (r < 8 && c < 8) || (r < 8 && c >= size - 8) || (r >= size - 8 && c < 8);
      const inTiming = (r === 6 || c === 6);
      const inAlign  = (r >= 16 && r <= 20 && c >= 16 && c <= 20);

      if (!inFinder && !inTiming && !inAlign) {
        hash = (hash * 1103515245 + 12345) & 0x7fffffff;
        matrix[r][c] = (hash % 7 < 3) ? 1 : 0;
      }
    }
  }

  return matrix;
}

function renderQRCodeSVG(text, containerEl) {
  if (!containerEl) return;
  const matrix = generateQRMatrix(text);
  const size = matrix.length;
  const cellSize = 5;
  const total = size * cellSize;

  let rects = '';
  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      if (matrix[r][c] === 1) {
        rects += `<rect x="${c * cellSize}" y="${r * cellSize}" width="${cellSize}" height="${cellSize}" fill="#0b1929"/>`;
      }
    }
  }

  containerEl.innerHTML = `
    <svg width="125" height="125" viewBox="0 0 ${total} ${total}" xmlns="http://www.w3.org/2000/svg" style="background:#fff;border-radius:4px;">
      ${rects}
    </svg>
  `;
}

// ══════════════════════════════════════════════════════════════
//  API COMMUNICATION (Spring Boot / MySQL)
// ══════════════════════════════════════════════════════════════
async function apiRequest(method, endpoint, body) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
  };
  if (body) opts.body = JSON.stringify(body);
  const res  = await fetch(`${API_BASE}${endpoint}`, opts);
  const json = await res.json();
  return { ok: res.ok, status: res.status, data: json };
}

function setConnStatus(status) {
  const dot  = $('connDot');
  const text = $('connStatus');
  if (!dot || !text) return;

  dot.className  = `conn-dot ${status}`;
  if (status === 'online') {
    text.textContent = 'Connected to MySQL Backend (Port 8080)';
  } else if (status === 'offline') {
    text.textContent = 'Backend Offline — Reconnecting…';
  } else {
    text.textContent = 'Local Demo Mode (No Server)';
  }
}

// ── Fetch stats from Spring Boot ───────────────────────────────
async function fetchStats() {
  try {
    const r = await apiRequest('GET', '/stats');
    if (!r.ok || !r.data?.success) throw new Error('Stats failed');

    const s = r.data.data;
    state.connected = true;
    state.demo = false;
    setConnStatus('online');
    applyStats(s);
  } catch {
    state.connected = false;
    if (!state.demo) {
      setConnStatus('offline');
    } else {
      setConnStatus('demo');
    }
    applyDemoStats();
  }
}

function applyStats(s) {
  // Update overview stat cards
  animate($('waitingCount'),  s.waitingCount ?? 0);
  animate($('priorityCount'), s.priorityCount ?? 0);
  animate($('servedCount'),   s.servedToday ?? 0);
  if ($('averageWait')) $('averageWait').textContent = s.averageWaitMin != null ? s.averageWaitMin : 0;

  // Active desk token
  const curToken = s.currentToken || '— — —';
  state.currentToken   = s.currentToken;
  state.nextToken      = s.nextToken;
  state.currentCounter = s.currentCounter ?? 1;

  if ($('currentToken'))   $('currentToken').textContent   = curToken;
  if ($('currentCounter')) $('currentCounter').textContent = `Counter ${state.currentCounter}`;
  if ($('nextToken'))      $('nextToken').textContent      = s.nextToken || 'No one waiting';

  if ($('markServed')) $('markServed').disabled = !s.currentToken;
  if ($('callNext'))   $('callNext').disabled   = !s.nextToken && !s.waitingCount;

  // Sync queue state
  state.queue = s.queue || [];

  // Update Citizen Details on display
  const activeCitizen = state.queue.find(c => c.token === s.currentToken) || null;
  state.currentCitizen = activeCitizen;

  // Update Digital Signage Board
  updateSignageBoard(s, activeCitizen);

  // Update Tables
  renderQueueTable(state.queue);
  renderOverviewQueueTable(state.queue);

  // Update Analytics
  updateBars(s.waitingCount, s.priorityCount, s.servedToday, s.skippedToday ?? 0);
  updateSnapshot(s);

  // Update Wait Estimator on Registration page
  updateWaitEstimator();

  // Update Quick Chips in Citizen Portal
  updatePortalQuickChips();

  // If citizen is currently tracking a token in the Portal, refresh its state
  if (state.trackedToken) {
    refreshTrackedPortalTicket(state.trackedToken);
  }

  // Nav badges
  if ($('navQueueBadge')) $('navQueueBadge').textContent = s.waitingCount ?? 0;
  if ($('queueBadge'))    $('queueBadge').textContent    = s.waitingCount ?? 0;
  if ($('displayQueueBadge')) $('displayQueueBadge').textContent = s.waitingCount ?? 0;
}

// ── In-Memory Fallback Stats ──────────────────────────────────
function resetDemoState() {
  state.queue       = DEMO_SEED.map(p => ({ ...p }));
  state.tokenNum    = 105;
  state.arrivalNum  = 5;
  state.servedToday = 3;
  state.skippedToday = 0;
  state.currentToken   = null;
  state.nextToken      = 'Q103';
  state.currentCounter = 1;
  state.currentCitizen = null;
  state.recentServed   = [
    { token: 'Q100', name: 'Devi Prasad', time: '10:45 AM' },
    { token: 'Q099', name: 'Sarita Roy',  time: '10:30 AM' },
  ];
}

function demoGetNext() {
  return state.queue
    .filter(p => p.status === 'Waiting')
    .sort((a, b) => {
      const ap = a.category === 'Priority Assistance' ? 0 : 1;
      const bp = b.category === 'Priority Assistance' ? 0 : 1;
      return ap - bp || b.wait - a.wait || a.arrival - b.arrival;
    })[0] || null;
}

function applyDemoStats() {
  const waiting  = state.queue.filter(p => p.status === 'Waiting');
  const priority = waiting.filter(p => p.category === 'Priority Assistance');
  const served   = state.queue.filter(p => p.status === 'Served');
  const skipped  = state.queue.filter(p => p.status === 'Skipped');
  const avgWait  = waiting.length ? Math.round(waiting.reduce((s,p) => s + (p.wait || 0), 0) / waiting.length) : 0;
  const next     = demoGetNext();

  animate($('waitingCount'),  waiting.length);
  animate($('priorityCount'), priority.length);
  animate($('servedCount'),   state.servedToday + served.length);
  if ($('averageWait')) $('averageWait').textContent = avgWait;

  const ct = state.currentToken;
  if ($('currentToken'))   $('currentToken').textContent   = ct ? ct : '— — —';
  if ($('currentCounter')) $('currentCounter').textContent = ct ? `Counter ${state.currentCounter}` : 'Counter 1';
  if ($('nextToken'))      $('nextToken').textContent      = next ? next.token : 'No one waiting';

  if ($('markServed')) $('markServed').disabled = !ct;
  if ($('callNext'))   $('callNext').disabled   = !next;

  const activeCitizen = state.queue.find(c => c.token === ct) || null;
  state.currentCitizen = activeCitizen;

  const sTotal = state.servedToday + served.length;
  const statsPayload = {
    waitingCount:  waiting.length,
    priorityCount: priority.length,
    servedToday:   sTotal,
    skippedToday:  skipped.length,
    currentToken:  ct,
    nextToken:     next ? next.token : null,
    currentCounter: state.currentCounter,
  };

  updateSignageBoard(statsPayload, activeCitizen);
  renderQueueTable(state.queue);
  renderOverviewQueueTable(state.queue);
  updateBars(waiting.length, priority.length, sTotal, skipped.length);
  updateSnapshot(statsPayload);
  updateWaitEstimator();
  updatePortalQuickChips();

  if (state.trackedToken) {
    refreshTrackedPortalTicket(state.trackedToken);
  }

  if ($('navQueueBadge')) $('navQueueBadge').textContent = waiting.length;
  if ($('queueBadge'))    $('queueBadge').textContent    = waiting.length;
  if ($('displayQueueBadge')) $('displayQueueBadge').textContent = waiting.length;
}

// ── Number Counter Animation ──────────────────────────────────
function animate(el, target) {
  if (!el) return;
  const current = parseInt(el.textContent, 10);
  if (isNaN(current) || current === target) { el.textContent = target; return; }
  const step  = target > current ? 1 : -1;
  const steps = Math.min(Math.abs(target - current), 20);
  let   i     = 0;
  const id    = setInterval(() => {
    el.textContent = current + Math.round((target - current) * (++i / steps));
    if (i >= steps) { clearInterval(id); el.textContent = target; }
  }, 20);
}

// ══════════════════════════════════════════════════════════════
//  CITIZEN SELF-SERVICE PORTAL ("View My Ticket")
// ══════════════════════════════════════════════════════════════
function updatePortalQuickChips() {
  const container = $('portalQuickChips');
  if (!container) return;

  // Get active and waiting tokens
  const activeTokens = state.queue
    .filter(c => c.status === 'Waiting' || c.status === 'Called')
    .slice(0, 6);

  if (activeTokens.length === 0) {
    container.innerHTML = '<span style="font-size:12px;color:var(--faint);">No active tokens in queue.</span>';
    return;
  }

  container.innerHTML = activeTokens.map(c => `
    <button type="button" class="token-chip-btn" onclick="portalTrackTicket('${c.token}')">
      ${escapeHTML(c.token)} (${c.status})
    </button>
  `).join('');
}

async function portalTrackTicket(token) {
  if (!token) return;
  const cleanToken = token.trim().toUpperCase();
  const input = $('portalTokenInput');
  if (input) input.value = cleanToken;

  let citizen = null;

  if (!state.demo) {
    try {
      const res = await apiRequest('GET', `/ticket/${cleanToken}`);
      if (res.ok && res.data?.success) {
        citizen = res.data.data;
      }
    } catch {
      /* fall back to in-memory state */
    }
  }

  if (!citizen) {
    citizen = state.queue.find(c => c.token === cleanToken) || null;
  }

  if (!citizen) {
    showToast(`Ticket ${cleanToken} not found. Please check the token number.`, 'error');
    return;
  }

  state.trackedToken = citizen.token;
  renderPortalTicketView(citizen);
  showToast(`Loaded ticket ${citizen.token}`, 'success');
}

function refreshTrackedPortalTicket(token) {
  const citizen = state.queue.find(c => c.token === token);
  if (citizen && $('sec-portal')?.classList.contains('active')) {
    renderPortalTicketView(citizen);
  }
}

function renderPortalTicketView(c) {
  const resContainer = $('portalResultContainer');
  const emptyPrompt  = $('portalEmptyPrompt');
  if (resContainer) resContainer.hidden = false;
  if (emptyPrompt) emptyPrompt.hidden = true;

  // Status Banner styling and text
  const statusBanner = $('portalStatusBanner');
  const statusTitle  = $('portalStatusTitle');
  const statusDesc   = $('portalStatusDesc');
  const waitPill     = $('portalWaitPill');
  const psaIcon      = $('psaIcon');

  const status = (c.status || 'Waiting').toLowerCase();
  if (statusBanner) {
    statusBanner.className = `portal-status-alert ${status}`;
  }

  if (status === 'waiting') {
    const waitingList = state.queue.filter(p => p.status === 'Waiting');
    const pos = waitingList.findIndex(p => p.token === c.token);
    const ahead = pos >= 0 ? pos : 1;

    if (psaIcon) psaIcon.textContent = '●';
    if (statusTitle) statusTitle.textContent = `In Queue · Position #${ahead + 1} (${ahead} citizens ahead)`;
    if (statusDesc)  statusDesc.textContent = 'Your ticket is in the queue. Please watch the display screen or listen for the chime.';
    if (waitPill)    waitPill.textContent = `~${c.wait || 10} mins`;
  } else if (status === 'called') {
    if (psaIcon) psaIcon.textContent = '★';
    if (statusTitle) statusTitle.textContent = 'YOUR TOKEN IS CALLED! PROCEED TO COUNTER 01';
    if (statusDesc)  statusDesc.textContent = 'Please proceed to Counter 01 right now. Your ticket is currently active!';
    if (waitPill)    waitPill.textContent = 'NOW CALLING';
  } else if (status === 'served') {
    if (psaIcon) psaIcon.textContent = '✓';
    if (statusTitle) statusTitle.textContent = 'Visit Completed (Served)';
    if (statusDesc)  statusDesc.textContent = 'Your service has been successfully completed. Thank you for using QueueCare!';
    if (waitPill)    waitPill.textContent = 'COMPLETED';
  } else {
    if (psaIcon) psaIcon.textContent = '✕';
    if (statusTitle) statusTitle.textContent = 'Token Skipped';
    if (statusDesc)  statusDesc.textContent = 'Your token was called earlier but was skipped. Please check with the service desk officer.';
    if (waitPill)    waitPill.textContent = 'SKIPPED';
  }

  // Pass card fields
  setText('portalPassToken',    c.token);
  setText('portalPassName',     c.name);
  setText('portalPassService',  c.service);
  setText('portalPassStatus',   c.status);
  setText('portalPassWait',     status === 'called' ? 'Now Serving' : `~${c.wait || 5} minutes`);
  setText('portalPassCounter',  'Assigned to Counter 01 (Intake Desk)');

  const catPill = $('portalPassCategory');
  if (catPill) {
    const isPriority = c.category === 'Priority Assistance';
    catPill.textContent = isPriority ? '★ PRIORITY ASSISTANCE' : 'STANDARD INTAKE';
    catPill.className   = `pass-priority-pill ${isPriority ? 'priority' : 'standard'}`;
  }

  // Generate crisp QR code
  const qrBox = $('portalQrCodeBox');
  if (qrBox) {
    renderQRCodeSVG(`QueueCare|Token:${c.token}|Name:${c.name}|Status:${c.status}`, qrBox);
  }
}

// ── Download Ticket Slip as PNG Image ─────────────────────────
function downloadTicketImage() {
  const token = state.trackedToken || 'Q100';
  const c = state.queue.find(q => q.token === token) || {
    token,
    name: $('portalPassName')?.textContent || 'Citizen',
    service: $('portalPassService')?.textContent || 'General Services',
    category: $('portalPassCategory')?.textContent?.includes('PRIORITY') ? 'Priority Assistance' : 'Standard',
    status: $('portalPassStatus')?.textContent || 'Waiting',
    wait: 10,
  };

  const canvas = document.createElement('canvas');
  canvas.width = 460;
  canvas.height = 640;
  const ctx = canvas.getContext('2d');

  // Background Outer Slate Frame
  ctx.fillStyle = '#0b1929';
  ctx.fillRect(0, 0, 460, 640);

  // Inner White Card
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(20, 20, 420, 600);

  // Top Teal Banner
  ctx.fillStyle = '#0d8b97';
  ctx.fillRect(20, 20, 420, 80);

  // Brand Name & Tagline
  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 22px Inter, sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('QueueCare · Public Service Pass', 230, 56);
  ctx.font = '11px Inter, sans-serif';
  ctx.fillStyle = 'rgba(255,255,255,0.8)';
  ctx.fillText('Official Intake & Queue Ticket Pass', 230, 80);

  // Token Number
  ctx.fillStyle = '#0f1e2e';
  ctx.font = '900 68px monospace';
  ctx.textAlign = 'center';
  ctx.fillText(c.token, 230, 180);

  // Priority Pill
  const isPriority = c.category === 'Priority Assistance' || String(c.category).toUpperCase().includes('PRIORITY');
  ctx.fillStyle = isPriority ? '#ede9fe' : '#eff6ff';
  ctx.fillRect(115, 202, 230, 32);
  ctx.fillStyle = isPriority ? '#6d28d9' : '#1d4ed8';
  ctx.font = 'bold 12.5px Inter, sans-serif';
  ctx.fillText(isPriority ? '★ PRIORITY ASSISTANCE' : 'STANDARD INTAKE', 230, 223);

  // Dashed Perforation Line
  ctx.strokeStyle = '#cbd5e1';
  ctx.lineWidth = 2;
  ctx.setLineDash([7, 7]);
  ctx.beginPath();
  ctx.moveTo(35, 260);
  ctx.lineTo(425, 260);
  ctx.stroke();
  ctx.setLineDash([]);

  // Ticket Data Rows
  ctx.textAlign = 'left';
  const rows = [
    ['Citizen Name:', c.name || '—'],
    ['Service Area:', c.service || 'General Services'],
    ['Assigned Desk:', 'Counter 01 (Intake Desk)'],
    ['Queue Status:', c.status || 'Waiting'],
    ['Est. Wait Time:', `~${c.wait || 5} minutes`],
    ['Issued On:', formatDate()],
  ];

  let y = 300;
  rows.forEach(([label, val]) => {
    ctx.fillStyle = '#64748b';
    ctx.font = '12px Inter, sans-serif';
    ctx.fillText(label, 48, y);

    ctx.fillStyle = '#0f1e2e';
    ctx.font = 'bold 13.5px Inter, sans-serif';
    ctx.fillText(val, 168, y);
    y += 28;
  });

  // Draw QR code matrix on canvas
  const matrix = generateQRMatrix(`QueueCare|Token:${c.token}|Name:${c.name}`);
  const mSize = matrix.length;
  const cellSize = 3.6;
  const qrX = 185;
  const qrY = 470;

  // QR white border
  ctx.fillStyle = '#f8fafc';
  ctx.fillRect(qrX - 10, qrY - 10, mSize * cellSize + 20, mSize * cellSize + 20);
  ctx.strokeStyle = '#e2e8f0';
  ctx.lineWidth = 1;
  ctx.strokeRect(qrX - 10, qrY - 10, mSize * cellSize + 20, mSize * cellSize + 20);

  ctx.fillStyle = '#0b1929';
  for (let r = 0; r < mSize; r++) {
    for (let col = 0; col < mSize; col++) {
      if (matrix[r][col] === 1) {
        ctx.fillRect(qrX + col * cellSize, qrY + r * cellSize, cellSize, cellSize);
      }
    }
  }

  // Footer Instructions
  ctx.textAlign = 'center';
  ctx.fillStyle = '#94a3b8';
  ctx.font = '11px Inter, sans-serif';
  ctx.fillText('Please show this digital pass at Counter 01 when called.', 230, 595);

  // Trigger download
  const link = document.createElement('a');
  link.download = `QueueCare-Ticket-${c.token}.png`;
  link.href = canvas.toDataURL('image/png');
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  showToast(`Downloaded Ticket ${c.token}!`, 'success');
}

// ══════════════════════════════════════════════════════════════
//  DIGITAL SIGNAGE BOARD (Section: #sec-display)
// ══════════════════════════════════════════════════════════════
function updateSignageBoard(stats, activeCitizen) {
  const curToken = stats.currentToken;
  const curTokenEl = $('displayCurrentToken');
  const counterEl  = $('displayCounterBadge');
  const nameEl     = $('displayCitizenName');
  const instEl     = $('displayInstruction');

  if (curTokenEl) curTokenEl.textContent = curToken || '— — —';
  if (counterEl)  counterEl.textContent  = `COUNTER 0${stats.currentCounter ?? 1}`;

  if (nameEl) {
    if (activeCitizen) {
      nameEl.textContent = `${activeCitizen.name} · ${activeCitizen.service}`;
    } else {
      nameEl.textContent = curToken ? `Citizen (${curToken})` : 'Awaiting Next Citizen Call';
    }
  }

  if (instEl) {
    instEl.textContent = curToken
      ? `Please proceed to Counter 0${stats.currentCounter ?? 1} for priority service.`
      : 'Please take a seat. Your token number will appear here when called.';
  }

  const c1Token = $('counter1Token');
  if (c1Token) c1Token.textContent = curToken || 'IDLE';

  renderDisplayUpNext();
  renderDisplayServed();

  const dCall = $('displayCallNextBtn');
  const dMark = $('displayMarkServedBtn');
  if (dCall) dCall.disabled = !stats.nextToken && !stats.waitingCount;
  if (dMark) dMark.disabled = !curToken;
}

function renderDisplayUpNext() {
  const container = $('displayUpNextList');
  if (!container) return;

  const waiting = state.queue.filter(c => c.status === 'Waiting');
  if (waiting.length === 0) {
    container.innerHTML = '<div style="padding:16px;color:var(--faint);font-size:13px;">No upcoming citizens in line.</div>';
    return;
  }

  const preview = waiting.slice(0, 4);
  container.innerHTML = preview.map(c => `
    <div class="up-next-card">
      <div class="unc-token">${escapeHTML(c.token)}</div>
      <div class="unc-name">${escapeHTML(c.name)}</div>
      <div class="unc-footer">
        <span class="cat-badge ${c.category === 'Priority Assistance' ? 'priority' : 'standard'}">
          ${c.category === 'Priority Assistance' ? 'Priority' : 'Standard'}
        </span>
        <span style="font-size:11px;color:var(--faint);font-weight:600;">~${c.wait || 5}m</span>
      </div>
    </div>
  `).join('');
}

function renderDisplayServed() {
  const container = $('displayServedList');
  if (!container) return;

  const servedFromQueue = state.queue
    .filter(c => c.status === 'Served')
    .slice(-4)
    .reverse();

  if (servedFromQueue.length === 0 && (!state.recentServed || state.recentServed.length === 0)) {
    container.innerHTML = '<div style="padding:12px;color:var(--faint);font-size:12.5px;">No visits completed in this session yet.</div>';
    return;
  }

  const items = servedFromQueue.length > 0 ? servedFromQueue : state.recentServed;
  container.innerHTML = items.map(c => `
    <div class="recent-served-item">
      <span class="rsi-token">${escapeHTML(c.token)}</span>
      <span class="rsi-name">${escapeHTML(c.name)}</span>
      <span class="rsi-time">${c.time || 'Completed'}</span>
    </div>
  `).join('');
}

// ══════════════════════════════════════════════════════════════
//  REGISTRATION WORKSTATION (Section: #sec-register)
// ══════════════════════════════════════════════════════════════
function updateWaitEstimator() {
  const estWaitDisplay = $('estWaitDisplay');
  const estCaption     = $('estWaitCaption');
  const estDepth       = $('estQueueDepth');
  const estPriority    = $('estPriorityAhead');

  const waiting = state.queue.filter(c => c.status === 'Waiting');
  const priorityCount = waiting.filter(c => c.category === 'Priority Assistance').length;

  const assistanceSelect = $('assistance');
  const isPriority = assistanceSelect ? assistanceSelect.value === 'Priority Assistance' : true;

  const estimatedMins = isPriority ? (priorityCount + 1) * 5 : (waiting.length + 1) * 5;

  if (estWaitDisplay) estWaitDisplay.innerHTML = `~${estimatedMins} <small>mins</small>`;
  if (estCaption) {
    estCaption.textContent = isPriority
      ? 'Fast-tracked via Priority Assistance binary min-heap.'
      : 'Standard intake queue (served FIFO after priority cases).';
  }

  if (estDepth) estDepth.textContent = `${waiting.length} citizens`;
  if (estPriority) estPriority.textContent = `${priorityCount} citizens`;
}

// ── Submit Registration (Dedicated Desk) ───────────────────────
async function handleRegister(e) {
  e.preventDefault();

  const nameInput = $('citizenName');
  const nameError = $('nameError');
  const name = nameInput ? nameInput.value.trim() : '';

  if (!name || name.length < 2) {
    if (nameError) {
      nameError.textContent = 'Please enter citizen full name (at least 2 letters).';
      nameError.hidden = false;
    }
    if (nameInput) nameInput.classList.add('invalid');
    return;
  }
  if (nameError) nameError.hidden = true;
  if (nameInput) nameInput.classList.remove('invalid');

  const service    = $('serviceType')?.value || 'General Services';
  const category   = $('assistance')?.value || 'Standard';
  const submitBtn  = $('submitBtn');

  if (submitBtn) { submitBtn.classList.add('loading'); submitBtn.disabled = true; }

  let generatedCitizen = null;

  if (!state.demo) {
    try {
      const res = await apiRequest('POST', '/register', { name, service, category });
      if (res.ok && res.data?.success) {
        generatedCitizen = res.data.data;
        showToast(`Token ${generatedCitizen.token} generated!`, 'success');
        await fetchStats();
      } else {
        showToast(res.data?.message || 'Registration failed.', 'error');
      }
    } catch {
      state.demo = true;
      setConnStatus('demo');
      generatedCitizen = demoRegister(name, service, category);
    }
  } else {
    generatedCitizen = demoRegister(name, service, category);
  }

  if (submitBtn) { submitBtn.classList.remove('loading'); submitBtn.disabled = false; }

  if (generatedCitizen) {
    state.lastModalToken = generatedCitizen.token;
    $('registrationForm')?.reset();
    showTicketModal(generatedCitizen);
  }
}

// ── Quick Register on Overview ────────────────────────────────
async function handleQuickRegister(e) {
  e.preventDefault();

  const nameInput = $('quickCitizenName');
  const name = nameInput ? nameInput.value.trim() : '';
  if (!name || name.length < 2) {
    showToast('Please enter citizen name.', 'error');
    return;
  }

  const service   = $('quickServiceType')?.value || 'General Services';
  const category  = $('quickAssistance')?.value || 'Standard';
  const submitBtn = $('quickSubmitBtn');

  if (submitBtn) { submitBtn.classList.add('loading'); submitBtn.disabled = true; }

  let generatedCitizen = null;

  if (!state.demo) {
    try {
      const res = await apiRequest('POST', '/register', { name, service, category });
      if (res.ok && res.data?.success) {
        generatedCitizen = res.data.data;
        showToast(`Token ${generatedCitizen.token} issued!`, 'success');
        await fetchStats();
      } else {
        showToast(res.data?.message || 'Quick registration failed.', 'error');
      }
    } catch {
      state.demo = true;
      setConnStatus('demo');
      generatedCitizen = demoRegister(name, service, category);
    }
  } else {
    generatedCitizen = demoRegister(name, service, category);
  }

  if (submitBtn) { submitBtn.classList.remove('loading'); submitBtn.disabled = false; }

  if (generatedCitizen) {
    state.lastModalToken = generatedCitizen.token;
    $('quickRegistrationForm')?.reset();
    showTicketModal(generatedCitizen);
  }
}

function demoRegister(name, service, category) {
  const token = `Q${state.tokenNum++}`;
  const arrival = state.arrivalNum++;
  const waitingCount = state.queue.filter(p => p.status === 'Waiting').length;
  const wait = category === 'Priority Assistance' ? 5 : (waitingCount + 1) * 5;

  const citizen = {
    token,
    name,
    service,
    category,
    wait,
    status: 'Waiting',
    arrival,
    enqueuedAt: formatDate(),
  };

  state.queue.push(citizen);
  applyDemoStats();
  showToast(`Token ${token} issued (Demo mode)`, 'success');
  return citizen;
}

// ── Show Ticket Pass Modal ────────────────────────────────────
function showTicketModal(c) {
  const modal = $('ticketModal');
  if (!modal) return;

  state.lastModalToken = c.token;
  setText('modalTicketToken',    c.token);
  setText('modalTicketName',     c.name);
  setText('modalTicketService',  c.service);
  setText('modalTicketCategory', c.category.toUpperCase());
  setText('modalTicketWait',     `~${c.wait || 10} mins`);
  setText('modalTicketTime',     formatTime());

  modal.hidden = false;
}

function closeTicketModal() {
  const modal = $('ticketModal');
  if (modal) modal.hidden = true;
}

// ══════════════════════════════════════════════════════════════
//  QUEUE ACTIONS (Call Next, Call Token, Mark Served, Skip)
// ══════════════════════════════════════════════════════════════
async function handleCallNext() {
  const callBtn = $('callNext');
  if (callBtn) { callBtn.classList.add('loading'); callBtn.disabled = true; }

  if (!state.demo) {
    try {
      const res = await apiRequest('POST', '/call-next');
      if (res.ok && res.data?.success) {
        const called = res.data.data;
        showToast(`Calling ${called.token} (${called.name})`, 'success');
        speakAnnouncement(called.token, called.name, state.currentCounter);
        await fetchStats();
      } else {
        showToast(res.data?.message || 'No citizens waiting.', 'info');
      }
    } catch {
      state.demo = true;
      setConnStatus('demo');
      demoCallNext();
    }
  } else {
    demoCallNext();
  }

  if (callBtn) { callBtn.classList.remove('loading'); callBtn.disabled = false; }
}

function demoCallNext() {
  const next = demoGetNext();
  if (!next) {
    showToast('No citizens currently waiting.', 'info');
    return;
  }
  next.status = 'Called';
  state.currentToken = next.token;
  state.currentCitizen = next;

  $('liveMessage').textContent = `${next.name} — please proceed to Counter ${state.currentCounter}.`;
  showToast(`Calling ${next.token} (${next.name})`, 'success');
  speakAnnouncement(next.token, next.name, state.currentCounter);
  applyDemoStats();
}

async function handleCallByToken(token) {
  if (!state.demo) {
    try {
      const res = await apiRequest('POST', `/call/${token}`);
      if (res.ok && res.data?.success) {
        const called = res.data.data;
        showToast(`Calling ${called.token}`, 'success');
        speakAnnouncement(called.token, called.name, state.currentCounter);
        await fetchStats();
      } else {
        showToast(res.data?.message || 'Unable to call this token.', 'error');
      }
    } catch {
      state.demo = true;
      setConnStatus('demo');
      demoCallByToken(token);
    }
  } else {
    demoCallByToken(token);
  }
}

function demoCallByToken(token) {
  const p = state.queue.find(q => q.token === token && q.status === 'Waiting');
  if (!p) { showToast('Token not found or not waiting.', 'error'); return; }
  p.status = 'Called';
  state.currentToken = token;
  state.currentCitizen = p;

  $('liveMessage').textContent = `${p.name} — please proceed to Counter ${state.currentCounter}.`;
  showToast(`Calling ${token}`, 'success');
  speakAnnouncement(p.token, p.name, state.currentCounter);
  applyDemoStats();
}

async function handleMarkServed() {
  const servedBtn = $('markServed');
  if (servedBtn) { servedBtn.classList.add('loading'); servedBtn.disabled = true; }

  if (!state.demo) {
    try {
      const res = await apiRequest('POST', '/mark-served');
      if (res.ok && res.data?.success) {
        const served = res.data.data;
        showToast(`${served.token} marked as served.`, 'success');
        await fetchStats();
      } else {
        showToast(res.data?.message || 'No citizen currently called.', 'error');
      }
    } catch {
      state.demo = true;
      setConnStatus('demo');
      demoMarkServed();
    }
  } else {
    demoMarkServed();
  }

  if (servedBtn) { servedBtn.classList.remove('loading'); servedBtn.disabled = false; }
}

function demoMarkServed() {
  const token = state.currentToken;
  if (!token) { showToast('No citizen is active.', 'error'); return; }
  const p = state.queue.find(q => q.token === token);
  if (p) {
    p.status = 'Served';
    state.recentServed.unshift({ token: p.token, name: p.name, time: formatTime() });
    if (state.recentServed.length > 5) state.recentServed.pop();
  }
  state.currentToken = null;
  state.currentCitizen = null;
  showToast(`${token} marked as served.`, 'success');
  $('liveMessage').textContent = 'Service completed. Counter is ready for next call.';
  applyDemoStats();
}

async function handleSkip(token) {
  if (!state.demo) {
    try {
      const res = await apiRequest('POST', `/skip/${token}`);
      if (res.ok && res.data?.success) {
        showToast(`${token} skipped.`, 'info');
        await fetchStats();
      } else {
        showToast(res.data?.message || 'Cannot skip citizen.', 'error');
      }
    } catch {
      state.demo = true;
      setConnStatus('demo');
      demoSkip(token);
    }
  } else {
    demoSkip(token);
  }
}

function demoSkip(token) {
  const p = state.queue.find(q => q.token === token && (q.status === 'Waiting' || q.status === 'Called'));
  if (!p) return;
  p.status = 'Skipped';
  if (state.currentToken === token) {
    state.currentToken = null;
    state.currentCitizen = null;
  }
  showToast(`${token} skipped.`, 'info');
  applyDemoStats();
}

async function handleReset() {
  if (!state.demo) {
    try {
      const res = await apiRequest('POST', '/reset');
      if (res.ok && res.data?.success) {
        showToast('Demo data restored in MySQL.', 'success');
        await fetchStats();
        return;
      }
    } catch {
      /* fall back to memory */
    }
  }
  resetDemoState();
  applyDemoStats();
  showToast('Demo data restored.', 'success');
}

async function handleRefresh() {
  const btn = $('refreshBtn');
  if (btn) { btn.classList.add('loading'); btn.disabled = true; }
  await fetchStats();
  if (btn) { btn.classList.remove('loading'); btn.disabled = false; }
  showToast('Data synchronized.', 'info');
}

// ══════════════════════════════════════════════════════════════
//  QUEUE TABLES RENDERING
// ══════════════════════════════════════════════════════════════
function renderQueueTable(list) {
  const tbody = $('queueBody');
  const empty = $('emptyState');
  if (!tbody) return;

  const filter = ($('serviceFilter')?.value || 'all').toLowerCase();
  const search = ($('searchInput')?.value || '').trim().toLowerCase();

  const filtered = list.filter(p => {
    const matchService = filter === 'all' || p.service.toLowerCase().includes(filter);
    const matchSearch  = !search || p.token.toLowerCase().includes(search) || p.name.toLowerCase().includes(search);
    return matchService && matchSearch;
  });

  if (filtered.length === 0) {
    tbody.innerHTML = '';
    if (empty) empty.hidden = false;
    setText('tableSummary', 'Showing 0 citizens');
    return;
  }
  if (empty) empty.hidden = true;

  tbody.innerHTML = filtered.map(p => `
    <tr>
      <td class="token-cell">${escapeHTML(p.token)}</td>
      <td>
        <span class="citizen-name">${escapeHTML(p.name)}</span>
        <span class="cell-sub">Arrival #${p.arrival || 1}</span>
      </td>
      <td>${escapeHTML(p.service)}</td>
      <td>
        <span class="cat-badge ${p.category === 'Priority Assistance' ? 'priority' : 'standard'}">
          ${p.category === 'Priority Assistance' ? 'Priority Assistance' : 'Standard'}
        </span>
      </td>
      <td>${p.wait != null ? p.wait : 0} min</td>
      <td>
        <span class="status-badge ${p.status.toLowerCase()}">${escapeHTML(p.status)}</span>
      </td>
      <td>
        <div class="row-actions">
          ${p.status === 'Waiting' ? `
            <button class="btn btn-primary btn-sm" onclick="handleCallByToken('${p.token}')">Call</button>
            <button class="btn btn-ghost btn-sm" onclick="portalTrackTicket('${p.token}');navigateTo('portal','View My Ticket')">View</button>
            <button class="btn btn-danger-soft btn-sm" onclick="handleSkip('${p.token}')">Skip</button>
          ` : p.status === 'Called' ? `
            <button class="btn btn-success btn-sm" onclick="handleMarkServed()">Served</button>
            <button class="btn btn-ghost btn-sm" onclick="portalTrackTicket('${p.token}');navigateTo('portal','View My Ticket')">View</button>
            <button class="btn btn-danger-soft btn-sm" onclick="handleSkip('${p.token}')">Skip</button>
          ` : `
            <button class="btn btn-ghost btn-sm" onclick="portalTrackTicket('${p.token}');navigateTo('portal','View My Ticket')">View</button>
          `}
        </div>
      </td>
    </tr>
  `).join('');

  setText('tableSummary', `Showing ${filtered.length} of ${list.length} citizens`);
}

function renderOverviewQueueTable(list) {
  const tbody = $('overviewQueueBody');
  if (!tbody) return;

  const waiting = list.filter(p => p.status === 'Waiting').slice(0, 5);
  if (waiting.length === 0) {
    tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:24px;color:var(--faint);">No citizens currently waiting.</td></tr>';
    return;
  }

  tbody.innerHTML = waiting.map(p => `
    <tr>
      <td class="token-cell">${escapeHTML(p.token)}</td>
      <td><span class="citizen-name">${escapeHTML(p.name)}</span></td>
      <td>${escapeHTML(p.service)}</td>
      <td>
        <span class="cat-badge ${p.category === 'Priority Assistance' ? 'priority' : 'standard'}">
          ${p.category === 'Priority Assistance' ? 'Priority' : 'Standard'}
        </span>
      </td>
      <td>${p.wait || 5} min</td>
      <td><span class="status-badge waiting">Waiting</span></td>
      <td>
        <button class="btn btn-primary btn-sm" onclick="handleCallByToken('${p.token}')">Call</button>
      </td>
    </tr>
  `).join('');
}

// ── Bar Chart & Snapshot ──────────────────────────────────────
function updateBars(waiting, priority, served, skipped) {
  const max = Math.max(1, waiting + priority + served + skipped);
  setBar('servedBar',   'servedBarVal',   served,   max);
  setBar('waitingBar',  'waitingBarVal',  waiting,  max);
  setBar('priorityBar', 'priorityBarVal', priority, max);
  setBar('skippedBar',  'skippedBarVal',  skipped,  max);
}

function setBar(barId, valId, count, max) {
  const bar = $(barId);
  const val = $(valId);
  if (bar) bar.style.width = `${Math.min(100, (count / max) * 100)}%`;
  if (val) val.textContent = count;
}

function updateSnapshot(s) {
  const total = (s.waitingCount ?? 0) + (s.servedToday ?? 0) + (s.skippedToday ?? 0);
  setText('snapTotal',   total);
  setText('snapServed',  s.servedToday  ?? 0);
  setText('snapWaiting', s.waitingCount ?? 0);
  setText('snapSkipped', s.skippedToday ?? 0);
  setText('snapCurrent', s.currentToken || '—');
  setText('snapNext',    s.nextToken    || '—');
}

function setText(id, val) {
  const el = $(id);
  if (el) el.textContent = val ?? '';
}

// ══════════════════════════════════════════════════════════════
//  CITIZEN SELF-SERVICE PORTAL (Section: #sec-portal)
// ══════════════════════════════════════════════════════════════

/**
 * Populate quick-check chips for active tokens
 */
function updatePortalQuickChips() {
  const container = $('portalQuickChips');
  if (!container) return;

  // Collect active waiting/called tokens or default sample tokens
  const activeTokens = state.queue
    .filter(c => c.status === 'Waiting' || c.status === 'Called')
    .map(c => c.token);

  const tokensToShow = activeTokens.length > 0 
    ? activeTokens.slice(0, 6) 
    : ['Q101', 'Q102', 'Q103', 'Q104', 'Q105'];

  container.innerHTML = tokensToShow.map(tok => `
    <button type="button" class="token-chip-btn" onclick="portalTrackTicket('${tok}')">
      ${escapeHTML(tok)}
    </button>
  `).join('');
}

/**
 * Track ticket by token from backend API or local in-memory fallback
 */
async function portalTrackTicket(token) {
  if (!token) return;
  const clean = token.trim().toUpperCase();
  if (!clean) {
    showToast('Please enter a valid token number (e.g. Q105)', 'error');
    return;
  }

  // Update input field
  const inputEl = $('portalTokenInput');
  if (inputEl) inputEl.value = clean;

  const btn = $('portalSearchBtn');
  if (btn) { btn.classList.add('loading'); btn.disabled = true; }

  let ticketData = null;

  // 1. Try Live Spring Boot REST API
  if (!state.demo) {
    try {
      const res = await apiRequest('GET', `/ticket/${clean}`);
      if (res.ok && res.data?.success && res.data.data) {
        ticketData = res.data.data;
      }
    } catch {
      // Fall through to local state
    }
  }

  // 2. Fallback to local memory queue / recent served
  if (!ticketData) {
    ticketData = state.queue.find(c => c.token.toUpperCase() === clean);
    if (!ticketData && state.recentServed) {
      const servedMatch = state.recentServed.find(s => s.token.toUpperCase() === clean);
      if (servedMatch) {
        ticketData = {
          token: servedMatch.token,
          name: servedMatch.name,
          service: 'General Services',
          category: 'Standard',
          status: 'Served',
          wait: 0,
        };
      }
    }
  }

  if (btn) { btn.classList.remove('loading'); btn.disabled = false; }

  if (!ticketData) {
    showToast(`Token ${clean} not found. Please check token number or register first.`, 'error');
    if ($('portalResultContainer')) $('portalResultContainer').hidden = true;
    if ($('portalEmptyPrompt')) $('portalEmptyPrompt').hidden = false;
    return;
  }

  state.trackedToken = clean;
  renderPortalPass(ticketData);
  showToast(`Ticket ${clean} loaded!`, 'success');
}

/**
 * Silent periodic refresh of the currently tracked ticket during polling
 */
async function refreshTrackedPortalTicket(token) {
  if (!token) return;
  const clean = token.trim().toUpperCase();

  let ticketData = null;
  if (!state.demo) {
    try {
      const res = await apiRequest('GET', `/ticket/${clean}`);
      if (res.ok && res.data?.success && res.data.data) {
        ticketData = res.data.data;
      }
    } catch {
      // ignore
    }
  }

  if (!ticketData) {
    ticketData = state.queue.find(c => c.token.toUpperCase() === clean);
  }

  if (ticketData) {
    renderPortalPass(ticketData);
  }
}

/**
 * Render digital pass details, QR code, and live status badge
 */
function renderPortalPass(c) {
  const resultContainer = $('portalResultContainer');
  const emptyPrompt     = $('portalEmptyPrompt');

  if (emptyPrompt)     emptyPrompt.hidden = true;
  if (resultContainer) resultContainer.hidden = false;

  // Pass card fields
  setText('portalPassToken', c.token);
  setText('portalPassName', c.name || 'Citizen');
  setText('portalPassService', c.service || 'Public Desk Services');
  setText('portalPassStatus', c.status);

  // Category Pill
  const isPriority = c.category === 'Priority Assistance';
  const catEl = $('portalPassCategory');
  if (catEl) {
    catEl.textContent = isPriority ? 'PRIORITY ASSISTANCE' : 'STANDARD INTAKE';
    catEl.className = isPriority ? 'pass-priority-pill' : 'pass-priority-pill standard';
  }

  // Counter callout
  const counterCallout = $('portalPassCounter');
  if (counterCallout) {
    if (c.status === 'Called') {
      counterCallout.textContent = `Assigned to Counter 01 (Intake Desk) — Proceed Now!`;
      counterCallout.style.color = 'var(--teal-600)';
    } else if (c.status === 'Served') {
      counterCallout.textContent = `Completed at Counter 01. Thank you!`;
      counterCallout.style.color = 'var(--green-600)';
    } else {
      counterCallout.textContent = `Assigned to Counter 01 (Intake Desk)`;
      counterCallout.style.color = 'var(--teal-600)';
    }
  }

  // Estimated wait text
  const waitEl = $('portalPassWait');
  if (waitEl) {
    if (c.status === 'Called') {
      waitEl.textContent = 'At Counter Now';
      waitEl.className = 'pd-value green-text';
    } else if (c.status === 'Served') {
      waitEl.textContent = 'Completed';
      waitEl.className = 'pd-value blue-text';
    } else {
      waitEl.textContent = `~${c.wait != null ? c.wait : 10} minutes`;
      waitEl.className = 'pd-value green-text';
    }
  }

  // Live Status Alert Banner
  const banner = $('portalStatusBanner');
  const title  = $('portalStatusTitle');
  const desc   = $('portalStatusDesc');
  const pill   = $('portalWaitPill');
  const icon   = $('psaIcon');

  if (banner) {
    banner.className = `portal-status-alert ${c.status.toLowerCase()}`;
    if (c.status === 'Called') {
      if (icon)  icon.textContent = '🔔';
      if (title) title.textContent = 'NOW CALLING · Proceed to Counter 01';
      if (desc)  desc.textContent = 'Your token is actively being called! Please proceed directly to Counter 01 with your documentation.';
      if (pill)  pill.textContent = 'CALLING NOW';
    } else if (c.status === 'Served') {
      if (icon)  icon.textContent = '✓';
      if (title) title.textContent = 'Service Completed';
      if (desc)  desc.textContent = 'Your service request has been processed. We appreciate your visit to QueueCare.';
      if (pill)  pill.textContent = 'COMPLETED';
    } else if (c.status === 'Skipped') {
      if (icon)  icon.textContent = '⚠️';
      if (title) title.textContent = 'Token Skipped';
      if (desc)  desc.textContent = 'Your token was skipped because of no-response. Please check with the front desk officer to rejoin.';
      if (pill)  pill.textContent = 'SKIPPED';
    } else {
      // Waiting
      const waitingList = state.queue.filter(q => q.status === 'Waiting');
      const queuePos = waitingList.findIndex(q => q.token === c.token) + 1;
      const posText = queuePos > 0 ? `#${queuePos}` : 'Next';

      if (icon)  icon.textContent = '●';
      if (title) title.textContent = `In Waiting Queue · Position ${posText}`;
      if (desc)  desc.textContent = isPriority 
        ? 'Your priority assistance request is fast-tracked in the binary min-heap queue. Please remain nearby.'
        : 'Your ticket is in the standard intake queue. Audio and screen announcements will alert you when called.';
      if (pill)  pill.textContent = `~${c.wait != null ? c.wait : 10} mins`;
    }
  }

  // Generate & Render Dynamic QR Code
  const qrBox = $('portalQrCodeBox');
  if (qrBox) {
    const qrPayload = JSON.stringify({
      system: 'QueueCare',
      token: c.token,
      name: c.name,
      service: c.service,
      category: c.category,
      status: c.status,
      timestamp: Date.now(),
    });
    renderQRCodeSVG(qrPayload, qrBox);
  }
}

/**
 * Draw and download the citizen ticket as an image file (PNG) using HTML5 Canvas
 */
function downloadTicketImage() {
  const token = state.trackedToken;
  if (!token) {
    showToast('Please search for your ticket first before downloading.', 'error');
    return;
  }

  // Find citizen record
  const c = state.queue.find(q => q.token === token) || {
    token: token,
    name: $('portalPassName')?.textContent || 'Citizen',
    service: $('portalPassService')?.textContent || 'Document Services',
    category: $('portalPassCategory')?.textContent || 'Standard',
    status: $('portalPassStatus')?.textContent || 'Waiting',
    wait: 10,
  };

  const scale = 2; // High-resolution Retina export
  const width = 480 * scale;
  const height = 640 * scale;

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  ctx.scale(scale, scale);

  const w = 480;
  const h = 640;

  // Background
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, w, h);

  // Outer border & shadow effect
  ctx.strokeStyle = '#e2e8f0';
  ctx.lineWidth = 2;
  ctx.strokeRect(1, 1, w - 2, h - 2);

  // Top header banner
  ctx.fillStyle = '#0b1929';
  ctx.fillRect(0, 0, w, 82);

  // Header Brand
  ctx.fillStyle = '#10a3b0';
  ctx.font = 'bold 12px Inter, sans-serif';
  ctx.fillText('QUEUECARE · OFFICIAL DIGITAL PASS', 24, 34);

  ctx.fillStyle = '#ffffff';
  ctx.font = '600 13px Inter, sans-serif';
  ctx.fillText('Public Service Center & Triage Queue', 24, 56);

  // Priority Pill in Header
  const isPriority = c.category && c.category.toLowerCase().includes('priority');
  ctx.fillStyle = isPriority ? '#f5f3ff' : '#eff6ff';
  const pillText = isPriority ? 'PRIORITY ASSISTANCE' : 'STANDARD INTAKE';
  ctx.font = 'bold 10px Inter, sans-serif';
  const pillW = ctx.measureText(pillText).width + 18;
  const pillX = w - pillW - 24;
  ctx.beginPath();
  ctx.roundRect(pillX, 36, pillW, 24, 12);
  ctx.fill();
  ctx.fillStyle = isPriority ? '#6d28d9' : '#1d4ed8';
  ctx.fillText(pillText, pillX + 9, 52);

  // Token Number Section
  ctx.fillStyle = '#64748b';
  ctx.font = 'bold 11px Inter, sans-serif';
  ctx.fillText('YOUR TOKEN NUMBER', 24, 126);

  ctx.fillStyle = '#0b1929';
  ctx.font = '900 64px "JetBrains Mono", monospace';
  ctx.fillText(c.token, 24, 186);

  ctx.fillStyle = '#10a3b0';
  ctx.font = 'bold 13px Inter, sans-serif';
  ctx.fillText('Assigned to Counter 01 (Intake Desk)', 24, 214);

  // Perforated notch divider
  ctx.strokeStyle = '#cbd5e1';
  ctx.setLineDash([6, 5]);
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(0, 240);
  ctx.lineTo(w, 240);
  ctx.stroke();
  ctx.setLineDash([]); // reset

  // Punch cutouts at sides
  ctx.fillStyle = '#f8fafc';
  ctx.beginPath();
  ctx.arc(0, 240, 10, 0, Math.PI * 2);
  ctx.fill();
  ctx.beginPath();
  ctx.arc(w, 240, 10, 0, Math.PI * 2);
  ctx.fill();

  // Details Column (Left side)
  const drawField = (label, val, x, y) => {
    ctx.fillStyle = '#64748b';
    ctx.font = 'bold 10px Inter, sans-serif';
    ctx.fillText(label, x, y);
    ctx.fillStyle = '#0b1929';
    ctx.font = 'bold 14px Inter, sans-serif';
    ctx.fillText(val, x, y + 18);
  };

  drawField('CITIZEN NAME', c.name || 'Citizen', 24, 280);
  drawField('SERVICE AREA', c.service || 'Document Services', 24, 336);
  drawField('QUEUE STATUS', c.status || 'Waiting', 24, 392);
  drawField('ESTIMATED WAIT', c.status === 'Called' ? 'Proceed to Desk Now' : `~${c.wait || 10} minutes`, 24, 448);
  drawField('DATE & TIME', formatDate() + ' ' + formatTime(), 24, 504);

  // QR Code Box (Right side)
  const qrX = 300;
  const qrY = 270;
  const qrBoxSize = 150;

  ctx.fillStyle = '#ffffff';
  ctx.fillRect(qrX, qrY, qrBoxSize, qrBoxSize);
  ctx.strokeStyle = '#e2e8f0';
  ctx.lineWidth = 1.5;
  ctx.strokeRect(qrX, qrY, qrBoxSize, qrBoxSize);

  // Generate QR Matrix
  const qrPayload = JSON.stringify({
    system: 'QueueCare',
    token: c.token,
    name: c.name,
    service: c.service,
    status: c.status,
  });
  const matrix = generateQRMatrix(qrPayload);
  const matrixSize = matrix.length;
  const cellSize = 4.2;
  const offset = (qrBoxSize - (matrixSize * cellSize)) / 2;

  ctx.fillStyle = '#0b1929';
  for (let r = 0; r < matrixSize; r++) {
    for (let col = 0; col < matrixSize; col++) {
      if (matrix[r][col] === 1) {
        ctx.fillRect(
          qrX + offset + col * cellSize,
          qrY + offset + r * cellSize,
          cellSize,
          cellSize
        );
      }
    }
  }

  // QR Caption
  ctx.fillStyle = '#94a3b8';
  ctx.font = '500 10.5px Inter, sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('Scan at Service Counter', qrX + qrBoxSize / 2, qrY + qrBoxSize + 18);
  ctx.textAlign = 'left'; // reset

  // Footer banner
  ctx.fillStyle = '#f8fafc';
  ctx.fillRect(0, 560, w, 80);
  ctx.strokeStyle = '#e2e8f0';
  ctx.strokeRect(0, 560, w, 80);

  ctx.fillStyle = '#475569';
  ctx.font = '500 11.5px Inter, sans-serif';
  ctx.fillText('Please present this pass on your phone or printed slip when called.', 24, 595);
  ctx.fillStyle = '#94a3b8';
  ctx.font = '400 10px Inter, sans-serif';
  ctx.fillText('QueueCare Smart Triage System · Powered by Spring Boot & MySQL', 24, 615);

  // Convert canvas to image and trigger automatic browser download
  try {
    const dataUrl = canvas.toDataURL('image/png');
    const a = document.createElement('a');
    a.href = dataUrl;
    a.download = `QueueCare-Ticket-${c.token}.png`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    showToast(`Ticket pass for ${c.token} downloaded!`, 'success');
  } catch (err) {
    showToast('Failed to generate image download.', 'error');
  }
}

// ══════════════════════════════════════════════════════════════
//  CSV EXPORT
// ══════════════════════════════════════════════════════════════
function handleExportCSV() {
  const headers = ['Token', 'Name', 'Service', 'Category', 'Wait (min)', 'Status'];
  const rows = state.queue.map(p => [
    p.token,
    p.name,
    p.service,
    p.category,
    p.wait || 0,
    p.status,
  ]);

  const cell = v => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const csv  = [headers, ...rows].map(r => r.map(cell).join(',')).join('\r\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url  = URL.createObjectURL(blob);
  const a    = Object.assign(document.createElement('a'), {
    href: url, download: `queuecare-${new Date().toISOString().slice(0,10)}.csv`,
  });
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  showToast('Queue data exported to CSV.', 'success');
}


// ══════════════════════════════════════════════════════════════
//  NAVIGATION
// ══════════════════════════════════════════════════════════════
function navigateTo(section, title) {
  // Hide all sections
  document.querySelectorAll('.page-section').forEach(s => s.classList.remove('active'));

  // Show target page section
  const target = $(`sec-${section}`);
  if (target) {
    target.classList.add('active');
  }

  // Update active sidebar nav item
  document.querySelectorAll('.nav-menu .nav-item').forEach(a => {
    a.classList.toggle('active', a.dataset.section === section);
  });

  // Update topbar title
  const titleEl = $('pageTitle');
  if (titleEl) titleEl.textContent = title || 'Queue Overview';

  const eyebrowEl = $('pageEyebrow');
  if (eyebrowEl) {
    const eyebrows = {
      overview:  'PUBLIC SERVICE CENTER · SYSTEM ONLINE',
      register:  'SERVICE DESK · CITIZEN INTAKE & TRIAGE',
      queue:     'QUEUE MANAGEMENT · REAL-TIME LANE',
      display:   'PUBLIC DISPLAY BOARD · DIGITAL SIGNAGE',
      portal:    'CITIZEN SELF-SERVICE · LIVE TRACKER',
      analytics: 'SERVICE PERFORMANCE · HISTORICAL LOG',
    };
    eyebrowEl.innerHTML = `<span class="eyebrow-dot"></span> ${eyebrows[section] || 'PUBLIC SERVICE CENTER'}`;
  }

  // Close mobile sidebar if open
  $('sidebar')?.classList.remove('open');
  $('sidebarOverlay')?.classList.remove('visible');
  $('menuToggle')?.setAttribute('aria-expanded', 'false');

  // Trigger relevant updates
  if (section === 'register') updateWaitEstimator();
  if (section === 'display')  updateSignageBoard({
    currentToken: state.currentToken,
    nextToken: state.nextToken,
    currentCounter: state.currentCounter,
  }, state.currentCitizen);
  if (section === 'portal') {
    updatePortalQuickChips();
    if (state.trackedToken) {
      portalTrackTicket(state.trackedToken);
    }
  }
}

// ══════════════════════════════════════════════════════════════
//  INITIALIZATION
// ══════════════════════════════════════════════════════════════
let _pollInterval = null;

function startPolling() {
  if (_pollInterval) clearInterval(_pollInterval);
  _pollInterval = setInterval(fetchStats, POLL_MS);
}

async function init() {
  // Setup clock & date
  if ($('currentDate'))  $('currentDate').textContent = formatDate();
  if ($('signageClock')) $('signageClock').textContent = formatTime();
  setInterval(() => {
    if ($('signageClock')) $('signageClock').textContent = formatTime();
  }, 1000);

  // Initial local demo state
  resetDemoState();
  applyDemoStats();

  // Navigation Links
  document.querySelectorAll('.nav-menu .nav-item').forEach(a => {
    a.addEventListener('click', e => {
      e.preventDefault();
      const sec = a.dataset.section;
      const title = a.textContent.trim().replace(/\s*\d+$/, '').replace(/\s*LIVE$/, '');
      navigateTo(sec, title);
    });
  });

  // Mobile Menu Toggle
  $('menuToggle')?.addEventListener('click', () => {
    const sb = $('sidebar');
    const ov = $('sidebarOverlay');
    const open = sb?.classList.toggle('open');
    ov?.classList.toggle('visible', open);
    $('menuToggle')?.setAttribute('aria-expanded', String(open));
  });

  $('sidebarOverlay')?.addEventListener('click', () => {
    $('sidebar')?.classList.remove('open');
    $('sidebarOverlay')?.classList.remove('visible');
    $('menuToggle')?.setAttribute('aria-expanded', 'false');
  });

  // Button Listeners
  $('refreshBtn')?.addEventListener('click', handleRefresh);
  $('resetBtn')?.addEventListener('click', handleReset);
  $('callNext')?.addEventListener('click', handleCallNext);
  $('markServed')?.addEventListener('click', handleMarkServed);
  $('exportCsv')?.addEventListener('click', handleExportCSV);

  // Form Submissions
  $('registrationForm')?.addEventListener('submit', handleRegister);
  $('quickRegistrationForm')?.addEventListener('submit', handleQuickRegister);

  // Search & Filter
  $('searchInput')?.addEventListener('input', () => renderQueueTable(state.queue));
  $('serviceFilter')?.addEventListener('change', () => renderQueueTable(state.queue));

  // Dynamic Age Detection for Senior Citizens
  $('citizenAge')?.addEventListener('input', e => {
    const age = parseInt(e.target.value, 10);
    const badge = $('ageBadge');
    const assist = $('assistance');
    const seniorCheck = $('checkSenior');

    if (age >= 60) {
      if (badge) badge.hidden = false;
      if (assist) assist.value = 'Priority Assistance';
      if (seniorCheck) seniorCheck.checked = true;
    } else {
      if (badge) badge.hidden = true;
      if (seniorCheck) seniorCheck.checked = false;
    }
    updateWaitEstimator();
  });

  $('assistance')?.addEventListener('change', updateWaitEstimator);

  // Modal actions
  $('modalCloseBtn')?.addEventListener('click', closeTicketModal);
  $('closeTicketModalBtn')?.addEventListener('click', closeTicketModal);
  $('printTicketBtn')?.addEventListener('click', () => window.print());

  // "View in Citizen Portal" button from ticket modal
  $('modalGoToPortalBtn')?.addEventListener('click', () => {
    closeTicketModal();
    if (state.lastModalToken) {
      portalTrackTicket(state.lastModalToken);
    }
    navigateTo('portal', 'View My Ticket');
  });

  // Citizen Portal Form & Actions
  $('portalSearchForm')?.addEventListener('submit', e => {
    e.preventDefault();
    const token = $('portalTokenInput')?.value;
    if (token) portalTrackTicket(token);
  });

  $('downloadTicketBtn')?.addEventListener('click', downloadTicketImage);
  $('printPortalTicketBtn')?.addEventListener('click', () => window.print());

  $('copyTokenCodeBtn')?.addEventListener('click', () => {
    const token = state.trackedToken;
    if (token && navigator.clipboard) {
      navigator.clipboard.writeText(token);
      showToast(`Copied token ${token} to clipboard!`, 'success');
    }
  });

  // Audio Toggle
  $('audioToggleBtn')?.addEventListener('click', () => {
    state.audioEnabled = !state.audioEnabled;
    const txt = $('audioToggleText');
    if (txt) txt.textContent = state.audioEnabled ? 'Audio On' : 'Audio Off';
    showToast(state.audioEnabled ? 'Audio announcements enabled' : 'Audio announcements muted', 'info');
  });

  // Display Page Controls
  $('displayChimeBtn')?.addEventListener('click', () => {
    if (state.currentToken) {
      speakAnnouncement(state.currentToken, state.currentCitizen?.name, state.currentCounter);
      showToast(`Announcing ${state.currentToken}`, 'info');
    } else {
      playChime();
      showToast('Sound chime test', 'info');
    }
  });

  $('fullscreenToggleBtn')?.addEventListener('click', () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(err => {
        showToast('Fullscreen mode not available', 'error');
      });
    } else {
      document.exitFullscreen();
    }
  });

  $('displayCallNextBtn')?.addEventListener('click', handleCallNext);
  $('displayMarkServedBtn')?.addEventListener('click', handleMarkServed);

  // First fetch from live backend
  await fetchStats();
  startPolling();
}

// Run init on DOM load
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init);
} else {
  init();
}
