# AzurPilot 手机端 App 接入调研报告

> 调研对象：`D:\Tools\AzurPilot`（只读分析，未修改任何文件）
> 运行形态：`gui.py` 拉起 WebUI（`0.0.0.0:25548`，`config/deploy.yaml:202,209`），WebUI 再以 `multiprocessing.Process` 拉起各实例 worker（`alas`）。
> 关键结论前提：手机 App 只能走 HTTP/WS，读不到 PC 文件。**因此下文所有"读文件"的方案都必须包一层新接口。**

---

## 0. 结论速览

| 需求 | 现状 | 是否可直接用 | 推荐做法 |
|---|---|---|---|
| 实时日志 | 日志同时进**磁盘文件** + **跨进程 Queue → 内存环形缓冲 → PyWebIO WebSocket** | ❌ 无任何 HTTP/SSE 日志接口 | 新增 `GET /api/log`（读 `log/<日期>_<实例>.txt` 尾部）+ `GET /api/log/stream`（SSE 增量） |
| 资源数据（油/物资/钻/PT/…） | 在 `config/<实例>.json` 的 `Dashboard` 对象里 | ⚠️ MCP 工具 `get_resources` 已有；HTTP 无 | 新增 `GET /api/resources`（复用 `McpConfigHelper.get_dashboard_resources`） |
| 任务控制（启动/停止/切配置） | `ProcessManager.start/stop/stop_by_user` + `AzurLaneConfig.cross_set/save` | ⚠️ MCP 工具有；HTTP 无 | 新增 `POST /api/instance/start|stop`、`POST /api/task/trigger`、`POST /api/config/set` |
| 实时画面 + 点按操控 | `/ws/live_screenshot`、`/ws/live_control` **已存在且 LAN 可直连** | ✅ **可直接用** | 手机端实现 H.264 解码（WebCodecs 或 MediaCodec）+ 发送控制 JSON |
| 当前任务 / 进度 | `ProcessManager.state`；`config/daily_summary.db` 的 `daily_summary_task_runs`；日志里的 `调度器: 开始任务` | ⚠️ 数据齐全，接口没有 | 新增 `GET /api/status`（聚合三者） |
| `/api/deploy/settings` 403 | `is_local_request()` 双重校验回环地址 | 设计如此，**LAN 永远 403** | 不要试图绕过；需要的字段自己新开接口 |
| 升级保留自定义代码 | **无插件/补丁机制**，`git reset --hard` 会还原被跟踪文件 | ❌ | 新代码放**独立进程 + 新文件**，不改造被跟踪文件 |

---

## 1. 实时日志

### 1.1 日志的诞生：一条日志走两条路

`module/logger.py` 定义了全局 `logger = logging.getLogger('alas')`（`logger.py:369`）。

**路径 A —— 磁盘文件（手机 App 最实用的数据源）**

| 位置 | 说明 |
|---|---|
| `module/logger.py:121-148` | `RichTimedRotatingHandler`，格式化模板固定为 `"%(asctime)s.%(msecs)03d \| %(levelname)s \| %(message)s"` |
| `module/logger.py:209-262` | `doRollover()`：把 `log/alas.txt` 换成 `log/2026-09-11_alas.txt`（`newPath = 日期 + "_" + path.name`，`logger.py:235-238`），并维护 `self.log_file` |
| `module/logger.py:444-459` | `set_file_logger(name)`：`log_dir = Path("./log")`，`log_file = log_dir.joinpath(f"{pname}.txt" if name=="gui" else f"{name}.txt")`，`when="midnight"` 每日轮转 |
| `module/webui/process_manager.py:724` | worker 进程启动时调用 `set_file_logger(name=config_name)` → 实例 `alas` 写 `log/<日期>_alas.txt` |
| `module/logger.py:403-417` | 另一条兜底：`_set_file_logger()` 写 `./log/{今天}_{name}.txt` |

实测磁盘现状（`D:\Tools\AzurPilot\log\`）：

```
2026-09-11_alas.txt   3,612,589 B   ← 实例 alas 的当日日志（★手机端应读这个）
2026-09-11_gui.txt            0 B   ← WebUI 自身进程日志
2026-09-10_alas.txt  22,971,525 B
bak/2026-09-08_alas.zip             ← 过期日志压缩备份
```

文件内容格式（实测首行）：

```
2026-09-11 00:00:16.461 | INFO | [设备-截图] 截图间隔设置为 0.2s
2026-09-11 00:00:22.068 | INFO | [设备-控制] 点击 ( 653,  626) @ AUTO_SEARCH_REWARD
```

> 注意：文件里含 Rich 的框线字符（`── 清除问号 ──`）和 ANSI-free 纯文本，**没有任何 HTML/颜色标记**，手机端直接按行渲染即可。

**路径 B —— WebUI 面板（内存 + WebSocket）**

```
worker 子进程                         WebUI 主进程
─────────────                        ─────────────
logger.addHandler(                    ProcessManager.__init__
  RichRenderableHandler(func=q.put))    self._renderable_queue = State.manager.Queue()   # process_manager.py:64
        │  logger.py:468-493           self.renderables = []  (上限 400，超了砍 80)      # :65-67
        ▼                                      ▲
  q.put(ConsoleRenderable) ──跨进程Queue──► _thread_log_queue_handler()                 # :592-601
                                               renderables.append(log)
                                               │
                                               ▼
                                     RichLog.put_log(pm)  # widgets.py:236-256（PyWebIO 协程）
                                       render_many() → console.export_html()            # widgets.py:131-148
                                       run_js("$('#pywebio-scope-log>div').append(html)")# widgets.py:150-160
                                               │  PyWebIO 自身 WebSocket
                                               ▼
                                          浏览器 DOM 追加
