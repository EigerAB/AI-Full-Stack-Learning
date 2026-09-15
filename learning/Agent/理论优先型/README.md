# Agent 理论优先型学习路线

> 路线定位：从必要的数学、机器学习和大语言模型原理出发，逐步建立对推理、规划、工具使用、记忆、协作、评测和安全的理论解释。目标不是训练基础模型，而是让 Agent 平台的设计决策有理论依据、有实验验证、能说明适用边界。

## 一、这条路线解决什么问题

架构路线回答“系统应该怎样构建”，理论路线回答“为什么这种方法可能有效、什么时候会失效”。完成后应当能够解释：

- 为什么 LLM 能根据上下文学习任务，又为什么这种能力不稳定；
- 为什么 Agent Loop 可以产生多步行为，但不能保证得到最优计划；
- Tool Calling 与普通文本生成在模型层和 Runtime 层分别意味着什么；
- 为什么增加上下文、记忆、检索或 Agent 数量不一定提升效果；
- 如何把随机模型的表现转化为可测量、可比较的工程结果；
- 如何阅读 Agent 论文，并判断结论能否迁移到真实平台。

这不是一条纯理论路线。每个模块都需要完成“概念 → 最小实验 → Agent 架构映射”三个环节。

---

## 00｜数学与实验方法预备

### 学习边界

只学习理解 LLM 与 Agent 所需的数学工具，不展开与 Agent 工程关系较弱的完整数学体系。

### 课程目录

- 00.1 向量、矩阵、张量和维度
- 00.2 点积、余弦相似度和向量空间
- 00.3 概率、条件概率与贝叶斯公式
- 00.4 随机变量、期望、方差和常见分布
- 00.5 最大似然与交叉熵
- 00.6 熵、信息量与 KL 散度
- 00.7 梯度、链式法则和优化直觉
- 00.8 抽样、估计、置信区间和统计显著性
- 00.9 相关性、因果性与混杂变量
- 00.10 可复现实验：随机种子、环境和数据版本

### 实验与验收

- 用 Python / NumPy 实现 softmax、交叉熵和余弦相似度；
- 对一个随机任务重复采样，计算均值、方差和置信区间；
- 能解释为什么只运行一次不能证明某个 Agent 方案更好。

---

## 01｜机器学习与神经网络基础

### 课程目录

- 01.1 监督学习、自监督学习和强化学习
- 01.2 参数、特征、目标函数和泛化
- 01.3 训练集、验证集、测试集和数据泄漏
- 01.4 线性层、激活函数和多层网络
- 01.5 前向传播与反向传播
- 01.6 表示学习与 Embedding
- 01.7 过拟合、正则化和分布偏移
- 01.8 Batch、Epoch、Learning Rate 的含义
- 01.9 预训练、微调和推理的成本边界
- 01.10 概率模型的 Calibration

### 实验与验收

- 训练一个极小的文本分类模型；
- 观察训练误差、验证误差和分布外输入；
- 能区分模型权重中学到的知识与运行时 Context 中提供的信息。

---

## 02｜Transformer 与语言模型原理

### 课程目录

- 02.1 Tokenization 与词表
- 02.2 Token Embedding 与位置表示
- 02.3 Query、Key、Value 的直觉
- 02.4 Scaled Dot-product Attention
- 02.5 Multi-head Attention
- 02.6 Causal Mask 与自回归生成
- 02.7 Feed-forward Network、Residual 和 Normalization
- 02.8 Transformer Block 的数据流
- 02.9 Next-token Prediction
- 02.10 Context Window 与计算复杂度
- 02.11 KV Cache 与增量生成
- 02.12 Attention 不等于可靠推理或可解释因果

### 实验与验收

- 手算一个极小 Attention 示例；
- 用可视化实验观察不同 Token 的注意力权重；
- 对比无 KV Cache 与使用 Cache 的生成过程；
- 能画出一个请求从 Token 到下一 Token 概率分布的路径。

