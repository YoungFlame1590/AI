import { actionRoles, dashboardOrderColumns, modules, roleModules } from "./config.js";
import { showError } from "./api.js";
import { mountDesignEditor } from "./design-editor.js";
import { isFieldDisabled, normalizeOptionValue, updateOrderAmountPreview } from "./orders.js";
import { el, state } from "./state.js";

export function toggleAuth() {
  const loggedIn = Boolean(state.token && state.user);
  el.loginView.classList.toggle("hidden", loggedIn);
  el.appView.classList.toggle("hidden", !loggedIn);
  if (loggedIn) {
    el.currentUser.textContent = `${state.user.displayName} · ${state.user.role} · ${state.user.storeName}`;
    el.demoTestBtn.hidden = state.user.role !== "ADMIN";
    el.clearDataBtn.hidden = state.user.role !== "ADMIN";
    renderNavAccess();
  } else {
    el.demoTestBtn.hidden = true;
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
  if (state.module === "designTemplates" && !["OPS", "ADMIN"].includes(state.user?.role)) {
    return { ...modules.designTemplates, readonly: true };
  }
  return modules[state.module] || modules.orders;
}

export function renderMetrics(metrics) {
  el.metrics.innerHTML = "";
  for (const metric of metrics) {
    const card = document.createElement("article");
    card.className = "metric";
    card.innerHTML = `<span>${escapeHtml(metric.label)}</span><strong>${escapeHtml(format(metric.value))}</strong>`;
    el.metrics.appendChild(card);
  }
}

export function renderTable(customColumns, customTitle) {
  const config = activeRecordConfig();
  if (state.module === "reports") {
    renderReportIndex();
    return;
  }
  const columns = customColumns || (state.module === "dashboard" ? dashboardOrderColumns : config.columns) || [];
  el.tableTitle.textContent = customTitle || (state.module === "dashboard" ? "最近订单" : config.title);
  if (!state.records.length) {
    el.tableWrap.innerHTML = "<p class=\"empty\">暂无数据</p>";
    return;
  }
  const rows = state.records.map((record) => `
    <tr data-id="${escapeAttribute(record.id)}" class="${state.selected?.id === record.id ? "selected" : ""}">
      ${columns.map((column) => `<td class="${cellClass(column, record[column])}">${formatCell(record[column], column)}</td>`).join("")}
    </tr>
  `).join("");
  el.tableWrap.innerHTML = `
    <table>
      <thead><tr>${columns.map((column) => `<th>${escapeHtml(labelForColumn(column, config))}</th>`).join("")}</tr></thead>
      <tbody>${rows}</tbody>
    </table>
  `;
}

export function renderForm(handlers) {
  const workspace = document.querySelector(".workspace");
  workspace?.classList.toggle("reports-workspace", state.module === "reports");
  workspace?.classList.toggle("design-workspace", state.module === "designProjects");
  if (state.module === "orders" && state.creatingOrderFromFile) {
    renderInitialOrderUpload(handlers);
    return;
  }
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
    renderReportDetail(record);
    return;
  }
  if (state.module === "orderChangeRequests") {
    renderChangeRequestDetail(record);
    renderReadonlyActions(config, handlers);
    return;
  }
  if (state.module === "designProjects") {
    renderDesignProjectDetail(record, handlers);
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
    if (state.selected?.id && !state.editing && (state.module !== "users" || state.selected.active)) {
      addAction("删除", handlers.deleteRecord, "danger");
    }
  } else {
    renderReadonlyActions(config, handlers);
  }
  if (!config.readonly) {
    const showManagementActions = state.module !== "orders" || state.user?.role === "ADMIN";
    for (const [label, method, pathFactory, body, actionKey] of showManagementActions ? (config.actions || []) : []) {
      if (state.selected?.id && !state.editing) {
        if (actionKey && !(actionRoles[actionKey] || []).includes(state.user?.role)) {
          continue;
        }
        addAction(label, () => handlers.runRecordAction(method, pathFactory(state.selected.id), body || {}));
      }
    }
  }
  if (state.module === "orders" && state.selected?.id && !state.editing && state.user?.role !== "COURIER") {
    addAction("上传文件", handlers.uploadOrderFile);
    addAction("查看文件", handlers.loadOrderFiles);
  }
}

