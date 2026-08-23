# 0002 · chat_service 的 10 步流程

**日期**：2026-08-19
**状态**：已学（第 2 课）

## 学到了什么

- `chat()` 是所有对话的必经之路（HTTP 和 gRPC 都汇到这里），结构是 10 步。
- 步骤 1-2：agent 存在性 + 状态门控；Conversation 归属校验（会话绑定 agent_id，换 agent 会被拒）。
- 步骤 4：文件两条消费路径——URL 引用给模型，storage_key 给绑定工作流（需归属校验 + 重签）。
- 步骤 5.5：BEFORE_DISPATCH hook 可 BLOCK / MODIFY 消息，走正常返回路径不是 HTTP 异常。
- 步骤 7-8：用户消息和 Assistant 占位用**独立的 DB session** 写入——因为 `MessageRepo.create_message` 内部 commit 后连接可能被池重取，重取的连接不带 `search_path`。
- **步骤 9 是分水岭**：detached 模式（`not stream and not _await_agent`）把步骤 9+10 整段塞进 `asyncio.create_task`，立即返回 session_id。资源准备失败**不抛给 HTTP 调用方**，而是经进度流 `emit_error` 上报（`error_type`: `mcp_required` / `resource_error`）。
- 步骤 9 装配顺序：MCP → API tools → 原生 tools → sub_agents（都是并集）→ blocked tools（差集，**必须最后**，否则被并集加回来）。
- 步骤 10 三种模式：detached（HTTP submit）、同步（_await_agent）、流式（gRPC ChatStream）。浏览器面只走 detached。
- `_run_agent_scoped` 三阶段：数据准备（加载历史）→ 执行 Agent（`Agent.chat` 进 core/agent/）→ 完成处理（后台任务 + 释放资源）。

## 关键文件

- `services/chat_service.py` — `chat()` 第 2418 行 / `_prepare_then_run_detached` 第 3107 行 / `_prepare_agent_for_session` 第 2965 行 / `_run_agent` 第 3978 行

## 排障归因表（增量）

| 症状 | 最可能步骤 | 确认方式 |
|---|---|---|
| 「Agent 不存在」 | 步骤 1 | agent_id 是否传对 |
| 「该会话属于其他智能体」 | 步骤 2 | 会话绑定 agent_id 与本次不符 |
| 工作流拿不到文件 | 步骤 4 | 前端传 file_url 还是 storage_key |
| 消息被拦但非 4xx | 步骤 5.5 | 查 BEFORE_DISPATCH hook |
| 消息写到错的租户 schema | 步骤 7-8 | DB session 重取后 search_path 漂移 |
| SSE 首条就是 error | 步骤 9（detached） | 看 error_type: mcp_required / resource_error |
| blocked 工具仍被调用 | 步骤 9 装配顺序 | 差集是否在并集之后 |
| 回复内容不对/工具没调 | _run_agent 阶段 2.3 | 进 core/agent/（下一课） |

## 待深化

- `_run_agent_scoped` 阶段 2.2「上下文管理（裁剪历史消息）」——ContextEngine 的细节，等讲到 core/context 时展开。
- AG-UI 挂起续跑（resume_messages 整份替换）——等讲到 AG-UI 协议时展开。
