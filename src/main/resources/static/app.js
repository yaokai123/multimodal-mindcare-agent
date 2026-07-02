const state = {
  auth: { username: "student", password: "student123456" },
  sessionId: null,
  sending: false,
  isAdmin: false,
  moodScore: 3,
  modelName: "multimodalAgent-qwen2.5-7b-ft:latest",
  latestReports: [],
  latestExcelRecords: [],
  latestAlerts: [],
  latestTickets: [],
  latestCases: [],
  latestDashboard: null,
  latestAuditLogs: [],
  voiceSession: null,
  voiceEventAbort: null,
  voiceAssistantCard: null,
  voiceAssistantOutput: "",
  voiceAgent: null,
  voiceMetrics: {},
  attachmentPreviewUrls: [],
  conversationHistory: [],
  ticketFilters: {
    status: "",
    risk: "",
    assignee: "",
    search: ""
  }
};

const $ = (selector) => document.querySelector(selector);

const els = {
  serviceState: $("#serviceState"),
  modelState: $("#modelState"),
  runtimeModel: $("#runtimeModel"),
  loginForm: $("#loginForm"),
  username: $("#username"),
  password: $("#password"),
  loginState: $("#loginState"),
  accountPanel: $("#accountPanel"),
  activeAccount: $("#activeAccount"),
  activeRole: $("#activeRole"),
  switchAccount: $("#switchAccount"),
  studentView: $("#studentView"),
  adminView: $("#adminView"),
  profileText: $("#profileText"),
  sessionBadge: $("#sessionBadge"),
  messages: $("#messages"),
  pipelineSteps: $("#pipelineSteps"),
  chatForm: $("#chatForm"),
  messageInput: $("#messageInput"),
  voicePanel: $("#voicePanel"),
  voiceState: $("#voiceState"),
  voiceRuntime: $("#voiceRuntime"),
  voiceKnowledgeScope: $("#voiceKnowledgeScope"),
  voiceLatency: $("#voiceLatency"),
  startVoiceButton: $("#startVoiceButton"),
  interruptVoiceButton: $("#interruptVoiceButton"),
  endVoiceButton: $("#endVoiceButton"),
  audioInput: $("#audioInput"),
  imageInput: $("#imageInput"),
  videoInput: $("#videoInput"),
  attachmentState: $("#attachmentState"),
  clearAttachments: $("#clearAttachments"),
  newSessionButton: $("#newSessionButton"),
  sendButton: $("#sendButton"),
  moodForm: $("#moodForm"),
  moodScale: $("#moodScale"),
  moodLabel: $("#moodLabel"),
  moodTrigger: $("#moodTrigger"),
  moodNote: $("#moodNote"),
  moodTrendSummary: $("#moodTrendSummary"),
  moodTrendChart: $("#moodTrendChart"),
  triggerRows: $("#triggerRows"),
  moodRows: $("#moodRows"),
  taskForm: $("#taskForm"),
  taskTitle: $("#taskTitle"),
  taskDetail: $("#taskDetail"),
  seedTasks: $("#seedTasks"),
  smartTasks: $("#smartTasks"),
  taskInsight: $("#taskInsight"),
  taskRows: $("#taskRows"),
  goalRows: $("#goalRows"),
  adminRefresh: $("#adminRefresh"),
  adminStats: $("#adminStats"),
  queueCount: $("#queueCount"),
  ticketStatusFilter: $("#ticketStatusFilter"),
  ticketRiskFilter: $("#ticketRiskFilter"),
  ticketAssigneeFilter: $("#ticketAssigneeFilter"),
  ticketSearchFilter: $("#ticketSearchFilter"),
  ticketFilterReset: $("#ticketFilterReset"),
  adminReportRows: $("#adminReportRows"),
  excelRows: $("#excelRows"),
  emailRows: $("#emailRows"),
  caseRows: $("#caseRows"),
  knowledgeUploadForm: $("#knowledgeUploadForm"),
  knowledgeFile: $("#knowledgeFile"),
  knowledgeUploadState: $("#knowledgeUploadState"),
  knowledgeSources: $("#knowledgeSources"),
  detailOverlay: $("#detailOverlay"),
  detailKicker: $("#detailKicker"),
  detailTitle: $("#detailTitle"),
  detailMeta: $("#detailMeta"),
  detailBody: $("#detailBody"),
  closeDetail: $("#closeDetail")
};

const pipeline = [
  ["input", "多模态接入"],
  ["fusion", "情绪融合"],
  ["router", "意图路由"],
  ["rag", "Agentic RAG"],
  ["mcp", "MCP 工具"],
  ["stream", "SSE 输出"]
];

if (!els.smartTasks && els.seedTasks) {
  els.smartTasks = document.createElement("button");
  els.smartTasks.id = "smartTasks";
  els.smartTasks.type = "button";
  els.smartTasks.className = "secondary";
  els.smartTasks.textContent = "Smart";
  els.seedTasks.insertAdjacentElement("afterend", els.smartTasks);
}

if (!els.goalRows && els.taskRows) {
  const goalBox = document.createElement("div");
  goalBox.className = "support-list compact";
  goalBox.id = "goalRows";
  els.taskRows.insertAdjacentElement("beforebegin", goalBox);
  els.goalRows = goalBox;
}

if (!els.conversationRows && els.studentView) {
  const historyPanel = document.createElement("section");
  historyPanel.className = "glass-panel history-card";
  historyPanel.innerHTML = `
    <div class="history-head">
      <div>
        <p class="kicker">History</p>
        <h2>历史会话</h2>
      </div>
      <span id="conversationCount">0</span>
    </div>
    <div id="conversationRows" class="history-list"></div>
  `;
  const sampleCard = document.querySelector(".sample-card");
  if (sampleCard) {
    sampleCard.insertAdjacentElement("afterend", historyPanel);
  } else {
    document.querySelector(".sensor-deck")?.prepend(historyPanel);
  }
  els.conversationRows = $("#conversationRows");
  els.conversationCount = $("#conversationCount");
}

if (!els.caseRows && els.adminView) {
  const casePanel = document.createElement("section");
  casePanel.className = "case-board";
  casePanel.innerHTML = `
    <div class="case-board-head">
      <div>
        <p class="kicker">Case Management</p>
        <h3>学生支持档案</h3>
      </div>
      <span id="caseCount">0</span>
    </div>
    <div id="caseRows" class="case-grid"></div>
  `;
  const anchor = document.querySelector(".admin-layout");
  if (anchor) {
    anchor.insertAdjacentElement("beforebegin", casePanel);
  } else {
    els.adminView.append(casePanel);
  }
  els.caseRows = $("#caseRows");
  els.caseCount = $("#caseCount");
}

function authHeader() {
  return `Basic ${btoa(`${state.auth.username}:${state.auth.password}`)}`;
}

async function api(path, options = {}) {
  const headers = { Authorization: authHeader(), ...(options.headers || {}) };
  const response = await fetch(path, { ...options, headers });
  if (!response.ok) {
    throw new Error(await response.text() || `${response.status} ${response.statusText}`);
  }
  return response;
}

function tone(element, value) {
  element.classList.remove("ok", "warn", "danger", "active");
  if (value) element.classList.add(value);
}

function dockChatComposer() {
  const stage = document.querySelector(".dialog-stage");
  const legacyCard = els.chatForm.closest(".intake-card");
  if (!stage || !els.messages || !els.chatForm) return;
  els.chatForm.classList.add("chat-composer");
  els.messages.insertAdjacentElement("afterend", els.chatForm);
  if (legacyCard) legacyCard.hidden = true;
}

function refineSupportPanelCopy() {
  const sampleCard = document.querySelector(".sample-card");
  const supportCards = document.querySelectorAll(".support-card");
  if (sampleCard) {
    sampleCard.querySelector(".kicker").textContent = "常用开场";
    const prompts = [
      ["焦虑倾诉", "把压力和睡眠问题先说清楚"],
      ["低落陪伴", "需要有人陪你整理一下状态"],
      ["普通聊天", "一起拆解今天能完成的事"]
    ];
    sampleCard.querySelectorAll("[data-prompt]").forEach((button, index) => {
      const [title, desc] = prompts[index] || ["开始对话", "从这里写下第一句话"];
      button.innerHTML = `<span>${title}</span><small>${desc}</small>`;
    });
  }
  if (supportCards[0]) supportCards[0].querySelector(".kicker").textContent = "情绪追踪";
  if (supportCards[1]) supportCards[1].querySelector(".kicker").textContent = "持续支持";
  els.moodLabel.placeholder = "给现在的情绪起个名字";
  els.moodTrigger.placeholder = "可能的触发因素";
  els.moodNote.placeholder = "简单写下现在的感受";
  els.moodForm.querySelector("button[type='submit']").textContent = "记录心情";
  els.taskTitle.placeholder = "添加一个今天能完成的小任务";
  els.taskDetail.placeholder = "补充细节，可不填";
  els.seedTasks.textContent = "生成建议";
  if (els.smartTasks) els.smartTasks.textContent = "智能推荐";
  els.taskForm.querySelector("button[type='submit']").textContent = "添加任务";
}

function railLinks() {
  return Array.from(document.querySelectorAll(".rail-nav a"));
}

function setRailActive(targetId) {
  railLinks().forEach((link) => {
    link.classList.toggle("active", link.getAttribute("href") === `#${targetId}`);
  });
}

function updateRailNavigation(activeTarget = "") {
  const detailOpen = !els.detailOverlay.hidden;
  railLinks().forEach((link) => {
    const targetId = link.getAttribute("href")?.slice(1) || "";
    const adminOnly = ["adminView", "knowledgeSources"].includes(targetId);
    const studentOnly = targetId === "studentView";
    const detailOnly = targetId === "detailOverlay";
    const disabled =
      (adminOnly && !state.isAdmin) ||
      (studentOnly && state.isAdmin) ||
      (detailOnly && !detailOpen);
    link.classList.toggle("disabled", disabled);
    link.setAttribute("aria-disabled", String(disabled));
    link.tabIndex = disabled ? -1 : 0;
  });
  setRailActive(activeTarget || (state.isAdmin ? "adminView" : "studentView"));
}

function showNavigationHint(targetId) {
  const messages = {
    adminView: "请使用管理员账号访问风险看板",
    knowledgeSources: "请使用管理员账号访问知识运营",
    studentView: "请切换到学生账号访问学生支持",
    detailOverlay: "请先打开风险工单、知识条目或学生画像"
  };
  els.loginState.textContent = messages[targetId] || "当前模块暂不可访问";
}

function navigateRail(event) {
  const link = event.target.closest(".rail-nav a");
  if (!link) return;
  event.preventDefault();
  const targetId = link.getAttribute("href")?.slice(1);
  const target = targetId ? document.getElementById(targetId) : null;
  if (!target || link.getAttribute("aria-disabled") === "true" || target.hidden) {
    showNavigationHint(targetId);
    updateRailNavigation();
    return;
  }
  target.scrollIntoView({ behavior: "smooth", block: "start" });
  setRailActive(targetId);
}

function setService(text, value) {
  els.serviceState.textContent = text;
  tone(els.serviceState, value);
}

function displayModelName(model) {
  return (model || "").includes("multimodalAgent-qwen2.5-7b-ft") ? "微调 Qwen2.5-7B" : (model || "未知模型");
}

function setModel(status) {
  state.modelName = status.model || state.modelName;
  const label = status.realModelEnabled ? `${status.provider} / ${displayModelName(state.modelName)}` : "mock / 离线演示";
  els.modelState.textContent = label;
  els.runtimeModel.textContent = displayModelName(state.modelName);
  tone(els.modelState, status.realModelEnabled ? "ok" : "warn");
}

function renderVoiceSession(data) {
  if (!els.voicePanel || !data) return;
  state.voiceSession = data;
  const activeVoice = Boolean(data.configured && data.roomName && data.livekitToken);
  if (els.voiceKnowledgeScope && data.knowledgeScope) {
    els.voiceKnowledgeScope.value = data.knowledgeScope;
  }
  els.voiceState.textContent = data.configured
    ? (activeVoice ? `房间已准备：${data.roomName}` : "实时语音服务已配置")
    : data.status;
  els.voicePanel.classList.toggle("ready", Boolean(data.configured));
  els.voicePanel.classList.toggle("active", activeVoice);
  els.voiceRuntime.innerHTML = `
    <span>ASR ${escapeHtml(data.asrProvider || "-")} / ${escapeHtml(data.asrModel || "-")}</span>
    <span>LLM ${escapeHtml(data.llmProvider || "-")} / ${escapeHtml(displayModelName(data.llmModel || ""))}</span>
    <span>TTS ${escapeHtml(data.ttsProvider || "-")} / ${escapeHtml(data.ttsVoice || data.ttsModel || "-")}</span>
    <span>${data.interruptEnabled ? "支持打断" : "未启用打断"}</span>
  `;
  renderVoiceLatency();
  els.startVoiceButton.disabled = activeVoice;
  els.interruptVoiceButton.disabled = !(activeVoice && data.interruptEnabled);
  els.endVoiceButton.disabled = !activeVoice;
}

