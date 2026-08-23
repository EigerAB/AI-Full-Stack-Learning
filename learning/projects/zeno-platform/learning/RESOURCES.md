# Resources

High-trust sources to ground lessons in. Updated as lessons surface new ones.

## Primary (in-repo, authoritative)

| Resource | Path | Why |
|---|---|---|
| Backend entry map | `zeno-backend-agent/AGENTS.md` | 30 秒系统模型 + 分层规则 + 改动落点 |
| Backend architecture | `zeno-backend-agent/ARCHITECTURE.md` | 请求主链路 + 代码地图 |
| Platform overview | `docs/engineering/architecture/overview.md` | nwaip 平台级架构 |
| Chat HTTP router | `zeno-backend-agent/http_server/routers/chat.py` | 两段式对话协议的 HTTP 面（第 1 课主源） |
| Chat service | `zeno-backend-agent/services/chat_service.py` | 对话主编排，`chat()` / `submit()` / `subscribe()`（第 2 课主源） |
| SSE wire renderer | `zeno-backend-agent/http_server/sse_wire.py` | 5 条流共用的统一 SSE 帧渲染 |
| Agent shell | `zeno-backend-agent/core/agent/base.py` | `Agent` 类外壳：`_execute_inner` 准备上下文 + 事件路由（第 3 课） |
| AgentLoop engine | `zeno-backend-agent/core/agent/execution/loop.py` | LLM↔Tool↔Repeat 循环体，`_run_unpinned` / `while True`（第 3 课主源） |
| Agent types | `zeno-backend-agent/core/agent/types.py` | `LoopConfig` / `ResultSubtype` / `LoopEventType` 枚举 |
| Agent errors | `zeno-backend-agent/core/agent/errors.py` | `run_error_from_result`——错误判据是 subtype 不是 exception |
| Tool executor | `zeno-backend-agent/core/tool/executor.py` | `ToolExecutor`：execute_tools / execute / is_error 判定 / sibling abort / 确认门（第 4 课主源） |
| Tool provider manager | `zeno-backend-agent/core/tool/provider_manager.py` | `ToolProviderManager`：PROVIDER_ORDER 优先级 / get_tool / invoke |
| Tool types | `zeno-backend-agent/core/tool/types.py` | `ToolExecutionResult` / `ToolContext` |
| Tool observation | `zeno-backend-agent/core/tool/observation.py` | `ensure_observation_contract`——统一结果信封 |
| Workflow DSL | `zeno-backend-agent/core/agent/workflow_dsl.py` | 工作流节点注册表 |
| Event catalog | `docs/engineering/protocols/EVENT-CATALOG.md` | 事件类型清单 |
| Two-phase protocol | `docs/engineering/protocols/CHAT-TWO-PHASE.md` | 两段式协议设计文档 |

## Secondary (framework docs, for the rusty layer)

| Resource | URL | Why |
|---|---|---|
| FastAPI dependencies | https://fastapi.tiangolo.com/tutorial/dependencies/ | `Depends` / `Annotated` 风格（用户生疏点） |
| FastAPI security/JWT | https://fastapi.tiangolo.com/tutorial/security/ | `require_access_token` 的写法背景 |
| MDN SSE | https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events | SSE 帧格式 `id:`/`event:`/`data:` |
| FastAPI background tasks | https://fastapi.tiangolo.com/tutorial/background-tasks/ | detached 执行的对照（本项目不用 BackgroundTasks，自己起 task） |
