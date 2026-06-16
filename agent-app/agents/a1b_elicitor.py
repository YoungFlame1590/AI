from agents.a1a_stakeholders import PROJECT_NAME, get_stakeholder


def create_a1b_agent(llm):
    from crewai import Agent

    return Agent(
        role="A1b 需求获取智能体 - 结构化访谈主持人",
        goal=(
            "通过短问题、追问和澄清，帮助图文快印门店连锁管理系统收集高质量原始需求，"
            "并在信息足够时提示可以保存访谈记录。"
        ),
        backstory=(
            "你熟悉需求工程访谈。你的职责不是写 SRS，也不是做技术方案，"
            "而是从涉众回答中发现流程、数据、异常、权限、报表、通知、性能和安全方面的缺口。"
            "每次只问一个高价值问题，问题要具体、容易回答、面向业务事实。"
        ),
        llm=llm,
        verbose=False,
        allow_delegation=False,
    )


def _format_history(history: list[dict]) -> str:
    if not history:
        return "暂无历史对话。"

    lines = []
    for item in history[-16:]:
        speaker = item.get("speaker", "未知")
        content = item.get("content", "")
        lines.append(f"{speaker}: {content}")
    return "\n".join(lines)


def run_task(agent, description: str, expected_output: str) -> str:
    from crewai import Crew, Task

    task = Task(description=description, expected_output=expected_output, agent=agent)
    crew = Crew(agents=[agent], tasks=[task], verbose=False)
    result = crew.kickoff()
    return str(result).strip()


def ask_a1a(stakeholder_agent, stakeholder_id: str, message: str, history: list[dict]) -> str:
    profile = get_stakeholder(stakeholder_id)
    description = f"""
项目：{PROJECT_NAME}
当前涉众：{profile.name}

历史对话：
{_format_history(history)}

当前问题：
{message}

请以“{profile.name}”的一线业务视角回答。回答要真实、具体、可追问，
优先包含操作频率、数量、异常场景、痛点、期望指标和担忧。
不要写系统设计方案，不要使用空泛口号。
"""
    return run_task(
        stakeholder_agent,
        description,
        "一段 150 到 350 字的涉众回答，必要时用简短条目列出关键诉求。",
    )


def next_question(a1b_agent, stakeholder_id: str, history: list[dict]) -> str:
    profile = get_stakeholder(stakeholder_id)
    description = f"""
项目：{PROJECT_NAME}
访谈对象：{profile.name}
访谈对象画像：{profile.role}

已有对话：
{_format_history(history)}

请生成下一轮需求获取问题。优先补齐以下信息缺口：
1. 核心业务流程和触发条件
2. 异常场景和人工兜底
3. 关键数据字段和记录要求
4. 权限、审批、财务或报表诉求
5. 可度量的性能、效率、提醒或安全期望

如果信息已经较充分，请先输出“建议保存：”，再说明还可以补问的最后一个问题。
"""
    return run_task(
        a1b_agent,
        description,
        "只输出一个具体问题；如果信息充分，以“建议保存：”开头并附一个最后澄清问题。",
    )


def summarize_requirements(a1b_agent, stakeholder_id: str, history: list[dict]) -> str:
    profile = get_stakeholder(stakeholder_id)
    description = f"""
项目：{PROJECT_NAME}
访谈对象：{profile.name}

完整对话：
{_format_history(history)}

请从原始访谈中提炼初步需求线索和待澄清问题。
注意：这不是正式 SRS，只是 raw/notes 中的初步整理。
"""
    return run_task(
        a1b_agent,
        description,
        "Markdown，包含“初步需求线索”和“待澄清问题”两个小节，每条简洁具体。",
    )

