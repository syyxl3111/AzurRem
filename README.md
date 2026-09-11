# AzurRem

**把 PC 上的 [AzurPilot](https://github.com/) WebUI 原生重建到安卓手机上。**

不是 WebView 套壳 —— 界面是用 Kotlin + Jetpack Compose 重写的，数据和操作走 AzurPilot 自己的 MCP 服务。电脑上跑一个小挂件当数据桥，手机连它就行。

<p align="center">
  <img src="docs/screenshots/01-主页.png" width="19%" />
  <img src="docs/screenshots/04-任务-队列中等待中.png" width="19%" />
  <img src="docs/screenshots/03-任务配置-中文.png" width="19%" />
  <img src="docs/screenshots/05-统计-行动力与趋势.png" width="19%" />
  <img src="docs/screenshots/09-日志.png" width="19%" />
</p>

---

## ⚠️ 三个名字别混

| 名字 | 是什么 |
|---|---|
| **AzurRem** | 本仓库 —— 安卓 App（桌面图标上显示的就是它） |
| **AzurPilot** | PC 上那个服务端项目。**本仓库不修改它任何被跟踪的文件**，只读访问 |
| **alas** | 连接设置里的**实例名**，是 AzurPilot 里的一个概念，跟上面两个都不是一回事 |

---

## 快速开始

### 1. PC 端：跑数据桥

下载 `AzurRemBridge.exe`，**双击**。

窗口出现、显示 `数据桥已启动` 和一行地址，就成了。首次运行它会自己找 AzurPilot ——
找不到就把 AzurPilot 文件夹**拖到 exe 上**，或者点窗口里那个 📁 手动挑。

```
数据桥已启动
http://192.168.1.100:25550     ← 地址可选中复制
[启动 AzurPilot]  [📁]
```

窗口右上角四个圆点（macOS 交通灯风格）：

| 圆点 | 行为 |
|---|---|
| 🔵 缩小 | 收进 **Windows 系统托盘**（左键点托盘图标恢复，右键有「显示窗口 / 退出」） |
| 🟢 全屏 | 窗口在紧凑 / 展开两种尺寸间切换 |
| 🟡 最小化 | 最小化到**任务栏** |
| 🔴 关闭 | 停掉数据桥并退出进程（不留后台孤儿） |

**关掉窗口 = 桥停了**，就这么简单。想让它开机自启，把 exe 的快捷方式丢进
`shell:startup`（Win+R 输入这个就能打开启动文件夹）—— 不需要管理员权限。

### 2. 手机端：装 App

安装 `AzurRem-*.apk`，打开，进 **设置 → 服务器地址**，填上 PC 的地址：

```
http://192.168.1.100:25548
```

**这个地址没有预设值，必须自己填。** 原因见下面的「关于安全」。

数据桥地址可以留空 —— 会自动按服务器地址的主机名推导成 `:25550`。

---

## 关于安全（请一定读一下）

### 代码里没有任何默认服务器地址

早期版本把开发者自己机器的内网地址硬编码成了默认值。那对开源是**有害**的：
别人装上这个 App 会直接连到那台机器上去。

所以现在地址一律由用户自己填。代码里只有一个**示例**地址
（`Settings.SAMPLE_URL`），它只出现在输入框的占位提示里，永远不会被当成默认值使用。

### AzurPilot 的接口在 HTTP 层不做鉴权

这一点必须说清楚，免得有人误以为"有密码就安全了"：

- WebUI 那个密码只挡 **PyWebIO 的浏览器会话**（`module/webui/utils.py` 的 `login()`，走 WebSocket）
- `module/webui/fastapi.py` 注册的中间件只有 `GZipMiddleware` 和一个设 `Cache-Control` 的 `HeaderMiddleware` —— **没有任何鉴权中间件**
- 实测：不带任何凭据 `GET /api/cl1_stats` → 200；不带凭据完成 MCP 握手并调用 `get_config` → 成功
- 唯一有防护的是 `/api/launcher/*`，但它判的是 `is_local_request`（**来源是不是本机**），不是密码

**结论：本 App 能连上就能读能改，没有任何服务器侧的屏障。**

所以请：
- **只在局域网内使用**，不要把 25548 / 25550 暴露到公网
- 不要在不可信的 WiFi 下开着数据桥

---

## 功能

| Tab | 内容 |
|---|---|
| **主页** | 状态卡 + 资源分组列表 + 右下角可拖动的启停悬浮按钮 |
| **任务** | 运行中 / 队列中 / 等待中 三段；整行点击进配置页，右侧 ⚡ 确认后立即执行。启停按钮在这个 Tab 也有 |
| **配置** | 搜索框 + 10 个可折叠分组 → 93 个任务，点进去编辑 |
| **统计** | 总行动力曲线 · 全资源趋势 · 侵蚀1 月度 · **委托收益统计** · 耄耋相接（数据收集 + 收获）· 每日经验检测 |
| **设置** | 连接 / 刷新 / 外观 / 工具（日志）/ 任务配置缓存 / 关于 |

<p align="center">
  <img src="docs/screenshots/10-统计-委托收益.png" width="19%" />
  <img src="docs/screenshots/11-统计-委托收益-明细.png" width="19%" />
  <img src="docs/screenshots/02-配置树.png" width="19%" />
  <img src="docs/screenshots/07-统计-耄耋相接.png" width="19%" />
  <img src="docs/screenshots/08-设置.png" width="19%" />
</p>

### 几个刻意做对的细节

- **任务页的三段逐行对齐 PC 概览页**（`app_dashboard.py:41-51`）。最容易漏的一条：
  **实例没在跑时「运行中」为空，所有逾期任务全部留在「队列中」**。
  「队列中」按 `SCHEDULER_PRIORITY` 排序，不是时间序 —— 所以第一条才是"下一个真要跑的任务"。
- **任务配置页是中文的**。MCP 自带的 `get_task_help` 因为 i18n 布局问题会退回英文键，
  所以结构走数据桥自己做的 join，当前值才走 MCP `get_config`。
- **配置页秒开**。见下面「预缓存」。
- **日志是准实时的**：按字节 offset 增量读，1 秒一次；只在日志页打开时才拉，切后台自动停。

---

## 预缓存：为什么点开配置页是"秒开"

AzurPilot 的 `mcp_server_sse.py` 里：

```python
async def _tool_get_config(arguments):
    config = AzurLaneConfig(inst)      # ← 每次都重建整个配置对象
    data = config.data.get(task, {})
```

它声明成 `async def`，但 `AzurLaneConfig(inst)` 是**同步重活**（读全部配置 + 深度合并默认值 + 校验），
跑在 uvicorn 的**单事件循环**上 —— 执行期间整个循环都被堵住。

而 App 每打开一个配置页要**新开一轮 MCP 会话**：

```
GET /mcp/sse 等 endpoint  →  initialize  →  notifications/initialized  →  tools/call get_config
```

整整 4 次往返，每一次都得排在别人后面。实测（PC 本地、无竞争）：

| 步骤 | 耗时 |
|---|---|
| 桥 `/api/task_schema` | 14 ms |
| MCP: SSE 握手 | 7 ms |
| MCP: initialize | 3 ms |
| MCP: initialized 通知 | 2 ms |
| MCP: `get_config` | 26 ms（最慢 135 ms） |
| **合计** | **50 ms** |

手机上还要叠加 WiFi 往返和信号抖动，用户看到的就是「点进去卡一下」。

服务端不能改（会被自动更新覆盖），所以缓存放手机这边：

- **拉一次，之后纯本地读** —— 任务树到手后，后台用**一个 MCP 会话**把 93 个任务的配置全拉下来
  （实测 2.6 秒；逐个开新会话要 4.7 秒）
- 命中缓存就**直接渲染，一帧骨架屏都不给**，然后后台静默校验
- 校验失败**不动界面** —— 用户手上那份数据是可用的，弹错误只会让人以为页面坏了
- 缓存在 `filesDir/config_cache.json`，**杀进程重开照样秒开**

设置页能看到缓存了多少个任务，也有「重新预缓存」。

---

## 架构

```
┌──────────────┐   HTTP 25550    ┌─────────────────────────┐
│  手机 App     │ ───────────────▶│  AzurRemBridge.exe       │
│  (Compose)   │   只读 JSON      │  (bridge/mobile_bridge.py)│
└──────┬───────┘                 └───────────┬─────────────┘
       │                                     │ 只读文件
       │  MCP over SSE (25548/mcp)           ▼
       │                          ┌──────────────────────┐
       └─────────────────────────▶│  D:\...\AzurPilot     │
                                  │  config/  log/        │
                                  └──────────────────────┘
```

### 为什么必须有数据桥

AzurPilot 的 WebUI **只暴露两个数据接口**（`/api/cl1_stats`、`/api/ap_timeline`）——
连它自己的 `obs_overlay.html` 也只用这两个。统计页是服务端渲染 HTML 的，
日志走 PyWebIO 的会话 WebSocket，队列优先级是运行时算的。

所以资源历史、耄耋相接、委托收益、每日经验、日志增量这些**没有 JSON 出口**，
只能由 PC 上的桥读出来。

**桥放在本仓库里，不在 AzurPilot 目录中** —— AzurPilot 升级是
`git reset --hard` + `git pull --ff-only`，放进去会被清掉。桥只读访问那个目录。

### 数据来源

| 数据 | 来源 |
|---|---|
| 资源当前值、状态、启停、调度队列、日志（兜底） | AzurPilot 自带的 **MCP 服务**（挂在 25548 的 `/mcp`） |
| 任务配置的**当前值 / 写入** | MCP `get_config` / `update_config` |
| 任务配置的**中文结构** | 桥 `/api/task_schema` |
| 总行动力曲线 | `GET /api/ap_timeline` |
| 侵蚀1 月度统计 | `GET /api/cl1_stats` + 客户端按 PC 口径派生 |
| 全资源历史趋势 | 桥 `/api/resource_history` |
| 任务菜单树（10 组 93 项） | 桥 `/api/task_tree` |
| 概览队列三段 | 桥 `/api/overview_tasks` |
| 日志（准实时） | 桥 `/api/logs/tail`（字节 offset 增量） |
| **委托收益统计** | 桥 `/api/commission_income`（`config/cl1_data.db`） |
| 耄耋相接 · 数据收集 | 桥 `/api/meow_hazard`（同一个 db） |
| 耄耋相接 · 收获 | 桥 `/api/meow_stats`（`azurstat_meowofficer_farming.csv`） |
| 每日经验检测 | 桥 `/api/ship_exp`（`log/cl1/<实例>/ship_exp_data.json`） |

### 口径对齐：数字必须和 PC 一样

桥上凡是"重算"的逻辑，都配了一个与 AzurPilot 原实现**逐字段对照**的脚本：

```powershell
# 每日经验检测（移植了 module/statistics/ship_exp_stats.py，125 项经验表 + 61 个字段）
D:\Tools\AzurPilot\.venv\Scripts\python.exe bridge\verify_ship_exp_parity.py

# 委托收益统计（移植了 module/statistics/commission_income_stats.py）
D:\Tools\AzurPilot\.venv\Scripts\python.exe bridge\verify_commission_parity.py
```

两个都必须是 `[PASS]`。这类偏差**很隐蔽** —— 不专门对比，根本发现不了
手机上比电脑上少了 3 个钻石。

---

## 构建

### Android App

```powershell
cd AzurPilotMobile
.\gradlew.bat assembleDebug
# 产出 app\build\outputs\apk\debug\app-debug.apk
```

| 组件 | 版本 |
|---|---|
| Android SDK | build-tools 36.0.0 / android-37.0 |
| AGP / Gradle / Kotlin | 9.3.2 / 9.5.0 / 2.4.10 |
| minSdk / targetSdk | 26 / 37 |

路径里有中文时 AGP 会拒绝构建，`gradle.properties` 里的 `android.overridePathCheck=true` 已经绕过了。

**跑单元测试：**

```powershell
.\gradlew.bat :app:unitTest
```

> 为什么不用标准的 `:app:testDebugUnitTest`？它在某些环境下起不来 ——
> Gradle 的 test worker 子进程刚启动就退出，报
> `ClassNotFoundException: GradleWorkerMain` 并伴随 `java.io.IOException: 管道正在被关闭`。
> 已排除的猜想：worker 的 jar 没缺（都在）、中文路径（换成 ASCII 目录联接一样挂）、`--no-daemon`。
> `:app:unitTest` 直接 fork java 跑 JUnit，绕开 worker API。

### 数据桥 exe

```powershell
bridge\build-exe.bat
# 产出 dist\AzurRemBridge.exe
```

需要 `pyinstaller`。打包参数是 `--onefile --noconsole`（无控制台窗口）+ 图标
`bridge/azurrem.ico`。

**验证：**

```powershell
python bridge\smoke_test_exe.py --port 25561   # 8 个接口全 200
python bridge\gui_test.py                     # 挂件窗口自测
```

---

## 已知限制

- **只在竖屏验证过**，已锁 `screenOrientation="portrait"`
- **日志是「准实时」不是「实时」**：PC 面板是 0.25 秒，但它读的是内存里的 Rich 对象走
  PyWebIO 会话通道，没有可复用的接口。App 是字节 offset 增量 + 1 秒轮询
- **任务级进度百分比**：AzurPilot 本身没有这个概念，App 也做不了
- **实时画面 / 触控**（`/ws/live_screenshot`、`/ws/live_control`）还没接
- 桥**没有自启机制**，重启电脑后要手动双击（或用启动文件夹里的快捷方式）
- `TaskHoardingDuration` 的偏移量桥里没实现，调大会导致队列分桶有偏差

---

## 免责声明

这是一个**非官方**的第三方客户端，与 AzurPilot 项目无关。

请遵守你所在地的法律法规以及游戏的服务条款。因使用本工具产生的一切后果由使用者自行承担。

## 许可

<!-- 选一个再取消注释；没有 LICENSE 文件的话默认是「保留所有权利」，别人不能合法使用
MIT License —— 见 LICENSE
-->
