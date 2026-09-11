# AzurPilot 调度队列 —— 代码考古报告

- 目标代码库：`D:\Tools\AzurPilot`（只读，未修改任何文件）
- 实例：`alas`（`config/alas.json`），PC 本机时间 2026-09-11 10:19 (+08:00)
- 验证方式：读代码 + 用项目自带 venv 真实执行 `AzurLaneConfig.get_next_task()`
  （探针脚本 `D:\Temp\alas_probe.py` / `alas_probe2.py`，已把 `atomic_write` / `write_file`
  全部 patch 成 no-op，运行结束打印 `writes attempted: []`，确认零写入）

---

## 0. 一句话结论

**WebUI 和 MCP `get_scheduler_queue` 用的是同一个数据源（同一个进程、同一个 `config/alas.json`、
同一条 `read_file → config_update` 管线），19 个启用任务集合逐字相同。差异全部出在"拿到数据之后
怎么算"：分桶规则、排序键、任务名。App 在客户端自己重写了分桶逻辑，所以永远对不齐。**

具体到「已停止」状态：App 的「队列中」内容和 WebUI 的**集合完全相同**，但**顺序相反**；
一旦实例是"活着但暂停"（state=1/3），App 的「队列中」会**多出一个任务**（WebUI 把它放在「运行中」）。

---

## 1. WebUI 上"调度队列"到底在哪个文件哪个函数

### 1.1 界面骨架（三个分组）

`D:\Tools\AzurPilot\module\webui\app_overview.py:64-87` —— `OverviewMixin.alas_overview()`

```
app_overview.py:64-71   put_scope("running",  [ put_text(t("Gui.Overview.Running")), put_scope("running_tasks") ])
app_overview.py:72-79   put_scope("pending",  [ put_text(t("Gui.Overview.Pending")), put_scope("pending_tasks") ])
app_overview.py:80-87   put_scope("waiting",  [ put_text(t("Gui.Overview.Waiting")), put_scope("waiting_tasks") ])
```

即 WebUI 是**三栏**：运行中 / 队列中 / 等待中。

### 1.2 真正填数据 + 渲染的函数（唯一一处）

`D:\Tools\AzurPilot\module\webui\app_dashboard.py:35-98` —— `DashboardMixin.alas_update_overview_task()`

```
app_dashboard.py:38      self.alas_config.load()          # 从磁盘重新读配置
app_dashboard.py:39      self.alas_config.get_next_task() # 计算队列
app_dashboard.py:41-51   分桶（见第 3 节）
app_dashboard.py:63-71   put_task()：任务名 + 下一次时间 + 设置按钮
app_dashboard.py:78-98   clear() 三个 scope 后重绘
```

任务名与时间的渲染，`app_dashboard.py:63-71`：

```python
def put_task(func: Function):
    with use_scope(f"overview-task_{func.command}"):
        put_column([
            put_text(t(f"Task.{func.command}.name")).style("--arg-title--"),
            put_text(str(func.next_run)).style("--arg-help--"),
        ], size="auto auto")
```

刷新频率：`app_overview.py:245`
```python
self.task_handler.add(self.alas_update_overview_task, 10, True)   # 每 10 秒一次
```
外加切到该标签页时立即刷一次：`app_home.py:311-320`（`page == "Overview"` → `alas_update_overview_task()`）。
`app_dashboard.py:55-61` 的 `_overview_snapshot` 只是"内容没变就不重绘"的渲染缓存，**不是数据缓存**。

### 1.3 另外两处容易混淆的"队列"（都不是队列中/等待中）

| 位置 | 内容 | 与调度队列的关系 |
|---|---|---|
| `module/webui/app_manage.py:125-144` `_get_enabled_tasks()` | 管理页"已启用任务 + 调度顺序"列表 | **直接读磁盘 JSON 文件**（`_read_config_mapping(filepath_config(...))`），只按 `Scheduler.Enable is True` 过滤，**完全不看 NextRun**，按优先级排序。是所有"启用"任务，不是"到期"任务 |
| `module/webui/app_task_config.py:465-477` | 配置页 `Scheduler.NextRun` 旁边的「立即运行」按钮 | 写入 `Scheduler.NextRun = ""`（见第 6 节，空串会被兜底成 `2020-01-01 00:00:00`） |

