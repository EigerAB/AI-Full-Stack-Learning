# Docker 使用实战

> 承接 [1-intro.md](./1-intro.md) 的概念篇。本篇以真实项目 `zeno-backend-agent/docker-compose.yml` 为例，
> 记录一次从零搭建本地开发环境（Postgres + Redis）踩过的坑和用到的命令，偏"能照着做"的实操笔记。

## 一、先分清楚 `docker` 和 `docker compose`

- `docker <cmd>`：操作**单个**镜像/容器/网络/卷，命令式、一次一个对象。
- `docker compose <cmd>`：读取当前目录的 `docker-compose.yml`，**批量**声明式地管理一组相互关联的服务（网络、卷、启动顺序、健康检查都帮你算好）。

本地开发环境通常涉及多个组件（数据库、缓存、后端、前端…），几乎总是用 `docker compose` 而不是一个个 `docker run`。

> 注：新版 Docker Desktop 内置的是 `docker compose`（没有中间横线），老版本/教程里常见的 `docker-compose`（有横线）是独立二进制，命令参数基本兼容，写笔记时以不带横线的新写法为准。

## 二、查看容器状态

```bash
docker ps
```
列出**正在运行**的容器。判断 Docker 引擎本身是否正常（比如 Docker Desktop 卡住时，这条命令会直接报 `500 Internal Server Error`，而不是返回空列表——这是引擎故障的信号，不是"没有容器"）。

```bash
docker ps -a
```
`-a` = all，连**已停止**的容器也列出来。排查"端口被占用"时很有用：也许某个容器早就停了，但残留配置还占着端口。

```bash
docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}"
```
`--format` 用 Go template 语法自定义输出列。默认输出信息很多、容易看花，指定只看名字/镜像/端口这三列，一眼能看出端口映射关系。

```bash
docker compose ps
```
和 `docker ps` 的区别：只看**当前目录 `docker-compose.yml` 这个项目**启动的容器，不会被其他项目的容器干扰。本地跑多个 compose 项目时（比如同时开着好几个仓库）优先用这个。

## 三、用 Compose 启动指定服务

一次真实操作：只想启动数据库和缓存，不想启动后端/沙盒容器（后端本地用 Python 直接跑，不进容器）：

```bash
docker compose up -d postgres redis
```

拆开看这条命令：

| 部分 | 含义 |
|---|---|
| `docker compose up` | 按 `docker-compose.yml` 的定义创建并启动服务 |
| `-d`（detach） | 后台运行，不占用终端、不用盯着日志 |
| `postgres redis` | 只启动这两个 **service 名**（compose 文件里 `services:` 下的键），其余服务（如 `backend`、`code`、`sandbox-1`）不受影响 |

这一条命令背后实际做了：
1. 按需拉取镜像（没有就 `pull`，本地已有就跳过）
2. 创建这个 compose 项目专属的网络，命名规则是 `<目录名>_<compose 里的 network 名>`（如 `zeno-backend-agent_zenflux-network`）
3. 创建数据卷（`postgres-data`、`redis-data`）用于持久化——**容器删了数据还在**，这是它和"容器内文件系统"的关键区别
4. 创建并启动容器（命名规则默认是 `<目录名>_<service名>_<序号>`，本例中因为 compose 文件显式写了 `container_name`，所以直接叫 `zenflux-postgres`/`zenflux-redis`）

其他常用变体：

```bash
docker compose up -d          # 启动 compose 文件里全部服务
docker compose down           # 停止并删除容器（卷默认保留，除非加 -v）
docker compose down -v        # 连数据卷也删——会清空数据库数据，慎用
docker compose logs postgres  # 看某个服务的日志，排查启动失败的第一步
docker compose logs -f redis  # -f = follow，实时跟随日志输出
docker compose restart backend
```

## 四、`docker-compose.yml` 关键字段对照真实例子

以下摘自 `zeno-backend-agent/docker-compose.yml` 的 `postgres` 服务，逐字段讲解：

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16      # 用哪个镜像（pgvector 是带向量扩展的 Postgres 16）
    container_name: zenflux-postgres   # 固定容器名，不用 compose 自动生成的名字
    restart: unless-stopped            # 容器崩溃/宿主机重启后自动拉起，除非手动停止过
    ports:
      - "127.0.0.1:15432:5432"         # 主机IP:主机端口:容器端口
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-zenflux}      # 读环境变量，没设就用默认值 zenflux
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-zenflux}
      POSTGRES_DB: ${POSTGRES_DB:-zenflux}
    volumes:
      - postgres-data:/var/lib/postgresql/data       # 具名卷，映射到容器内数据目录
    networks:
      - zenflux-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-zenflux}"]
      interval: 5s
      timeout: 5s
      retries: 10
