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

    /**
     * 服务器地址。**没有默认值** —— 空字符串表示用户还没填过。
     *
     * 读写两头都过一遍 [normalizeUrl]：写的时候把用户裸填的域名补全，
     * 读的时候兜住**升级前已经存进去的**裸域名（比如老版本存下的
     * `example.com`）—— 只补写不补读的话，那些用户升上来仍然连不上。
     */
    var serverUrl: String
        get() = normalizeUrl(prefs.getString(KEY_URL, "") ?: "")
        set(v) = prefs.edit { putString(KEY_URL, normalizeUrl(v)) }

    var instance: String
        get() = prefs.getString(KEY_INSTANCE, DEFAULT_INSTANCE) ?: DEFAULT_INSTANCE
        set(v) = prefs.edit { putString(KEY_INSTANCE, v) }

    /**
     * 网关地址。
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
     * WebUI 密码（= AzurPilot `config/deploy.yaml` 里的 `Password`）。
     *
     * **为什么需要它**：AzurPilot 从 2026-09-11 的提交 `a265c98de` 起给 MCP
     * 加了鉴权（`module/webui/mcp_auth.py`），密钥复用 WebUI 那一把。没设密码的
     * 实例不受影响（`mcp_auth.enabled()` 为 false 时全放行），但只要服务端设了
     * 密码，不带凭据访问 `/mcp/sse` 就一律 401 —— **MCP 通道整个失效**。
     *
     * 网页端也是这么做的：`utils.py:706` 的 `login()` 会弹一次密码框，记住之后
     * 靠 `localStorage["password"]` 免密。App 这里只是把同一个交互补上，
     * 不是引入新概念。
     *
     * ⚠️ 明文存在 SharedPreferences 里。没有引 EncryptedSharedPreferences 是
     * 刻意的：那要额外拉 security-crypto 依赖，而它防的是「别的 App 读不到」——
     * root 过的机器一样能读。对一台自建服务的密码来说这个投入产出不划算。
     * 真要更强应该走 Android Keystore 自己加密，那是另一件事。
     */
    var webuiPassword: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(v) = prefs.edit { putString(KEY_PASSWORD, v.trim()) }

    /**
     * 实际使用的网关地址。
     *
     * 服务器地址为空时返回空串（而不是某个写死的地址）—— 调用方会把它
     * 当成「没有可用网关」，给出「去设置里填地址」的提示，而不是去连一台
     * 跟我们毫无关系的机器。
     */
    val effectiveBridgeUrl: String
        get() = bridgeUrl.ifBlank {
            runCatching {
                val uri = java.net.URI(serverUrl)
                val host = uri.host
                when {
                    host.isNullOrBlank() -> ""
                    // ① 直连 AzurPilot（25548）：数据在**旁边那个**网关上，
                    //    换个端口去找它。
                    uri.port == AP_PORT -> "http://$host:$BRIDGE_PORT"
                    // ② 网关模式：MCP、只读接口、桥接口全在**同一个 origin** 上
                    //    （AzurRem 网关就是这么设计的），所以服务器地址本身就是
                    //    数据地址，不用再推导。
                    //    公网 `https://example.com` 走的也是这条 —— 少一个要填的字段。
                    else -> serverUrl.trimEnd('/')
                }
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
         *
         * 端口是 **25550**（网关），不是 25548（AzurPilot WebUI）—— 从 1.0.4 起
         * App 连的是网关，一个地址管全部。填 25548 也能用（那是直连 AP 的老路子，
         * 数据接口会自动去 25550 找），但新用户没必要走那条。
         */
        const val SAMPLE_URL = "http://192.168.1.100:25550"

        /**
         * 「地址还没填」的统一提示。
         *
         * ★ 必须和「连接失败」分开：连接失败让人去查网络/查端口，而这里要做的事
         * 只有一件 —— 去设置里把地址填上。原先 `pollOnce` 里有一份内联的措辞，
         * 其余路径则会掉进 `AzurPilotApi` 那句「服务器地址无效：」——
         * 一个带冒号却没有下文的错误，新人完全不知道下一步干嘛。
         * 现在两边共用这一句。
         */
        const val BLANK_URL_HINT =
            "还没填服务器地址。\n去「设置 → 服务器地址」填上你电脑的地址，例如 $SAMPLE_URL"

        const val DEFAULT_INSTANCE = "alas"

        //: 判断「已经带了 scheme」用。scheme 的合法字符是字母数字加 + - .
        private val SCHEME_RE = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://")
        private val IPV4_RE = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

        /**
         * 把用户填的地址补全成带 scheme 的 URL。
         *
         * ★ 为什么必须有这个：人会像在浏览器地址栏里那样直接敲 `example.com`。
         *   浏览器会自动补 `https://`，但 `HttpUrl.toHttpUrlOrNull()` 对没有
         *   scheme 的串**直接返回 null** —— 于是 App 报「服务器地址无效：example.com」，
         *   而用户刚刚才在同一个手机的浏览器里打开过这个域名，完全想不通哪里错了。
         *   实测就是这么翻车的。
         *
         * 补哪个 scheme：
         *   · IP 字面量 / localhost / IPv6 → `http://`（内网网关就是明文 HTTP）
         *   · 其余（域名）                → `https://`（公网入口，能加密就加密）
         *
         * 用户在输入框里**显式写了** scheme 的话一律不覆盖 —— 想用 http 就自己写。
         */
        fun normalizeUrl(raw: String): String {
            val text = raw.trim().trimEnd('/')
            if (text.isEmpty()) return ""
            if (SCHEME_RE.containsMatchIn(text)) return text

            val host = text.substringBefore('/').substringBefore(':')
            val isLiteralHost = IPV4_RE.matches(host) ||
                host.equals("localhost", ignoreCase = true) ||
                text.startsWith("[")          // IPv6 字面量：[::1]:25550
            return (if (isLiteralHost) "http://" else "https://") + text
        }

        /** 网关监听端口 —— 与 `dist\AzurRemBridge.exe` 的默认端口保持一致 */
        const val BRIDGE_PORT = 25550

        /**
         * AzurPilot WebUI 自己的默认端口。
         *
         * 只用来判断"用户填的是不是直连 AP" —— 是的话数据得去 [BRIDGE_PORT] 找；
         * 不是（走网关）就用服务器地址本身。
         */
        const val AP_PORT = 25548

        private const val KEY_URL = "server_url"
        private const val KEY_BRIDGE = "bridge_url"
        private const val KEY_PASSWORD = "webui_password"
        private const val KEY_INSTANCE = "instance"
        private const val KEY_POLL = "poll_seconds"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LOG_LINES = "log_lines"
        private const val KEY_FAB_RIGHT = "fab_right"
        private const val KEY_FAB_Y = "fab_y"
    }
}