---

## 2. 显示的是中文还是英文键？中文从哪来

**显示中文。** 键就是 `t(f"Task.{func.command}.name")`（`app_dashboard.py:67`），
其中 `func.command` 是配置里的 `Scheduler.Command`（英文任务键）。

翻译链路：

| 层 | 位置 |
|---|---|
| `t()` | `module/webui/lang.py:37-44` |
| `_t()` | `module/webui/lang.py:47-57` → `dic_lang[lang][s]` |
| 词典加载 | `module/webui/lang.py:63-73` `reload()`，`deep_iter(read_file(filepath_i18n(lang)), depth=3)` 用 `.` 拼 key |
| i18n 文件路径 | `module/config/utils.py:72-76` → `./module/config/i18n/{lang}.json` |
| 默认语言 | `module/webui/lang.py:15` `LANG = "zh-CN"`；`set_language()` 见 `lang.py:19-29` |

所以 i18n key 就是 **`Task.<Command>.name`**（`Task` 在第 1 层、任务名第 2 层、`name` 第 3 层，正好 depth=3）。

`module/config/i18n/zh-CN.json` 实测值：

```
zh-CN.json:157-158   "Commission": { "name": "委托" }
zh-CN.json:161-162   "Tactical":   { "name": "战术学院" }
zh-CN.json:285-286   "OpsiScheduling": { "name": "智能调度Plus" }
```

三个分栏标题的 key（`module/config/i18n/zh-CN.json`）：

```
zh-CN.json:6271   "Running": "运行中"
zh-CN.json:6272   "Pending": "队列中"      <-- 用户说的「队列中」
zh-CN.json:6273   "Waiting": "等待中"
zh-CN.json:6274   "NoTask":  "无任务"
```

**关键点：MCP 侧有同一份翻译可用，但 `get_scheduler_queue` 没用它。**
`mcp_server_sse.py:40` `helper = McpConfigHelper()`（默认 `lang="zh-CN"`，见 `module/config/mcp_helper.py:35-38`），
`mcp_helper.py:50-58`：

```python
task_i18n = self.i18n_data.get("Task", {}).get(task_name, {})
result = {"task_name": task_name,
          "display_name": task_i18n.get("name", task_name), ...}
```

即 `get_task_help(task).display_name` 拿到的中文名和 WebUI 的 `t(f"Task.{x}.name")` **同源同值**。

---

## 3. 实例「停止」（state=2）时 WebUI 显示什么

### 3.1 分桶代码

`module/webui/app_dashboard.py:41-51`（**逐字**）：

```python
if len(self.alas_config.pending_task) >= 1:
    if self.alas.alive:
        running = self.alas_config.pending_task[:1]
        pending = self.alas_config.pending_task[1:]
    else:
        running = []
        pending = self.alas_config.pending_task[:]
else:
    running = []
    pending = []
waiting = self.alas_config.waiting_task
```

结论：

| 实例状态 | 运行中 | 队列中 | 等待中 |
|---|---|---|---|
| 运行中（alive） | `pending_task[0]`（即将执行的**那一个**） | `pending_task[1:]` | `waiting_task` |
| 已停止（state=2） | 空 → 显示「无任务」 | **`pending_task` 全部** | `waiting_task` |

`ProcessManager.state` 语义：`module/webui/app_shell.py:411-433` 注释 `1(running) 2(not running) 3(warning) 4(stop for update)`；
实现见 `module/webui/process_manager.py:616-662`（`1 → alive`，`2 → 未运行/手动停止/日志为空`）。
且 alas 脚本跑在**独立子进程**里（`process_manager.py:18` `from multiprocessing import Process`，`:150`、`:285`），
WebUI 进程只是观察者。

### 3.2 数据不是缓存，是"重新读文件 + 重算"

```
app_dashboard.py:38   self.alas_config.load()
  └─ module/config/config.py:189-194   load(): self.data = self.read_file(...) ; self.config_override()
       └─ module/config/config_updater.py:916-931  read_file() → config_update(old)
            └─ module/config/utils.py:90-105       read_file() → atomic_read_bytes + json.loads  ← 每次都读盘
```

