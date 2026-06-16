from __future__ import annotations

from pathlib import Path
import re

from agents.a1a_stakeholders import PROJECT_NAME, STAKEHOLDERS
from agents.a1b_elicitor import run_task
from agents.a2_quality_analyzer import SUMMARIES_DIR, format_notes_for_analysis, read_note_records
from agents.a3_modeler import _relative
from agents.a4_srs_writer import _latest_srs, _read_uml_files
from agents.llm_config import A5_MODEL


def _version_key(path: Path) -> tuple[int, int]:
    match = re.search(r"-v(\d+)\.(\d+)\.md$", path.name)
    if not match:
        return (0, 0)
    return (int(match.group(1)), int(match.group(2)))


def _latest_a2_report() -> Path | None:
    reports = sorted(SUMMARIES_DIR.glob("需求问题清单-v*.md"), key=_version_key)
    return reports[-1] if reports else None


def _latest_a5_report() -> Path | None:
    reports = sorted(SUMMARIES_DIR.glob("需求验证报告-v*.md"), key=_version_key)
    return reports[-1] if reports else None


def _next_a5_report_path() -> Path:
    SUMMARIES_DIR.mkdir(parents=True, exist_ok=True)
    major = 1
    minor = 0
    while True:
        path = SUMMARIES_DIR / f"需求验证报告-v{major}.{minor}.md"
        if not path.exists():
            return path
        minor += 1


def _clean_markdown(value: str) -> str:
    text = value.strip()
    text = re.sub(r"^```(?:markdown|md)?\s*", "", text, flags=re.I)
    text = re.sub(r"\s*```$", "", text).strip()
    return text


def _format_uml_inputs(files: list[dict[str, str]]) -> str:
    blocks = []
    for item in files:
        fence = "plantuml" if item["name"].endswith(".puml") else "markdown"
        blocks.append(f"文件：{item['relativePath']}\n```{fence}\n{item['content']}\n```")
    return "\n\n---\n\n".join(blocks)


def _classify_report(content: str) -> dict[str, str | bool]:
    if "建议退回 A4" in content or "建议退回A4" in content:
        return {
            "decision": "return_a4",
            "message": "A5 建议退回 A4：无需重新获取涉众需求，先修订 SRS 后重新验证。",
            "canReviseA4": True,
        }
    if "建议退回 A1" in content or "建议退回A1" in content:
        return {
            "decision": "return_a1",
            "message": "A5 建议退回 A1：需要补充涉众访谈后再生成 SRS。",
            "canReviseA4": False,
        }
    if "建议直接提交 CCB" in content or "无问题" in content:
        return {
            "decision": "pass",
            "message": "A5 建议直接提交 CCB，当前验证通过。",
            "canReviseA4": False,
        }
    if "建议提交 CCB" in content:
        return {
            "decision": "submit_ccb",
            "message": "A5 建议提交 CCB 审批，并携带问题清单。",
            "canReviseA4": False,
        }
    return {
        "decision": "unknown",
        "message": "尚未识别 A5 评审结论。",
        "canReviseA4": False,
    }


def a5_status() -> dict:
    notes = read_note_records()
    uml_files = _read_uml_files()
    srs = _latest_srs()
    report = _latest_a5_report()
    report_status = _classify_report(report.read_text(encoding="utf-8")) if report else {
        "decision": "not_run",
        "message": "尚未运行 A5 验证。",
        "canReviseA4": False,
    }
    return {
        "notesCount": len(notes),
        "umlCount": len(uml_files),
        "latestSrs": _relative(srs) if srs else "",
        "latestA5Report": _relative(report) if report else "",
        "a5Model": A5_MODEL,
        **report_status,
    }


def create_a5_agent(llm):
    from crewai import Agent

    return Agent(
        role="A5 需求验证智能体 - SRS 交叉验证审查员",
        goal=(
            "对图文快印门店连锁管理系统的 SRS 初稿进行文档对文档交叉验证，"
            "发现遗漏、曲解、内部不一致和可追溯性问题，形成可供 CCB 审批参考的验证报告。"
        ),
        backstory=(
            "你熟悉需求验证与需求评审。你只检查文档之间是否一致、是否可追溯，"
            "不替涉众做业务合理性判断，也不直接修改任何输入文档。"
        ),
        llm=llm,
        verbose=False,
        allow_delegation=False,
    )


