# Superpowers 框架学习笔记

> 一句话概述：Superpowers 是 Jesse Vincent（obra）为 Claude Code 设计的一套 Skill 框架，把资深工程师的**流程纪律**（而不只是知识）封装成可复用的 `SKILL.md` 文档，让 Agent 在合适的时机自动加载相应的流程约束，而不是每次凭"感觉"自由发挥。本笔记结合 `zeno-agent-platform`（nwaip）仓库的 `.claude/` 目录，记录 Superpowers 的核心理念以及它在真实多人协作项目中的落地与改造。

## 1. Superpowers 要解决什么问题

LLM Agent 的技术能力通常够用，但缺的是工程纪律：

- 容易跳过设计环节，直接开始写代码
- 写完不做验证就汇报"完成了"（表演式汇报）
- 收到审查意见照单全收，不核实对错
- 大计划一次性莽着写完，容易返工、上下文被污染

Superpowers 用一批**互相配合的 Skill** 把这些纪律固化成文档，在正确的时间点被自动或手动触发。

## 2. 核心理念

| 理念 | 说明 |
|---|---|
| **Skill = 可复用的流程/技巧参考**，不是"我曾经这样解决过一次问题"的叙事 | 类似留给未来的自己/其他 Agent 的操作手册 |
| **HARD-GATE（硬闸门）** | 例如"设计未获批准前不许写代码"，用强约束语言写进 SKILL.md，防止 Agent 抄近路 |
| **Skill 本身也要 TDD** | `writing-skills`：先跑一个没有该 skill 的"压力场景"子 agent，观察它如何违规（RED）→ 写 skill 让它合规（GREEN）→ 找新的违规话术再堵漏洞（REFACTOR） |
| **Fresh subagent per task** | `subagent-driven-development`：每个任务派一个"干净上下文"的子 agent 去做，任务完成后再派 reviewer 子 agent 复查，避免主会话上下文被污染 |
| **Verification before completion** | 声称"修好了/测试通过"之前必须先跑验证命令看到真实输出，杜绝空口汇报 |
| **Doubt-driven / receiving code review** | 收到审查意见先核实是否真的站得住脚，不要无脑照改 |
| **git worktree 隔离** | 每个 feature 独立 worktree + 分支，避免并行任务互相污染工作区（这是 Superpowers 的默认假设，但在多窗口共享分支的团队里需要改造，见第 4 节） |

## 3. 本仓库（nwaip `.claude/`）里能看到的 Superpowers 落地

`.claude/skills/` 里有一批**源自 Superpowers 上游**、原样或改造后引入的 skill：

- `brainstorming` — 先设计后编码，带 HARD-GATE
- `writing-plans` / `subagent-driven-development` — 拆计划、派子 agent 执行 + 审查
- `systematic-debugging` — 先定位根因再修
- `requesting-code-review` / `receiving-code-review`
- `finishing-a-development-branch`
- `using-git-worktrees`
- `verification-before-completion`
- `writing-skills` / `skill-creator` — Skill 本身的 TDD 方法论
- `dispatching-parallel-agents`

代码里**明确留有痕迹**证明这些文件确实是从 Superpowers 上游拷贝改造而来，比如：

> `.claude/skills/using-git-worktrees/SKILL.md` 第 8 行写着："忽略下文的 `feature/` 前缀示例，以及 Jesse / superpowers 外来引用——它们不适用于本仓。"

说明团队保留了原文措辞，只在开头加了"nwaip 适配"的 override 说明，而不是从零重写。

## 4. 关键改造点：Superpowers 假设 vs. nwaip 现实

Superpowers 默认假设"单人单窗口、每个 feature 一个独立 worktree/分支"，但 nwaip 是**多个 Claude Code 窗口共用同一 checkout、同一分支**并行开发（见根 `CLAUDE.md`「Git 隔离」章节）。因此做了几处关键适配：

