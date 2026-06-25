#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const APP_ROOT = path.resolve(__dirname, "..");
const REPO_ROOT = path.resolve(APP_ROOT, "..", "..");
const VAULT_ROOT = path.join(REPO_ROOT, "obsidian-vault");
const SUMMARY_DIR = path.join(VAULT_ROOT, "wiki", "summaries");
const CHANGE_DIR = path.join(SUMMARY_DIR, "变更管理");
const JAVA_ROOT = path.join(APP_ROOT, "src", "main", "java", "com", "printshop", "mis");
const STATIC_JS = path.join(APP_ROOT, "src", "main", "resources", "static", "js");
const MIGRATION = path.join(APP_ROOT, "src", "main", "resources", "db", "migration", "V5__order_change_requests.sql");
const OPENAPI = path.join(SUMMARY_DIR, "API契约", "OpenAPI-接口契约-v2.2.yaml");
const OUTPUT = path.join(CHANGE_DIR, "CRR变更回归校验报告-v2.0.md");

const REQUIRED_CHANGE_FILES = [
  "domain/OrderChangeRequest.java",
  "repository/OrderChangeRequestRepository.java",
  "order/OrderChangeRequestService.java",
  "order/OrderChangeGuard.java",
  "order/OrderChangeRequestController.java",
];

const REQUIRED_ENDPOINTS = [
  "GET /api/order-change-requests",
  "GET /api/order-change-requests/{id}",
  "POST /api/orders/{orderId}/change-requests",
  "POST /api/order-change-requests/{id}/approve",
  "POST /api/order-change-requests/{id}/reject",
  "GET /api/workbench/tasks",
  "GET /api/orders/{orderId}/aggregate",
  "POST /api/orders/{orderId}/workflow/actions/{action}",
];

const V1_MODULE_EVIDENCE = [
  ["身份权限", "identity/IdentityService.java"],
  ["订单", "order/OrderService.java"],
  ["报价", "quotation/QuotationService.java"],
  ["作业单", "job/JobTicketService.java"],
  ["生产", "production/ProductionTaskService.java"],
  ["库存", "inventory/InventoryService.java"],
  ["配送", "delivery/DeliveryService.java"],
  ["财务", "finance/FinanceService.java"],
  ["审计", "audit/AuditTrailService.java"],
  ["报表", "reporting/ReportingService.java"],
];

function rel(file) {
  return path.relative(REPO_ROOT, file).replace(/\\/g, "/");
}

function exists(file) {
  return fs.existsSync(file);
}

function read(file) {
  return fs.readFileSync(file, "utf8");
}

function walk(dir, predicate = () => true) {
  if (!exists(dir)) return [];
  const result = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) result.push(...walk(full, predicate));
    else if (predicate(full)) result.push(full);
  }
  return result;
}

function addIssue(issues, dimension, severity, target, description, suggestion) {
  issues.push({
    id: `CRR-${String(issues.length + 1).padStart(3, "0")}`,
    dimension,
    severity,
    target,
    description,
    suggestion,
  });
}

function extractEndpoints() {
  const javaFiles = walk(JAVA_ROOT, (file) => file.endsWith(".java"));
  const endpoints = [];
  for (const file of javaFiles) {
    const text = read(file);
    if (!file.endsWith("Controller.java")) continue;
    const base = (text.match(/@RequestMapping\("([^"]*)"\)/u) || [null, ""])[1];
    for (const match of text.matchAll(/@(Get|Post|Put|Delete)Mapping(?:\(path\s*=\s*"([^"]*)"|(?:\("([^"]*)"\))?)?/gu)) {
      const child = match[2] || match[3] || "";
      const apiPath = `/${[base, child].join("/").split("/").filter(Boolean).join("/")}`;
      endpoints.push(`${match[1].toUpperCase()} ${apiPath}`);
    }
  }
  return endpoints;
}