```

关键调用点：

- `module/webui/process_manager.py:731` — `set_func_logger(func=q.put)`（在 **worker 进程内**执行）
- `module/webui/process_manager.py:162,168-175` — `start_log_queue_handler()`
- `module/webui/app_overview.py:250` — `self.task_handler.add(log.put_log(self.alas), 0.25, True)`（**0.25 秒轮询一次内存列表**，不是服务端主动 push）
- `module/webui/app_overview.py:379-380` — daemon 总览页同样使用
- `module/webui/process_manager.py:617-662` — `state` 属性会把最后 8 条 renderable 用 `Console(no_color=True).capture()` **渲染成纯文本**来判定"手动停止/更新/完成"，这是把 Rich 对象转文本的现成范例

### 1.2 有没有现成的纯 HTTP / SSE 日志接口？

**没有。** 全项目 `api_routes`（`module/webui/api.py:1785-1804`）里与推送相关的只有两个 SSE，都与日志无关：

- `GET /api/notify_stream`（`api.py:1421-1437`）—— 通知推送，数据源是内存里的 `_notification_queue = asyncio.Queue()`（`api.py:1411`），写入靠 `POST /api/notify`（`api.py:1414-1418`，被 `module/notify/notify.py:103-134` 以 `http://127.0.0.1:25548/api/notify` 调用）。**这个 SSE 是很好的"新增日志 SSE"的代码模板。**
- `GET /api/launcher/stream`（`api.py:1469-1497`）—— 只允许本机。

### 1.3 手机 App 的日志方案

**唯一可行的、零依赖改造的做法：新增接口读磁盘文件。**

```
GET /api/log?instance=alas&lines=200          # 尾部 N 行
GET /api/log/stream?instance=alas             # SSE，按 size/mtime 增量 tail
```

- 数据源：`D:\Tools\AzurPilot\log\{YYYY-MM-DD}_{instance}.txt`
  （今天=运行时日期；跨零点后文件名会变，需按 `ProcessManager.log_file` 或直接扫 `log/` 取最新）
- 实例名来源：`module/config/utils.py:203-223` 的 `alas_instance()`（本机现在返回 `['alas']`）
- 参考实现：`mcp_server_sse.py:253-277`（`_tool_get_recent_logs`）**已经就是这么干的**（读 `./log/{今天}_{实例}.txt`，取最后 N 行），可直接抄。
- 增量 tail 的锚点：文件名 mtime + size；SSE keepalive 抄 `api.py:1431` 的 `yield ": keepalive\n\n"`。

**若想和 WebUI 面板"像素级一致"**（含 400 条环形缓冲、Rich 高亮），则要走内存：

```python
pm = ProcessManager.get_manager("alas")
texts = []
console = Console(no_color=True)          # 与 process_manager.py:626 相同手法
for r in pm.renderables:                  # 最多 400 条
    with console.capture() as cap:
        console.print(r)
    texts.append(cap.get())
```

但这只覆盖 **worker 启动之后**的日志，且上限 400 条；历史日志仍必须读文件。**建议以文件为主、内存为辅。**

---

## 2. 资源数据（石油 / 物资 / 钻石 / PT / 魔方 / 行动力 / 黄币 / 紫币 / 核心 / 勋章 / 功勋 / 舰队币）

### 2.1 唯一的"当前值"真相源：`config/<实例>.json` 的 `Dashboard` 对象

`module/log_res/log_res.py:38-94` 是所有资源写入的唯一入口（`LogRes(config).Oil = ...`）：

```python
def __setattr__(self, key, value):
    if key in self.groups:                       # groups 来自 dashboard.yaml
        _key_group = f'Dashboard.{key}'
        if isinstance(value, int):               # 单值资源
            self.config.modified[_key_group + '.Value']  = value
            self.config.modified[_key_group + '.Record'] = datetime.now().replace(microsecond=0)
        elif isinstance(value, dict):            # {'Value','Limit'} 或 {'Value','Total'}
            for value_name, _value in value.items():
                self.config.modified[_key_group + f'.{value_name}'] = _value
            ...
```

- 资源清单定义：`module/config/argument/dashboard.yaml:8-20`
  `Oil, Coin, Gem, Pt, Cube, ActionPoint, YellowCoin, PurpleCoin, Core, Medal, Merit, GuildCoin`
- 读取：`module/log_res/log_res.py:122-127`

```python
def group(self, name):
    return deep_get(self.config.data, f'Dashboard.{name}')

@cached_property
def groups(self) -> dict:
    from module.config.utils import read_file, filepath_argument
    return deep_get(d=read_file(filepath_argument("dashboard")), keys='Dashboard')
# filepath_argument("dashboard") → ./module/config/argument/dashboard.yaml  (utils.py:68-69)
```

**实测数据结构**（`D:\Tools\AzurPilot\config\alas.json`，第 2 行起）：

```json
"Dashboard": {
  "Oil":         { "Value": 2517,    "Limit": 11200, "Color": "^000000", "Record": "2026-09-11 02:18:41" },
  "Coin":        { "Value": 18981,   "Limit": 68700, "Color": "^FFAA33", "Record": "2026-09-11 02:18:40" },
  "Gem":         { "Value": 85,                        "Color": "^FF3333", "Record": "2026-09-10 19:01:54" },
  "Pt":          { "Value": 19120,                     "Color": "^00BFFF", "Record": "2026-09-11 02:18:41" },
  "Cube":        { "Value": 86,                        "Color": "^33FFFF", "Record": "2026-09-11 00:04:06" },
  "ActionPoint": { "Value": 45,      "Total": 3145,    "Color": "^0000FF", "Record": "2026-09-11 01:38:40" },
  "YellowCoin":  { "Value": 115549,                    "Color": "^FF8800", "Record": "2026-09-11 01:38:31" },
  "PurpleCoin":  { "Value": 2452,                      "Color": "^7700BB", "Record": "2026-09-11 01:34:07" },
  "Core":        { "Value": 0, "Color": "^AAAAAA", "Record": "2020-01-01 00:00:00" },
  "Medal":       { "Value": 0, ... }, "Merit": { "Value": 0, ... }, "GuildCoin": { "Value": 0, ... },
  "Storage": { "Storage": {} }
}
```

