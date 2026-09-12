package com.azurpilot.mobile.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 任务配置缓存 —— 「拉一次，之后秒开」。
 *
 * ── 为什么必须做这个 ──────────────────────────────────────────
 *
 * 服务端 `mcp_server_sse.py` 的 `_tool_get_config` 长这样：
 *
 * ```python
 * async def _tool_get_config(arguments):
 *     config = AzurLaneConfig(inst)        # ← 每次都重建整个配置对象
 *     data = config.data.get(task, {})
 * ```
 *
 * 它声明成 `async def`，但 `AzurLaneConfig(inst)` 是一个**同步重活**
 * （读全部配置 + 深度合并默认值 + 按 argument.yaml 校验），跑在 uvicorn 的
 * **单事件循环**上 —— 执行期间整个循环都被堵住，连新会话的 SSE 握手帧都发不出去。
 *
 * 而 App 每打开一个配置页要**新开一轮 MCP 会话**：
 *     GET /mcp/sse 等 endpoint  →  initialize  →  notifications/initialized
 *     →  tools/call get_config
 * 整整 4 次往返，每一次都得排在别人后面。实测（PC 本地、无竞争）：
 *     桥 /api/task_schema   14 ms
 *     SSE 握手               7 ms
 *     initialize             3 ms
 *     initialized 通知       2 ms
 *     get_config            26 ms（最慢 135 ms）
 *     ────────────────────────────
 *     合计                  50 ms
 * 手机上要再叠加 WiFi 往返和信号抖动，于是用户看到的就是「点进去卡一下」。
 *
 * ── 为什么不改服务端 ──────────────────────────────────────────
 *
 * 用户约束：不动 AzurPilot 安装目录里的任何东西。而且那份代码会被
 * 自动更新覆盖。所以缓存只能放在**手机这边**。
 *
 * ── 设计 ────────────────────────────────────────────────────
 *
 *  - **内存**：命中即 0 成本，连 JSON 都不重解析。
 *  - **磁盘**：`filesDir/config_cache.json`，杀进程重开也还是秒开。
 *  - **stale-while-revalidate**：命中就先显示，后台静默校验，有新值再无缝替换。
 *    所以「秒开」不等于「看到旧数据」——只是旧数据不会挡着你先看到页面。
 *  - 写盘**不在主线程**，也不是每条一写：整批预缓存结束后落一次盘。
 *
 * 缓存键带 server 和 instance —— 换实例或换电脑时不会串数据。
 */
class ConfigCache(private val dir: File) {

    /**
     * 生产用法：缓存文件放应用私有目录。
     *
     * 之所以主构造器收的是 [File] 而不是 Context —— 是为了能在**纯 JVM 单元测试**里
     * 传一个临时目录把整套磁盘读写真跑一遍（见 `src/test/.../ConfigCacheTest.kt`）。
     * 这类代码一旦写错是静默失效的：JSON 坏掉 → 每次读都返回 null →
     * 表现成「缓存从来没生效」，在真机上完全看不出是哪一层断的。
     */
    constructor(context: Context) : this(context.filesDir)