---

## 03｜LLM 训练、对齐与能力来源

### 课程目录

- 03.1 预训练数据和 Scaling 的基本逻辑
- 03.2 Instruction Tuning
- 03.3 Preference Learning 的目标
- 03.4 RLHF、RLAIF 和直接偏好优化的概念边界
- 03.5 Tool Use 与 Structured Output 能力如何形成
- 03.6 Reasoning Model 与普通生成模型的差异
- 03.7 蒸馏、量化和模型压缩
- 03.8 Fine-tuning、Prompting 和 RAG 的选择
- 03.9 Alignment Tax 与能力退化
- 03.10 Benchmark、污染和 Goodhart's Law
- 03.11 模型能力是统计分布，不是稳定接口
- 03.12 模型版本变化对 Agent Runtime 的影响

### 实验与验收

- 对同一任务比较 Zero-shot、Few-shot 和结构化约束；
- 建立模型能力卡，记录输入边界、失败模式和成本；
- 能说明为什么平台不能把模型行为当作确定性函数。

---

## 04｜解码、概率与结构化生成

### 课程目录

- 04.1 Logit、概率分布和 Token 选择
- 04.2 Greedy、Temperature、Top-k 和 Top-p
- 04.3 Stop Sequence 与 Finish Reason
- 04.4 Beam Search 的适用边界
- 04.5 输出长度、截断和续写
- 04.6 JSON Mode、Schema-constrained Decoding
- 04.7 Function Calling 的生成过程
- 04.8 Sampling 与任务稳定性
- 04.9 Self-consistency 与多次采样
- 04.10 Calibration、不确定性和拒答

### 实验与验收

- 固定 Prompt，改变采样参数并统计输出分布；
- 比较自由文本解析与 Schema 约束生成的失败率；
- 构造截断、空响应和非法 Tool Call，设计 Runtime 处理策略。

---

## 05｜In-context Learning 与 Prompt 原理

### 课程目录

- 05.1 Instruction、Demonstration 与 Context
- 05.2 Zero-shot、One-shot 和 Few-shot
- 05.3 模式匹配、任务归纳与上下文学习
- 05.4 Prompt 顺序、分隔和显著性
- 05.5 System、Developer、User 与 Tool Message 的约束层级
- 05.6 Chain-of-thought 的能力与风险
- 05.7 Decomposition 与 Least-to-most
- 05.8 Self-reflection、Critique 和 Revision
- 05.9 Prompt Template 与变量边界
- 05.10 Prompt Injection 的模型层原因
- 05.11 Prompt 优化必须基于评测集
- 05.12 Context Engineering 与 Prompt Engineering 的区别

### 实验与验收

- 为同一任务设计三种 Prompt，并通过固定数据集比较；
- 测试示例顺序、无关上下文和冲突指令造成的变化；
- 能解释 Prompt 改进为什么可能只是在测试样例上过拟合。

---

## 06｜智能体理论基础

### 课程目录

- 06.1 Agent、Environment、Observation 与 Action
- 06.2 State 与 Partial Observation
- 06.3 Policy、Reward 和 Utility
- 06.4 MDP 与 POMDP 的基本模型
- 06.5 Model-based 与 Model-free 方法
- 06.6 Planning、Acting 与 Learning 的区别
- 06.7 LLM Agent 是怎样的近似 Agent
- 06.8 ReAct：Reasoning 与 Acting 交错
- 06.9 Reflexion / Critique 类方法
- 06.10 Agent Loop 与状态机
- 06.11 Autonomy、可控性和终止条件
- 06.12 “Agentic”不是二元属性

### 实验与验收

- 将一个工具任务形式化为 Observation、State、Action 和 Goal；
- 实现最小 ReAct Loop，并记录完整 Trajectory；
- 指出 LLM Agent 与经典 MDP 假设不一致的地方；
- 能区分 Runtime 状态、模型上下文和环境真实状态。

