# 0006 · SubAgent / Team runtime
**日期**：2026-09-01  
**状态**：已学（第 6 课）

这一课把多智能体的核心从“图结构”推进到了“上下文隔离”。我意识到，SubAgent 不是把同一轮 AgentLoop 多跑几次，而是另起一套 `ExecutionContext`，重新设置 `agent_id`、工具白名单、usage tracker 和 broadcaster identity；Team 则是从已有 `AgentInstance` 里抽取成员，走协调器和任务分配逻辑。这个认知非常关键，因为很多现象看起来像大模型失败，实际往往是上下文/身份串了。

最关键的事实：
- `SubagentRunner._create_child_context()` 会复制父级上下文，但重建工具集合和身份标识。
- 子代理事件与消息会带 `subagent_id` / `parent_tool_use_id`，父级只接收最终文本作为工具结果。
- `TeamRuntime` 依赖 `multi_agent.workers`、`agent_id`、alias 配置解析，而不是简单复用单一 AgentLoop。

这意味着排障时不能盯着模型输出本身，而要先确认：谁在跑、什么 context、哪些事件属于谁、最终结果在什么层面回传。