# 0007 - 角色注解与方法级授权

**日期**：2026-08-18
**类型**：课程（认证授权体系第三节，也是最后一节）

## 核心内容

### 完整授权链路

```
浏览器 → AuthGlobalFilter（网关认证）→ TrustedHeaderAuthenticationFilter（身份还原）
→ SecurityConfig（路径级授权）→ @PreAuthorize（方法级授权）→ Controller
```

### 1. TrustedHeaderAuthenticationFilter——身份还原

网关注入的 `X-User-*` 头是 HTTP 头，Spring Security 不认识。这个 Filter 把头翻译成 `Authentication` 对象：
- 读 `X-User-Id`、`X-User-Type`、`X-User-Name`（URL 解码）、`X-User-Roles`、`X-User-Role-Names`（解码后切分）
- 构建 `UsernamePasswordAuthenticationToken`，authority = `ROLE_<userType>`
- 完整身份存入 `Authentication.details`（HostSsoUser）
- 存入 `SecurityContextHolder`（Spring Security 用）+ `ContextHolder` ThreadLocal（业务代码用）
- 请求结束后清理 ThreadLocal（防止线程池复用串号）

### 2. ContextHolder——业务代码的身份入口

- ThreadLocal 存 orgCode、roles、roleNames、traceId
- SSE 异步线程 ThreadLocal 丢失时，回退到 `Authentication.details`（HostSsoUser）
- `isSuperAdmin()` 用 roleName 与 `permission.super-admin-roles` 求交（非 roleCode）

### 3. 六个角色注解

| 注解 | @PreAuthorize | 含义 |
|---|---|---|
| @AdminAuth | isAuthenticated() | 要求登录（过渡期） |
| @DeveloperAuth | isAuthenticated() | 要求登录（过渡期） |
| @AdminOrDeveloperAuth | isAuthenticated() | 要求登录（过渡期） |
| @SuperAdminAuth | @contextHolder.isSuperAdmin() | 要求超管角色 |
| @AssistantAuth | hasRole('ASSISTANT') | 要求 ASSISTANT 角色 |
| @PublicAccess | 无 | 标记公开，被扫描器注册到白名单 |

注解本质是组合了 `@PreAuthorize` 的元注解。`@EnableMethodSecurity` 是它们生效的前提。

### 4. SecurityConfig——路径级授权

- `@EnableMethodSecurity` 启用方法级安全
- `STATELESS` 无状态，身份每次从可信头还原
- 白名单：AUTH_WHITELIST（手动）+ @PublicAccess 扫描（自动）
- `anyRequest().authenticated()` 默认拒绝（fail-closed）
- ASYNC 派发放行（SSE）、OPTIONS 放行（CORS 预检）
- 401 返回 JSON + loginUrl（前端可直接跳登录）
- chat 模块额外配了 403 AccessDeniedHandler

### 5. PublicAccessPathScanner——自动发现公开端点

启动时扫描所有 `@RestController`，找到 `@PublicAccess` 标注的方法，自动注册到白名单。鉴权注解优先级高于 @PublicAccess（fail-closed）。

### 6. 两层授权

- **路径级**（SecurityConfig）：白名单放行，其他要求 authenticated()，无身份 → 401
- **方法级**（@PreAuthorize）：isAuthenticated() / hasRole() / isSuperAdmin()，无权限 → 403

### 7. PermissionProperties

`permission.super-admin-roles` 配置超管角色名列表，默认空集 = 无人是超管（fail-closed）。

## 关键设计决策

1. 网关认证、下游授权——职责分离
2. 身份头防伪造——网关先剥离外部同名头再注入
3. 角色判定用 roleName（可读）而非 roleCode（随机串）
4. 过渡期只检查 isAuthenticated()，细粒度鉴权待改造
5. SSE 异步回退到 Authentication.details
6. @PublicAccess 自动扫描，不用手动维护白名单
7. 鉴权注解优先于 @PublicAccess（fail-closed）
8. superAdminRoles 默认空集（fail-closed）
