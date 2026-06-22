const modules = {
  dashboard: { title: "工作台", endpoint: "/api/me/dashboard", readonly: true },
  orders: {
    title: "订单",
    endpoint: "/api/orders",
    id: "id",
    fields: [
      ["orderNo", "订单号"],
      ["customerName", "客户"],
      ["storeId", "门店ID", "number"],
      ["productType", "产品类型"],
      ["colorMode", "颜色/工艺"],
      ["pageCount", "页数", "number"],
      ["copies", "份数", "number"],
      ["dueAt", "交付时间"],
      ["deliveryMode", "交付方式"],
      ["priority", "优先级"],
      ["totalAmount", "金额", "number"],
      ["status", "状态"],
      ["currentStep", "当前步骤", "textarea"],
    ],
    columns: ["orderNo", "customerName", "productType", "status", "totalAmount"],
    actions: [
      ["提交审核", "POST", (id) => `/api/orders/${id}/status`, { status: "REVIEWING", step: "门店正在审核文件" }],
      ["进入生产", "POST", (id) => `/api/orders/${id}/status`, { status: "IN_PRODUCTION", step: "订单已进入生产排程" }],
    ],
  },
  quotations: {
    title: "报价",
    endpoint: "/api/quotations",
    fields: [
      ["quoteNo", "报价号"],
      ["orderId", "订单ID", "number"],
      ["subtotal", "小计", "number"],
      ["discountRate", "折扣率", "number"],
      ["finalAmount", "最终金额", "number"],
      ["status", "状态"],
      ["validUntil", "有效期"],
    ],
    columns: ["quoteNo", "orderId", "finalAmount", "discountRate", "status"],
    actions: [["审批通过", "POST", (id) => `/api/quotations/${id}/approve`]],
  },
  jobTickets: {
    title: "作业单",
    endpoint: "/api/job-tickets",
    fields: [
      ["ticketNo", "作业单号"],
      ["orderId", "订单ID", "number"],
      ["quotationId", "报价ID", "number"],
      ["specs", "规格", "textarea"],
      ["paperType", "纸张"],
      ["binding", "装订"],
      ["status", "状态"],
    ],
    columns: ["ticketNo", "orderId", "paperType", "binding", "status"],
  },
  productionTasks: {
    title: "生产排程",
    endpoint: "/api/production-tasks",
    fields: [
      ["taskNo", "生产任务号"],
      ["jobTicketId", "作业单ID", "number"],
      ["station", "工位/设备"],
      ["operatorName", "操作员"],
      ["plannedStart", "计划开始"],
      ["plannedEnd", "计划结束"],
      ["status", "状态"],
      ["progressPercent", "进度%", "number"],
      ["qualityStatus", "质检状态"],
    ],
    columns: ["taskNo", "station", "operatorName", "status", "progressPercent"],
    actions: [["完工质检通过", "POST", (id) => `/api/production-tasks/${id}/complete`]],
  },
  inventoryItems: {
    title: "库存",
    endpoint: "/api/inventory-items",
    fields: [
      ["sku", "SKU"],
      ["itemName", "物料名称"],
      ["category", "分类"],
      ["unit", "单位"],
      ["quantity", "库存数量", "number"],
      ["safetyStock", "安全库存", "number"],
      ["location", "库位"],
    ],
    columns: ["sku", "itemName", "category", "quantity", "safetyStock"],
    actions: [["入库 +10", "POST", (id) => `/api/inventory-items/${id}/adjust`, { delta: 10 }]],
  },
  deliveryTasks: {
    title: "配送/外协",
    endpoint: "/api/delivery-tasks",
    fields: [
      ["taskNo", "配送任务号"],
      ["orderId", "订单ID", "number"],
      ["mode", "模式"],
      ["carrierName", "承运人"],
      ["targetStore", "目标/地址"],
      ["status", "状态"],
      ["signedBy", "签收人"],
    ],
    columns: ["taskNo", "orderId", "mode", "carrierName", "status"],
    actions: [["签收", "POST", (id) => `/api/delivery-tasks/${id}/sign`, { signedBy: "客户签收" }]],
  },
  invoices: {
    title: "发票",
    endpoint: "/api/invoices",
    fields: [
      ["invoiceNo", "发票号"],
      ["orderId", "订单ID", "number"],
      ["title", "抬头"],
      ["taxNo", "税号"],
      ["amount", "金额", "number"],
      ["status", "状态"],
      ["issuedAt", "开票时间"],
    ],
    columns: ["invoiceNo", "orderId", "title", "amount", "status"],
    actions: [["开票", "POST", (id) => `/api/invoices/${id}/issue`]],
  },
  payments: {
    title: "收款/退款",
    endpoint: "/api/payments",
    fields: [
      ["paymentNo", "付款号"],
      ["orderId", "订单ID", "number"],
      ["amount", "金额", "number"],
      ["method", "方式"],
      ["status", "状态"],
      ["paidAt", "时间"],
    ],
    columns: ["paymentNo", "orderId", "amount", "method", "status"],
    actions: [["退款", "POST", (id) => `/api/payments/${id}/refund`]],
  },
  audits: {
    title: "审计",
    endpoint: "/api/audit-logs",
    readonly: true,
    fields: [
      ["operator", "操作人"],
      ["role", "角色"],
      ["action", "动作"],
      ["targetType", "对象"],
      ["targetId", "对象ID"],
      ["detail", "详情", "textarea"],
      ["createdAt", "时间"],
    ],
    columns: ["operator", "role", "action", "targetType", "createdAt"],
  },
  reports: { title: "报表", endpoint: "/api/reports", readonly: true },
};

