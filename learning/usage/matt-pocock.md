# Matt Pocock Skills

## 一句话定位

`mattpocock/skills` 是一套由 Matt Pocock 维护的 agent skill 集合，定位是 **“for real engineers, not vibe coding”**。它不是为了替代你的判断，而是通过 small、composable 的 skill 来修复 AI 编程里最常见的失败模式：需求没对齐、废话太多、代码跑不通、代码变泥球。

---

## 核心设计思想

- **小且可组合**：每个 skill 只做一件事，可以单独使用，也可以串成 flow。
- **可编辑**：用 `npx skills add mattpocock/skills` 安装后，skill 文件就在你的仓库里，你可以自己改。
- **两类调用方式**：
  - **User-invoked**：必须由你主动触发，比如 `/grill-with-docs`、`/to-tickets`。
  - **Model-invoked**：可以被你触发，也可以被 agent 在合适时机自动调用。
- **一个核心信念**：软件工程基本功在 AI 时代更重要，skill 只是把这些基本功固化成可重复实践。

---

## 安装方式

### 方式一：Claude Code 插件（只读、自动更新）

```bash
claude plugins install mattpocock-skills
# 或在会话里
/plugin install mattpocock-skills
```

### 方式二：skills.sh（可编辑、复制到项目）

```bash
npx skills@latest add mattpocock/skills
# 更新
npx skills update
```

第二种会把 skill 文件写进你的仓库，适合想自己 hack 的人。

### 初始化

安装后，每个仓库先运行一次：

```
/setup-matt-pocock-skills
```

它会配置：

- **Issue tracker**：问题跟踪方式（GitHub Issues / GitLab / 本地 markdown）
- **Triage labels**：五个默认 triage 标签
- **Domain docs**：`CONTEXT.md` 和 ADR 的存放位置

---

## 主流程：idea → ship

`ask-matt` 是总入口，相当于 skill 路由器。它定义的核心路径如下：

### 1. 对齐需求：`/grill-with-docs`

任何改动先从这里开始。Agent 会不断追问，直到把需求、边界、术语都搞清楚。同时会把结果写成 `CONTEXT.md` 和 ADR。

- 如果有 repo，用 `/grill-with-docs`（stateful，会留下文档）。
- 如果没 repo，用 `/grill-me`（stateless，只对话不存文件）。

### 2. 需要 runnable answer 的分支：`/prototype`

如果需求里有“这个状态模型行不？”或“这个 UI 长啥样？”这类问题，先 `/handoff` 出去 → `/prototype` 做可抛原型 → `/handoff` 回来。

### 3. 多会话构建的分支

**大功能 / 多 session**：

```
/to-spec → /to-tickets → /implement（每个 ticket 单独一个 context）
```

每个 ticket 都声明自己的 **blocking edges**，按依赖顺序做。

**小功能 / 单 session**：

```
/implement
```

直接在当前上下文里实现。

### 4. 实现与验证

`/implement` 内部会驱动：

- `/tdd`：先写失败测试，再写代码，再重构。
- `/code-review`：双轴 review（Standards + Spec）。

### 5. 维护性：持续运行

- `/improve-codebase-architecture`：定期扫描，找出可以做“深度模块”的候选点。
- `/domain-modeling`：维护共享语言，更新 `CONTEXT.md`。
- `/codebase-design`：设计模块形状。

---

## Grilling 系列详细对比

`grilling` 是这个系列的原语，`grill-me` 和 `grill-with-docs` 是面向用户封装出来的入口。

| skill | 类型 | 调用 | 产物 | 使用场景 |
|---|---|---|---|---|
| `grilling` | model-invoked | 一般由其他 skill 内部调用 | 无文件产物，只在会话内对齐设计树 | 不直接调；是 `grill-me`、`grill-with-docs`、`triage`、`wayfinder` 等的共享原语 |
| `grill-me` | user-invoked | 主动 `/grill-me` | 不保存任何文档 | 没有 repo，或只是临时 stress-test 一个想法/计划/设计 |
| `grill-with-docs` | user-invoked | 主动 `/grill-with-docs` | `CONTEXT.md`、ADR（可能） | 在 repo 里做真实改动，需要建立共享领域语言并留下决策痕迹 |

### 使用场景速判

- 你脑子里刚有个想法，想被反复追问把它问清楚，但不想留文档 → **`/grill-me`**
- 你正在仓库里做改动，需要把术语和决策写进 `CONTEXT.md` → **`/grill-with-docs`**
- 你后续还希望其他 agent 能看懂这个项目的领域语言 → **`/grill-with-docs`**
- 你只是想快速对齐，完全不需要保存 → **`/grill-me`**
- 其他 skill 内部需要一个“无情追问”机制 → **`grilling`**（用户一般不直接触发）

### grilling 的工作方式

`grilling` 本身是一个**无情追问原语**：

1. 把讨论映射成一棵 **design tree**，每个决策下面挂着它的子决策。
2. 按 **round** 工作，每轮只问 **frontier**（前提已确定的决策）。
3. 每个问题都要给出推荐答案，等用户确认或修改。
4. **facts 由 agent 自己查**（用工具、查代码、查文件），**decisions 由用户做**。
5. 当 frontier 为空，即设计树每个分支都被探明，会话结束。
6. 在用户确认双方已达成共识之前，**不要行动**。

