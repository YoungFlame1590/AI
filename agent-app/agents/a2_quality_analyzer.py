from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re

from agents.a1a_stakeholders import PROJECT_NAME, STAKEHOLDERS
from agents.a1b_elicitor import ask_a1a, run_task
from agents.recording import REPO_ROOT, VAULT_ROOT, save_record


NOTES_DIR = VAULT_ROOT / "raw" / "notes"
SUMMARIES_DIR = VAULT_ROOT / "wiki" / "summaries"


@dataclass
class NoteRecord:
    filename: str
    stakeholder: str
    content: str


ROLE_TO_ID = {profile.name: profile.id for profile in STAKEHOLDERS.values()}
ROLE_TO_ID["配送外协人员"] = "delivery"


def read_note_records() -> list[NoteRecord]:
    if not NOTES_DIR.exists():
        return []

    records = []
    for path in sorted(NOTES_DIR.glob("*.md")):
        stakeholder = path.name.split("-", 1)[0]
        records.append(
            NoteRecord(
                filename=path.name,
                stakeholder=stakeholder,
                content=path.read_text(encoding="utf-8"),
            )
        )
    return records


def notes_summary() -> dict:
    records = read_note_records()
    by_stakeholder: dict[str, int] = {}
    for record in records:
        by_stakeholder[record.stakeholder] = by_stakeholder.get(record.stakeholder, 0) + 1

    return {
        "count": len(records),
        "stakeholders": by_stakeholder,
        "records": [
            {
                "filename": record.filename,
                "stakeholder": record.stakeholder,
                "chars": len(record.content),
            }
            for record in records
        ],
    }


def format_notes_for_analysis(records: list[NoteRecord]) -> str:
    blocks = []
    for record in records:
        blocks.append(f"【{record.filename}】\n涉众：{record.stakeholder}\n\n{record.content}")
    return "\n\n---\n\n".join(blocks)


def create_a2_agent(llm):
    from crewai import Agent

    return Agent(
        role="A2 需求质量分析智能体 - 需求问题审查员",
        goal=(
            "读取图文快印门店连锁管理系统的全部涉众需求记录，发现模糊、不一致、矛盾和冲突问题，"
            "输出可审计的结构化分析报告，并给出回退到 A1a 的澄清建议。"
        ),
        backstory=(
            "你熟悉需求工程质量审查。你只报告问题，不改写原始需求；"
            "每个判断都必须引用来源涉众和原始摘录，并说明为什么需要回退澄清。"
        ),
        llm=llm,
        verbose=False,
        allow_delegation=False,
    )


def next_report_path() -> Path:
    SUMMARIES_DIR.mkdir(parents=True, exist_ok=True)
    major = 1
    minor = 0
    while True:
        path = SUMMARIES_DIR / f"需求问题清单-v{major}.{minor}.md"
        if not path.exists():
            return path
        minor += 1


def save_report(content: str) -> dict[str, str]:
    path = next_report_path()
    path.write_text(content, encoding="utf-8")
    return {"path": str(path), "relativePath": str(path.relative_to(REPO_ROOT)).replace("\\", "/")}


