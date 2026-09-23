# Maven、JVM 与 Spring 生命周期

学习 Java 后端时，经常会看到“生命周期”“编译期”“运行时”“启动阶段”等词。它们不是同一件事，而是分别描述不同层次的过程。

```text
Maven 构建项目
        ↓
JVM 启动并执行 Java 程序
        ↓
Spring 创建容器并管理 Bean
        ↓
业务代码运行
        ↓
应用和容器关闭
```

这表示“先构建、再独立启动应用”的一种常见顺序，不是互不重叠的阶段表。Maven 本身也运行在 JVM 中；启动、初始化和正常关闭都属于程序的运行时。

## 1. 先区分三个概念

| 生命周期 | 主要回答的问题 | 负责者 | 例子 |
| --- | --- | --- | --- |
| Maven 构建生命周期 | 源代码怎样变成可交付产物？ | Maven 和插件 | 编译、测试、打包、安装 |
| JVM 生命周期 | Java 类怎样被加载和执行？ | JVM | 加载 `.class`、执行 `main`、垃圾回收 |
| Spring 生命周期 | Spring Bean 怎样被发现、创建和管理？ | Spring 容器 | 扫描组件、依赖注入、创建代理 |

因此，第一节中提到的 `validate → compile → ... → deploy` 只是 Maven 的构建生命周期，不是 Java 项目从开发到退出的完整生命周期。

## 2. Maven 构建生命周期

### 2.1 Maven 负责什么

Maven 是构建工具。它读取 `pom.xml`，根据项目配置完成依赖下载、源码编译、测试执行和产物打包。

可以把它理解成工厂的生产流程：

```text
Java 源代码 + 依赖 + pom.xml
              ↓
         Maven 构建
              ↓
     .class、JAR 或 WAR
```

Maven 处理的是“如何构建项目”，不负责在生产环境中持续处理用户请求，也不负责 Spring Bean 的业务行为。

### 2.2 default 生命周期的阶段

Maven 的 `default` 生命周期完整阶段如下：

```text
validate
initialize
generate-sources
process-sources
generate-resources
process-resources
compile
process-classes
generate-test-sources
process-test-sources
generate-test-resources
process-test-resources
test-compile
test
prepare-package
package
pre-integration-test
integration-test
post-integration-test
verify
install
deploy
```

平时最常用的阶段可以这样理解：

| 阶段 | 含义 |
| --- | --- |
| `validate` | 检查项目配置是否基本正确 |
| `compile` | 编译主代码，通常是 `.java → .class` |
| `test-compile` | 编译测试代码 |
| `test` | 执行单元测试 |
| `package` | 打包成 `jar`、`war` 等产物 |
| `verify` | 执行更完整的验证 |
| `install` | 将产物安装到本机 Maven 仓库 |
| `deploy` | 将产物发布到远程 Maven 仓库 |

Maven 阶段具有顺序关系。执行：

```powershell
mvn package
```

相当于执行从 `validate` 到 `package` 的所有前置阶段，而不是只执行最后一个阶段。

`deploy` 在这里通常指“上传构件到远程 Maven 仓库”，不等于把 Web 服务部署到服务器。

### 2.3 Maven 还有其他生命周期

Maven 内置了三个生命周期：

```text
default：构建、测试、打包和发布
clean：清理构建结果
site：生成项目站点和报告
```

`clean` 生命周期的阶段是：

```text
pre-clean → clean → post-clean
```

所以：

```powershell
mvn clean package
```

是先执行 `clean` 生命周期，再执行 `default` 生命周期中的 `package` 阶段。

### 2.4 生命周期、阶段和目标的区别

这三个词容易混淆：

```text
生命周期 Lifecycle
    └── 阶段 Phase
            └── 插件目标 Goal
```

例如：

```text
default 生命周期
    └── compile 阶段
            └── maven-compiler-plugin:compile 目标
```

执行：

```powershell
mvn compile
```

调用的是生命周期阶段；Maven 会根据 `pom.xml` 中的绑定，找到对应的插件目标。

执行：

```powershell
mvn exec:java
```

则是直接调用 `exec-maven-plugin` 的 `java` 目标。它不是 `default` 生命周期中的标准阶段，而是一个插件目标。

## 3. JVM 生命周期

Java 源码编译完成后，得到的是 JVM 可以执行的字节码。这里要区分 JVM 进程的启动与退出，以及进程中各个类的加载、链接、初始化。下面用主类进入执行的过程串联这些概念。

