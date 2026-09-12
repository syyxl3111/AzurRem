package com.azurpilot.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azurpilot.mobile.data.ApPoint
import com.azurpilot.mobile.data.AzurPilotApi
import com.azurpilot.mobile.data.Cl1Stats
import com.azurpilot.mobile.data.CommissionIncome
import com.azurpilot.mobile.data.CommissionPeriod
import com.azurpilot.mobile.data.ConfigArg
import com.azurpilot.mobile.data.ConfigCache
import com.azurpilot.mobile.data.McpAuth
import com.azurpilot.mobile.data.MeowHazardStats
import com.azurpilot.mobile.data.MeowStats
import com.azurpilot.mobile.data.ResourceItem
import com.azurpilot.mobile.data.RunState
import com.azurpilot.mobile.data.ScheduledTask
import com.azurpilot.mobile.data.Settings
import com.azurpilot.mobile.data.ShipExpStats
import com.azurpilot.mobile.data.TaskConfig
import com.azurpilot.mobile.data.TaskTree
import com.azurpilot.mobile.data.TrendPoint
import com.azurpilot.mobile.data.UpdateChecker
import com.azurpilot.mobile.data.mergeLogs
import org.json.JSONObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Tab { Home, Tasks, Config, Stats, Settings }

/**
 * 轻提示的语气。决定颜色，不决定文案。
 *
 * 为什么要区分：启动成功的提示和连接失败的提示长得一样的话，
 * 用户得读完字才知道结果 —— 颜色先于文字传达成败。
 */
enum class ToastTone { Info, Success, Error }

/** 页面路由。
 *
 * 子页面（任务配置 / 日志）是**全屏压栈**式的：盖住 Tab 栏，
 * 顶部有返回箭头，也支持系统返回键和左边缘侧滑返回 —— 这是移动端的习惯，
 * 所以任务行不需要额外挂一个「设置」按钮，整行点击即可。
 */
sealed interface Route {
    data object Tabs : Route
    data class TaskConfig(val task: String) : Route
    data object Logs : Route
}

/** 资源历史查询窗口与分桶数（服务端再降采样一次，140 个点画 12 条曲线足够） */
private const val HISTORY_HOURS = 168
private const val HISTORY_BUCKETS = 140

/** App 里最多保留多少行日志。日志是纯内存列表，留太多会拖慢重组和渲染。 */
private const val LOG_BUFFER_MAX = 3000

