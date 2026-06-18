const state = {
  apiKey: "",
  stakeholders: [],
  history: [],
  rounds: [],
  manualRounds: [],
  a2Report: "",
  a2ReportPath: "",
  a2RollbackPlan: "",
  a3Status: null,
  a4Status: null,
  a5Status: null,
  a6Status: null,
  designStatus: null,
  savedRecords: [],
  busy: false,
  batchRunning: false,
};

const els = {
  apiKey: document.querySelector("#apiKey"),
  toggleKey: document.querySelector("#toggleKey"),
  keyStatus: document.querySelector("#keyStatus"),
  stakeholderSelect: document.querySelector("#stakeholderSelect"),
  stakeholderGoal: document.querySelector("#stakeholderGoal"),
  a1bRun: document.querySelector("#a1bRun"),
  a1bRunAll: document.querySelector("#a1bRunAll"),
  saveRecord: document.querySelector("#saveRecord"),
  clearChat: document.querySelector("#clearChat"),
  batchStatus: document.querySelector("#batchStatus"),
  a2NotesStatus: document.querySelector("#a2NotesStatus"),
  a2Analyze: document.querySelector("#a2Analyze"),
  a2Plan: document.querySelector("#a2Plan"),
  a2Rollback: document.querySelector("#a2Rollback"),
  a2Status: document.querySelector("#a2Status"),
  a3Status: document.querySelector("#a3Status"),
  a3Model: document.querySelector("#a3Model"),
  a4Status: document.querySelector("#a4Status"),
  a4Generate: document.querySelector("#a4Generate"),
  a5Status: document.querySelector("#a5Status"),
  a5Validate: document.querySelector("#a5Validate"),
  a4ReviseFromA5: document.querySelector("#a4ReviseFromA5"),
  a6Status: document.querySelector("#a6Status"),
  ccbConclusion: document.querySelector("#ccbConclusion"),
  acceptA5Risks: document.querySelector("#acceptA5Risks"),
  a6CreateBaseline: document.querySelector("#a6CreateBaseline"),
  designStatus: document.querySelector("#designStatus"),
  designRun: document.querySelector("#designRun"),
  a2Output: document.querySelector("#a2Output"),
  a2OutputText: document.querySelector("#a2OutputText"),
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
  state.busy = isBusy;
  const locked = isBusy || state.batchRunning;
  els.sendMessage.disabled = locked || !hasKey();
  els.messageInput.disabled = locked || !hasKey();
  els.a1bRun.disabled = locked || !hasKey();
  els.a1bRunAll.disabled = locked || !hasKey();
  els.a2Analyze.disabled = locked || !hasKey();
  els.a2Plan.disabled = locked || !hasKey() || !state.a2Report;
  els.a2Rollback.disabled = locked || !hasKey() || !state.a2RollbackPlan;
  els.a3Model.disabled = locked || !hasKey();
  els.a4Generate.disabled = locked || !hasKey();
  els.a5Validate.disabled = locked || !hasKey();
  els.a4ReviseFromA5.disabled = locked || !hasKey() || !state.a5Status?.canReviseA4;
  const needsRiskAcceptance = !!state.a6Status?.requiresA5RiskAcceptance;
  els.acceptA5Risks.disabled = locked || !needsRiskAcceptance;
  els.a6CreateBaseline.disabled = locked || !hasKey() || !state.a6Status?.canCreateBaseline || (needsRiskAcceptance && !els.acceptA5Risks.checked);
  els.designRun.disabled = locked || !hasKey() || !state.designStatus?.canRun;
  els.stakeholderSelect.disabled = locked;
  els.saveRecord.disabled = locked || state.manualRounds.every((round) => round.savedPath);
  els.clearChat.disabled = locked;
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

async function saveRound(stakeholderId, roundHistory, label) {
  const saved = await api("/api/records/save", {
    stakeholderId,
    history: roundHistory,
    summary: "",
  });
  addSavedRecord(saved.relativePath);
  return `${label}已保存：${saved.relativePath}`;
}

function addSavedRecord(relativePath) {
  if (!relativePath || state.savedRecords.includes(relativePath)) return;
  state.savedRecords.unshift(relativePath);
  renderSavedRecords();
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
  els.modelInfo.textContent = `A1/A2：${data.model} · A3/A4：${data.a3Model} · A5/A6：${data.a5Model} · 设计阶段：${data.designModel} · 百炼兼容接口：${data.baseUrl}`;
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

function renderSavedRecords() {
  els.records.innerHTML = "";
  if (state.savedRecords.length === 0) {
    const item = document.createElement("li");
    item.textContent = "本页面暂无保存记录";
    els.records.appendChild(item);
    return;
  }
  for (const relativePath of state.savedRecords) {
    const item = document.createElement("li");
    item.textContent = relativePath;
    item.title = relativePath;
    els.records.appendChild(item);
  }
}

async function loadA2NotesStatus() {
  const response = await fetch("/api/a2/notes");
  const data = await response.json();
  const stakeholderText = Object.entries(data.stakeholders || {})
    .map(([name, count]) => `${name}${count}篇`)
    .join("，");
  els.a2NotesStatus.textContent = `已读取 ${data.count} 篇需求记录${stakeholderText ? `：${stakeholderText}` : ""}`;
}

async function loadA3Status() {
  const response = await fetch("/api/a3/status");
  const data = await response.json();
  state.a3Status = data;
  const reportText = data.latestA2Report ? `最新 A2：${data.latestA2Report}` : "未检测到 A2 报告";
  els.a3Status.textContent = `可建模记录 ${data.notesCount} 篇 · ${reportText} · 模型：${data.a3Model}`;
}

async function loadA4Status() {
  const response = await fetch("/api/a4/status");
  const data = await response.json();
  state.a4Status = data;
  const reportText = data.latestA2Report ? `A2：${data.latestA2Report}` : "缺少 A2";
  const srsText = data.latestSrs ? `最新 SRS：${data.latestSrs}` : "尚未生成 SRS";
  els.a4Status.textContent = `notes ${data.notesCount} 篇 · UML ${data.umlCount} 个 · ${reportText} · ${srsText} · 模型：${data.a4Model}`;
}

async function loadA5Status() {
  const response = await fetch("/api/a5/status");
  const data = await response.json();
  state.a5Status = data;
  const srsText = data.latestSrs ? `SRS：${data.latestSrs}` : "缺少 SRS";
  const reportText = data.latestA5Report ? `最新报告：${data.latestA5Report}` : "尚未验证";
  const decisionText = data.message ? ` · ${data.message}` : "";
  els.a5Status.textContent = `notes ${data.notesCount} 篇 · UML ${data.umlCount} 个 · ${srsText} · ${reportText} · 模型：${data.a5Model}${decisionText}`;
  els.a5Status.className = data.decision === "return_a4" || data.decision === "return_a1" ? "status error" : "status ready";
  setBusy(state.busy);
}

async function loadA6Status() {
  const response = await fetch("/api/a6/status");
  const data = await response.json();
  state.a6Status = data;
  const srsText = data.latestSrs ? `SRS：${data.latestSrs}` : "缺少 SRS";
  const a5Text = data.latestA5Report ? `A5：${data.latestA5Report}` : "缺少 A5";
  const baselineCount = (data.baselines || []).length;
  const riskText = data.requiresA5RiskAcceptance ? " · A5 未通过，但可由 CCB 带风险批准基线" : "";
  els.a6Status.textContent = `${srsText} · ${a5Text} · ${data.a5Message}${riskText} · 下一基线：${data.nextBaselineId} · 已有 ${baselineCount} 个 · 模型：${data.a6Model}`;
  els.a6Status.className = data.canCreateBaseline ? "status ready" : "status error";
  if (!data.requiresA5RiskAcceptance) {
    els.acceptA5Risks.checked = false;
  }
  setBusy(state.busy);
}

async function loadDesignStatus() {
  const response = await fetch("/api/design/status");
  const data = await response.json();
  state.designStatus = data;
  const baselineText = data.latestBaseline ? `基线：${data.latestBaseline}` : "缺少需求基线";
  const srsText = data.srsPath ? `SRS：${data.srsPath}` : "缺少 SRS 正式版";
  const outputCount = (data.designOutputs || []).length;
  els.designStatus.textContent = `${baselineText} · ${srsText} · UML ${data.umlCount} 个（用例图 ${data.useCaseCount}，活动图 ${data.activityCount}） · 已有设计产物 ${outputCount} 份 · 模型：${data.designModel} · ${data.message}`;
  els.designStatus.className = data.canRun ? "status ready" : "status error";
  setBusy(state.busy);
}

function showA2Output(title, content) {
  els.a2Output.hidden = false;
  els.a2OutputText.textContent = `# ${title}\n\n${content}`;
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

els.acceptA5Risks.addEventListener("change", () => setBusy(state.busy));

els.chatForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const message = els.messageInput.value.trim();
  if (!message || !hasKey()) return;

  const stakeholder = selectedStakeholder();
  const questionItem = { speaker: "真人访谈者", content: message };
  appendMessage(questionItem.speaker, questionItem.content, "human", "manual");
  els.messageInput.value = "";
  setBusy(true);

  try {
    const data = await api("/api/chat/a1a", {
      apiKey: state.apiKey,
      stakeholderId: stakeholder.id,
      message,
      history: state.history,
    });
    const answerItem = { speaker: `A1a-${stakeholder.name}`, content: data.answer };
    appendMessage(answerItem.speaker, answerItem.content, "agent", "manual");
    const savedMessage = await saveRound(stakeholder.id, [questionItem, answerItem], "真人本轮");
    state.manualRounds.push({
      stakeholderId: stakeholder.id,
      history: [questionItem, answerItem],
      savedPath: savedMessage.split("：").pop(),
    });
    appendSystemMessage(savedMessage);
  } catch (error) {
    appendSystemMessage(error.message);
  } finally {
    setBusy(false);
  }
});

async function runA1bRound(stakeholder, contextHistory, displayInChat = true, label = "本轮") {
  const data = await api("/api/chat/a1b/run", {
    apiKey: state.apiKey,
    stakeholderId: stakeholder.id,
    history: contextHistory,
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
  if (displayInChat) {
    appendMessage(roundHistory[0].speaker, roundHistory[0].content, "a1b", "a1b");
    appendMessage(roundHistory[1].speaker, roundHistory[1].content, "agent", "a1b");
  }
  const savedMessage = await saveRound(stakeholder.id, roundHistory, label);
  state.rounds[state.rounds.length - 1].savedPath = savedMessage.split("：").pop();
  return { roundHistory, savedMessage };
}

els.a1bRun.addEventListener("click", async () => {
  if (!hasKey()) return;
  const stakeholder = selectedStakeholder();
  setBusy(true);
  try {
    const result = await runA1bRound(stakeholder, state.history, true, "A1b本轮");
    appendSystemMessage(result.savedMessage);
  } catch (error) {
    appendSystemMessage(error.message);
  } finally {
    setBusy(false);
  }
});

els.a1bRunAll.addEventListener("click", async () => {
  if (!hasKey() || state.batchRunning) return;
  state.batchRunning = true;
  setBusy(true);
  els.batchStatus.textContent = "批量访谈启动中...";
  els.batchStatus.className = "status ready";
  try {
    let savedCount = 0;
    const total = state.stakeholders.length * 3;
    for (const stakeholder of state.stakeholders) {
      const stakeholderContext = [];
      appendSystemMessage(`开始批量访谈：${stakeholder.name}`);
      for (let round = 1; round <= 3; round += 1) {
        els.batchStatus.textContent = `${stakeholder.name} ${round}/3 进行中... (${savedCount}/${total})`;
        const result = await runA1bRound(
          stakeholder,
          stakeholderContext,
          false,
          `${stakeholder.name} ${round}/3 `
        );
        stakeholderContext.push(...result.roundHistory);
        savedCount += 1;
        els.batchStatus.textContent = `${stakeholder.name} ${round}/3 已保存 (${savedCount}/${total})`;
        appendSystemMessage(result.savedMessage);
      }
    }
    els.batchStatus.textContent = `批量访谈完成：已保存 ${savedCount} / ${total} 轮`;
    els.batchStatus.className = "status ready";
  } catch (error) {
    els.batchStatus.textContent = `批量访谈中断：${error.message}`;
    els.batchStatus.className = "status error";
    appendSystemMessage(error.message);
  } finally {
    state.batchRunning = false;
    setBusy(false);
  }
});

els.a2Analyze.addEventListener("click", async () => {
  if (!hasKey()) return;
  setBusy(true);
  els.a2Status.textContent = "A2 正在分析全部需求记录...";
  els.a2Status.className = "status ready";
  try {
    const data = await api("/api/a2/analyze", {
      apiKey: state.apiKey,
    });
    state.a2Report = data.content;
    state.a2ReportPath = data.relativePath;
    state.a2RollbackPlan = "";
    els.a2Status.textContent = `A2 报告已保存：${data.relativePath}`;
    showA2Output("A2 需求质量分析报告", data.content);
    appendSystemMessage(`A2 报告已保存：${data.relativePath}`);
  } catch (error) {
    els.a2Status.textContent = `A2 分析失败：${error.message}`;
    els.a2Status.className = "status error";
    appendSystemMessage(error.message);
  } finally {
    setBusy(false);
  }
});

els.a2Plan.addEventListener("click", async () => {
  if (!hasKey() || !state.a2Report) return;
  setBusy(true);
  els.a2Status.textContent = "正在生成回退追问话术...";
  els.a2Status.className = "status ready";
  try {
    const data = await api("/api/a2/rollback-plan", {
      apiKey: state.apiKey,
      report: state.a2Report,
    });
    state.a2RollbackPlan = data.content;
    els.a2Status.textContent = "回退追问话术已生成，可执行回退访谈";
    showA2Output("A2 回退追问话术", data.content);
    appendSystemMessage("A2 回退追问话术已生成。");
  } catch (error) {
    els.a2Status.textContent = `生成回退追问失败：${error.message}`;
    els.a2Status.className = "status error";
    appendSystemMessage(error.message);
  } finally {
    setBusy(false);
  }
});

els.a2Rollback.addEventListener("click", async () => {
  if (!hasKey() || !state.a2RollbackPlan) return;
  setBusy(true);
  els.a2Status.textContent = "正在执行 A2 回退访谈...";
  els.a2Status.className = "status ready";
  try {
    const data = await api("/api/a2/rollback-run", {
      apiKey: state.apiKey,
      plan: state.a2RollbackPlan,
      stakeholderIds: null,
    });
    els.a2Status.textContent = `A2 回退访谈完成：已保存 ${data.count} 条补充记录`;
    appendSystemMessage(`A2 回退访谈完成：已保存 ${data.count} 条补充记录。`);
    for (const item of data.results || []) {
      addSavedRecord(item.recordPath);
    }
    await loadA2NotesStatus();
  } catch (error) {
    els.a2Status.textContent = `A2 回退访谈失败：${error.message}`;
    els.a2Status.className = "status error";
    appendSystemMessage(error.message);
  } finally {
    setBusy(false);
  }
});

els.a3Model.addEventListener("click", async () => {
  if (!hasKey()) return;
  setBusy(true);
  els.a3Status.textContent = "A3 正在生成 UML 模型...";
  els.a3Status.className = "status ready";
  try {
    const data = await api("/api/a3/model", {
      apiKey: state.apiKey,
    });
    const files = data.files || [];
    const fileList = files.map((item) => `- ${item}`).join("\n");
    const activityList = (data.activityDiagrams || []).map((item) => `- ${item}`).join("\n");
    els.a3Status.textContent = `A3 建模完成：已生成 ${files.length} 个文件`;
    showA2Output(
      "A3 UML 建模结果",
      `${data.summary}\n\n## 生成文件\n${fileList}\n\n## 活动图\n${activityList || "- 未返回活动图名称"}\n\n## 建模决策说明\n${data.decisionMarkdown}`
    );
    appendSystemMessage(`A3 建模完成：已生成 ${files.length} 个文件。`);
    await loadA3Status();
  } catch (error) {
    els.a3Status.textContent = `A3 建模失败：${error.message}`;
    els.a3Status.className = "status error";
    appendSystemMessage(error.message);
  } finally {
    setBusy(false);
  }
});

els.a4Generate.addEventListener("click", async () => {
  if (!hasKey()) return;
  setBusy(true);
  els.a4Status.textContent = "A4 正在生成 SRS 初稿...";
  els.a4Status.className = "status ready";
  try {
    const data = await api("/api/a4/generate", {
      apiKey: state.apiKey,
    });
    els.a4Status.textContent = `A4 SRS 已保存：${data.relativePath}`;
    showA2Output("A4 SRS 初稿", `${data.summary}\n\n## 保存路径\n${data.relativePath}\n\n${data.content}`);
    appendSystemMessage(`A4 SRS 已保存：${data.relativePath}`);
    await loadA4Status();
    await loadA6Status();
  } catch (error) {
    els.a4Status.textContent = `A4 生成失败：${error.message}`;
    els.a4Status.className = "status error";
    appendSystemMessage(error.message);
  } finally {
    setBusy(false);
  }
});

els.a5Validate.addEventListener("click", async () => {
  if (!hasKey()) return;
  setBusy(true);
  els.a5Status.textContent = "A5 正在验证 SRS 初稿...";
  els.a5Status.className = "status ready";
  try {
    const data = await api("/api/a5/validate", {
      apiKey: state.apiKey,
    });
    els.a5Status.textContent = `A5 报告已保存：${data.relativePath}`;
    showA2Output("A5 需求验证报告", `${data.summary}\n\n## 保存路径\n${data.relativePath}\n\n${data.content}`);
    appendSystemMessage(`A5 需求验证报告已保存：${data.relativePath}`);
    await loadA5Status();
    await loadA6Status();
  } catch (error) {
    els.a5Status.textContent = `A5 验证失败：${error.message}`;
    els.a5Status.className = "status error";
    appendSystemMessage(error.message);
  } finally {
    setBusy(false);
  }
});

els.a4ReviseFromA5.addEventListener("click", async () => {
  if (!hasKey() || !state.a5Status?.canReviseA4) return;
  setBusy(true);
  els.a5Status.textContent = "A4 正在按 A5 退回指令修订 SRS...";
  els.a5Status.className = "status ready";
  try {
    const data = await api("/api/a4/revise-from-a5", {
      apiKey: state.apiKey,
    });
    const warningText = (data.warnings || []).length ? `，含 ${data.warnings.length} 条待 A5 判断的提示` : "";
    els.a5Status.textContent = `A4 返修稿已保存：${data.relativePath}${warningText}`;
    showA2Output(
      "A4 SRS 返修稿",
      `${data.summary}\n\n## 保存路径\n${data.relativePath}\n\n## 校验提示\n${(data.warnings || []).map((item) => `- ${item}`).join("\n") || "- 无"}\n\n## 返修来源\n- SRS：${data.sourceSrs}\n- A5：${data.sourceA5Report}\n\n${data.content}`
    );
    appendSystemMessage(`A4 已按 A5 退回指令生成返修稿：${data.relativePath}`);
    await loadA4Status();
    await loadA5Status();
    await loadA6Status();
  } catch (error) {
    els.a5Status.textContent = `A4 返修失败，未生成新 SRS 文件：${error.message}`;
    els.a5Status.className = "status error";
    appendSystemMessage(`A4 返修失败，未写入知识库：${error.message}`);
  } finally {
    setBusy(false);
  }
});

els.a6CreateBaseline.addEventListener("click", async () => {
  if (!hasKey() || !state.a6Status?.canCreateBaseline) return;
  setBusy(true);
  els.a6Status.textContent = "A6 正在创建需求基线...";
  els.a6Status.className = "status ready";
  try {
    const data = await api("/api/a6/create-baseline", {
      apiKey: state.apiKey,
      ccbConclusion: els.ccbConclusion.value.trim() || "通过（无保留意见）",
      acceptA5Risks: els.acceptA5Risks.checked,
    });
    const fileList = (data.files || []).map((item) => `- ${item}`).join("\n");
    els.a6Status.textContent = `A6 基线已创建：${data.relativePath}`;
    showA2Output(
      "A6 需求基线",
      `${data.summary}\n\n## 基线目录\n${data.relativePath}\n\n## 生成文件\n${fileList}`
    );
    appendSystemMessage(`A6 需求基线已创建：${data.relativePath}`);
    await loadA6Status();
    await loadDesignStatus();
  } catch (error) {
    els.a6Status.textContent = `A6 创建基线失败：${error.message}`;
    els.a6Status.className = "status error";
    appendSystemMessage(error.message);
  } finally {
    setBusy(false);
  }
});

els.designRun.addEventListener("click", async () => {
  if (!hasKey() || !state.designStatus?.canRun) return;
  setBusy(true);
  els.designStatus.textContent = "设计阶段智能体正在构建知识图谱并评估架构选型...";
  els.designStatus.className = "status ready";
  try {
    const data = await api("/api/design/run", {
      apiKey: state.apiKey,
    });
    const fileList = (data.files || []).map((item) => `- ${item}`).join("\n");
    const counts = data.nodeCounts || {};
    els.designStatus.textContent = `设计阶段第一步完成：${data.selectedArchitecture}`;
    showA2Output(
      "设计阶段：知识图谱与架构选型",
      `${data.completion}\n\n## 输入基线\n- ${data.latestBaseline}\n- ${data.srsPath}\n\n## 知识图谱节点\nActor ${counts.actor || 0} / Component ${counts.component || 0} / Interface ${counts.interface || 0} / Data ${counts.data || 0} / Constraint ${counts.constraint || 0} / Total ${counts.total || 0}\n\n## 生成文件\n${fileList}\n\n## 摘要\n${data.summary}`
    );
    appendSystemMessage(`设计阶段第一步完成：${data.selectedArchitecture}，已生成 6 份产物。`);
    await loadDesignStatus();
  } catch (error) {
    els.designStatus.textContent = `设计阶段运行失败：${error.message}`;
    els.designStatus.className = "status error";
    appendSystemMessage(error.message);
  } finally {
    setBusy(false);
  }
});

els.saveRecord.addEventListener("click", async () => {
  const stakeholder = selectedStakeholder();
  const unsavedRound = state.manualRounds.find((round) => !round.savedPath);
  if (!unsavedRound) {
    appendSystemMessage("没有待兜底保存的真人手动访谈轮次；正常问答已按轮自动保存。");
    return;
  }
  setBusy(true);
  try {
    const data = await api("/api/records/save", {
      stakeholderId: unsavedRound.stakeholderId || stakeholder.id,
      history: unsavedRound.history,
      summary: "",
    });
    unsavedRound.savedPath = data.relativePath;
    addSavedRecord(data.relativePath);
    appendSystemMessage(`已保存手动访谈：${data.relativePath}`);
  } catch (error) {
    appendSystemMessage(error.message);
  } finally {
    setBusy(false);
  }
});

els.clearChat.addEventListener("click", () => {
  state.history = [];
  state.rounds = [];
  state.manualRounds = [];
  renderChat();
  setBusy(false);
});

Promise.allSettled([loadConfig(), loadStakeholders(), loadA2NotesStatus(), loadA3Status(), loadA4Status(), loadA5Status(), loadA6Status(), loadDesignStatus()]).then((results) => {
  const failed = results.filter((result) => result.status === "rejected");
  if (failed.length > 0) {
    const message = failed.map((result) => result.reason?.message || "状态读取失败").join("；");
    appendSystemMessage(`部分状态读取失败：${message}`);
  }
  renderSavedRecords();
  renderChat();
  updateKeyState();
});