async function loadVoiceStatus() {
  if (!els.voicePanel || state.isAdmin) return;
  try {
    const response = await api("/api/voice/status");
    renderVoiceSession(await response.json());
  } catch (error) {
    els.voiceState.textContent = "语音状态读取失败";
  }
}

async function startVoiceSession() {
  if (state.isAdmin || !els.voicePanel) return;
  els.voiceState.textContent = "正在准备实时语音房间";
  try {
    const response = await api("/api/voice/sessions", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        sessionId: state.sessionId,
        knowledgeScope: els.voiceKnowledgeScope?.value || "ALL",
        supportGoal: "current-dialog"
      })
    });
    const data = await response.json();
    renderVoiceSession(data);
    if (!data.configured) return;
    subscribeVoiceEvents(data.roomName);
    await startBrowserVoiceAgent(data);
    if (!window.LivekitClient && !window.LiveKit) {
      els.voiceState.textContent = "LiveKit 房间令牌已生成；控制通道已连接，等待 Agent 接入。";
    }
  } catch (error) {
    els.voiceState.textContent = "语音会话启动失败，请检查配置";
  }
}

async function startBrowserVoiceAgent(session) {
  await stopBrowserVoiceAgent();
  const agent = {
    session,
    stream: null,
    audioContext: null,
    source: null,
    processor: null,
    livekitRoom: null,
    livekitTrack: null,
    pending: [],
    speaking: false,
    speechStartedAt: 0,
    lastVoiceAt: 0,
    sending: Promise.resolve(),
    assistantText: "",
    awaitingTts: false,
    currentAudio: null,
    stopped: false
  };
  state.voiceAgent = agent;
  state.voiceMetrics = {};
  renderVoiceLatency();

  agent.stream = await navigator.mediaDevices.getUserMedia({
    audio: {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
      channelCount: 1
    }
  });
  await connectLiveKitMedia(agent);
  startPcmVad(agent);
  els.voiceState.textContent = "Voice is listening; speak naturally.";
}

async function connectLiveKitMedia(agent) {
  try {
    const LK = await loadLiveKitClient();
    if (!LK || !agent.session.livekitUrl || !agent.session.livekitToken) return;
    const Room = LK.Room || LK.LiveKitRoom;
    const createLocalAudioTrack = LK.createLocalAudioTrack || (async () => null);
    if (!Room) return;
    const room = new Room({ adaptiveStream: true, dynacast: true });
    await room.connect(agent.session.livekitUrl, agent.session.livekitToken);
    const track = await createLocalAudioTrack({ echoCancellation: true, noiseSuppression: true });
    if (track && room.localParticipant?.publishTrack) {
      await room.localParticipant.publishTrack(track);
      agent.livekitTrack = track;
    }
    agent.livekitRoom = room;
  } catch (error) {
    console.warn("LiveKit media bridge unavailable; using local ASR bridge.", error);
  }
}

function loadLiveKitClient() {
  if (window.LivekitClient || window.LiveKit) {
    return Promise.resolve(window.LivekitClient || window.LiveKit);
  }
  return new Promise((resolve) => {
    const script = document.createElement("script");
    script.src = "https://cdn.jsdelivr.net/npm/livekit-client/dist/livekit-client.umd.min.js";
    script.async = true;
    script.onload = () => resolve(window.LivekitClient || window.LiveKit);
    script.onerror = () => resolve(null);
    document.head.appendChild(script);
  });
}

function startPcmVad(agent) {
  const AudioContextClass = window.AudioContext || window.webkitAudioContext;
  const audioContext = new AudioContextClass();
  const source = audioContext.createMediaStreamSource(agent.stream);
  const processor = audioContext.createScriptProcessor(4096, 1, 1);
  agent.audioContext = audioContext;
  agent.source = source;
  agent.processor = processor;

  const threshold = 0.014;
  const silenceMs = 900;
  const minSpeechMs = 500;
  const maxSpeechMs = 9000;

  processor.onaudioprocess = (event) => {
    if (agent.stopped) return;
    const input = event.inputBuffer.getChannelData(0);
    const now = performance.now();
    const rms = Math.sqrt(input.reduce((sum, value) => sum + value * value, 0) / input.length);
    const active = rms >= threshold;
    if (active && !agent.speaking) {
      agent.speaking = true;
      agent.speechStartedAt = now;
      agent.pending = [];
      markVoiceMetric("asrStartAt");
    }
    if (agent.speaking) {
      agent.pending.push(new Float32Array(input));
      if (active) agent.lastVoiceAt = now;
      const speechMs = now - agent.speechStartedAt;
      const quietMs = now - agent.lastVoiceAt;
      if ((speechMs >= minSpeechMs && quietMs >= silenceMs) || speechMs >= maxSpeechMs) {
        finalizeVoiceSegment(agent);
      }
    }
  };

  source.connect(processor);
  processor.connect(audioContext.destination);
}

function finalizeVoiceSegment(agent) {
  if (!agent.speaking || !agent.pending.length) return;
  const chunks = agent.pending;
  agent.pending = [];
  agent.speaking = false;
  const pcm = encodePcm16(downsample(chunks, agent.audioContext.sampleRate, 16000));
  if (pcm.byteLength < 1600) return;
  agent.sending = agent.sending.then(() => sendVoiceSegment(agent, pcm)).catch((error) => {
    console.warn("voice segment failed", error);
    els.voiceState.textContent = "Voice turn failed; please try again.";
  });
}

async function sendVoiceSegment(agent, pcm) {
  if (agent.stopped || !agent.session?.roomName) return;
  els.voiceState.textContent = "ASR is transcribing.";
  const asrResponse = await api("/api/voice/agent/asr/doubao", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      roomName: agent.session.roomName,
      sessionId: state.sessionId,
      audioBase64: arrayBufferToBase64(pcm.buffer),
      format: "pcm",
      sampleRate: 16000,
      language: "zh-CN",
      finalTranscript: true
    })
  });
  const asr = await asrResponse.json();
  if (!asr.text) return;
  markVoiceMetric("asrDoneAt");
  agent.awaitingTts = true;
  agent.assistantText = "";
  await api(`/api/voice/agent/transcripts`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      roomName: agent.session.roomName,
      sessionId: state.sessionId,
      text: asr.text,
      finalTranscript: true
    })
  }).then((response) => response.text());
}

async function synthesizeVoiceAnswer(text) {
  const agent = state.voiceAgent;
  if (!agent || agent.stopped || !text.trim()) return;
  stopVoicePlayback();
  els.voiceState.textContent = "TTS is preparing audio.";
  markVoiceMetric("ttsStartAt");
  const response = await api("/api/voice/agent/tts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      roomName: agent.session.roomName,
      sessionId: state.sessionId,
      text,
      format: "mp3"
    })
  });
  const tts = await response.json();
  if (!tts.audioBase64) return;
  markVoiceMetric("ttsDoneAt");
  const audio = new Audio(`data:audio/${tts.format || "mp3"};base64,${tts.audioBase64}`);
  agent.currentAudio = audio;
  await audio.play();
}

function stopVoicePlayback() {
  const audio = state.voiceAgent?.currentAudio;
  if (audio) {
    audio.pause();
    audio.currentTime = 0;
    state.voiceAgent.currentAudio = null;
  }
}

async function stopBrowserVoiceAgent() {
  const agent = state.voiceAgent;
  if (!agent) return;
  agent.stopped = true;
  stopVoicePlayback();
  if (agent.processor) agent.processor.disconnect();
  if (agent.source) agent.source.disconnect();
  if (agent.audioContext) await agent.audioContext.close().catch(() => {});
  if (agent.livekitTrack?.stop) agent.livekitTrack.stop();
  if (agent.livekitRoom?.disconnect) agent.livekitRoom.disconnect();
  agent.stream?.getTracks().forEach((track) => track.stop());
  state.voiceAgent = null;
}

function downsample(chunks, inputRate, outputRate) {
  const length = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const input = new Float32Array(length);
  let offset = 0;
  chunks.forEach((chunk) => {
    input.set(chunk, offset);
    offset += chunk.length;
  });
  if (inputRate === outputRate) return input;
  const ratio = inputRate / outputRate;
  const output = new Float32Array(Math.floor(input.length / ratio));
  for (let i = 0; i < output.length; i += 1) {
    output[i] = input[Math.floor(i * ratio)] || 0;
  }
  return output;
}

function encodePcm16(samples) {
  const buffer = new ArrayBuffer(samples.length * 2);
  const view = new DataView(buffer);
  samples.forEach((sample, index) => {
    const clamped = Math.max(-1, Math.min(1, sample));
    view.setInt16(index * 2, clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff, true);
  });
  return new Uint8Array(buffer);
}

function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let i = 0; i < bytes.length; i += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return btoa(binary);
}

function markVoiceMetric(key) {
  state.voiceMetrics[key] = performance.now();
  renderVoiceLatency();
}

function renderVoiceLatency(metrics = null) {
  if (metrics) {
    state.voiceMetrics.server = metrics;
  }
  if (!els.voiceLatency) return;
  const server = state.voiceMetrics.server || {};
  const local = state.voiceMetrics;
  const asr = server.asrMs ?? duration(local.asrStartAt, local.asrDoneAt);
  const llm = server.llmFirstTokenMs ?? duration(local.asrDoneAt, local.llmFirstTokenAt);
  const tts = server.ttsMs ?? duration(local.ttsStartAt, local.ttsDoneAt);
  els.voiceLatency.textContent = `ASR ${fmtMs(asr)} / LLM ${fmtMs(llm)} / TTS ${fmtMs(tts)}`;
}

function duration(start, end) {
  return start && end && end >= start ? Math.round(end - start) : null;
}

function fmtMs(value) {
  return typeof value === "number" ? `${Math.round(value)}ms` : "-";
}

async function interruptVoiceSession() {
  if (!state.voiceSession?.roomName) return;
  try {
    stopVoicePlayback();
    const room = encodeURIComponent(state.voiceSession.roomName);
    await api(`/api/voice/sessions/${room}/interrupt`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ command: "interrupt_tts", reason: "student-barge-in" })
    });
    els.voiceState.textContent = "已发送打断命令，Agent 将停止当前 TTS 播放";
  } catch (error) {
    els.voiceState.textContent = "打断命令发送失败";
  }
}

async function endVoiceSession() {
  const roomName = state.voiceSession?.roomName;
  if (roomName) {
    try {
      const room = encodeURIComponent(roomName);
      await api(`/api/voice/sessions/${room}/end`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ command: "end_session", reason: "student-ended" })
      });
    } catch (error) {
      els.voiceState.textContent = "结束命令发送失败，已在本地关闭语音会话";
    }
  }
  await stopBrowserVoiceAgent();
  stopVoiceEvents();
  state.voiceSession = null;
  state.voiceAssistantCard = null;
  state.voiceAssistantOutput = "";
  els.voicePanel?.classList.remove("active");
  els.startVoiceButton.disabled = false;
  els.interruptVoiceButton.disabled = true;
  els.endVoiceButton.disabled = true;
  els.voiceState.textContent = "语音会话已结束";
}

async function subscribeVoiceEvents(roomName) {
  if (!roomName) return;
  stopVoiceEvents();
  state.voiceEventAbort = new AbortController();
  try {
    const response = await fetch(`/api/voice/sessions/${encodeURIComponent(roomName)}/events`, {
      headers: { Authorization: authHeader() },
      signal: state.voiceEventAbort.signal
    });
    if (!response.ok || !response.body) throw new Error("voice event stream failed");
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (state.voiceEventAbort && !state.voiceEventAbort.signal.aborted) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      buffer = parseSse(buffer, handleVoiceEvent);
    }
  } catch (error) {
    if (!state.voiceEventAbort?.signal.aborted) {
      els.voiceState.textContent = "语音控制通道已断开";
    }
  }
}

function stopVoiceEvents() {
  if (state.voiceEventAbort) {
    state.voiceEventAbort.abort();
    state.voiceEventAbort = null;
  }
}

