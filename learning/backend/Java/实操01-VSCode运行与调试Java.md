# 实操 01：在 VS Code 中运行与调试 Java

[返回学习目录](READEME.md)

> 笔记状态：完整课程。学习状态：待实践。  
> 适用环境：Windows、VS Code、Java 17；界面名称同时给出常见英文，菜单位置可能随版本变化。  
> 建议顺序：第一章后即可学习 1—6 节；第 7—9 节结合 Maven 与 Spring Boot 项目操作。  
> 本课目标：从点击运行，到用断点观察方法调用，再到启动指定的 Spring Boot 模块。

## 1. 准备 VS Code 的 Java 开发环境

### 安装扩展

1. 打开扩展面板：`Ctrl+Shift+X`。
2. 搜索并安装 Microsoft 的 **Extension Pack for Java**。其中包含 Java 语言支持、调试器、测试及 Maven 等工具。
3. 用 Spring Boot 时可再安装 **Spring Boot Extension Pack**，提供相关代码支持和应用面板；它不是运行普通 Java 的前提。

本课使用 Java 扩展提供的 **Run Java / Debug Java**，不依赖通用 Code Runner 扩展。[VS Code Java 入门](https://code.visualstudio.com/docs/java/java-tutorial)

### 确认项目使用的 JDK

打开命令面板 `Ctrl+Shift+P`，执行 **Java: Configure Java Runtime**，查看项目关联的 JDK，课程项目选择 Java 17。在终端执行 `java -version` 只检查终端的 Java，不能单独证明 IDE 使用相同版本。

如果项目没正确识别，先用“打开文件夹”打开项目目录，再等待导入完成。Maven 项目的语言级别由 POM 配置共同决定，不要只改一个全局下拉框就以为所有项目都换了版本。[Java 项目管理](https://code.visualstudio.com/docs/java/java-project)

**区分两个 Java 环境：**扩展自身的语言服务与业务项目可以使用不同运行环境。当前部分平台扩展自带语言服务运行环境；不带内置运行环境的版本可能要求 Java 21+ 启动工具，但业务项目仍可以使用 Java 17。不要为消除工具提示就随意升级项目 POM。[Java 扩展要求](https://github.com/redhat-developer/vscode-java#requirements)

## 2. 先运行一个无需 Maven 的程序

用 VS Code 的“文件 → 打开文件夹”打开一个专用练习目录，例如你自己创建的 `java-debug-practice`。新建 **DebugDemo.java**，保存下面的完整代码：

```java
public class DebugDemo {
    public static void main(String[] args) {
        int price = 20;
        int quantity = 3;
        int total = calculateTotal(price, quantity);
        System.out.println("总价：" + total);
    }

    static int calculateTotal(int price, int quantity) {
        int result = price * quantity;
        return result;
    }
}
```

这里用整数金额简化调试操作，不是生产计费模型。

保存后等待 Java 扩展加载，main 上方通常会出现 **Run | Debug**。点击 **Run**，预期输出：

```text
总价：60
```

普通 Run 的目的是运行程序，不会按你设置的断点暂停。调试时点击 Debug，或用 F5；不调试运行通常是 Ctrl+F5。若快捷键冲突，使用顶部“运行”菜单或 main 上方按钮。

不需要每次手动执行 javac，Java 工具会处理必要编译；但编译错误仍需先修复。输出位置取决于控制台配置，第 6 节会固定为集成终端。

## 3. 打第一个断点，观察变量

**断点是你指定的暂停位置。**点击下面这行左侧的行号边栏，出现红点：

```java
int total = calculateTotal(price, quantity);
```

操作顺序：

1. 保存文件，在这行设置断点。
2. 点击 main 上方的 **Debug**。
3. 等程序停在该行，编辑器会高亮当前执行位置。
4. 打开左侧“运行和调试”面板（Run and Debug，`Ctrl+Shift+D`）。
5. 查看 **Variables（变量）** 中的 price 和 quantity，应分别为 20、3；也可以悬停在变量上查看。

**普通行断点通常在该行执行前停住。**所以此刻 total 还没有被赋值，不能因为看不到它就以为调试器出错。

执行一次 **Step Over（单步跳过，F10）**，停到下一行后，再观察 total，此时应为 60。这里的“跳过”不是不执行，而是不进入方法内部逐行观察。

## 4. 单步进入、跳出与调用栈

重新启动调试，再停到 `calculateTotal(...)` 调用行。这次使用 **Step Into（单步进入，F11）**。

你会进入 calculateTotal 方法。此时：

- 当前方法的参数 price、quantity 分别是 20、3。
- 执行乘法那一行后，局部变量 result 变为 60。
- **Call Stack（调用堆栈）** 能显示当前方法由 main 调用。

点击调用栈中的 main，可以查看调用者当时的变量；这只是选择观察哪一层，不会让程序回到过去重新执行。

| 操作 | Windows 默认快捷键 | 本例中会怎样 |
| --- | --- | --- |
| Continue，继续 | F5 | 运行到下一个断点或结束 |
| Step Over，单步跳过 | F10 | 执行当前行，通常不进入被调方法 |
| Step Into，单步进入 | F11 | 进入 calculateTotal 内观察 |
| Step Out，单步跳出 | Shift+F11 | 继续执行当前方法，返回调用者 |
| Restart，重启 | Ctrl+Shift+F5 | 重新开始本次调试 |
| Stop，停止 | Shift+F5 | 结束当前调试会话；本课 launch 场景下停止启动的程序 |

笔记本可能需要配合 Fn 键。按钮悬停也能查看操作名和当前快捷键。[VS Code 调试基础](https://code.visualstudio.com/docs/debugtest/debugging)

## 5. Watch、条件断点与异常暂停

### Watch：持续观察一个表达式

暂停后，在 **Watch（监视）** 中添加：

```java
price * quantity
```

它根据当前选中的调用栈计算结果，本例是 60。超出变量作用域时可能显示无法求值。Debug Console（调试控制台）也能在暂停状态求值，例如输入 `quantity + 1`。

练习时先观察变量和简单运算。调用方法求值可能改变程序状态，例如执行 add 会真的添加元素。

### 条件断点：满足条件才停

右击已经设置的红点，选择编辑断点，将表达式条件设为：

```java
quantity > 5
```

以 quantity=3 启动，不会在这里停；把 main 中的 quantity 改为 6、保存并重新 Debug，就会暂停。条件断点适合循环中只观察特定数据。

### 异常暂停：在出错位置观察

完成前面的练习后，把 main 中 quantity 改为 0，并临时把 calculateTotal 的计算行改为：

```java
int result = price / quantity;
```

在 Run and Debug 的 **Breakpoints（断点）** 区域启用异常暂停，例如 **Uncaught Exceptions（未捕获异常）**，然后重新调试。

本例整数除以零会抛 ArithmeticException。暂停时查看 quantity=0 和调用栈，理解“在哪一行出错、参数从哪里来”。练习完成后恢复乘法与 quantity=3。项目很大时，启用所有已捕获异常可能暂停过于频繁。

Java 调试器支持变量、表达式和多种断点；界面选项取决于扩展版本。[Java 调试功能](https://code.visualstudio.com/docs/java/java-debugging)

## 6. launch.json：保存你的启动方式

简单程序通常无需手写配置。需要固定入口、参数和工作目录时，在“运行和调试”面板选择创建 launch.json，选择 Java。文件位于**当前打开文件夹**的 `.vscode/launch.json`。

对第 2 节的练习目录，可使用下面的完整配置。如果文件已有其他配置，把对象加入 configurations 数组，不覆盖原配置。

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "调试 DebugDemo",
      "request": "launch",
      "mainClass": "DebugDemo",
      "cwd": "${workspaceFolder}",
      "console": "integratedTerminal"
    }
  ]
}
```

| 字段 | 用途 |
| --- | --- |
| type | 使用 Java 调试器 |
| name | 调试下拉菜单里的显示名称 |
| request: launch | 由 VS Code 启动新程序并调试 |
| mainClass | 程序入口类，有 package 时需要完整包名 |
| cwd | 工作目录，相对文件路径从这里解析 |
| console | 本例在集成终端显示输出，也支持读取终端输入 |

`${workspaceFolder}` 是 VS Code 变量，代表当前工作区文件夹。保存后，从调试下拉菜单选择“调试 DebugDemo”，按 F5。

工作目录与源码文件目录并非总是相同。例如 `Path.of("sample.txt")` 会从 cwd 下找文件，而不是从 class 所在位置找。

## 7. 在 VS Code 中打开 Maven 项目

打开**包含根 pom.xml 的文件夹**。对于参考项目，打开 `D:\mine_projects\ruoyi-vue-pro`，确认分支为 `master-jdk17`，而不是单独打开某个 Controller.java。

操作顺序：

1. 等待 Java 项目导入与依赖解析完成，查看 **Java Projects** 是否出现各模块。
2. 查看 **Problems（问题）** 面板。如果 Spring 等依赖全部报红，先解决导入或依赖问题。
3. 修改 POM 后若 IDE 未更新，可从命令面板执行 **Java: Reload Projects**。
4. 在 **Maven** 面板中可以查看项目与执行构建目标；初次操作也可以在集成终端使用下列明确命令。

```powershell
Set-Location 'D:\mine_projects\ruoyi-vue-pro'
mvn -v
```

本仓库没有 mvnw.cmd，以上命令需要已安装并配置 Maven。若 mvn 未识别，先处理 Maven 环境；Java Projects 的项目导入成功并不保证终端也能找到 mvn。

需要编译启动模块及其依赖模块时，在项目根目录执行：

```powershell
mvn -pl yudao-server -am compile
```

`-pl` 选择模块，`-am` 同时构建它依赖的本项目模块。命令会执行相应构建步骤，包括注解处理，可能因依赖、JDK、源码等问题失败；它不会启动服务。芋道使用 Lombok、MapStruct，需等待 IDE 正确导入其构建配置。

VS Code 的自动编译和 Maven 完整构建并非同一个流程。IDE 没红线，不代表项目格式检查、测试等已经通过。[Java 项目管理](https://code.visualstudio.com/docs/java/java-project)

## 8. 运行与调试 Spring Boot 模块

### 找到正确入口

已核对芋道主应用入口为：

```text
yudao-server/src/main/java/cn/iocoder/yudao/server/YudaoServerApplication.java
```

打开文件，在 main 上方选择 Debug。基础后台需要本地 MySQL 数据、Redis 等环境，启动按钮不会替你准备这些依赖。先按芋道启动说明准备配置，当前课程不要求启用 AI 或其他可选业务模块。

Spring Boot Dashboard 可以列出应用，并提供运行或调试入口；本课选择 YudaoServerApplication。system、infra 是加载进主应用的业务模块，不需要各起一个进程。[VS Code Spring Boot 支持](https://code.visualstudio.com/docs/java/java-spring-boot)

### 保存带 Profile 的启动配置

下面是放在**目标项目** `.vscode/launch.json` 的启动配置对象，加入其 configurations 数组即可。这是示例，不是已经写入目标项目的实际配置：

```json
{
  "type": "java",
  "name": "芋道本地调试",
  "request": "launch",
  "mainClass": "cn.iocoder.yudao.server.YudaoServerApplication",
  "cwd": "${workspaceFolder}",
  "args": ["--spring.profiles.active=local"],
  "console": "integratedTerminal"
}
```

`local` 对应 `yudao-server/src/main/resources/application-local.yaml`，不保证数据库和 Redis 已可用。如果 VS Code 提示有多个候选项目，使用其生成配置中的 projectName，或填写 Java Projects 显示的准确项目名，不靠猜测。

需要添加其他参数时，分清位置：

| 配置项 | 示例 | 传给谁 |
| --- | --- | --- |
| args | `["--spring.profiles.active=local"]` | 应用，Spring Boot 读取配置参数 |
| vmArgs | `"-Xmx512m"` | JVM，本例设置最大堆内存 |
| env | `{"SERVER_PORT": "8082"}` | 被启动进程的环境变量 |

env 中的端口仅作为可选示例；本地所查 local 配置使用 48080，改端口还需同步前端请求地址。先保持已知可用的端口，不把无关改动混进断点练习。不要把真实密码保存在要提交的 launch.json 中。

## 9. 让一个 HTTP 请求停在断点上

启动服务后，Controller 内的断点通常不会自动命中：**必须有请求执行到那段代码**。

使用芋道“通知公告”的分页查询方法 `NoticeController.getNoticePage`。源码位于 `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/controller/admin/notice/NoticeController.java`，在以下行设置断点：

```java
PageResult<NoticeDO> pageResult = noticeService.getNoticePage(pageReqVO);
```

练习步骤：

1. 在该赋值行设置断点，用 Debug 启动 YudaoServerApplication。
2. 等启动日志确认服务可用。
3. 使用有通知公告查询权限的账号登录前端，打开通知公告列表；或者重发浏览器 Network 中已经成功的列表请求。默认管理接口为 `GET /admin-api/system/notice/page`，以实际配置为准，保留认证与租户相关请求头。
4. 暂停后查看 pageReqVO 的 pageNo、pageSize 等值；赋值行尚未执行时，pageResult 还不可用。
5. 在 `NoticeServiceImpl.getNoticePage` 的 `return noticeMapper.selectPage(reqVO);` 行增加断点，F5 继续到那里；再观察 NoticeMapper 的分页条件。
6. 观察查询前后的变量，再继续让请求完成。

直接 Step Into 有时会进入 Spring 代理或拦截逻辑，初学时在具体 Service 方法中增加断点更容易定位自己的代码。

暂停期间，请求可能一直显示等待，长时间暂停可能超时，不一定是服务死锁。如果返回 401/403 却没命中 Controller，先检查登录、租户与方法权限；查询需要 `system:notice:query` 权限，不要为打断点删除权限注解。

这部分是操作教程，本次没有启动目标服务，也没有执行实际接口请求。

## 10. 代码导航：减少来回找文件

| 需求 | 常见操作 |
| --- | --- |
| 打开某个 Java 类文件 | Ctrl+P，输入文件名 |
| 查看方法定义 | F12，或右键 Go to Definition |
| 从接口找到实现 | 右键 Go to Implementations |
| 查看方法在哪里被调用 | 右键 Find All References |
| 查看类型和方法说明 | 悬停查看提示 |
| 统一重命名一个符号 | F2，检查改动预览 |

这些操作依赖项目正确导入。方法实现与调用栈是不同概念：前者是静态源码关系，后者反映本次运行的实际调用路径。

## 11. 常见卡点：按现象检查

| 现象 | 优先检查 |
| --- | --- |
| main 上方没有 Run / Debug | 文件保存为 .java、main 签名、扩展是否启用、项目导入是否完成；也可尝试右上角 Debug Java |
| 大量依赖报红 | 打开的是不是根项目；Maven 仓库与依赖是否可用；查看 Problems 和 Java 输出 |
| 断点空心或提示未验证 | 类是否加载、是否有编译错误、正在运行的代码版本是否对应当前源码 |
| 红点正常但一直不停 | 是否用 Debug；是否执行到该行；是否选择了正确服务和进程；条件是否成立 |
| 修改代码后仍像旧逻辑 | 保存、停止并重新 Debug；不要默认所有代码变更都能热替换 |
| 找不到入口类 | package 与 mainClass 是否一致；是否导入了对应模块 |
| 端口已被占用 | 是否还有之前启动的服务；先确认并停止自己的旧实例 |
| 文件读取失败 | cwd、相对路径、文件是否存在 |
| VS Code 正常但终端构建失败 | 对比 JDK、Maven 配置及构建检查；以具体错误为准 |

必要时在 Output（输出）中选择对应 Java 工具的输出通道。不要一遇到红线就删除整个 Maven 仓库或重装所有工具。

## 12. 独立练习、自检与参考答案

### 练习清单

- [ ] 使用 Run 得到“总价：60”。
- [ ] 在方法调用前命中断点，看到 price=20、quantity=3。
- [ ] 分别使用 Step Over 和 Step Into，解释区别。
- [ ] 在方法内查看调用栈，并返回调用者。
- [ ] 设置 quantity > 5 条件，验证 3 不停、6 暂停。
- [ ] 通过异常暂停定位除零错误，之后恢复代码。
- [ ] 保存并选择 launch.json 配置启动。
- [ ] 在环境可用时，用 Debug 启动 Spring Boot，命中一次查询接口断点。

### 自检答案

1. **为什么 total 暂停时还看不到？**断点停在赋值行执行前，变量尚未完成赋值。
2. **F10 是否不执行方法？**不是，它会执行，只是通常不进入方法内部逐行观察。
3. **为什么服务启动了但 Controller 断点没命中？**启动与请求处理不同，需要请求到达对应方法，也要确认调试的是正确进程。
4. **Profile 写 args 还是 vmArgs？**本课的 `--spring.profiles.active=local` 写 args；vmArgs 用于 JVM 参数。
5. **为什么 java -version 正确，IDE 仍可能报 JDK 问题？**终端、语言服务、项目运行环境可能不同，要分别检查。

本课笔记可独立使用；实际界面操作与目标项目启动结果由学习者完成后记录，不把笔记写齐视为验收通过。
