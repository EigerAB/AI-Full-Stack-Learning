# 课外 01：Java 注解与反射——从看懂语法到读懂框架

> 适用：已有 Java 类、对象、接口和集合基础，正在学习第二章与第三章。  
> 环境：Java 17。配套程序不需要 Maven、Spring 或数据库。  
> 阅读方式：先读 1—5 节并运行示例，再读项目案例和练习；API 表按需回查。

这篇文章解决三个问题：`@Something` 究竟是什么？程序为什么能通过字符串找到方法？框架怎样把注解变成实际行为？学完后，你应该能沿着“注解定义 → 标注位置 → 读取代码 → 执行逻辑”查清一个简单注解的作用。

## 1. 从一个熟悉的问题出发

假设有一个公告表单，标题不能为空。普通写法是在保存前检查 `title == null || title.isBlank()`。当多个表单都有类似要求时，我们希望把“哪些字段需要检查”写在字段旁边，把检查代码集中起来。

于是可以设计这样的写法：

```java
@RequiredText(message = "标题不能为空")
private String title;
```

这里的 `RequiredText` 是**本课自己定义的注解**，不是 Java 内置校验功能。它只记录一条规则。真正检查标题的代码，还需要有人写、有人调用。

可以把整个过程拆成三步：

```text
注解：字段旁边记录“标题不能为空”
  ↓
反射：运行时找到这个字段，读取注解和字段值
  ↓
普通 Java 代码：判断值是否为空，把错误信息放进列表
```

你熟悉的 FastAPI 也常把声明和处理逻辑分开。这个经验有助于理解，但 Java 的 `@注解` 不等同于 Python 装饰器：写上它不代表立刻调用一个包装函数。

## 2. 注解是什么，语法怎么读？

### 2.1 注释给人看，注解可以让程序读取

“元数据”就是**描述代码的信息**。例如，“这个方法重写了父类方法”“这个字段对应某种字典”。注解为这些信息提供结构化的语法。

```java
@Override
public String toString() {
    return "一条公告";
}
```

`@Override` 让编译器检查重写关系。运行时调用 `toString()` 的行为来自 Java 方法机制，不是 `@Override` 在运行时触发调用。由谁读取注解，决定了它在哪里发挥作用。[Java 17 语言规范：预定义注解](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.4)

### 2.2 自定义注解的定义