> `Record == "2020-01-01 00:00:00"` = 从未采集到（WebUI 会显示成 `None`）。

### 2.2 各资源的写入点（哪个任务 OCR 出来写进去的）

| 资源 | 文件:行 | 代码 |
|---|---|---|
| Oil / Coin（**含 Limit**） | `module/campaign/campaign_status.py:116-122`、`174-180` | `_coin = {'Value': self._get_num(OCR_COIN, ...), 'Limit': self._get_num(OCR_COIN_LIMIT, ...)}` → `LogRes(self.config).Coin = _coin` |
| Oil / ActionPoint（大世界） | `module/os_handler/action_point.py:202-214` | `LogRes(self.config).Oil = oil`；`LogRes(self.config).ActionPoint = {'Value': current, 'Total': total}`；`total = current + Σ(行动力箱×系数)` |
| YellowCoin / PurpleCoin | `module/os_handler/os_status.py:170,180` | `LogRes(self.config).YellowCoin = yellow_coins` |
| Coin/Gem/Medal/Merit/GuildCoin/Core | `module/shop/shop_status.py:56,69,82,95,108,121` | 商店 OCR |
| Cube（魔方） | `module/gacha/gacha_reward.py:133,336` | `LogRes(self.config).Cube = self.build_cube_count` |
| Pt（活动 PT） | `module/campaign/campaign_status.py:80,88`、`module/raid/raid.py:555,560`、`module/coalition/coalition.py:129` | `LogRes(self.config).Pt = pt` |

**`/ 11200` 这种上限值从哪来**（`module/webui/app_dashboard.py:109-135`）：

```python
value = str(group["Value"])
if "Limit" in group.keys():
    value_limit = f" / {group['Limit']}"                       # Oil → " / 11200"、Coin → " / 68700"
elif "Total" in group.keys():
    value_total = f" ({group['Total']})"                       # ActionPoint → " (3145)"
elif group_name == "Pt":
    value_limit = " / " + re.sub(r'[,.\'"，。]', "", str(
        deep_get(self.alas_config.data, "EventGeneral.EventGeneral.PtLimit")))   # 用户配置，默认 0 → 不显示
```

- `Oil.Limit` / `Coin.Limit` 是**从游戏 UI 用 OCR 读出来的**（`OCR_OIL_LIMIT` / `OCR_COIN_LIMIT`），不是常量；
- `ActionPoint.Total` = 当前行动力 + 未开箱行动力（`action_point.py:205-208`）；
- `Pt` 的上限来自配置项 `EventGeneral.EventGeneral.PtLimit`（`module/config/argument/argument.yaml:623` 默认 `0`，`module/webui/app_event_tools.py:85` 会自动写入探测值）。
- 渲染时的 scope 命名在 `module/webui/dashboard_utils.py:14-83`（只管 scope id，**不含任何资源数据**）。

### 2.3 历史趋势 / 快照（SQLite）

| 数据库 | 表 | 代码 |
|---|---|---|
| `config/azurstats_local.db` | `resource_snapshots(id, instance, ts, oil, coin, gem, pt, cube, core, medal, merit, guild_coin, action_point, yellow_coin, purple_coin)` | 建表 `module/statistics/resource_stats.py:52-81`；写入 `:84-138`；查询 `:141-175`；区间聚合 `:201-303`；列名映射 `:36-49` |
| `config/azurstats_local.db` | `opsi_items` | `module/statistics/azurstats.py:141,194` |
| `config/cl1_data.db` | `cl1_data(instance, month, data_json, encrypted_blob)` | `module/statistics/cl1_database.py:115-160`；`data_json` 内嵌 `ap_snapshots` / `coins_snapshots`；写快照 `:683`（AP）、`:779`（黄币） |
| `config/daily_summary.db` | `daily_summary_task_runs(instance, task, started_at, finished_at, status, duration_seconds)` 等 5 张表 | `module/statistics/daily_summary_store.py:15,54-125` |

写快照的触发链：`LogRes.__setattr__` → `_record_all_resource_snapshot()`（`log_res.py:96-120`）→ `record_resource_snapshot()`（`resource_stats.py:84`）；`ActionPoint`/`YellowCoin` 还会额外写 `cl1_data.db`（`log_res.py:50-58,71-85`）。

对外读时间线：`module/statistics/opsi_month.py:228-249` 的 `get_resource_timeline(instance_name, limit)`。

### 2.4 已经有了哪些"可直接用"的资源接口

1. **HTTP（仅两个，且不需要本机）** — `module/webui/api.py:57-75`：
   - `GET /api/cl1_stats?instance=alas` → `{"success":true,"data":{...大世界月统计...}}`
   - `GET /api/ap_timeline?instance=alas` → `{"success":true,"data":[{ts, ap, source}, ...]}`
   - 现成的消费范例：`module/webui/obs_overlay.html:419,422` 用 `fetch()` 拉这两个接口画 OBS 悬浮层 —— **这就是一个纯 HTTP 客户端，可直接照抄给手机端**
2. **MCP（已有，但走 MCP 协议）** — `mcp_server_sse.py:225-229` 的 `get_resources` 工具：
   ```python
   config = AzurLaneConfig(inst)
   res = helper.get_dashboard_resources(config.data)   # module/config/mcp_helper.py:102-135
   ```
   返回 `{ "Oil": {"label":"石油","value":2517,"limit":11200,"last_update":"..."}, ... }` —— **正是手机 App 想要的结构，`get_dashboard_resources` 可以直接复用**。

### 2.5 结论

- ❌ **不存在** `GET /api/resources` 这类纯 HTTP 资源接口。
- ✅ `McpConfigHelper.get_dashboard_resources()`（`module/config/mcp_helper.py:102-135`）**已经把数据整形好了**，新增接口只需 5 行：

