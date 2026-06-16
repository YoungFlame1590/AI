from __future__ import annotations

from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from agents.a1a_stakeholders import create_a1a_agent, list_stakeholders
from agents.a2_quality_analyzer import analyze_notes, generate_rollback_plan, notes_summary, run_rollback
from agents.a1b_elicitor import ask_a1a, create_a1b_agent, next_question, summarize_requirements
from agents.a3_modeler import A3_MODEL, a3_status, run_a3_modeling
from agents.a4_srs_writer import A4_MODEL, a4_status, generate_srs
from agents.llm_config import create_llm, get_base_url, get_model_name
from agents.recording import list_records, save_record


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


class A2RollbackPlanRequest(BaseModel):
    apiKey: str = ""
    report: str = Field(min_length=1)


class A2RollbackRunRequest(BaseModel):
    apiKey: str = ""
    plan: str = Field(min_length=1)
    stakeholderIds: list[str] | None = None


def _history_dicts(history: list[ConversationItem]) -> list[dict[str, str]]:
    return [item.model_dump() for item in history]


def _http_error(exc: Exception) -> HTTPException:
    message = str(exc)
    if "Authentication" in message or "api_key" in message.lower():
        message = "百炼 API key 校验失败，请检查页面输入的 key。"
    return HTTPException(status_code=400, detail=message)


@app.get("/")
def index():
    return FileResponse(WEB_DIR / "index.html")


@app.get("/api/config")
def config() -> dict[str, str]:
    return {
        "model": get_model_name(),
        "a3Model": A3_MODEL,
        "a4Model": A4_MODEL,
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
