YAML 全称 YAML Ain't Markup Language（一种递归缩写），是一种人类可读的数据序列化格式。

YAML 常用于配置文件、数据交换以及各种需要以结构化方式存储数据的场景。

YAML 的配置文件后缀为 .yml，如：runoob.yml 。


### YAML 的特点与优势
YAML 的设计目标可读性强，语法简洁，接近自然语言的书写习惯，几乎不需要额外的符号。

相比 XML 和 JSON，YAML 减少了大量的括号和引号，书写更轻松。

它支持复杂数据结构，可以表达列表、字典、嵌套结构，甚至支持引用和锚点等高级特性。


### YAML 与 JSON、XML 的对比

三种格式各有侧重，下表从多个维度进行了对比：

|特性|YAML|JSON|XML|
|---|---|---|---|
|可读性|高|中|低|
|注释支持|支持|不支持|支持|
|语法复杂度|低|中|高|
|常见用途|配置文件|数据交换/API|文档/数据交换|

![](../../images/yaml-1.webp)


### YAML 的常见应用场景

YAML 在现代化开发栈中几乎无处不在，以下是最常见的几种场景：

应用程序的配置文件，如 Spring Boot 的 application.yml。

容器编排工具，如 Docker Compose、Kubernetes 的部署描述文件。

CI/CD 流水线配置，如 GitHub Actions、GitLab CI、CircleCI 的工作流定义。

静态站点生成器的 Front Matter，如 Jekyll、Hugo 的页面元数据。



## 基础语法规则

掌握 YAML 最基本的书写规则，包括缩进、注释、文件结构等。

### 缩进规则

YAML 使用空格进行缩进来表示层级关系，不能使用 Tab 键。

同一层级的元素必须保持相同的缩进量，通常建议使用 2 个空格作为缩进单位。

```yaml
person:
  name: 张三
  age: 25
```

### 基本语法

以下是 YAML 最核心的语法规则，需要牢记：

- 大小写敏感
- 使用缩进表示层级关系
- 缩进不允许使用 Tab，只允许空格
- 缩进的空格数不重要，只要相同层级的元素左对齐即可
- # 表示注释
- : 号后面要加空格

### 大小写敏感

YAML 中的键名、字符串等都是大小写敏感的。

Name 和 name 会被视为两个不同的键，编写时需要注意一致性问题。

### 注释写法

使用 # 开头的内容为注释，注释可以独占一行，也可以写在内容后面。

```yaml
# 这是一行注释
name: 张三  # 这也是注释
```

### 文件扩展名

YAML 文件通常使用 .yaml 或 .yml 作为扩展名，两者没有本质区别，视项目习惯而定。

### 文档分隔符

`---` 用于标识一个新文档的开始，在多文档文件中尤为重要。

`...` 用于标识文档结束，属于可选标记。

```yaml
---
doc: 第一个文档
---
doc: 第二个文档
```


## 基本数据类型

YAML 支持字符串、数字、布尔值、空值等基本数据类型，每种类型都有特定的书写方式。

### 数据类型概述

YAML 支持以下几种数据类型：

- 对象：键值对的集合，又称为映射（mapping）/ 哈希（hashes）/ 字典（dictionary）
- 数组：一组按次序排列的值，又称为序列（sequence）/ 列表（list）
- 纯量（scalars）：单个的、不可再分的值

### 标量（Scalar）

标量是 YAML 中最基本的数据单元，分为以下几种类型：

#### 字符串

字符串可以不使用引号，也可以使用双引号或单引号包裹。

双引号支持转义字符（如 \n），单引号则不支持转义。

```yaml
name: 张三          # 不加引号
name: "张三"        # 双引号，支持转义字符
name: '张三'        # 单引号，不支持转义
```

#### 数字

YAML 会自动识别整数和浮点数，无需额外声明类型。

```yaml
age: 25          # 整数
price: 19.99     # 浮点数
```

#### 布尔值

布尔值使用 true 和 false 表示。

```yaml
is_active: true
is_deleted: false
```

#### 空值

空值可以用 null、~ 或者什么都不写来表示。

