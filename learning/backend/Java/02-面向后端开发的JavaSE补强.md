# 02 面向后端开发的 JavaSE 补强

[返回学习目录](READEME.md)

> 笔记状态：全章已整理，可独立阅读。学习状态：List 与泛型入门已学，其余待学习。  
> 环境：JDK 17，无需 Spring Boot、Maven 或数据库。  
> 目标：掌握业务开发常用的集合、对象比较、异常、数据处理及现代 Java 语法。

本章是一份完整课程，可以分几次阅读。每个主题先解释用途，再给出用法和必要边界；不要求一次读完或记住所有方法。

阅读顺序：1—5 集合与泛型 → 6 对象比较 → 7 异常 → 8—9 Lambda、Stream 和 Optional → 10—11 时间与金额 → 12 资源关闭 → 13 注解与反射 → 14 现代语法 → 15—17 综合练习、自检答案和项目阅读。

代码约定：第 3、15 节是完整程序；其他标明“main 内片段”的示例放进 main 方法，并添加注明的 import。类定义或方法定义放在类体中，不能直接放进 main。所有示例均以 Java 17 为基线。

## 1. 为什么后端需要 List？

假设接口要返回多个产品名称，需要一个容器把它们放在一起。JavaScript 可以用数组，Python 可以用 list；Java 中常用 **List（列表）**保存这种有先后顺序、允许重复的数据。

```java
List<String> names = new ArrayList<>();
```

这行代码创建了一个可以增删元素的字符串列表。先把它拆开：

| 部分 | 含义 |
| --- | --- |
| `List` | 列表接口，规定列表应提供哪些操作，例如添加、按位置读取 |
| `<String>` | 指定列表中的元素类型为字符串，这种写法使用了泛型 |
| `names` | 变量名 |
| `new ArrayList<>()` | 创建具体的列表对象；ArrayList 实现了 List 接口 |

左边用接口描述“需要列表能力”，右边选择具体实现。`List` 是接口，不能写 `new List<>()`；本课先使用常见的 `ArrayList`。

右边的 `<>` 可以省略重复的 `String`，编译器会根据左边推断类型。

## 2. 泛型在这里解决什么问题？

**泛型允许我们在使用类或接口时指定它处理的数据类型。**在这个例子中，`List<String>` 就是在告诉编译器：“按字符串列表检查我的代码。”

```java
names.add("智能助手");  // 可以：添加字符串
// names.add(123);     // 取消注释会编译失败：整数不符合 String 类型
```

这样，放错类型的问题会在编译时被发现，而不是等到处理请求时才发现。

`add(...)` 表示添加一个元素；`get(0)` 表示读取第一个元素，索引从 0 开始；`size()` 返回元素个数。因为指定了 String 类型，`names.get(0)` 的结果可以直接赋给 String 变量。

## 3. 一个可以直接运行的例子

将下面的代码保存为 **`ProductListDemo.java`**。文件名必须与 public 类名一致，不要在文件名前加数字编号；编号可以写在上层文件夹名中。

```java
import java.util.ArrayList;
import java.util.List;

public class ProductListDemo {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        names.add("智能助手");
        names.add("知识库");

        String first = names.get(0);
        System.out.println(first);
        System.out.println(names.size());

        for (String name : names) {
            System.out.println(name);
        }
    }
}
```

`import` 告诉编译器这里使用的是 `java.util` 包中的 List 和 ArrayList；它们属于 JDK 标准库，不需要用 Maven 下载。

`for (String name : names)` 是增强 for 循环：依次取出 names 中的每个字符串，用变量 name 表示，并执行大括号中的代码。可以对照 Python 的 `for name in names` 理解。

在保存该文件的目录中执行（源码保存为 UTF-8）：

```powershell
javac -encoding UTF-8 ProductListDemo.java
java -cp . ProductListDemo
```

预期输出：

```text
智能助手
2
智能助手
知识库
```

第一次打印来自 `get(0)`，最后两次来自循环，因此“智能助手”出现两次。