- **没有 TTL 缓存**。`ConfigWatcher`（`module/config/watcher.py:27-38`）只看 mtime，且 `load()` 根本不调用它。
- 但也**不是"裸读配置文件"**：中间过了 `config_update()`（`config_updater.py:716-815`，按 `args.json` 补默认值/迁移）
  和 `config_override()`（`config.py:191` 调用，别名见 `config.py:432 config_override = override`）。
- 因为 alas 子进程停止后**没有任何人再推进 `NextRun`**，所以整份队列是"冻结"的：
  所有 `NextRun` 已过期的启用任务会一直堆在「队列中」。这就是停止状态下的表现。

---

## 4. "下一个要跑的任务"用的是哪个函数

### 4.1 计算队列：`AzurLaneConfig.get_next_task()`

`module/config/config.py:312-342`（逐字 + 行号）：

```python
312  def get_next_task(self):
313      """计算任务队列，设置 pending_task 和 waiting_task。"""
314      pending = []; waiting = []; error = []
317      now = current_time()
318      if AzurLaneConfig.is_hoarding_task:
319          now -= self.hoarding                       # ★ 囤积偏移：把"现在"往前挪
320      for func in self.data.values():
321          func = Function(func)
322          if not func.enable:
323              continue
324          if not isinstance(func.next_run, datetime):
325              error.append(func)                      # ★ 坏值 → 一律进 pending
326          elif func.next_run < now:                   # ★ 严格小于
327              pending.append(func)
328          else:
329              waiting.append(func)
331      f = Filter(regex=r"(.*)", attr=["command"])
332      f.load(self.SCHEDULER_PRIORITY)
333      if pending: pending = f.apply(pending)          # ★ 队列中 = 按优先级排
335      if waiting:
336          waiting = f.apply(waiting)
337          waiting = sorted(waiting, key=operator.attrgetter("next_run"))   # 等待中 = 按时间排
338      if error:
339          pending = error + pending
341      self.pending_task = pending
342      self.waiting_task = waiting
```

### 4.2 真正决定"下一个跑谁"：`AzurLaneConfig.get_next()`

`module/config/config.py:344-370`：`self.get_next_task()` → **取 `pending_task[0]`**（352-357）；
没有 pending 就取 `waiting_task[0]` 且 `next_run += hoarding`（361-366）；都没有则 `RequestHumanTakeover`（368-370）。
任务切换时也走它：`config.py:715-733 task_switched()`。

> ★ 所以 **`pending_task[0]` 才是"下一个要跑的任务"**，而 `pending_task` 是**按优先级**排的。
> `pending_task[0]` 旁边显示的时间是它自己的 `next_run`（通常是过去时间），WebUI「运行中」栏就是它。

### 4.3 相关定义

| 名称 | 位置 |
|---|---|
| `Function`（enable / command / next_run） | `module/config/config.py:40-70` |
| `hoarding` 属性 | `module/config/config.py:293-300`，读 `Alas.Optimization.TaskHoardingDuration` 分钟 |
| 囤积语义（中文说明） | `module/config/i18n/zh-CN.json:879-882`「囤积任务 X 分钟：任务触发后，等待 X 分钟，再一次性执行囤积的任务」 |
| `SCHEDULER_PRIORITY` | `module/config/config_manual.py:107-136`，= `merge_task_priority(TaskPriorityAdjustment, 默认表, available_tasks)` |
| 默认优先级表 | `module/config/config_manual.py:53-80` |
| 本机自定义优先级 | `config/alas.json` 的 `General.YukikazeTaskManager.TaskPriorityAdjustment`（实测 `OpsiAshBeacon` 排在 `OpsiScheduling` **之前**） |

---

## 5. MCP `get_scheduler_queue` 的实现与数据源对比（重点）

### 5.1 实现

`D:\Tools\AzurPilot\mcp_server_sse.py:362-374`（逐字）：

```python
362  async def _tool_get_scheduler_queue(arguments: Dict[str, Any]) -> ToolResponse:
363      inst = arguments["instance"]
364      config = AzurLaneConfig(inst)                    # ← 新建一个配置对象
365      queue_data = []
366      for task_name in config.data:                    # ← 顶层 group key
367          if task_name in ["Alas", "Error", "MUMU", "MumuPlayer12", "EmulatorManagement", "Dashboard"]:
368              continue
369          scheduler = config.data.get(task_name, {}).get("Scheduler", {})
370          if scheduler.get("Enable", False):           # ← 和 WebUI 同一条过滤
371              next_run = scheduler.get("NextRun", "2050-01-01 00:00:00")
372              queue_data.append({"task": task_name, "next_run": str(next_run)})   # ← 英文键 + 字符串
373      queue_data.sort(key=lambda x: str(x["next_run"]))  # ← 按时间字符串排序
374      return [TextContent(type="text", text=json.dumps(queue_data, ensure_ascii=False, indent=2))]
```