下面是定义片段，所需 import 和完整文件见第 5 节的示例文件。

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface RequiredText {
    String message() default "文本不能为空";
}
```

逐行理解：

| 写法 | 在这里表示什么 |
| --- | --- |
| `@interface RequiredText` | 声明一个名为 RequiredText 的注解类型 |
| `String message()` | 声明名为 message 的注解元素，值必须是 String |
| `default "文本不能为空"` | 使用注解时不写 message，就采用这个值 |
| `@Target(ElementType.FIELD)` | 允许把这个注解标在字段上 |
| `@Retention(RetentionPolicy.RUNTIME)` | 保留到运行时，供反射读取 |

`message()` 看起来像接口方法，但这里是在定义**注解可以携带的数据**。使用时写 `@RequiredText(message = "标题不能为空")`，读取注解对象后用 `rule.message()` 取得字符串。你不需要自己写一个类来 `implements RequiredText`。

注解元素不能随意写成任意对象：允许基本类型、String、Class、枚举、其他注解，以及这些类型的一维数组；不能使用 null 作为元素值。本课只用 String，不需要现在背完整规则。[Java 17 语言规范：注解元素](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.1)

一个常见简写：若元素名为 `value`，且其他元素都有默认值，就可以写 `@SomeAnnotation("abc")`，相当于 `@SomeAnnotation(value = "abc")`。我们定义的是 `message`，不能直接省略这个名字。[Java 17 语言规范：单元素注解](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.7.3)

### 2.3 什么是“元注解”？

**用来描述注解类型的注解**，叫元注解。上面的 Target 和 Retention 就是。先掌握这两个，其余认识用途即可。

Retention 可以理解为“这张标签保留到哪一步”：

| 策略 | 源码中 | 编译后的 class 文件中 | 运行时能否用反射读取 |
| --- | --- | --- | --- |
| `SOURCE` | 有 | 不保留 | 不能 |
| `CLASS` | 有 | 保留 | 不能依赖运行时反射读取 |
| `RUNTIME` | 有 | 保留 | 可以 |

没有声明 Retention 时，默认是 CLASS，所以“没有写 Retention”不等于“可以反射读取”。[RetentionPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/RetentionPolicy.html)、[Retention](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Retention.html)

另两个常见元注解：`@Documented` 控制注解使用信息是否进入生成的 API 文档；`@Inherited` 影响对子类查询**类级别注解**时是否沿父类查找。Inherited 不会让字段或重写方法上的注解自动继承，也不处理接口上的注解继承。[Documented](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Documented.html)、[Inherited](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Inherited.html)

## 3. 反射是什么，为什么要有它？

普通业务代码通常已经知道对象的类型、方法名和参数，可以直接调用：

```java
NoticeForm form = new NoticeForm("Java 学习");
String text = form.preview("预览：");
```

但一个通用校验器事先不知道你会传入哪种表单。它需要在运行时问：“这个对象是什么类型？有哪些字段？哪些字段带有校验注解？”

**反射就是运行时检查类型信息，并据此操作对象的一套 API。**它让这种通用处理成为可能；普通业务调用仍优先直接写，类型错误更容易在编译时发现。

### 3.1 先分清对象与 Class 对象

```java
NoticeForm form = new NoticeForm("Java 学习");
Class<NoticeForm> type = NoticeForm.class;
```

`form` 是一份具体表单，里面存着标题；`type` 是描述 NoticeForm 类型的对象，能够告诉我们构造方法、字段和方法等信息。`NoticeForm.class` 不是“创建一份表单”，也不是文件路径。

常见的三种获取方式：

| 写法 | 什么时候用 |
| --- | --- |
| `NoticeForm.class` | 写代码时已经知道类型 |
| `form.getClass()` | 已有对象，取得它实际所属的类型 |
| `Class.forName("java.lang.String")` | 从类名字符串查找类型；名字不对可能抛 ClassNotFoundException |

`Class<?>` 表示“描述某个尚未限定的类型的 Class 对象”。`Class<NoticeForm>` 则把类型明确为 NoticeForm。问号不是说对象没有类型，而是这段代码不预先限定它。[Class API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)

### 3.2 Field、Method、Constructor 分别是什么？

这三个类描述 Java 类的成员：Field 描述字段，Method 描述方法，Constructor 描述构造方法。它们不是字段值或执行结果，而是可以用来读取、调用这些成员的对象。

```java
Method method = NoticeForm.class.getMethod("preview", String.class);
Object result = method.invoke(form, "预览：");
```

第一行：找名为 preview、参数类型为 String 的 public 方法。必须指定参数类型，因为 Java 允许同名方法重载。

第二行：在 form 这个对象上调用找到的方法，把 `"预览："` 作为实参传入。`invoke` 的返回类型是 Object；本例方法实际返回 String。它不是在调用名叫 method 的业务方法。[Method.invoke](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...))

## 4. 先区分“找到成员”和“允许访问”

| API | 查询范围 |
| --- | --- |
| `getFields()` / `getMethods()` | public 成员，包含继承的 public 成员 |
| `getDeclaredFields()` / `getDeclaredMethods()` | 当前类自己声明的成员，包含 private，不包含父类声明的成员 |
| `getConstructor(...)` | 当前类匹配参数的 public 构造方法；构造方法不继承 |
| `getDeclaredConstructor(...)` | 当前类匹配参数的构造方法，也能找到非 public 的 |

单数形式按名称或参数找一个，复数形式返回数组。Declared 表示“本类声明”，不等于“私有”。查询到 private 成员也不意味着调用者可以直接读写它。[Class 成员查询 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)

示例使用 `field.trySetAccessible()` 尝试允许反射访问，再检查布尔结果。不要把它理解成万能的“破解 private”：模块封装等限制仍然有效，某些情况下还可能出现安全相关异常。[AccessibleObject](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html)

我们把示例类放在同一个外部类中，是为了单文件可运行；这些嵌套类之间本来就有特殊的私有访问关系。这里保留访问检查，是展示通用处理代码应考虑访问权限，不能用本例证明“所有 private 字段都必须或都可以开放”。

## 5. 动手：用反射读取注解，检查公告标题

### 5.1 文件与运行命令

完整代码已放在 [AnnotationReflectionDemo.java](examples/extra01-annotation-reflection/AnnotationReflectionDemo.java)。这个文件包含注解、表单、校验器和 main，无需复制多个片段拼装。笔记在其他设备上阅读时，请连同 examples 目录一起同步。

在 Windows PowerShell 中依次执行：

```powershell
Set-Location 'D:\note\YangNote\learning\backend\Java\extracurricular\examples\extra01-annotation-reflection'
javac -encoding UTF-8 -d out AnnotationReflectionDemo.java
java '-Dfile.encoding=UTF-8' -cp out AnnotationReflectionDemo
```

其他设备先替换为你的实际目录。`-d out` 把编译产物放到 out，`-cp out` 告诉 JVM 去那里找类。公开类名与文件名都为 AnnotationReflectionDemo，运行命令最后写**类名，不加 `.java` 或 `.class`**。本例没有 package 声明。

预期输出：

```text
预览：Java 学习
正常标题：[]
空白标题：[标题不能为空]
null 标题：[标题不能为空]
```

### 5.2 先看 main：反射做了哪些事？

```java
Class<NoticeForm> type = NoticeForm.class;
NoticeForm form = type.getConstructor(String.class).newInstance("Java 学习");
```

这里先找到接收 String 的构造方法，再传入标题创建对象。它达到的结果类似 `new NoticeForm("Java 学习")`，只是选择构造方法的过程交给反射 API。

接着 main 查找并调用 preview，然后调用校验器。为了展示字段写入，又用 `getDeclaredField("title")` 找到字段，并用 `titleField.set(form, "   ")` 把 form 的标题改成空白。

`Field` 描述的是哪个字段，`form` 指定读写哪个对象，传入的字符串是新值。`field.get(form)` 读取的也是这份对象中的值，不是读取注释或字段名。[Field API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html)

这些代码也说明：注解没有阻止空白值被写进去，后续的 `validate(form)` 才会检查它。

### 5.3 校验器如何把两者连接起来？

核心代码如下，完整版本还包含字段类型与访问检查：

```java
for (Field field : target.getClass().getDeclaredFields()) {
    RequiredText rule = field.getAnnotation(RequiredText.class);
    if (rule == null) {
        continue;
    }
    // 完整示例在这里确认字段是 String，并检查访问权限。
    String value = (String) field.get(target);
    if (value == null || value.isBlank()) {
        errors.add(rule.message());
    }
}
```

按数据流理解：

1. 从 target 得到实际类型，再遍历该类声明的字段。
2. 查询每个字段上的 RequiredText；没标这个注解就跳过。
3. 读取字段当前的值。
4. 执行普通的 if 判断，发现 null 或空白文本时记录错误信息。

这里有两次不同的“读取”：`getAnnotation(...)` 读取规则；`field.get(target)` 读取业务数据。把规则和数据放在一起判断的，是我们写的 validate 方法。

main 上的 `throws ReflectiveOperationException` 是为了让教学示例中的反射错误直接显示出来。实际工程通常应在合适的调用边界记录上下文或转换异常，不要捕获后悄悄忽略。

### 5.4 这个小程序的边界

它只校验当前类声明的 String 字段，传入对象不能是 null；不递归检查子对象，也不遍历父类。`@Target(FIELD)` 只能限制标注位置，不能要求字段必须是 String，因此完整代码还有一次类型检查。

这是理解原理的练习。后续学习接口参数校验时再使用框架提供的成熟能力，不把这个小程序直接扩展成生产校验框架。

## 6. 回到芋道：沿着读取者查明注解作用

本节核对的是本地 `D:\mine_projects\ruoyi-vue-pro` 的 master-jdk17 代码。以下均为项目相对路径，方便其他设备定位。

### 6.1 定义：DictFormat 记录字典类型

文件：`yudao-framework/yudao-spring-boot-starter-excel/src/main/java/cn/iocoder/yudao/framework/excel/core/annotations/DictFormat.java`。

关键声明为：

```java
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface DictFormat {
    String value();
}
```

现在你可以读出来：它标在字段上，运行时可以读取，使用者需要提供一个 String 类型的 value。比如 `@DictFormat("some_dict_type")` 的含义就是携带字典类型字符串；这里的名称仅作语法示意，不代表项目存在这个字典。

注意：项目这里虽然写了 Inherited，但它对字段注解没有上述继承效果。看见一个元注解，仍要按 Java 的适用范围理解。

### 6.2 读取：DictConvert 取得这条信息

文件：`yudao-framework/yudao-spring-boot-starter-excel/src/main/java/cn/iocoder/yudao/framework/excel/core/convert/DictConvert.java`。

其中的实际方法：

```java
private static String getType(ExcelContentProperty contentProperty) {
    return contentProperty.getField().getAnnotation(DictFormat.class).value();
}
```

拆成三个动作：从 Excel 字段配置得到 Field；读取该字段的 DictFormat；用 value() 得到字典类型。

再看同文件中的 `convertToExcelData`：它取得字典类型，把待转换值转成字符串，调用 `DictFrameworkUtils.parseDictDataLabel(type, value)` 查询标签，最后生成 Excel 单元格数据。

于是“注解能转换字典”这个说法可以讲得更准确：**注解提供字典类型，转换器读取这个信息并执行转换。**只在任意字段上加注解，不代表任意代码路径都会经过这个转换器。此处只追踪它的局部处理链；完整的 Excel 导出配置留到相应课程。

### 6.3 今后遇到陌生注解，怎么查？

在 VS Code 中先跳转到注解定义，读 Target、Retention 和元素含义；再搜索注解名和读取代码，例如 `getAnnotation(DictFormat.class)`。最后确认读取代码在什么场景被调用。

某些框架通过工具类、生成代码或其他元数据处理方式识别注解，直接搜索 getAnnotation 不一定能找到入口。“暂时没搜到读取者”不等于“注解没有作用”。本课要建立的是追踪思路，不是统一的框架实现结论。

## 7. 常见误解与报错速查

| 现象或说法 | 应该怎样理解、排查 |
| --- | --- |
| “加了自定义注解，为什么没效果？” | 看是否有处理代码，以及是否真的调用了它 |
| `getAnnotation(...)` 返回 null | 检查是否 RUNTIME、是否读对元素、是否真的标注；方法、字段和类是不同位置 |
| `NoSuchMethodException` | 检查方法名、参数类型、public/Declared 查询范围；`int.class` 和 `Integer.class` 是不同类型 |
| `IllegalAccessException` | 找到成员后，访问权限仍可能不允许读写或调用 |
| `InvocationTargetException` | 通过反射调用的方法内部抛出了异常；查看 `getCause()` 获取原始原因 |
| “反射可以任意修改一切” | 错；访问、模块封装、final 字段等仍有约束 |
| “有反射，所以 Java 是动态类型语言” | 错；运行时检查类型的能力不改变 Java 的静态类型规则 |
| “注解都靠运行时反射生效” | 错；前面讲过 Override 由编译器检查，SOURCE 注解也根本不保留到运行时 |

方法调用异常依据 [Method.invoke 文档](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...))；访问限制依据 [AccessibleObject 文档](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html)。

旧教程中可能出现 `Class.newInstance()`。新练习使用 `getDeclaredConstructor(...).newInstance(...)` 或本例的 `getConstructor(...).newInstance(...)`，明确选择构造方法；前者并不自动解决访问权限。[Class.newInstance 已弃用说明](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#newInstance())

## 8. 三个小实验与参考答案

先预测结果，再修改配套文件并重新执行编译、运行命令。每次实验后恢复原代码，避免多个修改互相干扰。

### 实验 A：把 RUNTIME 改成 CLASS

**问题：**校验器还能读到 RequiredText 吗？

**答案：**不能。查询得到 null，字段被跳过，因此三个校验结果都变为 `[]`，包括空白和 null 标题。这不是数据合格了，而是这份教学校验器没有读到规则。它说明校验能力还需要测试覆盖，不能只看注解写得是否漂亮。

### 实验 B：去掉 title 上的 RequiredText

**问题：**反射还能读写 title 吗？空白标题是否报错？

**答案：**仍可以读写，注解不是字段存在或可反射访问的前提。校验器读不到规则，会跳过这个字段，空白标题不会被报告。

### 实验 C：保留注解，但不调用 validate

**问题：**通过反射把 title 设置成空白，会因为注解抛出校验异常吗？

**答案：**不会。注解只是信息，没有人执行本例的校验代码，就不会发生这项校验。main 中反射访问、参数类型等自身的问题仍可能抛出异常，要与业务校验区分。

学完后，用自己的话解释这行项目代码即可自查：

```java
contentProperty.getField().getAnnotation(DictFormat.class).value();
```

如果能说清“哪部分得到字段、哪部分得到注解、哪部分得到配置值，以及是谁使用这个配置值”，本课的主要目标就达到了。

## 9. 资料与核对说明

整理日期：2026-09-16。按本次指定的 cangjie-skill 思路，将资料转成“概念解释 → 可运行练习 → 项目追踪 → 自查题”，而非逐篇摘抄；语言与 API 规则以上文链接的 Java 17 官方文档为准，项目片段来自本地源码。

示例已在本机 JDK 17 编译、运行：正常文本、空白文本、null 的输出与正文一致；另在临时目录验证了改为 CLASS 和去掉字段注解两种变体，均与实验 A、B 的答案一致。

| 你提供的文章 | 本次读取情况与使用方式 |
| --- | --- |
| [CSDN：Java 注解和反射详解](https://blog.csdn.net/zhuzicc/article/details/116071979) | 已读取正文部分，参考其注解、元注解到反射的主题安排；不沿用旧式实例化 API |
| [知乎文章](https://zhuanlan.zhihu.com/p/410489461) | 本次无法取得正文，保留延伸阅读链接，未据此转述内容 |
| [博客园：java 注解与反射](https://www.cnblogs.com/xxctx/p/18330813) | 已读取正文，参考把注解定义与处理过程连接起来的讲解方向；本课另写独立校验示例 |
| [掘金文章](https://juejin.cn/post/7034760945498849293) | 本次仅取得站点内容，未取得文章正文，未据此转述内容 |

无需先读完这些资料再做练习。先能运行、解释本课示例，遇到具体 API 问题再查对应官方文档。
