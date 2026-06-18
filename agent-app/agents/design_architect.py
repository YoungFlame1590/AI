from __future__ import annotations

from datetime import datetime
import json
from pathlib import Path
import re

from agents.a1a_stakeholders import PROJECT_NAME, STAKEHOLDERS
from agents.a1b_elicitor import run_task
from agents.a3_modeler import _relative
from agents.a6_baseline_manager import BASELINES_DIR
from agents.a2_quality_analyzer import SUMMARIES_DIR
from agents.llm_config import DESIGN_MODEL


BASELINE_RE = re.compile(r"BL-\d{8}-\d{2}")
DESIGN_OUTPUTS = {
    "knowledgeGraph": "知识图谱节点清单",
    "architectureReport": "架构选型报告",
    "asd": "ASD-架构风格声明",
    "mds": "MDS-模块划分方案",
    "dts": "DTS-依赖拓扑",
    "adr": "ADR-001-架构选型",
}


def _version_key(path: Path) -> tuple[int, int]:
    match = re.search(r"-v(\d+)\.(\d+)(?=\.md$)", path.name)
    if not match:
        return (0, 0)
    return (int(match.group(1)), int(match.group(2)))


def _latest_baseline() -> Path | None:
    if not BASELINES_DIR.exists():
        return None
    baselines = [
        path
        for path in BASELINES_DIR.iterdir()
        if path.is_dir() and BASELINE_RE.fullmatch(path.name)
    ]
    return sorted(baselines, key=lambda item: item.name)[-1] if baselines else None


def _next_versioned_markdown(stem: str) -> Path:
    SUMMARIES_DIR.mkdir(parents=True, exist_ok=True)
    major = 1
    minor = 0
    while True:
        path = SUMMARIES_DIR / f"{stem}-v{major}.{minor}.md"
        if not path.exists():
            return path
        minor += 1


def _latest_design_outputs() -> list[dict[str, str]]:
    files = []
    for stem in DESIGN_OUTPUTS.values():
        matches = sorted(SUMMARIES_DIR.glob(f"{stem}-v*.md"), key=_version_key)
        if matches:
            path = matches[-1]
            files.append({"name": path.name, "relativePath": _relative(path)})
    return files


def _read_baseline_inputs() -> dict:
    baseline = _latest_baseline()
    if not baseline:
        raise ValueError("未找到需求基线目录 BL-YYYYMMDD-NN，请先完成 A6 需求基线。")

    srs_path = baseline / "SRS-正式版.md"
    if not srs_path.exists():
        raise ValueError(f"最新基线 {baseline.name} 缺少 SRS-正式版.md。")

    uml_dir = baseline / "UML模型"
    if not uml_dir.exists():
        raise ValueError(f"最新基线 {baseline.name} 缺少 UML模型/ 目录。")

    uml_files = [
        path
        for path in sorted(uml_dir.iterdir(), key=lambda item: item.name)
        if path.is_file() and path.suffix.lower() in {".puml", ".md"}
    ]
    use_case_count = sum(1 for path in uml_files if path.name.startswith("用例图") and path.suffix.lower() == ".puml")
    activity_count = sum(1 for path in uml_files if path.name.startswith("活动图-") and path.suffix.lower() == ".puml")
    if use_case_count == 0 or activity_count == 0:
        raise ValueError(f"最新基线 {baseline.name} 的 UML模型/ 至少需要 1 个用例图和 1 个活动图。")

    return {
        "baseline": baseline,
        "srsPath": srs_path,
        "srsText": srs_path.read_text(encoding="utf-8"),
        "umlFiles": [
            {
                "name": path.name,
                "relativePath": _relative(path),
                "content": path.read_text(encoding="utf-8"),
            }
            for path in uml_files
        ],
        "useCaseCount": use_case_count,
        "activityCount": activity_count,
    }


def _format_uml_inputs(files: list[dict[str, str]]) -> str:
    blocks = []
    for item in files:
        fence = "plantuml" if item["name"].endswith(".puml") else "markdown"
        blocks.append(f"文件：{item['relativePath']}\n```{fence}\n{item['content']}\n```")
    return "\n\n---\n\n".join(blocks)


def _extract_json(text: str) -> dict:
    cleaned = text.strip()
    fenced = re.search(r"```(?:json)?\s*(.*?)\s*```", cleaned, flags=re.S | re.I)
    candidates = [fenced.group(1).strip()] if fenced else []
    candidates.append(cleaned)

    decoder = json.JSONDecoder()
    for candidate in candidates:
        direct = candidate.strip()
        if not direct:
            continue
        try:
            payload = json.loads(direct)
            if isinstance(payload, dict):
                return payload
        except json.JSONDecodeError:
            pass

        for match in re.finditer(r"\{", direct):
            try:
                payload, _ = decoder.raw_decode(direct[match.start() :])
            except json.JSONDecodeError:
                continue
            if isinstance(payload, dict):
                return payload

    raise ValueError("设计阶段智能体输出不是可解析的 JSON，请重试。")


