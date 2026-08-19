# 0008 - WebMVC 与 WebFlux 的区别及不能共存的原因

**日期**：2026-08-18
**类型**：概念澄清（来自学习者在消化第 3 课 Maven 多模块时的提问）

## 背景

学习者在消化第 3 课时对「common 依赖 spring-boot-starter-web（WebMVC），而 gateway 用 spring-cloud-starter-gateway-server-webflux，两者不能共存」产生疑问。核心问题：这两个架构分别是干嘛的？区别是什么？为什么不能共存？

## WebMVC 与 WebFlux 分别是什么

两者都是 Spring 框架的 Web 技术栈，都用来处理 HTTP 请求，但底层模型完全不同。

### WebMVC（Spring 经典的阻塞式 Web 框架）

基于 **Servlet API**，核心模型是「一个请求一个线程」：

```
请求进来 → Tomcat 从线程池分配一个线程 → 线程执行 Controller 方法
→ 调 Service → 查数据库（线程阻塞等待）→ 拿到结果 → 返回响应 → 线程释放回池
```

项目里 admin 和 chat 用 WebMVC。方法直接返回对象：

```java
@RestController
@RequestMapping("/products")
public class ProductController {
    @PostMapping
    public ProductResult createProduct(@RequestBody @Valid CreateProductParam param) {
        return productService.createProduct(param);  // 阻塞式调用，直接返回对象
    }
}
```

### WebFlux（Spring 的响应式 Web 框架）

Spring 5 引入，基于 **Reactor**（响应式流规范），不依赖 Servlet API。核心模型是「少量线程处理大量请求」：

```
请求进来 → 事件循环线程接收 → 发起异步操作（不阻塞）→ 线程立刻去处理下一个请求
→ 异步操作完成回调 → 同一个或另一个事件循环线程处理结果 → 返回响应
```

项目里 gateway 用 WebFlux。方法返回 `Mono<T>`：

```java
public class AuthGlobalFilter implements GlobalFilter {
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Mono<AuthenticatedUser> userMono = resolveUser(path, token);
        return userMono.doOnNext(user -> injectHeaders(builder, user))
                .then(Mono.defer(() -> chain.filter(...)))
                .onErrorResume(e -> unauthorized(exchange, path, e));
    }
}
```

`Mono` 是 Reactor 的类型，表示「未来会产出一个值」。代码不阻塞，而是声明一个操作流水线。

## 核心区别

| | WebMVC | WebFlux |
|---|---|---|
| 底层 | Servlet API（Tomcat） | Reactor（Netty） |
| 线程模型 | 一个请求一个线程（阻塞） | 少量事件循环线程（非阻塞） |
| 返回值 | 直接返回对象 `ProductResult` | 返回 `Mono<T>` / `Flux<T>` |
| 思维方式 | 命令式——一步一步做 | 声明式——描述数据流 |
| 适合场景 | CRUD 业务、重数据库操作 | 高并发转发、流式 IO |
| 学习曲线 | 低，直观 | 高，响应式编程难懂 |
| 成熟度 | 非常成熟，生态完善 | 相对较新，调试难 |

### 阻塞 vs 非阻塞的具体区别

**WebMVC（阻塞）**——查 Redis 时线程干等着：
```
线程1: 收到请求A → 调 Redis → [等 5ms] → 拿到结果 → 返回
                              ↑ 这 5ms 线程1 什么也干不了
```

**WebFlux（非阻塞）**——查 Redis 时线程去干别的：
```
事件循环线程: 收到请求A → 发起 Redis 查询（不等）→ 去处理请求B → 请求C...
              ← Redis 回来了 → 处理请求A 的结果 → 返回
```

如果同时来 10000 个请求：
- WebMVC 需要 10000 个线程（或排队），每个线程占约 1MB 栈空间 → 10GB 内存
- WebFlux 用几个事件循环线程就能扛住，因为线程不在 IO 上等待

## 项目里为什么各用各的

按职责分工选择技术栈：

