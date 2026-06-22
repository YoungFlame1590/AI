#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const APP_ROOT = path.resolve(__dirname, "..");
const REPO_ROOT = path.resolve(APP_ROOT, "..", "..");
const VAULT_ROOT = path.join(REPO_ROOT, "obsidian-vault");
const SUMMARIES_DIR = path.join(VAULT_ROOT, "wiki", "summaries");
const JAVA_ROOT = path.join(APP_ROOT, "src", "main", "java", "com", "printshop");
const MIS_ROOT = path.join(JAVA_ROOT, "mis");
const STATIC_ROOT = path.join(APP_ROOT, "src", "main", "resources", "static");
const MIGRATION = path.join(APP_ROOT, "src", "main", "resources", "db", "migration", "V1__print_mis_schema.sql");

const OUTPUTS = {
  rcr: path.join(SUMMARIES_DIR, "RCR逆向校验报告-v1.0.md"),
  quality: path.join(SUMMARIES_DIR, "模块设计质量校验-v1.0.md"),
  adr002: path.join(SUMMARIES_DIR, "ADR-002-模块拆分-v1.0.md"),
  adr003: path.join(SUMMARIES_DIR, "ADR-003-依赖拓扑-v1.0.md"),
  adr004: path.join(SUMMARIES_DIR, "ADR-004-横切审计与防腐层-v1.0.md"),
};

const OPENAPI = path.join(SUMMARIES_DIR, "API契约", "OpenAPI-接口契约-v2.0.yaml");

const REQUIRED_ENDPOINTS = [
  ["POST", "/api/auth/login"],
  ["GET", "/api/me"],
  ["GET", "/api/me/dashboard"],
  ["GET", "/api/stores"],
  ["GET", "/api/users"],
  ["GET", "/api/orders"],
  ["POST", "/api/orders"],
  ["GET", "/api/orders/{id}"],
  ["PUT", "/api/orders/{id}"],
  ["DELETE", "/api/orders/{id}"],
  ["POST", "/api/orders/{id}/status"],
  ["POST", "/api/orders/{id}/files"],
  ["GET", "/api/orders/{id}/files"],
  ["GET", "/api/quotations"],
  ["POST", "/api/quotations"],
  ["GET", "/api/quotations/{id}"],
  ["PUT", "/api/quotations/{id}"],
  ["DELETE", "/api/quotations/{id}"],
  ["POST", "/api/quotations/{id}/approve"],
  ["GET", "/api/job-tickets"],
  ["POST", "/api/job-tickets"],
  ["GET", "/api/job-tickets/{id}"],
  ["PUT", "/api/job-tickets/{id}"],
  ["DELETE", "/api/job-tickets/{id}"],
  ["GET", "/api/production-tasks"],
  ["POST", "/api/production-tasks"],
  ["GET", "/api/production-tasks/{id}"],
  ["PUT", "/api/production-tasks/{id}"],
  ["DELETE", "/api/production-tasks/{id}"],
  ["POST", "/api/production-tasks/{id}/complete"],
  ["GET", "/api/inventory-items"],
  ["POST", "/api/inventory-items"],
  ["GET", "/api/inventory-items/{id}"],
  ["PUT", "/api/inventory-items/{id}"],
  ["DELETE", "/api/inventory-items/{id}"],
  ["POST", "/api/inventory-items/{id}/adjust"],
  ["GET", "/api/delivery-tasks"],
  ["POST", "/api/delivery-tasks"],
  ["GET", "/api/delivery-tasks/{id}"],
  ["PUT", "/api/delivery-tasks/{id}"],
  ["DELETE", "/api/delivery-tasks/{id}"],
  ["POST", "/api/delivery-tasks/{id}/sign"],
  ["GET", "/api/invoices"],
  ["POST", "/api/invoices"],
  ["GET", "/api/invoices/{id}"],
  ["PUT", "/api/invoices/{id}"],
  ["DELETE", "/api/invoices/{id}"],
  ["POST", "/api/invoices/{id}/issue"],
  ["GET", "/api/payments"],
  ["POST", "/api/payments"],
  ["GET", "/api/payments/{id}"],
  ["PUT", "/api/payments/{id}"],
  ["DELETE", "/api/payments/{id}"],
  ["POST", "/api/payments/{id}/refund"],
  ["GET", "/api/audit-logs"],
  ["GET", "/api/audit-logs/{id}"],
  ["GET", "/api/reports"],
  ["GET", "/stats"],
];

