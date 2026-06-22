from __future__ import annotations

from datetime import datetime
import json
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from agents.a1a_stakeholders import create_a1a_agent, list_stakeholders
from agents.a2_quality_analyzer import analyze_notes, analyze_notes_structured, generate_rollback_plan, notes_summary, run_n8n_rollback, run_rollback
from agents.a1b_elicitor import ask_a1a, create_a1b_agent, next_question, summarize_requirements
from agents.a3_modeler import A3_MODEL, a3_status, run_a3_modeling
from agents.a4_srs_writer import A4_MODEL, a4_status, generate_srs, revise_srs_from_a5
from agents.a5_requirement_validator import A5_MODEL, a5_status, validate_requirements
from agents.a6_baseline_manager import A6_MODEL, a6_status, create_baseline
from agents.design_architect import DESIGN_MODEL, design_status, run_design_architecture
from agents.design_constraints import design_constraints_status, run_design_constraints
from agents.llm_config import create_llm, get_base_url, get_model_name
from agents.recording import REPO_ROOT, VAULT_ROOT, list_records, save_record


ROOT = Path(__file__).resolve().parent
WEB_DIR = ROOT / "web"

app = FastAPI(title="A1a/A1b 需求获取系统", version="1.0.0")
app.mount("/static", StaticFiles(directory=WEB_DIR), name="static")


class ConversationItem(BaseModel):
    speaker: str = Field(min_length=1)
    content: str = Field(min_length=1)


class A1aChatRequest(BaseModel):
    apiKey: str = ""
    stakeholderId: str = Field(min_length=1)
    message: str = Field(min_length=1)
    history: list[ConversationItem] = []


class A1bRequest(BaseModel):
    apiKey: str = ""
    stakeholderId: str = Field(min_length=1)
    history: list[ConversationItem] = []


class SaveRecordRequest(BaseModel):
    stakeholderId: str = Field(min_length=1)
    history: list[ConversationItem] = []
    summary: str | None = None


class A2AnalyzeRequest(BaseModel):
    apiKey: str = ""


class A6BaselineRequest(BaseModel):
    apiKey: str = ""
    ccbConclusion: str = "通过（无保留意见）"
    acceptA5Risks: bool = False


class N8nStakeholder(BaseModel):
    id: str = Field(min_length=1)
    name: str = Field(min_length=1)


class N8nA1bBatchRequest(BaseModel):
    apiKey: str = ""
    stakeholders: list[N8nStakeholder] | None = None
    roundsPerStakeholder: int = 3


class A2RollbackPlanRequest(BaseModel):
    apiKey: str = ""
    report: str = Field(min_length=1)


class A2RollbackRunRequest(BaseModel):
    apiKey: str = ""
    plan: str = Field(min_length=1)
    stakeholderIds: list[str] | None = None


class N8nA2RollbackRequest(BaseModel):
    apiKey: str = ""
    report: str = Field(min_length=1)
    stakeholderIds: list[str] | None = None


class N8nCcbPendingRequest(BaseModel):
    resumeUrl: str = Field(min_length=1)
    executionId: str = ""
    workflowName: str = "需求开发全流程（工作流1）"
    latestSrs: str = ""
    latestA5Report: str = ""
    a5Decision: str = ""
    a5Summary: str = ""


def _history_dicts(history: list[ConversationItem]) -> list[dict[str, str]]:
    return [item.model_dump() for item in history]


def _http_error(exc: Exception) -> HTTPException:
    message = str(exc)
    if "Authentication" in message or "api_key" in message.lower():
        message = "百炼 API key 校验失败，请检查页面输入的 key。"
    return HTTPException(status_code=400, detail=message)


def _markdown_table_cell(value: str) -> str:
    return value.replace("\\", "\\\\").replace("|", "\\|").replace("\r\n", "<br>").replace("\n", "<br>").strip()


@app.get("/")
def index():
    return FileResponse(WEB_DIR / "index.html")


