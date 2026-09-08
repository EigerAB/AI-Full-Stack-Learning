# Agent 框架优先型学习路线

> 路线定位：先借助成熟框架快速构建完整 Agent 应用，再通过改写、替换和源码追踪反推 Runtime 原理。目标仍然是平台开发能力，不是记忆框架 API。

## 一、路线策略

### 1. 主框架只选一个

框架优先不等于框架越多越好。课程采用以下角色分工：

- **主框架：LangGraph**——用于深入 State、Graph、Checkpoint、Interrupt、Streaming 和多 Agent 编排；
- **Agent Loop 对照：OpenAI Agents SDK**——用于理解 Runner、Tool、Handoff、Guardrail、Session 和 Trace 等更偏 Agent 的抽象；
- **RAG 对照：LlamaIndex**——只在数据接入、索引、Retriever 和 Agentic RAG 模块使用；
- **多 Agent 架构研讨：AutoGen**——官方项目已经进入 maintenance mode，因此只研究消息驱动、Actor Runtime 和分层设计，不作为新项目技术选型；
- **应用编排观察：CrewAI**——了解 Crew / Flow 的高层开发体验，不作为底层架构学习主线；
- **生产项目对照：Zeno Agent Platform**——观察通用框架之外的平台约束和自研设计。

框架角色可随生态变化调整，但课程中的 State、Event、Tool、Checkpoint、Handoff、Trace 等问题保持不变。

### 2. 每次使用框架都要完成四步

```text
先用框架完成能力
      ↓
画出框架隐藏的控制流
      ↓
阅读关键源码或接口定义
      ↓
脱离框架重写最小版本
```

如果只能写出调用代码，却不能回答状态存在哪里、循环由谁驱动、失败如何传播、进程重启后如何恢复，则该模块尚未学完。

### 3. 统一项目

课程持续演进一个 **Engineering Copilot**：能够检索项目知识、调用代码工具、执行审批工作流、委派子任务，并通过 FastAPI + Web 前端展示全过程。

---

## 00｜框架生态与选型基线

### 课程目录

- 00.1 Agent SDK、Graph Runtime、Workflow Engine 和 Platform 的区别
- 00.2 用统一维度评价一个框架
  - Agent / Runner 抽象
  - State / Context 模型
  - Tool System
  - Control Flow
  - Persistence
  - Streaming / Events
  - Human-in-the-loop
  - Multi-Agent
  - Trace / Eval
  - 扩展与供应商绑定
- 00.3 主框架与对照框架的分工
- 00.4 建立框架能力矩阵
- 00.5 锁定依赖与记录版本
- 00.6 建立不依赖框架的业务接口

### 实践与验收

- 创建框架能力矩阵，而不是功能勾选表；
- 记录选择 LangGraph 作为主线的理由和退出条件；
- 定义业务层 `ModelPort`、`ToolPort` 和 `RunRepository`，避免业务代码直接依赖框架对象。

---

## 01｜第一个框架 Agent

### 课程目录

- 01.1 安装、配置与最小模型调用
- 01.2 Message 与 State 定义
- 01.3 单节点 Graph
- 01.4 Tool 定义与 Tool Calling
- 01.5 Agent Loop 的预构建接口
- 01.6 Structured Output
- 01.7 同步、异步和流式调用
- 01.8 错误处理与最小测试

### 实践与验收

- 用主框架完成一个带两个只读工具的 Agent；
- 使用 Fake / Stub Model 测试，不让单元测试依赖在线模型；
- 画出“用户输入 → 模型 → 工具 → 模型 → 最终输出”的真实控制流；
- 找到框架中负责循环和终止判断的实现位置。

---

## 02｜Tool、Middleware 与 Guardrail

### 课程目录

- 02.1 Tool Schema 的生成与校验
- 02.2 Tool Node / Executor
- 02.3 Tool 错误如何返回模型
- 02.4 并行调用与副作用
- 02.5 Tool Middleware / Hook
- 02.6 动态工具选择
- 02.7 权限、审批和高风险工具
- 02.8 输入、输出和 Tool Guardrail
- 02.9 MCP Tool 接入
- 02.10 框架 Tool 与领域服务的隔离

### 框架对照

