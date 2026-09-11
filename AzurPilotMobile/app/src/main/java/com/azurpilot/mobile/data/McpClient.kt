package com.azurpilot.mobile.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class McpException(message: String) : Exception(message)

/**
 * 一轮 MCP 会话（MCP 的 SSE 传输变体）。
 *
 *   GET  <origin>/mcp/sse        → SSE 流
 *        首帧 event: endpoint
 *             data: /mcp/mcp/messages?session_id=xxx
 *                   ↑ 注意是 **双 mcp** —— SseServerTransport("/mcp/messages")
 *                     被挂载在 "/mcp" 下导致的。所以必须用服务端给的这段
 *                     路径，不能自己拼 /mcp/messages。
 *   POST <origin> + 上面那段 data  → 发 JSON-RPC，响应从 SSE 流回来
 *
 * 每轮开一条新会话、用完即关：天然抗断线，也正好贴合 30s 轮询。
 */
class McpSession(baseUrl: String) : AutoCloseable {

    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    private val nextId = AtomicInteger(1)
    private val endpointReady = CompletableDeferred<String>()
    private val closed = AtomicBoolean(false)
    @Volatile private var source: EventSource? = null
    private val origin: String

    init {
        val parsed = baseUrl.trim().toHttpUrlOrNull()
            ?: throw McpException("服务器地址无效：$baseUrl")
        origin = "${parsed.scheme}://${parsed.host}:${parsed.port}"
        startSse()
    }

    private fun startSse() {
        val request = Request.Builder()
            .url("$origin/mcp/sse")
            .header("Accept", "text/event-stream")
            .get()
            .build()

        source = EventSources.createFactory(HTTP).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    when (type) {
                        "endpoint" -> {
                            val path = data.trim()
                            val full = if (path.startsWith("http")) {
                                path
                            } else {
                                origin + if (path.startsWith("/")) path else "/$path"
                            }
                            endpointReady.complete(full)
                        }
                        "message" -> dispatch(data)
                        else -> Unit
                    }
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?,
                ) {
                    val msg = buildString {
                        append("无法连接 AzurPilot")
                        t?.message?.takeIf { it.isNotBlank() }?.let { append("：$it") }
                        response?.let { append("（HTTP ${it.code}）") }
                    }
                    if (!endpointReady.isCompleted) endpointReady.completeExceptionally(McpException(msg))
                    failAll(msg)
                }