注册与挂载：

- 工具表 `mcp_server_sse.py:479`
- 工具 schema/描述 `mcp_server_sse.py:168-172`（描述："获取当前正在排队等待执行的任务列表及它们的预计执行时间"）
- 挂载：`module/webui/app.py:380-395`
  ```python
  from mcp_server_sse import app as mcp_app
  ...
  application.mount("/mcp", mcp_app)
  ```

### 5.2 关键事实：MCP 与 WebUI 是**同一个进程**

`module/webui/app.py:395` 把 MCP 的 ASGI app 直接 mount 在 WebUI 的 Starlette 应用上 ——
也就是 `http://192.168.1.100:25548/mcp` 和 PC 端 WebUI 是**同一个进程、同一个 CWD、同一份 `config/alas.json`**。
（CWD 由 `module/logger.py:397` `os.chdir(<项目根>/../)` 固定到项目根。）

所以：**"数据源不同"这个假设不成立 —— 数据源完全相同。不一致来自两边各自的计算规则。**

### 5.3 实测验证（真实代码执行）

探针 `D:\Temp\alas_probe2.py` 输出（2026-09-11 10:19:56，实例 `alas`）：

```
--- RAW FILE (no config_update) Scheduler.Enable==true: 19
   OpsiScheduling             2026-09-09 00:00:00
   OpsiAshBeacon              2026-09-11 09:57:48
   Commission                 2026-09-11 10:22:59
   Guild / Reward / Exercise  2026-09-11 12:00:00
   Dorm                       2026-09-11 13:38:13
   Tactical                   2026-09-11 14:18:32
   Meowfficer/Daily/Hard/Gacha/Freebies/Minigame/PrivateQuarters/OpsiAshAssist/OpsiShop/OpsiDaily  2026-09-12 00:00:00
   Restart                    2026-09-12 00:17:00

--- config.data (after config_update, what BOTH webui & mcp read): 19     <-- 完全一致
--- groups present in cfg.data but NOT in raw file: []
--- groups in raw file but NOT in cfg.data: []
```

`Scheduler.Enable` / `Scheduler.NextRun` 在 `module/config/argument/args.json` 里是
`type: "checkbox"` / `type: "datetime"`，都不在 `config_update` 的"强制用默认值"分支
（`config_updater.py:734-738` 的 `lock`/`state`/`display==hide`）里，所以文件值被原样保留。

六名单跳过表（`mcp_server_sse.py:367`）是**空操作**：`Alas`、`Dashboard` 在 args.json 里存在但**没有 `Scheduler` 组**，
`Error`/`MUMU`/`MumuPlayer12`/`EmulatorManagement` 四个在 args.json 里根本不存在。
即两边有效过滤条件**完全相同**（`Scheduler.Enable == True`）。

同一探针里两边的分桶/排序（真实 `get_next_task()`）：

```
NOW = 2026-09-11 10:19:56
is_hoarding_task = True , cfg.hoarding = 0:00:00 , TaskHoardingDuration = 0

WebUI pending order (队列中) : ['OpsiAshBeacon', 'OpsiScheduling']
time-sorted (App 的做法)     : ['OpsiScheduling', 'OpsiAshBeacon']      <-- ★ 顺序相反
WebUI waiting order (等待中) : ['Commission','Exercise','Guild','Reward','Dorm','Tactical',...]
time-sorted waiting          : ['Commission','Exercise','Guild','Reward','Dorm','Tactical',...]  <-- 一致

alive=True   运行中=['OpsiAshBeacon']  队列中=['OpsiScheduling']
alive=False  运行中=[]                 队列中=['OpsiAshBeacon','OpsiScheduling']
App（没有 alive 概念）                 队列中=['OpsiScheduling','OpsiAshBeacon']

writes attempted: []
```

