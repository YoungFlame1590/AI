# Repository Guidelines

## Project Structure & Module Organization

This repository has two main work areas:

- `agent-app/`: FastAPI + CrewAI web app for A1a/A1b requirements elicitation.
  - `app.py`: API entrypoint and static web serving.
  - `agents/`: stakeholder agents, elicitation logic, LLM config, and Obsidian record writing.
  - `web/`: plain HTML/CSS/JS frontend.
  - `requirements.txt`: Python dependencies.
- `obsidian-vault/`: Obsidian knowledge base.
  - `raw/notes/`: elicitation records named `{涉众角色}-{YYYYMMDD-HHMM}-需求记录.md`.
  - `wiki/`: summaries, baselines, design artifacts.
  - `templates/`: reusable Markdown templates.
  - `compile.js`: vault integrity checker.

Root-level `启动需求获取页面.bat` is the main local launcher.

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

Start manually if needed:

```powershell
cd agent-app
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000
```

## Coding Style & Naming Conventions

Use 4-space indentation for Python and 2-space indentation for frontend files. Keep Python modules focused by responsibility. Use clear snake_case names in Python and camelCase in JavaScript. Do not hard-code API keys. Markdown records must follow the existing Chinese filename pattern so `compile.js` passes.

## Testing Guidelines

There is no formal test suite yet. Before committing, run:

```powershell
python -m compileall agent-app\agents agent-app\app.py
cd obsidian-vault; node compile.js
```

For behavior changes, verify the local page at `http://127.0.0.1:8000` and confirm new records are written under `obsidian-vault/raw/notes/`.

## Commit & Pull Request Guidelines

Existing commits use concise imperative messages, for example `Add CrewAI requirements elicitation app` and `Organize vault and agent app directories`. Follow that style. PRs should describe the user-facing change, list verification commands, and mention any generated records or screenshots when UI behavior changes.

## Security & Configuration Tips

API keys are entered in the webpage and must not be committed. Keep `.env`, `.venv/`, `__pycache__/`, and other local artifacts ignored. Before pushing, search for leaked secrets:

```powershell
rg -n "ghp_|DASHSCOPE_API_KEY|test-key" -g "!*.git/**" -g "!**/.venv/**"
```

