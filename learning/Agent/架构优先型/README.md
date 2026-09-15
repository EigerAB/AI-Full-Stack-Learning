# Agent 架构与平台开发学习路线

> 路线定位：以内核递进为主，从一个最小 Agent Loop 出发，逐步构建工具、上下文、记忆、工作流、多 Agent、评测和平台能力。Zeno Agent Platform 作为生产级源码对照，而不是知识体系本身。

## 一、能力地图

完整的 Agent 系统可以拆成五层：

| 层级 | 核心问题 | 主要模块 |
|---|---|---|
| Model | 模型如何接收输入、产生文本或行动？ | Model Client、Message、Tool Calling、Streaming |
| Context | 本轮应该把哪些信息交给模型？ | Prompt、History、RAG、Memory、Context Engine |
| Runtime | 谁驱动模型反复思考、行动并决定结束？ | Agent Loop、State、Budget、Suspend / Resume |
| Capability | Agent 可以安全地做什么？ | Tool、MCP、Sandbox、Workflow、Multi-Agent |
| Platform | 如何把 Runtime 作为可靠的多租户产品运行？ | API、Scheduler、Trace、Eval、Security、Governance |

课程顺序遵守依赖关系：先懂模型调用协议，再实现 Runtime；Tool 能稳定工作后再加入 Context、Memory 和 RAG；单 Agent 可靠后再学习 Workflow 和 Multi-Agent；最后进入平台化。

---

## 00｜Agent 工程导论与学习基线

### 课程材料

- [第 1 课｜Agent、Workflow 与 Agent Platform 的系统边界](./lessons/0001-agent-engineering-foundations.md)
- [Agent Runtime 学习领域词汇](./CONTEXT.md)

### 课程目录

- 00.1 Agent、Chatbot、Workflow、Copilot 的区别
- 00.2 Agent 的 Model、Tools、Instructions 三要素
- 00.3 从一次 Model Call 到一次 Agent Run
- 00.4 确定性程序与概率性系统的差异
- 00.5 Agent 系统五层架构
- 00.6 如何阅读 Agent 框架源码
- 00.7 如何设计可验证的学习实验
- 00.8 贯穿课程项目的需求与边界

### 实践与验收

- 画出一次 Run 的初始时序图；
- 定义 `Run`、`Turn`、`Step`、`Event`、`State` 等统一术语；
- 为贯穿项目建立最小 FastAPI 工程和测试骨架；
- 能解释“调用了 LLM”为什么不等于“实现了 Agent”。

---

## 01｜LLM 应用与模型接口基础

### 课程目录

- 01.1 Message、Role、Content Part 与对话历史
- 01.2 Token、Context Window 和输出限制
- 01.3 Sampling、Stop Reason 与非确定性
- 01.4 Structured Output 与 Schema 校验
- 01.5 Function / Tool Calling 消息协议
- 01.6 流式响应与增量事件
- 01.7 Token Usage、延迟和费用统计
- 01.8 Provider 差异与统一 `ModelClient`
- 01.9 Timeout、Retry、Rate Limit 和 Fallback
- 01.10 模型输出不可信原则

### 实践与验收

- 实现一个与具体供应商解耦的 `ModelClient` 协议；
- 使用 Fake Model 测试正常文本、工具调用、空响应、截断和异常；
- 能区分模型层失败、协议解析失败和 Runtime 失败。

---

## 02｜Agent Runtime 内核

> 第一核心模块。先手写，再对照框架和 Zeno AgentLoop。

### 课程目录

- 02.1 Agent 的最小定义与 ReAct 模型
- 02.2 Observe–Think–Act 循环
- 02.3 `ExecutionContext` 与 Runtime State
- 02.4 LLM 输出中的 Tool Call 提取
- 02.5 Tool Observation 回填消息历史
- 02.6 Final Output 与终止条件
- 02.7 最大轮次、Token、时间和费用预算
- 02.8 空响应检测、截断与续写
- 02.9 Run 状态机
  - Pending
  - Running
  - Suspended
  - Completed
  - Failed
  - Cancelled
