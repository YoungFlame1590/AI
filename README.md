# 图文快印门店连锁管理系统 · AI 实践仓库

本仓库按职责拆分为两个主目录：

```text
agent-app/          A1a/A1b/A2/A3/A4/A5/A6 与设计阶段 CrewAI Web 应用
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

## A3-A6 阶段产物

页面侧边栏继续提供后续需求工程智能体：

- `A3 UML 建模`：生成 PlantUML 用例图、活动图和建模决策说明。
- `A4 SRS 文档生成`：生成或按 A5 退回指令返修 `SRS-初稿-vX.Y.md`。
- `A5 需求验证`：生成 `需求验证报告-vX.Y.md`，作为 CCB 评审建议与风险清单。
- `A6 需求基线`：在 A5 已验证当前 SRS 后创建 `wiki/baselines/BL-YYYYMMDD-NN/`；若 A5 未通过，需要勾选风险确认后由 CCB 带风险批准。

## 设计阶段第一步

页面侧边栏包含 `设计阶段：知识图谱与架构选型` 面板。该智能体读取最新 A6 基线中的 `SRS-正式版.md` 和 `UML模型/` 快照，生成：

- `知识图谱节点清单-vX.Y.md`
- `架构选型报告-vX.Y.md`
- `ASD-架构风格声明-vX.Y.md`
- `MDS-模块划分方案-vX.Y.md`
- `DTS-依赖拓扑-vX.Y.md`
- `ADR-001-架构选型-vX.Y.md`

设计阶段以正式基线为唯一权威输入；没有 `BL-YYYYMMDD-NN` 基线时，请先完成 A6。

## 设计阶段第二步

页面侧边栏包含 `设计阶段：约束与接口契约` 面板。该智能体读取最新需求基线和第一步设计产物，生成：

- `设计约束/TLCD-三层约束文档-vX.Y.md`：职责、接口、依赖三类约束。
- `API契约/OpenAPI-接口契约-vX.Y.yaml`：OpenAPI 3.0.3 接口契约。
- `设计约束/约束提示词-vX.Y.md`：后续 AI 代码生成可复用的项目记忆、契约记忆和规则记忆。

第二步只生成设计资产，不生成业务代码，也不接入 n8n 工作流。

## v1 Spring Boot 业务工程

课程 STEP 12 的 v1 代码生成产物位于：

```text
generated-code/printshop-v1/
```

该工程是独立的 Spring Boot 3 + Java 17 准生产版 Print MIS 模块化单体，使用 MySQL 8、Spring Data JPA、Flyway 和 Spring Security，实现登录、订单、文件、报价、作业单、生产、库存、配送、发票、收款、审计、报表和 `/stats`。

启动数据库：

```powershell
cd generated-code\printshop-v1
docker compose up -d mysql
```

MySQL 默认映射到 `127.0.0.1:13306`，避免和本机已有的 `3306` MySQL 冲突。

启动系统：

```powershell
cd generated-code\printshop-v1
mvn spring-boot:run
```

如果 Docker 镜像拉取失败，可先用本地 H2 演示 profile：

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

验证：

```powershell
mvn test
mvn package
```

默认地址：`http://127.0.0.1:8080`。

页面入口：`http://127.0.0.1:8080/`。默认账号为 `customer/clerk/manager/ops/finance/courier/admin`，密码均为 `demo123`。登录后可进入面向角色的业务系统，使用左侧模块导航完成订单接单、文件上传、报价审批、作业单、排产、库存、配送、收款开票、审计和报表操作。审计日志保持只读查询。

设计漂移检测：

```powershell
node generated-code\printshop-v1\scripts\verify-design-drift.js --write
node generated-code\printshop-v1\scripts\verify-design-drift.js --check
```

检测覆盖架构职责、依赖拓扑、接口契约、需求与角色覆盖四种漂移，并生成 `RCR逆向校验报告-v1.0.md`、`模块设计质量校验-v1.0.md` 和 `ADR-002` 至 `ADR-004`。

## n8n 工作流

本仓库提供可导入 n8n 的需求开发全流程工作流：

```text
obsidian-vault/wiki/summaries/n8n工作流/需求开发全流程-工作流1.json
```

Docker Desktop 可用后，双击根目录脚本启动 n8n：

```text
启动n8n-Docker.bat
```

n8n 页面地址：

```text
http://localhost:5678
```

导入工作流后，Webhook 启动参数中的 `agentBaseUrl` 使用：

```json
{
  "agentBaseUrl": "http://host.docker.internal:8000"
}
```

这是因为 Docker 容器内的 `127.0.0.1` 指向容器自身，不是 Windows 宿主机。

导入并激活工作流后，可双击根目录脚本触发完整流程：

```text
一键启动n8n工作流.bat
```

脚本会检查 agent-app 与 n8n 是否在线，提示输入百炼 API key，并用正确的 JSON 请求启动 `requirements-workflow-1/start`。API key 只存在于本次命令内存中，不写入文件。

工作流会按 `A1 → A2 → A2风险提示 → A3 → A4 → A5 → CCB人工审批 → A6` 执行。A2 若发现高/紧急问题，只记录为风险提示并继续推进；如 CCB 或教师要求补访，可在页面手动执行 A2 回退后重新运行工作流。

当 n8n 停在 `CCB审批等待` 时，知识库会生成待审批文件：

```text
obsidian-vault/wiki/summaries/n8n工作流/ccb-pending/
```

此时双击根目录脚本提交 CCB 决策：

```text
一键CCB审批.bat
```

审批结论为 `approved` 或 `approvedWithRisk` 时，工作流继续执行 A6 创建基线；`rejected` 或 `revise` 不创建基线。

## 目录说明

### agent-app

- `app.py`：FastAPI 后端。
- `agents/`：A1a/A1b/A2/A3/A4/A5/A6、设计阶段智能体、百炼 LLM 配置、记录写入逻辑。
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

- A1/A2：`qwen3.6-flash`
- A3/A4：`qwen3.6-plus`
- A5/A6/设计阶段：`qwen3.7-plus`
- 默认接口：`https://dashscope.aliyuncs.com/compatible-mode/v1`

如需覆盖：

```powershell
$env:BAILIAN_MODEL="qwen3.6-flash"
$env:BAILIAN_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
```
