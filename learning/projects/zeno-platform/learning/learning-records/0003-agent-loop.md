# 0003 · AgentLoop 核心引擎
**日期**：2026-08-23
**状态**：已学（第 3 课）

## 学到了什么
- AgentLoop 不在 `core/agent/base.py`，而在 `core/agent/execution/loop.py`。`base.py` 的 `Agent` 类是外壳：`_execute_inner`（base.py:430）准备 `ExecutionContext` → 建一个 `AgentLoop` 实例（base.py:539）→ `async for event in loop.run()`（base.py:557）把事件分类路由给 stream_handler/SSE。
- 调用链：`Agent.execute` → `_execute_inner` → `loop.run`（loop.py:293）→ `_run_unpinned`（loop.py:305）→ `while True`（loop.py:380）。
- **一轮 = 5 个阶段**：①闸门检查（max_turns/max_budget）→ ②LLM 调用（流式 `_llm_stream_single` / 非流式 `_llm_call_single`，都走 `from core.llm import llm`）→ ③工具提取+终止判断 → ④工具执行（BeforeToolUse hook → `_execute_tools` → 挂起分流）→ ⑤收尾本轮（append_turn + AfterToolUse + trim）。
- `llm_rounds` 在 LLM 调用成功后 +1（loop.py:502），不是工具执行后。max_turns=30 = 最多 30 次 LLM 调用。
- **5 种终止 subtype**：`success`（无工具调用且有正文）/ `error_max_turns` / `error_max_budget_usd` / `error_during_execution`（LLM 抛异常 / 工具抛异常 / 空回复重试仍空）/ `error_no_model`（model_name 为空）。另有 `suspended`（AG-UI 挂起，非错误）。
- **🔴 最大坑：错误判据是 subtype，不是 exception**。`_make_result`（loop.py:1958）只在传入 Exception 时才加 `error` 键，5 条失败里 4 条不传 Exception。按「有没有 error 键」判会全部漏判 → 「成功的空 run」。正确判据是 `subtype.startswith("error_")`（errors.py:80），由 `run_error_from_result`（errors.py:54）实现。
- **没有 provider fallback 了**（2026-07-22 全删回退）：单模型单次调用，失败直接上抛。「配 A 跑 B」结构上不可能——模型名不一致的问题在 `_prepare_execution` 解析阶段，不在 loop。
- **单个工具 is_error=True 不终止循环**——错误结果照常回传模型，模型可自行修正。只有 `_execute_tools` 本身抛异常才终止（loop.py:698-713）。「工具报错但 AI 继续跑」是正常行为。
- **空回复守卫**（loop.py:535）：`_has_visible_output` 只看 content（thinking 不算）。既无正文也无合法工具调用 → 注入提示重试 1 次，仍空抛 `EmptyModelResponseError` 收尾成 ERROR_EXECUTION，绝不静默 SUCCESS。
- **max_tokens 恢复**（loop.py:511）：stop_reason==max_tokens 时注入续写提示，最多重试 3 次。
- **Plan 模式**（loop.py:595）：`permission_mode=="plan"` → 工具全拒，SUCCESS，只读分析。
- **BeforeToolUse hook**（loop.py:609）：BLOCK 清空调用注入拦截消息后 continue（不终止），MODIFY 改写 tool_calls。
- 流式 vs 非流式差别只在第②阶段 LLM 调用怎么 yield；共用同一个 while True 循环体。浏览器面走流式，前端「思考中…」= `LLM_THINKING_DELTA` 实时渲染。

## 关键文件
- `core/agent/execution/loop.py` — `AgentLoop` 类（180）/ `run`（293）/ `_run_unpinned`（305）/ `while True`（380）/ `_execute_tools`（1400）/ `_make_result`（1958）
- `core/agent/base.py` — `Agent` 类（139）/ `_execute_inner`（430，事件路由）/ `_prepare_execution`（654）
- `core/agent/types.py` — `LoopConfig`（31）/ `ResultSubtype`（185）/ `LoopEventType`（204）
- `core/agent/errors.py` — `run_error_from_result`（54，错误判据）/ `_RESULT_ERROR_MESSAGES`（44）

## 排障归因表（增量）
| 症状 | 最可能落点 | 确认方式 |
|---|---|---|
| 「已完成」但内容空、无 error 事件 | RESULT subtype = `error_no_model` | 看 subtype；查 model_name 为何为空 |
| AI 跑很多轮没停 | `error_max_turns` | 看 turns_used；工具是否反复调同一个 |
| 跑到一半被中止 | `error_max_budget_usd` | 查 max_budget_usd + usage_tracker |
| SSE error 带异常类名 | `error_during_execution` | 看 error.type；LLM/工具失败日志 |
| 工具报错但 AI 继续跑 | 正常（is_error=True 不终止） | 不是 bug；看模型是否在修正 |
| 工具被拦但 AI 没停 | BeforeToolUse hook BLOCK | 看 hook 返回 blocked |
| Plan 模式工具没执行 | `permission_mode=="plan"` | 设计如此，只读分析 |
| 回复截断后又续上 | max_tokens 恢复（≤3 次） | 看日志「max_tokens 截断检测」 |

## 待深挖
- `_prepare_execution`（base.py:654）里 model_name 怎么解析的——`error_no_model` 的根因在这里，等讲到 core/llm relay/selector 时展开。
- `_execute_tools` → `tool_executor.execute_tools` 的内部——下一课 core/tool 展开。
- 挂起分流（loop.py:719）的 AG-UI 细节——等讲到 AG-UI 协议时展开。
- `_trim_if_needed` / ContextEngine 的上下文裁剪——等讲到 core/context 时展开。
