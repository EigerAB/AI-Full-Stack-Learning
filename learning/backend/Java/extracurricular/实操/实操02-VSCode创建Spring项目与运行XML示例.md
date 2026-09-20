# 实操 02：VS Code 创建 Spring 项目与运行 XML 示例

> 本篇整理两个问题：创建向导能生成哪些文件？怎样在 VS Code 中运行、调试目前的 Spring XML 示例？
> 环境：Windows、PowerShell、VS Code；当前示例使用 JDK 17 运行。

## 1. 快捷创建项目，会自动生成业务文件夹吗？

**Spring Initializr 会生成 Spring Boot 的基础工程，不会根据你的业务自动生成 aspect、dao、entity、service 等包及实现。**截图中的这些业务包由开发者或专用代码生成工具创建，不是 Spring 强制要求的目录。

| 内容 | 一般由谁创建 | 用途 |
| --- | --- | --- |
| pom.xml | Maven 项目生成工具 | 声明项目依赖与构建配置 |
| src/main/java、基础包、启动类 | Spring Initializr | 放置应用代码并提供 Spring Boot 启动入口 |
| src/main/resources、初始配置文件 | Spring Initializr | 放置配置及资源，具体文件随生成选项变化 |
| src/test/java、初始测试类 | Spring Initializr | 放置测试代码 |
| .mvn、mvnw、mvnw.cmd | Maven Wrapper 生成工具；Initializr 通常会附带 | 不依赖全局 Maven 安装即可构建项目 |
| aspect、dao、entity、service | 开发者或专用代码生成工具 | 按职责组织业务代码 |
| aspects.xml、daos.xml、services.xml | 本次教程示例的作者 | 声明 Bean、依赖注入和 AOP 配置；不是 Initializr 默认生成文件 |
| .vscode/launch.json | 开发者或 VS Code 调试配置向导 | 指定调试入口等；本项目已配置 |
| target | Maven 或 IDE 构建过程 | 保存编译产物，不需要手工编写 |

截图里 App.java 与这些包同级；它是一个 Java 文件，不是文件夹。本次 XML 示例中的 App.java 也是作者编写的入口，不是 Initializr 生成的 Spring Boot 启动类。

### 1.1 用快捷入口创建 Spring Boot 项目

1. 按 **Ctrl + Shift + X** 打开扩展页，安装 **Extension Pack for Java** 和 **Spring Initializr Java Support**。也可安装包含后者的 Spring Boot Extension Pack。
2. 按 **Ctrl + Shift + P** 打开命令面板，搜索 `Spring Initializr`。
3. 选择 **Spring Initializr: Create a Maven Project**。
4. 按向导选择 Spring Boot 版本、Java 语言、Group Id、Artifact Id、打包方式、Java 版本及依赖。
5. 选择保存位置，然后打开生成的项目文件夹。

创建独立 Web 练习时可以用 Group Id `com.example`、Artifact Id `hello-web`、Java 17，并选择 Spring Web 依赖。Spring Boot 版本以向导实际提供的兼容版本为准；不要用新生成的工程覆盖当前 XML 示例。