function handleVoiceEvent(eventData) {
  if (!eventData || !eventData.type) return;
  if (eventData.sessionId) {
    state.sessionId = eventData.sessionId;
  }
  if (eventData.type === "status") {
    const labels = {
      "control-channel-ready": "控制通道已连接，等待 LiveKit Agent",
      "agent-command-stream-ready": "LiveKit Agent 已连接控制通道",
      "chat-session-ready": "语音对话上下文已接入",
      heartbeat: "实时语音通道运行中"
    };
    els.voiceState.textContent = labels[eventData.text] || eventData.text || "语音通道运行中";
  }
  if (eventData.type === "phase") {
    updatePipelinePhase(eventData.phase);
    const phaseLabels = {
      asr: "正在实时转写",
      llm: "正在生成回答",
      tts: "正在播放语音",
      done: "本轮语音回复完成",
      input: "正在接入输入",
      router: "正在判断意图",
      rag: "正在检索知识",
      stream: "正在流式生成"
    };
    els.voiceState.textContent = phaseLabels[eventData.phase] || `语音阶段：${eventData.phase}`;
  }
  if (eventData.type === "transcript" && eventData.finalTranscript) {
    clearEmpty();
    addMessage("user", eventData.text || "");
    state.voiceAssistantCard = addMessage("assistant", "");
    state.voiceAssistantOutput = "";
    els.voiceState.textContent = "已收到转写，正在生成回答";
  }
  if (eventData.type === "assistant_token") {
    clearEmpty();
    if (!state.voiceAssistantCard) {
      state.voiceAssistantCard = addMessage("assistant", "");
      state.voiceAssistantOutput = "";
    }
    state.voiceAssistantOutput += eventData.token || "";
    if (state.voiceAgent?.awaitingTts) {
      state.voiceAgent.assistantText += eventData.token || "";
      if (!state.voiceMetrics.llmFirstTokenAt) markVoiceMetric("llmFirstTokenAt");
    }
    updateAssistant(state.voiceAssistantCard, state.voiceAssistantOutput);
    els.voiceState.textContent = "正在把回答发送给 TTS";
  }
  if (eventData.type === "command") {
    if (eventData.command === "interrupt_tts") stopVoicePlayback();
    els.voiceState.textContent = eventData.command === "interrupt_tts"
      ? "TTS 已收到打断命令"
      : "语音控制命令已下发";
  }
  if (eventData.type === "done") {
    if (state.voiceAgent?.awaitingTts) {
      const answer = state.voiceAgent.assistantText;
      state.voiceAgent.awaitingTts = false;
      markVoiceMetric("llmDoneAt");
      synthesizeVoiceAnswer(answer).catch((error) => {
        console.warn("tts playback failed", error);
        els.voiceState.textContent = "TTS playback failed.";
      });
    }
    els.voiceState.textContent = "本轮语音回答完成";
    state.voiceAssistantCard = null;
    state.voiceAssistantOutput = "";
    setSession("READY", "ok");
  }
  if (eventData.type === "error") {
    els.voiceState.textContent = eventData.text || "语音链路出现错误";
  }
  if (eventData.type === "latency") {
    renderVoiceLatency(eventData.metrics || {});
  }
}

function selectedFiles() {
  return [
    ["audio", "语音", els.audioInput.files?.[0]],
    ["image", "图像", els.imageInput.files?.[0]],
    ["video", "视频", els.videoInput.files?.[0]]
  ].filter(([, , file]) => file);
}

function formatFileSize(size) {
  if (!Number.isFinite(size)) return "";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function revokeAttachmentPreviewUrls() {
  state.attachmentPreviewUrls.forEach((url) => URL.revokeObjectURL(url));
  state.attachmentPreviewUrls = [];
}

function createAttachmentPreview([kind, label, file], { keep = false } = {}) {
  const item = document.createElement("article");
  item.className = `attachment-preview ${kind}`;
  const meta = document.createElement("div");
  meta.className = "attachment-preview-meta";
  meta.innerHTML = `<strong>${label}</strong><span>${escapeHtml(file.name)}</span><small>${formatFileSize(file.size)}</small>`;

  if (kind === "image" && file.type.startsWith("image/")) {
    const url = URL.createObjectURL(file);
    if (!keep) state.attachmentPreviewUrls.push(url);
    const img = document.createElement("img");
    img.src = url;
    img.alt = file.name;
    item.append(img);
  } else {
    const icon = document.createElement("div");
    icon.className = "attachment-preview-icon";
    icon.textContent = kind === "audio" ? "ASR" : kind === "video" ? "VID" : "FILE";
    item.append(icon);
  }

  item.append(meta);
  return item;
}

function renderMessageAttachments(card, files) {
  if (!files.length) return;
  const wrap = document.createElement("div");
  wrap.className = "message-attachments";
  files.forEach((fileEntry) => wrap.append(createAttachmentPreview(fileEntry, { keep: true })));
  card.append(wrap);
}

function updateAttachments() {
  const files = selectedFiles();
  els.clearAttachments.hidden = files.length === 0;
  revokeAttachmentPreviewUrls();
  els.attachmentState.innerHTML = "";
  if (files.length) {
    files.forEach((fileEntry) => els.attachmentState.append(createAttachmentPreview(fileEntry)));
  } else {
    els.attachmentState.textContent = "暂无附件";
  }
  els.attachmentState.classList.toggle("active", files.length > 0);
}

function clearAttachments() {
  els.audioInput.value = "";
  els.imageInput.value = "";
  els.videoInput.value = "";
  updateAttachments();
}

function renderPipeline(activeKey = "") {
  els.pipelineSteps.innerHTML = "";
  const activeIndex = pipeline.findIndex(([key]) => key === activeKey);
  pipeline.forEach(([key, label], index) => {
    const item = document.createElement("div");
    const completed = activeIndex >= 0 && index < activeIndex;
    item.className = `pipeline-step ${key === activeKey ? "active" : ""} ${completed ? "completed" : ""}`;
    item.innerHTML = `<span>${completed ? "✓" : String(index + 1).padStart(2, "0")}</span><strong>${label}</strong>`;
    els.pipelineSteps.append(item);
  });
}

function updatePipelinePhase(phase) {
  if (!pipeline.some(([key]) => key === phase)) return;
  renderPipeline(phase);
}

function setSession(text, value) {
  els.sessionBadge.textContent = text;
  tone(els.sessionBadge, value);
}

function addMessage(role, content = "") {
  const card = document.createElement("article");
  card.className = `message-card ${role}`;
  card.dataset.raw = content;
  const label = role === "user" ? "学生输入" : displayModelName(state.modelName);
  card.innerHTML = `<header><span>${label}</span></header><div class="message-content"></div>`;
  card.querySelector(".message-content").textContent = content;
  els.messages.append(card);
  els.messages.scrollTop = els.messages.scrollHeight;
  return card;
}

function updateAssistant(card, text) {
  card.dataset.raw = text;
  card.querySelector(".message-content").textContent = text;
  els.messages.scrollTop = els.messages.scrollHeight;
}

function renderCitations(card, citations = [], grounded = false, review = "") {
  card.querySelector(".citation-list")?.remove();
  if (!citations.length && !review) return;
  const list = document.createElement("div");
  list.className = "citation-list";
  list.innerHTML = `<strong>参考依据</strong>`;
  if (review) {
    const note = document.createElement("p");
    note.className = grounded ? "grounded-note ok" : "grounded-note warn";
    note.textContent = review;
    list.append(note);
  }
  citations.forEach((citation) => {
    const item = document.createElement("article");
    item.className = "citation-card";
    item.innerHTML = `
      <div><span>${escapeHtml(citation.category || "GENERAL")}</span><small>${Number(citation.score || 0).toFixed(2)}</small></div>
      <h4>${escapeHtml(citation.source || "knowledge")}</h4>
      <p>${escapeHtml(citation.excerpt || "")}</p>
    `;
    list.append(item);
  });
  card.append(list);
  els.messages.scrollTop = els.messages.scrollHeight;
}

function renderCitations(card, citations = [], grounded = false, review = "", answerBasis = "") {
  card.querySelector(".citation-list")?.remove();
  const shownCitations = citations.filter((citation) => citation.shown !== false);
  if (!shownCitations.length && !review && !answerBasis) return;
  const list = document.createElement("div");
  list.className = "citation-list";
  list.innerHTML = `<strong>Reference basis</strong>`;
  if (answerBasis) {
    const basis = document.createElement("p");
    basis.className = answerBasis === "KNOWLEDGE_BASE" ? "grounded-note ok" : "grounded-note warn";
    basis.textContent = `Answer basis: ${answerBasis}`;
    list.append(basis);
  }
  if (review) {
    const note = document.createElement("p");
    note.className = grounded ? "grounded-note ok" : "grounded-note warn";
    note.textContent = review;
    list.append(note);
  }
  shownCitations.forEach((citation) => {
    const item = document.createElement("article");
    item.className = "citation-card";
    item.innerHTML = `
      <div><span>${escapeHtml(citation.category || "GENERAL")}</span><small>${Number(citation.score || 0).toFixed(2)}</small></div>
      <h4>${escapeHtml(citation.source || "knowledge")}</h4>
      <small>${escapeHtml(citation.tags || "")} / ${escapeHtml(citation.audience || "")} / ${escapeHtml(citation.riskLevel || "")}</small>
      <p>${escapeHtml(citation.excerpt || "")}</p>
    `;
    list.append(item);
  });
  card.append(list);
  els.messages.scrollTop = els.messages.scrollHeight;
}

function renderMultimodalSignals(card, signals = []) {
  card.querySelector(".multimodal-confidence")?.remove();
  if (!signals.length) return;
  const list = document.createElement("div");
  list.className = "multimodal-confidence";
  list.innerHTML = `<strong>Multimodal confidence</strong>`;
  signals.forEach((signal) => {
    const item = document.createElement("article");
    item.className = "citation-card";
    item.innerHTML = `
      <div><span>${escapeHtml(signal.modality || "modality")}</span><small>${Number(signal.confidence || 0).toFixed(2)}</small></div>
      <h4>${escapeHtml(signal.emotion || "")} / score ${Number(signal.score || 0).toFixed(1)}</h4>
      <p>${escapeHtml(signal.evidence || "")}</p>
    `;
    list.append(item);
  });
  card.append(list);
}

function renderEmptyConversation() {
  els.messages.innerHTML = `
    <section class="empty-state">
      <div class="empty-visual" aria-hidden="true"></div>
      <div class="empty-copy">
        <p class="kicker">Ready</p>
        <h2>从一次表达开始，形成持续支持</h2>
        <p>系统会把文本、语音和多模态信号接入当前支持档案，并按真实链路生成回复、风险提示和后续小任务。</p>
      </div>
      <div class="empty-prompts">
        <button type="button" data-prompt="我最近压力有点大，晚上总是睡不好，想先梳理一下原因。">梳理压力</button>
        <button type="button" data-prompt="我今天状态不太好，想找人聊一聊。">情绪陪伴</button>
        <button type="button" data-prompt="帮我把今天要做的事情拆成几个轻量步骤。">拆解任务</button>
      </div>
    </section>
  `;
}

function clearEmpty() {
  els.messages.querySelector(".empty-state")?.remove();
}

function startNewSession() {
  state.sessionId = null;
  clearAttachments();
  renderPipeline();
  setSession("READY");
  renderEmptyConversation();
  renderConversationHistory();
  els.messageInput.focus();
}

async function loadConversationHistory() {
  if (state.isAdmin || !els.conversationRows) return;
  try {
    const response = await api("/api/chat/sessions");
    state.conversationHistory = await response.json();
  } catch (error) {
    state.conversationHistory = [];
  }
  renderConversationHistory();
}

function renderConversationHistory() {
  if (!els.conversationRows) return;
  els.conversationRows.innerHTML = "";
  if (els.conversationCount) {
    els.conversationCount.textContent = String(state.conversationHistory.length);
  }
  if (!state.conversationHistory.length) {
    els.conversationRows.innerHTML = `<p class="empty-record">暂无历史会话</p>`;
    return;
  }
  state.conversationHistory.slice(0, 10).forEach((item) => {
    const row = document.createElement("button");
    row.type = "button";
    row.className = `history-row risk-${String(item.riskLevel || "LOW").toLowerCase()}${item.sessionId === state.sessionId ? " active" : ""}`;
    row.dataset.sessionId = item.sessionId;
    const media = [
      item.hasAudio ? "语音" : "",
      item.hasImage ? "图像" : "",
      item.hasVideo ? "视频" : ""
    ].filter(Boolean);
    row.innerHTML = `
      <span class="history-row-time">${formatDate(item.updatedAt)}</span>
      <strong>${escapeHtml(item.summary || item.title || "未命名会话")}</strong>
      <span class="history-row-meta">
        <mark>${escapeHtml(item.riskLevel || "LOW")}</mark>
        <small>${media.length ? media.join(" / ") : "纯文本"}</small>
      </span>
    `;
    row.addEventListener("click", () => restoreConversation(item.sessionId));
    els.conversationRows.append(row);
  });
}

async function restoreConversation(sessionId) {
  if (!sessionId || state.sending) return;
  try {
    const response = await api(`/api/chat/sessions/${encodeURIComponent(sessionId)}`);
    const conversation = await response.json();
    state.sessionId = conversation.sessionId;
    els.messages.innerHTML = "";
    (conversation.messages || []).forEach((message) => {
      const role = String(message.role || "").toLowerCase() === "assistant" ? "assistant" : "user";
      addMessage(role, message.content || "");
    });
    if (!(conversation.messages || []).length) {
      renderEmptyConversation();
    }
    setSession("RESTORED", "ok");
    renderPipeline();
    renderConversationHistory();
    els.messages.scrollTop = els.messages.scrollHeight;
  } catch (error) {
    setSession("FAILED", "danger");
  }
}

function parseSse(buffer, onEvent) {
  const blocks = buffer.split("\n\n");
  const rest = blocks.pop() || "";
  for (const block of blocks) {
    const data = block.split("\n").find((line) => line.startsWith("data:"));
    if (data) onEvent(JSON.parse(data.slice(5)));
  }
  return rest;
}

async function sendChat(event) {
  event.preventDefault();
  if (state.sending || state.isAdmin) return;
  const message = els.messageInput.value.trim();
  const files = selectedFiles();
  if (!message && !files.length) return;

  state.sending = true;
  els.sendButton.disabled = true;
  els.messageInput.value = "";
  clearEmpty();
  setSession("RUNNING", "warn");
  renderPipeline("input");

  const visibleInput = [
    message || "学生上传了多模态内容",
    ...files.map(([, label, file]) => `${label}: ${file.name}`)
  ].join("\n");
  const userCard = addMessage("user", visibleInput);
  renderMessageAttachments(userCard, files);
  const assistant = addMessage("assistant", "");

  try {
    const response = files.length ? await sendMultimodal(message, files) : await sendText(message);
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let output = "";

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      buffer = parseSse(buffer, (eventData) => {
        if (eventData.type === "phase") {
          updatePipelinePhase(eventData.phase);
        }
        if (eventData.type === "meta") {
          state.sessionId = eventData.sessionId;
        }
        if (eventData.type === "token") {
          output += eventData.content;
          updateAssistant(assistant, output);
        }
        if (eventData.type === "multimodal") {
          renderMultimodalSignals(assistant, eventData.multimodalSignals || []);
        }
        if (eventData.type === "citations") {
          renderCitations(assistant, eventData.citations || [], Boolean(eventData.grounded), eventData.review || "", eventData.answerBasis || "");
        }
        if (eventData.type === "error") {
          output = eventData.content || "模型暂时没有返回内容。";
          updateAssistant(assistant, output);
        }
      });
    }

    if (!output) updateAssistant(assistant, "模型暂时没有返回内容。");
    setSession("READY", "ok");
  } catch (error) {
    updateAssistant(assistant, "请求失败，请确认后端和 Ollama 已启动。");
    setSession("FAILED", "danger");
  } finally {
    state.sending = false;
    els.sendButton.disabled = false;
    clearAttachments();
    await loadConversationHistory();
    els.messageInput.focus();
  }
}

