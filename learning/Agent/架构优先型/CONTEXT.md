# Agent Runtime 学习领域词汇

本词汇表固定架构优先型课程使用的核心语言，避免把对话、模型调用和运行时执行混为一谈。它描述概念边界，不绑定某个框架的实现名称。

## Agent 与配置

**Agent**：
一个在运行时循环中使用模型作出决策、选择行动，并根据环境反馈继续推进目标的软件系统。模型本身不是 Agent。
_Avoid_：LLM、机器人、单次模型调用

**Agent Definition**：
Agent 的可版本化定义，包括所用模型、指令、能力和运行策略，但不包含某次执行产生的状态。
_Avoid_：Agent Instance、Run、聊天记录

**Agent Platform**：
管理 Agent Definition、能力资产、执行资源、运行记录、安全策略和质量治理的系统。
_Avoid_：Agent Loop、单个 Agent 应用

## 执行生命周期

**Conversation Turn**：
由一次用户输入开始、以面向用户的最终响应或挂起状态结束的交互单元。一次 Conversation Turn 通常对应一个 Run。
_Avoid_：LLM Call、Loop Round

**Run**：
Runtime 为一个目标创建的、具有独立标识和终态的一次执行生命周期；暂停后恢复仍属于同一个 Run。
_Avoid_：请求、线程、模型调用

**Loop Round**：
Agent Loop 的一次决策循环，包含一次模型决策以及由该决策触发的零个或多个行动。
_Avoid_：Conversation Turn、Step

**Step**：
Run 中一个可独立观察的原子执行单元，例如构建上下文、调用模型或执行一个工具。
_Avoid_：Loop Round、Event

**State**：
描述 Run 当前逻辑位置和已知信息的可演进快照，是后续决策的输入之一。
_Avoid_：Event、日志、全部数据库数据

**Event**：
Run 中已经发生且不可变的事实记录，可用于流式展示、追踪或重建状态。
_Avoid_：State、Command

**Command**：
请求系统采取行动或推进状态的意图，可能被拒绝或失败。
_Avoid_：Event、Result

## 模型与能力

**Model Call**：
向模型发送一次输入并接收一次完整或流式输出的交互。一个 Run 可以包含多次 Model Call。
_Avoid_：Run、Agent

**Tool Call**：
模型或 Runtime 提出的结构化能力调用请求，只有执行并通过业务校验后才产生真实效果。
_Avoid_：Tool Result、业务成功

**Observation**：
环境或能力执行层返回给 Agent、供后续决策使用的结构化反馈。
_Avoid_：原始异常、未经约束的日志文本

**Capability**：
Agent 被允许调用的外部能力边界，可以由 Tool、Workflow、其他 Agent 或受控执行环境提供。
_Avoid_：任意 Python 函数、模型知识