function renderDesignProjectDetail(record = {}, handlers) {
  renderTimeline([]);
  if (!state.selected) {
    el.detailTitle.textContent = "在线设计";
    el.recordActions.innerHTML = "";
    el.recordForm.innerHTML = "<p class=\"empty wide\">请先从模板市场创建一个设计项目。</p>";
    return;
  }
  const detail = state.designProjectDetail?.project?.id === record.id ? state.designProjectDetail : { project: record, template: null, versions: [] };
  el.detailTitle.textContent = `在线设计 · ${record.title || record.projectNo}`;
  el.recordForm.innerHTML = "";
  const mount = document.createElement("div");
  mount.className = "wide";
  el.recordForm.appendChild(mount);
  el.recordActions.innerHTML = "";
  mountDesignEditor(mount, detail.project, detail.template, detail.versions || [], {
    onSaveVersion: handlers.saveDesignVersion,
    onRestoreVersion: handlers.restoreDesignVersion,
    onSubmitOrder: handlers.submitDesignOrder,
  });
}

function renderInitialOrderUpload(handlers) {
  el.detailTitle.textContent = "新增订单";
  el.recordForm.innerHTML = `
    <section class="initial-order-upload wide">
      <label for="initialOrderFile">订单文件</label>
      <input
        id="initialOrderFile"
        name="initialOrderFile"
        type="file"
        accept=".pdf,.jpg,.jpeg,.png,.doc,.docx,.psd,.ai"
        required
      >
      <p>支持 PDF、图片、Word、PSD 和 AI，单个文件不超过 50MB。</p>
    </section>
  `;
  el.recordActions.innerHTML = "";
  addAction("上传并生成订单", handlers.createOrderFromFile, "primary");
  addAction("取消", () => {
    state.creatingOrderFromFile = false;
    state.selected = state.records[0] || null;
    renderTable();
    renderForm(handlers);
  });
}

export function renderAggregateDetail(handlers) {
  const aggregate = state.aggregate || {};
  const order = aggregate.order || {};
  const nextTask = (aggregate.nextTasks || [])[0];
  el.detailTitle.textContent = order.orderNo ? `订单聚合详情 · ${order.orderNo}` : "订单聚合详情";
  el.recordForm.innerHTML = `
    <section class="aggregate-summary wide">
      <div><span>客户</span><strong>${formatCell(order.customerName)}</strong></div>
      <div><span>状态</span><strong>${formatCell(order.status, "status")}</strong></div>
      <div><span>付款</span><strong>${formatCell(order.paymentStatus, "paymentStatus")}</strong></div>
      <div><span>金额</span><strong class="amount-cell">${formatCell(order.totalAmount, "totalAmount")}</strong></div>
      <div><span>产品</span><strong>${formatCell(order.productType)} / ${formatCell(order.colorMode)}</strong></div>
      <div><span>交付</span><strong>${formatCell(order.deliveryMode)} · ${formatCell(order.priority)}</strong></div>
      <div class="wide"><span>下一步建议</span><strong>${nextTask ? `${formatCell(nextTask.label)} · ${formatCell(nextTask.hint)}` : "暂无待办，等待其他角色处理"}</strong></div>
    </section>
    ${aggregateSectionsForRole(aggregate)}
  `;
  el.recordActions.innerHTML = "";
  for (const action of aggregate.nextTasks || []) {
    const handler = action.action === "UPLOAD_FILE"
      ? handlers.uploadOrderFile
      : () => handlers.runWorkflowAction(action.action, order.id);
    addAction(action.label || action.action, handler, "primary");
  }
  if (state.selectedTask?.type === "CHANGE_REQUEST") {
    addAction("审批通过", () => handlers.runChangeTask("approve", state.selectedTask.changeRequestId), "primary");
    addAction("驳回", () => handlers.runChangeTask("reject", state.selectedTask.changeRequestId), "danger");
  }
  if (state.selectedTask?.type === "SERVICE_REVIEW") {
    addAction("提交评价", () => handlers.runRecordAction("POST", state.selectedTask.path, "__serviceReviewFromTask"), "primary");
  }
  renderTimeline(aggregate.audits || []);
}