function sendText(message) {
  return api("/api/chat/stream", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId: state.sessionId, message })
  });
}

function sendMultimodal(message, files) {
  const body = new FormData();
  body.append("message", message || "学生上传了多模态内容，希望获得支持。");
  if (state.sessionId) body.append("sessionId", state.sessionId);
  files.forEach(([key, , file]) => body.append(key, file));
  return api("/api/chat/multimodal/stream", { method: "POST", body });
}

async function loadStudentSupport() {
  if (state.isAdmin) return;
  try {
    const [moodResponse, taskResponse, trendResponse, goalResponse] = await Promise.all([
      api("/api/support/mood"),
      api("/api/support/tasks"),
      api("/api/support/trends?days=14"),
      api("/api/support/goals")
    ]);
    renderMoodEntries(await moodResponse.json());
    renderTasks(await taskResponse.json());
    renderSupportTrend(await trendResponse.json());
    renderGoals(await goalResponse.json());
  } catch (error) {
    renderMoodEntries([]);
    renderTasks([]);
    renderSupportTrend(null);
    renderGoals([]);
  }
}

async function saveMoodEntry(event) {
  event.preventDefault();
  const payload = {
    moodScore: state.moodScore,
    moodLabel: els.moodLabel.value.trim() || "未命名",
    trigger: els.moodTrigger.value.trim(),
    note: els.moodNote.value.trim()
  };
  await api("/api/support/mood", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  els.moodNote.value = "";
  els.moodTrigger.value = "";
  await loadStudentSupport();
}

async function saveSupportTask(event) {
  event.preventDefault();
  const title = els.taskTitle.value.trim();
  if (!title) return;
  await api("/api/support/tasks", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      title,
      category: "SELF_CARE",
      detail: els.taskDetail.value.trim()
    })
  });
  els.taskTitle.value = "";
  els.taskDetail.value = "";
  await loadStudentSupport();
}

async function seedSuggestedTasks() {
  await api("/api/support/tasks/suggested", { method: "POST" });
  await loadStudentSupport();
}

async function generateSmartTasks() {
  await api("/api/support/tasks/smart", { method: "POST" });
  await loadStudentSupport();
}

async function createDefaultGoal(category) {
  await api("/api/support/goals", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      title: `${category} support goal`,
      category,
      detail: "Use this goal to keep future conversations and tiny tasks focused.",
      active: true
    })
  });
  await loadStudentSupport();
}

async function toggleTask(taskId, completed) {
  await api(`/api/support/tasks/${taskId}?completed=${completed}`, { method: "PATCH" });
  await loadStudentSupport();
}

function setMoodScore(score) {
  state.moodScore = Number(score);
  els.moodScale.querySelectorAll("button").forEach((button) => {
    button.classList.toggle("active", Number(button.dataset.score) === state.moodScore);
  });
}

function renderMoodEntries(entries) {
  els.moodRows.innerHTML = "";
  if (!entries.length) {
    els.moodRows.append(emptyRecord("还没有情绪记录"));
    return;
  }
  entries.slice(0, 5).forEach((entry) => {
    const row = document.createElement("article");
    row.className = "support-row";
    row.innerHTML = `
      <div><strong>${escapeHtml(entry.moodLabel)}</strong><span>${entry.moodScore}/5</span></div>
      <small>${formatDate(entry.createdAt)}${entry.trigger ? " · " + escapeHtml(entry.trigger) : ""}</small>
      <p>${escapeHtml(entry.note || "无备注")}</p>
    `;
    els.moodRows.append(row);
  });
}

function renderTasks(tasks) {
  els.taskRows.innerHTML = "";
  if (!tasks.length) {
    els.taskRows.append(emptyRecord("暂无小任务"));
    return;
  }
  tasks.slice(0, 8).forEach((task) => {
    const row = document.createElement("article");
    row.className = `support-row task ${task.completed ? "completed" : ""}`;
    row.innerHTML = `
      <label>
        <input type="checkbox" ${task.completed ? "checked" : ""}>
        <span>${escapeHtml(task.title)}</span>
      </label>
      <small>${escapeHtml(task.category || "SELF_CARE")}</small>
      ${task.detail ? `<p>${escapeHtml(task.detail)}</p>` : ""}
      ${task.recommendationReason ? `<small>${escapeHtml(task.recommendationReason)}</small>` : ""}
    `;
    row.querySelector("input").addEventListener("change", (event) => toggleTask(task.id, event.target.checked));
    els.taskRows.append(row);
  });
}

function renderSupportTrend(trend) {
  if (!els.moodTrendSummary || !els.moodTrendChart || !els.triggerRows || !els.taskInsight) return;
  els.moodTrendSummary.innerHTML = "";
  els.moodTrendChart.innerHTML = "";
  els.triggerRows.innerHTML = "";
  els.taskInsight.innerHTML = "";
  if (!trend) {
    els.moodTrendSummary.append(emptyRecord("Trend unavailable"));
    return;
  }
  const badge = document.createElement("div");
  badge.className = `insight-badge ${trend.lowMoodAlert ? "danger" : "ok"}`;
  badge.innerHTML = `<strong>${escapeHtml(trend.latestRiskLevel || "LOW")}</strong><span>${escapeHtml(trend.careSuggestion || "")}</span>`;
  els.moodTrendSummary.append(badge);

  (trend.moodTrend || []).slice(-14).forEach((point) => {
    const bar = document.createElement("div");
    bar.className = "trend-bar";
    bar.style.height = `${Math.max(12, Number(point.averageScore || 0) * 18)}px`;
    bar.title = `${point.date}: ${Number(point.averageScore || 0).toFixed(1)}/5`;
    els.moodTrendChart.append(bar);
  });
  if (!els.moodTrendChart.children.length) {
    els.moodTrendChart.append(emptyRecord("No mood trend yet"));
  }

  (trend.triggerClusters || []).forEach((cluster) => {
    const row = document.createElement("article");
    row.className = "support-row";
    row.innerHTML = `<div><strong>${escapeHtml(cluster.trigger)}</strong><span>${cluster.count}</span></div><small>avg ${Number(cluster.averageScore || 0).toFixed(1)}/5</small>`;
    els.triggerRows.append(row);
  });

  const percent = Math.round(Number(trend.taskCompletionRate || 0) * 100);
  els.taskInsight.innerHTML = `<div class="insight-badge"><strong>${percent}%</strong><span>${trend.completedTasks || 0}/${trend.totalTasks || 0} completed</span></div>`;
}

function renderGoals(goals) {
  if (!els.goalRows) return;
  els.goalRows.innerHTML = "";
  const head = document.createElement("article");
  head.className = "support-row";
  head.innerHTML = `
    <div><strong>Support goals</strong><span>${goals.filter((goal) => goal.active).length}</span></div>
    <small>sleep / study / connection / emotion stability</small>
    <div class="goal-actions">
      ${["SLEEP_SUPPORT", "STUDY_STRESS", "CONNECTION", "EMOTION_STABILITY"]
        .map((category) => `<button type="button" data-goal="${category}">${category}</button>`)
        .join("")}
    </div>
  `;
  head.querySelectorAll("[data-goal]").forEach((button) => {
    button.addEventListener("click", () => createDefaultGoal(button.dataset.goal));
  });
  els.goalRows.append(head);
  goals.slice(0, 4).forEach((goal) => {
    const row = document.createElement("article");
    row.className = "support-row";
    row.innerHTML = `<div><strong>${escapeHtml(goal.title)}</strong><span>${goal.active ? "ACTIVE" : "PAUSED"}</span></div><small>${escapeHtml(goal.category || "")}</small><p>${escapeHtml(goal.detail || "")}</p>`;
    els.goalRows.append(row);
  });
}

function supportGoalLabel(category) {
  const labels = {
    SLEEP_SUPPORT: "睡眠支持",
    STUDY_STRESS: "学习压力",
    CONNECTION: "人际连接",
    EMOTION_STABILITY: "情绪稳定"
  };
  return labels[category] || category || "支持目标";
}

function renderMoodEntries(entries) {
  els.moodRows.innerHTML = "";
  if (!entries.length) {
    els.moodRows.append(emptyRecord("还没有情绪记录"));
    return;
  }
  entries.slice(0, 5).forEach((entry) => {
    const row = document.createElement("article");
    row.className = "support-row mood-entry";
    row.innerHTML = `
      <div><strong>${escapeHtml(entry.moodLabel)}</strong><span>${entry.moodScore}/5</span></div>
      <small>${formatDate(entry.createdAt)}${entry.trigger ? " · " + escapeHtml(entry.trigger) : ""}</small>
      <p>${escapeHtml(entry.note || "没有补充备注")}</p>
    `;
    els.moodRows.append(row);
  });
}

function renderTasks(tasks) {
  els.taskRows.innerHTML = "";
  if (!tasks.length) {
    els.taskRows.append(emptyRecord("今天还没有小任务"));
    return;
  }
  tasks.slice(0, 8).forEach((task) => {
    const row = document.createElement("article");
    row.className = `support-row task ${task.completed ? "completed" : ""}`;
    row.innerHTML = `
      <label>
        <input type="checkbox" ${task.completed ? "checked" : ""}>
        <span>${escapeHtml(task.title)}</span>
      </label>
      <small>${escapeHtml(supportGoalLabel(task.category || "SELF_CARE"))}</small>
      ${task.detail ? `<p>${escapeHtml(task.detail)}</p>` : ""}
      ${task.recommendationReason ? `<small>${escapeHtml(task.recommendationReason)}</small>` : ""}
    `;
    row.querySelector("input").addEventListener("change", (event) => toggleTask(task.id, event.target.checked));
    els.taskRows.append(row);
  });
}

function renderSupportTrend(trend) {
  if (!els.moodTrendSummary || !els.moodTrendChart || !els.triggerRows || !els.taskInsight) return;
  els.moodTrendSummary.innerHTML = "";
  els.moodTrendChart.innerHTML = "";
  els.triggerRows.innerHTML = "";
  els.taskInsight.innerHTML = "";
  if (!trend) {
    els.moodTrendSummary.append(emptyRecord("趋势数据暂不可用"));
    return;
  }
  const badge = document.createElement("div");
  badge.className = `insight-badge ${trend.lowMoodAlert ? "danger" : "ok"}`;
  badge.innerHTML = `<strong>${trend.lowMoodAlert ? "需要关注" : "状态平稳"}</strong><span>${escapeHtml(trend.careSuggestion || "最近状态比较稳定，可以保持轻量记录和自我照顾。")}</span>`;
  els.moodTrendSummary.append(badge);

  (trend.moodTrend || []).slice(-14).forEach((point) => {
    const bar = document.createElement("div");
    bar.className = "trend-bar";
    bar.style.height = `${Math.max(12, Number(point.averageScore || 0) * 18)}px`;
    bar.title = `${point.date}: ${Number(point.averageScore || 0).toFixed(1)}/5`;
    els.moodTrendChart.append(bar);
  });
  if (!els.moodTrendChart.children.length) {
    els.moodTrendChart.append(emptyRecord("记录几次后会生成趋势"));
  }

  (trend.triggerClusters || []).forEach((cluster) => {
    const row = document.createElement("article");
    row.className = "support-row trigger-entry";
    row.innerHTML = `<div><strong>${escapeHtml(cluster.trigger)}</strong><span>${cluster.count} 次</span></div><small>平均心情 ${Number(cluster.averageScore || 0).toFixed(1)}/5</small>`;
    els.triggerRows.append(row);
  });

  const percent = Math.round(Number(trend.taskCompletionRate || 0) * 100);
  els.taskInsight.innerHTML = `<div class="insight-badge progress-badge"><strong>${percent}%</strong><span>已完成 ${trend.completedTasks || 0}/${trend.totalTasks || 0}</span></div>`;
}