const roleModules = {
  CUSTOMER: ["dashboard", "orders", "invoices", "payments", "audits"],
  CLERK: ["dashboard", "orders", "quotations", "jobTickets", "inventoryItems", "audits"],
  MANAGER: ["dashboard", "orders", "quotations", "jobTickets", "productionTasks", "inventoryItems", "audits", "reports"],
  OPS: ["dashboard", "orders", "productionTasks", "inventoryItems", "deliveryTasks", "audits", "reports"],
  FINANCE: ["dashboard", "orders", "invoices", "payments", "audits", "reports"],
  COURIER: ["dashboard", "orders", "deliveryTasks", "audits"],
  ADMIN: Object.keys(modules),
};

const state = {
  token: localStorage.getItem("printshop-token") || "",
  user: JSON.parse(localStorage.getItem("printshop-user") || "null"),
  module: "dashboard",
  records: [],
  selected: null,
  editing: false,
};

const el = {
  loginView: document.querySelector("#loginView"),
  appView: document.querySelector("#appView"),
  loginForm: document.querySelector("#loginForm"),
  username: document.querySelector("#username"),
  password: document.querySelector("#password"),
  currentUser: document.querySelector("#currentUser"),
  logoutBtn: document.querySelector("#logoutBtn"),
  pageTitle: document.querySelector("#pageTitle"),
  pageSubTitle: document.querySelector("#pageSubTitle"),
  metrics: document.querySelector("#metrics"),
  tableTitle: document.querySelector("#tableTitle"),
  tableWrap: document.querySelector("#tableWrap"),
  detailTitle: document.querySelector("#detailTitle"),
  recordActions: document.querySelector("#recordActions"),
  recordForm: document.querySelector("#recordForm"),
  timeline: document.querySelector("#timeline"),
  newBtn: document.querySelector("#newBtn"),
  refreshBtn: document.querySelector("#refreshBtn"),
  responseBox: document.querySelector("#responseBox"),
  lastMessage: document.querySelector("#lastMessage"),
};

function authHeader() {
  return state.token ? { Authorization: `Basic ${state.token}` } : {};
}

async function api(path, options = {}) {
  const isFormData = options.body instanceof FormData;
  const response = await fetch(path, {
    headers: {
      ...(isFormData ? {} : { "Content-Type": "application/json" }),
      ...authHeader(),
      ...(options.headers || {}),
    },
    ...options,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || `${response.status} ${response.statusText}`);
  }
  return Object.prototype.hasOwnProperty.call(data, "data") ? data.data : data;
}

function show(value, message = "已更新") {
  el.responseBox.textContent = JSON.stringify(value, null, 2);
  el.lastMessage.textContent = message;
}

function showError(error) {
  show({ error: error.message }, "操作失败");
}

function toggleAuth() {
  const loggedIn = Boolean(state.token && state.user);
  el.loginView.classList.toggle("hidden", loggedIn);
  el.appView.classList.toggle("hidden", !loggedIn);
  if (loggedIn) {
    el.currentUser.textContent = `${state.user.displayName} · ${state.user.role} · ${state.user.storeName}`;
    renderNavAccess();
  }
}

function allowedModules() {
  return roleModules[state.user?.role] || ["dashboard"];
}

function renderNavAccess() {
  const allowed = new Set(allowedModules());
  document.querySelectorAll(".nav button").forEach((button) => {
    button.hidden = !allowed.has(button.dataset.module);
  });
}

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
  renderForm();
  renderMetrics([]);
  show(data, `${config.title}已加载`);
}

async function renderDashboard() {
  const dashboard = await api("/api/me/dashboard");
  renderMetrics(dashboard.metrics || []);
  state.records = dashboard.orders || [];
  state.selected = state.records[0] || null;
  renderTable(["orderNo", "customerName", "productType", "status", "currentStep"], "最近订单");
  renderForm();
  renderTimeline(dashboard.audits || []);
  show(dashboard, "工作台已加载");
}