export function promptActionForm({ title, fields, initial = {}, submitLabel = "提交" }) {
  return new Promise((resolve) => {
    const dialog = document.createElement("dialog");
    dialog.className = "action-dialog";
    dialog.innerHTML = `
      <form method="dialog" class="action-dialog-form">
        <header>
          <h3>${escapeHtml(title || "填写信息")}</h3>
        </header>
        <div class="action-dialog-fields"></div>
        <footer>
          <button type="button" data-dialog-cancel>取消</button>
          <button type="submit" class="primary">${escapeHtml(submitLabel)}</button>
        </footer>
      </form>
    `;
    const fieldWrap = dialog.querySelector(".action-dialog-fields");
    for (const [field, label, type, options] of fields || []) {
      const node = createFieldNode(field, type, options, initial[field] ?? defaultActionFieldValue(type, options));
      node.name = field;
      if (type !== "textarea") {
        node.required = true;
      }
      const wrapper = document.createElement("label");
      if (type === "textarea") {
        wrapper.className = "wide";
      }
      wrapper.textContent = label;
      wrapper.appendChild(node);
      fieldWrap.appendChild(wrapper);
    }
    let resolved = false;
    const finish = (value) => {
      if (resolved) return;
      resolved = true;
      dialog.close();
      dialog.remove();
      resolve(value);
    };
    dialog.querySelector("[data-dialog-cancel]").addEventListener("click", () => finish(null));
    dialog.addEventListener("cancel", (event) => {
      event.preventDefault();
      finish(null);
    });
    dialog.querySelector("form").addEventListener("submit", (event) => {
      event.preventDefault();
      finish(readDialogForm(dialog));
    });
    document.body.appendChild(dialog);
    dialog.showModal();
    dialog.querySelector("select, input, textarea")?.focus();
  });
}

function readDialogForm(dialog) {
  const payload = {};
  for (const input of dialog.querySelectorAll("input, textarea, select")) {
    const value = input.value.trim();
    if (value === "") continue;
    payload[input.name] = input.type === "number" ? Number(value) : value;
  }
  return payload;
}

function defaultActionFieldValue(type, options = []) {
  if (type === "select") {
    return options[options.length - 1] || "";
  }
  return "";
}

export function renderTimeline(audits = []) {
  if (["CUSTOMER", "COURIER"].includes(state.user?.role)) {
    el.timeline.innerHTML = "<p class=\"empty\">当前角色不显示审计记录</p>";
    return;
  }
  el.timeline.innerHTML = audits.slice(0, 8).map((item) => `
    <article>
      <strong>${formatCell(item.action)}</strong>
      <span>${formatCell(item.operator)} · ${formatCell(item.role)} · ${formatCell(item.createdAt)}</span>
      <p>${formatCell(item.detail)}</p>
    </article>
  `).join("");
}

export function format(value) {
  if (value === null || value === undefined) return "-";
  if (typeof value === "object") return JSON.stringify(value);
  if (typeof value === "number") return Number.isInteger(value) ? String(value) : value.toFixed(2);
  return String(value);
}