def _run_json_task(agent, description: str) -> dict:
    output = run_task(agent, description, "只输出可解析 JSON，包含六份设计阶段 Markdown 产物。")
    try:
        return _extract_json(output)
    except ValueError:
        retry = f"""{description}

## 输出格式纠偏

上一次输出不是合法 JSON。请重新输出，严格遵守：
- 只输出一个 JSON 对象。
- 不要输出 Markdown 代码围栏。
- 不要在 JSON 前后添加解释文字。
- 顶层必须包含 summary、selectedArchitecture、nodeCounts、artifacts。
- artifacts 必须包含 knowledgeGraph、architectureReport、asd、mds、dts、adr 六个 Markdown 字符串。
"""
        return _extract_json(run_task(agent, retry, "只输出合法 JSON 对象。"))


def _clean_markdown(value: str) -> str:
    text = str(value or "").strip()
    text = re.sub(r"^```(?:markdown|md)?\s*", "", text, flags=re.I)
    text = re.sub(r"\s*```$", "", text).strip()
    return text


def _validate_artifacts(artifacts: dict[str, str], selected_architecture: str) -> None:
    missing = [key for key in DESIGN_OUTPUTS if not _clean_markdown(artifacts.get(key, ""))]
    if missing:
        names = ", ".join(DESIGN_OUTPUTS[key] for key in missing)
        raise ValueError(f"设计阶段智能体未生成必要产物：{names}。")

    architecture_report = artifacts["architectureReport"]
    dimension_markers = ["功能复杂度", "并发", "性能", "可扩展", "团队", "运维"]
    dimension_hits = sum(1 for marker in dimension_markers if marker in architecture_report)
    has_scoring = "加权总分" in architecture_report or "评分" in architecture_report or dimension_hits >= 4
    has_recommendation = (
        "推荐架构" in architecture_report
        or "选定架构" in architecture_report
        or "最终决策" in architecture_report
        or bool(selected_architecture.strip())
    )
    if not has_scoring or not has_recommendation:
        raise ValueError("架构选型报告缺少可识别的评分依据或推荐架构结论。")

    adr = artifacts["adr"]
    if "重新审视条件" not in adr:
        raise ValueError("ADR-001 必须包含“重新审视条件”。")

    dts = artifacts["dts"]
    if "```mermaid" not in dts or "graph TD" not in dts:
        raise ValueError("DTS 必须包含 Mermaid 依赖拓扑代码块。")


def _count_nodes(node_counts: dict | None, knowledge_graph: str) -> dict[str, int]:
    defaults = {"actor": 0, "component": 0, "interface": 0, "data": 0, "constraint": 0}
    if isinstance(node_counts, dict):
        for key in defaults:
            try:
                defaults[key] = max(0, int(node_counts.get(key, 0)))
            except (TypeError, ValueError):
                defaults[key] = 0

    if sum(defaults.values()) == 0:
        patterns = {
            "actor": r"\bActor::",
            "component": r"\bComponent::",
            "interface": r"\bInterface::",
            "data": r"\bData::",
            "constraint": r"\bConstraint::",
        }
        for key, pattern in patterns.items():
            defaults[key] = len(re.findall(pattern, knowledge_graph))

    defaults["total"] = sum(defaults.values())
    return defaults


def design_status() -> dict:
    try:
        inputs = _read_baseline_inputs()
        error = ""
        latest_baseline = _relative(inputs["baseline"])
        srs_path = _relative(inputs["srsPath"])
        uml_count = len(inputs["umlFiles"])
        use_case_count = inputs["useCaseCount"]
        activity_count = inputs["activityCount"]
    except ValueError as exc:
        error = str(exc)
        latest_baseline = ""
        srs_path = ""
        uml_count = 0
        use_case_count = 0
        activity_count = 0

    return {
        "latestBaseline": latest_baseline,
        "srsPath": srs_path,
        "umlCount": uml_count,
        "useCaseCount": use_case_count,
        "activityCount": activity_count,
        "designOutputs": _latest_design_outputs(),
        "canRun": not error,
        "message": error or "设计阶段输入已就绪。",
        "designModel": DESIGN_MODEL,
    }