const REQUIRED_ROLES = [
  ["customer", "CUSTOMER", "客户"],
  ["clerk", "CLERK", "门店店员"],
  ["manager", "MANAGER", "门店店长"],
  ["ops", "OPS", "总部运营管理员"],
  ["finance", "FINANCE", "财务人员"],
  ["courier", "COURIER", "配送/外协人员"],
  ["admin", "ADMIN", "系统管理员"],
];

const REQUIRED_TABLES = [
  "stores",
  "user_accounts",
  "print_orders",
  "order_files",
  "quotations",
  "job_tickets",
  "production_tasks",
  "inventory_items",
  "delivery_tasks",
  "invoices",
  "payments",
  "audit_logs",
];

const MODULE_EVIDENCE = [
  ["AUTH", ["SecurityConfig.java", "/api/auth/login", "user_accounts"]],
  ["ORD", ["PrintOrder.java", "/api/orders", "print_orders"]],
  ["FILE", ["OrderFile.java", "/api/orders/{id}/files", "order_files"]],
  ["QUO", ["Quotation.java", "/api/quotations", "quotations"]],
  ["JOB", ["JobTicket.java", "/api/job-tickets", "job_tickets"]],
  ["PRO", ["ProductionTask.java", "/api/production-tasks", "production_tasks"]],
  ["INV", ["InventoryItem.java", "/api/inventory-items", "inventory_items"]],
  ["DLV", ["DeliveryTask.java", "/api/delivery-tasks", "delivery_tasks"]],
  ["FIN", ["InvoiceRecord.java", "PaymentRecord.java", "/api/invoices", "/api/payments"]],
  ["AUD", ["AuditLogEntry.java", "/api/audit-logs", "audit_logs"]],
  ["RPT", ["/api/reports", "orderFunnel", "productionLoad"]],
];

function rel(filePath) {
  return path.relative(REPO_ROOT, filePath).replace(/\\/g, "/");
}

function exists(filePath) {
  return fs.existsSync(filePath);
}

function read(filePath) {
  return fs.readFileSync(filePath, "utf8");
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

function readJavaFiles() {
  return walk(JAVA_ROOT, (file) => file.endsWith(".java")).map((file) => ({
    path: file,
    rel: rel(file),
    text: read(file),
  }));
}

function addDrift(drifts, dimension, location, constraint, description, severity, suggestion) {
  drifts.push({
    id: `DRIFT-${String(drifts.length + 1).padStart(3, "0")}`,
    dimension,
    location,
    constraint,
    description,
    severity,
    suggestion,
  });
}

function combinePaths(base, child) {
  return `/${[base || "", child || ""].join("/").split("/").filter(Boolean).join("/")}`;
}

function extractControllerEndpoints(javaFiles) {
  const endpoints = [];
  for (const file of javaFiles.filter((item) => item.rel.includes("/controller/") || item.rel.endsWith("StatsController.java"))) {
    const baseMatch = file.text.match(/@RequestMapping\("([^"]*)"\)/u);
    const base = baseMatch ? baseMatch[1] : "";
    for (const match of file.text.matchAll(/@(Get|Post|Put|Delete)Mapping(?:\(path\s*=\s*"([^"]*)"|(?:\("([^"]*)"\))?)?/gu)) {
      endpoints.push({
        method: match[1].toUpperCase(),
        path: combinePaths(base, match[2] || match[3] || ""),
        file: file.rel,
      });
    }
  }
  return endpoints;
}

function hasEndpoint(endpoints, method, apiPath) {
  return endpoints.some((item) => item.method === method && item.path === apiPath);
}

