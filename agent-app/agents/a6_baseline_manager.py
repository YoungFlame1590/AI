from __future__ import annotations

from datetime import datetime
import json
from pathlib import Path
import re
import shutil

from agents.a1a_stakeholders import PROJECT_NAME, STAKEHOLDERS
from agents.a1b_elicitor import run_task
from agents.a2_quality_analyzer import format_notes_for_analysis, read_note_records
from agents.a3_modeler import UML_DIR, _relative
from agents.a4_srs_writer import _latest_srs, _read_uml_files
from agents.a5_requirement_validator import _classify_report, _latest_a5_report
from agents.llm_config import A6_MODEL
from agents.recording import VAULT_ROOT


BASELINES_DIR = VAULT_ROOT / "wiki" / "baselines"
BASELINE_INDEX = BASELINES_DIR / "00-基线索引.md"
REQ_ID_RE = re.compile(r"\b(?:REQ|NFR)-[A-Z]+-\d{3}\b")


def _clean_markdown(value: str) -> str:
    text = value.strip()
    text = re.sub(r"^```(?:markdown|md)?\s*", "", text, flags=re.I)
    text = re.sub(r"\s*```$", "", text).strip()
    return text


def _escape_table_cell(value: str) -> str:
    return value.replace("\\", "\\\\").replace("|", "\\|").replace("\r\n", "<br>").replace("\n", "<br>").strip()


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
        raise ValueError("A6 输出不是可解析的 JSON，请重试创建基线。") from exc


def _extract_requirement_ids(text: str) -> list[str]:
    return sorted(set(REQ_ID_RE.findall(text)))


def _next_baseline_id(now: datetime | None = None) -> str:
    BASELINES_DIR.mkdir(parents=True, exist_ok=True)
    today = (now or datetime.now()).strftime("%Y%m%d")
    existing = []
    for path in BASELINES_DIR.iterdir():
        if not path.is_dir():
            continue
        match = re.fullmatch(rf"BL-{today}-(\d{{2}})", path.name)
        if match:
            existing.append(int(match.group(1)))
    return f"BL-{today}-{(max(existing) + 1 if existing else 1):02d}"


def _existing_baselines() -> list[dict[str, str]]:
    if not BASELINES_DIR.exists():
        return []
    baselines = []
    for path in sorted(BASELINES_DIR.iterdir()):
        if path.is_dir() and re.fullmatch(r"BL-\d{8}-\d{2}", path.name):
            baselines.append(
                {
                    "id": path.name,
                    "relativePath": _relative(path),
                    "created": datetime.fromtimestamp(path.stat().st_mtime).strftime("%Y-%m-%d %H:%M"),
                }
            )
    return baselines


def _latest_validation_status(srs: Path | None = None) -> dict:
    report = _latest_a5_report()
    if not report:
        return {
            "latestA5Report": "",
            "decision": "not_run",
            "message": "尚未运行 A5 验证。",
            "canCreateBaseline": False,
            "requiresA5RiskAcceptance": False,
        }
    report_text = report.read_text(encoding="utf-8")
    if srs:
        srs_ref = _relative(srs)
        report_is_stale = srs.stat().st_mtime > report.stat().st_mtime
        if report_is_stale:
            return {
                "latestA5Report": _relative(report),
                "decision": "stale",
                "message": f"最新 SRS（{srs_ref}）尚未经过 A5 复验，请先重新运行 A5 验证最新 SRS。",
                "canCreateBaseline": False,
                "requiresA5RiskAcceptance": False,
            }

    status = _classify_report(report_text)
    passed = status["decision"] in {"submit_ccb", "pass"}
    message = str(status["message"])
    if not passed:
        message = f"{message} 可由 CCB 带风险批准基线。"
    return {
        "latestA5Report": _relative(report),
        "decision": str(status["decision"]),
        "message": message,
        "canCreateBaseline": True,
        "requiresA5RiskAcceptance": not passed,
    }


def a6_status() -> dict:
    srs = _latest_srs()
    uml_files = _read_uml_files()
    validation = _latest_validation_status(srs)
    return {
        "latestSrs": _relative(srs) if srs else "",
        "latestA5Report": validation["latestA5Report"],
        "a5Decision": validation["decision"],
        "a5Message": validation["message"],
        "canCreateBaseline": validation["canCreateBaseline"],
        "requiresA5RiskAcceptance": validation["requiresA5RiskAcceptance"],
        "umlCount": len(uml_files),
        "nextBaselineId": _next_baseline_id(),
        "baselines": _existing_baselines(),
        "a6Model": A6_MODEL,
    }


def create_a6_agent(llm):
    from crewai import Agent

    return Agent(
        role="A6 需求基线智能体 - 需求基线管理员",
        goal=(
            "在 CCB 审批通过后，为图文快印门店连锁管理系统创建不可变需求基线，"
            "生成需求清单、RTM 溯源矩阵和基线完成确认。"
        ),
        backstory=(
            "你熟悉需求配置管理、基线管理和需求追溯。你不会修改已批准输入，"
            "只把已确认材料整理成可审计、可追溯、可冻结的基线快照。"
        ),
        llm=llm,
        verbose=False,
        allow_delegation=False,
    )