1. **禁止 AI 自动建/切分支**：`.claude/hooks/guard-branch.py` 这个 PreToolUse 护栏硬拦截；只有走 `/cell` 才允许在 `.worktrees/<task>` 下创建隔离 worktree（分支前缀必须是 `feat|fix|refactor/`）。
2. **暂存纪律**：共享工作区下 `git add -A` / `git commit -am` 会误提交别人的改动，强制"只 add 自己改的具体文件路径"。
3. **`.superpowers/sdd/` 目录**：`subagent-driven-development` 的过程文件（任务简报、diff 包、review 包）落在这个 gitignored 目录里，作为子 agent 之间传递上下文的确定性路径；但只能在 `/cell` 起的独立 worktree 里用，主 checkout 上并行跑会互相覆盖。
4. **`/cell` 命令**：本仓在 Superpowers 之上自建的"dev-cell 隔离流程"，串联：
   `brainstorming → spec → 双闸审批（门1批准spec + 门2选实施模式） → cell-up（建独立 worktree + 分支 + 本地库） → plan → 闸③ → SDD 执行 → 闸② 审查 → 合并 → cell-down`
5. **跳过基线全量测试的例外**：Superpowers 原版要求"起 worktree 前先跑测试确保基线干净"；nwaip 基线本身有一定量既有失败（技术债），`cell/SKILL.md` 里明确记录了为什么不这么做、改成验证什么信号更有效（例如 `TEST_DATABASE_URL` 是否钉到本 cell，而不是共享库）。
6. **落盘分工**：过程材料（diff 包、reviewer 原始输出）留在 gitignored 的 `.superpowers/sdd/`，随 cell down 一起清掉；但**审批凭据（收尾报告）必须进版本库**（写进 `plan.md` 的「收尾报告」段），否则 `.superpowers/` 被删后就无法追溯当时基于什么批准的。

## 5. `.claude/` 目录全景图

| 目录 / 文件 | 作用 |
|---|---|
| `commands/` | `/xxx` 斜杠命令，多为薄壳，正文按路径委托给 skill / agent |
| `skills/` | 技能库；顶层 `skills/<name>/SKILL.md` 可被模型自动触发或 `/xxx` 调用；`skills/backend/<name>/` 是按路径引用的子技能库（不自动触发，由命令/agent 正文 `Read and follow` 加载） |
| `agents/` | 项目级子 agent（code-reviewer、python-reviewer、e2e-runner 等），通过 Task 工具按 frontmatter `name` 派发 |
| `rules/` | 按路径生效：`platform-*` 全局生效，`backend-*` 只对 `zeno-backend-agent/**` 生效，前端规则只对 `zeno-frontend-agent/**` 生效 |
| `hooks/` | PreToolUse 护栏脚本，如 `guard-branch.py`（主 checkout 禁止建/切分支——多窗口共享 HEAD） |
| `settings.json` | hooks 注册等 harness 配置 |

命令与 Skill 的对应关系（举例）：

| 命令 | 关联 Skill | 关联 Agent |
|---|---|---|
| `/brainstorm` | `brainstorming` | — |
| `/code-review` | `code-review` | `code-reviewer` |
| `/execute-plan` | `backend/executing-plans`（回退 `writing-plans`） | — |
| `/tdd` | `backend/test-driven-development` | `tdd-guide` |

还有一批**没有对应斜杠命令**、由模型按 `description` 自动触发或人工直接调用的独立 Skill：`cell`、`deploy`、`local-dev`、`prd-generator`、`systematic-debugging`、`verification-before-completion`、`doubt-driven-development`、`requesting-code-review` / `receiving-code-review`、`using-git-worktrees`、`finishing-a-development-branch`、`gc-cleanup`、`search-first`、`skill-creator`、`writing-skills`、`codebase-onboarding` 等。

## 6. 推荐学习路径

1. **主线三件套**：`brainstorming` → `writing-plans` → `subagent-driven-development`
   理解"设计批准 → 拆计划 → 子 agent 执行 + 审查"这条核心流程。
2. **元方法论**：`writing-skills`
   理解 Skill 本身是怎么被"当作代码一样做 TDD"出来的——这是 Superpowers 里最容易被忽视、但最值得学的部分。
3. **真实约束下的改造样本**：`cell/SKILL.md` + 根 `CLAUDE.md`
   看本仓库是如何在"多窗口共享同一分支"这个特殊约束下，对 Superpowers 原生假设做外科手术式改造的——这部分对"如何把通用框架落地到自己团队真实约束里"最有参考价值。


## 7. 新需求的 Skill 调用流程（Feature Lane 全链路）

