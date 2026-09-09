const logEl = document.getElementById("log");
const statsEl = document.getElementById("stats");
const knowledgeEl = document.getElementById("knowledge");
const form = document.getElementById("form");
const input = document.getElementById("text");
const micBtn = document.getElementById("mic");
const screenBtn = document.getElementById("screen");
const screenWatch = document.getElementById("screen-watch");
const screenPreview = document.getElementById("screen-preview");
const screenCaption = document.getElementById("screen-caption");
const statusEl = document.getElementById("voice-status");
const topicEl = document.getElementById("topic");
const modelsEl = document.getElementById("models");
const uploadForm = document.getElementById("upload");
const txtFile = document.getElementById("txt-file");
const uploadStatus = document.getElementById("upload-status");
const spPairForm = document.getElementById("sp-pair");
const spOld = document.getElementById("sp-old");
const spNew = document.getElementById("sp-new");
const spPairStatus = document.getElementById("sp-pair-status");
const improveStatus = document.getElementById("improve-status");
const gameStatus = document.getElementById("game-status");
const radioOnEl = document.getElementById("radio-on");
const secretBtn = document.getElementById("secret");
const gameStage = document.getElementById("game-stage");
const radioWrap = document.getElementById("radio-wrap");
const chatTitle = document.getElementById("chat-title");

const CHEAT_RE = /\b(iddqd|idkfa|idfa|idclip|idspispopd|idchoppers|idmypos|iddt|idclev[0-9]{2}|idbehold[vsiral]?)\b/i;
const IS_SAFARI = /^((?!chrome|android).)*safari/i.test(navigator.userAgent || "");

let sessionId = null;
let voice = { pauseMs: 1500, maxUtteranceMs: 60000, hintIdleMs: 0 };
let listening = false;
let pauseTimer = null;
let maxTimer = null;
let idleTimer = null;
let metaTimer = null;
let doomCi = null;
let doomPlayer = null;
let lastCheat = "нет";
let gameStartedAt = Date.now();
let hintBusy = false;
let radioOn = true;
let doomMode = false;
let doomBooted = false;
let lastInjectedCheat = "";

function roleLabel(role, source) {
  if (role === "user") return "вы";
  if (source === "screen") return "экран";
  if (role === "hint") return doomMode ? "рация" : "подсказка";
  return doomMode ? "напарник" : "помощник";
}

function extractCheat(text) {
  const match = CHEAT_RE.exec(text || "");
  return match ? match[1].toLowerCase() : "";
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function addPtoLink(div, label, url) {
  if (!url) return;
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = /маршрут/i.test(label) ? "inject route" : "inject";
  btn.textContent = label;
  btn.addEventListener("click", () => {
    window.open(url, "_blank", "noopener");
  });
  div.append(btn);
}

function applyPtoTab(tab, openUrl, thenUrl) {
  if (!tab) return;
  try {
    if (openUrl) tab.location = openUrl;
    else tab.close();
  } catch (_) { /* Safari может закрыть окно только по жесту */ }
  if (!thenUrl || thenUrl === openUrl) return;
  window.setTimeout(() => {
    try {
      tab.location = thenUrl;
    } catch (_) { /* вкладку могли закрыть */ }
  }, 1400);
}

function maybeTypeCheat(msg, fromUser) {
  if (!doomMode || !fromUser || !msg || msg.role === "user") return;
  const cheat = extractCheat(msg.text);
  if (!cheat || cheat === lastInjectedCheat) return;
  injectCheat(cheat).catch((err) => {
    if (gameStatus) gameStatus.textContent = err.message;
  });
}

function addMessage(msg, opts) {
  if (msg && msg.skill) setSkillUi(msg.skill);
  if (doomMode && msg && (msg.role === "hint" || msg.role === "assistant") && msg.source !== "system") {
    showRadioOverlay(msg.text);
  }
  const div = document.createElement("div");
  div.className = `msg ${msg.role}`;
  const meta = document.createElement("div");
  meta.className = "meta";
  const latency = msg.latencyMs != null ? ` · ${msg.latencyMs} мс` : "";
  meta.textContent = `${roleLabel(msg.role, msg.source)} · ${msg.source || ""}${latency}`;
  const body = document.createElement("div");
  body.textContent = msg.text;
  div.append(meta, body);
  const cardUrl = msg.cardUrl || "";
  const openUrl = msg.openUrl || "";
  if (cardUrl && openUrl && openUrl !== cardUrl) {
    addPtoLink(div, /yandex|rtext=/i.test(openUrl) ? "Постройте маршрут" : "открыть страницу", openUrl);
    addPtoLink(div, "открыть карточку", cardUrl);
  } else if (cardUrl) {
    addPtoLink(div, "открыть карточку", cardUrl);
  } else if (openUrl) {
    addPtoLink(div, /aisto\.local|\/institutions|\/login|\/grant/i.test(openUrl) ? "открыть страницу" : "открыть ссылку", openUrl);
  }
  const firstUrl = cardUrl || openUrl;
  const routeUrl = /yandex|rtext=/i.test(openUrl) && openUrl !== cardUrl ? openUrl : "";
  applyPtoTab(opts && opts.tab, firstUrl, routeUrl);
  maybeTypeCheat(msg, opts && opts.fromUser);
  const cheat = extractCheat(msg.text);
  if (cheat && msg.role !== "user") {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "inject";
    btn.textContent = `вбить ${cheat} в Doom`;
    btn.addEventListener("click", () => {
      injectCheat(cheat).catch((err) => { gameStatus.textContent = err.message; });
    });
    div.append(btn);
  }
  logEl.append(div);
  logEl.scrollTop = logEl.scrollHeight;
}

function renderSession(session) {
  if (session && session.skill) setSkillUi(session.skill);
  logEl.replaceChildren();
  (session.messages || []).forEach(addMessage);
}

function setSkillUi(skill) {
  const el = document.getElementById("skill-label");
  if (!el) return;
  const names = {
    topsp: "TopSP",
    hint: "подсказчик",
    interview: "собеседование",
    "pto-site": "обучалка ПТО",
    doom: "Doom-рация"
  };
  el.textContent = "скил: " + (names[skill] || skill || "TopSP");
}

function stat(title, value, how) {
  return `<div><dt>${title} <span class="val">${value}</span></dt><dd>${how}</dd></div>`;
}

function renderMetrics(m) {
  statsEl.innerHTML = [
    stat("ходы", m.turns, doomMode ? "вопросы в рацию" : "реплики на встрече"),
    stat("эфир", m.hints, doomMode ? "автоподсказки рации" : "подсказки с эфира"),
    stat("экран", m.screens || 0, doomMode ? "кадры Doom" : "на созвоне кадр выключен"),
    stat("критик", m.improvementCycles, "GLM в фоне учит факты"),
    stat("KB", m.knowledgeDocs, "база seed + выученное + txt"),
    stat("качество", m.lastQuality, "оценка критика 0-10"),
    stat("avg", `${m.avgLatencyMs} мс`, "ответ чата"),
  ].join("");
  if (m.pendingImprove > 0) {
    improveStatus.textContent = "критик GLM ещё пишет оценку...";
  } else if (m.lastLearned && m.lastFact) {
    improveStatus.textContent = "выучен факт, следующая рация уже может на него опереться.";
  } else {
    improveStatus.textContent = "";
  }
  const factEl = document.getElementById("last-fact");
  if (m.lastFact) {
    factEl.textContent = (m.lastLearned ? "новый факт: " : "критик без записи: ") + m.lastFact + (m.lastReason ? " · " + m.lastReason : "");
  } else if (m.lastReason) {
    factEl.textContent = m.lastReason;
  } else {
    factEl.textContent = "после ответа GLM может запомнить, где вы тупите.";
  }
}

async function api(path, options, timeoutMs) {
  const ctrl = timeoutMs ? new AbortController() : null;
  const timer = timeoutMs ? setTimeout(() => ctrl.abort(), timeoutMs) : null;
  try {
    const res = await fetch(path, { ...options, signal: ctrl ? ctrl.signal : undefined });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || res.statusText);
    return data;
  } catch (err) {
    if (err && err.name === "AbortError") {
      throw new Error("Infereco молчит, рация повторит");
    }
    throw err;
  } finally {
    if (timer) clearTimeout(timer);
  }
}