---

## 07｜搜索、规划与决策

### 课程目录

- 07.1 状态空间与搜索树
- 07.2 BFS、DFS、Best-first 和 A* 的基本思想
- 07.3 启发式函数与代价
- 07.4 Plan-and-execute
- 07.5 Tree / Graph of Thoughts 类方法
- 07.6 规划器与执行器分离
- 07.7 在线规划与重规划
- 07.8 不确定环境中的决策
- 07.9 Verification 与 Search 的关系
- 07.10 预算约束下的探索—利用权衡
- 07.11 何时应该使用确定性 Workflow
- 07.12 为什么“思考更多”不一定更好

### 实验与验收

- 用同一任务比较直接回答、ReAct 与 Plan-and-execute；
- 记录搜索宽度、调用次数、成功率、延迟和费用；
- 构造环境变化导致原计划失效的案例；
- 能根据任务特征选择 Agent Loop、Planner 或 Workflow。

---

## 08｜Tool Use、Grounding 与环境交互

### 课程目录

- 08.1 语言空间与行动空间
- 08.2 Affordance：工具向模型暴露什么能力
- 08.3 Tool Description 与 Schema 的归纳偏置
- 08.4 Tool Selection、Argument Generation 和 Result Interpretation
- 08.5 Grounding 与外部事实
- 08.6 Observation 的信息损失与噪声
- 08.7 Tool Error 如何影响后续决策
- 08.8 幂等、副作用和不可逆行动
- 08.9 Human Approval 与安全决策
- 08.10 Tool Learning 与能力发现
- 08.11 MCP 等工具协议解决什么、不解决什么
- 08.12 Tool Use 的分层评测

### 实验与验收

- 改写 Tool 名称、描述和 Schema，测量选择准确率；
- 向 Observation 注入噪声、冲突和恶意内容；
- 将 Tool 失败拆成选择、参数、执行和解释四层指标；
- 能说明 Tool Calling 成功不等于业务操作成功。

---

## 09｜Context、Memory 与 RAG 理论

### 课程目录

- 09.1 Working Context 与 External Memory
- 09.2 Parametric 与 Non-parametric Knowledge
- 09.3 Semantic、Episodic 和 Procedural Memory
- 09.4 写入、检索、巩固和遗忘
- 09.5 Embedding 空间与语义相似度
- 09.6 稠密检索、稀疏检索和混合检索
- 09.7 Chunking 的信息边界
- 09.8 Query–Document Relevance
- 09.9 Rerank 与多阶段检索
- 09.10 Retrieval Precision / Recall
- 09.11 Groundedness、Faithfulness 与 Citation
- 09.12 Agentic RAG 与迭代检索
- 09.13 Context 污染和 Memory 冲突
- 09.14 长上下文、RAG 与摘要的取舍

### 实验与验收

- 在一个小语料上实现 BM25 与向量检索并比较；
- 分别测量检索命中、排序质量和最终回答质量；
- 构造陈旧记忆、冲突记忆和错误检索；
- 能解释为什么“召回了相关文档”仍可能产生错误回答。

---

## 10｜Multi-Agent 与协作理论

### 课程目录

- 10.1 单 Agent 的能力边界
- 10.2 任务分解、角色分工和专业化
- 10.3 中央式 Supervisor 与去中心化协作
- 10.4 Handoff、Agent-as-Tool 与消息通信
- 10.5 Contract Net、Blackboard 和 Actor 思想
- 10.6 Shared State 与 Message Passing
- 10.7 Coordination Cost
- 10.8 信息不对称与上下文隔离
- 10.9 Consensus、Conflict 和 Aggregation
- 10.10 Credit Assignment
- 10.11 Emergent Behavior 与级联错误
- 10.12 多 Agent 是否真的优于单 Agent
- 10.13 A2A 类协议的理论边界
- 10.14 多 Agent 评测设计