- 主框架：研究 Tool Node、Middleware 与动态工具；
- OpenAI Agents SDK：研究 Function Tool、Guardrail 和 Tool Approval；
- Zeno：对照 ToolExecutor、ProviderManager、确认门与 Observation Contract。

### 实践与验收

- 接入查询代码、读取文档和创建变更建议三个工具；
- 写操作必须经过审批，拒绝后 Run 可继续或正确结束；
- 替换框架 Tool 包装层时，底层业务函数和测试不变。

---

## 03｜StateGraph 与确定性控制流

### 课程目录

- 03.1 State Schema 与 Reducer
- 03.2 Node、Edge 和 Conditional Edge
- 03.3 Router 与显式分支
- 03.4 Loop 与终止条件
- 03.5 Parallel Branch 与结果聚合
- 03.6 Subgraph
- 03.7 Command 与状态更新
- 03.8 Agent 节点与普通函数节点的边界
- 03.9 Graph 编译与运行配置
- 03.10 从 Graph 反推状态机

### 实践与验收

- 建立“意图识别 → 检索/代码分析 → 质量检查 → 回复”的 Graph；
- 为每条边编写可重复的路由测试；
- 不依靠 LLM 决定本可由确定性规则决定的流程；
- 手写一个极简 StateGraph Executor，理解框架为你隐藏了什么。

---

## 04｜Checkpoint、持久化与 Durable Execution

### 课程目录

- 04.1 Thread / Run / Checkpoint 的关系
- 04.2 Checkpointer 接口
- 04.3 内存持久化与数据库持久化
- 04.4 State Snapshot 与恢复点
- 04.5 Interrupt 与 Resume
- 04.6 Time Travel 与 Replay
- 04.7 幂等和副作用隔离
- 04.8 进程重启后的恢复
- 04.9 Checkpoint Schema 演进
- 04.10 持久化不等于可靠执行

### 实践与验收

- 在写工具之前暂停并等待人工批准；
- 杀掉 FastAPI 进程后重新启动并恢复 Run；
- 证明恢复不会重复执行已经成功的外部副作用；
- 对比框架 Checkpoint 与 Zeno 的会话、事件和运行状态。

---

## 05｜Streaming、事件与 FastAPI 集成

### 课程目录

- 05.1 框架的 Stream Mode 与事件类型
- 05.2 Token、Message、State Update 和 Custom Event
- 05.3 框架内部事件到领域事件的转换
- 05.4 FastAPI 后台任务
- 05.5 SSE 输出与事件信封
- 05.6 Sequence ID 与断线重连
- 05.7 Cancellation 传播
- 05.8 背压与慢客户端
- 05.9 前端状态归并
- 05.10 两阶段提交与订阅

### 实践与验收

- 使用 FastAPI 暴露 Run 创建、状态查询、取消和 SSE 订阅接口；
- 前端展示 Agent、Model 和 Tool 的分层事件；
- 浏览器断开并重连后不丢失、不重复展示事件；
- 对照 Zeno 两阶段 Chat 和统一 SSE wire。

---

## 06｜Memory 与 RAG 框架集成

### 课程目录

- 06.1 Checkpoint Memory 与长期 Memory 的区别
- 06.2 Store 与跨 Thread 状态
- 06.3 Memory Extraction 和更新策略
- 06.4 文档解析、Chunk、Index 和 Retriever
- 06.5 Retriever Tool 与自动上下文注入
- 06.6 Query Rewrite、Hybrid Search 与 Rerank
- 06.7 权限过滤和引用
- 06.8 Agentic RAG
- 06.9 LlamaIndex 数据抽象对照
- 06.10 抽离框架特有的 Document / Node 类型

### 实践与验收

- Engineering Copilot 能检索项目规范并提供引用；
- 实现跨会话偏好记忆及显式删除；
- 对检索和最终回答分别评测；
- 更换 Retriever 后 Agent Graph 不发生结构性修改。

---

## 07｜Multi-Agent 框架模式

### 课程目录

- 07.1 单 Agent 扩展边界
- 07.2 Router 模式
- 07.3 Supervisor + Worker
- 07.4 Agent-as-Tool
- 07.5 Handoff
- 07.6 多 Agent Graph 与 Subgraph
- 07.7 上下文传递和隔离
- 07.8 并发、汇总与失败传播
- 07.9 AutoGen 消息驱动 Runtime 对照
- 07.10 Crew / Team 高层抽象对照
- 07.11 预算、递归和循环保护
- 07.12 Multi-Agent Evaluation