                override fun onClosed(eventSource: EventSource) {
                    failAll("SSE 连接已被服务端关闭")
                }
            },
        )
    }

    private fun dispatch(payload: String) {
        val obj = try {
            JSONObject(payload)
        } catch (_: Exception) {
            return
        }
        if (!obj.has("id") || obj.isNull("id")) return // 通知类消息，忽略
        val id = obj.optInt("id", Int.MIN_VALUE)
        if (id == Int.MIN_VALUE) return
        pending.remove(id)?.complete(obj)
    }

    private fun failAll(message: String) {
        val it = pending.values.iterator()
        while (it.hasNext()) {
            it.next().completeExceptionally(McpException(message))
            it.remove()
        }
    }

    private suspend fun post(url: String, json: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA))
            .build()
        HTTP.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw McpException("服务端拒绝了请求（HTTP ${resp.code}）")
            }
        }
    }

    private suspend fun awaitEndpoint(timeoutMs: Long): String =
        withTimeoutOrNull(timeoutMs) { endpointReady.await() }
            ?: throw McpException("等待 MCP 会话超时")

    suspend fun rpc(method: String, params: JSONObject? = null, timeoutMs: Long = 20_000): JSONObject {
        val url = awaitEndpoint(timeoutMs)
        val id = nextId.getAndIncrement()
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        try {
            post(url, payload.toString())
            val resp = withTimeoutOrNull(timeoutMs) { deferred.await() }
                ?: throw McpException("调用 $method 超时")
            resp.optJSONObject("error")?.let {
                throw McpException(it.optString("message").ifBlank { "MCP 调用失败" })
            }
            return resp
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun notify(method: String, params: JSONObject? = null) {
        val url = awaitEndpoint(20_000)
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            if (params != null) put("params", params)
        }
        post(url, payload.toString())
    }

    suspend fun initialize() {
        rpc(
            "initialize",
            JSONObject().apply {
                put("protocolVersion", "2024-11-05")
                put("capabilities", JSONObject())
                put("clientInfo", JSONObject().apply {
                    put("name", "AzurPilot Mobile")
                    put("version", "1.0")
                })
            },
        )
        notify("notifications/initialized")
    }

    /**
     * 调用一个 MCP 工具。
     *
     * 注意返回是「JSON 字符串套娃」：
     *   result.content[0].text 里才是真正的数据，需要二次解析。
     *
     * 另外服务端的工具错误**不会**置 isError，而是把 "Error: ..." 当普通文本返回，
     * 所以这里两种都要判。
     */
    suspend fun callTool(
        name: String,
        arguments: JSONObject = JSONObject(),
        timeoutMs: Long = 25_000,
    ): String {
        val resp = rpc(
            "tools/call",
            JSONObject().apply {
                put("name", name)
                put("arguments", arguments)
            },
            timeoutMs,
        )
        val result = resp.optJSONObject("result")
            ?: throw McpException("$name 的响应里没有 result")
        val text = extractText(result)
        if (result.optBoolean("isError", false) || text.startsWith("Error:")) {
            val detail = text.removePrefix("Error:").trim()
            throw McpException(detail.ifBlank { "$name 执行失败" })
        }
        return text
    }

    private fun extractText(result: JSONObject): String {
        val content = result.optJSONArray("content") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val item = content.optJSONObject(i) ?: continue
            if (item.optString("type") == "text") sb.append(item.optString("text"))
        }
        return sb.toString()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            source?.cancel()
        } catch (_: Exception) {
        }
        source = null
        failAll("会话已关闭")
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** 全局共享：连接池复用，避免每 30s 重建 */
        val HTTP: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                // SSE 是长连接，读超时必须关掉
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        /**
         * 普通 HTTP（数据桥的 `api` 系列接口）专用。
         *
         * ★ 之前这些请求复用了 [HTTP]，而它为了 SSE 把 `readTimeout` 设成了 **0 = 永不超时**。
         * 对长连接是对的，对一次性 GET 是灾难：对端接受了连接却不回数据时，
         * 这个请求会**永远挂着**，界面就一直转圈，且没有任何日志能看出卡在哪。
         *
         * 桥的接口实测都在 20ms 级（最慢的 `/api/task_schema` 也就 100ms），
         * 12 秒读超时是极宽松的上限；`callTimeout` 再兜一层，保证**一定有结局**。
         *
         * 用 `newBuilder()` 派生而不是新建 —— 连接池和线程池跟着 [HTTP]，
         * 不会多占一份资源。
         *
         * 注：别在这里写通配路径 `api` + 斜杠 + 星号 —— Kotlin 的块注释**可以嵌套**，
         * 那个星号会开一个内层注释把后面整个文件吞掉（compiler 只会报
         * 文件末尾 "Unclosed comment"，很难往回找）。这个坑已经踩过一次。
         */
        val PLAIN: OkHttpClient by lazy {
            HTTP.newBuilder()
                .readTimeout(12, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}

/**
 * 行动力曲线的一个采样点（来自纯 HTTP 接口 /api/ap_timeline）。
 *
 * 快照里同时有 `ap`（当前剩余）和 `ap_total`（含未开的行动力箱）。
 * 实际数据实测 3097/3097 都带 ap_total，画曲线要用它 —— 当前值在 0~200 之间
 * 反复横跳，看不出趋势；总量才是真正在消耗的东西。
 */
data class ApPoint(
    val epochMillis: Long,
    val ap: Int,
    val apTotal: Int?,
    val source: String,
) {
    /** 画曲线用的值：优先总行动力，字段缺失时退回当前值 */
    val plotValue: Int get() = apTotal ?: ap
}

/** 把 [ApPoint] 归一成通用趋势点 */
fun List<ApPoint>.toTrendPoints(): List<TrendPoint> =
    map { TrendPoint(it.epochMillis, it.plotValue.toLong()) }

/**
 * 通用趋势点（用于资源历史，来自 sidecar 的 /api/resource_history）
 *
 * 注意与 [ApPoint] 分开：行动力那个接口的字段名是 `ap`，资源历史是 `v`。
 */
data class TrendPoint(val epochMillis: Long, val value: Long)

/** 一次资源历史查询的结果 */
data class ResourceHistory(
    val from: String?,
    val to: String?,
    val sampleCount: Int,
    val series: Map<String, List<TrendPoint>>,
)

/**
 * 按桶降采样。
 *
 * 服务端会返回全量采样点（实测 3097 个），直接画进 ~900px 的图里就是一团噪声。
 * 平均之后保留趋势形状，点数降到可控范围。
 */
fun downsampleAp(points: List<ApPoint>, buckets: Int = 90): List<ApPoint> =
    downsampleTrend(points.toTrendPoints(), buckets).map {
        ApPoint(epochMillis = it.epochMillis, ap = it.value.toInt(), apTotal = it.value.toInt(), source = "")
    }

/**
 * 大世界月度统计 —— 即「侵蚀1 · 本月大世界」那张表。
 *
 * ⚠️ **口径对齐的是 PC 页面，不是 `/api/cl1_stats`。**
 * PC 页面用的是 `module/webui/app_stat_opsi.py:159-307 _build_cl1_summary()`，
 * 而 REST 接口走的是 `module/statistics/opsi_month.py:48-99 get_detailed_summary()`，
 * **两者对同名列用了完全不同的公式**：
 *
 * | 列 | PC 页面 | REST 接口 |
 * |---|---|---|
 * | 战斗轮次 | `(场次 + 1) // 2` | `场次 // 2` |
 * | 出击消耗 | `轮次 × 5` | `轮次 × 120` |
 * | 净赚体力 | `当月购买体力 − 出击消耗` | `akashi_ap` |
 *
 * 最坑的一点：接口里那个叫 `net_stamina_gain` 的字段，**值是 `akashi_ap`**，
 * 也就是 PC 上的「当月购买体力」，根本不是净赚。
 * 所以这里保留原始字段，派生值一律按 PC 口径在客户端重算。
 */
data class Cl1Stats(
    val month: String,
    /** 战斗场次 */
    val battleCount: Int,
    /** 遇见明石次数 */
    val akashiEncounters: Int,
    /** 吊机（塞壬研究装置）次数 */
    val sirenResearchDevices: Int,
    /** 当月购买体力 —— 来自接口的 `net_stamina_gain`（字段名与语义不符） */
    val apBought: Int,
    /** 战斗轮次 = (场次 + 1) / 2 */
    val battleRounds: Int,
    /** 出击消耗 = 轮次 × 5 */
    val sortieCost: Long,
    /** 净赚体力 = 当月购买体力 − 出击消耗 */
    val netAp: Long,
    /** 循环效率 % = 净赚 / 出击消耗 × 100 */
    val loopEfficiency: Double,
    /** 遇见明石概率 % */
    val akashiRate: Double,
    /** 吊机概率 % */
    val sirenRate: Double,
    /** 平均体力 = 当月购买体力 / 明石次数，四舍五入到整数 */
    val averageStamina: Int,
)

/** 按 PC 口径（`app_stat_opsi.py:159-234`）从 `/api/cl1_stats` 的原始值派生 */
private fun deriveCl1(
    month: String,
    battleCount: Int,
    akashiEncounters: Int,
    sirenResearchDevices: Int,
    apBought: Int,
): Cl1Stats {
    val rounds = (battleCount + 1) / 2
    val sortieCost = rounds.toLong() * 5L
    val netAp = apBought.toLong() - sortieCost
    return Cl1Stats(
        month = month,
        battleCount = battleCount,
        akashiEncounters = akashiEncounters,
        sirenResearchDevices = sirenResearchDevices,
        apBought = apBought,
        battleRounds = rounds,
        sortieCost = sortieCost,
        netAp = netAp,
        loopEfficiency = if (sortieCost > 0) netAp.toDouble() / sortieCost * 100.0 else 0.0,
        akashiRate = if (rounds > 0) akashiEncounters.toDouble() / rounds * 100.0 else 0.0,
        sirenRate = if (rounds > 0) sirenResearchDevices.toDouble() / rounds * 100.0 else 0.0,
        // PC 端是 int(ap_bought / ak + 0.5)，即四舍五入到整数（显示 115 而不是 115.2）
        averageStamina = if (akashiEncounters > 0) {
            (apBought.toDouble() / akashiEncounters + 0.5).toInt()
        } else {
            0
        },
    )
}

/**
 * 「一个任务配置页要的全部东西」——结构元数据 + 用户当前值。
 *
 * [schemaRaw] 单独留一份原始 JSON 是给缓存用的：见 [AzurPilotApi.fetchConfigBundle]。
 * 走 MCP `get_task_help` 兜底时为 null（那条路给的是另一种结构，不能混进缓存）。
 */
data class ConfigBundle(
    val task: TaskConfig,
    val schemaRaw: JSONObject?,
    val values: JSONObject,
)

class AzurPilotApi(private val baseUrl: String) {

    private companion object {
        /**
         * 预缓存时并发抓「结构」的路数。
         *
         * 结构走的是数据桥（Python `ThreadingHTTPServer`，能并发），
         * 6 路是压着手机 WiFi 的舒适区取的：再多收益就很小了，
         * 而单次只要 14ms，93 个任务 6 路也就 ~300ms。
         *
         * **当前值不走这个并发** —— 服务端是 uvicorn 单事件循环，
         * 并发打过去只会一起排在更长的队里。见 [prefetchTaskConfigs]。
         */
        const val SCHEMA_CONCURRENCY = 6

        /**
         * 上一次**成功**用过的数据桥地址。
         *
         * `bridgeCandidates()` 里固定带着一个 `:25549`（早期版本桥的端口），
         * 绝大多数机器上那个端口根本没开。不记住成功地址的话，每次桥调用都要
         * 白试一遍死端口 —— 主机在线但端口关闭时通常会立刻回 RST（快），
         * 可一旦遇上防火墙丢包 / 手机 WiFi 省电，就要一直等到 8 秒连接超时，
         * 用户看到的就是「点一下卡 8 秒」。
         *
         * 粘住之后正常路径只剩**一次**请求。用户改地址或换主机时，
         * [orderCandidates] 会发现粘住的地址不在候选里而自动丢弃。
         */
        @Volatile
        private var stickyBridge: String? = null

        /**
         * 把上次成功的地址提到最前，其余保持原顺序。
         *
         * 只做重排、**不删任何候选** —— 粘住的桥如果后来关掉了，
         * 后面的候选仍然会被轮到，不会因为「粘性」而彻底连不上。
         */
        fun orderCandidates(baseUrls: List<String>): List<String> {
            val cleaned = baseUrls.map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }
            val sticky = stickyBridge
            if (sticky == null || sticky !in cleaned) {
                // 用户改了地址/换了主机 —— 旧的粘性记录作废
                if (sticky != null) stickyBridge = null
                return cleaned
            }
            return listOf(sticky) + cleaned.filter { it != sticky }
        }

        fun markBridgeWorking(base: String) {
            stickyBridge = base
        }

        /** 旧版 /api/history 每行是一条完整快照，列名与 Dashboard 的对应关系 */
        val LEGACY_COLUMNS = listOf(
            "Oil" to "oil",
            "Coin" to "coin",
            "Gem" to "gem",
            "Pt" to "pt",
            "Cube" to "cube",
            "Core" to "core",
            "Medal" to "medal",
            "Merit" to "merit",
            "GuildCoin" to "guild_coin",
            "ActionPoint" to "action_point",
            "YellowCoin" to "yellow_coin",
            "PurpleCoin" to "purple_coin",
        )
    }

    private val origin: String = run {
        val parsed = baseUrl.trim().toHttpUrlOrNull() ?: throw McpException("服务器地址无效：$baseUrl")
        "${parsed.scheme}://${parsed.host}:${parsed.port}"
    }

    /** 一次轮询：状态 + 资源 + 当前任务 + 调度队列 + 日志，共用一条 MCP 会话 */
    suspend fun fetchSnapshot(instance: String, logLines: Int): Snapshot = withContext(Dispatchers.IO) {
        McpSession(baseUrl).use { session ->
            session.initialize()

            val (running, stateCode) = parseStatus(session.callTool("get_status"), instance)

            val resources = parseResources(
                session.callTool("get_resources", JSONObject().put("instance", instance)),
            )

            val currentTask = runCatching {
                session.callTool("get_current_running_task", JSONObject().put("instance", instance)).trim()
            }.getOrDefault("")

            val queue = runCatching {
                parseQueue(session.callTool("get_scheduler_queue", JSONObject().put("instance", instance)))
            }.getOrDefault(emptyList())

            val logs = runCatching {
                parseLogs(
                    session.callTool(
                        "get_recent_logs",
                        JSONObject().apply {
                            put("instance", instance)
                            put("lines", logLines)
                        },
                    ),
                )
            }.getOrDefault(emptyList())

            Snapshot(
                running = running,
                stateCode = stateCode,
                currentTask = currentTask.ifBlank { "—" },
                resources = resources,
                queue = queue,
                logs = logs,
                fetchedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun listInstances(): List<String> = withContext(Dispatchers.IO) {
        McpSession(baseUrl).use { session ->
            session.initialize()
            val arr = JSONArray(session.callTool("list_instances"))
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        }
    }

    suspend fun startInstance(instance: String): String = withContext(Dispatchers.IO) {
        McpSession(baseUrl).use { session ->
            session.initialize()
            session.callTool("start_instance", JSONObject().put("instance", instance))
        }
    }

    suspend fun stopInstance(instance: String): String = withContext(Dispatchers.IO) {
        McpSession(baseUrl).use { session ->
            session.initialize()
            session.callTool("stop_instance", JSONObject().put("instance", instance))
        }
    }

    suspend fun triggerTask(instance: String, task: String): String = withContext(Dispatchers.IO) {
        McpSession(baseUrl).use { session ->
            session.initialize()
            session.callTool(
                "trigger_task",
                JSONObject().apply {
                    put("instance", instance)
                    put("task", task)
                },
            )
        }
    }

    // ── 纯 HTTP 接口（非 MCP，无鉴权） ──────────────────────

    suspend fun fetchApTimeline(instance: String): List<ApPoint> = withContext(Dispatchers.IO) {
        val json = httpGet("$origin/api/ap_timeline?instance=$instance")
        val data = JSONObject(json).optJSONArray("data") ?: return@withContext emptyList()
        (0 until data.length()).mapNotNull { i ->
            val o = data.optJSONObject(i) ?: return@mapNotNull null
            val ap = o.optLongOrNull("ap")?.toInt() ?: return@mapNotNull null
            val epoch = parseIsoToEpoch(o.optString("ts")) ?: return@mapNotNull null
            ApPoint(
                epochMillis = epoch,
                ap = ap,
                apTotal = o.optLongOrNull("ap_total")?.toInt(),
                source = o.optString("source"),
            )
        }.sortedBy { it.epochMillis }
    }

    suspend fun fetchCl1Stats(instance: String): Cl1Stats? = withContext(Dispatchers.IO) {
        runCatching {
            val json = httpGet("$origin/api/cl1_stats?instance=$instance")
            val d = JSONObject(json).optJSONObject("data") ?: return@runCatching null
            deriveCl1(
                month = d.optString("month"),
                battleCount = d.optInt("battle_count"),
                akashiEncounters = d.optInt("akashi_encounters"),
                sirenResearchDevices = d.optInt("siren_research_devices"),
                // ⚠️ net_stamina_gain 的实际语义是 akashi_ap（当月购买体力），不是净赚
                apBought = d.optInt("net_stamina_gain"),
            )
        }.getOrNull()
    }

    /**
     * 全资源历史趋势 —— 走 sidecar 数据桥。
     *
     * AzurPilot 自带接口里没有资源历史，数据只存在 config/azurstats_local.db。
     * 这里兼容两种桥的返回格式：
     *   1. `bridge/mobile_bridge.py` 的 `/api/resource_history`
     *      —— 服务端已按桶降采样，payload 小，**优先**
     *   2. 早期版本 `/api/history`
     *      —— 返回原始行（实测 1000 点 ≈ 250KB），需要客户端自己降采样
     */
    suspend fun fetchResourceHistory(
        baseUrls: List<String>,
        instance: String,
        hours: Int,
        buckets: Int,
    ): ResourceHistory = withContext(Dispatchers.IO) {
        val primary = runCatching {
            parseResourceHistory(
                bridgeGet(baseUrls, "/api/resource_history?instance=$instance&hours=$hours&buckets=$buckets"),
            )
        }
        primary.getOrNull()?.let { if (it.series.isNotEmpty()) return@withContext it }

        // 退回旧接口（早期版本的数据桥只有 /api/history，返回的是 {"ok": ...} 而不是 {"success": ...}）
        val fallback = runCatching {
            parseLegacyHistory(
                bridgeGet(
                    baseUrls,
                    "/api/history?instance=$instance&hours=$hours",
                    accept = { it.optBoolean("ok", false) },
                ),
                buckets,
            )
        }
        fallback.getOrNull()?.let { if (it.series.isNotEmpty()) return@withContext it }

        primary.exceptionOrNull()?.let { throw it }
        fallback.exceptionOrNull()?.let { throw it }
        throw McpException("数据桥没有返回资源历史")
    }

    private fun parseResourceHistory(root: JSONObject): ResourceHistory {
        val data = root.optJSONObject("data")
            ?: return ResourceHistory(null, null, 0, emptyMap())

        val resObj = data.optJSONObject("resources")
        val series = LinkedHashMap<String, List<TrendPoint>>()
        if (resObj != null) {
            for (key in resObj.keys()) {
                val arr = resObj.optJSONArray(key) ?: continue
                val points = ArrayList<TrendPoint>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val epoch = parseIsoToEpoch(o.optString("ts")) ?: continue
                    val value = o.optLongOrNull("v") ?: continue
                    points += TrendPoint(epoch, value)
                }
                if (points.size >= 2) series[key] = points
            }
        }

        return ResourceHistory(
            from = data.optString("from").takeIf { it.isNotBlank() },
            to = data.optString("to").takeIf { it.isNotBlank() },
            sampleCount = data.optInt("points"),
            series = series,
        )
    }

    /** 旧版 `/api/history`：每行是一条完整快照，取后再按桶降采样 */
    private fun parseLegacyHistory(root: JSONObject, buckets: Int): ResourceHistory {
        val points = root.optJSONArray("points")
            ?: return ResourceHistory(null, null, 0, emptyMap())

        val raw = LinkedHashMap<String, MutableList<TrendPoint>>()
        var firstTs: String? = null
        var lastTs: String? = null

        for (i in 0 until points.length()) {
            val o = points.optJSONObject(i) ?: continue
            val ts = o.optString("ts")
            if (firstTs == null) firstTs = ts
            lastTs = ts
            val epoch = parseIsoToEpoch(ts) ?: continue
            for ((key, column) in LEGACY_COLUMNS) {
                val value = o.optLongOrNull(column) ?: continue
                raw.getOrPut(key) { ArrayList() } += TrendPoint(epoch, value)
            }
        }

        val series = raw
            .mapValues { (_, list) -> downsampleTrend(list, buckets) }
            .filterValues { it.size >= 2 }

        return ResourceHistory(
            from = firstTs,
            to = lastTs,
            sampleCount = root.optInt("count", points.length()),
            series = series,
        )
    }

    // ── 数据桥（sidecar）─────────────────────────────────────
    //
    // 25549 上可能跑着早期版本的数据桥（只有 /api/history），新接口在 25550。
    // 所以这里接受一组候选地址，依次尝试。

    /**
     * 依次尝试候选数据桥，返回第一个**内容有效**的响应。
     *
     * 关键点：校验必须放在循环**内部**。早期版本的数据桥对未知路径返回的是
     * HTTP 200 + `{"ok": false, "error": "未知路径"}` 而不是 404 —— 如果把校验
     * 放在循环外，第一个候选（旧桥）就会「成功」返回错误 JSON，永远不会去试
     * 第二个候选，新接口全部静默失效。
     */
    private suspend fun bridgeGet(
        baseUrls: List<String>,
        path: String,
        accept: (JSONObject) -> Boolean = { it.optBoolean("success", false) },
    ): JSONObject {
        var lastError: Exception? = null
        // 上次成功的地址排最前 —— 正常路径只剩一次请求，不再白试死端口
        for (base in orderCandidates(baseUrls)) {
            try {
                val root = JSONObject(httpGet("$base$path"))
                if (!accept(root)) {
                    throw McpException(
                        root.optString("error").ifBlank { "数据桥返回失败" },
                    )
                }
                markBridgeWorking(base)
                return root
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: McpException("没有可用的数据桥地址")
    }

    /** 任务菜单树（图片里那个左栏）：10 个分组、93 个任务，带中文名 */
    suspend fun fetchTaskTree(baseUrls: List<String>): TaskTree = withContext(Dispatchers.IO) {
        val data = bridgeGet(baseUrls, "/api/task_tree").optJSONObject("data")
            ?: return@withContext TaskTree(emptyList())

        val groupsArr = data.optJSONArray("groups") ?: return@withContext TaskTree(emptyList())
        val groups = ArrayList<MenuGroup>(groupsArr.length())
        for (i in 0 until groupsArr.length()) {
            val g = groupsArr.optJSONObject(i) ?: continue
            val tasksArr = g.optJSONArray("tasks") ?: JSONArray()
            val tasks = ArrayList<MenuItem>(tasksArr.length())
            for (j in 0 until tasksArr.length()) {
                val t = tasksArr.optJSONObject(j) ?: continue
                val key = t.optString("key").ifBlank { continue }
                tasks += MenuItem(key, t.optString("name").ifBlank { key })
            }
            groups += MenuGroup(
                key = g.optString("key"),
                name = g.optString("name").ifBlank { g.optString("key") },
                page = g.optString("page", "setting"),
                collapsible = g.optBoolean("collapsible", true),
                tasks = tasks,
            )
        }
        TaskTree(groups)
    }

    /** 耄耋相接（指挥喵）收获统计 */
    suspend fun fetchMeowStats(baseUrls: List<String>, instance: String): MeowStats =
        withContext(Dispatchers.IO) {
            val data = bridgeGet(baseUrls, "/api/meow_stats?instance=$instance")
                .optJSONObject("data") ?: return@withContext MeowStats(false, "数据桥没有返回数据", emptyList())

            val arr = data.optJSONArray("rows") ?: JSONArray()
            val rows = ArrayList<MeowRow>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                rows += MeowRow(
                    level = o.optInt("level"),
                    rounds = o.optInt("rounds"),
                    coinPerRound = o.optDouble("coinPerRound", 0.0),
                    goldPerRound = o.optDouble("goldPerRound", 0.0),
                    abyssPerRound = o.optDouble("abyssPerRound", 0.0),
                    obscurePerRound = o.optDouble("obscurePerRound", 0.0),
                )
            }
            MeowStats(
                available = data.optBoolean("available", false),
                reason = if (rows.isEmpty()) "还没有耄耋相接数据" else null,
                rows = rows,
            )
        }

    /** 每日经验检测 */
    suspend fun fetchShipExp(baseUrls: List<String>, instance: String): ShipExpStats =
        withContext(Dispatchers.IO) {
            val data = bridgeGet(baseUrls, "/api/ship_exp?instance=$instance")
                .optJSONObject("data")
                ?: return@withContext ShipExpStats(false, "数据桥没有返回数据", "", 0, 0.0, 0.0, 0.0, emptyList(), emptyList())

            val shipsArr = data.optJSONArray("ships") ?: JSONArray()
            val ships = ArrayList<ShipExpRow>(shipsArr.length())
            for (i in 0 until shipsArr.length()) {
                val o = shipsArr.optJSONObject(i) ?: continue
                ships += ShipExpRow(
                    position = o.optInt("position"),
                    level = o.optInt("level"),
                    currentExp = o.optLong("currentExp"),
                    totalExp = o.optLong("totalExp"),
                    targetExp = o.optLong("targetExp"),
                    expNeeded = o.optLong("expNeeded"),
                    battlesNeeded = o.optInt("battlesNeeded"),
                    timeNeeded = o.optString("timeNeeded"),
                )
            }

            val dailyArr = data.optJSONArray("daily") ?: JSONArray()
            val daily = ArrayList<ShipExpDaily>(dailyArr.length())
            for (i in 0 until dailyArr.length()) {
                val o = dailyArr.optJSONObject(i) ?: continue
                daily += ShipExpDaily(
                    date = o.optString("date"),
                    battleCount = o.optInt("battleCount"),
                    expGained = o.optDouble("expGained", 0.0),
                    expPerHour = o.optDouble("expPerHour", 0.0),
                    runTime = o.optDouble("runTime", 0.0),
                )
            }

            ShipExpStats(
                available = data.optBoolean("available", false),
                reason = data.optString("reason").takeIf { it.isNotBlank() },
                lastCheckTime = data.optString("lastCheckTime"),
                targetLevel = data.optInt("targetLevel"),
                avgBattleSeconds = data.optDouble("avgBattleSeconds", 0.0),
                avgRoundSeconds = data.optDouble("avgRoundSeconds", 0.0),
                avgMeowBattleSeconds = data.optDouble("avgMeowBattleSeconds", 0.0),
                ships = ships,
                daily = daily,
                expPerHour = data.optDouble("expPerHour", 0.0),
                todayBattleCount = data.optInt("todayBattleCount", 0),
                todayExp = data.optLong("todayExp", 0L),
                todayRunMinutes = data.optInt("todayRunMinutes", 0),
            )
        }

    /**
     * 增量拉日志 —— 桥的 `/api/logs/tail`。
     *
     * 为什么不继续用 MCP 的 `get_recent_logs`：它每次都 `readlines()` **整读**
     * 日志文件（实测 16.2 MB / 12.4 万行）再取尾部 N 行。30 秒一次还能忍，
     * 想做到「准实时」就完全不可行 —— 那是每秒读 16MB。
     *
     * @param offset 上次返回的 offset；传 0 表示首次/重置，桥会给最后 [tailLines] 行
     */
    suspend fun fetchLogTail(
        baseUrls: List<String>,
        instance: String,
        offset: Long,
        tailLines: Int,
    ): LogTail? = withContext(Dispatchers.IO) {
        val path = "/api/logs/tail?instance=$instance&offset=$offset&tail_lines=$tailLines"
        runCatching {
            val data = bridgeGet(baseUrls, path).optJSONObject("data") ?: return@runCatching null
            LogTail(
                file = data.optString("file"),
                size = data.optLong("size", 0L),
                offset = data.optLong("offset", 0L),
                reset = data.optBoolean("reset", false),
                truncated = data.optBoolean("truncated", false),
                // 桥已经做过 trimEnd 和 np.int64 清洗，这里只兜一次底
                lines = data.optJSONArray("lines")?.let { arr ->
                    (0 until arr.length()).map { i -> cleanLogLine(arr.optString(i)) }
                } ?: emptyList(),
            )
        }.getOrNull()
    }

    /**
     * 概览页的队列分段 —— 走数据桥，和 PC 的 `app_dashboard.py:35-58` 同源。
     *
     * 桥只返回 **pending / waiting** 两段：桥是独立进程，拿不到 `alive`，
     * 而「运行中」那一段完全取决于 alive。所以切分放在客户端
     * （`Models.kt` 的 `splitOverview()`，逐行对齐 PC）。
     *
     * 和 MCP 的 `get_scheduler_queue` 的区别（这才是之前对不上的原因）：
     *   - 桥这边 **pending 是按 SCHEDULER_PRIORITY 优先级排序**的，
     *     MCP 那边是按时间排序 —— 时间序的第一条**不是下一个真要跑的任务**；
     *   - 桥做了 `next_run < now` 的严格比较，并且把解析失败的任务也算进 pending；
     *   - 桥顺带给了中文名。
     */
    suspend fun fetchOverviewTasks(
        baseUrls: List<String>,
        instance: String,
    ): Pair<List<ScheduledTask>, List<ScheduledTask>> = withContext(Dispatchers.IO) {
        val data = bridgeGet(baseUrls, "/api/overview_tasks?instance=$instance")
            .optJSONObject("data")
            ?: return@withContext emptyList<ScheduledTask>() to emptyList()

        parseTaskList(data.optJSONArray("pending")) to parseTaskList(data.optJSONArray("waiting"))
    }

    private fun parseTaskList(arr: JSONArray?): List<ScheduledTask> {
        if (arr == null) return emptyList()
        val out = ArrayList<ScheduledTask>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = o.optString("task").ifBlank { continue }
            val next = o.optString("nextRun")
            out += ScheduledTask(
                task = key,
                nextRun = next,
                epochMillis = parseEpoch(next),
                name = o.optString("name").takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    /**
     * 耄耋相接**数据收集**（按侵蚀等级 3 / 5 分组）。
     *
     * 注意别和 [fetchMeowStats] 搞混 —— 那个是「耄耋相接收获」（战利品 CSV），
     * 这个是 PC「耄耋相接数据收集」表格，源头是 `config/cl1_data.db`。
     */
    suspend fun fetchMeowHazard(baseUrls: List<String>, instance: String): MeowHazardStats =        withContext(Dispatchers.IO) {
            val data = bridgeGet(baseUrls, "/api/meow_hazard?instance=$instance")
                .optJSONObject("data")
                ?: return@withContext MeowHazardStats("", emptyList())

            val arr = data.optJSONArray("rows") ?: JSONArray()
            val rows = ArrayList<MeowHazardRow>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                rows += MeowHazardRow(
                    hazardLevel = o.optInt("hazardLevel"),
                    battleCount = o.optInt("battleCount"),
                    rounds = o.optDouble("rounds", 0.0),
                    avgBattleTime = o.optDouble("avgBattleTime", 0.0),
                    avgRoundTime = o.optDouble("avgRoundTime", 0.0),
                    sirenCount = o.optInt("sirenCount"),
                    sirenRate = o.optDouble("sirenRate", 0.0),
                )
            }
            MeowHazardStats(month = data.optString("month"), rows = rows)
        }

    // ── 委托收益统计（数据桥）────────────────────────────────

    /**
     * 委托收益统计：按 [period] 聚合 5 种资源 + 最近几条结算记录。
     *
     * 为什么走桥而不是 MCP：这份数据在 `config/cl1_data.db` 里，
     * 是 AzurPilot 的统计页（`app_stat_commission.py`）自己读的，
     * **MCP 没有对应的工具**。桥直接读同一个库、抄同一套聚合口径，
     * 所以数字和 PC 页面上的一致。
     *
     * [period] 用 [CommissionPeriod.wire]（day / week / month）。
     */
    suspend fun fetchCommissionIncome(
        baseUrls: List<String>,
        instance: String,
        period: CommissionPeriod,
        limit: Int = 10,
    ): CommissionIncome = withContext(Dispatchers.IO) {
        val data = bridgeGet(
            baseUrls,
            "/api/commission_income?instance=$instance&period=${period.wire}&limit=$limit",
        ).optJSONObject("data")
            ?: return@withContext CommissionIncome(false, period, 0, emptyList(), emptyList())

        val rowsArr = data.optJSONArray("rows")
        val rows = ArrayList<CommissionRow>(rowsArr?.length() ?: 0)
        if (rowsArr != null) {
            for (i in 0 until rowsArr.length()) {
                val o = rowsArr.optJSONObject(i) ?: continue
                rows += CommissionRow(
                    name = o.optString("name"),
                    // 桥从 i18n 取的中文名；万一缺了就退回英文键，不要空着
                    label = o.optString("label").ifBlank { o.optString("name") },
                    color = o.optString("color"),
                    total = o.optLong("total"),
                    count = o.optInt("count"),
                    avg = o.optDouble("avg", 0.0),
                )
            }
        }

        val recentArr = data.optJSONArray("recent")
        val recent = ArrayList<CommissionEntry>(recentArr?.length() ?: 0)
        if (recentArr != null) {
            for (i in 0 until recentArr.length()) {
                val o = recentArr.optJSONObject(i) ?: continue
                val itemsObj = o.optJSONObject("items")
                val items = LinkedHashMap<String, Long>()
                if (itemsObj != null) {
                    for (k in itemsObj.keys()) items[k] = itemsObj.optLong(k)
                }
                recent += CommissionEntry(
                    ts = o.optString("ts"),
                    commissionCount = o.optInt("commissionCount"),
                    items = items,
                )
            }
        }

        CommissionIncome(
            available = data.optBoolean("available"),
            period = period,
            totalCommissions = data.optInt("totalCommissions"),
            rows = rows,
            recent = recent,
        )
    }

    // ── 任务配置（MCP）───────────────────────────────────────

    /**
     * 任务配置的中文结构 —— 优先走数据桥。
     *
     * 为什么不用 MCP 的 `get_task_help`：`module/config/mcp_helper.py:63` 查的是
     * `i18n[task_name][group][arg]`，但 i18n 的实际布局是 `i18n[group][arg]`
     * （分组名在顶层），于是 `i18n["Guild"]` 取到 None，**所有参数名退回英文键**。
     * 数据桥里的 `/api/task_schema` 自己做了正确的 join，还顺手遵守了
     * `display: hide`（Command / SuccessInterval 这些在 WebUI 里是藏起来的）。
     *
     * 桥不可用时退回 `get_task_help`（英文名，但至少能改配置）。
     */
    suspend fun fetchTaskConfigSchema(baseUrls: List<String>, task: String): TaskConfig =
        withContext(Dispatchers.IO) {
            // 这里必须切到 IO —— bridgeGet 里是阻塞式 OkHttp 调用，
            // 而调用方（viewModelScope）跑在 Main 上，漏掉就会抛
            // NetworkOnMainThreadException，再被 runCatching 悄悄吞掉，
            // 结果就是「页面永远显示英文」，还不报错。
            val viaBridge = runCatching {
                val data = bridgeGet(baseUrls, "/api/task_schema?task=$task")
                    .optJSONObject("data")
                if (data == null) null else parseBridgeSchema(task, data)
            }.getOrNull()
            if (viaBridge != null && viaBridge.groups.isNotEmpty()) return@withContext viaBridge

            fetchTaskConfigHelp(task)
        }

    /**
     * 任务配置的结构元数据（含中文名、类型、可选项）。
     *
     * 注意：`get_task_help` 返回的 `default` 是 **args.json 里的默认值**，
     * 不是用户当前配置 —— 当前值必须另外调 [fetchTaskConfig]。
     */
    suspend fun fetchTaskConfigHelp(task: String): TaskConfig = withContext(Dispatchers.IO) {
        val json = McpSession(baseUrl).use { session ->
            session.initialize()
            session.callTool("get_task_help", JSONObject().put("task_name", task))
        }
        parseTaskHelp(task, json)
    }

    /** 用户当前配置：`{ "GroupKey": { "ArgKey": value, ... }, ... }` */
    suspend fun fetchTaskConfig(instance: String, task: String): JSONObject = withContext(Dispatchers.IO) {
        val json = McpSession(baseUrl).use { session ->
            session.initialize()
            session.callTool(
                "get_config",
                JSONObject().apply {
                    put("instance", instance)
                    put("task", task)
                },
            )
        }
        runCatching { JSONObject(json) }.getOrElse { JSONObject() }
    }

    /**
     * 一次拿到「结构 + 当前值」。
     *
     * ★ 为什么不是简单地把两个 await 串起来：
     * 结构走**桥的 HTTP**（14ms），当前值走 **MCP 会话**（4 次往返、50ms），
     * 两条链路**完全独立**，没有任何先后依赖。串行跑就是白等一个。
     * 并行之后冷启动一次打开从 ~64ms 降到 ~max(14, 50) ≈ 50ms。
     *
     * [schemaRaw] 是桥返回的原始 `data` 对象，专门为了写缓存 —— 让缓存能存
     * 「服务端原样给的 JSON」而不是我们自己序列化的二手结构，冷启动解析时
     * 和网络路径共用同一个 [parseBridgeSchema]，两边不会长出差异。
     */
    suspend fun fetchConfigBundle(
        baseUrls: List<String>,
        instance: String,
        task: String,
    ): ConfigBundle = withContext(Dispatchers.IO) {
        coroutineScope {
            val schemaDeferred = async {
                runCatching {
                    val data = bridgeGet(baseUrls, "/api/task_schema?task=$task").optJSONObject("data")
                    if (data == null) null else parseBridgeSchema(task, data) to data
                }.getOrNull()
            }
            val valuesDeferred = async { fetchTaskConfig(instance, task) }

            val schemaPair = schemaDeferred.await()
            val values = valuesDeferred.await()

            // 桥不可用才退回 MCP 的 get_task_help（英文名，但至少能改配置）
            val meta = schemaPair?.first ?: fetchTaskConfigHelp(task)

            ConfigBundle(task = meta, schemaRaw = schemaPair?.second, values = values)
        }
    }

    /**
     * 批量预缓存：**一个 MCP 会话**里连续调 N 次 `get_config`。
     *
     * ★ 这是预缓存最省的地方。每开一轮新会话都要付
     * GET /mcp/sse 等 endpoint + initialize + initialized 这套固定成本
     * （实测 12ms 左右，看着不多，但 93 个任务就是 93 次，且每次都要在
     * 服务端那条被 `AzurLaneConfig(inst)` 堵住的单事件循环里排队）。
     * 单会话实测：93 个任务 2572ms（逐个开会话要 4686ms，省 1.8 倍）。
     *
     * 结构走桥的 HTTP，**6 路并发**（互相独立，桥是多线程的）；
     * 当前值**刻意串行**在一条会话上 —— 服务端是单事件循环，
     * 并发打过去只会让每个请求都排在更长的队里，总量反而更慢。
     *
     * 全程可取消（`withContext` 的结构化并发），用户中途进页面/退出不会漏协程。
     */
    suspend fun prefetchTaskConfigs(
        baseUrls: List<String>,
        instance: String,
        tasks: List<String>,
        onEach: suspend (task: String, schemaRaw: JSONObject?, values: JSONObject?) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val total = tasks.size
        if (total == 0) return@withContext 0

        // ── 1. 结构：桥的 HTTP，6 路并发 ──
        val schemas = ConcurrentHashMap<String, JSONObject>()
        var done = 0
        for (chunk in tasks.chunked(SCHEMA_CONCURRENCY)) {
            coroutineScope {
                chunk.map { t ->
                    async {
                        runCatching {
                            bridgeGet(baseUrls, "/api/task_schema?task=$t").optJSONObject("data")
                        }.getOrNull()?.let { schemas[t] = it }
                    }
                }.awaitAll()
            }
            done += chunk.size
            onProgress(done, total)
        }

        // ── 2. 当前值：一条 MCP 会话，串行 N 次 ──
        var ok = 0
        runCatching {
            McpSession(baseUrl).use { session ->
                session.initialize()
                for (t in tasks) {
                    val values = runCatching {
                        val text = session.callTool(
                            "get_config",
                            JSONObject().apply {
                                put("instance", instance)
                                put("task", t)
                            },
                        )
                        JSONObject(text)
                    }.getOrNull()

                    if (values != null) ok++
                    onEach(t, schemas[t], values)
                    done++
                    onProgress(done, total * 2)
                }
            }
        }

        ok
    }

    /** 写入单个配置项 */
    suspend fun updateConfig(
        instance: String,
        task: String,
        group: String,
        arg: String,
        value: Any?,
    ): String = withContext(Dispatchers.IO) {
        McpSession(baseUrl).use { session ->
            session.initialize()
            session.callTool(
                "update_config",
                JSONObject().apply {
                    put("instance", instance)
                    put("task", task)
                    put("group", group)
                    put("arg", arg)
                    put("value", value ?: JSONObject.NULL)
                },
            )
        }
    }

    private fun parseTaskHelp(task: String, json: String): TaskConfig {
        val root = JSONObject(json)
        val groupsObj = root.optJSONObject("groups")
        val groups = ArrayList<ConfigGroup>()
        if (groupsObj != null) {
            for (groupKey in groupsObj.keys()) {
                val g = groupsObj.optJSONObject(groupKey) ?: continue
                val argsObj = g.optJSONObject("arguments")
                val args = ArrayList<ConfigArg>()
                if (argsObj != null) {
                    for (argKey in argsObj.keys()) {
                        val a = argsObj.optJSONObject(argKey) ?: continue
                        val options = LinkedHashMap<String, String>()
                        a.optJSONObject("options")?.let { opts ->
                            for (k in opts.keys()) options[k] = opts.optString(k)
                        }
                        args += ConfigArg(
                            key = argKey,
                            name = a.optString("display_name").ifBlank { argKey },
                            help = a.optString("help"),
                            type = a.optString("type", "input"),
                            default = a.opt("default"),
                            options = options,
                            current = null,
                        )
                    }
                }
                groups += ConfigGroup(
                    key = groupKey,
                    name = g.optString("display_name").ifBlank { groupKey },
                    help = g.optString("help"),
                    args = args,
                )
            }
        }
        return TaskConfig(
            task = task,
            displayName = root.optString("display_name").ifBlank { task },
            help = root.optString("help"),
            groups = groups,
        )
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url).get().build()
        // 用 PLAIN 而不是 HTTP —— 后者 readTimeout=0，普通 GET 会永远挂着
        McpSession.PLAIN.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw McpException("HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    private fun parseIsoToEpoch(ts: String): Long? = try {
        java.time.OffsetDateTime.parse(ts).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            java.time.LocalDateTime.parse(ts.replace(' ', 'T'))
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * 把桥 `/api/task_schema` 返回的 `data` 解析成 [TaskConfig]。
 *
 * 提成**顶层函数**（原来挂在 AzurPilotApi 上是 private）是为了让
 * [ConfigCache] 也能用同一份解析逻辑 —— 缓存里存的是原始 JSON，
 * 冷启动时要靠这个函数把它还原成可渲染的结构。
 * 两处必须完全同源，否则「网络拉的」和「缓存读的」会长得不一样。
 */
internal fun parseBridgeSchema(task: String, data: JSONObject): TaskConfig {
    val groupsArr = data.optJSONArray("groups") ?: JSONArray()
    val groups = ArrayList<ConfigGroup>(groupsArr.length())

    for (i in 0 until groupsArr.length()) {
        val g = groupsArr.optJSONObject(i) ?: continue
        val argsArr = g.optJSONArray("args") ?: JSONArray()
        val args = ArrayList<ConfigArg>(argsArr.length())

        for (j in 0 until argsArr.length()) {
            val a = argsArr.optJSONObject(j) ?: continue
            val options = LinkedHashMap<String, String>()
            a.optJSONObject("options")?.let { opts ->
                for (k in opts.keys()) options[k] = opts.optString(k)
            }
            args += ConfigArg(
                key = a.optString("key"),
                name = a.optString("name").ifBlank { a.optString("key") },
                help = a.optString("help"),
                type = a.optString("type", "input"),
                default = a.opt("default"),
                options = options,
                current = null,
            )
        }

        groups += ConfigGroup(
            key = g.optString("key"),
            name = g.optString("name").ifBlank { g.optString("key") },
            help = g.optString("help"),
            args = args,
        )
    }

    return TaskConfig(
        task = task,
        displayName = data.optString("displayName").ifBlank { task },
        help = data.optString("help"),
        groups = groups,
    )
}