function renderReportIndex() {
  const report = state.records[0] || {};
  const operations = report.operations || {};
  const finance = report.finance || {};
  const serviceQuality = report.serviceQuality || {};
  const lowStock = report.lowStock || [];
  const productionLoad = report.productionLoad || [];
  const rows = [
    ["订单总数", operations.totalOrders ?? 0],
    ["已完成订单", operations.completedOrders ?? 0],
    ["活跃订单", operations.activeOrders ?? 0],
    ["收款笔数", finance.paymentCount ?? 0],
    ["已开票数", finance.issuedInvoiceCount ?? 0],
    ["平均评分", serviceQuality.averageRating ?? 0],
    ["未关闭客诉", serviceQuality.complaintOpenCount ?? 0],
    ["低库存物料", Array.isArray(lowStock) ? lowStock.length : 0],
    ["生产任务", Array.isArray(productionLoad) ? productionLoad.length : 0],
  ];
  el.tableTitle.textContent = "报表摘要";
  el.tableWrap.innerHTML = `
    <table>
      <thead><tr><th>指标</th><th>数值</th></tr></thead>
      <tbody>${rows.map(([label, value]) => `<tr><td>${escapeHtml(label)}</td><td>${formatCell(value)}</td></tr>`).join("")}</tbody>
    </table>
  `;
}

function renderReportDetail(report = {}) {
  el.detailTitle.textContent = "经营报表详情";
  el.recordActions.innerHTML = "";
  const operations = report.operations || {};
  const finance = report.finance || {};
  const serviceQuality = report.serviceQuality || {};
  const lowStock = Array.isArray(report.lowStock) ? report.lowStock : [];
  const productionLoad = Array.isArray(report.productionLoad) ? report.productionLoad : [];
  const purchaseSuggestions = Array.isArray(report.purchaseSuggestions) ? report.purchaseSuggestions : [];
  const storeQualityRanking = Array.isArray(report.storeQualityRanking) ? report.storeQualityRanking : [];
  const financeSummary = [
    ["收款金额", finance.paidAmount ?? 0, "paidAmount"],
    ["退款金额", finance.refundAmount ?? 0, "refundAmount"],
    ["收款笔数", finance.paymentCount ?? 0, "paymentCount"],
    ["发票数量", finance.invoiceCount ?? 0, "invoiceCount"],
    ["已开票数", finance.issuedInvoiceCount ?? 0, "issuedInvoiceCount"],
  ];
  const operationsSummary = [
    ["订单总数", operations.totalOrders ?? 0, "totalOrders"],
    ["已完成订单", operations.completedOrders ?? 0, "completedOrders"],
    ["活跃订单", operations.activeOrders ?? 0, "activeOrders"],
  ];
  el.recordForm.innerHTML = `
    <section class="report-dashboard wide">
      <div class="aggregate-summary report-summary">
        <div><span>订单总数</span><strong>${formatCell(operations.totalOrders ?? 0)}</strong></div>
        <div><span>已完成</span><strong>${formatCell(operations.completedOrders ?? 0)}</strong></div>
        <div><span>活跃订单</span><strong>${formatCell(operations.activeOrders ?? 0)}</strong></div>
        <div><span>收款金额</span><strong class="amount-cell">${formatCell(finance.paidAmount ?? 0, "paidAmount")}</strong></div>
        <div><span>退款金额</span><strong class="amount-cell">${formatCell(finance.refundAmount ?? 0, "refundAmount")}</strong></div>
        <div><span>已开票</span><strong>${formatCell(finance.issuedInvoiceCount ?? 0)}</strong></div>
        <div><span>平均评分</span><strong>${formatCell(serviceQuality.averageRating ?? 0)}</strong></div>
        <div><span>未关闭客诉</span><strong>${formatCell(serviceQuality.complaintOpenCount ?? 0)}</strong></div>
      </div>
      <div class="report-section-grid">
        ${reportObjectTable("订单漏斗", report.orderFunnel, ["状态", "数量"])}
        ${reportMetricList("财务摘要", financeSummary)}
        ${reportMetricList("运营摘要", operationsSummary)}
      </div>
      <div class="report-section-grid report-section-grid-wide">
        ${storeSummaryTable(report.storeSummary)}
        ${reportArrayTable("生产负载", productionLoad, ["taskNo", "station", "operatorName", "status", "progressPercent"])}
        ${reportArrayTable("低库存预警", lowStock, ["sku", "itemName", "category", "quantity", "safetyStock"])}
        ${reportObjectTable("服务质量", serviceQuality.storeRatings || {}, ["门店", "评分"])}
        ${reportArrayTable("门店服务排行", storeQualityRanking, ["storeName", "reviewCount", "averageRating", "negativeRate", "averageComplaintHours", "overdueOpenCount"])}
        ${reportArrayTable("采购建议", purchaseSuggestions, ["suggestionNo", "sku", "recommendedQuantity", "estimatedCost", "status"])}
      </div>
    </section>
  `;
  renderTimeline([]);
}

