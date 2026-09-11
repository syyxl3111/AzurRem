package com.azurpilot.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 版本更新检查 —— 数据源是**本项目的 GitHub Releases**。
 *
 * ── 为什么要走 Releases 而不是别的 ─────────────────────────────
 *
 * `GET /repos/{owner}/{repo}/releases/latest` 是**匿名可访问**的
 * （实测 HTTP 200，不需要 token），返回里直接带 `tag_name` 和
 * `assets[].browser_download_url`。所以 App 不需要任何密钥就能查版本、
 * 也能直接下 APK —— 这正是「后续更新版本号检测 GitHub、直接拉 apk」的实现方式。
 *
 * ── 约定（发新版时必须遵守，否则检查不到）──────────────────────
 *
 *   1. **Release 的 tag 用 `v<版本号>`**，例如 `v1.0.2`。
 *      解析时会剥掉前缀 `v`。写成 `1.0.2` 或 `v1.0.2` 都能认。
 *   2. **APK 作为 Release 资产上传，文件名以 `.apk` 结尾**。
 *      有多个 `.apk` 资产时取**第一个**，所以一个 Release 只放一个 APK。
 *   3. 版本号用点分数字（`1.0.2`）。比较是**逐段按数值**比的，
 *      不是字符串比较 —— 否则 `1.0.10` 会被判成比 `1.0.9` 旧。
 */
class UpdateChecker {

    /** 一次检查的结果。null 表示「已是最新」或「查不到」 */
    data class UpdateInfo(
        /** 远端版本号，已剥掉 `v` 前缀，例如 `1.0.3` */
        val version: String,
        /** Release 标题 */
        val title: String,
        /** Release 说明（Markdown 原文） */
        val notes: String,
        /** APK 直链 */
        val apkUrl: String,
        /** APK 字节数，用于显示下载体积 */
        val apkSize: Long,
    )

    sealed interface Result {
        /** 有新版本 */
        data class Available(val info: UpdateInfo) : Result

        /** 已经是最新 */
        data class UpToDate(val current: String, val latest: String) : Result

        /** 查不到 —— 没网 / GitHub 挂了 / 仓库还没发过 Release */
        data class Failed(val reason: String) : Result
    }

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            HTTP.newCall(request).execute().use { resp ->
                // 404 = 这个仓库还没有任何 Release。这不是错误，是「还没发过版」
                if (resp.code == 404) {
                    return@withContext Result.Failed("这个仓库还没有发布过 Release")
                }
                if (!resp.isSuccessful) {
                    return@withContext Result.Failed("GitHub 返回 HTTP ${resp.code}")
                }

                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                val tag = root.optString("tag_name").trim()
                if (tag.isBlank()) return@withContext Result.Failed("Release 里没有 tag_name")

                val latest = normalizeVersion(tag)
                if (compareVersions(latest, currentVersion) <= 0) {
                    return@withContext Result.UpToDate(currentVersion, latest)
                }

                val assets = root.optJSONArray("assets")
                var apkUrl = ""
                var apkSize = 0L
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.optString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = a.optString("browser_download_url")
                            apkSize = a.optLong("size")
                            break
                        }
                    }
                }
                if (apkUrl.isBlank()) {
                    return@withContext Result.Failed("新版 $latest 的 Release 里没有 APK 附件")
                }

                Result.Available(
                    UpdateInfo(
                        version = latest,
                        title = root.optString("name").ifBlank { "v$latest" },
                        notes = root.optString("body"),
                        apkUrl = apkUrl,
                        apkSize = apkSize,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.Failed(e.message?.takeIf { it.isNotBlank() } ?: "检查更新失败")
        }
    }

    /**
     * 下载 APK 到应用私有目录。
     *
     * 放 `filesDir/updates/` 而不是 cacheDir —— 系统安装器是**另一个进程**，
     * 通过 FileProvider 读这个文件；cacheDir 在系统存储紧张时可能被清掉，
     * 而且用户从通知栏回来重新点安装时会发现文件没了。
     *
     * 返回下载好的文件。
     */
    suspend fun download(
        info: UpdateInfo,
        targetDir: File,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(info.apkUrl).get().build()
        HTTP.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("下载失败：HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("下载失败：响应为空")
            val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize

            targetDir.mkdirs()
            // 先写 .part 再改名 —— 下到一半失败/被杀，不会留一个「看起来完整」的
            // APK 让安装器去解析，那会报一个跟真实原因毫无关系的解析错误
            val tmp = File(targetDir, "${info.version}.apk.part")
            val out = File(targetDir, "AzurRem-${info.version}.apk")
            tmp.delete()

            var received = 0L
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        received += n
                        onProgress(received, total)
                    }
                }
            }

            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, overwrite = true)
                tmp.delete()
            }
            out
        }
    }

    companion object {
        /** 官方仓库。改这里就能换成自己的 fork */
        const val OWNER = "syyxl3111"
        const val REPO = "AzurRem"

        private const val RELEASE_API =
            "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

        /**
         * 下载专用的客户端。
         *
         * 单独一个而不是复用 MCP 的：那个为了 SSE 把 readTimeout 设成了 0（永不超时），
         * 而这里下的是 13MB 的文件，需要一个**整体**超时兜底，
         * 否则网络卡住时进度条会停在某个百分比永远不动。
         */
        private val HTTP: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.MINUTES)
                .build()
        }

        /** `v1.0.2` → `1.0.2`；顺手去掉可能的 `release-` 之类前缀 */
        fun normalizeVersion(raw: String): String =
            raw.trim().removePrefix("v").removePrefix("V").trim()

        /**
         * 逐段按数值比较版本号。a > b 返回正数，a < b 返回负数，相等返回 0。
         *
         * ★ **不能直接比字符串**：`"1.0.10" < "1.0.9"` 在字符串序下是 true，
         * 于是发到第 10 个补丁版时更新检查会开始说「已是最新」，
         * 而用户手上其实是旧版 —— 这种 bug 在发到 1.0.10 之前完全看不出来。
         *
         * 非数字段（`1.0.2-beta` 的 `beta`）直接忽略。
         */
        fun compareVersions(a: String, b: String): Int {
            val pa = parseSegments(a)
            val pb = parseSegments(b)
            val n = maxOf(pa.size, pb.size)
            for (i in 0 until n) {
                val x = pa.getOrElse(i) { 0 }
                val y = pb.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }

        private fun parseSegments(v: String): List<Int> =
            normalizeVersion(v)
                .split('.', '-', '+', '_')
                .mapNotNull { it.trim().toIntOrNull() }
    }
}