function renderMetrics(metrics) {
  el.metrics.innerHTML = "";
  for (const metric of metrics) {
    const card = document.createElement("article");
    card.className = "metric";
    card.innerHTML = `<span>${metric.label}</span><strong>${metric.value}</strong>`;
    el.metrics.appendChild(card);
  }
}

function renderTable(customColumns, customTitle) {
  const config = modules[state.module];
  const columns = customColumns || config.columns || [];
  el.tableTitle.textContent = customTitle || config.title;
  if (!state.records.length) {
    el.tableWrap.innerHTML = "<p class=\"empty\">暂无数据</p>";
    return;
  }
  const rows = state.records.map((record) => `
    <tr data-id="${record.id}" class="${state.selected?.id === record.id ? "selected" : ""}">
      ${columns.map((column) => `<td>${format(record[column])}</td>`).join("")}
    </tr>
  `).join("");
  el.tableWrap.innerHTML = `
    <table>
      <thead><tr>${columns.map((column) => `<th>${column}</th>`).join("")}</tr></thead>
      <tbody>${rows}</tbody>
    </table>
  `;
}

function renderForm() {
  const config = modules[state.module] || modules.orders;
  const record = state.editing ? (state.selected || {}) : (state.selected || {});
  el.detailTitle.textContent = state.selected?.id ? `${config.title} #${state.selected.id}` : `${config.title}详情`;
  el.recordForm.innerHTML = "";
  el.recordActions.innerHTML = "";
  if (state.module === "reports") {
    el.recordForm.innerHTML = "<p>报表结果见最近响应。</p>";
    return;
  }
  for (const [field, label, type] of config.fields || []) {
    const node = type === "textarea" ? document.createElement("textarea") : document.createElement("input");
    node.name = field;
    node.value = record[field] ?? "";
    if (type === "number") {
      node.type = "number";
      node.step = "0.01";
    }
    node.disabled = config.readonly || !state.editing;
    const wrapper = document.createElement("label");
    if (type === "textarea") {
      wrapper.className = "wide";
    }
    wrapper.textContent = label;
    wrapper.appendChild(node);
    el.recordForm.appendChild(wrapper);
  }
  if (!config.readonly) {
    addAction(state.editing ? "保存" : "编辑", state.editing ? saveRecord : () => {
      state.editing = true;
      renderForm();
    }, "primary");
    if (state.editing) {
      addAction("取消", () => {
        state.editing = false;
        renderForm();
      });
    }
    if (state.selected?.id && !state.editing) {
      addAction("删除", deleteRecord, "danger");
    }
  }
  for (const [label, method, pathFactory, body] of config.actions || []) {
    if (state.selected?.id && !state.editing) {
      addAction(label, () => runRecordAction(method, pathFactory(state.selected.id), body || {}));
    }
  }
  if (state.module === "orders" && state.selected?.id && !state.editing) {
    addAction("上传文件", uploadOrderFile);
    addAction("查看文件", loadOrderFiles);
  }
}

function renderTimeline(audits = []) {
  el.timeline.innerHTML = audits.slice(0, 8).map((item) => `
    <article>
      <strong>${item.action || "-"}</strong>
      <span>${item.operator || "-"} · ${item.role || "-"} · ${format(item.createdAt)}</span>
      <p>${item.detail || ""}</p>
    </article>
  `).join("");
}

function addAction(label, handler, cls = "") {
  const button = document.createElement("button");
  button.type = "button";
  button.textContent = label;
  button.className = cls;
  button.addEventListener("click", handler);
  el.recordActions.appendChild(button);
}

function readForm() {
  const payload = {};
  for (const input of el.recordForm.querySelectorAll("input, textarea")) {
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

async function runRecordAction(method, path, body) {
  const data = await api(path, { method, body: JSON.stringify(body) });
  await loadModule(state.module);
  show(data, "状态动作完成");
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

function format(value) {
  if (value === null || value === undefined) return "-";
  if (typeof value === "number") return Number.isInteger(value) ? String(value) : value.toFixed(2);
  return String(value);
}

el.loginForm.addEventListener("submit", login);
el.logoutBtn.addEventListener("click", logout);
el.refreshBtn.addEventListener("click", () => loadModule().catch(showError));
el.newBtn.addEventListener("click", () => {
  state.selected = {};
  state.editing = true;
  renderForm();
});

document.querySelectorAll(".nav button").forEach((button) => {
  button.addEventListener("click", () => loadModule(button.dataset.module).catch(showError));
});

document.body.addEventListener("click", (event) => {
  const row = event.target.closest("tr[data-id]");
  if (!row) return;
  const id = Number(row.dataset.id);
  state.selected = state.records.find((record) => record.id === id) || null;
  state.editing = false;
  renderTable();
  renderForm();
});

toggleAuth();
if (state.token && state.user) {
  loadModule("dashboard").catch((error) => {
    logout();
    showError(error);
  });
}