### 实验与验收

- 对同一数据集比较单 Agent、Supervisor 和 Debate；
- 统计质量、Token、延迟、失败传播和协调开销；
- 构造 Agent 间错误信息传播实验；
- 只有在实验显示净收益时才保留 Multi-Agent 设计。

---

## 11｜Agent Evaluation 与实验科学

### 课程目录

- 11.1 Construct：到底想测量什么
- 11.2 Dataset 的代表性与覆盖率
- 11.3 Unit、Component、Trajectory 和 End-to-end Eval
- 11.4 Pass Rate、Precision、Recall 与 Ranking Metric
- 11.5 Pairwise Comparison 与 Rubric
- 11.6 Model Judge 的偏差与校准
- 11.7 Human Evaluation 与标注一致性
- 11.8 随机性、重复采样和置信区间
- 11.9 Baseline、Ablation 与对照实验
- 11.10 Offline Eval 与 Online Eval
- 11.11 Distribution Shift 与 Benchmark 污染
- 11.12 Regression、门槛和发布决策
- 11.13 Failure Taxonomy 与错误归因
- 11.14 评测驱动开发

### 实验与验收

- 建立一个包含正常、边界和对抗案例的数据集；
- 为 Tool、RAG、Trajectory 和 Final Answer 分层评分；
- 对一种 Agent 改进做消融实验；
- 报告均值、方差、置信区间、成本和失败类型，而不是只报一个总分。

---

## 12｜可靠性、安全与对齐

### 课程目录

- 12.1 能力、目标和约束的错配
- 12.2 Hallucination 与不确定性
- 12.3 Reward Hacking 与 Specification Gaming
- 12.4 Prompt Injection 与间接注入
- 12.5 Confused Deputy 与权限升级
- 12.6 数据外泄和跨租户污染
- 12.7 Least Privilege 与 Capability Security
- 12.8 Sandbox 与环境隔离
- 12.9 Guardrail 的分类与覆盖边界
- 12.10 Human Oversight 与可逆性
- 12.11 Defense in Depth
- 12.12 Threat Modeling 与 Red Team
- 12.13 安全性、可用性和成本的权衡
- 12.14 为什么模型对齐不能替代系统安全

### 实验与验收

- 为 Engineering Copilot 建立 Threat Model；
- 构造直接注入、间接注入和恶意 Tool Result；
- 比较 Prompt Guardrail、规则、权限和沙箱各自能阻断什么；
- 证明敏感动作失败时保持安全，而不是默认继续执行。

---

## 13｜Agent 系统与分布式系统理论连接

### 课程目录

- 13.1 Agent Run 作为长事务
- 13.2 State Machine 与 Event Sourcing
- 13.3 At-most-once、At-least-once 与重复执行
- 13.4 Idempotency 与去重
- 13.5 Checkpoint、Snapshot 与 Replay
- 13.6 Timeout、Retry、Backoff 和 Circuit Breaker
- 13.7 Partial Failure 与 Cancellation Propagation
- 13.8 Queue、Worker 和 Lease
- 13.9 Consistency、Availability 与状态可见性
- 13.10 Saga 与补偿
- 13.11 多租户与资源隔离
- 13.12 Trace、Causality 与故障归因
- 13.13 Durable Execution 的理论边界
- 13.14 为什么 Agent 平台首先是分布式系统

### 实验与验收

- 模拟 Worker 在工具执行前后崩溃；
- 对一个有副作用的任务设计幂等和补偿策略；
- 从 Event Log 恢复 Run State；
- 能判断一次失败是模型随机性、编排缺陷还是分布式系统问题。

---

## 14｜论文阅读、复现与知识更新

### 课程目录