### 实践与验收

- 分别用 Supervisor 和 Handoff 实现代码审查协作；
- 与单 Agent 基线比较质量、延迟和费用；
- 用测试证明子 Agent 看不到不应继承的上下文；
- 对照 Zeno SubAgent / Team Runtime 的生命周期和消息协议。

---

## 08｜OpenAI Agents SDK 对照专题

> 该模块不是切换主框架，而是用更轻量、代码优先的 Runtime 对照 Graph 思维。

### 课程目录

- 08.1 Agent、Runner 与 Run Loop
- 08.2 Function Tool 与 Hosted Tool
- 08.3 Agent-as-Tool 与 Handoff
- 08.4 Input / Output / Tool Guardrail
- 08.5 Session 与运行状态
- 08.6 Streaming Event
- 08.7 Human-in-the-loop 与恢复
- 08.8 Trace、Span 与自定义 Processor
- 08.9 Model Provider 抽象
- 08.10 Graph-first 与 Code-first 的取舍

### 实践与验收

- 用 OpenAI Agents SDK 重写课程中的一个多 Agent 场景；
- 对比它与主框架在状态、控制流、恢复和可观测性上的差异；
- 形成一份“业务类型 → Runtime 选择”的决策记录。

---

## 09｜开放协议与框架边界

> 协议基线以 2026 年现行正式规范为准：MCP 使用 2026-07-28 无状态核心模型，A2A 使用 v1；旧教程中的 MCP 初始化握手、长会话和早期 A2A v0.x 只作为迁移材料。

### 课程目录

- 09.1 MCP：Agent 与外部能力的连接边界
- 09.2 MCP Host、Client、Server 与 Tool / Resource / Prompt
- 09.3 MCP 无状态、自包含请求与逐请求能力声明
- 09.4 MCP 授权、用户同意和 Tool Output 安全
- 09.5 A2A：Agent Card、Message、Task 和 Artifact
- 09.6 A2A 长任务、流式状态和异步通知
- 09.7 AG-UI：Run、Message、Tool、State 与 HITL 事件
- 09.8 协议对象与框架内部对象的转换层
- 09.9 Capability Discovery、版本协商和降级
- 09.10 Vendor Extension 与互操作风险
- 09.11 MCP、A2A、AG-UI 的组合边界
- 09.12 什么时候不需要开放协议

### 实践与验收

- 将一个内部工具发布成 MCP Server；
- 编写旧式 MCP session client 到无状态请求模型的迁移测试；
- 编写框架适配器，而不是让业务层直接使用协议对象；
- 用 A2A v1 为远程 Agent 设计 Agent Card、任务状态和 Artifact 交换模型；
- 把框架内部事件映射为可由前端消费的 AG-UI 事件；
- 能指出协议解决的是系统边界问题，而不是自动解决 Agent 质量问题。

---

## 10｜Tracing、Testing 与 Evaluation

### 课程目录

- 10.1 框架 Trace 与业务 Trace
- 10.2 Run、Node、Model 和 Tool Span
- 10.3 Fake Model、Fake Tool 与确定性测试
- 10.4 Graph 路由和状态转换测试
- 10.5 Trajectory Evaluation
- 10.6 Tool Selection Evaluation
- 10.7 Final Answer 与 RAG Evaluation
- 10.8 Dataset、Experiment 和 Baseline
- 10.9 Model-based Grader 的校准
- 10.10 Regression Gate
- 10.11 线上失败案例回流
- 10.12 框架版本升级回归

### 实践与验收

- 记录贯穿项目的完整执行 Trace；
- 建立不调用真实模型的 Runtime 测试集；
- 建立离线评测集与升级前后的对比报告；
- 发生错误时能从前端 Run ID 追踪到具体 Node、Model Call 或 Tool Call。

---

## 11｜框架生产化与平台封装

### 课程目录

