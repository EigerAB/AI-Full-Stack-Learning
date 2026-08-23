
# ZAP — Zenflux Agent Protocol Specification

> **当前运行入口边界**：本规范保留了 SSE/WebSocket/HTTP 降级等历史与产品级协议设计内容；ZAP 协议的可执行入口是 gRPC（进程另经 `http_server/` 暴露管理台 HTTP 面，不覆盖 ZAP）。上游网关如继续使用 HTTP/WS/SSE，必须在进入本进程前转换为 gRPC。

> **Version**: 2.3  
> **Status**: Draft  
> **Date**: 2026-04-20  
> **Convention**: 本文中 MUST / MUST NOT / SHOULD / MAY 遵循 [RFC 2119](https://www.rfc-editor.org/rfc/rfc2119) 语义

> **Draft 声明**：本规范处于 Draft 状态，尚未经过多实现互操作性验证。接口定义、事件语义和行为约束可能在后续版本中发生不兼容变更。实现者 SHOULD 做好适配准备。

> **类型定义语言**：本规范使用 TypeScript 接口作为类型定义语言。选择 TypeScript 是因为目标受众（前端/全栈工程师）对其最为熟悉，且 TypeScript 的类型系统足以表达协议中的结构约束。

---

## 目录

- [§1 概述](#1-概述)
- [§2 协议模型](#2-协议模型)
- [§3 传输层](#3-传输层)
  - [§3.5 连接生命周期管理](#35-连接生命周期管理) *(v2.2 新增)*
- [§4 三帧协议](#4-三帧协议)
  - [§4.6 请求取消](#46-请求取消) *(v2.2 新增)*
- [§5 核心能力](#5-核心能力)
  - [§5.8 会话中断与恢复](#58-会话中断与恢复) *(v2.2 新增)*
  - [§5.9 动态渲染指南](#59-动态渲染指南) *(v2.2 新增)*
  - [§5.10 Tool 中心化事件](#510-tool-中心化事件) *(v2.3 新增)*
- [§6 事件中间件](#6-事件中间件)
- [§7 多 Agent 协作与外部协议集成](#7-多-agent-协作与外部协议集成)
- [§8 可靠性](#8-可靠性)
  - [§8.6 背压控制](#86-背压控制) *(v2.2 新增)*
  - [§8.7 高频事件合并](#87-高频事件合并) *(v2.2 新增)*
  - [§8.8 幂等性与去重](#88-幂等性与去重) *(v2.2 新增)*
  - [§8.9 消息排序保证](#89-消息排序保证) *(v2.2 新增)*
  - [§8.10 优雅降级](#810-优雅降级) *(v2.2 新增)*
  - [§8.11 熔断器模式](#811-熔断器模式) *(v2.2 新增)*
  - [§8.12 竞态条件防护](#812-竞态条件防护) *(v2.2 新增)*
- [§9 安全模型](#9-安全模型)
- [§10 版本与能力协商](#10-版本与能力协商)
- [附录 A — 完整事件类型 TypeScript 接口](#附录-a--完整事件类型-typescript-接口)
- [附录 B — JSON 示例与交互序列](#附录-b--json-示例与交互序列)
- [附录 C — 架构决策记录 ADR](#附录-c--架构决策记录-adr)
- [附录 D — Future Work](#附录-d--future-work)

---

# §1 概述

## 1.1 什么是 ZAP

ZAP (Zenflux Agent Protocol) 是 **Agent 与前端之间的实时通信协议层**。它不是 Agent 框架，也不是前端渲染引擎，而是 Agent 产出内容到达用户的「管道」。

```
┌─────────────────────────────────────────────────────────────────────┐
│                         系统上下文                                    │
│                                                                     │
│  ┌──────────┐    ┌──────────────────────┐    ┌──────────────────┐  │
│  │          │    │                      │    │                  │  │
│  │  LLM     │◄──│    Agent Runtime      │    │   External       │  │
│  │ Provider │──►│                       │◄──►│   Services       │  │
│  │          │    │                      │    │  (A2A / MCP)     │  │
│  └──────────┘    └──────────┬───────────┘                          │
│                             │                                      │
│                             │ events                               │
│                             ▼                                      │
│               ╔═══════════════════════════╗                        │
│               ║                           ║                        │
│               ║         Z A P             ║  ← 本文档的范围         │
│               ║                           ║                        │
│               ║  事件管线 │ 三帧协议        ║                        │
│               ║  中间件   │ 传输层         ║                        │
│               ║                           ║                        │
│               ╚═══════════╤═══════════════╝                        │
│                           │                                        │
│               ┌───────────┴───────────┐                            │
│               │                       │                            │
│               ▼                       ▼                            │
│     ┌──────────────────┐    ┌──────────────────┐                   │
│     │   SSE Client     │    │ WebSocket Client  │                  │
│     └──────────────────┘    └──────────────────┘                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**ZAP 负责且仅负责**：

| 职责 | 说明 |
|------|------|
| **事件结构化** | 将 Agent 的输出（文本、工具调用、UI 指令等）封装为结构化事件 |
| **可靠传输** | 通过 seq 编号、缓冲、断线重连保证事件不丢失、不重复 |
| **双向通信** | Server→Client 推送和 Client→Server 回传两条路径 |
| **协议适配** | 统一事件格式在 SSE / WebSocket / gRPC 三种传输上工作 |
| **能力扩展** | 通过扩展点（delta.type / req.method / middleware）接入新能力 |

**ZAP 不负责**：

| 非职责 | 属于 |
|--------|------|
| Agent 的对话逻辑、记忆管理 | Agent Runtime |
| LLM 调用和模型选择 | Agent Runtime |
| 用户认证和鉴权 | 应用层 / API Gateway |
| 前端 UI 渲染实现 | 前端框架层 |

## 1.2 术语表

| 术语 | 定义 |
|------|------|
| **Session** | 一次完整的 Agent 运行实例，从启动到结束。包含一个或多个 Conversation |
| **Conversation** | Session 内的一轮对话上下文，包含用户消息和 Agent 回复 |
| **Message** | Conversation 内的一条消息，包含一个或多个 Content Block |
| **Content Block** | Message 内的原子内容单元：text、thinking、tool_use、tool_result |
| **Event** | 协议中的最小通信单元，封装在统一信封中，携带 seq 序号和 UUID |
| **Event Envelope** | 事件外层结构：`event_uuid`、`seq`、`type`、`session_id`、`timestamp`、`data` |
| **Delta** | Message 层增量更新事件（`message_delta`），通过 `data.delta.type` 区分类型 |
| **Three-Frame Protocol** | WebSocket 通信的三种帧：`req`（请求）、`res`（响应）、`event`（推送） |
| **Dispatcher** | 三帧协议中按域（domain）路由请求的处理器 |
| **EventBroadcaster** | Agent 侧事件发射器，构造事件并传入管线 |
| **EventManager** | 事件管线核心，负责 seq 分配、持久化、向传输层分发 |
| **Generative UI** | Agent 动态生成前端 UI 组件的能力，统一采用 A2UI 格式 |
| **Predictive State** | 前端利用 `content_delta(input_json)` 流式 partial JSON 实时预览工具参数的技术，纯前端实现 |
| **Shared State** | Agent 与前端之间双向同步的结构化状态 |
| **HITL** | Human-in-the-Loop，Agent 暂停执行并等待人工决策的交互模式 |
| **Frontend Tool** | Agent 调用前端浏览器能力（DOM、剪贴板、本地存储等）的机制 |
| **Event Middleware** | 事件管线中可插拔的处理链节，用于日志、过滤、限流等横切关注点 |
| **Event Compaction** | 将冗长的增量事件流压缩为快照事件 |

## 1.3 协议参与者

```
┌───────────┐                              ┌────────────┐
│           │──── events (push) ──────────►│            │
│  Server   │                              │  Client    │
│  (Agent)  │◄─── requests (pull) ────────│  (前端)     │
│           │──── responses ──────────────►│            │
└───────────┘                              └────────────┘
```

**Server 角色**：
- 事件生产者：Agent Runtime → EventBroadcaster → EventManager → 推送
- 请求响应者：Dispatcher 处理 Client 的 req，返回 res

**Client 角色**：
- 事件消费者：接收、去重、路由事件到对应 Handler
- 请求发起者：用户操作 → req frame → Server
- 能力提供者：注册前端工具，供 Agent 调用

## 1.4 设计原则

| # | 原则 | 含义 | 约束 |
|---|------|------|------|
| P1 | **增量扩展，零废弃** | 新能力通过新增事件类型接入，不修改已有事件语义 | 任何现有事件在新版本中保持完全兼容 |
| P2 | **推送与回传分离** | Server→Client 和 Client→Server 走不同路径 | 推送经持久化管线（有 seq），回传经三帧 req/res（无 seq） |
| P3 | **传输层透明** | 协议层不依赖特定传输，同一事件在 SSE/WS/gRPC 上语义一致 | 事件信封格式统一，传输差异由适配层处理 |
| P4 | **LLM 原生兼容** | Content 层 4 种 block type 与 LLM Streaming API 一一映射 | Content 层事件不做任何自定义扩展 |
| P5 | **纵深优先** | 每个能力做到可靠、完整、可恢复 | 断线重连、事件去重、幂等性是一等公民 |
| P6 | **可插拔** | 横切关注点通过中间件注入，不侵入核心管线 | EventMiddleware 定义统一接口 |

### 事件交付分级

P5 的"可靠"承诺并非对所有事件一视同仁。背压控制（§8.6）和事件合并（§8.7）允许对低优先级事件进行丢弃或合并，这与 P5 并不矛盾——关键在于区分事件的交付保证级别：

| 交付级别 | 事件类型 | 保证 | 背压行为 |
|----------|---------|------|---------|
| **保证交付** | `session_start/end`、`conversation_start/stop`、`message_start/stop`、`content_start/stop`、`error`、`done`、`hitl`、`frontend_tool` | MUST 交付，不可丢弃 | 背压时阻塞 Agent（L1 生产端限流） |
| **保证交付（可合并）** | `content_delta`、`message_delta(state)` | MUST 交付内容，但多个 delta 可合并为一个（§8.7） | 合并窗口内聚合，减少事件数量但不丢失内容 |
| **尽力交付** | `ping`、`agent_status`、`message_delta(progress)` | MAY 丢弃或合并，Client MUST 容忍缺失 | 背压时保留最新一条，丢弃中间状态 |

> 这一分级确保了 P5 的核心承诺——**语义完整性**：对话的结构（start/stop 配对）和内容（text/tool_use）不会丢失，而状态性、心跳性事件在极端背压下可以降级。

## 1.5 设计约束

| 约束 | 来源 | 影响 |
|------|------|------|
| 持久化缓冲 | 基础设施 | 事件 MUST 可 JSON 序列化，单事件 SHOULD ≤ 64KB |
| SSE 单向 | HTTP 协议 | SSE 客户端回传 MUST 有 HTTP POST 降级方案 |
| LLM 流式格式 | LLM 供应商 | Content 层事件 MUST NOT 偏离 `content_start/delta/stop` 结构（与 §2.2 Content 层事件名一致） |

---

# §2 协议模型

## 2.1 统一事件信封

协议中所有事件 MUST 封装在统一信封中。信封是 ZAP 的基石——无论事件类型、传输方式、协议版本，信封结构保持不变。

```typescript
interface ZapEvent {
  event_uuid: string        // 全局唯一标识符，用于去重
  seq: number               // Session 内严格递增序号，用于断线重连
  type: string              // 事件类型（见 §2.3）
  session_id: string        // 所属 Session
  conversation_id?: string  // 所属 Conversation（Session 层事件不需要）
  message_id?: string       // 所属 Message（Session/Conversation 层事件不需要）
  timestamp: string         // ISO 8601
  data: object              // 事件特定数据（结构由 type 决定）
}
```

**为什么信封中有三级 ID（session + conversation + message）**：
- Client 可能同时接收多个 Conversation 的事件（多 tab / 多窗口）
- 断线重连按 Session 级别补发（`after_seq` 是 Session 级别的）
- 三级 ID 让 Client 无需上下文状态即可将事件路由到正确的 UI 位置

**`data._meta`「透传 + 轻标签」**：稳定的 `type` 作轻标签；上游引擎/协议差异（如 ACP 私有字段、工具执行状态 `tool_status`）统一挂在 `data` 的 `_meta` out-of-band 键，MUST NOT 进入受控 schema 校验、MUST NOT 新增事件类型。心跳 `system_ping` 在 SSE `event:` 行呈现为 `heartbeat`（信封 `type` 不变）。实现见 `core/event_bus/wire_contract.py`（`WIRE_META_KEY` / `sse_event_name`）与两段式接口 [CHAT-TWO-PHASE](./CHAT-TWO-PHASE.md)。

## 2.2 五层事件层级

事件按作用域组织为五层，从粗粒度到细粒度：

```
┌─────────────────────────────────────────────────────────────────┐
│ Session 层                                                       │
│ 生命周期：session_start ──── [运行中] ──── session_end            │
│ 事件：session_start, session_stopped, session_end, ping          │
│                                                                   │
│ ┌─────────────────────────────────────────────────────────────┐  │
│ │ Conversation 层                                              │  │
│ │ 生命周期：conversation_start ── [对话中] ── conversation_stop │  │
│ │ 事件：conversation_start, conversation_delta, conversation_stop│ │
│ │                                                              │  │
│ │ ┌─────────────────────────────────────────────────────────┐ │  │
│ │ │ Message 层                                               │ │  │
│ │ │ 生命周期：message_start ── [生成中] ── message_stop      │ │  │
│ │ │ 事件：message_start, message_delta, message_stop         │ │  │
│ │ │                                                          │ │  │
│ │ │ ┌───────────────────────────────────────────────────┐   │ │  │
│ │ │ │ Content 层                                         │   │ │  │
│ │ │ │ 生命周期：content_start ── [流式中] ── content_stop│   │ │  │
│ │ │ │ 事件：content_start, content_delta, content_stop,  │   │ │  │
│ │ │ │       content_snapshot                              │   │ │  │
│ │ │ │                                                    │   │ │  │
│ │ │ │ 与 LLM Streaming API 1:1 映射                      │   │ │  │
│ │ │ └───────────────────────────────────────────────────┘   │ │  │
│ │ └─────────────────────────────────────────────────────────┘ │  │
│ └─────────────────────────────────────────────────────────────┘  │
│                                                                   │
│ System 层（横切，不限于特定层级）                                   │
│ 事件：error, done, agent_status                                   │
└─────────────────────────────────────────────────────────────────┘
```

**各层事件注册表**：

### Session 层

| 事件类型 | 触发条件 | 关键字段 |
|----------|---------|----------|
| `session_start` | Agent 运行启动 | `session_id`, `capabilities?`, `parent_session_id?` |
| `session_stopped` | Agent 被中断 | `stop_reason` |
| `session_end` | Agent 正常结束 | — |
| `ping` | 心跳保活 | — |

### Conversation 层

| 事件类型 | 触发条件 | 关键字段 |
|----------|---------|----------|
| `conversation_start` | 新对话开始 | `conversation_id` |
| `conversation_delta` | 对话元数据更新 | `delta.plan` / `delta.title` / `delta.messages_snapshot` |
| `conversation_stop` | 对话结束 | — |

### Message 层

| 事件类型 | 触发条件 | 关键字段 |
|----------|---------|----------|
| `message_start` | Agent 开始生成回复 | `message_id`, `role`, `model` |
| `message_delta` | 回复增量更新 | `delta.type` → 见 §2.3 |
| `message_stop` | 回复生成完成 | `stop_reason`, `usage` |

### Content 层（与 LLM Streaming API 一致）

| 事件类型 | 触发条件 | 关键字段 |
|----------|---------|----------|
| `content_start` | 开始一个内容块 | `index`, `content_block.type` |
| `content_delta` | 内容块增量 | `index`, `delta.type` (text / thinking / input_json / signature) |
| `content_stop` | 内容块结束 | `index` |
| `content_snapshot` | 压缩快照 | `index`, `content_block`（完整内容） |

### System 层

| 事件类型 | 触发条件 | 关键字段 |
|----------|---------|----------|
| `error` | 任何层级的错误 | `error.type`, `error.message`, `error.code?` |
| `done` | 事件流结束标记 | — |
| `agent_status` | Agent 状态变更 | `status` (thinking / tool_calling / ...) |

> 完整 TypeScript 接口见 [附录 A](#附录-a--完整事件类型-typescript-接口)

## 2.3 Delta 类型注册表

Delta 类型是 ZAP 最重要的扩展点。`message_delta` 事件通过 `data.delta.type` 区分不同种类的增量内容。

### 基础类型（单向推送，元信息）

| delta.type | 来源 | 前端处理 |
|------------|------|---------|
| *(无 type)* | LLM API | usage 统计 |
| `recommended` | 后处理 | 推荐问题 |
| `billing` | 后处理 | 计费信息 |
| `progress` | Agent | 进度条（含 title、status、subtasks） |
| `clue` | Agent | 确认提示卡片 |

### 交互类型（推送 + 回传闭环）

| delta.type | 能力 | 前端处理 | 回传 req.method |
|------------|------|---------|----------------|
| `a2ui` | 生成式 UI (A2UI v0.9) | 动态组件渲染 | `surface.interact` |
| `hitl` | 人在环中 | 决策弹窗/表单 | `hitl.resolve` |
| `state` | 共享状态 | Store 更新 | `state.patch` |
| `frontend_tool` | 前端工具 | 执行浏览器 API | `frontend_tool.result` |

### 多 Agent 类型（§7.1）

| delta.type / 事件 | 来源 | 前端处理 |
|------------|------|---------|
| `agent.subagent_start` / `agent.subagent_stop` | 子代理生命周期 | 子代理卡片创建/完成态 |
| `content_*` (携带 `agent_id`) | 子代理流式输出 | 分流渲染到对应子代理卡片 |
| `agent_message` | Teammate 间消息 | 团队消息流气泡 |

### Tool 中心化类型（v2.3 新增）

| delta.type | 能力 | 前端处理 | 回传 req.method |
|------------|------|---------|----------------|
| `tool_result_delta` | 工具输出增量流 | 增量渲染工具结果面板 | *(无，单向推送)* |
| `tool_suspend` | 统一挂起（收敛 hitl/frontend_tool/a2ui） | 根据 `kind` 选择等待 UI | `tool.resume` |

> v2.2 的 `hitl` / `frontend_tool` 帧在 v2.3 期间继续发射以保持兼容；v3.0 起将仅发 `tool_suspend`。

### 扩展类型（开放接口）

| delta.type | 用途 | 前端处理 |
|------------|------|---------|
| `custom` | 应用层自定义事件 | 按 `name` 字段路由到自定义 handler |
| `raw` | 外部协议透传 | 按 `source` 字段路由 |

### 工具结果渲染

工具结果保持在 Content 层原样透传，前端根据 `tool_use.name` 选择渲染组件。协议层不做任何翻译或增强。

```
Content 层事件流（协议不变）：
  content_start(index:1, type:"tool_use", name:"query_database")
  content_delta(index:1, input_json)   ← 可用于 Predictive State
  content_stop(index:1)
  content_start(index:2, type:"tool_result")
  content_stop(index:2)

前端渲染（协议外）：
  switch(tool_use.name) {
    "query_database"   → <DataTable />
    "web_search"       → <SearchResults />
    "knowledge_search" → <SourceList />
    default            → <JSONCollapse />
  }
```

**设计理由**：见 [ADR-011](#adr-011-前端自渲染工具结果废弃-toolenhancerregistry)

## 2.4 事件生命周期

一个典型的 Agent 回复（含工具调用和 HITL）产生以下事件序列：

```
seq  type                    说明
───  ────                    ────
 1   session_start           Session 建立
 2   conversation_start      对话开始
 3   message_start           Agent 开始回复
 4   content_start           [index:0] text 块开始
 5   content_delta           [index:0] "你好"
 6   content_delta           [index:0] "，我来帮你"
 7   content_stop            [index:0] text 块结束
 8   content_start           [index:1] tool_use 块开始（name:"query_database"）
 9   content_delta           [index:1] input_json  ← 前端可做 Predictive State
10   content_stop            [index:1] tool_use 块结束
11   content_start           [index:2] tool_result 块开始  ← 前端按 tool_use.name 渲染
12   content_stop            [index:2] tool_result 块结束
13   message_delta           type:"hitl" (需要用户确认)
     ── Client req: hitl.resolve ──
14   content_start           [index:3] text 块开始 (Agent 恢复)
15   content_delta           [index:3] "好的，已执行"
16   content_stop            [index:3] text 块结束
17   message_delta           (usage 统计)
18   message_stop            回复结束
19   conversation_stop       对话结束
20   done                    流结束
```

**关键规则**：

| 规则 | 约束 |
|------|------|
| seq 严格递增 | 持久化后的 seq MUST 严格递增。但事件合并（§8.7）可能导致 Client 可见的 seq 出现跳跃——例如 seq 13/14/15 合并后 Client 只看到 seq=15。Client MUST 容忍 seq 间隙，仅以 seq 的单调递增（而非连续性）作为排序依据 |
| 生命周期配对 | 每个 `*_start` MUST 有对应的 `*_stop`（error 情况除外） |
| Content 块并行 | 不同 index 的 Content 块可以交错 |
| Delta 插入点 | `message_delta` 在 `message_start` 和 `message_stop` 之间任意位置 |
| System 无层级 | `error`、`done`、`agent_status` 可在任何位置出现 |

---

# §3 传输层

## 3.1 SSE 通道

SSE (Server-Sent Events) 是 HTTP 长连接上的单向推送通道。

**连接建立**：

> 以下示例省略可配置的 base path 前缀（默认 `/api/v1`）。

```
Client                                          Server
  │                                                │
  │─── POST /api/v1/chat ───────────────────────►│  (发起对话)
  │◄── 200 {session_id: "sess_abc"} ──────────────│
  │                                                │
  │─── GET /api/v1/chat/sess_abc ───────────────►│  (建立 SSE)
  │◄── 200 Content-Type: text/event-stream ───────│
  │◄── event: session_start\ndata: {...}\n\n ─────│
  │◄── event: content_delta\ndata: {...}\n\n ─────│
  │◄── ... ────────────────────────────────────────│
  │◄── event: done\ndata: {...}\n\n ──────────────│
```

**SSE 线上格式**：

```
event: <event_type>
data: <ZapEvent JSON>

```

每个事件后 MUST 有空行（`\n\n`）作为分隔符。`data` 是完整的 ZapEvent JSON。

**SSE 回传降级**：

| 操作 | HTTP 端点 |
|------|----------|
| HITL 决策 | `POST /api/v1/hitl/{hitl_id}/resolve` |
| UI 交互 | `POST /api/v1/ui/{component_id}/action` |
| 状态修改 | `POST /api/v1/state/{session_id}/patch` |
| 前端工具结果 | `POST /api/v1/frontend-tool/{call_id}/result` |

## 3.2 WebSocket 通道

WebSocket 是全双工通道，支持推送和回传。

**连接建立**：

```
Client                                          Server
  │                                                │
  │─── WS Upgrade /v1/channel/ws ────────────────►│
  │◄── 101 Switching Protocols ───────────────────│
  │                                                │
  │─── req{method:"client.capabilities"} ────────►│  (能力通告)
  │◄── res{ok:true} ─────────────────────────────│
  │                                                │
  │─── req{method:"chat.send", params:{...}} ────►│  (发起对话)
  │◄── res{ok:true, data:{session_id}} ──────────│
  │                                                │
  │◄── event{event:"session_start", ...} ────────│  (推送)
  │◄── event{event:"content_delta", ...} ────────│
  │◄── event{event:"message_delta",               │
  │         data:{delta:{type:"hitl",...}}} ──────│
  │                                                │
  │─── req{method:"hitl.resolve", ...} ──────────►│  (回传)
  │◄── res{ok:true} ─────────────────────────────│
  │                                                │
  │◄── event{event:"content_delta", ...} ────────│  (继续推送)
  │◄── event{event:"done"} ──────────────────────│
```

## 3.3 gRPC BiDi 通道

> **注意**：本节为非规范性参考。完整的 gRPC 适配规范将在独立文档中定义。以下提供 proto 骨架和映射规则供实现者参考。

Gateway 通过 gRPC BiDi 流与后端通信，实现多用户连接聚合：

```
Client A ──WS──┐                         ┌── Backend
Client B ──WS──┤── Gateway ──gRPC BiDi──►│  (单条流
Client C ──WS──┘   (连接聚合)              │   多路复用)
```

gRPC 消息格式与 WebSocket 三帧一致，由 Gateway 做 1:1 翻译。

### Proto 骨架

```protobuf
syntax = "proto3";
package zap.v1;

service ZapService {
  rpc StreamSession(stream ClientMessage) returns (stream ServerMessage);
}

message ClientMessage {
  oneof payload {
    RequestFrame request = 1;
    CancelFrame cancel = 2;
    ClientCapabilities capabilities = 3;
  }
  string session_id = 10;
}

message ServerMessage {
  oneof payload {
    ResponseFrame response = 1;
    EventFrame event = 2;
  }
}

message RequestFrame {
  string id = 1;
  string method = 2;
  google.protobuf.Struct params = 3;
  optional uint64 timeout_ms = 4;
}

message CancelFrame {
  string id = 1;
  optional string reason = 2;
}

message ResponseFrame {
  string id = 1;
  bool ok = 2;
  google.protobuf.Struct data = 3;
  optional ErrorDetail error = 4;
}

message EventFrame {
  string event = 1;
  uint64 seq = 2;
  string event_uuid = 3;
  string session_id = 4;
  optional string conversation_id = 5;
  optional string message_id = 6;
  string timestamp = 7;
  google.protobuf.Struct data = 8;
}

message ErrorDetail {
  string code = 1;
  string message = 2;
}

message ClientCapabilities {
  string protocol_version = 1;
  repeated string supported_delta_types = 2;
  map<string, bool> features = 3;
}
```

### 三帧映射规则

| WebSocket 帧 | gRPC 消息 | 方向 |
|--------------|-----------|------|
| `req{...}` | `ClientMessage.request` | Client → Server |
| `cancel{...}` | `ClientMessage.cancel` | Client → Server |
| `res{...}` | `ServerMessage.response` | Server → Client |
| `event{...}` | `ServerMessage.event` | Server → Client |

### Session 路由策略

Gateway 负责将 gRPC 流中的消息路由到正确的 Backend 实例：

| 策略 | 说明 |
|------|------|
| Session 亲和性 | 同一 `session_id` 的所有消息 MUST 路由到同一 Backend 实例 |
| 连接复用 | 多个 Client 的消息可共享同一条 gRPC 流（通过 `session_id` 区分） |
| 故障转移 | Backend 实例不可用时，Gateway SHOULD 将新请求路由到其他实例（已有 Session 需要重建） |

## 3.4 双通道等价模型

**核心原则**：SSE 和 WebSocket 是**等价的推送通道**。所有事件在两个通道上格式一致、语义一致。

```
                    ┌────────── EventManager ──────────┐
                    │                                  │
                    ▼                                  ▼
          ┌──────────────────┐              ┌──────────────────┐
          │   SSE Transport  │              │   WS Transport   │
          │                  │              │                  │
          │ event: type      │              │ {type:"event",   │
          │ data: {...}      │              │  event: type,    │
          │                  │              │  data: {...}}    │
          └──────────────────┘              └──────────────────┘
          推送：✅                           推送：✅
          回传：HTTP POST 降级              回传：req/res 帧
```

**选择建议**：

| 场景 | 推荐 | 原因 |
|------|------|------|
| 只需接收 Agent 回复 | SSE | 更简单，HTTP 兼容性好 |
| 需要交互（UI / HITL / State） | WebSocket | 回传走三帧，延迟低 |
| 网关部署 | gRPC BiDi | 多用户聚合，减少后端连接数 |

## 3.5 连接生命周期管理

WebSocket / gRPC BiDi 连接经历五个状态：`idle` → `connecting` → `connected` → `reconnecting` → `closed`。Client 在非正常断开后 MUST 自动重连（指数退避 + 抖动，10 分钟预算），支持系统休眠检测（wall-clock 间隔异常时重置预算）。Server 使用永久关闭码（4001/4002/4003）阻止无效重试。连接空闲时通过传输层 ping/pong（30s）和应用层 ping 事件（15s）双层保活。

> 完整的 5 状态 Mermaid 状态图、退避参数、休眠检测算法、关闭码表和 Keep-alive 参数见 [状态机 §1 连接状态机](./STATE-MACHINES.md#1-连接状态机)。

### 3.5.6 SSE 连接恢复

SSE 连接断开后的恢复更简单——Client 用 `after_seq` 发起新的 GET 请求：

```
Client                                          Server
  │ (SSE 连接断开, 最后 seq=42)                    │
  │                                                │
  │ (等待 BASE_DELAY，同样的指数退避)                │
  │                                                │
  │─── GET /api/v1/chat/sess_abc?after_seq=42 ─────►│
  │◄── 200 Content-Type: text/event-stream ────────│
  │◄── event: content_delta\ndata: {seq:43,...}\n\n│
  │◄── ...                                        │
```

SSE 不需要传输层 ping/pong（HTTP 长连接由 Server 端 `:keepalive` 注释行保活），但应用层 ping 事件同样适用。

---

# §4 三帧协议

## 4.1 帧类型定义

WebSocket 上运行三种帧：

```typescript
// Client → Server（请求）
interface RequestFrame {
  type: "req"
  id: string              // 请求 ID，用于 req/res 配对
  method: string           // 域.操作 (如 "chat.send", "hitl.resolve")
  params?: object
  timeout_ms?: number      // v2.2：Client 端超时提示（见 §4.6）
}

// Server → Client（响应）
interface ResponseFrame {
  type: "res"
  id: string              // 与 req.id 相同
  ok: boolean
  data?: object           // 成功时返回
  error?: {               // 失败时返回
    code: string          // UPPER_SNAKE_CASE 错误码
    message: string
  }
}

// Server → Client（推送）
interface EventFrame {
  type: "event"
  event: string           // 事件类型
  seq: number
  event_uuid: string
  data: object
  session_id: string
  conversation_id?: string
  message_id?: string
  timestamp: string
}

// Client → Server（取消，v2.2 新增，见 §4.6）
interface CancelFrame {
  type: "cancel"
  id: string              // 要取消的 req.id
  reason?: string
}
```

**配对语义**：`req.id == res.id`。Server MUST 对每个 req 返回且仅返回一个 res（包括被 cancel 取消时返回 `CANCELLED` 错误）。

## 4.2 域路由机制

三帧协议通过 `req.method` 的**前缀**路由到对应 Dispatcher：`req.method = "domain.action"`（如 `chat.send`、`hitl.resolve`）。Server 按前缀匹配域，找到对应 Dispatcher 调用 `handle_request`；匹配不到则返回 `{ok: false, error: {code: "UNKNOWN_METHOD"}}`。

> 完整的域注册表（含 12 个域、所有 method 和实现状态）见 [Channel 三帧协议 §9.1 已注册域](./CHANNEL-PROTOCOL.md#91-已注册域)。
> 路由引擎与 Dispatcher 协议定义见 [Channel 三帧协议 §4 域路由](./CHANNEL-PROTOCOL.md#4-域路由)。

## 4.4 推送-回传完整时序图

以 HITL 确认流程为例，展示推送和回传两条路径的完整交互：

```
Client (前端)                    Channel Service                   Agent Runtime
    │                                │                                │
    │── req{method:"chat.send",      │                                │
    │   params:{message:"帮我删表"}}─►│                                │
    │                                │── 创建 Session ───────────────►│
    │◄─ res{ok:true,                 │                                │
    │   data:{session_id:"sess_01"}} │                                │
    │                                │                                │
    │                                │  ┌─────── 推送路径 ───────┐    │
    │                                │  │ EventBroadcaster       │    │
    │                                │  │    ↓                   │    │
    │                                │  │ MiddlewareChain        │    │
    │                                │  │    ↓                   │    │
    │                                │  │ EventManager           │    │
    │                                │  │  (seq 分配 + 持久化)    │    │
    │                                │  │    ↓                   │    │
    │                                │  │ Transport (WS/SSE)     │    │
    │                                │  └────────────────────────┘    │
    │                                │                                │
    │◄─ event{seq:1, type:"session_start"} ──────────────────────────│
    │◄─ event{seq:2, type:"conversation_start"} ────────────────────│
    │◄─ event{seq:3, type:"message_start"} ─────────────────────────│
    │◄─ event{seq:4, type:"content_start", index:0} ───────────────│
    │◄─ event{seq:5, type:"content_delta", "正在分析"} ─────────────│
    │◄─ event{seq:6, type:"content_stop"} ──────────────────────────│
    │                                │                                │
    │                                │         Agent 调用 hitl()      │
    │                                │         Agent 暂停 (await)     │
    │                                │                                │
    │◄─ event{seq:7, type:"message_delta",                           │
    │   delta:{type:"hitl", content:{                                │
    │     hitl_id:"hitl_01",                                         │
    │     kind:"confirm",                                            │
    │     title:"确认执行 DROP TABLE?"                                │
    │   }}} ─────────────────────────────────────────────────────────│
    │                                │                                │
    │ 显示确认弹窗                     │                                │
    │ 用户点击"确认"                   │                                │
    │                                │                                │
    │                                │  ┌─────── 回传路径 ───────┐    │
    │── req{id:"r01",                │  │                        │    │
    │   method:"hitl.resolve",       │  │ Channel Service        │    │
    │   params:{hitl_id:"hitl_01",   │  │    ↓                   │    │
    │           choice:"approve"}}──►│  │ HitlDispatcher         │    │
    │                                │  │    ↓                   │    │
    │                                │  │ Future.set_result()    │────│
    │                                │  │                        │    │
    │                                │  └────────────────────────┘    │
    │◄─ res{id:"r01", ok:true} ─────│                                │
    │                                │                                │
    │                                │         Agent 恢复执行          │
    │                                │                                │
    │◄─ event{seq:8, type:"content_start", index:1} ───────────────│
    │◄─ event{seq:9, type:"content_delta", "已执行 DROP TABLE"} ───│
    │◄─ event{seq:10, type:"content_stop"} ─────────────────────────│
    │◄─ event{seq:11, type:"message_stop"} ─────────────────────────│
    │◄─ event{seq:12, type:"conversation_stop"} ────────────────────│
    │◄─ event{seq:13, type:"done"} ────────────────────────────────│
```

**关键观察**：

| 路径 | 流经 | 特征 |
|------|------|------|
| 推送路径 | EventBroadcaster → MiddlewareChain → EventManager → Transport | 有 seq、有持久化、可重放 |
| 回传路径 | Transport → Channel Service → Dispatcher → Agent | 无 seq、无持久化、请求-响应配对 |
| 两条路径 | **完全解耦** | 推送不等回传，回传不等推送 |

## 4.5 三帧协议错误处理

### 标准错误码

| 错误码 | HTTP 等价 | 说明 |
|--------|----------|------|
| `UNKNOWN_METHOD` | 404 | method 不在 Dispatcher 注册表中 |
| `INVALID_PARAMS` | 400 | 请求参数格式错误 |
| `UNAUTHORIZED` | 401 | 用户认证失败或 Session 不属于当前用户 |
| `SESSION_NOT_FOUND` | 404 | session_id 不存在 |
| `INTERNAL_ERROR` | 500 | Server 内部错误 |
| `RATE_LIMITED` | 429 | 请求过于频繁 |
| `CANCELLED` | — | 请求被 Client cancel 帧取消（v2.2） |
| `TIMEOUT` | 408 | 请求处理超时（v2.2） |
| `NO_ACTIVE_GENERATION` | 409 | 中断时无活跃生成（v2.2） |
| `ABORT_IN_PROGRESS` | 409 | 上一个中断尚未完成（v2.2） |

### 超时语义

Client 发送 req 后，如果 `timeout_ms` 内未收到 res，SHOULD 假定请求失败并可重试。Server 对同一 `req.id` 的重复请求 SHOULD 幂等处理。

## 4.6 请求取消

### 动机

v2.1 中 req/res 是严格的一对一配对，Client 无法取消已发出的请求。但存在多种场景需要取消：

- 用户点击"停止生成"→ 需要取消 `chat.send`
- HITL 弹窗被用户关闭（而非点击选项）→ 需要取消 pending 的 HITL
- 前端工具执行超时 → 需要通知 Server 放弃等待
- 页面导航/关闭 → 需要取消所有 pending 请求

### Cancel 帧行为

> Cancel 帧的字段定义见 [Channel 三帧协议 §2.5 CancelFrame](./CHANNEL-PROTOCOL.md#25-cancelframe-zap-v22)。

Client → Server 发送 `cancel` 帧后，Server MUST 返回以下之一：

| 情况 | Server 行为 |
|------|------------|
| req 仍在处理中 | 尽快中止处理，返回 `res{ok:false, error:{code:"CANCELLED"}}` |
| req 已处理完成 | 正常返回 `res`（cancel 被忽略） |
| req.id 不存在 | 静默忽略（可能已超时清理） |

**时序保证**：cancel 和 res 可能交叉。Client MUST 同时处理两种结局：

```
场景 A：cancel 生效
  Client ── req{id:"r01"} ──► Server
  Client ── cancel{id:"r01"} ──► Server
  Client ◄── res{id:"r01", ok:false, error:{code:"CANCELLED"}} ── Server

场景 B：cancel 到达前已完成
  Client ── req{id:"r01"} ──► Server
  Client ── cancel{id:"r01"} ──► Server
  Client ◄── res{id:"r01", ok:true, data:{...}} ── Server   (cancel 被忽略)
```

### 超时自动取消

Client 发送 req 时可通过 `timeout_ms` 字段指定超时（见 [Channel 三帧协议 §2.2 RequestFrame](./CHANNEL-PROTOCOL.md#22-requestframe)）。Server 收到带 `timeout_ms` 的 req 后 SHOULD 在该时间内完成处理。超时后 Server SHOULD 主动返回：

```json
{"type":"res","id":"r01","ok":false,"error":{"code":"TIMEOUT","message":"Request timed out after 30000ms"}}
```

Client 不应依赖 Server 端超时，SHOULD 同时维护本地超时计时器，到期后发送 cancel 帧。

### 级联取消

取消 `chat.send` 时，Server SHOULD 级联取消该 Session 内所有由此轮对话触发的 pending 状态：

```
cancel{id:"chat_01"}
  │
  ├── 中止 LLM 生成
  │     └── 推送 message_stop{stop_reason:"cancelled"}
  │
  ├── 取消 pending HITL (如有)
  │     └── 推送 error{code:"CANCELLED", message:"Chat aborted"}
  │     └── HITL 状态 → cancelled
  │
  ├── 取消 pending Frontend Tool (如有)
  │     └── Future 设为 CancelledError
  │
  └── 推送 conversation_stop
        └── 推送 done
```

**部分结果保留**：已推送的事件（content_delta 等）不会撤回。Client 收到 cancel 后的事件序列（message_stop + conversation_stop + done）代表"到此为止"的完整结果。

### 新增错误码

| 错误码 | 说明 |
|--------|------|
| `CANCELLED` | 请求被 Client cancel 帧取消 |
| `TIMEOUT` | 请求超时（Server 端超时） |

---

# §5 核心能力

ZAP 在 Message 层引入四种交互型 delta.type，每种对应一个完整的推送-回传闭环。

**统一交互模型**：

```
Agent emit_*()
  │
  ▼
message_delta(type: "<能力>")    ← 推送
  │
  ▼
Client 处理（渲染 UI / 执行工具 / 更新状态）
  │
  ▼
req{method: "<能力>.action/resolve/result/patch"}   ← 回传
  │
  ▼
Dispatcher → Agent (Future.set_result)
  │
  ▼
Agent 恢复执行
```

## 5.1 生成式 UI（A2UI）

### 概念

Agent 在对话中动态推送 UI 组件蓝图，Client 渲染交互式组件，用户操作结果回传给 Agent。

### 为什么选择 A2UI 作为唯一格式

| 候选 | 优势 | 劣势 | 决策 |
|------|------|------|------|
| **A2UI** | LLM 友好（平面邻接表）、数据-视图分离、增量更新 | 需要 Client 组件注册 | **采纳** |
| zenflux 自定义 | 完全可控 | 需自建渲染器、LLM 需理解新 DSL | 放弃 |
| Open-JSON-UI | 社区标准 | 嵌套 JSON 对 LLM 不友好 | 放弃 |

### A2UI 平面邻接表模型

A2UI 的核心设计是**平面邻接表**：组件不嵌套，而是通过 `children` / `child` 字段引用其他组件的 `id`。

```
传统嵌套模型 (LLM 不友好)          A2UI 平面模型 (LLM 友好)
─────────────────────             ─────────────────────
{                                 [
  component: "Card",                { id: "root", component: "Card",
  children: [                         children: ["title", "btn"] },
    { component: "Text",             { id: "title", component: "Text",
      text: "标题" },                  text: "标题" },
    { component: "Button",           { id: "btn", component: "Button",
      children: [                       child: "btn-t",
        { component: "Text",           action: { event: {name:"click"} } },
          text: "点击" }              { id: "btn-t", component: "Text",
      ]                                 text: "点击" }
    }                               ]
  ]
}

✗ LLM 需维护 3 级嵌套括号          ✓ LLM 逐个输出，每个组件独立
✗ 一个括号错 → 整棵树无效          ✓ 单组件错不影响已输出的组件
```

**数据-视图分离**：

| 机制 | 用途 | 说明 |
|------|------|------|
| `updateComponents` | 结构更新 | 按组件 `id` 覆盖或新增 UI 组件 |
| `updateDataModel` | 数据更新 | 按 path 修改绑定数据，不改组件树 |
| `createSurface` | 生命周期 | 创建或重建一个 surface |

当前实现使用 A2UI v0.9 三消息模型。wire 合同由 `createSurface`、`updateComponents` 和 `updateDataModel` 表达。

### 架构流程

```
Agent ──emit──► message_delta(type:"a2ui")
                    │
                    ▼
              EventManager → Client
                                │
                          A2UIRenderer.processMessages(messages)
                                │
                                ▼
                          动态渲染组件树
                                │
                          用户点击/提交
                                │
                                ▼
                          req{method:"surface.interact"}
                                │
                                ▼
                          UIDispatcher → Agent
```

### 数据结构

```typescript
interface A2UIDelta {
  type: "a2ui"
  surface_id: string
  catalog_id?: string
  messages: Array<
    | { createSurface: Record<string, unknown> }
    | { updateComponents: Record<string, unknown> }
    | { updateDataModel: Record<string, unknown> }
  >
  placement?: Record<string, unknown>
  streaming?: boolean
}
```

### 生命周期

```
       createSurface              updateComponents/updateDataModel
[不存在] ───────────────► [已渲染] ─────────────────────────────► [已更新]
                       │         ↑      │                          ▲
                       │         └──────┘                          │
                       │       多次增量更新                         │
                       └──── createSurface 可重建同一 surface ─────┘
```

**生命周期规则**：

| 规则 | 约束 |
|------|------|
| createSurface 可重建 | 对已存在的 `surface_id` 重新发 `createSurface` 时，Client SHOULD 按完整权威 surface 重建 |
| updateComponents 覆盖 | 同一 surface 内组件按 `id` 覆盖；未出现的组件保持不变 |
| updateDataModel 增量 | 按 path 更新 DataModel；不改变组件结构 |
| surface_id 唯一性 | 同一 Session 内，`surface_id` 在所有 A2UI surface 中 MUST 唯一 |

### 发射方式

A2UI 由 Agent Runtime 直接通过 `emit_a2ui()` 发射为 `message_delta(type:"a2ui")`：

```
Agent 调用 emit_a2ui(messages=[...])
  │
  ▼
EventBroadcaster → message_delta(type:"a2ui", messages:[...])
  │
  ▼
EventManager → Client → A2UIRenderer
```

A2UI 是交互型 delta，与 HITL / State / Frontend Tool 共享 `message_delta` 容器和推送-回传闭环。它**不**走 Content 层的 tool_result（区别于普通工具结果的前端自渲染模式）。

### 回传

```typescript
// req
{ method: "surface.interact", params: { session_id, surface_id, source_component_id, event_name, context? } }
// res
{ ok: true }
```

### SSE 降级

`POST /api/v1/ui/{component_id}/action`

### 错误码

| 错误码 | 说明 |
|--------|------|
| `UNKNOWN_COMPONENT` | component_id 不存在 |
| `INVALID_ACTION` | action 不在组件声明的 action 列表中 |

## 5.2 共享状态同步

### 概念

Agent 与 Client 维护一份结构化共享状态，双方都可以读写。

### 两种同步模式

| 模式 | 适用场景 | 数据量 | 语义 |
|------|---------|--------|------|
| **snapshot** | 首次同步、状态重置 | 完整状态 | 替换整个 scope 的状态 |
| **patch** (JSON Patch RFC 6902) | 增量更新 | 仅变更部分 | 原子操作序列 |

### 作用域（scope）

| scope | 生命周期 | 典型用途 |
|-------|---------|---------|
| `session` | 整个 Session | 全局配置、用户偏好 |
| `conversation` | 单个 Conversation | 购物车、表单状态 |

### 架构流程

```
        Server 侧                                    Client 侧
┌─────────────────────┐                    ┌─────────────────────────┐
│ agent_state = {}    │                    │ AgentStateStore         │
│                     │                    │                         │
│ 写入：              │   snapshot 推送     │ 接收：                   │
│  state["cart"] = [] │──────────────────►│  store.applySnapshot()   │
│                     │                    │                         │
│ 增量：              │   patch 推送       │ 接收：                   │
│  apply_patch(ops)   │──────────────────►│  applyPatch(ops)        │
│                     │                    │                         │
│ 接收：              │   state.patch 回传  │ 用户修改：               │
│  apply_patch(ops)  ◄│──────────────────│  → req{state.patch}     │
└─────────────────────┘                    └─────────────────────────┘
```

### 数据结构

```typescript
interface StateContent {
  op: "snapshot" | "patch"
  scope: "session" | "conversation"
  state?: Record<string, unknown>                // op=snapshot 时
  delta?: Array<{                                // op=patch 时 (RFC 6902)
    op: "add" | "remove" | "replace" | "move" | "copy" | "test"
    path: string
    value?: unknown
    from?: string
  }>
  version?: number                               // 乐观锁版本号
}
```

### 版本与冲突处理

状态同步使用**乐观锁**机制：

```
               Server (version=2)              Client (version=2)
                    │                                │
                    │◄── state.patch(version=2) ─────│  Client 修改
                    │                                │
              版本检查通过                              │
              version → 3                             │
                    │                                │
                    │── res{ok:true, version:3} ────►│
                    │                                │
                    │── state(patch, version:3) ────►│  Server 广播
                    │                                │
                    │                                │  Client 更新到 v3
```

**冲突场景**：

```
               Server (version=2)              Client (version=1 过期)
                    │                                │
                    │◄── state.patch(version=1) ─────│  Client 用旧版本
                    │                                │
              版本检查失败                              │
              1 ≠ 2 (当前版本)                         │
                    │                                │
                    │── res{ok:false,               │
                    │   error:{code:"VERSION_CONFLICT",
                    │          message:"当前 v2"}}──►│
                    │                                │
                    │                                │  Client 拉取最新
                    │                                │  用 v2 重做 patch
```

**冲突解决策略**：

| 策略 | 适用场景 | 实现方式 |
|------|---------|---------|
| 重试 | 简单修改 | Client 拉取最新 snapshot，重新计算 patch |
| 合并 | 非冲突字段修改 | Client 用 `test` 操作检测被改字段，跳过冲突 |
| 覆盖 | 用户明确选择 | 不传 version（Server 跳过版本检查） |

### 状态重置

Server 可以随时推送 `snapshot` 覆盖整个 scope 的状态：

```typescript
// 重置整个 conversation 状态
message_delta(type:"state", content:{
  op: "snapshot",
  scope: "conversation",
  state: { cart: [], total: 0 },
  version: 1    // 版本重置
})
```

Client 收到 `snapshot` 后 MUST 丢弃该 scope 的现有状态，替换为 `state` 字段内容。

### 回传

```typescript
// req
{ method: "state.patch", params: { session_id, scope, patches: [...], version? } }
// res（成功）
{ ok: true, data: { version: 3 } }
// res（冲突）
{ ok: false, error: { code: "VERSION_CONFLICT", message: "..." } }
```

### SSE 降级

`POST /api/v1/state/{session_id}/patch`

### 错误码

| 错误码 | 说明 |
|--------|------|
| `INVALID_SCOPE` | scope 不是 session / conversation |
| `INVALID_PATCH` | patch 操作格式错误或 path 不存在 |
| `VERSION_CONFLICT` | 乐观锁冲突，Client 需要拉取最新版本 |
| `STATE_NOT_FOUND` | 目标 scope 尚未初始化状态 |
| `STATE_TOO_LARGE` | 状态超过大小限制（默认 1MB） |

## 5.3 人在环中（HITL）

### 概念

Agent 在执行过程中暂停，向用户发送决策请求，等待响应后继续执行。

### 设计决策

ZAP 采用**会话内暂停模型**：Agent 不终止 Run，在内存中等待（asyncio.Future 等），收到回传后恢复。这与 AG-UI 的 Run 终止模型不同。

### 四种类型（kind）

| kind | 交互形式 | 返回数据 |
|------|---------|----------|
| `confirm` | 二选一确认 | `choice: string` |
| `choice` | 多选一 | `choice: string` |
| `form` | 结构化表单 | `data: object` |
| `input` | 自由文本 | `data: {text: string}` |

### 架构流程

```
Agent                                              Client
  │                                                  │
  │ result = await hitl(                             │
  │   kind="confirm",                                │
  │   title="确认执行 DROP TABLE?",                   │
  │   options=["approve","reject"]                   │
  │ )                                                │
  │                                                  │
  │ ←── Agent 暂停 ──────────────────────────►       │
  │                                                  │
  │ ← emit message_delta(type:"hitl") ─────────────►│
  │                                                  │ 显示确认弹窗
  │                                                  │
  │                                                  │ 用户点击"同意"
  │                                                  │
  │ ◄─── req{method:"hitl.resolve",  ───────────────│
  │        params:{choice:"approve"}}                │
  │                                                  │
  │ ←── Agent 恢复执行 ──►                            │
  │ result == "approve"                              │
```

### 数据结构

```typescript
interface HitlContent {
  hitl_id: string
  kind: "confirm" | "choice" | "form" | "input"
  title: string
  description?: string
  options?: Array<{ id: string; label: string; description?: string }>
  payload?: {
    fields?: Array<{ name: string; type: string; label: string; required?: boolean }>
  }
  timeout_ms?: number
  timeout_default?: string         // 超时后的默认行为：对应 options 中的某个 id（如 "reject"）
  persistence?: "volatile" | "durable"
}
```

### 持久化策略

| 模式 | 存储 | Agent 崩溃恢复 | 适用场景 |
|------|------|--------------|---------|
| `volatile` | 内存 (asyncio.Future) | 不可恢复 | 短时确认（< 60s） |
| `durable` | 持久化存储 (Redis / DB) | 可恢复 | 长时审批、跨进程 |

**volatile 流程**：

```
Agent 进程内                                      外部
┌──────────────────┐
│ future = Future() │
│ hitl_store[id]    │
│   = future        │
│                   │                    Client resolve
│ await future  ────│───── 暂停 ──────── req{hitl.resolve} ──►
│                   │                              │
│ result = future   │◄── set_result() ─────────────│
│ .result           │
│                   │
│ Agent 恢复执行     │
└──────────────────┘
```

Agent 进程崩溃 → Future 丢失 → 用户操作无处回传 → Client 收到 `INVALID_HITL_ID`。

**durable 流程**：

```
Agent 进程                    持久化存储                  Client
    │                             │                        │
    │── save(hitl_id, ctx) ─────►│                        │
    │── emit hitl delta ─────────│───────────────────────►│
    │                             │                        │
    │ ✖ 进程崩溃                   │                        │ 用户操作
    │                             │◄── hitl.resolve ───────│
    │                             │── save(hitl_id, result)│
    │                             │                        │
    │ ✓ 进程恢复                   │                        │
    │── check(hitl_id) ─────────►│                        │
    │◄── result ────────────────│                        │
    │                             │                        │
    │ Agent 用 result 继续执行     │                        │
```

### 超时行为

| timeout_ms 配置 | 到期行为 |
|-----------------|---------|
| 设置了 timeout | 推送 error 事件 (`REQUEST_USER_INPUT_TIMEOUT`)，Agent 收到 timeout 信号 |
| 未设置 | 永久等待，直到 Session 超时（30min） |

Agent 可以在 `hitl()` 调用中指定 timeout 后的默认行为：

```
result = await hitl(
  kind="confirm",
  title="确认操作",
  timeout_ms=30000,
  timeout_default="reject"    // 超时自动选择 reject
)
```

### HITL 与 AG-UI 的区别

| 特性 | ZAP HITL | AG-UI |
|------|---------|-------|
| 暂停模型 | **会话内暂停**（Agent 不终止） | Run 终止 + resume |
| 恢复成本 | 极低（Future.set_result） | 较高（需重建 Run） |
| 上下文保持 | 完整（进程内变量不丢失） | 需通过外部状态恢复 |
| 适用性 | 延迟敏感的确认 | 长时间审批 |

### 回传

```typescript
// req
{ method: "hitl.resolve", params: { session_id, hitl_id, choice, data? } }
// res
{ ok: true }
```

### HITL 状态机

HITL 请求经历 `pending` → `resolved` / `cancelled` 的生命周期。`pending` 状态可通过 `hitl.resolve` 回传、超时或 session 结束触发状态迁移。

> 完整的 HITL 状态图（含 Mermaid 图、转换触发条件和不变量）见 [状态机 §5 HITL 确认状态机](./STATE-MACHINES.md#5-hitl-确认状态机)。

### 错误码

| 错误码 | 说明 |
|--------|------|
| `INVALID_HITL_ID` | hitl_id 不存在或已过期 |
| `REQUEST_USER_INPUT_TIMEOUT` | 超时自动拒绝 |
| `INVALID_CHOICE` | choice 不在 options 中 |

## 5.4 前端工具调用

### 概念

前端工具（Frontend Tools）让 Agent 调用**浏览器端**能力。与后端工具（Agent → 后端 API → 结果）相反，前端工具由 Agent **推送请求**，前端在用户浏览器中**执行**并**回传**结果。

```
后端工具:    Agent ──→ 后端 API ──→ 结果 ──→ Agent
前端工具:    Agent ──→ 推送请求 ──→ 前端执行 ──→ 回传结果 ──→ Agent
```

### 工具能力分类

前端可注册的工具按能力域分类。命名规范：`{domain}.{action}`。

| 能力域 | 工具名示例 | 说明 |
|--------|-----------|------|
| **页面状态** | `page.getSelection`, `page.getUrl`, `page.getScrollPosition` | 读取页面上下文 |
| **剪贴板** | `clipboard.read`, `clipboard.write` | 读写系统剪贴板 |
| **导航** | `navigation.goto`, `navigation.back` | 页面跳转 |
| **存储** | `storage.get`, `storage.set`, `storage.remove` | localStorage / sessionStorage |
| **DOM** | `dom.querySelector`, `dom.scrollTo`, `dom.highlight` | 页面元素操作 |
| **设备** | `geolocation.get`, `camera.capture`, `screen.screenshot` | 浏览器设备 API |
| **表单** | `form.fill`, `form.submit`, `form.getData` | 表单交互（Dazee 场景） |
| **通知** | `notification.show`, `notification.toast` | 用户提示 |

> 以上为建议分类，非强制枚举。前端可自由定义工具名和 schema。

### 数据模型

```typescript
interface FrontendToolContent {
  call_id: string                    // 调用唯一 ID（UUID），Agent 生成，用于回传配对
  tool_name: string                  // 工具名称，MUST 与注册的 name 一致
  args: Record<string, unknown>      // 工具参数，无参数时传 {}
  description?: string               // 人类可读描述，用于调试或 UI 提示
  timeout_ms?: number                // 超时毫秒数（默认 10000）
}

interface FrontendToolRegisterParams {
  tools: Array<{
    name: string                     // 工具名，格式 {domain}.{action}
    description: string              // 必填，Agent 用此理解工具用途
    schema: {                        // JSON Schema，Agent 用此生成参数
      type: "object"
      properties: Record<string, {
        type: string
        description?: string
        enum?: string[]
        default?: unknown
      }>
      required?: string[]
    }
  }>
}

interface FrontendToolResultParams {
  session_id: string
  call_id: string
  result?: unknown                   // 成功时：工具返回值
  error?: string                     // 失败时：错误信息
  is_error?: boolean                 // 是否错误
}
```

### 注册机制

#### 注册时机

| 时机 | 说明 |
|------|------|
| **连接建立** | Client 在 WS 连接成功后 MUST 立即注册所有可用工具 |
| **动态变化** | 路由切换、组件挂载/卸载导致可用工具变化时，SHOULD 重新注册 |
| **重新注册** | 每次 `frontend_tool.register` 是**全量替换**，不是增量。Server 用新列表覆盖旧列表 |

#### 注册流程

```
Client                                          Server
  │                                                │
  │── req{method:"frontend_tool.register",         │
  │   params:{                                     │
  │     tools:[                                    │
  │       {name:"clipboard.read",                  │
  │        description:"读取系统剪贴板文本内容",       │
  │        schema:{type:"object",                  │
  │          properties:{}, required:[]}},          │
  │       {name:"form.fill",                       │
  │        description:"填充当前页面表单字段",         │
  │        schema:{type:"object",                  │
  │          properties:{                          │
  │            fields:{type:"object",              │
  │              description:"字段名到值的映射"}     │
  │          }, required:["fields"]}},             │
  │       {name:"navigation.goto",                 │
  │        description:"跳转到指定 URL",             │
  │        schema:{type:"object",                  │
  │          properties:{                          │
  │            url:{type:"string",                 │
  │              description:"目标 URL"},           │
  │            replace:{type:"boolean",            │
  │              default:false}                    │
  │          }, required:["url"]}}                  │
  │     ]                                          │
  │   }} ─────────────────────────────────────────►│
  │                                                │
  │                                    Server 缓存工具列表
  │                                    合并到 Agent tools
  │                                                │
  │◄─ res{ok:true, data:{registered:3}} ──────────│
```

#### Agent 工具列表合并

Server 将前端注册的工具与后端工具合并，构建统一的 tools 列表发送给 LLM。前端工具的 `description` 和 `schema` 直接映射为 LLM 的 tool description 和 `input_schema`。

```
LLM 看到的 tools 列表：
  ├── query_database    (后端工具)
  ├── web_search        (后端工具)
  ├── clipboard.read    (前端工具 ← 来自 frontend_tool.register)
  ├── form.fill         (前端工具 ← 来自 frontend_tool.register)
  └── navigation.goto   (前端工具 ← 来自 frontend_tool.register)
```

LLM 不区分后端工具和前端工具——它只看到统一的 tool 列表。Agent Runtime 根据工具来源决定调用路径（后端直接执行 vs 推送给前端执行）。

### 调用生命周期

#### 完整时序

```
Agent                          Server                         Client
  │                              │                               │
  │ LLM 选择调用 clipboard.read  │                               │
  │                              │                               │
  │ 1. 生成 call_id              │                               │
  │ 2. 创建 Future               │                               │
  │ 3. emit frontend_tool delta  │                               │
  │ ────────────────────────────►│                               │
  │                              │  message_delta                │
  │                              │  type:"frontend_tool"         │
  │                              │  content:{                    │
  │                              │    call_id:"ft_001",          │
  │                              │    tool_name:"clipboard.read",│
  │                              │    args:{},                   │
  │                              │    timeout_ms:10000}          │
  │                              │ ─────────────────────────────►│
  │                              │                               │
  │ 4. await Future              │                    5. 查找 handler
  │    (Agent 暂停)              │                    6. 执行 clipboard API
  │                              │                    7. 获得结果
  │                              │                               │
  │                              │  req:frontend_tool.result     │
  │                              │  params:{                     │
  │                              │    call_id:"ft_001",          │
  │                              │    result:{text:"..."}}       │
  │                              │ ◄─────────────────────────────│
  │                              │                               │
  │ 8. Future.set_result         │  res:{ok:true}                │
  │ ◄────────────────────────────│ ─────────────────────────────►│
  │                              │                               │
  │ 9. Agent 恢复执行            │                               │
  │    result = {text:"..."}     │                               │
```

#### 超时处理

```
T+0ms     Agent emit frontend_tool (timeout_ms: 10000)
T+0ms     Agent: future = create_future(); await wait_for(future, 10s)
T+10000ms Agent: TimeoutError → unregister(call_id) → 返回错误给 LLM
T+12000ms 若前端此时才回传 → Server: INVALID_CALL_ID → 前端静默忽略
```

Agent 可在 `timeout_ms` 中指定超时时间。未指定时使用默认值 10000ms。

#### 状态机

```
[未创建] ─ emit ─► [pending] ─ result ─► [resolved] ─► Agent 恢复
                      │
                      ├─ timeout ──► [timed_out] ─► Agent 收到 TimeoutError
                      │
                      └─ disconnect ► [cancelled] ─► Agent 收到 ToolNotAvailable
```

### SSE 降级

SSE 是单向流。Client 通过 HTTP POST 回传结果和注册工具。

| 操作 | HTTP 端点 |
|------|----------|
| 注册工具 | `POST /api/v1/frontend-tool/register` |
| 回传结果 | `POST /api/v1/frontend-tool/{call_id}/result` |

### 安全模型

| 层级 | 机制 | 说明 |
|------|------|------|
| **注册白名单** | Client 声明 → Server 缓存 | Agent 只能调用已注册的工具 |
| **Server 黑名单** | 管理员配置 | 全局禁止特定工具名（如 `file_system.write`） |
| **权限分级** | 按风险等级 | 低（`page.getUrl`）、中（`clipboard.read`）、高（`camera.capture`） |
| **超时保护** | `timeout_ms` | 所有调用 MUST 有 timeout |
| **速率限制** | 每 Session / 每工具 | 如每 Session 每秒最多 5 次前端工具调用 |
| **参数校验** | JSON Schema | Client 根据注册的 schema 校验 args |
| **URL 校验** | 协议白名单 | `navigation.goto` 仅允许 `http:` / `https:`，禁止 `javascript:` |

### 错误码

| 错误码 | 说明 |
|--------|------|
| `UNKNOWN_TOOL` | 工具未注册或已被管理员禁止 |
| `TOOL_TIMEOUT` | 调用超时（`timeout_ms` 到期） |
| `TOOL_EXECUTION_ERROR` | Client 执行失败（详情在 `error` 字段） |
| `TOOL_NOT_AVAILABLE` | Client 已断开或工具注册已过期 |
| `INVALID_CALL_ID` | call_id 不存在或已过期（超时后回传） |
| `INVALID_PARAMS` | 缺少必填字段（call_id / session_id） |

## 5.5 工具结果前端渲染

### 概念

工具调用结果（`tool_result`）保持在 Content 层原样透传给前端。前端根据对应 `tool_use.name` 选择渲染组件，协议层不做任何翻译或增强。

### 设计原则

| 原则 | 说明 |
|------|------|
| **工具名即契约** | `tool_use.name` 是前端选择渲染器的唯一依据，不需要额外 hint 或 metadata |
| **协议层零侵入** | 工具结果走标准 Content 层事件，不引入新事件类型 |
| **渲染权归前端** | 同一 tool_result 在不同场景（Playground / 移动端 / 嵌入）可有不同渲染方式 |
| **优雅降级** | 前端不认识的 tool_use.name，fallback 到 JSON 折叠展示 |

### 事件流

工具调用在 Content 层产生以下事件序列，与 LLM Streaming API 完全一致：

```
content_start  index:N  content_block:{type:"tool_use", id:"toolu_01", name:"query_database"}
content_delta  index:N  delta:{type:"input_json", text:"..."}   (多次)
content_stop   index:N
content_start  index:N+1  content_block:{type:"tool_result", tool_use_id:"toolu_01", content:[...]}
content_stop   index:N+1
```

前端在收到 `content_start(type:"tool_result")` 时，通过 `tool_use_id` 回查对应的 `tool_use.name`，选择渲染组件。

### 5.5.1 渲染架构——三层模型

**核心问题**：协议只负责搬运数据，那"数据怎么变成 UI"由谁说了算？

```
答案：三层分工

┌─────────────────────────────────────────────────────────────────┐
│ L1  Protocol Layer   (协议层 — 不可修改)                          │
│     Content 层事件原样搬运 tool_result                            │
│     数据格式 = LLM API 格式                                      │
├─────────────────────────────────────────────────────────────────┤
│ L2  Manifest Layer   (清单层 — 配置文件，无需改代码)               │
│     Tool Manifest 描述"这个工具的结果长什么样、建议怎么画"          │
│     格式 = JSON 配置                                             │
├─────────────────────────────────────────────────────────────────┤
│ L3  Renderer Layer   (渲染层 — 组件库 + 注册表)                   │
│     通用渲染器库（表格、代码、Markdown、图表...）                   │
│     按 Manifest 的 display_type 自动选择                          │
│     新工具 → 改 Manifest 配置即可，无需新建组件                     │
└─────────────────────────────────────────────────────────────────┘
```

**分工原则**：

| 谁 | 定义什么 | 怎么改 |
|----|---------|--------|
| **协议** | 事件格式、Content 层结构 | 不改 |
| **Agent / 后端** | 工具返回的数据结构 | 修改工具实现 |
| **Tool Manifest** | 数据结构描述 + 渲染建议 | 改 JSON 配置文件，不改代码 |
| **前端渲染器库** | 通用组件（Table、Code、Chart 等） | 仅当需要全新 UI 形态时才改代码 |

**关键结论**：

> **格式由谁定义？** → 数据格式由 Agent/工具定义，渲染方式由 Manifest 配置 + 前端渲染器库共同决定。协议不参与这个决策。
>
> **怎么避免频繁改代码？** → 绝大多数新工具只需添加一条 Manifest 配置，复用已有的通用渲染器（表格、代码块、Markdown 等）。只有当出现全新的 UI 形态（如 3D 模型查看器）时才需要写新组件。

### 5.5.2 Tool Manifest（工具渲染清单）

Tool Manifest 是一个 JSON 配置文件，描述每个工具的结果形状和渲染建议。它**不属于协议**，是前端 SDK 的配置层。

**加载时机**：Session 建立时，前端从 Server 拉取（或从本地配置读取）当前可用工具的 Manifest。

```typescript
interface ToolManifest {
  tools: Record<string, ToolRenderConfig>
}

interface ToolRenderConfig {
  name: string                        // 工具名，与 tool_use.name 一致
  display_name: string                // 人类可读名称，用于 UI 标题
  icon?: string                       // 图标标识（icon name 或 URL）

  result: {
    display_type: DisplayType         // 渲染类型（见下表）
    schema?: JSONSchema               // 结果数据的 JSON Schema（可选，用于类型安全渲染）
    config?: Record<string, unknown>  // 渲染器特定配置
  }

  input?: {
    predictive?: boolean              // 是否启用 Predictive State（§5.6）
    display_type?: DisplayType        // 输入参数的预览渲染类型
    config?: Record<string, unknown>
  }
}
```

**DisplayType 枚举**（通用渲染器类型）：

| display_type | 通用渲染器 | 适用场景 | 必备？ |
|-------------|-----------|---------|--------|
| `"table"` | DataTable | 结构化行列数据 | 是 |
| `"code"` | CodeBlock | 代码、SQL、配置文件 | 是 |
| `"markdown"` | MarkdownRenderer | 富文本、文档 | 是 |
| `"json"` | JSONCollapse | 任意 JSON（默认 fallback） | 是 |
| `"image"` | ImagePreview | 图片 URL / base64 | 是 |
| `"file_list"` | FileList | 文件列表（名称、大小、链接） | 是 |
| `"chart"` | Chart | 图表（line / bar / pie） | 推荐 |
| `"map"` | MapView | 地理坐标 | 可选 |
| `"diff"` | DiffViewer | 文本差异对比 | 可选 |
| `"terminal"` | TerminalOutput | 命令行输出 | 可选 |
| `"html"` | SafeHTMLRenderer | 安全渲染的 HTML 片段 | 可选 |
| `"card"` | CardTemplateRenderer | 键值对数据，通过 Mustache 模板渲染（§5.9.4） | 推荐 |
| `"custom"` | 自定义组件 | 业务特定 UI | 按需 |

### 5.5.3 Manifest 配置示例

一个典型项目的 `tool-manifest.json`：

```json
{
  "tools": {
    "query_database": {
      "name": "query_database",
      "display_name": "数据库查询",
      "icon": "database",
      "result": {
        "display_type": "table",
        "schema": {
          "type": "object",
          "properties": {
            "columns": { "type": "array", "items": { "type": "string" } },
            "rows": { "type": "array", "items": { "type": "array" } },
            "total_count": { "type": "number" }
          }
        },
        "config": {
          "max_rows": 100,
          "sortable": true,
          "searchable": true,
          "export_csv": true
        }
      },
      "input": {
        "predictive": true,
        "display_type": "code",
        "config": { "language": "sql" }
      }
    },

    "web_search": {
      "name": "web_search",
      "display_name": "网络搜索",
      "icon": "search",
      "result": {
        "display_type": "custom",
        "config": {
          "component": "SearchResults"
        }
      }
    },

    "run_code": {
      "name": "run_code",
      "display_name": "代码执行",
      "icon": "terminal",
      "result": {
        "display_type": "terminal",
        "config": {
          "show_exit_code": true,
          "max_lines": 500
        }
      },
      "input": {
        "predictive": true,
        "display_type": "code",
        "config": { "language": "auto" }
      }
    },

    "generate_chart": {
      "name": "generate_chart",
      "display_name": "图表生成",
      "icon": "chart",
      "result": {
        "display_type": "chart",
        "config": {
          "chart_type_field": "type",
          "data_field": "data",
          "options_field": "options"
        }
      }
    },

    "read_file": {
      "name": "read_file",
      "display_name": "文件读取",
      "icon": "file",
      "result": {
        "display_type": "code",
        "config": {
          "language_field": "language",
          "content_field": "content",
          "line_numbers": true,
          "collapsible": true,
          "max_lines": 200
        }
      }
    }
  }
}
```

**关键设计**：

| 设计点 | 说明 |
|--------|------|
| `display_type` 指向通用渲染器 | 绝大多数工具用内置渲染器即可，不需要写新组件 |
| `schema` 描述数据形状 | 渲染器按 schema 提取字段，不硬编码路径 |
| `config` 控制渲染细节 | 同一个渲染器通过不同 config 呈现不同效果 |
| `config.*_field` 字段映射 | 告诉渲染器"数据在哪个字段"，而不是硬编码字段名 |
| `input.predictive` | 声明是否启用 Predictive State（§5.6） |
| `custom` 类型 | 当通用渲染器不够时，指定自定义组件名 |

### 5.5.4 渲染器注册表（Renderer Registry）

前端维护一个渲染器注册表，将 `display_type` 映射到实际组件。

```typescript
interface RendererRegistry {
  renderers: Map<DisplayType, RendererFactory>
  register(type: DisplayType, factory: RendererFactory): void
  resolve(type: DisplayType): RendererFactory
}

type RendererFactory = (props: RendererProps) => UIComponent

interface RendererProps {
  tool_name: string                      // tool_use.name
  tool_display_name: string              // Manifest 中的 display_name
  icon?: string
  data: unknown                          // tool_result 的 content（已解析）
  schema?: JSONSchema                    // Manifest 中的 schema
  config: Record<string, unknown>        // Manifest 中的 config
  phase: "input_preview" | "result"      // 当前阶段
  partial_input?: unknown                // Predictive State 的 partial 数据
}
```

**注册表初始化**（前端 SDK 内置）：

```
RendererRegistry
  ├── "table"     → DataTableRenderer
  ├── "code"      → CodeBlockRenderer
  ├── "markdown"  → MarkdownRenderer
  ├── "json"      → JSONCollapseRenderer      ← 默认 fallback
  ├── "image"     → ImagePreviewRenderer
  ├── "file_list" → FileListRenderer
  ├── "chart"     → ChartRenderer
  ├── "diff"      → DiffViewerRenderer
  ├── "terminal"  → TerminalOutputRenderer
  ├── "html"      → SafeHTMLRenderer
  └── "custom"    → CustomComponentBridge     ← 查找注册的自定义组件
```

**业务方扩展**（注册自定义渲染器）：

```typescript
// 只有当通用渲染器不够时才需要写这个
registry.register("search_results", SearchResultsRenderer)
registry.register("3d_model", ThreeDModelViewer)
```

### 5.5.5 渲染决策流程

前端收到 `content_start(type:"tool_result")` 时的完整决策链：

```
收到 tool_result
  │
  ▼
通过 tool_use_id 回查 tool_use.name
  │
  ├── name = "query_database"
  │
  ▼
查找 Manifest: tools["query_database"]
  │
  ├── 找到 → display_type = "table", config = {sortable:true, ...}
  │          │
  │          ▼
  │        RendererRegistry.resolve("table")
  │          │
  │          ├── 找到 DataTableRenderer
  │          │     │
  │          │     ▼
  │          │   DataTableRenderer({
  │          │     data: parsedToolResult,
  │          │     schema: manifest.result.schema,
  │          │     config: manifest.result.config
  │          │   })
  │          │     │
  │          │     ▼
  │          │   渲染可排序、可搜索的数据表格
  │          │
  │          └── 未找到 → fallback 到 "json"
  │
  └── 未找到 Manifest（工具未配置）
        │
        ▼
      默认 fallback: JSONCollapseRenderer
        │
        ▼
      渲染可折叠的 JSON 树
```

### 5.5.6 新增工具的三条路径

| 场景 | 需要做什么 | 需要改代码？ | 需要前端发版？ |
|------|-----------|------------|--------------|
| **路径 A**：新工具，结果是表格数据 | 在 Manifest 加一条配置 `display_type: "table"` | 否 | 否 |
| **路径 B**：新工具，结果格式特殊但可用现有渲染器组合 | 在 Manifest 配置 + 调整 `config` 字段 | 否 | 否 |
| **路径 C**：新工具，需要全新 UI 形态 | 写一个新 Renderer 组件 + 注册 + Manifest 配置 | 是 | 是 |

**实际比例预期**：

```
路径 A (改配置)    ████████████████████████  70%
路径 B (改配置)    ████████                  20%
路径 C (写组件)    ███                       10%
```

### 5.5.7 Manifest 与 Predictive State 联动

Manifest 中的 `input.predictive` 和 `input.display_type` 控制 §5.6 Predictive State 的行为：

```
LLM 生成 tool_use 参数
  │
  ▼
content_delta(input_json)
  │
  ▼
查找 Manifest: tools[name].input
  │
  ├── predictive = true ?
  │     ├── 是 → 启用 Predictive State
  │     │        display_type = "code", config.language = "sql"
  │     │        │
  │     │        ▼
  │     │      CodeBlockRenderer({
  │     │        data: partialSQL,      ← 实时更新的 partial JSON
  │     │        config: {language:"sql"},
  │     │        phase: "input_preview"
  │     │      })
  │     │        │
  │     │        ▼
  │     │      用户看到 SQL 语句逐字出现在代码编辑器中
  │     │
  │     └── 否 → 不预览，等 tool_result
  │
  └── 无 input 配置 → 不预览
```

### 5.5.8 Manifest 的加载与更新

| 加载方式 | 说明 | 适用场景 |
|----------|------|---------|
| **静态内置** | 打包在前端 SDK 中 | 通用工具（search、code、file 等） |
| **Session 初始化时拉取** | `session_start` 后 GET `/api/v1/tools/manifest` | 动态工具列表 |
| **Manifest 变更推送** | 通过 `message_delta(type:"custom", name:"manifest_update")` 推送 | 运行时新增工具 |

**缓存策略**：Manifest 按版本号缓存，仅在版本变更时重新拉取。

```
GET /api/v1/tools/manifest
  Headers:
    If-None-Match: "v42"

  200 OK (有更新)
    ETag: "v43"
    Body: { tools: {...} }

  304 Not Modified (无更新)
```

### 5.5.9 通用渲染器的 config 规范

每个内置渲染器支持的 `config` 字段：

**DataTableRenderer (`"table"`)**

| config 字段 | 类型 | 默认 | 说明 |
|-------------|------|------|------|
| `columns_field` | string | `"columns"` | 数据中列名所在字段 |
| `rows_field` | string | `"rows"` | 数据中行数据所在字段 |
| `max_rows` | number | 100 | 最大显示行数 |
| `sortable` | boolean | true | 是否可排序 |
| `searchable` | boolean | false | 是否可搜索 |
| `export_csv` | boolean | false | 是否可导出 CSV |
| `pagination` | boolean | true | 是否分页 |
| `page_size` | number | 20 | 每页行数 |

**CodeBlockRenderer (`"code"`)**

| config 字段 | 类型 | 默认 | 说明 |
|-------------|------|------|------|
| `content_field` | string | — | 代码内容所在字段（不设则用整个 text） |
| `language` | string | `"auto"` | 语言（auto = 自动检测） |
| `language_field` | string | — | 语言所在字段（动态语言） |
| `line_numbers` | boolean | true | 是否显示行号 |
| `collapsible` | boolean | false | 是否可折叠 |
| `max_lines` | number | 500 | 最大显示行数（超出则折叠） |
| `highlight_lines` | number[] | — | 高亮行 |
| `copyable` | boolean | true | 是否可一键复制 |

**ChartRenderer (`"chart"`)**

| config 字段 | 类型 | 默认 | 说明 |
|-------------|------|------|------|
| `chart_type_field` | string | `"type"` | 图表类型所在字段 (line/bar/pie/scatter) |
| `data_field` | string | `"data"` | 数据所在字段 |
| `options_field` | string | `"options"` | 图表选项所在字段 |
| `responsive` | boolean | true | 自适应容器宽度 |
| `height` | string | `"300px"` | 图表高度 |

**TerminalOutputRenderer (`"terminal"`)**

| config 字段 | 类型 | 默认 | 说明 |
|-------------|------|------|------|
| `stdout_field` | string | `"stdout"` | 标准输出字段 |
| `stderr_field` | string | `"stderr"` | 标准错误字段 |
| `exit_code_field` | string | `"exit_code"` | 退出码字段 |
| `show_exit_code` | boolean | true | 是否显示退出码 |
| `max_lines` | number | 500 | 最大行数 |
| `ansi_colors` | boolean | true | 是否解析 ANSI 颜色码 |

### 5.5.10 渲染架构与协议的边界

```
                   协议的职责范围                         渲染架构的职责范围
          ┌─────────────────────────┐          ┌──────────────────────────────┐
          │                         │          │                              │
          │  content_start          │          │  Tool Manifest               │
          │  content_delta          │────────►│    ↓                          │
          │  content_stop           │          │  Renderer Registry           │
          │                         │          │    ↓                          │
          │  数据格式 = LLM API      │          │  通用渲染器 + 自定义组件      │
          │  不可修改               │          │    ↓                          │
          │                         │          │  实际 UI 组件                 │
          └─────────────────────────┘          └──────────────────────────────┘
               协议不知道渲染的存在                   渲染不依赖协议的变更
```

**关键约束**：

| 约束 | 说明 |
|------|------|
| Manifest 不进入协议 | Manifest 是应用层配置，不是协议事件的一部分 |
| 渲染器不修改事件 | 渲染器只读取 tool_result，不向协议写回任何信息 |
| Schema 是建议不是强制 | schema 帮助渲染器理解数据，但渲染器 MUST 容忍不符合 schema 的数据（优雅降级） |
| config 的 `*_field` 是间接引用 | 通过字段名映射解耦渲染器和工具的数据结构 |

## 5.6 Predictive State（预测性状态更新）

### 概念

Predictive State 是一种**纯前端技术**，利用 Content 层已有的 `content_delta(type:"input_json")` 事件流，在工具参数生成过程中实时预览 UI 效果。协议层无需任何改动。

### 原理

LLM 在生成 tool_use 参数时，会逐 token 推送 `input_json`（partial JSON）。前端使用 Partial JSON Parser 增量解析这些片段，将已解析的字段映射到 UI 状态。

```
LLM 生成过程                                前端实时预览
─────────────                              ─────────────
text: '{"sql": "SEL'               → SQL 编辑器出现 "SEL"
text: 'ECT * FROM u'               → SQL 变为 "SELECT * FROM u"
text: 'sers", "limi'               → SQL 变为 "SELECT * FROM users"
text: 't": 10}'                    → SQL 完整 + limit 字段出现 10
(tool_use 结束，tool_result 返回)            → 数据表格渲染完整结果
```

### 前端实现模式

```typescript
interface PredictiveStateConfig {
  toolName: string
  parser: (partialArgs: Partial<T>) => UIState
}

// 前端注册 Predictive State 处理器
predictiveState.register({
  toolName: "fill_form",
  parser: (args) => ({
    formFields: Object.entries(args)
      .filter(([_, v]) => v !== undefined)
      .map(([key, value]) => ({ field: key, value }))
  })
})
```

### 适用场景

| 场景 | 工具 | 预览效果 |
|------|------|---------|
| 表单填充 | `fill_form` | 字段逐个出现并填入值 |
| SQL 查询 | `query_database` | SQL 语句实时显示 |
| 代码生成 | `run_code` | 代码逐行出现在编辑器 |
| 搜索 | `web_search` | 搜索关键词实时显示 |

### 约束

| 约束 | 说明 |
|------|------|
| 纯前端 | Server 不需要知道前端是否启用了 Predictive State |
| 容错 | Partial JSON 解析失败时静默忽略，等下一个 delta |
| 不影响执行 | 预览是视觉效果，不影响 tool_use 的实际执行 |
| 依赖 Content 层 | 需要 `content_delta(input_json)` 事件流，SSE 和 WS 均可用 |

## 5.7 能力对比总结

| 维度 | 生成式 UI | 共享状态 | HITL | 前端工具 |
|------|----------|---------|------|---------|
| delta.type | `a2ui` | `state` | `hitl` | `frontend_tool` |
| 回传 method | `surface.interact` | `state.patch` | `hitl.resolve` | `frontend_tool.result` |
| 暂停模型 | 不暂停 | 不暂停 | **暂停** | **暂停** |
| 数据方向 | S→C 推送 + C→S 回传 | **双向**同步 | S→C 推送 + C→S 决策 | S→C 调用 + C→S 结果 |
| 持久化 | 无 | scope 级 | volatile/durable | 无 |
| 超时 | 无 | 无 | `timeout_ms` | `timeout_ms` |
| 注册 | 无 | 无 | 无 | **需要注册** |

**统一模式**：四大能力共享同一个 `message_delta` 容器，推送经持久化管线（有 seq），回传走三帧 req/res。这确保了：

- 断线重连时所有交互型推送可重放
- 去重对所有类型生效
- 中间件链对所有类型统一拦截
- 前端路由逻辑统一（按 `delta.type` switch）

**工具结果渲染**（§5.5）和 **Predictive State**（§5.6）不使用 `message_delta`，而是复用 Content 层已有的事件流，属于前端侧增强，不增加协议复杂度。

## 5.8 会话中断与恢复

### 概念

会话中断是指 Client 主动终止正在进行的 Agent 生成过程。与 HITL（Agent 主动暂停等待人工决策）相反，中断是**Client 发起的强制停止**。

### 设计动机

用户在以下场景需要中断：

| 场景 | 触发 | 期望行为 |
|------|------|---------|
| Agent 回复跑偏 | 用户点击"停止" | 立即停止生成，保留已有内容 |
| 生成时间过长 | 用户失去耐心 | 停止并允许追加指令 |
| 误操作 | 发错了消息 | 停止当前回复 |

### 中断方法

**WebSocket**：`req{method: "chat.abort"}`

```typescript
// req
{
  type: "req",
  id: "abort_01",
  method: "chat.abort",
  params: {
    session_id: string
    conversation_id?: string     // 可选：不指定则中断当前活跃对话
    preserve_partial?: boolean   // 默认 true：保留已生成内容
  }
}

// res
{
  type: "res",
  id: "abort_01",
  ok: true,
  data: {
    aborted_message_id?: string  // 被中断的消息 ID
    preserved_content: boolean   // 是否保留了部分内容
  }
}
```

**SSE 降级**：`POST /api/v1/chat/{session_id}/abort`

### 中断时序

```
Client                          Server                          Agent
  │                               │                               │
  │ (Agent 正在生成中)              │                               │
  │◄── event{content_delta} ──────│◄── emit content_delta ────────│
  │◄── event{content_delta} ──────│◄── emit content_delta ────────│
  │                               │                               │
  │── req{chat.abort} ───────────►│                               │
  │                               │── interrupt signal ──────────►│
  │                               │                               │ Agent 收到中断
  │                               │                               │
  │◄── event{content_stop} ───────│  (关闭当前 content block)       │
  │◄── event{message_stop,        │                               │
  │         stop_reason:"abort"}──│                               │
  │◄── event{conversation_stop} ──│                               │
  │◄── event{done} ───────────────│                               │
  │                               │                               │
  │◄── res{ok:true} ─────────────│                               │
```

### 部分结果保留

`preserve_partial` 控制中断后已生成内容的处理：

| preserve_partial | 行为 | 适用场景 |
|------------------|------|---------|
| `true`（默认） | 已推送的 content_delta 保留在对话历史中 | Agent 跑偏但部分内容有用 |
| `false` | Server 推送 `conversation_delta(messages_snapshot)` 回滚到用户消息 | 误操作，完全重来 |

**preserve_partial=true 时的事件流**：

```
seq  type               说明
───  ────               ────
 4   content_start      [index:0] text 块开始
 5   content_delta      [index:0] "你好，让我来"
 6   content_delta      [index:0] "分析一下..."      ← 用户此时点击中断
 7   content_stop       [index:0] text 块正常关闭
 8   message_stop       stop_reason: "abort"
 9   conversation_stop  对话结束
10   done               流结束
```

保留的部分结果作为 `stop_reason:"abort"` 的消息存入对话历史。Agent 在下一轮对话中可以看到这条不完整的回复，并据此调整行为。

**preserve_partial=false 时的事件流**：

```
seq  type                  说明
───  ────                  ────
 4   content_start         [index:0] text 块开始
 5   content_delta         [index:0] "你好，让我来"     ← 用户此时点击中断
 6   content_stop          [index:0] text 块关闭
 7   message_stop          stop_reason: "abort"
 8   conversation_delta    messages_snapshot (不含被中断的消息)
 9   conversation_stop     对话结束
10   done                  流结束
```

### 中断与 HITL/Frontend Tool 的交互

中断发生时，如果 Agent 正在等待 HITL 或 Frontend Tool：

| 阻塞状态 | 中断行为 |
|----------|---------|
| HITL pending | HITL 状态 → `cancelled`，Agent 收到 `AbortError` |
| Frontend Tool pending | Future 设为 `CancelledError`，Agent 收到 `ToolCancelledError` |
| LLM 生成中 | 立即停止 token 生成 |
| 工具执行中 | 发送中断信号（最终一致，工具可能已完成） |

### 中断状态机

```
[idle] ── chat.send ──► [generating] ── natural end ──► [idle]
                              │
                              │ chat.abort
                              ▼
                         [aborting] ── cleanup done ──► [idle]
                              │
                              └── 推送 message_stop(abort)
                                  + conversation_stop
                                  + done
```

### 错误码

| 错误码 | 说明 |
|--------|------|
| `NO_ACTIVE_GENERATION` | 当前没有正在进行的生成（已结束或未开始） |
| `ABORT_IN_PROGRESS` | 上一个中断尚未完成（避免重复中断） |

### cancel 帧 vs chat.abort 决策表

`cancel`（§4.6）取消单个 req，`chat.abort`（§5.8）中断整个生成过程。虽然 `cancel` 一个 `chat.send` 也会级联取消生成，但两者的语义和适用场景不同：

| 场景 | 使用 | 原因 |
|------|------|------|
| 用户点击"停止生成" | `chat.abort` | 语义明确：中断 Agent 生成，保留/丢弃部分结果 |
| 用户关闭 HITL 弹窗（不做选择） | `cancel{id: hitl_req_id}` | 只取消单个 HITL 请求，不影响 Agent 继续生成 |
| 前端工具执行超时 | `cancel{id: tool_req_id}` | 只取消单个工具调用 |
| 页面导航/关闭 | `chat.abort` + 关闭连接 | 需要完整的清理流程 |
| 用户发送新消息覆盖当前生成 | `chat.abort` 然后 `chat.send` | 先中断再开始新对话 |
| 取消一个尚未完成的 `state.patch` | `cancel{id: patch_req_id}` | 只取消单个状态修改 |

**关键区别**：

| 维度 | `cancel` 帧 | `chat.abort` |
|------|-------------|-------------|
| 作用范围 | 单个 req（by `id`） | 整个 Session/Conversation 的当前生成 |
| 级联效果 | cancel `chat.send` 会级联取消生成 | 直接中断生成 + 取消所有 pending HITL/Tool |
| stop_reason | `"cancelled"` | `"abort"` |
| 部分结果控制 | 无（取消就是取消） | `preserve_partial` 参数控制 |
| 适用传输 | 仅 WebSocket（SSE 无 cancel 帧） | WebSocket + SSE 降级 |

## 5.9 动态渲染指南

> **注意**：本节为**参考性内容**（informative, not normative）。本节描述的是前端 SDK 实现指南和推荐实践，而非协议规范性要求。实现者可以根据自身前端框架和 UX 需求调整具体实现方式，只要遵守 §2–§5.8 中的协议规范即可。

§5.5 定义了"数据怎么映射到组件"的静态架构（Manifest → Registry → Renderer）。本节补充"协议事件流怎么一步步变成屏幕上的 UI"的动态过程——流式渲染生命周期、多块消息组成、自动类型推断、卡片模板、主题令牌、加载状态与错误渲染。

**与 §5.5 的关系**：§5.5 是"选什么组件"，§5.9 是"组件怎么跑起来"。两者互补，不重叠。

### 5.9.1 流式渲染生命周期

协议 Content 层事件（`content_start` / `content_delta` / `content_stop`）到前端组件生命周期的映射：

```
协议事件                    组件生命周期                  用户可见状态
─────────────────────────────────────────────────────────────────────
content_start         →     mount(skeleton)          →   骨架占位符
                            allocateLayout()              按 display_type 预分配尺寸

首个 content_delta    →     activate()               →   骨架消失，内容开始出现
                            startStreaming()               光标闪烁 / 打字机效果

后续 content_delta    →     appendDelta(delta)       →   增量追加
                            (text: 追加字符)               实时更新
                            (input_json: partial parse)
                            (tool_result: 分块到达)

content_stop          →     finalize()               →   完成态
                            enableInteractions()          隐藏光标，启用排序/复制/导出
                            commitLayout()                固定最终尺寸
```

**组件状态机**：

```
                    content_start
                         │
                         ▼
                   ┌───────────┐
                   │  Skeleton  │  骨架/占位符
                   └─────┬─────┘
                         │ 首个 content_delta
                         ▼
                   ┌───────────┐
              ┌───►│ Streaming  │◄───┐  后续 content_delta
              │    └─────┬─────┘    │
              │          │          │
              └──────────┘          │
                         │ content_stop
                         ▼
                   ┌───────────┐
                   │ Complete   │  完成态，启用交互
                   └─────┬─────┘
                         │ 组件卸载
                         ▼
                   ┌───────────┐
                   │ Unmounted  │
                   └───────────┘
```

**各 Content Block 类型的增量更新策略**：

| block type | delta 类型 | 增量策略 | 完成时动作 |
|-----------|-----------|---------|-----------|
| `text` | `text` | 字符追加到文本缓冲区，Markdown 渲染器增量解析 | 最终 Markdown 渲染，启用复制 |
| `thinking` | `thinking` | 字符追加，折叠区域内实时更新 | 折叠，显示摘要 |
| `tool_use` | `input_json` | partial JSON 累积，触发 Predictive State 预览 | 冻结预览，等待 tool_result |
| `tool_result` | 通常一次性到达 | 整块数据传给渲染器 | 渲染器进入 Complete 态 |

**Predictive State 到 Result 的无缝过渡**：

当 `tool_use` 的 Manifest 配置了 `input.predictive = true` 时，前端在 `input_json` 阶段已经用渲染器预览了工具参数。当 `tool_result` 到达时，需要无缝过渡而非"闪一下消失再出现"：

```
阶段 1: input_preview（Predictive State）
  ┌─────────────────────────────────────┐
  │ 📊 数据库查询                        │
  │ ┌─────────────────────────────────┐ │
  │ │ SELECT * FROM users             │ │  ← CodeBlockRenderer
  │ │ WHERE age > 25█                 │ │     phase: "input_preview"
  │ │                                 │ │     光标闪烁，SQL 逐字出现
  │ └─────────────────────────────────┘ │
  └─────────────────────────────────────┘

阶段 2: executing（content_stop 后，tool_result 前）
  ┌─────────────────────────────────────┐
  │ 📊 数据库查询                        │
  │ ┌─────────────────────────────────┐ │
  │ │ SELECT * FROM users             │ │  ← SQL 冻结
  │ │ WHERE age > 25                  │ │     光标消失
  │ └─────────────────────────────────┘ │
  │ ⏳ 执行中...                         │  ← 进度指示器
  └─────────────────────────────────────┘

阶段 3: result（tool_result 到达）
  ┌─────────────────────────────────────┐
  │ 📊 数据库查询                        │
  │ ┌─────────────────────────────────┐ │
  │ │ SELECT * FROM users             │ │  ← SQL 保留（可折叠）
  │ │ WHERE age > 25                  │ │
  │ └─────────────────────────────────┘ │
  │ ┌─────────────────────────────────┐ │
  │ │ name  │ age │ email             │ │  ← DataTableRenderer
  │ │ Alice │ 30  │ alice@...         │ │     phase: "result"
  │ │ Bob   │ 28  │ bob@...           │ │     渐入动画
  │ └─────────────────────────────────┘ │
  └─────────────────────────────────────┘
```

**过渡规则**：

| 过渡 | 行为 | 动画 |
|------|------|------|
| Skeleton → Streaming | 骨架淡出，内容淡入 | `opacity` 过渡，150ms |
| input_preview → executing | 冻结预览内容，追加进度指示器 | 无闪烁，仅追加 |
| executing → result | 进度指示器收起，结果区域展开 | `height` 过渡 + `opacity` 淡入，300ms |
| Streaming → Complete | 隐藏光标/加载指示器，启用交互控件 | 控件 `opacity` 淡入，200ms |

**前端实现接口**：

```typescript
interface StreamingRenderer {
  mount(block: ContentBlockStart, manifest?: ToolRenderConfig): void
  appendDelta(delta: ContentDelta): void
  finalize(): void
  destroy(): void

  readonly state: "skeleton" | "streaming" | "complete" | "unmounted"
}

interface PredictiveTransition {
  freezePreview(): void
  showExecuting(status?: string): void
  transitionToResult(data: unknown, animate?: boolean): void
}
```

### 5.9.2 多块消息组成模型

一条 Agent 消息（`message_start` → `message_stop`）可能包含多个 Content Block，前端需要将它们组成一个连贯的消息气泡。

**消息内部布局**：

```
┌─ Message Bubble ──────────────────────────────────────────┐
│                                                           │
│  [TextGroup]         连续 text block 合并为一段            │
│  "让我查一下数据库..."                                      │
│                                                           │
│  [ThinkingRegion]    thinking block → 可折叠区域           │
│  ▶ 思考过程 (点击展开)                                      │
│                                                           │
│  [ToolCard]          tool_use + tool_result 组成卡片       │
│  ┌─ 📊 数据库查询 ─────────────────────────────────────┐  │
│  │  input:  SELECT * FROM users WHERE age > 25        │  │
│  │  result: ┌──────────────────────────────────┐      │  │
│  │          │ name  │ age │ email              │      │  │
│  │          │ Alice │ 30  │ alice@...          │      │  │
│  │          └──────────────────────────────────┘      │  │
│  └────────────────────────────────────────────────────┘  │
│                                                           │
│  [TextGroup]         又一段连续文本                         │
│  "根据查询结果，共有 2 位用户..."                            │
│                                                           │
│  [ToolCard]          第二个工具调用                         │
│  ┌─ 📈 图表生成 ───────────────────────────────────────┐  │
│  │  result: [Chart Component]                         │  │
│  └────────────────────────────────────────────────────┘  │
│                                                           │
│  [TextGroup]         结尾文本                              │
│  "如上图所示..."                                           │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

**Block 分组策略**：

前端收到的 Content Block 序列需要分组后再渲染，规则如下：

| 规则 | 说明 | 示例 |
|------|------|------|
| **连续 text 合并** | 相邻的 `text` block 合并为一个 TextGroup | index:0 text + index:4 text（中间有 tool）→ 两个独立 TextGroup |
| **tool_use + tool_result 配对** | 通过 `tool_use_id` 配对，组成一个 ToolCard | tool_use(id:A) + tool_result(tool_use_id:A) → 一张卡片 |
| **thinking 独立区域** | `thinking` block 始终独立为 ThinkingRegion | 不与 text 合并 |
| **未配对 tool_use** | tool_use 到达但 tool_result 尚未到达 → 显示为"执行中"的 ToolCard | 流式过程中常见 |
| **未配对 tool_result** | tool_result 找不到对应 tool_use → 独立渲染 | 异常情况，优雅降级 |

**分组算法**：

```
输入：Content Block 按 index 排序的有序序列
输出：BlockGroup 列表

current_text_group = null
groups = []

for each block in blocks:
  if block.type == "text":
    if current_text_group == null:
      current_text_group = new TextGroup()
    current_text_group.append(block)

  else:
    if current_text_group != null:
      groups.push(current_text_group)
      current_text_group = null

    if block.type == "thinking":
      groups.push(new ThinkingRegion(block))

    if block.type == "tool_use":
      groups.push(new ToolCard(block))  // 暂无 result

    if block.type == "tool_result":
      matched_card = groups.findLast(g =>
        g.type == "tool_card" && g.tool_use_id == block.tool_use_id
      )
      if matched_card:
        matched_card.setResult(block)   // 配对成功
      else:
        groups.push(new OrphanResult(block))  // 降级

if current_text_group != null:
  groups.push(current_text_group)

return groups
```

**TypeScript 接口**：

```typescript
type BlockGroupType = "text" | "thinking" | "tool_card" | "orphan_result"

interface BlockGroup {
  type: BlockGroupType
  blocks: ContentBlock[]
  startIndex: number
  endIndex: number
}

interface TextGroup extends BlockGroup {
  type: "text"
  mergedText: string
}

interface ThinkingRegion extends BlockGroup {
  type: "thinking"
  collapsed: boolean
  summary?: string
}

interface ToolCard extends BlockGroup {
  type: "tool_card"
  toolUse: ToolUseBlock
  toolResult?: ToolResultBlock
  phase: "input_preview" | "executing" | "result" | "error"
  manifest?: ToolRenderConfig
}

interface OrphanResult extends BlockGroup {
  type: "orphan_result"
  toolResult: ToolResultBlock
}

interface MessageComposition {
  messageId: string
  groups: BlockGroup[]
  isStreaming: boolean

  appendBlock(block: ContentBlock): void
  finalizeBlock(index: number): void
  finalizeMessage(): void
}
```

**视觉层级**：

| 层级 | 元素 | 视觉表现 |
|------|------|---------|
| 主体 | TextGroup | 正常字号，无边框，直接嵌入消息气泡 |
| 附属 | ToolCard | 带边框的卡片，缩进或内嵌，有图标和标题 |
| 辅助 | ThinkingRegion | 灰色折叠区域，默认收起，字号较小 |
| 降级 | OrphanResult | 虚线边框，提示"未关联的工具结果" |

### 5.9.3 自动类型推断

当 Tool Manifest 中没有某个工具的配置时（新工具刚上线、第三方 MCP 工具等），前端不应只能显示 JSON 折叠。自动类型推断器（AutoDetector）通过启发式规则推断最佳 `display_type`。

**推断链**（按优先级从高到低执行，首个命中即停止）：

```
tool_result.content
  │
  ▼
AutoDetector.detect(data)
  │
  ├─ [1] TableDetector        优先级: 100
  │    条件: (data.columns && data.rows)
  │          || (Array.isArray(data) && data.length > 0
  │              && typeof data[0] === "object")
  │    结果: display_type = "table"
  │    置信度: columns+rows → 0.95, Array<Object> → 0.80
  │
  ├─ [2] ImageDetector         优先级: 90
  │    条件: typeof data === "string"
  │          && (/^https?:\/\/.+\.(png|jpg|gif|svg|webp)/i.test(data)
  │              || /^data:image\//.test(data))
  │    结果: display_type = "image"
  │    置信度: 0.95
  │
  ├─ [3] CodeDetector          优先级: 80
  │    条件: typeof data === "string"
  │          && (containsCodePatterns(data) || data.language)
  │    判据: 缩进一致性、关键字密度、括号配对、
  │          首行 shebang/import/package
  │    结果: display_type = "code", config.language = inferLanguage(data)
  │    置信度: 0.60 ~ 0.90（取决于特征匹配数）
  │
  ├─ [4] MarkdownDetector      优先级: 70
  │    条件: typeof data === "string"
  │          && containsMarkdownSyntax(data)
  │    判据: 标题(#)、列表(- / *)、链接([])、
  │          粗体(**)、代码块(```)
  │    结果: display_type = "markdown"
  │    置信度: 0.70 ~ 0.90
  │
  ├─ [5] KeyValueDetector      优先级: 60
  │    条件: typeof data === "object" && !Array.isArray(data)
  │          && Object.keys(data).length <= 12
  │          && allValuesArePrimitive(data)
  │    结果: display_type = "card"（使用自动生成的卡片模板）
  │    置信度: 0.75
  │
  ├─ [6] TerminalDetector      优先级: 50
  │    条件: typeof data === "object"
  │          && ("stdout" in data || "stderr" in data
  │              || "exit_code" in data)
  │    结果: display_type = "terminal"
  │    置信度: 0.90
  │
  └─ [7] JSONFallback          优先级: 0
       条件: 始终命中
       结果: display_type = "json"
       置信度: 1.0（确定性 fallback）
```

**推断结果接口**：

```typescript
interface AutoDetectResult {
  display_type: DisplayType
  confidence: number           // 0.0 ~ 1.0
  config: Record<string, unknown>  // 推断出的渲染器配置
  detector: string             // 命中的检测器名称
}

interface AutoDetector {
  readonly chain: DetectorEntry[]

  detect(data: unknown): AutoDetectResult

  register(entry: DetectorEntry): void
}

interface DetectorEntry {
  name: string
  priority: number             // 数值越大越先执行
  detect(data: unknown): AutoDetectResult | null
}
```

**与 Manifest 的优先级关系**：

```
收到 tool_result
  │
  ▼
查找 Manifest: tools[name]
  │
  ├── 找到 → 使用 Manifest 配置（Manifest 优先级最高）
  │
  └── 未找到
        │
        ▼
      AutoDetector.detect(data)
        │
        ├── confidence >= 0.7 → 使用推断结果
        │
        └── confidence < 0.7 → fallback 到 "json"
```

**业务方扩展**：可通过 `AutoDetector.register()` 注入自定义检测器，例如检测特定业务数据格式。自定义检测器的 priority SHOULD 设为 200+，确保优先于内置检测器。

### 5.9.4 卡片模板系统

80% 的工具结果是"一组键值对"——天气、文件信息、用户资料、API 状态等。为这些场景提供模板驱动的卡片渲染器（CardTemplateRenderer），无需编写自定义组件即可产出高质量 UI。

**Manifest 配置方式**：

在 Tool Manifest 中将 `display_type` 设为 `"card"`，并在 `config.template` 中定义模板：

```json
{
  "tools": {
    "get_weather": {
      "name": "get_weather",
      "display_name": "天气查询",
      "icon": "cloud-sun",
      "result": {
        "display_type": "card",
        "config": {
          "template": {
            "title": "{{city}} 天气",
            "subtitle": "{{temperature}}°C · {{description}}",
            "icon_url": "{{icon_url}}",
            "fields": [
              { "label": "湿度", "value": "{{humidity}}%", "icon": "droplet" },
              { "label": "风速", "value": "{{wind_speed}} km/h", "icon": "wind" },
              { "label": "体感温度", "value": "{{feels_like}}°C", "icon": "thermometer" },
              { "label": "能见度", "value": "{{visibility}} km" }
            ],
            "footer": "更新于 {{updated_at}}",
            "accent_color": "{{#is_sunny}}#FF9500{{/is_sunny}}{{^is_sunny}}#5AC8FA{{/is_sunny}}"
          }
        }
      }
    },

    "get_user_profile": {
      "name": "get_user_profile",
      "display_name": "用户资料",
      "icon": "user",
      "result": {
        "display_type": "card",
        "config": {
          "template": {
            "title": "{{display_name}}",
            "subtitle": "@{{username}} · {{role}}",
            "avatar_url": "{{avatar_url}}",
            "fields": [
              { "label": "邮箱", "value": "{{email}}", "icon": "mail" },
              { "label": "注册时间", "value": "{{created_at}}", "icon": "calendar" },
              { "label": "最后登录", "value": "{{last_login}}" }
            ],
            "actions": [
              { "label": "查看详情", "action": "surface.interact", "params": { "type": "navigate", "url": "/users/{{id}}" } }
            ]
          }
        }
      }
    }
  }
}
```

**模板语法**：

采用 Mustache 模板语法（逻辑最少化），支持以下特性：

<div v-pre>

| 语法 | 说明 | 示例 |
|------|------|------|
| `{{field}}` | 变量插值 | `{{city}}` → "北京" |
| `{{#condition}}...{{/condition}}` | 条件块（truthy 时渲染） | `{{#is_error}}❌{{/is_error}}` |
| `{{^condition}}...{{/condition}}` | 反向条件块（falsy 时渲染） | `{{^is_error}}✅{{/is_error}}` |
| `{{#list}}...{{/list}}` | 列表迭代 | `{{#tags}}{{.}} {{/tags}}` |

</div>

**卡片布局结构**：

```
┌─ Card ──────────────────────────────────────────────┐
│                                                     │
│  ┌────┐  Title                                      │
│  │icon│  Subtitle                                   │
│  └────┘                                             │
│                                                     │
│  ┌─ Fields ──────────────────────────────────────┐  │
│  │  🌡️ 湿度        65%                           │  │
│  │  💨 风速        12 km/h                        │  │
│  │  🌡️ 体感温度    22°C                           │  │
│  │     能见度      10 km                          │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌─ Actions ─────────────────────────────────────┐  │
│  │  [查看详情]                                    │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  Footer: 更新于 2026-03-31 14:30                     │
│                                                     │
└─────────────────────────────────────────────────────┘
```

**TypeScript 接口**：

```typescript
interface CardTemplate {
  title?: string                    // Mustache 模板
  subtitle?: string
  icon_url?: string                 // 模板，支持动态 URL
  avatar_url?: string               // 圆形头像（与 icon_url 互斥）
  fields?: CardField[]
  footer?: string
  accent_color?: string             // 模板，支持条件色
  actions?: CardAction[]
  layout?: "vertical" | "horizontal"  // 字段布局方向，默认 vertical
  columns?: number                  // horizontal 布局时的列数，默认 2
}

interface CardField {
  label: string                     // Mustache 模板
  value: string                     // Mustache 模板
  icon?: string                     // 图标名称
  format?: "text" | "date" | "number" | "url" | "badge"
  badge_color?: string              // format="badge" 时的颜色
}

interface CardAction {
  label: string
  action: "surface.interact"        // 触发 ZAP surface.interact 事件
  params: Record<string, unknown>   // Mustache 模板值
  style?: "primary" | "secondary" | "danger"
}
```

**自动卡片生成**：

当 AutoDetector（§5.9.3）的 KeyValueDetector 命中时，自动生成一个简单的卡片模板：

```typescript
function autoGenerateCardTemplate(data: Record<string, unknown>): CardTemplate {
  const fields = Object.entries(data)
    .filter(([_, v]) => typeof v !== "object")
    .map(([key, _]) => ({
      label: humanizeKey(key),      // "created_at" → "Created At"
      value: `{{${key}}}`,
    }))

  return {
    title: `{{${findTitleField(data) ?? Object.keys(data)[0]}}}`,
    fields,
  }
}
```

### 5.9.5 主题令牌系统

所有通用渲染器通过**主题令牌**（Theme Tokens）消费视觉样式，而非硬编码 CSS。不同产品（Playground / 移动端 / 嵌入式 Widget）注入不同的 ThemeTokens，同一 Manifest 配置产出不同视觉风格。

**ThemeTokens 接口**：

```typescript
interface ThemeTokens {
  // 基础
  surface: {
    bg: string                      // 背景色
    bg_secondary: string            // 次级背景（卡片、代码块）
    border: string                  // 边框色
    border_radius: string           // 圆角，如 "8px"
  }

  // 文字
  text: {
    primary: string                 // 主文字色
    secondary: string               // 次级文字（标签、时间戳）
    muted: string                   // 弱化文字（占位符）
    code: string                    // 行内代码文字色
    link: string                    // 链接色
  }

  // 状态色
  status: {
    success: string
    warning: string
    error: string
    info: string
  }

  // 表格
  table: {
    header_bg: string
    header_text: string
    row_hover: string
    stripe_bg: string
    border: string
  }

  // 代码块
  code: {
    bg: string
    text: string
    line_number: string
    highlight_bg: string            // 高亮行背景
    syntax_theme: string            // 语法高亮主题名（如 "github-dark"）
  }

  // 图表
  chart: {
    palette: string[]               // 数据系列颜色
    grid_color: string
    axis_color: string
    tooltip_bg: string
  }

  // 卡片
  card: {
    bg: string
    border: string
    shadow: string                  // box-shadow 值
    header_bg: string
    accent_border_width: string     // 左侧强调色条宽度
  }

  // 动画
  motion: {
    duration_fast: string           // "150ms"
    duration_normal: string         // "300ms"
    duration_slow: string           // "500ms"
    easing: string                  // "cubic-bezier(0.4, 0, 0.2, 1)"
  }

  // 间距
  spacing: {
    block_gap: string               // BlockGroup 之间的间距
    card_padding: string            // 卡片内边距
    inline_gap: string              // 行内元素间距
  }
}
```

**预设主题**：

```typescript
const PlaygroundTheme: ThemeTokens = {
  surface: { bg: "#ffffff", bg_secondary: "#f9fafb", border: "#e5e7eb", border_radius: "12px" },
  text: { primary: "#111827", secondary: "#6b7280", muted: "#9ca3af", code: "#dc2626", link: "#2563eb" },
  status: { success: "#059669", warning: "#d97706", error: "#dc2626", info: "#2563eb" },
  // ... 其余字段
}

const MobileTheme: ThemeTokens = {
  surface: { bg: "#ffffff", bg_secondary: "#f5f5f5", border: "#e0e0e0", border_radius: "16px" },
  text: { primary: "#1a1a1a", secondary: "#757575", muted: "#bdbdbd", code: "#e53935", link: "#1976d2" },
  // ... 更大的字号、更大的触控区域
}

const EmbedTheme: ThemeTokens = {
  surface: { bg: "transparent", bg_secondary: "#f8f8f8", border: "#ddd", border_radius: "8px" },
  text: { primary: "inherit", secondary: "#888", muted: "#aaa", code: "#c7254e", link: "#0366d6" },
  // ... 继承宿主页面样式
}

const DarkTheme: ThemeTokens = {
  surface: { bg: "#0d1117", bg_secondary: "#161b22", border: "#30363d", border_radius: "12px" },
  text: { primary: "#e6edf3", secondary: "#8b949e", muted: "#484f58", code: "#ff7b72", link: "#58a6ff" },
  status: { success: "#3fb950", warning: "#d29922", error: "#f85149", info: "#58a6ff" },
  // ...
}
```

**渲染器消费 Token 的方式**：

渲染器通过 Context / Provider 获取当前 ThemeTokens，而非直接引用 CSS 变量或硬编码颜色值：

```typescript
interface ThemedRendererProps extends RendererProps {
  theme: ThemeTokens
}

// 渲染器实现示例（伪代码）
function DataTableRenderer(props: ThemedRendererProps) {
  const { theme, data, config } = props
  return Table({
    headerStyle: { background: theme.table.header_bg, color: theme.table.header_text },
    rowHoverStyle: { background: theme.table.row_hover },
    stripeStyle: { background: theme.table.stripe_bg },
    borderStyle: { borderColor: theme.table.border },
    // ...
  })
}
```

**与 A2UI 的样式隔离**：

§5.1 A2UI（Agent-to-UI）组件由 Agent 动态生成，拥有自己的样式。ThemeTokens 仅作用于**通用渲染器**（§5.5.4 Renderer Registry 中注册的组件），不侵入 A2UI 组件的样式空间：

```
┌─ Message Bubble ──────────────────────────────────┐
│                                                   │
│  [TextGroup]    ← ThemeTokens.text.*              │
│                                                   │
│  [ToolCard]     ← ThemeTokens.card.* + 渲染器 token │
│                                                   │
│  [A2UI 组件]    ← 自带样式，不受 ThemeTokens 影响    │
│                                                   │
└───────────────────────────────────────────────────┘
```

### 5.9.6 加载与过渡状态

定义渲染器在等待、过渡、流式输出各阶段的标准行为，确保一致的用户体验。

**阶段定义**：

```
用户发送消息
  │
  ▼
┌──────────────────────────────────────────────────────────────────┐
│ Phase 0: Pending                                                │
│ 消息已发送，等待 Agent 响应                                        │
│ UI: 消息气泡底部显示"思考中..."脉冲动画                              │
│ 时机: 用户消息发出 → 收到首个 message_start                        │
└──────────────────────────────────────────────────────────────────┘
  │ message_start
  ▼
┌──────────────────────────────────────────────────────────────────┐
│ Phase 1: Streaming Text                                         │
│ Agent 正在生成文本                                                │
│ UI: 文字逐字出现 + 末尾闪烁光标                                    │
│ 时机: content_start(text) → content_stop(text)                   │
└──────────────────────────────────────────────────────────────────┘
  │ content_start(tool_use)
  ▼
┌──────────────────────────────────────────────────────────────────┐
│ Phase 2: Tool Calling (Predictive State)                        │
│ LLM 正在生成工具参数                                              │
│ UI (有 Manifest + predictive):                                   │
│   工具卡片出现，参数实时预览（如 SQL 逐字出现）                       │
│ UI (无 Manifest):                                                │
│   工具卡片出现，显示工具名 + 骨架动画                                │
│ 时机: content_start(tool_use) → content_stop(tool_use)           │
└──────────────────────────────────────────────────────────────────┘
  │ content_stop(tool_use)
  ▼
┌──────────────────────────────────────────────────────────────────┐
│ Phase 3: Tool Executing                                         │
│ 工具正在后端执行                                                   │
│ UI: 工具卡片显示旋转加载指示器 + agent_status 文本                   │
│     如"正在查询数据库..." / "已执行 3/10 步"                        │
│ 时机: content_stop(tool_use) → content_start(tool_result)        │
│ 数据源: agent_status 事件（如有）                                   │
└──────────────────────────────────────────────────────────────────┘
  │ content_start(tool_result)
  ▼
┌──────────────────────────────────────────────────────────────────┐
│ Phase 4: Result Rendering                                       │
│ 工具结果到达，渲染器展示结果                                        │
│ UI: 加载指示器收起，结果区域展开（带过渡动画）                        │
│ 时机: content_start(tool_result) → content_stop(tool_result)     │
└──────────────────────────────────────────────────────────────────┘
  │ message_stop
  ▼
┌──────────────────────────────────────────────────────────────────┐
│ Phase 5: Complete                                               │
│ 消息生成完毕                                                      │
│ UI: 所有光标/加载指示器消失，交互控件启用                             │
│ 时机: message_stop                                               │
└──────────────────────────────────────────────────────────────────┘
```

**骨架屏规范**：

每种 `display_type` 在 Skeleton 阶段的占位符样式：

| display_type | 骨架屏形态 | 预估尺寸 |
|-------------|-----------|---------|
| `"table"` | 3 行 × 4 列的灰色矩形块 + 表头条 | 高度 200px |
| `"code"` | 5 行等宽灰色条（模拟代码行） | 高度 150px |
| `"markdown"` | 标题条 + 3 行不等宽灰色条 | 高度 120px |
| `"chart"` | 矩形区域 + 底部轴线 | 高度 300px |
| `"card"` | 圆形头像 + 2 行标题条 + 3 行字段条 | 高度 180px |
| `"image"` | 灰色矩形 + 中心图片图标 | 高度 200px |
| `"terminal"` | 深色矩形 + 3 行等宽条 | 高度 150px |
| `"json"` | 3 层缩进的灰色条 | 高度 120px |

骨架屏 MUST 使用脉冲动画（`pulse`），频率 1.5s，使用 `ThemeTokens.motion.duration_slow` 控制。

**进度指示器与 `agent_status` 联动**：

```typescript
interface ToolExecutionIndicator {
  toolName: string
  displayName: string               // 来自 Manifest
  icon?: string

  state: "calling" | "executing" | "done" | "error"
  statusText?: string               // 来自 agent_status 事件
  progress?: {                      // 来自 agent_status 事件（如有）
    current: number
    total: number
  }
  elapsed_ms: number                // 前端计时
}
```

**过渡动画规范**：

| 过渡场景 | 动画类型 | 时长 | 缓动函数 |
|---------|---------|------|---------|
| 骨架 → 内容 | 骨架淡出 + 内容淡入 | `motion.duration_fast` | `motion.easing` |
| 预览 → 执行中 | 预览区冻结 + 指示器滑入 | `motion.duration_fast` | `motion.easing` |
| 执行中 → 结果 | 指示器收起 + 结果区展开 | `motion.duration_normal` | `motion.easing` |
| 流式 → 完成 | 光标淡出 + 控件淡入 | `motion.duration_fast` | `motion.easing` |
| 新 BlockGroup 出现 | 从下方滑入 + 淡入 | `motion.duration_normal` | `motion.easing` |

所有动画 MUST 尊重用户的 `prefers-reduced-motion` 媒体查询。当该查询为 `reduce` 时，所有过渡 MUST 降级为即时切换（duration = 0）。

### 5.9.7 错误渲染规范

协议中的错误（`error` 事件、tool_result 中的错误、请求超时等）MUST 有标准化的渲染模式，不应由各渲染器各自处理。

**错误分类与渲染**：

| 错误来源 | 事件/字段 | 渲染方式 |
|---------|----------|---------|
| 工具执行失败 | `tool_result.content` 中包含 `is_error: true` | ToolCard 内嵌错误面板 |
| Agent 级错误 | `error` 事件（System 层） | 独立错误横幅（Banner） |
| 请求超时 | `CancelFrame` 或客户端超时 | 工具卡片显示超时提示 |
| 连接断开 | 传输层断连 | 全局连接状态栏 |
| 流式中断 | `chat.abort` | 消息气泡显示"已中断"标记 |

**工具错误面板**：

```
┌─ ToolCard ─────────────────────────────────────────┐
│ 📊 数据库查询                                       │
│ ┌─────────────────────────────────────────────────┐ │
│ │ SELECT * FROM users WHERE age > 25              │ │
│ └─────────────────────────────────────────────────┘ │
│ ┌─ Error ─────────────────────────────────────────┐ │
│ │ ❌ 执行失败                                      │ │
│ │                                                 │ │
│ │ ERROR 1146: Table 'mydb.users' doesn't exist    │ │
│ │                                                 │ │
│ │ [复制错误] [重试]                                 │ │
│ └─────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

**错误面板样式**：使用 `ThemeTokens.status.error` 作为边框和图标颜色，背景使用 `status.error` 的 10% 透明度变体。

**Agent 级错误横幅**：

```
┌─ Error Banner ─────────────────────────────────────────────────┐
│ ⚠️  [error_code] error_message                                 │
│                                                                │
│ 详细信息（可展开）:                                               │
│ ┌────────────────────────────────────────────────────────────┐ │
│ │ { "code": "RATE_LIMITED", "message": "...", "retry_after": │ │
│ │   30, "details": {...} }                                   │ │
│ └────────────────────────────────────────────────────────────┘ │
│                                                                │
│ [关闭] [重试]                                                   │
└────────────────────────────────────────────────────────────────┘
```

**错误渲染接口**：

```typescript
interface ErrorRenderConfig {
  source: "tool_error" | "agent_error" | "timeout" | "connection" | "abort"
  code?: string
  message: string
  details?: unknown
  retryable: boolean
  retry_action?: () => void
}

interface ErrorRenderer {
  renderToolError(config: ErrorRenderConfig, toolCard: ToolCard): void
  renderAgentError(config: ErrorRenderConfig): void
  renderConnectionError(state: ConnectionState): void
  renderAbortMark(messageId: string, preservePartial: boolean): void
}
```

**中断标记**：

当 `chat.abort` 导致消息中断时，消息气泡 MUST 显示中断标记：

```
┌─ Message Bubble ──────────────────────────────────┐
│                                                   │
│  让我查一下数据库...                                │
│                                                   │
│  ┌─ 📊 数据库查询 ────────────────────────────┐   │
│  │  SELECT * FROM users WHERE age > 25       │   │
│  │  ⏳ 执行中...                              │   │
│  └───────────────────────────────────────────┘   │
│                                                   │
│  ── ✂️ 已中断 ──────────────────────────────────  │
│                                                   │
└───────────────────────────────────────────────────┘
```

当 `preserve_partial = true` 时，中断前已完成的内容保留显示；当 `preserve_partial = false` 时，整条消息标记为"已取消"并灰化。

---

## 5.10 Tool 中心化事件 *(v2.3 新增)*

> **版本**: v2.3 引入；v2.2 客户端可忽略以下三类 `delta.type`，不影响兼容性。
> **关联规范**: [`TOOL-MODEL.md`](./TOOL-MODEL.md) §14。

### 5.10.1 `tool_result_delta`

适用于 `ExecutionMode.output_stream=True` 的 Tool（如 Code Interpreter、长任务）。
通过 `message.delta` 携带，结构如下：

```json
{
  "type": "tool_result_delta",
  "content": {
    "tool_use_id": "tu_1",
    "chunk_index": 0,
    "delta_text": "hello ",
    "delta_data": null,
    "is_final": false,
    "truncated": false,
    "total_tokens": null
  }
}
```

- `chunk_index` 单调递增，从 0 开始。
- 最后一片必须 `is_final=true`；`truncated=true` 表示被 `max_output_tokens` 截断。

### 5.10.2 `tool_suspend`

收敛 v2.2 的 `hitl` / `surface.interact 请求` / `frontend_tool.request`。

```json
{
  "type": "tool_suspend",
  "content": {
    "suspension_id": "sus_1",
    "tool_use_id": "tu_1",
    "kind": "user_confirm | external_wait | agent_internal",
    "timeout_ms": 60000,
    "ui_template_id": "platform.confirm.modal",
    "metadata": { "question": "delete?", "risk": "high" }
  }
}
```

- `kind` 决定前端选择的等待 UI；`ui_template_id` 由 Tool 作者或平台提供（参见 `TOOL-MODEL.md` §10）。
- v2.2 的 `hitl` / `frontend_tool.request` 帧在 v2.3 期间继续发射以保持兼容；v3.0 起将仅发 `tool_suspend`。

### 5.10.3 `tool_resume`（双向）

- 入站方法：`tool.resume` —— 前端调用，结构：
  ```json
  {
    "method": "tool.resume",
    "params": {
      "suspension_id": "sus_1",
      "outcome": "resolved | rejected | cancelled",
      "result": { "approved": true },
      "error": "user declined"
    }
  }
  ```
  - `outcome=rejected` 必须携带 `error`。
- 出站确认 delta：`message.delta(type="tool_resume")` —— 服务端在 SuspensionManager resolve / reject / cancel 后回放，便于其他订阅者（审计、其他 tab）感知。

---

# §6 事件中间件

## 6.1 中间件协议

事件中间件是在 EventBroadcaster 与 EventManager 之间拦截、处理事件的可插拔组件。

```
Agent emit_*()
  │
  ▼
EventBroadcaster
  │
  ▼
┌─────────────────────────────────────────────┐
│           MiddlewareChain                    │
│  M1 → M2 → M3 → ... → Mn                   │
└─────────────────────────────────────────────┘
  │
  ▼
EventManager → 持久化 → 推送
```

每个中间件实现统一接口：

```typescript
interface EventMiddleware {
  name: string
  priority: number
  process(event: ZapEvent, next: (event: ZapEvent) => ZapEvent | null): ZapEvent | null
}
```

**行为语义**：

| 操作 | 代码 | 效果 |
|------|------|------|
| 透传 | `return next(event)` | 不修改，传给下一个 |
| 转换 | `event.data = ...; return next(event)` | 修改后传递 |
| 丢弃 | `return null` | 事件不再继续传播 |
| 生成 | `return next(newEvent)` | 替换为新事件 |

中间件按优先级（priority）升序执行，单个中间件异常 MUST NOT 导致整条链崩溃（异常自动透传原事件）。

```
process 调用链：

  LoggingMW(10)
    → ValidationMW(15)
      → FilterMW(30)
        → RehostMW(35)
          → RateLimitMW(60)
            → CompactionMW(65)
              → EncryptionMW(85)
                → MetricsMW(90)
                  → EventManager
```

## 6.2 中间件链

中间件通过 `use(middleware, priority)` 注册到链中。优先级数字越小越先执行。

**建议优先级区间**：

| 区间 | 用途 |
|------|------|
| 0–19 | 日志、校验（尽早发现问题） |
| 20–49 | 过滤、脱敏、URL 重写 |
| 50–79 | 限流、压缩 |
| 80–99 | 加密、指标 |

## 6.3 内置中间件注册表

### 后端中间件

| 优先级 | 中间件 | 职责 | 说明 |
|--------|--------|------|------|
| 10 | LoggingMiddleware | 事件审计日志 | `mode: "summary"` 只记 type+seq，`"full"` 记完整 data |
| 15 | ValidationMiddleware | 事件结构校验 | 校验必填字段、type 合法性、数据格式 |
| 30 | FilterMiddleware | 按 type / 权限过滤 | 白名单/黑名单模式，可按 user 角色配置 |
| 35 | RehostMiddleware | 外部 URL → 内部 URL 重写 | 扫描 Content 层 `tool_result` 中的外部链接并重写为代理 URL |
| 60 | RateLimitMiddleware | 高频事件限流 / 合并 | 窗口内同类事件合并（如连续 progress） |
| 65 | CompactionMiddleware | 事件压缩 | 历史加载和断线重连场景生成 snapshot |
| 85 | EncryptionMiddleware | 敏感字段加密 | 对指定路径的字段进行 AES 加密 |
| 90 | MetricsMiddleware | 事件计数、延迟指标 | 发送到 Prometheus / StatsD |

### MetricsMiddleware 标准指标列表

MetricsMiddleware SHOULD 暴露以下标准指标，供可观测性系统（Prometheus / Grafana / StatsD）采集：

**计数器（Counter）**：

| 指标名 | 标签 | 说明 |
|--------|------|------|
| `zap_events_total` | `type`, `session_id` | 事件总数，按类型分 |
| `zap_events_coalesced_total` | `type` | 被合并的事件总数 |
| `zap_events_dropped_total` | `type`, `reason` | 被丢弃的事件总数（背压/限流） |
| `zap_req_total` | `method`, `status` | 三帧请求总数，status = `ok` / `error` / `cancelled` |
| `zap_reconnections_total` | `transport`, `reason` | 重连次数 |
| `zap_hitl_total` | `kind`, `outcome` | HITL 总数，outcome = `resolved` / `timeout` / `cancelled` |
| `zap_circuit_breaker_trips_total` | `domain` | 熔断器触发次数 |

**直方图（Histogram）**：

| 指标名 | 标签 | 说明 |
|--------|------|------|
| `zap_event_latency_ms` | `type` | 事件从 Agent emit 到 Client 接收的端到端延迟 |
| `zap_req_duration_ms` | `method` | 三帧请求的往返时间 |
| `zap_hitl_wait_duration_ms` | `kind` | HITL 从推送到 resolve 的等待时间 |
| `zap_coalescing_window_actual_ms` | `type` | 实际合并窗口时长 |

**仪表（Gauge）**：

| 指标名 | 标签 | 说明 |
|--------|------|------|
| `zap_active_sessions` | — | 当前活跃 Session 数 |
| `zap_active_connections` | `transport` | 当前活跃连接数（按传输类型分） |
| `zap_event_buffer_size` | `session_id` | 事件缓冲区当前大小 |
| `zap_backpressure_queue_depth` | — | 背压队列深度 |
| `zap_pending_hitl_count` | — | 当前 pending 的 HITL 数量 |

**分布式追踪**：

每个事件信封 MAY 携带 `_trace` 元数据用于分布式追踪：

```typescript
interface ZapEventTrace {
  trace_id?: string       // W3C Trace Context trace-id
  span_id?: string        // 当前事件的 span-id
  parent_span_id?: string // 父 span（如 Agent emit 的 span）
}
```

`_trace` 字段是可选的，Client MUST NOT 依赖其存在。

**RehostMiddleware 详解**：

RehostMiddleware 将外部 URL 重写为内部代理 URL，解决跨域和访问控制问题。

RehostMiddleware 扫描 `content_start(type:"tool_result")` 事件中的 URL 字段并重写：

```
原始事件:
  content_start(type:"tool_result", content:[
    {type:"text", text:"{\"url\":\"https://external.com/report.pdf\"}"}
  ])

                ↓ RehostMiddleware

重写后:
  content_start(type:"tool_result", content:[
    {type:"text", text:"{\"url\":\"/api/v1/proxy/abc123\"}"}
  ])
```

**CompactionMiddleware 详解**：

CompactionMiddleware 在特定场景下将增量事件序列压缩为快照事件。

```
原始序列 (5 个事件):
  content_start(index:0, type:"text")
  content_delta(index:0, text:"你")
  content_delta(index:0, text:"好")
  content_delta(index:0, text:"世界")
  content_stop(index:0)

                ↓ CompactionMiddleware

压缩后 (1 个事件):
  content_snapshot(index:0, content_block:{type:"text", text:"你好世界"})
```

### 前端中间件

| 中间件 | 职责 | 说明 |
|--------|------|------|
| DedupMiddleware | 基于 event_uuid 去重 | 维护 Set，超过 1000 条自动清理最旧的 |
| StoreMiddleware | 按 delta.type 路由到 Store | `state`→agentStateStore, `ui`→agentUIStore, `hitl`→hitlStore |
| AutoLifecycleMiddleware | 管理 start/stop 状态 | 追踪各层生命周期，异常检测（stop 无对应 start） |

## 6.4 中间件配置

中间件链按 Session 维度配置。不同 Session 可以有不同的中间件链。

**配置结构**：

```typescript
interface MiddlewareChainConfig {
  middlewares: Array<{
    name: string
    enabled: boolean
    priority?: number       // 覆盖默认优先级
    config?: Record<string, unknown>
  }>
}
```

**示例配置**：

```json
{
  "middlewares": [
    {"name": "LoggingMiddleware", "enabled": true, "config": {"mode": "summary", "sample_rate": 0.1}},
    {"name": "ValidationMiddleware", "enabled": true},
    {"name": "FilterMiddleware", "enabled": true, "config": {"blacklist": ["progress"]}},
    {"name": "RehostMiddleware", "enabled": true, "config": {"proxy_base": "/api/v1/proxy"}},
    {"name": "RateLimitMiddleware", "enabled": true, "config": {"window_ms": 100, "max_events": 10}},
    {"name": "CompactionMiddleware", "enabled": false},
    {"name": "EncryptionMiddleware", "enabled": false},
    {"name": "MetricsMiddleware", "enabled": true, "config": {"backend": "prometheus"}}
  ]
}
```

## 6.5 自定义中间件

开发者可以编写自定义中间件并注册到链中。

**编写规范**：

| 规则 | 约束 |
|------|------|
| 实现 EventMiddleware 接口 | MUST 实现 `process(event, next)` |
| 声明 name 和 priority | name MUST 唯一 |
| 异常安全 | process MUST NOT 抛出未捕获异常 |
| 性能 | 单个中间件处理 SHOULD < 1ms |
| 无状态优先 | SHOULD 避免跨事件状态，需要时用外部存储 |

**示例：敏感词过滤中间件**：

```typescript
interface SensitiveFilterMiddleware extends EventMiddleware {
  name: "SensitiveFilterMiddleware"
  priority: 40
  process(event: ZapEvent, next: NextFn): ZapEvent | null {
    // 对 content_delta 的 text 进行脱敏
    // 对 message_delta 的 content 进行脱敏
    // 其他事件透传
  }
}
```

## 6.6 事件压缩

事件压缩将冗长的增量序列合并为快照，用于历史加载和断线重连降级。

**压缩规则**：

| 源事件序列 | 压缩为 |
|-----------|--------|
| `content_start` → `content_delta` × N → `content_stop` | `content_snapshot`（完整内容） |
| `message_delta(state, op:patch)` × N | `message_delta(state, op:snapshot)` |
| `conversation_delta(plan)` × N | 最终 `conversation_delta(plan)` |
| `message_delta(progress)` × N | 最后一条 progress |

**触发时机**：

| 场景 | 说明 |
|------|------|
| 打开历史对话 | 加载压缩后事件 |
| 缓冲 TTL 到期前 | 定期压缩，节省存储 |
| 断线重连时 seq 已过期 | 返回压缩快照 |

---

# §7 多 Agent 协作与外部协议集成

ZAP 原生支持多 Agent 协作，并通过标准扩展点集成外部 UI 协议。

```
┌──────────────────────────────────────────────────────────────┐
│                       协作与集成层级                           │
│                                                              │
│  多 Agent 协作层                                              │
│  ┌──────────────────────────────────────────────────┐       │
│  │ Coordinator/Worker  → 层级式委派、task-notification │       │
│  │ Agent Teams/Swarm   → 对等式协作、邮箱消息路由      │       │
│  │   → 子 Session / agent_message delta / 通知事件    │       │
│  └──────────────────────────────────────────────────┘       │
│                                                              │
│  交互型 Delta（Agent 直接 emit）                              │
│  ┌──────────────────────────────────────────────────┐       │
│  │ A2UI                                              │       │
│  │   → Agent emit_a2ui()                             │       │
│  │   → message_delta(type:"a2ui")                    │       │
│  └──────────────────────────────────────────────────┘       │
│                                                              │
│  客户端渲染层                                                 │
│  ┌──────────────────────────────────────────────────┐       │
│  │ ToolRenderer       → 按 tool_use.name 渲染结果     │       │
│  │ A2UIRenderer       → 渲染声明式 JSON               │       │
│  │ MCPAppIframeHost   → sandboxed iframe + postMsg   │       │
│  └──────────────────────────────────────────────────┘       │
└──────────────────────────────────────────────────────────────┘
```

## 7.1 多 Agent 协作模型

ZAP 支持两种 Agent 协作拓扑：**层级式（Coordinator/Worker）** 和 **对等式（Agent Teams）**。两种模式共享 ZAP 的事件管线和传输层，但在拓扑、通信方式和生命周期管理上有根本区别。

### 7.1.1 总览

```
模式一：层级式（Coordinator/Worker）         模式二：对等式（Agent Teams）

     ┌──────────────┐                         ┌────────────┐
     │  Coordinator  │                         │   Leader    │
     │  (主 Agent)   │                         │  (发起者)   │
     └──┬─────┬─────┘                         └──┬─────┬───┘
        │     │     │                             │     │
        ▼     ▼     ▼                             ▼     ▼
    ┌───┐ ┌───┐ ┌───┐                       ┌────┐  ┌────┐
    │ W1│ │ W2│ │ W3│                       │ T1 │  │ T2 │
    └───┘ └───┘ └───┘                       └──┬─┘  └─┬──┘
    (完成后销毁)                                 │      │
                                                └──────┘
                                             邮箱 / SendMessage

    星型拓扑                                   网状拓扑
    单向上报                                   双向通信
    短生命周期                                  长生命周期
```

### 7.1.2 层级式协作（Coordinator/Worker）

Coordinator 是主 Agent，通过 `agent` 工具委派子代理执行具体任务。子代理（Worker）完成后，结果作为 `tool_result` 返回 Coordinator——对主 Agent 的 LLM 来说，子代理只是一个普通工具。

#### Session 与作用域隔离

子代理**共享父级的 `session_id`**，通过 `agent_id` 实现 Broadcaster 内部的作用域隔离。所有内部字典（accumulator、message_id、pending_metadata、tool_ids）使用复合 key `{session_id}::{agent_id}` 避免与父级冲突：

```
Coordinator Session (sess_abc)
  ├── 主 Agent scope key: "sess_abc"
  ├── 子代理 "researcher" scope key: "sess_abc::researcher"
  └── 子代理 "coder" scope key: "sess_abc::coder"
```

子代理的 `agent_id` 由 `SubagentRunner` 生成：若父级有 `agent_id`，子代理为 `{parent_agent_id}::{definition.name}`；否则直接为 `definition.name`。

#### 执行模型

```
Coordinator                    SubagentRunner                   Worker (AgentLoop)
  │                                │                                │
  │── tool_use: agent ────────────►│                                │
  │   {agent_type: "researcher",   │                                │
  │    prompt: "搜索相关论文"}      │                                │
  │                                │── emit subagent_start ────────►│ (事件总线)
  │                                │                                │
  │                                │── broadcaster.start_message ───│
  │                                │   (agent_id="researcher")      │
  │                                │                                │
  │                                │   ┌─ AgentLoop.run() ─────────►│
  │                                │   │  content_start/delta/stop   │
  │                                │   │  (agent_id="researcher")   │
  │                                │   │  → 事件总线实时推送          │
  │                                │   │  → 前端可分流显示卡片       │
  │                                │   └─────────────────────────────│
  │                                │                                │
  │                                │── emit subagent_stop ─────────►│ (事件总线)
  │                                │                                │
  │◄── tool_result ────────────────│                                │
  │    {result: "找到 3 篇论文..."} │                                │
  │                                │                                │
  │   (Coordinator 继续推理)        │                                │
```

**关键行为**：

- 子代理的 `content_start` / `content_delta` / `content_stop` 事件携带 `agent_id` 字段，通过事件总线实时推送
- 前端根据 `agent_id` 将子代理输出渲染为独立卡片（展开可查看流式响应）
- 对 Coordinator 的 LLM，子代理最终结果作为 `tool_result` 的文本摘要返回（前 500 字符）
- 子代理的完整运行记录（content blocks）通过 Broadcaster 的 `_scope_key` 隔离持久化

#### 生命周期事件

子代理启动和停止时，`SubagentRunner` 发送两个事件到统一事件总线：

```typescript
// agent.subagent_start
interface SubagentStartData {
  agent_id: string              // 子代理 ID（如 "researcher"）
  agent_name: string            // 人类可读名称
  parent_agent_id: string       // 父级 agent_id（空字符串表示顶层 Agent）
  permission_mode: string       // "inherit" | "restricted"
  tools?: string[]              // 工具白名单（可选）
}

// agent.subagent_stop
interface SubagentStopData {
  agent_id: string
  agent_name: string
  result_summary: string        // 结果摘要（前 500 字符）
  usage: {                      // Token 用量统计
    input_tokens?: number
    output_tokens?: number
    total_cost?: number
  }
}
```

#### 工具继承与白名单

子代理的工具列表由 `AgentDefinition.tools` 控制：

| `tools` 值 | 行为 |
|------------|------|
| `None`（默认） | 继承父级工具列表，自动剥离 `agent` 工具（防止嵌套） |
| `[]`（空列表） | 无工具，纯文本推理 |
| `["web_search", "read_file"]` | 白名单过滤，只保留指定工具 |

其他约束由 `AgentDefinition` 定义：

| 字段 | 说明 |
|------|------|
| `max_turns` | 子代理最大对话轮次（默认 10），防止失控 |
| `cost_tier` | `"standard"` 继承父级模型，`"light"` 尝试同品牌轻量模型 |
| `permission_mode` | `"inherit"` 继承父级权限，`"restricted"` 受限模式 |

### 7.1.3 对等式协作（Agent Teams）

Agent Teams 是一组**命名的对等 Agent**，它们属于同一个团队（team），可以互相发送消息。

#### 团队拓扑

```
Team "backend-dev"
  ┌──────────────────────────────────────────────┐
  │                                              │
  │  ┌──────────┐   邮箱    ┌──────────┐        │
  │  │ "leader"  │◄────────►│"reviewer" │        │
  │  │ (发起者)   │          └──────────┘        │
  │  └─────┬────┘                                │
  │        │ 邮箱                                 │
  │        ▼                                     │
  │  ┌──────────┐                                │
  │  │ "coder"   │                               │
  │  └──────────┘                                │
  │                                              │
  └──────────────────────────────────────────────┘
```

每个 Teammate 运行在独立 Session 中，通过 `team_id` 关联：

```json
{
  "type": "session_start",
  "data": {
    "session_id": "sess_coder",
    "agent_role": "teammate",
    "agent_name": "coder",
    "team_id": "team_backend_dev",
    "capabilities": { ... }
  }
}
```

#### 消息路由

Teammate 之间通过 `agent_message` delta 通信。消息路由由 Server 按 `team_id` + `to` 字段分发：

```typescript
interface AgentMessageContent {
  from: string             // 发送者 agent_name
  to: string | "*"         // 接收者 agent_name，"*" 为广播
  team_id: string          // 团队标识
  message_type: "text" | "task" | "result" | "status"
  content: unknown         // 消息体
  reply_to?: string        // 回复的消息 ID（可选）
}
```

**发送消息**（通过三帧 req）：

```json
{
  "type": "req",
  "id": "msg_01",
  "method": "agent.send_message",
  "params": {
    "team_id": "team_backend_dev",
    "to": "reviewer",
    "message_type": "task",
    "content": { "task": "Review this PR", "pr_url": "..." }
  }
}
```

**接收消息**（通过事件推送）：

```json
{
  "type": "message_delta",
  "data": {
    "delta": {
      "type": "agent_message",
      "content": {
        "from": "leader",
        "to": "coder",
        "team_id": "team_backend_dev",
        "message_type": "task",
        "content": { "task": "Implement the auth module" }
      }
    }
  }
}
```

#### 团队管理

| 操作 | req method | 说明 |
|------|-----------|------|
| 创建团队 | `team.create` | 创建团队，指定成员配置 |
| 添加成员 | `team.spawn_member` | 在团队中启动新 Teammate |
| 列出成员 | `team.list_members` | 查询团队当前在线成员 |
| 发送消息 | `agent.send_message` | 向指定 Teammate 或广播发送消息 |
| 移除成员 | `team.remove_member` | 停止某个 Teammate 的 Session |

#### 消息投递保证

| 保证级别 | 说明 |
|----------|------|
| **至少一次投递** | 消息持久化到邮箱后即确认，Teammate 上线后 MUST 消费所有未读消息 |
| **顺序保证** | 同一 `from → to` 方向的消息 MUST 按发送顺序投递 |
| **离线缓冲** | Teammate 离线时消息缓存在邮箱中，上线后补发 |
| **广播语义** | `to: "*"` 发送给团队中**除自己外**的所有在线成员 |

### 7.1.4 两种模式对比

| 维度 | Coordinator/Worker | Agent Teams |
|------|-------------------|-------------|
| 拓扑 | **星型**（Coordinator → Workers） | **网状**（Teammates 互相通信） |
| 通信方向 | **单向**（Worker 结果作为 tool_result 返回） | **双向**（任意 Teammate 互发） |
| 生命周期 | Worker **完成即销毁**（AgentLoop 结束即释放） | Teammate **持续存活**直到被移除 |
| 作用域隔离 | 共享 `session_id`，通过 `agent_id` 做 Broadcaster scope 隔离 | `team_id` 平级关系 |
| 生命周期事件 | `agent.subagent_start` / `agent.subagent_stop` | `agent_message` delta |
| 结果传递 | Worker 结果作为 `tool_result` 返回父级 LLM（前 500 字符摘要） | 邮箱 / `SendMessage` |
| 适用场景 | 任务可明确分解、子任务独立 | 需要协商、迭代、角色分工 |
| 权限模型 | Worker 工具白名单受限，默认剥离 `agent` 工具防嵌套 | Teammate 共享 Leader 权限桥 |

### 7.1.5 前端展示

Client 根据 `agent_id` 字段将子代理输出分流渲染为独立卡片。

#### 层级式子代理（Coordinator/Worker）

| 事件 | agent_id | 前端 UI |
|------|----------|---------|
| `agent.subagent_start` | `"researcher"` | 创建子代理卡片（折叠态，显示 agent_name） |
| `content_start/delta/stop` | `"researcher"` | 展开卡片后可查看实时流式输出 |
| `agent.subagent_stop` | `"researcher"` | 卡片标记为完成态，显示 result_summary 和 usage |
| `content_start/delta/stop` | `None`（空） | 父级 Agent 的正常输出流 |

前端根据 `agent_id` 字段判断事件归属：
- `agent_id` 为空/不存在 → 父级 Agent 输出，直接渲染到主对话流
- `agent_id` 非空 → 子代理输出，路由到对应卡片内渲染

#### 对等式协作（Agent Teams）

| 事件 | 前端 UI |
|------|---------|
| `agent.team_formed` | 团队面板中显示新成员上线 |
| `agent_message` | 团队消息流中显示消息气泡（区分发送者） |

## 7.2 A2UI（Agent-to-UI）

Google 的声明式 UI 协议。是 ZAP 生成式 UI 的**唯一格式**。

### 接入架构

```
Agent 调用 emit_a2ui(messages=[...])
        │
        ▼
EventBroadcaster
        │
        ▼
message_delta(type:"a2ui", delta:{
  surface_id: "unique-id",
  catalog_id: "...",
  messages: [...]
})
        │
        ▼
EventManager → Client → A2UIRenderer
```

### 与 ZAP 的集成点

| ZAP 层 | A2UI 角色 | 说明 |
|--------|----------|------|
| Agent Runtime | emit_a2ui() | Agent 直接发射 `a2ui` delta，不经过工具结果翻译 |
| EventMiddleware | 透传 | 无特殊处理 |
| Client Store | agentUIStore | 按 id 管理 UI 实例状态 |
| Client Renderer | A2UIRenderer | 组件树渲染 + action 回传 |

## 7.3 MCP Apps

Model Context Protocol 的 UI 扩展，通过 `ui://` URI 嵌入交互式 UI。与 A2UI 不同，MCP Apps 是**独立沙盒应用**，不是声明式组件。

### 接入架构

```
LLM → tool_result (含 _meta.ui)
        │
        ▼
Content 层原样推送
  content_start(type:"tool_result")
  content_stop
        │
        ▼
前端检测 tool_result._meta.ui
        │
        ├── resourceUri: "ui://mcp-server/app-name"
        ├── title: "天气查看器"
        └── permissions: ["geolocation"]
              │
              ▼
Client → sandboxed iframe
         ↕ postMessage (白名单权限)
```

### A2UI vs MCP Apps 对比

| 维度 | A2UI (`ui` delta) | MCP Apps (tool_result 前端检测) |
|------|-------------------|-------------------------------|
| 渲染方式 | Client 组件树 | sandboxed iframe |
| 安全模型 | Client 受控 | iframe 沙箱隔离 |
| 通信方式 | 三帧协议 `surface.interact` | `postMessage` |
| 更新粒度 | A2UI v0.9 messages 增量 | 整个 App 刷新 |
| 协议路径 | `message_delta(type:"a2ui")` — Agent 主动 emit | Content 层 `tool_result` — 前端按 `_meta.ui` 检测 |
| 适用场景 | 轻量交互（表单、卡片） | 复杂独立应用（地图、编辑器） |
| LLM 生成 | LLM 直接生成 JSON | LLM 只选择 App URI |

**ZAP 与外部协议对比**：

| 特性 | ZAP | AG-UI | A2UI | MCP Apps |
|------|-----|-------|------|----------|
| 传输 | 五层事件 + 三帧 | HTTP/WS 扁平事件 | 依赖传输层 | 依赖 MCP |
| 多 Agent | Coordinator/Worker + Agent Teams | 无原生 | 无 | 无 |
| 状态管理 | snapshot + patch | snapshot + delta | updateDataModel | App 内部 |
| UI 生成 | A2UI 格式 | 自定义组件 | 声明式组件 | iframe 沙盒 |
| 人在环中 | hitl + hitl.resolve | Run 终止 + resume | action 回调 | 无 |
| 断线重连 | seq + after_seq | 无原生 | 无 | 无 |
| 事件去重 | event_uuid | 无 | 无 | 无 |
| 中间件 | EventMiddleware 链 | agent.use() | 无 | 无 |

---

# §8 可靠性

## 8.1 断线重连

Client 用最后收到的 seq 号重连，Server 从缓冲中补发缺失事件。

```
Client                              Server                    Buffer
  │                                    │                        │
  │ (连接断开，最后 seq=42)             │                        │
  │                                    │                        │
  │ (重新连接)                          │                        │
  │─── GET /api/v1/chat/sess_abc       │                        │
  │    ?after_seq=42 ─────────────────►│                        │
  │                                    │─── get_events(>42) ──►│
  │                                    │◄── [seq43,44,45,...] ──│
  │◄── event seq=43 ──────────────────│                        │
  │◄── event seq=44 ──────────────────│                        │
  │◄── event seq=45 ──────────────────│                        │
  │◄── (实时事件流继续) ───────────────│                        │
```

**三种重连结果**：

| Client 请求 | 缓冲状态 | Server 行为 |
|------------|---------|------------|
| `after_seq=42` | seq 43-50 在缓冲中 | 补发 43-50，继续实时流 |
| `after_seq=42` | seq 43-45 在缓冲，46+ 已过期 | 补发 43-45 + content_snapshot (46+) |
| `after_seq=42` | 全部已过期 | 返回 messages_snapshot + 实时流 |

**messages_snapshot 降级**：

当缓冲完全过期时，Server 通过 `conversation_delta` 推送 messages_snapshot，让 Client 一次性恢复到当前状态：

```json
{
  "seq": 1,
  "type": "conversation_delta",
  "data": {
    "delta": {
      "messages_snapshot": [
        {"role": "user", "content": "帮我查询用户数据"},
        {"role": "assistant", "content": "好的，我来查询...",
         "ui": [{"id": "chart-01", "format": "a2ui", ...}],
         "state": {"cart": [], "total": 0}}
      ]
    }
  }
}
```

### 缓冲管理

```
Event Buffer (Redis Sorted Set)
─────────────────────────────────
Key:    buffer:{session_id}
Score:  seq
Member: JSON(event)
TTL:    24h (可配置)

写入: EventManager 每次分配 seq 后 ZADD
读取: 重连时 ZRANGEBYSCORE(after_seq, +inf)
清理: Redis TTL 自动过期 + 定期压缩
```

## 8.2 事件去重

| 层级 | 机制 | 说明 |
|------|------|------|
| Server | `event_uuid` 全局唯一 | UUID v4 |
| Client | DedupMiddleware | 维护 event_uuid Set，重复丢弃 |
| 幂等性 | `req.id` | 相同 req.id 的请求，Dispatcher SHOULD 幂等处理 |

## 8.3 错误处理

**错误事件结构**：

```typescript
interface ErrorData {
  error: {
    type: "network_error" | "timeout_error" | "overloaded_error"
        | "internal_error" | "validation_error"
    message: string
    code?: string       // UPPER_SNAKE_CASE
    retryable?: boolean
  }
}
```

**三帧错误响应**：

```json
{
  "type": "res",
  "id": "req_001",
  "ok": false,
  "error": {
    "code": "INVALID_HITL_ID",
    "message": "hitl_id 不存在或已过期"
  }
}
```

所有错误码 MUST 使用 `UPPER_SNAKE_CASE` 格式。

## 8.4 Session 分支与时间旅行

ZAP 通过 `session_start.parent_session_id` 支持会话分支：

```
Session A (正常对话)
  ├── conv_01: "帮我分析数据"
  ├── conv_02: "用方案 A 实现"
  │
  └──► 用户想尝试方案 B
       │
       ▼
Session B (分支, parent_session_id = "sess_A")
  ├── conv_01 (复制自 A)
  └── conv_02: "用方案 B 实现"
```

### 分支规则

| 规则 | 约束 |
|------|------|
| 独立 seq | 分支 Session 的 seq 从 1 开始，与 parent 无关 |
| 状态快照 | 分支时**深拷贝** parent 的 session scope 状态（见下文） |
| 独立演化 | 分支后两个 Session 完全独立，互不影响 |
| 缓冲独立 | 各有自己的 Event Buffer |
| 单级分支 | 分支图为**树**结构（非 DAG）——每个 Session 最多有一个 parent |

### 分支图拓扑

分支图是**有根树**，不允许多级合并（即不形成 DAG）：

```
         sess_A (root)
        /       \
    sess_B     sess_C
      |
    sess_D

允许：sess_D 从 sess_B 分支（多级分支，形成树）
禁止：sess_D 同时从 sess_B 和 sess_C 合并（DAG）
```

| 约束 | 说明 |
|------|------|
| 多级分支 | 允许——Session D 可以从 Session B 分支，Session B 本身是 Session A 的分支 |
| 合并 | 不允许——两个分支 Session 不能合并回同一个 Session |
| 最大深度 | 实现 SHOULD 限制分支深度（推荐 ≤ 10 级），防止资源耗尽 |

### Shared State 分支语义

分支时 parent 的 Shared State 采用**深拷贝**（deep copy）语义：

```
分支时刻 T：
  Session A: state = { theme: "dark", filters: { status: "active" } }
                          │
                          │ branch
                          ▼
  Session B: state = { theme: "dark", filters: { status: "active" } }  ← 深拷贝

T+1: Session A 修改 state.filters.status = "all"
     Session B 的 state.filters.status 仍为 "active"  ← 完全独立
```

| 设计决策 | 说明 |
|----------|------|
| 深拷贝 vs CoW | 采用深拷贝。CoW（Copy-on-Write）虽然节省内存，但增加了并发修改时的复杂度，且 Shared State 通常较小（< 100KB） |
| 分支点状态 | 分支时复制的是**分支请求到达时**的 state 快照，不保证与 parent 最后一个 state.patch 事件精确对齐 |

### 分支请求

```typescript
// Client 发起分支
{
  type: "req",
  id: "branch_01",
  method: "session.branch",
  params: {
    parent_session_id: string      // 要分支的 Session
    branch_point?: {               // 可选：从历史某个点分支（时间旅行）
      conversation_id?: string     // 分支到哪个 Conversation 之后
      message_id?: string          // 分支到哪条消息之后
    }
  }
}

// Server 响应
{
  type: "res",
  id: "branch_01",
  ok: true,
  data: {
    session_id: string             // 新 Session ID
    copied_conversations: number   // 复制的 Conversation 数量
  }
}
```

### 用途

| 场景 | 说明 |
|------|------|
| A/B 测试 | 同一上下文尝试不同方案 |
| 回滚 | 从历史某个点重新开始（时间旅行） |
| 多人协作 | 各自从共同基线分支 |

## 8.4.1 多客户端语义

同一 `session_id` 可能被多个 Client 连接同时访问（多标签页、多设备）。ZAP 采用**单控制通道**语义：

```
Tab A ──WS──┐
            ├──► Session "sess_01"
Tab B ──WS──┘
```

### 连接策略

| 策略 | 行为 | 适用场景 |
|------|------|---------|
| **单控制通道**（默认） | 同一 Session 同时只允许一个控制连接（可发送 req）。新连接建立时，旧连接降级为**只读观察者**（只接收事件，req 返回 `SESSION_LOCKED`） | 避免并发控制冲突 |
| **Last-writer-wins**（可选） | 多个连接均可发送 req，冲突时最后到达的 req 生效 | 简单场景，容忍偶发冲突 |

Server 通过 `session_start.capabilities.features.multi_client_policy` 通告采用的策略：

```json
{
  "features": {
    "multi_client_policy": "single_control"
  }
}
```

### 冲突处理

| 冲突类型 | 单控制通道 | Last-writer-wins |
|----------|-----------|-----------------|
| 两个 Client 同时发送 `state.patch` | 观察者的 req 被拒绝 | 后到达的 patch 覆盖先到达的 |
| 两个 Client 同时 resolve 同一 HITL | 观察者的 req 被拒绝 | 先到达的 resolve 生效，后到达的返回 `HITL_ALREADY_RESOLVED` |
| 一个 Client 发送 `chat.abort`，另一个发送 `chat.send` | 观察者的 req 被拒绝 | 按到达顺序执行（可能 abort 后立即 send） |

### 观察者模式

降级为观察者的连接：

- MUST 继续接收所有事件推送（事件流不中断）
- req 返回 `{ok: false, error: {code: "SESSION_LOCKED", message: "Another client holds the control channel"}}`
- Client SHOULD 在 UI 上显示"其他设备正在控制此会话"提示
- 当控制连接断开后，观察者 MAY 通过发送 `req{method: "session.claim_control"}` 升级为控制连接

## 8.5 超时与清理

| 资源 | 超时策略 | 清理行为 |
|------|---------|---------|
| Session | 无活动 30min | 推送 session_end，释放缓冲 |
| HITL (volatile) | `timeout_ms` 到期 | 设为 timeout 错误 |
| HITL (durable) | 存储 Key TTL | 可跨进程恢复直到 TTL |
| Frontend Tool | `timeout_ms` 到期 | 设为 timeout 错误 |
| WebSocket | ping/pong 30s 无响应 | 关闭连接，触发重连 |
| Event Buffer | TTL 24h | 自动过期，降级为 snapshot |

## 8.6 背压控制

### 问题

当 Agent 产生事件的速度超过 Client 消费速度时（如高频 content_delta、大量工具结果），未受控的推送会导致：

- Client 内存溢出（事件堆积）
- 网络缓冲区膨胀（TCP 窗口填满）
- Event Buffer 写入延迟增加

### 三层背压机制

```
Agent Runtime                    EventManager                    Transport
      │                               │                              │
      │  emit_*()                      │                              │
      │──────────────────────────────►│                              │
      │                               │  ┌───── L1 生产端限流 ────┐   │
      │                               │  │ await 直到队列低于阈值  │   │
      │  ◄── await (阻塞 Agent) ──────│  └────────────────────────┘   │
      │                               │                              │
      │                               │  enqueue(event)              │
      │                               │──────────────────────────────►│
      │                               │  ┌───── L2 写入端串行化 ──┐   │
      │                               │  │ 同时最多 1 个 write    │   │
      │                               │  │ 后续 write 排队        │   │
      │                               │  └────────────────────────┘   │
      │                               │                              │
      │                               │                    ┌── L3 传输端感知 ──┐
      │                               │                    │ WS: 检测 bufferedAmount │
      │                               │                    │ SSE: 检测 write 返回 false │
      │                               │                    │ 超阈值 → 暂停 drain │
      │                               │                    └──────────────────────┘
```

### L1：生产端限流（EventManager 入口）

EventManager 维护待发送事件队列。当队列深度超过 `HIGH_WATER_MARK`（默认 1000 条）时，`emit()` 变为 `await emit()`，阻塞 Agent 直到队列降到 `LOW_WATER_MARK`（默认 500 条）。

```typescript
interface BackpressureConfig {
  high_water_mark: number    // 默认 1000：暂停接受新事件
  low_water_mark: number     // 默认 500：恢复接受新事件
  max_queue_size: number     // 默认 5000：超过则丢弃低优先级事件
}
```

**丢弃策略**：当队列达到 `max_queue_size` 时，按优先级丢弃：

| 优先级 | 事件类型 | 丢弃策略 |
|--------|---------|---------|
| 1（不可丢弃） | `*_start`, `*_stop`, `hitl`, `error`, `done` | 永不丢弃 |
| 2（可合并） | `content_delta`, `progress`, `agent_status` | 合并同类 → 保留最新 |
| 3（可丢弃） | `ping` | 直接丢弃 |

### L2：写入端串行化

Transport 写入 MUST 串行化——同一时刻最多一个 write 操作在飞。后续 write 操作排队等待。

**理由**：并发写入可能导致事件乱序（不同事件的网络延迟不同）、资源竞争（如 HTTP POST 并发写同一 Firestore 文档）、和重试风暴。串行化 + 批量化（见 §8.7）是解决这些问题的标准方案。

```
write(event_A) ──► [in-flight]
write(event_B) ──► [queued]     ← 等 A 完成
write(event_C) ──► [queued]     ← 等 B 完成
                       │
                   A 完成 → 发送 B
                       │
                   B 完成 → 发送 C（此时可能已与 D 合并）
```

### L3：传输端感知

| 传输 | 背压信号 | 处理方式 |
|------|---------|---------|
| WebSocket | `ws.bufferedAmount > threshold` | 暂停 drain 循环，等 bufferedAmount 下降 |
| SSE | `response.write()` 返回 `false` | 等待 `drain` 事件后继续 |
| gRPC | 流控窗口满 | gRPC 原生流控自动暂停 |

### Client 端限流

Client 发送 req 也受限流保护：

| 限流维度 | 默认限制 | 超限行为 |
|----------|---------|---------|
| 每 Session 每秒 req 数 | 20 | 返回 `RATE_LIMITED` 错误 |
| 并发 pending req 数 | 10 | req 排队直到 slot 可用 |
| 单 req payload 大小 | 256KB | 返回 `INVALID_PARAMS` 错误 |

## 8.7 高频事件合并

### 问题

LLM 流式输出时，`content_delta(text)` 的产生频率可达每秒 50–100 个事件。每个事件独立推送会导致：

- 网络开销大（每个事件都有信封开销）
- HTTP POST 写入次数过多（Hybrid/SSE 传输场景）
- Client 渲染帧率被事件处理抢占

### 合并策略

EventManager 在推送前对高频事件进行**时间窗口合并**：

```
事件合并管线：

  Agent emit                    合并缓冲区                      推送
  ─────────                    ──────────                     ────
  content_delta("你")    ──►  buffer["你"]
                               │ (等待窗口)
  content_delta("好")    ──►  buffer["你好"]
                               │ (等待窗口)
  content_delta("世")    ──►  buffer["你好世"]
                               │
                               │ flush (窗口到期)
                               └──────────────────────────►  content_delta("你好世")
                                                              (1 个事件代替 3 个)
```

### 合并规则

| 事件类型 | 合并方式 | 窗口 | 产出 |
|----------|---------|------|------|
| `content_delta(text)` | 拼接 `text` 字段 | 100ms | 1 个 delta，text 为完整拼接结果 |
| `content_delta(thinking)` | 拼接 `text` 字段 | 100ms | 同上 |
| `content_delta(input_json)` | 拼接 `text` 字段 | 100ms | 同上 |
| `message_delta(progress)` | 保留最新 | 200ms | 最后一条 progress |
| `agent_status` | 保留最新 | 200ms | 最后一条 status |
| `ping` | 去重 | 5000ms | 最多 1 条 |
| 其他 | **不合并** | — | 立即推送 |

### 合并窗口配置

```typescript
interface CoalescingConfig {
  content_delta_window_ms: number    // 默认 100ms
  progress_window_ms: number         // 默认 200ms
  status_window_ms: number           // 默认 200ms
  ping_window_ms: number             // 默认 5000ms
}
```

**强制 flush 时机**：

| 触发条件 | 说明 |
|----------|------|
| 非合并事件到达 | 如 `content_stop` 到达时，先 flush 缓冲的 delta |
| 窗口到期 | 每个合并窗口到期时自动 flush |
| 连接关闭 | 优雅关闭前 flush 所有缓冲 |
| 缓冲大小超限 | 单条合并事件 > 64KB 时立即 flush |

### text 合并的自包含快照模式

对于 CCR（Cloud Code Runner）等场景，合并后的 `content_delta` 可以采用**自包含快照**而非增量拼接：

```
增量模式（默认）：
  合并后 content_delta.text = "你好世"          ← 本次窗口内的增量

快照模式（可选）：
  合并后 content_delta.text = "你好世界，让我来帮" ← 从 content_start 以来的全部文本
```

快照模式的优势：Client 中途连接也能看到完整文本，不需要从 content_start 重放。通过 `session_start.capabilities.features.coalescing_mode: "snapshot" | "incremental"` 协商。

### 对断线重连的影响

合并后的事件使用**新的 event_uuid**，但保留原始的 seq 号范围信息：

```json
{
  "event_uuid": "coalesced_001",
  "seq": 15,
  "type": "content_delta",
  "data": {
    "index": 0,
    "delta": {"type": "text", "text": "你好世"},
    "_coalesced": {
      "original_count": 3,
      "original_seq_range": [13, 14, 15]
    }
  }
}
```

`_coalesced` 字段是**可选元数据**，用于调试和监控。Client MUST NOT 依赖此字段。

## 8.8 幂等性与去重

§8.2 定义了基本的去重机制（`event_uuid` + `DedupMiddleware`）。本节补充生产环境中必须处理的深层去重场景。

### 问题

在 at-least-once 投递模型下，以下场景会产生重复：

| 场景 | 重复来源 | 频率 |
|------|---------|------|
| 断线重连补发 | Server 从 Buffer 重放已收到的事件 | 每次重连 |
| 传输层重试 | HTTP POST 超时后重试，但 Server 已处理 | 网络抖动时 |
| Bridge 回声 | 双向 Bridge 中，自己发出的事件被回传 | 持续 |
| tool_use/tool_result 重复 | LLM 重试或 Session 分支导致相同 ID 出现 | 偶发 |

### 8.8.1 三层去重机制

```
事件到达 Client
  │
  ▼
┌─────────────────────────────────────────────────────────────┐
│ L1: seq 去重（传输层）                                        │
│                                                             │
│ if event.seq <= lastReceivedSeq:                            │
│   drop()  // 已处理过的 seq                                  │
│                                                             │
│ 特点：O(1)，零内存开销，但只能检测严格有序的重复               │
│ 局限：合并事件（§8.7）的 seq 是末尾 seq，中间 seq 被跳过      │
└──────────────────────────────────┬──────────────────────────┘
                                   │ 通过
                                   ▼
┌─────────────────────────────────────────────────────────────┐
│ L2: UUID 去重（应用层）                                       │
│                                                             │
│ Ring Buffer: 固定大小的 event_uuid 集合（默认 2048 条）       │
│                                                             │
│ if recentUUIDs.has(event.event_uuid):                       │
│   drop()                                                    │
│ else:                                                       │
│   recentUUIDs.add(event.event_uuid)                         │
│   // 超出容量时淘汰最旧的 UUID                                │
│                                                             │
│ 特点：处理乱序重复、合并事件重复                               │
│ 局限：Ring Buffer 大小有限，极端情况下旧 UUID 被淘汰后重复     │
└──────────────────────────────────┬──────────────────────────┘
                                   │ 通过
                                   ▼
┌─────────────────────────────────────────────────────────────┐
│ L3: 语义去重（业务层）                                        │
│                                                             │
│ tool_use:   按 tool_use.id 去重                              │
│ tool_result: 按 tool_use_id 去重（同一工具调用只接受首个结果）  │
│ hitl:       按 hitl_id 去重（同一决策只处理首次 resolve）      │
│                                                             │
│ 特点：业务语义级别的幂等保证                                   │
└─────────────────────────────────────────────────────────────┘
```

### 8.8.2 UUID 环形缓冲区

```typescript
interface DeduplicationBuffer {
  readonly capacity: number        // 默认 2048

  has(uuid: string): boolean
  add(uuid: string): void

  readonly size: number
  readonly evictionCount: number   // 监控：被淘汰的 UUID 数量
}
```

**容量选择依据**：

| 场景 | 事件频率 | 推荐容量 | 覆盖时间 |
|------|---------|---------|---------|
| 普通对话 | ~10 events/s | 2048 | ~3 分钟 |
| 高频流式 | ~100 events/s（合并前） | 2048 | ~20 秒 |
| 高频流式 + 合并 | ~10 events/s（合并后） | 2048 | ~3 分钟 |

容量 SHOULD 覆盖至少一个完整的重连周期（`MAX_DELAY` = 30s），确保重连补发的事件能被去重。

### 8.8.3 Bridge 回声抑制

在双向 Bridge 架构中（如 CLI ↔ IDE），Client 发出的事件可能被 Server 回传。Client MUST 维护一个**出站 UUID 集合**，收到事件时检查是否是自己发出的：

```
Client                          Server
  │                                │
  │── req(uuid:A) ────────────────►│
  │   outboundUUIDs.add(A)         │
  │                                │
  │◄── event(uuid:A) ─────────────│  ← 回声
  │   outboundUUIDs.has(A) → drop  │
  │                                │
  │◄── event(uuid:B) ─────────────│  ← 正常事件
  │   outboundUUIDs.has(B) → pass  │
```

出站 UUID 集合同样使用 Ring Buffer，容量 SHOULD ≥ 256。

## 8.9 消息排序保证

### 问题

ZAP 事件在不同层级有不同的排序语义。混淆这些语义会导致 UI 闪烁、数据不一致、或重放错误。

### 三层排序模型

```
┌─────────────────────────────────────────────────────────────────┐
│ L1: 摄入排序（Ingress Order）                                     │
│                                                                 │
│ Agent emit → EventManager 接收的顺序                              │
│ 保证：单 Agent 严格有序（Agent 是单线程 emit）                      │
│ 不保证：多 Agent（coordinator 场景）的全局序                        │
│ 实现：EventManager 内部串行 append                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ L2: 持久化排序（Persistence Order）                               │
│                                                                 │
│ EventManager → Buffer 写入的顺序                                  │
│ 保证：seq 严格递增。合并事件（§8.7）保留末尾 seq，中间 seq 被跳过    │
│ Client MUST 容忍 seq 间隙，仅以单调递增为排序依据                    │
│ 实现：seq 由 EventManager 原子分配（单进程递增 / 分布式用 Redis INCR）│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ L3: 重放排序（Replay Order）                                      │
│                                                                 │
│ Buffer → Client 推送的顺序                                       │
│ 保证：按 seq 升序推送                                             │
│ 约束：合并事件的 seq = 原始 seq 范围的末尾值，中间 seq 被跳过       │
│ 实现：ZRANGEBYSCORE(after_seq, +inf) 按 score 排序                │
└─────────────────────────────────────────────────────────────────┘
```

### Flush Gate（实现建议）

> **注意**：Flush Gate 是 Client-Server 内部实现机制，非协议级帧类型。本节描述的是推荐的实现模式，而非规范性要求。

当 Client 需要发送一个**依赖于之前所有事件已被处理**的操作时（如 `chat.abort`），SHOULD 使用 Flush Gate 模式：

```
Client                          Server
  │                                │
  │── flush_request ──────────────►│
  │                                │── 等待所有 in-flight 事件写入完成
  │                                │── 等待所有 pending 合并窗口 flush
  │◄── flush_ack(last_seq:N) ─────│
  │                                │
  │── chat.abort ─────────────────►│  ← 现在安全：所有事件已持久化
```

**实现要求**：

| 要求 | 说明 |
|------|------|
| flush MUST 等待合并窗口 | 合并缓冲区中的事件 MUST 先 flush 再响应 |
| flush MUST 等待串行写入 | L2 写入端的 in-flight 操作 MUST 完成 |
| flush_ack 携带 last_seq | Client 可验证所有事件已到达 |
| flush 超时 | 默认 5000ms，超时后 Client 可选择强制 abort |

### 写入端排序保证

Transport 写入 MUST 保证以下不变量：

```
对于任意两个事件 A 和 B：
  if A.seq < B.seq:
    write(A) MUST happen-before write(B)
```

**实现方式**：串行写入（§8.6 L2）天然保证此不变量。并发写入 MUST NOT 被使用，即使底层传输支持。

**特殊情况——非 stream_event 写入**：

当 Transport 需要发送非 `stream_event` 类型的写入（如 heartbeat、state_update）时，MUST 先 flush 缓冲的 stream_event：

```
buffer: [stream_event_A, stream_event_B]
  │
  │ 需要发送 heartbeat
  │
  ▼
flush buffer → 发送 stream_event_A, stream_event_B
  │
  ▼
发送 heartbeat
```

## 8.10 优雅降级

### 问题

生产环境中，Client 和 Server 的版本不一定匹配，网络条件不一定理想，认证不一定完整。协议 MUST 定义在这些非理想条件下的行为。

### 8.10.1 版本兼容 Shim

当 Client 和 Server 的协议版本不一致时，通过 Shim 层进行兼容：

```
Client (v2.2)                    Server (v2.1)
  │                                │
  │── session_start               │
  │   capabilities.version:2.2    │
  │                                │
  │◄── session_start              │
  │    capabilities.version:2.1   │
  │                                │
  │   Client 检测到版本差异         │
  │   激活 CompatShim              │
  │                                │
  │   CompatShim:                  │
  │   - cancel 帧 → 降级为忽略    │
  │   - chat.abort → 降级为断开重连│
  │   - 背压信号 → 降级为 Client 端丢弃│
  │   - 合并事件 → 按普通事件处理  │
```

**Shim 规则**：

| 功能 | v2.2 Client + v2.1 Server | v2.1 Client + v2.2 Server |
|------|--------------------------|--------------------------|
| cancel 帧 | Client 不发送 cancel | Server 不发送 cancel |
| chat.abort | 降级为断开连接 | Server 不推送 abort 相关事件 |
| 背压信号 | Client 自行限流 | Server 不发送背压信号 |
| 事件合并 | Client 按普通事件处理 | Server 不合并 |
| 卡片模板 | 正常工作（纯前端功能） | 正常工作 |
| 主题令牌 | 正常工作（纯前端功能） | 正常工作 |

**关键原则**：新功能的缺失 MUST NOT 导致核心功能（对话、工具调用、HITL）不可用。

### 8.10.2 部分认证失败

认证失败不一定是全有或全无。以下场景需要部分降级：

| 场景 | 行为 |
|------|------|
| Token 过期但 Session 仍活跃 | 尝试刷新 Token（§3.5.4），刷新期间缓冲事件 |
| Token 刷新成功 | 用新 Token 重连，补发缓冲事件 |
| Token 刷新失败 | 迁移至 `closed`，展示重新登录提示 |
| Token 权限不足（部分功能不可用） | 降级运行，禁用需要高权限的功能（如工具调用），保留基本对话 |

### 8.10.3 传输降级路径

当首选传输不可用时，Client SHOULD 自动降级：

```
WebSocket (首选)
  │
  │ 连接失败（如被企业防火墙阻断）
  │
  ▼
SSE + HTTP POST (降级)
  │
  │ SSE 连接失败（如被代理阻断）
  │
  ▼
HTTP Long Polling (最终降级)
  │
  │ 功能限制：
  │ - 延迟增加（polling 间隔）
  │ - 无实时推送（模拟推送）
  │ - 合并窗口自动增大（减少请求数）
```

**降级检测**：Client 在首选传输连续失败 3 次后 SHOULD 尝试降级传输。降级后 SHOULD 定期（每 5 分钟）尝试恢复首选传输。

**降级通知**：传输降级时，Client SHOULD 通过 `onStateChange` 通知上层 UI，展示"连接质量降低"提示。

## 8.11 熔断器模式

### 问题

传输层重试和应用层重试是两个不同的关注点。混淆二者会导致**重试风暴**——传输层重试 × 应用层重试 = 指数级请求放大。

### 分层重试架构

```
┌─────────────────────────────────────────────────────────────────┐
│ 应用层熔断器                                                      │
│                                                                 │
│ 每个功能域独立的熔断器：                                            │
│ - MCP 连接熔断：连续 N 次错误后停止重连                             │
│ - 自动压缩熔断：压缩连续失败后暂停                                   │
│ - 工具调用熔断：同一工具连续失败后标记为不可用                        │
│                                                                 │
│ 状态：closed(正常) → open(熔断) → half-open(探测)                 │
└──────────────────────────────────┬──────────────────────────────┘
                                   │ 应用层决定是否发起请求
                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 传输层重试                                                       │
│                                                                 │
│ 仅处理网络级别的瞬态故障：                                          │
│ - TCP 连接超时 → 重试                                             │
│ - HTTP 5xx → 重试（尊重 Retry-After）                             │
│ - WebSocket 异常断开 → 重连（§3.5）                                │
│                                                                 │
│ 不处理：                                                          │
│ - HTTP 4xx（客户端错误，不重试）                                    │
│ - 业务逻辑错误（由应用层处理）                                      │
└─────────────────────────────────────────────────────────────────┘
```

### 熔断器接口

```typescript
interface CircuitBreaker {
  readonly state: "closed" | "open" | "half_open"
  readonly failureCount: number
  readonly lastFailureTime: number

  execute<T>(fn: () => Promise<T>): Promise<T>
  recordSuccess(): void
  recordFailure(error: Error): void
  reset(): void
}

interface CircuitBreakerConfig {
  failure_threshold: number        // 触发熔断的连续失败次数，默认 5
  reset_timeout_ms: number         // 熔断后多久进入 half_open，默认 60000
  half_open_max_attempts: number   // half_open 状态下的最大探测次数，默认 1
  excluded_errors?: string[]       // 不计入失败的错误码（如 CANCELLED）
}
```

### 熔断器状态机

```
              失败次数 < threshold
         ┌──────────────────────────┐
         │                          │
         ▼                          │
    ┌──────────┐  失败次数 >= threshold  ┌──────────┐
    │  closed   │──────────────────────►│   open   │
    │ (正常运行) │                        │ (拒绝请求) │
    └──────────┘                        └────┬─────┘
         ▲                                   │
         │ 探测成功                           │ reset_timeout 到期
         │                                   ▼
         │                             ┌───────────┐
         └─────────────────────────────│ half_open  │
                                       │ (允许1个请求)│
                探测失败 → 回到 open     └───────────┘
```

### 错误风暴抑制

当多个 Client 同时遭遇 Server 故障时，所有 Client 同时重试会形成**惊群效应**。缓解措施：

| 措施 | 说明 |
|------|------|
| 重试抖动 | 所有重试延迟 MUST 包含随机抖动（§3.5.2 已定义） |
| Retry-After 尊重 | Server 返回 `Retry-After` 头时，Client MUST 等待指定时间 |
| 指数退避上限 | 重试间隔 MUST 有上限（MAX_DELAY），防止无限等待 |
| 客户端限流 | Client 端限流（§8.6）防止单 Client 过度重试 |
| 熔断传播 | 当熔断器 open 时，Client SHOULD 展示降级 UI 而非持续重试 |

## 8.12 竞态条件防护

### 问题

异步协议中，多个并发操作可能产生竞态条件。以下是 ZAP 中已知的竞态场景和防护模式。

### 8.12.1 权限决策竞赛（Promise.race 模式）

当 HITL 决策可以来自多个源（用户点击、自动化 Hook、超时）时，采用竞赛模式：

```
HITL 请求到达
  │
  ├── 源 A: 用户 UI 点击 ──────────────────────────────┐
  │                                                    │
  ├── 源 B: 自动化 Hook（如 auto-approve 规则）──────────┤ Promise.race
  │                                                    │
  └── 源 C: 超时定时器 ────────────────────────────────┘
                                                       │
                                                       ▼
                                                   首个完成者获胜
                                                       │
                                                       ▼
                                                   取消其他源
                                                   (AbortController)
```

**实现要求**：

| 要求 | 说明 |
|------|------|
| 原子性 | 只有一个源的决策被采纳，其他源 MUST 被取消 |
| 取消安全 | 使用 `AbortController` / `AbortSignal` 取消落败者 |
| 无副作用 | 落败源的决策 MUST NOT 产生任何副作用 |
| 日志可追溯 | 记录获胜源和落败源，用于审计 |

### 8.12.2 Epoch 计数器

当 Session 经历重连、分支、或 Agent 重启时，旧的异步操作可能在新上下文中完成。Epoch 计数器确保旧操作不会污染新上下文：

```typescript
interface EpochGuard {
  readonly currentEpoch: number

  increment(): number
  guard<T>(epoch: number, fn: () => Promise<T>): Promise<T | null>
}

// 使用示例
const epoch = epochGuard.increment()  // 重连时递增

// 异步操作完成时检查 epoch
const result = await epochGuard.guard(epoch, async () => {
  const data = await fetchToolResult()
  return data  // 只有 epoch 匹配时才返回
})
// result === null 表示 epoch 已过期，丢弃结果
```

**适用场景**：

| 场景 | Epoch 递增时机 | 效果 |
|------|--------------|------|
| WebSocket 重连 | 新连接建立时 | 旧连接的 pending 回调被丢弃 |
| Session 分支 | 新 Session 创建时 | 旧 Session 的异步操作不影响新 Session |
| Agent 重启 | 新 Agent 实例启动时 | 旧实例的工具结果被丢弃 |
| Transport 切换 | 降级/升级传输时 | 旧传输的 in-flight 数据被丢弃 |

### 8.12.3 Singleflight

当多个调用者同时请求同一资源时（如多个组件同时触发重连），Singleflight 确保只有一个实际请求被发出：

```
调用者 A: reconnect() ──┐
                        │
调用者 B: reconnect() ──┤── Singleflight ──► 实际只执行一次 reconnect()
                        │                          │
调用者 C: reconnect() ──┘                          │
                                                   ▼
                                              所有调用者收到同一个结果
```

```typescript
interface Singleflight<T> {
  execute(key: string, fn: () => Promise<T>): Promise<T>
}
```

**适用场景**：

| 场景 | key | 效果 |
|------|-----|------|
| 重连 | `"reconnect"` | 多个组件触发重连，只执行一次 |
| Token 刷新 | `"token_refresh"` | 多个请求发现 Token 过期，只刷新一次 |
| Manifest 加载 | `"manifest_load"` | 多个渲染器请求 Manifest，只拉取一次 |
| 状态快照 | `"snapshot:{session_id}"` | 多个 Client 请求同一 Session 快照，只生成一次 |

---

# §9 安全模型

## 9.1 分层安全架构

```
┌──────────────────────────────────────────────────────────┐
│ L4 数据安全                                               │
│   EncryptionMiddleware · 敏感字段加密                      │
├──────────────────────────────────────────────────────────┤
│ L3 逻辑安全                                               │
│   Session 隔离 · 事件隔离 · 前端沙箱                       │
├──────────────────────────────────────────────────────────┤
│ L2 应用安全                                               │
│   JWT 认证 · Dispatcher 鉴权 · 白名单                     │
├──────────────────────────────────────────────────────────┤
│ L1 传输安全                                               │
│   TLS · WSS · CORS                                       │
└──────────────────────────────────────────────────────────┘
```

## 9.2 威胁模型

ZAP 协议面临以下主要威胁类别。实现者 MUST 评估每个威胁并采取对应缓解措施。

| # | 威胁 | 攻击向量 | 影响 | 缓解措施 |
|---|------|---------|------|---------|
| T1 | **中间人攻击 (MITM)** | 攻击者拦截 Client-Server 之间的 SSE/WebSocket 流量 | 窃取对话内容、Session Token、注入伪造事件 | L1 传输安全：MUST 使用 TLS 1.2+（WSS / HTTPS）；HSTS 头 SHOULD 启用 |
| T2 | **事件重放** | 攻击者捕获合法事件并重新发送 | 重复执行工具调用、HITL 决策 | `event_uuid` 去重（§8.8）；`seq` 单调递增校验；Server 端 HITL resolve 幂等性 |
| T3 | **事件注入** | 攻击者伪造 Server 推送事件 | 显示虚假内容、触发恶意 UI 渲染 | TLS 保证信道完整性；可选 HMAC 签名（§9.8）；Client MUST 校验 `session_id` 归属 |
| T4 | **跨 Session 泄露** | 攻击者尝试通过 `after_seq` 参数获取其他 Session 的事件 | 数据泄露 | Server MUST 校验 `session_id` + `user_id` 归属（§9.3） |
| T5 | **XSS 通过动态渲染** | Agent 输出包含恶意脚本，通过 A2UI / Mustache / Markdown 渲染执行 | 窃取用户凭证、操控 UI | 渲染安全（§9.6）：CSP、模板沙箱、HTML 净化 |
| T6 | **前端工具滥用** | 恶意 Agent 调用高危前端工具（camera、file_system.write） | 隐私泄露、数据破坏 | 前端工具安全（§9.7）：权限分级、强制确认 |
| T7 | **DoS 通过事件洪泛** | 攻击者或失控 Agent 产生海量事件 | Client 资源耗尽、UI 无响应 | 背压控制（§8.6）；速率限制中间件；Client 端合并窗口 |
| T8 | **Session 劫持** | 攻击者获取有效 Session Token | 完全控制 Session | Token 短有效期 + 刷新机制（§3.5.4）；Token 绑定 IP/User-Agent（SHOULD） |

## 9.3 认证与鉴权

| 层级 | 机制 | 说明 |
|------|------|------|
| **连接层** | JWT / Session Token | SSE 和 WebSocket 连接时验证 |
| **事件隔离** | `session_id` + `user_id` | 用户只能接收自己 Session 的事件 |
| **三帧回传** | user_id 校验 | Dispatcher 验证 req 的 user_id 与 Session 所有者一致 |

**Token 生命周期**：

| 阶段 | 要求 |
|------|------|
| 签发 | MUST 包含 `sub`(user_id)、`exp`(过期时间)、`session_scope`(可选：限制 Token 只能访问特定 Session) |
| 传输 | SSE：`Authorization: Bearer <token>` 头；WebSocket：首帧 `client.capabilities` 中携带 |
| 刷新 | Token 过期前 Client SHOULD 主动刷新（§3.5.4）；刷新期间事件 MUST 缓冲 |
| 撤销 | Server SHOULD 支持 Token 撤销列表（revocation list），被撤销的 Token 立即失效 |

## 9.4 数据隔离

| 维度 | 隔离级别 | 说明 |
|------|---------|------|
| Session | **完全隔离** | 不同 Session 的事件、状态、HITL、前端工具互不可见 |
| User | **完全隔离** | 用户 A 无法访问用户 B 的任何 Session |
| Tenant | **完全隔离** | 租户间数据物理隔离（不同 Redis namespace） |
| Conversation | Session 内共享 | 同一 Session 内的 Conversation 共享 session scope 状态 |

**隔离校验点**：

| 操作 | 校验 |
|------|------|
| SSE 连接 `GET /api/v1/chat/{session_id}` | Server MUST 校验 Token 的 `sub` 是否为该 Session 的所有者 |
| 断线重连 `?after_seq=N` | 同上；Server MUST NOT 返回其他 Session 的事件 |
| 三帧 req | Dispatcher MUST 校验 `params.session_id`（如有）与连接所属 Session 一致 |
| Shared State patch | Server MUST 校验 patch 的 scope 和 session_id 归属 |

## 9.5 前端沙箱安全

MCP Apps 在 sandboxed iframe 中运行：

| 安全控制 | 值 | 说明 |
|----------|------|------|
| iframe sandbox | `allow-scripts` | 允许脚本，不允许同源 |
| CSP | `script-src 'self'` | 限制脚本来源 |
| postMessage origin | 白名单 | Client 只接受白名单 origin 的消息 |
| 权限声明 | `_meta.ui.permissions` | MCP App 声明所需权限，Client 弹窗确认 |

## 9.6 渲染安全

Agent 动态生成的内容（A2UI 组件、Mustache 模板、Markdown、HTML 片段）是 XSS 的主要攻击面。

### 9.6.1 A2UI 渲染安全

A2UI 组件在 Client 的受控组件树中渲染（非 innerHTML），天然具备一定的 XSS 防护。但仍需注意：

| 风险 | 缓解 |
|------|------|
| `action.event.payload` 包含恶意数据 | Client MUST 对 payload 做深度校验，不直接拼接到 DOM 属性 |
| 组件 `text` 字段包含 `<script>` | Client MUST 使用 `textContent`（而非 `innerHTML`）渲染文本 |
| `layout.position` 注入 CSS | Client MUST 白名单校验 position 值（仅允许预定义的布局位置） |
| 组件 ID 冲突/覆盖 | Client MUST 在 Session scope 内 namespace 化组件 ID |

### 9.6.2 Mustache 模板安全（卡片渲染）

<div v-pre>

§5.9.4 的卡片模板使用 Mustache 语法。Mustache 默认对 `{{var}}` 做 HTML 转义，但 `{{{var}}}` (triple-stache) 不转义。

| 规则 | 约束级别 |
|------|---------|
| Client MUST NOT 在卡片模板中使用 `{{{triple-stache}}}` | MUST |
| Client MUST 对模板中的 URL 值做 allowlist 校验（仅允许 `https://`、`data:image/`） | MUST |
| Client SHOULD 对渲染后的 HTML 做二次净化（DOMPurify 或等效方案） | SHOULD |
| 模板中的 `{{#section}}` 循环 MUST 有最大迭代次数限制（默认 100） | MUST |

</div>

### 9.6.3 Markdown 与 HTML 渲染安全

| 内容类型 | 安全要求 |
|----------|---------|
| Markdown（`display_type: "markdown"`） | Client MUST 使用安全的 Markdown 渲染器（禁用 raw HTML 或经 DOMPurify 净化） |
| HTML 片段（`display_type: "html"`） | Client MUST 在 sandboxed iframe 中渲染，或使用 DOMPurify 净化后渲染 |
| 代码块（`display_type: "code"`） | Client MUST 使用纯文本渲染（语法高亮不执行代码） |

### 9.6.4 CSP 推荐配置

Client 应用 SHOULD 配置以下 Content Security Policy：

```
Content-Security-Policy:
  default-src 'self';
  script-src 'self';
  style-src 'self' 'unsafe-inline';
  img-src 'self' https: data:;
  connect-src 'self' wss://*.example.com;
  frame-src 'self';
  frame-ancestors 'none';
```

> 具体域名根据部署环境调整。`unsafe-inline` 仅用于 style（A2UI 组件的内联样式），script MUST NOT 允许 `unsafe-inline`。

## 9.7 前端工具安全

### 9.7.1 基本控制

| 控制 | 说明 |
|------|------|
| 注册白名单 | 只有 Client 注册的工具可被 Agent 调用 |
| Server 黑名单 | 管理员可全局禁止特定工具（如 `file.write`） |
| 超时 | 所有调用 MUST 有 timeout_ms |
| 结果大小限制 | 单次返回结果 SHOULD ≤ 1MB |

### 9.7.2 权限分级与强制确认

前端工具按风险等级分为三级，高危操作 MUST 经过用户确认：

| 风险等级 | 示例工具 | 用户确认 | 说明 |
|----------|---------|---------|------|
| **低** | `page.getUrl`、`page.getTitle`、`clipboard.readText` | 不需要 | 只读操作，无副作用 |
| **中** | `clipboard.writeText`、`localStorage.setItem`、`notification.show` | SHOULD 首次确认 | 有副作用但风险可控，首次调用时弹窗确认，后续同类操作可静默 |
| **高** | `camera.capture`、`file_system.write`、`geolocation.get`、`payment.initiate` | MUST 每次确认 | 涉及隐私、资金或不可逆操作，每次调用 MUST 弹窗确认 |

**确认弹窗要求**：

- MUST 显示工具名称、参数摘要、调用来源（Agent 名称）
- MUST 提供"允许"和"拒绝"两个选项
- SHOULD 提供"本次 Session 始终允许"选项（仅限中等风险）
- MUST NOT 自动确认高危操作（即使 Agent 请求）

### 9.7.3 速率限制

| 维度 | 默认限制 | 说明 |
|------|---------|------|
| 每 Session 每秒 | 5 次 | 防止 Agent 循环调用 |
| 每工具每分钟 | 30 次 | 防止单一工具被滥用 |
| 高危工具每 Session | 10 次 | 高危操作总量限制 |

## 9.8 事件完整性（可选）

对于高安全性要求的部署环境，Server MAY 对事件进行 HMAC 签名，Client 可验证事件未被篡改。

### 签名方案

```typescript
interface SignedZapEvent extends ZapEvent {
  _signature?: {
    alg: "hmac-sha256"
    sig: string                    // Base64(HMAC-SHA256(shared_secret, canonical_payload))
    kid?: string                   // Key ID，用于密钥轮换
  }
}
```

**规范化载荷（canonical payload）**：将 `event_uuid`、`seq`、`type`、`session_id`、`timestamp`、`data` 按字段名字典序排列后 JSON 序列化（无空格），作为 HMAC 输入。

| 要求 | 约束级别 |
|------|---------|
| 签名字段 `_signature` 是可选的 | MAY |
| 如果 Client 在 `client.capabilities` 中声明 `verify_signatures: true`，Server MUST 对所有事件签名 | MUST（条件性） |
| Client 收到带签名的事件时 SHOULD 验证签名 | SHOULD |
| 签名验证失败时，Client MUST 丢弃该事件并记录告警 | MUST（条件性） |
| 密钥 MUST NOT 硬编码在前端代码中，SHOULD 通过安全通道（如 session_start 的加密字段）下发 | MUST NOT / SHOULD |

---

# §10 版本与能力协商

## 10.1 协商机制

Client 和 Server 在连接建立时交换能力集。

**Server 通告**（session_start 携带）：

```json
{
  "type": "session_start",
  "data": {
    "session_id": "sess_123",
    "capabilities": {
      "protocol_version": "2.2",
      "supported_delta_types": ["ui", "hitl", "state", "frontend_tool",
                               "agent_event", "agent_message", "custom", "raw"],
      "supported_req_methods": ["surface.interact", "hitl.resolve", "state.patch",
                                "frontend_tool.result", "frontend_tool.register",
                                "chat.abort", "agent.send_message",
                                "team.create", "team.spawn_member", "team.list_members"],
      "features": {
        "event_middleware": true,
        "event_compaction": true,
        "session_branching": true,
        "request_cancel": true,
        "event_coalescing": true,
        "coalescing_mode": "incremental",
        "backpressure": true,
        "dedup_layers": 3,
        "flush_gate": true,
        "circuit_breaker": true,
        "transport_fallback": true
      }
    }
  }
}
```

**Client 通告**：

- WebSocket：`req{method: "client.capabilities", params: {...}}`
- SSE：Header `X-ZAP-Capabilities: v2,ui,hitl,state`

## 10.2 降级策略

| Client 不支持的能力 | Server 行为 |
|--------------------|------------|
| `ui` delta | 不推送 |
| `hitl` delta | 不推送，Agent 使用默认行为（见下表） |
| `state` delta | 不推送 |
| `frontend_tool` | 不调用 |
| `request_cancel` | Server 忽略 cancel 帧（v2.2） |
| `event_coalescing` | 不合并，逐事件推送（v2.2） |
| `chat.abort` | 返回 `UNKNOWN_METHOD`，Client 需用关闭连接替代（v2.2） |
| `flush_gate` | Client 不使用 flush，直接发送操作（可能丢失尾部事件）（v2.2） |
| `circuit_breaker` | Client 不熔断，持续重试（可能加剧故障）（v2.2） |
| `transport_fallback` | Client 不降级传输，仅使用首选传输（v2.2） |

### HITL 默认行为

当 Client 不支持 `hitl` 能力（或 Client 已断开）时，Agent 的 HITL 请求无法到达用户。Server MUST 按以下规则自动 resolve：

| HITL kind | 默认行为 | 说明 |
|-----------|---------|------|
| `confirm` | 如果设置了 `timeout_default`，使用该值 resolve；否则 resolve 为 `"reject"` | 安全优先：未确认的操作默认拒绝 |
| `choice` | 如果设置了 `timeout_default`，使用该值 resolve；否则返回 `HITL_UNAVAILABLE` 错误给 Agent | Agent 需要处理无法获得用户选择的情况 |
| `form` | 返回 `HITL_UNAVAILABLE` 错误给 Agent | 表单无法自动填写 |
| `input` | 如果 `payload.fields` 中所有字段都有 `default` 值，使用默认值 resolve；否则返回 `HITL_UNAVAILABLE` 错误 | 尽可能使用默认值 |

Agent Runtime 收到 `HITL_UNAVAILABLE` 错误后 SHOULD：
1. 记录日志（HITL 降级发生）
2. 尝试替代方案（如跳过需要确认的操作，或使用保守的默认行为）
3. 如果无替代方案，向用户推送 `error` 事件说明需要交互但 Client 不支持

## 10.3 兼容性承诺

| 规则 | 约束 |
|------|------|
| 前向兼容 | Client MUST 静默忽略未知 delta.type |
| 增量扩展 | 新增 delta.type 不影响已有事件流 |

---

# 附录 A — 完整事件类型 TypeScript 接口

## A.1 事件信封

```typescript
interface ZapEvent {
  event_uuid: string
  seq: number
  type: string
  session_id: string
  conversation_id?: string
  message_id?: string
  timestamp: string
  data: Record<string, unknown>
}
```

## A.2 Session 层

```typescript
interface SessionStartData {
  session_id: string
  parent_session_id?: string
  capabilities?: {
    protocol_version: string
    supported_delta_types: string[]
    supported_req_methods: string[]
    features: Record<string, boolean>
  }
}

interface SessionStoppedData {
  stop_reason: string
}
```

## A.3 Conversation 层

```typescript
interface ConversationStartData {
  conversation_id: string
}

interface ConversationDeltaData {
  delta: {
    plan?: unknown
    title?: string
    messages_snapshot?: unknown[]
  }
}
```

## A.4 Message 层

```typescript
interface MessageStartData {
  message_id: string
  role: "assistant"
  model?: string
}

interface MessageDeltaData {
  delta: {
    type?: string
    content?: Record<string, unknown>
    [key: string]: unknown
  }
}

interface MessageStopData {
  stop_reason: "end_turn" | "tool_use" | "max_tokens" | "stop_sequence" | "abort" | "cancelled"
  usage?: {
    input_tokens: number
    output_tokens: number
    cache_creation_input_tokens?: number
    cache_read_input_tokens?: number
  }
}
```

## A.5 Content 层

```typescript
interface ContentStartData {
  index: number
  content_block: {
    type: "text" | "thinking" | "tool_use" | "tool_result"
    text?: string
    thinking?: string
    id?: string
    name?: string
    input?: Record<string, unknown>
  }
}

interface ContentDeltaData {
  index: number
  delta: {
    type: "text" | "thinking" | "input_json"
    text: string
  }
}

interface ContentStopData {
  index: number
}

interface ContentSnapshotData {
  index: number
  content_block: {
    type: "text" | "thinking" | "tool_use" | "tool_result"
    text?: string
    thinking?: string
    id?: string
    name?: string
    input?: Record<string, unknown>
  }
}
```

## A.6 System 层

```typescript
interface ErrorData {
  error: {
    type: "network_error" | "timeout_error" | "overloaded_error"
        | "internal_error" | "validation_error"
    message: string
    code?: string
    retryable?: boolean
  }
}

interface AgentStatusData {
  status: "thinking" | "tool_calling" | "generating" | "idle"
}
```

## A.7 交互型 Delta

```typescript
interface A2UIDelta {
  type: "a2ui"
  surface_id: string
  catalog_id?: string
  messages: Array<
    | { createSurface: Record<string, unknown> }
    | { updateComponents: Record<string, unknown> }
    | { updateDataModel: Record<string, unknown> }
  >
  placement?: Record<string, unknown>
  streaming?: boolean
}

interface HitlContent {
  hitl_id: string
  kind: "confirm" | "choice" | "form" | "input"
  title: string
  description?: string
  options?: Array<{ id: string; label: string; description?: string }>
  payload?: {
    fields?: Array<{ name: string; type: string; label: string; required?: boolean; default?: unknown }>
  }
  timeout_ms?: number
  timeout_default?: string         // 超时后的默认行为：对应 options 中的某个 id（如 "reject"）
  persistence?: "volatile" | "durable"
}

interface StateContent {
  op: "snapshot" | "patch"
  scope: "session" | "conversation"
  state?: Record<string, unknown>
  delta?: Array<{ op: string; path: string; value?: unknown; from?: string }>
  version?: number
}

interface FrontendToolContent {
  call_id: string
  tool_name: string
  args: Record<string, unknown>
  description?: string
  timeout_ms?: number
}

interface TaskNotificationContent {
  task_id: string
  agent_id: string
  agent_name?: string
  status: "running" | "completed" | "failed" | "cancelled"
  result?: unknown
  error?: { code: string; message: string }
  progress?: { percent?: number; message?: string }
}

interface AgentMessageContent {
  from: string
  to: string | "*"                 // "*" 为广播
  team_id: string
  message_type: "text" | "task" | "result" | "status"
  content: unknown
  reply_to?: string
}
```

## A.8 三帧协议

```typescript
interface RequestFrame {
  type: "req"
  id: string
  method: string
  params?: Record<string, unknown>
  timeout_ms?: number              // v2.2：Client 端超时提示（见 §4.6）
}

interface ResponseFrame {
  type: "res"
  id: string
  ok: boolean
  data?: Record<string, unknown>
  error?: { code: string; message: string }
}

interface EventFrame {
  type: "event"
  event: string
  seq: number
  event_uuid: string
  data: Record<string, unknown>
  session_id: string
  conversation_id?: string
  message_id?: string
  timestamp: string
}

interface CancelFrame {
  type: "cancel"
  id: string                       // 要取消的 req.id
  reason?: string                  // 可选的取消原因（供日志/调试）
}
```

## A.9 请求取消语义 *(v2.2 新增)*

Cancel 帧的完整语义定义见 §4.6。上述 `CancelFrame` 接口同时列于 A.8 以保持三帧（加 cancel）协议的完整性。

## A.10 会话中断 *(v2.2 新增)*

```typescript
interface ChatAbortParams {
  session_id: string
  conversation_id?: string
  preserve_partial?: boolean   // 默认 true
}

interface ChatAbortResult {
  aborted_message_id?: string
  preserved_content: boolean
}
```

## A.11 背压配置 *(v2.2 新增)*

```typescript
interface BackpressureConfig {
  high_water_mark: number     // 默认 1000
  low_water_mark: number      // 默认 500
  max_queue_size: number      // 默认 5000
}

interface CoalescingConfig {
  content_delta_window_ms: number   // 默认 100
  progress_window_ms: number        // 默认 200
  status_window_ms: number          // 默认 200
  ping_window_ms: number            // 默认 5000
}
```

## A.12 连接状态 *(v2.2 新增)*

```typescript
type ConnectionState = "idle" | "connecting" | "connected" | "reconnecting" | "closed"

interface ReconnectionConfig {
  base_delay_ms: number          // 默认 1000
  max_delay_ms: number           // 默认 30000
  budget_ms: number              // 默认 600000 (10 min)
  jitter: number                 // 默认 0.25 (±25%)
  sleep_detection_threshold_ms: number  // 默认 max_delay_ms × 2
}

/** WebSocket 永久关闭码。Client 收到后 MUST NOT 重试。 */
type PermanentCloseCode = 1000 | 4001 | 4002 | 4003
```

## A.13 流式渲染 *(v2.2 新增)*

```typescript
interface StreamingRenderer {
  mount(block: ContentBlockStart, manifest?: ToolRenderConfig): void
  appendDelta(delta: ContentDelta): void
  finalize(): void
  destroy(): void

  readonly state: "skeleton" | "streaming" | "complete" | "unmounted"
}

interface PredictiveTransition {
  freezePreview(): void
  showExecuting(status?: string): void
  transitionToResult(data: unknown, animate?: boolean): void
}
```

## A.14 多块消息组成 *(v2.2 新增)*

```typescript
type BlockGroupType = "text" | "thinking" | "tool_card" | "orphan_result"

interface BlockGroup {
  type: BlockGroupType
  blocks: ContentBlock[]
  startIndex: number
  endIndex: number
}

interface TextGroup extends BlockGroup {
  type: "text"
  mergedText: string
}

interface ThinkingRegion extends BlockGroup {
  type: "thinking"
  collapsed: boolean
  summary?: string
}

interface ToolCard extends BlockGroup {
  type: "tool_card"
  toolUse: ToolUseBlock
  toolResult?: ToolResultBlock
  phase: "input_preview" | "executing" | "result" | "error"
  manifest?: ToolRenderConfig
}

interface OrphanResult extends BlockGroup {
  type: "orphan_result"
  toolResult: ToolResultBlock
}

interface MessageComposition {
  messageId: string
  groups: BlockGroup[]
  isStreaming: boolean

  appendBlock(block: ContentBlock): void
  finalizeBlock(index: number): void
  finalizeMessage(): void
}
```

## A.15 自动类型推断与卡片模板 *(v2.2 新增)*

```typescript
interface AutoDetectResult {
  display_type: DisplayType
  confidence: number               // 0.0 ~ 1.0
  config: Record<string, unknown>
  detector: string
}

interface DetectorEntry {
  name: string
  priority: number                 // 数值越大越先执行
  detect(data: unknown): AutoDetectResult | null
}

interface AutoDetector {
  readonly chain: DetectorEntry[]
  detect(data: unknown): AutoDetectResult
  register(entry: DetectorEntry): void
}

interface CardTemplate {
  title?: string                   // Mustache 模板
  subtitle?: string
  icon_url?: string
  avatar_url?: string
  fields?: CardField[]
  footer?: string
  accent_color?: string
  actions?: CardAction[]
  layout?: "vertical" | "horizontal"
  columns?: number
}

interface CardField {
  label: string
  value: string
  icon?: string
  format?: "text" | "date" | "number" | "url" | "badge"
  badge_color?: string
}

interface CardAction {
  label: string
  action: "surface.interact"
  params: Record<string, unknown>
  style?: "primary" | "secondary" | "danger"
}
```

## A.16 主题令牌与错误渲染 *(v2.2 新增)*

```typescript
interface ThemeTokens {
  surface: { bg: string; bg_secondary: string; border: string; border_radius: string }
  text: { primary: string; secondary: string; muted: string; code: string; link: string }
  status: { success: string; warning: string; error: string; info: string }
  table: { header_bg: string; header_text: string; row_hover: string; stripe_bg: string; border: string }
  code: { bg: string; text: string; line_number: string; highlight_bg: string; syntax_theme: string }
  chart: { palette: string[]; grid_color: string; axis_color: string; tooltip_bg: string }
  card: { bg: string; border: string; shadow: string; header_bg: string; accent_border_width: string }
  motion: { duration_fast: string; duration_normal: string; duration_slow: string; easing: string }
  spacing: { block_gap: string; card_padding: string; inline_gap: string }
}

interface ErrorRenderConfig {
  source: "tool_error" | "agent_error" | "timeout" | "connection" | "abort"
  code?: string
  message: string
  details?: unknown
  retryable: boolean
  retry_action?: () => void
}

interface ErrorRenderer {
  renderToolError(config: ErrorRenderConfig, toolCard: ToolCard): void
  renderAgentError(config: ErrorRenderConfig): void
  renderConnectionError(state: ConnectionState): void
  renderAbortMark(messageId: string, preservePartial: boolean): void
}

interface ToolExecutionIndicator {
  toolName: string
  displayName: string
  icon?: string
  state: "calling" | "executing" | "done" | "error"
  statusText?: string
  progress?: { current: number; total: number }
  elapsed_ms: number
}
```

## A.17 去重缓冲区 *(v2.2 新增)*

```typescript
interface DeduplicationBuffer {
  readonly capacity: number

  has(uuid: string): boolean
  add(uuid: string): void

  readonly size: number
  readonly evictionCount: number
}
```

## A.18 熔断器 *(v2.2 新增)*

```typescript
interface CircuitBreaker {
  readonly state: "closed" | "open" | "half_open"
  readonly failureCount: number
  readonly lastFailureTime: number

  execute<T>(fn: () => Promise<T>): Promise<T>
  recordSuccess(): void
  recordFailure(error: Error): void
  reset(): void
}

interface CircuitBreakerConfig {
  failure_threshold: number
  reset_timeout_ms: number
  half_open_max_attempts: number
  excluded_errors?: string[]
}
```

## A.19 竞态防护 *(v2.2 新增)*

```typescript
interface EpochGuard {
  readonly currentEpoch: number
  increment(): number
  guard<T>(epoch: number, fn: () => Promise<T>): Promise<T | null>
}

interface Singleflight<T> {
  execute(key: string, fn: () => Promise<T>): Promise<T>
}
```

---

# 附录 B — JSON 示例与交互序列

## B.1 纯文本对话（最简路径）

```json
{"event_uuid":"e001","seq":1,"type":"session_start","session_id":"sess_01","timestamp":"2026-03-17T10:00:00Z","data":{"session_id":"sess_01"}}
{"event_uuid":"e002","seq":2,"type":"conversation_start","session_id":"sess_01","conversation_id":"conv_01","timestamp":"2026-03-17T10:00:00Z","data":{"conversation_id":"conv_01"}}
{"event_uuid":"e003","seq":3,"type":"message_start","session_id":"sess_01","conversation_id":"conv_01","message_id":"msg_01","timestamp":"2026-03-17T10:00:00Z","data":{"message_id":"msg_01","role":"assistant","model":"claude-sonnet-4-20250514"}}
{"event_uuid":"e004","seq":4,"type":"content_start","session_id":"sess_01","conversation_id":"conv_01","message_id":"msg_01","timestamp":"2026-03-17T10:00:00Z","data":{"index":0,"content_block":{"type":"text","text":""}}}
{"event_uuid":"e005","seq":5,"type":"content_delta","session_id":"sess_01","conversation_id":"conv_01","message_id":"msg_01","timestamp":"2026-03-17T10:00:00Z","data":{"index":0,"delta":{"type":"text","text":"你好，"}}}
{"event_uuid":"e006","seq":6,"type":"content_delta","session_id":"sess_01","conversation_id":"conv_01","message_id":"msg_01","timestamp":"2026-03-17T10:00:01Z","data":{"index":0,"delta":{"type":"text","text":"有什么可以帮你的？"}}}
{"event_uuid":"e007","seq":7,"type":"content_stop","session_id":"sess_01","conversation_id":"conv_01","message_id":"msg_01","timestamp":"2026-03-17T10:00:01Z","data":{"index":0}}
{"event_uuid":"e008","seq":8,"type":"message_stop","session_id":"sess_01","conversation_id":"conv_01","message_id":"msg_01","timestamp":"2026-03-17T10:00:01Z","data":{"stop_reason":"end_turn","usage":{"input_tokens":12,"output_tokens":18}}}
{"event_uuid":"e009","seq":9,"type":"conversation_stop","session_id":"sess_01","conversation_id":"conv_01","timestamp":"2026-03-17T10:00:01Z","data":{}}
{"event_uuid":"e010","seq":10,"type":"done","session_id":"sess_01","timestamp":"2026-03-17T10:00:01Z","data":{}}
```

## B.2 工具调用（前端自渲染）

工具结果在 Content 层原样推送，前端根据 `tool_use.name`（`query_database`）选择渲染组件。

seq 5 的 `input_json` 可被前端用于 Predictive State（实时预览 SQL 语句）。

```json
{"seq":4,"type":"content_start","data":{"index":1,"content_block":{"type":"tool_use","id":"toolu_01","name":"query_database","input":{}}}}
{"seq":5,"type":"content_delta","data":{"index":1,"delta":{"type":"input_json","text":"{\"sql\":\"SELECT * FROM users LIMIT 5\"}"}}}
{"seq":6,"type":"content_stop","data":{"index":1}}
{"seq":7,"type":"content_start","data":{"index":2,"content_block":{"type":"tool_result","tool_use_id":"toolu_01","content":[{"type":"text","text":"{\"columns\":[\"id\",\"name\",\"email\"],\"rows\":[[1,\"Alice\",\"alice@example.com\"],[2,\"Bob\",\"bob@example.com\"]]}"}]}}}
{"seq":8,"type":"content_stop","data":{"index":2}}
```

## B.3 HITL 确认流程

```json
{"seq":14,"type":"message_delta","data":{"delta":{"type":"hitl","content":{"hitl_id":"hitl_01","kind":"confirm","title":"确认执行 DROP TABLE users?","description":"此操作不可逆","options":[{"id":"approve","label":"确认"},{"id":"reject","label":"取消"}],"timeout_ms":60000,"persistence":"durable"}}}}
```

Client 回传：

```json
{"type":"req","id":"req_042","method":"hitl.resolve","params":{"session_id":"sess_01","hitl_id":"hitl_01","choice":"approve"}}
```

Server 响应：

```json
{"type":"res","id":"req_042","ok":true}
```

## B.4 共享状态同步

Server 推送 snapshot：

```json
{"seq":5,"type":"message_delta","data":{"delta":{"type":"state","content":{"op":"snapshot","scope":"conversation","state":{"cart":[],"total":0},"version":1}}}}
```

Server 推送 patch：

```json
{"seq":12,"type":"message_delta","data":{"delta":{"type":"state","content":{"op":"patch","scope":"conversation","delta":[{"op":"add","path":"/cart/-","value":{"id":"item_01","name":"Widget","price":9.99}},{"op":"replace","path":"/total","value":9.99}],"version":2}}}}
```

Client 回传 patch：

```json
{"type":"req","id":"req_055","method":"state.patch","params":{"session_id":"sess_01","scope":"conversation","patches":[{"op":"replace","path":"/cart/0/quantity","value":2},{"op":"replace","path":"/total","value":19.98}],"version":2}}
```

## B.5 生成式 UI（A2UI）

Server 推送 UI（add）：

```json
{
  "seq": 15,
  "type": "message_delta",
  "data": {
    "delta": {
      "type": "ui",
      "content": {
        "format": "a2ui",
        "verb": "add",
        "id": "shipping-001",
        "components": [
          {"id": "root", "component": "Card", "children": ["title", "buttons"]},
          {"id": "title", "component": "Text", "text": "选择配送方式"},
          {"id": "buttons", "component": "Row", "children": ["btn-standard", "btn-express"]},
          {"id": "btn-standard", "component": "Button", "child": "t-standard",
           "action": {"event": {"name": "select", "payload": {"method": "standard"}}}},
          {"id": "t-standard", "component": "Text", "text": "标准配送 (3-5天)"},
          {"id": "btn-express", "component": "Button", "child": "t-express",
           "action": {"event": {"name": "select", "payload": {"method": "express"}}}},
          {"id": "t-express", "component": "Text", "text": "加急配送 (1天)"}
        ],
        "layout": {"position": "inline", "width": "md"}
      }
    }
  }
}
```

Client 回传：

```json
{"type":"req","id":"req_060","method":"surface.interact","params":{"session_id":"sess_01","surface_id":"checkout-surface","source_component_id":"shipping-001","event_name":"shipping.select","context":{"method":"express"}}}
```

Server 推送 UI（update，用户选择后更新显示）：

```json
{
  "seq": 17,
  "type": "message_delta",
  "data": {
    "delta": {
      "type": "ui",
      "content": {
        "format": "a2ui",
        "verb": "update",
        "id": "shipping-001",
        "components": [
          {"id": "title", "component": "Text", "text": "已选择：加急配送"},
          {"id": "buttons", "component": "Row", "children": []}
        ]
      }
    }
  }
}
```

## B.6 前端工具调用

Client 注册工具：

```json
{"type":"req","id":"req_100","method":"frontend_tool.register","params":{"session_id":"sess_01","tools":[{"name":"clipboard.read","description":"读取系统剪贴板内容","schema":{}},{"name":"dom.getSelection","description":"获取页面选中文本","schema":{}}]}}
```

Server 推送工具调用：

```json
{
  "seq": 20,
  "type": "message_delta",
  "data": {
    "delta": {
      "type": "frontend_tool",
      "content": {
        "call_id": "ftc_001",
        "tool_name": "clipboard.read",
        "args": {},
        "timeout_ms": 10000
      }
    }
  }
}
```

Client 返回结果：

```json
{"type":"req","id":"req_101","method":"frontend_tool.result","params":{"session_id":"sess_01","call_id":"ftc_001","result":"用户剪贴板中的文本内容..."}}
```

Client 返回错误：

```json
{"type":"req","id":"req_102","method":"frontend_tool.result","params":{"session_id":"sess_01","call_id":"ftc_002","is_error":true,"error":"NotAllowedError: Clipboard read permission denied"}}
```

## B.7 断线重连序列

正常重连（缓冲可用）：

```
Client                              Server
  │                                    │
  │ (网络恢复, 最后 seq=42)             │
  │── GET /api/v1/chat/sess_01?after_seq=42 ►│
  │                                    │── 查询 Buffer(seq>42)
  │◄── event{seq:43, type:"content_delta", ...} ──│
  │◄── event{seq:44, type:"content_delta", ...} ──│
  │◄── event{seq:45, type:"content_stop", ...} ───│
  │◄── (实时流继续) ──────────────────────────────│
```

降级重连（缓冲过期）：

```
Client                              Server
  │                                    │
  │ (网络恢复, 最后 seq=42, 但缓冲 TTL 已过)      │
  │── GET /api/v1/chat/sess_01?after_seq=42 ────────►│
  │                                    │── 查询 Buffer → 部分/全部过期
  │◄── event{seq:43, type:"conversation_delta",    │
  │    data:{delta:{messages_snapshot:[...]}}} ────│  ← snapshot 事件携带新 seq
  │◄── event{seq:44, type:"message_start", ...} ──│
  │◄── (从当前消息的实时流继续) ───────────────────│
```

> **注意**：降级重连时 Server 生成的 snapshot 事件 MUST 继续使用 Session 级单调递增的 seq（此处 seq=43），而非从 1 重新开始。这保证了 Client 的 seq 不变量——Session 内 seq 始终单调递增。

## B.8 WebSocket 完整握手

```json
// 1. Client 通告能力
{"type":"req","id":"cap_01","method":"client.capabilities","params":{"protocol_version":"2.0","supported_delta_types":["ui","hitl","state","frontend_tool"]}}

// 2. Server 确认
{"type":"res","id":"cap_01","ok":true}

// 3. Client 发起对话
{"type":"req","id":"chat_01","method":"chat.send","params":{"message":"你好","conversation_id":"conv_new"}}

// 4. Server 确认
{"type":"res","id":"chat_01","ok":true,"data":{"session_id":"sess_abc","conversation_id":"conv_01"}}

// 5. 事件流开始
{"type":"event","event":"session_start","seq":1,"event_uuid":"e001","session_id":"sess_abc","timestamp":"2026-03-17T10:00:00Z","data":{"session_id":"sess_abc","capabilities":{"protocol_version":"2.2","supported_delta_types":["ui","hitl","state","frontend_tool"],"features":{"event_middleware":true,"event_compaction":true,"request_cancel":true,"coalescing_mode":"incremental"}}}}
```

## B.9 请求取消 *(v2.2 新增)*

Client 取消一个正在进行的 HITL：

```json
// 1. Server 推送 HITL
{"type":"event","event":"message_delta","seq":7,"event_uuid":"e007","session_id":"sess_01","conversation_id":"conv_01","message_id":"msg_01","timestamp":"2026-03-31T10:00:07Z","data":{"delta":{"type":"hitl","content":{"hitl_id":"hitl_01","kind":"confirm","title":"确认删除？"}}}}

// 2. Client 发送 resolve 请求
{"type":"req","id":"r_hitl_01","method":"hitl.resolve","params":{"session_id":"sess_01","hitl_id":"hitl_01","choice":"approve"}}

// 3. 用户改主意了，立即取消
{"type":"cancel","id":"r_hitl_01","reason":"user_dismissed"}

// 4a. 如果 cancel 生效（resolve 尚未被 Server 处理）
{"type":"res","id":"r_hitl_01","ok":false,"error":{"code":"CANCELLED","message":"Request cancelled by client"}}

// 4b. 如果 cancel 来晚了（resolve 已处理）
{"type":"res","id":"r_hitl_01","ok":true}
```

## B.10 会话中断 *(v2.2 新增)*

用户在 Agent 生成过程中点击"停止"：

```json
// 1. Agent 正在生成
{"type":"event","event":"content_delta","seq":5,"event_uuid":"e005","session_id":"sess_01","conversation_id":"conv_01","message_id":"msg_01","timestamp":"2026-03-31T10:00:05Z","data":{"index":0,"delta":{"type":"text","text":"让我来分析"}}}
{"type":"event","event":"content_delta","seq":6,"event_uuid":"e006","session_id":"sess_01","conversation_id":"conv_01","message_id":"msg_01","timestamp":"2026-03-31T10:00:05Z","data":{"index":0,"delta":{"type":"text","text":"这个问题..."}}}

// 2. 用户点击"停止"
{"type":"req","id":"abort_01","method":"chat.abort","params":{"session_id":"sess_01","preserve_partial":true}}

// 3. Server 推送中断后的收尾事件
{"type":"event","event":"content_stop","seq":7,"event_uuid":"e007","session_id":"sess_01","conversation_id":"conv_01","message_id":"msg_01","timestamp":"2026-03-31T10:00:06Z","data":{"index":0}}
{"type":"event","event":"message_stop","seq":8,"event_uuid":"e008","session_id":"sess_01","conversation_id":"conv_01","message_id":"msg_01","timestamp":"2026-03-31T10:00:06Z","data":{"stop_reason":"abort"}}
{"type":"event","event":"conversation_stop","seq":9,"event_uuid":"e009","session_id":"sess_01","conversation_id":"conv_01","timestamp":"2026-03-31T10:00:06Z","data":{}}
{"type":"event","event":"done","seq":10,"event_uuid":"e010","session_id":"sess_01","timestamp":"2026-03-31T10:00:06Z","data":{}}

// 4. Server 确认
{"type":"res","id":"abort_01","ok":true,"data":{"aborted_message_id":"msg_01","preserved_content":true}}
```

## B.11 高频事件合并效果 *(v2.2 新增)*

合并前（3 个事件，3 次网络传输）：

```json
{"seq":13,"type":"content_delta","data":{"index":0,"delta":{"type":"text","text":"你"}}}
{"seq":14,"type":"content_delta","data":{"index":0,"delta":{"type":"text","text":"好"}}}
{"seq":15,"type":"content_delta","data":{"index":0,"delta":{"type":"text","text":"世"}}}
```

合并后（1 个事件，1 次网络传输）：

```json
{"seq":15,"type":"content_delta","event_uuid":"coalesced_001","data":{"index":0,"delta":{"type":"text","text":"你好世"},"_coalesced":{"original_count":3,"original_seq_range":[13,14,15]}}}
```

## B.12 多块消息渲染时序 *(v2.2 新增)*

一条包含文本 + 工具调用 + 结果 + 后续文本的完整消息渲染时序：

```
时间轴    事件                                          前端动作
──────────────────────────────────────────────────────────────────────────────
t0        message_start                                 创建 MessageBubble
          {role:"assistant", message_id:"msg_01"}

t1        content_start index:0 type:"text"             创建 TextGroup #1
                                                        mount(skeleton)

t2        content_delta index:0 text:"让我"              TextGroup #1 → streaming
t3        content_delta index:0 text:"查一下"            追加文本
t4        content_delta index:0 text:"数据库..."          追加文本
t5        content_stop  index:0                         TextGroup #1 → complete

t6        content_start index:1 type:"tool_use"         创建 ToolCard #1
          name:"query_database" id:"toolu_01"           mount(skeleton)

t7        content_delta index:1                         ToolCard #1 → input_preview
          input_json:'{"sql":"SELECT'             Predictive State: SQL 逐字出现

t8        content_delta index:1                         追加 partial JSON
          input_json:' * FROM users'

t9        content_delta index:1                         追加 partial JSON
          input_json:' WHERE age > 25"}'

t10       content_stop  index:1                         ToolCard #1 → executing
                                                        freezePreview()
                                                        showExecuting("查询中...")

t11       agent_status "正在执行 SQL 查询..."             更新执行状态文本

t12       content_start index:2 type:"tool_result"      ToolCard #1 → result
          tool_use_id:"toolu_01"                        transitionToResult(data)
          content:[{columns:["name","age"],              DataTableRenderer 渲染表格
                    rows:[["Alice",30],["Bob",28]]}]

t13       content_stop  index:2                         ToolCard #1 → complete
                                                        启用排序/导出

t14       content_start index:3 type:"text"             创建 TextGroup #2
t15       content_delta index:3 text:"根据查询结果..."    TextGroup #2 → streaming
t16       content_stop  index:3                         TextGroup #2 → complete

t17       message_stop                                  MessageBubble → complete
                                                        所有光标/加载指示器消失
```

最终 BlockGroup 结构：

```json
{
  "messageId": "msg_01",
  "groups": [
    { "type": "text", "startIndex": 0, "endIndex": 0, "mergedText": "让我查一下数据库..." },
    {
      "type": "tool_card", "startIndex": 1, "endIndex": 2,
      "toolUse": { "name": "query_database", "id": "toolu_01" },
      "toolResult": { "tool_use_id": "toolu_01", "content": "..." },
      "phase": "result"
    },
    { "type": "text", "startIndex": 3, "endIndex": 3, "mergedText": "根据查询结果..." }
  ],
  "isStreaming": false
}
```

## B.13 断线重连去重时序 *(v2.2 新增)*

Client 在 seq=42 时断线，重连后 Server 补发 seq 43-45。但 seq 43 是合并事件（原始 seq 41-43），Client 已收到 seq 41-42：

```
断线前 Client 已收到：
  seq=40  content_delta  uuid=e040
  seq=41  content_delta  uuid=e041
  seq=42  content_delta  uuid=e042  ← 最后收到的 seq

Server 端合并后的 Buffer：
  seq=43  content_delta  uuid=coalesced_01  (原始 seq 41-43 合并)
  seq=44  content_stop   uuid=e044
  seq=45  message_stop   uuid=e045

重连：Client 请求 after_seq=42
  │
  ▼
Server 补发 seq=43, 44, 45

Client 去重过程：
  seq=43 (uuid=coalesced_01):
    L1 seq 去重: 43 > 42 → 通过
    L2 UUID 去重: coalesced_01 不在缓冲 → 通过
    ⚠️ 问题：此合并事件包含 seq 41-42 的内容，已部分显示
    解决：Client 检查 _coalesced.original_seq_range
          发现 [41,42] ≤ lastReceivedSeq(42)
          → 此合并事件是部分重复
          → 使用合并后的完整文本替换已有文本（幂等操作）

  seq=44 (uuid=e044):
    L1: 44 > 42 → 通过
    L2: e044 不在缓冲 → 通过
    → 正常处理

  seq=45 (uuid=e045):
    L1: 45 > 42 → 通过
    L2: e045 不在缓冲 → 通过
    → 正常处理
```

---

# 附录 C — 架构决策记录 ADR

## ADR-001: 通过 delta.type 扩展新能力，而非新增事件层级

**背景**：需要接入生成式 UI、共享状态等新能力。方案 A: 在 Message 层新增 `delta.type`；方案 B: 在五层之上新增「交互层」。

**决策**：方案 A。

**理由**：不需要修改 EventManager、持久化、传输层——这些是已稳定的核心。复用已有的 seq、去重、断线重连能力。

**权衡**：`message_delta` 承载更多语义，需要严格的 type 注册和文档管理。

## ADR-002: 推送和回传走不同路径

**背景**：推送和回传可以走同一管道，也可以分离。

**决策**：推送走事件管线（经持久化），回传走三帧 req/res（不经持久化）。

**理由**：推送需要 seq、持久化、断线重连。回传是即时请求-响应，不需要这些。分离让两条路径独立优化。

**权衡**：SSE Client 的回传需要 HTTP POST 降级。

## ADR-003: SSE 和 WebSocket 平等

**背景**：可以只支持 WebSocket，也可以两者都支持。

**决策**：两者平等，所有推送事件在两个通道上格式一致。

**理由**：SSE 的 HTTP 兼容性更好（CDN、负载均衡、代理友好）。很多场景不需要回传。

**权衡**：SSE 回传需要额外 HTTP 端点，增加 API 表面。

## ADR-004: JSON Patch (RFC 6902) 用于状态增量同步

**背景**：可以用自定义 diff 格式，也可以用标准 JSON Patch。

**决策**：JSON Patch RFC 6902。

**理由**：标准化，前后端都有成熟库。操作语义明确。可包含 `test` 操作实现乐观锁。

**权衡**：JSON Pointer 路径语法有学习成本，数组操作不如 CRDT 友好。

## ADR-005: volatile / durable 两种 HITL 持久化

**背景**：Agent 等待用户决策时可能崩溃。

**决策**：提供 volatile（内存）和 durable（持久化存储）两种模式。

**理由**：大部分 HITL 是短时的（< 60s），volatile 零开销。长审批需要 durable，崩溃后可恢复。

**权衡**：durable 增加存储依赖和恢复逻辑复杂度。

## ADR-006: A2UI 作为唯一生成式 UI 格式

**背景**：初始设计支持三种 UI 格式（zenflux 自定义、A2UI、Open-JSON-UI），增加了前端实现和 LLM prompt 维护成本。

**决策**：只保留 A2UI。

**理由**：
- A2UI 的平面邻接表对 LLM 最友好（逐个输出，无需维护嵌套）
- 数据-视图分离让增量更新高效
- Google 背书，社区采用度上升
- 一种格式减少 Client 渲染器维护成本

**权衡**：放弃了 zenflux 自定义格式的灵活性。A2UI 的组件注册需要额外工作。

## ADR-007: HITL 取代 interrupt 命名

**背景**：v1 使用 `hitl`，设计 v2 时一度考虑改名 `interrupt`（受 AG-UI 影响）。

**决策**：统一使用 `hitl`。

**理由**：
- HITL (Human-in-the-Loop) 是业界标准术语，语义更精确
- `interrupt` 容易与系统中断概念混淆

**权衡**：与 AG-UI 的 `interrupt` 术语不一致，需要在外部协议对比中标注映射。

## ADR-008: 外部 SSE 封装适配器退役

**背景**：v1 支持一种与 LLM 原生流式形状不同的外部封装事件格式，需要双向转换适配器。

**决策**：v2 只保留与 LLM Streaming 对齐的 Zenflux 事件格式。

**理由**：
- 双形状适配层是大量 bug 的来源
- 维护多种输出形状的转换逻辑成本高
- 所有已知 Client 都已迁移到原生格式

**权衡**：极少数依赖旧封装的外部集成需要迁移（迁移指南见 `08-MIGRATION.md`）。

## ADR-009: 前端工具采用注册制

**背景**：Agent 调用前端能力时，可以直接调用（假设 Client 都支持），或要求 Client 先注册。

**决策**：注册制（`frontend_tool.register`）。

**理由**：
- 安全：只有 Client 声明的工具可被调用
- 发现：Agent 知道有哪些工具可用
- 优雅降级：不支持某工具的 Client 不会收到调用

**权衡**：增加了连接建立时的握手步骤。

## ADR-010: Event Buffer 使用 Redis Sorted Set

**背景**：断线重连需要缓冲最近的事件。可选内存（进程内 deque）或外部存储（Redis）。

**决策**：Redis Sorted Set（score=seq）。

**理由**：
- 多进程部署时缓冲共享
- `ZRANGEBYSCORE(after_seq, +inf)` 高效查询
- Redis TTL 自动清理
- 内存只适合单进程开发环境

**权衡**：依赖 Redis，增加基础设施需求。开发环境可用内存 fallback。

## ADR-011: 前端自渲染工具结果，废弃 ToolEnhancerRegistry

**背景**：v2.0 中工具结果通过 ToolEnhancerRegistry 翻译为 `message_delta`（`chart`/`sql`/`data`/`files` 等），推送给前端渲染。存在以下问题：

1. **输出形状分裂**：历史上按请求参数切换输出路径时，Enhancer 仅在部分路径激活，导致同一场景下事件流形状不一致。
2. **Predictive State 不可能**：Enhancer 在 tool_result 之后才介入，无法利用 `input_json` 做实时预览。
3. **新工具接入链路长**：写工具 → 写 Enhancer → 前端加 delta handler → 前后端同时发版。
4. **渲染决策后端化**：后端决定"画成什么样"，前端失去灵活性（不同场景想用不同渲染方式）。

**决策**：废弃 ToolEnhancerRegistry。工具结果在 Content 层原样透传（`tool_result` content block），前端根据 `tool_use.name` 选择渲染组件。

**理由**：
- **协议纯净**：只有一种事件流形状，与 LLM Streaming API 完全一致
- **Predictive State 可用**：`content_delta(input_json)` 在工具参数生成过程中实时推送，前端可增量解析做预览
- **新工具只改前端**：写工具 → 前端写 Renderer 组件。不需要碰后端
- **渲染权归前端**：同一 tool_result 在 Playground、移动端、嵌入场景可有不同渲染方式
- **工具名即契约**：`tool_use.name` 本身就是前端选择渲染器的依据，不需要额外 hint

**保留的 delta.type**：
- 交互型（有推送-回传闭环）：`ui`、`hitl`、`state`、`frontend_tool`
- 元信息型（单向推送）：`progress`、`recommended`、`billing`、`clue`

**移除的 delta.type**：
- `chart`、`sql`、`data`、`dashboard`、`files`、`sandbox`、`knowledge`、`mind`、`search`、`ppt`

**权衡**：
- 非浏览器渠道（飞书、钉钉）不能"自渲染"，需要各渠道适配器独立处理 tool_result。但这些适配器本来就是独立的（如 `services/notification_adapters/`、`channels/feishu/` 等），不依赖 ToolEnhancerRegistry。
- 前端需要了解每个工具的返回格式。但前端本来就要决定"怎么画"，工具名是天然的契约。
- URL rehost 需要移到中间件层处理 Content 层事件中的外部 URL。

## ADR-012: Predictive State 作为纯前端能力

**背景**：AG-UI 协议提出 Predictive State Updates——在 Agent 生成工具参数时，前端实时预览参数效果（如表单字段逐个填充）。

**决策**：Predictive State 作为前端 SDK 能力实现，不引入新协议事件。

**理由**：
- ZAP 已有 `content_delta(type:"input_json")`，逐 token 推送工具参数的 partial JSON
- 前端使用 Partial JSON Parser 增量解析即可实现
- 纯前端实现意味着：后端零改动、协议零改动、不同前端可自行决定是否启用
- 与 ADR-011（前端自渲染）一脉相承：渲染逻辑归前端

**权衡**：需要前端引入 Partial JSON Parser 库，增加少量前端复杂度。

## ADR-013: 引入 cancel 帧实现请求取消

**背景**：v2.1 中三帧协议只有 req/res/event，Client 无法取消已发出的 req。实际场景中，用户关闭 HITL 弹窗、页面导航、或 timeout 都需要取消能力。

**参考**：Claude Code 的 `control_cancel_request` + `AbortSignal` 机制——发送取消请求后立即 reject pending Promise，不等 Server 确认。

**决策**：新增 `cancel` 帧类型，Client 可随时取消 pending req。

**理由**：
- 取消是生产系统的必备能力，缺失会导致资源泄漏（Server 持续等待不会到来的结果）
- `cancel` 帧比超时更快响应用户意图
- 级联取消（cancel chat.send → 取消所有子操作）避免了 orphan 状态

**权衡**：
- cancel 与 res 的竞态需要 Client 和 Server 同时处理两种结局
- 增加了三帧协议的复杂度（从 3 种帧类型变为 4 种）
- 选择了非对称设计（只有 Client→Server 的 cancel，Server 不能取消推送），保持推送路径简单

## ADR-014: chat.abort 采用会话内中断而非 Run 终止

**背景**：用户需要中断正在生成的 Agent 回复。方案 A: 终止当前 Run/Session，新建 Session 继续（AG-UI 模式）；方案 B: 会话内中断，保留 Session 上下文（Claude Code / ZAP 模式）。

**决策**：方案 B，会话内中断。

**理由**：
- 中断后用户通常想继续对话（"别说了，换个方式"），终止 Session 会丢失上下文
- `preserve_partial` 选项让用户决定是否保留部分结果
- 中断恢复成本极低（推送收尾事件即可），而 Session 重建需要恢复状态
- 与 ZAP 的 HITL 暂停模型一脉相承：Session 是长生命周期的

**权衡**：
- Agent Runtime 需要支持中断信号（asyncio.CancelledError 等）
- 已推送的部分结果可能是不完整的（如 tool_use 参数只生成了一半），Client 需要容错
- stop_reason="abort" 的消息进入对话历史可能影响后续 Agent 行为（但这也是优势——Agent 知道被打断了）

## ADR-015: 三层背压模型

**背景**：LLM 高频输出 + 多种传输方式 + 可能缓慢的 Client，需要背压机制。方案 A: 无限缓冲 + 丢弃最旧；方案 B: 水位线背压阻塞 Agent。

**参考**：Claude Code 的 `SerialBatchEventUploader` + `maxQueueSize` + WebSocket `bufferedAmount` 检测。

**决策**：三层背压（生产端 → 写入端 → 传输端），以水位线模型为核心。

**理由**：
- L1（生产端限流）：阻塞 Agent 是最安全的背压方式——不丢事件，只降速
- L2（写入端串行化）：参考 Claude Code 的经验——并发 POST 导致写入冲突和重试风暴
- L3（传输端感知）：WebSocket `bufferedAmount` 和 SSE `drain` 事件是标准的传输层背压信号

**权衡**：
- L1 阻塞 Agent 会增加端到端延迟（但比丢事件好）
- 水位线参数需要调优（high=1000, low=500 是经验值，可能因场景而异）
- 丢弃策略（超过 max_queue_size 时按优先级丢弃）引入了"事件不可靠"的语义，与 §1.4 P5 原则存在张力。但 ping 和 progress 本就是可丢弃的

## ADR-016: 高频事件合并在 EventManager 层而非传输层

**背景**：text 合并可以在 Agent 侧（emit 前）、EventManager（持久化前）、或传输层（推送前）实现。

**参考**：Claude Code 的 CCRClient 在传输层做 100ms 窗口合并 + text 快照模式。

**决策**：在 EventManager 层实现合并（持久化之前），但快照模式作为可选传输层优化。

**理由**：
- EventManager 层合并减少了持久化写入次数（Buffer 中存 1 条而非 3 条）
- 合并后的事件用新 event_uuid，不影响去重逻辑
- 传输层快照模式是额外优化，适合 Client 中途接入场景

**权衡**：
- EventManager 层合并意味着原始事件不再可追溯（除非通过 `_coalesced` 元数据）
- 合并窗口引入额外延迟（100ms），对低延迟场景可能过大（可配置为 50ms 或 0ms 禁用）
- 断线重连时补发的是合并后的事件，seq 号语义发生变化（合并事件占用末尾 seq）

## ADR-017: 卡片模板系统 vs 每个工具写自定义组件

**背景**：80% 的工具结果是键值对结构（天气、用户资料、文件信息等）。方案 A: 每个工具写一个自定义渲染组件；方案 B: 提供 Mustache 模板驱动的通用卡片渲染器。

**参考**：Claude Code 没有模板系统，每个工具的渲染逻辑硬编码在前端。这导致新增工具必须发版前端。

**决策**：方案 B。在 DisplayType 中新增 `"card"` 类型，通过 `config.template` 定义 Mustache 模板。

**理由**：
- 新增工具只改 Manifest JSON 配置，无需前端发版（路径 A 覆盖率从 70% 提升到 90%）
- Mustache 是逻辑最少化模板语言，不引入安全风险（无任意代码执行）
- AutoDetector 的 KeyValueDetector 可自动生成简单卡片模板，实现零配置渲染

**权衡**：
- Mustache 表达力有限，复杂布局仍需自定义组件（`display_type: "custom"`）
- 模板语法是额外的学习成本（但比写 React 组件低得多）
- 模板渲染性能略低于原生组件（但卡片数据量小，可忽略）

## ADR-018: 主题令牌 vs CSS 变量 vs Tailwind Token

**背景**：通用渲染器需要适配不同产品的视觉风格（Playground / 移动端 / 嵌入式）。方案 A: CSS 自定义属性（`--zap-*`）；方案 B: TypeScript ThemeTokens 接口；方案 C: Tailwind Design Token。

**决策**：方案 B（TypeScript ThemeTokens），CSS 变量作为可选的底层实现。

**理由**：
- TypeScript 接口提供编译时类型检查，CSS 变量只有运行时错误
- ThemeTokens 可在非 Web 环境使用（React Native、Flutter、终端 UI）
- 渲染器通过 Context/Provider 消费 token，与 A2UI 组件的样式空间天然隔离
- 实际实现时，ThemeTokens 可以映射到 CSS 变量（`surface.bg` → `--zap-surface-bg`），两者不冲突

**权衡**：
- 比纯 CSS 变量多一层抽象，增加了 bundle 大小（但 ThemeTokens 对象很小）
- Tailwind 用户需要额外的 token 映射层（但 Tailwind 本身也支持 CSS 变量作为 token 源）
- 主题切换需要重新注入 ThemeTokens（而 CSS 变量可以通过切换 class 实现），但 Context re-render 在现代框架中性能足够

## ADR-019: 三层去重而非单层 UUID 去重

**背景**：v2.1 的 §8.2 仅定义了 UUID 去重。生产环境中发现三类重复场景无法被单层 UUID 去重覆盖：seq 有序重复（重连补发）、Bridge 回声、语义级重复（tool_use ID 碰撞）。

**参考**：Claude Code 在 SSE 传输中维护 `seenSequenceNums` 做 seq 去重，在 REPL Bridge 中维护 `recentPostedUUIDs` / `recentInboundUUIDs` 两个环形缓冲区做双向回声抑制，在 `messages.ts` 中做 tool_use/tool_result ID 去重防止 Session 损坏。

**决策**：三层去重——L1 seq（传输层）、L2 UUID 环形缓冲（应用层）、L3 语义去重（业务层）。

**理由**：
- seq 去重是 O(1) 且零内存开销，处理最常见的有序重复
- UUID 环形缓冲处理乱序和合并事件的重复，固定内存（2048 × ~36 bytes ≈ 72KB）
- 语义去重是最后一道防线，防止业务逻辑级别的不一致

**权衡**：
- 三层去重增加了处理复杂度，但每层都是 O(1) 操作，性能影响可忽略
- 环形缓冲有容量限制，极端情况下旧 UUID 被淘汰后可能漏检（但此时 seq 去重会兜底）

## ADR-020: Flush Gate 而非全局锁

**背景**：`chat.abort` 等操作需要确保之前的所有事件已被处理。方案 A: 全局写入锁（暂停所有写入直到确认）；方案 B: Flush Gate（等待 in-flight 操作完成后再执行）。

**参考**：Claude Code 的 REPL Bridge 使用 `FlushGate` 模式——在 flush 后 drain，确保所有缓冲事件已写入后再执行后续操作。

**决策**：方案 B。Flush Gate 是非阻塞的等待模式，不暂停新事件的产生。

**理由**：
- 全局锁会阻塞 Agent 的事件产生，增加端到端延迟
- Flush Gate 只等待已有的 in-flight 操作，新事件可以继续排队
- 与串行写入（§8.6 L2）天然配合——等待当前 write 完成即可

**权衡**：
- Flush Gate 不保证"flush 之后没有新事件"——如果 Agent 在 flush 期间继续产生事件，这些事件会在 abort 之后到达
- 解决方案：abort 时同时通知 Agent 停止产生事件（§5.8 的 abort 机制已覆盖）

## ADR-021: 分层熔断而非全局重试策略

**背景**：需要在传输层重试和应用层重试之间建立清晰边界。方案 A: 统一的全局重试策略；方案 B: 传输层和应用层各自独立的重试/熔断机制。

**参考**：Claude Code 明确分离了传输层重试（`withRetry` 处理 HTTP 5xx 和连接超时）和应用层熔断（`autoCompact` 的 circuit breaker、MCP 的 `MAX_ERRORS_BEFORE_RECONNECT`、权限系统的 `auto_mode_config` circuit breaker）。

**决策**：方案 B。传输层只处理网络瞬态故障，应用层按功能域独立熔断。

**理由**：
- 全局策略无法区分"网络抖动"和"服务端 bug"——前者应重试，后者应熔断
- 功能域独立熔断避免一个功能的故障影响其他功能（如 MCP 连接失败不应影响对话）
- 传输层重试 × 应用层重试的乘积效应是重试风暴的根源，分层后可独立控制

**权衡**：
- 每个功能域需要独立配置熔断参数，增加了配置复杂度
- 需要明确定义哪些错误属于传输层、哪些属于应用层（边界有时模糊）

---

# 附录 D — Future Work

以下是已识别但尚未纳入当前版本的前瞻性方向。这些方向将在后续版本中根据实际需求优先级逐步推进。

| 方向 | 说明 | 预期版本 |
|------|------|---------|
| **多模态支持** | 语音流（WebRTC / Opus）、视频流、屏幕共享等实时多媒体通道。需要定义二进制帧类型和媒体协商机制 | v3.0+ |
| **二进制帧** | 当前所有事件为 JSON 文本。大型 `tool_result`（图片、文件）的 Base64 编码效率低。需要定义 Binary Frame 类型和分片传输协议 | v2.4+ |
| **A2UI 孤立组件容错** | A2UI 组件树中的循环引用、孤立节点（无父组件）的检测和自动修复策略 | v2.3 |
| **大 Shared State 增量压缩** | 当 Shared State 超过 100KB 时，JSON Patch 的效率下降。需要评估二进制差分（如 BSON diff）或分片 State 方案 | v2.4+ |
| **HTTP Long Polling 完整定义** | §8.10.3 提到 Long Polling 作为最终降级传输，但未定义完整的轮询协议（间隔、批量、超时） | v2.3 |
| **合规性测试套件** | 提供 golden fixtures（标准事件序列 JSON 文件）和自动化测试框架，用于验证 Client/Server 实现的协议合规性 | v2.3 |
| **运维指南** | SLO 定义（事件延迟 P99 < 200ms、重连成功率 > 99.5%）、告警规则、容量规划公式 | 独立文档 |
| **Unicode / Grapheme Cluster** | `content_delta(text)` 的文本分割是否保证 grapheme cluster 完整性（当前未定义） | v2.3 |
| **时区规范** | `timestamp` 字段当前要求 ISO 8601，但未明确时区处理（Server 时区 vs UTC）。建议强制 UTC | v2.3 |
| **事件签名标准化** | §9.8 的 HMAC 签名方案需要更完整的密钥管理、轮换和分发机制 | v2.4+ |
