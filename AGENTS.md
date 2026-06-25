# Repository Guidelines

## Project Structure & Module Organization

This repository has two main work areas:

- `agent-app/`: FastAPI + CrewAI web app for A1a/A1b elicitation, A2 quality analysis, A3 UML modeling, A4 SRS drafting, A5 validation, A6 baselining, and design-stage architecture/constraint work.
  - `app.py`: API entrypoint and static web serving.
  - `agents/`: stakeholder agents, elicitation, quality analysis, UML modeling, SRS drafting, validation, baselining, design architecture, design constraints, LLM config, and Obsidian record writing.
  - `web/`: plain HTML/CSS/JS frontend.
  - `requirements.txt`: Python dependencies.
- `obsidian-vault/`: Obsidian knowledge base.
  - `raw/notes/`: elicitation records named `{涉众角色}-{YYYYMMDD-HHMM}-需求记录.md`.
  - `wiki/summaries/`: A2 reports, UML `.puml` files, SRS drafts, validation reports, design-stage outputs, and other artifacts.
  - `wiki/baselines/`: approved A6 baseline snapshots named `BL-YYYYMMDD-NN`.
  - `templates/`: reusable Markdown templates.
  - `compile.js`: vault integrity checker.
- `generated-code/printshop-v1/`: generated Java 17 + Spring Boot 3 v1 business application.
  - Spring Security + Spring Data JPA + Flyway + MySQL 8 Print MIS module.
  - `mis/domain`, `mis/repository`, `mis/security`: JPA entities, repositories, and Basic Auth.
  - `mis/identity`, `mis/order`, `mis/quotation`, `mis/job`, `mis/production`, `mis/inventory`, `mis/delivery`, `mis/finance`, `mis/audit`, `mis/reporting`, `mis/dashboard`: module controllers and services.
  - `src/main/resources/db/migration/`: Flyway schema and seven demo accounts.
  - `src/main/resources/static/js/`: no-build ES module frontend (`config`, `state`, `api`, `orders`, `render`, `main`) served by Spring Boot at `/`.
- `generated-code/printshop-v2/`: v2 demand-change management application copied from v1 and extended for `CR-001 订单变更冻结`.
  - Adds order change request entity, repository, service, controller, Flyway `V5__order_change_requests.sql`, frontend `orderChangeRequests` module, and `scripts/verify-change-regression.js`.
  - Outputs change-management artifacts under `obsidian-vault/wiki/summaries/变更管理/` and baseline `obsidian-vault/wiki/baselines/BL-20260624-01/`.

Root-level `启动需求获取页面.bat` is the main local launcher.
Root-level `启动n8n-Docker.bat`, `一键启动n8n工作流.bat`, and `一键CCB审批.bat` support the n8n workflow.

## Build, Test, and Development Commands

Run the app locally:

```powershell
.\启动需求获取页面.bat
```

Check the Obsidian vault:

```powershell
cd obsidian-vault
node compile.js
```

Validate Python syntax:

```powershell
python -m compileall agent-app\agents agent-app\app.py
```

Validate frontend JavaScript:

```powershell
node --check agent-app\web\app.js
```

Build and test the generated v1 business app:

```powershell
cd generated-code\printshop-v1
mvn test
mvn package
Get-ChildItem src\main\resources\static\js -Filter *.js | ForEach-Object { node --check $_.FullName }
```

Run the v1 business app with MySQL:

```powershell
cd generated-code\printshop-v1
docker compose up -d mysql
mvn spring-boot:run
```

The MySQL container maps to `127.0.0.1:13306`, not `3306`, to avoid local MySQL conflicts. For quick demos when Docker cannot pull the image, use:

```powershell
cd generated-code\printshop-v1
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

Root-level `start-printmis-v1.bat` tries Docker MySQL first and falls back to the H2 demo profile.

Run v1 design drift verification:

```powershell
node generated-code\printshop-v1\scripts\verify-design-drift.js --check
```

Build and verify the v2 change-management app:

```powershell
cd generated-code\printshop-v2
mvn test
mvn package
Get-ChildItem src\main\resources\static\js -Filter *.js | ForEach-Object { node --check $_.FullName }
node scripts\verify-change-regression.js --write
node scripts\verify-change-regression.js --check
```

v2 uses a separate MySQL database from v1: container `printshop-v2-mysql`, port `13307`, database `printshop_v2`. Do not point v2 at v1's `printshop_v1` schema, or Flyway validation will fail because the v1 migration history has different checksums.

Run the generated v1 app and open its frontend:

```powershell
cd generated-code\printshop-v1
mvn spring-boot:run
```

Then visit `http://127.0.0.1:8080/`.

Start manually if needed:

```powershell
cd agent-app
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000
```

Run n8n with Docker and trigger workflow 1:

```powershell
.\启动n8n-Docker.bat
.\一键启动n8n工作流.bat
```

## Coding Style & Naming Conventions

Use 4-space indentation for Python and 2-space indentation for frontend files. Keep Python modules focused by responsibility. Use clear snake_case names in Python and camelCase in JavaScript. Do not hard-code API keys. Markdown records must follow the Chinese filename pattern so `compile.js` passes.

A3 PlantUML activity diagrams must use plugin-compatible conditions:

```plantuml
if ([Guard Condition]) then (是)
elseif ([Guard Condition]) then (是)
```

Do not generate `if [Guard Condition] then`. Keep `.puml` files free of Markdown fences.

## Testing Guidelines

There is no formal test suite yet. Before committing, run:

```powershell
python -m compileall agent-app\agents agent-app\app.py
node --check agent-app\web\app.js
cd obsidian-vault; node compile.js
```

For behavior changes, verify the local page at `http://127.0.0.1:8000`. A1/A2 writes records under `obsidian-vault/raw/notes/`; A3 writes UML outputs under `obsidian-vault/wiki/summaries/UML模型/`; A4 writes SRS drafts as `obsidian-vault/wiki/summaries/SRS-初稿-vX.Y.md`; A5 writes validation reports as `obsidian-vault/wiki/summaries/需求验证报告-vX.Y.md`; A6 writes approved baselines under `obsidian-vault/wiki/baselines/BL-YYYYMMDD-NN/`; design-stage architecture writes `知识图谱节点清单-vX.Y.md`, `架构选型报告-vX.Y.md`, `ASD-架构风格声明-vX.Y.md`, `MDS-模块划分方案-vX.Y.md`, `DTS-依赖拓扑-vX.Y.md`, and `ADR-001-架构选型-vX.Y.md`; design-stage constraints write `设计约束/TLCD-三层约束文档-vX.Y.md`, `API契约/OpenAPI-接口契约-vX.Y.yaml`, and `设计约束/约束提示词-vX.Y.md`; v1 code generation writes `generated-code/printshop-v1/` and `wiki/summaries/代码生成/v1代码生成说明.md`.

For v1 frontend work, keep the no-build static approach. The main page is a role-oriented Print MIS business system, not a raw endpoint console. Users log in with the seeded accounts (`customer`, `clerk`, `manager`, `ops`, `finance`, `courier`, `admin`, password `demo123`) and use module navigation for orders, files, quotations, job tickets, production tasks, inventory, delivery tasks, invoices, payments, audit logs, reports, and `/stats`. Business seed data should stay empty by default; keep only base stores, default inventory, and the seven accounts. Admin-only `DELETE /api/admin/business-data` clears generated business data while preserving those accounts and default inventory. Order-detail quick workflow actions may generate quotation, job ticket, production, delivery, payment, refund, and invoice records from the selected order, but must still enforce role permissions. Couriers accept and sign delivery tasks; they must not generate delivery tasks or manage order files. Audit logs are read-only.

For v1 design verification, keep the four-drift scope: architecture responsibility, dependency topology, API contract, and requirement/role coverage. The current contract is `obsidian-vault/wiki/summaries/API契约/OpenAPI-接口契约-v2.0.yaml`. Use `--write` only when intentionally regenerating `RCR逆向校验报告-v1.0.md`, `模块设计质量校验-v1.0.md`, or ADR-002~004.

For v2 change-management work, keep `generated-code/printshop-v1/` intact and make incremental changes under `generated-code/printshop-v2/`. CR-001 must route processed-order specification changes through `/api/orders/{orderId}/change-requests`; direct `PUT /api/orders/{id}` must not silently overwrite processed orders. Pending order changes must block production scheduling, production completion, and delivery creation until manager/admin approval or rejection. Regenerate `CRR变更回归校验报告-v2.0.md` with `verify-change-regression.js --write` only when intentionally updating the CRR artifact.

A2 is advisory in n8n workflow 1: severe issues become an `A2风险提示` node output and do not automatically call A1 rollback. Manual rollback remains available through the web UI or `/api/n8n/a2-rollback`.

A5 is a validation advisor, not an automatic gate-fixer. It writes `需求验证报告-vX.Y.md`; A4 may run one manual `/api/a4/revise-from-a5` pass, but there is no A3/A4/A5 auto-repair loop. CCB approval is handled by n8n Wait plus `obsidian-vault/wiki/summaries/n8n工作流/ccb-pending/`; use `一键CCB审批.bat` to resume. A6 may create a baseline with explicit risk acceptance, and has a deterministic RTM fallback if model-formatted baseline output fails.

## Commit & Pull Request Guidelines

Existing commits use concise imperative messages, for example `Add CrewAI requirements elicitation app` and `Organize vault and agent app directories`. Follow that style. PRs should describe the user-facing change, list verification commands, and mention any generated records or screenshots when UI behavior changes.

Generated notes, A2 reports, A3 UML files, A4 SRS drafts, A5 validation reports, A6 baselines, design-stage outputs, `ccb-pending/`, and Obsidian plugin/workspace state are project artifacts. Commit them only when the task explicitly asks for those outputs; otherwise keep commits scoped to source changes. Failed A4 repair drafts are intentionally not written to the vault.

## Security & Configuration Tips

API keys are entered in the webpage and must not be committed. A1/A2 default to `qwen3.6-flash`; A3/A4 use `qwen3.6-plus`; A5/A6/design-stage architecture use `qwen3.7-plus`. Keep `.env`, `.venv/`, `__pycache__/`, and local Obsidian plugin state out of source commits unless intentionally requested. Before pushing, search for leaked secrets:

```powershell
rg -n "token-prefix|api-key-env-name|test-key" -g "!*.git/**" -g "!**/.venv/**"
```
