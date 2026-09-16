![[file-structrue.png]]

### 1、最外层：区分源码、配置和生成结果
```
smart-square-admin/
├── src/
│   ├── main/
│   │   ├── java/           Java 源代码
│   │   └── resources/      配置文件、SQL、模板等资源
│   └── test/               测试代码与测试资源
├── target/                 Maven 构建生成的内容
├── logs/                   日志文件
├── pom.xml                 Maven 项目配置
└── Dockerfile              构建容器镜像的说明
```



### 2、`controller`、`service`、`mapper`：按工作职责分层

假设前端请求“查询产品详情”，常见流程是：

```
前端发起 HTTP 请求
        ↓
Controller：接收产品 ID，调用业务方法
        ↓
Service：处理业务规则
        ↓
Mapper：查询数据库
        ↓
数据库
```

查询结果再沿调用链返回。

|目录|主要职责|产品查询的例子|
|---|---|---|
|`controller`|接收 HTTP 请求、取得参数、返回响应|接收 `/products/123`|
|`service`|实现业务流程和业务规则|判断产品是否允许当前用户查看|
|`mapper`|访问数据库；在 MyBatis 项目中常放 Mapper 接口|按产品 ID 查询记录|
|`entity`|存放数据实体，通常用于表示数据库中的数据|`Product` 对象包含 ID、名称、价格|


### 3、`dto`：不同环节传递的数据不一定一样

DTO 是 **Data Transfer Object，数据传输对象**。

比如数据库中的用户包含：

```
id、用户名、密码哈希、创建时间
```

但前端查询用户时，不应该收到密码哈希；前端创建用户时，也不应该自行指定数据库生成的 ID。

因此经常会分别定义：

```
创建请求对象：用户名、密码
数据库实体：ID、用户名、密码哈希、创建时间
响应对象：ID、用户名、创建时间
```

你可以把请求、响应 DTO 类比为 FastAPI 中按用途定义的 Pydantic 数据模型；不过 Java DTO **不一定自带校验能力**。


### 4、config、core、support 是什么？

这几个最容易让人困惑，因为它们的界限没有统一标准。

|目录|常见用途|
|---|---|
|`config`|Java 配置类，例如注册组件、配置跨域、配置客户端|
|`core`|项目认为比较基础、公共或核心的能力|
|`support`|为业务提供辅助或适配能力|

截图里 `core` 的部分子目录，可以这样认识：

| 子目录                 | 常见含义             |
| ------------------- | ---------------- |
| `aspect`            | 切面，例如统一记录某些方法的日志 |
| `event`             | 事件及相关处理代码        |
| `security`          | 认证、授权等安全相关代码     |
| `utils`             | 工具类              |
| `agentspec`、`skill` | 项目特有分类，需要查看代码确认  |
`support` 下的 `chat`、`product`、`gateway` 等，可能是相应业务的辅助实现。**不能仅凭名字认定它比 Service 更底层，或者 Service 必须调用它。**

另外：
- enums 通常放枚举，例如状态、类型等固定选项。
- typehandler 在 MyBatis 项目中通常放类型转换处理器，例如 Java 类型与数据库字段类型之间的转换。



### 5、`resources`：程序运行时使用的配置与资源
你红框中的这部分可以逐个理解：

|文件或目录|常见作用|
|---|---|
|`application.yml`|Spring Boot 的基础配置|
|`application-dev.yml`|`dev` Profile 对应的配置|
|`application-local.yml`|`local` Profile 对应的配置|
|其他 `application-xxx.yml`|名为 `xxx` 的 Profile 配置；具体对应什么环境由团队定义|
|`logback-spring.xml`|日志格式、级别、输出位置等配置|
|`mapper`|在 MyBatis 项目中常放包含 SQL 的 Mapper XML|
|`db/migration`|常见的数据库迁移脚本位置，是否实际使用需看依赖和配置|
|`crd-templates`|从名字看是某类模板，准确用途需要看文件及读取代码|

注意两处都可能有 `mapper`：

```
java/.../mapper/          Java 接口：声明查询方法
resources/mapper/        XML 文件：可能编写对应 SQL
```

它们可以配合工作。也有项目用注解或框架提供的方法实现查询，不为每个接口写 XML。

多个 `application-xxx.yml` 通常用来应对不同运行环境。**存在这些文件，不代表启动时会把它们全部启用。**
