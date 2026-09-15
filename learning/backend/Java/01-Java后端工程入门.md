# 01 Java 后端工程入门

[返回学习目录](READEME.md)

> 状态：首轮概念问答已讲解，独立服务实践尚未验收；已进入第二课。  
> 本章目标：理解项目如何组织、构建和运行，能找到程序入口并使用基本工程工具。  
> 学习方式：先学 1—5 节并完成第一轮练习，再进入 6—9 节。不要求一次记住全部内容。

## 1. 从熟悉的 FastAPI 出发

假设你平时这样启动服务：

```powershell
uvicorn main:app --reload
```

这里存在不同职责：Python 提供语言运行环境，FastAPI 提供 Web 应用框架，Uvicorn 负责监听网络请求并按 ASGI 协议调用应用，pip/uv 等工具管理依赖和项目环境。

Java 也需要这些能力，但工具之间的分工不同：

| 职责        | 你熟悉的工具或概念        | Java 课程中的对应入口                                   |
| --------- | ---------------- | ----------------------------------------------- |
| 开发和运行语言程序 | Python 解释器与开发环境  | JDK，其中包含 java、javac 等工具                         |
| 管理依赖      | pip/uv、依赖声明      | Maven、pom.xml                                   |
| 自动构建      | 前端构建脚本、打包流程      | Maven 生命周期和插件                                   |
| 编写 Web 应用 | FastAPI          | Spring MVC，通常由 Spring Boot 配置启动                 |
| 接收网络请求    | Uvicorn          | Servlet 应用中的 Tomcat/Jetty；响应式应用常见 Reactor Netty |
| 应用启动入口    | main:app 指向的应用对象 | Java main 方法调用 SpringApplication.run            |
| 交付产物      | 源码、依赖环境或容器镜像     | 常见为可执行 JAR，再配运行环境或容器镜像                          |

这些是职责对照，不是一一等价关系。尤其不要把 Maven 当作 Java 的虚拟环境，也不要把 Spring Boot 当作 JVM。

**本节检查：**程序正在响应 HTTP 请求时，编译工具是否还必须一直工作？不需要，构建与服务运行是不同阶段。

## 2. JDK、JVM、字节码和 JAR

### 2.1 从源码到运行

普通 Java 程序可以先用下面的流程理解：

```text
Hello.java
    ↓ javac 编译
Hello.class（字节码）
    ↓ java 命令启动 JVM，加载类
执行 main 方法
```

- **JDK**：开发工具包，包含编译器、运行工具、标准库和诊断工具等。
- **JVM**：执行 Java 字节码的虚拟机，负责类加载、执行和内存管理等。
- **JRE**：运行环境这一概念，包含 JVM 和运行所需的库等。现代开发通常直接安装 JDK，不需要再单独寻找一个 JRE 安装包。
- **JAR**：归档格式，可以装入 class、资源和元数据。普通库 JAR 不一定能通过 `java -jar` 直接启动。

JVM 执行字节码时会涉及解释执行和即时编译，具体原理留到 JVM 章节。这里先理解：源码、编译产物、运行进程是不同东西。

### 2.2 一个不依赖 Spring 的小实验

在自己新建的临时练习目录中，将下面内容保存为 `Hello.java`：

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello Java backend");
    }
}
```

在该目录执行：

```powershell
javac Hello.java
java -cp . Hello
```

预期输出 `Hello Java backend`。这是给学习者执行的练习，本笔记没有把它标记为已完成。

`-cp .` 表示从当前目录查找应用类，`Hello` 是类名，而不是 `Hello.class` 文件名。classpath 是类和资源的查找路径，不是源码目录的另一种叫法。

真实项目有包和外部依赖，手动维护编译、classpath 和打包很繁琐，Maven 就承担了其中大量工程工作。

## 3. Maven 解决哪些问题

Maven 主要管理依赖和构建过程。`pom.xml` 是它读取的项目描述文件。

一个依赖通常用三个值定位：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>3.5.9</version>
</dependency>
```

这是依赖片段，不是完整可运行的 POM。

| 字段 | 作用 |
| --- | --- |
| groupId | 组织或项目组标识 |
| artifactId | 构件名称 |
| version | 构件版本 |

