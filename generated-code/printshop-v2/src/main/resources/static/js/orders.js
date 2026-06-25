import { optionAliases, orderEditableFields, orderOptions, priceConfig } from "./config.js";
import { el, state } from "./state.js";

export function defaultRecordForModule(moduleName) {
  if (moduleName !== "orders") return {};
  const defaults = {
    customerName: state.user?.displayName || "",
    storeId: state.user?.storeId || "",
    productType: orderOptions.productType[0],
    colorMode: orderOptions.colorMode[0],
    pageCount: 1,
    copies: 1,
    dueAt: localDateTime(),
    deliveryMode: orderOptions.deliveryMode[0],
    priority: "普通",
    status: "SUBMITTED",
    paymentStatus: "UNPAID",
    currentStep: "新订单待提交",
  };
  defaults.totalAmount = estimateOrderAmount(defaults);
  return defaults;
}

export function normalizeOptionValue(value) {
  return optionAliases[value] || value;
}

export function localDateTime() {
  const date = new Date();
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function formOrderDraft() {
  const draft = { ...(state.selected || {}) };
  for (const input of el.recordForm.querySelectorAll("input, textarea, select")) {
    const raw = input.value.trim();
    draft[input.name] = input.type === "number" ? Number(raw || 0) : raw;
  }
  return draft;
}

export function estimateOrderAmount(order) {
  const productType = normalizeOptionValue(order.productType) || orderOptions.productType[0];
  const colorMode = normalizeOptionValue(order.colorMode) || orderOptions.colorMode[0];
  const deliveryMode = normalizeOptionValue(order.deliveryMode) || orderOptions.deliveryMode[0];
  const priority = normalizeOptionValue(order.priority) || "普通";
  const pageCount = Math.max(1, Math.trunc(Number(order.pageCount || 1)));
  const copies = Math.max(1, Math.trunc(Number(order.copies || 1)));
  const variable = pageCount * copies * (priceConfig.colorPageRate[colorMode] || 0);
  const base = (priceConfig.productBase[productType] || 0) + variable;
  const total = base * (priceConfig.priorityMultiplier[priority] || 1) + (priceConfig.deliveryFee[deliveryMode] || 0);
  return Math.round(total * 100) / 100;
}

export function updateOrderAmountPreview() {
  if (state.module !== "orders") return;
  const amountInput = el.recordForm.querySelector("[name='totalAmount']");
  if (!amountInput) return;
  amountInput.value = estimateOrderAmount(formOrderDraft()).toFixed(2);
}

export function isFieldDisabled(config, record, field) {
  if (config.readonly || !state.editing) return true;
  if (state.module !== "orders") return false;
  if (["orderNo", "customerName", "storeId", "totalAmount", "paidAmount"].includes(field)) return true;
  if (!state.selected?.id && ["dueAt", "status", "paymentStatus", "currentStep"].includes(field)) return true;
  if (state.user?.role === "CUSTOMER" && state.selected?.id && !["SUBMITTED", "REVIEWING"].includes(record.status)) {
    return true;
  }
  return !(orderEditableFields[state.user?.role] || []).includes(field);
}
