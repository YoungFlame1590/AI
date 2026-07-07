import { api, authHeader, show, showError } from "./api.js";
import { deliveryQuoteFormFields, modules, serviceReviewFormFields } from "./config.js";
import { defaultRecordForModule, updateOrderAmountPreview } from "./orders.js";
import {
  allowedModules,
  renderForm,
  renderMetrics,
  promptActionForm,
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
  createOrderFromFile,
  uploadOrderFile,
  loadOrderFiles,
  saveDesignVersion,
  restoreDesignVersion,
  submitDesignOrder,
  runDemoTest,
};

let registerMode = false;

async function login(event) {
  event.preventDefault();
  try {
    if (registerMode) {
      const session = await api("/api/auth/register", {
        method: "POST",
        body: JSON.stringify({
          username: el.username.value.trim(),
          password: el.password.value,
          displayName: el.displayName.value.trim() || el.username.value.trim(),
        }),
      });
      state.token = session.token;
      state.user = session.user;
      localStorage.setItem("printshop-token", state.token);
      localStorage.setItem("printshop-user", JSON.stringify(state.user));
      toggleRegisterMode(false);
      toggleAuth();
      await loadModule("dashboard");
      return;
    }
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
    showAuthError(error.message);
  }
}

function toggleRegisterMode(force = null) {
  registerMode = force === null ? !registerMode : force;
  clearAuthError();
  document.querySelectorAll(".register-only").forEach((node) => node.classList.toggle("hidden", !registerMode));
  el.loginForm.querySelector("button[type='submit']").textContent = registerMode ? "注册并进入" : "登录系统";
  el.registerModeBtn.textContent = registerMode ? "返回登录" : "注册客户账号";
  el.username.autocomplete = registerMode ? "username" : "username";
  el.password.autocomplete = registerMode ? "new-password" : "current-password";
  el.displayName.required = registerMode;
}

function showAuthError(message) {
  el.authMessage.textContent = message || "登录失败，请检查账号和密码。";
  el.authMessage.classList.remove("hidden");
}

function clearAuthError() {
  el.authMessage.textContent = "";
  el.authMessage.classList.add("hidden");
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
  state.creatingOrderFromFile = false;
  state.designProjectDetail = null;
  document.querySelectorAll(".nav button").forEach((button) => {
    button.classList.toggle("active", button.dataset.module === name);
  });
  const config = modules[name];
  el.pageTitle.textContent = config.title;
  el.pageSubTitle.textContent = name === "dashboard" ? "端到端业务状态" : `${config.title} 管理`;
  el.tableTitle.textContent = config.title;
  el.newBtn.hidden = name === "dashboard";
  el.newBtn.disabled = Boolean(config.readonly);
  if (name === "dashboard") {
    await renderDashboard();
    return;
  }
  const data = await api(config.endpoint);
  state.records = Array.isArray(data) ? data : [data];
  state.selected = state.records[0] || null;
  if (name === "designProjects" && state.selected?.id) {
    state.designProjectDetail = await api(`/api/design-projects/${state.selected.id}`);
  }
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
  state.creatingOrderFromFile = false;
  await loadModule(state.module);
  show(data, method === "POST" ? "新增完成" : "保存完成");
}