结合 Superpowers 原版流程 + 本仓 `.claude/skills/cell/SKILL.md` 的落地改造，一个"新需求"从提出到合并的完整调用链：

### 入口分流（先判断走哪条 lane）

按"要做什么"判断，不按"改几个文件"判断：

| 需求性质 | 判据 | 走的路径 |
|---|---|---|
| 非变更 | 调研/理解/咨询/审查 | 只读探索，不建 cell |
| Bug 修复 | 已有行为不符预期 | → `systematic-debugging`（不写 spec，问题已知） |
| 小改 | ≤4 文件、无新模块、不涉 schema、不改行为契约 | 一句轻确认后直改，不写 spec |
| **新功能 / 改行为** | creative work | → **Feature Lane**（下面的完整流程） |

### Feature Lane 完整调用链

```
① brainstorming
   ↓（一次一问澄清意图 → 提 2-3 个方案带取舍 → 分段呈现设计 → spec 自审四项）
   写 docs/plan/YYYY-MM-DD-<feature>/spec.md
   ↓
🚦 门1+门2（一次问收两项，不分两次往返）
   Q1 spec 批准吗？     ①批准 ②需要修改
   Q2 实施模式？        ①当前分支直改 ②起 cell（涉DB/需隔离环境→推荐）
   未批准 → 禁止往下走
   ↓
② （若选②）cell-up —— 建独立 worktree + 分支 + 本地库 + 分配端口
   ↓
③ writing-plans
   ↓（把 spec 拆成"2-5 分钟一个"的任务，每个任务给准确文件路径+完整代码+验证步骤）
   写 plan.md
🚦 门3：plan 批准
   ↓
④ subagent-driven-development（须在 cell worktree 内跑；本仓约束不能在主 checkout 跑，多窗口并发会互相覆盖）
   ↓ 对 plan 里每个任务：
     派一个"干净上下文"的实现子 agent → 完成后派 task-reviewer 子 agent 复查（spec 符合度 + 代码质量）
     → 有问题就派 fix 子 agent 修 → 通过才标记任务完成
   全部任务完成后 → 派一个更强模型的"全分支 review"子 agent 做终审
   ↓
⑤ verification-before-completion（横切纪律，贯穿全程）
   声称"完成/修好"之前必须先跑验证命令，看到真实输出才能说完成
   ↓
🚦 闸②：收尾报告人工审批
   plan.md 里写「收尾报告」段：结论、spec 逐条对照、真实测试输出、审查结论、风险、复现方式
   ↓
⑥ 合并（AI 两阶段执行）+ 发布（人做）+ cell-down 清理
```


### 关键点

1. **`/cell <需求>` 是这条链路的"单一入口"**——自己不重造轮子，只按阶段委派给 `brainstorming` → `writing-plans` → `subagent-driven-development` → `requesting-code-review`/`receiving-code-review` → `finishing-a-development-branch` 这些原版 Superpowers skill。也可以不用 `/cell`，直接描述需求，模型会按 `[user,model]` 触发器自动识别"这是创造性工作"并加载 `brainstorming`。

2. **三条横切铁律**，不属于任何单一阶段、按情境随时触发：
   - `test-driven-development`——没有先写失败测试，不许写生产代码
   - `systematic-debugging`——没查到根因，不许提修复方案
   - `verification-before-completion`——没跑验证，不许说"完成了"

3. **本仓库相比 Superpowers 原版加的硬闸门**：`brainstorming` 的 HARD-GATE 明确写着——设计（spec）没获批准前，禁止调用任何实现类 skill、禁止写代码、禁止 cell-up。防止"看起来简单就跳过设计直接写代码"。

4. **是否起 cell（隔离环境）不是 AI 替你选的**——AI 只给推荐（涉数据库/需要隔离验证时推荐起 cell），最终选①当前分支直改还是②起 cell，由人在门1+门2里决定，因为起 cell 有真实的环境成本（worktree、建库、建表）。

### ① brainstorming 详解：分段呈现设计 & spec 自审四项

**分段呈现设计（Presenting the design）**——具体执行的是这几条规则：

