# 0004 · 工具执行层
**日期**：2026-08-23
**状态**：已学（第 4 课）

## 学到了什么
- 三层结构：**ToolExecutor**（执行+校验+策略，executor.py:113）→ **ToolProviderManager**（按优先级路由，provider_manager.py:17）→ **ToolProviderController**（每种工具类型的实际执行器）。Executor 管「该不该跑、怎么跑」，Manager 管「找谁跑」，Provider 管「真跑」。
- **五种 Provider 优先级**（PROVIDER_ORDER，provider_manager.py:30）：BUILTIN > API > PYTHON > TOOL_TEMPLATE > MCP > FRONTEND。同名工具冲突时高优先级盖低优先级。
- **execute_tools 三道关**（executor.py:772）：① schema 预检（不过→整批不跑，返回 _schema_blocked_results）→ ② 策略分组（_partition_by_policy）→ ③ 并行执行 + sibling abort。
- **sibling abort**（executor.py:856-857 / 897-899）：关键工具（`_CRITICAL_TOOLS = {sandbox, code_interpreter, bash, shell}`，executor.py:865）is_error=True → 同批次剩余工具跳过，返回 `error_msg="sibling_abort"`。非关键工具失败不触发 abort。
- **🔴 is_error 判定**（executor.py:677-685）：三个条件取或——`result["is_error"] is True` / `result["success"] is False` / `result["error"]` 非空（不是 None/空串/空列表/空 dict）。第 3 条意味着 `{"success":True,"error":"warning"}` 也会被判 is_error=True。
- **execute() 单工具五道门**（executor.py:463）：① get_tool 找不到→TOOL_NOT_FOUND（不抛）→ ② is_runtime_executable False→reject → ③ 确认门/挂起 → ④ schema 校验 → ⑤ manager.invoke 真跑。**前四道门失败都返回结构化 dict（is_error），不抛异常**；只有第⑤道 invoke 抛异常才被 except 捕获包装成 tool_error_result。
- 这解释了上一课的「单工具 is_error 不终止循环」：execute() 把所有失败包装成结构化 dict → _execute_single_tracked 包装成 ToolExecutionResult(is_error=True) → AgentLoop 看到「有错误的结果」而非「异常」，照常追加进消息历史。只有 _execute_tools 自己抛异常（极罕见）才走 loop.py:698 的 except 终止。
- **确认门**（_maybe_suspend_for_blocking，executor.py:337）：工具声明 `blocking=USER_CONFIRM` 时停下等人确认。三种结局（超时/取消/拒绝）**全部返回结构化失败不抛异常**，提示「不要重试」。旧版本曾向上裸抛导致整轮 run 炸，已修。AG-UI 下结构性恒关；开关 `confirm_gate_enabled()` 控制。
- **ToolExecutionResult**（types.py:231）：tool_id / tool_name / tool_input / result / is_error / error_msg。AgentLoop 拿到后用 _tool_results_to_blocks 转成 tool_result block 追加进消息历史，is_error 字段会进 block 给模型看。
- **ToolContext**（types.py:246）：工具执行的身份上下文（session_id / user_id / agent_id / extra）。排障「工具拿到了错的租户上下文」查这里。

## 关键文件
- `core/tool/executor.py` — ToolExecutor（113）/ execute_tools（772）/ execute（463）/ _execute_single_tracked（591）/ _maybe_suspend_for_blocking（337）/ _run_parallel_with_abort（871）/ _CRITICAL_TOOLS（865）/ is_error 判定（677-685）
- `core/tool/provider_manager.py` — ToolProviderManager（17）/ PROVIDER_ORDER（30）/ get_tool（79）/ invoke（215）
- `core/tool/types.py` — ToolExecutionResult（231）/ ToolContext（246）
- `core/tool/provider.py` — ToolProviderType 枚举（66）
- `core/tool/observation.py` — ensure_observation_contract（统一结果信封）

## 排障归因表（增量）
| 症状 | 最可能落点 | 确认方式 |
|---|---|---|
| 「工具未找到」 | 门 1 TOOL_NOT_FOUND | 查 manager.get_tool；名字是否和 provider 注册名一致 |
| 整批工具都没跑 | 关 1 schema 预检不过 | 看 prevalidation；模型传的参数结构 |
| 工具显示 sibling_abort | 关 3 关键工具失败触发 abort | 查同批次先跑的关键工具为何 is_error |
| 工具成功了但被当失败 | is_error 判定第 3 条 | 查 result 里有没有非空 error 字段 |
| 工具被拒但 AI 还在重试 | 确认门 POLICY_DENIED + 模型没读懂 | 看 error_code；模型行为问题非工具层 |
| 工具在当前视图不可执行 | 门 2 runtime_tool_view_reject | 查 is_runtime_executable；RuntimeToolView 配置 |
| 自定义工具被内置盖掉 | PROVIDER_ORDER 优先级 | 查同名工具；get_tool 返回的 provider_type |
| 工具结果太大被截断 | _postprocess 压缩 | 看 result 有没有 truncated/compressed/ref_id |

## 待深挖
- ToolSchemaValidator 的 _pre_execute_validation 和 _postprocess_result_with_schema 细节——等讲到 schema 校验边界时展开。
- ToolResultCompressor 的压缩机制（ref_id / recover_full_content）——等讲到上下文压缩时展开。
- RuntimeToolView（工具视图）——等讲到工具选择/视图构建时展开。
- ToolPolicyEnforcer._partition_by_policy 的分组策略细节——等讲到调用策略时展开。