function detectIssues() {
  const issues = [];
  for (const file of REQUIRED_CHANGE_FILES) {
    const target = path.join(JAVA_ROOT, ...file.split("/"));
    if (!exists(target)) {
      addIssue(issues, "v2新能力覆盖", "高", rel(target), "缺少订单变更冻结核心代码文件。", "补齐 CR-001 对应实体、仓储、服务、守卫或控制器。");
    }
  }

  const migrationText = exists(MIGRATION) ? read(MIGRATION) : "";
  if (!/CREATE TABLE order_change_requests/u.test(migrationText)) {
    addIssue(issues, "数据结构", "高", rel(MIGRATION), "缺少订单变更请求表。", "新增 Flyway V5 建表脚本。");
  }

  const endpoints = extractEndpoints();
  for (const endpoint of REQUIRED_ENDPOINTS) {
    if (!endpoints.includes(endpoint)) {
      addIssue(issues, "接口契约", "高", endpoint, "Controller 未实现订单变更请求接口。", "补齐 OrderChangeRequestController 对应方法。");
    }
  }

  const openapi = exists(OPENAPI) ? read(OPENAPI) : "";
  for (const endpoint of REQUIRED_ENDPOINTS) {
    const apiPath = endpoint.replace(/^[A-Z]+\s+/u, "");
    if (!openapi.includes(`${apiPath}:`)) {
      addIssue(issues, "接口契约", "高", rel(OPENAPI), `${apiPath} 未写入 OpenAPI v2.2。`, "同步契约文件。");
    }
  }

  const javaText = walk(JAVA_ROOT, (file) => file.endsWith(".java")).map(read).join("\n");
  for (const [moduleName, file] of V1_MODULE_EVIDENCE) {
    const target = path.join(JAVA_ROOT, ...file.split("/"));
    if (!exists(target)) {
      addIssue(issues, "v1回归", "高", rel(target), `${moduleName} 模块在 v2 中缺失。`, "v2 必须在 v1 基础上增量演进。");
    }
  }
  for (const phrase of ["待审批变更", "生产/SLA", "CREATE_CHANGE_REQUEST", "APPROVE_CHANGE_REQUEST", "REJECT_CHANGE_REQUEST"]) {
    if (!javaText.includes(phrase)) {
      addIssue(issues, "变更闭环", "中", phrase, `代码中缺少 ${phrase} 证据。`, "补充冻结提示、审计动作或审批逻辑。");
    }
  }

  const frontend = walk(STATIC_JS, (file) => file.endsWith(".js")).map(read).join("\n");
  for (const phrase of ["orderChangeRequests", "申请变更", "审批通过", "驳回"]) {
    if (!frontend.includes(phrase)) {
      addIssue(issues, "前端可用性", "中", phrase, `前端缺少 ${phrase} 入口。`, "在角色工作台中补齐变更请求模块或按钮。");
    }
  }

  return issues;
}

function rows(issues) {
  if (issues.length === 0) return "| - | - | - | - | 未发现高严重度回归 | - |";
  return issues.map((issue) => `| ${issue.id} | ${issue.dimension} | ${issue.severity} | ${issue.target} | ${issue.description} | ${issue.suggestion} |`).join("\n");
}

function report(issues) {
  const high = issues.filter((issue) => issue.severity === "高").length;
  const medium = issues.filter((issue) => issue.severity === "中").length;
  return `# CRR变更回归校验报告-v2.0

## 校验对象

- 变更请求：CR-001 订单变更冻结
- 代码目录：\`generated-code/printshop-v2/\`
- 契约：\`obsidian-vault/wiki/summaries/API契约/OpenAPI-接口契约-v2.2.yaml\`
- 基线目标：BL-20260624-01

## 校验范围

| 检查类别 | 内容 |
|---|---|
| v2 新能力 | 订单变更请求实体、仓储、服务、守卫、控制器、前端入口 |
| 数据结构 | \`order_change_requests\` Flyway 建表脚本 |
| 接口契约 | 查询、创建、审批、驳回变更请求接口 |
| v1 回归 | 身份、订单、报价、作业单、生产、库存、配送、财务、审计、报表模块仍存在 |
| 闭环证据 | 待审批冻结、审批通过、驳回、审计留痕 |

## 问题清单

| 编号 | 维度 | 严重程度 | 对象 | 问题 | 建议 |
|---|---|---|---|---|---|
${rows(issues)}

## 结论

- 高严重度问题：${high}
- 中严重度提示：${medium}
- 门禁结论：${high === 0 ? "通过，CR-001 可随 BL-20260624-01 纳入新基线。" : "未通过，需先修复高严重度问题。"}

## 回归说明

v2 在 v1 准生产 Print MIS 的基础上增量增加订单变更冻结，不删除 v1 主流程。待审批变更会阻断排产、完工和配送；审批通过后订单字段与金额更新，审批驳回后订单保持原字段。审计日志保留创建、批准和驳回动作。
`;
}

function main() {
  const args = process.argv.slice(2);
  const writeMode = args.includes("--write");
  const checkMode = args.includes("--check");
  if (!writeMode && !checkMode) {
    console.log("Usage: node generated-code/printshop-v2/scripts/verify-change-regression.js [--write|--check]");
    process.exitCode = 1;
    return;
  }

  const issues = detectIssues();
  const content = report(issues);
  if (writeMode) {
    fs.mkdirSync(CHANGE_DIR, { recursive: true });
    fs.writeFileSync(OUTPUT, content, "utf8");
    console.log(`[write] ${rel(OUTPUT)}`);
  }
  if (checkMode) {
    if (!exists(OUTPUT)) {
      console.error(`[missing] ${rel(OUTPUT)}`);
      process.exitCode = 1;
      return;
    }
    if (read(OUTPUT) !== content) {
      console.error(`[stale] ${rel(OUTPUT)}`);
      process.exitCode = 1;
      return;
    }
  }
  const high = issues.filter((issue) => issue.severity === "高").length;
  console.log(`CRR check complete: ${issues.length} issue(s), high=${high}`);
  if (high > 0) process.exitCode = 1;
}

main();
