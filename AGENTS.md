# Repository Guidelines

## Project Structure & Module Organization

This repository has two main work areas:

- `agent-app/`: FastAPI + CrewAI web app for A1a/A1b elicitation, A2 quality analysis, A3 UML modeling, A4 SRS drafting, A5 validation, A6 baselining, and design-stage architecture work.
  - `app.py`: API entrypoint and static web serving.
  - `agents/`: stakeholder agents, elicitation, quality analysis, UML modeling, SRS drafting, validation, baselining, design architecture, LLM config, and Obsidian record writing.
  - `web/`: plain HTML/CSS/JS frontend.
  - `requirements.txt`: Python dependencies.
- `obsidian-vault/`: Obsidian knowledge base.
  - `raw/notes/`: elicitation records named `{涉众角色}-{YYYYMMDD-HHMM}-需求记录.md`.
  - `wiki/summaries/`: A2 reports, UML `.puml` files, SRS drafts, validation reports, design-stage outputs, and other artifacts.
  - `wiki/baselines/`: approved A6 baseline snapshots named `BL-YYYYMMDD-NN`.
  - `templates/`: reusable Markdown templates.
  - `compile.js`: vault integrity checker.

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

For behavior changes, verify the local page at `http://127.0.0.1:8000`. A1/A2 writes records under `obsidian-vault/raw/notes/`; A3 writes UML outputs under `obsidian-vault/wiki/summaries/UML模型/`; A4 writes SRS drafts as `obsidian-vault/wiki/summaries/SRS-初稿-vX.Y.md`; A5 writes validation reports as `obsidian-vault/wiki/summaries/需求验证报告-vX.Y.md`; A6 writes approved baselines under `obsidian-vault/wiki/baselines/BL-YYYYMMDD-NN/`; design-stage architecture writes `知识图谱节点清单-vX.Y.md`, `架构选型报告-vX.Y.md`, `ASD-架构风格声明-vX.Y.md`, `MDS-模块划分方案-vX.Y.md`, `DTS-依赖拓扑-vX.Y.md`, and `ADR-001-架构选型-vX.Y.md`.

A2 is advisory in n8n workflow 1: severe issues become an `A2风险提示` node output and do not automatically call A1 rollback. Manual rollback remains available through the web UI or `/api/n8n/a2-rollback`.

A5 is a validation advisor, not an automatic gate-fixer. It writes `需求验证报告-vX.Y.md`; A4 may run one manual `/api/a4/revise-from-a5` pass, but there is no A3/A4/A5 auto-repair loop. CCB approval is handled by n8n Wait plus `obsidian-vault/wiki/summaries/n8n工作流/ccb-pending/`; use `一键CCB审批.bat` to resume. A6 may create a baseline with explicit risk acceptance, and has a deterministic RTM fallback if model-formatted baseline output fails.

## Commit & Pull Request Guidelines

Existing commits use concise imperative messages, for example `Add CrewAI requirements elicitation app` and `Organize vault and agent app directories`. Follow that style. PRs should describe the user-facing change, list verification commands, and mention any generated records or screenshots when UI behavior changes.

Generated notes, A2 reports, A3 UML files, A4 SRS drafts, A5 validation reports, A6 baselines, design-stage outputs, `ccb-pending/`, and Obsidian plugin/workspace state are project artifacts. Commit them only when the task explicitly asks for those outputs; otherwise keep commits scoped to source changes. Failed A4 repair drafts are intentionally not written to the vault.

## Security & Configuration Tips

API keys are entered in the webpage and must not be committed. A1/A2 default to `qwen3.6-flash`; A3/A4 use `qwen3.6-plus`; A5/A6/design-stage architecture use `qwen3.7-plus`. Keep `.env`, `.venv/`, `__pycache__/`, and local Obsidian plugin state out of source commits unless intentionally requested. Before pushing, search for leaked secrets:

```powershell
rg -n "ghp_|DASHSCOPE_API_KEY|test-key" -g "!*.git/**" -g "!**/.venv/**"
```
