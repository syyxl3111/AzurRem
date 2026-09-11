package com.azurpilot.mobile.data

import com.azurpilot.mobile.ui.theme.RESOURCE_FALLBACK_LABEL
import com.azurpilot.mobile.ui.theme.RESOURCE_ORDER
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val TS_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** AzurPilot 用这个时间戳表示「从未采集到」 */
private const val NEVER_TS = "2020-01-01"

/** 一项资源（对应 Dashboard.<Key>） */
data class ResourceItem(
    val key: String,
    val label: String,
    /** 当前值；null = 未采集 */
    val value: Long?,
    /** 上限（石油/物资有；来自游戏 UI 的 OCR，不是常量） */
    val limit: Long?,
    /** 总量（行动力：当前 + 未开箱） */
    val total: Long?,
    val lastUpdateRaw: String?,
    /** 从未采集过 */
    val never: Boolean,
) {
    /** 进度条填充比例 —— **封顶 1.0，绝不溢出**（石油/物资过了上限仍能继续存） */
    val progress: Float? = run {
        val v = value
        val l = limit
        if (v == null || l == null || l <= 0L) null else (v.toDouble() / l.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }

    /** 是否已超上限（值照实显示，只是进度条满格） */
    val overflow: Boolean = run {
        val v = value
        val l = limit
        v != null && l != null && l > 0L && v > l
    }

    /** 相对时间文案 */
    val ageText: String = when {
        never || value == null -> "未采集"
        lastUpdateRaw == null -> "未采集"
        else -> relativeAge(lastUpdateRaw)
    }
}

/** 调度队列里的一项 */
data class ScheduledTask(
    val task: String,
    val nextRun: String,
    val epochMillis: Long?,
    /** 中文名（桥的 /api/overview_tasks 会给；MCP 那条路没有，为 null） */
    val name: String? = null,
)

/**
 * 概览页的三段队列。
 *
 * PC 端 WebUI 的概览页是**运行中 / 队列中 / 等待中**三段
 * （界面骨架 `app_overview.py:64-87`，填充逻辑 `app_dashboard.py:35-58`）。
 * App 之前只有两段，而且完全没处理「实例没在跑」的情况，所以对不上。
 */
data class OverviewBuckets(
    val running: List<ScheduledTask>,
    val pending: List<ScheduledTask>,
    val waiting: List<ScheduledTask>,
)

/**
 * 把「桥给出的全部逾期任务」切成运行中 / 队列中 —— **逐行对齐 PC**。
 *
 * `module/webui/app_dashboard.py:41-51` 原文：
 * ```python
 * if len(pending_task) >= 1:
 *     if self.alas.alive:                      # 实例活着
 *         running = pending_task[:1]           #   第一个就是正在跑的
 *         pending = pending_task[1:]           #   其余进队列中
 *     else:                                    # 实例没跑
 *         running = []                         #   运行中清空
 *         pending = pending_task[:]            #   全部留在队列中，一个不减
 * else:
 *     running = []; pending = []
 * ```
 *
 * 最容易漏的是 `else` 那一支：**停止状态下三段的分母是不一样的**。
 * 而且停止时恰好「谁在跑」无从谈起，硬按时间切会把本该在「运行中」的那个
 * 也留在队列里 —— 这就是 App 之前和 PC 对不上的直接原因。
 *
 * ⚠️ `waiting` 不受 alive 影响，原样透传。
 */
fun splitOverview(
    alive: Boolean,
    pendingAll: List<ScheduledTask>,
    waiting: List<ScheduledTask>,
): OverviewBuckets = when {
    pendingAll.isEmpty() -> OverviewBuckets(emptyList(), emptyList(), waiting)
    alive -> OverviewBuckets(
        running = pendingAll.take(1),
        pending = pendingAll.drop(1),
        waiting = waiting,
    )
    else -> OverviewBuckets(emptyList(), pendingAll, waiting)
}

/** 一轮轮询拿到的完整快照 */
data class Snapshot(
    val running: Boolean,
    val stateCode: Int,
    val currentTask: String,
    val resources: List<ResourceItem>,
    val queue: List<ScheduledTask>,
    val logs: List<String>,
    val fetchedAtMillis: Long,
)

/** state 语义见 ProcessManager.state：1 运行 / 2 停止 / 3 异常 / 4 更新中 */
object RunState {
    const val RUNNING = 1
    const val STOPPED = 2
    const val ERROR = 3
    const val UPDATING = 4

    fun label(code: Int): String = when (code) {
        RUNNING -> "运行中"
        STOPPED -> "已停止"
        ERROR -> "异常"
        UPDATING -> "更新中"
        else -> "未知"
    }
}

// ─────────────────────────────────────────────────────────────
// 降采样
// ─────────────────────────────────────────────────────────────

/**
 * 按桶降采样（桶内取平均）。
 *
 * 服务端会返回全量采样点（实测行动力 3000+、资源快照 5000+），直接画进
 * ~900px 的图里就是一团噪声。平均之后趋势形状还在，点数降到可控范围。
 */
fun downsampleTrend(points: List<TrendPoint>, buckets: Int): List<TrendPoint> {
    if (buckets <= 0 || points.size <= buckets) return points
    val out = ArrayList<TrendPoint>(buckets)
    val chunk = points.size.toDouble() / buckets
    for (i in 0 until buckets) {
        val from = (i * chunk).toInt()
        val to = ((i + 1) * chunk).toInt().coerceAtMost(points.size)
        if (from >= to) continue
        var sum = 0L
        for (j in from until to) sum += points[j].value
        out += TrendPoint(points[(from + to) / 2].epochMillis, sum / (to - from))
    }
    return out
}

// ─────────────────────────────────────────────────────────────
// 解析
// ─────────────────────────────────────────────────────────────

fun parseBooleanOrNull(raw: String?): Boolean? {
    val s = raw?.trim()?.lowercase() ?: return null
    return when (s) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> null
    }
}

