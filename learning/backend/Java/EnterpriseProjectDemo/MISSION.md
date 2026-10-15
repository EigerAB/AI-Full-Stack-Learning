# MISSION

## 学习者背景

- 已会：Python FastAPI、JavaSE 基础、SQL 基础
- 在学：Java 企业级后端（Spring Boot 3 + MyBatis-Plus），主线课程在 `../lessons/`
- 参考项目：日常用 `ruoyi-vue-pro`；本目录以真实工作项目 **smart-square-back**（Spring Boot 3.2 + DM8 + MyBatis-Plus + Flyway + 微服务网关）为实战教材

## 使命

**通过解剖真实企业项目里的完整功能链路，学会"读后端代码"和"设计数据库表"**：看到一个页面/接口，能自己找到它从 HTTP 入口到数据库行的每一层代码，并理解每层为什么存在、为什么这么写。

## 当前案例

`FM_SQUARE_CHAT_FEEDBACK`（AI 对话反馈分析）：用户在 aichat 点赞/点踩 → 网关 → chat 微服务 → 达梦库；管理端"监控管理 → 反馈分析"页做查询与统计。

选它的原因：链路短而完整（一写四读），同时覆盖企业后端最常见的几个真实设计点——分层、事务、幂等、行锁、快照表、组织隔离、分页聚合。

## 判断"学会了"的标准

1. 给一个没见过的表名/接口名，能在 10 分钟内自己顺出 Controller→Service→Mapper→表 的完整链路
2. 能说出每层各自"只负责什么、不负责什么"
3. 能解释快照表为什么存在、`ASSOCIATION_STATUS` 这类字段解决什么问题
