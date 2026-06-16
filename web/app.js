const state = {
  apiKey: "",
  stakeholders: [],
  history: [],
  rounds: [],
};

const els = {
  apiKey: document.querySelector("#apiKey"),
  toggleKey: document.querySelector("#toggleKey"),
  keyStatus: document.querySelector("#keyStatus"),
  stakeholderSelect: document.querySelector("#stakeholderSelect"),
  stakeholderGoal: document.querySelector("#stakeholderGoal"),
  a1bRun: document.querySelector("#a1bRun"),
  saveRecord: document.querySelector("#saveRecord"),
  clearChat: document.querySelector("#clearChat"),
  records: document.querySelector("#records"),
  modelInfo: document.querySelector("#modelInfo"),
  chatLog: document.querySelector("#chatLog"),
  chatForm: document.querySelector("#chatForm"),
  messageInput: document.querySelector("#messageInput"),
  sendMessage: document.querySelector("#sendMessage"),
};

function selectedStakeholder() {
  return state.stakeholders.find((item) => item.id === els.stakeholderSelect.value);
}

function hasKey() {
  return state.apiKey.trim().length > 0;
}

function setBusy(isBusy) {
  els.sendMessage.disabled = isBusy || !hasKey();
  els.messageInput.disabled = isBusy || !hasKey();
  els.a1bRun.disabled = isBusy || !hasKey();
  els.saveRecord.disabled = isBusy || state.history.length === 0;
}

function updateKeyState() {
  state.apiKey = els.apiKey.value.trim();
  if (hasKey()) {
    els.keyStatus.textContent = "key 已输入，仅在本次请求中使用";
    els.keyStatus.className = "status ready";
  } else {
    els.keyStatus.textContent = "输入 key 后启用智能体";
    els.keyStatus.className = "status";
  }
  setBusy(false);
}

function appendMessage(speaker, content, kind = "", source = "manual") {
  state.history.push({ speaker, content, source });
  renderChat();
  setBusy(false);
}

function appendSystemMessage(content) {
  appendMessage("系统提示", content, "agent", "system");
}

function renderChat() {
  els.chatLog.innerHTML = "";
  if (state.history.length === 0) {
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = "选择涉众并输入 API key 后，可以开始访谈。保存后会写入 raw/notes/。";
    els.chatLog.appendChild(empty);
    return;
  }

  for (const item of state.history) {
    const node = document.createElement("article");
    const kind = item.speaker.startsWith("真人") ? "human" : item.speaker.startsWith("A1b") ? "a1b" : "agent";
    node.className = `message ${kind}`;
    node.innerHTML = `
      <div class="speaker"></div>
      <div class="content"></div>
    `;
    node.querySelector(".speaker").textContent = item.speaker;
    node.querySelector(".content").textContent = item.content;
    els.chatLog.appendChild(node);
  }
  els.chatLog.scrollTop = els.chatLog.scrollHeight;
}

async function api(path, body) {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.detail || "请求失败");
  }
  return data;
}

async function loadConfig() {
  const response = await fetch("/api/config");
  const data = await response.json();
  els.modelInfo.textContent = `模型：${data.model} · 百炼兼容接口：${data.baseUrl}`;
}

async function loadStakeholders() {
  const response = await fetch("/api/stakeholders");
  state.stakeholders = await response.json();
  els.stakeholderSelect.innerHTML = "";
  for (const item of state.stakeholders) {
    const option = document.createElement("option");
    option.value = item.id;
    option.textContent = item.name;
    els.stakeholderSelect.appendChild(option);
  }
  updateStakeholderInfo();
}

async function loadRecords() {
  const response = await fetch("/api/records");
  const records = await response.json();
  els.records.innerHTML = "";
  if (records.length === 0) {
    const item = document.createElement("li");
    item.textContent = "暂无记录";
    els.records.appendChild(item);
    return;
  }
  for (const record of records) {
    const item = document.createElement("li");
    item.textContent = `${record.name} (${record.modified})`;
    els.records.appendChild(item);
  }
}

function updateStakeholderInfo() {
  const stakeholder = selectedStakeholder();
  els.stakeholderGoal.textContent = stakeholder ? stakeholder.goal : "";
}

els.apiKey.addEventListener("input", updateKeyState);

els.toggleKey.addEventListener("click", () => {
  const showing = els.apiKey.type === "text";
  els.apiKey.type = showing ? "password" : "text";
  els.toggleKey.textContent = showing ? "显示" : "隐藏";
});

els.stakeholderSelect.addEventListener("change", updateStakeholderInfo);

els.chatForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const message = els.messageInput.value.trim();
  if (!message || !hasKey()) return;

  const stakeholder = selectedStakeholder();
    appendMessage("真人访谈者", message, "human", "manual");
  els.messageInput.value = "";
  setBusy(true);

  try {
    const data = await api("/api/chat/a1a", {
      apiKey: state.apiKey,
      stakeholderId: stakeholder.id,
      message,
      history: state.history,
    });
    appendMessage(`A1a-${stakeholder.name}`, data.answer, "agent", "manual");
  } catch (error) {
    appendMessage("系统提示", error.message, "agent");
  } finally {
    setBusy(false);
  }
});

els.a1bRun.addEventListener("click", async () => {
  if (!hasKey()) return;
  const stakeholder = selectedStakeholder();
  setBusy(true);
  try {
    const data = await api("/api/chat/a1b/run", {
      apiKey: state.apiKey,
      stakeholderId: stakeholder.id,
      history: state.history,
    });
    const roundHistory = [
      { speaker: "A1b需求获取智能体", content: data.question },
      { speaker: `A1a-${stakeholder.name}`, content: data.answer },
    ];
    state.rounds.push({
      stakeholderId: stakeholder.id,
      history: roundHistory,
      savedPath: "",
    });
    appendMessage(roundHistory[0].speaker, roundHistory[0].content, "a1b", "a1b");
    appendMessage(roundHistory[1].speaker, roundHistory[1].content, "agent", "a1b");

    const saved = await api("/api/records/save", {
      stakeholderId: stakeholder.id,
      history: roundHistory,
      summary: "",
    });
    state.rounds[state.rounds.length - 1].savedPath = saved.relativePath;
    appendSystemMessage(`本轮已保存：${saved.relativePath}`);
    await loadRecords();
  } catch (error) {
    appendSystemMessage(error.message);
  } finally {
    setBusy(false);
  }
});

els.saveRecord.addEventListener("click", async () => {
  const stakeholder = selectedStakeholder();
  const manualHistory = state.history
    .filter((item) => item.source === "manual")
    .map(({ speaker, content }) => ({ speaker, content }));
  if (manualHistory.length === 0) {
    appendSystemMessage("没有可保存的真人手动访谈内容；A1b 自动轮次已在生成时单独保存。");
    return;
  }
  setBusy(true);
  try {
    const data = await api("/api/records/save", {
      stakeholderId: stakeholder.id,
      history: manualHistory,
      summary: "",
    });
    appendSystemMessage(`已保存手动访谈：${data.relativePath}`);
    await loadRecords();
  } catch (error) {
    appendSystemMessage(error.message);
  } finally {
    setBusy(false);
  }
});

els.clearChat.addEventListener("click", () => {
  state.history = [];
  state.rounds = [];
  renderChat();
  setBusy(false);
});

Promise.all([loadConfig(), loadStakeholders(), loadRecords()]).then(() => {
  renderChat();
  updateKeyState();
});
