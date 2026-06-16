# 图文快印门店连锁管理系统 · AI 实践仓库

本仓库按职责拆分为两个主目录：

```text
agent-app/          A1a/A1b/A2 CrewAI 需求获取与质量分析 Web 应用
obsidian-vault/     Obsidian 知识库与课程设计资产
```

根目录只保留总说明、`.gitignore` 和一键启动脚本。

## 快速启动

双击根目录脚本：

```text
启动需求获取页面.bat
```

脚本会：

1. 检查并关闭 `127.0.0.1:8000` 上的旧服务。
2. 创建或复用 `agent-app/.venv`。
3. 安装 `agent-app/requirements.txt`。
4. 启动本地页面并打开 `http://127.0.0.1:8000`。

网页中输入阿里云百炼 API key 后即可使用智能体。key 只在当前页面和请求中使用，不写入仓库或知识库。

## A2 需求质量分析

页面侧边栏包含 `A2 需求质量分析` 面板：

1. 点击 `运行 A2 质量分析`，系统读取 `obsidian-vault/raw/notes/` 的全部需求记录。
2. A2 报告保存到 `obsidian-vault/wiki/summaries/需求问题清单-vX.Y.md`。
3. 点击 `生成回退追问`，A2 会生成面向涉众的自然语言澄清问题。
4. 点击 `执行回退访谈`，系统调用对应 A1a 补充获取需求，并保存到 `obsidian-vault/raw/notes/`。

## 目录说明

### agent-app

- `app.py`：FastAPI 后端。
- `agents/`：A1a 涉众智能体、A1b 需求获取智能体、A2 质量分析智能体、百炼 LLM 配置、记录写入逻辑。
- `web/`：本地页面 HTML/CSS/JS。
- `requirements.txt`：Python 依赖。
- `.env.example`：可选模型与接口配置示例，不存放真实 key。

需求记录会写入：

```text
obsidian-vault/raw/notes/
```

### obsidian-vault

- `raw/notes/`：原始涉众访谈记录。
- `wiki/summaries/`：需求、UML、SRS、ADR、设计约束等阶段性产物。
- `wiki/baselines/`：经审批的正式基线。
- `templates/`：Obsidian 模板。
- `archive/`：归档材料。
- `compile.js`：知识库结构、链接、Markdown 和基线一致性检查脚本。

检查知识库：

```powershell
cd obsidian-vault
node compile.js
```

## 默认模型

- 模型：`qwen3.6-flash`
- 默认接口：`https://dashscope.aliyuncs.com/compatible-mode/v1`

如需覆盖：

```powershell
$env:BAILIAN_MODEL="qwen3.6-flash"
$env:BAILIAN_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
```