依赖自身还可能依赖其他库，称为传递依赖。Maven 从配置的仓库解析依赖，并通常缓存在用户目录下的 `.m2/repository`；项目通过解析后的 classpath 使用这些构件。这个缓存可以由多个项目共享，不等同于 Python 的 venv。

### 3.1 dependencies 与 dependencyManagement

- `dependencies`：声明当前项目实际需要的依赖。
- `dependencyManagement`：集中管理依赖版本等规则，本身不会把其中所有库都加入当前项目。
- **BOM**：用于统一一组依赖版本的 POM，通常通过 `type=pom`、`scope=import` 导入 dependencyManagement。
- **parent**：继承公共项目配置，可以涉及版本、属性和插件等，比单纯导入 BOM 的范围更广。

因此，子模块省略依赖版本，可能是版本已经由父 POM 或 BOM 管理，而不是 Maven 自动挑选最新版本。

### 3.2 依赖作用域：这个库在什么时候需要？

Maven 不仅管理“需要哪个库”，还管理“什么时候需要它”。例如，JUnit 是测试工具，测试时需要，正式运行服务时通常不需要。这个使用范围就叫**依赖作用域**，通过 `<dependency>` 里面的 `<scope>` 配置：

```xml
<!-- 依赖片段，假设父 POM 已管理版本 -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

这里的 `test` 表示只供测试代码编译和运行使用。普通依赖不写 `<scope>` 时默认是 `compile`，可用于业务代码的编译和运行，也能用于测试。

本节先认识这个配置项，能看懂 `test` 与默认 `compile` 即可，其他作用域遇到具体用途时再学。

## 4. 编译、测试、打包、启动有什么区别

Maven 的 default 生命周期中，常用阶段顺序如下，中间还存在其他阶段：

```text
validate → compile → test → package → verify → install → deploy
```

执行后面的阶段，会先经过前面的阶段；具体工作由绑定的插件目标完成。

| 命令 | 目的 |
| --- | --- |
| `mvn compile` | 编译主代码 |
| `mvn test` | 编译并运行配置的单元测试 |
| `mvn package` | 经过前置阶段后打包，通常会运行单元测试 |
| `mvn verify` | 运行至验证阶段；是否运行集成测试取决于项目插件和配置 |
| `mvn install` | 将构建产物安装到本地 Maven 仓库，供其他本地项目依赖 |
| `mvn deploy` | 发布构件到远程 Maven 仓库，不等于部署并启动业务服务 |
| `mvn spring-boot:run` | 调用 Spring Boot 插件的 run 目标，在具备相应配置的应用模块中启动应用 |

`clean` 属于另一个生命周期，通常清理 target 构建产物；普通 `package` 不会自动先 clean。

这些命令用于理解，第一轮不需要对整个目标项目执行构建或启动。[Maven 构建生命周期](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)

**需要记住：打包成功不代表数据库连接成功，也不代表业务服务已经启动。**

## 5. Spring Boot 如何启动服务

下面是简化的应用入口：

```java
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

它需要 Spring Boot 依赖和相关 import；这段代码用于解释入口，不能作为独立 Java 文件直接用 javac 编译。

对于配置正确的 Servlet Web 应用，启动过程可以先这样理解：main 启动 Spring 应用，框架读取配置并创建所需对象，配置嵌入式 Web 服务器，服务器绑定端口后接收请求。具体初始化顺序和 Bean 原理放在第三章。

Starter 提供一组相关依赖；自动配置根据类路径、配置和已有 Bean 等条件决定配置哪些能力。不是写上一个注解就能无条件获得所有功能。

