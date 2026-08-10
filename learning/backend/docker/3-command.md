## 常用客户端命令
以下是常用的 Docker 客户端命令：

|**命令**|**功能**|**示例**|
|---|---|---|
|`docker run`|启动一个新的容器并运行命令|`docker run -d ubuntu`|
|`docker ps`|列出当前正在运行的容器|`docker ps`|
|`docker ps -a`|列出所有容器（包括已停止的容器）|`docker ps -a`|
|`docker build`|使用 Dockerfile 构建镜像|`docker build -t my-image .`|
|`docker images`|列出本地存储的所有镜像|`docker images`|
|`docker pull`|从 Docker 仓库拉取镜像|`docker pull ubuntu`|
|`docker push`|将镜像推送到 Docker 仓库|`docker push my-image`|
|`docker exec`|在运行的容器中执行命令|`docker exec -it container_name bash`|
|`docker stop`|停止一个或多个容器|`docker stop container_name`|
|`docker start`|启动已停止的容器|`docker start container_name`|
|`docker restart`|重启一个容器|`docker restart container_name`|
|`docker rm`|删除一个或多个容器|`docker rm container_name`|
|`docker rmi`|删除一个或多个镜像|`docker rmi my-image`|
|`docker logs`|查看容器的日志|`docker logs container_name`|
|`docker inspect`|获取容器或镜像的详细信息|`docker inspect container_name`|
|`docker exec -it`|进入容器的交互式终端|`docker exec -it container_name /bin/bash`|
|`docker network ls`|列出所有 Docker 网络|`docker network ls`|
|`docker volume ls`|列出所有 Docker 卷|`docker volume ls`|
|`docker-compose up`|启动多容器应用（从 `docker-compose.yml` 文件）|`docker-compose up`|
|`docker-compose down`|停止并删除由 `docker-compose` 启动的容器、网络等|`docker-compose down`|
|`docker info`|显示 Docker 系统的详细信息|`docker info`|
|`docker version`|显示 Docker 客户端和守护进程的版本信息|`docker version`|
|`docker stats`|显示容器的实时资源使用情况|`docker stats`|
|`docker login`|登录 Docker 仓库|`docker login`|
|`docker logout`|登出 Docker 仓库|`docker logout`|

**常用选项说明:**

- **`-d`**：后台运行容器，例如 `docker run -d ubuntu`。
- **`-it`**：以交互式终端运行容器，例如 `docker exec -it container_name bash`。
- **`-t`**：为镜像指定标签，例如 `docker build -t my-image .`。

## 容器使用

### 获取镜像

如果我们本地没有 ubuntu 镜像，我们可以使用 docker pull 命令来载入 ubuntu 镜像：

```bash
$ docker pull ubuntu
```

### 启动容器

以下命令使用 ubuntu 镜像启动一个容器，参数为以命令行模式进入该容器：

```bash
$ docker run -it ubuntu /bin/bash
```

参数说明：

- **-i**: 交互式操作。
- **-t**: 终端。
- **ubuntu**: ubuntu 镜像。
- **/bin/bash**：放在镜像名后的是命令，这里我们希望有个交互式 Shell，因此用的是 /bin/bash。

要退出终端，直接输入 **exit**:

```bash
root@ed09e4490c57:/# exit
```


### 启动已停止运行的容器

查看所有的容器命令如下：

```bash
$ docker ps -a
```

使用 docker start 启动一个已停止的容器：

```bash
$ docker start b750bbbcfd88 
```

### 后台运行

在大部分的场景下，我们希望 docker 的服务是在后台运行的，我们可以过 -d 指定容器的运行模式。

```bash
$ docker run -itd --name ubuntu-test ubuntu /bin/bash
```

**注：**加了 -d 参数默认不会进入容器，想要进入容器需要使用指令 **docker exec**（下面会介绍到）。

### 停止一个容器

停止容器的命令如下：

```bash
$ docker stop <容器 ID>
```
停止的容器可以通过 docker restart 重启：

```bash
$ docker restart <容器 ID>
```

### 进入容器

在使用 **-d** 参数时启动容器时，容器会运行在后台，这时如果要进入容器，可以通过以下命令进入：

- **docker attach**：允许你与容器的标准输入（stdin）、输出（stdout）和标准错误（stderr）进行交互。
    
- **docker exec**：推荐大家使用 docker exec 命令，因为此命令会退出容器终端，但不会导致容器的停止。
    

**attach 命令**

使用 docker attach 命令实例：

