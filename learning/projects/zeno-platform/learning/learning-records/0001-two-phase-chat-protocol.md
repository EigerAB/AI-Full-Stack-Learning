# 0001 · 两段式对话协议

**日期**：2026-08-19
**状态**：已学（第 1 课）

## 学到了什么

- ZenFlux Agent 的对话入口是**两段式**，不是常规的单 POST 流式：
  - Phase 1 `POST /chat` → 立即返回 `session_id`（detached 执行）
  - Phase 2 `GET /chat/{id}/progress` → SSE 订阅，支持 `Last-Event-ID` 重连
- 拆两段的动机：Agent 一轮可能跑几十秒到几分钟，单连接挂着会被网关超时断；拆开后 Phase 1 瞬间返回，Phase 2 断了能重连不丢事件。
- 路由的三个参数对应三道门：`body`（Pydantic 校验→422）、`AuthDep`（JWT→401）、`TenantContextDep`（租户 search_path→400）。横切关注点靠 FastAPI `Annotated[..., Depends(...)]` 注入，不在业务函数里手写。
- SSE 帧渲染是**全后端五条流共用**的 `sse_wire.format_wire_sse()`，信封结构 `{type, session_id, seq, data, _meta}`，`seq` 驱动重连。
- `SessionNotFoundError` 不抛 HTTP 异常，而是 yield 一条 `system_error` 事件——让 EventSource / fetch 客户端都能读到。

## 关键文件

- `http_server/routers/chat.py` — 两个路由 + 请求 schema
- `http_server/dependencies.py` — `require_access_token`（第 256 行）
- `http_server/tenant_deps.py` — `TenantContextDep`（第 75 行）
- `http_server/sse_wire.py` — 共享 SSE 帧渲染
- `services/chat_service.py` — `submit()` / `subscribe()` / `chat()`

## 排障归因表（待后续课程填充）

| 症状 | 最可能层 | 确认方式 |
|---|---|---|
| 422 | `ChatSubmitRequest` schema | 看响应体字段名 |
| 401 | `require_access_token` 依赖链 | 看 Authorization 头 |
| 400「缺少 tenant_id」 | `TenantContextDep` | 看 JWT 里 tenant_id |
| 409 | `run_idempotent_http` 幂等层 | 看是否重复 idempotency_key |
| 一直转圈无 4xx/5xx | Phase 1 挂住 | 后端日志有无「对话请求」行 |

## 待深化

- `run_idempotent_http` 的内部实现（锁/缓存机制）——第 2 课再细看
- wire 信封的 5 层结构和 `EVENT-CATALOG.md` 的事件类型——等讲到事件流那课
