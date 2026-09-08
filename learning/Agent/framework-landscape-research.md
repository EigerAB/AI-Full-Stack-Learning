# Python Agent 框架与开放协议版图（2026）

> 研究日期：2026-09-08  
> 目标：为“深入 Agent 框架与底层架构，并具备 Agent 平台开发能力”的课程选择主框架、对照框架和协议栈。  
> 证据范围：仅采用官方文档、官方仓库和正式规范。文中的“判断/建议”是基于这些一手材料做出的课程设计推论，不是项目方自述。

## 结论先行

建议采用“一条主线、两条对照、三层协议”的课程结构：

1. **主框架：LangGraph**。它把共享状态、节点、边、运行时、检查点、暂停/恢复和 replay/fork 都暴露为一等概念，最适合讲清楚 Agent 平台真正要解决的状态机、可靠执行和人工介入问题。官方也明确将其定位为长时间运行、有状态 Agent 的低层编排运行时，而非替你隐藏 prompt 或 Agent 架构的高层封装。[LangGraph 概览](https://docs.langchain.com/oss/python/langgraph/overview) · [Graph API](https://docs.langchain.com/oss/python/langgraph/graph-api)
2. **主要对照：OpenAI Agents SDK**。用它讲“极简 agent loop”以及 tools、handoffs、guardrails、sessions、tracing 如何形成完整但更轻的 SDK。它能让学生看到：同一个多 Agent 问题可以用 manager-as-tools 或 handoff 表达，而不必先建显式状态图。[Agents](https://openai.github.io/openai-agents-python/agents/) · [Agent orchestration](https://openai.github.io/openai-agents-python/multi_agent/)
3. **领域对照：LlamaIndex Workflows/Agents**。用它连接 Agent 与数据/RAG，讲事件驱动 workflow、context augmentation、agent-as-tool 和文档型 Agent；不建议把它当作通用分布式 Agent 平台的唯一主线。[LlamaIndex 框架介绍](https://github.com/run-llama/llama_index/blob/main/docs/src/content/docs/framework/index.md) · [多 Agent 模式](https://github.com/run-llama/llama_index/blob/main/docs/src/content/docs/framework/understanding/agent/multi_agent.md)
4. **AutoGen 降级为架构研讨材料**。它的 Core/AgentChat/Extensions 分层、Actor 模型、异步消息和分布式 runtime 很有教学价值；但截至本次研究，官方仓库已明确进入 maintenance mode，新用户被引导到 Microsoft Agent Framework，因此不适合作为面向新项目的课程主框架。[AutoGen 官方仓库](https://github.com/microsoft/autogen)
5. **CrewAI 作为短实验或产品化案例**。它适合快速展示 role/task/crew 与 Flow 混合编排，但这些高层组织隐喻会遮蔽部分运行时机制；部署、监控和扩缩容的一部分完整体验位于商业 AMP 平台，因此不宜承担“底层架构主线”。[CrewAI 官方介绍](https://docs.crewai.com/core-concepts/Agents) · [CrewAI AMP](https://docs.crewai.com/enterprise/introduction)
6. **MCP、A2A、AG-UI 都应是必修，而不是框架附录**：MCP 处理 Agent↔工具/数据，A2A 处理独立 Agent↔Agent，AG-UI 处理 Agent↔用户界面。三者共同补足平台的南向集成、横向协作和北向交互面。[MCP 2026-07-28 规范](https://modelcontextprotocol.io/specification/2026-07-28) · [A2A 最新规范](https://a2a-protocol.org/latest/specification/) · [AG-UI 介绍](https://github.com/ag-ui-protocol/ag-ui/blob/main/docs/introduction.mdx)

## 一、框架调查

### 1. LangGraph：推荐作为课程主框架

**核心抽象**

- `State` 是共享状态快照，`Node` 是读取状态并返回更新的同步/异步函数，`Edge` 决定下一节点；分支、循环、`Command` 跳转与 `Send` map-reduce 都建立在这套显式图模型上。[Graph API 概览](https://docs.langchain.com/oss/python/langgraph/graph-api) · [Graph API 使用指南](https://docs.langchain.com/oss/python/langgraph/use-graph-api)
- Graph API 与 Functional API 共用同一运行时。后者以 `@entrypoint` 和 `@task` 为核心，让普通 Python 控制流也获得持久化、流式输出和人工介入；前者更容易可视化和审计状态迁移。[Functional API](https://docs.langchain.com/oss/python/langgraph/functional-api)
- checkpointer 在每个执行步骤保存 checkpoint，并按 thread 组织状态，由此支撑会话记忆、fault tolerance、human-in-the-loop、time travel、replay 和 fork。[Persistence](https://docs.langchain.com/oss/python/langgraph/persistence) · [Time travel](https://docs.langchain.com/oss/python/langgraph/use-time-travel)
- `interrupt()` 把暂停/恢复变成运行时原语；subgraph 则提供模块组合与每次调用、每 thread 或无状态三种持久化边界。[Interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts) · [Subgraphs](https://docs.langchain.com/oss/python/langgraph/use-subgraphs)

**适合教学的模块**

1. 从 ReAct loop 手写到 `StateGraph`：状态 schema、reducer、节点、条件边、循环终止。
2. BSP/Pregel 式执行视角：superstep、并行节点、状态合并、失败边界；官方说明 LangGraph 受到 Pregel 和 Apache Beam 启发。[官方仓库](https://github.com/langchain-ai/langgraph) · [LangGraph runtime](https://docs.langchain.com/oss/python/langgraph/pregel)
3. 可靠执行：checkpoint、thread、pending writes、重试、幂等副作用、恢复。
4. 人机协作：审批、编辑状态、暂停、恢复、replay 与 fork。
5. 模块化：subgraph、多 Agent supervisor/agent-as-tool、短期与长期记忆边界。
6. 平台能力：streaming、trace/evaluation、持久化后端、服务化与多租户隔离。

**局限与风险**

- 它是有意保持低层的编排框架，不负责替开发者抽象 prompt 或决定 Agent 架构；学生必须自己设计状态、边和错误语义，入门代码量与认知负担高于高层 SDK。[LangGraph 概览](https://docs.langchain.com/oss/python/langgraph/overview)
- `interrupt()` 恢复时会从节点开头重新执行，而不是从 Python 调用行继续，因此 interrupt 前的副作用必须幂等，且 interrupt 的顺序不能随意变化。[Interrupts 的规则](https://docs.langchain.com/oss/python/langgraph/interrupts)
- Functional API 更贴近普通 Python，但不支持图可视化，状态粒度与 checkpoint 行为也不同于 Graph API；教学中不能把两者当成纯语法糖。[Functional API 对比](https://docs.langchain.com/oss/python/langgraph/functional-api)

**课程判断**：最能训练“平台开发能力”，因为可靠性和状态边界不会被框架隐藏。建议占实践课约 45%–55%。

### 2. OpenAI Agents SDK：推荐作为主要对照框架

**核心抽象**

- `Agent` = instructions + model + tools，并可选 handoffs、guardrails、structured output、hooks 和 MCP servers；`Runner` 驱动模型调用、工具调用与 Agent 切换的循环。[Agents](https://openai.github.io/openai-agents-python/agents/) · [Quickstart](https://openai.github.io/openai-agents-python/quickstart/)
- 多 Agent 主要有两种模式：manager 把 specialist 当作 tool 调用，或通过 handoff 让 specialist 接管当前对话；handoff 在模型眼中也是一种工具。[Agent orchestration](https://openai.github.io/openai-agents-python/multi_agent/) · [Handoffs](https://openai.github.io/openai-agents-python/handoffs/)
- Sessions 管理跨 run 历史；guardrails 可约束输入、输出和工具；tracing 默认记录 LLM generation、tool call、handoff、guardrail 与自定义事件。[Sessions](https://openai.github.io/openai-agents-python/sessions/) · [Guardrails](https://openai.github.io/openai-agents-python/guardrails/) · [Tracing](https://openai.github.io/openai-agents-python/tracing/)
- SDK 还覆盖 streaming、MCP、human-in-the-loop、Realtime/Voice 与确定性测试工具，适合展示从文本 Agent 到实时 Agent 的连续产品面。[Running agents](https://openai.github.io/openai-agents-python/running_agents/) · [Testing](https://openai.github.io/openai-agents-python/testing/)

**适合教学的模块**

1. 最小 agent loop：model → tool → result → model，以及 max turns、错误与取消。
2. manager-as-tools vs handoff：控制权、上下文、最终回答归属和 guardrail 边界。
3. typed tool、structured output、input/output/tool guardrails。
4. session/context 的区别，以及业务依赖不应塞入模型历史。
5. tracing/span、评测与确定性 fake model 测试。
6. MCP 接入与 Realtime/Voice 作为选修拓展。

**局限与风险**

- 官方明确指出：若希望完全拥有 agent loop，应直接使用 Responses API；Agents SDK 的价值正是替你管理 turns、tools、handoffs 和 sessions。因此它不如 LangGraph 适合暴露任意图拓扑、checkpoint superstep 和显式状态机的底层机制。[Agents](https://openai.github.io/openai-agents-python/agents/)
- 默认模型路径是 OpenAI Responses API，虽支持自定义 model/provider 和非 OpenAI tracing，但课程若追求供应商中立，需要刻意增加 adapter 与契约测试。[Models](https://openai.github.io/openai-agents-python/models/) · [Tracing](https://openai.github.io/openai-agents-python/tracing/)
- handoff 只发生在一次 run 内；输入 guardrail 默认只作用于链中第一个 Agent，输出 guardrail作用于最终输出 Agent。复杂工作流不能误以为每个 hop 都自动获得相同安全边界。[Handoffs](https://openai.github.io/openai-agents-python/handoffs/)
- 内建 tracing 在 Zero Data Retention 组织策略下不可用，生产课应同时教授可替换 exporter/processor 与敏感数据治理。[Tracing](https://openai.github.io/openai-agents-python/tracing/)

**课程判断**：它是“agent-loop-first”路线的最佳对照，建议占 15%–20%；让学生在完成同一作业后与 LangGraph 比较代码量、控制权、可恢复性和可观测性。

### 3. AutoGen：保留其架构价值，但不作为新课程主框架

**2026 状态**

官方仓库已标记 **maintenance mode**：不再接收新特性或增强，转为社区维护；新用户被明确建议使用 Microsoft Agent Framework，现有用户迁移。这个事实应直接改变课程选型，而不能只依据 AutoGen 过去的知名度。[AutoGen 官方仓库 README](https://github.com/microsoft/autogen/blob/main/README.md)

**核心抽象**

- 分层为 Core、AgentChat、Extensions：Core 提供事件驱动的 Actor/消息运行时；AgentChat 提供 `AssistantAgent`、Team、termination 等任务/对话抽象；Extensions 放置模型 client、code executor、runtime、memory 等具体集成。[官方仓库架构说明](https://github.com/microsoft/autogen)
- Core 中 Agent 由 `AgentId` 标识，runtime 管理 Agent 生命周期与消息交付；消息是 Pydantic model 或 dataclass 纯数据，通过 direct message 或 publish/subscribe 通信。[Agent and Runtime](https://microsoft.github.io/autogen/stable/user-guide/core-user-guide/framework/agent-and-agent-runtime.html) · [Message and Communication](https://microsoft.github.io/autogen/stable/user-guide/core-user-guide/framework/message-and-communication.html)
- 单进程 `SingleThreadedAgentRuntime` 与跨进程/机器的分布式 runtime 共享 Agent 编程模型；后者由 host service 与 worker runtime 组成。[Runtime architecture](https://microsoft.github.io/autogen/dev/user-guide/core-user-guide/core-concepts/architecture.html)

**适合教学的模块**

1. Actor 模型、Agent identity/lifecycle、typed message、direct vs pub/sub。
2. 业务逻辑与消息传输解耦；单进程 runtime 到分布式 runtime 的演进。
3. AgentChat 的 teams、group chat、termination 与 human-in-the-loop。
4. 沙箱化 code executor 与扩展层边界。
5. 迁移案例：从 AutoGen 抽象映射到后继框架，训练平台演进思维。

**局限与风险**

- 最大风险不是 API 小缺点，而是项目生命周期：维护模式意味着新课程若将它作为主线，会把学生带到官方已不推荐的新项目路径。[AutoGen 官方仓库](https://github.com/microsoft/autogen)
- Core 官方自己提示其 API “unopinionated and flexible”，因此也更具挑战；仅想快速落地应使用 AgentChat。[Core Agent and Runtime](https://microsoft.github.io/autogen/stable/user-guide/core-user-guide/framework/agent-and-agent-runtime.html)
- 分布式 Agent runtime 在官方文档中仍标为 experimental，并明确提示可能有 breaking changes。[Distributed Agent Runtime](https://microsoft.github.io/autogen/stable/user-guide/core-user-guide/framework/distributed-agent-runtime.html)

**课程判断**：压缩为 1–2 个架构专题，重点读 Core，而非要求学生围绕已进入维护模式的生态做大项目；如课程需要微软生产路线，应另行评估官方后继 Microsoft Agent Framework 及其 [AutoGen 迁移指南](https://learn.microsoft.com/en-us/agent-framework/migration-guide/from-autogen/)。

### 4. LlamaIndex Workflows / Agents：数据型 Agent 的领域对照

**核心抽象**

- LlamaIndex 的总体定位是 context-augmented LLM application：data connector、index、retriever/query engine、tool、agent 与 workflow 共同把私有数据提供给模型。[框架介绍](https://github.com/run-llama/llama_index/blob/main/docs/src/content/docs/framework/index.md)
- Workflows 是 event-driven、async-first、step-based 的执行模型；`Workflow` 由带 `@step` 的步骤消费和产生 event，durability 可插拔，server 可把 workflow 暴露为支持 streaming、persistence 和 HITL 的 REST 服务。[Llama Agents/Workflows 官方仓库](https://github.com/run-llama/llama-agents)
- `AgentWorkflow` 是预配置的 Workflow，管理 Agent、state、tool calling 与 handoff；官方列出内建 AgentWorkflow、orchestrator-as-tools、自定义 planner 三种多 Agent 模式，灵活性逐级上升。[Multi-agent patterns](https://github.com/run-llama/llama_index/blob/main/docs/src/content/docs/framework/understanding/agent/multi_agent.md)
- `FunctionAgent`、`ReActAgent`、memory、tools 与 context/store 构成 Agent 层；底层仍可自行编写 Workflow 来表达反思、纠错或文档处理流程。[AgentWorkflow 源码](https://github.com/run-llama/llama_index/blob/main/llama-index-core/llama_index/core/agent/workflow/multi_agent_workflow.py)

**适合教学的模块**

1. context engineering：ingest → parse → index → retrieve/rerank → synthesis。
2. 把 query engine/RAG pipeline 暴露为 Agent tool，并进行 tool contract 与引用质量测试。
3. event/step/context/store、streaming event、HITL 和 workflow durability。
4. AgentWorkflow handoff 与 orchestrator-as-tools 的比较。
5. 文档 Agent 微服务化：workflow server/client、任务状态和长任务恢复。

**局限与风险**

- 它的最强差异化来自文档、检索和 context augmentation；若课程主要目标是通用运行时、跨语言分布式 Actor 或底层调度器，LlamaIndex 会把注意力带向数据层。这是由官方产品边界推导出的课程判断。[LlamaIndex 官方仓库](https://github.com/run-llama/llama_index)
- `AgentWorkflow` 以低代码 handoff 换取默认策略；官方也建议在需要更多控制时升级为 orchestrator 或自定义 planner，因此不能只教 `AgentWorkflow` 后就宣称掌握了多 Agent 编排。[Multi-agent patterns](https://github.com/run-llama/llama_index/blob/main/docs/src/content/docs/framework/understanding/agent/multi_agent.md)
- 当前生态同时出现 `llama_index` 中的 agent workflow 与独立 `llama-agents`/`llama-index-workflows` 包；课程必须 pin 版本和 import path，避免文档/包结构演进造成漂移。[LlamaIndex 仓库](https://github.com/run-llama/llama_index) · [Llama Agents 仓库](https://github.com/run-llama/llama-agents)

**课程判断**：作为数据/RAG 专题占 10%–15%，比作为通用主框架更合适。

### 5. CrewAI：快速协作实验与业务 Flow 案例

**核心抽象**

- Crews 以 `Agent`、`Task`、`Crew`、`Process` 表达角色化协作；Agent 带 role、goal、tools、memory/knowledge 等配置，Task 定义目标、预期输出和依赖。[CrewAI 介绍](https://docs.crewai.com/core-concepts/Agents) · [官方 README](https://github.com/crewAIInc/crewAI/blob/main/README.md)
- Flows 是更确定的事件驱动层，以 start/listen/router、state、branching 和 persistence/resume 编排普通 Python、LLM 调用与 Crew；官方明确把 Crews 定位为自治协作，把 Flows 定位为精确控制。[CrewAI 文档首页](https://docs.crewai.com/) · [CrewAI 介绍](https://docs.crewai.com/core-concepts/Agents)
- 工程模板常使用 `@CrewBase`、`@agent`、`@task`、`@crew` 等装饰器，并把 Agent/Task 配置放入 YAML。[Using Annotations](https://docs.crewai.com/learn/using-annotations)

**适合教学的模块**

1. role/task/process 的快速多 Agent 原型。
2. sequential/hierarchical 协作、delegation、structured output 与 human review。
3. Flow 中的 state、router、listener、persistence，以及 Crew 作为“自治岛”嵌入确定业务流程。
4. YAML 配置与 Python 扩展的工程权衡。

**局限与适用边界**

- role/backstory/crew 是生产力很高的领域隐喻，却不等价于底层 Agent runtime；若用它开设主线，学生容易会“配置团队”而不会设计 checkpoint、调度、消息协议和故障恢复。这是对其公开抽象层级的教学判断。[CrewAI 介绍](https://docs.crewai.com/core-concepts/Agents)
- 完整的托管部署、实时监控、扩缩容、RBAC/协作等平台能力由 CrewAI AMP 提供；开源框架能力与商业控制面的边界应在课程中显式说明。[CrewAI AMP](https://docs.crewai.com/enterprise/introduction)
- 自治 Crew 对开放式研究/内容生成友好；需要可预测、可审计的决策或 API 编排时，官方也建议使用 Flow。因此不要把“更多 Agent”误当作更可靠的架构。[Crews vs Flows](https://docs.crewai.com/core-concepts/Agents)

**课程判断**：用 1 个短实验比较高层 DSL 与显式状态图即可；不建议作为“深入底层架构”课程的主框架。

## 二、开放协议调查

### 1. MCP：Agent ↔ 工具与数据

**核心抽象与 2026 关键变化**

- MCP 仍采用 host–client–server 角色和 JSON-RPC 消息，server 向 client 提供 resources、prompts、tools；但 **2026-07-28 正式规范已将核心从双向有状态会话改为 stateless、self-contained request**，capability 改为逐请求携带。旧版 `initialize`/`initialized` handshake 和 `Mcp-Session-Id` 已退休，`server/discover` 只作为可选预发现调用。[2026-07-28 Specification](https://modelcontextprotocol.io/specification/2026-07-28) · [官方发布说明](https://blog.modelcontextprotocol.io/posts/2026-07-28/)
- 每个请求携带 protocol version、client identity 与 capabilities；Streamable HTTP 使用 `Mcp-Method`、`Mcp-Name` header 便于 gateway/WAF 路由和授权，list 响应获得 cache hint 与确定性顺序。[2026-07-28 发布说明](https://blog.modelcontextprotocol.io/posts/2026-07-28/)
- 旧版 server-to-client sampling/elicitation/roots 请求所需的长连接被 Multi Round-Trip Requests（MRTR）替代；Tasks 移入可选扩展，支持轮询、mid-flight input 和 durable handle。Roots、Sampling、Logging 以及 legacy HTTP+SSE 已进入至少 12 个月的弃用窗口，新实现不应继续采用。[2026-07-28 发布说明](https://blog.modelcontextprotocol.io/posts/2026-07-28/)
- Tool 仍以名称、描述和 schema 定义并通过 list/call 发现与调用；resources、prompts、tools 是 server primitives，elicitation 是当前 client feature，Tasks、Skills over MCP、MCP Apps 属于显式协商的扩展。[当前规范概览](https://modelcontextprotocol.io/specification/2026-07-28)

**适合教学的模块**

1. 从 function calling 到远程能力协议：schema、discovery、capability negotiation。
2. stdio 与 Streamable HTTP、stateless request、header routing、超时、取消、progress 和 cache。
3. tools、resources、prompts 的控制权差异；MRTR/elicitation 与 Tasks 扩展。
4. auth、consent、least privilege、tool output validation、审计和 prompt-injection/data-exfiltration 威胁建模。
5. 编写最小 MCP server/client，并接入两个不同 Agent 框架验证互操作。

**局限**

- MCP 解决的是上下文/能力交换，不是多 Agent 的任务协作协议，也不规定 Agent 内部规划器、状态机或 UI。
- 工具可触发任意数据访问与代码执行，当前规范要求显式用户同意、清晰授权 UI、访问控制，并把 tool annotation 当作不可信数据；这些原则无法只靠线协议强制。[2026-07-28 Security and Trust](https://modelcontextprotocol.io/specification/2026-07-28)
- 2026-07-28 是破坏性大改，协议核心、SDK 与既有 host 的支持不会自动同步；课程应以新规范为主，同时安排旧 session 模型迁移实验，固定协议/SDK 版本并测试 capability 降级。[官方发布与 SDK 状态](https://blog.modelcontextprotocol.io/posts/2026-07-28/)

### 2. A2A：独立 Agent ↔ 独立 Agent

**核心抽象**

- A2A 面向彼此内部实现不透明、框架/语言/厂商不同的独立 Agent 系统；目标是能力发现、模态协商、任务管理和安全的信息交换，而不要求访问对方内部状态、记忆或工具。[A2A v1 最新规范](https://a2a-protocol.org/latest/specification/)
- `AgentCard` 发布 identity、endpoint、capabilities、security schemes 与 skills；客户端可通过标准 well-known URI 发现 card。[Agent discovery](https://github.com/a2aproject/A2A/blob/main/docs/topics/agent-discovery.md)
- `Message` 表示一次通信，复杂长任务使用带 lifecycle/state/history 的 `Task`，输出沉淀为 `Artifact`；交互支持同步、流式状态/Artifact 更新和异步 push notification，并可绑定 JSON-RPC、gRPC、HTTP+JSON/REST。[A2A v1 最新规范](https://a2a-protocol.org/latest/specification/)
- 项目由 Google 捐赠并托管于 Linux Foundation，提供官方 Python、JavaScript、Java、.NET、Rust SDK 和兼容性工具。[A2A 官方组织](https://github.com/a2aproject)

**适合教学的模块**

1. Agent Card 与 capability/skill discovery。
2. Message、Part、Task、TaskState、Artifact 与 context ID 的领域模型。
3. request/response、streaming、polling、push notification 的长任务语义。
4. 鉴权、租户隔离、任务所有权、幂等、取消与失败状态。
5. 用 LangGraph Agent 暴露 A2A server，再由另一框架的 client 调用。

**局限**

- A2A 不暴露远程 Agent 的内部工具、prompt 或 memory，这是互操作与封装边界，不是调试/可观测协议；平台仍需独立 trace、审计和策略层。[A2A v1 规范目标](https://a2a-protocol.org/latest/specification/)
- 它不是 MCP 的替代品：MCP 把能力作为工具/资源提供给 Agent，A2A 把另一自治系统作为协作方。两者应组合而非二选一。[A2A 官方文档导航](https://github.com/a2aproject/A2A/blob/main/mkdocs.yml)
- A2A 已进入 v1，并拥有多种正式 binding、版本协商和 migration/legacy compatibility 章节；这使它可以进入正式课程，但也意味着不能再以搜索结果常见的 v0.2/v0.3 示例为准。实现必须 pin v1 协议/SDK 并做 conformance tests。[A2A v1 最新规范](https://a2a-protocol.org/latest/specification/) · [官方 SDK 列表](https://a2a-protocol.org/latest/)

### 3. AG-UI：Agent ↔ 用户界面

**核心抽象**

- AG-UI 是 Agent backend 与 user-facing application 之间的开放、轻量、双向事件协议，旨在同步消息、Agent state、UI intent、tool interaction 与用户交互。[AG-UI Introduction](https://github.com/ag-ui-protocol/ag-ui/blob/main/docs/introduction.mdx)
- 基本单位是有序事件：run/step lifecycle、text message streaming、tool call/result、state snapshot/delta、activity、reasoning 以及 raw/custom extension；状态增量采用 RFC 6902 JSON Patch。[Events](https://github.com/ag-ui-protocol/ag-ui/blob/main/docs/concepts/events.mdx) · [SDK event types](https://github.com/ag-ui-protocol/ag-ui/blob/main/docs/sdk/js/core/events.mdx)
- 协议不绑定单一 transport，可运行在 SSE、WebSocket、webhook 等之上，并提供参考 HTTP 实现和中间件层。[AG-UI 官方仓库](https://github.com/ag-ui-protocol/ag-ui)

**适合教学的模块**

1. 把“token streaming”提升为 typed event stream：run、step、message、tool、state 生命周期。
2. frontend tool vs backend tool、HITL 审批、interrupt/resume 和 optimistic UI。
3. state snapshot + delta、重连、重复事件、顺序保证与前端 reducer。
4. generative UI 与通用 protocol event 的边界；custom/raw event 的可移植性成本。
5. 将 LangGraph 或 OpenAI Agents SDK 的内部事件映射为 AG-UI，再实现框架可替换的前端。

**局限**

- AG-UI 只标准化 Agent–用户交互语义，不替代后端编排、MCP 工具安全或 A2A 任务协作。[AG-UI 协议栈定位](https://github.com/ag-ui-protocol/ag-ui/blob/main/README.md)
- `RAW`/`CUSTOM` 提供扩展性，但大量依赖自定义事件会削弱跨客户端互操作；团队需要维护自己的事件契约。[Events](https://github.com/ag-ui-protocol/ag-ui/blob/main/docs/concepts/events.mdx)
- 官方事件文档仍包含 draft/deprecated 类型，表明协议处于活跃演进期；生产课程必须覆盖版本协商、兼容适配和事件降级，而不是把当前 enum 当作永恒 API。[Events](https://github.com/ag-ui-protocol/ag-ui/blob/main/docs/concepts/events.mdx)

## 三、用于课程选型的对比矩阵

| 技术 | 最值得教的底层能力 | 抽象层级 | 2026 课程角色 | 不应承担的角色 |
|---|---|---:|---|---|
| LangGraph | 状态图、checkpoint、可靠执行、HITL、replay/subgraph | 低—中 | **主线** | 快速隐藏所有编排细节的入门 DSL |
| OpenAI Agents SDK | agent loop、tool、handoff、guardrail、session、trace | 中 | **主要对照** | 任意分布式状态图运行时 |
| AutoGen | Actor、typed message、runtime、pub/sub、分层 API | 低—高 | 架构研讨/迁移案例 | 2026 新项目主线 |
| LlamaIndex | context augmentation、RAG tool、event workflow、文档 Agent | 中 | 数据型专题 | 通用分布式 runtime 唯一主线 |
| CrewAI | role/task/crew、业务 Flow、快速多 Agent 原型 | 高（Flow 较低） | 短实验/产品案例 | 深入运行时主线 |
| MCP | Agent↔工具/数据的发现、schema、session、安全 | 协议 | **必修协议** | Agent↔Agent 或 UI 协议 |
| A2A | Agent Card、Task lifecycle、Artifact、远程协作 | 协议 | **必修协议** | Agent 内部编排/工具协议 |
| AG-UI | typed UI event、state sync、tool/HITL UX | 协议 | **必修协议** | 后端编排或工具权限系统 |

## 四、建议课程路线

### 阶段 0：不用框架，先建立可迁移心智模型

- 手写最小 ReAct/tool loop；定义 `AgentRun`、`Message`、`ToolCall`、`ToolResult`、`RunState`。
- 加入 token budget、max turns、timeout/cancel、retry、幂等键与结构化错误。
- 用 fake model 与 fake tools 做确定性测试，先区分“模型错误、工具错误、编排错误、基础设施错误”。

### 阶段 1：LangGraph 主线——从状态机到可靠运行时

- State/Node/Edge/reducer → branch/loop/parallel → `Command`/`Send`。
- checkpoint/thread/store → crash recovery → replay/fork/time travel。
- interrupt/HITL → side-effect idempotency → subgraph/multi-agent。
- streaming/trace/evaluation → 持久化后端 → API 服务与多租户设计。

主项目建议：**可恢复的企业研究/审批 Agent**。它必须能在工具失败或进程重启后恢复、在高风险动作前暂停审批、回放/分叉历史，并输出完整 trace。

### 阶段 2：同题异构实现——OpenAI Agents SDK

- 用 Agent/Runner/tools 重写主项目的简化版。
- 分别实现 manager-as-tools 与 handoff，比较上下文所有权、guardrail 位置、状态持久化、trace 和测试。
- 把结论写成 ADR：何时选择显式 workflow runtime，何时选择轻量 agent loop SDK。

### 阶段 3：数据型 Agent——LlamaIndex

- 建立 ingestion/index/retrieval/rerank/evaluation pipeline。
- 把 RAG/query engine 封装为 tool，比较 AgentWorkflow、orchestrator 和 custom Workflow。
- 重点测“检索证据质量”，而不只测最终文本是否看起来合理。

### 阶段 4：架构对照——AutoGen 与 CrewAI

- AutoGen Core：实现 typed-message actor demo，再阅读 distributed runtime，讨论为什么该项目的思想仍有价值、但生命周期状态使其不再适合作为主线。
- CrewAI：用 Crew 完成角色协作，再用 Flow 包住确定性控制，比较高层 DSL 与显式状态图的透明度。

### 阶段 5：协议化平台 Capstone

构建一个可替换框架的 Agent 平台薄层：

- 南向：两个 MCP server（一个只读 resource，一个需要审批的写 tool）。
- 核心：LangGraph runtime；可切换一个 OpenAI Agents SDK 实现进行契约测试。
- 横向：通过 A2A 暴露 Agent Card 与长任务 Task/Artifact。
- 北向：通过 AG-UI 输出 run/message/tool/state/HITL 事件。
- 平台要求：租户隔离、secrets、policy/approval、event log、trace、eval、成本/用量、版本 pinning、replay 与灾难恢复。

## 五、最终选型原则

- 若目标是**理解并建设 Agent runtime/platform**：LangGraph 为主，协议必修。
- 若目标是**快速构建 OpenAI 模型驱动的产品 Agent**：OpenAI Agents SDK 优先，同时补可靠执行和供应商边界。
- 若目标是**文档/RAG/数据 Agent**：LlamaIndex 作为领域核心，但仍需外部平台与协议知识。
- 若目标是**快速角色化团队自动化**：CrewAI 可优先做原型，可靠业务流程应落到 Flow，并明确开源/商业控制面边界。
- 若目标是**学习消息驱动、多 Agent Actor 架构**：AutoGen Core 仍值得阅读；若是 2026 新生产项目，不应忽略其官方维护模式与后继迁移方向。

最重要的课程设计原则是：**框架 API 会变，运行时问题不会变**。学生最终应能脱离某个框架的名词，解释并实现状态、调度、工具契约、上下文、记忆、恢复、人工审批、可观测性、安全边界，以及 MCP/A2A/AG-UI 三个互操作表面。
