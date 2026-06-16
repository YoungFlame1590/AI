# 图文快印门店连锁管理系统 Obsidian 知识库

本知识库用于支撑《高级软件设计实践》的完整产物管理，从需求获取、需求分析、UML 建模、SRS、需求基线，到架构设计、接口契约、代码生成、逆向校验、变更管理和资产归档。

## 目录结构

```text
raw/
  notes/                 原始涉众访谈与需求记录
wiki/
  summaries/             分析提炼产物和阶段性草稿
  baselines/             经 CCB 审批后的正式基线
archive/                 废弃草稿、历史材料和过期版本
templates/               Obsidian 模板
compile.js               知识库结构与内容检查脚本
```

## 四层规则

- `raw/notes/` 只保存原始记录，不直接修改为正式需求。
- `wiki/summaries/` 保存可迭代分析产物，文件名使用 `{文档类型}-v{版本号}.md`。
- `wiki/baselines/` 保存正式基线，目录名使用 `BL-YYYYMMDD-NN`。
- `archive/` 保存被替换、废弃或不再活跃的材料。

## 命名规范

- 原始需求记录：`{涉众角色}-{YYYYMMDD-HHMM}-需求记录.md`
- 汇总产物：`{文档类型}-v{版本号}.md`
- 基线目录：`BL-YYYYMMDD-NN`

## 推荐工作流

1. 在 `raw/notes/` 中记录涉众访谈。
2. 在 `wiki/summaries/` 中生成需求清单、问题清单、UML、SRS 等产物。
3. 通过验证后，在 `wiki/baselines/` 中创建正式基线。
4. 后续设计、接口、代码和变更产物继续写入 `wiki/summaries/`。
5. 废弃材料移动到 `archive/`。
6. 每次提交前运行：

```bash
node compile.js
```

## A1a/A1b 需求获取页面

本知识库包含一个本地 Web 应用，用于通过 CrewAI 和阿里云百炼完成需求获取：

- A1a：七类涉众智能体，模拟门店店员、门店店长、总部运营管理员、财务人员、客户、配送/外协人员、系统管理员。
- A1b：需求获取智能体，可半自动向 A1a 追问，并帮助收集原始需求。
- 默认模型：`qwen3.6-flash`。
- 默认接口：`https://dashscope.aliyuncs.com/compatible-mode/v1`。

### 一键启动

双击：

```text
启动需求获取页面.bat
```

脚本会自动创建 `.venv`、安装依赖、启动本地服务，并打开：

```text
http://127.0.0.1:8000
```

### API key 使用方式

在网页顶部输入阿里云百炼 API key 后，才能使用 A1a/A1b 智能体。key 只在当前浏览器页面和本次请求中使用，不写入 `.env`、Markdown 或仓库。

如需覆盖默认模型或接口，可设置环境变量：

```powershell
$env:BAILIAN_MODEL="qwen3.6-flash"
$env:BAILIAN_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
```

### 保存记录

点击“保存到 Obsidian”后，会生成：

```text
raw/notes/{涉众角色}-{YYYYMMDD-HHMM}-需求记录.md
```

保存后建议运行：

```bash
node compile.js
```