async function ensureSession() {
  if (sessionId) {
    try {
      await api(`/api/sessions/${sessionId}`);
      return sessionId;
    } catch (_) {
      sessionId = null;
    }
  }
  const session = await api("/api/sessions", { method: "POST" });
  sessionId = session.id;
  renderSession(session);
  if (statusEl) {
    statusEl.textContent = "сессия обновлена после перезапуска бэка";
  }
  return sessionId;
}

function isSessionLost(err) {
  const text = err && err.message ? err.message : String(err || "");
  return /session not found/i.test(text);
}

async function refreshMeta() {
  const [metrics, knowledge, specs, tasks, code, telegram] = await Promise.all([
    api("/api/metrics"),
    api("/api/knowledge"),
    api("/api/specs").catch(() => ({ files: [], chunks: 0 })),
    api("/api/tasks").catch(() => []),
    api("/api/code").catch(() => ({ files: 0, chunks: 0, roots: [] })),
    api("/api/telegram").catch(() => ({ enabled: false, chats: 0, hint: "статус недоступен" })),
  ]);
  const telegramStatus = document.getElementById("telegram-status");
  if (telegramStatus) {
    telegramStatus.textContent = telegram.hint || (telegram.enabled ? "бот есть" : "бота нет");
  }
  renderMetrics(metrics);
  knowledgeEl.replaceChildren();
  knowledge
    .filter((doc) => doc.source !== "spec" && doc.source !== "code")
    .forEach((doc) => {
      const li = document.createElement("li");
      li.className = doc.source === "learned" ? "learned" : "";
      const extra = doc.chunks > 1 ? ` · ${doc.chunks} куска` : "";
      li.textContent = `${doc.title} (${doc.source}${extra})`;
      knowledgeEl.append(li);
    });
  const specsStatus = document.getElementById("specs-status");
  const specsEl = document.getElementById("specs");
  if (specsStatus) {
    const count = (specs.files || []).length;
    specsStatus.textContent = count
      ? `${count} файла в RAG, внутри нарезаны на куски для поиска`
      : "СП ещё грузятся или папка пуста";
  }
  if (specsEl) {
    specsEl.replaceChildren();
    (specs.files || []).forEach((file) => {
      const li = document.createElement("li");
      const specGroup = knowledge.find((doc) => doc.source === "spec" && doc.title === file.file);
      li.textContent = specGroup && specGroup.chunks > 1
        ? `${file.file} · ${specGroup.chunks} кусков`
        : file.file;
      specsEl.append(li);
    });
  }
  const codeStatus = document.getElementById("code-status");
  const codeEl = document.getElementById("code");
  if (codeStatus) {
    codeStatus.textContent = code.files
      ? `${code.files} исходников, ${code.chunks} кусков`
      : "код ещё грузится";
  }
  if (codeEl) {
    codeEl.replaceChildren();
    (code.roots || []).forEach((root) => {
      const li = document.createElement("li");
      li.textContent = root.exists ? root.name : `${root.name} (нет папки)`;
      codeEl.append(li);
    });
  }
  const tasksEl = document.getElementById("tasks");
  if (tasksEl) {
    tasksEl.replaceChildren();
    (tasks || []).forEach((task) => {
      const li = document.createElement("li");
      li.textContent = task.title || task.file;
      tasksEl.append(li);
    });
  }
  return metrics;
}