@app.get("/api/config")
def config() -> dict[str, str]:
    return {
        "model": get_model_name(),
        "a3Model": A3_MODEL,
        "a4Model": A4_MODEL,
        "a5Model": A5_MODEL,
        "a6Model": A6_MODEL,
        "designModel": DESIGN_MODEL,
        "baseUrl": get_base_url(),
    }


@app.get("/api/stakeholders")
def stakeholders() -> list[dict[str, str]]:
    return list_stakeholders()


@app.post("/api/chat/a1a")
def chat_a1a(payload: A1aChatRequest) -> dict[str, str]:
    try:
        llm = create_llm(payload.apiKey)
        agent = create_a1a_agent(payload.stakeholderId, llm)
        answer = ask_a1a(agent, payload.stakeholderId, payload.message, _history_dicts(payload.history))
        return {"answer": answer}
    except Exception as exc:
        raise _http_error(exc) from exc


@app.post("/api/chat/a1b/next")
def chat_a1b_next(payload: A1bRequest) -> dict[str, str]:
    try:
        llm = create_llm(payload.apiKey)
        agent = create_a1b_agent(llm)
        question = next_question(agent, payload.stakeholderId, _history_dicts(payload.history))
        return {"question": question}
    except Exception as exc:
        raise _http_error(exc) from exc


@app.post("/api/chat/a1b/run")
def chat_a1b_run(payload: A1bRequest) -> dict[str, str]:
    try:
        history = _history_dicts(payload.history)
        llm = create_llm(payload.apiKey)
        a1b_agent = create_a1b_agent(llm)
        question = next_question(a1b_agent, payload.stakeholderId, history)
        history_with_question = history + [{"speaker": "A1b需求获取智能体", "content": question}]
        a1a_agent = create_a1a_agent(payload.stakeholderId, llm)
        answer = ask_a1a(a1a_agent, payload.stakeholderId, question, history_with_question)
        return {"question": question, "answer": answer}
    except Exception as exc:
        raise _http_error(exc) from exc


@app.post("/api/records/save")
def save(payload: SaveRecordRequest) -> dict[str, str]:
    try:
        return save_record(payload.stakeholderId, _history_dicts(payload.history), payload.summary)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/api/records")
def records() -> list[dict[str, Any]]:
    return list_records()


@app.post("/api/n8n/a1b-batch")
def n8n_a1b_batch(payload: N8nA1bBatchRequest) -> dict[str, Any]:
    prompts = {
        "clerk": "请从门店店员角度说明接单、报价、生产流转、交付时最容易出错的场景。",
        "manager": "请从门店店长角度说明排产调度、折扣审批、异常处理和门店管理诉求。",
        "operation": "请从总部运营管理员角度说明连锁门店运营、规则配置、报表和风险管控诉求。",
        "finance": "请从财务人员角度说明收款、对账、发票、退款和外协成本核算诉求。",
        "customer": "请从客户角度说明下单、透明报价、订单进度、交付和售后退款诉求。",
        "delivery": "请从配送/外协人员角度说明任务流转、签收、异常上报和外协协作诉求。",
        "admin": "请从系统管理员角度说明权限、审计、配置变更、性能和故障恢复诉求。",
    }
    try:
        rounds = max(1, min(int(payload.roundsPerStakeholder or 3), 5))
        stakeholders = payload.stakeholders or [N8nStakeholder(id=item["id"], name=item["name"]) for item in list_stakeholders()]
        llm = create_llm(payload.apiKey)
        a1b_agent = create_a1b_agent(llm)
        saved: list[dict[str, Any]] = []
        for stakeholder in stakeholders:
            context: list[dict[str, str]] = []
            for round_index in range(1, rounds + 1):
                if round_index == 1:
                    context.append(
                        {
                            "speaker": "n8n工作流",
                            "content": prompts.get(
                                stakeholder.id,
                                f"请说明{stakeholder.name}的核心需求、痛点、异常场景和验收期望。",
                            ),
                        }
                    )
                question = next_question(a1b_agent, stakeholder.id, context)
                history_with_question = context + [{"speaker": "A1b需求获取智能体", "content": question}]
                a1a_agent = create_a1a_agent(stakeholder.id, llm)
                answer = ask_a1a(a1a_agent, stakeholder.id, question, history_with_question)
                round_history = [
                    {"speaker": "A1b需求获取智能体", "content": question},
                    {"speaker": f"A1a-{stakeholder.name}", "content": answer},
                ]
                record = save_record(stakeholder.id, round_history, "")
                context.extend(round_history)
                saved.append(
                    {
                        "stakeholderId": stakeholder.id,
                        "stakeholderName": stakeholder.name,
                        "round": round_index,
                        "recordPath": record["relativePath"],
                    }
                )
        return {"a1RecordCount": len(saved), "a1Records": saved}
    except Exception as exc:
        raise _http_error(exc) from exc