def _format_uml_inputs(files: list[dict[str, str]]) -> str:
    blocks = []
    for item in files:
        fence = "plantuml" if item["name"].endswith(".puml") else "markdown"
        blocks.append(f"文件：{item['relativePath']}\n```{fence}\n{item['content']}\n```")
    return "\n\n---\n\n".join(blocks)


def _build_baseline_note(
    baseline_id: str,
    ccb_conclusion: str,
    validation: dict,
    accept_a5_risks: bool,
    srs_path: Path,
    requirement_count: int,
    stakeholder_names: str,
    uml_count: int,
) -> str:
    now_text = datetime.now().strftime("%Y-%m-%d %H:%M")
    return f"""# 基线说明

| 字段 | 内容 |
|---|---|
| 基线版本号 | {baseline_id} |
| 创立时间 | {now_text} |
| CCB 审批结论 | {_escape_table_cell(ccb_conclusion)} |
| A5 验证报告 | {_escape_table_cell(str(validation["latestA5Report"]))} |
| A5 评审结论 | {_escape_table_cell(str(validation["decision"]))} |
| A5 风险接受 | {"是，CCB 带风险批准" if accept_a5_risks else "否，A5 已建议提交 CCB"} |
| SRS 来源 | {_relative(srs_path)} |
| 需求条目总数 | {requirement_count} 条 |
| 覆盖涉众 | {_escape_table_cell(stakeholder_names)} |
| 包含文件 | SRS正式版 / 需求清单 / UML模型{uml_count}个 / RTM / 基线创立确认 |
| 不可变声明 | 本目录内所有文件自创立之日起不得修改或删除 |
"""


def _validate_baseline_outputs(requirement_ids: list[str], requirement_list: str, rtm: str) -> None:
    if not requirement_list.strip() or not rtm.strip():
        raise ValueError("A6 未生成需求清单或 RTM。")

    missing_in_list = [req_id for req_id in requirement_ids if req_id not in requirement_list]
    if missing_in_list:
        raise ValueError(f"A6 生成的需求清单遗漏需求编号：{', '.join(missing_in_list)}。")

    missing_in_rtm = [req_id for req_id in requirement_ids if req_id not in rtm]
    if missing_in_rtm:
        raise ValueError(f"A6 生成的 RTM 遗漏需求编号：{', '.join(missing_in_rtm)}。")

    required_columns = ["REQ编号", "需求描述摘要", "来源涉众", "优先级", "所在SRS章节", "状态"]
    missing_columns = [column for column in required_columns if column not in rtm]
    if missing_columns:
        raise ValueError(f"A6 生成的 RTM 缺少必要列：{', '.join(missing_columns)}。")


def _write_baseline_index(baseline_id: str, ccb_conclusion: str, file_count: int, validation: dict, accept_a5_risks: bool) -> None:
    BASELINES_DIR.mkdir(parents=True, exist_ok=True)
    if BASELINE_INDEX.exists():
        text = BASELINE_INDEX.read_text(encoding="utf-8")
    else:
        text = (
            "# 基线索引\n\n"
            "正式基线必须在 CCB 审批通过后创建。基线目录命名格式为 `BL-YYYYMMDD-NN`。\n\n"
            "| 基线编号 | 创建日期 | 审批状态 | 包含产物 | 备注 |\n"
            "|---|---|---|---|---|\n"
        )

    lines = text.splitlines()
    lines = [line for line in lines if "| - | - | - | - | 尚未创建正式基线 |" not in line]
    risk_note = "带A5风险批准" if accept_a5_risks else "A5建议提交CCB"
    row = (
        f"| {baseline_id} | {datetime.now().strftime('%Y-%m-%d')} | "
        f"{_escape_table_cell(ccb_conclusion)} | {file_count} 个文件 | {risk_note}；A5={_escape_table_cell(str(validation['decision']))} |"
    )
    if row not in lines:
        lines.append(row)
    BASELINE_INDEX.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def _copy_uml_files(baseline_dir: Path, uml_files: list[dict[str, str]]) -> list[str]:
    uml_target = baseline_dir / "UML模型"
    uml_target.mkdir(parents=True, exist_ok=False)
    copied = []
    for item in uml_files:
        source = UML_DIR / item["name"]
        if not source.exists():
            continue
        target = uml_target / item["name"]
        shutil.copy2(source, target)
        copied.append(_relative(target))
    return copied