## 4. Set、Map：换一种方式组织数据

### 4.1 Set：只保留不重复的元素

假设用户选择了多个标签，重复选择的“Java”只需要保存一次。**Set 是不包含重复元素的集合**，HashSet 是常见实现。

添加 `import java.util.Set;` 和 `import java.util.HashSet;`，在 main 内执行：

```java
Set<String> tags = new HashSet<>();
tags.add("Java");
tags.add("Java");
tags.add("AI");
System.out.println(tags.size());          // 2
System.out.println(tags.contains("AI"));  // true
```

`contains` 检查是否包含元素。HashSet 不保证遍历顺序；如果业务需要按插入顺序遍历，可以使用 LinkedHashSet。不要用 Set 的遍历顺序表达业务排序。

### 4.2 Map：通过键找到值

如果经常“根据产品编号查产品名称”，可以用 **Map 保存键和值的对应关系**，对照 Python 的 dict、JavaScript 的 Map 理解。

添加 `import java.util.Map;` 和 `import java.util.HashMap;`，在 main 内执行：

```java
Map<Long, String> namesById = new HashMap<>();
namesById.put(101L, "智能助手");
namesById.put(102L, "知识库");
System.out.println(namesById.get(101L));  // 智能助手
namesById.put(101L, "新版助手");         // 相同键会替换原值
System.out.println(namesById.size());    // 2
```

`Map<Long, String>` 的两个类型分别表示键类型和值类型。`101L` 中的 L 表示 long 类型的整数；Long 是对应的包装类型，下一节解释。

`put` 写入，`get` 按键读取。HashMap 查找不存在的键会返回 null；null 表示没有对象引用，不能直接对它调用方法。需要区分“键不存在”和“键对应的值为 null”时，用 `containsKey`。

### 4.3 如何选择？

| 需求 | 首先考虑 | 常用实现 |
| --- | --- | --- |
| 保存产品列表，按位置读取 | List | ArrayList |
| 标签去重、判断是否包含 | Set | HashSet |
| 按编号查找名称 | Map | HashMap |

ArrayList 按索引读取通常是 O(1)，从中间删除元素可能需要移动后面的元素，是 O(n)。HashSet、HashMap 的基本查找在哈希分布合理时通常平均 O(1)。这里 n 是元素个数，目的是理解数据增多后的成本，不要求现在研究内部实现。它们也不是数据库或跨请求缓存的替代品。

## 5. 泛型再补两点：包装类型和类型参数

### 5.1 为什么是 `List<Integer>`，而不是 `List<int>`？

Java 有基本类型，例如 int、long、boolean；也有对应的包装类型 Integer、Long、Boolean。**泛型类型参数不能直接使用基本类型**，所以整数列表写 `List<Integer>`。

添加 List、ArrayList 的 import，在 main 内执行：

```java
List<Integer> counts = new ArrayList<>();
counts.add(3);                 // int 自动转换为 Integer，叫自动装箱
int count = counts.get(0);     // Integer 转回 int，叫自动拆箱
```

包装类型可以为 null，而基本类型不可以。`Integer count = null;` 后再赋给 int，会在拆箱时抛出 NullPointerException。后端读取允许为空的数据时要特别留意。

### 5.2 看到 T 时怎样理解？

项目中可能遇到 `Response<T>` 这样的写法。**T 是类型参数名，是留给使用者指定的类型位置**，不是某个固定业务类，也不要求必须命名为 T。

下面是类定义示例，放在主类内部、main 外部：

```java
static class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T getValue() {
        return value;
    }
}
```

然后在 main 中使用：

```java
Box<String> box = new Box<>("知识库");
String name = box.getValue();
```

这里把 T 指定为 String，构造参数与返回值也按 String 检查。后续看到 `Response<Product>`，就可以理解为“装有 Product 数据的响应”。本章不展开通配符与类型擦除。

## 6. 对象相等：equals 与 hashCode

### 6.1 == 和 equals 比较的是什么？