def _validate_report(content: str) -> None:
    required = [
        "比对一",
        "比对二",
        "比对三",
        "比对四",
        "问题汇总",
        "总体评审意见",
    ]
    missing = [item for item in required if item not in content]
    if missing:
        raise ValueError(f"A5 生成的验证报告缺少必要部分：{', '.join(missing)}。")


def validate_requirements(llm) -> dict:
    notes = read_note_records()
    if not notes:
        raise ValueError("obsidian-vault/raw/notes/ 中没有可验证的原始需求记录。")

    srs = _latest_srs()
    if not srs:
        raise ValueError("缺少 SRS 初稿，请先运行 A4 生成 SRS。")

    uml_files = _read_uml_files()
    use_case_count = sum(1 for item in uml_files if item["name"].startswith("用例图") and item["name"].endswith(".puml"))
    activity_count = sum(1 for item in uml_files if item["name"].startswith("活动图-") and item["name"].endswith(".puml"))
    if use_case_count == 0 or activity_count == 0:
        raise ValueError("缺少 A3 UML 用例图或活动图，请先运行 A3 建模。")

    a2_report = _latest_a2_report()
    stakeholders = "、".join(profile.name for profile in STAKEHOLDERS.values())
    notes_text = format_notes_for_analysis(notes)
    srs_text = srs.read_text(encoding="utf-8")
    uml_text = _format_uml_inputs(uml_files)
    a2_text = a2_report.read_text(encoding="utf-8") if a2_report else "未检测到 A2 需求问题清单。"
    agent = create_a5_agent(llm)

    description = f"""
## 输入材料

项目名称：{PROJECT_NAME}
涉众范围：{stakeholders}
重点业务领域：订单接单、报价、生产流转、收款对账、门店运营、配送外协、权限审计

## SRS 初稿

来源：{_relative(srs)}

{srs_text}

## A1 涉众对话记录

{notes_text}

## A2 需求质量分析

来源：{_relative(a2_report) if a2_report else "未检测到 A2 报告"}

{a2_text}

## A3 UML 模型

{uml_text}

## 历史需求文档

首次立项，无历史需求。

## 验证任务

你只做文档对文档检查，不判断需求业务合理性，不修改任何文档。
请生成 A5 需求验证报告，必须包含以下部分：

### 比对一：历史需求比对
无历史需求时标记“不适用”，并说明原因。

### 比对二：涉众对话比对
逐条追溯 SRS 功能需求到 raw/notes 原始对话，标注完全匹配、合理诠释、部分偏差、严重曲解。

### 比对三：项目文档比对
检查 SRS 中的用户特征、约束、假设与输入材料是否一致；如无额外项目章程，说明仅与 A1/A2/A3 材料比对。

### 比对四：SRS 内部一致性
检查术语、状态枚举、角色名称、数据字段、功能需求之间是否存在不一致或矛盾。

### 问题汇总统计
按阻塞性、重要、建议统计数量。

### 总体评审意见
按以下选项给出建议：
- 建议退回 A4（SRS 文本问题，重写后重新验证）
- 建议退回 A1（需求获取不足，需补充涉众对话）
- 建议提交 CCB 审批（附问题清单）
- 建议直接提交 CCB 审批（无问题）

### 退回指令
如需退回，列出退回 A4 或 A1 的具体修改/补充方向。

输出约束：
- 每个问题必须引用具体来源，至少包含文件名、章节、段落摘要或 REQ 编号。
- 严重程度只能使用：阻塞性、重要、建议。
- Markdown 表格行列数必须一致。
- 不要输出 Markdown 代码围栏，不要输出 JSON。
"""
    content = run_task(
        agent,
        description,
        "完整 Markdown A5 需求验证报告，包含四组比对、问题汇总、总体评审意见和退回指令。",
    )
    report = _clean_markdown(content)
    _validate_report(report)
    path = _next_a5_report_path()
    path.write_text(report + "\n", encoding="utf-8")
    return {
        "relativePath": _relative(path),
        "path": str(path),
        "model": A5_MODEL,
        "content": report,
        "summary": f"A5 需求验证报告已生成：{_relative(path)}",
    }
