# 学习资源

这里收录学习 SmartSquare 后端所需的高质量、高可信资源。课程设计会优先引用这些资源。

## 项目内一手资料（最高优先级）

| 资源 | 路径 | 用途 |
| ---- | ---- | ---- |
| 系统架构文档 | `docs/ARCHITECTURE.md` | 模块关系图、认证流程、业务域、事件驱动、部署架构 |
| 后端规范入口 | `docs/standards/backend/README.md` | 编码规范总览，链接到各主题细则 |
| 项目结构规范 | `docs/standards/backend/project-structure.md` | 分层、命名、Lombok、依赖注入约定 |
| Controller 规范 | `docs/standards/backend/api-controller.md` | RESTful 路由、OpenAPI、分页 |
| Service 事务规范 | `docs/standards/backend/service-transaction.md` | Service 层、事务边界、领域事件 |
| 数据迁移规范 | `docs/standards/backend/data-flyway.md` | Flyway 迁移、Entity 映射 |
| 依赖管理规范 | `docs/standards/backend/dependency-management.md` | Maven POM 管理约定 |
| 安全日志规范 | `docs/standards/backend/security-logging.md` | 注释、日志、敏感数据处理 |
| 测试规范 | `docs/standards/backend/testing.md` | JUnit + jqwik 测试要求 |
| SSO 集成指南 | `docs/standards/sso-integration-guide.md` | 单点登录集成完整说明 |
| 父 POM | `pom.xml` | 全部依赖版本与插件配置 |
| AGENTS.md | `AGENTS.md` | 项目概览、模块目录、业务域速查、命令指引 |

## 外部官方文档（按学习路径排列）

### Spring 基础（初学者起点）

| 资源 | 链接 | 用途 |
| ---- | ---- | ---- |
| Spring Boot 官方文档 | https://docs.spring.io/spring-boot/docs/3.5.x/reference/htmlsingle/ | 自动配置、起步依赖、配置管理 |
| Spring Web MVC | https://docs.spring.io/spring-framework/reference/web/webmvc.html | Controller、请求映射、参数绑定 |
| Spring Security 参考 | https://docs.spring.io/spring-security/reference/ | 过滤器链、认证授权、方法安全 |
| Baeldung Spring 教程 | https://www.baeldung.com/spring-tutorial | 实战向的 Spring 教程合集 |

### 微服务与 Spring Cloud

| 资源 | 链接 | 用途 |
| ---- | ---- | ---- |
| Spring Cloud Gateway | https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway.html | 响应式网关、过滤器链、路由谓词 |
| Spring Cloud OpenFeign | https://docs.spring.io/spring-cloud-openfeign/reference/ | 声明式 HTTP 客户端 |
| Nacos 官方文档 | https://nacos.io/zh-cn/docs/v3/quick-start.html | 服务注册发现、配置中心 |
| Spring Cloud Alibaba | https://sca.aliyun.com/ | Nacos 整合 Spring Cloud 的参考 |

### 认证授权

| 资源 | 链接 | 用途 |
| ---- | ---- | ---- |
| JWT 介绍 (RFC 7519) | https://datatracker.ietf.org/doc/html/rfc7519 | JWT 标准规范 |
| jwt.io | https://jwt.io/ | JWT 在线调试、理解三段结构 |
| Spring Security OAuth2 | https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html | OAuth2 客户端、OIDC 登录 |
| OAuth 2.0 RFC 6749 | https://datatracker.ietf.org/doc/html/rfc6749 | 授权码流程等四种模式 |

### 数据持久化

| 资源 | 链接 | 用途 |
| ---- | ---- | ---- |
| MyBatis-Plus 官方文档 | https://baomidou.com/ | ORM 用法、条件构造器、分页 |
| Flyway 官方文档 | https://documentation.red-gate.com/fd | 数据库迁移脚本规范 |
| 达梦数据库文档 | https://eco.dameng.com/document/dm/zh-cn/start/ | DM8 方言与 JDBC 配置 |

### AI 协议（进阶方向）

| 资源 | 链接 | 用途 |
| ---- | ---- | ---- |
| AG-UI 协议 | https://docs.ag-ui.com/ | AI 对话流式事件协议 |
| A2A 协议 | https://a2a-protocol.org/ | Agent-to-Agent 通信协议 |
| Model Context Protocol | https://modelcontextprotocol.io/ | MCP Server 接入规范 |
| OpenAI Java SDK | https://github.com/openai/openai-java | LLM 调用客户端 |

## 资源使用原则

1. **一手资料优先**：项目内的 `docs/` 和源码是最权威的，外部文档用于补背景
2. **官方文档优先**：Spring / Nacos / MyBatis-Plus 的官方文档比博客更可靠
3. **版本对齐**：阅读外部文档时注意版本，本项目用 Spring Boot 3.5.x、Spring Cloud 2025.0.0、Nacos 3.2.1