function watchImprove() {
  clearInterval(metaTimer);
  let ticks = 0;
  metaTimer = setInterval(async () => {
    ticks += 1;
    try {
      const metrics = await refreshMeta();
      if (metrics.pendingImprove === 0 || ticks > 20) clearInterval(metaTimer);
    } catch (_) {
      clearInterval(metaTimer);
    }
  }, 1000);
}

function showRadioOverlay(text) {
  const overlay = document.getElementById("radio-overlay");
  const line = document.getElementById("radio-line");
  if (line) {
    const clean = (text || "").trim();
    if (clean) line.textContent = clean;
  }
  if (overlay) overlay.hidden = false;
  const deck = document.getElementById("radio-deck");
  if (deck) deck.hidden = false;
}

function setRadioLive(text) {
  const live = document.getElementById("radio-live");
  if (live) live.textContent = text || "слушаю тебя...";
  const deck = document.getElementById("radio-deck");
  if (deck && doomMode) deck.hidden = false;
}

function muteDoomAudio(ci) {
  try {
    if (doomPlayer && typeof doomPlayer.setVolume === "function") doomPlayer.setVolume(0);
  } catch (_) { /* ignore */ }
  try {
    if (ci && typeof ci.mute === "function") ci.mute();
  } catch (_) { /* ignore */ }
  document.querySelectorAll("#dos audio, #dos video").forEach((el) => {
    el.muted = true;
    el.volume = 0;
  });
}

const MOVE_CODE = /^(Arrow|KeyW|KeyA|KeyS|KeyD|Control|Alt|Space|Shift|Numpad)/;
let movingCodes = new Set();
let gameWatchTimer = null;
let lastGameVisionAt = 0;
let lastGameSig = "";
const gameCanvas = document.createElement("canvas");

function playerMoving() {
  return movingCodes.size > 0;
}

function scaleCanvasJpeg(source, maxW, quality) {
  const w0 = source.width;
  const h0 = source.height;
  if (!w0 || !h0) return null;
  const scale = Math.min(1, maxW / w0);
  const w = Math.max(160, Math.round(w0 * scale));
  const h = Math.max(90, Math.round(h0 * scale));
  gameCanvas.width = w;
  gameCanvas.height = h;
  const ctx = gameCanvas.getContext("2d");
  ctx.drawImage(source, 0, 0, w, h);
  return gameCanvas.toDataURL("image/jpeg", quality);
}

function imageDataToJpeg(shot) {
  const tmp = document.createElement("canvas");
  tmp.width = shot.width;
  tmp.height = shot.height;
  tmp.getContext("2d").putImageData(shot, 0, 0);
  return scaleCanvasJpeg(tmp, 640, 0.72);
}

async function grabDoomJpeg() {
  if (doomCi && typeof doomCi.screenshot === "function") {
    try {
      const shot = await Promise.race([
        doomCi.screenshot(),
        new Promise((_, reject) => setTimeout(() => reject(new Error("timeout")), 900)),
      ]);
      if (typeof shot === "string" && shot.startsWith("data:")) return shot;
      if (shot && shot.data && shot.width) return imageDataToJpeg(shot);
      if (shot instanceof HTMLCanvasElement) return scaleCanvasJpeg(shot, 640, 0.72);
    } catch (_) { /* fallback to canvas */ }
  }
  const canvas = document.querySelector("#dos canvas");
  if (!canvas || canvas.width < 16) return null;
  try {
    return scaleCanvasJpeg(canvas, 640, 0.72);
  } catch (_) {
    return null;
  }
}

async function captureDoomFrame(force) {
  if (!doomMode || !sessionId || screenBusy) return;
  if (!force && !playerMoving()) return;
  const dataUrl = await grabDoomJpeg();
  if (!dataUrl) return;
  const sig = frameSignature(gameCanvas);
  if (!force && !sigChanged(lastGameSig, sig)) return;
  lastGameSig = sig;
  lastGameVisionAt = Date.now();
  const preview = document.getElementById("radio-frame");
  if (preview) {
    preview.hidden = false;
    preview.src = dataUrl;
  }
  screenBusy = true;
  try {
    const msg = await api(`/api/sessions/${sessionId}/screen`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ image: dataUrl }),
    }, 25000);
    addMessage(msg);
    showRadioOverlay(msg.text);
  } catch (err) {
    if (statusEl) statusEl.textContent = err.message;
  } finally {
    screenBusy = false;
  }
}

function startGameWatch() {
  stopGameWatch();
  window.addEventListener("keydown", onDoomKeyDown, true);
  window.addEventListener("keyup", onDoomKeyUp, true);
  gameWatchTimer = setInterval(() => {
    captureDoomFrame(false).catch(() => {});
  }, 1600);
}

function stopGameWatch() {
  clearInterval(gameWatchTimer);
  gameWatchTimer = null;
  window.removeEventListener("keydown", onDoomKeyDown, true);
  window.removeEventListener("keyup", onDoomKeyUp, true);
  movingCodes.clear();
}