/** get_status → [{"instance":"alas","running":true,"state":1}] */
fun parseStatus(json: String, instance: String): Pair<Boolean, Int> {
    val arr = JSONArray(json)
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        if (o.optString("instance") == instance) {
            return o.optBoolean("running", false) to o.optInt("state", RunState.STOPPED)
        }
    }
    // 没匹配到实例时退回第一条
    val first = arr.optJSONObject(0)
    return (first?.optBoolean("running", false) ?: false) to (first?.optInt("state", RunState.STOPPED) ?: RunState.STOPPED)
}

/** get_resources → { "Oil": {"label":"石油","value":2517,"limit":11200,"last_update":"..."} } */
fun parseResources(json: String): List<ResourceItem> {
    val root = JSONObject(json)
    val out = ArrayList<ResourceItem>(root.length())
    val seen = HashSet<String>()

    fun emit(key: String) {
        val o = root.optJSONObject(key) ?: return
        if (!seen.add(key)) return
        val lastUpdate = if (o.has("last_update") && !o.isNull("last_update")) o.optString("last_update") else null
        val never = lastUpdate == null || lastUpdate.startsWith(NEVER_TS)
        out += ResourceItem(
            key = key,
            label = o.optString("label").ifBlank { RESOURCE_FALLBACK_LABEL[key] ?: key },
            // 从未采集的资源，服务端会返回 value=0 + 时间戳 2020-01-01。
            // 直接透传会显示成「0」，看起来像「真的是 0」；置空才会走「未采集」分支。
            value = if (never) null else o.optLongOrNull("value"),
            limit = o.optLongOrNull("limit"),
            total = o.optLongOrNull("total"),
            lastUpdateRaw = lastUpdate,
            never = never,
        )
    }

    RESOURCE_ORDER.forEach(::emit)
    // 服务端将来若新增资源，追加在后面，不丢数据
    root.keys().forEach { emit(it) }
    return out
}

/** get_scheduler_queue → [{"task":"Commission","next_run":"2026-09-11 02:38:02"}] */
fun parseQueue(json: String): List<ScheduledTask> {
    val arr = JSONArray(json)
    val out = ArrayList<ScheduledTask>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val task = o.optString("task").ifBlank { continue }
        val next = o.optString("next_run")
        // epoch 要算出来 —— 队列中/等待中是按「NextRun 有没有到」切的
        out += ScheduledTask(task, next, parseEpoch(next))
    }
    return out
}