对于基本类型，`==` 比较值；对于对象引用，`==` 判断是否指向同一个对象。**`equals` 用于表达逻辑上的相等，但具体规则由类决定**；Object 的默认实现仍然比较对象身份。

main 内片段，不需要 import：

```java
String a = new String("Java");
String b = new String("Java");
System.out.println(a == b);       // false：两个对象
System.out.println(a.equals(b));  // true：String 按字符内容比较
```

这里特意使用 new，避免字符串常量复用干扰观察。平时比较字符串内容用 equals，不要依靠 `==`。

### 6.2 为什么 HashSet 去重还涉及 hashCode？

HashSet、HashMap 使用哈希值帮助查找，再用 equals 判断是否相等。因此规则是：**equals 相等的对象必须有相同的 hashCode；hashCode 相同不代表对象相等。**

如果业务规定“产品编号相同就视为同一个产品”，则需要为相应类型配套定义 equals 和 hashCode。添加 `import java.util.Objects;`，下面的类放在主类内部：

```java
static final class ProductKey {
    private final long id;

    ProductKey(long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProductKey)) return false;
        ProductKey that = (ProductKey) other;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
```

`instanceof` 检查对象类型；强制转换后才能访问该类型的字段。`@Override` 表示重写父类方法，编译器会检查是否真的重写成功。

向 HashSet 添加两个 `new ProductKey(101L)`，最终只有一个元素。实际开发可由 IDE 生成这两个方法，但必须先决定比较哪些字段。不要在对象放进 HashSet 或作为 HashMap 的键之后，修改参与比较和计算哈希值的字段。

## 7. 异常：失败怎么传给调用者？

当产品数量为负数时，方法无法正常完成。**异常用来中断当前正常流程，并把失败信息传给调用者。**

将这个方法放在主类中、main 外：

```java
static int parseQuantity(String text) {
    int quantity = Integer.parseInt(text);
    if (quantity < 0) {
        throw new IllegalArgumentException("数量不能为负数");
    }
    return quantity;
}
```

`Integer.parseInt` 把字符串转换成整数；如果传入 abc，会抛出 NumberFormatException。`throw` 表示实际抛出一个异常。

调用方可以在 main 中处理：

```java
try {
    int quantity = parseQuantity("-1");
    System.out.println(quantity);
} catch (IllegalArgumentException e) {
    System.out.println(e.getMessage());  // 数量不能为负数
}
```

发生异常后，try 中后续语句不再执行，进入匹配的 catch。若当前方法没有处理，就继续向调用者传播。

| 类型 | 编译器的要求 | 例子 |
| --- | --- | --- |
| 受检异常 | 捕获，或在方法签名用 throws 声明继续交给调用方 | IOException |
| 非受检异常 | 不强制捕获或声明，通常为 RuntimeException 及其子类 | IllegalArgumentException、NullPointerException |

`throws IOException` 是方法声明“可能发生这个异常”，与实际执行的 `throw` 不同。业务代码也会定义自己的异常类型，后续全局异常处理课程再统一转成接口响应。

现在先养成两个习惯：只在能处理或能补充上下文的位置捕获异常；不要 catch 后什么也不做，也不要把失败伪装成成功返回值。

## 8. Lambda 与 Stream：把一组数据变成另一组结果

### 8.1 先理解 Lambda

如果要“保留以 Java 开头的名称”，需要给出判断规则：

```java
name -> name.startsWith("Java")
```

这就是 Lambda 表达式：箭头左边是参数，右边是处理规则。相当于“输入一个 name，返回它是否以 Java 开头”。Java 中 Lambda 用于匹配只有一个抽象方法的接口，这类接口叫函数式接口。先能读懂规则，不必背接口名称。

### 8.2 Stream 把规则串起来

**Stream 是处理一组数据的流水线，不是保存数据的集合，也不是网络流式响应。**添加 `import java.util.List;`，在 main 内执行：

```java
List<String> names = List.of("Java入门", "Python入门", "Java实战");
List<String> result = names.stream()
        .filter(name -> name.startsWith("Java"))
        .map(name -> name + "课程")
        .toList();
System.out.println(result);  // [Java入门课程, Java实战课程]
```

