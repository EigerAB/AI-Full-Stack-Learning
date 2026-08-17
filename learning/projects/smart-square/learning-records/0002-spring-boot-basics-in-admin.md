# 0002 - Spring Boot 基础锚定到 admin 模块

**日期**：2026-08-17
**类型**：技能习得

## 学到了什么

1. **启动类注解**：`@SpringBootApplication` 是三合一（配置+自动配置+组件扫描），`@EnableDiscoveryClient`/`@EnableFeignClients`/`@MapperScan`/`@EnableConfigurationProperties` 各开启一块能力
2. **配置分层**：`application.yml` 只选 profile，`application-{profile}.yml` 承载配置，`${ENV:default}` 占位符让环境变量覆盖默认值
3. **三层架构**：Controller（路由+鉴权）→ Service（业务+事务）→ Mapper（数据）→ Entity（持久化容器）
4. **构造器注入**：`@RequiredArgsConstructor` + `private final`，禁用 `@Autowired`
5. **统一响应**：`ResponseAdvice` 实现 `ResponseBodyAdvice`，在框架层自动把业务对象包成 `Response<T>`，Controller 不手动包装
6. **统一异常**：`ExceptionAdvice` 三道防线——BusinessException（业务）/ MethodArgumentNotValidException（校验）/ Exception（兜底），禁止用 null 表示业务错误

## 关键洞察

- **约定优于配置**是 Spring Boot 的核心哲学：按约定放注解和包结构，基础设施自动就位
- **关注点分离**的三个体现：Controller 不包响应（ResponseAdvice 管）、Service 不写路由（Controller 管）、Entity 不写业务（Service 管）
- **异常 vs null**：用异常表示业务错误比用 null 更安全——null 会让调用方忘记判空且丢失错误信息，异常强制处理且携带 ErrorCode

## 与上一节的关联

上一节建立了五层技术栈全景，本节深入「业务层」的 Spring Boot 基础机制。下一节将从「公共层」的 Maven 多模块开始，进入微服务架构阶段。

## 待深入

- `@AdminAuth` 注解的 AOP 实现（认证授权阶段）
- `@Transactional` 事务边界的细节（Service 事务规范文档）
- `ResponseAdvice` 为什么要排除 `SseEmitter`/`Flux`/`Mono`（AI 对话阶段，SSE 流式响应不能被包装）
