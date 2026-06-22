from __future__ import annotations

from datetime import datetime
import json
from pathlib import Path
import re

from agents.a1a_stakeholders import PROJECT_NAME, STAKEHOLDERS
from agents.a1b_elicitor import run_task
from agents.a2_quality_analyzer import SUMMARIES_DIR
from agents.a3_modeler import _relative
from agents.design_architect import _latest_baseline, _read_baseline_inputs, _version_key
from agents.llm_config import DESIGN_MODEL


CONSTRAINTS_DIR = SUMMARIES_DIR / "设计约束"
API_CONTRACTS_DIR = SUMMARIES_DIR / "API契约"
STEP1_OUTPUTS = {
    "knowledgeGraph": "知识图谱节点清单",
    "architectureReport": "架构选型报告",
    "asd": "ASD-架构风格声明",
    "mds": "MDS-模块划分方案",
    "dts": "DTS-依赖拓扑",
    "adr": "ADR-001-架构选型",
}


def _latest_versioned_file(directory: Path, stem: str, suffix: str = ".md") -> Path | None:
    if not directory.exists():
        return None
    files = sorted(directory.glob(f"{stem}-v*{suffix}"), key=_version_key)
    return files[-1] if files else None


def _next_versioned_path(directory: Path, stem: str, suffix: str) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    major = 1
    minor = 0
    while True:
        path = directory / f"{stem}-v{major}.{minor}{suffix}"
        if not path.exists():
            return path
        minor += 1


def _read_step1_outputs() -> dict[str, dict[str, str]]:
    outputs: dict[str, dict[str, str]] = {}
    missing = []
    for key, stem in STEP1_OUTPUTS.items():
        path = _latest_versioned_file(SUMMARIES_DIR, stem)
        if not path:
            missing.append(stem)
            continue
        outputs[key] = {
            "name": path.name,
            "relativePath": _relative(path),
            "content": path.read_text(encoding="utf-8"),
        }
    if missing:
        raise ValueError(f"缺少设计阶段第一步产物：{', '.join(missing)}。请先运行知识图谱与架构选型。")
    return outputs


def _existing_outputs() -> dict[str, list[dict[str, str]]]:
    result = {"constraints": [], "apiContracts": []}
    if CONSTRAINTS_DIR.exists():
        for path in sorted(CONSTRAINTS_DIR.glob("*.md"), key=lambda item: item.stat().st_mtime, reverse=True):
            if path.name != ".gitkeep":
                result["constraints"].append({"name": path.name, "relativePath": _relative(path)})
    if API_CONTRACTS_DIR.exists():
        for path in sorted(API_CONTRACTS_DIR.glob("*.yaml"), key=lambda item: item.stat().st_mtime, reverse=True):
            result["apiContracts"].append({"name": path.name, "relativePath": _relative(path)})
    return result


def design_constraints_status() -> dict:
    try:
        baseline_inputs = _read_baseline_inputs()
        step1_outputs = _read_step1_outputs()
        error = ""
        latest_baseline = _relative(baseline_inputs["baseline"])
        srs_path = _relative(baseline_inputs["srsPath"])
        step1_count = len(step1_outputs)
    except ValueError as exc:
        error = str(exc)
        latest_baseline = _relative(_latest_baseline()) if _latest_baseline() else ""
        srs_path = ""
        step1_count = 0

    existing = _existing_outputs()
    return {
        "latestBaseline": latest_baseline,
        "srsPath": srs_path,
        "step1Count": step1_count,
        "requiredStep1Count": len(STEP1_OUTPUTS),
        "constraintsCount": len(existing["constraints"]),
        "apiContractsCount": len(existing["apiContracts"]),
        "constraintsOutputs": existing["constraints"],
        "apiContracts": existing["apiContracts"],
        "canRun": not error,
        "message": error or "约束设计与接口契约输入已就绪。",
        "designModel": DESIGN_MODEL,
    }


