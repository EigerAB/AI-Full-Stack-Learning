# 0005 - 网关集中鉴权与 JWT 机制

**日期**：2026-08-17
**类型**：技能习得

## 学到了什么

1. **认证 vs 授权**：认证=你是谁（网关做），授权=你能做什么（下游做）
2. **AuthGlobalFilter 完整链路**（9 步）：剥离伪造头→注入 traceId→放行登录端点→提取 token→无 token 放行→有 token 校验→注入可信头→失败回 401→转发
3. **JWT 机制**：HS256 签名；只携带 sessionId，不携带用户信息；JwtVerifier 验签+检查过期
4. **Session-in-Redis 模式**：真实身份存 Redis（key: `square:session:{sessionId}`），JWT 只是指针；登出删 Redis 即撤销——解决 JWT 不可撤销问题
5. **可信头注入**：X-User-Id/Type/OrgCode/Name/Roles/RoleNames；中文 URL 编码；角色名先编码再 join
6. **防伪造**：网关先剥离外部 X-User-* 头再注入校验过的真实身份；安全前提是外部不可绕过网关直达下游
7. **无 token 放行 vs 有 token 失效**：无 token 交下游判断公开/受保护；有 token 但会话失效回 401 不降级（否则前端停在残废状态）
8. **TrustedHeaderAuthenticationFilter**：下游从头还原身份，写入 SecurityContext + ContextHolder（ThreadLocal），finally 清理防身份泄漏
9. **@AdminAuth 真面目**：= @PreAuthorize("isAuthenticated()")，认证级拦截，细粒度角色授权待改造
10. **SecurityConfig 三层放行**：硬编码白名单 + @PublicAccess 扫描 + anyRequest().authenticated() 兜底

## 关键洞察

- **Session-in-Redis + JWT-as-pointer 是兼顾无状态和可撤销的折中设计**：纯 JWT 不可撤销，纯 Session 不够轻量；JWT 当指针、Redis 存身份，登出删 Redis 即失效
- **「网关做认证，下游做授权」是职责分离**：网关不知道哪些端点公开，所以无 token 不拦截；下游用 SecurityConfig + @PublicAccess 做细粒度授权
- **「有 token 但失效回 401 不降级」是 UX 驱动的安全决策**：技术上可以降级放行，但会导致前端永远等不到 401，用户体验恶化
- **ThreadLocal 清理不是可选的**：线程池复用线程，不清理 = 身份泄漏，这是 Servlet 容器线程模型的固有约束
- **@AdminAuth 当前只做认证级拦截**：注释明确写「过渡期」，说明细粒度授权是已知技术债，后续要改造

## 与上一节的关联

上一节讲了服务间怎么互相发现和调用（Nacos + Feign + 身份头透传），本节深入身份头是怎么来的——网关的完整认证链路。下一节将讲登录是怎么发生的（SSO/JADP/OAuth2/OIDC）。

## 待深入

- SSO 登录链路：ticket 换 token 的完整流程（下一节）
- JADP 是什么、怎么对接（下一节）
- OAuth2/OIDC 第三方登录（下一节）
- OpenApiAuthFilter 开放 API 的 API Key 鉴权（另一条认证路径）