1. **按复杂度分段呈现，不是一次性甩一大段文字**：简单的部分几句话说完，复杂/有取舍的部分可以展开到 200-300 字，篇幅跟着复杂度走，不搞一刀切。
2. **每呈现完一段就问一次"这样对吗"**，得到确认才往下讲下一段——不是把整个设计讲完了才让你一次性拍板。
3. **固定要覆盖这几块**：架构、组件、数据流、错误处理、测试策略——这是呈现设计时必须过一遍的清单，缺哪块就补哪块。
4. **走查关键决策（本仓库加的一条）**：带你过一遍"打算怎么建"的每一步，每一步都要明确亮出这一步的关键决策点 + AI 会默认采用的方案，让你确认或改——**禁止对关键决策做"静默默认"**（不能自己悄悄拍板走过去，必须显式摆出来给你看）。
5. **随时可以回退重讲**：发现某处理解错了或反馈对不上，随时回到前面的设计段落重新澄清，不是"设计一旦讲出去就不能改"。

**Spec 自审四项**——这是在设计已确认、写完 spec.md 之后、交给用户评审（门1）**之前**，AI 自己拿"新鲜的眼睛"过一遍的检查清单：

1. **占位符扫描**——扫有没有"TBD"、"待定"、未写完的段落、含糊笼统的需求描述，发现了就当场补全/写死，不允许带占位符交出去。
2. **内部一致性**——检查 spec 各章节之间有没有自相矛盾，比如"§2 设计方案"是否跟"§1 范围界定"对得上，防止前后两节各说各话。
3. **范围检查**——判断这个需求的体量是否适合作为**一个**实施计划来推进，还是其实太大了应该拆成多个独立需求分开做。
4. **歧义检查**——找有没有哪条需求描述能被两种不同方式理解，一旦发现歧义就要主动选定一种理解并写清楚，不留模糊表述让实施阶段自己猜。

**执行方式上的两个关键约束**：
- **发现问题就地改掉，不需要重新走一遍全部四项**——改完那一条就继续检查下一条，不是"改一处就从头重审"。
- **这是 AI 自己跑的检查清单，不是派子 agent 去做**——成本很低（团队估算约 30 秒），但实测能在这一步抓出 3-5 个真实问题，属于"划算的自我检查"，不值得为它单独起一个子 agent。



### ② cell-up 详解（给这个需求分配一套完全隔离的环境）

`cell-up` 是门1+门2批准"起 cell"后 AI 自动执行的一套机械操作，本质是分配一套独立的：代码工作区 + git 分支 + 后端/前端服务 + 数据库。具体拆开讲：

**1. `allocate` —— 抢 slot，分配资源编号**
```powershell
python tools/cell/cell.py allocate <task>
```
- 本仓最多 10 个 slot（`MAX_SLOTS=10`），slot 0 永久保留给主环境，**并发上限 9 个 cell**。
- 拿到 slot 号 `N` 后，所有资源都从 N 派生，不是随便挑的：

  | 资源 | 派生公式 | 举例（N=1） |
  |---|---|---|
  | 后端端口 | `8000 + N*10` | 8010 |
  | 前端端口 | `5175 + N` | 5176 |
  | PG 库名 | `zenflux_cell<N>` | `zenflux_cell1` |
  | Redis DB | `N + 1` | 1 |
  | ES 前缀 | `cell<N>_` | `cell1_` |

- 端口从 slot 1（8010/5176）起，故意避开主环境的 8000/5175，不会互相打架。
- 分配结果写进账本，`/cell`（无参）出看板时靠这份账本判断哪些 cell 在跑。
- 扩容硬顶是 Redis：默认只有 0-15 共 16 个 DB。slot 8 派生的 8080 是常见端口，占用了不一定是自己的服务，要先排查。

**2. `git worktree add` —— 建独立代码工作区 + 分支**
```powershell
git worktree add .worktrees/<task> -b feat/<task>
```
- 建一个完全独立的工作目录 `.worktrees/<task>`，指向新分支 `feat/<task>`（或 `fix/`、`refactor/` 前缀）。
- 这是 `guard-branch.py` 护栏里**唯一放行**的建分支方式——主 checkout 是多窗口共享的，AI 不能在主 checkout 自动建/切分支，但这个受控路径可以。
- 独立 git index，跟主 checkout、跟别的并行 cell 完全互不干扰。