### 5.4 两边差异清单

| 维度 | PC WebUI | MCP `get_scheduler_queue` |
|---|---|---|
| 配置文件 | `./config/alas.json`（`utils.py:79-83`） | 同 |
| 读取管线 | `load()` → `read_file` → `config_update` → `override` | **同**（`AzurLaneConfig(inst)` 构造即 `load()`，`config.py:132-187`） |
| 对象生命周期 | WebUI 长生命周期对象（`app_instances.py:50` `load_config`），带 `self.modified` 覆盖层（`config.py:193-194`） | 每次调用**新建**对象，`modified` 为空 |
| 启用过滤 | `Scheduler.Enable`（`config.py:322`） | `Scheduler.Enable` + 6 名单跳过（`mcp_server_sse.py:367-370`，实测空操作）→ **同** |
| 到期判定 | `next_run < now - hoarding`，**严格小于**，`now` 来自 NTP 校时（`config.py:317-319,326`） | **无**（返回全量，由 App 端 `<= now` 自行切） |
| 分桶 | **3 桶**：运行中 / 队列中 / 等待中，且依赖 `alas.alive`（`app_dashboard.py:41-51`） | **0 桶**：扁平数组 |
| 队列中排序 | **`SCHEDULER_PRIORITY` 优先级序**（`config.py:331-334`） | **`next_run` 升序**（`mcp_server_sse.py:373`） |
| 等待中排序 | `next_run` 升序（`config.py:337`） | `next_run` 升序 → **同** |
| 任务名 | `t(f"Task.{Command}.name")` → **中文**（`app_dashboard.py:67`） | 顶层 group key → **英文** |
| 时间类型 | `datetime` 对象 | `str()` 成 `"YYYY-MM-DD HH:MM:SS"` |
| 坏 NextRun | 强制塞进 pending/队列中（`config.py:324-325,338-339`） | 原样输出字符串 |

> 附带核实：所有 scheduler 组的**顶层 key 与 `Scheduler.Command` 完全一致**（探针输出 `mismatches: NONE`），
> 所以 App 用英文 key 对照中文名不会错位，只是**没有翻译**。

---

## 6. `NextRun` 的含义 / 格式 / 谁写入 / 为什么是过去时间

### 6.1 含义

**`Scheduler.NextRun` = "这个任务最早可以再次运行的时间点"（到期时间/可运行时刻），不是"计划中的未来时间"。**
- 过去时间 ⇒ **已到期/积压**，任务想跑但还没轮到；
- 未来时间 ⇒ 还没到点，等待中。

旁证：WebUI 自己判断"今天是否已完成"用的就是 `next_run.date() > now().date()`
—— `module/webui/app_event_tools.py:118-123` `_is_task_done_today()`。

### 6.2 格式

- 文件里：字符串 `"YYYY-MM-DD HH:MM:SS"`（如 `"2026-09-09 00:00:00"`）。
- 内存里：`datetime`，由 `config_update` → `parse_value()` 的 `datetime.fromisoformat(value)` 转出来
  （`module/config/utils.py:226-261`，关键在 `:256-259`）。
- `Function.__init__` 读的默认值：`DEFAULT_TIME = datetime(2023,1,1,0,0)`（`utils.py:46`，`config.py:55`）。
- `args.json` 里 `Scheduler.NextRun` 的定义默认值是 `"2020-01-01 00:00:00"`。

### 6.3 谁写入