使用适当的 Spring Boot 打包插件配置后，可以生成包含应用类、资源、依赖和启动加载器的可执行 JAR。之后用 `java -jar` 启动，不需要 Maven 常驻，也不需要开发用 IDE。[Spring Boot 首个应用教程](https://docs.spring.io/spring-boot/3.5/tutorial/first-application/index.html)

芋道通过 `yudao-spring-boot-starter-web` 引入 `spring-boot-starter-web`，当前所查依赖沿用默认 Tomcat。Web 服务器是依赖选择的一部分，Spring Boot 不等同于某一种服务器。

## 6. 项目目录与多模块

一个常规 Maven Java 模块的结构：

```text
模块目录/
├── pom.xml
├── src/main/java/       主代码
├── src/main/resources/  配置等资源
├── src/test/java/       测试代码
├── src/test/resources/  测试资源
└── target/              生成的构建产物
```

包名如 `com.example.demo` 通常对应源码下的 `com/example/demo` 路径。编译后生成的 class 不应该手工当作源码维护。

芋道根 POM 的 `<packaging>pom</packaging>` 表示根项目负责配置与组织构建，`<modules>` 列出子模块，例如 yudao-framework、yudao-server、yudao-module-system。根项目构建时，Maven 按依赖顺序组织这些模块，不是把所有模块自动合成一个 JAR。

真正的主应用是 `yudao-server`：它的 packaging 是 jar，通过 dependencies 引入 system、infra 等模块，再由 Spring Boot 插件打包。根 modules 中被注释的 AI 等模块，不会仅因文件夹存在就参与构建。

| 概念   | 含义                            |
| ---- | ----------------------------- |
| 聚合   | 根项目通过 modules 组织一起构建的模块       |
| 继承   | 子项目通过 parent 复用父配置            |
| 模块依赖 | 某模块通过 dependencies 使用另一个模块的构件 |
| 微服务  | 运行和部署层面的服务边界                  |

聚合和继承可以同时存在，但不是一回事。一个 Maven 模块也不必对应一个独立服务，例如 `yudao-common` 提供公共代码，由 `yudao-server` 组合模块运行。

## 7. 配置与日志入门

Spring Boot 支持配置文件、环境变量、命令行参数等配置来源。`application.yml` 是常见的配置文件名；Profile 用来选择一组环境相关配置。

**`application.yml` 是 Spring Boot 的应用配置文件，并不是用来替代环境变量的。**
在 Spring Boot 中，**Profile 是给一组配置起的名字，用来选择当前启用哪组配置**。

只针对你自己后续创建的可执行练习 JAR，可以这样指定端口：

```powershell
java -jar target/demo.jar --server.port=8082
```

这里的 JAR 路径是示例，需要替换成实际产物。Spring Profile 与 Maven Profile 是两套不同机制，后者影响构建，不要仅凭相同名称认为它们自动关联。

读启动日志时先找三个信息：启动的模块与环境、服务器端口、失败异常链。出现异常时，找到具体 `Caused by` 和对应代码/配置，而不是只看最后的“启动失败”。

配置文件中可能包含敏感内容，学习记录只摘录必要的非敏感字段。

## 8. 在芋道 ruoyi-vue-pro 中验证

以下代码位置已按本地 `master-jdk17` 分支核对（2026-09-15，提交 `8e43004cf6`）。第一轮只读源码，不启动服务。

- 根 POM：`D:\mine_projects\ruoyi-vue-pro\pom.xml`。寻找 `packaging`、`modules`、`java.version`、`spring.boot.version`、`dependencyManagement`。
- 版本管理 POM：`D:\mine_projects\ruoyi-vue-pro\yudao-dependencies\pom.xml`。观察自定义 BOM 怎样集中管理 MyBatis-Plus 等依赖。
- 启动模块 POM：`D:\mine_projects\ruoyi-vue-pro\yudao-server\pom.xml`。寻找 `parent`、system/infra 依赖和 Boot 插件的 `repackage` 配置。
- 启动入口：`D:\mine_projects\ruoyi-vue-pro\yudao-server\src\main\java\cn\iocoder\yudao\server\YudaoServerApplication.java`。找到 main 和 `SpringApplication.run`。
- 应用配置位于 `yudao-server/src/main/resources/application.yaml` 与 `application-local.yaml`；`.yaml` 和 `.yml` 都是 YAML 扩展名。

本地所查芋道仓库没有 `mvnw.cmd`，不要照抄其他工程的 Wrapper 命令。下面使用已安装并加入 PATH 的 Maven；若提示找不到 mvn，需要配置 Maven，或先用 VS Code 的 Java 项目运行入口。

学习者可以执行以下只读版本检查：

```powershell
java -version
javac -version
Set-Location 'D:\mine_projects\ruoyi-vue-pro'
mvn -v
```

重点比较最后一条输出的 Java version/Java home 与前两条是否匹配。IDE 的项目 JDK、Maven 使用的 JDK、终端 java 可能不同。

需要追踪依赖时，后续可在项目根目录执行：

```powershell
mvn -pl yudao-server dependency:tree
```

这个命令可能解析或下载依赖，也可能因内部构件尚未安装而失败。失败时先分析构件来源，不要为了读依赖树直接启动所有服务。

## 9. 调试与故障分类

VS Code 的具体操作已整理为完整配套课程：[实操 01：在 VS Code 中运行与调试 Java](实操01-VSCode运行与调试Java.md)。先用普通 Java 程序练习运行、断点、单步执行、变量和调用栈，再学习 Maven / Spring Boot 的启动配置与接口调试。本节保留用于定位问题类型的速查表。

| 现象 | 首先判断 |
| --- | --- |
| 无法解析依赖 | 坐标、仓库、网络或本地/私有构件是否可用 |
| 编译报错 | 源码、JDK 和编译配置是否匹配 |
| 找不到主类 | 启动类名、classpath 和编译产物是否正确 |
| JAR 无主清单属性 | 是否把普通库 JAR 当作可执行应用，打包插件是否正确配置 |
| 端口占用 | 服务进程启动后无法绑定目标端口 |
| 数据库连接失败 | 配置、网络、凭证与服务依赖；不等同于编译失败 |

## 10. 第一轮练习与验收

先完成以下小练习，再展开更完整的 Spring Boot 动手项目：

1. 执行版本检查，记下 java、javac 和 Maven 实际使用的版本。
2. 自己运行 Hello.java，观察源码和 class 的区别。
3. 打开芋道根 POM、yudao-dependencies 与 yudao-server 的 POM，找到第 8 节列出的字段。
4. 用自己的话回答下面三个问题。

**问题 A：**已经把 Spring Boot 应用打成可执行 JAR，运行它时为什么通常不需要 Maven？

**参考答案：**Maven 负责下载依赖、组织编译、测试和打包。Spring Boot 的可执行 JAR 已包含应用代码、所需依赖和启动加载器，运行时使用 `java -jar app.jar`，由 JVM 加载并执行代码，不需要 Maven 再参与构建。目标机器仍需要兼容的 Java 运行环境。

记忆要点：**Maven 负责构建程序，JVM 负责执行程序。**

**问题 B：**父 POM 在 dependencyManagement 中管理了一个库的版本，子模块是否就自动拥有这个库？为什么？

**参考答案：**不会。子模块通过 `<parent>` 继承父 POM 的配置，包括 dependencyManagement 中的版本规则；要实际使用该库，仍需在自己的 `<dependencies>` 中声明它，可以省略已管理的版本。例如：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

记忆要点：**继承到版本规则，不等于引入了这个库。**标签名是单数 `<parent>`，不是 `<parents>`。本题前提是父 POM 仅在 dependencyManagement 中管理该库。

**问题 C：**执行 package 后看到 BUILD SUCCESS，是否可以断定服务已经开始监听端口？为什么？

**参考答案：**不能。`package` 是打包操作，本身不会把业务服务作为持续运行的应用启动起来。即使程序没有错误，打包完成后通常也没有服务监听端口，还需要单独启动。打包成功也不能证明数据库等运行条件正常。

### 本轮学习记录

- A：最初不清楚，已补充构建与运行的区别。
- B：已知道不能自动获得依赖，已纠正为“parent 继承版本规则，dependencies 声明实际依赖”。
- C：判断正确，已补充“打包本身不会持续启动服务”。
- 按学习安排进入第二课；以下实践验收项保留，后续结合 Spring Boot 动手课程补齐。

完成阅读不等于本章完成。以下能力全部通过后，再更新目录状态：

- [ ] 能区分 JDK、JVM、Maven、Spring Boot 和 Web 服务器的职责。
- [ ] 能解释依赖声明、版本管理、聚合与继承。
- [ ] 能找到项目启动入口，区分构建和运行。
- [ ] 能创建、调试、打包并运行独立练习服务。
- [ ] 能根据依赖树定位一个基本依赖问题。

后两项在本章后续动手练习中完成，当前尚未验收。