- 02.10 同步、异步和流式运行模式
- 02.11 Cancellation、Timeout 与资源清理
- 02.12 Result 与 Exception 的失败语义

### 实践与验收

- 手写不依赖 Agent 框架的 Mini Runtime；
- 用 Fake Model 精确驱动每条状态转换；
- 测试 max turns、空输出、工具异常、取消和预算耗尽；
- 对照 Zeno `core/agent/execution/loop.py`，解释其终止 subtype 与错误判定。

---

## 03｜Tool System 与能力执行层

### 课程目录

- 03.1 Tool 的名称、描述和参数 Schema
- 03.2 Python 类型到 JSON Schema
- 03.3 Tool Registry、Resolver 与 Provider
- 03.4 参数解析、校验和错误反馈
- 03.5 Tool Result / Observation 统一信封
- 03.6 串行、并行和依赖执行
- 03.7 工具超时、重试与熔断
- 03.8 幂等、副作用和重复调用
- 03.9 权限策略与人工确认门
- 03.10 Critical Tool 与 Sibling Abort
- 03.11 沙箱、代码执行和文件系统隔离
- 03.12 MCP 的 Host、Client、Server 与 Tool
- 03.13 动态工具发现与延迟加载
- 03.14 Tool Hook / Middleware

### 实践与验收

- 实现支持注册、解析、校验和结构化失败的 Tool Runtime；
- 实现只读工具、写工具和高风险工具三种策略；
- 故意制造 Schema 错误、权限拒绝、超时和部分失败；
- 对照 Zeno ToolExecutor、ProviderManager 和 observation contract。

---

## 04｜Context Engineering

> 重点理解：Prompt 是指令，Context 是一次决策所能看到的完整信息。

### 课程目录

- 04.1 System Prompt、History、Tool Result 与 Runtime State
- 04.2 Context Builder 的输入与输出
- 04.3 Token Budget 分配策略
- 04.4 滑动窗口与消息裁剪
- 04.5 对话摘要与分层摘要
- 04.6 Tool Observation 压缩
- 04.7 文件、代码和多模态内容
- 04.8 静态 Prompt Cache 与动态 Context Cache
- 04.9 上下文污染、权限和租户隔离
- 04.10 Context Provenance 与可解释性
- 04.11 Long-context 不等于无需 Context Engineering
- 04.12 Context 质量的测试方法

### 实践与验收

- 实现独立于 Agent Loop 的 `ContextEngine`；
- 对同一任务比较全量历史、滑动窗口和摘要策略；
- 能定位“模型能力不足”和“上下文装配错误”的差异。

---

## 05｜Memory System

### 课程目录

- 05.1 Context、State、History 与 Memory 的边界
- 05.2 Working、Semantic、Episodic 与 Procedural Memory
- 05.3 Memory Extractor：从对话识别可记忆事实
- 05.4 写入时机、置信度与人工确认
- 05.5 Memory Store 与数据模型
- 05.6 检索、排序和上下文注入
- 05.7 去重、冲突合并与版本
- 05.8 衰减、遗忘和删除
- 05.9 用户级、Agent 级与组织级作用域
- 05.10 隐私、合规与多租户隔离
- 05.11 Memory 质量评测
- 05.12 对照 Zeno Agent Memory

### 实践与验收

- 实现事实抽取、存储、检索、更新和删除闭环；
- 测试互相冲突的用户事实与跨租户访问；
- 证明 Memory 改善了任务，而不只是增加了 Token。

---

## 06｜RAG 与知识系统

### 课程目录

- 06.1 RAG 在 Agent 中是 Context Source 还是 Tool
- 06.2 文档加载、解析与规范化
- 06.3 Chunking 策略和元数据
- 06.4 Embedding 的工程含义
- 06.5 Vector、Keyword 与 Hybrid Search
- 06.6 Query Rewrite 与多路召回
- 06.7 Metadata Filter 与权限过滤
- 06.8 Rerank
- 06.9 引用、证据和 Grounded Answer
- 06.10 Agentic RAG 与多跳检索
- 06.11 索引更新、删除和一致性
- 06.12 Retrieval 与 Generation 分阶段评测