function renderGoals(goals) {
  if (!els.goalRows) return;
  els.goalRows.innerHTML = "";
  const activeCount = goals.filter((goal) => goal.active).length;
  const head = document.createElement("article");
  head.className = "support-row goal-summary";
  head.innerHTML = `
    <div><strong>支持目标</strong><span>${activeCount} 个进行中</span></div>
    <small>选择一个方向，让后续对话更连续</small>
    <div class="goal-actions">
      ${["SLEEP_SUPPORT", "STUDY_STRESS", "CONNECTION", "EMOTION_STABILITY"]
        .map((category) => `<button type="button" data-goal="${category}">${supportGoalLabel(category)}</button>`)
        .join("")}
    </div>
  `;
  head.querySelectorAll("[data-goal]").forEach((button) => {
    button.addEventListener("click", () => createDefaultGoal(button.dataset.goal));
  });
  els.goalRows.append(head);
  goals.slice(0, 4).forEach((goal) => {
    const row = document.createElement("article");
    row.className = "support-row goal-entry";
    row.innerHTML = `<div><strong>${escapeHtml(goal.title)}</strong><span>${goal.active ? "进行中" : "暂停"}</span></div><small>${escapeHtml(supportGoalLabel(goal.category || ""))}</small><p>${escapeHtml(goal.detail || "")}</p>`;
    els.goalRows.append(row);
  });
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : "";
}

function riskTone(risk) {
  if (risk === "HIGH" || risk === "FAILED" || risk === "PENDING" || risk === "OPEN" || risk === "ESCALATED" || risk === "UNREACHABLE") return "danger";
  if (risk === "MEDIUM" || risk === "IN_PROGRESS" || risk === "ACKNOWLEDGED" || risk === "CONTACTED" || risk === "REFERRED") return "warn";
  if (risk === "LOW" || risk === "SUCCESS" || risk === "CLOSED" || risk === "RESOLVED") return "ok";
  return "";
}

function slaLabel(ticket) {
  if (isClosedTicket(ticket)) return "SLA closed";
  if (ticket.slaBreached) return "SLA overdue";
  if (ticket.slaMinutesRemaining === undefined || ticket.slaMinutesRemaining === null) return "SLA n/a";
  return `${ticket.slaMinutesRemaining}m left`;
}

function isClosedTicket(ticket) {
  return ticket?.status === "CLOSED" || ticket?.status === "RESOLVED";
}

function statCard(label, value, kind, onClick) {
  const node = document.createElement("article");
  node.className = `stat-card ${kind || ""} ${onClick ? "clickable" : ""}`;
  node.innerHTML = `<strong>${value}</strong><span>${label}</span>`;
  if (onClick) {
    node.addEventListener("click", onClick);
  }
  return node;
}

function renderAdminStats(reports, excelRecords, alerts, tickets = [], visibleTickets = tickets) {
  els.adminStats.innerHTML = "";
  const high = reports.filter((item) => item.riskLevel === "HIGH").length;
  const medium = reports.filter((item) => item.riskLevel === "MEDIUM").length;
  const mailFailed = alerts.filter((item) => item.status === "FAILED").length;
  const openTickets = tickets.filter((item) => !isClosedTicket(item)).length;
  const escalated = tickets.filter((item) => item.status === "ESCALATED").length;
  const slaBreached = tickets.filter((item) => item.slaBreached).length;
  const filteredOpen = visibleTickets.filter((item) => !isClosedTicket(item)).length;
  els.queueCount.textContent = openTickets;
  els.adminStats.append(
    statCard("待处置", openTickets, openTickets ? "danger" : "ok", () => quickTicketFilter({ status: "!RESOLVED" })),
    statCard("筛选结果", visibleTickets.length, visibleTickets.length ? "warn" : "ok"),
    statCard("筛选待办", filteredOpen, filteredOpen ? "danger" : "ok"),
    statCard("已升级", escalated, escalated ? "danger" : "ok", () => quickTicketFilter({ status: "ESCALATED" })),
    statCard("SLA超时", slaBreached, slaBreached ? "danger" : "ok", () => quickTicketFilter({ search: "SLA_BREACHED" })),
    statCard("高风险", high, "danger", () => quickTicketFilter({ risk: "HIGH" })),
    statCard("需关注", medium, "warn"),
    statCard("邮件失败", mailFailed, mailFailed ? "danger" : "ok")
  );
}

function renderDashboardPanel(dashboard, auditLogs = []) {
  let panel = $("#dashboardTrendPanel");
  if (!panel) {
    panel = document.createElement("section");
    panel.id = "dashboardTrendPanel";
    panel.className = "dashboard-trends";
    els.adminStats.insertAdjacentElement("afterend", panel);
  }
  const riskPoints = dashboard?.highRiskTrend || [];
  const ticketPoints = dashboard?.openTicketTrend || [];
  const sources = dashboard?.riskSources || [];
  const anomalies = dashboard?.anomalies || [];
  panel.innerHTML = `
    <article class="trend-panel">
      <div><strong>High-risk trend</strong><span>${riskPoints.reduce((sum, point) => sum + point.highRiskReports, 0)}</span></div>
      <div class="mini-bars">${riskPoints.map((point) => `<i style="height:${Math.max(8, point.highRiskReports * 14)}px" title="${point.date}: ${point.highRiskReports}"></i>`).join("")}</div>
    </article>
    <article class="trend-panel">
      <div><strong>Open tickets</strong><span>${ticketPoints.reduce((sum, point) => sum + point.openTickets, 0)}</span></div>
      <div class="mini-bars">${ticketPoints.map((point) => `<i style="height:${Math.max(8, point.openTickets * 14)}px" title="${point.date}: ${point.openTickets}"></i>`).join("")}</div>
    </article>
    <article class="trend-panel">
      <div><strong>Avg response</strong><span>${Number(dashboard?.averageResponseMinutes || 0).toFixed(0)}m</span></div>
      <p>${sources.map((item) => `${item.name}:${item.count}`).join(" · ") || "No source data"}</p>
    </article>
    <article class="trend-panel">
      <div><strong>Audit</strong><span>${auditLogs.length}</span></div>
      <p>${auditLogs.slice(0, 3).map((log) => `${log.action}`).join(" · ") || "No audit logs"}</p>
      <button type="button" id="exportRiskCsv" class="secondary">Export CSV</button>
    </article>
  `;
  $("#exportRiskCsv")?.addEventListener("click", () => {
    window.open("/api/admin/export/risk-tickets.csv", "_blank");
  });
  anomalies.forEach((student) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "record-card anomaly-card";
    button.innerHTML = `<div><strong>${escapeHtml(student.username)}</strong><span class="${riskTone(student.riskLevel)}">${escapeHtml(student.riskLevel)}</span></div><small>${escapeHtml(student.reason)} · mood ${Number(student.moodAverage || 0).toFixed(1)}</small>`;
    button.addEventListener("click", () => openStudentProfile(student.userId));
    panel.append(button);
  });
}

function renderCaseRows(cases = []) {
  if (!els.caseRows) return;
  els.caseRows.innerHTML = "";
  if (els.caseCount) els.caseCount.textContent = cases.length;
  if (!cases.length) {
    els.caseRows.append(emptyRecord("暂无学生支持档案"));
    return;
  }
  cases.slice(0, 12).forEach((item) => {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "case-card";
    const mood = Number(item.moodAverage30d || 0);
    card.innerHTML = `
      <div class="case-card-main">
        <strong>${escapeHtml(item.displayName || item.username)}</strong>
        <span class="${riskTone(item.latestRiskLevel)}">${escapeHtml(item.latestRiskLevel || "LOW")}</span>
      </div>
      <div class="case-metrics">
        <span>30天心情 ${mood ? mood.toFixed(1) : "-"}</span>
        <span>工单 ${item.openTickets || 0}</span>
        <span>目标 ${item.activeGoals || 0}</span>
        <span>待办 ${item.pendingTasks || 0}</span>
      </div>
      <p>${escapeHtml(item.nextStep || "继续观察")}</p>
    `;
    card.addEventListener("click", () => openStudentProfile(item.userId));
    els.caseRows.append(card);
  });
}

function emptyRecord(text) {
  const node = document.createElement("p");
  node.className = "empty-record";
  node.textContent = text;
  return node;
}

function recordButton(title, badge, meta, summary, onClick) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "record-card";
  button.innerHTML = `
    <div><strong>${escapeHtml(title)}</strong><span class="${riskTone(badge)}">${escapeHtml(badge || "SKIPPED")}</span></div>
    <small>${escapeHtml(meta || "")}</small>
    <p>${escapeHtml(summary || "无摘要")}</p>
  `;
  button.addEventListener("click", onClick);
  return button;
}

function ticketMatchesFilters(ticket) {
  const filters = state.ticketFilters;
  const assignee = ticket.assignedTo || "未分派";
  const search = [
    ticket.username,
    ticket.summary,
    ticket.content,
    ticket.handlerNote,
    ticket.actionLog,
    ...(ticket.events || []).map((event) => `${event.eventType} ${event.outcome} ${event.note}`),
    ticket.assignedTo,
    ticket.status,
    ticket.riskLevel
  ].join(" ").toLowerCase();
  const statusMatches = !filters.status
    || (filters.status === "!RESOLVED" ? !isClosedTicket(ticket) : ticket.status === filters.status);
  return statusMatches
    && (!filters.risk || ticket.riskLevel === filters.risk)
    && (!filters.assignee || assignee === filters.assignee)
    && (!filters.search || search.includes(filters.search.toLowerCase()));
}

function filteredTickets() {
  return state.latestTickets.filter(ticketMatchesFilters);
}

function renderAssigneeFilter(tickets) {
  const selected = state.ticketFilters.assignee;
  const assignees = [...new Set(tickets.map((ticket) => ticket.assignedTo || "未分派"))].sort();
  els.ticketAssigneeFilter.innerHTML = `<option value="">全部负责人</option>`;
  assignees.forEach((assignee) => {
    const option = document.createElement("option");
    option.value = assignee;
    option.textContent = assignee;
    option.selected = assignee === selected;
    els.ticketAssigneeFilter.append(option);
  });
}

function applyTicketFilters() {
  const visibleTickets = filteredTickets();
  renderAdminStats(state.latestReports, state.latestExcelRecords || [], state.latestAlerts || [], state.latestTickets, visibleTickets);
  renderTicketRows(visibleTickets);
}

