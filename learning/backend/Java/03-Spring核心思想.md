# 03 Spring 核心思想

[返回学习目录](READEME.md)

> 笔记状态：全章已整理。学习状态：待学习。  
> 先修：第一章工程基础，第二章的类、接口、集合、注解和 Lambda。  
> 环境：Java 17、Spring Boot 3.5.9；这个固定版本用于衔接课程，不代表当前最新版本。  
> 本章目标：解释对象由谁创建、依赖如何传入、哪些对象由 Spring 管理，以及代理何时生效。

本章先用独立的“产品目录”示例理解 Spring 原理，再对照芋道通知公告模块。独立示例无需数据库、Redis 或模型账号；代码、运行方法与答案都在本文中，配套源码位于 `examples/03-spring-core`。

## 1. 先分清 Spring、Spring Boot、Spring MVC

| 名称 | 本章先理解的职责 |
| --- | --- |
| Spring Framework | 提供对象管理、依赖注入、AOP 等基础能力；也包含 Web、事务等模块 |
| Spring Boot | 在 Spring 基础上简化依赖选择、配置与应用启动 |
| Spring MVC | Spring 中处理 Web 请求的框架，提供 Controller、参数绑定等能力 |

使用 Spring Boot 不等于一定要写 HTTP 接口。本章只启动一个执行后退出的控制台程序，观察 Spring 如何管理对象。下一章再让它接收 HTTP 请求。

对照 FastAPI：你通过 Depends 声明依赖，框架负责解析并传入；Spring 也有依赖声明与装配，但容器中的对象生命周期和请求处理模型不同，不能把每个 Bean 当作每次请求创建的对象。

## 2. 从自己 new 对象开始：我们到底想解决什么？

产品服务要查询产品名称，需要一个负责提供数据的对象。下面是普通 Java 的思路片段，暂不涉及 Spring：

```java
ProductRepository repository = new ProductRepository();
ProductService service = new ProductService(repository);
```

ProductService 使用 ProductRepository，所以我们说“Service 依赖 Repository”。这里的依赖是**对象之间的协作关系**，不是第一章 Maven 管理的 JAR 依赖。

对象少时，在 main 中自己创建、传入完全可行。对象多起来，每个对象又依赖其他对象，就需要统一安排创建顺序、共享和关闭。Spring 的容器可以承担这部分工作。

**容器（ApplicationContext）**是管理这些对象及其关系的程序组件，不是 Docker 容器。**Bean**就是被 Spring 容器管理的对象；它仍然是普通 Java 对象，并非一种新语法。

## 3. IoC 与 DI：谁来创建，怎样传入？

**IoC（控制反转）**在这里表示：对象不用自己决定所有协作者怎么创建，而把装配控制交给外部容器。

**DI（依赖注入）**是一种实现方式：对象声明需要什么，外部把相应对象传进来。构造器注入就是通过构造方法传入。

下面是 Service 的关键片段，完整文件见第 10 节：

```java
@Service
public class ProductService {
    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }
}
```

逐项看：`@Service` 标记这个类可作为服务组件；构造器参数声明需要 ProductRepository；Spring 找到符合要求的 Bean，创建 Service 时传入；final 表示字段初始化后不再换成另一个引用。

这里仅有一个构造器，因此不必额外加 `@Autowired`。这不是所有任意构造器都会自动注入的承诺；有多个构造器时要明确选择规则。注入也不要求每个服务都先写接口，用具体类型同样可以。[构造器注入](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)

