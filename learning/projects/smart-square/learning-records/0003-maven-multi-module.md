# 0003 - Maven 多模块与依赖方向

**日期**：2026-08-17
**类型**：技能习得

## 学到了什么

1. **七个模块**：common（公共库）、proto（gRPC 契约）、admin（主服务）、chat（对话微服务）、gateway（网关）、sso（单点登录）、knowledge（知识库）
2. **依赖方向严格单向**：common/proto 最底层不依赖业务模块；admin/chat 依赖 common+proto；gateway 独立不依赖 common（因为 WebFlux vs WebMVC 冲突）；admin/chat 不互相依赖
3. **chat 调 admin 走 OpenFeign**：运行时 HTTP 调用，不是 Maven 编译期依赖
4. **父 POM 集中管控**：properties 声明版本号，dependencyManagement 托管依赖版本，pluginManagement 托管插件版本；子模块引用时不写 version
5. **dependencyManagement vs dependencies**：前者只声明版本不引入，后者真正引入；子模块要引入还得在自己的 dependencies 里写，但不用写 version
6. **构建顺序坑**：common 变更后要先 install 到 .m2 再 package 上层模块，否则编译时拿到旧版 common

## 关键洞察

- **多模块拆分的本质是「共享 vs 私有」分离**，不是为了微服务而微服务
- **gateway 不依赖 common 是技术约束驱动的设计决策**——WebFlux 和 WebMVC 不能共存，这迫使网关的鉴权逻辑自己写，不复用 common 的安全设施
- **Maven 依赖（编译期）vs OpenFeign 调用（运行时）是两种不同的耦合**：前者让 chat 被迫引入 admin 全部代码，后者只需要一个 Feign 接口定义
- **版本集中管控不是洁癖，是防 bug**：版本不一致会导致 API 行为差异引发难查的问题

## 与上一节的关联

上一节讲了 admin 内部的三层架构（Controller→Service→Mapper），本节上升到模块间关系——为什么拆成七个模块、依赖怎么走。下一节将进入 Nacos，讲这些模块在运行时怎么互相发现。

## 待深入

- Nacos 注册发现的具体机制（下一节）
- OpenFeign 的接口定义和调用细节（后续课程）
- proto 模块的 protobuf-maven-plugin 编译流程（AI 协议阶段）
