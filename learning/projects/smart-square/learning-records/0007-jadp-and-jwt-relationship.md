# 0007 - JADP 与 JWT 的关系澄清

**日期**：2026-08-18
**类型**：概念澄清（来自学习者的提问）

## 背景

学习者在消化第 1 课（技术栈全景图）时对 JADP 产生疑问，随后进一步追问 JADP 和 JWT 的关系。这两个问题本质上是同一个认知盲点：把不同层面的东西当成了同一层面。

## 问题 1：JADP 是什么

### 要点

**JADP 是企业内部已有的统一身份认证平台**（类似企业版 OAuth2/OIDC IdP）。广场作为企业内部 AI 平台，通过接入 JADP 复用企业已有的身份体系，而不是自己搞一套账号密码。

### JADP 提供的信息

- 用户身份：userId、userName、邮箱
- 角色：roleCode（不可读随机串，如 `47DB3AED9CE54400...`）+ roleName（可读角色名，如「应用广场超级管理员」）
- 岗位：postCode、postName
- 组织部门：orgCode、orgName、deptCode、deptName、orgLevel

### 关键架构决策

**SSO 认证中心是唯一对接 JADP 的服务**，其他系统不碰 JADP，只对接 SSO。

好处：
1. JADP 对接逻辑只维护一处（sso 模块）
2. JADP 换了或升级了，只改 sso，其他系统无感
3. 接入方不需要了解 JADP 细节，只需知道「拿 ticket 找 SSO 换身份」

### 两种模式

- **mock 模式**（`sso.jadp.mock-enabled=true`，默认）：用 JadpMockServiceImpl 返回假数据，本地开发联调
- **真实模式**（`sso.jadp.mock-enabled=false`）：用 JadpServiceImpl 通过 RestTemplate 调 ai-jadp-proxy 真实接口

三个真实接口：
- `GET /api/jadp-proxy/current-user/profile` — 用户完整信息
- `GET /api/jadp-proxy/current-user/administrative-org` — 行政组织
- `GET /api/jadp-proxy/org-structure` — 组织架构树

### JADP 在登录链路里的位置

1. 用户访问广场前端 → 未登录 → 跳 SSO authorize
2. SSO 发现没有全局会话 → **跳转 JADP 登录页**
3. 用户在 JADP 登录页输入账号密码 → JADP 校验通过 → 回跳 SSO，带 JADP access-token
4. SSO 用 access-token 调 JADP 代理拉取用户 profile
5. SSO 建立全局会话，签发一次性 ticket 给前端
6. 前端拿 ticket 找 SSO validate 换身份 → 广场网关写 Redis 会话 + 签平台 JWT

**JADP 只参与第 2-4 步**（认证本身）。之后的 ticket 换 token、Redis 会话、JWT 签发都是广场自己的逻辑，跟 JADP 无关。

### /chat/ 路径的 JADP 直连

`/chat/**` 路径走 SsoUserResolver（Host SSO），不走平台 Session JWT。AI 助手用户可能来自宿主系统，直接持有 JADP access-token。网关用 JADP token 调 SSO 数据代理拉取身份，不走「登录换 ticket」流程。

## 问题 2：JADP 和 JWT 的关系

### 核心澄清

**JADP 和 JWT 不是同一层面的东西，不是「协同」关系，而是「上下游」关系**。

类比：JADP 是公安局（发身份证的机构），JWT 是身份证的材质和防伪技术。一个是发证的机构，一个是证件的格式。

### 对比

| | JADP | JWT |
|---|---|---|
| 本质 | 一个系统（企业身份认证平台） | 一种数据格式（JSON Web Token） |
| 角色 | 身份提供者（IdP）——负责认证用户 | 令牌格式——负责携带声明 |
| 谁运行 | 企业内部已有的基础设施 | 广场自己用 Hutool 库签发和校验 |

它们解决的是完全不同的问题：JADP 回答「这个用户是谁、在哪个部门、有什么角色」，JWT 回答「怎么把一个 sessionId 安全地传给下游服务」。

### 上下游关系

```
JADP（认证源头）
  → SSO 认证中心（唯一对接 JADP，拉取身份）
    → 广场网关（写 Redis 会话 + 签 JWT）
      → 后续请求带 JWT（网关验签 → 查 Redis → 还原身份）
        → 下游服务（从 X-User-* 头拿到身份，源头是 JADP）
```

身份信息的源头是 JADP，但广场内部传递身份用的令牌格式是 JWT。JWT 只是广场自己的「内部通行证格式」，JADP 不知道也不关心广场用 JWT 还是别的格式。

### 两个不同的 token

| | JADP access-token | 广场 JWT |
|---|---|---|
| 谁签发 | JADP | 广场网关（JwtSigner） |
| 用什么签名 | JADP 自己的机制 | HS256 + JWT_SECRET |
| 携带什么 | JADP 的会话信息 | sessionId（指向 Redis） |
| 谁校验 | SSO 调 JADP 代理时用 | 网关 AuthGlobalFilter 校验 |
| 生命周期 | 登录后短期有效，用于拉 profile | 7 天，每次请求都带 |

JADP access-token 只在登录那一刻用——SSO 拿它去 JADP 拉用户 profile，拉完就不用了。之后广场内部全用自己签的 JWT。

### 为什么不直接用 JADP token 当广场的 token

1. **性能**：JADP 是外部系统，每次请求都调它拉 profile 太慢。广场用 Redis 缓存身份 + JWT 做本地校验，网关只查 Redis 不回源 JADP。

2. **解耦**：广场的鉴权逻辑不依赖 JADP 在线。JADP 挂了，已登录用户还能用——身份已缓存在 Redis，JWT 校验不依赖 JADP。

3. **可控性**：广场需要自己控制会话过期、登出撤销。用 JADP token 则广场无法单方面让它失效（归 JADP 管）。用 Session-in-Redis + JWT-as-pointer，广场删 Redis 就能撤销。

## 一句话总结

**JADP 是身份的源头（上游），JWT 是广场内部的令牌格式（下游）**。用户在 JADP 登录 → SSO 从 JADP 拉身份 → 广场签自己的 JWT 用于后续请求。两者不是协同关系，而是「JADP 认证用户，广场用 JWT 记住这个用户」的上下游接力。

## 关键洞察

- **不同层面的东西不能放在一起比较**：JADP 是系统，JWT 是格式；一个是 IdP，一个是 token 标准
- **身份的源头和身份的传递是分开的**：JADP 提供身份信息，JWT 只负责在广场内部传递身份指针
- **不直接用 JADP token 的三个原因（性能/解耦/可控性）是架构设计的经典权衡**：外部依赖要缓存、要隔离、要可控