### 实践与验收

- 建立一个带元数据和权限控制的知识库；
- 将 Retriever 同时实现为 Context Source 和 Tool，比较差异；
- 创建检索评测集并测量 Recall、Ranking 与回答忠实度。

---

## 07｜Workflow Engine 与确定性编排

> 第二核心模块。重点不是会画节点，而是理解执行图、状态和恢复语义。

### 课程目录

- 07.1 Agent 与 Workflow 的职责边界
- 07.2 DAG、State Machine 与 Event-driven Workflow
- 07.3 Workflow Definition、Run 与 Node Instance
- 07.4 DSL Schema 与静态校验
- 07.5 Node Registry 与 Executor
- 07.6 条件、分支、循环、并行和聚合
- 07.7 变量作用域、输入输出和数据绑定
- 07.8 Checkpoint 与 Durable Execution
- 07.9 Retry、Compensation 与恢复
- 07.10 Suspend / Resume 与人工节点
- 07.11 Workflow 中嵌入 Agent
- 07.12 Agent 选择或生成 Workflow
- 07.13 定义版本、迁移和运行兼容性
- 07.14 节点级调试和 Replay

### 实践与验收

- 实现轻量状态图执行器；
- 支持条件、并行节点、Checkpoint 和失败恢复；
- 对照 Zeno Workflow DSL 与节点执行器；
- 能解释为什么“重启后继续”不是简单地再次调用函数。

---

## 08｜Multi-Agent Runtime

### 课程目录

- 08.1 何时不该使用 Multi-Agent
- 08.2 Router、Supervisor、Handoff 与 Agents-as-Tools
- 08.3 SubAgent 生命周期
- 08.4 Task、Message、Artifact 与 Result
- 08.5 上下文继承、裁剪和隔离
- 08.6 并发调度与结果汇总
- 08.7 能力发现与动态任务分配
- 08.8 中央控制与去中心化协作
- 08.9 死循环、争用和级联失败
- 08.10 Token、时间和费用预算分配
- 08.11 Agent 身份、权限与信任边界
- 08.12 A2A 类协议的设计目标
- 08.13 对照 Zeno SubAgent / Team Runtime

### 实践与验收

- 实现 Supervisor + Worker 和 Handoff 两种模式；
- 比较单 Agent、Agent-as-Tool 与独立 Handoff 的效果；
- 测试子任务失败、并发取消、预算耗尽和递归委派。

---

## 09｜事件系统与 Human-in-the-loop

### 课程目录

- 09.1 Command、State、Event 与 Result
- 09.2 Event Envelope 与事件目录
- 09.3 Run / Turn / Model / Tool 事件层级
- 09.4 SSE、WebSocket 和轮询的取舍
- 09.5 Sequence ID、重连与事件重放
- 09.6 Approval、Clarification 和 User Input
- 09.7 Suspend、Checkpoint 与 Resume
- 09.8 两阶段任务提交协议
- 09.9 前后端状态一致性
- 09.10 乐观更新和重复事件处理
- 09.11 AG-UI 类协议的架构角色
- 09.12 Agent 调试台的状态模型

### 实践与验收

- 用 FastAPI 实现任务提交与 SSE 订阅；
- 支持断线重连、工具审批和恢复执行；
- 用前端展示 Run、Turn、Model Call 和 Tool Call；
- 对照 Zeno 两阶段 Chat 协议与统一 SSE wire。

---

## 10｜可靠性、可观测性与故障恢复

### 课程目录

- 10.1 Agent 系统的失败分类
- 10.2 Log、Metric、Trace 与 Domain Event
- 10.3 Trace、Span 与 Correlation ID
- 10.4 Prompt、Context、Model 和 Tool Span
- 10.5 Token、费用、延迟和吞吐指标
- 10.6 Timeout、Retry、Backoff 与 Circuit Breaker
- 10.7 幂等、去重和 At-least-once Delivery
- 10.8 Checkpoint、Replay 与故障复现
- 10.9 后台任务丢失与进程重启
- 10.10 资源泄漏、取消传播和僵尸 Run
- 10.11 告警、SLO 与容量规划
- 10.12 Agent 链路排障方法论