| # | 写入者 | 位置 | 写什么 |
|---|---|---|---|
| 1 | `AzurLaneConfig.task_delay()` | `module/config/config.py:480-541`，落点 `:536 self.modified[f'{task}.Scheduler.NextRun'] = run` | **主写入者**。每个任务跑完都调它（success / failure / server_update / minute / target）。`server_update=True` 会走 `utils.py:450-473 get_server_next_update()`，取到的是 `ServerUpdate` 里的**整点时刻**（如 `00:00`） |
| 2 | `AzurLaneConfig.task_call()` | `config.py:667-695`，落点 `:686-689` | 强制调用别的任务：`NextRun = now`，同时 `Enable = True` |
| 3 | `AzurLaneConfig.override()` / `limit_next_run()` | `config.py:392-420`（`Load()` 每次都会调用，别名 `config_override` 在 `:432`） | 把"未来太远"的 `NextRun` **在内存里**改写成 `now`（通用兜底 25 小时，`:417-420`；OpsiExplore 31 天 `:409-410`；IslandPearlSell 8 天 `:415`）。**不落盘** |
| 4 | `opsi_task_delay()` | `config.py:543-665`，落点 `:573-582` | 大世界任务整体延后 |
| 5 | MCP `trigger_task` | `mcp_server_sse.py:377-385` | `cross_set Enable=True` + `NextRun=now` + `config.save()` |
| 6 | MCP `clear_scheduler_queue` | `mcp_server_sse.py:388-399` | **只把 `Enable` 置 false，完全不动 `NextRun`** → 留下一个陈旧的过去时间；以后重新启用就会显示一个很老的时刻 |
| 7 | WebUI「立即运行」按钮 | `module/webui/app_task_config.py:465-477` | 写 `NextRun = ""`。空串在 `config_update` 里被当作"用默认值"（`config_updater.py:734-738`）→ 回落到 `"2020-01-01 00:00:00"` → 立刻到期。这就是"立即运行"的实现方式 |
| 8 | 从未运行/被禁用的任务 | `args.json` 默认 | 一直是 `"2020-01-01 00:00:00"` |

**停止/暂停期间没有任何写入者**：alas 子进程已退出，`NextRun` 冻结在最后一次写入的值上。
所以"实例停止时队列看起来全是过去时间"是**设计使然**，不是 bug。

### 6.4 为什么是 `09-09 00:00`（实测本机配置）

`config/alas.json` 里 `OpsiScheduling`：

```json
"OpsiScheduling": { "Scheduler": {
    "Enable": true,
    "NextRun": "2026-09-09 00:00:00",
    "Command": "OpsiScheduling",
    "SuccessInterval": 30,
    "FailureInterval": 30,
    "ServerUpdate": "00:00" } }
```

- `ServerUpdate = "00:00"` → 它每次跑完，`task_delay(success=True, server_update=True)`
  都会算到**下一个 00:00**（`utils.py:450-473`）。所以它的 `NextRun` **永远是零点整**——
  `09-09 00:00:00` 这个"零点"指纹就是这么来的。
- 它最后一次完成是在 09-08，于是被排到 **09-09 00:00:00**。
- 之后实例一直停着（或即使运行，按优先级 `OpsiAshBeacon > OpsiScheduling`，
  前面还有 Commission/Tactical 等），没人再推进它 → 冻结在 09-09 00:00:00。
- 今天 09-11 ⇒ 它**逾期约 2 天**，含义是"这个任务想跑"。
  同样地，WebUI 把它放在「队列中」（停止时）/ 第二优先的「运行中」候选，MCP 也把它列在时间序第一位 —— **两边都有它**。

---

## 7. App 现在错在哪 / 应该改成什么

### 7.1 直接病因（按严重度）

1. **【高】客户端自己重写分桶，且少了「运行中」这一桶。**
   WebUI 是**三桶 + alive 判定**（`app_dashboard.py:41-51`），App 只有两桶。
   - 停止时：两边集合相同（已实测），但 **顺序相反**；
   - "活着但暂停"（`ProcessManager.state` 为 1 或 3，"暂停"通常就是这个）时：
     App 的「队列中」会把 WebUI 放在「运行中」的那一个**也算进去** → 多一项。
2. **【高】「队列中」的排序键错了。**
   WebUI 的队列中是**调度优先级序**（`config.py:331-334`），App 是 `next_run` 升序。
   实测同一份数据：WebUI `[OpsiAshBeacon, OpsiScheduling]`，App `[OpsiScheduling, OpsiAshBeacon]`。
   后果：**App 里「队列中」排第一的任务并不是下一个真要跑的任务**，而 WebUI 的第一位才是。
3. **【中】任务名没翻译。** MCP 返回英文 group key（`mcp_server_sse.py:372`），
   WebUI 显示的是 `Task.<Command>.name` 的中文（`app_dashboard.py:67`）。
4. **【中】`hoarding` 偏移没算。** WebUI 的到期线是 `now - TaskHoardingDuration`（`config.py:318-319`），
   且用**严格小于**；App 用 `<= now`。
   本机 `TaskHoardingDuration = 0` 所以今天不体现，但用户一旦把它设成非 0（"囤积任务 X 分钟"），
   最近 X 分钟内到期的任务在 WebUI 里是「等待中」、在 App 里却会进「队列中」——**集合级差异**。
