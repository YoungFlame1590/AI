import { el, state } from "./state.js";

export function authHeader() {
  return state.token ? { Authorization: `Basic ${state.token}` } : {};
}

export async function api(path, options = {}) {
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

export function show(value, message = "已更新") {
  el.responseBox.textContent = JSON.stringify(value, null, 2);
  el.lastMessage.textContent = message;
}

export function showError(error) {
  show({ error: error.message }, "操作失败");
}