data class AppUiState(
    // 连接
    /**
     * 服务器地址。**默认是空串**，不是某个写死的局域网地址。
     *
     * 原来的默认值写的是开发者自己那台机器的内网 IP。那对开源是**有害**的：
     * 别人装上这个 App 会直接连到那台机器上去，而 AzurPilot 的 HTTP 接口
     * 不做任何鉴权 —— 等于把一个陌生人的自动化后台交到别人手里。
     * 现在地址一律由用户在「设置」里自己填，填一次就记住了。
     */
    val serverUrl: String = "",
    val bridgeUrl: String = "",
    val instance: String = Settings.DEFAULT_INSTANCE,

    /**
     * WebUI 密码 —— 服务端设了才需要填，留空表示服务端没设密码。
     *
     * 存一份在 UI 状态里只是为了让设置页能显示和编辑；真正发请求时读的是
     * [McpAuth.key]（见那边的注释：为什么它是全局静态而不是构造参数）。
     */
    val webuiPassword: String = "",
    val pollSeconds: Int = 30,
    val logLines: Int = 400,
    val themeMode: Int = 0,

    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,

    // 运行态
    val running: Boolean = false,
    val stateCode: Int = RunState.STOPPED,
    val currentTask: String = "—",
    val resources: List<ResourceItem> = emptyList(),
    val queue: List<ScheduledTask> = emptyList(),
    val logs: List<String> = emptyList(),
    val lastSyncAt: Long = 0L,

    // 日志增量流（桥的 /api/logs/tail）
    /** 下次拉取要带的字节 offset；0 = 还没拉过（桥会给最后 logLines 行） */
    val logOffset: Long = 0L,
    /** 服务端实际读的文件名，用来判断有没有跨天轮转 */
    val logFile: String = "",
    /** true = 日志由桥的增量流维护；false = 退回 MCP 的全量 tail */
    val logsFromBridge: Boolean = false,

    // 交互
    val tab: Tab = Tab.Home,
    val route: Route = Route.Tabs,
    val actionInFlight: String? = null,
    val confirmStop: Boolean = false,
    val toast: String? = null,
    val toastTone: ToastTone = ToastTone.Info,

    /** 点了某个任务的 ⚡，等他确认「是否立即行动」；null = 没有待确认的 */
    val confirmTrigger: String? = null,

    // 配置树（网关 /api/task_tree）
    val taskTree: TaskTree? = null,
    val treeLoading: Boolean = false,
    val treeError: String? = null,

    // 单个任务的配置（MCP get_task_help + get_config）
    val configTask: TaskConfig? = null,
    val configValues: JSONObject = JSONObject(),
    val configLoading: Boolean = false,
    val configError: String? = null,
    val configSaving: String? = null,

    // ── 配置缓存（见 ConfigCache 的注释：为什么必须缓存）──
    /** 当前页显示的是缓存内容，后台还在校验 */
    val configFromCache: Boolean = false,
    /** 这条缓存的写入时间，0 = 不是缓存来的 */
    val configCachedAt: Long = 0L,
    /** 预缓存进度 */
    val prefetching: Boolean = false,
    val prefetchDone: Int = 0,
    val prefetchTotal: Int = 0,
    /** 已缓存的任务数（设置页显示） */
    val cacheEntries: Int = 0,

    // 统计页新增两块
    val meowStats: MeowStats? = null,
    val meowLoading: Boolean = false,
    /** 耄耋相接数据收集（侵蚀等级 3/5）—— 和上面的收获表是两张不同的表 */
    val meowHazard: MeowHazardStats? = null,
    val meowHazardLoading: Boolean = false,
    val shipExp: ShipExpStats? = null,
    val shipExpLoading: Boolean = false,

    // 委托收益统计（网关 /api/commission_income）
    val commission: CommissionIncome? = null,
    val commissionLoading: Boolean = false,
    /** 拉失败的原因。**和「本月零收益」是两回事**，界面要分开显示 */
    val commissionError: String? = null,
    /** 当前选中的统计周期，切换时会重新拉 */
    val commissionPeriod: CommissionPeriod = CommissionPeriod.Month,

    /**
     * 概览队列（桥的 /api/overview_tasks）。
     *
     * 优先级：桥在线时用这两个；桥不可达时退回 [queue] + `splitQueue()`
     * （MCP 那条路没有优先级排序，顺序会和 PC 不同，但至少不漏任务）。
     */
    val overviewPending: List<ScheduledTask> = emptyList(),
    val overviewWaiting: List<ScheduledTask> = emptyList(),
    val overviewLoaded: Boolean = false,
    val queueLoading: Boolean = false,

    // FAB 位置
    val fabOnRight: Boolean = true,
    val fabYFraction: Float = 1f,

    // 统计页
    val apTimeline: List<ApPoint> = emptyList(),
    val cl1Stats: Cl1Stats? = null,
    val statsLoading: Boolean = false,
    val resourceHistory: Map<String, List<TrendPoint>> = emptyMap(),
    val historySamples: Int = 0,
    val historyLoading: Boolean = false,
    val historyError: String? = null,

    // ── 应用内更新（数据源是本项目的 GitHub Releases）──
    /** 当前安装的版本号，从 PackageManager 读 */
    val appVersion: String = "",
    val updateChecking: Boolean = false,
    /** 查到的新版本；null = 没有新版或还没查过 */
    val updateAvailable: UpdateChecker.UpdateInfo? = null,
    /** 检查结果的一句话说明（已是最新 / 查不到的原因） */
    val updateMessage: String? = null,
    /** 下载中：已收字节 / 总字节（总未知时为 0） */
    val updateProgress: Pair<Long, Long>? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)

    /**
     * 任务配置缓存。
     *
     * 服务端的 `get_config` 每次都重建整个 `AzurLaneConfig`，而且跑在
     * uvicorn 的单事件循环上（同步重活堵住 async 循环），App 每开一个配置页
     * 还要新开一整轮 MCP 会话。服务端不能动，所以缓存放这边。
     * 详见 ConfigCache 的类注释。
     */
    private val cache = ConfigCache(app)

    private val _ui = MutableStateFlow(
        AppUiState(
            serverUrl = settings.serverUrl,
            bridgeUrl = settings.effectiveBridgeUrl,
            instance = settings.instance,
            webuiPassword = settings.webuiPassword,
            pollSeconds = settings.pollSeconds,
            logLines = settings.logLines,
            themeMode = settings.themeMode,
            fabOnRight = settings.fabOnRight,
            fabYFraction = settings.fabYFraction,
        ),
    )
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private var pollJob: Job? = null

    /** 日志增量流的独立协程：只在日志页打开时跑 */
    private var logJob: Job? = null

    /** 预缓存协程：可被新一轮取消 */
    private var prefetchJob: Job? = null

    init {
        // ★ 必须赶在第一轮请求之前：AzurPilot 设了 WebUI 密码就会对 MCP 收凭据，
        //   晚一步配置，启动时那一轮会先吃到一次 401（界面闪一下「无法连接」）。
        McpAuth.configure(settings.webuiPassword)

        startPolling()
        // 任务树不只是「配置」页要用 —— 首页状态卡的任务名、任务页的行标题
        // 都得靠它翻译成中文（MCP 那边给的是英文键）。所以启动时就拉一次。
        // 拉完会自动触发配置预缓存（见 loadTaskTree）。
        loadTaskTree()
        // 缓存可能来自上一次运行（存在 filesDir 里）—— 先把条数读出来显示，
        // 这样设置页一进去就能看到「已缓存 N 个任务」，而不是先显示 0 再跳
        _ui.update { it.copy(cacheEntries = cache.size()) }
    }

    // ─────────────────────────────────────────────────────
    // 轮询
    // ─────────────────────────────────────────────────────

    fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                pollOnce()
                delay(_ui.value.pollSeconds.coerceIn(5, 600) * 1000L)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * 手动下拉刷新 / 状态卡的刷新按钮。
     *
     * ★ 除了重连 MCP 快照，还要把**当前页面自己那批数据**一起强制重拉。
     *
     * 原来这里只有一句 `pollOnce()`，而统计页那几个板块（行动力曲线、资源趋势、
     * 耄耋相接、每日经验、委托收益）走的是**另一批接口**，跟 MCP 快照没关系 ——
     * 于是点刷新时页面上的数字一动不动，用户看到的就是「刷新没反应 / 数据没更新」。
     * 各页的刷新按钮都接在这里，所以在这里按当前 Tab 分派一次。
     */
    fun manualRefresh() {
        viewModelScope.launch { pollOnce() }

        // 日志页是**子页面**（Route.Logs）而不是 Tab，所以按路由先判一次。
        // `startLogStream()` 自己会把 offset 清零 —— 于是这里是"从头重拉"，
        // 而不是"在已有内容后面继续追加"。
        if (_ui.value.route == Route.Logs) {
            stopLogStream()
            _ui.update { it.copy(logs = emptyList(), logsFromBridge = false) }
            startLogStream()
            return
        }

        when (_ui.value.tab) {
            Tab.Stats -> {
                loadStats(force = true)
                loadMeowAndExp(force = true)
                loadCommission(force = true)
            }
            // 任务页和配置页的中文名都靠任务树
            Tab.Tasks, Tab.Config -> loadTaskTree(force = true)
            else -> Unit
        }
    }

    private suspend fun pollOnce() {
        val s = _ui.value

        // ★ 还没填服务器地址 —— 这**不是**「连接失败」，是「还没配置」。
        //   两者的提示必须不同：连接失败让人去查网络/查端口，而这里要做的
        //   只有一件事 —— 去设置里把地址填上。
        //   不特判的话，空白地址会被 AzurPilotApi 判成「服务器地址无效：」，
        //   界面显示一句带冒号却没下文的错误，新人完全不知道下一步该干嘛。
        if (s.serverUrl.isBlank()) {
            _ui.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    connected = false,
                    error = Settings.BLANK_URL_HINT,
                )
            }
            return
        }

        _ui.update { it.copy(refreshing = true) }
        try {
            val snap = AzurPilotApi(s.serverUrl).fetchSnapshot(s.instance, s.logLines)
            _ui.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    connected = true,
                    error = null,
                    running = snap.running,
                    stateCode = snap.stateCode,
                    currentTask = snap.currentTask,
                    resources = snap.resources,
                    queue = snap.queue,
                    logs = if (it.logsFromBridge) it.logs else mergeLogs(it.logs, snap.logs),
                    lastSyncAt = snap.fetchedAtMillis,
                )
            }
            // 队列分段单独走桥（MCP 那条路没有优先级排序，对不上 PC）。
            // 放在 MCP 快照之后，因为「运行中」那一段要用到刚拿到的 running。
            loadOverviewQueue()
        } catch (e: Exception) {
            _ui.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    connected = false,
                    error = e.message?.takeIf { m -> m.isNotBlank() } ?: "连接失败",
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────
    // 概览队列（三段）
    // ─────────────────────────────────────────────────────

    /**
     * 拉「概览队列」—— 桥已经按 PC 口径分好 pending / waiting 并排好序。
     *
     * 桥不可达时**不清空**上一次的结果，而是把 `overviewLoaded` 置 false，
     * 让界面退回 MCP 的近似切法（顺序可能和 PC 不同，但不会突然空掉）。
     */
    private suspend fun loadOverviewQueue() {
        val s = _ui.value
        _ui.update { it.copy(queueLoading = true) }
        runCatching { AzurPilotApi(s.serverUrl).fetchOverviewTasks(bridgeCandidates(), s.instance) }
            .fold(
                onSuccess = { (pending, waiting) ->
                    _ui.update {
                        it.copy(
                            queueLoading = false,
                            overviewPending = pending,
                            overviewWaiting = waiting,
                            overviewLoaded = true,
                        )
                    }
                },
                onFailure = {
                    _ui.update { it.copy(queueLoading = false, overviewLoaded = false) }
                },
            )
    }

    // ─────────────────────────────────────────────────────
    // 启停
    // ─────────────────────────────────────────────────────
    /**
     * 点 FAB：
     *   运行中 → 弹**模态**确认框（停止有副作用，会按配置收尾，不能一击即停）
     *   未运行 → 直接启动，结果用上方轻提示回报（成功绿 / 失败红）
     */
    fun onFabClick() {
        if (_ui.value.actionInFlight != null) return
        if (_ui.value.running) {
            _ui.update { it.copy(confirmStop = true) }
        } else {
            doStart()
        }
    }

    fun cancelStop() = _ui.update { it.copy(confirmStop = false) }

    fun confirmStop() {
        _ui.update { it.copy(confirmStop = false) }
        doStop()
    }

    private fun doStart() = runAction("start") { api, inst -> api.startInstance(inst) }

    private fun doStop() = runAction("stop") { api, inst -> api.stopInstance(inst) }

    // ── 立即执行：先弹气泡确认，再真的触发 ──

    /** 点 ⚡ —— 只登记待确认，不动服务端 */
    fun requestTrigger(task: String) {
        if (_ui.value.actionInFlight != null) return
        _ui.update { it.copy(confirmTrigger = task) }
    }

    fun cancelTrigger() = _ui.update { it.copy(confirmTrigger = null) }

    fun confirmTrigger() {
        val task = _ui.value.confirmTrigger ?: return
        _ui.update { it.copy(confirmTrigger = null) }
        runAction("trigger:$task") { api, inst -> api.triggerTask(inst, task) }
    }

    /**
     * 统一跑一个会改服务端状态的动作。
     *
     * 成功和失败都要给上方轻提示，但**语气不同** —— 成功绿、失败红。
     * MCP 的工具错误不会置 isError，而是把 "Error: ..." 当普通文本返回
     * （见 McpClient 的注释），所以这里还要按内容判一次。
     */
    private fun runAction(key: String, block: suspend (AzurPilotApi, String) -> String) {
        val s = _ui.value
        if (s.actionInFlight != null) return
        viewModelScope.launch {
            _ui.update { it.copy(actionInFlight = key) }
            try {
                val raw = block(AzurPilotApi(s.serverUrl), s.instance).trim()
                // MCP 的工具错误不置 isError，而是把 "Error: ..." 当普通文本返回
                val ok = !raw.startsWith("Error", ignoreCase = true)
                _ui.update {
                    it.copy(
                        actionInFlight = null,
                        toast = humanizeActionMessage(raw, ok),
                        toastTone = if (ok) ToastTone.Success else ToastTone.Error,
                    )
                }
                // 状态需要一点时间才会反映到服务端，稍等再拉一次
                delay(900)
                pollOnce()
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        actionInFlight = null,
                        toast = e.message?.takeIf { m -> m.isNotBlank() } ?: "操作失败",
                        toastTone = ToastTone.Error,
                    )
                }
            }
        }
    }

    fun consumeToast() = _ui.update { it.copy(toast = null) }

    /** 纯提示，不改任何服务端状态（给「关于」里那些还没接上的入口用） */
    fun notify(message: String) =
        _ui.update { it.copy(toast = message, toastTone = ToastTone.Info) }

    /**
     * 把 MCP 的原样回执翻成人话。
     *
     * MCP 返回的是 `Success: Started alas (alas)` 这种服务端英文回执，
     * 直接弹给用户很像报错。这里只做**最小改写**，不杜撰服务端没说的事
     * —— 认不出来的就保留原文，宁可生硬也不要编。
     */
    private fun humanizeActionMessage(raw: String, ok: Boolean): String {
        if (raw.isBlank()) return if (ok) "操作完成" else "操作失败"

        val body = raw
            .removePrefix("Success:").removePrefix("Error:")
            .trim()

        if (!ok) return body.ifBlank { "操作失败" }

        // Success: Started alas (alas) / Success: Stopped alas
        val started = Regex("""Started\s+(\S+?)(?:\s+\(.*\))?$""").find(body)
        if (started != null) return "已启动 ${started.groupValues[1]}"
        val stopped = Regex("""Stopped\s+(\S+)$""").find(body)
        if (stopped != null) return "已停止 ${stopped.groupValues[1]}"

        return body.ifBlank { "操作完成" }
    }

    // ─────────────────────────────────────────────────────
    // 统计
    // ─────────────────────────────────────────────────────

    fun loadStats(force: Boolean = false) {
        val s = _ui.value
        if (s.statsLoading || s.historyLoading) return
        if (!force && s.apTimeline.isNotEmpty() && s.resourceHistory.isNotEmpty()) return

        viewModelScope.launch {
            _ui.update { it.copy(statsLoading = true) }
            val api = AzurPilotApi(s.serverUrl)
            val timeline = runCatching { api.fetchApTimeline(s.instance) }.getOrDefault(emptyList())
            val cl1 = runCatching { api.fetchCl1Stats(s.instance) }.getOrNull()
            _ui.update { it.copy(statsLoading = false, apTimeline = timeline, cl1Stats = cl1) }
        }

        viewModelScope.launch {
            _ui.update { it.copy(historyLoading = true, historyError = null) }
            val result = runCatching {
                AzurPilotApi(s.serverUrl).fetchResourceHistory(
                    baseUrls = bridgeCandidates().ifEmpty { listOf(s.bridgeUrl) },
                    instance = s.instance,
                    hours = HISTORY_HOURS,
                    buckets = HISTORY_BUCKETS,
                )
            }
            result.fold(
                onSuccess = { history ->
                    _ui.update {
                        it.copy(
                            historyLoading = false,
                            historyError = if (history.series.isEmpty()) "网关还没有采集到资源快照" else null,
                            resourceHistory = history.series,
                            historySamples = history.sampleCount,
                        )
                    }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(
                            historyLoading = false,
                            historyError = e.message?.takeIf { m -> m.isNotBlank() }
                                ?: "取不到资源历史（网关没启动？）",
                        )
                    }
                },
            )
        }
    }

    // ─────────────────────────────────────────────────────
    // 导航与设置
    // ─────────────────────────────────────────────────────

    fun selectTab(tab: Tab) {
        // 从日志页切走时要把增量流停掉，否则它在后台每秒打一次桥
        stopLogStream()
        _ui.update { it.copy(tab = tab, route = Route.Tabs) }
        when (tab) {
            Tab.Stats -> {
                // ★ 每次进统计页都**强制重拉**，不吃缓存。
                //
                //   这几个接口在局域网里是 40~90ms 级，而用户点进来就是想看**当前**
                //   的数字 —— 拿着上次进页面时的旧值，表现出来就是「统计页没更新，
                //   和 PC 上对不上」。重拉期间旧数据仍留在屏幕上（骨架屏只在完全
                //   没数据时才出），所以不会闪一下空白。
                loadStats(force = true)
                loadMeowAndExp(force = true)
                // 委托收益和别的统计一样，只在进统计页时拉
                loadCommission(force = true)
            }
            // 任务页也要用任务树里的中文名，所以两个 Tab 都触发一次加载
            Tab.Config, Tab.Tasks -> loadTaskTree()
            else -> Unit
        }
    }

    // ─────────────────────────────────────────────────────
    // 网关：候选地址
    // ─────────────────────────────────────────────────────

    /**
     * 网关候选地址。
     *
     * 25549 上可能跑着早期版本的网关（没有新接口），新版跑在 25550。
     * 所以这里给出一组候选，API 层会依次尝试 —— 哪个在跑都能用。
     *
     * 服务器地址为空时**返回空列表**：宁可让调用方报「没有可用的网关地址」，
     * 也不要去连一台跟我们毫无关系的机器（那正是硬编码 IP 时代的毛病）。
     */
    private fun bridgeCandidates(): List<String> {
        val s = _ui.value
        if (s.serverUrl.isBlank()) return emptyList()

        val host = runCatching {
            java.net.URI(s.bridgeUrl.ifBlank { s.serverUrl }).host
        }.getOrNull() ?: return emptyList()

        val out = LinkedHashSet<String>()
        if (s.bridgeUrl.isNotBlank()) out += s.bridgeUrl
        if (host.isNotBlank()) {
            out += "http://$host:25549"
            out += "http://$host:${Settings.BRIDGE_PORT}"
        }
        return out.toList()
    }

    // ─────────────────────────────────────────────────────
    // 路由
    // ─────────────────────────────────────────────────────

    /**
     * 打开一个任务的配置页。
     *
     * ★ 这里是「秒开」的关键：**缓存命中就直接渲染，一帧骨架屏都不给**，
     * 然后才在后台静默校验。没有缓存时才走原来的骨架屏 + 网络。
     *
     * 为什么不是「有缓存也先转圈再刷新」：用户点进去要的是**立刻看到内容**。
     * 显示旧值 200ms 再无声替换成新值，比转 200ms 圈再显示新值体验好得多 ——
     * 而且配置这种数据，绝大多数时候旧值就是新值。
     */
    fun openTaskConfig(task: String) {
        val s = _ui.value
        val k = cache.key(s.serverUrl, s.instance, task)

        val cachedTask = cache.schema(k)
        val cachedValues = cache.values(k)

        if (cachedTask != null && cachedValues != null) {
            _ui.update {
                it.copy(
                    route = Route.TaskConfig(task),
                    configTask = cachedTask,
                    configValues = cachedValues,
                    configLoading = false,
                    configError = null,
                    configFromCache = true,
                    configCachedAt = cache.savedAt(k),
                )
            }
            // 后台校验，有新值再无缝替换（失败也**不动**界面，见 loadTaskConfig）
            loadTaskConfig(task, silent = true)
            return
        }

        _ui.update {
            it.copy(
                route = Route.TaskConfig(task),
                configTask = null,
                configValues = JSONObject(),
                configError = null,
                configFromCache = false,
                configCachedAt = 0L,
            )
        }
        loadTaskConfig(task)
    }

    fun openLogs() {
        _ui.update { it.copy(route = Route.Logs) }
        startLogStream()
    }

    fun back() {
        stopLogStream()
        _ui.update {
            it.copy(route = Route.Tabs, configTask = null, configValues = JSONObject(), configError = null)
        }
    }

    // ─────────────────────────────────────────────────────
    // 配置预缓存
    // ─────────────────────────────────────────────────────

    /**
     * 把所有任务的配置**一次性**拉到手机里存好。
     *
     * 目的就是让之后每一次点开都是纯本地读 —— 不再碰网络，也不再碰服务端
     * 那条被堵住的事件循环。
     *
     * 时机：任务树拉到之后自动跑一次（任务树给了全部 93 个任务键）。
     * 全程后台，不阻塞任何界面；用户中途点开某个任务也不会被它拖慢
     * （点开走的是缓存或独立请求，不排队等这个）。
     */
    fun prefetchAllConfigs(force: Boolean = false, announce: Boolean = false) {
        val s = _ui.value
        if (s.prefetching) return

        val tasks = s.taskTree?.groups?.flatMap { g -> g.tasks.map { it.key } }.orEmpty()
        if (tasks.isEmpty()) return
        // 全都缓存过了就别重复跑（省电、省服务端）
        if (!force && cache.size() >= tasks.size) {
            _ui.update { it.copy(cacheEntries = cache.size()) }
            return
        }

        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            _ui.update {
                it.copy(prefetching = true, prefetchDone = 0, prefetchTotal = tasks.size * 2)
            }

            val server = s.serverUrl
            val instance = s.instance
            runCatching {
                AzurPilotApi(server).prefetchTaskConfigs(
                    baseUrls = bridgeCandidates(),
                    instance = instance,
                    tasks = tasks,
                    onEach = { task, schemaRaw, values ->
                        // 结构和值**任一缺失都不写** —— 只有一半的条目渲染不出来，
                        // 留着半个反而会让下次打开既命中不了缓存、又白占内存
                        if (schemaRaw != null && values != null) {
                            cache.put(cache.key(server, instance, task), task, schemaRaw, values)
                        }
                    },
                    onProgress = { done, total ->
                        _ui.update { it.copy(prefetchDone = done, prefetchTotal = total) }
                    },
                )
            }

            cache.flush()
            _ui.update {
                it.copy(prefetching = false, cacheEntries = cache.size())
            }
            // 只在**用户手动点**的时候报一句。开机自动预缓存如果也弹提示，
            // 每次开 App 都蹦一条，很快就变成噪音了。
            if (announce) {
                _ui.update {
                    it.copy(
                        toast = "已缓存 ${cache.size()} 个任务的配置",
                        toastTone = ToastTone.Success,
                    )
                }
            }
        }
    }

    /** 设置页手动「重新预缓存」 */
    fun reprefetchAll() {
        cache.clear()
        _ui.update { it.copy(cacheEntries = 0) }
        prefetchAllConfigs(force = true, announce = true)
    }

    // ─────────────────────────────────────────────────────
    // 日志增量流
    // ─────────────────────────────────────────────────────

    /**
     * 日志只在**日志页打开时**才高频拉。
     *
     * 1 秒一次看着激进，但单次只读几十 KB 的增量（桥按字节 offset 读），
     * 和原来「30 秒一次整读 16MB」相比，单位时间的磁盘 IO 反而**低得多**。
     *
     * PC 面板是 0.25 秒（内存里的 Rich 对象走 PyWebIO 会话通道，没有可复用的 HTTP 接口），
     * 1 秒在观感上已经够"实时"了。
     */
    private fun startLogStream() {
        if (logJob?.isActive == true) return
        logJob = viewModelScope.launch {
            // 每次进日志页都从"最后 N 行"重新开始，避免拿着上次会话的陈旧 offset
            _ui.update { it.copy(logOffset = 0L) }
            while (isActive) {
                pullLogTail()
                delay(1000)
            }
        }
    }

    private fun stopLogStream() {
        logJob?.cancel()
        logJob = null
    }

    /**
     * 切到后台：停日志流，也停轮询。
     * 轮询本身开销不大，但既然界面看不见了，没必要每 30 秒建一条 MCP 会话。
     */
    fun pauseForBackground() {
        stopLogStream()
        stopPolling()
    }

    /** 回前台：接上轮询；如果还停在日志页，日志流也一起接上 */
    fun resumeForeground() {
        startPolling()
        viewModelScope.launch {
            pollOnce()
            if (_ui.value.route == Route.Logs) startLogStream()
        }
    }

    private suspend fun pullLogTail() {
        val s = _ui.value
        val tail = AzurPilotApi(s.serverUrl)
            .fetchLogTail(bridgeCandidates(), s.instance, s.logOffset, s.logLines)

        if (tail == null) {
            // 桥不可达 —— 交回 MCP 的全量 tail 兜底（pollOnce 里那一路）
            if (s.logsFromBridge) _ui.update { it.copy(logsFromBridge = false, logOffset = 0L) }
            return
        }

        _ui.update {
            // 三种情况要**整体替换**而不是追加：
            //   1. 桥说 reset（文件变小了）
            //   2. 还没拉过（offset 归零）
            //   3. ★ 换文件了 —— 日志文件名带日期，每天 0 点轮转。
            //      桥是无状态的，唯一能自己发现的判据是 size < offset；
            //      但日志页如果**跨过 0 点没退出**，新文件长到比旧 offset 还大时，
            //      那一拉就会从今天文件的中间开始给。所以再比一次文件名。
            val switched = it.logFile.isNotEmpty() && tail.file != it.logFile
            val merged = if (tail.reset || it.logOffset <= 0L || switched) {
                tail.lines
            } else {
                it.logs + tail.lines
            }
            it.copy(
                logsFromBridge = true,
                logFile = tail.file,
                logOffset = tail.offset,
                logs = merged.takeLast(LOG_BUFFER_MAX),
            )
        }

        // 一次没读完（撞到 max_bytes）就立刻接着拉，别等下一秒
        if (tail.truncated) pullLogTail()
    }

    // ─────────────────────────────────────────────────────
    // 配置树与任务配置
    // ─────────────────────────────────────────────────────

    fun loadTaskTree(force: Boolean = false) {
        val s = _ui.value
        if (s.treeLoading) return
        if (!force && s.taskTree != null) return

        viewModelScope.launch {
            _ui.update { it.copy(treeLoading = true, treeError = null) }
            try {
                val tree = AzurPilotApi(s.serverUrl).fetchTaskTree(bridgeCandidates())
                _ui.update {
                    it.copy(treeLoading = false, taskTree = tree, cacheEntries = cache.size())
                }
                // ★ 任务树到手后**立刻**开始预热配置缓存。
                //   放在这里而不是 init 里，是因为预缓存需要任务树给出全部 93 个任务键。
                //   它跑在独立协程里，不阻塞这里，也不会让任务树晚一秒显示。
                prefetchAllConfigs()
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        treeLoading = false,
                        treeError = e.message?.takeIf { m -> m.isNotBlank() }
                            ?: "取不到任务菜单树（网关没启动？）",
                    )
                }
            }
        }
    }

    /**
     * 拉一个任务的配置（结构 + 当前值，两条链路并行）。
     *
     * [silent] = true 时是**后台校验**：不显示骨架屏，失败也**不改界面**。
     * 这一点很重要 —— 用户此时正看着缓存内容，后台校验失败（比如手机刚切走
     * WiFi）如果弹出「读取配置失败」，会让人以为页面坏了，而实际上手上的数据
     * 完全可用。缓存的存在意义就是这种时候兜住。
     */
    fun loadTaskConfig(task: String, silent: Boolean = false) {
        val s = _ui.value
        if (!silent) _ui.update { it.copy(configLoading = true, configError = null) }

        viewModelScope.launch {
            val api = AzurPilotApi(s.serverUrl)
            try {
                val bundle = api.fetchConfigBundle(bridgeCandidates(), s.instance, task)

                // 用户可能已经返回或点了别的任务 —— 这时结果属于一个已经不显示的页面，
                // 写回去会让下一次打开闪现上一个任务的内容
                val route = _ui.value.route
                if (route !is Route.TaskConfig || route.task != task) return@launch

                val k = cache.key(s.serverUrl, s.instance, task)
                cache.put(k, task, bundle.schemaRaw, bundle.values)
                cache.flush()

                _ui.update {
                    it.copy(
                        configLoading = false,
                        configTask = bundle.task,
                        configValues = bundle.values,
                        configError = null,
                        configFromCache = false,
                        configCachedAt = System.currentTimeMillis(),
                        cacheEntries = cache.size(),
                    )
                }
            } catch (e: Exception) {
                if (silent) {
                    _ui.update { it.copy(configLoading = false) }
                } else {
                    _ui.update {
                        it.copy(
                            configLoading = false,
                            configError = e.message?.takeIf { m -> m.isNotBlank() } ?: "读取配置失败",
                        )
                    }
                }
            }
        }
    }

    /**
     * 写入一个配置项。
     *
     * 值需要按类型转换：开关发布尔，数字型 input 发数字 —— 否则
     * 服务端 cross_set 会把 "3" 当字符串存进去，Alas 读配置时会出问题。
     */
    fun saveConfigArg(groupKey: String, arg: ConfigArg, raw: Any?) {
        val s = _ui.value
        val task = (s.route as? Route.TaskConfig)?.task ?: return
        if (s.configSaving != null) return

        val value: Any? = when {
            arg.isSwitch -> when (raw) {
                is Boolean -> raw
                else -> raw?.toString()?.equals("true", ignoreCase = true) ?: false
            }
            arg.default is Number && raw is String -> raw.toDoubleOrNull() ?: raw
            else -> raw
        }

        viewModelScope.launch {
            _ui.update { it.copy(configSaving = "$groupKey.${arg.key}") }
            try {
                AzurPilotApi(s.serverUrl).updateConfig(s.instance, task, groupKey, arg.key, value)
                // 本地回填，避免整页重拉。
                // ★ 用 _ui.value 而不是协程外捕获的 s —— 用户可能已经连改了两项，
                //   拿旧快照回填会把前一次改的覆盖掉。
                val values = JSONObject(_ui.value.configValues.toString())
                val g = values.optJSONObject(groupKey) ?: JSONObject().also { values.put(groupKey, it) }
                g.put(arg.key, value)

                // ★ 同步进缓存：否则下次打开这个任务会先显示**改之前**的旧值，
                //   等后台校验回来才跳成新值 —— 看起来像「刚才那次没保存上」。
                cache.put(cache.key(s.serverUrl, s.instance, task), task, null, values)
                cache.flush()

                _ui.update { it.copy(configSaving = null, configValues = values, toast = "已保存 ${arg.name}") }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        configSaving = null,
                        toast = e.message?.takeIf { m -> m.isNotBlank() } ?: "保存失败",
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────
    // 耄耋相接 / 每日经验检测
    // ─────────────────────────────────────────────────────

    fun loadMeowAndExp(force: Boolean = false) {
        val s = _ui.value
        val api = AzurPilotApi(s.serverUrl)
        val bridges = bridgeCandidates()

        // 地址都还没填时，让人去 PC 上「双击 AzurRemBridge.exe」是南辕北辙 ——
        // 他连服务器地址都没填，第一步根本还没走到「网关」那一步。
        val hint = if (s.serverUrl.isBlank()) Settings.BLANK_URL_HINT else BRIDGE_DOWN_HINT

        // ★★ 这三条的「要不要拉」必须看**有没有真的拿到数据**，不能只看对象是不是 null。
        //
        //   原来三个 guard 都是 `s.xxx == null`，而失败分支塞进去的是
        //   `MeowStats(false, hint, emptyList())` 这种**非 null 的占位对象** ——
        //   于是首次请求一旦失败（网关还没起、刚改完地址还在重连、第一次进统计页时
        //   网络还没通…），`== null` 永远为假，这一格就**再也不会重试**，
        //   直到杀进程重开。
        //
        //   用户实测就是这个问题：资源趋势和委托收益都正常，只有
        //   「耄耋相接」和「每日经验检测」一直挂着「PC 上的网关没在运行」，
        //   而后端接口其实是好的（同一个网关的另外 7 个接口都通）。
        //
        //   `available` 是现成的失败标志；MeowHazard 没有这个字段，用 rows 空判断。
        val hazardStale = s.meowHazard == null || s.meowHazard.rows.isEmpty()
        val meowStale = s.meowStats == null || !s.meowStats.available
        val expStale = s.shipExp == null || !s.shipExp.available

        // 耄耋相接**数据收集**（按侵蚀等级 3/5 分组的场次与耗时）
        if (!s.meowHazardLoading && (force || hazardStale)) {
            viewModelScope.launch {
                _ui.update { it.copy(meowHazardLoading = true) }
                val result = runCatching { api.fetchMeowHazard(bridges, s.instance) }
                _ui.update {
                    it.copy(
                        meowHazardLoading = false,
                        meowHazard = result.getOrElse { MeowHazardStats("", emptyList()) },
                    )
                }
            }
        }

        // 耄耋相接**收获**（战利品 CSV）—— 和上面是两张表，PC 端两张都显示
        if (!s.meowLoading && (force || meowStale)) {
            viewModelScope.launch {
                _ui.update { it.copy(meowLoading = true) }
                val result = runCatching { api.fetchMeowStats(bridges, s.instance) }
                _ui.update {
                    it.copy(
                        meowLoading = false,
                        // 桥不可达时给能照着做的提示，不要把
                        // "Failed to connect to /<主机>:<端口>" 这种原始异常甩给用户
                        meowStats = result.getOrElse { MeowStats(false, hint, emptyList()) },
                    )
                }
            }
        }

        if (!s.shipExpLoading && (force || expStale)) {
            viewModelScope.launch {
                _ui.update { it.copy(shipExpLoading = true) }
                val result = runCatching { api.fetchShipExp(bridges, s.instance) }
                _ui.update {
                    it.copy(
                        shipExpLoading = false,
                        shipExp = result.getOrElse {
                            ShipExpStats(
                                available = false,
                                reason = hint,
                                lastCheckTime = "", targetLevel = 0,
                                avgBattleSeconds = 0.0, avgRoundSeconds = 0.0,
                                avgMeowBattleSeconds = 0.0,
                                ships = emptyList(), daily = emptyList(),
                            )
                        },
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────
    // 委托收益统计
    // ─────────────────────────────────────────────────────

    /**
     * 拉委托收益统计。
     *
     * [period] 变了要强制重拉（缓存里那份是别的周期的）；同一个周期则不重复请求 ——
     * 这个接口每次要读一个几 MB 的 SQLite 再聚合，切 Tab 时无脑重拉没必要。
     */
    fun loadCommission(period: CommissionPeriod? = null, force: Boolean = false) {
        val target = period ?: _ui.value.commissionPeriod
        val s = _ui.value
        if (s.commissionLoading) return
        if (!force && s.commission != null && s.commission.period == target) return

        // 切周期时立刻把选中态改掉，不然按钮要等网络回来才高亮
        _ui.update { it.copy(commissionPeriod = target, commissionLoading = true) }

        viewModelScope.launch {
            val api = AzurPilotApi(_ui.value.serverUrl)
            val result = runCatching {
                api.fetchCommissionIncome(bridgeCandidates(), _ui.value.instance, target)
            }
            _ui.update {
                it.copy(
                    commissionLoading = false,
                    commission = result.getOrNull(),
                    // 桥不可达时不把「读不到」当成「没有收益」——
                    // 前者要提示去开桥，后者是正常的零。这里的 null 让界面
                    // 走「读不到」那条分支，不会误报成「本月零收益」。
                    commissionError = result.exceptionOrNull()?.let { e ->
                        e.message?.takeIf { m -> m.isNotBlank() } ?: "取不到委托收益（网关没启动？）"
                    },
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────
    // 应用内更新
    // ─────────────────────────────────────────────────────

    /**
     * 当前安装的版本号。
     *
     * 从 PackageManager 读**实际安装的**值，而不是写死一个字符串 ——
     * 写死的话改了 build.gradle 忘了改代码，更新检查就会一直判错。
     */
    private fun installedVersion(): String {
        val ctx = getApplication<Application>()
        return runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    /** 检查 GitHub 上有没有新版本（匿名可查，不需要 token） */
    fun checkForUpdate() {
        if (_ui.value.updateChecking) return
        val current = installedVersion()
        _ui.update {
            it.copy(
                appVersion = current,
                updateChecking = true,
                updateMessage = null,
                updateAvailable = null,
            )
        }

        viewModelScope.launch {
            val result = UpdateChecker().check(current)
            _ui.update {
                when (result) {
                    is UpdateChecker.Result.Available -> it.copy(
                        updateChecking = false,
                        updateAvailable = result.info,
                        updateMessage = "有新版 ${result.info.version}",
                    )

                    is UpdateChecker.Result.UpToDate -> it.copy(
                        updateChecking = false,
                        updateAvailable = null,
                        updateMessage = "已是最新版本（$current）",
                    )

                    is UpdateChecker.Result.Failed -> it.copy(
                        updateChecking = false,
                        updateAvailable = null,
                        updateMessage = result.reason,
                    )
                }
            }
        }
    }

    /**
     * 下载新版 APK。
     *
     * 下载完**交给系统安装器**，不在应用内自己装 —— 自己装要么需要 root，
     * 要么要跟 PackageInstaller 的会话机制缠斗，而系统安装器自带
     * 签名校验、权限确认和「未知来源」引导，出问题时的提示也标准。
     *
     * 界面上拿到的 APK 路径会在 [installDownloadedApk] 里用。
     */
    fun downloadUpdate() {
        val info = _ui.value.updateAvailable ?: return
        if (_ui.value.updateProgress != null) return      // 已经在下了

        val ctx = getApplication<Application>()
        val dir = java.io.File(ctx.filesDir, "updates")

        viewModelScope.launch {
            _ui.update { it.copy(updateProgress = 0L to info.apkSize) }
            runCatching {
                // 进度回调用的是同步 IO 线程，这里只在整数百分比变化时更新状态，
                // 否则 13MB 会触发上千次 StateFlow 更新，界面反而卡
                var lastPct = -1
                UpdateChecker().download(info, dir) { received, total ->
                    val pct = if (total > 0) ((received * 100) / total).toInt() else -1
                    if (pct != lastPct) {
                        lastPct = pct
                        _ui.value = _ui.value.copy(updateProgress = received to total)
                    }
                }
            }.fold(
                onSuccess = { file ->
                    _ui.update {
                        it.copy(
                            updateProgress = null,
                            updateMessage = "下载完成，正在打开安装程序…",
                        )
                    }
                    installDownloadedApk(file)
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(
                            updateProgress = null,
                            updateMessage = e.message?.takeIf { m -> m.isNotBlank() }
                                ?: "下载失败",
                        )
                    }
                },
            )
        }
    }

    /**
     * 把下载好的 APK 交给系统安装器。
     *
     * 必须走 FileProvider 的 content:// —— Android 7.0 起把 file:// 直接传给
     * 别的应用会抛 FileUriExposedException（安装器是另一个进程，正属于这种情况）。
     */
    private fun installDownloadedApk(file: java.io.File) {
        val ctx = getApplication<Application>()
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file,
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(
                    uri,
                    "application/vnd.android.package-archive",
                )
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }.onFailure { e ->
            // 最常见的是用户没给「安装未知应用」权限。系统那条路会自己弹引导，
            // 但如果我们连 Activity 都拉不起来，就得在这里说清楚。
            _ui.update {
                it.copy(
                    updateMessage = "打不开安装程序：${e.message ?: "未知原因"}。" +
                        "请到系统设置里允许本应用「安装未知应用」后重试。",
                )
            }
        }
    }

    fun dismissUpdate() = _ui.update {
        it.copy(updateAvailable = null, updateMessage = null, updateProgress = null)
    }

    fun updateServerUrl(url: String) {        settings.serverUrl = url
        // 网关地址留空时是跟着服务器主机走的，所以这里要一起刷新
        _ui.update {
            it.copy(
                serverUrl = settings.serverUrl,
                bridgeUrl = settings.effectiveBridgeUrl,
                error = null,
                connected = false,
            )
        }
        restartPolling()
        // 缓存键带 server —— 换了服务器就是另一套配置，重新预热
        prefetchAllConfigs(force = true)
    }

    fun updateBridgeUrl(url: String) {
        settings.bridgeUrl = url
        _ui.update { it.copy(bridgeUrl = settings.effectiveBridgeUrl) }
    }

    /**
     * 改 WebUI 密码。
     *
     * 改完**立刻重启轮询**：密码填对了下一轮就连上，填错了也应该马上看到
     * 「HTTP 401」—— 而不是对着旧状态干等一个轮询周期（默认 30 秒）才反应过来。
     */
    fun updateWebuiPassword(password: String) {
        settings.webuiPassword = password
        McpAuth.configure(settings.webuiPassword)
        _ui.update {
            it.copy(
                webuiPassword = settings.webuiPassword,
                error = null,
                connected = false,
            )
        }
        restartPolling()
    }

    fun updateInstance(name: String) {
        settings.instance = name.trim().ifBlank { Settings.DEFAULT_INSTANCE }
        _ui.update { it.copy(instance = settings.instance) }
        restartPolling()
        // 缓存键带 instance —— 换实例后旧缓存全部不适用，重新预热
        prefetchAllConfigs(force = true)
    }

    fun updatePollSeconds(sec: Int) {
        settings.pollSeconds = sec
        _ui.update { it.copy(pollSeconds = settings.pollSeconds) }
        restartPolling()
    }

    fun updateLogLines(lines: Int) {
        settings.logLines = lines
        // 重置 offset，否则新的行数要等下次进日志页才生效
        _ui.update { it.copy(logLines = settings.logLines, logOffset = 0L) }
    }

    fun updateThemeMode(mode: Int) {
        settings.themeMode = mode
        _ui.update { it.copy(themeMode = mode) }
    }

    /**
     * 清屏。
     *
     * **不动 `logOffset`** —— 它记的是"文件读到哪了"，跟屏幕上显示多少行无关。
     * 清了之后新日志继续从那个位置往后追加，这才是用户想要的"清屏"。
     */
    fun clearLogs() = _ui.update { it.copy(logs = emptyList()) }

    fun onFabMoved(onRight: Boolean, yFraction: Float) {
        settings.fabOnRight = onRight
        settings.fabYFraction = yFraction
        _ui.update { it.copy(fabOnRight = onRight, fabYFraction = yFraction) }
    }

    private fun restartPolling() {
        _ui.update { it.copy(logs = emptyList(), loading = true) }
        startPolling()
    }
}