- 14.1 如何读 Abstract、Method、Experiment 和 Limitation
- 14.2 区分论文贡献、实现技巧和宣传结论
- 14.3 Baseline 是否公平
- 14.4 Dataset 和 Metric 是否支持结论
- 14.5 Ablation 是否验证了关键机制
- 14.6 统计结果与实际工程收益
- 14.7 从论文伪代码到最小复现
- 14.8 无法复现时如何定位差异
- 14.9 从实验结果提取架构决策
- 14.10 建立 Agent 论文主题地图
- 14.11 跟踪框架与协议变化
- 14.12 写一份可供团队决策的研究报告

### 建议复现主题

不要一次追很多论文，每类选择一个代表问题：

- Reason + Act；
- Planning / Search；
- Reflection / Verification；
- Tool Learning；
- Memory；
- Agentic RAG；
- Multi-Agent Collaboration；
- Agent Evaluation；
- Agent Security。

### 实验与验收

- 完成至少两个最小复现；
- 对其中一个做消融实验；
- 记录无法复现或与论文结论不同的结果；
- 将研究结论转换成一条明确的架构决策，而不是停在论文摘要。

---

## 二、学习阶段与架构路线映射

| 理论阶段 | 理论模块 | 对应架构模块 | 阶段能力 |
|---|---|---|---|
| A：模型基础 | 00～05 | 架构 00～01 | 理解模型接口背后的概率、生成和上下文机制 |
| B：Agent 决策 | 06～08 | 架构 02～03 | 解释 Loop、规划和 Tool Use 的能力边界 |
| C：知识与协作 | 09～10 | 架构 04～09 | 理解 Memory、RAG 和 Multi-Agent 的收益与代价 |
| D：验证与安全 | 11～12 | 架构 10～12 | 用实验评价系统，并建立分层防御 |
| E：平台原理 | 13 | 架构 13 | 把 Agent Runtime 放入分布式系统模型 |
| F：研究能力 | 14 | 架构 14～15 | 能阅读、复现并转化 Agent 研究成果 |

## 三、推荐学习顺序

理论内容不应连续学完再写代码。推荐交错推进：

1. 完成理论 00～02，获得阅读 LLM 资料的最低基础；
2. 同步学习架构 00～02，立即手写 Agent Loop；
3. 学习理论 03～08，用实验解释模型行为、规划和工具调用；
4. 进入架构 03～09，构建 Tool、Context、Memory、Workflow 和 Multi-Agent；
5. 学习理论 09～13，为评测、安全和平台可靠性建立理论模型；
6. 架构路线毕业项目与理论 14 的论文复现同步进行。

如果某个数学推导不能帮助你解释模型行为、设计实验或做出架构选择，先记录为选修，不阻塞主线。

## 四、每个理论主题的笔记模板

```markdown
# 主题名称

## 它试图解释什么问题

## 最少前置知识

## 核心假设

## 机制或推导

## 最小实验

## 实验结果与反例

## 对 Agent 架构的影响

## 适用边界

## 与 Zeno / Mini Runtime 的对应位置

## 仍未解决的问题
```

## 五、完成标准

完成理论路线不以看完课程或论文数量衡量，而以以下能力衡量：

- 可以用自己的语言解释核心机制，不依赖框架术语；
- 可以把一个模糊的 Agent 问题转化为可测量假设；
- 可以设计 baseline、对照组和消融实验；
- 可以报告随机性与失败分布，而不是挑选成功案例；
- 可以识别论文结论依赖的数据、模型和环境假设；
- 可以把理论结论映射到具体 Runtime 接口和平台策略；
- 面对新框架、新论文或新模型时，知道应该验证哪些不变量。

## 六、暂缓内容

以下主题可在主线完成后选修，不作为 Agent 平台开发的前置条件：

- 从零训练大规模基础模型；
- 深入 CUDA Kernel 和分布式训练；
- 完整强化学习算法谱系；
- 复杂形式化验证；
- 追逐所有 Prompt 技巧和 Agent 论文；
- 为了“懂理论”而进行与架构决策无关的长篇数学推导。