逐步看：`List.of` 创建列表；`stream()` 开始处理；`filter` 筛选；`map` 转换每个元素；`toList()` 触发处理并收集结果。这里的 map 是转换操作，与前面的 Map 键值容器不是一回事。

可以对照 JavaScript 的 filter/map 理解，但 Java Stream 的这些中间操作会延迟到终止操作才执行。原来的 names 没被改变；同一个 Stream 用完不能再次使用，想再处理时重新调用 `names.stream()`。

`List.of(...)` 和这里 `Stream.toList()` 得到的列表不支持增删元素。需要可修改列表时，可以用 `new ArrayList<>(result)`。可修改列表也不等于内部所有元素都可以或不可以修改，需要分别判断。

### 8.3 方法引用是什么？

`name -> name.length()` 可以简写成 `String::length`，这种复用已有方法的写法叫方法引用。下面两种表达在这个场景中等价：

```java
names.forEach(name -> System.out.println(name));
names.forEach(System.out::println);
```

业务规则复杂、有多个分支时，普通 for 循环可能更清楚。不需要把所有循环都改成 Stream，也不默认使用 parallelStream。[Java 17 Stream 文档](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html)

## 9. Optional：明确表达“可能没有结果”

按编号查产品可能找不到。返回 null 容易被调用者忽略；**Optional 是用来表示“有一个值，或者没有值”的容器**，常用作可能查无结果的方法返回值。

添加 `import java.util.Optional;`，在 main 内执行：

```java
String input = null;
Optional<String> name = Optional.ofNullable(input);
System.out.println(name.orElse("未命名"));  // 未命名
```

`ofNullable` 接受可能为 null 的值；`orElse` 在没有值时使用默认值。有值时返回其中的值。

也可以接住 Stream 的查找结果（需要 List 的 import）：

```java
String found = List.of("Java", "Python").stream()
        .filter(name -> name.equals("Go"))
        .findFirst()
        .orElse("未找到");
```

`findFirst` 返回 Optional，因为可能没有元素满足条件。不要不检查就调用 `get()`，空 Optional 会抛出异常。已有 if 判空很清楚时不必强行改写；也不需要把所有字段、参数都包装成 Optional。[Java 17 Optional 文档](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html)

## 10. 日期时间：先确定你要表达什么

后端有“生日”“预约时间”“日志发生时刻”等不同含义，不能都当成一个日期字符串。

| 类型 | 表达什么 | 示例用途 |
| --- | --- | --- |
| LocalDate | 只有日期，没有时区 | 生日、账期日期 |
| LocalDateTime | 日期和时间，但不带时区 | 尚未关联地区的日历时间 |
| Instant | 时间线上的一个确定时刻 | 事件时间、跨地区时间比较 |
| ZonedDateTime | 带时区的日期时间 | 展示某个地区的当地时间 |

添加 `import java.time.*;`，main 内片段：

```java
LocalDate date = LocalDate.parse("2026-09-15");
System.out.println(date.plusDays(1)); // 2026-09-16

Instant moment = Instant.parse("2026-09-15T00:00:00Z");
ZonedDateTime shanghai = moment.atZone(ZoneId.of("Asia/Shanghai"));
System.out.println(shanghai.getHour()); // 8
```

Z 表示 UTC 偏移为零。同一时刻在上海显示为早上 8 点。`LocalDateTime` 的 Local 不意味着它已经知道服务器或用户所在时区；不补充时区信息，无法唯一确定全球时间线上的时刻。

这些类型的修改方法通常返回新对象，例如 `date.plusDays(1)` 不会改变原来的 date。接口和数据库如何保存时间，需要约定格式与时区，不能仅凭字符串看起来一致就认为时间一致。

## 11. 金额：使用 BigDecimal 表达十进制运算

double 使用二进制浮点表示，某些十进制小数无法精确表示。处理金额时，通常用 **BigDecimal 表达十进制数，并明确舍入规则**。