function detectArchitectureDrifts(javaFiles) {
  const drifts = [];
  for (const file of javaFiles) {
    const text = file.text;
    if (file.rel.includes("/controller/") && /Repository\b/u.test(text)) {
      addDrift(drifts, "架构职责漂移", file.rel, "C-RESP-001", "Controller 直接依赖 Repository，违反 HTTP 适配层职责。", "高", "Controller 只注入应用服务。");
    }
    if (file.rel.includes("/domain/") && /web\.bind|Repository|Service|Controller/u.test(text)) {
      addDrift(drifts, "架构职责漂移", file.rel, "C-RESP-002", "实体层依赖 Web/Service/Repository 语义。", "高", "实体仅保留 JPA 与业务字段。");
    }
    if (file.rel.includes("/repository/") && !/extends\s+JpaRepository/u.test(text)) {
      addDrift(drifts, "架构职责漂移", file.rel, "C-RESP-003", "Repository 未采用 Spring Data JPA 统一仓储边界。", "中", "使用 JpaRepository 保持一致存储模型。");
    }
    if (file.rel.includes("/infra/") && /com\.printshop\.mis\.(domain|repository|service|controller)/u.test(text)) {
      addDrift(drifts, "架构职责漂移", file.rel, "C-DEP-002", "Infra 反向依赖 MIS 业务实现。", "高", "基础设施层不得依赖业务层。");
    }
    if (file.rel.includes("/controller/") && /new\s+BigDecimal|\.save\(|\.delete\(/u.test(text)) {
      addDrift(drifts, "架构职责漂移", file.rel, "C-RESP-004", "Controller 出现业务计算或存储操作迹象。", "中", "业务计算与持久化统一放入 MisService。");
    }
  }
  return drifts;
}

function detectTopologyDrifts(javaFiles) {
  const drifts = [];
  const service = javaFiles.find((file) => file.rel.endsWith("MisService.java"));
  const security = javaFiles.find((file) => file.rel.endsWith("SecurityConfig.java"));
  if (!service || !/com\.printshop\.mis\.repository/u.test(service.text)) {
    addDrift(drifts, "依赖拓扑漂移", "MisService", "C-DEP-001", "业务应用服务缺少统一仓储编排入口。", "高", "保持 Controller -> MisService -> Repository 的主依赖方向。");
  }
  if (!security || !/UserAccountRepository/u.test(security.text)) {
    addDrift(drifts, "依赖拓扑漂移", "SecurityConfig", "C-DEP-003", "安全配置未从用户仓储加载演示账号。", "中", "使用 UserAccountRepository 作为身份源。");
  }
  for (const file of javaFiles.filter((item) => item.rel.includes("/repository/"))) {
    if (/com\.printshop\.mis\.(controller|service)\./u.test(file.text)) {
      addDrift(drifts, "依赖拓扑漂移", file.rel, "C-DEP-004", "Repository 依赖上层组件。", "高", "Repository 只依赖实体与 Spring Data。");
    }
  }
  return drifts;
}

function detectContractDrifts(javaFiles) {
  const drifts = [];
  const endpoints = extractControllerEndpoints(javaFiles);
  for (const [method, apiPath] of REQUIRED_ENDPOINTS) {
    if (!hasEndpoint(endpoints, method, apiPath)) {
      addDrift(drifts, "接口契约漂移", `${method} ${apiPath}`, "C-API-001/OpenAPI-v2.0", "准生产版核心接口未在 Controller 中实现。", "高", "补齐对应 Controller 方法。");
    }
  }
  if (!exists(OPENAPI)) {
    addDrift(drifts, "接口契约漂移", rel(OPENAPI), "C-API-002", "缺少准生产版 OpenAPI v2.0 契约。", "高", "生成 OpenAPI-接口契约-v2.0.yaml。");
    return drifts;
  }
  const contract = read(OPENAPI);
  for (const [, apiPath] of REQUIRED_ENDPOINTS.filter((item) => item[1].startsWith("/api/"))) {
    if (!contract.includes(`${apiPath}:`)) {
      addDrift(drifts, "接口契约漂移", rel(OPENAPI), "OpenAPI-v2.0", `${apiPath} 未出现在 OpenAPI 契约中。`, "高", "同步更新 OpenAPI 契约。");
    }
  }
  if (!/openapi:\s*3\.0\.3/u.test(contract) || !/securitySchemes:/u.test(contract)) {
    addDrift(drifts, "接口契约漂移", rel(OPENAPI), "C-API-003", "OpenAPI 契约缺少 3.0.3 或 BasicAuth 安全定义。", "中", "补齐 OpenAPI 版本与安全方案。");
  }
  return drifts;
}

function detectRequirementCoverageDrifts(javaFiles) {
  const drifts = [];
  const joined = javaFiles.map((file) => `${file.rel}\n${file.text}`).join("\n");
  const migration = exists(MIGRATION) ? read(MIGRATION) : "";
  const appJs = exists(path.join(STATIC_ROOT, "app.js")) ? read(path.join(STATIC_ROOT, "app.js")) : "";
  const combined = `${joined}\n${migration}\n${appJs}`;

  for (const [moduleCode, hints] of MODULE_EVIDENCE) {
    const missing = hints.filter((hint) => !combined.includes(hint));
    if (missing.length > 0) {
      addDrift(drifts, "需求与角色覆盖漂移", moduleCode, "RTM/SRS", `${moduleCode} 模块缺少实现证据：${missing.join("、")}。`, "高", "补齐实体、接口、前端入口或迁移表。");
    }
  }
  for (const [username, role, roleName] of REQUIRED_ROLES) {
    if (!migration.includes(`'${username}'`) || !migration.includes(`'${role}'`)) {
      addDrift(drifts, "需求与角色覆盖漂移", username, "七类演示账号", `缺少 ${roleName} 演示账号或角色。`, "高", "在 Flyway 种子数据中补齐七类账号。");
    }
  }
  for (const table of REQUIRED_TABLES) {
    if (!migration.includes(`CREATE TABLE ${table}`)) {
      addDrift(drifts, "需求与角色覆盖漂移", table, "持久化覆盖", `缺少 ${table} 业务表。`, "高", "补齐 Flyway 建表脚本。");
    }
  }
  return drifts;
}

function collectFacts(javaFiles) {
  const endpoints = extractControllerEndpoints(javaFiles);
  const migration = exists(MIGRATION) ? read(MIGRATION) : "";
  return {
    endpoints,
    controllers: javaFiles.filter((file) => file.rel.includes("/controller/") || file.rel.endsWith("StatsController.java")).length,
    services: javaFiles.filter((file) => file.rel.includes("/service/")).length,
    repositories: javaFiles.filter((file) => file.rel.includes("/repository/")).length,
    entities: javaFiles.filter((file) => file.rel.includes("/domain/")).length,
    tables: REQUIRED_TABLES.filter((table) => migration.includes(`CREATE TABLE ${table}`)).length,
    roles: REQUIRED_ROLES.filter(([username, role]) => migration.includes(`'${username}'`) && migration.includes(`'${role}'`)).length,
  };
}

function summarize(drifts) {
  return ["架构职责漂移", "依赖拓扑漂移", "接口契约漂移", "需求与角色覆盖漂移"].map((dimension) => {
    const subset = drifts.filter((item) => item.dimension === dimension);
    const high = subset.filter((item) => item.severity === "高").length;
    const medium = subset.filter((item) => item.severity === "中").length;
    return {
      dimension,
      count: subset.length,
      severity: high ? "高" : medium ? "中" : subset.length ? "低" : "-",
    };
  });
}

function renderRows(drifts) {
  if (drifts.length === 0) return "| - | - | - | - | 未发现漂移 | - | - |";
  return drifts.map((item) => `| ${item.id} | ${item.dimension} | ${item.location} | ${item.constraint} | ${item.description} | ${item.severity} | ${item.suggestion} |`).join("\n");
}

function renderRcr(drifts, facts) {
  const summaryRows = summarize(drifts).map((item) => `| ${item.dimension} | ${item.count} | ${item.severity} |`).join("\n");
  const high = drifts.filter((item) => item.severity === "高").length;
  return `# RCR逆向校验报告-v1.0

## 校验范围

- 代码目录：\`generated-code/printshop-v1/\`
- 系统形态：Spring Boot 3 + Java 17 + Spring Security + Spring Data JPA + Flyway + MySQL 8
- 正向设计资产：需求基线、MDS、DTS、TLCD、OpenAPI v2.0
- 检测口径：架构职责、依赖拓扑、接口契约、需求与角色覆盖四种漂移

## 校验结果汇总

| 校验维度 | 漂移数量 | 严重程度 |
|---|---:|---|
${summaryRows}

## 漂移详情

| 编号 | 维度 | 位置 | 违反的约束编号 | 问题描述 | 严重程度 | 修复建议 |
|---|---|---|---|---|---|---|
${renderRows(drifts)}

## 准生产能力快照

| 项目 | 数量 |
|---|---:|
| Controller | ${facts.controllers} |
| Application Service | ${facts.services} |
| JPA Entity | ${facts.entities} |
| JPA Repository | ${facts.repositories} |
| Flyway 业务表 | ${facts.tables} |
| 演示角色账号 | ${facts.roles} |
| HTTP Endpoint | ${facts.endpoints.length} |

## 结论

高严重度漂移：${high}

- [${high === 0 ? "x" : " "}] 通过——当前 v1 准生产版实现与设计资产一致，可进入运行演示与后续 v2 演进。
- [${high === 0 ? " " : "x"}] 需修复——存在高严重度漂移，应先修正代码或契约。
`;
}

function renderQuality(drifts, facts) {
  const high = drifts.filter((item) => item.severity === "高").length;
  return `# 模块设计质量校验-v1.0

## 四维度质量校验

| 维度 | 结论 | 证据 | 后续建议 |
|---|---|---|---|
| 完备性 | ${facts.tables === REQUIRED_TABLES.length && facts.roles === REQUIRED_ROLES.length ? "通过" : "需补齐"} | 已覆盖身份、门店、订单、文件、报价、作业单、生产、库存、配送、发票、收款、审计和报表；Flyway 表 ${facts.tables}/${REQUIRED_TABLES.length}，演示角色 ${facts.roles}/${REQUIRED_ROLES.length}。 | v2 可继续补真实设备、短信、企微和税控接口。 |
| 正确性 | ${drifts.some((item) => item.dimension === "架构职责漂移" && item.severity === "高") ? "需修复" : "通过"} | Controller 仅做 HTTP 适配，MisService 编排业务规则，Repository 使用 JPA，Domain 为持久化实体。 | 继续避免 Controller 直接访问 Repository。 |
| 一致性 | ${drifts.some((item) => item.dimension === "接口契约漂移" && item.severity === "高") ? "需修复" : "通过"} | OpenAPI v2.0、Controller 路径、Flyway 表和前端模块导航均按准生产 Print MIS 模块命名。 | 每次新增接口后同步 OpenAPI 与漂移检测脚本。 |
| 有效性 | ${high === 0 ? "通过" : "需修复"} | v1 具备 Maven 测试、MySQL Compose、Flyway 种子数据、Basic Auth 登录、静态前端和 \`/stats\`。 | 发布前运行 \`mvn test\`、\`mvn package\` 与 \`verify-design-drift.js --check\`。 |

## 门禁结论

- 高严重度漂移：${high}
- 质量门禁：${high === 0 ? "通过" : "未通过"}
`;
}

function renderAdr002() {
  return `# ADR-002 模块拆分决策

## 状态

已决定

## 背景

图文快印门店连锁管理系统从课程演示版升级为准生产 Print MIS，需要覆盖估价/报价、作业单、生产排程、库存、配送外协、收款开票、客户沟通与审计闭环。若继续使用单一 Workbench 或旧的六动作接口，将无法支撑真实门店角色的日常操作。

## 决策

采用按 Print MIS 业务能力划分的模块结构：AUTH、STORE、ORD、FILE、QUO、JOB、PRO、INV、DLV、FIN、AUD、RPT。前端采用同一模块导航，后端通过 MisController + MisService + JPA Repository 实现模块化单体。

## 理由

- 模块边界直接对应成熟 Print MIS 的业务域。
- 单体部署适合课程 v1，JPA 与 Flyway 提供可演示的数据一致性。
- 前端模块导航、OpenAPI 契约、数据库表和审计动作保持同名映射，便于逆向校验。

## 备选方案

| 方案 | 放弃原因 |
|---|---|
| 继续使用接口演示台 | 只能证明接口可调，无法让七类用户完成业务闭环。 |
| 直接拆成微服务 | v1 课程范围过重，部署和事务复杂度超过收益。 |
| 只做内存 Workbench | 缺少身份、权限、持久化和成熟 MIS 的经营对象。 |

## 影响

- 新业务能力必须先判断归属模块，再补实体、Repository、Service、Controller、OpenAPI 和前端导航。
- 审计与报表作为横切能力，不允许绕过统一记录。
`;
}

function renderAdr003() {
  return `# ADR-003 依赖拓扑决策

## 状态

已决定

## 背景

准生产 v1 同时引入 JPA、Spring Security、Flyway 和静态前端。为避免快速退化为大泥球，需要固定后端依赖方向和模块协作方式。

## 决策

采用 Controller -> MisService -> Repository -> Entity 的主依赖方向；SecurityConfig 只读取 UserAccountRepository；Infra 提供统计和追踪能力，不反向依赖 MIS 业务；前端只通过 REST API 访问业务数据。

## 理由

- 单一应用服务便于在 v1 中保持跨订单、报价、生产、财务的事务一致性。
- Repository 统一使用 Spring Data JPA，避免每个模块自建存储模型。
- Flyway 作为数据库结构事实源，降低 JPA 与 MySQL 漂移风险。

## 备选方案

| 方案 | 放弃原因 |
|---|---|
| Controller 直接调用 Repository | 会把状态流转和审计散落到 HTTP 层。 |
| 每个模块独立 Service 互相注入 | v1 初期会形成复杂循环依赖。 |
| 前端直接拼接数据库语义 | 破坏 API 契约和审计闭环。 |

## 影响

- 漂移检测会把 Controller 依赖 Repository、Infra 依赖业务层、Repository 依赖上层视为高严重度问题。
- 后续拆分 Service 时必须保持单向依赖和事务边界清晰。
`;
}

function renderAdr004() {
  return `# ADR-004 横切审计与防腐层决策

## 状态

已决定

## 背景

Print MIS 的关键动作包括报价审批、生产完工、库存调整、签收、开票、退款和配置维护。这些动作必须可追溯；同时支付、税务、设备、短信和企微等外部系统不应直接污染核心业务代码。

## 决策

所有写操作通过 MisService 调用统一审计记录与 \`/stats\` 统计；外部系统在 v1 中保留为可替换适配器或模拟点，不接真实第三方；审计日志仅允许查询，不允许业务修改和删除。

## 理由

- 审计与统计统一后，课程演示可直接证明业务链路闭环。
- 防腐层策略允许 v1 先完成业务系统，再在 v2 替换真实支付、税务、设备或通知适配器。
- 审计日志不可变符合财务、权限和门店运营追责需要。

## 备选方案

| 方案 | 放弃原因 |
|---|---|
| 各模块自行写审计 | 审计字段、时机和统计口径不一致。 |
| v1 直接接真实第三方 | 密钥、网络和账号风险会干扰课程验证。 |
| 审计日志支持编辑删除 | 破坏追溯可信度。 |

## 影响

- 新增写接口必须调用统一记录逻辑。
- \`/api/audit-logs\` 保持只读。
- 后续接入第三方时从 Infra 适配器进入，不让业务模块直接处理外部协议。
`;
}

function buildOutputs(drifts, facts) {
  return {
    [OUTPUTS.rcr]: renderRcr(drifts, facts),
    [OUTPUTS.quality]: renderQuality(drifts, facts),
    [OUTPUTS.adr002]: renderAdr002(),
    [OUTPUTS.adr003]: renderAdr003(),
    [OUTPUTS.adr004]: renderAdr004(),
  };
}

function run() {
  const args = process.argv.slice(2);
  const writeMode = args.includes("--write");
  const checkMode = args.includes("--check");
  if (!writeMode && !checkMode) {
    console.log("Usage: node generated-code/printshop-v1/scripts/verify-design-drift.js [--write|--check]");
    process.exitCode = 1;
    return;
  }

  const javaFiles = readJavaFiles();
  const drifts = [
    ...detectArchitectureDrifts(javaFiles),
    ...detectTopologyDrifts(javaFiles),
    ...detectContractDrifts(javaFiles),
    ...detectRequirementCoverageDrifts(javaFiles),
  ];
  const facts = collectFacts(javaFiles);
  const outputs = buildOutputs(drifts, facts);

  if (writeMode) {
    for (const [file, content] of Object.entries(outputs)) {
      fs.writeFileSync(file, content, "utf8");
      console.log(`[write] ${rel(file)}`);
    }
  }

  if (checkMode) {
    let stale = false;
    for (const [file, content] of Object.entries(outputs)) {
      if (!exists(file)) {
        console.error(`[missing] ${rel(file)}`);
        stale = true;
      } else if (read(file) !== content) {
        console.error(`[stale] ${rel(file)}`);
        stale = true;
      }
    }
    if (stale) {
      process.exitCode = 1;
      return;
    }
  }

  const high = drifts.filter((item) => item.severity === "高").length;
  console.log(`四种漂移检测完成：${drifts.length} drift(s), high=${high}`);
  if (high > 0) process.exitCode = 1;
}

run();