function renderTicketRows(tickets) {
  els.adminReportRows.innerHTML = "";
  if (!tickets.length) {
    els.adminReportRows.append(emptyRecord("没有匹配的风险处置工单"));
    return;
  }
  tickets.slice(0, 24).forEach((item) => {
    els.adminReportRows.append(recordButton(
      `#${item.id} / ${item.username}`,
      item.status,
      `${item.riskLevel} · ${item.assignedTo || "未分派"} · ${slaLabel(item)} · ${formatDate(item.updatedAt)}`,
      item.handlerNote || item.summary || item.content,
      () => openTicket(item)
    ));
  });
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function renderReportRows(reports) {
  els.adminReportRows.innerHTML = "";
  if (!reports.length) {
    els.adminReportRows.append(emptyRecord("暂无风险记录"));
    return;
  }
  reports.slice(0, 24).forEach((item) => {
    els.adminReportRows.append(recordButton(
      `${item.username} / ${item.emotion}`,
      item.riskLevel,
      `${item.intent} · ${formatDate(item.createdAt)}`,
      item.summary,
      () => item.sessionId ? openConversation(item) : openRecord("报告详情", item)
    ));
  });
}

function renderReportRows(reports) {
  els.adminReportRows.innerHTML = "";
  if (!reports.length) {
    els.adminReportRows.append(emptyRecord("暂无风险记录"));
    return;
  }
  reports.slice(0, 24).forEach((item) => {
    els.adminReportRows.append(recordButton(
      `${item.username} / ${item.emotion}`,
      item.riskLevel,
      `${item.intent} / ${formatDate(item.createdAt)}`,
      `${item.summary || ""}${item.reviewRecommended ? " / 建议人工复核" : ""}`,
      () => openReportDetail(item)
    ));
  });
}

function openReportDetail(report) {
  els.detailOverlay.hidden = false;
  updateRailNavigation("detailOverlay");
  els.detailKicker.textContent = `Report #${report.id}`;
  els.detailTitle.textContent = `${report.username} / ${report.riskLevel}`;
  els.detailMeta.textContent = `${report.emotion} / confidence ${Number(report.confidence || 0).toFixed(2)} / ${formatDate(report.createdAt)}`;
  els.detailBody.innerHTML = "";
  els.detailBody.append(detailRow("Intent", report.intent));
  els.detailBody.append(detailRow("Risk", report.riskLevel));
  els.detailBody.append(detailRow("Emotion", `${report.emotion} / score ${Number(report.emotionScore || 0).toFixed(2)}`));
  els.detailBody.append(detailRow("Confidence", Number(report.confidence || 0).toFixed(2)));
  els.detailBody.append(detailRow("Manual review", report.reviewRecommended ? "建议人工复核" : "置信度可接受"));
  els.detailBody.append(detailRow("Summary", report.summary || "none"));
  els.detailBody.append(detailRow("Fusion explanation", report.fusionExplanation || "none"));
  els.detailBody.append(detailRow("Modality scores", prettyJson(report.modalityScoresJson)));
  els.detailBody.append(detailRow("Privacy-safe evidence", prettyJson(report.rawEvidenceJson)));
  els.detailBody.append(detailRow("Emotion tags", prettyJson(report.emotionTags)));
  const actions = document.createElement("section");
  actions.className = "ticket-editor";
  actions.innerHTML = `
    <label><span>Corrected risk</span><select id="modelFeedbackRisk"><option value="">No change</option><option value="LOW">LOW</option><option value="MEDIUM">MEDIUM</option><option value="HIGH">HIGH</option></select></label>
    <label><span>Feedback note</span><textarea id="modelFeedbackNote" rows="3" placeholder="为什么是误报或漏报"></textarea></label>
    <div class="form-actions two">
      <button id="markFalsePositive" type="button" class="secondary">误报</button>
      <button id="markFalseNegative" type="button" class="secondary">漏报</button>
    </div>
  `;
  els.detailBody.append(actions);
  $("#markFalsePositive").addEventListener("click", () => sendModelFeedback(report.id, "FALSE_POSITIVE"));
  $("#markFalseNegative").addEventListener("click", () => sendModelFeedback(report.id, "FALSE_NEGATIVE"));
  if (report.sessionId) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "secondary detail-action";
    button.textContent = "查看完整会话";
    button.addEventListener("click", () => openConversation(report));
    els.detailBody.append(button);
  }
}

function prettyJson(value) {
  if (!value) return "none";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

async function sendModelFeedback(reportId, feedbackType) {
  await api(`/api/admin/reports/${reportId}/feedback`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      feedbackType,
      correctedRiskLevel: $("#modelFeedbackRisk")?.value || null,
      note: $("#modelFeedbackNote")?.value || ""
    })
  });
  els.detailBody.prepend(emptyRecord("已记录模型反馈"));
}

function renderExcelRows(records) {
  els.excelRows.innerHTML = "";
  if (!records.length) {
    els.excelRows.append(emptyRecord("暂无 Excel 记录"));
    return;
  }
  records.slice(0, 24).forEach((item) => {
    els.excelRows.append(recordButton(
      `#${item.reportId} / ${item.username}`,
      item.excelStatus,
      `${item.emotion} · ${item.riskLevel} · ${formatDate(item.createdAt)}`,
      item.summary || item.content,
      () => openRecord("Excel 写入", item)
    ));
  });
}

function renderEmailRows(records) {
  els.emailRows.innerHTML = "";
  if (!records.length) {
    els.emailRows.append(emptyRecord("暂无预警邮件"));
    return;
  }
  records.slice(0, 24).forEach((item) => {
    els.emailRows.append(recordButton(
      `#${item.reportId} / ${item.recipient}`,
      item.status,
      `${item.riskLevel} · ${item.attempts} 次 · ${formatDate(item.updatedAt)}`,
      item.errorMessage || item.summary,
      () => openRecord("邮件预警", item)
    ));
  });
}

function detailRow(label, value) {
  const row = document.createElement("div");
  row.className = "detail-row";
  row.innerHTML = `<span>${escapeHtml(label)}</span><strong>${escapeHtml(value ?? "无")}</strong>`;
  return row;
}

function openRecord(title, record) {
  els.detailOverlay.hidden = false;
  updateRailNavigation("detailOverlay");
  els.detailKicker.textContent = "记录详情";
  els.detailTitle.textContent = title;
  els.detailMeta.textContent = formatDate(record.createdAt || record.updatedAt);
  els.detailBody.innerHTML = "";
  Object.entries(record).forEach(([key, value]) => {
    if (value !== null && value !== undefined && typeof value !== "object") {
      els.detailBody.append(detailRow(key, value));
    }
  });
}

function openTicket(ticket) {
  els.detailOverlay.hidden = false;
  updateRailNavigation("detailOverlay");
  els.detailKicker.textContent = `工单 #${ticket.id} / 报告 #${ticket.reportId}`;
  els.detailTitle.textContent = `${ticket.username} · ${ticket.status}`;
  els.detailMeta.textContent = `${ticket.riskLevel} · ${formatDate(ticket.updatedAt)}`;
  els.detailBody.innerHTML = `
    <section class="ticket-editor">
      <label>
        <span>处置状态</span>
        <select id="ticketStatus">
          ${["PENDING", "IN_PROGRESS", "CONTACTED", "UNREACHABLE", "REFERRED", "ESCALATED", "CLOSED", "OPEN", "ACKNOWLEDGED", "RESOLVED"]
            .map((status) => `<option value="${status}" ${status === ticket.status ? "selected" : ""}>${status}</option>`)
            .join("")}
        </select>
      </label>
      <label>
        <span>负责人</span>
        <input id="ticketAssignedTo" value="${escapeHtml(ticket.assignedTo || "")}" placeholder="辅导员或处理人">
      </label>
      <label>
        <span>处理备注</span>
        <textarea id="ticketNote" rows="4" placeholder="记录已联系、转介、后续跟进安排">${escapeHtml(ticket.handlerNote || "")}</textarea>
      </label>
      <label>
        <span>Contact method</span>
        <select id="ticketContactMethod">
          ${["", "PHONE", "SMS", "IN_APP", "OFFLINE", "FAMILY", "COUNSELOR"]
            .map((method) => `<option value="${method}" ${method === (ticket.contactMethod || "") ? "selected" : ""}>${method || "No contact yet"}</option>`)
            .join("")}
        </select>
      </label>
      <label>
        <span>Contact target</span>
        <input id="ticketContactTarget" value="${escapeHtml(ticket.contactTarget || "")}" placeholder="student / family / counselor">
      </label>
      <label>
        <span>Referred to</span>
        <input id="ticketReferredTo" value="${escapeHtml(ticket.referredTo || "")}" placeholder="psychological center / hospital / counselor">
      </label>
      <button id="saveTicket" type="button">保存处置</button>
    </section>
  `;
  els.detailBody.append(detailRow("风险等级", ticket.riskLevel));
  els.detailBody.append(detailRow("情绪标签", ticket.emotion));
  els.detailBody.append(detailRow("摘要", ticket.summary || "无"));
  els.detailBody.append(detailRow("原始内容", ticket.content || "无"));
  els.detailBody.append(detailRow("处理记录", ticket.actionLog || "暂无"));
  els.detailBody.append(detailRow("SLA", `${slaLabel(ticket)} / reminder ${ticket.slaReminderStage || 0}m`));
  els.detailBody.append(detailRow("首次响应", formatDate(ticket.firstRespondedAt) || "未响应"));
  els.detailBody.append(detailRow("最近联系", formatDate(ticket.lastContactAt) || "未联系"));
  els.detailBody.append(detailRow("联系方式", ticket.contactMethod || "未记录"));
  els.detailBody.append(detailRow("联系对象", ticket.contactTarget || "未记录"));
  els.detailBody.append(detailRow("转介对象", ticket.referredTo || "未转介"));
  if (ticket.sessionId) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "secondary detail-action";
    button.textContent = "查看完整会话";
    button.addEventListener("click", () => openConversation(ticket));
    els.detailBody.append(button);
  }
  const profileButton = document.createElement("button");
  profileButton.type = "button";
  profileButton.className = "secondary detail-action";
  profileButton.textContent = "Student profile";
  profileButton.addEventListener("click", () => openStudentProfile(ticket.userId));
  els.detailBody.append(profileButton);
  const notifyButton = document.createElement("button");
  notifyButton.type = "button";
  notifyButton.className = "secondary detail-action";
  notifyButton.textContent = "Trigger notification";
  notifyButton.addEventListener("click", () => triggerTicketNotification(ticket.id));
  els.detailBody.append(notifyButton);
  els.detailBody.querySelector(".ticket-editor").insertAdjacentHTML("beforeend", `
      <label>
        <span>Resolution type</span>
        <select id="ticketResolutionType">
          ${["", "FALSE_POSITIVE", "STABILIZED", "REFERRED", "FOLLOW_UP_PLANNED", "CONTINUED_MONITORING"]
            .map((type) => `<option value="${type}" ${type === (ticket.resolutionType || "") ? "selected" : ""}>${type || "Not closed"}</option>`)
            .join("")}
        </select>
      </label>
      <label>
        <span>Closure reason</span>
        <select id="ticketClosureReason">
          ${["", "FALSE_POSITIVE", "STABILIZED", "REFERRED", "CONTINUED_FOLLOW_UP"]
            .map((reason) => `<option value="${reason}" ${reason === (ticket.closureReason || "") ? "selected" : ""}>${reason || "Not closed"}</option>`)
            .join("")}
        </select>
      </label>
      <label>
        <span>Resolution reason</span>
        <textarea id="ticketResolutionReason" rows="3">${escapeHtml(ticket.resolutionReason || "")}</textarea>
      </label>
  `);
  renderTicketTimeline(ticket);
  $("#saveTicket").addEventListener("click", () => saveTicket(ticket.id));
}

async function openStudentProfile(userId) {
  els.detailOverlay.hidden = false;
  updateRailNavigation("detailOverlay");
  els.detailKicker.textContent = "学生支持档案";
  els.detailTitle.textContent = `Student #${userId}`;
  els.detailMeta.textContent = "Loading...";
  els.detailBody.innerHTML = `<p class="empty-record">Loading profile...</p>`;
  try {
    const response = await api(`/api/admin/students/${userId}/profile`);
    const profile = await response.json();
    els.detailTitle.textContent = profile.displayName || profile.username;
    els.detailMeta.textContent = profile.currentSupportFocus || profile.trend?.careSuggestion || "";
    els.detailBody.innerHTML = "";
    const summary = document.createElement("section");
    summary.className = "case-profile-summary";
    summary.innerHTML = `
      <article><strong>${(profile.riskTickets || []).filter((ticket) => !isClosedTicket(ticket)).length}</strong><span>未结工单</span></article>
      <article><strong>${Math.round(Number(profile.trend?.taskCompletionRate || 0) * 100)}%</strong><span>任务完成率</span></article>
      <article><strong>${profile.trend?.lowMoodAlert ? "YES" : "NO"}</strong><span>低分预警</span></article>
      <article><strong>${(profile.goals || []).filter((goal) => goal.active).length}</strong><span>活跃目标</span></article>
    `;
    els.detailBody.append(summary);

    const windows = profileSection("7 / 14 / 30 天趋势");
    (profile.summaries || []).forEach((item) => {
      windows.append(detailRow(
        `${item.days} 天`,
        `心情 ${Number(item.averageMood || 0).toFixed(1)} / 记录 ${item.moodEntries || 0} / 任务 ${item.completedTasks || 0}/${item.totalTasks || 0} / ${item.latestRiskLevel || "LOW"}`
      ));
    });
    els.detailBody.append(windows);

    const goals = profileSection("当前支持目标");
    if ((profile.goals || []).length) {
      (profile.goals || []).forEach((goal) => goals.append(detailRow(
        supportGoalLabel(goal.category),
        `${goal.title} (${goal.active ? "进行中" : "暂停"})${goal.detail ? " / " + goal.detail : ""}`
      )));
    } else {
      goals.append(emptyRecord("尚未设置支持目标"));
    }
    els.detailBody.append(goals);

    const taskSection = profileSection("小任务执行");
    (profile.tasks || []).slice(0, 8).forEach((task) => taskSection.append(detailRow(
      task.completed ? "已完成" : "待完成",
      `${task.title}${task.recommendationReason ? " / " + task.recommendationReason : ""}`
    )));
    if (!taskSection.querySelector(".detail-row")) taskSection.append(emptyRecord("暂无小任务"));
    els.detailBody.append(taskSection);

    const ticketSection = profileSection("风险工单");
    (profile.riskTickets || []).slice(0, 8).forEach((ticket) => ticketSection.append(detailRow(
      `#${ticket.id} / ${ticket.status}`,
      `${ticket.riskLevel} / ${ticket.summary || ticket.content || ""}`
    )));
    if (!ticketSection.querySelector(".detail-row")) ticketSection.append(emptyRecord("暂无风险工单"));
    els.detailBody.append(ticketSection);

    const noteSection = profileSection("管理员备注");
    noteSection.insertAdjacentHTML("beforeend", `
      <form id="caseNoteForm" class="case-note-form">
        <select id="caseNoteType">
          <option value="FOLLOW_UP">跟进备注</option>
          <option value="CONTACT">联系记录</option>
          <option value="PLAN">支持计划</option>
          <option value="HANDOFF">转介交接</option>
        </select>
        <textarea id="caseNoteContent" rows="3" placeholder="记录这次观察、联系结果或后续安排"></textarea>
        <button type="submit">添加备注</button>
      </form>
    `);
    (profile.adminNotes || []).forEach((note) => noteSection.append(detailRow(
      `${note.noteType} / ${note.actor}`,
      `${formatDate(note.createdAt)} / ${note.content}`
    )));
    els.detailBody.append(noteSection);
    $("#caseNoteForm")?.addEventListener("submit", (event) => saveCaseNote(event, profile.userId));

    (profile.recentConversation || []).slice(-8).forEach((message) => {
      const card = document.createElement("article");
      card.className = `conversation-card ${String(message.role || "").toLowerCase()}`;
      card.innerHTML = `<header><strong>${escapeHtml(message.role)}</strong><span>${formatDate(message.createdAt)}</span></header><p>${escapeHtml(message.content)}</p>`;
      els.detailBody.append(card);
    });
  } catch (error) {
    els.detailBody.innerHTML = `<p class="empty-record">Failed to load profile</p>`;
  }
}