```yaml
value: null
value: ~
value:            # 什么都不写也表示 null
```

#### 纯量综合示例

以下示例展示了所有纯量类型的使用方式，包括布尔值、浮点数、整数、空值、字符串、日期和时间：

```yaml
boolean:
    - TRUE  # true,True 都可以
    - FALSE  # false,False 都可以
float:
    - 3.14
    - 6.8523015e+5  # 可以使用科学计数法
int:
    - 123
    - 0b1010_0111_0100_1010_1110    # 二进制表示
null:
    nodeName: 'node'
    parent: ~  # 使用 ~ 表示 null
string:
    - 哈哈
    - 'Hello world'  # 可以使用双引号或者单引号包裹特殊字符
    - newline
      newline2    # 字符串可以拆成多行，每一行会被转化成一个空格
date:
    - 2018-02-17    # 日期必须使用 ISO 8601 格式，即 yyyy-MM-dd
datetime:
    - 2018-02-17T15:02:31+08:00    # 时间使用 ISO 8601 格式，时间和日期之间使用 T 连接，最后使用 + 代表时区
```

> 日期和时间必须使用 ISO 8601 格式。日期格式为 yyyy-MM-dd，时间格式为 yyyy-MM-ddTHH:mm:ss+时区，中间用 T 连接。

### 多行字符串

YAML 提供了两种多行字符串的书写方式，分别适用于不同的场景。

#### 折叠样式（>）

使用 > 符号将多行文本折叠为一行，换行符会被替换为空格。

```yaml
description: >
  这是第一行
  这是第二行
  这是第三行
```

上面的写法等价于："这是第一行 这是第二行 这是第三行\n"

#### 保留换行样式（|）

使用 | 符号保留原始换行符，适合书写多行文本（如脚本内容）。

```yaml
script: |
  echo "第一行"
  echo "第二行"
  echo "第三行"
```

#### | 与 > 对比

下面通过同一段内容 Foo\nBar 对比两种写法的差异：

```yaml
this: |
  Foo
  Bar
that: >
  Foo
  Bar
```

转为 JavaScript 代码如下：

```json
{ this: 'Foo\nBar\n', that: 'Foo Bar\n' }
```

可以看到，| 保留了换行符，结果为 Foo\nBar\n；而 > 将换行折叠为空格，结果为 Foo Bar\n。

> 选择 > 还是 | 取决于你的需求：如果需要将多行内容合并为一段，使用 >；如果需要保留原始换行格式（如脚本、诗歌），使用 |。

---

## 复合数据结构

YAML 支持映射和序列两种复合结构，二者可以自由嵌套，表达任意复杂的数据关系。

### 映射（Mapping / 字典）

映射是键值对的集合，是 YAML 中最常用的数据结构。

#### 基本键值对写法

```yaml
name: 张三
age: 25
city: 北京
```

#### 嵌套映射

通过缩进可以在映射中嵌套另一个映射，形成层级结构。

```yaml
person:
  name: 张三
  address:
    city: 北京
    zipcode: "100000"
```

#### 流式映射写法

也可以使用 key:{key1: value1, key2: value2, ...} 的流式写法。

```yaml
key: {child-key: value, child-key2: value2}
```

#### 复杂键（Complex Key）

使用问号加空格代表一个复杂的 key，配合冒号加空格代表一个 value：

```yaml
?
    - complexkey1
    - complexkey2
:
    - complexvalue1
    - complexvalue2
```

意思即对象的属性是一个数组` [complexkey1, complexkey2]`，对应的值也是一个数组 `[complexvalue1, complexvalue2]`。

### 序列（Sequence / 列表）

序列使用 - 前缀表示，用于存储有序的数据集合。

#### 基本列表写法

```yaml
fruits:
  - 苹果
  - 香蕉
  - 橙子
```

#### 嵌套列表

```yaml
matrix:
  - [1, 2, 3]
  - [4, 5, 6]
  - [7, 8, 9]
```

#### 多维数组

YAML 支持多维数组，可以使用行内表示：

```yaml
key: [value1, value2, ...]
```

数据结构的子成员是一个数组，则可以在该项下面缩进一个空格：