function reportMetricList(title, rows = []) {
  if (!rows.length) {
    return `<section class="aggregate-section report-card"><h3>${escapeHtml(title)}</h3><p class="empty">暂无${escapeHtml(title)}数据</p></section>`;
  }
  return `
    <section class="aggregate-section report-card">
      <h3>${escapeHtml(title)}</h3>
      <div class="report-metric-list">
        ${rows.map(([label, value, column]) => `
          <div>
            <span>${escapeHtml(label)}</span>
            <strong>${formatCell(value, column)}</strong>
          </div>
        `).join("")}
      </div>
    </section>
  `;
}

function reportObjectTable(title, object = {}, headers = ["项目", "数值"]) {
  const entries = Object.entries(object || {});
  if (!entries.length) {
    return `<section class="aggregate-section wide"><h3>${escapeHtml(title)}</h3><p class="empty">暂无${escapeHtml(title)}数据</p></section>`;
  }
  return `
    <section class="aggregate-section report-card">
      <h3>${escapeHtml(title)}</h3>
      <table>
        <thead><tr><th>${escapeHtml(headers[0])}</th><th>${escapeHtml(headers[1])}</th></tr></thead>
        <tbody>${entries.map(([key, value]) => `<tr><td>${headers[0] === "状态" ? formatCell(key, "status") : escapeHtml(key)}</td><td>${formatCell(value, key)}</td></tr>`).join("")}</tbody>
      </table>
    </section>
  `;
}