```python
async def api_resources(request):
    from module.config.config import AzurLaneConfig
    from module.config.mcp_helper import McpConfigHelper
    inst = request.query_params.get("instance", DEFAULT_CONFIG_NAME)
    cfg  = AzurLaneConfig(inst)
    return JSONResponse({"success": True, "data": McpConfigHelper().get_dashboard_resources(cfg.data)})
```

---

## 3. 任务控制

### 3.1 进程级：启动 / 停止

`module/webui/process_manager.py`：

| 方法 | 行 | 语义 |
|---|---|---|
| `ProcessManager.get_manager(name)` | `664-678` | 取（或建）某实例的管理器 |
| `.start(func: str|None, ev=None)` | `115-166` | 起 `multiprocessing.Process` 跑 `run_process`；`func=None` 时自动 `get_config_mod(name)`（`142-143`）；**已在跑则直接 return**（`132-133`） |
| `.stop()` | `177-185` | 强杀 worker 进程树（更新/清理/MCP 用） |
| `.stop_by_user(action)` | `187-212` | WebUI 停止按钮专用，会按配置执行收尾动作（返回主页/关游戏/关模拟器） |
| `.alive` | `603-614` | 是否存活 |
| `.state` | `616-662` | `1=运行 2=停止 3=异常 4=更新中` |
| `.running_instances()` | `792-798` | 当前所有在跑的实例 |
| `get_manager().renderables` | `65` | 日志环形缓冲（见 §1） |

WebUI 里的实际调用（可直接抄）：

```python
# module/webui/app_overview.py:312-323
switch_scheduler = BinarySwitchButton(
    label_on=t("Gui.Button.Stop"), label_off=t("Gui.Button.Start"),
    onclick_on=lambda: self.alas.stop_by_user(
        self.alas_config.Optimization_WhenSchedulerStopped),   # stay_there/goto_main/close_game/close_emulator
    onclick_off=lambda: self.alas.start(task),
    get_state=lambda: self.alas.alive, ...)

# module/webui/app_task_config.py:553-554
def _alas_start(self):
    self.alas.start(None, updater.event)
```

停止后的收尾动作枚举见 `module/webui/scheduler_stop.py:5-10` 与 `:48-93`。

### 3.2 配置级：读 / 写 / 切实例

| 入口 | 文件:行 |
|---|---|
| `read_file(path)` / `write_file(path, data)`（底层，JSON/YAML 原子读写） | `module/config/utils.py:90-139` |
| `ConfigUpdater.read_file(config_name)` / `.write_file(config_name, data, mod_name)` | `module/config/config_updater.py:916-931, 933-943` |
| `filepath_config(filename, mod_name)` → `./config/<name>.json` | `module/config/utils.py:79-83` |
| `alas_instance()` → 所有实例名 | `module/config/utils.py:203-223` |
| `AzurLaneConfig(name)` / `.load()` | `module/config/config.py:132-187 / 189-194` |
| `.cross_set(path, value)`（**改任意配置项，如 `Commission.Scheduler.Enable`**） | `module/config/config.py:469-476` |
| `.save()` / `.update()`（把 `modified` 落盘） | `module/config/config.py:372-390` |
| `get_next_task()`（算出 pending/waiting 队列） | `module/config/config.py:312` |
| WebUI 的保存路径（含校验、联动回调） | `module/webui/app_task_config.py:599-712`（`_save_config` → `config_updater.write_file`，`:709`） |
| 切换实例（WebUI 语义） | `module/webui/app_instances.py:39-58`（`ui_alas(config_name)`：`self.alas = ProcessManager.get_manager(name)`，`self.alas_config = load_config(name)`） |

### 3.3 "暂停某个任务"怎么做

**没有 pause 语义**。等价操作是**改调度器开关**（MCP 里已经这么实现，`mcp_server_sse.py:388-399`）：

```python
from module.config.config import AzurLaneConfig
config = AzurLaneConfig("alas")
config.cross_set(f"{task}.Scheduler.Enable", False)   # 暂停/取消该任务调度
config.cross_set(f"{task}.Scheduler.NextRun", str(current_time()))  # 立即执行
config.save()
```

### 3.4 "查看当前运行的任务和进度"

三个数据源，都齐了但没有 HTTP：

1. **调度队列**（running / pending / waiting）— `module/webui/app_dashboard.py:35-98`
   ```python
   self.alas_config.load(); self.alas_config.get_next_task()
   running = self.alas_config.pending_task[:1] if self.alas.alive else []
   pending = self.alas_config.pending_task[1:]
   waiting = self.alas_config.waiting_task
   # 每项是一个 Function 对象：task.command / task.next_run
   ```
2. **当前正在执行的具体任务** — `config/daily_summary.db`（实测有 2919 条记录）：
   ```sql
   SELECT task, started_at FROM daily_summary_task_runs
   WHERE instance='alas' AND finished_at IS NULL ORDER BY id DESC LIMIT 1;
   ```
   写入点：`alas.py:2098-2112`（`_record_daily_summary_task_start/finish`）、`module/statistics/daily_summary_store.py:221-275`；`status ∈ {success, recoverable, ...}`。
   实测样例：`{'id': 2918, 'instance': 'alas', 'task': 'Commission', 'started_at': '2026-09-10 21:17:08', 'finished_at': '2026-09-10 21:17:26', 'status': 'success', 'duration_seconds': 18.12}`