function onDoomKeyDown(event) {
  if (!doomMode || !MOVE_CODE.test(event.code)) return;
  const first = movingCodes.size === 0;
  movingCodes.add(event.code);
  if (first) captureDoomFrame(true).catch(() => {});
}

function onDoomKeyUp(event) {
  movingCodes.delete(event.code);
}

async function injectCheat(code) {
  if (!doomCi || typeof doomCi.sendKeyEvent !== "function") {
    throw new Error("Doom ещё не готов. кликни Play в кадре.");
  }
  const canvas = document.querySelector("#dos canvas");
  if (canvas) {
    canvas.tabIndex = 0;
    try { canvas.focus(); } catch (_) { /* ignore */ }
  }
  const cheat = String(code).toLowerCase();
  for (const ch of cheat) {
    const key = ch.charCodeAt(0);
    doomCi.sendKeyEvent(key, true);
    await sleep(45);
    doomCi.sendKeyEvent(key, false);
    await sleep(55);
  }
  lastCheat = cheat;
  lastInjectedCheat = cheat;
  gameStatus.textContent = `вбил ${cheat}`;
}

function bootDoom() {
  if (doomBooted) return;
  doomBooted = true;
  if (typeof Dos !== "function") {
    gameStatus.textContent = "js-dos не загрузился. нужен интернет до v8.js-dos.com";
    return;
  }
  if (window.emulators && !window.emulators.pathPrefix) {
    window.emulators.pathPrefix = "https://v8.js-dos.com/latest/";
  }
  doomPlayer = Dos(document.getElementById("dos"), {
    url: "/doom/doom.jsdos",
    autoStart: true,
    kiosk: true,
    theme: "dark",
    noCloud: true,
    volume: 0,
    imageRendering: "pixelated",
    onEvent: (event, ci) => {
      if (event === "ci-ready" && ci) {
        doomCi = ci;
        muteDoomAudio(ci);
        setTimeout(() => muteDoomAudio(ci), 400);
        setTimeout(() => muteDoomAudio(ci), 2000);
        startGameWatch();
        const canvas = document.querySelector("#dos canvas");
        if (canvas) {
          canvas.tabIndex = 0;
          try { canvas.focus(); } catch (_) { /* ignore */ }
        }
        gameStatus.textContent = "звук выключен. ходьба: рация смотрит кадр. микрофон: твои слова сверху. скажи «бог» для iddqd.";
      }
    },
  });
  if (doomPlayer && typeof doomPlayer.setVolume === "function") {
    doomPlayer.setVolume(0);
  }
}

async function sendText(text, source, shown, tab) {
  if (!text.trim()) return;
  await ensureSession();
  if (!sessionId) return;
  input.value = "";
  if (!shown) {
    addMessage({ role: "user", text, source, latencyMs: null });
  }
  statusEl.textContent = doomMode ? "напарник отвечает..." : "модель отвечает...";
  let msg;
  try {
    msg = await api(`/api/sessions/${sessionId}/messages`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text, source }),
    }, 35000);
  } catch (err) {
    if (!isSessionLost(err)) throw err;
    sessionId = null;
    await ensureSession();
    msg = await api(`/api/sessions/${sessionId}/messages`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text, source }),
    }, 35000);
  }
  addMessage(msg, { tab, fromUser: true });
  const cheat = extractCheat(msg.text);
  if (cheat && doomMode && gameStatus) {
    gameStatus.textContent = `рация предлагает ${cheat}. вбиваю в кадр.`;
  }
  await refreshMeta();
  watchImprove();
  if (doomMode) armIdleHint();
  statusEl.textContent = listening || airOn ? "эфир включён. говорите дальше." : "можно говорить или писать";
}

async function requestHint() {
  if (!sessionId || hintBusy || !radioOn || !doomMode) return;
  if (Date.now() - lastGameVisionAt < 5000) {
    armIdleHint();
    return;
  }
  hintBusy = true;
  try {
    const elapsedSec = Math.floor((Date.now() - gameStartedAt) / 1000);
    const msg = await api(`/api/sessions/${sessionId}/hints`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        elapsedSec,
        lastCheat,
        playing: Boolean(doomCi),
      }),
    }, 12000);
    addMessage(msg);
    await refreshMeta();
  } finally {
    hintBusy = false;
    armIdleHint();
  }
}

function armIdleHint() {
  clearTimeout(idleTimer);
  if (!doomMode || !radioOn) return;
  const wait = voice.hintIdleMs > 0 ? voice.hintIdleMs : 8000;
  idleTimer = setTimeout(() => {
    requestHint().catch((err) => {
      statusEl.textContent = err.message;
    });
  }, wait);
}

function setMicUi(on) {
  listening = on;
  micBtn.classList.toggle("live", on);
  micBtn.textContent = on ? "стоп" : "старт";
}

const TARGET_RATE = 16000;
let mediaStream = null;
let audioCtx = null;
let workletNode = null;
let mixNode = null;
let sourceNode = null;
let muteNode = null;
let micGain = null;
let displayAudioSource = null;
let displayAudioGain = null;
let sttSocket = null;
let liveEl = null;
let liveSending = false;
let pendingFinals = [];
let airOn = false;

function downsample(float32, fromRate, toRate) {
  if (fromRate === toRate) return float32;
  const ratio = fromRate / toRate;
  const out = new Float32Array(Math.floor(float32.length / ratio));
  for (let i = 0; i < out.length; i++) {
    out[i] = float32[Math.floor(i * ratio)];
  }
  return out;
}

