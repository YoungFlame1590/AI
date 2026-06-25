import { api, show, showError } from "./api.js";
import { modules } from "./config.js";
import { defaultRecordForModule, updateOrderAmountPreview } from "./orders.js";
import {
  allowedModules,
  renderForm,
  renderMetrics,
  renderTable,
  renderTimeline,
  toggleAuth,
} from "./render.js";
import { el, state } from "./state.js";

const handlers = {
  saveRecord,
  deleteRecord,
  runRecordAction,
  runWorkflowAction,
  runChangeTask,
  uploadOrderFile,
  loadOrderFiles,
};

async function login(event) {
  event.preventDefault();
  try {
    const session = await api("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username: el.username.value.trim(), password: el.password.value }),
    });
    state.token = session.token;
    state.user = session.user;
    localStorage.setItem("printshop-token", state.token);
    localStorage.setItem("printshop-user", JSON.stringify(state.user));
    toggleAuth();
    await loadModule("dashboard");
  } catch (error) {
    showError(error);
  }
}

function logout() {
  state.token = "";
  state.user = null;
  localStorage.removeItem("printshop-token");
  localStorage.removeItem("printshop-user");
  toggleAuth();
}

async function loadModule(name = state.module) {
  if (!allowedModules().includes(name)) {
    name = "dashboard";
  }
  state.module = name;
  state.editing = false;
  document.querySelectorAll(".nav button").forEach((button) => {
    button.classList.toggle("active", button.dataset.module === name);
  });
  const config = modules[name];
  el.pageTitle.textContent = config.title;
  el.pageSubTitle.textContent = name === "dashboard" ? "端到端业务状态" : `${config.title} 管理`;
  el.tableTitle.textContent = config.title;
  el.newBtn.disabled = Boolean(config.readonly);
  if (name === "dashboard") {
    await renderDashboard();
    return;
  }
  const data = await api(config.endpoint);
  state.records = Array.isArray(data) ? data : [data];
  state.selected = state.records[0] || null;
  renderTable();
  renderForm(handlers);
  renderMetrics([]);
  renderTimeline([]);
  show(data, `${config.title}已加载`);
}

async function renderDashboard() {
  const workbench = await api("/api/workbench/tasks");
  state.workbench = workbench;
  state.aggregate = null;
  state.selectedTask = null;
  renderMetrics(workbench.metrics || []);
  state.records = workbench.tasks || [];
  state.selected = state.records[0] || null;
  renderTable(["title", "orderNo", "customerName", "status", "actionLabel", "currentStep"], "我的待办任务");
  if (state.selected?.orderId) {
    await loadOrderAggregate(state.selected.orderId, state.selected, false);
    renderForm(handlers);
  } else {
    renderForm(handlers);
    renderTimeline([]);
  }
  show(workbench, "任务工作台已加载");
}

function readForm() {
  const payload = {};
  for (const input of el.recordForm.querySelectorAll("input, textarea, select")) {
    if (input.disabled) continue;
    const value = input.value.trim();
    if (value === "") continue;
    payload[input.name] = input.type === "number" ? Number(value) : value;
  }
  return payload;
}

async function saveRecord() {
  const config = modules[state.module];
  const method = state.selected?.id ? "PUT" : "POST";
  const path = state.selected?.id ? `${config.endpoint}/${state.selected.id}` : config.endpoint;
  const data = await api(path, { method, body: JSON.stringify(readForm()) });
  state.editing = false;
  await loadModule(state.module);
  show(data, method === "POST" ? "新增完成" : "保存完成");
}

async function deleteRecord() {
  if (!state.selected?.id || !confirm(`确认删除 #${state.selected.id}？`)) return;
  const config = modules[state.module];
  const data = await api(`${config.endpoint}/${state.selected.id}`, { method: "DELETE" });
  await loadModule(state.module);
  show(data, "删除完成");
}

async function clearBusinessData() {
  if (state.user?.role !== "ADMIN") return;
  if (!confirm("确认清空所有订单、报价、生产、配送、财务、库存和审计业务数据？基础账号和门店会保留。")) return;
  const data = await api("/api/admin/business-data", { method: "DELETE" });
  state.module = "dashboard";
  state.selected = null;
  state.records = [];
  await loadModule("dashboard");
  show(data, "业务数据已清空");
}

async function runRecordAction(method, path, body) {
  if (body === "__changeRequestFromOrder") {
    body = buildOrderChangeRequestBody();
  }
  const data = await api(path, { method, body: JSON.stringify(body) });
  await loadModule(state.module);
  show(data, "状态动作完成");
}

