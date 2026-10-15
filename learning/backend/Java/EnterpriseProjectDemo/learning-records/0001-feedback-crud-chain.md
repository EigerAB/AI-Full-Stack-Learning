# 0001 反馈功能 CRUD 链路定位

日期：2026-10-08

## 情景

学习后端+数据库，选定 smart-square-back 的 `FM_SQUARE_CHAT_FEEDBACK`（反馈分析）作为第一个解剖案例。

## 已确认的事实

- 数据在 `smart-square-dm` 达梦容器（localhost:5236），schema `SMARTSQUARE`，密码 `SYSDBA001`；Navicat 17.2 不含达梦连接（需 17.3+），本机 localhost 需写 `127.0.0.1`（DM 端口只绑 IPv4）。
- 写入：`PUT /chat/messages/runs/{runId}/feedback` → `ChatMessageController` → `ChatMessageFeedbackService.updateFeedback`（事务 + 幂等 check-lock-check + 快照 upsert + 消息表冗余列同步）。
- 查询：`GET /chat/feedbacks/**` → `ChatFeedbackController`（@AdminAuth）→ `ChatFeedbackQueryService`（共用 query() 构造器 + ORG_CODE 组织隔离 + selectPage/selectMaps）。
- 无 Update/Delete：反馈一次写入不可改不可删，`ASSOCIATION_STATUS` 处理失联。

## 非显然的教训（值得复习）

1. **快照 vs join**：分析型数据要存评价时刻的问答副本，因为原消息可删。
2. **check-lock-check**：幂等查询挡重复，行锁挡并发，锁内复查堵间隙——缺一环都不对。
3. **单条聚合 SQL**：统计卡的分子分母必须来自同一快照，否则比率算歪。
4. **组织隔离焊在构造器**：所有查询强制 `ORG_CODE`，不靠每个接口自觉。
5. **逗号串的代价**：`PROBLEM_TYPES` 只能 INSTR 匹配，走不了索引——小数据量的取舍。

## ZPD 评估（下一步教什么）

学习者处于"能看懂分层，但还不熟 Spring/MyBatis-Plus 习惯用法"阶段。后续候选课题：

- 0003 Flyway 迁移：V1.0.10 脚本逐行读（DDL 设计：为什么这些列、默认值、索引）
- 0004 @Transactional 与行锁：FOR UPDATE 语义、隔离级别、何时会死锁
- 0005 DTO/Param/Result 三层数据对象的分工与转换（convertFrom 模式）
- 横向：对照 ruoyi-vue-pro 找同类链路，对比写法差异

## 状态

MISSION.md 已建立；lessons/0001、0002 已产出；reference/feedback-feature-chain.html 已产出。