def create_design_constraints_agent(llm):
    from crewai import Agent

    return Agent(
        role="设计阶段约束与接口契约智能体 - 架构约束工程师",
        goal=(
            "基于图文快印门店连锁管理系统的需求基线、MDS、DTS、ASD 和 ADR，"
            "生成三层约束文档、OpenAPI 3.0.3 接口契约和可复用约束提示词。"
        ),
        backstory=(
            "你擅长把软件设计意图转化为 AI 可执行的结构化规则。你关注职责边界、"
            "接口契约、依赖白名单和后续代码生成时的可校验性。"
        ),
        llm=llm,
        verbose=False,
        allow_delegation=False,
    )


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
    raise ValueError("设计阶段第二步智能体输出不是可解析的 JSON，请重试。")


def _run_json_task(agent, description: str) -> dict:
    output = run_task(agent, description, "只输出可解析 JSON，包含 TLCD、OpenAPI YAML 和约束提示词。")
    try:
        return _extract_json(output)
    except ValueError:
        retry = f"""{description}

## 输出格式纠偏

上一次输出不是合法 JSON。请重新输出，严格遵守：
- 只输出一个 JSON 对象。
- 不要输出 Markdown 代码围栏。
- 不要在 JSON 前后添加解释文字。
- 顶层必须包含 summary、apiDomains、artifacts。
- artifacts 必须包含 tlcdMarkdown、openapiYaml、constraintPrompt 三个字符串。
"""
        return _extract_json(run_task(agent, retry, "只输出合法 JSON 对象。"))


def _clean_markdown(value: str) -> str:
    text = str(value or "").strip()
    text = re.sub(r"^```(?:markdown|md)?\s*", "", text, flags=re.I)
    text = re.sub(r"\s*```$", "", text).strip()
    return text


def _clean_yaml(value: str) -> str:
    text = str(value or "").strip()
    text = re.sub(r"^```(?:yaml|yml)?\s*", "", text, flags=re.I)
    text = re.sub(r"\s*```$", "", text).strip()
    return text


def _validate_no_secret(*values: str) -> None:
    secret_re = re.compile(r"(ghp_|sk-ws|DASHSCOPE_API_KEY|BAILIAN_API_KEY)", re.I)
    for value in values:
        if secret_re.search(value):
            raise ValueError("生成产物疑似包含 API key 或访问令牌，已拒绝保存。")


def _validate_outputs(tlcd: str, openapi_yaml: str, constraint_prompt: str) -> None:
    if not tlcd.strip() or not openapi_yaml.strip() or not constraint_prompt.strip():
        raise ValueError("设计阶段第二步产物不能为空。")

    for required in ["职责", "接口", "依赖", "C-RESP", "C-API", "C-DEP"]:
        if required not in tlcd:
            raise ValueError(f"TLCD 三层约束文档缺少必要内容：{required}。")

    for required in ["openapi: 3.0.3", "paths:", "components:", "schemas:"]:
        if required not in openapi_yaml:
            raise ValueError(f"OpenAPI YAML 缺少必要结构：{required}")

    path_count = len(re.findall(r"^\s*/[a-zA-Z0-9_/{}/.-]+:", openapi_yaml, flags=re.M))
    if path_count < 6:
        raise ValueError("OpenAPI YAML 至少需要覆盖 6 个业务域接口路径。")

    for required in ["项目记忆", "契约记忆", "规则记忆", "生成要求"]:
        if required not in constraint_prompt:
            raise ValueError(f"约束提示词缺少必要段落：{required}。")

    _validate_no_secret(tlcd, openapi_yaml, constraint_prompt)


def _format_step1_inputs(outputs: dict[str, dict[str, str]]) -> str:
    blocks = []
    for key, item in outputs.items():
        blocks.append(f"文件：{item['relativePath']}\n```markdown\n{item['content']}\n```")
    return "\n\n---\n\n".join(blocks)