def analyze_notes(llm) -> dict[str, str]:
    records = read_note_records()
    if not records:
        raise ValueError("obsidian-vault/raw/notes/ 中没有可分析的需求记录。")

    agent = create_a2_agent(llm)
    notes_text = format_notes_for_analysis(records)
    description = f"""
## 输入材料

以下是项目知识库 raw/notes/ 目录下的全部涉众需求记录：

项目名称：{PROJECT_NAME}

---

{notes_text}

---

## 你的任务

对上述所有需求记录进行逐条质量检测，执行以下四个维度的分析：

1. 模糊检测：识别不可量化词语，如尽快、尽量、尽可能、一般情况下、通常、大概、左右、适当、合理、很多、足够、及时、快速。
2. 不一致检测：检查订单状态、角色定义、时间/数值规范、格式规范等术语定义是否一致。
3. 矛盾检测：检查同一参数是否出现不同数值或逻辑上不能同时成立的要求。
4. 冲突检测：按订单接单、报价、生产流转、收款对账、门店运营、配送外协、权限审计等功能域检查涉众目标是否相互排斥。

## 输出格式

### 第一部分：需求问题清单

| 问题编号 | 来源涉众 | 原始需求摘录 | 问题类型 | 严重程度 | 问题描述 | 建议处理方式 |
|---------|---------|-----------|---------|---------|---------|-----------|

严重程度填写：低（模糊）/ 中（不一致）/ 高（矛盾）/ 紧急（冲突）

### 第二部分：每条问题的详细说明

每条问题单独一节，包含：
1. 为什么判定为该类型问题
2. 如果不解决，可能导致的系统实现后果
3. 建议的澄清路径（应该向哪个涉众追问什么）

### 第三部分：回退指令

列出需要重新获取需求的涉众清单。

### 第四部分：分析结论

[ ] 存在问题需求，流程回退至需求获取阶段
[ ] 所有需求通过分析，可进入A3建模阶段
"""
    report = run_task(
        agent,
        description,
        "严格按指定四部分输出 Markdown 需求质量分析报告。",
    )
    saved = save_report(report)
    return {**saved, "content": report}


def generate_rollback_plan(llm, report: str) -> dict[str, str]:
    if not report.strip():
        raise ValueError("缺少 A2 分析报告，无法生成回退追问。")

    agent = create_a2_agent(llm)
    description = f"""
根据下面的 A2 需求问题清单，针对需要回退的每个涉众，
生成该涉众的 A1a 智能体在下一轮对话中应该说的追问话术。

要求：
- 不使用“模糊”“不一致”“矛盾”“冲突”等需求工程术语
- 用该涉众熟悉的图文快印业务语言提问
- 每个问题自然地嵌入对话语境，不像在填问卷
- 每个涉众输出一段完整的对话开场白 + 2-3 个追问问题

格式：
## {{涉众角色}} 的追问话术
{{对话开场白}}
追问1：{{具体问题}}
追问2：{{具体问题}}
追问3：{{具体问题（如有）}}

A2 报告：
{report}
"""
    plan = run_task(agent, description, "按涉众分节输出回退追问话术 Markdown。")
    return {"content": plan}


def parse_rollback_plan(plan: str) -> list[dict]:
    items: list[dict] = []
    current_role: str | None = None
    current_questions: list[str] = []

    def flush():
        if current_role and current_questions:
            stakeholder_id = ROLE_TO_ID.get(current_role)
            if stakeholder_id:
                items.append(
                    {
                        "stakeholder": current_role,
                        "stakeholderId": stakeholder_id,
                        "questions": current_questions.copy(),
                    }
                )

    for raw_line in plan.splitlines():
        line = raw_line.strip()
        heading = re.match(r"^##\s+(.+?)\s*的追问话术", line)
        if heading:
            flush()
            current_role = heading.group(1).strip()
            current_questions = []
            continue

        question = re.match(r"^追问\d+[:：]\s*(.+)", line)
        if current_role and question:
            current_questions.append(question.group(1).strip())

    flush()
    return items


def run_rollback(llm, plan: str, selected_stakeholder_ids: list[str] | None = None) -> dict:
    parsed = parse_rollback_plan(plan)
    if not parsed:
        raise ValueError("未能从回退追问话术中解析出可执行的问题。")

    selected = set(selected_stakeholder_ids or [])
    results = []
    for item in parsed:
        stakeholder_id = item["stakeholderId"]
        if selected and stakeholder_id not in selected:
            continue

        from agents.a1a_stakeholders import create_a1a_agent

        agent = create_a1a_agent(stakeholder_id, llm)
        history: list[dict] = []
        for question in item["questions"]:
            answer = ask_a1a(agent, stakeholder_id, question, history)
            round_history = [
                {"speaker": "A2回退追问", "content": question},
                {"speaker": f"A1a-{item['stakeholder']}", "content": answer},
            ]
            saved = save_record(stakeholder_id, round_history)
            history.extend(round_history)
            results.append(
                {
                    "stakeholder": item["stakeholder"],
                    "question": question,
                    "answer": answer,
                    "recordPath": saved["relativePath"],
                }
            )

    return {"count": len(results), "results": results}