5. **【中】时间语义/时区。** `NextRun` 是**服务器本机本地时间**（PC 时区 +08:00），
   由 NTP 校时的时间源产生（`module/config/time_source.py:148-155`；启动日志实测
   `网络时间已校准: ntp.aliyun.com, offset=0.077s`）。
   App 必须按 `yyyy-MM-dd HH:mm:ss` 以 **+08:00 本地时间**解析成 DateTime 再比较，
   不能用字符串比较、不能当 UTC、也不能用手机未校时的时钟。这是停止状态下
   "集合也不一致" 时最可能的 App 侧原因。
6. **【低】坏值兜底。** 非 datetime 的 `NextRun` 在 WebUI 里**无条件进「队列中」**
   （`config.py:324-325, 338-339`），而 `str()` 出来的原文（例如 `"None"`）在 App 里会被当字符串比较，
   极可能落到「等待中」。
7. **【低】别指望 `clear_scheduler_queue` / `trigger_task` 会规范化 `NextRun`。**
   前者只关 Enable（`mcp_server_sse.py:392-396`），会留下陈旧 `NextRun`。

### 7.2 应该改调用什么

**理想（服务端改动最小、语义 100% 对齐）**：给 MCP 加一个工具，直接返回 WebUI 的**成品分桶**，
内部就是 `AzurLaneConfig.load()` + `get_next_task()`（`config.py:189, 312`）+ `app_dashboard.py:41-51` 那 6 行：

```
get_overview_queue(instance) -> {
  "alive": bool,
  "running": [{task, command, next_run, name}],   # pending[0] if alive else []
  "pending": [...],                               # 队列中：pending[1:] if alive else pending[:]
  "waiting": [...]                                # 等待中：waiting_task
}
```

要点：
- `pending` 的顺序**必须直接沿用 `get_next_task()` 的结果**，不要重新排序；
- 附带 `name = i18n["Task"][command]["name"]`，与 `mcp_helper.py:50-58` 复用同一份 i18n；
- `alive` 取 `ProcessManager.get_manager(inst).alive`（现成用法见 `mcp_server_sse.py:205-211`）。

**若短期不改服务端**，App 至少要：
1. 用 `get_status`（`mcp_server_sse.py:205-211`）拿 `running`/`state`，判断 alive
   （`state == 1` 视为运行中 → 有「运行中」项；`state == 2` 才是真停止）；
2. 用 `get_current_running_task`（`mcp_server_sse.py:330-359`）找当前在跑的任务名，
   从「队列中」里把它剔掉，补进「运行中」；
3. 「队列中」不要按 `next_run` 排 —— 目前 MCP 的工具集里**没有暴露 `SCHEDULER_PRIORITY`**，
   所以**无法在客户端正确复现优先级序**。这正是"必须扩 MCP 工具"的技术原因；
   退一步至少也要保证「队列中」不被当成"按时间排的下一个"来展示；
4. 时间一律 `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")` 在 **Asia/Shanghai** 解析；
5. 中文名用 `list_tasks` / `get_task_help`（`display_name`），别硬编码映射表。

### 7.3 一句话给 App 的修改建议

> 「队列中/等待中」不要在客户端从 `NextRun` 反推；
> 让 MCP 直接返回 WebUI 那三步（`load()` → `get_next_task()` → `app_dashboard.py:41-51` 分桶）的结果，
> 顺序照搬 `pending_task`（优先级序）与 `waiting_task`（时间序）。

---

## 8. 证据文件索引