3. **日志兜底**（MCP 的做法）— 在日志里正则找最后一条 `调度器: 开始任务 \`XXX\``：
   写入点 `alas.py:2089`，解析实现 `mcp_server_sse.py:330-359`，正则 `r"调度器: 开始任务\s*[`'\" ](.*?)[`'\" ]"`。

⚠️ **没有"百分比进度"**。全局搜 `progress` 只有大世界探索进度（`module/os/tasks/explore.py:64-129` 写进配置项 `OpsiExplore_ExploreProgress`）和舰船经验进度（`module/statistics/ship_exp_stats.py:310-386`，`app_stat_ship.py`/`app_stat_opsi.py` 渲染）。**任务级进度需要自己新造。**

### 3.5 有没有已经暴露成 HTTP 的？

`api_routes`（`module/webui/api.py:1785-1804`）中**与任务控制相关的只有 launcher 那几个，且全部 `is_local_request` 本机限制**（`/api/launcher/startup`、`/stream`、`/report`、`trusted-login`）。

**唯一的现成远程控制面是 MCP（默认随 WebUI 启动，挂在 `/mcp` 下）**：

- `module/webui/app.py:380,395` — `from mcp_server_sse import app as mcp_app` … `application.mount("/mcp", mcp_app)`
- `mcp_server_sse.py:499-569` — 纯 ASGI：`/mcp/sse`（SSE 建流）+ `/mcp/messages`（POST JSON-RPC），**CORS `allow_origins=["*"]`**（`:564-568`）
- 独立运行也可：`uv run python mcp_server_sse.py` → `host="0.0.0.0", port=22268`（`mcp_server_sse.py:571-574`）
- 18 个工具（`mcp_server_sse.py:47-198`）：`list_instances / get_status / list_tasks / get_task_help / get_resources / get_config / update_config / get_recent_logs / start_instance / stop_instance / get_screenshot / get_current_running_task / get_scheduler_queue / trigger_task / clear_scheduler_queue / restart_emulator / restart_adb / update_alas`

> 手机 App 若能实现 MCP-over-SSE 客户端（一次 `GET /mcp/sse` 拿 `session_id`，再 `POST /mcp/messages` 发 JSON-RPC），**Q1/Q2/Q3 几乎全部解决，且零改造**。代价是要实现 JSON-RPC 2.0 + 处理 SSE 消息端点，比"新增 REST 接口"复杂。

---

## 4. 实时截图 / 远程操控

### 4.1 路由

`module/webui/api.py:1802-1803`：

```python
WebSocketRoute("/ws/live_screenshot", ws_live_screenshot),
WebSocketRoute("/ws/live_control",    ws_live_control),
```

**两者都没有任何鉴权**（既不检查密码也不检查 `is_local_request`）——LAN 直连即可。

### 4.2 `/ws/live_screenshot` 协议

客户端连接（参数见 `api.py:1037-1044`，JS 侧构造见 `assets/gui/js/alas-utils.js:871-889`）：

```
ws://192.168.1.100:25548/ws/live_screenshot
   ?instance=alas&codec=h264&mode=auto&fps=60&width=640&bitrate_scale=1.00
```
| 参数 | 默认 | 范围 |
|---|---|---|
| `instance` | `ap`(DEFAULT_CONFIG_NAME) | 实例名 |
| `mode` | `auto` | `auto` / `scrcpy` / `screenshot` |
| `fps` | 60 | 15–240 |
| `width` | 640 | 320–1280 |
| `bitrate_scale` | 1.0 | 0.25–1.5 |

**服务端 → 客户端**：

1. 一条 **JSON 文本** `ready`（三种模式字段不同）：

```jsonc
// 模式 ws-scrcpy（首选，api.py:1125-1138）
{"type":"ready","mode":"ws-scrcpy","codec":"h264","format":"raw_h264",
 "codec_string":"avc1.640028","description":null,
 "width":1280,"height":720,"fps":60,"bitrate_mode":"scrcpy",
 "maxrate":"6144k","bitrate_scale":1.0}

// 模式 scrcpy 兜底（api.py:1184-1197）：多一个 "description"(AVCC)
// 模式 screenshot（api.py:1231-1242）：mime = 'video/mp4; codecs="avc1.42E01E"'，format 字段缺失
```

2. 之后持续推送**二进制帧**（WebSocket binary）：
   - `format == "raw_h264"` → **裸 H.264 Annex-B 码流**（无时间戳、无封装），JS 侧用 WebCodecs `VideoDecoder` 解（`alas-utils.js:550-644`）；偶尔会插入一条 JSON `{"type":"resize","width":W,"height":H}`（`api.py:1154-1158`）
   - screenshot 模式 → **fMP4 分片**（ffmpeg 现编），JS 侧丢给 `MediaSource`（`alas-utils.js:808+`）
3. 出错时：`{"type":"error","message":"..."}`（`api.py:1063,1071-1074,1313`）

**服务端内部实现层级**（`ws_live_screenshot` → `_ws_live_scrcpy` → …）：

| 层级 | 行 | 说明 |
|---|---|---|
| `ws_live_screenshot` | `1027-1078` | 解析参数；`DEMO=1` 直接拒绝；`mode=auto` 先试 scrcpy 失败再回退 |
| `_ws_live_ws_scrcpy` | `1093-1162` | `LiveWsScrcpySession`：adb push `ws-scrcpy-server-v1.19-ws7.jar` → `app_process` 起设备端 WS server → `adb forward` → 代理 |
| `_ws_live_raw_scrcpy` | `1165-1213` | `LiveScrcpySession`：自己跑 scrcpy-server，直读 abstract socket 的裸 H264 |
| `_ws_live_screenshot_fallback` | `1216-1332` | `device.screenshot()` 循环 + **ffmpeg** 编码（`_get_ffmpeg_path`，`api.py:91-99`） |

### 4.3 `/ws/live_control` 协议

连接：`ws://host:25548/ws/live_control?instance=alas`（`api.py:1344`）

**客户端 → 服务端：JSON 文本**（`api.py:1360-1400`）：

| action | 载荷 | 服务端动作 |
|---|---|---|
| `tap` | `{"type":"tap","x":653,"y":626}` | `target.tap(x,y)` |
| `drag` | `{"type":"drag","start":{"x":..,"y":..},"end":{"x":..,"y":..},"duration_ms":220}` | `target.drag(...)` |
| `key` | `{"type":"key","keycode":4}` 或 `{"type":"key","key":"Backspace"}` | `_key_to_android_keycode()`（`api.py:969-987`）映射后 `keycode()` |
| `text` | `{"type":"text","text":"abc"}` | `target.text()` |
| `back` / `home` / `app_switch` | `{"type":"back"}` | `CONTROL_ACTION_KEYCODES`（`api.py:990-994`） |