def run_design_constraints(llm) -> dict:
    baseline_inputs = _read_baseline_inputs()
    step1_outputs = _read_step1_outputs()
    stakeholders = "、".join(profile.name for profile in STAKEHOLDERS.values())
    today = datetime.now().strftime("%Y-%m-%d")
    agent = create_design_constraints_agent(llm)

    description = f"""
## 固定项目上下文

项目名称：{PROJECT_NAME}
涉众范围：{stakeholders}
业务领域：订单接单、报价、生产流转、收款对账、门店运营、配送外协、权限审计
设计阶段：第二步，三层约束设计 + OpenAPI 接口契约 + 约束提示词构造
生成日期：{today}

## 权威需求基线

最新需求基线：{baseline_inputs["baseline"].name}
SRS 正式版：{_relative(baseline_inputs["srsPath"])}

```markdown
{baseline_inputs["srsText"]}
```

## 设计阶段第一步产物

{_format_step1_inputs(step1_outputs)}

## 任务要求

请一次性生成 3 类产物：

1. TLCD 三层约束文档 Markdown。
   - 本项目将三层约束聚焦为职责约束、接口约束、依赖约束。
   - 职责约束编号使用 C-RESP-001 起，至少 5 条。
   - 接口约束编号使用 C-API-001 起，至少 5 条。
   - 依赖约束编号使用 C-DEP-001 起，至少 5 条。
   - 每条约束必须包含：编号、约束内容、适用范围、禁止事项、来源依据。
   - 来源依据必须引用 SRS、ASD、MDS、DTS 或 ADR-001。

2. OpenAPI 3.0.3 YAML 契约。
   - 顶层必须是 openapi: 3.0.3。
   - 覆盖至少 6 个业务域接口：订单、报价、排产、配送/外协、财务、审计。
   - 每个接口包含 operationId、requestBody 或 parameters、responses、错误码。
   - components.schemas 必须定义核心 DTO：Order、Quotation、ProductionTask、DeliveryTask、Invoice、AuditLog。
   - 不要输出 Markdown 代码围栏。

3. 约束提示词 Markdown。
   - 必须包含“项目记忆”“契约记忆”“规则记忆”“生成要求”四段。
   - 用于后续 AI 代码生成，明确代码必须遵守 TLCD 和 OpenAPI。
   - 包含可复制模板，不要引用不存在的工具。

输出必须是一个 JSON 对象，不要输出 JSON 以外的解释文字：
{{
  "summary": "阶段完成摘要",
  "apiDomains": ["订单", "报价", "排产", "配送外协", "财务", "审计"],
  "artifacts": {{
    "tlcdMarkdown": "完整 Markdown",
    "openapiYaml": "完整 YAML 字符串",
    "constraintPrompt": "完整 Markdown"
  }}
}}
"""
    payload = _run_json_task(agent, description)
    artifacts = payload.get("artifacts", {})
    if not isinstance(artifacts, dict):
        raise ValueError("设计阶段第二步智能体输出缺少 artifacts 对象。")

    tlcd = _clean_markdown(str(artifacts.get("tlcdMarkdown", "")))
    openapi_yaml = _clean_yaml(str(artifacts.get("openapiYaml", "")))
    constraint_prompt = _clean_markdown(str(artifacts.get("constraintPrompt", "")))
    _validate_outputs(tlcd, openapi_yaml, constraint_prompt)

    tlcd_path = _next_versioned_path(CONSTRAINTS_DIR, "TLCD-三层约束文档", ".md")
    openapi_path = _next_versioned_path(API_CONTRACTS_DIR, "OpenAPI-接口契约", ".yaml")
    prompt_path = _next_versioned_path(CONSTRAINTS_DIR, "约束提示词", ".md")

    tlcd_path.write_text(tlcd + "\n", encoding="utf-8")
    openapi_path.write_text(openapi_yaml + "\n", encoding="utf-8")
    prompt_path.write_text(constraint_prompt + "\n", encoding="utf-8")

    files = [_relative(tlcd_path), _relative(openapi_path), _relative(prompt_path)]
    domains = payload.get("apiDomains", [])
    if not isinstance(domains, list) or not domains:
        domains = ["订单", "报价", "排产", "配送外协", "财务", "审计"]
    summary = str(payload.get("summary", "")).strip() or "设计阶段第二步完成：已生成 TLCD、OpenAPI 契约与约束提示词。"

    return {
        "summary": summary,
        "latestBaseline": _relative(baseline_inputs["baseline"]),
        "srsPath": _relative(baseline_inputs["srsPath"]),
        "files": files,
        "apiDomains": domains,
        "model": DESIGN_MODEL,
        "completion": f"设计阶段第二步完成：产出文件 3 份，OpenAPI 覆盖 {len(domains)} 个业务域。",
    }
