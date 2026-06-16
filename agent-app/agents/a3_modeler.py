from __future__ import annotations

from datetime import datetime
import json
from pathlib import Path
import re

from agents.a1a_stakeholders import PROJECT_NAME, STAKEHOLDERS
from agents.a1b_elicitor import run_task
from agents.a2_quality_analyzer import SUMMARIES_DIR, format_notes_for_analysis, read_note_records
from agents.llm_config import A3_MODEL
from agents.recording import REPO_ROOT


UML_DIR = SUMMARIES_DIR / "UML模型"


def _relative(path: Path) -> str:
    return str(path.relative_to(REPO_ROOT)).replace("\\", "/")


def _version_key(path: Path) -> tuple[int, int]:
    match = re.search(r"-v(\d+)\.(\d+)\.md$", path.name)
    if not match:
        return (0, 0)
    return (int(match.group(1)), int(match.group(2)))


def latest_a2_report() -> Path | None:
    reports = sorted(SUMMARIES_DIR.glob("需求问题清单-v*.md"), key=_version_key)
    return reports[-1] if reports else None


def _next_versioned_path(directory: Path, stem: str, suffix: str) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    major = 1
    minor = 0
    while True:
        path = directory / f"{stem}-v{major}.{minor}{suffix}"
        if not path.exists():
            return path
        minor += 1


def _sanitize_filename(value: str) -> str:
    cleaned = re.sub(r"[\\/:*?\"<>|#`\[\]{}]", "", value).strip()
    cleaned = re.sub(r"\s+", "", cleaned)
    return cleaned[:40] or "核心用例"


def a3_status() -> dict:
    records = read_note_records()
    report = latest_a2_report()
    UML_DIR.mkdir(parents=True, exist_ok=True)
    files = []
    for path in sorted(UML_DIR.glob("*"), key=lambda item: item.stat().st_mtime, reverse=True):
        if path.is_file() and path.suffix.lower() in {".puml", ".md"}:
            files.append(
                {
                    "name": path.name,
                    "relativePath": _relative(path),
                    "modified": datetime.fromtimestamp(path.stat().st_mtime).strftime("%Y-%m-%d %H:%M"),
                }
            )
    return {
        "notesCount": len(records),
        "latestA2Report": _relative(report) if report else "",
        "a3Model": A3_MODEL,
        "umlFiles": files,
    }


def create_a3_agent(llm):
    from crewai import Agent

    return Agent(
        role="A3 建模智能体 - UML 建模师",
        goal=(
            "将图文快印门店连锁管理系统的原始需求记录和 A2 质量分析结果转化为可渲染、"
            "可追溯、可供下游智能体解析的 PlantUML UML 模型。"
        ),
        backstory=(
            "你熟悉需求建模、用例抽取和业务流程建模。你只根据输入材料建模，"
            "每条连线、include/extend 关系和活动图分支都要有业务理由；"
            "活动图必须覆盖正常路径、异常路径和边界路径。"
        ),
        llm=llm,
        verbose=False,
        allow_delegation=False,
    )


def _extract_json(text: str) -> dict:
    cleaned = text.strip()
    fenced = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", cleaned, flags=re.S)
    if fenced:
        cleaned = fenced.group(1)
    else:
        start = cleaned.find("{")
        end = cleaned.rfind("}")
        if start != -1 and end != -1 and end > start:
            cleaned = cleaned[start : end + 1]

    try:
        return json.loads(cleaned)
    except json.JSONDecodeError as exc:
        raise ValueError("A3 输出不是可解析的 JSON，请重试建模。") from exc


def _require_puml(value: str, label: str) -> str:
    text = re.sub(r"^```(?:plantuml|puml)?\s*", "", value.strip(), flags=re.I)
    text = re.sub(r"\s*```$", "", text).strip()
    if "@startuml" not in text or "@enduml" not in text:
        raise ValueError(f"A3 生成的{label}缺少 @startuml 或 @enduml。")
    return text


def _normalize_activity_puml(value: str, label: str) -> str:
    text = _require_puml(value, label)
    text = re.sub(r"^(\s*if)\s+(\[[^\]\r\n]+\])\s+then\b", r"\1 (\2) then", text, flags=re.M)
    text = re.sub(r"^(\s*elseif)\s+(\[[^\]\r\n]+\])\s+then\b", r"\1 (\2) then", text, flags=re.M)
    if re.search(r"^\s*(?:if|elseif)\s+\[[^\]\r\n]+\]\s+then\b", text, flags=re.M):
        raise ValueError(f"A3 生成的{label}仍包含不兼容的 if [条件] then 语法。")
    if not re.search(r"^\s*(?:stop|end)\s*$", text, flags=re.M):
        raise ValueError(f"A3 生成的{label}缺少明确终止节点 stop 或 end。")
    return text