构造器注入的好处是：依赖一眼可见，测试时也可以自己 new 并传入替代对象。不是使用 Spring 后就禁止所有 new，而是应明确哪些对象需要由容器管理。[IoC 与 Bean](https://docs.spring.io/spring-framework/reference/core/beans/introduction.html)

## 4. Spring 怎么知道要管理哪些对象？

### 4.1 组件扫描：发现我们写的类

常见注解如下：

| 注解 | 典型位置 | 先理解的用途 |
| --- | --- | --- |
| `@Component` | 通用组件类 | 通用的组件标记 |
| `@Service` | 业务服务类 | 组件标记，同时表达业务职责 |
| `@Repository` | 数据访问类 | 表达数据访问职责，相关异常转换支持后续再讲 |
| `@Controller` | Web 控制器类 | 在 Web 场景下处理请求，下一章再用 |

Spring 在指定包范围内扫描这些标记，注册 Bean。`@SpringBootApplication` 包含组件扫描相关能力，默认从启动类所在包及其子包查找组件。

本课统一放在 `demo.springcore` 包下：

```text
demo.springcore
├── CoreApplication
├── ProductRepository
├── ProductService
└── TraceAspect
```

如果把 Service 移到另一个不在扫描范围内的包，光写 `@Service` 也不保证它被发现。这里讲的是 Java package 关系，不只是文件夹看起来相邻。[Spring Boot 代码布局](https://docs.spring.io/spring-boot/3.5/reference/using/structuring-your-code.html)

### 4.2 @Bean：明确提供一个对象

有些对象来自第三方库，不能去它的源码上加 @Component；或者需要自己设置构造参数。可以在配置类中使用 @Bean。

这个独立示意文件名为 ClockConfig.java，不是本课示例启动必需的文件：

```java
package demo.springcore;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

`@Configuration` 表示配置来源；`@Bean` 表示把方法返回的对象交给 Spring 管理。默认 Bean 名称是方法名 clock，其他组件可以声明 Clock 构造器参数使用它。这里的 new/工厂方法与容器管理不矛盾：你提供创建方式，容器负责管理产物。

需要引用其他 Bean 时，优先声明构造器或 @Bean 方法参数，不必在各处手工调用配置方法。[Java 配置与 @Bean](https://docs.spring.io/spring-framework/reference/core/beans/java/basic-concepts.html)

## 5. 没有候选对象、或者候选太多时怎么办？

Spring 必须知道往构造器里传哪个对象。最常见的两类问题是：

- **没有候选 Bean**：缺少组件标记、超出扫描范围，或者条件不成立。
- **有多个候选 Bean**：按类型无法确定该选哪一个。

例如一个接口 MessageSender 有两个实现：邮件发送、站内信发送。当参数只写 MessageSender，就需要明确选择。可以在参数上写 `@Qualifier("emailSender")` 指定 Bean 名称；也可以用 @Primary 为同类候选设置优先选择。

这是识别问题的入口，不要求现在设计多个实现。看到启动失败时先读“需要什么类型、找到了几个候选”，不要靠给所有类反复加 @Autowired 解决。

## 6. 自动配置与条件装配

组件扫描侧重发现应用组件；**自动配置**是 Spring Boot 根据已有依赖、配置以及 Bean 等条件，提供合适的基础配置。Starter 则主要是一组相关依赖，它和自动配置不是同一个概念。

你可以把条件装配理解为“满足条件才注册这个 Bean”。例如 @ConditionalOnProperty 可以根据配置开关决定是否启用某个组件，@ConditionalOnMissingBean 可以在没有用户自定义 Bean 时提供默认实现。

本课加入 `spring-boot-starter-aop` 后，Boot 在默认条件下启用所需的 AOP 自动配置。若没有匹配的切面或调用路径不经过代理，不会仅凭加一个 Starter 就产生你想要的增强行为。

后面遇到自动配置不生效时，可通过应用的 `--debug` 参数查看条件评估报告。这个 debug 是配置诊断输出，不等于 VS Code 的断点调试。[Spring Boot 自动配置](https://docs.spring.io/spring-boot/3.5/reference/using/auto-configuration.html)

## 7. Bean 生命周期：从创建到关闭

先记住简化过程：**创建对象 → 注入依赖 → 执行初始化回调 → 提供给应用使用 → 容器关闭时执行销毁回调**。实际过程还包含 Bean 后处理器和可能的代理包装，暂不背完整源码顺序。

在 Bean 中可以声明：

```java
@PostConstruct
public void init() {
    System.out.println("服务已初始化");
}

@PreDestroy
public void close() {
    System.out.println("服务即将销毁");
}
```

这些是类中的方法，不能放进 main。Boot 3 对应的 import 是 `jakarta.annotation.PostConstruct` 和 `jakarta.annotation.PreDestroy`。初始化回调在依赖注入后执行；销毁回调适合清理持有的资源。

不是每个请求都会执行这两个方法。进程被强制终止时，也不能保证正常销毁流程一定执行。不要把一次性初始化逻辑和每次请求的业务混在一起。[生命周期注解](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/postconstruct-and-predestroy-annotations.html)

## 8. Bean 作用域与线程安全

这里的**作用域**指“容器在多大范围内共享一个对象”，与 Maven `<scope>` 控制依赖使用阶段是不同概念。

| 作用域 | 含义 |
| --- | --- |
| singleton（默认） | 每个容器中，每个 Bean 定义通常共享一个实例 |
| prototype | 每次向容器请求该 Bean 时创建新实例 |
| request | Web 环境中每个 HTTP 请求拥有对应实例 |

prototype 注入一个 singleton 时，不会自动在每次业务方法调用时换新实例；prototype 的销毁也通常由使用者处理。这里先识别区别，不展开高级获取方式。[Bean 作用域](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)

**单例不等于线程安全。**后端多个请求可能同时使用同一个 Service。把 `currentUserId` 等请求数据放在 Service 的可变字段中，就可能被其他请求覆盖。

构造器注入的 repository 字段是在保存协作者；方法参数、局部变量则适合保存当前调用的数据。共享集合、计数器等可变状态仍需要额外并发设计，不能仅因类上有 @Service 就认为安全。

## 9. AOP 与代理：业务方法外面的公共行为

### 9.1 为什么需要 AOP？

假设许多方法都需要执行前后计时。每个方法复制日志代码会让业务逻辑变乱。**AOP（面向切面编程）**可以把这类跨多个方法的行为集中定义，例如日志、事务或某些权限检查。

先认识三个词：切面是公共行为的组织单元；切点决定哪些方法匹配；通知是匹配后在前、后或周围执行的逻辑。本课使用“环绕通知”，包住一次方法调用。

### 9.2 代理是怎样参与的？

Spring AOP 常通过代理工作：调用者拿到的可能是代理对象，先经过代理的增强逻辑，再执行实际对象的方法。

```text
调用者 → 代理中的计时逻辑 → 实际业务方法 → 返回经过计时逻辑
```

常见机制有基于接口的 JDK 动态代理、基于子类的 CGLIB 代理。基于子类的方式不能重写 final 方法，private 方法也不是可供这种代理拦截的入口。本课无需手写代理类。

### 9.3 为什么内部调用可能失效？

假设外部通过代理调用 `service.summary()`，summary 内部又直接执行 `names()`。这个内部调用走的是当前对象自身，**不会重新经过外面的代理**。

如果切面只匹配 names：外部调用 `service.names()` 会触发切面；内部 `summary → names` 不会触发 names 的切面。本课完整示例专门演示这一点。

这也是后面学习 `@Transactional` 时必须记住的前提：在常见代理模式下，给内部调用的方法加注解，不能保证注解对应的增强会执行。是否生效要看调用是否经过代理，并满足相应配置。[Spring AOP 代理机制](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)

## 10. 完整实验：让 Spring 创建并管理产品服务

可直接使用配套目录 `examples/03-spring-core`，也可以按下列文件手工创建。项目只有 5 个核心文件；它是原理实验，不是芋道模块的复制版。

```text
03-spring-core/
├── pom.xml
└── src/main/java/demo/springcore/
    ├── CoreApplication.java
    ├── ProductRepository.java
    ├── ProductService.java
    └── TraceAspect.java
```

### 10.1 pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.9</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>spring-core-lesson</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

parent 提供版本及构建默认配置，dependencies 引入实际所需的库，Boot 插件负责可执行打包等操作。没有引入 Web 或数据库 Starter，因此本例不会监听 HTTP 端口，也不需要数据库连接配置。

### 10.2 ProductRepository.java

```java
package demo.springcore;

import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {
    public List<String> findNames() {
        return List.of("智能助手", "知识库");
    }
}
```

它先用固定数据代替数据库。Spring 不会因为写了 @Repository 就自动生成 SQL；这里只是注册一个带数据访问职责的组件。

### 10.3 ProductService.java

```java
package demo.springcore;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProductService {
    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        System.out.println("服务已初始化");
    }

    public List<String> names() {
        return repository.findNames();
    }

    public String summary() {
        return "产品数量：" + names().size();
    }

    @PreDestroy
    public void close() {
        System.out.println("服务即将销毁");
    }
}
```

注意 summary 中的 names 调用，后面观察它有没有触发切面。

### 10.4 TraceAspect.java

```java
package demo.springcore;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TraceAspect {
    @Around("execution(* demo.springcore.ProductService.names(..))")
    public Object trace(ProceedingJoinPoint call) throws Throwable {
        System.out.println("进入 names");
        try {
            return call.proceed();
        } finally {
            System.out.println("离开 names");
        }
    }
}
```

@Aspect 标记切面，@Component 把它注册到容器。@Around 的表达式只匹配 ProductService.names 方法；`*` 表示不限返回类型，`(..)` 表示不限参数。`call.proceed()` 继续执行后续调用链及目标方法，finally 中的输出在调用结束或抛错时执行。现在能读懂这个具体匹配即可，不需要背切点表达式语法大全。

### 10.5 CoreApplication.java

```java
package demo.springcore;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CoreApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                SpringApplication.run(CoreApplication.class, args)) {
            ProductService first = context.getBean(ProductService.class);
            ProductService second = context.getBean(ProductService.class);
            System.out.println("同一个 Bean：" + (first == second));
        }
    }

    @Bean
    public CommandLineRunner demo(ProductService service) {
        return args -> {
            System.out.println(service.names());
            System.out.println(service.summary());
        };
    }
}
```

CommandLineRunner 是 Boot 提供的启动后回调接口，本例用 Lambda 描述执行内容。@Bean 方法的 ProductService 参数由 Spring 注入；我们没有在这里 new Service。

SpringApplication.run 返回可关闭的容器。try-with-resources 让示例结束时关闭容器，从而观察销毁回调。`getBean` 用来演示单例身份；普通业务代码优先通过注入获得协作者，不需要到处查容器。

## 11. 如何运行、看输出和打断点

### 11.1 在 VS Code 中运行

1. 打开 `examples/03-spring-core` 文件夹，等待 Maven 项目导入。
2. 确认项目 JDK 为 17，Problems 中没有阻止编译的错误。
3. 打开 CoreApplication.java，点击 main 上方 Run 或 Debug。
4. 在 ProductService 的构造器、names、summary 和 TraceAspect.trace 中设置断点，观察调用者。

首次导入需要联网下载公开 Maven 依赖。带回家离线运行前，要在能联网的环境完成一次导入或构建；笔记本身不依赖联网阅读。更具体的 IDE 操作见 [VS Code 实操课](实操01-VSCode运行与调试Java.md)。

### 11.2 命令行构建与运行

已安装并配置 Maven 时，在示例目录执行：

```powershell
mvn package
java -Dfile.encoding=UTF-8 -jar target/spring-core-lesson-1.0.0.jar
```

PowerShell 若对 JVM 参数解析有问题，可以把整个参数写为 `'-Dfile.encoding=UTF-8'`。示例没有 Maven Wrapper 文件，不要直接执行不存在的 `./mvnw`；没有全局 Maven 时，可使用 VS Code 的 Java 运行入口，或在另一设备安装配置 Maven。

### 11.3 预期结果

忽略 Boot 自身的启动日志，示例业务输出应按下列顺序出现：

```text
服务已初始化
进入 names
离开 names
[智能助手, 知识库]
产品数量：2
同一个 Bean：true
服务即将销毁
```

为什么“离开 names”比列表先打印？外部 println 必须先等 service.names 返回结果，切面结束后才执行打印。为什么 summary 没多打印一次“进入 names”？因为它在对象内部调用 names，没有重新通过代理。

这个程序执行完正常退出。没有 Web 服务器，也没有持续运行的任务，所以退出不是启动失败。

**验证记录（2026-09-15）：**配套示例已按 Java 17 编译并通过 Maven package，实际执行可运行 JAR 得到了上述七行业务输出；文中五份文件内容与配套源码一致。本例没有自动化测试，验证的是构建及实际运行结果。

## 12. 独立练习与参考答案

### 练习

1. 在固定列表中添加第三个产品，预测 names 与 summary 输出。
2. 在 demo 回调中再调用一次 service.names，观察切面输出次数。
3. 临时移除 ProductRepository 上的 @Repository，重新运行并阅读缺少 Bean 的错误，随后恢复。
4. 把 summary 改为直接返回固定文字，确认内部是否执行 names；再恢复。
5. 不改代码，使用断点解释构造器、init、业务方法和 close 的执行时机。

### 参考答案

1. 列表增加第三项，summary 数量变为 3。
2. 新增一次外部 names 调用，会新增一组“进入/离开 names”；内部调用仍不增加。
3. Spring 找不到 ProductService 构造器需要的 ProductRepository Bean，应用通常在启动期间失败。
4. 直接返回固定文字后不再调用 names；原本虽然调用 names，但因为自调用也不触发其切面。
5. 构造与注入在 Bean 创建阶段，init 在初始化阶段，demo 执行业务调用，main 的 try 块退出时关闭容器并执行 close。

### 概念自检

| 问题 | 参考答案 |
| --- | --- |
| 写了 @Service 就一定被管理吗？ | 还需在扫描范围内并满足相关条件 |
| Bean 与普通对象最大的区别？ | 是否被容器管理；Bean 仍是 Java 对象 |
| DI 一定需要接口吗？ | 不需要，可以注入具体类型；接口用于表达可替换的协作契约 |
| @Bean 和 @Component 怎么选？ | 自己的组件常用扫描，第三方对象或自定义创建常用 @Bean |
| 同一个 Service 能被多个请求共用吗？ | 默认单例通常会共用，因此要避免混入请求级可变状态 |
| 自己 new 的 Service 自动有 AOP 吗？ | 不会仅因类有注解就自动获得容器提供的代理 |
| 内部调用为什么可能绕过事务等增强？ | 常见 Spring AOP 代理模式下，内部调用没有重新经过代理 |
| --debug 等于打断点吗？ | 不等于，它在此用于输出 Boot 配置诊断信息 |

## 13. 本章边界与后续路线

本章要求能解释对象管理、注入、生命周期和代理，不要求研究 Spring 源码中的所有扩展点，不要求实现事务，也不需要先搭建微服务。

参考项目确定为芋道 `ruoyi-vue-pro` 的 `master-jdk17`，路径 `D:\mine_projects\ruoyi-vue-pro`。先保留独立实验，再对照下面三个位置，理解“同一原理的不同写法”：

1. `yudao-server/src/main/java/cn/iocoder/yudao/server/YudaoServerApplication.java`：启动类用 `scanBasePackages` 显式指定 server 和 module 包扫描范围，不只是依赖启动包的默认扫描。
2. `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/notice/NoticeServiceImpl.java`：类上有 `@Service`，字段 `noticeMapper` 使用 `jakarta.annotation.Resource` 注入。
3. `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/controller/admin/notice/NoticeController.java`：字段 `noticeService` 同样使用 `@Resource`，查询方法通过 `@PreAuthorize` 声明权限要求。

**为什么项目不是构造器注入？**`@Resource` 字段注入也是容器装配依赖的方式，通常先按指定或默认名称解析，在相应规则下再按类型匹配。课程优先用构造器让依赖关系更直观；阅读芋道时识别它的约定，不需要为了学习立即重构项目。手工 new 一个带 @Resource 字段的对象，也不会自动完成字段注入。

**Mapper 从哪里来？**NoticeMapper 是接口，由 MyBatis 的相关扫描与配置创建代理并注册为 Bean，不能简单套用“所有 Bean 都是 @Service 类”的理解。数据库课程再展开它如何执行 SQL。

项目 `yudao-framework` 下的自定义 Starter 提供 Web、安全等基础配置，不应以为这些类全部由上述 module 包扫描发现。当前只识别业务组件与基础设施配置的分工，下一章再看请求处理。

更多入口见 [芋道项目学习地图](芋道项目学习地图.md)。下一章用通知公告查询接口学习 Spring Boot REST API。

- [ ] 能自己运行示例并解释输出顺序。
- [ ] 能说明组件扫描和 @Bean 两种注册方式。
- [ ] 能排查一次缺少依赖 Bean 的启动错误。
- [ ] 能解释单例与线程安全的区别。
- [ ] 能用示例说明代理调用与内部调用的区别。

学习状态按实际阅读与练习更新，文档和示例准备好不等于已经通过验收。
