package com.azurpilot.mobile.data

import android.content.Context
import androidx.core.content.edit

/**
 * 轻量设置存储（SharedPreferences，不引 DataStore）
 *
 * ⚠️ **这里刻意没有任何默认服务器地址。**
 *
 * 早期版本把开发者自己的局域网地址（`http://192.168.x.x:25548`）写成了
 * [DEFAULT_URL]。那对开源是**有害**的：别人装上这个 App 会直接连到
 * 那个地址上去 —— 而 AzurPilot 的 HTTP 接口不做任何鉴权
 * （`fastapi.py` 只注册了 GZip 和一个设 Cache-Control 的中间件，
 * `mcp` 与 `api` 两组路径实测不带凭据即可调用 `get_config` / `update_config`），
 * 等于把一个陌生人的自动化后台交到别人手里。
 *
 * 所以现在的规则是：**地址必须由用户自己在「设置」里填**，代码里只保留一个
 * **示例**地址用于输入框的 placeholder（[SAMPLE_URL]），它永远不会被当作默认值。
 *
 * 注：写路径时别用「斜杠 + 星号」的通配写法 —— Kotlin 的块注释**可以嵌套**，
 * 那个星号会开一个内层注释把后面整段代码吞掉（compiler 只在文件末尾报
 * "Unclosed comment"）。这个坑已经踩过两次了。
 */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("azurpilot_mobile", Context.MODE_PRIVATE)

    /** 服务器地址。**没有默认值** —— 空字符串表示用户还没填过 */
    var serverUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(v) = prefs.edit { putString(KEY_URL, v.trim().trimEnd('/')) }

    var instance: String
        get() = prefs.getString(KEY_INSTANCE, DEFAULT_INSTANCE) ?: DEFAULT_INSTANCE
        set(v) = prefs.edit { putString(KEY_INSTANCE, v) }

    /**
     * 数据桥地址。
     *
     * 留空则自动按 [serverUrl] 的主机名 + [BRIDGE_PORT] 推导 ——
     * 大多数情况下用户只需要填一个服务器地址就够了。
     *
     * 默认端口是 **25550**（`dist\AzurRemBridge.exe` 用的就是它）。
     * 早期版本默认 25549，那个端口上是更早的一版桥（只有 `/api/history`），
     * 新接口一个都没有 —— App 会按候选列表兜住，但默认值不该指向一个不存在的服务。
     */
    var bridgeUrl: String
        get() = prefs.getString(KEY_BRIDGE, "") ?: ""
        set(v) = prefs.edit { putString(KEY_BRIDGE, v.trim().trimEnd('/')) }

    /**
     * 实际使用的数据桥地址。
     *
     * 服务器地址为空时返回空串（而不是某个写死的地址）—— 调用方会把它
     * 当成「没有可用数据桥」，给出「去设置里填地址」的提示，而不是去连一台
     * 跟我们毫无关系的机器。
     */
    val effectiveBridgeUrl: String
        get() = bridgeUrl.ifBlank {
            runCatching {
                val host = java.net.URI(serverUrl).host
                if (host.isNullOrBlank()) "" else "http://$host:$BRIDGE_PORT"
            }.getOrDefault("")
        }

    /** 轮询间隔（秒），默认 30 */
    var pollSeconds: Int
        get() = prefs.getInt(KEY_POLL, 30)
        set(v) = prefs.edit { putInt(KEY_POLL, v.coerceIn(5, 600)) }

    /** 0 跟随系统 / 1 浅色 / 2 深色 */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, 0)
        set(v) = prefs.edit { putInt(KEY_THEME, v) }

    /** 日志一次拉多少行 */
    var logLines: Int
        get() = prefs.getInt(KEY_LOG_LINES, 400)
        set(v) = prefs.edit { putInt(KEY_LOG_LINES, v.coerceIn(100, 2000)) }

    // ── 启动/停止悬浮按钮的位置（吸附左右边缘后记忆） ──

    /** true = 靠右边缘 */
    var fabOnRight: Boolean
        get() = prefs.getBoolean(KEY_FAB_RIGHT, true)
        set(v) = prefs.edit { putBoolean(KEY_FAB_RIGHT, v) }

    /** 垂直位置，0..1 归一化。默认 1.0 = 贴着 Tab 栏上沿（屏幕右下角） */
    var fabYFraction: Float
        get() = prefs.getFloat(KEY_FAB_Y, 1f)
        set(v) = prefs.edit { putFloat(KEY_FAB_Y, v.coerceIn(0f, 1f)) }

    companion object {
        /**
         * 输入框里的**示例**地址，只在界面提示里出现。
         *
         * ★ 它永远不会被当成默认值写进设置 —— 这正是为了开源安全：
         * 任何写死的地址都意味着「装了 App 的人会连到某个特定机器」。
         * 用 RFC 5737 的文档保留网段 + 常见内网网段做示例，不会指向真实主机。
         */
        const val SAMPLE_URL = "http://192.168.1.100:25548"

        const val DEFAULT_INSTANCE = "alas"

        /** 数据桥监听端口 —— 与 `dist\AzurRemBridge.exe` 的默认端口保持一致 */
        const val BRIDGE_PORT = 25550

        private const val KEY_URL = "server_url"
        private const val KEY_BRIDGE = "bridge_url"
        private const val KEY_INSTANCE = "instance"
        private const val KEY_POLL = "poll_seconds"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LOG_LINES = "log_lines"
        private const val KEY_FAB_RIGHT = "fab_right"
        private const val KEY_FAB_Y = "fab_y"
    }
}
