from __future__ import annotations

from datetime import datetime
from pathlib import Path
import re

from agents.a1a_stakeholders import PROJECT_NAME, STAKEHOLDERS
from agents.a1b_elicitor import run_task
from agents.a2_quality_analyzer import SUMMARIES_DIR, format_notes_for_analysis, read_note_records
from agents.a3_modeler import UML_DIR, _relative, latest_a2_report
from agents.llm_config import A4_MODEL
from agents.recording import REPO_ROOT


def _version_key(path: Path) -> tuple[int, int]:
    match = re.search(r"-v(\d+)\.(\d+)\.md$", path.name)
    if not match:
        return (0, 0)
    return (int(match.group(1)), int(match.group(2)))


def _next_srs_path() -> Path:
    SUMMARIES_DIR.mkdir(parents=True, exist_ok=True)
    major = 1
    minor = 0
    while True:
        path = SUMMARIES_DIR / f"SRS-初稿-v{major}.{minor}.md"
        if not path.exists():
            return path
        minor += 1


def _latest_srs() -> Path | None:
    files = sorted(SUMMARIES_DIR.glob("SRS-初稿-v*.md"), key=_version_key)
    return files[-1] if files else None


def _read_uml_files() -> list[dict[str, str]]:
    if not UML_DIR.exists():
        return []
    files = []
    for path in sorted(UML_DIR.glob("*")):
        if path.is_file() and path.suffix.lower() in {".puml", ".md"} and path.name != ".gitkeep":
            files.append(
                {
                    "name": path.name,
                    "relativePath": _relative(path),
                    "content": path.read_text(encoding="utf-8"),
                }
            )
    return files


def _format_uml_inputs(files: list[dict[str, str]]) -> str:
    blocks = []
    for item in files:
        fence = "plantuml" if item["name"].endswith(".puml") else "markdown"
        blocks.append(f"文件：{item['relativePath']}\n```{fence}\n{item['content']}\n```")
    return "\n\n---\n\n".join(blocks)


def a4_status() -> dict:
    notes = read_note_records()
    report = latest_a2_report()
    uml_files = _read_uml_files()
    latest_srs = _latest_srs()
    return {
        "notesCount": len(notes),
        "latestA2Report": _relative(report) if report else "",
        "umlCount": len(uml_files),
        "umlFiles": [
            {
                "name": item["name"],
                "relativePath": item["relativePath"],
            }
            for item in uml_files
        ],
        "latestSrs": _relative(latest_srs) if latest_srs else "",
        "a4Model": A4_MODEL,
    }


def create_a4_agent(llm):
    from crewai import Agent

    return Agent(
        role="A4 需求文档智能体 - SRS 编写员",
        goal=(
            "将图文快印门店连锁管理系统的原始需求、A2 质量分析、A3 UML 模型整合为"
            "结构完整、编号连续、可测试的软件需求规格说明书初稿。"
        ),
        backstory=(
            "你熟悉 IEEE 830 风格的软件需求规格说明书。你强调可追溯、可验证、"
            "可量化，禁止把具体数字改写成模糊形容词。"
        ),
        llm=llm,
        verbose=False,
        allow_delegation=False,
    )


def _clean_markdown(value: str) -> str:
    text = value.strip()
    text = re.sub(r"^```(?:markdown|md)?\s*", "", text, flags=re.I)
    text = re.sub(r"\s*```$", "", text).strip()
    return text


def _validate_srs(content: str) -> None:
    required = ["# 软件需求规格说明书", "1. 引言", "2. 总体描述", "3. 具体需求"]
    missing = [item for item in required if item not in content]
    if missing:
        raise ValueError(f"A4 生成的 SRS 缺少必要章节：{', '.join(missing)}。")
    if "REQ-" not in content:
        raise ValueError("A4 生成的 SRS 缺少 REQ 编号。")
    if "来源涉众覆盖检查" not in content:
        raise ValueError("A4 生成的 SRS 缺少来源涉众覆盖检查。")


def generate_srs(llm) -> dict:
    notes = read_note_records()
    if not notes:
        raise ValueError("obsidian-vault/raw/notes/ 中没有可写入 SRS 的需求记录。")

    a2_report = latest_a2_report()
    if not a2_report:
        raise ValueError("缺少 A2 需求问题清单，请先运行 A2 质量分析。")

    uml_files = _read_uml_files()
    use_case_count = sum(1 for item in uml_files if item["name"].startswith("用例图") and item["name"].endswith(".puml"))
    activity_count = sum(1 for item in uml_files if item["name"].startswith("活动图-") and item["name"].endswith(".puml"))
    decision_count = sum(1 for item in uml_files if item["name"].startswith("建模决策说明") and item["name"].endswith(".md"))
    if use_case_count == 0 or activity_count == 0:
        raise ValueError("缺少 A3 UML 用例图或活动图，请先运行 A3 建模。")
    if decision_count == 0:
        raise ValueError("缺少 A3 建模决策说明，请先运行 A3 建模。")

    stakeholders = "、".join(profile.name for profile in STAKEHOLDERS.values())
    notes_text = format_notes_for_analysis(notes)
    a2_text = a2_report.read_text(encoding="utf-8")
    uml_text = _format_uml_inputs(uml_files)
    today = datetime.now().strftime("%Y-%m-%d")
    agent = create_a4_agent(llm)

    description = f"""
## 输入材料

项目名称：{PROJECT_NAME}
涉众范围：{stakeholders}
重点业务领域：订单接单、报价、生产流转、收款对账、门店运营、配送外协、权限审计
生成日期：{today}

## A1 原始需求记录

{notes_text}

## A2 需求质量分析

来源：{_relative(a2_report)}

{a2_text}

## A3 UML 模型与建模决策

{uml_text}

## 任务

请直接生成完整的软件需求规格说明书 SRS 初稿，遵循 IEEE 830 风格章节。

必须包含：
- 标题：# 软件需求规格说明书
- 项目名称、文档版本 v1.0-draft、生成日期
- 1. 引言：目的、范围、定义与缩略语、参考文献、概述
- 2. 总体描述：产品视角、产品功能概述、用户特征、约束条件、假设与依赖
- 3. 具体需求：功能需求、非功能需求、接口需求、数据需求
- 每条功能需求使用唯一 REQ 编号，格式 REQ-模块缩写-三位序号
- 每条功能需求包含：描述、输入、输出、前置条件、验收标准、来源涉众、优先级
- 数据字典表：字段名、数据类型、长度/范围、是否必填、说明
- 文档末尾附需求编号索引表
- 文档末尾附来源涉众覆盖检查

写作约束：
- 只依据输入材料生成，不编造与图文快印门店连锁管理无关的功能。
- 保留原始需求中的具体数字、时间、百分比，不要改写成模糊词。
- 禁止在需求条目中使用：尽快、尽量、尽可能、通常、大概、适当、合理、快速、及时、良好、足够。
- Markdown 表格行列数必须一致。
- 不要输出 Markdown 代码围栏，不要输出 JSON。

输出完整 Markdown 文档正文。
"""
    content = run_task(
        agent,
        description,
        "完整 Markdown SRS 初稿，含 IEEE 830 风格章节、REQ 编号、数据字典、编号索引和涉众覆盖检查。",
    )
    srs = _clean_markdown(content)
    _validate_srs(srs)
    path = _next_srs_path()
    path.write_text(srs + "\n", encoding="utf-8")
    return {
        "relativePath": _relative(path),
        "path": str(path),
        "model": A4_MODEL,
        "content": srs,
        "summary": f"A4 SRS 初稿已生成：{_relative(path)}",
    }
