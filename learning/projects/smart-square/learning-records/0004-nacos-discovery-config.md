# 0004 - Nacos 注册发现与配置中心

**日期**：2026-08-17
**类型**：技能习得

## 学到了什么

1. **Nacos 双角色**：服务注册发现 + 配置中心，一个组件解决两个问题，减少基础设施数量
2. **服务注册**：`@EnableDiscoveryClient` + `spring.application.name` + Nacos 地址/命名空间/分组
3. **命名空间隔离**：本地用 `dev_ykh`，远程用 `public`，不同命名空间的服务实例完全互相不可见
4. **gateway 路由**：`lb://服务名` 通过 Nacos 解析实际地址并负载均衡；catch-all 路由 `Path=/**` 必须放最后
5. **SSE 超时处理**：`/chat/**` 路由设 `response-timeout: -1`，适配 AI 对话流式长连接
6. **服务间调用**：chat 用 OpenFeign 声明式调 admin 的 `/internal/**` 接口，像调本地方法
7. **internal 接口**：`/internal/**` 前缀 + `@PublicAccess`，仅集群内可达、不经网关对外暴露
8. **身份头透传**：`FeignIdentityHeaderInterceptor` 把网关注入的 `X-User-*` 可信头从 chat 透传给 admin
9. **可信头约定**：gateway 剥离伪造头 + 注入真实头，下游信任，网络策略保证不可绕过网关直达

## 关键洞察

- **服务发现解决的是「地址动态性」问题**：服务重启换端口、扩容加实例，写死地址维护不过来
- **命名空间是 Nacos 的一级隔离机制**，让多开发者本地服务与线上服务互不干扰——这是本地开发必须理解的
- **OpenFeign 的价值是「声明式」**：只写接口不写实现，运行时自动生成 HTTP 调用。调用方代码像调本地方法，实际是跨进程
- **身份传递链是端到端的**：gateway 注入 → chat 承载 → Feign 透传 → admin 还原。每一跳都不能断，否则 admin 不知道是谁在操作
- **可信头机制的安全性依赖网络策略**：下游信任 X-User-* 头的前提是外部无法绕过网关直达下游，这由部署层网络隔离保证

## 与上一节的关联

上一节讲了模块间的 Maven 编译期依赖方向，本节讲模块间的运行时关系——服务怎么注册、发现、调用。
两节课合在一起，构成了「微服务架构」的完整图景：编译期依赖 + 运行时通信。

## 待深入

- gateway 的 AuthGlobalFilter 具体怎么校验 JWT/JADP（下一节，进入认证授权体系）
- OpenFeign 的负载均衡策略（Ribbon vs Spring Cloud LoadBalancer）
- Nacos 作为配置中心的动态推送机制（本项目以 yml 为主，但 Nacos config 已配置）