function floatToPcm16(float32) {
  const pcm = new Int16Array(float32.length);
  for (let i = 0; i < float32.length; i++) {
    const s = Math.max(-1, Math.min(1, float32[i]));
    pcm[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
  }
  return pcm;
}

function ensureLiveBubble() {
  if (liveEl && liveEl.isConnected) return liveEl;
  liveEl = document.createElement("div");
  liveEl.className = "msg user live";
  const meta = document.createElement("div");
  meta.className = "meta";
  meta.textContent = doomMode ? "ты · микрофон" : "эфир · собеседующий";
  const body = document.createElement("div");
  body.className = "live-body";
  body.textContent = "слушаю...";
  liveEl.append(meta, body);
  logEl.append(liveEl);
  logEl.scrollTop = logEl.scrollHeight;
  return liveEl;
}

function setLiveText(text) {
  const el = ensureLiveBubble();
  el.querySelector(".live-body").textContent = text || "слушаю...";
  logEl.scrollTop = logEl.scrollHeight;
  if (doomMode) setRadioLive(text || "слушаю тебя...");
}

function commitLive(text) {
  if (liveEl && liveEl.isConnected) {
    liveEl.classList.remove("live");
    liveEl.querySelector(".meta").textContent = doomMode ? "ты · voice" : "эфир · voice";
    liveEl.querySelector(".live-body").textContent = text;
  }
  liveEl = null;
  if (doomMode) setRadioLive(text);
}

function dropLive() {
  if (liveEl && liveEl.isConnected) liveEl.remove();
  liveEl = null;
}

async function waitSttReady() {
  for (let i = 0; i < 90; i++) {
    const stt = await api("/api/stt");
    if (stt.ready) return stt;
    statusEl.textContent = stt.status === "downloading"
      ? "скачиваю русскую модель Vosk (~45 МБ), один раз..."
      : (stt.error ? `STT: ${stt.error}` : "готовлю локальное распознавание...");
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error("модель распознавания не успела загрузиться. обновите страницу.");
}

function stopCaptureNodes() {
  if (workletNode) {
    try { workletNode.port.onmessage = null; workletNode.disconnect(); } catch (_) { /* ignore */ }
  }
  if (sourceNode) try { sourceNode.disconnect(); } catch (_) { /* ignore */ }
  if (micGain) try { micGain.disconnect(); } catch (_) { /* ignore */ }
  if (displayAudioSource) try { displayAudioSource.disconnect(); } catch (_) { /* ignore */ }
  if (displayAudioGain) try { displayAudioGain.disconnect(); } catch (_) { /* ignore */ }
  if (mixNode) try { mixNode.disconnect(); } catch (_) { /* ignore */ }
  if (muteNode) try { muteNode.disconnect(); } catch (_) { /* ignore */ }
  if (audioCtx) try { audioCtx.close(); } catch (_) { /* ignore */ }
  if (mediaStream) mediaStream.getTracks().forEach((t) => t.stop());
  workletNode = null;
  sourceNode = null;
  micGain = null;
  displayAudioSource = null;
  displayAudioGain = null;
  mixNode = null;
  muteNode = null;
  audioCtx = null;
  mediaStream = null;
}

function detachDisplayAudio() {
  if (displayAudioSource) try { displayAudioSource.disconnect(); } catch (_) { /* ignore */ }
  if (displayAudioGain) try { displayAudioGain.disconnect(); } catch (_) { /* ignore */ }
  displayAudioSource = null;
  displayAudioGain = null;
}

function closeSttSocket() {
  if (!sttSocket) return;
  try {
    if (sttSocket.readyState === WebSocket.OPEN) sttSocket.send("flush");
    sttSocket.close();
  } catch (_) { /* ignore */ }
  sttSocket = null;
}

function stopEngine() {
  closeSttSocket();
  stopCaptureNodes();
  airOn = false;
  setMicUi(false);
  if (!liveSending) dropLive();
}

async function ensureAudioGraph() {
  if (audioCtx && mixNode && workletNode) return;
  const Ctx = window.AudioContext || window.webkitAudioContext;
  if (!Ctx) throw new Error("нет AudioContext. обновите Safari или Chrome.");
  try {
    audioCtx = new Ctx({ sampleRate: 48000 });
  } catch (_) {
    audioCtx = new Ctx();
  }
  await audioCtx.resume();
  mixNode = audioCtx.createGain();
  mixNode.gain.value = 1;
  muteNode = audioCtx.createGain();
  muteNode.gain.value = 0;
  try {
    await audioCtx.audioWorklet.addModule("/pcm-worklet.js?v=stt5");
    workletNode = new AudioWorkletNode(audioCtx, "pcm-worklet", {
      processorOptions: { targetRate: TARGET_RATE },
    });
    workletNode.port.onmessage = (event) => {
      if (!sttSocket || sttSocket.readyState !== WebSocket.OPEN) return;
      if (sttSocket.bufferedAmount > 64000) return;
      if (event.data && event.data.byteLength >= 320) sttSocket.send(event.data);
    };
    mixNode.connect(workletNode);
    workletNode.connect(muteNode);
  } catch (_) {
    const processor = audioCtx.createScriptProcessor(4096, 1, 1);
    workletNode = processor;
    processor.onaudioprocess = (event) => {
      if (!sttSocket || sttSocket.readyState !== WebSocket.OPEN) return;
      if (sttSocket.bufferedAmount > 64000) return;
      const pcm = floatToPcm16(downsample(event.inputBuffer.getChannelData(0), audioCtx.sampleRate, TARGET_RATE));
      if (pcm.length >= 160) sttSocket.send(pcm.buffer.slice(pcm.byteOffset, pcm.byteOffset + pcm.byteLength));
    };
    mixNode.connect(processor);
    processor.connect(muteNode);
  }
  muteNode.connect(audioCtx.destination);
}

function attachStreamToMix(stream, kind) {
  if (!stream || !audioCtx || !mixNode) return false;
  if (!stream.getAudioTracks().length) return false;
  if (kind === "display") {
    detachDisplayAudio();
  } else {
    if (sourceNode) try { sourceNode.disconnect(); } catch (_) { /* ignore */ }
    if (micGain) try { micGain.disconnect(); } catch (_) { /* ignore */ }
    sourceNode = null;
    micGain = null;
  }
  const src = audioCtx.createMediaStreamSource(stream);
  const gain = audioCtx.createGain();
  gain.gain.value = kind === "display" ? 1.45 : 1;
  src.connect(gain);
  gain.connect(mixNode);
  if (kind === "display") {
    displayAudioSource = src;
    displayAudioGain = gain;
  } else {
    sourceNode = src;
    micGain = gain;
  }
  return true;
}

async function ensureSttPipeline() {
  await waitSttReady();
  if (!sttSocket || sttSocket.readyState !== WebSocket.OPEN) {
    sttSocket = await openSttSocket();
  }
  await ensureAudioGraph();
  airOn = true;
  setLiveText("слушаю эфир...");
}

async function onFinalTranscript(text) {
  const clean = (text || "").trim();
  if (!clean) {
    if (!liveSending && (listening || airOn)) setLiveText("слушаю эфир...");
    return;
  }
  commitLive(clean);
  pendingFinals.push(clean);
  await drainFinals();
}

async function drainFinals() {
  if (liveSending) return;
  liveSending = true;
  try {
    while (pendingFinals.length) {
      const next = pendingFinals.shift();
      let tab = null;
      if (/маршрут|доеха|добрать|гнесин|несен|учрежд|pto|tvorchestvo|aisto|яндекс|организац|грант|карта/i.test(next)) {
        tab = window.open("about:blank", "pto-demo");
      }
      await sendText(next, "voice", true, tab);
    }
    if ((listening || airOn) && !liveEl) setLiveText("слушаю эфир...");
  } catch (err) {
    statusEl.textContent = err.message;
  } finally {
    liveSending = false;
    if (pendingFinals.length) {
      drainFinals().catch((err) => { statusEl.textContent = err.message; });
    }
  }
}

function openSttSocket() {
  return new Promise((resolve, reject) => {
    const proto = location.protocol === "https:" ? "wss:" : "ws:";
    const ws = new WebSocket(`${proto}//${location.host}/ws/stt`);
    ws.binaryType = "arraybuffer";
    let opened = false;
    const timer = setTimeout(() => reject(new Error("нет связи с распознаванием")), 8000);
    ws.onopen = () => { /* wait ready frame */ };
    ws.onerror = () => {
      clearTimeout(timer);
      if (!opened) reject(new Error("WebSocket STT не открылся"));
    };
    ws.onmessage = (event) => {
      let msg = {};
      try { msg = JSON.parse(event.data); } catch (_) { return; }
      if (msg.type === "ready") {
        opened = true;
        clearTimeout(timer);
        resolve(ws);
        return;
      }
      if (msg.type === "error") {
        statusEl.textContent = msg.text || "ошибка STT";
        if (!opened) {
          clearTimeout(timer);
          reject(new Error(msg.text || "ошибка STT"));
        }
        return;
      }
      if (msg.type === "partial") {
        setLiveText(msg.text);
        statusEl.textContent = "эфир: " + (msg.text || "слушаю");
        return;
      }
      if (msg.type === "final") {
        onFinalTranscript(msg.text).catch((err) => { statusEl.textContent = err.message; });
      }
    };
    ws.onclose = () => {
      if (listening || airOn) statusEl.textContent = "поток распознавания закрылся";
    };
  });
}

async function startListening() {
  statusEl.textContent = doomMode
    ? "готовлю твой микрофон для рации..."
    : "готовлю эфир собеседующего и поток Vosk...";
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    statusEl.textContent = "нет getUserMedia. откройте http://localhost:8080 в Safari или Chrome.";
    return;
  }
  try {
    await ensureSttPipeline();
  } catch (err) {
    statusEl.textContent = err.message;
    return;
  }
  try {
    const detailed = doomMode
      ? { audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true } }
      : { audio: { channelCount: 1, echoCancellation: false, noiseSuppression: false, autoGainControl: true } };
    try {
      mediaStream = await navigator.mediaDevices.getUserMedia(detailed);
    } catch (first) {
      if (first && first.name === "NotAllowedError") throw first;
      mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    }
  } catch (err) {
    statusEl.textContent = err.name === "NotAllowedError"
      ? "микрофон запрещён. Safari: Настройки сайта > Микрофон > разрешить."
      : (err.message || "нет доступа к микрофону");
    return;
  }
  attachStreamToMix(mediaStream, "mic");
  setLiveText(doomMode ? "слушаю тебя..." : "слушаю эфир...");
  setMicUi(true);
  statusEl.textContent = doomMode
    ? "рация слушает только твой микрофон. звук Doom выключен."
    : "эфир только с микрофона. экран на созвоне не смотрю, подсказки по голосу.";
}