### 实践与验收

- 给每次 Run 生成完整 Trace；
- 注入模型限流、工具超时、数据库断开和进程重启；
- 通过 Trace 在限定时间内定位故障层；
- 能安全 Replay 一次失败 Run，而不重复副作用。

---

## 11｜Agent Evaluation

### 课程目录

- 11.1 为什么传统单元测试不够
- 11.2 确定性组件测试与 Fake Model
- 11.3 Dataset、Case、Run、Score 和 Grader
- 11.4 Final Answer Evaluation
- 11.5 Trajectory 与 Tool Selection Evaluation
- 11.6 Code-based Grader
- 11.7 Model-based Grader
- 11.8 Human Review 与标注一致性
- 11.9 Golden Dataset 与失败案例回流
- 11.10 非确定性、重复采样和置信区间
- 11.11 RAG、Memory 和 Multi-Agent 专项评测
- 11.12 Baseline、Regression 与 CI Gate
- 11.13 在线反馈和 A/B 实验

### 实践与验收

- 建立覆盖成功、边界和对抗输入的评测集；
- 同时实现规则型和模型型 Grader；
- 生成基线报告，并让关键回归阻断 CI；
- 能说明一个总分为什么不能替代分层指标。

---

## 12｜安全与治理

### 课程目录

- 12.1 Agent Threat Modeling
- 12.2 Direct / Indirect Prompt Injection
- 12.3 Tool 参数、Observation 与外部内容不可信
- 12.4 最小权限与 Capability-based Security
- 12.5 高风险动作确认与审批策略
- 12.6 Sandbox、网络和文件系统隔离
- 12.7 Secret 管理与日志脱敏
- 12.8 PII、数据保留与删除
- 12.9 多租户数据和运行隔离
- 12.10 Agent 身份与 Agent 间授权
- 12.11 审计日志和策略版本
- 12.12 Red Team 与安全评测

### 实践与验收

- 为贯穿项目完成 Threat Model；
- 实现输入、工具和输出三层 Guardrail；
- 验证间接注入不能越权调用高风险工具；
- 能从审计记录还原一次敏感操作的决策过程。

---

## 13｜Agent Platform 架构

### 课程目录

- 13.1 Control Plane 与 Runtime Data Plane
- 13.2 Agent Definition、Version、Deployment 与 Run
- 13.3 Model、Tool、Knowledge、Prompt 和 Skill 资产
- 13.4 配置快照与可复现运行
- 13.5 API Gateway 与任务提交
- 13.6 Scheduler、Queue、Worker 和 Runtime Pool
- 13.7 Event Store、State Store 与 Artifact Store
- 13.8 长任务、Checkpoint 和故障转移
- 13.9 多租户、配额与资源隔离
- 13.10 Provider 接入与凭据管理
- 13.11 发布、灰度、回滚和版本兼容
- 13.12 Trace、Eval 和运营反馈闭环
- 13.13 成本归因与预算治理
- 13.14 插件、Skill 与开放协议生态
- 13.15 高可用、水平扩展与容灾

### 实践与验收

- 完成控制面和运行面的架构设计；
- 设计核心实体、API、事件和存储边界；
- 对一次 Run 完成容量、可靠性和成本分析；
- 对照 Zeno 平台解释其 Service、Core、Infra 和 HTTP 层的边界。

---

## 14｜框架源码与架构对比

### 课程目录

- 14.1 如何选择代表性框架，而不是追逐框架数量
- 14.2 Agent / Runner / Graph 的入口抽象
- 14.3 Loop、Node 与状态转换
- 14.4 Message、State 和 Context 模型
- 14.5 Tool、Middleware 和 Hook
- 14.6 Checkpoint 与 Durable Execution
- 14.7 Handoff、Supervisor 与 Agents-as-Tools
- 14.8 Streaming、Event 与 Human-in-the-loop
- 14.9 Trace、Eval 与扩展接口
- 14.10 框架抽象泄漏和退出成本
- 14.11 从源码反推适用场景
- 14.12 Zeno 与通用框架的取舍对比