```yaml
-
 - A
 - B
 - C
```

#### 数组元素为对象的复杂示例

一个常见的实际场景：数组中的每个元素是由多个属性构成的对象：

```yaml
companies:
    -
        id: 1
        name: company1
        price: 200W
    -
        id: 2
        name: company2
        price: 500W
```

意思是 companies 属性是一个数组，每一个数组元素又是由 id、name、price 三个属性构成。

数组也可以使用流式方式表示：

```bash
companies: [{id: 1,name: company1,price: 200W},{id: 2,name: company2,price: 500W}]
```

### 映射与序列混合使用

映射和序列可以灵活组合，表达复杂的数据结构。

```yaml
students:
  - name: 张三
    age: 20
    subjects:
      - 数学
      - 英语
  - name: 李四
    age: 22
    subjects:
      - 物理
      - 化学
```

> 在上面的例子中，students 是一个序列，每个元素是一个映射，映射中的 subjects 又是一个序列。这种嵌套组合是 YAML 表达力的核心。

### 复合结构示例

数组和对象可以构成复合结构，以下是一个同时包含列表和映射的示例：

```yaml
languages:
  - Ruby
  - Perl
  - Python
websites:
  YAML: yaml.org
  Ruby: ruby-lang.org
  Python: python.org
  Perl: use.perl.org

```
转换为 JSON 后为：

```json
{
  languages: [ 'Ruby', 'Perl', 'Python'],
  websites: {
    YAML: 'yaml.org',
    Ruby: 'ruby-lang.org',
    Python: 'python.org',
    Perl: 'use.perl.org'
  }
}
```


## 流式（Flow）语法

除了块式写法，YAML 还支持类似 JSON 的流式写法，适合紧凑场景。

### 流式映射

使用花括号 {} 包裹键值对，用逗号分隔。

person: {name: 张三, age: 25, city: 北京}

### 流式列表

使用方括号 [] 包裹元素，用逗号分隔。

fruits: [苹果, 香蕉, 橙子]

### 流式与块式的对比与使用场景

|风格|特点|适用场景|
|---|---|---|
|块式（Block）|可读性更好，层级清晰|结构复杂、层级较深的数据|
|流式（Flow）|更紧凑，单行表达|简单数据，或嵌入到块式结构中的小型数据|

两种风格也可以混合使用：

```yaml
users:
  - {name: 张三, age: 25}
  - {name: 李四, age: 30}
```


## 高级特性

YAML 提供了锚点、引用、合并键等高级特性，帮助减少重复内容。

### 锚点与引用

使用 & 定义锚点，使用 * 引用锚点，可以避免重复书写相同的内容。

```yaml
default_settings: &defaults
  adapter: postgres
  host: localhost

development:
  <<: *defaults
  database: dev_db

test:
  <<: *defaults
  database: test_db
```

在上面的例子中，&defaults 定义了一个锚点，*defaults 引用了该锚点的内容，<< 表示合并到当前数据。

上面的配置展开后相当于：

```yaml
defaults:
  adapter:  postgres
  host:     localhost

development:
  database: myapp_development
  adapter:  postgres
  host:     localhost

test:
  database: myapp_test
  adapter:  postgres
  host:     localhost
```

#### 列表中的锚点引用

锚点也可以用在列表中：

```bash
- &showell Steve
- Clark
- Brian
- Oren
- *showell
```

转为 JavaScript 数组后为：

```bash
[ 'Steve', 'Clark', 'Brian', 'Oren', 'Steve' ]
```

### 合并键

<< 用于将一个映射的内容合并到当前映射中，常与锚点配合使用。

> 合并键 << 是 YAML 1.1 规范中的特性，在大多数 YAML 解析器中都得到支持。它能让配置文件更加简洁，但需要注意它并非 JSON 规范的一部分，转换到 JSON 时会展开为实际内容。

### 多文档支持

一个 YAML 文件中可以通过 --- 分隔存放多个独立文档，常见于 Kubernetes 的配置文件中。

```bash
---
kind: Pod
metadata:
  name: pod-1
---
kind: Pod
metadata:
  name: pod-2
```

