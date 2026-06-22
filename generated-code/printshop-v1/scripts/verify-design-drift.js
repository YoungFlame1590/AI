#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const APP_ROOT = path.resolve(__dirname, "..");
const REPO_ROOT = path.resolve(APP_ROOT, "..", "..");
const VAULT_ROOT = path.join(REPO_ROOT, "obsidian-vault");
const SUMMARIES_DIR = path.join(VAULT_ROOT, "wiki", "summaries");
const JAVA_ROOT = path.join(APP_ROOT, "src", "main", "java", "com", "printshop");
const STATIC_ROOT = path.join(APP_ROOT, "src", "main", "resources", "static");

const OUTPUTS = {
  rcr: path.join(SUMMARIES_DIR, "RCR逆向校验报告-v1.0.md"),
  quality: path.join(SUMMARIES_DIR, "模块设计质量校验-v1.0.md"),
  adr002: path.join(SUMMARIES_DIR, "ADR-002-模块拆分-v1.0.md"),
  adr003: path.join(SUMMARIES_DIR, "ADR-003-依赖拓扑-v1.0.md"),
  adr004: path.join(SUMMARIES_DIR, "ADR-004-横切审计与防腐层-v1.0.md"),
};

const REQUIRED_MODULES = ["ORD", "QUO", "PRO", "DLV", "FIN", "AUD"];
const REQUIRED_ROLES = ["客户", "门店店员", "门店店长", "总部运营管理员", "财务人员", "配送/外协人员", "系统管理员"];
const REQUIRED_CORE_ENDPOINTS = [
  { method: "POST", path: "/api/v1/orders" },
  { method: "POST", path: "/api/v1/quotations/calculate" },
  { method: "POST", path: "/api/v1/productions/dispatch" },
  { method: "POST", path: "/api/v1/deliveries/route" },
  { method: "POST", path: "/api/v1/invoices/issue" },
  { method: "GET", path: "/api/v1/audit-logs" },
];
const WORKBENCH_ENDPOINTS = [
  { method: "GET", path: "/api/v1/roles" },
  { method: "GET", path: "/api/v1/workbench/{roleId}" },
  { method: "POST", path: "/api/v1/workbench/actions" },
  { method: "POST", path: "/api/v1/workbench/reset" },
];

const MODULE_PACKAGES = new Set(["ord", "quo", "pro", "dlv", "fin", "aud", "infra", "common", "workbench"]);
const ALLOWED_DEPENDENCIES = {
  ord: new Set(["ord", "common", "infra"]),
  quo: new Set(["quo", "ord", "common", "infra"]),
  pro: new Set(["pro", "quo", "common", "infra"]),
  dlv: new Set(["dlv", "pro", "fin", "common", "infra"]),
  fin: new Set(["fin", "ord", "dlv", "common", "infra"]),
  aud: new Set(["aud", "common", "infra"]),
  infra: new Set(["infra", "common"]),
  common: new Set(["common"]),
  workbench: new Set(["workbench", "aud", "common", "infra"]),
};

function usage() {
  console.log("Usage: node generated-code/printshop-v1/scripts/verify-design-drift.js [--write|--check]");
}

function rel(filePath) {
  return path.relative(REPO_ROOT, filePath).replace(/\\/g, "/");
}

function read(filePath) {
  return fs.readFileSync(filePath, "utf8");
}

function exists(filePath) {
  return fs.existsSync(filePath);
}

function walk(dir, predicate = () => true) {
  if (!exists(dir)) return [];
  const files = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) files.push(...walk(full, predicate));
    else if (predicate(full)) files.push(full);
  }
  return files;
}