### 实践与验收

- 选择一个主框架和一个对照框架；
- 用两者实现相同业务，并制作架构对比矩阵；
- 在源码中追踪一次完整 Run；
- 替换框架而不改业务 Tool 和领域模型，检验边界是否合理。

---

## 15｜毕业项目：Engineering Copilot Platform

### M1 — Mini Agent Runtime

- 统一 Model Client；
- Agent Loop；
- Tool Registry 与 Tool Executor；
- 结构化结果和流式事件；
- 预算、取消与失败语义；
- Fake Model 驱动的完整测试。

### M2 — Context 与知识能力

- Context Engine；
- 对话摘要；
- Memory；
- RAG 与引用；
- Context / Retrieval Evaluation。

### M3 — Orchestration

- Workflow DSL；
- Checkpoint；
- Suspend / Resume；
- Supervisor + Worker；
- Human Approval；
- SSE 调试事件。

### M4 — Platform

- Agent Definition 与版本；
- FastAPI 控制面与运行接口；
- PostgreSQL 状态、事件和配置存储；
- 多租户和权限；
- Trace 与 Replay；
- Eval Pipeline；
- 前端 Agent 调试台；
- 安全策略、审计和部署说明。

### 最终答辩标准

- 能现场画出系统边界和一次 Run 的时序；
- 能解释关键抽象的替代方案与取舍；
- 能注入故障并依靠 Trace 定位；
- 能证明 Checkpoint 后恢复不会重复危险副作用；
- 能使用评测数据说明修改是否改善系统；
- 能指出哪些能力属于应用、Runtime 或 Platform，而不是把所有逻辑堆进 Agent Loop。

---

## 二、建议阶段与顺序

| 阶段 | 模块 | 阶段结果 |
|---|---|---|
| A：内核基础 | 00～03 | 能独立实现 Agent Loop 和 Tool Runtime |
| B：智能与状态 | 04～06 | 能管理 Context、Memory 和 RAG |
| C：复杂编排 | 07～09 | 能实现 Workflow、Multi-Agent 和人机协作 |
| D：生产工程 | 10～12 | 系统可观测、可评测、可恢复且安全 |
| E：平台与源码 | 13～14 | 能设计平台并评价框架取舍 |
| F：综合项目 | 15 | 形成可以展示和持续演进的平台作品 |

不要按日历硬性推进。每个阶段只有在实践与验收项通过后才进入下一阶段。

## 三、现有 Zeno 课程迁移

| 原课程 | 新模块 | 使用方式 |
|---|---|---|
| 0001 两阶段对话协议 | 09 | 作为长任务提交、SSE 重连案例 |
| 0002 Chat Service Flow | 13 | 作为 HTTP → Service → Core 的平台链路案例 |
| 0003 Agent Loop | 02、10 | 对照循环、失败 subtype 和空回复守卫 |
| 0004 Tool Execution Layer | 03、12 | 对照 Provider、Observation、确认门和失败判定 |
| 0005 Workflow DSL | 07 | 对照 DSL、节点注册和执行语义 |
| 0006 SubAgent Team Runtime | 08 | 对照子 Agent、Team 和协作协议 |

后续 Zeno 专题建议按新体系补充：Context Engine、Memory、RAG、事件目录、Eval、Trace、Sandbox 与多租户隔离。它们不再按调用链编号，而是挂到对应能力模块下面。

## 四、暂缓学习的内容

以下内容不是无用，而是不应在基础架构建立前抢占注意力：

- 复杂 Prompt 技巧合集；
- 同时学习多个框架的 API；
- 一开始就做自治 Agent Team；
- 在没有评测集时做模型微调；
- 为简单 Demo 引入 Kafka、Kubernetes 或复杂向量数据库；
- 只会搭建低代码工作流但无法解释运行语义；
- 只根据最终回答判断 Agent 是否正常。