form.addEventListener("submit", (e) => {
  e.preventDefault();
  const text = input.value;
  let tab = null;
  if (/маршрут|доеха|добрать|гнесин|несен|учрежд|pto|tvorchestvo|aisto|яндекс|организац|грант|карта/i.test(text)) {
    tab = window.open("about:blank", "pto-demo");
  }
  sendText(text, "text", false, tab).catch((err) => {
    applyPtoTab(tab, "");
    statusEl.textContent = err.message;
  });
});

micBtn.addEventListener("click", () => {
  if (listening) {
    if (mediaStream) mediaStream.getTracks().forEach((t) => t.stop());
    mediaStream = null;
    if (sourceNode) try { sourceNode.disconnect(); } catch (_) { /* ignore */ }
    if (micGain) try { micGain.disconnect(); } catch (_) { /* ignore */ }
    sourceNode = null;
    micGain = null;
    setMicUi(false);
    if (!displayStream) {
      stopEngine();
      statusEl.textContent = "эфир выключен";
    } else {
      statusEl.textContent = "микрофон выключен, звук вкладки ещё слушаю";
    }
  } else {
    startListening().catch((err) => { statusEl.textContent = err.message; });
  }
});

let displayStream = null;
let screenTimer = null;
let screenBusy = false;
let lastFrameSig = "";
const screenCanvas = document.createElement("canvas");

