# uv 工作流文档

[uv](https://github.com/astral-sh/uv) 是一个用 Rust 编写的高性能 Python 包与项目管理工具，目标是替代 `pip`、`pip-tools`、`pipx`、`poetry`、`virtualenv`、`pyenv` 等一系列工具，提供统一的工作流。

## 1. 安装 uv

```powershell
# Windows (PowerShell)
powershell -ExecutionPolicy Bypass -c "irm https://astral.sh/uv/install.ps1 | iex"

# macOS / Linux
curl -LsSf https://astral.sh/uv/install.sh | sh

# 或者通过 pip / pipx 安装
pipx install uv
```

安装完成后验证：

```bash
uv --version
uv self update   # 升级 uv 本身
```

## 2. 管理 Python 版本

uv 自带 Python 版本管理，无需依赖 pyenv。

```bash
uv python list                 # 查看可安装/已安装的 Python 版本
uv python install 3.12         # 安装指定版本
uv python install 3.11 3.12    # 一次安装多个版本
uv python pin 3.12             # 在当前项目写入 .python-version，锁定版本
uv python uninstall 3.10       # 卸载某个版本
```

## 3. 项目初始化与依赖管理

### 3.1 新建项目

```bash
uv init my-project             # 创建新项目（生成 pyproject.toml、.python-version 等）
cd my-project
uv init --lib                  # 创建可发布的库项目（含 src 布局）
uv init --app                  # 创建应用型项目（默认）
```

### 3.2 添加 / 移除依赖

```bash
uv add requests                # 添加依赖，自动更新 pyproject.toml 和 uv.lock
uv add "fastapi>=0.110"        # 指定版本约束
uv add --dev pytest ruff       # 添加开发依赖（写入 dev group）
uv add --optional web flask    # 添加到可选依赖组（extras）
uv remove requests             # 移除依赖
```

### 3.3 同步与锁定

```bash
uv lock                        # 根据 pyproject.toml 生成/更新 uv.lock
uv sync                        # 按 uv.lock 精确同步虚拟环境（增删依赖）
uv sync --frozen               # 严格按 lock 文件同步，不重新解析（CI 推荐）
uv sync --no-dev               # 只同步生产依赖，跳过 dev 依赖
```

### 3.4 运行代码

```bash
uv run python main.py          # 在项目虚拟环境中运行脚本（自动同步依赖）
uv run pytest                  # 运行已安装的命令行工具
uv run -- python -m mymodule   # 传参给子命令
```

`uv run` 会自动检测 `pyproject.toml` / `uv.lock` 是否有变化，并按需同步虚拟环境，无需手动 `activate`。

## 4. 虚拟环境管理

```bash
uv venv                        # 在 .venv 下创建虚拟环境（默认使用项目 pinned 版本）
uv venv --python 3.11          # 指定 Python 版本创建
.venv\Scripts\activate         # Windows 激活（如需手动进入环境）
source .venv/bin/activate      # macOS/Linux 激活
```

日常开发推荐直接用 `uv run`，无需手动 activate。

## 5. 配置镜像源（国内加速）

uv 默认从 PyPI 官方源拉取包，在国内网络下可能很慢，可以配置镜像源加速。

### 5.1 临时指定（单次命令）

```bash
uv add requests --index-url https://pypi.tuna.tsinghua.edu.cn/simple
uv pip install requests --index-url https://pypi.tuna.tsinghua.edu.cn/simple
```

### 5.2 环境变量（当前 shell 生效）

```bash
# Windows PowerShell
$env:UV_INDEX_URL = "https://pypi.tuna.tsinghua.edu.cn/simple"

# macOS / Linux
export UV_INDEX_URL="https://pypi.tuna.tsinghua.edu.cn/simple"
```

### 5.3 全局配置（推荐，一劳永逸）

编辑 uv 的全局配置文件：

- Windows: `%APPDATA%\uv\uv.toml`
- macOS/Linux: `~/.config/uv/uv.toml`

```toml
[[index]]
url = "https://pypi.tuna.tsinghua.edu.cn/simple"
default = true
```

### 5.4 项目级配置

也可以只对某个项目生效，写入项目根目录的 `uv.toml`（或 `pyproject.toml` 的 `[tool.uv]` 段）：

```toml
# uv.toml
[[index]]
url = "https://pypi.tuna.tsinghua.edu.cn/simple"
default = true
```

```toml
# 或者写在 pyproject.toml 里
[[tool.uv.index]]
url = "https://pypi.tuna.tsinghua.edu.cn/simple"
default = true
```

### 5.5 常用国内镜像源

| 镜像源 | 地址 |
| --- | --- |
| 清华大学 | `https://pypi.tuna.tsinghua.edu.cn/simple` |
| 阿里云 | `https://mirrors.aliyun.com/pypi/simple` |
| 中国科技大学 | `https://pypi.mirrors.ustc.edu.cn/simple` |
| 腾讯云 | `https://mirrors.cloud.tencent.com/pypi/simple` |

> 注意：uv 的镜像配置项是 `UV_INDEX_URL` / `[[index]]`，与 pip 的 `PIP_INDEX_URL` / `pip.conf` 是两套独立配置，不会互相继承，需要分别设置。如果同时保留 pip 配置，建议两边镜像地址保持一致，避免依赖解析结果不一致。

## 6. 单文件脚本 / 临时依赖

适合不想建整个项目、只是跑个脚本的场景：

```bash
uv run --with requests script.py     # 临时安装依赖并运行脚本，不写入任何配置
uv run --python 3.11 script.py       # 临时指定 Python 版本运行

# 也可以在脚本头部用 PEP 723 内联声明依赖
# /// script
# dependencies = ["requests"]
# ///
uv run script.py                     # uv 会自动读取内联依赖并安装
```

## 7. 全局工具管理（替代 pipx）

```bash
uv tool install ruff            # 全局安装命令行工具（隔离环境）
uv tool run ruff check .        # 等价于 uvx ruff check .
uvx black .                     # uvx 是 uv tool run 的简写
uv tool list                    # 查看已安装工具
uv tool uninstall ruff          # 卸载
uv tool upgrade ruff            # 升级
```

## 8. 兼容 pip 的接口（迁移过渡用）

```bash
uv pip install requests         # 类似 pip install，但更快
uv pip compile requirements.in -o requirements.txt   # 替代 pip-compile
uv pip sync requirements.txt    # 替代 pip-sync
```

## 9. 常用文件说明

| 文件 | 作用 |
| --- | --- |
| `pyproject.toml` | 项目元数据、依赖声明（`[project.dependencies]` / `[dependency-groups]`） |
| `uv.lock` | 精确锁定所有依赖版本及哈希，应提交到 git |
| `.python-version` | 记录项目使用的 Python 版本，`uv python pin` 生成 |
| `.venv/` | 虚拟环境目录，不应提交到 git |

## 10. 推荐的日常工作流

1. `uv init` 创建项目，`uv python pin 3.12` 锁定版本
2. `uv add <包名>` 添加依赖，`uv add --dev <包名>` 添加开发工具
3. 日常用 `uv run <命令>` 执行脚本/测试/工具，无需手动 activate
4. 提交代码时把 `pyproject.toml` 和 `uv.lock` 一起提交
5. CI / 部署环境用 `uv sync --frozen` 精确还原依赖
6. 全局命令行工具（如 ruff、black、cookiecutter）用 `uv tool install` 而不是污染项目环境

## 11. 常见问题

**Q: 还需要 pyenv 吗？**
A: 纯 uv 工作流下不需要，`uv python install/pin` 已覆盖版本管理需求。仅当依赖 pyenv shim 做全局 shell 级别切换，或需要编译特殊 CPython 版本时才考虑保留。

**Q: uv.lock 需要提交到 git 吗？**
A: 需要，这样才能保证团队和 CI 环境的依赖版本完全一致。

**Q: 配置了镜像源，`uv.lock` 里会记录镜像地址吗？**
A: 不会。`uv.lock` 只记录包名、版本和哈希，不记录来源地址，因此换镜像源不会导致 lock 文件冲突，团队成员可以各自配置自己习惯的镜像。

**Q: 如何在多个 Python 版本间测试兼容性？**
A: 结合 `uv python install 3.9 3.10 3.11 3.12` 安装多版本，再配合 `tox` 或简单脚本用 `uv run --python 3.x` 逐一测试。
