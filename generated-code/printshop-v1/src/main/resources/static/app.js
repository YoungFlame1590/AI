const state = {
  busy: false,
  roles: [],
  roleId: localStorage.getItem("printshop-role") || "customer",
  snapshot: null,
  selectedOrderId: "",
};

const els = {
  roleSelect: document.querySelector("#roleSelect"),
  currentUser: document.querySelector("#currentUser"),
  roleFocus: document.querySelector("#roleFocus"),
  serviceStatus: document.querySelector("#serviceStatus"),
  totalRequests: document.querySelector("#totalRequests"),
  lastRequestAt: document.querySelector("#lastRequestAt"),
  metrics: document.querySelector("#metrics"),
  taskCount: document.querySelector("#taskCount"),
  taskList: document.querySelector("#taskList"),
  orderCount: document.querySelector("#orderCount"),
  orderRows: document.querySelector("#orderRows"),
  selectedOrder: document.querySelector("#selectedOrder"),
  orderDetail: document.querySelector("#orderDetail"),
  actionBar: document.querySelector("#actionBar"),
  moduleCounts: document.querySelector("#moduleCounts"),
  auditList: document.querySelector("#auditList"),
  latestResponse: document.querySelector("#latestResponse"),
  lastMessage: document.querySelector("#lastMessage"),
  refreshWorkbench: document.querySelector("#refreshWorkbench"),
  resetDemo: document.querySelector("#resetDemo"),
};

function setBusy(isBusy) {
  state.busy = isBusy;
  document.querySelectorAll("button, select").forEach((node) => {
    node.disabled = isBusy;
  });
}

function pretty(value) {
  return JSON.stringify(value, null, 2);
}