function storeSummaryTable(summary = {}) {
  const entries = Object.entries(summary || {});
  if (!entries.length) {
    return `<section class="aggregate-section wide"><h3>门店汇总</h3><p class="empty">暂无门店汇总数据</p></section>`;
  }
  return `
    <section class="aggregate-section report-card">
      <h3>门店汇总</h3>
      <table>
        <thead><tr><th>门店</th><th>订单数</th><th>完成数</th><th>报价金额</th></tr></thead>
        <tbody>
          ${entries.map(([storeName, stats]) => `
            <tr>
              <td>${escapeHtml(storeName)}</td>
              <td>${formatCell(stats.orderCount ?? 0)}</td>
              <td>${formatCell(stats.completedCount ?? 0)}</td>
              <td class="cell-amount">${formatCell(stats.quotedAmount ?? 0, "quotedAmount")}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </section>
  `;
}

function reportArrayTable(title, items = [], columns = []) {
  if (!items.length) {
    return `<section class="aggregate-section wide"><h3>${escapeHtml(title)}</h3><p class="empty">暂无${escapeHtml(title)}数据</p></section>`;
  }
  return `
    <section class="aggregate-section report-card">
      <h3>${escapeHtml(title)}</h3>
      <table>
        <thead><tr>${columns.map((column) => `<th>${escapeHtml(labelForColumn(column))}</th>`).join("")}</tr></thead>
        <tbody>${items.slice(0, 8).map((item) => `<tr>${columns.map((column) => `<td class="${cellClass(column, item[column])}">${formatCell(item[column], column)}</td>`).join("")}</tr>`).join("")}</tbody>
      </table>
    </section>
  `;
}

function aggregateSection(title, items = [], columns = []) {
  if (!items.length) {
    return `<section class="aggregate-section wide"><h3>${escapeHtml(title)}</h3><p class="empty">暂无${escapeHtml(title)}记录</p></section>`;
  }
  return `
    <section class="aggregate-section wide">
      <h3>${escapeHtml(title)}</h3>
      <table>
        <thead><tr>${columns.map((column) => `<th>${escapeHtml(labelForColumn(column))}</th>`).join("")}</tr></thead>
        <tbody>
          ${items.slice(0, 6).map((item) => `<tr>${columns.map((column) => `<td class="${cellClass(column, item[column])}">${formatCell(item[column], column)}</td>`).join("")}</tr>`).join("")}
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
    files: fileListSection(aggregate.files),
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

function fileListSection(files = []) {
  if (!files.length) {
    return `<section class="aggregate-section wide"><h3>文件</h3><p class="empty">暂无文件记录</p></section>`;
  }
  return `
    <section class="aggregate-section wide">
      <h3>文件</h3>
      <div class="file-record-list">
        ${files.slice(0, 8).map(fileRecord).join("")}
      </div>
    </section>
  `;
}

function fileRecord(file) {
  return `
    <article class="file-record">
      <header class="file-record-head">
        <span class="file-version">V${escapeHtml(format(file.versionNo))}</span>
        <strong title="${escapeAttribute(file.fileName)}">${formatCell(file.fileName)}</strong>
        ${formatCell(file.reviewStatus, "reviewStatus")}
      </header>
      <div class="file-meta">
        <div><span>文件类型</span><strong>${formatCell(file.contentType)}</strong></div>
        <div><span>大小</span><strong>${formatBytes(file.sizeBytes)}</strong></div>
        <div><span>上传人</span><strong>${formatCell(file.uploadedBy)}</strong></div>
        <div><span>上传时间</span><strong>${formatDateTime(file.uploadedAt)}</strong></div>
      </div>
      ${fileAnalysis(file)}
      <footer class="file-actions">
        <button type="button" data-file-preview="${escapeAttribute(file.id)}">预览</button>
        <button type="button" data-file-download="${escapeAttribute(file.id)}">下载</button>
      </footer>
    </article>
  `;
}

function fileAnalysis(file) {
  const pageSize = file.detectedWidthMm && file.detectedHeightMm
    ? `${format(file.detectedWidthMm)} x ${format(file.detectedHeightMm)} mm`
    : "-";
  const pixels = file.detectedPixelWidth && file.detectedPixelHeight
    ? `${format(file.detectedPixelWidth)} x ${format(file.detectedPixelHeight)} px`
    : "-";
  const dpi = file.detectedDpiX && file.detectedDpiY
    ? `${format(file.detectedDpiX)} x ${format(file.detectedDpiY)} DPI`
    : "-";
  return `
    <section class="file-analysis">
      <header>
        <span>自动识别</span>
        ${formatCell(file.analysisStatus || "PENDING", "analysisStatus")}
      </header>
      <div class="file-analysis-grid">
        <div><span>页数</span><strong>${formatCell(file.detectedPageCount)}</strong></div>
        <div><span>页面尺寸</span><strong>${escapeHtml(pageSize)}</strong></div>
        <div><span>像素尺寸</span><strong>${escapeHtml(pixels)}</strong></div>
        <div><span>DPI</span><strong>${escapeHtml(dpi)}</strong></div>
      </div>
      ${file.mixedPageSizes ? "<p class=\"analysis-warning\">检测到混合页面尺寸，请人工确认生产规格。</p>" : ""}
      <p>${formatCell(file.analysisMessage || "等待文件分析")}</p>
    </section>
  `;
}

function formatBytes(value) {
  const bytes = Number(value || 0);
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${bytes} B`;
}

function formatDateTime(value) {
  if (!value) return "-";
  return escapeHtml(String(value).replace("T", " ").slice(0, 19));
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
        <tbody>${items.map((item) => `<tr><th>${escapeHtml(item.label)}</th><td>${formatCell(item.value)}</td></tr>`).join("")}</tbody>
      </table>
    </section>
  `;
}

function renderChangeRequestDetail(record = {}) {
  renderTimeline([]);
  if (!state.selected) {
    el.recordForm.innerHTML = "<p class=\"empty wide\">暂无订单变更记录</p>";
    return;
  }
  const compareItems = [
    ["产品类型", "oldProductType", "newProductType"],
    ["颜色/工艺", "oldColorMode", "newColorMode"],
    ["页数", "oldPageCount", "newPageCount"],
    ["份数", "oldCopies", "newCopies"],
    ["交付方式", "oldDeliveryMode", "newDeliveryMode"],
    ["优先级", "oldPriority", "newPriority"],
    ["金额", "oldAmount", "newAmount", "amountDelta"],
  ];
  el.recordForm.innerHTML = `
    <section class="aggregate-summary wide">
      <div><span>变更编号</span><strong>${formatCell(record.requestNo)}</strong></div>
      <div><span>订单号</span><strong>${formatCell(record.orderNo)}</strong></div>
      <div><span>发起人</span><strong>${formatCell(record.requestedBy)} · ${formatCell(record.requesterRole)}</strong></div>
      <div><span>状态</span><strong>${formatCell(record.status, "status")}</strong></div>
      <div><span>金额差异</span><strong class="amount-cell">${formatCell(record.amountDelta, "amountDelta")}</strong></div>
      <div><span>审批人</span><strong>${formatCell(record.approvedBy)}</strong></div>
    </section>
    <section class="aggregate-section change-compare wide">
      <h3>变更对比</h3>
      <div class="change-compare-grid">
        ${compareItems.map(([label, oldField, newField, deltaField]) => changeCompareItem(label, record[oldField], record[newField], deltaField ? record[deltaField] : null, newField)).join("")}
      </div>
    </section>
    <section class="aggregate-section wide">
      <h3>说明与审批</h3>
      <div class="change-compare">
        <div class="change-note"><span>变更原因</span><strong>${formatCell(record.reason)}</strong></div>
        <div class="change-note"><span>审批意见</span><strong>${formatCell(record.decisionComment)}</strong></div>
      </div>
    </section>
  `;
}

function renderReadonlyActions(config, handlers) {
  for (const [label, method, pathFactory, body, actionKey] of config.actions || []) {
    const canRunWithoutSelection = typeof pathFactory === "function" && pathFactory.length === 0;
    if ((state.selected?.id || canRunWithoutSelection) && !state.editing) {
      if (actionKey && !(actionRoles[actionKey] || []).includes(state.user?.role)) {
        continue;
      }
      addAction(label, () => handlers.runRecordAction(method, pathFactory(state.selected?.id), body || {}));
    }
  }
}

function changeCompareItem(label, oldValue, newValue, deltaValue, column) {
  const delta = deltaValue === null || deltaValue === undefined
    ? ""
    : `<span>差异</span><strong class="amount-cell">${formatCell(deltaValue, "amountDelta")}</strong>`;
  return `
    <div class="change-compare-item">
      <span>${escapeHtml(label)}</span>
      <strong class="old">${formatCell(oldValue, column)}</strong>
      <strong class="new">${formatCell(newValue, column)}</strong>
      ${delta}
    </div>
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
    const normalizedValue = String(normalizeOptionValue(value));
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
    node.step = ["pageCount", "copies"].includes(field) ? "1" : "0.01";
    if (["pageCount", "copies"].includes(field)) {
      node.min = "1";
      node.max = field === "pageCount" ? "5000" : "10000";
    }
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

function labelForColumn(column, config = null) {
  const field = (config?.fields || []).find(([name]) => name === column);
  if (field) return field[1];
  for (const moduleConfig of Object.values(modules)) {
    const moduleField = (moduleConfig.fields || []).find(([name]) => name === column);
    if (moduleField) return moduleField[1];
  }
  const labels = {
    actionLabel: "待办动作",
    amountDelta: "金额差异",
    averageRating: "平均评分",
    carrierName: "承运人",
    channelName: "配送渠道",
    currentStep: "当前步骤",
    customerName: "客户",
    deliveryFee: "配送费",
    dynamicSafetyStock: "动态安全库存",
    estimatedCost: "预计成本",
    estimatedFee: "预估费用",
    forecastNext30Days: "未来30天预测",
    externalStatus: "外部状态",
    contentType: "文件类型",
    fileName: "文件名",
    fileActions: "文件操作",
    fileStatus: "文件状态",
    finalAmount: "最终金额",
    invoiceNo: "发票号",
    itemName: "物料名称",
    orderId: "订单ID",
    orderNo: "订单号",
    paidAmount: "收款金额",
    paymentNo: "付款号",
    progressPercent: "进度%",
    qualityStatus: "质检状态",
    quoteNo: "报价号",
    quotedAmount: "报价金额",
    refundAmount: "退款金额",
    requestNo: "变更编号",
    requestedBy: "发起人",
    requesterRole: "发起角色",
    sizeBytes: "大小",
    station: "工位/设备",
    status: "状态",
    taskNo: "任务号",
    ticketNo: "作业单号",
    title: "任务",
    uploadedAt: "上传时间",
    uploadedBy: "上传人",
    uploadedRole: "上传角色",
    versionNo: "版本",
    reviewStatus: "审核状态",
    recommendedQuantity: "建议数量",
    suggestionNo: "采购建议号",
    averageComplaintHours: "平均处理小时",
    negativeRate: "差评率",
    overdueOpenCount: "超时未处理",
    trackingNo: "运单号",
  };
  return labels[column] || column;
}

function formatCell(value, column = "") {
  const display = format(value);
  const safe = escapeHtml(display);
  if (isStatusColumn(column)) {
    return `<span class="status-pill ${statusClass(display)}">${safe}</span>`;
  }
  if (column === "progressPercent") {
    const progress = Math.max(0, Math.min(100, Number(value) || 0));
    return `<span class="progress-cell" style="--progress: ${progress}%"><span>${safe}%</span></span>`;
  }
  if (isAmountColumn(column)) {
    return `<span class="amount-cell">${safe}</span>`;
  }
  return safe;
}

function cellClass(column, value) {
  const classes = [];
  if (typeof value === "number") classes.push("cell-number");
  if (isAmountColumn(column)) classes.push("cell-amount");
  if (column === "progressPercent") classes.push("cell-progress");
  return classes.join(" ");
}

function isStatusColumn(column) {
  return /(^status$|Status$|status$)/.test(column);
}

function isAmountColumn(column) {
  return /(amount|delta|total|subtotal|paid|refund|final|quoted)/i.test(column);
}

function statusClass(value) {
  const status = String(value || "").toUpperCase();
  if (/(DONE|PAID|APPROVED|CONFIRMED|COMPLETED|SIGNED|ISSUED|PASS)/.test(status)) {
    return "status-good";
  }
  if (/(CANCELLED|REJECTED|REFUNDED|FAILED|VOID)/.test(status)) {
    return "status-bad";
  }
  if (/(PENDING|SUBMITTED|REVIEWING|QUOTED|JOB_READY|UNPAID|PARTIAL)/.test(status)) {
    return "status-warn";
  }
  if (/(PRODUCTION|DELIVERING|ACTIVE|PROCESS|READY|ACCEPTED)/.test(status)) {
    return "status-active";
  }
  return "";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function escapeAttribute(value) {
  return escapeHtml(format(value));
}