function setScreenUi(on) {
  screenBtn.classList.toggle("live", on);
  screenBtn.textContent = on ? "скрыть" : "экран";
  if (screenWatch) screenWatch.hidden = !on && !screenPreview.src;
}

function frameSignature(canvas) {
  const w = 32;
  const h = 18;
  const tmp = document.createElement("canvas");
  tmp.width = w;
  tmp.height = h;
  const ctx = tmp.getContext("2d", { willReadFrequently: true });
  ctx.drawImage(canvas, 0, 0, w, h);
  const data = ctx.getImageData(0, 0, w, h).data;
  let bits = "";
  for (let i = 0; i < data.length; i += 4) {
    bits += data[i] + data[i + 1] + data[i + 2] > 380 ? "1" : "0";
  }
  return bits;
}

function frameChanged(next) {
  return sigChanged(lastFrameSig, next);
}

function sigChanged(prev, next) {
  if (!prev) return true;
  if (!next) return false;
  let diff = 0;
  const n = Math.min(prev.length, next.length);
  for (let i = 0; i < n; i++) {
    if (prev[i] !== next[i]) diff += 1;
  }
  return diff > n * 0.04;
}

function stopScreen() {
  clearInterval(screenTimer);
  screenTimer = null;
  detachDisplayAudio();
  if (displayStream) displayStream.getTracks().forEach((t) => t.stop());
  displayStream = null;
  lastFrameSig = "";
  screenBusy = false;
  setScreenUi(false);
  if (screenCaption) screenCaption.textContent = "экран выключен. последний кадр помню.";
  if (!listening && airOn) {
    stopEngine();
  }
}

async function sendScreenFrame(dataUrl) {
  if (!sessionId || screenBusy) return;
  screenBusy = true;
  if (screenCaption) screenCaption.textContent = "читаю кадр...";
  try {
    const msg = await api(`/api/sessions/${sessionId}/screen`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ image: dataUrl }),
    }, 25000);
    addMessage(msg);
    if (screenCaption) screenCaption.textContent = msg.text;
    await refreshMeta();
  } catch (err) {
    statusEl.textContent = err.message;
    if (screenCaption) screenCaption.textContent = err.message;
  } finally {
    screenBusy = false;
  }
}

function grabScreenJpeg(video) {
  if (!video.videoWidth) return null;
  const maxW = 1440;
  const scale = Math.min(1, maxW / video.videoWidth);
  const w = Math.max(320, Math.round(video.videoWidth * scale));
  const h = Math.max(180, Math.round(video.videoHeight * scale));
  screenCanvas.width = w;
  screenCanvas.height = h;
  const ctx = screenCanvas.getContext("2d");
  ctx.drawImage(video, 0, 0, w, h);
  return screenCanvas.toDataURL("image/jpeg", 0.78);
}

async function captureScreenOnce(video, force) {
  const dataUrl = grabScreenJpeg(video);
  if (!dataUrl) return;
  const sig = frameSignature(screenCanvas);
  if (!force && !frameChanged(sig)) return;
  lastFrameSig = sig;
  if (screenPreview) screenPreview.src = dataUrl;
  await sendScreenFrame(dataUrl);
}

function screenErrorText(err) {
  const name = err && err.name ? err.name : "";
  const raw = err && err.message ? err.message : "";
  if (name === "NotAllowedError" || name === "AbortError") {
    return "захват экрана отменён. нажмите экран ещё раз и выберите окно или весь экран.";
  }
  if (name === "NotSupportedError" || /not supported/i.test(raw)) {
    return "этот браузер не даёт шарить экран. откройте http://localhost:8080 в Safari или Chrome.";
  }
  if (name === "NotFoundError") {
    return "нет источника для захвата. выберите окно сайта или весь экран.";
  }
  return raw || "нет доступа к экрану";
}

async function requestDisplayStream() {
  const attempts = [
    { video: true, audio: true },
    { video: true },
  ];
  let lastErr = null;
  for (const options of attempts) {
    try {
      return await navigator.mediaDevices.getDisplayMedia(options);
    } catch (err) {
      lastErr = err;
      if (err && (err.name === "NotAllowedError" || err.name === "AbortError")) {
        throw err;
      }
    }
  }
  throw lastErr || new Error("захват экрана не удался");
}