```bash
$ docker attach 1e560fca3906 
```

**注意：** 如果从这个容器退出，会导致容器的停止。

**exec 命令**

使用 docker exec 命令实例：

docker exec -it 243c32535da7 /bin/bash

[![](https://www.runoob.com/wp-content/uploads/2016/05/docker-exec.png)](https://www.runoob.com/wp-content/uploads/2016/05/docker-exec.png)

**注意：** 如果从这个容器退出，容器不会停止，这就是为什么推荐大家使用 **docker exec**。

更多参数说明请使用 docker exec --help 命令查看。

### 导出和导入容器

**导出容器**

如果要导出本地某个容器，可以使用 **docker export** 命令。

```bash
$ docker export 1e560fca3906 > ubuntu.tar
```

导出容器 1e560fca3906 快照到本地文件 ubuntu.tar。

这样将导出容器快照到本地文件。

**导入容器快照**

可以使用 docker import 从容器快照文件中再导入为镜像，以下实例将快照文件 ubuntu.tar 导入到镜像 test/ubuntu:v1:

```bash
$ cat docker/ubuntu.tar | docker import - test/ubuntu:v1
```
此外，也可以通过指定 URL 或者某个目录来导入，例如：

```bash
$ docker import http://example.com/exampleimage.tgz example/imagerepo
```

### 删除容器

删除容器使用 **docker rm** 命令：

```bash
$ docker rm -f 1e560fca3906
```
下面的命令可以清理掉所有处于终止状态的容器。

```bash
$ docker container prune
```


## 镜像使用

### 列出镜像列表

我们可以使用 **docker images** 来列出本地主机上的镜像。

```bash
runoob@runoob:~$ docker images           
REPOSITORY          TAG                 IMAGE ID            CREATED             SIZE
ubuntu              14.04               90d5884b1ee0        5 days ago          188 MB
php                 5.6                 f40e9e0f10c8        9 days ago          444.8 MB
nginx               latest              6f8d099c3adc        12 days ago         182.7 MB
mysql               5.6                 f2e8d6c772c0        3 weeks ago         324.6 MB
httpd               latest              02ef73cf1bc0        3 weeks ago         194.4 MB
ubuntu              15.10               4e3b13c8a266        4 weeks ago         136.3 MB
hello-world         latest              690ed74de00f        6 months ago        960 B
training/webapp     latest              6fae60ef3446        11 months ago       348.8 MB
```

各个选项说明:

- **REPOSITORY：**表示镜像的仓库源
    
- **TAG：**镜像的标签
    
- **IMAGE ID：**镜像ID
    
- **CREATED：**镜像创建时间
    
- **SIZE：**镜像大小
    

同一仓库源可以有多个 TAG，代表这个仓库源的不同个版本，如 ubuntu 仓库源里，有 15.10、14.04 等多个不同的版本，我们使用 REPOSITORY:TAG 来定义不同的镜像。

所以，我们如果要使用版本为15.10的ubuntu系统镜像来运行容器时，命令如下：

```bash
runoob@runoob:~$ docker run -t -i ubuntu:15.10 /bin/bash 
root@d77ccb2e5cca:/#
```

参数说明：

- **-i**: 交互式操作。
- **-t**: 终端。
- **ubuntu:15.10**: 这是指用 ubuntu 15.10 版本镜像为基础来启动容器。
- **/bin/bash**：放在镜像名后的是命令，这里我们希望有个交互式 Shell，因此用的是 /bin/bash。

如果要使用版本为 14.04 的 ubuntu 系统镜像来运行容器时，命令如下：

```bash
runoob@runoob:~$ docker run -t -i ubuntu:14.04 /bin/bash 
root@39e968165990:/# 
```

如果你不指定一个镜像的版本标签，例如你只使用 ubuntu，docker 将默认使用 ubuntu:latest 镜像。


### 获取一个新的镜像

当我们在本地主机上使用一个不存在的镜像时 Docker 就会自动下载这个镜像。如果我们想预先下载这个镜像，我们可以使用 docker pull 命令来下载它。

```bash
Crunoob@runoob:~$ docker pull ubuntu:13.10
13.10: Pulling from library/ubuntu
6599cadaf950: Pull complete 
23eda618d451: Pull complete 
f0be3084efe9: Pull complete 
52de432f084b: Pull complete 
a3ed95caeb02: Pull complete 
Digest: sha256:15b79a6654811c8d992ebacdfbd5152fcf3d165e374e264076aa435214a947a3
Status: Downloaded newer image for ubuntu:13.10
```

下载完成后，我们可以直接使用这个镜像来运行容器。


### 查找镜像

我们可以从 Docker Hub 网站来搜索镜像，Docker Hub 网址为： **[https://hub.docker.com/](https://hub.docker.com/)**

我们也可以使用 docker search 命令来搜索镜像。比如我们需要一个 httpd 的镜像来作为我们的 web 服务。我们可以通过 docker search 命令搜索 httpd 来寻找适合我们的镜像。

```bash
runoob@runoob:~$  docker search httpd
```

**NAME:** 镜像仓库源的名称

**DESCRIPTION:** 镜像的描述

**OFFICIAL:** 是否 docker 官方发布

**stars:** 类似 Github 里面的 star，表示点赞、喜欢的意思。

**AUTOMATED:** 自动构建。

### 拖取镜像

我们决定使用上图中的 httpd 官方版本的镜像，使用命令 docker pull 来下载镜像。

```bash
runoob@runoob:~$ docker pull httpd
Using default tag: latest
latest: Pulling from library/httpd
8b87079b7a06: Pulling fs layer 
a3ed95caeb02: Download complete 
0d62ec9c6a76: Download complete 
a329d50397b9: Download complete 
ea7c1f032b5c: Waiting 
be44112b72c7: Waiting
```

下载完成后，我们就可以使用这个镜像了。

```bash
runoob@runoob:~$ docker run httpd
```

### 删除镜像

镜像删除使用 **docker rmi** 命令，比如我们删除 hello-world 镜像：

$ docker rmi hello-world

![](https://www.runoob.com/wp-content/uploads/2016/05/docker-rmi-image.png)


### 创建镜像

当我们从 docker 镜像仓库中下载的镜像不能满足我们的需求时，我们可以通过以下两种方式对镜像进行更改。

- 1、从已经创建的容器中更新镜像，并且提交这个镜像
- 2、使用 Dockerfile 指令来创建一个新的镜像

#### 更新镜像

更新镜像之前，我们需要使用镜像来创建一个容器。

```bash
runoob@runoob:~$ docker run -t -i ubuntu:15.10 /bin/bash
```

进入容器后，更新系统：

```bash
apt-get update
apt-get upgrade -y
```

在完成操作之后，输入 exit 命令来退出这个容器。

```bash
exit
```

此时 ID 为 e218edb10161 的容器，是按我们的需求更改的容器。我们可以通过命令 docker commit 来提交容器副本。

```bash
runoob@runoob:~$ docker commit -m="has update" -a="runoob" e218edb10161 runoob/ubuntu:v2
sha256:70bf1840fd7c0d2d8ef0a42a817eb29f854c1af8f7c59fc03ac7bdee9545aff8
```

各个参数说明：

- **-m:** 提交的描述信息
    
- **-a:** 指定镜像作者
    
- **e218edb10161：**容器 ID
    
- **runoob/ubuntu:v2:** 指定要创建的目标镜像名

我们可以使用 **docker images** 命令来查看我们的新镜像 **runoob/ubuntu:v2**：

```bash
runoob@runoob:~$ docker images
REPOSITORY          TAG                 IMAGE ID            CREATED             SIZE
runoob/ubuntu       v2                  70bf1840fd7c        15 seconds ago      158.5 MB
ubuntu              14.04               90d5884b1ee0        5 days ago          188 MB
php                 5.6                 f40e9e0f10c8        9 days ago          444.8 MB
nginx               latest              6f8d099c3adc        12 days ago         182.7 MB
mysql               5.6                 f2e8d6c772c0        3 weeks ago         324.6 MB
httpd               latest              02ef73cf1bc0        3 weeks ago         194.4 MB
ubuntu              15.10               4e3b13c8a266        4 weeks ago         136.3 MB
hello-world         latest              690ed74de00f        6 months ago        960 B
training/webapp     latest              6fae60ef3446        12 months ago       348.8 MB
```

使用我们的新镜像 **runoob/ubuntu** 来启动一个容器

```bash
runoob@runoob:~$ docker run -t -i runoob/ubuntu:v2 /bin/bash                            
root@1a9fbdeb5da3:/#
```

#### 构建镜像

我们使用命令 **docker build** ， 从零开始来创建一个新的镜像。为此，我们需要创建一个 Dockerfile 文件，其中包含一组指令来告诉 Docker 如何构建我们的镜像。

```bash
runoob@runoob:~$ cat Dockerfile 
FROM    centos:6.7
MAINTAINER      Fisher "fisher@sudops.com"

RUN     /bin/echo 'root:123456' |chpasswd
RUN     useradd runoob
RUN     /bin/echo 'runoob:123456' |chpasswd
RUN     /bin/echo -e "LANG=\"en_US.UTF-8\"" >/etc/default/local
EXPOSE  22
EXPOSE  80
CMD     /usr/sbin/sshd -D
```

每一个指令都会在镜像上创建一个新的层，每一个指令的前缀都必须是大写的。

第一条FROM，指定使用哪个镜像源

RUN 指令告诉docker 在镜像内执行命令，安装了什么。。。

然后，我们使用 Dockerfile 文件，通过 docker build 命令来构建一个镜像。

```bash
runoob@runoob:~$ docker build -t runoob/centos:6.7 .
Sending build context to Docker daemon 17.92 kB
Step 1 : FROM centos:6.7
 ---&gt; d95b5ca17cc3
Step 2 : MAINTAINER Fisher "fisher@sudops.com"
 ---&gt; Using cache
 ---&gt; 0c92299c6f03
Step 3 : RUN /bin/echo 'root:123456' |chpasswd
 ---&gt; Using cache
 ---&gt; 0397ce2fbd0a
Step 4 : RUN useradd runoob
......
```

参数说明：

- **-t** ：指定要创建的目标镜像名
    
- **.** ：Dockerfile 文件所在目录，可以指定Dockerfile 的绝对路径
    

使用docker images 查看创建的镜像已经在列表中存在,镜像ID为860c279d2fec

```bash
runoob@runoob:~$ docker images 
REPOSITORY          TAG                 IMAGE ID            CREATED              SIZE
runoob/centos       6.7                 860c279d2fec        About a minute ago   190.6 MB
runoob/ubuntu       v2                  70bf1840fd7c        17 hours ago         158.5 MB
ubuntu              14.04               90d5884b1ee0        6 days ago           188 MB
php                 5.6                 f40e9e0f10c8        10 days ago          444.8 MB
nginx               latest              6f8d099c3adc        12 days ago          182.7 MB
mysql               5.6                 f2e8d6c772c0        3 weeks ago          324.6 MB
httpd               latest              02ef73cf1bc0        3 weeks ago          194.4 MB
ubuntu              15.10               4e3b13c8a266        5 weeks ago          136.3 MB
hello-world         latest              690ed74de00f        6 months ago         960 B
centos              6.7                 d95b5ca17cc3        6 months ago         190.6 MB
training/webapp     latest              6fae60ef3446        12 months ago        348.8 MB
```

我们可以使用新的镜像来创建容器

```bash
runoob@runoob:~$ docker run -t -i runoob/centos:6.7  /bin/bash
[root@41c28d18b5fb /]# id runoob
uid=500(runoob) gid=500(runoob) groups=500(runoob)
```

从上面看到新镜像已经包含我们创建的用户 runoob。

#### 设置镜像标签

我们可以使用 docker tag 命令，为镜像添加一个新的标签。

```bash
runoob@runoob:~$ docker tag 860c279d2fec runoob/centos:dev
```

docker tag 镜像ID，这里是 860c279d2fec ,用户名称、镜像源名(repository name)和新的标签名(tag)。

使用 docker images 命令可以看到，ID为860c279d2fec的镜像多一个标签。

```bash
runoob@runoob:~$ docker images
REPOSITORY          TAG                 IMAGE ID            CREATED             SIZE
runoob/centos       6.7                 860c279d2fec        5 hours ago         190.6 MB
runoob/centos       dev                 860c279d2fec        5 hours ago         190.6 MB
runoob/ubuntu       v2                  70bf1840fd7c        22 hours ago        158.5 MB
ubuntu              14.04               90d5884b1ee0        6 days ago          188 MB
php                 5.6                 f40e9e0f10c8        10 days ago         444.8 MB
nginx               latest              6f8d099c3adc        13 days ago         182.7 MB
mysql               5.6                 f2e8d6c772c0        3 weeks ago         324.6 MB
httpd               latest              02ef73cf1bc0        3 weeks ago         194.4 MB
ubuntu              15.10               4e3b13c8a266        5 weeks ago         136.3 MB
hello-world         latest              690ed74de00f        6 months ago        960 B
centos              6.7                 d95b5ca17cc3        6 months ago        190.6 MB
training/webapp     latest              6fae60ef3446        12 months ago       348.8 MB
```