/**
 * 把调度队列切成 队列中 / 等待中。
 *
 * 判据与 AzurPilot 的 `AzurLaneConfig.get_next_task()` 一致：
 *   pending  = 已启用 且 NextRun <= now   （时间到了，等着跑）
 *   waiting  = 已启用 且 NextRun >  now   （还没到点）
 * 两边都按时间升序。
 */
fun splitQueue(queue: List<ScheduledTask>): Pair<List<ScheduledTask>, List<ScheduledTask>> {
    val now = System.currentTimeMillis()
    val pending = ArrayList<ScheduledTask>()
    val waiting = ArrayList<ScheduledTask>()
    for (task in queue) {
        val at = task.epochMillis
        if (at != null && at <= now) pending += task else waiting += task
    }
    pending.sortBy { it.epochMillis ?: Long.MAX_VALUE }
    waiting.sortBy { it.epochMillis ?: Long.MAX_VALUE }
    return pending to waiting
}

internal fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return try {
        get(key).let { v ->
            when (v) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal fun parseEpoch(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    return try {
        LocalDateTime.parse(text.trim(), TS_FMT)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

private fun relativeAge(raw: String): String {
    val then = try {
        LocalDateTime.parse(raw.trim(), TS_FMT)
    } catch (_: Exception) {
        return "未采集"
    }
    val now = LocalDateTime.now()
    val secs = ChronoUnit.SECONDS.between(then, now)
    return when {
        secs < 0 -> "刚刚"
        secs < 60 -> "刚刚"
        secs < 3600 -> "${secs / 60} 分钟前"
        secs < 86_400 -> "${secs / 3600} 小时前"
        secs < 86_400 * 30 -> "${secs / 86_400} 天前"
        else -> "${secs / (86_400 * 30)} 个月前"
    }
}

// ─────────────────────────────────────────────────────────────
// 日志清洗
// ─────────────────────────────────────────────────────────────

private val NP_WRAPPER = Regex("""np\.(?:int64|int32|float64|float32|bool_|str_|uint8)\(([^()]*)\)""")

/**
 * 服务端返回的日志有两个实测问题：
 *  1. Rich 补齐的行尾空格（最长约 120 字符）
 *  2. numpy 类型泄漏成字面量 `np.int64(281)`
 */
fun cleanLogLine(raw: String): String =
    raw.trimEnd().replace(NP_WRAPPER, "$1")

fun parseLogs(raw: String): List<String> =
    raw.split('\n').map(::cleanLogLine)

/**
 * 跨轮次去重：以「上一次的最后一行」为锚点，
 * 在本次结果里从后往前找它，只追加其后的内容。
 *
 * ⚠️ **这个方法有已知缺陷，只在桥不可达时当兜底用。**
 * 日志里重复行极常见（`潜艇呼叫计时器到达`、`[战斗UI] PAUSE_OldeRoyal` 之类），
 * 锚点会命中**更早的那一次**，于是重复显示或丢行。
 * 桥在线时走 [LogTail] 的**字节 offset 增量**，那一类问题整类消失。
 */
fun mergeLogs(old: List<String>, fresh: List<String>, maxLines: Int = 2000): List<String> {
    if (fresh.isEmpty()) return old
    if (old.isEmpty()) return fresh.takeLast(maxLines)
    val anchor = old.last()
    for (i in fresh.indices.reversed()) {
        if (fresh[i] == anchor) {
            return (old + fresh.subList(i + 1, fresh.size)).takeLast(maxLines)
        }
    }
    return (old + fresh).takeLast(maxLines)
}

/**
 * 增量日志的一次拉取结果（桥的 `/api/logs/tail`）。
 *
 * 服务端**无状态**：`offset` 由客户端带着来回传。这样 App 重启、
 * 切后台回来、甚至换设备，都不用跟服务端对账。
 */
data class LogTail(
    /** 服务端实际读的那个文件（每天 0 点会换名字，客户端据此判断有没有轮转） */
    val file: String,
    val size: Long,
    /** 下次要带的 offset（服务端只保证前进到最后一个换行符，半行留给下次） */
    val offset: Long,
    /** true = 客户端持有的 offset 已失效（文件变小/换文件），应当用本次结果整体替换 */
    val reset: Boolean,
    /** true = 本次没读完（撞到 max_bytes），客户端应立刻再拉一次 */
    val truncated: Boolean,
    val lines: List<String>,
)