**3. `render-env` —— 写环境变量文件**
```powershell
python tools/cell/cell.py render-env <N> --worktree .worktrees/<task>
```
- 往 worktree 里写后端 `.env` 和前端 `.env.development.local`，把 allocate 出来的端口号、库名灌进去。
- 关键点：会把 `DATABASE_URL` 和 `TEST_DATABASE_URL` **一起钉到 `zenflux_cellN`**——如果 `TEST_DATABASE_URL` 没钉对，单测会打到共享主库 `zenflux`，因为 `create_all` 只建缺失表、不改已有表，表现成"新加的列不生效"这种很难查的假失败。

**4. `db-create` —— 建空数据库**
```powershell
python tools/cell/cell.py db-create <N>
```
- 只是 `CREATE DATABASE zenflux_cellN` + 装 pgvector 插件，只建库壳，不建表。

**5. 建表（紧跟 db-create，起后端前必做）**
- 不是 cell.py 自动做的，是流程里明确要求手动跑的一段脚本。
- 原因：后端启动 lifespan 第一步就查 `tenants` 表，空库直接崩 `relation "tenants" does not exist`；而 Alembic 的 PG 版本脚本链在本仓已经"烂掉"（团队实际走的是达梦 metadata replay，没人维护 PG 版本脚本）。
- 所以 cell 本地 PG 一律用 `Base.metadata.create_all()` 一次性把全量 ORM 表铺进 `public` schema（幂等，可重复跑）——**这不算真正的迁移**，只是本地一次性库的建表手段，真正的 schema 迁移债还是要在合并前照规矩补上。
- 顺序硬约束：`db-create` → `create_all` → 才能起后端。

**6. 起后端 + 起前端**
- 后端：`cd .worktrees/<task>/zeno-backend-agent` → 激活 conda 环境 → `python -m app.http_main`（后台跑），看到 `Application startup complete.` 算成功。
- 前端：`cd .worktrees/<task>/zeno-frontend-agent` → `pnpm install` → `pnpm run dev`（后台跑）。

**7. 冒烟验证（不是跑全量测试）**
只验证三件真正会导致 cell 不可用的事，秒级完成：
1. 表建好了没（查 `information_schema.tables` 数量）
2. `TEST_DATABASE_URL` 是否真的钉到本 cell 的库（一行 grep）
3. 后端 `/health` 和前端能不能访问

**为什么不跑全量基线测试**：Superpowers 原版 `using-git-worktrees` 要求先跑测试确认基线干净，但本仓基线本来就不是全绿的（有几十上百个既有失败的技术债），全量跑一遍既费时间又验证不出"环境到底起好了没"这个真正想知道的信号，所以改成上面三项针对性检查。

验证通过后口头汇报一份基线报告，例如：
```
Worktree ready at <full-path>
DB zenflux_cell1 ready (87 tables) · TEST_DATABASE_URL 已钉本 cell
Backend :8010 healthy · Frontend :5176 up
Ready to implement <feature-name>
```

至此 cell-up 完成，进入 `writing-plans` 阶段，plan 就在这个 worktree 里写。
 
## 8. 参考文件路径清单（回仓库查证用）

- `.claude/skills/brainstorming/SKILL.md`
- `.claude/skills/writing-plans/SKILL.md`
- `.claude/skills/subagent-driven-development/SKILL.md`
- `.claude/skills/subagent-driven-development/scripts/task-brief`
- `.claude/skills/subagent-driven-development/scripts/sdd-workspace`
- `.claude/skills/subagent-driven-development/scripts/review-package`
- `.claude/skills/using-git-worktrees/SKILL.md`
- `.claude/skills/writing-skills/SKILL.md`
- `.claude/skills/cell/SKILL.md`
- `.claude/skills/backend/executing-plans/SKILL.md`
- `.claude/rules/documentation.md`
- `.claude/commands/onboard.md`
- `.claude/README.md`
- 根目录 `CLAUDE.md`（「Git 隔离（多窗口并行 — 硬约束）」章节）
- `.claude/hooks/guard-branch.py`
- `.claude/skills/cell/SKILL.md`（入口分流表、Feature Lane 完整流程、门1/门2/门3/闸②）