def create_design_agent(llm):
    from crewai import Agent

    return Agent(
        role="设计阶段架构智能体 - 软件架构师",
        goal=(
            "基于图文快印门店连锁管理系统的正式需求基线，构建知识图谱，"
            "执行五维度架构选型评估，并生成 ASD、MDS、DTS 与 ADR-001。"
        ),
        backstory=(
            "你是一名注重证据链的软件架构师。你的所有节点、边、评分和架构决策"
            "都必须引用 SRS 或 UML 来源，不接受无依据的直觉判断。"
        ),
        llm=llm,
        verbose=False,
        allow_delegation=False,
    )


def run_design_architecture(llm) -> dict:
    inputs = _read_baseline_inputs()
    stakeholders = "、".join(profile.name for profile in STAKEHOLDERS.values())
    today = datetime.now().strftime("%Y-%m-%d")
    agent = create_design_agent(llm)

    description = f"""
## 固定项目上下文

项目名称：{PROJECT_NAME}
涉众范围：{stakeholders}
业务领域：订单接单、报价、生产流转、收款对账、门店运营、配送外协、权限审计
设计阶段：第一步，知识图谱构建 + 五维度架构选型评估
生成日期：{today}

## 权威输入

最新需求基线：{inputs["baseline"].name}
SRS 正式版：{_relative(inputs["srsPath"])}

```markdown
{inputs["srsText"]}
```

## UML 模型快照

{_format_uml_inputs(inputs["umlFiles"])}

## 任务要求

请完成以下产物，所有结论必须引用 SRS 章节、REQ/NFR 编号或 UML 文件依据：

1. 知识图谱节点清单：提取 Actor、Component、Interface、Data、Constraint 五类节点，建立 USES、PROVIDES、DEPENDS_ON、READS、WRITES、CONSTRAINED_BY 边，并完成覆盖自检。
2. 架构选型报告：对单体架构、分层架构、事件驱动架构、微服务架构进行五维度评分，维度权重为功能复杂度20%、并发与性能25%、可扩展性20%、团队规模20%、运维能力15%。
   - 必须包含小节标题“## 五维度评分”。
   - 必须包含小节标题“## 推荐架构风格”。
   - 必须包含“加权总分”表格列。
3. ASD 架构风格声明：给出选定风格、核心原则、强制约束、推荐实践和明确禁止项。
4. MDS 模块划分方案：给出模块职责、不属于本模块的职责、接口摘要、依赖模块，以及完备性、正确性、一致性、有效性自检。
5. DTS 依赖拓扑：给出合法依赖白名单、禁止依赖黑名单，并包含 Mermaid `graph TD` 拓扑图。
6. ADR-001 架构选型：包含状态、决策日期、背景、候选方案对比、最终决策、后续设计约束、重新审视条件和备注。

输出必须是一个 JSON 对象，不要输出 JSON 以外的解释文字：
{{
  "summary": "阶段完成摘要",
  "selectedArchitecture": "推荐架构风格",
  "nodeCounts": {{"actor": 0, "component": 0, "interface": 0, "data": 0, "constraint": 0}},
  "artifacts": {{
    "knowledgeGraph": "完整 Markdown",
    "architectureReport": "完整 Markdown",
    "asd": "完整 Markdown",
    "mds": "完整 Markdown",
    "dts": "完整 Markdown",
    "adr": "完整 Markdown"
  }}
}}
"""
    payload = _run_json_task(agent, description)
    artifacts_raw = payload.get("artifacts", {})
    if not isinstance(artifacts_raw, dict):
        raise ValueError("设计阶段智能体输出缺少 artifacts 对象。")

    artifacts = {key: _clean_markdown(str(artifacts_raw.get(key, ""))) for key in DESIGN_OUTPUTS}
    selected = str(payload.get("selectedArchitecture", "")).strip() or "未明确"
    _validate_artifacts(artifacts, selected)

    saved_files = []
    for key, stem in DESIGN_OUTPUTS.items():
        path = _next_versioned_markdown(stem)
        path.write_text(artifacts[key] + "\n", encoding="utf-8")
        saved_files.append(_relative(path))

    node_counts = _count_nodes(payload.get("nodeCounts"), artifacts["knowledgeGraph"])
    summary = str(payload.get("summary", "")).strip() or (
        f"软件设计第一步完成，选定架构风格：{selected}。"
    )

    return {
        "summary": summary,
        "selectedArchitecture": selected,
        "nodeCounts": node_counts,
        "latestBaseline": _relative(inputs["baseline"]),
        "srsPath": _relative(inputs["srsPath"]),
        "umlCount": len(inputs["umlFiles"]),
        "files": saved_files,
        "model": DESIGN_MODEL,
        "completion": (
            f"软件设计第一步完成：产出文件 6 份，知识图谱节点 {node_counts['total']} 个，"
            f"选定架构风格：{selected}，ADR-001 状态：已决定。"
        ),
    }
