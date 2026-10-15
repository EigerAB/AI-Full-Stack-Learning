# RESOURCES

本案例的一手资料就是真实代码库本身，按可信度排序：

## 一手源码（最高优先级，一切以代码为准）

| 资源 | 路径 | 用途 |
|---|---|---|
| smart-square-back 仓库 | `D:\projects\smart-square-back` | 案例代码所在仓库 |
| 反馈写入入口 | `smart-square-chat/.../controller/ChatMessageController.java` | `PUT /chat/messages/runs/{runId}/feedback` |
| 反馈写入服务 | `smart-square-chat/.../service/chat/ChatMessageFeedbackService.java` | 事务 + 幂等 + 行锁 + 快照 |
| 反馈查询服务 | `smart-square-chat/.../service/chat/feedback/ChatFeedbackQueryService.java` | 管理端列表/统计/详情 |
| 管理端控制器 | `smart-square-chat/.../controller/ChatFeedbackController.java` | `GET /chat/feedbacks/**` |
| 实体 / Mapper | `entity/ChatFeedback.java`、`mapper/ChatFeedbackMapper.java` | MyBatis-Plus 映射 |
| 建表迁移 | `smart-square-chat/src/main/resources/db/migration/V1.0.10__add_chat_feedback_analysis.sql` | Flyway 版本化 DDL |
| 模块说明 | `smart-square-back/AGENTS.md` | 仓库整体架构与约定 |

## 官方文档（查概念用）

| 资源 | 链接 | 用途 |
|---|---|---|
| MyBatis-Plus 文档 | https://baomidou.com/ | `BaseMapper`、`QueryWrapper`、分页插件 |
| Spring 事务文档 | https://docs.spring.io/spring-framework/reference/data-access/transaction.html | `@Transactional` 语义 |
| Flyway 文档 | https://documentation.red-gate.com/flyway/ | 版本化迁移 |
| 达梦 DM8 文档 | https://www.dameng.com/ | `INSTR` 等方言差异 |

## 本地可验证环境

- 达梦库：`docker exec -i smart-square-dm` 内 `/opt/dmdbms/bin/disql`（`SYSDBA`/`SYSDBA001`@5236，schema `SMARTSQUARE`）
- 后端服务：`localhost:8081`（`make run` 启动）