**服务端 → 客户端**：只有错误 `{"type":"error","message":"..."}`（`api.py:1365,1400,1407`），**正常操作无任何回执**。

控制目标的优先级（`api.py:1347-1357`）：
`LiveWsScrcpySession`（若该实例已有预览）→ `LiveScrcpySession` → **`LiveControlDevice` 兜底**。

`LiveControlDevice`（`api.py:943-966`）直接用 **adb shell input**，不依赖 scrcpy：

```python
self.connection.adb_shell(["input", "tap", int(x), int(y)])
self.connection.adb_shell(["input", "swipe", x1, y1, x2, y2, duration_ms])
self.connection.adb_shell(["input", "keyevent", int(keycode)])
self.connection.adb_shell(["input", "text", text])
```

> ⚠️ **坐标契约**：`LiveWsScrcpySession._scale_point`（`api.py:638-642`）与 `LiveScrcpySession.scale_point`（`:887-891`）都假定**客户端传来的坐标是 1280×720 基准**，再等比缩放到真实分辨率。手机端必须把触摸点换算到 1280×720 逻辑坐标。

### 4.4 手机 App 可用性评估

✅ **完全可行**，且是**已存在、可直接用**的功能。前端实现范例就是 `assets/gui/js/alas-utils.js:550-1174`（WebCodecs 解码 → canvas 渲染 → pointerdown/up 换算成 tap/drag → 键盘事件转 key/text）。

**前置条件**：

1. **ADB 必须已连通模拟器/手机**——所有三条链路（ws-scrcpy / scrcpy / adb input）都走 `Connection(config)` → adb。断连则 `ready` 都不会发，直接 `{"type":"error"}`。
2. **ws-scrcpy 不依赖外部安装**：jar 已随仓库分发 —— `bin/scrcpy/ws-scrcpy-server-v1.19-ws7.jar`（常量定义 `api.py:386-392`：`WS_SCRCPY_PORT=8886`、`WS_SCRCPY_PACKAGE=com.genymobile.scrcpy.Server`）。启动时会 `adb push` 到 `/data/local/tmp/`。
3. **`deploy.yaml` 里没有任何 scrcpy 开关**（已 grep 确认，`config/deploy.yaml` 无 `scrcpy` 字样）→ **不需要在 deploy.yaml 开启任何东西**。scrcpy 版本/路径在 `module/config/config_manual.py:196-197`（`SCRCPY_FILEPATH_LOCAL='./bin/scrcpy/scrcpy-server-v1.20.jar'`），且 jar 也在 `bin/scrcpy/` 里。
4. **兜底截图模式需要 ffmpeg**：`_get_ffmpeg_path()`（`api.py:91-99`）= `shutil.which("ffmpeg")` → 退回 `imageio_ffmpeg.get_ffmpeg_exe()`；找不到就返回 error 并 close。
5. **`DEMO=1` 环境变量会禁用预览与控制**（`api.py:1029-1035`、`1337-1343`）。
6. 截图方式若配成 `scrcpy` 会与预览抢同一条视频流（参考 `module/base/debug_clip.py:451-452` 的告警）；预览本身用的是独立 jar + 独立端口，实践上 `mode=auto` 会先试 ws-scrcpy。
7. **编码格式**：只有 H.264。Android 端建议 `MediaCodec`（`video/avc`）+ 从 `codec_string`（如 `avc1.640028`）+ `description` 构造 csd-0/csd-1；或直接用 ExoPlayer/Media3 的低延迟配置。

---

## 5. `api_routes` 全量清单 与 鉴权规则

### 5.1 `module/webui/api.py:1785-1804`（完整原文）

```python
api_routes = [
    Route("/api/cl1_stats", api_cl1_stats),
    Route("/api/ap_timeline", api_ap_timeline),
    Route("/api/notify", api_notify, methods=["POST"]),
    Route("/api/notify_stream", api_notify_stream),
    Route("/api/launcher/status", api_launcher_status),
    Route("/api/launcher/startup", api_launcher_startup, methods=["POST"]),
    Route("/api/launcher/stream", api_launcher_stream),
    Route("/api/launcher/report", api_launcher_report, methods=["POST"]),
    Route("/api/launcher/trusted-login", api_launcher_trusted_login, methods=["POST"]),
    Route("/launcher-login", launcher_login, methods=["GET"]),
    Route("/api/deploy/settings", api_deploy_settings),
    Route("/api/deploy/settings", api_deploy_settings_save, methods=["POST"]),
    Route("/api/deploy/startup-run", api_deploy_startup_run),
    Route("/api/deploy/startup-run", api_deploy_startup_run_save, methods=["POST"]),
    Route("/api/import_legacy_upload", api_import_legacy_upload, methods=["POST"]),
    Route("/obs", serve_obs_overlay),
    WebSocketRoute("/ws/live_screenshot", ws_live_screenshot),
    WebSocketRoute("/ws/live_control", ws_live_control),
]
```

挂载位置：`module/webui/fastapi.py:304-311`（`routes.extend(api_routes)`），**注册在 Starlette 路由表里，`asgi_app` 未加任何全局鉴权中间件**（`fastapi.py:313-324` 只有 GZip 和 Header 中间件）。WebUI 的密码只在 **PyWebIO 页面会话**里校验（`module/webui/app.py:347-357` 的 `login(key, ...)`），**对 `/api/*`、`/ws/*`、`/mcp/*` 完全不生效**。

### 5.2 每个端点的鉴权矩阵