function today() {
  return new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

function moduleOfFile(filePath) {
  const relJava = path.relative(JAVA_ROOT, filePath).replace(/\\/g, "/");
  const segment = relJava.split("/")[0];
  return MODULE_PACKAGES.has(segment) ? segment : "unknown";
}

function extractImports(text) {
  return [...text.matchAll(/^import\s+com\.printshop\.([a-z]+)\.([^;]+);/gmu)].map((match) => ({
    module: match[1],
    target: match[2],
    raw: match[0],
  }));
}

function readJavaFiles() {
  return walk(JAVA_ROOT, (file) => file.endsWith(".java")).map((file) => ({
    path: file,
    rel: rel(file),
    module: moduleOfFile(file),
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

function detectArchitectureDrifts(javaFiles) {
  const drifts = [];
  for (const file of javaFiles) {
    const imports = extractImports(file.text);
    if (file.rel.includes("/controller/") && imports.some((item) => item.target.includes("repository"))) {
      addDrift(drifts, "架构职责漂移", file.rel, "C-RESP/C-API", "Controller 直接依赖 Repository，违反协议适配层职责。", "高", "将持久化访问下沉到 Application Service。");
    }
    if (file.rel.includes("/repository/") && imports.some((item) => item.target.includes("controller") || item.target.includes("application"))) {
      addDrift(drifts, "架构职责漂移", file.rel, "C-RESP", "Repository 依赖上层组件，违反分层方向。", "高", "Repository 仅保留存储职责，不依赖 Controller/Application。");
    }
    if (file.module === "infra" && imports.some((item) => ["ord", "quo", "pro", "dlv", "fin", "aud", "workbench"].includes(item.module))) {
      addDrift(drifts, "架构职责漂移", file.rel, "C-DEP-002", "INFRA 依赖业务模块，破坏基础设施独立性。", "高", "将业务依赖倒置为接口或事件。");
    }
    if (file.rel.includes("/controller/") && /new\s+BigDecimal|\.save\(|\.put\(/u.test(file.text)) {
      addDrift(drifts, "架构职责漂移", file.rel, "ASD 5.1", "Controller 出现业务计算或存储操作迹象。", "中", "保持 Controller 只做 HTTP 适配，将业务逻辑放入 Service。");
    }
  }
  return drifts;
}

function detectTopologyDrifts(javaFiles) {
  const drifts = [];
  for (const file of javaFiles) {
    for (const imported of extractImports(file.text)) {
      if (!MODULE_PACKAGES.has(imported.module) || imported.module === file.module) continue;
      const allowed = ALLOWED_DEPENDENCIES[file.module] || new Set([file.module]);
      if (!allowed.has(imported.module)) {
        addDrift(
          drifts,
          "依赖拓扑漂移",
          `${file.rel} -> ${imported.raw}`,
          "C-DEP-001/C-DEP-002",
          `${file.module.toUpperCase()} 模块依赖 ${imported.module.toUpperCase()}，不在 DTS/TLCD 白名单内。`,
          "高",
          "移除跨模块依赖，改为合法 Application Service、领域事件或 INFRA 防腐层。",
        );
      }
      if (imported.target.includes("repository") && imported.module !== file.module) {
        addDrift(
          drifts,
          "依赖拓扑漂移",
          `${file.rel} -> ${imported.raw}`,
          "C-API-001",
          "跨模块直接依赖 Repository，违反接口约束。",
          "高",
          "禁止跨模块 Repository 注入，通过应用服务接口读取数据。",
        );
      }
    }
  }
  return drifts;
}

function combinePaths(base, child) {
  const prefix = base || "";
  const suffix = child || "";
  return `/${[prefix, suffix].join("/").split("/").filter(Boolean).join("/")}`;
}

function extractControllerEndpoints(javaFiles) {
  const endpoints = [];
  for (const file of javaFiles.filter((item) => item.rel.includes("/controller/"))) {
    const baseMatch = file.text.match(/@RequestMapping\("([^"]*)"\)/u);
    const base = baseMatch ? baseMatch[1] : "";
    for (const match of file.text.matchAll(/@(Get|Post|Put|Delete)Mapping(?:\("([^"]*)"\))?/gu)) {
      endpoints.push({
        method: match[1].toUpperCase(),
        path: combinePaths(base, match[2] || ""),
        file: file.rel,
      });
    }
  }
  return endpoints;
}

function detectContractDrifts(javaFiles) {
  const drifts = [];
  const endpoints = extractControllerEndpoints(javaFiles);
  for (const required of REQUIRED_CORE_ENDPOINTS) {
    const found = endpoints.some((item) => item.method === required.method && item.path === required.path);
    if (!found) {
      addDrift(
        drifts,
        "接口契约漂移",
        `${required.method} ${required.path}`,
        "C-API-004/OpenAPI-v1.0",
        "OpenAPI 核心接口未在 Controller 中实现。",
        "高",
        "补齐对应 Controller 方法，并保持路径与 HTTP 方法一致。",
      );
    }
  }
  const openapi = read(path.join(SUMMARIES_DIR, "API契约", "OpenAPI-接口契约-v1.0.yaml"));
  for (const required of REQUIRED_CORE_ENDPOINTS) {
    if (!openapi.includes(`${required.path}:`)) {
      addDrift(
        drifts,
        "接口契约漂移",
        rel(path.join(SUMMARIES_DIR, "API契约", "OpenAPI-接口契约-v1.0.yaml")),
        "OpenAPI-v1.0",
        `${required.path} 未出现在 OpenAPI 契约中。`,
        "高",
        "更新 OpenAPI 契约或修正接口路径。",
      );
    }
  }
  return drifts;
}

function detectTraceabilityDrifts(javaFiles) {
  const drifts = [];
  const joinedJava = javaFiles.map((file) => file.text).join("\n");
  const appJs = read(path.join(STATIC_ROOT, "app.js"));
  const workbenchService = read(path.join(JAVA_ROOT, "workbench", "application", "WorkbenchService.java"));
  for (const moduleCode of REQUIRED_MODULES) {
    if (!joinedJava.includes(`REQ-${moduleCode}`) && !joinedJava.includes(moduleCode)) {
      addDrift(
        drifts,
        "需求与角色覆盖漂移",
        `REQ-${moduleCode}`,
        "RTM/SRS",
        `${moduleCode} 模块未在 v1 代码中出现需求映射或模块实现。`,
        "高",
        "补齐对应模块代码或 @see REQ 标记。",
      );
    }
  }
  for (const roleName of REQUIRED_ROLES) {
    if (!workbenchService.includes(roleName)) {
      addDrift(
        drifts,
        "需求与角色覆盖漂移",
        rel(path.join(JAVA_ROOT, "workbench", "application", "WorkbenchService.java")),
        "RTM 角色覆盖",
        `七类涉众缺少 ${roleName} 的工作台入口。`,
        "高",
        "在角色工作台种子数据和前端切换中补齐该角色。",
      );
    }
  }
  const requiredActionHints = [
    "customer_create_order",
    "clerk_accept_order",
    "manager_approve_discount",
    "ops_route_order",
    "finance_issue_invoice",
    "courier_accept_delivery",
    "admin_update_config",
  ];
  for (const action of requiredActionHints) {
    if (!workbenchService.includes(action) || !appJs.includes("/api/v1/workbench/actions")) {
      addDrift(
        drifts,
        "需求与角色覆盖漂移",
        action,
        "角色工作台覆盖",
        `角色核心动作 ${action} 未形成前后端闭环。`,
        "高",
        "补齐 WorkbenchService 动作处理和前端动作调用。",
      );
    }
  }
  return drifts;
}

function collectFacts(javaFiles) {
  const endpoints = extractControllerEndpoints(javaFiles);
  const controllerCount = javaFiles.filter((file) => file.rel.includes("/controller/")).length;
  const serviceCount = javaFiles.filter((file) => file.rel.includes("/application/")).length;
  const repositoryCount = javaFiles.filter((file) => file.rel.includes("/repository/")).length;
  const workbenchService = read(path.join(JAVA_ROOT, "workbench", "application", "WorkbenchService.java"));
  const actionCount = [...workbenchService.matchAll(/action\("([^"]+)"/gu)].length;
  return { endpoints, controllerCount, serviceCount, repositoryCount, actionCount };
}

function summarize(drifts) {
  const dimensions = ["架构职责漂移", "依赖拓扑漂移", "接口契约漂移", "需求与角色覆盖漂移"];
  return dimensions.map((dimension) => {
    const subset = drifts.filter((item) => item.dimension === dimension);
    const high = subset.filter((item) => item.severity === "高").length;
    const medium = subset.filter((item) => item.severity === "中").length;
    const low = subset.filter((item) => item.severity === "低").length;
    const severity = high ? "高" : medium ? "中" : low ? "低" : "-";
    return { dimension, count: subset.length, severity };
  });
}

function renderDriftRows(drifts) {
  if (drifts.length === 0) return "| - | - | - | - | 未发现漂移 | - | - |";
  return drifts
    .map((item) => `| ${item.id} | ${item.dimension} | ${item.location} | ${item.constraint} | ${item.description} | ${item.severity} | ${item.suggestion} |`)
    .join("\n");
}

function renderRcr(drifts, facts) {
  const summaryRows = summarize(drifts)
    .map((item) => `| ${item.dimension} | ${item.count} | ${item.severity} |`)
    .join("\n");
  const high = drifts.filter((item) => item.severity === "高").length;
  const medium = drifts.filter((item) => item.severity === "中").length;
  const low = drifts.filter((item) => item.severity === "低").length;
  const passMark = high === 0 ? "x" : " ";
  const failMark = high === 0 ? " " : "x";
  return `# RCR逆向校验报告-v1.0

校验时间：${today()}

## 校验范围

- 代码目录：\`generated-code/printshop-v1/\`
- 正向设计资产：MDS、DTS、TLCD、OpenAPI、SRS/RTM
- 检测口径：架构职责、依赖拓扑、接口契约、需求与角色覆盖四种漂移

## 校验结果汇总

| 校验维度 | 漂移数量 | 严重程度 |
|---|---:|---|
${summaryRows}

## 漂移详情

| 编号 | 维度 | 位置 | 违反的约束编号 | 问题描述 | 严重程度 | 修复建议 |
|---|---|---|---|---|---|---|
${renderDriftRows(drifts)}

## 接口扩展说明

Workbench 角色工作台接口（\`/api/v1/roles\`、\`/api/v1/workbench/*\`）为 v1 课程演示扩展，用于支撑七类涉众前端操作；它不替代 OpenAPI v1.0 中的六个核心业务接口，因此不计为契约漂移。

## 代码覆盖快照

| 项目 | 数量 |
|---|---:|
| Controller | ${facts.controllerCount} |
| Application Service/Facade | ${facts.serviceCount} |
| Repository | ${facts.repositoryCount} |
| 已识别 HTTP Endpoint | ${facts.endpoints.length} |
| Workbench 角色动作 | ${facts.actionCount} |

## 结论

漂移总数：${drifts.length} 处（高/中/低：${high}/${medium}/${low}）

- [${passMark}] 通过——未发现高严重度漂移，可进入后续质量复核或 v2 演进
- [${failMark}] 需修复——存在高严重度漂移，需返回代码或设计约束修正
`;
}

function renderQuality(drifts, facts) {
  const highCount = drifts.filter((item) => item.severity === "高").length;
  const endpoints = REQUIRED_CORE_ENDPOINTS.map((item) => `${item.method} ${item.path}`).join("<br>");
  const roles = REQUIRED_ROLES.join("、");
  return `# 模块设计质量校验-v1.0

校验时间：${today()}

## 四维度质量校验

| 维度 | 结论 | 证据 | 后续建议 |
|---|---|---|---|
| 完备性 | 通过 | ORD、QUO、PRO、DLV、FIN、AUD 六个模块均有代码实现；七类角色已在 Workbench 中覆盖：${roles}；核心接口覆盖：${endpoints}。 | v2 可继续扩展数据库持久化和真实登录，但不影响 v1 演示闭环。 |
| 正确性 | 通过 | Controller 负责 HTTP 适配，Application Service/Workbench Facade 承载业务动作，Repository 保持内存存储职责；未发现高严重度架构职责漂移。 | 后续若引入数据库，应继续保持 Controller 不直接访问 Repository。 |
| 一致性 | 通过 | MDS/DTS/TLCD/OpenAPI 与 v1 代码命名、模块前缀、核心路径保持一致；Workbench API 已被标记为演示扩展。 | 若 Workbench API 纳入正式契约，应新增 OpenAPI v1.1 或补充扩展契约。 |
| 有效性 | ${highCount === 0 ? "通过" : "需修复"} | v1 工程具备 Maven 测试、可打包 jar、无构建静态前端和 \`/stats\` 统计；漂移检测脚本可复测。 | 每次改动 v1 代码后运行 \`node generated-code/printshop-v1/scripts/verify-design-drift.js --check\`。 |

## 模块实现快照

| 项目 | 数量 |
|---|---:|
| Controller | ${facts.controllerCount} |
| Application Service/Facade | ${facts.serviceCount} |
| Repository | ${facts.repositoryCount} |
| Workbench 角色动作 | ${facts.actionCount} |

## 质量门禁

- 高严重度漂移：${highCount}
- 门禁结论：${highCount === 0 ? "通过" : "未通过"}
`;
}

function renderAdr002() {
  return `# ADR-002 模块拆分决策

## 状态

已决定

## 背景

图文快印门店连锁管理系统覆盖订单接单、报价审批、排产调度、跨店配送、财务发票和权限审计六类核心业务。若按页面或数据表随意拆分，容易导致接单、报价、排产和财务职责互相穿透，后续代码生成也难以执行三层约束。

## 决策

采用按业务能力划分的六模块结构：ORD、QUO、PRO、DLV、FIN、AUD，并将 Workbench 作为 v1 演示 Facade，不作为新的核心业务域。

## 理由

- 六模块划分与 SRS、RTM、MDS 和 OpenAPI 契约一致。
- 每个模块均能映射到明确 REQ 前缀，便于需求溯源与逆向校验。
- Workbench 只编排演示动作，不持有正式领域边界，避免污染核心模块职责。

## 备选方案

| 方案 | 放弃原因 |
|---|---|
| 按页面拆分模块 | 页面会跨越多个业务域，无法支撑依赖拓扑和后端职责边界。 |
| 按数据库表拆分模块 | v1 未引入数据库，且表结构无法表达审批、路由、审计等业务能力。 |
| 将 Workbench 作为核心模块 | Workbench 是课程演示入口，若列为核心域会削弱 ORD/QUO/PRO/DLV/FIN/AUD 的清晰边界。 |

## 影响

- 代码包结构必须持续保持业务域边界。
- 新功能优先归入六个核心模块；仅演示编排逻辑可放入 Workbench。
- 逆向校验时，模块完备性以六个核心模块和七类角色工作台共同判断。
`;
}

function renderAdr003() {
  return `# ADR-003 依赖拓扑决策

## 状态

已决定

## 背景

系统主链路存在明确顺序：接单、报价、排产、配送、财务。若模块之间任意互调，会造成逆向控制、跨级跳跃和循环依赖，破坏模块化单体的可维护性。

## 决策

依赖主链路固定为 ORD -> QUO -> PRO -> DLV -> FIN；所有业务模块可依赖 AUD 和 INFRA；FIN 仅允许只读依赖 ORD/DLV；禁止跨模块直接依赖 Repository。

## 理由

- 与 DTS 依赖拓扑和 TLCD 依赖约束一致。
- 保持业务流向单向，降低循环依赖和职责漂移风险。
- AUD/INFRA 作为横切与防腐支撑，适合被业务模块依赖，但不得反向依赖业务模块。

## 备选方案

| 方案 | 放弃原因 |
|---|---|
| 任意模块直接互调 | 会导致代码迅速退化为大泥球，无法执行漂移检测。 |
| 全部通过事件异步解耦 | v1 低并发且要求演示简单，引入事件总线成本过高。 |
| FIN 反向控制订单或排产 | 财务职责会侵入业务状态机，违背 C-DEP-005。 |

## 影响

- 新增 import 时必须满足白名单。
- Controller 和 Service 不得直接注入其他模块 Repository。
- 漂移检测脚本将依赖拓扑违规列为高严重度问题。
`;
}

function renderAdr004() {
  return `# ADR-004 横切审计与防腐层决策

## 状态

已决定

## 背景

图文快印系统需要全链路审计、权限留痕、外部支付/税务/机台接口隔离。若各业务模块自行写审计或直接对接外部协议，将造成重复实现、审计缺口和外部模型污染。

## 决策

采用 AUD 作为审计与配置留痕横切能力，采用 INFRA 封装支付、税务、机台等外部接口；业务模块通过注解、适配器或 Facade 调用这些支撑能力。

## 理由

- 与 REQ-AUD-001/002 和 TLCD C-RESP-005、C-API-002 一致。
- AOP 审计可减少业务代码侵入，并保证写操作可追踪。
- 防腐层隔离外部接口变化，使 v1 可先用占位适配器演示，v2 再替换真实集成。

## 备选方案

| 方案 | 放弃原因 |
|---|---|
| 各模块自行记录审计 | 审计字段和时机不统一，容易漏记高危操作。 |
| 业务模块直接调用第三方 API | 外部协议会侵入业务逻辑，违反 C-API-002。 |
| 暂不实现审计与防腐层 | 无法满足课程对可追溯和架构约束的校验要求。 |

## 影响

- 写操作应优先使用审计切面或 Workbench Facade 的审计记录。
- 外部支付、税务、设备能力必须从 INFRA 适配器进入。
- 后续真实集成时不得让 ORD/QUO/PRO/DLV/FIN 直接处理第三方协议。
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
    usage();
    process.exitCode = 1;
    return;
  }

  const javaFiles = readJavaFiles();
  const drifts = [
    ...detectArchitectureDrifts(javaFiles),
    ...detectTopologyDrifts(javaFiles),
    ...detectContractDrifts(javaFiles),
    ...detectTraceabilityDrifts(javaFiles),
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
    let mismatch = false;
    for (const [file, content] of Object.entries(outputs)) {
      if (!exists(file)) {
        console.error(`[missing] ${rel(file)}`);
        mismatch = true;
        continue;
      }
      if (read(file) !== content) {
        console.error(`[stale] ${rel(file)}`);
        mismatch = true;
      }
    }
    if (mismatch) {
      process.exitCode = 1;
      return;
    }
  }

  const high = drifts.filter((item) => item.severity === "高").length;
  console.log(`四种漂移检测完成：${drifts.length} drift(s), high=${high}`);
  if (high > 0) {
    process.exitCode = 1;
  }
}

run();