- **admin/chat 用 WebMVC**——CRUD 业务，大量数据库操作（MyBatis-Plus 是阻塞的），用 WebMVC 简单直观。阻塞式 ORM 和 WebMVC 天然匹配。
- **gateway 用 WebFlux**——网关的核心工作是转发：收请求 → 鉴权 → 转给下游 → 返回。它自己不做数据库操作，全是 IO（查 Redis、调 SSO、转发 HTTP）。非阻塞模型让网关用极少线程扛住高并发转发，这是 Spring Cloud Gateway 的设计初衷。

gateway 的 pom.xml 注释明确写了：
```xml
<!-- Spring Cloud Gateway Server WebFlux（基于 WebFlux，注意：本模块不能引入 spring-boot-starter-web
     或 Servlet MVC 依赖） -->
<artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
```

## 为什么不能共存

### 第一层：技术冲突——Servlet vs Netty

WebMVC 启动时需要一个 **Servlet 容器**（Tomcat），基于 Servlet API 处理请求。
WebFlux 启动时需要一个 **Reactive 服务器**（Netty），基于 Reactor 处理请求。

Spring Boot 启动时只能选一个 Web 服务器类型。同时引入 `spring-boot-starter-web`（带 Tomcat）和 `spring-boot-starter-webflux`（带 Netty），Spring Boot 会困惑：到底用哪个服务器？哪个 `DispatcherHandler`？哪个过滤器链？

虽然 Spring Boot 有自动配置优先级规则（WebMVC 会赢），但这会导致 WebFlux 的组件不工作——Spring Cloud Gateway 的路由过滤器全部基于 Reactor，没有 Reactor 运行时它们就是死代码。

### 第二层：编程模型冲突——阻塞 vs 非阻塞

两套过滤器链互不识别：
- WebMVC 的 `Filter`（如 `TrustedHeaderAuthenticationFilter`）是 `javax.servlet.Filter`，只在 Servlet 容器里生效
- WebFlux 的 `GlobalFilter`（如 `AuthGlobalFilter`）是 Reactor 的，只在 Netty 里生效

这就是为什么项目把 common 里的 `TrustedHeaderAuthenticationFilter`（WebMVC 的 Servlet Filter）放在 admin/chat 用，而 gateway 自己写了一套独立的鉴权逻辑（WebFlux 的 GlobalFilter）——**不是不想复用，是技术栈不兼容，复用不了**。

### 总结图

```
WebMVC 世界                    WebFlux 世界
─────────────                  ─────────────
Servlet API                    Reactor
Tomcat (Servlet 容器)           Netty (Reactive 服务器)
一个请求一个线程                  少量事件循环线程
阻塞式调用                      非阻塞 Mono/Flux
javax.servlet.Filter           GlobalFilter / WebFilter
@RestController 返回对象         @RestController 返回 Mono

     admin / chat                    gateway
     ────────────                    ───────
     TrustedHeaderAuthenticationFilter  AuthGlobalFilter
     ResponseAdvice (Servlet)          自己写响应包装
     SecurityConfig (Servlet)          自己写鉴权

两套世界不能混用 → gateway 不依赖 common → 鉴权逻辑各写一套
```

## 一句话总结

**WebMVC 是阻塞式的「一请求一线程」模型，WebFlux 是非阻塞的「事件循环」模型**。项目按职责分工：admin/chat 做 CRUD 用 WebMVC，gateway 做转发用 WebFlux。两者不能共存是因为 Servlet 容器和 Netty 服务器二选一，且阻塞和非阻塞的编程模型根本不兼容——这就是 gateway 不依赖 common、鉴权逻辑各写一套的根本原因。

## 关键洞察

- **技术选型按职责分工**：CRUD 业务用 WebMVC（简单），转发网关用 WebFlux（高并发），不是全用一种
- **不能共存是两层冲突**：服务器层面（Tomcat vs Netty 二选一）+ 编程模型层面（阻塞 vs 非阻塞不兼容）
- **架构后果**：gateway 不依赖 common，鉴权逻辑各写一套——这不是设计缺陷，是技术栈约束的必然结果