async function deleteRecord() {
  if (!state.selected?.id) return;
  const isUser = state.module === "users";
  const prompt = isUser
    ? `确认停用账号 ${state.selected.username}？历史订单和审计记录将保留。`
    : `确认删除 #${state.selected.id}？`;
  if (!confirm(prompt)) return;
  const config = modules[state.module];
  const data = await api(`${config.endpoint}/${state.selected.id}`, { method: "DELETE" });
  await loadModule(state.module);
  show(data, isUser ? "账号已停用，历史数据已保留" : "删除完成");
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

async function runDemoTest() {
  if (state.user?.role !== "ADMIN") return;
  if (!confirm("确认清空业务数据并生成 CR09 一键测试数据？基础账号、门店、模板和默认库存会保留。")) return;
  const data = await api("/api/admin/demo-test", {
    method: "POST",
    body: JSON.stringify({ orders: 24, clear: true }),
  });
  await loadModule("storeQualityRanking");
  show(data, data.message || "CR09 一键测试完成");
}

async function runRecordAction(method, path, body) {
  if (requiresConfirmation(path) && !confirm("这是不可逆业务动作，确认继续执行？")) return;
  if (body === "__changeRequestFromOrder") {
    body = buildOrderChangeRequestBody();
  } else if (body === "__designProjectFromTemplate") {
    body = buildDesignProjectBody();
  } else if (body === "__designVersion") {
    body = buildDesignVersionBody();
  } else if (body === "__designSubmitOrder") {
    body = buildDesignSubmitOrderBody();
  } else if (body === "__deliveryQuoteFromOrder") {
    body = await buildDeliveryQuoteBody();
    if (!body) return;
  } else if (body === "__serviceReviewFromInvitation" || body === "__serviceReviewFromTask") {
    const orderId = state.selected?.orderId || state.selectedTask?.orderId || state.selected?.id;
    path = `/api/orders/${orderId}/service-reviews`;
    body = await buildServiceReviewBody();
    if (!body) return;
  } else if (body === "__complaintReply") {
    body = { reply: "已联系客户并记录处理方案，24小时内跟进完成。" };
  }
  const data = await api(path, { method, body: JSON.stringify(body) });
  await loadModule(state.module);
  show(data, "状态动作完成");
}

async function runWorkflowAction(action, orderId, body = {}) {
  if (requiresWorkflowConfirmation(action) && !confirm("这是不可逆业务动作，确认继续执行？")) return;
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

async function saveDesignVersion(canvasJson) {
  if (!state.selected?.id) return null;
  const detail = await api(`/api/design-projects/${state.selected.id}/versions`, {
    method: "POST",
    body: JSON.stringify({ label: "编辑器保存", canvasJson }),
  });
  state.designProjectDetail = detail;
  state.selected = detail.project;
  show(detail, "设计版本已保存");
  return detail;
}

async function restoreDesignVersion(versionNo) {
  if (!state.selected?.id) return null;
  const detail = await api(`/api/design-projects/${state.selected.id}/restore/${versionNo}`, {
    method: "POST",
  });
  state.designProjectDetail = detail;
  state.selected = detail.project;
  show(detail, `已回溯到 V${versionNo}`);
  return detail;
}

async function submitDesignOrder(payload, canvasJson) {
  if (!state.selected?.id) return null;
  await saveDesignVersion(canvasJson);
  const data = await api(`/api/design-projects/${state.selected.id}/submit-order`, {
    method: "POST",
    body: JSON.stringify(payload || {}),
  });
  await loadModule("designProjects");
  show(data, "设计稿已提交订单");
  return data;
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

function buildDesignProjectBody() {
  const template = state.selected || {};
  return {
    templateId: template.id,
    title: `${template.title || "在线模板"}设计稿`,
    canvasJson: template.canvasJson || "{}",
  };
}

function buildDesignVersionBody() {
  const textarea = el.recordForm.querySelector("[name='canvasJson']");
  return {
    label: "页面手动保存",
    canvasJson: textarea?.value || state.selected?.canvasJson || "{}",
  };
}

function buildDesignSubmitOrderBody() {
  return {
    copies: Number(state.selected?.defaultCopies || 100),
    paperType: "标准纸",
    craftType: "无特殊工艺",
    deliveryMode: "到店自提",
    priority: "普通",
  };
}

async function buildDeliveryQuoteBody() {
  const order = state.selected || {};
  const values = await promptActionForm({
    title: "第三方配送报价",
    fields: deliveryQuoteFormFields,
    initial: {
      deliveryAddress: "",
      packageWeightKg: String(Math.max(0.5, Number(order.copies || 1) / 100)),
      channelCode: order.priority === "特急" ? "IMMEDIATE" : "EXPRESS",
    },
    submitLabel: "获取报价",
  });
  if (!values) return null;
  return {
    orderId: order.id,
    channelCode: values.channelCode,
    pickupAddress: order.storeName || "门店",
    deliveryAddress: values.deliveryAddress,
    packageWeightKg: Number(values.packageWeightKg),
  };
}

async function buildServiceReviewBody() {
  const values = await promptActionForm({
    title: "提交服务评价",
    fields: serviceReviewFormFields,
    initial: {
      printQualityRating: "5",
      timelinessRating: "5",
      staffRating: "5",
      valueRating: "5",
      comment: "",
    },
    submitLabel: "提交评价",
  });
  if (!values) return null;
  return {
    printQualityRating: Number(values.printQualityRating),
    timelinessRating: Number(values.timelinessRating),
    staffRating: Number(values.staffRating),
    valueRating: Number(values.valueRating),
    comment: values.comment || "",
  };
}

function requiresWorkflowConfirmation(action) {
  return ["COMPLETE_PRODUCTION", "SIGN_DELIVERY", "INVOICE", "REFUND"].includes(action);
}

function requiresConfirmation(path) {
  return /\/(issue|refund|sign)$/.test(path) || path.includes("/workflow/invoice") || path.includes("/workflow/refund");
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
      if (state.module === "dashboard") {
        const orderId = state.selected.id;
        await refreshWorkbenchOnly();
        const nextTask = state.records.find((task) => String(task.orderId) === String(orderId)) || state.selectedTask;
        await loadOrderAggregate(orderId, nextTask, true);
      } else {
        await loadModule("orders");
      }
      show(data, data.analysisMessage || "文件已上传");
    } catch (error) {
      showError(error);
    }
  });
  input.click();
}