### grill-with-docs 会额外做什么

`/grill-with-docs` 在追问过程中，额外触发 `domain-modeling`：

- 当用户用词与现有 `CONTEXT.md` 冲突，立即指出。
- 把模糊或一词多义的术语，锐化成规范的领域术语。
- 用具体、边缘的场景 stress-test 领域关系。
- 把用户说的和代码实现做交叉验证。
- 术语一旦确定，立刻更新 `CONTEXT.md`。
- 只有满足下面三个条件时才写 ADR：
  1. **难以撤销**；
  2. **没有上下文会让人惊讶**；
  3. **是真 trade-off**（有明确替代方案并被主动选择）。

### 一句话选择

> 有 repo 就用 `/grill-with-docs`；没 repo 或只是临时头脑风暴就用 `/grill-me`；不要直接调 `grilling`，它是被上面两个 skill 复用的原语。

---

## Engineering Skills

### User-invoked

| skill | 作用 |
|---|---|
| `ask-matt` | 问哪个 skill/flow 适合你当前情况 |
| `grill-with-docs` | 面试式澄清需求，产出 `CONTEXT.md` 和 ADR |
| `triage` | 把 issue 按状态机流转 triage 角色 |
| `improve-codebase-architecture` | 扫描代码库，找“做深模块”的机会 |
| `setup-matt-pocock-skills` | 初始化 repo 配置 |
| `to-spec` | 把当前对话整理成 spec，写到 issue tracker |
| `to-tickets` | 把 plan 拆成带阻塞关系的 tracer-bullet tickets |
| `implement` | 按 spec/ticket 实现，内部驱动 `/tdd` 和 `/code-review` |
| `wayfinder` | 大项目/绿场项目的地图式规划 |

### Model-invoked

| skill | 作用 |
|---|---|
| `prototype` | 做可抛原型回答设计问题 |
| `diagnosing-bugs` | 严格诊断循环：先建立复现，再假设、验证、修复 |
| `research` | 用 background agent 调研一手资料，写引用文档 |
| `tdd` | 先写失败测试，再写代码，再重构 |
| `domain-modeling` | 维护和锐化领域模型 |
| `codebase-design` | 深度模块设计词汇和实践 |
| `code-review` | 双轴 review：Standards + Spec |
| `resolving-merge-conflicts` | 按意图逐块解决 merge/rebase 冲突 |
| `wizard` | 生成交互式 bash wizard，引导人类完成只有他能做的步骤 |

---

## Productivity Skills

### User-invoked

| skill | 作用 |
|---|---|
| `grill-me` | 无 repo 时的需求/设计追问 |
| `handoff` | 把当前会话压缩成 handoff 文档，交给另一个 agent 继续 |
| `teach` | 多 session 教学，把当前目录作为 stateful teaching workspace |
| `to-questionnaire` | 把需要别人回答的问题写成问卷 |
| `wait-what` | 当一条消息没传达清楚时，用 `CONTEXT.md` 词汇重新解释 |

### Model-invoked

| skill | 作用 |
|---|---|
| `grilling` | 面试原语，被 `grill-me`、`grill-with-docs`、`triage`、`wayfinder` 等复用 |
| `writing-for-agents` | 写 skill、AGENTS.md、CLAUDE.md 等 agent 文档 |

---

## 它想修复的四个失败模式

1. **Agent 没做你想要的** → 用 grilling 先对齐需求。
2. **Agent 废话太多** → 用 `CONTEXT.md` 建立共享语言。
3. **代码跑不通** → 用 `/tdd` 和 `/diagnosing-bugs` 建立反馈循环。
4. **代码变泥球** → 用 `/improve-codebase-architecture` 和 `/codebase-design` 关心设计。

---

## 与 Superpowers 的对比

| 维度 | Matt Pocock Skills | Superpowers |
|---|---|---|
| 安装方式 | 可编辑本地文件 / Claude 插件 | 自动插件更新 |
| 调用方式 | 多为 user-invoked | 多为自动触发 |
| 流程 | 以 `ask-matt` 路由，idea→ship 为主线 | 自动走 brainstorming→plan→SDD→review |
| 强调点 | 对齐、domain docs、架构设计、可组合 | TDD、subagent、worktree、自动 review |
| 适用 | 喜欢自己控制、愿意维护 `CONTEXT.md` | 希望 agent 自动跑完流程 |

两者可以互补。比如可以用 Matt Pocock 的 `grill-with-docs` 做需求对齐，再用 Superpowers 的 `subagent-driven-development` 执行实现。

---

## 关键文件

安装后会生成：

- `docs/agents/issue-tracker.md` — 问题跟踪配置
- `docs/agents/triage-labels.md` — triage 标签映射
- `docs/agents/domain.md` — domain docs 消费者规则
- `CONTEXT.md` — 项目共享语言和术语
- `CLAUDE.md` / `AGENTS.md` — agent 规则文件

---

## 参考来源

- 仓库 README：https://github.com/mattpocock/skills/blob/main/README.md
- 路由 skill `ask-matt`：https://github.com/mattpocock/skills/blob/main/skills/engineering/ask-matt/SKILL.md
- 初始化 skill `setup-matt-pocock-skills`：https://github.com/mattpocock/skills/blob/main/skills/engineering/setup-matt-pocock-skills/SKILL.md
- 课程官网：https://www.totaltypescript.com/