async function startScreen() {
  statusEl.textContent = "на созвоне только голос. кадр снимаю только в Doom.";
}

function waitForVideo(video) {
  if (video.videoWidth) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("кадр экрана не появился. выберите окно ещё раз.")), 5000);
    const done = () => {
      if (!video.videoWidth) return;
      clearTimeout(timer);
      video.removeEventListener("loadeddata", done);
      video.removeEventListener("loadedmetadata", done);
      resolve();
    };
    video.addEventListener("loadeddata", done);
    video.addEventListener("loadedmetadata", done);
  });
}

screenBtn.addEventListener("click", () => {
  if (displayStream) {
    stopScreen();
    statusEl.textContent = "экран выключен";
  } else {
    startScreen().catch((err) => { statusEl.textContent = err.message; });
  }
});

radioOnEl.addEventListener("change", () => {
  radioOn = radioOnEl.checked;
  if (radioOn) armIdleHint();
  else clearTimeout(idleTimer);
});

document.getElementById("cheats").addEventListener("click", (e) => {
  const btn = e.target.closest("[data-cheat]");
  if (!btn) return;
  injectCheat(btn.getAttribute("data-cheat")).catch((err) => {
    gameStatus.textContent = err.message;
  });
});

if (spPairForm) {
  spPairForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const oldFile = spOld && spOld.files[0];
    const newFile = spNew && spNew.files[0];
    if (!oldFile || !newFile) {
      spPairStatus.textContent = "выберите старое и новое СП";
      return;
    }
    const body = new FormData();
    body.append("oldFile", oldFile);
    body.append("newFile", newFile);
    spPairStatus.textContent = "сравниваю пару СП...";
    try {
      const overview = await api("/api/specs/pair", { method: "POST", body });
      spPairStatus.textContent = `СП «${overview.fileName || "petclinic"}» v${overview.fromVersion}>v${overview.toVersion}, изменений ${(overview.summary && overview.summary.total) || 0}. В чате: #sp затем #task.`;
      spOld.value = "";
      spNew.value = "";
      await refreshMeta();
    } catch (err) {
      spPairStatus.textContent = err.message;
    }
  });
}

if (uploadForm) {
  uploadForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const file = txtFile && txtFile.files[0];
    if (!file) {
      uploadStatus.textContent = "выберите файл .txt";
      return;
    }
    const body = new FormData();
    body.append("file", file);
    uploadStatus.textContent = "загрузка...";
    try {
      const created = await api("/api/knowledge/txt", { method: "POST", body });
      uploadStatus.textContent = `в RAG добавлено кусков: ${created.length}`;
      txtFile.value = "";
      await refreshMeta();
    } catch (err) {
      uploadStatus.textContent = err.message;
    }
  });
}

function loadCss(href) {
  if ([...document.querySelectorAll("link")].some((el) => el.href.includes("js-dos.css"))) return;
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = href;
  document.head.appendChild(link);
}

function loadScript(src) {
  return new Promise((resolve, reject) => {
    if (typeof Dos === "function") {
      resolve();
      return;
    }
    const script = document.createElement("script");
    script.src = src;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("не загрузился js-dos"));
    document.body.appendChild(script);
  });
}

async function enterDoomMode() {
  if (doomMode || !sessionId) return;
  if (displayStream) stopScreen();
  secretBtn.disabled = true;
  const data = await api(`/api/sessions/${sessionId}/doom`, { method: "POST" });
  doomMode = true;
  gameStartedAt = Date.now();
  document.body.classList.add("doom");
  document.title = "Doom-рация";
  gameStage.hidden = false;
  radioWrap.hidden = false;
  chatTitle.textContent = "Рация";
  topicEl.textContent = data.topic || "Doom-рация";
  input.placeholder = "куда идти? бог? где дробовик?";
  renderSession(data);
  const deck = document.getElementById("radio-deck");
  if (deck) deck.hidden = false;
  showRadioOverlay("Рация на связи. Иди в кадре: смотрю экран. Говори в микрофон: текст будет слева, ответ справа.");
  setRadioLive("включаю микрофон...");
  statusEl.textContent = "секрет: Doom. звук игры выключен, слушаю только тебя.";
  loadCss("https://v8.js-dos.com/latest/js-dos.css");
  try {
    await loadScript("https://v8.js-dos.com/latest/js-dos.js");
    bootDoom();
  } catch (err) {
    gameStatus.textContent = err.message;
  }
  if (listening || airOn) stopEngine();
  startListening().catch((err) => { statusEl.textContent = err.message; });
  armIdleHint();
  requestHint().catch((err) => { statusEl.textContent = err.message; });
}

secretBtn.addEventListener("click", () => {
  enterDoomMode().catch((err) => { statusEl.textContent = err.message; });
});

async function boot() {
  const [models, voiceCfg, session] = await Promise.all([
    api("/api/models"),
    api("/api/voice"),
    api("/api/sessions", { method: "POST" }),
  ]);
  topicEl.textContent = models.topic;
  modelsEl.innerHTML = `чат: ${models.chat}<br>Doom кадр: ${models.vision || "нет"}<br>критик: ${models.critic}`;
  voice = voiceCfg;
  sessionId = session.id;
  renderSession(session);
  await refreshMeta();
  statusEl.textContent = "старт: эфир созвона. ПТО по СП. Doom сам снимает кадр.";
}

boot().catch((err) => {
  const text = err && err.message ? err.message : "не удалось загрузить сессию";
  statusEl.textContent = text;
  topicEl.textContent = text;
});