def create_baseline(llm, ccb_conclusion: str, accept_a5_risks: bool = False) -> dict:
    conclusion = ccb_conclusion.strip() or "通过（无保留意见）"
    srs = _latest_srs()
    if not srs:
        raise ValueError("缺少 SRS 初稿，请先运行 A4 生成或返修 SRS。")

    validation = _latest_validation_status(srs)
    if not validation["canCreateBaseline"]:
        raise ValueError(f"A5 尚未允许创建基线：{validation['message']}")
    if validation["requiresA5RiskAcceptance"] and not accept_a5_risks:
        raise ValueError("最新 A5 未给出可提交 CCB 结论；如需创建基线，请勾选“我确认接受当前 A5 风险并提交 CCB”。")

    uml_files = _read_uml_files()
    use_case_count = sum(1 for item in uml_files if item["name"].startswith("用例图") and item["name"].endswith(".puml"))
    activity_count = sum(1 for item in uml_files if item["name"].startswith("活动图-") and item["name"].endswith(".puml"))
    if use_case_count == 0 or activity_count == 0:
        raise ValueError("缺少 A3 UML 用例图或活动图，请先运行 A3 建模。")

    notes = read_note_records()
    srs_text = srs.read_text(encoding="utf-8")
    requirement_ids = _extract_requirement_ids(srs_text)
    if not requirement_ids:
        raise ValueError("SRS 中没有可纳入基线的 REQ/NFR 编号。")

    baseline_id = _next_baseline_id()
    stakeholder_names = "、".join(profile.name for profile in STAKEHOLDERS.values())
    agent = create_a6_agent(llm)
    description = f"""
## 输入材料

项目名称：{PROJECT_NAME}
基线版本号：{baseline_id}
CCB 审批结论：{conclusion}
A5 评审结论：{validation['decision']}
A5 风险接受：{"是，CCB 带风险批准" if accept_a5_risks else "否，A5 已建议提交 CCB"}
涉众范围：{stakeholder_names}

## SRS 正式版候选

来源：{_relative(srs)}

{srs_text}

## A5 验证报告

来源：{validation['latestA5Report']}

{_latest_a5_report().read_text(encoding="utf-8")}

## A3 UML 模型与建模决策

{_format_uml_inputs(uml_files)}

## A1 原始需求摘要

{format_notes_for_analysis(notes)}

## 任务

请为本次需求基线生成：
1. 需求清单 Markdown。
2. 需求溯源矩阵 RTM Markdown。
3. 基线创立确认 Markdown。

需求编号必须完整覆盖以下 SRS 编号：
{', '.join(requirement_ids)}

RTM 表格必须包含列：
REQ编号、需求描述摘要、来源涉众、优先级、所在SRS章节、对应用例（用例图）、对应活动图、设计模块（待填）、代码文件（待填）、测试用例（待填）、状态。

当前阶段设计模块、代码文件、测试用例统一填写“待填”。
状态统一填写“已基线”或“待实现”。
不要输出 Markdown 代码围栏，不要输出 JSON 外的解释文字。

输出 JSON：
{{
  "requirementListMarkdown": "完整需求清单 Markdown",
  "rtmMarkdown": "完整 RTM Markdown，含覆盖完整性/涉众覆盖/孤立需求自检",
  "confirmationMarkdown": "完整基线创立确认 Markdown"
}}
"""
    output = run_task(
        agent,
        description,
        "只输出可解析 JSON，包含需求清单、RTM 和基线创立确认 Markdown。",
    )
    payload = _extract_json(output)
    requirement_list = _clean_markdown(str(payload.get("requirementListMarkdown", "")))
    rtm = _clean_markdown(str(payload.get("rtmMarkdown", "")))
    confirmation = _clean_markdown(str(payload.get("confirmationMarkdown", "")))
    _validate_baseline_outputs(requirement_ids, requirement_list, rtm)

    baseline_dir = BASELINES_DIR / baseline_id
    if baseline_dir.exists():
        raise ValueError(f"基线目录已存在，拒绝覆盖：{_relative(baseline_dir)}")
    baseline_dir.mkdir(parents=True, exist_ok=False)

    files = []
    baseline_note = _build_baseline_note(
        baseline_id,
        conclusion,
        validation,
        accept_a5_risks,
        srs,
        len(requirement_ids),
        stakeholder_names,
        len(uml_files),
    )
    outputs = {
        "基线说明.md": baseline_note,
        "SRS-正式版.md": srs_text,
        "需求清单.md": requirement_list,
        "需求溯源矩阵-RTM.md": rtm,
        "基线创立确认.md": confirmation,
    }
    for name, content in outputs.items():
        target = baseline_dir / name
        target.write_text(content.rstrip() + "\n", encoding="utf-8")
        files.append(_relative(target))

    files.extend(_copy_uml_files(baseline_dir, uml_files))
    _write_baseline_index(baseline_id, conclusion, len(files), validation, accept_a5_risks)

    return {
        "baselineId": baseline_id,
        "relativePath": _relative(baseline_dir),
        "files": files,
        "model": A6_MODEL,
        "requirementCount": len(requirement_ids),
        "a5Decision": validation["decision"],
        "a5RiskAccepted": accept_a5_risks,
        "summary": f"A6 需求基线已创建：{_relative(baseline_dir)}，覆盖 {len(requirement_ids)} 条需求。",
    }
