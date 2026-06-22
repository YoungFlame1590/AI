const state = {
  busy: false,
  orderId: "ORD-DEMO-001",
};

const payloads = {
  order: document.querySelector("#orderPayload"),
  quotation: document.querySelector("#quotationPayload"),
  production: document.querySelector("#productionPayload"),
  delivery: document.querySelector("#deliveryPayload"),
  invoice: document.querySelector("#invoicePayload"),
};

const els = {
  serviceStatus: document.querySelector("#serviceStatus"),
  totalRequests: document.querySelector("#totalRequests"),
  lastRequestAt: document.querySelector("#lastRequestAt"),
  moduleCounts: document.querySelector("#moduleCounts"),
  latestResponse: document.querySelector("#latestResponse"),
  requestLog: document.querySelector("#requestLog"),
  auditList: document.querySelector("#auditList"),
  runWorkflow: document.querySelector("#runWorkflow"),
  refreshStats: document.querySelector("#refreshStats"),
};

function setBusy(isBusy) {
  state.busy = isBusy;
  document.querySelectorAll("button").forEach((button) => {
    button.disabled = isBusy;
  });
}

function pretty(value) {
  return JSON.stringify(value, null, 2);
}

function writePayloads(orderId = state.orderId) {
  state.orderId = orderId;
  payloads.order.value = pretty({
    orderId,
    fileSizeMb: 10.5,
    pageCount: 12,
    paymentStatus: "1已付",
    financialVerifyStatus: "0待核销",
  });
  payloads.quotation.value = pretty({
    orderId,
    discountRate: 0.95,
    finalAmount: 180.0,
  });
  payloads.production.value = pretty({
    orderId,
    deviceSn: "DEVICE-01",
    creditLimitUsed: 1200.0,
  });
  payloads.delivery.value = pretty({
    orderId,
    targetStoreId: "STORE-A",
    financialVerifyStatus: "1已核销",
    outsourcingCostRatio: 12.5,
  });
  payloads.invoice.value = pretty({
    orderId,
    amount: 180.0,
    triggerMode: "交付后开",
  });
}

function addLog(text, ok = true) {
  const item = document.createElement("li");
  item.className = ok ? "ok" : "warn";
  item.textContent = `${new Date().toLocaleTimeString()} ${text}`;
  els.requestLog.prepend(item);
}

function showResponse(value) {
  els.latestResponse.textContent = pretty(value);
}

function parsePayload(textarea) {
  try {
    return JSON.parse(textarea.value);
  } catch (error) {
    throw new Error(`JSON 格式错误：${error.message}`);
  }
}

async function requestJson(path, options = {}) {
  const response = await fetch(path, {
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": `ui-${Date.now()}`,
      ...(options.headers || {}),
    },
    ...options,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || data.detail || `${response.status} ${response.statusText}`);
  }
  return data;
}

async function postJson(path, payload) {
  return requestJson(path, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

function renderStats(stats) {
  els.serviceStatus.textContent = "运行中";
  els.serviceStatus.className = "ok";
  els.totalRequests.textContent = stats.totalRequests ?? 0;
  els.lastRequestAt.textContent = stats.lastRequestAt ? new Date(stats.lastRequestAt).toLocaleString() : "-";
  const modules = ["ORD", "QUO", "PRO", "DLV", "FIN", "AUD"];
  els.moduleCounts.innerHTML = "";
  for (const moduleName of modules) {
    const node = document.createElement("div");
    node.className = "module-count";
    node.innerHTML = `<span>${moduleName}</span><strong>${stats.moduleCounts?.[moduleName] || 0}</strong>`;
    els.moduleCounts.appendChild(node);
  }
}

async function refreshStats() {
  try {
    const stats = await requestJson("/stats", { method: "GET" });
    renderStats(stats);
    return stats;
  } catch (error) {
    els.serviceStatus.textContent = "异常";
    els.serviceStatus.className = "warn";
    addLog(`stats 读取失败：${error.message}`, false);
    throw error;
  }
}

function renderAuditLogs(logs) {
  if (!Array.isArray(logs) || logs.length === 0) {
    els.auditList.textContent = "暂无审计日志";
    return;
  }
  els.auditList.innerHTML = "";
  for (const item of logs.slice().reverse()) {
    const node = document.createElement("div");
    node.className = "audit-item";
    node.textContent = `${item.timestamp || "-"} · ${item.action || "-"} · ${item.operatorId || "-"}`;
    els.auditList.appendChild(node);
  }
}

async function runAction(action) {
  const routes = {
    order: ["/api/v1/orders", payloads.order],
    quotation: ["/api/v1/quotations/calculate", payloads.quotation],
    production: ["/api/v1/productions/dispatch", payloads.production],
    delivery: ["/api/v1/deliveries/route", payloads.delivery],
    invoice: ["/api/v1/invoices/issue", payloads.invoice],
  };
  if (action === "audit") {
    const logs = await requestJson("/api/v1/audit-logs", { method: "GET" });
    renderAuditLogs(logs);
    showResponse(logs);
    addLog("GET /api/v1/audit-logs 成功");
    await refreshStats();
    return logs;
  }
  const [path, textarea] = routes[action];
  const data = await postJson(path, parsePayload(textarea));
  showResponse(data);
  addLog(`${path} 成功`);
  await refreshStats();
  return data;
}

async function handleAction(action) {
  if (state.busy) return;
  setBusy(true);
  try {
    await runAction(action);
  } catch (error) {
    addLog(error.message, false);
    showResponse({ error: error.message });
  } finally {
    setBusy(false);
  }
}

async function runWorkflow() {
  if (state.busy) return;
  const orderId = `ORD-DEMO-${Date.now().toString().slice(-6)}`;
  writePayloads(orderId);
  setBusy(true);
  try {
    addLog(`开始演示完整流程：${orderId}`);
    await runAction("order");
    await runAction("quotation");
    await runAction("production");
    await runAction("delivery");
    await runAction("invoice");
    await runAction("audit");
    const stats = await refreshStats();
    showResponse({
      message: "完整流程演示完成",
      orderId,
      stats,
    });
    addLog("完整流程演示完成");
  } catch (error) {
    addLog(`完整流程中断：${error.message}`, false);
    showResponse({ error: error.message });
  } finally {
    setBusy(false);
  }
}

document.querySelectorAll("[data-run]").forEach((button) => {
  button.addEventListener("click", () => handleAction(button.dataset.run));
});

els.runWorkflow.addEventListener("click", runWorkflow);
els.refreshStats.addEventListener("click", async () => {
  if (state.busy) return;
  setBusy(true);
  try {
    const stats = await refreshStats();
    showResponse(stats);
    addLog("GET /stats 成功");
  } catch (error) {
    showResponse({ error: error.message });
  } finally {
    setBusy(false);
  }
});

writePayloads();
refreshStats().catch(() => {});
