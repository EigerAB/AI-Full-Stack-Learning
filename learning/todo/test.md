# 工作流生命周期、发布版本与编辑状态

> 核查日期：2026-09-14。适用于浏览器工作流列表及画布使用的 `/api/v1/agents`、`AgentProductService` 链路。
> 文档性质：现状架构说明与设计差异登记；不是运行权限变更批准，也不表示 UI 优化已实现。

## 1. 范围与已有设计

本文中的主体是历史上命名为 AgentProduct 的工作流资产，不是 `/config-agents` 管理的配置式智能体，也不是一次工作流执行任务。

| 来源 | 定义 | 使用边界 |
|---|---|---|
| [平台重构 PRD §3.3.3、§3.3.4](../../../product/prd-agent-platform-refactor.md) | 运行生命周期与 Workflow 配置版本分离 | 原始设计意图；其中运行授权要求与现实现存在差异，见 §6 |
| [Agents HTTP 设计 §3、§4、§5](../../api/http/agents.md) | 草稿、发布快照、生命周期操作和生产执行 | 接口设计依据，不保证每条历史描述都已被代码执行 |
| [Workflow Service 架构 §7](workflow-service-architecture.md) | `draft → active → archived`，以及运行任务状态 | 对应 WorkflowDefinition / gRPC 业务模型，不能与本文生命周期枚举混用 |

解释现状以代码事实为证据；修改产品行为仍须遵循项目文档裁决层级。发现冲突时登记差异，不以“代码如此”为理由宣布覆盖原 PRD。

## 2. 状态词汇与存储关系

### 2.1 不能统称为 status 的四种事实

| 概念 | 来源 | 回答的问题 |
|---|---|---|
| 生命周期 L | `agents.lifecycle_status` | 主体处于 draft、enabled、disabled 还是 delisted？ |
| 当前发布事实 P | `agents.published_version_id` 及其引用的版本 | 当前有没有发布基线？ |
| 发布内容差异 E | 当前内容与当前发布快照的比较结果 | 当前内容是否还需重新发布？ |
| 保存状态 S | 编辑器保存过程 | 当前编辑是否已持久化？ |

`draft` 在生命周期中表示配置中、尚未作为生产默认可用主体；“Workflow 草稿”则指可编辑的配置副本。二者不是同一状态：主体已经 enabled 时仍然有草稿。

本文使用“启用”，不使用容易与一次执行混淆的“启动”。`running/succeeded/failed/cancelled` 属于运行任务，不能拿来替代上述事实。

### 2.2 三张表

| 表 / 模型 | 关键字段 | 责任 |
|---|---|---|
| `agents` / AgentProduct | lifecycle_status、workflow_id、published_version_id | 主体身份、生命周期和当前版本指针 |
| `workflows` / AgentWorkflow | agent_id、draft_dsl、draft_revision | 当前编辑草稿；agent_id 唯一 |
| `workflow_versions` / AgentWorkflowVersion | workflow_id、dsl、version_number、is_published、unpublished_at、source_revision | 发布及历史快照 |

`agents.workflow_id` 是软引用；`workflows.agent_id` 关联主体；版本通过 workflow_id 归属草稿。published_version_id 引用当前版本，不能仅凭版本 ID 非空就证明引用完整有效。

发布创建 DSL 快照并切换指针，不删除当前草稿。不可变的是已生成的配置快照；取消发布时间等元数据可以更新。`is_published=true` 保留“曾发布”事实，取消发布后仍可能为 true。因此，历史上有发布版不等于当前有发布版。

为展示建模时，P=true 应要求：指针存在、版本存在且属于当前工作流、未被取消发布。当前列表主要只检查指针是否非空，尚未实现该完整语义摘要。

### 2.3 编辑差异与保存状态

E 仅在存在当前发布基线时适用。已保存草稿与发布版不同，仍然有待发布更改；保存成功不能清除 E。修改后恢复原内容，则 E 应为 false，不是永久保留“曾编辑”标记。

列表比较已保存草稿；编辑器还可能持有未保存内容。两页只有基于同一内容快照才要求结果相同。未加载、比较失败是 unknown，不是 false。没有发布基线是不适用，不是“待更新已发布版本”。

当前 contentDirty 是本地编辑脏标记，不能独立证明持久化草稿与发布版不同。跨端可靠 E 摘要、DSL 规范化比较及字段清单属于待实施设计，详见 UI spec，而非现有 API 保证。

## 3. 当前服务操作对事实的影响

| 操作 | 生命周期影响 | 当前发布指针 | 草稿 / 版本影响 |
|---|---|---|---|
| 创建 | draft | 无 | 建立草稿 |
| 编辑、保存草稿 | 不因保存自动启停 | 保持 | 更新草稿，不替换发布快照 |
| 仅发布（enable_agent=false） | 保持原值 | 指向新发布版 | 从草稿建立快照 |
| 发布并启用（enable_agent=true） | enabled | 指向新发布版 | 从草稿建立快照 |
| 生命周期 enable | draft / disabled → enabled | 使用已有或指定版本 | 不等于重新发布草稿 |
| disable | enabled → disabled | 保持 | 保持 |
| delist | disabled → delisted | 保持 | 保持 |
| restore_from_delisted | delisted → disabled | 保持 | 保持 |
| unpublish | enabled / disabled → draft | 清空 | 标记当前版本取消发布，保留历史及草稿 |
| restore-to-draft | 保持 | 保持 | 用历史快照覆盖草稿，增加修订号 |

注意：当前 unpublish 不接受 `draft + 发布指针`，因此“已有发布版”不代表所有发布相关操作均可用；不能从展示标签反推按钮权限。