async function runWorkflowAction(action, orderId, body = {}) {
  if (action === "REQUEST_CHANGE") {
    body = buildOrderChangeRequestBody();
  }
  const data = await api(`/api/orders/${orderId}/workflow/actions/${action}`, {
    method: "POST",
    body: JSON.stringify(body),
  });
  await loadOrderAggregate(orderId, state.selectedTask, true);
  await refreshWorkbenchOnly();
  show(data, data.message || "工作流动作完成");
}

async function runChangeTask(decision, changeRequestId) {
  const data = await api(`/api/order-change-requests/${changeRequestId}/${decision}`, {
    method: "POST",
    body: JSON.stringify({ comment: decision === "approve" ? "同意订单变更" : "驳回订单变更" }),
  });
  if (state.aggregate?.order?.id) {
    await loadOrderAggregate(state.aggregate.order.id, state.selectedTask, true);
  }
  await refreshWorkbenchOnly();
  show(data, decision === "approve" ? "变更已审批通过" : "变更已驳回");
}

async function loadOrderAggregate(orderId, task = null, render = true) {
  state.selectedTask = task;
  const aggregate = await api(`/api/orders/${orderId}/aggregate`);
  state.aggregate = aggregate;
  state.selected = aggregate.order;
  if (render) {
    renderTable(["title", "orderNo", "customerName", "status", "actionLabel", "currentStep"], "我的待办任务");
    renderForm(handlers);
  }
  return aggregate;
}

async function refreshWorkbenchOnly() {
  if (state.module !== "dashboard") return;
  const workbench = await api("/api/workbench/tasks");
  state.workbench = workbench;
  state.records = workbench.tasks || [];
  renderMetrics(workbench.metrics || []);
}

function buildOrderChangeRequestBody() {
  const order = state.selected || {};
  return {
    productType: order.productType || "培训手册",
    colorMode: order.colorMode === "装订加覆膜" ? "彩色" : "装订加覆膜",
    pageCount: Number(order.pageCount || 1),
    copies: Number(order.copies || 1),
    deliveryMode: order.deliveryMode || "同城配送",
    priority: order.priority === "加急" ? "普通" : "加急",
    reason: "客户/店员申请处理中订单规格或时效变更",
  };
}

async function uploadOrderFile() {
  if (!state.selected?.id) return;
  const input = document.createElement("input");
  input.type = "file";
  input.accept = ".pdf,.jpg,.jpeg,.png,.doc,.docx,.psd,.ai";
  input.addEventListener("change", async () => {
    if (!input.files.length) return;
    const form = new FormData();
    form.append("file", input.files[0]);
    try {
      const data = await api(`/api/orders/${state.selected.id}/files`, { method: "POST", body: form });
      await loadModule("orders");
      show(data, "文件已上传");
    } catch (error) {
      showError(error);
    }
  });
  input.click();
}

async function loadOrderFiles() {
  if (!state.selected?.id) return;
  try {
    const data = await api(`/api/orders/${state.selected.id}/files`);
    show(data, "订单文件已加载");
  } catch (error) {
    showError(error);
  }
}

el.loginForm.addEventListener("submit", login);
el.logoutBtn.addEventListener("click", logout);
el.clearDataBtn.addEventListener("click", () => clearBusinessData().catch(showError));
el.refreshBtn.addEventListener("click", () => loadModule().catch(showError));
el.newBtn.addEventListener("click", () => {
  state.selected = defaultRecordForModule(state.module);
  state.editing = true;
  renderForm(handlers);
});
el.recordForm.addEventListener("input", updateOrderAmountPreview);
el.recordForm.addEventListener("change", updateOrderAmountPreview);

document.querySelectorAll(".nav button").forEach((button) => {
  button.addEventListener("click", () => loadModule(button.dataset.module).catch(showError));
});

document.body.addEventListener("click", (event) => {
  const row = event.target.closest("tr[data-id]");
  if (!row) return;
  const rowId = row.dataset.id;
  state.selected = state.records.find((record) => String(record.id) === rowId) || null;
  state.editing = false;
  if (state.module === "dashboard" && state.selected?.orderId) {
    loadOrderAggregate(state.selected.orderId, state.selected).catch(showError);
    return;
  }
  state.aggregate = null;
  state.selectedTask = null;
  renderTable();
  renderForm(handlers);
});

toggleAuth();
if (state.token && state.user) {
  loadModule("dashboard").catch((error) => {
    logout();
    showError(error);
  });
}
