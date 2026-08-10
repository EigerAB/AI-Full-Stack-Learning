
# Skill 语法笔记

> 一句话概述：Skill（Agent Skill）本质是"知识注入"——把领域知识、操作流程、脚本和参考资料打包成一个 `SKILL.md` + 支持文件的目录，AI 在需要时才把它渐进式加载进上下文，从而实现"教一次、反复调用"的能力复用。
>
> 参考来源：
> - [SKILL.md文档的书写规范和要求（博客园）](https://www.cnblogs.com/liuyanhang/p/19678364)
> - [Claude Code 官方文档 - 使用 skills 扩展 Claude](https://code.claude.com/docs/zh-CN/skills)
> - [菜鸟教程 - Skills 教程](https://www.runoob.com/vibe-coding/skills-agent.html)


## 1. Skill 是什么

- Skill 是一种**结构化的 Prompt Engineering**：把分散在人脑中的领域知识、操作流程和最佳实践，转化为 AI 可理解、可执行的指令集。
- 本质是**知识注入**：Skill 不会动态生成新工具，而是把指令文本注入到 LLM 的上下文中，LLM 用已有的工具（bash、read、edit 等）来执行这些指令。
- 类比："给 AI 实习生的岗位培训大礼包"：
  - 普通 Prompt = 每次都要从头教一遍
  - Rule / 记忆 = 贴在工位上的行为守则（一直生效，但只能管态度和格式）
  - MCP / Tools = 给他装了一堆软件和 API（能调用，但不知道什么时候用、怎么组合用）
  - **Skill** = 一整套"岗位培训大礼包"（流程图 + SOP + 话术模板 + 常用脚本），告诉他"遇到这类事情，就按这个文件夹里的方法来做"

## 2. 核心结构：一个文件夹 + 一个 SKILL.md

最小可用结构：

```
my-skill/
└── SKILL.md   （唯一必需文件）
```

进阶结构（技能变复杂、需要脚本/模板/参考资料时）：

```
my-skill/
├── SKILL.md      # 必需：指令 + 元数据（建议控制在 500 行以内）
├── scripts/      # 可选：可执行代码
├── references/   # 可选：文档资料（大段参考资料放这里，而不是塞进 SKILL.md）
└── assets/       # 可选：模板、素材资源
```

`SKILL.md` 必须严格遵循 **YAML Frontmatter（元数据）+ Markdown Body（正文）** 的结构，用 `---` 分隔。

```yaml
---
name: pdf-processing
description: 从 PDF 中提取文本和表格，填写表单，并合并文档
---

# PDF 处理

## 使用场景
当需要对 PDF 文件进行操作时使用，例如：
- 提取 PDF 文本或表格数据
- 填写 PDF 表单
- 合并多个 PDF 文件
```

## 3. YAML Frontmatter 字段规范

Frontmatter 是 AI 决定"是否加载该 Skill"时**唯一**会读取的部分（发现阶段），因此至关重要。

### 3.1 基础字段（通用）

| 字段 | 是否必需 | 描述与规范 |
|---|---|---|
| `name` | 必需 | Skill 唯一标识符。最长 64 字符，只能使用小写字母、数字和连字符 `-`，且不能以 `-` 开头或结尾（如 `pdf-form-filler`）。 |
| `description` | 必需 / 强烈推荐 | **核心中的核心**。1-2 句话描述功能、适用场景和触发条件，最长 1024 字符，不能为空。AI **仅凭此**判断是否加载该 Skill。 |
| `license` | 可选 | 许可证名称，或指向随 Skill 附带的许可证文件。 |
| `compatibility` | 可选 | 环境与依赖说明（产品、系统包、网络权限等），最长 500 字符。 |
| `version` | 可选 | 版本号（如 `1.0.0`），用于管理迭代。 |
| `author` | 可选 | 作者或团队名称。 |
| `metadata` | 可选 | 自定义键值对，用于扩展元数据（如 `author`、`version`）。 |
| `allowed-tools` | 可选 | 定义该 Skill 可自动使用、无需用户每次确认的工具列表（空格分隔，实验性）。 |

最小必填示例：

```yaml
---
name: skill-name
description: 说明该 Skill 的功能以及适用场景
---
```


## 4. 正文（Markdown Body）应重点描述的内容

正文只有在 Skill **被触发后**才会被完整读入上下文，所以要围绕"AI 的思维链 + 行动指南"来写：

1. **角色定义（Role Definition）**
   赋予 AI 一个具体专家人设，例如"你是一名严谨的财务审计员"。

2. **核心指令与步骤（Instructions & Steps）**
   用**祈使句**（命令式语气），分步骤清晰描述操作流程：
   - 任务拆解：复杂任务拆成步骤 1、2、3
   - 逻辑判断：不同情况下如何选择（如"如果 PDF 有密码，先解密；没有则直接读取"）
   - 工具调用：明确指示何时运行 `scripts/` 脚本、何时查阅 `references/` 文档

3. **输出规范（Output Format）**
   规定最终呈现内容的结构与风格，例如"必须包含摘要、风险点、建议措施三部分"或"使用表格展示对比数据"。

4. **示例（Examples / Few-Shot）**
   给出用户输入/期望输出的示例对，并明确列出会触发该 Skill 的典型用户语句。

5. **资源引用（References & Assets）**
   指导何时读取哪个支持文件，例如"执行审计前必须先阅读 `references/compliance_rules.md`"，"使用 `assets/template.pptx` 作为报告模板"。

6. **错误处理（Error Handling）**
   规定失败时该怎么做，例如"脚本报错时把完整错误日志返回给用户，不要尝试自行修复"。

### 通用结构模板

```markdown
---
name: [技能标识名]
description: [一句话描述功能 + 触发场景 + 核心价值]
version: 1.0.0
---

# [技能名称]

## 角色定义
你是一名 [具体角色]，擅长 [核心能力]。

## 核心指令
请严格按照以下步骤执行任务：
1. **分析意图**：[步骤说明]
2. **查阅资料**：如果需要，读取 `references/[文件名]` 获取详细信息。
3. **执行操作**：运行 `scripts/[脚本名]` 处理数据。
4. **输出结果**：按照下方的输出格式要求生成回答。

## 输出格式
- 必须包含：[要素 A]、[要素 B]
- 风格：[专业/幽默/简洁]

## 示例
**用户输入**：[示例提问]
**你的回答**：[示例回答]

## 错误处理
如果遇到 [某种错误]，请 [执行某种操作]。
```


## 5. 需求分析清单（写 Skill 前先想清楚 4 件事）

1. **触发机制（Trigger）**
   用户说什么话时应该跳出来帮忙？→ 写进 `description`（列举触发短语和关键词）。
2. **确定性 vs. 自由度（Determinism）**
   任务是必须严格按步骤执行（如填税表），还是可以灵活发挥（如写文案）？前者在 `Instructions` 中写死步骤并配合 `scripts/`；后者给更多基于文本的引导。
3. **上下文管理（Context Management）**
   完成任务是否需要大量参考资料（API 文档、公司制度）？不要把长文本塞进 `SKILL.md`，放进 `references/`，并指示"按需读取"。
4. **容错与边界（Error Handling）**
   任务失败时（文件找不到、格式错误）该怎么办？在 `Instructions` 中加入错误处理指南。


## 6. Skill 的工作机制：渐进式披露（Progressive Disclosure）

Skill 通过三个阶段高效管理上下文：

1. **发现（Discovery）**：会话启动时，AI 只加载所有 Skill 的 `name` + `description`（最基本的识别信息），成本极低。
2. **激活（Activation）**：当用户消息与某个 Skill 的 `description` 匹配时，AI 才把完整的 `SKILL.md` 正文读入上下文。
3. **执行（Execution）**：AI 按照指令执行，按需读取 `references/` 中的参考资料，或运行 `scripts/` 中的脚本、使用 `assets/` 中的模板/素材。


### description 编写要点

- **列举触发短语**：把用户可能说的话写进去，例如 "deploy my app"、"push this live"、"打包"、"部署"。
- **定义时序位置**：说明"在什么之前/之后"使用，例如 "before writing implementation code"。
- **包含产品关键词**：如果覆盖多个大平台/产品，把产品名都列出来。


## 9. 编写与维护的最佳实践

- `SKILL.md` 正文尽量控制在 **500 行以内**；大段参考资料、API 规范、示例集合放到 `references/`，按需加载，不要一次性全部塞进主文件。
- 内容要"说明该做什么"，而不是絮叨"为什么"，因为 Skill 一旦被加载，其内容会**保留在整个会话上下文中**，每一行都是重复的 token 成本。
- 不要让 AI 自动把一次成功对话总结成 Skill——容易变成一篇塞满细节的长 Prompt。正确做法是人工判断：
  - 哪些内容属于"入口指引"（写进 `SKILL.md`：什么时候用、先做什么、后做什么）
  - 哪些属于"参考资料"（放进 `references/`：模板、配置、长文档）
  - 哪些属于"确定性动作"（交给 `scripts/`：稳定、机械、容易出错的步骤，用代码而不是自然语言描述）
- 迭代方式：准备一批真实提示，在"启用该 Skill"和"禁用该 Skill"两种情况下分别跑一遍，比较触发准确率与输出质量差异，而不是仅凭"看到它触发了"就认为写对了。

## 10. 相关资源整理

| 资源说明                             | 链接                                                                                          |
| -------------------------------- | ------------------------------------------------------------------------------------------- |
| Skill 聚合入口                       | https://skills.sh/                                                                          |
| Skills 市场（中文界面）                  | https://skillsmp.com/zh                                                                     |
| 腾讯 Skills 市场                     | https://skillhub.tencent.com/                                                               |
| Agent Skills 官方标准站点              | https://agentskills.io                                                                      |
| Anthropic 官方工程文章                 | https://www.anthropic.com/engineering/equipping-agents-for-the-real-world-with-agent-skills |
| VS Code Copilot Agent Skills 文档  | https://code.visualstudio.com/docs/copilot/customization/agent-skills                       |
| Anthropic 官方 Skills GitHub 仓库    | https://github.com/anthropics/skills                                                        |
| Awesome Claude Skills            | https://github.com/ComposioHQ/awesome-claude-skills                                         |
| Superpowers（软件开发自动化工作流 Skill 集合） | https://github.com/obra/superpowers                                                         |