- 11.1 为什么不能直接把框架对象当平台领域模型
- 11.2 Agent Definition 与编译产物
- 11.3 Agent Version、Deployment 与 Run
- 11.4 Model / Tool / Prompt / Knowledge Registry
- 11.5 Framework Adapter 与 Anti-corruption Layer
- 11.6 FastAPI Control Plane
- 11.7 Runtime Worker 与任务队列
- 11.8 Checkpoint、Event 和 Artifact 存储
- 11.9 多租户、权限和配额
- 11.10 配置快照与可复现性
- 11.11 灰度、回滚和框架升级
- 11.12 成本与资源治理

### 实践与验收

- 平台 API 不暴露主框架内部类型；
- 一次历史 Run 能绑定明确的 Agent、模型、Prompt 和工具版本；
- 实现租户隔离、并发限制和费用预算；
- 设计主框架替换实验，列出真实迁移成本。

---

## 12｜源码深挖与去框架化

### 课程目录

- 12.1 从公开入口追踪一次完整 Run
- 12.2 Graph 编译与调度
- 12.3 Pregel / 消息传播类执行思想
- 12.4 Reducer 与并发状态更新
- 12.5 Tool Node 内部执行
- 12.6 Checkpoint 读写时机
- 12.7 Interrupt 的控制流实现
- 12.8 Stream Event 的产生和传播
- 12.9 Runner 的 Agent Loop 与终止语义
- 12.10 扩展点、抽象泄漏和性能边界
- 12.11 手写框架核心能力的最小版本
- 12.12 与 Zeno Runtime 的逐层对比

### 实践与验收

- 提交一份带入口、关键类和调用链的源码阅读报告；
- 不使用预构建 Agent API，直接用底层 Graph 实现一次循环；
- 手写最小 Graph / Runner 验证调度和状态传播；
- 能指出框架最值得复用和最应由平台自行掌控的部分。

---

## 13｜框架路线毕业项目

### 阶段一：可用应用

- 单 Agent + Tools；
- 知识检索与引用；
- FastAPI + SSE；
- 简单 Web 调试界面。

### 阶段二：长任务 Runtime

- StateGraph；
- Checkpoint；
- Interrupt / Resume；
- 幂等写工具；
- Multi-Agent 协作。

### 阶段三：平台化

- Agent Definition / Version / Deployment；
- Tool、Model、Prompt 和 Knowledge Registry；
- 多租户和权限；
- Trace、Eval 和回归基线；
- Worker、取消、重试和恢复。

### 阶段四：去框架验证

- 选择一个关键模块手写替代；
- 使用另一个 Runtime 重写同一场景；
- 业务服务与平台 API 保持不变；
- 输出框架选择 ADR 和迁移成本报告。

---

## 二、建议阶段

| 阶段 | 模块 | 结果 |
|---|---|---|
| A：快速闭环 | 00～02 | 做出一个可测试的 Tool Agent |
| B：状态与交互 | 03～05 | 掌握 Graph、Checkpoint、SSE 和人机协作 |
| C：知识与协作 | 06～09 | 接入 RAG、Memory、Multi-Agent 和开放协议 |
| D：生产工程 | 10～11 | 加入 Trace、Eval、多租户和平台封装 |
| E：理解内核 | 12 | 从源码理解框架并手写关键机制 |
| F：综合项目 | 13 | 完成可运行、可解释、可迁移的平台作品 |

## 三、框架学习防偏清单

每完成一个模块，都检查以下问题：

- 我能否脱离框架术语解释这项能力？
- 状态由谁持有，何时被修改，如何持久化？
- 控制流由代码、Graph 还是模型决定？
- 异常、结构化失败和业务拒绝如何传播？
- 中断、取消或进程崩溃后发生什么？
- 如何用 Fake Model 写确定性测试？
- 如何观察这一过程，而不是只看到最终文本？
- 如果明天更换框架，哪些代码应该保持不变？

有三个以上问题答不出来时，不继续增加框架功能，先回到 [架构优先型学习路线](./Agent架构与平台开发学习路线.md) 对应模块补基础。

## 四、课程维护策略

框架 API 和产品能力变化较快，课程应把“稳定知识”和“易变资料”分开：

- 本文只维护学习顺序、能力目标和验收标准；
- 具体版本、安装方式和 API 示例放到每节课的实验记录；
- 每进入新阶段时重新检查一次官方文档；
- 每季度更新一次 [框架生态研究](./framework-landscape-research.md)；
- 升级框架前必须运行离线评测与 Runtime 回归测试。