| 路由 | 鉴权 | 代码 |
|---|---|---|
| `/api/cl1_stats`、`/api/ap_timeline` | **无** | `api.py:57-75` |
| `/api/notify` (POST)、`/api/notify_stream` (SSE) | **无** | `api.py:1414-1437` |
| `/api/launcher/status` | 无拒绝，但返回体里带 `request_local` 布尔 | `api.py:1440-1443` + `launcher.py:58-68` |
| `/api/launcher/startup` (POST) | 非本机 → 403 | `api.py:1448-1452` |
| `/api/launcher/stream` (SSE) | 非本机 → 403 | `api.py:1471-1475` |
| `/api/launcher/report` (POST) | 非本机 → 403 | `api.py:1502-1506` |
| `/api/launcher/trusted-login` (POST) | 非本机 → 403；再校验 `x-webui-launcher-secret` + 限频 | `api.py:1537-1560` |
| `/launcher-login` (GET) | 非本机 → 403；再校验一次性 `token` | `api.py:1573-1583`，拒绝页 `:1607-1612` |
| `/api/deploy/settings` GET/POST、`/api/deploy/startup-run` GET/POST | 非本机 → **403** | `api.py:1617-1621, 1632-1636, 1658-1662, 1677-1681` |
| `/api/import_legacy_upload` (POST) | **无鉴权**（可直接往 `config/`、`log/cl1/` 写文件！） | `api.py:1701-1782` |
| `/obs` | **无鉴权**（返回 `module/webui/obs_overlay.html`） | `api.py:77-88` |
| `/ws/live_screenshot`、`/ws/live_control` | **无鉴权** | `api.py:1027, 1335` |

### 5.3 为什么 `/api/deploy/settings` 在 LAN 上返回 403

判定函数 `module/webui/launcher.py:15-35`（原文）：

```python
LOCAL_HOSTS = {"127.0.0.1", "::1", "localhost"}

def is_local_request(request) -> bool:
    client = getattr(request, "client", None)
    host = getattr(client, "host", "") if client is not None else ""
    header_host = _normalize_host(request.headers.get("host", ""))
    return host in LOCAL_HOSTS and header_host in LOCAL_HOSTS

def _normalize_host(host: str) -> str:
    host = str(host or "").strip().lower()
    if host.startswith("["):
        return host[1:].split("]", maxsplit=1)[0]
    return host.split(":", maxsplit=1)[0]
```

**它同时校验两个东西，且是 `and`：**

1. `request.client.host` —— **TCP 连接的对端 IP**（ASGI scope 里的 peer address）
2. `Host` 请求头（去端口、去 `[]`、小写化）

手机访问 `http://192.168.1.100:25548/...` 时：

- `request.client.host = "192.168.31.x"`（手机自己的内网 IP）→ **不在 `LOCAL_HOSTS`** → 直接 False
- `Host: 192.168.1.100:25548` → 归一化后 `"192.168.1.100"` → 也不在 `LOCAL_HOSTS` → 又一次 False

所以 403 是**设计使然**：它是"本机 WebUI / 本机启动器"之间的私有通道（防止内网其他设备读到 `deploy.yaml` 里的 ADB 路径、代理、SSH 密钥配置，以及调用开机自启动/免密登录）。**注意连"内网同样被拒"是刻意的**——只有回环地址 + `localhost` Host 才放行，`192.168.1.100` 或域名访问一律 403。

→ **手机 App 不要试图绕过。** 如果确实需要 deploy 配置里的字段，只能：
（a）在服务端新增一个"仅暴露白名单字段"的 LAN 接口，或
（b）让 App 侧改为用户手动填写。

> 顺带：`/api/import_legacy_upload` 无鉴权且会写盘（`api.py:1757-1762`），LAN 上任何设备都能往 `config/*.json`、`config/*.db`、`log/cl1/**` 写入任意内容——这是一个需要在文档里提醒的安全点（若手机 App 走端口映射/公网暴露，务必先加鉴权）。

---

## 6. 补丁 / 插件机制 & 升级保活

### 6.1 查证结果：**不存在面向用户的插件/补丁机制**

| 候选 | 实际内容 | 结论 |
|---|---|---|
| `deploy/patch.py`（165 行） | 安装期对 **site-packages 第三方库**打补丁：`patch_trust_env`（`:35-58` 改 requests 的 `trust_env`）、`patch_uiautomator2`（`:77-126`）、`patch_apkutils2`（`:129-154`）；`pre_checks()`（`:157-161`） | ❌ 不是用户插件机制 |
| `deploy/Windows/patch.py` | 同类安装期补丁 | ❌ |
| `module/webui/patch.py`（148 行） | 运行时猴子补丁：`patch_executor`（`:41-59` 限制线程池）、`patch_mimetype`（`:62-80`）、`fix_py37_subprocess_communicate`（`:83-148`） | ❌ 不是插件机制 |
| `.github/` | 只有 `ISSUE_TEMPLATE/`、`workflows/`（CI、docker-publish、git-over-cdn…）、`scripts/` | ❌ 无插件相关 |
| `module/submodule/` | **"mod" 机制**：`MOD_DICT = {'maa':'AlasMaaBridge','fpy':'AlasFpyBridge'}`（`utils.py:7-16`），`load_mod()` 用 `importlib` 从根目录 `submodule/<Dir>/` 加载（`submodule.py:11-17`）。这是为**其他游戏**（MAA/FPY 桥接）准备的，注册表是硬编码常量 | ⚠️ 唯一形态相近的扩展点，但**无法用于挂载自定义 HTTP 路由** |
| 全项目搜 `plugin / custom / extension / patch` 目录 | 无 | ❌ |
| `importlib.import_module` 全部命中 | `module/submodule/submodule.py:17,27`、`module/hard/hard.py:51`、`module/campaign/run.py:85`（后两个是内部地图模块） | ❌ 无自动扫描加载用户代码的钩子 |

### 6.2 升级会覆盖什么

`module/webui/updater.py:280-295` → `deploy/git.py:86-135`：

```python
# deploy/git.py:117-132
self._fetch_with_retry(source, branch)
...
self.execute(f'{git} reset --hard {source}/{branch}')   # ← 第 129 行
self.execute(f'{git} pull --ff-only {source} {branch}')
```