```text
启动 JVM
    ↓
加载主类
    ↓
链接主类
    ↓
初始化主类
    ↓
执行 main 及后续代码
（期间可能继续加载其他类，并进行 JIT、GC、线程调度等）
    ↓
JVM 退出
```

### 3.1 启动 JVM

执行：

```powershell
java com.example.demo.App
```

操作系统会启动一个 JVM 进程。JVM 创建运行所需的内存区域、线程和类加载器，然后准备执行主类。

### 3.2 加载、链接和初始化类

JVM 使用类加载器找到 `App.class`，然后大致经历：

1. 加载：把类的字节码读入 JVM。
2. 验证：检查字节码格式和安全性。
3. 准备：为静态变量分配空间。
4. 解析：将常量池中的符号引用转换为实际引用，具体时机可能由 JVM 决定。
5. 初始化：执行静态变量初始化和静态代码块。

例如：

```java
public class App {
    private static String name = initName();

    private static String initName() {
        System.out.println("类初始化");
        return "Java";
    }
}
```

`initName()` 在类初始化时执行，属于运行时。其他类也可能在应用处理业务时才首次初始化，并不是所有类都必须在 `main` 之前完成初始化。

### 3.3 执行与退出

JVM 找到：

```java
public static void main(String[] args)
```

后开始执行程序。运行期间，JVM 还会负责：

- 管理线程；
- 分配和回收堆内存；
- 解释执行字节码；
- 对热点代码进行 JIT 编译；
- 处理异常和 I/O。

当所有非守护线程结束，或者程序主动退出时，JVM 进程结束。

因此，简单控制台项目在 `main` 方法执行完、且没有其他存活的非守护线程时，会正常退出。如果仍有这类线程运行，`main` 返回不一定意味着整个 JVM 随即退出。

## 4. Spring 生命周期

Spring 生命周期主要讨论 Spring 容器中的 Bean 如何从定义变成可使用对象，再到容器关闭时被销毁。

```text
创建 ApplicationContext
        ↓
读取 XML、Java 配置或扫描注解
        ↓
注册 BeanDefinition
        ↓
创建 Bean
        ↓
注入依赖
        ↓
执行初始化处理
        ↓
创建代理或完成后置处理
        ↓
Bean 可被业务代码使用
        ↓
容器关闭，执行销毁处理
```

### 4.1 创建容器

例如 003 示例中的代码：

```java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext("com.example.demo");
```

这一步不只是创建一个普通对象，还会启动 Spring 的容器流程。

### 4.2 发现和注册 Bean

Spring 会扫描指定包，发现带有组件注解的类：

```java
@Repository
public class UserDaoImpl {
}

@Service
public class UserServiceImpl {
}

@Component
@Aspect
public class LogAspect {
}
```

这些类会被登记为 Bean 定义。Bean 定义可以理解为“Spring 以后应该如何创建这个对象的说明”。

### 4.3 创建 Bean 和注入依赖

`UserServiceImpl` 依赖 `UserDaoImpl`：

```java
@Service
public class UserServiceImpl {

    @Autowired
    private UserDaoImpl userDao;
}
```

Spring 创建 `UserServiceImpl` 后，会找到 `UserDaoImpl` Bean，并把它注入 `userDao` 字段。

`@Autowired` 的依赖注入发生在 Spring 创建、组装 Bean 的过程中，属于运行时。普通非懒加载单例通常在容器启动时创建；懒加载或 prototype Bean 则可能在后续获取时创建并注入。它不是 `javac` 编译时完成的。

### 4.4 初始化和代理

Bean 完成依赖注入后，Spring 可能执行：

- `@PostConstruct` 方法；
- `InitializingBean` 的初始化方法；
- 自定义初始化方法；
- `BeanPostProcessor` 的前置和后置处理。

在 AOP 场景中，Spring 还可能为目标 Bean 创建代理对象。例如：

```java
@Around("execution(* com.example.demo.service.*.*(..))")
```

调用 `UserServiceImpl.findUserList()` 时，实际可能先进入代理，再由代理执行切面逻辑，最后执行原始业务方法。

### 4.5 使用和销毁

容器完成初始化后，业务代码通过容器取得 Bean：

```java
UserServiceImpl service = context.getBean(UserServiceImpl.class);
```

业务代码使用 Bean 期间，Spring 负责维护它的作用域和相关生命周期。

容器关闭时，Spring 会执行销毁处理，例如：

```java
context.close();
```

对于由容器负责销毁的 Bean，例如通常的单例 Bean，Spring 会在关闭阶段调用其已注册的销毁回调。prototype Bean 等情况需要单独考虑清理责任。销毁回调与 JVM 回收对象内存不是同一件事。

