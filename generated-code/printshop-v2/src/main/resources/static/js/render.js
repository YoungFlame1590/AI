import { actionRoles, dashboardOrderColumns, modules, roleModules } from "./config.js";
import { showError } from "./api.js";
import { isFieldDisabled, normalizeOptionValue, updateOrderAmountPreview } from "./orders.js";
import { el, state } from "./state.js";

export function toggleAuth() {
  const loggedIn = Boolean(state.token && state.user);
  el.loginView.classList.toggle("hidden", loggedIn);
  el.appView.classList.toggle("hidden", !loggedIn);
  if (loggedIn) {
    el.currentUser.textContent = `${state.user.displayName} · ${state.user.role} · ${state.user.storeName}`;
    el.clearDataBtn.hidden = state.user.role !== "ADMIN";
    renderNavAccess();
  } else {
    el.clearDataBtn.hidden = true;
  }
}

export function allowedModules() {
  return roleModules[state.user?.role] || ["dashboard"];
}

export function renderNavAccess() {
  const allowed = new Set(allowedModules());
  document.querySelectorAll(".nav button").forEach((button) => {
    button.hidden = !allowed.has(button.dataset.module);
  });
}

export function activeRecordConfig() {
  if (state.module === "dashboard") {
    return { ...modules.orders, title: "订单", readonly: true, actions: [] };
  }
  if (state.module === "deliveryTasks" && state.user?.role === "COURIER") {
    return { ...modules.deliveryTasks, readonly: true };
  }
  return modules[state.module] || modules.orders;
}

export function renderMetrics(metrics) {
  el.metrics.innerHTML = "";
  for (const metric of metrics) {
    const card = document.createElement("article");
    card.className = "metric";
    card.innerHTML = `<span>${metric.label}</span><strong>${metric.value}</strong>`;
    el.metrics.appendChild(card);
  }
}