    private val file = File(dir, FILE_NAME)
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "config-cache-io").apply { isDaemon = true }
    }

    /** 磁盘上有没有东西还没落盘 */
    private val dirty = AtomicBoolean(false)

    // ── 内存层 ──
    private val schemaMem = ConcurrentHashMap<String, TaskConfig>()
    private val valuesMem = ConcurrentHashMap<String, JSONObject>()
    private val savedAtMem = ConcurrentHashMap<String, Long>()
    /** 磁盘上还没被解析过的原始 JSON，惰性解析用 */
    private val schemaRawMem = ConcurrentHashMap<String, String>()
    private val valuesRawMem = ConcurrentHashMap<String, String>()

    @Volatile private var loaded = false

    /**
     * 缓存键：`server|instance|task`。
     *
     * 必须**归一化**（去首尾空格 + 去结尾斜杠），因为设置页里地址是手输的：
     * `http://h:1` / `http://h:1/` / `"  http://h:1  "` 指的是同一台机器，
     * 键要是长得不一样，用户改一下输入就被判成「换了服务器」，整份缓存白存，
     * 而且**完全不报错** —— 只是又变慢了，根本查不出来。
     */
    fun key(server: String, instance: String, task: String): String =
        "${server.trim().trimEnd('/')}|${instance.trim()}|$task"

    // ─────────────────────────────────────────────────────
    // 读
    // ─────────────────────────────────────────────────────

    /** 已解析的结构。命中就是纯内存读，不走网络也不解析 JSON */
    fun schema(k: String): TaskConfig? {
        ensureLoaded()
        schemaMem[k]?.let { return it }
        // 磁盘上有、但还没解析过 —— 解析一次并留在内存
        val raw = schemaRawMem[k] ?: return null
        val parsed = runCatching {
            parseBridgeSchema(k.substringAfterLast('|'), JSONObject(raw))
        }.getOrNull() ?: return null
        schemaMem[k] = parsed
        return parsed
    }

    /** 用户当前配置值 */
    fun values(k: String): JSONObject? {
        ensureLoaded()
        valuesMem[k]?.let { return it }
        val raw = valuesRawMem[k] ?: return null
        val parsed = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        valuesMem[k] = parsed
        return parsed
    }

    /** 这条缓存的写入时间（epoch millis），没有则 0 */
    fun savedAt(k: String): Long {
        ensureLoaded()
        return savedAtMem[k] ?: 0L
    }

    /** 有完整的一条（结构和值都在）才算命中 —— 只有一半没法渲染 */
    fun has(k: String): Boolean = schema(k) != null && values(k) != null

    // ─────────────────────────────────────────────────────
    // 写
    // ─────────────────────────────────────────────────────

    /**
     * 存一条。
     *
     * [schemaData] 是桥 `/api/task_schema` 返回的 `data` 对象（原始 JSON），
     * [values] 是 `get_config` 的结果。任一为 null 表示这次没拿到，保留旧值不动 ——
     * 半个新数据比不上一个完整的旧数据。
     */
    fun put(k: String, task: String, schemaData: JSONObject?, values: JSONObject?) {
        ensureLoaded()
        var changed = false

        if (schemaData != null) {
            runCatching { parseBridgeSchema(task, schemaData) }.getOrNull()?.let { parsed ->
                schemaMem[k] = parsed
                schemaRawMem[k] = schemaData.toString()
                changed = true
            }
        }
        if (values != null) {
            valuesMem[k] = values
            valuesRawMem[k] = values.toString()
            changed = true
        }
        if (changed) {
            savedAtMem[k] = System.currentTimeMillis()
            dirty.set(true)
        }
    }

    /** 丢掉一条（比如换实例、或保存后发现对不上） */
    fun invalidate(k: String) {
        ensureLoaded()
        schemaMem.remove(k)
        valuesMem.remove(k)
        schemaRawMem.remove(k)
        valuesRawMem.remove(k)
        savedAtMem.remove(k)
        dirty.set(true)
    }

    fun clear() {
        schemaMem.clear(); valuesMem.clear()
        schemaRawMem.clear(); valuesRawMem.clear(); savedAtMem.clear()
        loaded = true
        io.execute {
            runCatching {
                file.delete()
                File(dir, "$FILE_NAME.tmp").delete()
            }
        }
    }

    /** 已缓存的条目数（设置页显示用） */
    fun size(): Int {
        ensureLoaded()
        return schemaRawMem.size
    }

    // ─────────────────────────────────────────────────────
    // 磁盘
    // ─────────────────────────────────────────────────────

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching { loadFromDisk() }
            loaded = true
        }
    }

    private fun loadFromDisk() {
        if (!file.exists()) return
        val root = JSONObject(file.readText())
        val entries = root.optJSONObject("e") ?: return
        for (k in entries.keys()) {
            val e = entries.optJSONObject(k) ?: continue
            e.optLong("t", 0L).takeIf { it > 0 }?.let { savedAtMem[k] = it }
            e.optString("s").takeIf { it.isNotEmpty() }?.let { schemaRawMem[k] = it }
            e.optString("g").takeIf { it.isNotEmpty() }?.let { valuesRawMem[k] = it }
        }
    }

    /**
     * 落盘。**异步 + 合并**：预缓存会连续 put 93 次，这里只真正写一次。
     */
    fun flush() {
        val payload = takeSnapshot() ?: return
        io.execute { writeAtomically(payload) }
    }

    /** 同步落盘。只给单元测试用 —— 生产路径永远走 [flush] 的异步版本。 */
    internal fun flushBlocking() {
        val payload = takeSnapshot() ?: return
        writeAtomically(payload)
    }

    /** 等所有异步写盘做完。只给单元测试用。 */
    internal fun awaitIdle() {
        runCatching { io.submit { }.get() }
    }

    /**
     * 把当前内存状态序列化出来。**没有改动就返回 null** ——
     * 在调用的这一刻抓内容，避免写盘期间内存又被改（写出去的会是半新半旧的）。
     */
    private fun takeSnapshot(): String? {
        if (!dirty.compareAndSet(true, false)) return null
        val entries = JSONObject()
        for (k in schemaRawMem.keys) {
            val e = JSONObject()
            e.put("t", savedAtMem[k] ?: 0L)
            schemaRawMem[k]?.let { e.put("s", it) }
            valuesRawMem[k]?.let { e.put("g", it) }
            entries.put(k, e)
        }
        return JSONObject().apply {
            put("v", FORMAT_VERSION)
            put("e", entries)
        }.toString()
    }

    /**
     * 先写临时文件再改名 —— 直接覆盖的话，写一半被杀进程会留下
     * 半个 JSON，下次启动整份缓存都读不出来（而且是**静默**读不出来）。
     */
    private fun writeAtomically(payload: String) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(payload)
            if (file.exists() && !file.delete()) return@runCatching
            if (!tmp.renameTo(file)) {
                // 改名可能因为被占用/跨卷失败 —— 退回直接写，别把这次数据丢了
                file.writeText(payload)
                tmp.delete()
            }
        }
    }

    private companion object {
        const val FILE_NAME = "config_cache.json"
        const val FORMAT_VERSION = 1
    }
}
