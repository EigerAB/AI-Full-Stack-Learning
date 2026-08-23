# Notes

## User preferences

- 前端精通，后端略生疏（FastAPI/HTTP 层最生疏）。
- 学习目标：**排查 AI 链路问题**，不是从零写后端。
- AI 模块重点：**Agent 编排 + 工作流**（AgentLoop / Workflow DSL / 节点执行器 / SubAgent / Team）。
- 教学语言：中文为主，代码/术语保留英文。
- 不要泛泛讲 web 概念，直接上这个项目**不一样**的地方，给真实文件和行号。
- 课末用「这里会怎么坏？」的检索题收尾，不要词汇测验。

## Lesson sequencing plan

1. ✅ **两段式对话协议**（HTTP 入口）——排障的起点。`POST /chat` → session_id；`GET /chat/{id}/progress` → SSE。
2. ✅ **chat_service.chat()** ——从 HTTP 到 services 的第一跳，10 步流程。
3. ✅ **AgentLoop** ——LLM ↔ Tool ↔ Repeat 的核心引擎（`core/agent/execution/loop.py`）。
4. ✅ **工具执行层** ——`core/tool/`：ToolExecutor → ToolProviderManager → provider.invoke；is_error 判定；sibling abort；确认门。
5. **Workflow DSL + 节点执行器** ——工作流编排，节点注册表（`core/agent/workflow_dsl.py` + `workflow_node_executors.py`）。
6. **SubAgent / Team runtime** ——多代理协作。
7. **事件流 / SSE wire** ——5 层信封，怎么从 core 流回浏览器。
8. **排障综合演练** ——给症状，定位层级。

## Working notes

- 第一课选「两段式对话协议」：它是任何 AI 链路排障的入口，且正好落在用户生疏的 FastAPI/HTTP 层 + 用户关心的 Agent 编排的交界处。
- 项目的 `Annotated[..., Depends(...)]` 风格的依赖注入，`AuthDep` / `TenantContextDep` 是常用 dep，要讲。
- SSE 帧渲染统一走 `http_server/sse_wire.py`，五条流共用——这是「不一样」的地方，值得强调。
- 第 3 课纠正了第 2 课归因表里的路径：AgentLoop 在 `core/agent/execution/loop.py`，不在 `base.py`。`base.py` 的 `Agent` 是外壳，负责准备 ExecutionContext + 事件路由；循环体在 `execution/loop.py`。
- 第 3 课的核心排障点：**错误判据是 subtype 不是 exception**（`run_error_from_result`）。5 条失败里 4 条不传 Exception，按 error 键判会漏成「成功的空 run」。这是线上最高频的误判。
- 第 3 课发现的「不一样」：①无 provider fallback（单模型单次，失败上抛）；②单工具 is_error 不终止循环；③空回复守卫绝不静默 SUCCESS。
- 第 4 课的核心排障点：**is_error 三条件取或**（executor.py:681）——`is_error is True` / `success is False` / `error` 非空。第 3 条最反直觉：success=True 但有非空 error 字段也算失败。
- 第 4 课的「不一样」：①sibling abort 只对关键工具（sandbox/bash/shell/code_interpreter）触发；②前四道门失败都返回结构化 dict 不抛异常（只有 invoke 抛才被 except 捕获）；③确认门三种结局全结构化失败不抛（旧版曾裸抛炸整轮 run）。
- 第 4 课补全了第 3 课「单工具 is_error 不终止循环」的根因：execute() 把所有失败包装成结构化 dict → AgentLoop 看到「有错误的结果」而非「异常」。
- 下一课（第 5 课）选「Workflow DSL + 节点执行器」：回到 Agent 编排主线，看人画好的 DAG 节点链怎么跑，和工作流工具节点怎么接回工具层。
