# 教学笔记

记录用户偏好和教学过程中的工作笔记。

## 用户画像（来自首次问答）

- **目标**：上手维护开发，能独立完成功能修改和 bug 修复
- **水平**：Spring 初学者——会 Java 基础，但 Spring Boot/Security/Cloud 用得不多
- **优先方向**：微服务架构 → 认证授权体系
- **后续方向**：AI 对话协议、智能编码/MCP、数据持久化、可观测

## 教学策略

- 从 Spring Boot 基础起步，但每节课都锚定到项目真实代码
- 先建立「整体认知地图」（技术栈全景），再按优先方向逐层深入
- 对初学者，知识获取阶段降低难度；技能练习阶段引入适当难度
- 优先解释「为什么这样设计」，而非只罗列「用了什么」

## 课程规划（初步）

### 第一阶段：认知地图（1-2 课）
1. 技术栈全景与架构总览（本节直接回答用户最初的问题）
2. 模块依赖方向与分层约定

### 第二阶段：Spring 基础锚定到项目（2-3 课）
3. Spring Boot 自动配置与 application.yml（看 admin 的配置）
4. Controller / Service / Entity 三层（看 ProductController 链路）
5. 统一响应与异常处理（ResponseAdvice / ExceptionAdvice）

### 第三阶段：微服务架构（3-4 课）
6. Maven 多模块与依赖方向
7. Nacos 注册发现与配置中心
8. Spring Cloud Gateway 路由与过滤器
9. OpenFeign 跨服务调用（chat → admin）

### 第四阶段：认证授权体系（3-4 课）
10. JWT 机制与 Token 生命周期
11. 网关 AuthGlobalFilter 集中鉴权
12. 角色注解 @AdminAuth / @DeveloperAuth 与 SecurityConfig
13. JADP / SSO / OAuth2 / OIDC 第三方登录

## 进度

- [x] 0001 技术栈全景（参考文档）
- [x] 0002 Spring Boot 基础锚定到 admin（课程）
- [x] 0003 Maven 多模块与依赖方向
- [x] 0004 Nacos 注册发现与配置中心（含 OpenFeign + 身份头透传）
- [x] 0005 网关集中鉴权与 JWT 机制（认证授权体系第一节）
- [x] 0006 SSO 登录链路与 JADP/OAuth2/OIDC（认证授权体系第二节）
- [x] 0007 角色注解与方法级授权（认证授权体系第三节，完结）

## 消化期提问记录

### 2026-08-18：JADP 是什么 + JADP 和 JWT 的关系

学习者在消化第 1 课时对 JADP 产生疑问，随后追问 JADP 和 JWT 的关系。核心认知盲点：把不同层面的东西（系统 vs 格式）当成了同一层面来比较。

已整理到 `learning-records/0007-jadp-and-jwt-relationship.md`，要点：
- JADP 是企业身份认证平台（系统），JWT 是令牌格式（标准），不是协同关系而是上下游
- JADP 是身份源头，JWT 是广场内部传递身份的格式
- 不直接用 JADP token 当广场 token 的三个原因：性能（Redis 缓存不回源）、解耦（JADP 挂了不影响）、可控性（广场自己控制会话撤销）

### 2026-08-18：WebMVC 与 WebFlux 的区别及不能共存的原因

学习者在消化第 3 课 Maven 多模块时对「common 用 WebMVC、gateway 用 WebFlux、两者不能共存」产生疑问。

已整理到 `learning-records/0008-webmvc-vs-webflux.md`，要点：
- WebMVC 是阻塞式「一请求一线程」模型（Servlet/Tomcat），WebFlux 是非阻塞「事件循环」模型（Reactor/Netty）
- 项目按职责分工：admin/chat 做 CRUD 用 WebMVC，gateway 做转发用 WebFlux
- 不能共存两层原因：服务器层面（Tomcat vs Netty 二选一）+ 编程模型层面（阻塞 vs 非阻塞，过滤器链互不识别）
- 架构后果：gateway 不依赖 common，鉴权逻辑各写一套——是技术栈约束的必然结果

## 待确认

- 用户是否方便运行项目（需要 Redis + Nacos + 达梦）？如果不能本地运行，课程会更偏向读代码