发布请求模型默认 enable_agent=false，画布入口显式传 true。仅发布后 `draft + 发布指针` 是可以产生的合法组合，不应批量作为脏数据改成 enabled。

上表区分发布入口与生命周期入口。后者有前置状态和首次启用门禁校验；当前 publish_workflow 并没有完全复用这些校验，不应把生命周期状态图误写成所有入口统一执行的完整约束。

## 4. 必然关系与边界

以下是状态模型及正常完整数据下的服务流程推论，不是数据库已建立的跨表硬约束，也不是对所有导入、历史操作和并发情况的证明。

| 条件 | 推论 | 边界 |
|---|---|---|
| 启用成功，引用有效 | 有当前发布版本 | 启用依赖版本；不是先启用就自动产生草稿发布版 |
| 正常停用或下架、下架恢复 | 保留发布指针 | 生命周期操作不负责清空版本 |
| 创建或取消发布成功 | draft 且无当前发布指针 | 不代表从未发布 |
| draft 且有有效当前发布版 | 已发布但未启用 | draft 不能直接翻译为未发布 |
| E=true | P=true | 比较定义；必须先有发布基线 |
| 发布成功、当前内容此后未改变 | E=false | 迟到发布响应不能覆盖后续编辑 |
| 保存或修改草稿 | 不必改变 L、P | 草稿与发布版本分离 |

不能反推：

- P=true 不推出 enabled，也不推出 E=false。
- L=draft 不推出 P=false；P=false 不推出从未发布。
- 历史 is_published=true 不推出该版本当前生效。
- 已保存不推出已发布，也不推出 E=false。
- 修订号、更新时间不同不必然代表发布内容不同。
- disabled 的名称不能证明所有执行入口都拒绝访问，见 §6。

引用损坏、未知生命周期或异常字段组合应显式作为异常处理，不能为了满足上述关系伪造发布成功或自动修库。

## 5. 列表与编辑页为何不一致

当前列表按 published_version_id 派生 `status=published`，显示“发布中”；编辑页按 lifecycle_status 是否 draft 二分“草稿 / 已发布”。前者判断发布指针，后者判断生命周期，两者使用不同事实却展示成同一种状态。

因此存在两个独立问题：

1. “发布中”文案把已有发布版误写成正在执行发布。
2. `draft + 发布版` 在列表显示发布、编辑页显示草稿；disabled / delisted 又可能在编辑页被粗略归入已发布。

后端列表和详情均提供生命周期与发布指针，不是详情接口只能返回 draft。问题不要求先拆表或重命名数据库枚举；应先统一概念和投影，同时补齐需要的内容差异事实。

## 6. 原设计与现状的差异：尚未裁决

| 事项 | 原设计 / 文档 | 已核查实现 | 本文处理 |
|---|---|---|---|
| 停用后的生产执行 | PRD §3.3.3 要求拒绝新会话/API | production Run 解析允许 enabled / disabled；HTTP 文档 §5 也有此描述 | 登记冲突，不将当前行为升级为产品裁决；需单独确认并覆盖各执行入口 |
| 发布并启用门禁 | HTTP §3.3 要求 enable_agent=true 时门禁通过 | publish_workflow 校验 DSL 和引用空间，但未像 lifecycle enable 一样执行首次启用 gate 判断 | 不宣称两入口等价；门禁治理另行决策 |
| 生命周期前置状态 | 生命周期接口限定启停、下架和恢复路径 | 发布并启用直接写 enabled，未复用上述前置状态分支 | 状态图只描述对应接口，不能泛化为全局互斥规则 |
| 发布差异摘要 | UI 需要草稿与发布版的可靠比较 | 当前依赖前端 contentDirty 等局部信息 | 新摘要是拟议改动，不是已存在能力 |

本次只形成解释与展示规格，不修改运行授权、门禁、取消发布可用状态或数据库约束。上述差异需要单独批准修复范围；不能借两 Tag 优化隐式改变它们。

## 7. 展示设计的归属

架构层保留 L、P、E、S，不把它们合并存入一个 status。UI 可以利用 E=true 隐含 P=true 等关系压缩标签，但不能丢失底层事实。

原始 1～3 Tag 完整组合、最多两个 Tag 的映射、下架优先级、未知降级、接口增量建议和页面验收统一维护在 [工作流状态展示 spec](../../../plan/2026-09-14-workflow-status-display/spec.md)。其中“待更新发布”是拟议展示文案，不是新生命周期枚举，也不是异步发布任务状态。

后续若变更生命周期或生产访问规则，应同时核对 PRD、HTTP 契约及本说明；若仅修改标签布局与用词，则修改 UI spec，不反向重定义数据库字段。

## 8. 源码核查入口

- [ORM 模型](../../../../zeno-backend-agent/infra/database/models/agent_product.py)：三表关系、枚举与版本元数据。
- [服务](../../../../zeno-backend-agent/services/agent_product_service.py)：publish_workflow、transition_lifecycle、生产 Run 解析。
- [HTTP 数据投影](../../../../zeno-backend-agent/http_server/agent_http.py)与[请求模型](../../../../zeno-backend-agent/models/http_agent.py)：列表详情字段与发布参数默认值。
- [列表映射](../../../../zeno-frontend-agent/src/pages/product/AgentsPage.tsx)：toCardItem。
- [画布工具栏](../../../../zeno-frontend-agent/src/pages/product/agent/AgentWorkflowToolbar.tsx)与[画布页](../../../../zeno-frontend-agent/src/pages/product/agent/AgentWorkflowTab.tsx)：生命周期、脏标记与发布行为。

以上是核查时的实现快照，不代表运行时联调验收或数据库全量一致性检查已通过。