def run_a3_modeling(llm) -> dict:
    records = read_note_records()
    if not records:
        raise ValueError("obsidian-vault/raw/notes/ 中没有可建模的需求记录。")

    report_path = latest_a2_report()
    report_text = (
        report_path.read_text(encoding="utf-8")
        if report_path
        else "未检测到 A2 报告。可以继续建模，但建议后续补充 A2 质量分析结论。"
    )
    notes_text = format_notes_for_analysis(records)
    stakeholders = "、".join(profile.name for profile in STAKEHOLDERS.values())
    agent = create_a3_agent(llm)

    description = f"""
## 输入材料

项目名称：{PROJECT_NAME}
涉众范围：{stakeholders}
业务领域：订单接单、报价、生产流转、收款对账、门店运营、配送外协、权限审计

## A2 需求质量分析

来源：{_relative(report_path) if report_path else "未检测到 A2 报告"}

{report_text}

## 原始需求记录

{notes_text}

## 任务

请一次性生成 UML 建模产物：
1. 用例图 PlantUML：识别 Actor、系统边界、核心用例、include/extend 关系。
2. 核心用例活动图 PlantUML：选择 3 到 6 个最关键用例，每个用例一张活动图。
3. 建模决策说明 Markdown：包含涉众到 Actor 映射、用例合并/拆分理由、路径覆盖清单。

约束：
- 所有 PlantUML 必须能独立渲染，包含 @startuml 和 @enduml。
- 活动图必须使用泳道，必须包含开始/结束节点。
- 每个分支必须使用方括号 Guard Condition。
- 活动图条件必须写成 if ([Guard Condition]) then (是)。
- 活动图 elseif 必须写成 elseif ([Guard Condition]) then (是)。
- 禁止输出 if [Guard Condition] then (是) 或 elseif [Guard Condition] then (是)。
- 每张活动图至少包含一个明确终止节点 stop 或 end。
- 不输出图片描述，不生成 SRS。
- 只依据输入材料，不能编造与图文快印业务无关的功能。
- 建模决策说明不要使用 Obsidian 双向链接；如使用 Markdown 表格，所有行列数必须一致。
- PlantUML 字符串内部不要包含 Markdown 代码围栏。

输出必须是一个 JSON 对象，不要输出 JSON 以外的解释文字：
{{
  "summary": "一句话说明生成内容",
  "useCaseDiagram": "完整 PlantUML 字符串",
  "activityDiagrams": [
    {{"name": "接单报价", "plantuml": "完整 PlantUML 字符串"}}
  ],
  "decisionMarkdown": "完整 Markdown 说明"
}}
"""
    output = run_task(
        agent,
        description,
        "只输出可解析 JSON，包含用例图、活动图数组、建模决策说明。",
    )
    payload = _extract_json(output)

    use_case = _require_puml(str(payload.get("useCaseDiagram", "")), "用例图")
    activities = payload.get("activityDiagrams", [])
    if not isinstance(activities, list) or not activities:
        raise ValueError("A3 未生成活动图。")

    saved_files = []
    use_case_path = _next_versioned_path(UML_DIR, "用例图", ".puml")
    use_case_path.write_text(use_case + "\n", encoding="utf-8")
    saved_files.append(_relative(use_case_path))

    activity_names = []
    for item in activities:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name", "")).strip() or "核心用例"
        plantuml = _normalize_activity_puml(str(item.get("plantuml", "")), f"活动图-{name}")
        path = _next_versioned_path(UML_DIR, f"活动图-{_sanitize_filename(name)}", ".puml")
        path.write_text(plantuml + "\n", encoding="utf-8")
        saved_files.append(_relative(path))
        activity_names.append(name)

    if not activity_names:
        raise ValueError("A3 未生成有效活动图。")

    decision = str(payload.get("decisionMarkdown", "")).strip()
    if not decision:
        raise ValueError("A3 未生成建模决策说明。")
    decision_path = _next_versioned_path(UML_DIR, "建模决策说明", ".md")
    decision_path.write_text(decision + "\n", encoding="utf-8")
    saved_files.append(_relative(decision_path))

    summary = str(payload.get("summary", "")).strip() or (
        f"A3 建模完成：用例图 1 张，活动图 {len(activity_names)} 张。"
    )
    return {
        "summary": summary,
        "model": A3_MODEL,
        "latestA2Report": _relative(report_path) if report_path else "",
        "files": saved_files,
        "activityDiagrams": activity_names,
        "decisionMarkdown": decision,
    }