function profileSection(title) {
  const section = document.createElement("section");
  section.className = "case-profile-section";
  section.innerHTML = `<h3>${escapeHtml(title)}</h3>`;
  return section;
}

async function saveCaseNote(event, userId) {
  event.preventDefault();
  const content = $("#caseNoteContent")?.value.trim();
  if (!content) return;
  await api(`/api/admin/students/${userId}/notes`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      noteType: $("#caseNoteType")?.value || "FOLLOW_UP",
      content
    })
  });
  await loadAdminData();
  await openStudentProfile(userId);
}

function renderTicketTimeline(ticket) {
  const section = document.createElement("section");
  section.className = "ticket-timeline";
  section.innerHTML = `
    <div class="timeline-head">
      <strong>Crisis timeline</strong>
      <span class="${ticket.slaBreached ? "danger" : "ok"}">${slaLabel(ticket)}</span>
    </div>
    <form id="ticketEventForm" class="ticket-editor">
      <label>
        <span>Event type</span>
        <select id="ticketEventType">
          <option value="CONTACT">CONTACT</option>
          <option value="REFERRAL">REFERRAL</option>
          <option value="ESCALATION">ESCALATION</option>
          <option value="NOTE">NOTE</option>
          <option value="RESOLUTION">RESOLUTION</option>
        </select>
      </label>
      <label>
        <span>Outcome</span>
        <select id="ticketEventOutcome">
          <option value="CONTACTED_SAFE">CONTACTED_SAFE</option>
          <option value="UNREACHABLE">UNREACHABLE</option>
          <option value="REFERRED">REFERRED</option>
          <option value="FOLLOW_UP_PLANNED">FOLLOW_UP_PLANNED</option>
          <option value="FALSE_POSITIVE">FALSE_POSITIVE</option>
          <option value="STABILIZED">STABILIZED</option>
          <option value="SLA_BREACHED">SLA_BREACHED</option>
        </select>
      </label>
      <label>
        <span>Contact method</span>
        <select id="ticketEventContactMethod">
          <option value="">None</option>
          <option value="PHONE">PHONE</option>
          <option value="SMS">SMS</option>
          <option value="IN_APP">IN_APP</option>
          <option value="OFFLINE">OFFLINE</option>
          <option value="FAMILY">FAMILY</option>
          <option value="COUNSELOR">COUNSELOR</option>
        </select>
      </label>
      <label>
        <span>Contact target</span>
        <input id="ticketEventContactTarget" placeholder="student / family / counselor">
      </label>
      <label>
        <span>Note</span>
        <textarea id="ticketEventNote" rows="3"></textarea>
      </label>
      <button type="submit">Add event</button>
    </form>
    <div class="timeline-list"></div>
  `;
  const list = section.querySelector(".timeline-list");
  const events = ticket.events || [];
  if (!events.length) {
    list.append(emptyRecord("No structured intervention event yet"));
  }
  events.forEach((event) => {
    const item = document.createElement("article");
    item.className = "timeline-item";
    const contact = [event.contactMethod, event.contactTarget].filter(Boolean).join(" / ");
    item.innerHTML = `<div><strong>${escapeHtml(event.eventType)}</strong><span>${escapeHtml(event.outcome || "")}</span></div><small>${escapeHtml(event.actor || "system")} · ${formatDate(event.createdAt)}${contact ? " / " + escapeHtml(contact) : ""}</small><p>${escapeHtml(event.note || "")}</p>`;
    list.append(item);
  });
  section.querySelector("#ticketEventForm").addEventListener("submit", (event) => addTicketEvent(event, ticket.id));
  els.detailBody.append(section);
}

async function addTicketEvent(event, ticketId) {
  event.preventDefault();
  const payload = {
    eventType: $("#ticketEventType").value,
    outcome: $("#ticketEventOutcome").value,
    contactMethod: $("#ticketEventContactMethod")?.value || "",
    contactTarget: $("#ticketEventContactTarget")?.value || "",
    note: $("#ticketEventNote").value
  };
  await api(`/api/admin/risk-tickets/${ticketId}/events`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  await loadAdminData();
  const updated = state.latestTickets.find((ticket) => ticket.id === ticketId);
  if (updated) openTicket(updated);
}

async function triggerTicketNotification(ticketId) {
  await api(`/api/admin/risk-tickets/${ticketId}/notify`, { method: "POST" });
  await loadAdminData();
  const updated = state.latestTickets.find((ticket) => ticket.id === ticketId);
  if (updated) openTicket(updated);
}

async function saveTicket(ticketId) {
  const payload = {
    status: $("#ticketStatus").value,
    assignedTo: $("#ticketAssignedTo").value,
    handlerNote: $("#ticketNote").value,
    contactMethod: $("#ticketContactMethod")?.value || "",
    contactTarget: $("#ticketContactTarget")?.value || "",
    referredTo: $("#ticketReferredTo")?.value || "",
    closureReason: $("#ticketClosureReason")?.value || "",
    resolutionType: $("#ticketResolutionType")?.value || "",
    resolutionReason: $("#ticketResolutionReason")?.value || ""
  };
  try {
    const response = await api(`/api/admin/risk-tickets/${ticketId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const updated = await response.json();
    await loadAdminData();
    openTicket(updated);
  } catch (error) {
    els.detailMeta.textContent = "保存失败";
  }
}

async function openConversation(report) {
  els.detailOverlay.hidden = false;
  updateRailNavigation("detailOverlay");
  els.detailKicker.textContent = `${report.username} / ${report.sessionId}`;
  els.detailTitle.textContent = "完整对话";
  els.detailMeta.textContent = "管理员视图";
  els.detailBody.innerHTML = `<p class="empty-record">读取中...</p>`;
  try {
    const response = await api(`/api/admin/conversations/${encodeURIComponent(report.sessionId)}`);
    const data = await response.json();
    els.detailBody.innerHTML = "";
    data.messages.forEach((message) => {
      const card = document.createElement("article");
      card.className = `conversation-card ${message.role.toLowerCase()}`;
      card.innerHTML = `<header><strong>${message.role}</strong><span>${formatDate(message.createdAt)}</span></header><p>${escapeHtml(message.content)}</p>`;
      els.detailBody.append(card);
    });
  } catch (error) {
    els.detailBody.innerHTML = `<p class="empty-record">读取失败</p>`;
  }
}

function closeDetail() {
  els.detailOverlay.hidden = true;
  updateRailNavigation();
}

async function loadReports() {
  const response = await api("/api/admin/reports");
  return response.json();
}

async function loadExcelRecords() {
  const response = await api("/api/admin/excel-records");
  return response.json();
}

async function loadAlertRecords() {
  const response = await api("/api/admin/alerts");
  return response.json();
}

async function loadRiskTickets() {
  const response = await api("/api/admin/risk-tickets");
  return response.json();
}

async function loadStudentCases() {
  const response = await api("/api/admin/students/cases");
  return response.json();
}

async function loadKnowledgeSources() {
  if (!els.knowledgeSources) return;
  try {
    const response = await api("/api/admin/knowledge");
    renderKnowledgeSources(await response.json());
  } catch (error) {
    els.knowledgeSources.innerHTML = "";
  }
}

async function loadDashboardTrends() {
  const response = await api("/api/admin/dashboard/trends");
  return response.json();
}

async function loadAuditLogs() {
  const response = await api("/api/admin/audit-logs");
  return response.json();
}

async function loadAdminData() {
  const [reports, excelRecords, alerts, tickets, dashboard, auditLogs, cases] = await Promise.all([
    loadReports(),
    loadExcelRecords(),
    loadAlertRecords(),
    loadRiskTickets(),
    loadDashboardTrends(),
    loadAuditLogs(),
    loadStudentCases()
  ]);
  state.latestReports = reports;
  state.latestExcelRecords = excelRecords;
  state.latestAlerts = alerts;
  state.latestTickets = tickets;
  state.latestDashboard = dashboard;
  state.latestAuditLogs = auditLogs;
  state.latestCases = cases;
  renderAssigneeFilter(tickets);
  applyTicketFilters();
  renderDashboardPanel(dashboard, auditLogs);
  renderCaseRows(cases);
  renderExcelRows(excelRecords);
  renderEmailRows(alerts);
  await loadKnowledgeSources();
}

function renderKnowledgeSources(sources) {
  els.knowledgeSources.innerHTML = "";
  if (!sources.length) {
    els.knowledgeSources.append(emptyRecord("No knowledge source"));
    return;
  }
  sources.forEach((source) => {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "record-card knowledge-source";
    card.innerHTML = `
      <div><strong>${escapeHtml(source.source)}</strong><span>${escapeHtml(source.category || "GENERAL")}</span></div>
      <small>${source.chunks} chunks · ${formatDate(source.latestCreatedAt)}</small>
    `;
    card.addEventListener("click", () => openKnowledgeSource(source.source));
    els.knowledgeSources.append(card);
  });
}

async function openKnowledgeSource(source) {
  els.detailOverlay.hidden = false;
  updateRailNavigation("detailOverlay");
  els.detailKicker.textContent = "Knowledge source";
  els.detailTitle.textContent = source;
  els.detailMeta.textContent = "Chunk preview";
  els.detailBody.innerHTML = `<p class="empty-record">Loading...</p>`;
  try {
    const response = await api(`/api/admin/knowledge/${encodeURIComponent(source)}/chunks`);
    const chunks = await response.json();
    els.detailBody.innerHTML = `
      <section class="ticket-editor">
        <label><span>Category</span><input id="knowledgeCategoryEdit" placeholder="GENERAL"></label>
        <label><span>Active</span><select id="knowledgeActiveEdit"><option value="true">active</option><option value="false">disabled</option></select></label>
        <label><span>Search test</span><input id="knowledgeSearchQuery" placeholder="query this knowledge base"></label>
        <div class="form-actions two">
          <button id="saveKnowledgeSource" type="button">Save source</button>
          <button id="testKnowledgeSearch" type="button" class="secondary">Search test</button>
        </div>
      </section>
      <div id="knowledgeSearchResults" class="record-list"></div>
    `;
    $("#saveKnowledgeSource").addEventListener("click", () => updateKnowledgeSource(source));
    $("#testKnowledgeSearch").addEventListener("click", testKnowledgeSearch);
    chunks.forEach((chunk) => {
      const card = document.createElement("article");
      card.className = "conversation-card";
      card.innerHTML = `<header><strong>${escapeHtml(chunk.category || "GENERAL")}</strong><span>#${chunk.sourceIndex} · ${formatDate(chunk.createdAt)}</span></header><p>${escapeHtml(chunk.content || "")}</p>`;
      els.detailBody.append(card);
    });
  } catch (error) {
    els.detailBody.innerHTML = `<p class="empty-record">Failed to load chunks</p>`;
  }
}

