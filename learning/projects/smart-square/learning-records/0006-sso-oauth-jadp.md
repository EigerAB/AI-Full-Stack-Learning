# 0006 - SSO 登录链路与 JADP/OAuth2/OIDC

**日期**：2026-08-17
**类型**：技能习得

## 学到了什么

1. **登录在网关做**：SsoLoginController + SsoLoginService 在 gateway 侧，不依赖 admin 在线——可用性驱动的设计决策
2. **SSO 四步编排**：校验入参 → 解析 clientId/clientSecret → SsoValidateClient 校验 ticket → SessionWriter 写 Redis + JwtSigner 签 JWT
3. **clientId/clientSecret 注册表**：多前端（广场/知识平台）各有凭证，SSO 据此做按客户端准入控制；不传 clientId 默认 square
4. **ticket 语义**：一次性，用过即失效；401=无效/过期（认证失败），403=有效但无权访问该系统（准入拒绝）
5. **Redis 会话写入**：square:session:{sid} 存身份 JSON；square:gsid:{gsid} 用 Set 存反向索引
6. **反向索引用 Set 的原因**：一次 SSO 登录可被多 RP 各自换取本地会话，用 String 会覆盖导致孤儿会话
7. **globalSessionId**：SSO 全局会话 ID，back-channel 登出据此整组删除本地会话（单点登出）
8. **JWT claims 结构**：sessionId（Redis 指针）+ userId/orgCode（冗余）+ globalSessionId（登出用）+ userType=USER
9. **/chat/ 认证路径不同**：走 Host SSO / JADP，不走平台 Session JWT——因为 AI 助手用户来自宿主系统
10. **JADP**：企业统一身份认证平台；mock 模式联调（token 格式 mock-jadp-access-token-{username}），jwt 模式直连 JADP 代理；解析结果缓存在 Redis
11. **OAuth2/OIDC**：开发者第三方登录，用外部 IdP 凭证（assertion/ID Token）换广场 token；在 admin 侧 OAuth2Controller/OidcController
12. **三条认证路径**：SSO（平台用户）→ admin 路径；JADP（宿主用户）→ /chat/；OAuth2（开发者）→ /developers/

## 关键洞察

- **登录换取放在网关是可用性决策**：admin 宕机时用户仍能登录，只是登录后访问 admin 接口会失败
- **反向索引用 Set 而非 String 是多 RP 场景的必然选择**：用 String 会导致后登录覆盖先登录，孤儿会话无法被 back-channel 登出清理
- **/chat/ 和 admin 走不同认证路径是用户来源不同决定的**：chat 用户来自宿主系统持有 JADP token，admin 用户是广场自己登录的持有平台 JWT
- **401 vs 403 的语义区分**：401 是「你没认证成功」，403 是「你认证了但不让你进」——这个区分贯穿整个认证体系
- **三条认证路径对应三种用户类型**：平台用户（SSO）、宿主用户（JADP）、开发者（OAuth2/OIDC），各自有独立的登录链路和凭据体系

## 与上一节的关联

上一节讲了网关怎么校验已有 token（AuthGlobalFilter），本节讲 token 是怎么来的（SsoLoginService 登录换取）。
两节课合起来构成了认证授权体系的完整图景：登录换 token → 后续请求带 token → 网关校验 token → 注入可信头 → 下游还原身份 → 方法级授权。

## 待深入

- back-channel 登出的具体实现（HMAC 验签、整组删除）
- OpenApiAuthFilter 开放 API 的 API Key 鉴权（第四条认证路径）
- AI 协议阶段：AG-UI / A2A / SSE / 意图识别 gRPC
- 智能编码与 MCP：WebSocket / JSON-RPC / 远程沙箱
