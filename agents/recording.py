from __future__ import annotations

from datetime import datetime, timedelta
from pathlib import Path
import re

from agents.a1a_stakeholders import PROJECT_NAME, get_stakeholder


ROOT = Path(__file__).resolve().parents[1]
RAW_NOTES_DIR = ROOT / "raw" / "notes"


def _escape_table_cell(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace("|", "\\|")
        .replace("\r\n", "<br>")
        .replace("\n", "<br>")
        .strip()
    )


def _safe_filename_role(name: str) -> str:
    return re.sub(r'[<>:"/\\|?*\s]+', "", name).strip() or "涉众"


def _extract_lines(summary: str, heading: str) -> list[str]:
    lines: list[str] = []
    capture = False
    for raw_line in summary.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if heading in line:
            capture = True
            continue
        if capture and line.startswith("#"):
            break
        if capture:
            cleaned = re.sub(r"^[-*]\s*", "", line)
            cleaned = re.sub(r"^\d+[.)]\s*", "", cleaned)
            if cleaned:
                lines.append(cleaned)
    return lines


def _shorten(value: str, limit: int = 120) -> str:
    normalized = re.sub(r"\s+", " ", value).strip()
    if len(normalized) <= limit:
        return normalized
    return f"{normalized[:limit]}..."


def _fallback_needs(history: list[dict]) -> list[str]:
    needs = []
    for item in history:
        speaker = str(item.get("speaker", ""))
        content = str(item.get("content", ""))
        if speaker.startswith("A1a") and content.strip():
            needs.append(f"来自{speaker}回答：{_shorten(content)}")
        if len(needs) >= 6:
            break
    return needs


def _fallback_pending(history: list[dict]) -> list[str]:
    pending = []
    for item in history:
        speaker = str(item.get("speaker", ""))
        content = str(item.get("content", ""))
        if speaker.startswith("A1b") and content.strip() and "建议保存" not in content:
            pending.append(f"围绕该问题继续核实：{_shorten(content, 90)}")
        if len(pending) >= 4:
            break
    return pending


def build_markdown(
    stakeholder_id: str,
    history: list[dict],
    summary: str | None,
    recorder: str = "A1需求获取页面",
    recorded_at: datetime | None = None,
) -> str:
    profile = get_stakeholder(stakeholder_id)
    now = recorded_at or datetime.now()
    title_time = now.strftime("%Y%m%d-%H%M")
    display_time = now.strftime("%Y-%m-%d %H:%M")

    needs = _extract_lines(summary or "", "初步需求线索")
    pending = _extract_lines(summary or "", "待澄清问题")
    if not needs:
        needs = _fallback_needs(history) or ["待 A2 需求质量分析阶段进一步提炼。"]
    if not pending:
        pending = _fallback_pending(history) or ["暂无，后续访谈可继续补充。"]

    rows = []
    raw_index = 1
    for item in history:
      speaker = str(item.get("speaker", "未知"))
      content = str(item.get("content", ""))
      if not content.strip():
          continue
      clue = "涉众回答" if speaker.startswith("A1a") else "访谈提问"
      rows.append(
          f"| RAW-{raw_index:03d} | {_escape_table_cell(speaker)} | "
          f"{_escape_table_cell(content)} | {clue} |"
      )
      raw_index += 1

    need_rows = []
    for index, need in enumerate(needs, start=1):
        need_rows.append(f"| TMP-{index:03d} | {_escape_table_cell(need)} | RAW | 待分析 |")

    pending_lines = "\n".join(f"- {item}" for item in pending)

    return f"""# {profile.name}-{title_time}-需求记录

## 基本信息

| 项目 | 内容 |
|---|---|
| 项目名称 | {PROJECT_NAME} |
| 涉众角色 | {profile.name} |
| 访谈时间 | {display_time} |
| 记录人 | {recorder} |

## 涉众背景

{profile.backstory}

## 原始对话记录

| 编号 | 说话方 | 内容 | 初步需求线索 |
|---|---|---|---|
{chr(10).join(rows) if rows else "| RAW-001 | 系统 | 尚无对话内容 | 待补充 |"}

## 初步需求摘录

| 临时ID | 需求描述 | 来源原文编号 | 备注 |
|---|---|---|---|
{chr(10).join(need_rows)}

## 待澄清问题

{pending_lines}
"""


def save_record(stakeholder_id: str, history: list[dict], summary: str | None = None) -> dict[str, str]:
    profile = get_stakeholder(stakeholder_id)
    RAW_NOTES_DIR.mkdir(parents=True, exist_ok=True)

    created_at = datetime.now()
    record_time = created_at
    timestamp = created_at.strftime("%Y%m%d-%H%M")
    role = _safe_filename_role(profile.name)
    path = RAW_NOTES_DIR / f"{role}-{timestamp}-需求记录.md"
    offset = 1
    while path.exists():
        record_time = created_at + timedelta(minutes=offset)
        timestamp = record_time.strftime("%Y%m%d-%H%M")
        path = RAW_NOTES_DIR / f"{role}-{timestamp}-需求记录.md"
        offset += 1

    content = build_markdown(stakeholder_id, history, summary, recorded_at=record_time)
    path.write_text(content, encoding="utf-8")
    return {"path": str(path), "relativePath": str(path.relative_to(ROOT)).replace("\\", "/")}


def list_records() -> list[dict[str, str]]:
    RAW_NOTES_DIR.mkdir(parents=True, exist_ok=True)
    records = []
    for path in sorted(RAW_NOTES_DIR.glob("*.md"), key=lambda item: item.stat().st_mtime, reverse=True):
        records.append({
            "name": path.name,
            "relativePath": str(path.relative_to(ROOT)).replace("\\", "/"),
            "modified": datetime.fromtimestamp(path.stat().st_mtime).strftime("%Y-%m-%d %H:%M"),
        })
    return records