function showResponse(value) {
  els.latestResponse.textContent = pretty(value);
  els.lastMessage.textContent = value?.message || "已更新";
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

async function loadRoles() {
  state.roles = await requestJson("/api/v1/roles", { method: "GET" });
  if (!state.roles.some((role) => role.roleId === state.roleId)) {
    state.roleId = state.roles[0]?.roleId || "customer";
  }
  els.roleSelect.innerHTML = "";
  for (const role of state.roles) {
    const option = document.createElement("option");
    option.value = role.roleId;
    option.textContent = role.roleName;
    option.selected = role.roleId === state.roleId;
    els.roleSelect.appendChild(option);
  }
}

async function loadWorkbench() {
  state.snapshot = await requestJson(`/api/v1/workbench/${state.roleId}`, { method: "GET" });
  const orders = state.snapshot.orders || [];
  if (!orders.some((order) => order.orderId === state.selectedOrderId)) {
    state.selectedOrderId = orders[0]?.orderId || "";
  }
  render();
  showResponse({
    message: state.snapshot.message,
    role: state.snapshot.role?.roleName,
    selectedOrder: state.selectedOrderId,
  });
}

function render() {
  renderHeader();
  renderMetrics();
  renderTasks();
  renderOrders();
  renderDetail();
  renderActions();
  renderStats();
  renderAudits();
}

function renderHeader() {
  const role = state.snapshot?.role;
  els.currentUser.textContent = role ? `${role.userName} · ${role.userId}` : "-";
  els.roleFocus.textContent = role?.focus || "-";
  els.serviceStatus.textContent = "运行中";
  els.serviceStatus.className = "ok";
}

function renderMetrics() {
  els.metrics.innerHTML = "";
  for (const metric of state.snapshot?.metrics || []) {
    const card = document.createElement("article");
    card.className = `metric-card ${metric.tone || "normal"}`;
    card.innerHTML = `<span>${metric.label}</span><strong>${metric.value}</strong>`;
    els.metrics.appendChild(card);
  }
}

function renderTasks() {
  const tasks = state.snapshot?.tasks || [];
  els.taskCount.textContent = String(tasks.length);
  els.taskList.innerHTML = "";
  if (tasks.length === 0) {
    els.taskList.textContent = "当前角色暂无待办";
    return;
  }
  for (const task of tasks) {
    const item = document.createElement("article");
    item.className = `task-item ${task.severity === "高" ? "hot" : ""}`;
    item.innerHTML = `
      <div>
        <strong>${task.title}</strong>
        <span>${task.orderId || "全局任务"} · ${task.dueText}</span>
      </div>
      <button type="button" data-action="${task.actionId}" data-order="${task.orderId || ""}">处理</button>
    `;
    els.taskList.appendChild(item);
  }
}

function renderOrders() {
  const orders = state.snapshot?.orders || [];
  els.orderCount.textContent = String(orders.length);
  els.orderRows.innerHTML = "";
  if (orders.length === 0) {
    const row = document.createElement("tr");
    row.innerHTML = `<td colspan="5">当前角色暂无可见订单</td>`;
    els.orderRows.appendChild(row);
    return;
  }
  for (const order of orders) {
    const row = document.createElement("tr");
    row.className = order.orderId === state.selectedOrderId ? "selected" : "";
    row.dataset.order = order.orderId;
    row.innerHTML = `
      <td><strong>${order.orderId}</strong><span>${order.storeName}</span></td>
      <td>${order.customerName}</td>
      <td><span class="status-pill">${order.status}</span></td>
      <td>¥${Number(order.amount || 0).toFixed(2)}</td>
      <td>${(order.tags || []).map((tag) => `<span class="tag">${tag}</span>`).join("")}</td>
    `;
    els.orderRows.appendChild(row);
  }
}

function renderDetail() {
  const order = selectedOrder();
  els.selectedOrder.textContent = order ? order.orderId : "未选择";
  if (!order) {
    els.orderDetail.textContent = "请选择一笔订单";
    return;
  }
  const fields = [
    ["客户", order.customerName],
    ["门店", order.storeName],
    ["主状态", order.status],
    ["报价", order.quoteStatus],
    ["排产", order.productionStatus],
    ["配送", order.deliveryStatus],
    ["发票", order.invoiceStatus],
    ["财务", order.financeStatus],
    ["优先级", order.priority],
    ["当前步骤", order.currentStep],
  ];
  els.orderDetail.innerHTML = fields.map(([label, value]) => `
    <div>
      <span>${label}</span>
      <strong>${value || "-"}</strong>
    </div>
  `).join("");
}

function renderActions() {
  els.actionBar.innerHTML = "";
  for (const action of state.snapshot?.actions || []) {
    const button = document.createElement("button");
    button.type = "button";
    button.dataset.action = action.actionId;
    button.dataset.order = action.orderRequired ? state.selectedOrderId : "";
    button.textContent = action.label;
    if (action.orderRequired && !state.selectedOrderId) {
      button.disabled = true;
    }
    if (!action.orderRequired) {
      button.className = "secondary";
    }
    els.actionBar.appendChild(button);
  }
}

function renderStats() {
  const stats = state.snapshot?.stats || {};
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

function renderAudits() {
  const audits = state.snapshot?.audits || [];
  if (audits.length === 0) {
    els.auditList.textContent = "暂无审计日志";
    return;
  }
  els.auditList.innerHTML = "";
  for (const item of audits) {
    const node = document.createElement("article");
    node.className = "audit-item";
    node.innerHTML = `
      <strong>${item.action || "-"}</strong>
      <span>${item.operatorId || "-"} · ${formatTime(item.timestamp)}</span>
      <p>${item.snapshot || ""}</p>
    `;
    els.auditList.appendChild(node);
  }
}

function selectedOrder() {
  return (state.snapshot?.orders || []).find((order) => order.orderId === state.selectedOrderId);
}

function formatTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

async function runAction(actionId, orderId) {
  setBusy(true);
  try {
    const payload = {};
    const order = selectedOrder();
    if (actionId === "clerk_quote_order" && order) {
      payload.amount = order.amount;
    }
    const snapshot = await requestJson("/api/v1/workbench/actions", {
      method: "POST",
      body: JSON.stringify({
        roleId: state.roleId,
        action: actionId,
        orderId: orderId || "",
        payload,
      }),
    });
    state.snapshot = snapshot;
    const orders = snapshot.orders || [];
    if (!orders.some((item) => item.orderId === state.selectedOrderId)) {
      state.selectedOrderId = orders[0]?.orderId || "";
    }
    render();
    showResponse({
      message: snapshot.message,
      role: snapshot.role?.roleName,
      selectedOrder: state.selectedOrderId,
    });
  } catch (error) {
    showError(error);
  } finally {
    setBusy(false);
  }
}

async function resetDemo() {
  setBusy(true);
  try {
    state.snapshot = await requestJson(`/api/v1/workbench/reset?roleId=${encodeURIComponent(state.roleId)}`, {
      method: "POST",
    });
    state.selectedOrderId = state.snapshot.orders?.[0]?.orderId || "";
    render();
    showResponse({ message: state.snapshot.message, role: state.snapshot.role?.roleName });
  } catch (error) {
    showError(error);
  } finally {
    setBusy(false);
  }
}

function showError(error) {
  els.serviceStatus.textContent = "异常";
  els.serviceStatus.className = "warn";
  showResponse({ error: error.message });
}

els.roleSelect.addEventListener("change", async () => {
  state.roleId = els.roleSelect.value;
  localStorage.setItem("printshop-role", state.roleId);
  state.selectedOrderId = "";
  setBusy(true);
  try {
    await loadWorkbench();
  } catch (error) {
    showError(error);
  } finally {
    setBusy(false);
  }
});

els.refreshWorkbench.addEventListener("click", async () => {
  if (state.busy) return;
  setBusy(true);
  try {
    await loadWorkbench();
  } catch (error) {
    showError(error);
  } finally {
    setBusy(false);
  }
});

els.resetDemo.addEventListener("click", () => {
  if (!state.busy) resetDemo();
});

document.body.addEventListener("click", (event) => {
  const actionButton = event.target.closest("[data-action]");
  if (actionButton && !state.busy) {
    runAction(actionButton.dataset.action, actionButton.dataset.order || state.selectedOrderId);
    return;
  }
  const row = event.target.closest("tr[data-order]");
  if (row && !state.busy) {
    state.selectedOrderId = row.dataset.order;
    renderOrders();
    renderDetail();
    renderActions();
  }
});

async function init() {
  setBusy(true);
  try {
    await loadRoles();
    await loadWorkbench();
  } catch (error) {
    showError(error);
  } finally {
    setBusy(false);
  }
}

init();