## 5. 用 003 示例把三种生命周期串起来

在 `003-spring-framework-demo` 中执行：

```powershell
.\mvnw.cmd compile exec:java
```

实际过程可以拆成下面几步：

```text
0. 启动 Maven
   Maven 本身已在 JVM 中运行

1. Maven compile 阶段
   编译 App.java、UserServiceImpl.java 等源码
   得到 .class 文件

2. exec:java 在 Maven 所在的 JVM 内运行指定主类
   加载 App.class，执行 main 方法

3. Spring 容器启动
   扫描 com.example.demo 包
   发现 @Service、@Repository、@Component

4. Spring 创建并组装 Bean
   创建 UserDaoImpl
   创建 UserServiceImpl
   处理 @Autowired
   创建 AOP 代理

5. 业务代码运行
   调用 findUserList()
   LogAspect 输出方法名
   DAO 返回模拟用户

6. main 方法结束
   示例的主方法返回，exec:java 完成
   Maven 完成命令并退出；不是 main 一返回就必定立即终止整个 JVM
```

`exec:java` 复用 Maven 所在的 JVM；直接执行 `java ...` 启动应用则是另一种方式。[Exec Maven Plugin 官方说明](https://www.mojohaus.org/exec-maven-plugin/java-mojo.html)

对应关系如下：

| 代码或命令 | 属于哪一层 | 发生的事情 |
| --- | --- | --- |
| `mvn compile` | Maven | 编译源码 |
| `mvn package` | Maven | 测试并打包 |
| `exec:java` | Maven 插件 + JVM | 在 Maven 所在的 JVM 内运行指定主类 |
| `main` | JVM | Java 程序入口开始执行 |
| `new AnnotationConfigApplicationContext(...)` | Spring | 创建容器并扫描组件 |
| `@Autowired` | Spring | 注入依赖 |
| `@Around` | Spring AOP | 运行时通过代理增强方法 |
| `findUserList()` | 业务运行时 | 执行实际业务逻辑 |

## 6. 编译期、构建期、启动期和运行时

这几个词可以这样区分：

| 术语 | 发生时间 | 典型内容 |
| --- | --- | --- |
| 开发期 | 写代码时 | 编写 Java、配置和测试 |
| 编译期 | `javac` 或 Maven 编译阶段 | 语法检查、类型检查、生成字节码 |
| 构建期 | Maven 执行构建流程时 | 下载依赖、编译、测试、打包 |
| 启动期 | JVM 和 Spring 启动时 | 加载类、扫描组件、注入依赖、建立连接 |
| 运行时 | 程序正在被执行时，包含启动和正常关闭 | 初始化、执行方法、处理请求、运行 JIT |
| 关闭期 | 程序或容器停止时 | 关闭连接、销毁 Bean、停止线程 |

例如：

- `import` 写错，通常是编译期错误。
- Maven 依赖下载失败，是构建期问题。
- `@Autowired` 找不到候选 Bean，是 Bean 创建过程中的问题，常见于启动期，也可能在之后创建 Bean 时暴露。
- 数据库密码错误，通常在应用启动或运行时暴露。
- 用户请求参数不合法，是运行时业务问题。

## 7. 常见误解

### Maven 的 `package` 会启动应用吗？

不一定。它主要负责编译、测试和打包。除非额外配置插件，否则它不会长期启动你的服务。

### `@Autowired` 是编译器执行的吗？

不是。`@Autowired` 的注解信息会保留到类文件中，真正的依赖注入由 Spring 容器在运行时创建、组装 Bean 时完成，常见于容器启动阶段。

### `mvn deploy` 是把服务部署到服务器吗？

默认不是。它通常是把 JAR、POM 等 Maven 构件上传到远程仓库。把应用部署到服务器、容器平台或云环境，是另一套发布流程。

### Java 有没有一个统一的“项目生命周期”？

没有一个覆盖所有工具和框架的统一阶段表。Maven、JVM、Spring、Spring Boot、Docker 和部署平台都可能有自己的生命周期。学习时先判断这个词属于哪一层。

## 8. 记忆方式

```text
Maven：把代码造出来。
JVM：把字节码跑起来。
Spring：把运行时对象组装起来并管理好。
```

在实际排查问题时，先判断问题发生在哪一层：

```text
构建失败       → 看 Maven、依赖和插件
类找不到       → 看 JVM、classpath 和打包结果
Bean 找不到    → 看 Spring 扫描和配置
注入失败       → 看 Spring Bean 类型和依赖关系
请求处理失败   → 看运行时业务、数据库和外部服务
```