| 文件 | 行 | 内容 |
|---|---|---|
| `module/webui/app_overview.py` | 64-87 | 运行中/队列中/等待中 三个 scope |
| `module/webui/app_overview.py` | 245 | 每 10 秒刷新 |
| `module/webui/app_dashboard.py` | 35-98 | `alas_update_overview_task()` 全部分桶+渲染逻辑 |
| `module/webui/app_dashboard.py` | 38-51 | `load()` → `get_next_task()` → alive 分桶 |
| `module/webui/app_dashboard.py` | 67 | `t(f"Task.{command}.name")` 中文名 |
| `module/webui/app_home.py` | 311-320 | 标签页可见时立即刷新 |
| `module/webui/app_manage.py` | 125-144 | 管理页"已启用任务"列表（另一种列表） |
| `module/webui/app_task_config.py` | 465-477 | 「立即运行」写 `NextRun=""` |
| `module/webui/app_event_tools.py` | 118-123 | "今日已完成" = `next_run.date() > today` |
| `module/webui/lang.py` | 15, 37-44, 47-57, 63-73 | `t()` / `_t()` / i18n 词典加载 |
| `module/webui/app_instances.py` | 50 | `self.alas_config = load_config(config_name)` |
| `module/webui/app_shell.py` | 411-433 | state 1/2/3/4 语义 |
| `module/webui/process_manager.py` | 18, 150, 285, 604-662 | alas 跑在独立子进程；`alive` / `state` |
| `module/config/config.py` | 40-70 | `Function` |
| `module/config/config.py` | 132-187 | `__init__` → `init_task` → `load()` |
| `module/config/config.py` | 189-194 | `load()`：读盘 + `config_override()` |
| `module/config/config.py` | 293-300 | `hoarding` |
| `module/config/config.py` | 312-342 | `get_next_task()` ★ 队列计算核心 |
| `module/config/config.py` | 344-370 | `get_next()`：`pending_task[0]` 才是下一个 |
| `module/config/config.py` | 392-420 | `override()/limit_next_run()` |
| `module/config/config.py` | 432 | `config_override = override` |
| `module/config/config.py` | 480-541 | `task_delay()` → `NextRun` 主写入者 |
| `module/config/config.py` | 667-695 | `task_call()` |
| `module/config/config_updater.py` | 716-815 | `config_update()`，`:734-738` 空值/默认值兜底 |
| `module/config/config_updater.py` | 916-931 | `read_file()`（每次读盘，无缓存） |
| `module/config/utils.py` | 46 | `DEFAULT_TIME` |
| `module/config/utils.py` | 79-83 | `filepath_config` |
| `module/config/utils.py` | 90-105 | `read_file` = `json.loads`（不做时间转换） |
| `module/config/utils.py` | 226-261 | `parse_value` → `datetime.fromisoformat` |
| `module/config/utils.py` | 450-473 | `get_server_next_update`（为什么是整点） |
| `module/config/config_manual.py` | 53-80, 107-136 | 默认优先级表 / `SCHEDULER_PRIORITY` |
| `module/config/time_source.py` | 148-155 | `now()`（NTP 校时） |
| `module/config/mcp_helper.py` | 35-58 | MCP 侧中文名 `display_name` |
| `module/config/i18n/zh-CN.json` | 157-158, 161-162, 285-286 | `Task.*.name` 中文 |
| `module/config/i18n/zh-CN.json` | 6271-6274 | 运行中/队列中/等待中/无任务 |
| `module/config/i18n/zh-CN.json` | 879-882 | 「囤积任务 X 分钟」说明 |
| `mcp_server_sse.py` | 40 | `McpConfigHelper()`（zh-CN） |
| `mcp_server_sse.py` | 168-172 | `get_scheduler_queue` schema/描述 |
| `mcp_server_sse.py` | 205-211 | `get_status`（running / state） |
| `mcp_server_sse.py` | 330-359 | `get_current_running_task` |
| `mcp_server_sse.py` | 362-374 | `get_scheduler_queue` ★ 实现 |
| `mcp_server_sse.py` | 377-399 | `trigger_task` / `clear_scheduler_queue` |
| `mcp_server_sse.py` | 479 | 工具注册表 |
| `module/webui/app.py` | 380-395 | MCP mount 在 `/mcp`（同一进程） |
| `module/logger.py` | 397 | `os.chdir` 到项目根（相对路径基准） |

### 探针（放在仓库外，未触碰 `D:\Tools\AzurPilot`）

- `D:\Temp\alas_probe.py` —— WebUI 分桶 vs MCP 输出 vs App 规则
- `D:\Temp\alas_probe2.py` —— 原始 JSON vs `config.data`、排序对比、alive 差一项

两个脚本都先 patch 掉 `deploy.atomic.atomic_write` / `module.config.utils.write_file`，
实测末尾 `writes attempted: []`。