**Ctrl + Shift + P 是命令面板快捷键，不是 Spring 项目专用快捷键。**若想直接进入创建向导，依次按 Ctrl + K、Ctrl + S，搜索上述创建命令，再绑定一个没有冲突的快捷键。[VS Code 官方创建指南](https://code.visualstudio.com/docs/java/java-spring-boot)

### 1.2 本次 XML 示例与 Spring Boot 向导的区别

当前项目 `001-spring-framework-demo-helloworld-xml` 是一个**普通 Maven + Spring Framework 控制台项目**，用 XML 配置容器，未使用 Spring Boot。

因此，要复现这个教程，可以从普通 Maven 工程开始添加依赖、Java 类和 XML，也可以取得作者示例。Initializr 生成的是另一种起点，不能一键生成这篇教程的全部代码。

本地示例基于 [pdai 教程](https://pdai.tech/md/spring/spring-x-framework-helloworld.html) 与作者源码搭建，包名已改为 `com.example.demo`；保留原例 Spring 5.3.10 和 Java 8 编译目标，用 JDK 17 运行以便对照学习。

### 1.3 怎样手动创建截图中的包？

以创建 service 包为例：

1. 在左侧资源管理器展开 `src/main/java/com/example/demo`。
2. 右键 `demo` → 新建文件夹，命名为 `service`。
3. 在 service 中新建 `UserServiceImpl.java`。
4. 文件开头声明与源码目录相匹配的包名：

```java
package com.example.demo.service;

public class UserServiceImpl {
}
```

这是创建空类的演示，当前示例已有同名文件，不要覆盖现有实现。也可在 Java Projects 视图中使用扩展提供的新建包、新建类命令。

文件位置与包名对应如下：

```text
源码根目录：src/main/java
源码根目录内的位置：com/example/demo/service/UserServiceImpl.java
包名：com.example.demo.service
完整类名：com.example.demo.service.UserServiceImpl
```

包名不包含磁盘路径，也不包含 `src.main.java`。其他包使用该类时写：

```java
import com.example.demo.service.UserServiceImpl;
```

本例各包的职责：

| 包或文件 | 本例作用 |
| --- | --- |
| entity/User.java | 保存用户姓名、年龄等数据 |
| dao/UserDaoImpl.java | 返回模拟用户数据，不连接数据库 |
| service/UserServiceImpl.java | 调用 DAO 完成用户查询 |
| aspect/LogAspect.java | 在匹配的业务方法执行时打印方法名 |
| App.java | 创建 Spring 容器、获取 Service 并执行查询 |

VS Code 将 `java/com/example/demo` 显示在同一行，是紧凑文件夹显示效果，实际仍是多层目录。以后重命名包，要同步修改源码目录、package、import，以及 XML 类名、组件扫描范围、AOP 切点和启动配置中的引用。

## 2. 在 VS Code 中运行当前 Java 项目

### 2.1 打开正确的项目根目录

选择“文件 → 打开文件夹”，打开：

```text
D:\mine_projects\demo\java\demo\001-spring-framework-demo-helloworld-xml
```

左侧根目录应直接看见 pom.xml、src、mvnw.cmd。不要只打开单个 App.java 文件。首次加载需要等待 Java 扩展导入 Maven 项目和依赖。

打开上层目录并非一定错误，VS Code 可以管理多个项目；但如果父目录中混有零散 Java 练习，导入状态更难判断。当前先单独打开这个项目，也能直接使用它的 .vscode 启动配置。

### 2.2 方式一：点击 Run

打开：

```text
src/main/java/com/example/demo/App.java
```

找到 `public static void main(String[] args)`，点击上方的 **Run**。预期业务输出为：

```text
execute method: findUserList
pdai,18
```

第一行来自 AOP，第二行来自 main 中的用户信息打印。这个示例没有 Web 服务，不需要打开浏览器，也没有 localhost 页面。

### 2.3 方式二：在终端运行

选择“终端 → 新建终端”，执行：

```powershell
Set-Location 'D:\mine_projects\demo\java\demo\001-spring-framework-demo-helloworld-xml'
.\mvnw.cmd compile exec:java
```

`mvnw.cmd` 使用项目配置的 Maven 版本；首次使用需要联网下载。`compile` 编译源码，`exec:java` 通过项目已配置的插件运行 `com.example.demo.App`。除了上述业务输出，还会有 Maven 日志，成功时显示 BUILD SUCCESS。

这条命令已经在本机验证成功。它适合确认构建与程序执行情况，但**不会自动连接 VS Code 调试器，编辑器断点不会因此自动生效**。

不要只对 App.java 执行普通 `javac App.java`：本项目还需要 Spring、AspectJ 等依赖以及 XML 资源，交给 Maven 或已导入项目的 Java 扩展处理更方便。

### 2.4 方式三：断点调试

1. 在 App.java 中找到下面这行，在行号左侧单击，设置红色断点：

```java
List<User> userList = service.findUserList();
```

2. 点击 main 上方的 **Debug**，或按 **F5**，选择“运行 Spring XML 示例”。
3. 命中断点后，当前行会高亮，表示程序暂停在这里，通常还没有执行这行。
4. 按 **F10** 执行当前行，再在 Variables（变量）中展开 userList，查看用户对象。

默认快捷键：

| 按键 | 作用 |
| --- | --- |
| F5 | 启动调试；暂停时继续运行到下一个断点或结束 |
| Ctrl + F5 | 不附加调试器运行 |
| F10 | 单步跳过：执行当前行，不进入调用的方法内部 |
| F11 | 单步进入：尝试进入当前行调用的方法 |
| Shift + F11 | 单步跳出：运行到当前方法返回调用者 |
| Shift + F5 | 停止调试 |

这些操作对应的是调试器动作，实际按键可能受自定义快捷键或笔记本 Fn 设置影响。[VS Code Java 调试说明](https://code.visualstudio.com/docs/java/java-debugging)

想观察本例的调用过程，分别在下面三处设置断点：

```text
LogAspect.businessService 中的打印语句
    ↓
UserServiceImpl.findUserList 中调用 userDao 的语句
    ↓
UserDaoImpl.findUserList 中返回模拟数据的语句
```

通过 F5 在断点之间继续运行，比反复 F11 进入 Spring 代理内部更便于初学时观察业务流程。

### 2.5 为什么调试模式很快就退出？

本例的流程是“main 启动 → 创建容器 → 查询数据 → 打印结果 → 结束”。没有需要持续运行的服务线程时，程序结束，调试会话也就结束。

使用 Spring 容器不代表自动启动 Web 服务。它与持续监听 HTTP 请求的 FastAPI 服务不同。**没有断点时，点击 Debug 也会很快执行完；想停下来观察，先打断点。**

### 2.6 launch.json 是做什么的？

项目中的 `.vscode/launch.json` 已配置：

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "运行 Spring XML 示例",
            "request": "launch",
            "mainClass": "com.example.demo.App",
            "console": "integratedTerminal"
        }
    ]
}
```

`mainClass` 是入口的完整类名，不是文件路径，不加 .java 或 .class。`name` 是 F5 选择列表中的显示名称；`integratedTerminal` 表示使用 VS Code 内置终端。

## 3. 本次实际遇到的问题速查

| 现象 | 含义与处理 |
| --- | --- |
| Spring、AspectJ import 大量报红，包名也报红 | 检查 Maven 是否正确导入。本次日志显示源码未进入正确构建路径；单独打开含 pom.xml 的项目目录，等待 Java 初始化 |
| 命令行成功，编辑器仍报红 | 构建与编辑器分析状态可能不同。先重新导入项目；若仍有旧索引，命令面板执行 `Java: Clean Java Language Server Workspace` 并按提示重启 |
| main 上方没有 Run / Debug | 检查 Java 扩展、项目加载状态及 main 方法签名；若提示受限模式，确认这是自己可信的项目后启用相关功能 |
| Problems 显示 3 | 表示有 3 条诊断，可能是错误、警告或提示；点击查看具体内容，不等于运行失败 |
| 左下角错误 0、警告 3 | 当前编辑器诊断为 0 个错误、3 个警告；警告仍值得读，但不能据此判断程序崩溃 |
| context 下有黄色波浪线 | 先悬停确认具体警告；若提示资源未关闭，可用 try-with-resources 管理支持关闭的容器，不能只凭颜色确定原因 |
| 状态栏变成红色或橙色 | 某些主题用不同颜色表示调试状态，不代表程序报错 |
| 显示 Compiling… | 正在编译或准备调试；若长时间停留，查看 Java 输出与调试日志，不要连续重复启动 |
| 输出两行后退出 | 这是本例的正常结束；程序是控制台示例 |
| 出现 Upgrade Progress、plan.md | Java 升级工具的任务文件，不是普通运行所需步骤；解决导入问题不要求将示例升级到 Java 25 |

本篇聚焦当前示例的创建、运行与调试。更完整的 IDE 操作见 [实操 01：VS Code 运行与调试 Java](实操01-VSCode运行与调试Java.md)。