async function updateKnowledgeSource(source) {
  const category = $("#knowledgeCategoryEdit").value.trim();
  await api(`/api/admin/knowledge/${encodeURIComponent(source)}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ category: category || null, active: $("#knowledgeActiveEdit").value === "true" })
  });
  await loadKnowledgeSources();
  openKnowledgeSource(source);
}

async function testKnowledgeSearch() {
  const query = $("#knowledgeSearchQuery").value.trim();
  if (!query) return;
  const response = await api("/api/admin/knowledge/search-test", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ query })
  });
  const results = await response.json();
  const target = $("#knowledgeSearchResults");
  target.innerHTML = "";
  results.forEach((result) => {
    const card = document.createElement("article");
    card.className = "record-card";
    card.innerHTML = `<div><strong>${escapeHtml(result.source)}</strong><span>${Number(result.score || 0).toFixed(2)}</span></div><small>${escapeHtml(result.category || "")}</small><p>${escapeHtml(result.content || "")}</p>`;
    target.append(card);
  });
}

function renderKnowledgeSources(sources) {
  els.knowledgeSources.innerHTML = "";
  if (!sources.length) {
    els.knowledgeSources.append(emptyRecord("No knowledge source"));
    return;
  }
  sources.forEach((source) => {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "record-card knowledge-source";
    card.innerHTML = `
      <div><strong>${escapeHtml(source.source)}</strong><span>${escapeHtml(source.category || "GENERAL")}</span></div>
      <small>v${source.version || 1} / ${escapeHtml(source.versionStatus || (source.active ? "ENABLED" : "DISABLED"))} / ${escapeHtml(source.riskLevel || "LOW")} / ${source.chunks} chunks / feedback ${source.feedbackCount || 0}</small>
      <small>${escapeHtml(source.tags || "general")} / ${escapeHtml(source.audience || "ALL_STUDENTS")}</small>
    `;
    card.addEventListener("click", () => openKnowledgeSource(source.source));
    els.knowledgeSources.append(card);
  });
}

async function openKnowledgeSource(source) {
  els.detailOverlay.hidden = false;
  updateRailNavigation("detailOverlay");
  els.detailKicker.textContent = "Knowledge source";
  els.detailTitle.textContent = source;
  els.detailMeta.textContent = "Versioned knowledge operations";
  els.detailBody.innerHTML = `<p class="empty-record">Loading...</p>`;
  try {
    const response = await api(`/api/admin/knowledge/${encodeURIComponent(source)}/chunks`);
    const chunks = await response.json();
    els.detailBody.innerHTML = `
      <section class="ticket-editor">
        <label><span>Category</span><input id="knowledgeCategoryEdit" placeholder="GENERAL"></label>
        <label><span>Tags</span><input id="knowledgeTagsEdit" placeholder="sleep,stress,campus"></label>
        <label><span>Audience</span><input id="knowledgeAudienceEdit" placeholder="ALL_STUDENTS"></label>
        <label><span>Risk</span><select id="knowledgeRiskEdit"><option value="LOW">LOW</option><option value="MEDIUM">MEDIUM</option><option value="HIGH">HIGH</option></select></label>
        <label><span>Active</span><select id="knowledgeActiveEdit"><option value="true">active</option><option value="false">disabled</option></select></label>
        <label><span>Version</span><select id="knowledgeVersionStatusEdit"><option value="ENABLED">enabled</option><option value="DISABLED">disabled</option><option value="ROLLED_BACK">rolled back</option></select></label>
        <label><span>Version note</span><input id="knowledgeVersionNoteEdit" placeholder="why this version changed"></label>
        <label><span>Search test</span><input id="knowledgeSearchQuery" placeholder="query this knowledge base"></label>
        <label><span>Min score</span><input id="knowledgeMinScore" type="number" min="0" max="1" step="0.01" value="0.18"></label>
        <div class="form-actions two">
          <button id="saveKnowledgeSource" type="button">Save source</button>
          <button id="testKnowledgeSearch" type="button" class="secondary">Search test</button>
        </div>
      </section>
      <div id="knowledgeSearchResults" class="record-list"></div>
    `;
    $("#saveKnowledgeSource").addEventListener("click", () => updateKnowledgeSource(source));
    $("#testKnowledgeSearch").addEventListener("click", testKnowledgeSearch);
    chunks.forEach((chunk) => {
      const card = document.createElement("article");
      card.className = "conversation-card";
      card.innerHTML = `<header><strong>${escapeHtml(chunk.category || "GENERAL")}</strong><span>#${chunk.sourceIndex} / v${chunk.version || 1} / ${escapeHtml(chunk.versionStatus || "")} / ${formatDate(chunk.createdAt)}</span></header><small>${escapeHtml(chunk.tags || "no tags")} / ${escapeHtml(chunk.audience || "ALL_STUDENTS")} / ${escapeHtml(chunk.riskLevel || "LOW")}</small><p>${escapeHtml(chunk.content || "")}</p>`;
      els.detailBody.append(card);
    });
  } catch (error) {
    els.detailBody.innerHTML = `<p class="empty-record">Failed to load chunks</p>`;
  }
}

async function updateKnowledgeSource(source) {
  await api(`/api/admin/knowledge/${encodeURIComponent(source)}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      category: $("#knowledgeCategoryEdit").value.trim() || null,
      tags: $("#knowledgeTagsEdit").value.trim(),
      audience: $("#knowledgeAudienceEdit").value.trim(),
      riskLevel: $("#knowledgeRiskEdit").value,
      active: $("#knowledgeActiveEdit").value === "true",
      versionStatus: $("#knowledgeVersionStatusEdit").value,
      versionNote: $("#knowledgeVersionNoteEdit").value.trim()
    })
  });
  await loadKnowledgeSources();
  openKnowledgeSource(source);
}

async function testKnowledgeSearch() {
  const query = $("#knowledgeSearchQuery").value.trim();
  if (!query) return;
  const minScore = Number($("#knowledgeMinScore")?.value || 0.18);
  const response = await api("/api/admin/knowledge/search-test", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ query, minScore })
  });
  const results = await response.json();
  const target = $("#knowledgeSearchResults");
  target.innerHTML = "";
  results.forEach((result) => {
    const card = document.createElement("article");
    card.className = "record-card";
    card.innerHTML = `<div><strong>${escapeHtml(result.source)}</strong><span>${Number(result.score || 0).toFixed(2)} / ${result.shown ? "shown" : "hidden"}</span></div><small>${escapeHtml(result.category || "")} / ${escapeHtml(result.tags || "")} / ${escapeHtml(result.audience || "")} / ${escapeHtml(result.riskLevel || "")} / ${escapeHtml(result.basis || "")}</small><p>${escapeHtml(result.content || "")}</p><button type="button" class="secondary">引用不合适</button>`;
    card.querySelector("button")?.addEventListener("click", () => sendCitationFeedback(result));
    target.append(card);
  });
}

async function sendCitationFeedback(result) {
  await api("/api/admin/knowledge/citation-feedback", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      chunkId: result.chunkId || null,
      source: result.source || "unknown",
      category: result.category || "GENERAL",
      reason: "引用不合适",
      note: `score=${Number(result.score || 0).toFixed(3)}, basis=${result.basis || ""}`
    })
  });
  const target = $("#knowledgeSearchResults");
  if (target) {
    target.prepend(emptyRecord("已记录引用反馈"));
  }
  await loadKnowledgeSources();
}

async function uploadKnowledge(event) {
  event.preventDefault();
  const file = els.knowledgeFile.files?.[0];
  if (!file) {
    els.knowledgeUploadState.textContent = "请选择文件";
    return;
  }
  const body = new FormData();
  body.append("file", file);
  els.knowledgeUploadState.textContent = "入库中";
  try {
    const response = await api("/api/admin/knowledge/file", { method: "POST", body });
    const data = await response.json();
    els.knowledgeUploadState.textContent = `${data.source} / ${data.category} / ${data.chunks} 个片段`;
    els.knowledgeFile.value = "";
    await loadKnowledgeSources();
  } catch (error) {
    els.knowledgeUploadState.textContent = "入库失败";
  }
}

function syncTicketFiltersFromInputs() {
  state.ticketFilters.status = els.ticketStatusFilter.value;
  state.ticketFilters.risk = els.ticketRiskFilter.value;
  state.ticketFilters.assignee = els.ticketAssigneeFilter.value;
  state.ticketFilters.search = els.ticketSearchFilter.value.trim();
  applyTicketFilters();
}

function resetTicketFilters() {
  state.ticketFilters = { status: "", risk: "", assignee: "", search: "" };
  els.ticketStatusFilter.value = "";
  els.ticketRiskFilter.value = "";
  els.ticketAssigneeFilter.value = "";
  els.ticketSearchFilter.value = "";
  applyTicketFilters();
}

function debounce(fn, delay = 160) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}

function quickTicketFilter(nextFilters) {
  state.ticketFilters = {
    status: nextFilters.status ?? "",
    risk: nextFilters.risk ?? "",
    assignee: nextFilters.assignee ?? "",
    search: nextFilters.search ?? ""
  };
  els.ticketStatusFilter.value = state.ticketFilters.status;
  els.ticketRiskFilter.value = state.ticketFilters.risk;
  els.ticketAssigneeFilter.value = state.ticketFilters.assignee;
  els.ticketSearchFilter.value = state.ticketFilters.search;
  applyTicketFilters();
}

function showLoggedOut() {
  state.isAdmin = false;
  state.conversationHistory = [];
  els.loginForm.hidden = false;
  els.accountPanel.hidden = true;
  els.studentView.hidden = false;
  els.adminView.hidden = true;
  renderEmptyConversation();
  renderPipeline();
  renderConversationHistory();
  updateRailNavigation("studentView");
}

function isAdmin(profile) {
  return profile.roles?.some((role) => role.authority === "ROLE_ADMIN");
}

async function loadProfile() {
  const response = await api("/api/profile");
  const profile = await response.json();
  state.isAdmin = isAdmin(profile);
  const accountName = state.isAdmin ? (profile.displayName || profile.username) : profile.username;
  els.loginForm.hidden = true;
  els.accountPanel.hidden = false;
  els.activeAccount.textContent = accountName;
  els.activeRole.textContent = state.isAdmin ? "管理员账号" : "学生账号";

  if (state.isAdmin) {
    els.studentView.hidden = true;
    els.adminView.hidden = false;
    await loadAdminData();
    updateRailNavigation("adminView");
  } else {
    els.studentView.hidden = false;
    els.adminView.hidden = true;
    els.profileText.textContent = "学生支持对话";
    await loadVoiceStatus();
    await loadStudentSupport();
    await loadConversationHistory();
    updateRailNavigation("studentView");
  }
  els.loginState.textContent = "登录成功";
}

async function loadAgentStatus() {
  const response = await api("/api/agent/status");
  setModel(await response.json());
}

async function checkHealth() {
  try {
    const response = await fetch("/actuator/health");
    const body = await response.json();
    setService(body.status === "UP" ? "服务正常" : `服务 ${body.status}`, body.status === "UP" ? "ok" : "warn");
  } catch (error) {
    setService("服务不可用", "danger");
  }
}

async function login(event) {
  event?.preventDefault();
  state.auth.username = els.username.value.trim();
  state.auth.password = els.password.value;
  try {
    await loadProfile();
    await loadAgentStatus();
  } catch (error) {
    showLoggedOut();
    els.loginState.textContent = "账号或密码错误";
  }
}

els.loginForm.addEventListener("submit", login);
document.querySelector(".rail-nav")?.addEventListener("click", navigateRail);
els.switchAccount.addEventListener("click", () => {
  showLoggedOut();
  els.username.focus();
});
els.chatForm.addEventListener("submit", sendChat);
els.startVoiceButton?.addEventListener("click", startVoiceSession);
els.interruptVoiceButton?.addEventListener("click", interruptVoiceSession);
els.endVoiceButton?.addEventListener("click", endVoiceSession);
els.moodScale.addEventListener("click", (event) => {
  const button = event.target.closest("[data-score]");
  if (button) setMoodScore(button.dataset.score);
});
els.moodForm.addEventListener("submit", saveMoodEntry);
els.taskForm.addEventListener("submit", saveSupportTask);
els.seedTasks.addEventListener("click", seedSuggestedTasks);
els.smartTasks?.addEventListener("click", generateSmartTasks);
els.audioInput.addEventListener("change", updateAttachments);
els.imageInput.addEventListener("change", updateAttachments);
els.videoInput.addEventListener("change", updateAttachments);
els.clearAttachments.addEventListener("click", clearAttachments);
els.newSessionButton.addEventListener("click", startNewSession);
els.adminRefresh.addEventListener("click", loadAdminData);
els.ticketStatusFilter.addEventListener("change", syncTicketFiltersFromInputs);
els.ticketRiskFilter.addEventListener("change", syncTicketFiltersFromInputs);
els.ticketAssigneeFilter.addEventListener("change", syncTicketFiltersFromInputs);
els.ticketSearchFilter.addEventListener("input", debounce(syncTicketFiltersFromInputs));
els.ticketFilterReset.addEventListener("click", resetTicketFilters);
els.knowledgeUploadForm.addEventListener("submit", uploadKnowledge);
els.closeDetail.addEventListener("click", closeDetail);
els.detailOverlay.addEventListener("click", (event) => {
  if (event.target === els.detailOverlay) closeDetail();
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !els.detailOverlay.hidden) closeDetail();
});
document.addEventListener("click", (event) => {
  const prompt = event.target.closest("[data-prompt]");
  if (prompt && !state.isAdmin) {
    els.messageInput.value = prompt.dataset.prompt;
    els.messageInput.focus();
  }
});

dockChatComposer();
refineSupportPanelCopy();
checkHealth();
renderPipeline();
renderEmptyConversation();
login();