export function renderTable(customColumns, customTitle) {
  const config = activeRecordConfig();
  const columns = customColumns || (state.module === "dashboard" ? dashboardOrderColumns : config.columns) || [];
  el.tableTitle.textContent = customTitle || (state.module === "dashboard" ? "最近订单" : config.title);
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

export function renderForm(handlers) {
  if (state.module === "dashboard" && state.aggregate) {
    renderAggregateDetail(handlers);
    return;
  }
  if (state.module === "dashboard" && !state.aggregate) {
    el.detailTitle.textContent = "任务详情";
    el.recordActions.innerHTML = "";
    el.recordForm.innerHTML = "<p class=\"empty wide\">暂无待办任务。可从“订单”模块新增订单，或刷新工作台。</p>";
    renderTimeline([]);
    return;
  }
  const config = activeRecordConfig();
  const record = state.selected || {};
  el.detailTitle.textContent = state.selected?.id ? `${config.title} #${state.selected.id}` : `${config.title}详情`;
  el.recordForm.innerHTML = "";
  el.recordActions.innerHTML = "";
  if (state.module === "reports") {
    el.recordForm.innerHTML = "<p>报表结果见最近响应。</p>";
    return;
  }
  for (const [field, label, type, options] of config.fields || []) {
    const node = createFieldNode(field, type, options, record[field]);
    node.name = field;
    node.disabled = isFieldDisabled(config, record, field);
    const wrapper = document.createElement("label");
    if (type === "textarea") {
      wrapper.className = "wide";
    }
    wrapper.textContent = label;
    wrapper.appendChild(node);
    el.recordForm.appendChild(wrapper);
  }
  if (state.module === "orders" && state.editing) {
    updateOrderAmountPreview();
  }
  if (!config.readonly) {
    addAction(state.editing ? "保存" : "编辑", state.editing ? handlers.saveRecord : () => {
      state.editing = true;
      renderForm(handlers);
    }, "primary");
    if (state.editing) {
      addAction("取消", () => {
        state.editing = false;
        renderForm(handlers);
      });
    }
    if (state.selected?.id && !state.editing) {
      addAction("删除", handlers.deleteRecord, "danger");
    }
  }
  const showManagementActions = state.module !== "orders" || state.user?.role === "ADMIN";
  for (const [label, method, pathFactory, body, actionKey] of showManagementActions ? (config.actions || []) : []) {
    if (state.selected?.id && !state.editing) {
      if (actionKey && !(actionRoles[actionKey] || []).includes(state.user?.role)) {
        continue;
      }
      addAction(label, () => handlers.runRecordAction(method, pathFactory(state.selected.id), body || {}));
    }
  }
  if (state.module === "orders" && state.selected?.id && !state.editing && state.user?.role !== "COURIER") {
    addAction("上传文件", handlers.uploadOrderFile);
    addAction("查看文件", handlers.loadOrderFiles);
  }
}

export function renderAggregateDetail(handlers) {
  const aggregate = state.aggregate || {};
  const order = aggregate.order || {};
  const nextTask = (aggregate.nextTasks || [])[0];
  el.detailTitle.textContent = order.orderNo ? `订单聚合详情 · ${order.orderNo}` : "订单聚合详情";
  el.recordForm.innerHTML = `
    <section class="aggregate-summary wide">
      <div><span>客户</span><strong>${format(order.customerName)}</strong></div>
      <div><span>状态</span><strong>${format(order.status)}</strong></div>
      <div><span>付款</span><strong>${format(order.paymentStatus)}</strong></div>
      <div><span>金额</span><strong>${format(order.totalAmount)}</strong></div>
      <div><span>产品</span><strong>${format(order.productType)} / ${format(order.colorMode)}</strong></div>
      <div><span>交付</span><strong>${format(order.deliveryMode)} · ${format(order.priority)}</strong></div>
      <div class="wide"><span>下一步建议</span><strong>${nextTask ? `${format(nextTask.label)} · ${format(nextTask.hint)}` : "暂无待办，等待其他角色处理"}</strong></div>
    </section>
    ${aggregateSectionsForRole(aggregate)}
  `;
  el.recordActions.innerHTML = "";
  for (const action of aggregate.nextTasks || []) {
    addAction(action.label || action.action, () => handlers.runWorkflowAction(action.action, order.id), "primary");
  }
  if (state.selectedTask?.type === "CHANGE_REQUEST") {
    addAction("审批通过", () => handlers.runChangeTask("approve", state.selectedTask.changeRequestId), "primary");
    addAction("驳回", () => handlers.runChangeTask("reject", state.selectedTask.changeRequestId), "danger");
  }
  renderTimeline(aggregate.audits || []);
}

export function renderTimeline(audits = []) {
  if (["CUSTOMER", "COURIER"].includes(state.user?.role)) {
    el.timeline.innerHTML = "<p class=\"empty\">当前角色不显示审计记录</p>";
    return;
  }
  el.timeline.innerHTML = audits.slice(0, 8).map((item) => `
    <article>
      <strong>${item.action || "-"}</strong>
      <span>${item.operator || "-"} · ${item.role || "-"} · ${format(item.createdAt)}</span>
      <p>${item.detail || ""}</p>
    </article>
  `).join("");
}

export function format(value) {
  if (value === null || value === undefined) return "-";
  if (typeof value === "number") return Number.isInteger(value) ? String(value) : value.toFixed(2);
  return String(value);
}

function aggregateSection(title, items = [], columns = []) {
  if (!items.length) {
    return `<section class="aggregate-section wide"><h3>${title}</h3><p class="empty">暂无${title}记录</p></section>`;
  }
  return `
    <section class="aggregate-section wide">
      <h3>${title}</h3>
      <table>
        <thead><tr>${columns.map((column) => `<th>${column}</th>`).join("")}</tr></thead>
        <tbody>
          ${items.slice(0, 6).map((item) => `<tr>${columns.map((column) => `<td>${format(item[column])}</td>`).join("")}</tr>`).join("")}
        </tbody>
      </table>
    </section>
  `;
}

function aggregateSectionsForRole(aggregate) {
  const role = state.user?.role;
  const financeItems = [...(aggregate.payments || []), ...(aggregate.invoices || [])];
  const allSections = {
    progress: progressSection(aggregate),
    quotations: aggregateSection("报价", aggregate.quotations, ["quoteNo", "finalAmount", "status"]),
    jobTickets: aggregateSection("作业单", aggregate.jobTickets, ["ticketNo", "paperType", "binding", "status"]),
    productionTasks: aggregateSection("生产", aggregate.productionTasks, ["taskNo", "station", "status", "progressPercent"]),
    deliveryTasks: aggregateSection("配送", aggregate.deliveryTasks, ["taskNo", "mode", "carrierName", "status"]),
    finance: aggregateSection("财务", financeItems, ["paymentNo", "invoiceNo", "amount", "status"]),
    changeRequests: aggregateSection("变更", aggregate.changeRequests, ["requestNo", "requestedBy", "status", "amountDelta"]),
    files: aggregateSection("文件", aggregate.files, ["fileName", "fileStatus", "uploadedAt"]),
  };
  const visible = {
    CUSTOMER: ["progress", "files", "changeRequests", "finance"],
    CLERK: ["files", "quotations", "jobTickets", "changeRequests"],
    MANAGER: ["quotations", "changeRequests", "productionTasks", "progress"],
    OPS: ["productionTasks", "deliveryTasks", "progress"],
    FINANCE: ["finance", "changeRequests", "progress"],
    COURIER: ["deliveryTasks", "progress"],
    ADMIN: Object.keys(allSections),
  }[role] || ["progress"];
  return visible.map((key) => allSections[key]).join("");
}

function progressSection(aggregate) {
  const order = aggregate.order || {};
  const items = [
    { label: "当前步骤", value: order.currentStep },
    { label: "报价数", value: (aggregate.quotations || []).length },
    { label: "作业单数", value: (aggregate.jobTickets || []).length },
    { label: "生产任务数", value: (aggregate.productionTasks || []).length },
    { label: "配送任务数", value: (aggregate.deliveryTasks || []).length },
  ];
  return `
    <section class="aggregate-section wide">
      <h3>进度</h3>
      <table>
        <tbody>${items.map((item) => `<tr><th>${item.label}</th><td>${format(item.value)}</td></tr>`).join("")}</tbody>
      </table>
    </section>
  `;
}

function createFieldNode(field, type, options = [], value = "") {
  if (type === "textarea") {
    const node = document.createElement("textarea");
    node.value = value ?? "";
    return node;
  }
  if (type === "select") {
    const node = document.createElement("select");
    const normalizedValue = normalizeOptionValue(value);
    for (const option of options) {
      const optionNode = document.createElement("option");
      optionNode.value = option;
      optionNode.textContent = option;
      node.appendChild(optionNode);
    }
    node.value = options.includes(normalizedValue) ? normalizedValue : options[0];
    return node;
  }
  const node = document.createElement("input");
  node.value = value ?? "";
  if (type === "number") {
    node.type = "number";
    node.step = "0.01";
  }
  return node;
}

function addAction(label, handler, cls = "") {
  const button = document.createElement("button");
  button.type = "button";
  button.textContent = label;
  button.className = cls;
  button.addEventListener("click", () => {
    try {
      const result = handler();
      if (result && typeof result.catch === "function") {
        result.catch(showError);
      }
    } catch (error) {
      showError(error);
    }
  });
  el.recordActions.appendChild(button);
}