```

### 端口映射 `主机:容器`

`"127.0.0.1:15432:5432"` 是最容易踩坑的一行：
- `5432` 是 Postgres 在**容器内部**监听的标准端口，改不了（除非改 Postgres 自身配置）
- `15432` 是暴露到**宿主机（你的 Windows）**上的端口，可以随便定，这里特意设成非标准端口是为了不和你本机可能已经装的 Postgres 冲突
- `127.0.0.1:` 前缀表示只监听本机回环地址，局域网内其他机器连不上——安全性考虑

**结论**：从宿主机连接数据库时，端口要写 `15432`（映射后的），而不是 `5432`（容器内部的）。这也是我们当时配 `.env` 里 `DATABASE_URL` 时容易搞错的地方：

```bash
# 错：容器内部端口，宿主机连不上
DATABASE_URL=postgresql+asyncpg://zenflux:zenflux@localhost:5432/zenflux

# 对：宿主机映射端口
DATABASE_URL=postgresql+asyncpg://zenflux:zenflux@localhost:15432/zenflux
```

### `${VAR:-default}` 语法

compose 文件里到处能看到 `${POSTGRES_USER:-zenflux}` 这种写法：先读同目录 `.env` 文件或 shell 环境变量里的 `POSTGRES_USER`，如果没设置，就用冒号后面的默认值 `zenflux`。这让 compose 文件本身可以提交到 Git（不含密码），敏感值靠 `.env`（不提交）覆盖。

### `healthcheck`：为什么要等"healthy"而不是"Up"

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U zenflux"]
  interval: 5s     # 每 5 秒探测一次
  timeout: 5s      # 单次探测超时时间
  retries: 10      # 连续失败 10 次才判定为 unhealthy
```

容器进程启动 ≠ 服务真正可用。Postgres 进程刚起来时可能还在做初始化，这时候连接会被拒绝。`healthcheck` 定期在容器**内部**跑一个探测命令，只有探测成功，`docker compose ps` 才会显示 `healthy`。

其他服务如果依赖它，可以这样等：

```yaml
depends_on:
  postgres:
    condition: service_healthy   # 不是简单等它"启动"，而是等它探测通过
```

这也是为什么我们启动完 `postgres`/`redis` 后，会用：

```bash
docker compose ps
```

确认 `STATUS` 列变成 `Up ... (healthy)` 才继续下一步（跑数据库迁移），而不是容器刚 `Up` 就急着连接。

### 数据卷 `volumes`

```yaml
volumes:
  postgres-data:
    driver: local
```

这是**具名卷**（named volume），由 Docker 自己管理存储位置（Windows 上其实存在 WSL2 的虚拟磁盘里），特点：
- 生命周期独立于容器：`docker compose down`（不加 `-v`）删容器不删卷，数据还在
- 下次 `docker compose up` 会自动复用同名卷，数据库数据原样还在
- 想彻底清空重来：`docker compose down -v` 或 `docker volume rm zeno-backend-agent_postgres-data`

对比"绑定挂载"（bind mount，直接映射宿主机某个文件夹）：

```yaml
volumes:
  - ./workspace:/app/workspace   # 宿主机 ./workspace ←→ 容器内 /app/workspace，双向实时同步
```

具名卷适合"我不关心数据存哪，只要持久化"（数据库场景）；绑定挂载适合"我要在宿主机编辑代码，容器里实时看到"（开发挂载源码场景）。

### 网络 `networks`

```yaml
networks:
  zenflux-network:
    driver: bridge      # 默认桥接网络，同网络下容器间可以用 service 名互相访问
  zenflux-internal:
    internal: true       # 内部网络，不能访问外网，也没法从宿主机直接访问，用于隔离敏感组件
```

同一个 `zenflux-network` 里的容器可以直接用**服务名当域名**互相访问，比如 `backend` 服务里配置 `REDIS_HOST=redis`（不是 `localhost`）——Docker 内置的 DNS 会把 `redis` 解析到 `zenflux-redis` 容器的内部 IP。这也是为什么容器之间的连接字符串和"宿主机连容器"的连接字符串端口/host 经常不一样：

| 视角 | Redis 地址 |
|---|---|
| 容器内的 `backend` 连 `redis` | `redis:6379`（服务名 + 容器内端口） |
| 宿主机上跑的脚本连 Redis | `localhost:16379`（映射后的宿主机端口） |

## 五、Dockerfile 与 docker-compose.yml 的关系

- `Dockerfile`：定义**怎么造一个镜像**（一层层的构建步骤：装依赖、拷代码、设置启动命令）
- `docker-compose.yml`：定义**怎么把若干镜像组合起来跑成一套服务**（用哪个镜像/怎么建、端口、环境变量、依赖顺序）