添加 `import java.math.BigDecimal;` 和 `import java.math.RoundingMode;`，main 内片段：

```java
BigDecimal price = new BigDecimal("19.90");
BigDecimal total = price.multiply(BigDecimal.valueOf(3));
System.out.println(total); // 59.70

BigDecimal average = total.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
System.out.println(average); // 14.93
```

multiply 是乘法；divide 的后两个参数表示保留 2 位小数，并使用 HALF_UP 舍入。本例的舍入规则只是演示，真实计费要按业务要求确定。

优先从字符串构造准确的金额；不要用 `new BigDecimal(0.1)` 期待精确得到十进制 0.1。BigDecimal 不可变，运算返回新对象。

比较数值大小或相等时使用 compareTo：结果为负数、0、正数，分别表示小于、数值相等、大于。`new BigDecimal("2.0").equals(new BigDecimal("2.00"))` 是 false，因为 equals 还比较小数位数；两者 compareTo 的结果是 0。除法无法精确表示且没指定舍入规则时可能抛出 ArithmeticException。[Java 17 BigDecimal 文档](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)

## 12. I/O 与资源关闭：文件读完以后怎么办？

I/O 指输入与输出，例如读取文件、写出响应。字节流处理原始字节，适合图片等二进制内容；字符读写器按编码处理文字。

打开文件可能占用操作系统资源。Java 垃圾回收主要管理对象内存，不能代替及时关闭文件、连接等资源。**try-with-resources 用来在代码块退出时自动关闭资源，包括发生异常时。**可以对照 Python 的 with 理解。

