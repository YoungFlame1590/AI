import os


DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
DEFAULT_MODEL = "qwen3.6-flash"
A3_MODEL = "qwen3.6-plus"
A4_MODEL = "qwen3.6-plus"
A5_MODEL = "qwen3.7-plus"
A6_MODEL = "qwen3.7-plus"
DESIGN_MODEL = "qwen3.7-plus"


def get_model_name() -> str:
    return os.getenv("BAILIAN_MODEL", DEFAULT_MODEL).strip() or DEFAULT_MODEL


def get_base_url() -> str:
    return os.getenv("BAILIAN_BASE_URL", DEFAULT_BASE_URL).strip() or DEFAULT_BASE_URL


def create_llm(api_key: str, model_override: str | None = None):
    if not api_key or not api_key.strip():
        raise ValueError("请先在页面输入阿里云百炼 API key。")

    try:
        from crewai import LLM
    except ModuleNotFoundError as exc:
        raise RuntimeError("未安装 CrewAI 依赖。请双击 启动需求获取页面.bat 自动安装依赖。") from exc

    model = model_override or get_model_name()

    return LLM(
        model=model,
        provider="dashscope",
        api_key=api_key.strip(),
        base_url=get_base_url(),
        temperature=0.6,
    )
