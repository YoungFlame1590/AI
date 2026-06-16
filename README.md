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

