# Superpowers 使用指南

## 目录

- [1. 什么是 Superpowers](#1-什么是-superpowers)
  - [1.1 核心流程](#11-核心流程)
  - [1.2 `/skill` 指令说明](#12-skill-指令说明)
  - [1.3 Superpowers Skill 速查](#13-superpowers-skill-速查)
- [2. 实战案例：为 DocsHub 增加文档评论功能](#2-实战案例为-docshub-增加文档评论功能)
  - [2.0 项目背景](#20-项目背景)
  - [2.1 启动：using-superpowers](#21-启动using-superpowers)
  - [2.2 需求澄清：brainstorming](#22-需求澄清brainstorming)
  - [2.3 拆计划：writing-plans](#23-拆计划writing-plans)
  - [2.4 隔离工作区：using-git-worktrees](#24-隔离工作区using-git-worktrees)
  - [2.5 开发执行：subagent-driven-development](#25-开发执行subagent-driven-development)
  - [2.6 并行执行：dispatching-parallel-agents](#26-并行执行dispatching-parallel-agents)
  - [2.7 批量人肉检查点：executing-plans](#27-批量人肉检查点executing-plans)
  - [2.8 编码过程：test-driven-development](#28-编码过程test-driven-development)
  - [2.9 自审：requesting-code-review](#29-自审requesting-code-review)
  - [2.10 处理反馈：receiving-code-review](#210-处理反馈receiving-code-review)
  - [2.11 修 Bug：systematic-debugging](#211-修-bugsystematic-debugging)
  - [2.12 完成前验证：verification-before-completion](#212-完成前验证verification-before-completion)
  - [2.13 收尾：finishing-a-development-branch](#213-收尾finishing-a-development-branch)
  - [2.14 沉淀为可复用 Skill：writing-skills](#214-沉淀为可复用-skillwriting-skills)
  - [2.15 流程总览](#215-流程总览)
- [3. 一句话总结](#3-一句话总结)

---

## 1. 什么是 Superpowers

`superpowers` 是一套**软件开发方法论插件**。它通过一组可组合的 skill，让 coding agent 在动手写代码之前先走设计、规划、验证流程，而不是直接开写。

本质上是把一套 workflow 固化成自动触发的 skill：

- 不直接编码，先通过提问把真实需求/设计敲定；
- 把设计拆成小块，确认后再出实现计划；
- 用 TDD、子 agent、review、worktree 等机制保证质量；
- 相关 skill 在对话中**自动触发**，不需要你手动记流程。

### 1.1 核心流程

1. **brainstorming** — 动笔前先苏格拉底式地澄清需求，输出设计稿。
2. **using-git-worktrees** — 设计确认后，创建独立分支/worktree，跑通基线测试。
3. **writing-plans** — 把实现拆成 2–5 分钟的小任务，每个都给出文件路径、代码、验证步骤。
4. **subagent-driven-development / executing-plans** — 按 plan 派子 agent 或批量执行，含 two-stage review。
5. **test-driven-development** — 严格 RED-GREEN-REFACTOR。
6. **requesting-code-review** — 任务间自动检查是否符合 plan。
7. **finishing-a-development-branch** — 完成时验证测试、决定 merge/PR/清理。

其中 `using-superpowers` 这个 meta skill 要求：**在任何动作之前，先 invoke 可能相关的 skill**。

### 1.2 `/skill` 指令说明

这里的 `/skill` 就是调用某个 skill 的入口，等价于内部 `skill` 工具的调用。你一般不需要自己敲它，agent 会在合适时机自动调用；但你可以主动说“用 brainstorming 帮我先想一下”。

| 用法 | 作用 |
|---|---|
| `skill invoke <name>` | 触发指定 skill，例如 `skill invoke brainstorming` |
| `skill list [path]` | 列出某个目录下有哪些 skill |
| `skill search [path] [keywords]` | 按关键词搜索 skill |

### 1.3 Superpowers Skill 速查

| skill | 作用 |
|---|---|
| `using-superpowers` | 总控规则：任何任务前先检查/调用 skill |
| `brainstorming` | 动笔前澄清需求、做设计、防直接编码 |
| `writing-plans` | 把设计拆成可执行的小任务清单 |
| `executing-plans` | 按 plan 批量执行，并设人工检查点 |
| `subagent-driven-development` | 派子 agent 逐项实现，含 spec + code quality 两轮 review |
| `dispatching-parallel-agents` | 并行派多个子 agent 处理独立任务 |
| `test-driven-development` | 强制 RED-GREEN-REFACTOR |
| `systematic-debugging` | 四步根因分析：复现、隔离、修复、验证 |
| `verification-before-completion` | 宣称完成前必须跑验证命令、给出证据 |
| `requesting-code-review` | 自审代码，按严重度报问题，critical 阻塞 |
| `receiving-code-review` | 处理别人给你的 review 反馈，不盲目接受 |
| `using-git-worktrees` | 用 git worktree 隔离功能分支 |
| `finishing-a-development-branch` | 收尾：测、决定 merge/PR、清理 worktree |
| `writing-skills` | 按最佳实践写新 skill 或改现有 skill |

你本地同时还装有一些非 Superpowers 的 skill（如 `tdd`、`codebase-design`、`research`、`wizard`、`grilling` 等），它们不属于 Superpowers 包，是独立能力，调用方式一样，但各自只负责一个垂直领域。

---

## 2. 实战案例：为 DocsHub 增加文档评论功能

### 2.0 项目背景

我们有一个内部 Wiki 平台 **DocsHub**（类似 Confluence，后端 Node.js + Postgres，前端 Next.js）。用户提出需求：

> 给每篇文档加评论功能，支持 @ 人、发通知、有权限控制、软删除。

下面按 Superpowers 流程走一遍，说明每个 skill 在真实项目中会在什么时机触发、做什么、产出什么。

### 2.1 启动：using-superpowers

你一开口说：

> “给 DocsHub 加个评论功能。”

Agent 立刻触发 `using-superpowers`。它检查到这是一个**新建功能**任务，于是不直接写代码，而是先进入 `brainstorming`。如果是修复 bug，它会先进入 `systematic-debugging`。

这一步的核心作用：**在所有动作之前，先决定用哪个 skill 来驱动整个流程。**

### 2.2 需求澄清：brainstorming

Agent 进入 `brainstorming`，用苏格拉底式提问把模糊需求变具体：

- 谁可以评论？（所有登录用户 / 仅编辑者 / 仅读者？）
- 是否支持楼中楼回复？（一期只做一级评论，回复用 @ 引用）
- 通知怎么发？（邮件 + 站内信 / 仅站内信？）
- 删除评论是物理删还是软删？（软删，保留审计日志）
- 前端 UI 形式？（内嵌在文档页底部，不弹窗）
- 是否需要分页？（文档最多 100 条，超出后分页）

最终产出：`docs/comment-design.md` 或项目内的设计文档。你确认后，进入下一步。

### 2.3 拆计划：writing-plans

设计确认后，Agent 用 `writing-plans` 把实现拆成 2–5 分钟可完成的小任务。每个任务都包含：

- 明确的验收条件
- 要修改的文件路径
- 需要的测试
- 验证命令

示例任务清单：

1. 创建 `comments` 表迁移（字段：`id`, `doc_id`, `author_id`, `parent_id`, `content`, `created_at`, `deleted_at`）
2. 写 `POST /docs/:id/comments` 接口的测试：权限、@ 解析、通知触发
3. 实现该接口的最小代码
4. 实现前端 `CommentSection` 组件及其测试
5. 实现通知服务 `notifyMentionedUsers` 的测试与代码
6. 实现软删除端点 `DELETE /comments/:id`
7. 端到端验证：创建 → @ 人 → 删除 → 刷新状态一致

### 2.4 隔离工作区：using-git-worktrees

在动手改代码前，Agent 触发 `using-git-worktrees`：

- 从 `main` 切出 `feature/comments` 分支
- 在 `../docshub-comments` 创建一个独立的 worktree
- 确保 `npm install` 已执行
- 跑 `npm test`，确认基线测试全部通过

这一步保证主工作目录不会被污染，也防止在已有未提交改动上叠加新功能。

### 2.5 开发执行：subagent-driven-development

Plan 写好后，Agent 进入 `subagent-driven-development`，把任务派给子 agent：

- **Agent A**：负责数据迁移 + 后端 API
- **Agent B**：负责前端组件
- **Agent C**：负责通知服务

每个子 agent 完成后都要过 **两轮 review**：

1. **Spec compliance review**：检查是否严格按 plan 实现
2. **Code quality review**：检查风格、安全、边界条件、测试覆盖

如果某个子任务没通过 review，会被打回重改，直到通过才继续。

### 2.6 并行执行：dispatching-parallel-agents

在上一步中，Agent A、B、C 三个任务之间没有共享状态，所以 Agent 用 `dispatching-parallel-agents` 并行启动它们：

- 一个子 agent 在写 `comments` 表迁移和 API
- 一个子 agent 在写 `CommentSection` 前端组件
- 一个子 agent 在写 `notifyMentionedUsers` 通知逻辑

主 agent 汇总结果，解决冲突后推进。

### 2.7 批量人肉检查点：executing-plans

在这个主流程里，Agent 使用 `subagent-driven-development` 自动推进；但如果这是个小脚本、配置更新或运维任务，Plan 会改用 `executing-plans` 模式：

- 每完成 2–3 个任务暂停一次
- 把 diff 和验证结果展示给你
- 等你点头再继续下一步

例如：如果本次需求是“给评论表加 `edited_at` 字段”，Agent 会用 `executing-plans`，因为任务边界清晰、人工检查点更安全。

### 2.8 编码过程：test-driven-development

每个任务严格走 **RED-GREEN-REFACTOR**：

1. 先写测试 `comments.create.test.ts`，断言：创建评论后 `notifyMentionedUsers` 被调用
2. 看测试失败（RED）
3. 写最小实现让测试通过（GREEN）
4. 重构命名、拆分函数、消除重复（REFACTOR）
5. 如果中途顺手写了“额外功能”但无测试，按 skill 要求删除或补测试

这一步保证代码是“先测试后实现”，而不是先实现再补测试。

### 2.9 自审：requesting-code-review

每完成一个子任务，Agent 自动做一次 `requesting-code-review`：

- 是否完成了 plan 中的验收条件？
- 是否有越界实现（YAGNI）？
- 是否有 SQL 注入、XSS、权限绕过风险？
- `@` 解析是否支持中文用户名、全角空格？
- 测试是否覆盖了边界分支？

Critical 问题会阻塞继续；major / minor 问题记录并安排修复。

### 2.10 处理反馈：receiving-code-review

你或另一个 reviewer 看完后提出：

> “被删除的评论不应继续收到通知；@ 解析应允许包含数字和中文。”

Agent 触发 `receiving-code-review`：

- 不盲改，先判断每条反馈是否合理
- 对合理项补充到 plan（回到 `writing-plans` 或 `subagent-driven-development`）
- 对不合理项说明原因并保留原设计
- 修改完成后再次 `requesting-code-review`

### 2.11 修 Bug：systematic-debugging

集成测试时发现：当评论里 @ 多人时，偶尔有人收不到通知。

Agent 触发 `systematic-debugging`，按四步走：

1. **复现**：构造 `@alice @bob` 的评论
2. **隔离**：在 `notifyMentionedUsers` 里加日志，发现用户名解析正则没处理全角空格
3. **修复**：更新正则，补充单元测试
4. **验证**：跑 `npm test -- comments`，确认通知数量正确

### 2.12 完成前验证：verification-before-completion

在 Agent 说“做完了”之前，`verification-before-completion` 强制要求：

- 跑完整测试套件 `npm test`
- 跑 lint `npm run lint`
- 手动验证关键路径：打开文档页 → 写评论 → @ 人 → 删除评论 → 刷新后状态一致
- 把命令输出和验证结果贴在回复里作为证据

没有证据，不能宣布完成。

### 2.13 收尾：finishing-a-development-branch

测试全绿后，Agent 触发 `finishing-a-development-branch`：

- 展示当前 diff、分支状态、worktree 位置
- 给出选项：直接 merge / 开 PR / 保留 worktree 继续 / 丢弃
- 你选“开 PR”后，它推送 `feature/comments` 到远程，生成 PR 摘要，清理本地 worktree

### 2.14 沉淀为可复用 Skill：writing-skills

项目结束后发现："文档型产品中的 @ 提及通知" 可能在多个功能里复用。

Agent 用 `writing-skills` 把本次经验沉淀成一份通用 skill：

- 提取 `@` 解析、通知去重、权限检查逻辑
- 按 Superpowers 格式写成 `.agents/skills/mention-notify/SKILL.md`
- 写一个最小测试用例验证 skill 行为
- 下一个需要 @ 功能的项目可直接 `skill invoke mention-notify`

### 2.15 流程总览

| 阶段 | 触发 Skill | 关键产出 |
|---|---|---|
| 启动 | `using-superpowers` | 选择后续 skill，不直接写代码 |
| 设计 | `brainstorming` | 明确需求，产出设计文档 |
| 规划 | `writing-plans` | 可执行任务清单 |
| 隔离 | `using-git-worktrees` | 干净 worktree + 基线测试通过 |
| 执行 | `subagent-driven-development` | 子 agent 按 plan 实现，含 two-stage review |
| 并行 | `dispatching-parallel-agents` | 多任务并行推进 |
| 人工检查 | `executing-plans` | 小任务批次，人工点头后继续 |
| 编码原则 | `test-driven-development` | RED-GREEN-REFACTOR 产物 |
| 自审 | `requesting-code-review` | 问题分级清单 |
| 反馈 | `receiving-code-review` | 确认/驳回修改项 |
| 排错 | `systematic-debugging` | 根因 + 修复 + 测试 |
| 验证 | `verification-before-completion` | 命令输出作为证据 |
| 收尾 | `finishing-a-development-branch` | PR / merge / 清理 |
| 复用 | `writing-skills` | 团队可复用 skill |

---

## 3. 一句话总结

Superpowers 不是让你背流程，而是让 Agent 在每一步**自动停下来、先想后做、先验后说**。

你作为人类拍板设计、计划和收尾；Agent 负责按计划执行、TDD、review、调试和验证。