### 标签与类型转换

YAML 支持通过标签（Tag）显式指定数据类型，使用 !! 前缀声明。

```bash
explicit_string: !!str 123
explicit_int: !!int "123"
explicit_float: !!float "3.14"
```

常用标签包括 !!str（字符串）、!!int（整数）、!!float（浮点数）、!!bool（布尔值）、!!null（空值）等。


## 常见错误与注意事项

YAML 对格式非常敏感，以下是最常见的错误类型和避免方法。

### 缩进错误导致的解析失败

YAML 对缩进极为敏感，同一层级的缩进量必须完全一致，否则会导致解析报错。

# 错误示例：缩进不一致
```bash
person:
  name: 张三
   age: 25   # 缩进多了一个空格，会报错
```

### Tab 与空格混用问题

YAML 规范明确不允许使用 Tab 进行缩进，必须统一使用空格，否则大多数解析器会报错。

> 建议在编辑器中开启「将 Tab 转换为空格」的设置，避免不小心混入 Tab 字符。大多数现代编辑器都支持对 .yaml 和 .yml 文件自动启用此设置。

### 特殊字符转义问题

当字符串中包含 :、#、{、}、[、] 等特殊字符时，建议使用引号将字符串包裹起来，避免被解析器误判。

title: "标题: 副标题"   # 冒号需要用引号包裹
tag: "#重要"           # 井号需要用引号包裹，否则会被当作注释

### 字符串是否需要加引号的场景

以下情况建议显式加上引号，避免 YAML 解析器做出错误的类型推断：

|场景|示例|说明|
|---|---|---|
|字符串以数字开头|"100000"|不加引号会被解析为数字|
|易混淆的布尔词|"true"、"false"、"yes"、"no"|不加引号会被解析为布尔值|
|空值词|"null"、"~"|不加引号会被解析为 null|
|包含特殊符号|"标题: 副标题"|冒号、井号等会被误判为语法标记|


## 实践应用

通过实际场景中的完整示例，理解 YAML 在真实项目中的用法。

### 编写一个简单的配置文件

以下是一个典型的应用程序配置文件，展示了 YAML 在实际项目中的常见结构：

```bash
# 应用基本配置
app:
  name: MyApplication
  version: 1.0.0
  debug: false

# 数据库连接配置
database:
  host: localhost
  port: 5432
  username: admin
  password: "secret"

# 日志配置
logging:
  level: info
  file: /var/log/app.log
```

### YAML 在 Docker Compose 中的应用

Docker Compose 使用 YAML 定义多容器应用的服务编排：

```bash
version: "3"
services:
  web:
    image: nginx:latest
    ports:
      - "80:80"
  db:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: example
```

### YAML 在 Kubernetes 中的应用

Kubernetes 使用 YAML 定义各类资源对象，以下是一个 Pod 的定义示例：

```bash
apiVersion: v1
kind: Pod
metadata:
  name: nginx-pod
spec:
  containers:
    - name: nginx
      image: nginx:latest
      ports:
        - containerPort: 80
```

### YAML 在 GitHub Actions 中的应用

GitHub Actions 使用 YAML 定义 CI/CD 工作流：

```bash
name: CI

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run tests
        run: npm test

```

## 工具与验证

推荐一些实用的 YAML 工具和资源，帮助你更高效地编写和验证 YAML 文件。

### 在线 YAML 校验工具

YAML Lint 是最常用的在线校验工具，可以搜索 "yamllint online" 查找可用的在线校验网站。

各类 IDE 也内置了 YAML 语法检查功能，可以在编写时实时提示错误。

### 常用编程语言解析 YAML 的库

|语言|常用库|特点|
|---|---|---|
|Python|PyYAML、ruamel.yaml|PyYAML 最流行，ruamel.yaml 支持 YAML 1.2|
|JavaScript / Node.js|js-yaml|浏览器和 Node.js 环境通用|
|Java|SnakeYAML|Spring Boot 默认使用的 YAML 解析库|
|Go|gopkg.in/yaml.v3|Go 生态中最常用的 YAML 库|
|Ruby|Psych|标准库内置，无需额外安装|