先在运行目录创建 UTF-8 文件 `sample.txt`，首行写“Java学习”。添加这些 import：

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
```

main 内片段：

```java
try (BufferedReader reader = Files.newBufferedReader(
        Path.of("sample.txt"), StandardCharsets.UTF_8)) {
    System.out.println(reader.readLine()); // Java学习
} catch (IOException e) {
    throw new IllegalStateException("读取 sample.txt 失败", e);
}
```

BufferedReader 提供按行读取等能力；`readLine()` 没有更多内容时返回 null。try 括号中的资源必须支持 AutoCloseable 协议，代码块退出时调用 close。catch 中把原异常 e 作为原因保留，后续还能定位实际失败原因。

相对路径从程序的当前工作目录解析，不一定是源码文件所在目录。运行前确认 sample.txt 在工作目录中；文件不存在会走 catch，这是正常的失败分支。

## 13. 注解与反射：为什么框架能认识你的类？

需要展开学习时，阅读 [课外 01：Java 注解与反射](课外01-Java注解与反射.md)，包含独立可运行示例、芋道源码案例与自查题。

### 13.1 注解为代码提供元信息

`@Override`、`@Test`、后续的 `@RestController` 都是注解。**注解是附加在类、方法等位置的描述信息，由编译器、工具或框架读取。**它本身不是自动执行的业务代码。

例如，给一个普通方法随意起名叫 test 并不会让测试工具自动执行；测试工具会按自己的规则识别测试。`@Override` 则主要让编译器检查方法是否正确重写，不负责运行时的接口路由。

### 13.2 反射在运行时查看类型信息

正常代码在编写时明确调用哪个方法；**反射允许程序在运行时查看类的信息，甚至创建对象、调用方法**。一个最小例子，放进 main：

```java
Class<?> type = String.class;
System.out.println(type.getName()); // java.lang.String
System.out.println(type.getDeclaredMethods().length > 0); // true
```

`String.class` 取得描述 String 类型的对象；`Class<?>` 表示“描述某个类型的 Class 对象”，问号在这里不限定具体类型；getDeclaredMethods 获取该类声明的方法信息。

框架可以借助类型信息和运行时保留的注解完成对象创建、映射等工作，但并非所有注解都能在运行时读到。本章只要求理解用途，不要求自己写反射框架；Spring 的具体处理在第三章展开。

## 14. Java 17 中值得认识的现代语法

### 14.1 record：简洁的数据载体

只想表达“产品编号和名称”时，普通类往往需要写构造方法、访问方法及 equals/hashCode。record 可以简化这类数据载体。

下面的类型定义放在主类内部、main 外：

```java
record ProductSummary(long id, String name) {}
```

main 中使用：

```java
ProductSummary product = new ProductSummary(101L, "智能助手");
System.out.println(product.name()); // 智能助手，访问方法不是 getName()
```

record 自动提供对应构造方法、访问方法和基于组件的 equals/hashCode/toString。组件字段是 final，但如果组件引用可变列表，列表内容仍可能改变，因此不等于深度不可变。record 适合一些数据传递场景，不要求把数据库实体全部改成 record。[Java 17 record 指南](https://docs.oracle.com/en/java/javase/17/language/records.html)

### 14.2 switch 表达式：按分支算出一个值

main 内片段：

```java
String status = "PUBLISHED";
String label = switch (status) {
    case "DRAFT" -> "草稿";
    case "PUBLISHED" -> "已发布";
    default -> "未知状态";
};
System.out.println(label); // 已发布
```

右边整个 switch 产生一个值，赋给 label；这种箭头分支不需要再写 break。这里假设 status 非 null。

### 14.3 版本边界

本章用到的 Lambda/Stream 是 Java 8 起已有的能力；List.of 从 Java 9 提供；var 从 Java 10 可用于有初始化值的局部变量；switch 表达式从 Java 14 正式提供；record 与 Stream.toList 从 Java 16 正式提供，Java 17 可以直接使用这些功能。

`var names = new ArrayList<String>();` 由编译器推断局部变量类型，并不让 Java 变成动态类型。Java 21/25 的新增特性会在对应专题补充，本章不需要切换 JDK。

## 15. 综合练习：处理一组产品

下面把集合、record、Stream、Optional 和金额运算放进同一个业务场景：筛选上架产品、统计标签，并计算上架产品总价。

保存为 **`ProductPractice.java`**，它是完整程序，不需要引用前面例子的类：

```java
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ProductPractice {
    record Product(long id, String name, String tag,
                   BigDecimal price, boolean published) {}

    public static void main(String[] args) {
        List<Product> products = List.of(
                new Product(101L, "智能助手", "AI", new BigDecimal("19.90"), true),
                new Product(102L, "知识库", "AI", new BigDecimal("9.90"), true),
                new Product(103L, "学习工具", "教育", new BigDecimal("5.00"), false));

        List<String> publishedNames = products.stream()
                .filter(Product::published)
                .map(Product::name)
                .toList();

        Set<String> tags = new LinkedHashSet<>();
        Map<Long, Product> byId = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Product product : products) {
            tags.add(product.tag());
            byId.put(product.id(), product);
            if (product.published()) {
                total = total.add(product.price());
            }
        }

        String missingName = Optional.ofNullable(byId.get(999L))
                .map(Product::name)
                .orElse("未找到");

        System.out.println(publishedNames);
        System.out.println(tags);
        System.out.println(total);
        System.out.println(missingName);
    }
}
```

`Product::published` 读取每个产品的布尔值作为筛选条件；`Product::name` 读取名称。Optional 的 map 表示“有产品时取名称，没有时继续保持为空”。`BigDecimal.ZERO` 是数值零，add 返回新值，因此需要重新赋给 total。

在文件所在目录执行：

```powershell
javac -encoding UTF-8 ProductPractice.java
java -cp . ProductPractice
```

预期输出：

```text
[智能助手, 知识库]
[AI, 教育]
29.80
未找到
```

这里用 LinkedHashSet 保持标签的插入顺序，让示例输出稳定。产品编号假设唯一；如果数据中有重复编号，byId 会被后放入的值覆盖。

独立练习：把第三个产品改为已发布，预测输出；再把查询编号从 999L 改为 102L，检查默认值是否还会使用。不需要新建 Web 接口或连接数据库。

## 16. 自检问题与参考答案

可以先回答，再向下查看答案。没有实时对话时，也能独立检查理解。

1. 保存有序产品列表、标签去重、按编号查产品，各用哪种容器？
2. 为什么 `List<int>` 不成立？Integer 为 null 时能直接赋给 int 吗？
3. 两个内容一样的 String，为什么 == 可能为 false？
4. HashSet 中的自定义对象只重写 equals，不重写 hashCode，有什么问题？
5. throw 和 throws 有什么区别？
6. filter 与 map 有什么区别？Stream.toList 得到的列表能直接 add 吗？
7. Optional 如何处理没有结果？
8. LocalDateTime 是否已经包含时区？
9. 金额为什么从字符串构造 BigDecimal？比较 2.0 与 2.00 的数值相等用什么？
10. 为什么读取文件要关闭资源？try-with-resources 解决什么问题？
11. 注解和反射分别做什么？
12. record 中引用了 ArrayList，是否意味着该列表内容不可修改？

### 参考答案

1. List、Set、Map，分别对应按顺序保存、不重复元素、键值查找。
2. 泛型参数不能直接使用基本类型；应写 `List<Integer>`。null 拆箱会抛出 NullPointerException。
3. 对象引用的 == 比较身份，String.equals 比较字符内容。两个对象可以内容相同。
4. 可能违反“相等对象的哈希值必须相同”的约定，导致去重和查找不符合预期，需要配套实现。
5. throw 实际抛出异常；throws 在方法签名中声明可能向外传播的异常。
6. filter 决定保留哪些元素，map 把元素转换成结果；Stream.toList 的结果不支持 add。
7. 例如用 orElse 指定默认值，或者按业务要求抛出异常，不要直接对空 Optional 调用 get。
8. 不包含。关联明确时区后，才能把本地日期时间解释为时间线上的时刻。
9. 避免先引入二进制浮点的表示误差；使用 compareTo，返回 0 表示数值相等。
10. 文件等占用操作系统资源，不能只等待垃圾回收；try-with-resources 确保退出代码块时关闭资源。
11. 注解提供元信息，反射在运行时检查类型并可操作对象；框架可以组合使用它们。
12. 不意味着。组件字段的引用不能重新赋值，不代表被引用的列表内容不可变。

综合练习答案：第三个产品发布后，名称列表增加“学习工具”，总价变为 34.80，标签不变；查询 102L 得到“知识库”，不再使用“未找到”。

## 17. 回到芋道 ruoyi-vue-pro 与学习进度

本章学的是通用 Java，不要求先理解平台业务。参考项目为 `D:\mine_projects\ruoyi-vue-pro` 的 `master-jdk17`。打开 `yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/util/collection/CollectionUtils.java`，寻找 `convertList(Collection<T> from, Function<T, U> func)`：

- 先确认输入是什么集合。
- 再逐个读 filter、map 等操作中的规则。
- 最后确认输出类型和用途。

本地核对时，该方法的核心语句为：

```java
return from.stream().map(func).filter(Objects::nonNull).collect(Collectors.toList());
```

func 是调用方传入的转换规则；先 map 转换，再 filter 去掉 null，最后收集成 List。`collect(Collectors.toList())` 是另一种收集写法，不要把它的返回集合性质直接等同于 `Stream.toList()` 的不可修改列表。

这是现有方法内的一行，不是可独立运行程序；泛型 T 表示输入元素类型，U 表示转换后的类型。接着可读同模块的 `common/pojo/PageResult.java` 和 `CommonResult.java`，观察泛型怎样用于分页与接口返回。代码位置核对于 2026-09-15。

- [x] List 与泛型入门：学习者已反馈学会。
- [ ] Set、Map、集合选择与泛型补充。
- [ ] equals/hashCode 与异常处理。
- [ ] Lambda、Stream、Optional。
- [ ] 时间、金额与资源关闭。
- [ ] 注解、反射与现代语法。
- [ ] 完成综合练习，能解释修改后的结果。

笔记已齐不代表已经学完。其余项目按你的阅读和练习结果更新；下一章为 Spring 核心思想。