async function createOrderFromFile() {
  const input = el.recordForm.querySelector("[name='initialOrderFile']");
  if (!input?.files?.length) {
    throw new Error("请先选择订单文件。");
  }
  const form = new FormData();
  form.append("file", input.files[0]);
  const data = await api("/api/orders/from-file", { method: "POST", body: form });
  const orderId = data.order?.id;
  state.creatingOrderFromFile = false;
  await loadModule("orders");
  state.selected = state.records.find((record) => String(record.id) === String(orderId)) || state.selected;
  state.editing = false;
  renderTable();
  renderForm(handlers);
  show(data, data.file?.analysisMessage || "文件已上传，订单已生成");
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

async function openOrderFile(fileId, mode) {
  const response = await fetch(`/api/order-files/${fileId}/${mode}`, {
    headers: authHeader(),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || `${response.status} ${response.statusText}`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  if (mode === "preview") {
    window.open(url, "_blank", "noopener");
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
    return;
  }
  const link = document.createElement("a");
  link.href = url;
  link.download = fileNameFromDisposition(response.headers.get("Content-Disposition")) || `order-file-${fileId}`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function fileNameFromDisposition(disposition) {
  if (!disposition) return "";
  const utf8 = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8) return decodeURIComponent(utf8[1]);
  const ascii = disposition.match(/filename="?([^";]+)"?/i);
  return ascii ? ascii[1] : "";
}

el.loginForm.addEventListener("submit", login);
el.registerModeBtn.addEventListener("click", () => toggleRegisterMode());
el.loginForm.addEventListener("input", clearAuthError);
el.logoutBtn.addEventListener("click", logout);
el.demoTestBtn.addEventListener("click", () => runDemoTest().catch(showError));
el.clearDataBtn.addEventListener("click", () => clearBusinessData().catch(showError));
el.refreshBtn.addEventListener("click", () => loadModule().catch(showError));
el.newBtn.addEventListener("click", () => {
  if (state.module === "orders") {
    state.selected = null;
    state.editing = false;
    state.creatingOrderFromFile = true;
    renderForm(handlers);
    return;
  }
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
  const preview = event.target.closest("[data-file-preview]");
  if (preview) {
    openOrderFile(preview.dataset.filePreview, "preview").catch(showError);
    return;
  }
  const download = event.target.closest("[data-file-download]");
  if (download) {
    openOrderFile(download.dataset.fileDownload, "download").catch(showError);
    return;
  }
  const row = event.target.closest("tr[data-id]");
  if (!row) return;
  const rowId = row.dataset.id;
  state.selected = state.records.find((record) => String(record.id) === rowId) || null;
  state.editing = false;
  state.creatingOrderFromFile = false;
  if (state.module === "dashboard" && state.selected?.orderId) {
    loadOrderAggregate(state.selected.orderId, state.selected).catch(showError);
    return;
  }
  state.aggregate = null;
  state.selectedTask = null;
  if (state.module === "designProjects" && state.selected?.id) {
    api(`/api/design-projects/${state.selected.id}`)
      .then((detail) => {
        state.designProjectDetail = detail;
        renderTable();
        renderForm(handlers);
      })
      .catch(showError);
    return;
  }
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
