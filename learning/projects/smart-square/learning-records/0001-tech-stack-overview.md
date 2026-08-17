# 0001 - 技术栈全景认知建立

**日期**：2026-08-17
**类型**：认知里程碑

## 学到了什么

建立了 SmartSquare 后端的整体技术栈认知地图：

1. **项目本质**：AI 开放平台，统一管理分发 LLM/MCP/Agent/Skill/Tool，提供 AI 编程助手和智能对话助手
2. **架构演进**：从 dal/server/bootstrap 三层单体 → 网关 + 主服务 + 对话微服务 + 公共库 + gRPC 契约的微服务架构
3. **五层技术栈**：
   - 网关层：Spring Cloud Gateway (WebFlux) + Nacos
   - 业务层：Spring Boot 3.5.9 + WebMVC + Security + OpenFeign + Spring AI + gRPC
   - 公共层：Hutool + Caffeine + OkHttp + MinIO
   - 数据层：MyBatis-Plus + 达梦 DM8 + Flyway + Redis
   - 运维层：Maven 多模块 + Spotless + jqwik + Docker/Helm
4. **关键设计决策的「为什么」**：网关用 WebFlux 而业务用 WebMVC、chat 拆独立微服务、Feign 不直连数据库、gRPC 做意图识别、达梦因信创、MyBatis-Plus 因 SQL 可控

## 关键洞察

- **不是技术堆砌，而是按职责分工**：WebFlux/WebMVC、Feign/gRPC、Caffeine/Redis 的选择都遵循「按场景选工具」
- **演进式架构**：现有结构是从单体演进而来，理解演进历史有助于理解为什么 common 模块里有 dal 的痕迹
- **信创约束塑造技术选型**：达梦数据库这一约束直接影响了 ORM（选 MyBatis-Plus 而非 JPA）和迁移工具（需要达梦适配包）

## 待深入

- 每个技术在实际代码里怎么用（后续课程逐层展开）
- 微服务拆分的边界是怎么划的（下一阶段重点）
- 认证授权的完整链路（第四阶段重点）