- `git reset --hard` 会把**所有被 Git 跟踪的文件**还原到远端版本 → **你直接改 `module/webui/api.py` 加的路由，升级后 100% 丢失**。
- **没有 `git clean`** → **新增的未跟踪文件（新模块、新脚本）不会被删**。
- `.gitignore` 覆盖了 `config/*.json`、`config/*.db`、`log`、`*.pyw` 等（`.gitignore:1-20`）→ 配置与日志天然不参与更新。
- 仓库实际形态：`git remote = https://gitcode.com/ddl2/AzurLaneAutoScript`（是 git 仓库，非 zip 发行版）。

### 6.3 结论与建议

**项目里没有"升级后保留自定义代码"的官方机制。** 想保活，只能靠工程手段：

| 方案 | 保活 | 代价 |
|---|---|---|
| **A. 独立 sidecar 服务（推荐）**：新写一个 `mobile_api.py`（未跟踪文件），`import module.config.*` / `module.webui.process_manager` 直接复用内部函数，自己起一个 uvicorn 在别的端口（如 25549） | ✅ 代码文件不被 reset；只要内部 API 不 breaking 就长期可用 | 需要多开一个进程/端口；跨进程读 `ProcessManager.renderables` 拿不到（那是 WebUI 进程内存），日志/资源走文件即可 |
| **B. 新文件 + 一行挂载**：新文件保留，但在 `module/webui/api.py` 的 `api_routes`（或 `app.py`）里加一行 `from module.webui.mobile_api import mobile_routes; routes.extend(mobile_routes)` | ⚠️ 新文件活着，**这一行每次升级后要手动补** | 升级后需人工重新打补丁；可用 `git diff` 记录 |
| **C. 维护 fork**：把改动提交到自己的分支 | ✅ | 需要自建远端，跟随上游合并成本高 |
| **D. 只做只读监控**：不改服务端，手机端直连 `/ws/live_screenshot`、`/ws/live_control`、`/obs`、`/api/cl1_stats`、`/api/ap_timeline`、`/mcp/sse` | ✅ 零改造零丢失 | 日志/资源/控制只能走 MCP 协议 |

---

## 7. 给手机 App 的建议落地清单

### 立即可用（零改造，今天就能连）

| 能力 | 接口 | 备注 |
|---|---|---|
| 实时画面 + 点按/滑动/按键 | `ws://IP:25548/ws/live_screenshot?instance=alas&mode=auto&fps=60&width=640` + `ws://IP:25548/ws/live_control?instance=alas` | 需自己实现 H.264 解码与 1280×720 坐标换算 |
| 大世界月统计 / 行动力曲线 | `GET /api/cl1_stats?instance=alas`、`GET /api/ap_timeline?instance=alas` | 纯 JSON，无鉴权 |
| 通知推送流 | `GET /api/notify_stream`（SSE） | 无鉴权 |
| OBS 悬浮页（可当 H5 探针） | `GET /obs` | 无鉴权 |
| 全部 18 项控制/查询能力 | MCP：`GET /mcp/sse` + `POST /mcp/messages`（或独立 `22268` 端口） | 需实现 MCP JSON-RPC over SSE |

### 需要新增代码（建议一次性补齐的 REST 面）

| 新接口 | 数据源（已存在） | 复用函数 |
|---|---|---|
| `GET /api/instances` | `config/*.json` | `module/config/utils.py:203` `alas_instance()` |
| `GET /api/status?instance=` | 内存 + SQLite | `ProcessManager.get_manager(n).alive/.state`（`process_manager.py:603,616`）；`get_scheduler_queue` 逻辑抄 `mcp_server_sse.py:362-374`；当前任务抄 `mcp_server_sse.py:330-359` 或查 `daily_summary.db` |
| `GET /api/resources?instance=` | `config/<实例>.json` → `Dashboard` | `McpConfigHelper.get_dashboard_resources()`（`module/config/mcp_helper.py:102-135`） |
| `GET /api/log?instance=&lines=` + `GET /api/log/stream`(SSE) | `log/<日期>_<实例>.txt` | 抄 `mcp_server_sse.py:253-277`；SSE 骨架抄 `api.py:1421-1437` |
| `GET /api/screenshot?instance=` | 一次性抓图 | 抄 `mcp_server_sse.py:300-327`（返回 base64 JPEG） |
| `POST /api/instance/start` / `stop` | — | `ProcessManager.get_manager(n).start(func)` / `.stop_by_user(action)` |
| `POST /api/task/{task}/enable` / `trigger` | — | `config.cross_set(f"{task}.Scheduler.Enable", bool)`；`NextRun` 设当前时间；`config.save()`（`mcp_server_sse.py:377-399`） |
| `POST /api/config/set` | — | `config.cross_set(path, value)` + `config.save()`（`mcp_server_sse.py:240-250`） |
| `GET /api/task_config?instance=&task=` | — | `AzurLaneConfig(inst).data[task]`（`mcp_server_sse.py:232-237`） |

**统一建议**：新增接口后放进**新文件**（如 `module/webui/mobile_api.py`），只在 `api.py:1785` 的 `api_routes` 处加 2 行导入——接受"每次升级手动补 2 行"，或改用 sidecar 独立进程（§6.3 方案 A）。

---

## 附：本次未找到 / 明确不存在的项

- ❌ 任何返回日志内容的 HTTP / SSE / WebSocket 接口（除 MCP 的 `get_recent_logs` 工具）
- ❌ `GET /api/resources` 之类的资源 HTTP 接口
- ❌ 任何 HTTP 形式的"启动/停止/暂停实例或任务"接口（除只对本机开放的 launcher 通道 + MCP）
- ❌ 任务级"进度百分比"（只有大世界探索进度配置项与舰船经验进度统计）
- ❌ `config/dashboard.json`（不存在；资源清单在 `module/config/argument/dashboard.yaml`）
- ❌ `deploy.yaml` 里的 scrcpy 开关（不存在，也不需要）
- ❌ 用户插件 / 补丁 / 扩展目录或自动加载钩子
- ❌ `module/webui/dashboard_utils.py` 中的资源数据（该文件只做 scope 命名）