两者的连接点是 compose 文件里的 `build`：

```yaml
services:
  code:
    build:
      context: ./docker/code        # 构建上下文目录（Dockerfile 里 COPY 的相对路径基准）
      dockerfile: Dockerfile        # 用哪个 Dockerfile（默认就叫这个名字）
```

有 `build` 字段的服务，`docker compose up` 会先执行等价于 `docker build` 的步骤本地构建出镜像，再拿去启动容器；只有 `image` 没有 `build` 的服务（如本文的 `postgres`/`redis`）直接从 Registry 拉现成镜像。

单独构建/重新构建镜像：

```bash
docker compose build code          # 只重新构建 code 这个服务对应的镜像
docker compose up -d --build code  # 构建 + 启动一步到位（改了 Dockerfile 后常用）
```

## 六、排障实录

### 1. Docker 引擎本身没起来（`Virtualization support not detected`）
win电脑功能未配置导致起不来
Docker Desktop 启动报错 “Virtualization support not detected“ 的完美解决方案
https://blog.csdn.net/L2463323447/article/details/155704552
https://blog.csdn.net/m0_55837832/article/details/118959577


### 2. 端口被"看不见的进程"占用

现象：某个端口理应是我的服务在监听，实际访问却返回了完全不相关的响应（比如本该是 vite 却返回了 nginx 的 502）。排查顺序：

```powershell
# 1. 看这个端口到底被哪个 PID 监听
Get-NetTCPConnection -LocalPort 5175 -ErrorAction SilentlyContinue |
  Select-Object LocalPort, OwningProcess

# 2. 反查 PID 对应的进程
Get-Process -Id <PID>
Get-CimInstance Win32_Process -Filter "ProcessId=<PID>" | Select-Object ProcessId, Name, CommandLine

# 3. 用 curl 直接打真实端口，跳过任何可能存在的代理层
curl.exe -i -sS http://127.0.0.1:<port>/some/path --max-time 5
```

这次实际的坑：`netstat` 显示端口只被 vite（node）进程监听，但请求却收到了一个远程 nginx 的 502——说明请求路径不是"物理端口被占用"，而是**应用层的代理配置**（vite 的 `VITE_DEV_PROXY_TARGET`）被改成了别的地址，vite 自己把请求转发出去了。教训：`docker ps` / `netstat` 只能证明"网络层没人抢端口"，应用层自己的转发逻辑要单独查配置文件。

### 3. Windows PowerShell 改配置文件引发编码问题

```powershell
(Get-Content .env) -replace '...' | Set-Content .env
```

这行代码在中文注释较多的文件上有坑：`Get-Content`/`Set-Content` 默认按**系统代码页**（国内 Windows 常是 GBK）读写，而项目里的 `.env`/代码文件基本都是 UTF-8。反复用这种方式改写几次后，文件会出现 `UnicodeDecodeError: 'utf-8' codec can't decode bytes ...`。

更保险的写法是显式指定 UTF-8（不带 BOM）：

```powershell
$content = [System.IO.File]::ReadAllText("$PWD\.env")
$content = $content -replace "旧内容", "新内容"
[System.IO.File]::WriteAllText("$PWD\.env", $content, (New-Object System.Text.UTF8Encoding($false)))
```

同理，命令输出重定向到文件时 `*>` 在 PowerShell 里默认是 UTF-16LE，读回来要对应指定编码，否则会看到"每个字符间多一个空格"的乱码（本质是把 UTF-16 的双字节按单字节编码误读了）：

```powershell
python -c "print(open('log.txt', encoding='utf-16').read())"
```


## 七、常用命令速查表

| 命令                                             | 用途                                    |
| ---------------------------------------------- | ------------------------------------- |
| `docker ps` / `docker ps -a`                   | 看运行中 / 全部容器                           |
| `docker compose up -d [service...]`            | 后台启动全部或指定服务                           |
| `docker compose down [-v]`                     | 停止并删除容器（`-v` 连数据卷也删）                  |
| `docker compose ps`                            | 看当前 compose 项目的容器状态（含 healthcheck 结果） |
| `docker compose logs [-f] <service>`           | 看日志（`-f` 实时跟随）                        |
| `docker compose restart <service>`             | 重启单个服务                                |
| `docker compose build [--no-cache] <service>`  | 重新构建镜像                                |
| `docker exec -it <container> <cmd>`            | 进容器内部执行命令（如 `bash`、`psql`）            |
| `docker volume ls` / `docker volume rm <name>` | 查看 / 删除数据卷                            |
| `docker network ls`                            | 查看网络列表                                |
| `docker system df`                             | 看镜像/容器/卷占用的磁盘空间                       |
| `docker system prune`                          | 清理未被引用的镜像/容器/网络（慎用，先确认不需要）            |