@app.post("/api/n8n/a2-analyze")
def n8n_a2_analyze(payload: A2AnalyzeRequest) -> dict[str, Any]:
    try:
        llm = create_llm(payload.apiKey)
        return analyze_notes_structured(llm)
    except Exception as exc:
        raise _http_error(exc) from exc


@app.post("/api/n8n/a2-rollback")
def n8n_a2_rollback(payload: N8nA2RollbackRequest) -> dict[str, Any]:
    try:
        llm = create_llm(payload.apiKey)
        return run_n8n_rollback(llm, payload.report, payload.stakeholderIds)
    except Exception as exc:
        raise _http_error(exc) from exc


@app.post("/api/n8n/ccb-pending")
def n8n_ccb_pending(payload: N8nCcbPendingRequest) -> dict[str, str]:
    pending_dir = VAULT_ROOT / "wiki" / "summaries" / "n8n工作流" / "ccb-pending"
    pending_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    execution_label = payload.executionId.strip() or timestamp
    safe_execution = "".join(char if char.isalnum() or char in "-_" else "-" for char in execution_label)
    json_path = pending_dir / f"CCB待审批-{timestamp}-{safe_execution}.json"
    md_path = pending_dir / f"CCB待审批-{timestamp}-{safe_execution}.md"
    safe_workflow_name = _markdown_table_cell(payload.workflowName or "未提供")
    safe_execution_id = _markdown_table_cell(payload.executionId or safe_execution)
    safe_srs = _markdown_table_cell(payload.latestSrs or "未提供")
    safe_a5_report = _markdown_table_cell(payload.latestA5Report or "未提供")
    safe_a5_decision = _markdown_table_cell(payload.a5Decision or "未提供")
    safe_summary = _markdown_table_cell(payload.a5Summary or "未提供")
    data = {
        "createdAt": datetime.now().isoformat(timespec="seconds"),
        "workflowName": payload.workflowName,
        "executionId": payload.executionId,
        "resumeUrl": payload.resumeUrl,
        "latestSrs": payload.latestSrs,
        "latestA5Report": payload.latestA5Report,
        "a5Decision": payload.a5Decision,
        "a5Summary": payload.a5Summary,
        "allowedDecisions": ["approved", "approvedWithRisk", "rejected", "revise"],
    }
    json_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    md_path.write_text(
        "\n".join(
            [
                "# CCB 待审批",
                "",
                "| 字段 | 内容 |",
                "|---|---|",
                f"| 工作流 | {safe_workflow_name} |",
                f"| 执行ID | {safe_execution_id} |",
                f"| 最新 SRS | {safe_srs} |",
                f"| A5 报告 | {safe_a5_report} |",
                f"| A5 结论 | {safe_a5_decision} |",
                f"| A5 摘要 | {safe_summary} |",
                "",
                "## 审批方式",
                "",
                "运行根目录 `一键CCB审批.bat`，脚本会读取同目录 JSON 中的运行时 resume URL 并提交审批。",
                "",
                "可选审批结论：",
                "",
                "- `approved`：通过，无保留意见。",
                "- `approvedWithRisk`：带 A5 风险批准进入基线。",
                "- `rejected`：审批不通过，终止本次工作流。",
                "- `revise`：退回修改，本次工作流不创建基线。",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return {
        "pendingJson": str(json_path.relative_to(REPO_ROOT)).replace("\\", "/"),
        "pendingMarkdown": str(md_path.relative_to(REPO_ROOT)).replace("\\", "/"),
        "resumeUrlStored": "true",
    }


@app.get("/api/a2/notes")
def a2_notes() -> dict[str, Any]:
    return notes_summary()


@app.post("/api/a2/analyze")
def a2_analyze(payload: A2AnalyzeRequest) -> dict[str, str]:
    try:
        llm = create_llm(payload.apiKey)
        return analyze_notes(llm)
    except Exception as exc:
        raise _http_error(exc) from exc


@app.post("/api/a2/rollback-plan")
def a2_rollback_plan(payload: A2RollbackPlanRequest) -> dict[str, str]:
    try:
        llm = create_llm(payload.apiKey)
        return generate_rollback_plan(llm, payload.report)
    except Exception as exc:
        raise _http_error(exc) from exc


@app.post("/api/a2/rollback-run")
def a2_rollback_run(payload: A2RollbackRunRequest) -> dict[str, Any]:
    try:
        llm = create_llm(payload.apiKey)
        return run_rollback(llm, payload.plan, payload.stakeholderIds)
    except Exception as exc:
        raise _http_error(exc) from exc


@app.get("/api/a3/status")
def a3_modeling_status() -> dict[str, Any]:
    return a3_status()


@app.post("/api/a3/model")
def a3_model(payload: A2AnalyzeRequest) -> dict[str, Any]:
    try:
        llm = create_llm(payload.apiKey, model_override=A3_MODEL)
        return run_a3_modeling(llm)
    except Exception as exc:
        raise _http_error(exc) from exc


@app.get("/api/a4/status")
def a4_srs_status() -> dict[str, Any]:
    return a4_status()


@app.post("/api/a4/generate")
def a4_generate(payload: A2AnalyzeRequest) -> dict[str, Any]:
    try:
        llm = create_llm(payload.apiKey, model_override=A4_MODEL)
        return generate_srs(llm)
    except Exception as exc:
        raise _http_error(exc) from exc


@app.post("/api/a4/revise-from-a5")
def a4_revise_from_a5(payload: A2AnalyzeRequest) -> dict[str, Any]:
    try:
        llm = create_llm(payload.apiKey, model_override=A4_MODEL)
        return revise_srs_from_a5(llm)
    except Exception as exc:
        raise _http_error(exc) from exc


@app.get("/api/a5/status")
def a5_validation_status() -> dict[str, Any]:
    return a5_status()


@app.post("/api/a5/validate")
def a5_validate(payload: A2AnalyzeRequest) -> dict[str, Any]:
    try:
        llm = create_llm(payload.apiKey, model_override=A5_MODEL)
        return validate_requirements(llm)
    except Exception as exc:
        raise _http_error(exc) from exc


@app.get("/api/a6/status")
def a6_baseline_status() -> dict[str, Any]:
    return a6_status()


@app.post("/api/a6/create-baseline")
def a6_create_baseline(payload: A6BaselineRequest) -> dict[str, Any]:
    try:
        llm = create_llm(payload.apiKey, model_override=A6_MODEL)
        return create_baseline(llm, payload.ccbConclusion, payload.acceptA5Risks)
    except Exception as exc:
        raise _http_error(exc) from exc


@app.get("/api/design/status")
def design_architecture_status() -> dict[str, Any]:
    return design_status()


@app.post("/api/design/run")
def design_architecture_run(payload: A2AnalyzeRequest) -> dict[str, Any]:
    try:
        llm = create_llm(payload.apiKey, model_override=DESIGN_MODEL)
        return run_design_architecture(llm)
    except Exception as exc:
        raise _http_error(exc) from exc


@app.get("/api/design/constraints/status")
def design_constraints_api_status() -> dict[str, Any]:
    return design_constraints_status()


@app.post("/api/design/constraints/run")
def design_constraints_api_run(payload: A2AnalyzeRequest) -> dict[str, Any]:
    try:
        llm = create_llm(payload.apiKey, model_override=DESIGN_MODEL)
        return run_design_constraints(llm)
    except Exception as exc:
        raise _http_error(exc) from exc
