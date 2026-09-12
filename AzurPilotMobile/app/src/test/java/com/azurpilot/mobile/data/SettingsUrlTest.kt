package com.azurpilot.mobile.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 服务器地址补全的单元测试。
 *
 * ── 为什么这个必须测 ────────────────────────────────────────
 *
 * 人会像在浏览器地址栏里那样**直接敲域名**：`example.com`。浏览器会自动补
 * `https://`，所以用户觉得这个地址是好的；但 `HttpUrl.toHttpUrlOrNull()`
 * 对没有 scheme 的串**直接返回 null** —— App 报「服务器地址无效：example.com」，
 * 而用户刚在同一个手机的浏览器里打开过它，完全想不通哪里错了。
 *
 * 真机实测就是这么翻车的（真机 / 流量 / 公网，存储里是裸域名，连不上；
 * 模拟器上因为是我用 adb 写的完整 https:// 地址，所以没事 —— 这种
 * 「只在用户手动输入时才出现」的 bug，靠人工点两下很容易漏掉）。
 *
 * 跑：`gradlew :app:unitTest`
 */
class SettingsUrlTest {

    private fun n(raw: String) = Settings.normalizeUrl(raw)

    @Test
    fun `空地址保持为空`() {
        assertEquals("", n(""))
        assertEquals("", n("   "))
    }

    @Test
    fun `裸域名补 https`() {
        assertEquals("https://example.com", n("example.com"))
        assertEquals("https://example.com", n("example.com/"))
        assertEquals("https://example.com", n("  example.com  "))
        assertEquals("https://example.com", n("example.com"))
    }

    @Test
    fun `IP 与 localhost 补 http`() {
        assertEquals("http://192.168.1.100:25550", n("192.168.1.100:25550"))
        assertEquals("http://10.0.2.2:25550", n("10.0.2.2:25550"))
        assertEquals("http://192.168.1.100", n("192.168.1.100"))
        assertEquals("http://localhost:25550", n("localhost:25550"))
        assertEquals("http://[::1]:25550", n("[::1]:25550"))

        // 主机名大小写不敏感，所以这里只断言**语义**：补对了 scheme、主机对得上。
        // normalizeUrl 的职责是「补 scheme」，不是「把 URL 规范化」——
        // 真去动主机名的大小写反而多一处能出错的地方。
        assertEquals("localhost", n("LOCALHOST:25550").toHttpUrlOrNull()?.host)
    }

    @Test
    fun `显式写了 scheme 就一律不覆盖`() {
        // 想用 http 就自己写 —— 内网域名没证书的时候这是刚需
        assertEquals("http://example.com", n("http://example.com"))
        assertEquals("https://example.com", n("https://example.com"))
        assertEquals("http://192.168.1.100:25548", n("http://192.168.1.100:25548"))
        assertEquals("https://example.com:8443", n("https://example.com:8443"))
    }

    @Test
    fun `带路径时按主机判断而不是按整串`() {
        assertEquals("https://example.com/relay", n("example.com/relay"))
        assertEquals("http://192.168.1.100:25550/api", n("192.168.1.100:25550/api"))
    }

    @Test
    fun `末尾斜杠会被去掉`() {
        assertEquals("https://example.com", n("https://example.com/"))
        assertEquals("http://192.168.1.100:25548", n("http://192.168.1.100:25548/"))
    }

    @Test
    fun `补全后一定能被当作合法 HTTP URL 解析`() {
        // 这条是真正要保证的性质：补全的目的就是让 toHttpUrlOrNull() 不再返回 null
        for (raw in listOf("example.com", "192.168.1.100:25550", "localhost:25550")) {
            val url = n(raw).toHttpUrlOrNull()
            requireNotNull(url) { "补全后仍然解析不了：$raw -> ${n(raw)}" }
        }
    }
}
