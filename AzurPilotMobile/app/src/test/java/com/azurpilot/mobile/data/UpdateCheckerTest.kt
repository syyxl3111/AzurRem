package com.azurpilot.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本号比较的单元测试。
 *
 * ── 为什么这个必须测 ──────────────────────────────────────────
 *
 * 更新检查的核心判断是「远端版本 > 本地版本」。如果这里写成字符串比较，
 * `"1.0.10" < "1.0.9"` 会成立 —— 于是**发到第 10 个补丁版的那天**，
 * App 会开始告诉所有人「已是最新」，而他们手上其实是旧版。
 *
 * 这种 bug 在 `1.0.9 → 1.0.10` 之前**完全看不出来**，一旦触发又很难联想到
 * 是比较函数的问题（表现是「检查更新一直是好的，突然就不提示了」）。
 * 所以把边界一次性钉死。
 *
 * 跑：`gradlew :app:unitTest`
 */
class UpdateCheckerTest {

    private fun cmp(a: String, b: String) = UpdateChecker.compareVersions(a, b)

    @Test
    fun `相同版本返回 0`() {
        assertEquals(0, cmp("1.0.2", "1.0.2"))
        assertEquals(0, cmp("v1.0.2", "1.0.2"))
        assertEquals(0, cmp("1.0", "1.0.0"))
        assertEquals(0, cmp("1", "1.0.0"))
    }

    @Test
    fun `逐段按数值比 而不是字符串比`() {
        // ★ 这一组就是字符串比较会挂的地方
        assertTrue("1.0.10 必须大于 1.0.9", cmp("1.0.10", "1.0.9") > 0)
        assertTrue("1.0.9 必须小于 1.0.10", cmp("1.0.9", "1.0.10") < 0)
        assertTrue("1.10.0 必须大于 1.9.0", cmp("1.10.0", "1.9.0") > 0)
        assertTrue("2.0.0 必须大于 1.99.99", cmp("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun `v 前缀不影响比较`() {
        assertEquals(0, cmp("v1.0.2", "V1.0.2"))
        assertTrue(cmp("v1.0.3", "1.0.2") > 0)
        assertTrue(cmp("1.0.2", "v1.0.3") < 0)
    }

    @Test
    fun `段数不同时缺的段按 0 算`() {
        assertTrue("1.0.1 大于 1.0", cmp("1.0.1", "1.0") > 0)
        assertEquals(0, cmp("1.0.0", "1.0"))
        assertTrue("1.1 大于 1.0.9", cmp("1.1", "1.0.9") > 0)
    }

    @Test
    fun `非数字后缀会被忽略`() {
        // 1.0.2-beta 解析成 [1,0,2]，和 1.0.2 等价
        assertEquals(0, cmp("1.0.2-beta", "1.0.2"))
        assertEquals(0, cmp("1.0.2+build7", "1.0.2"))
        assertTrue(cmp("1.0.3-rc1", "1.0.2") > 0)
    }

    @Test
    fun `脏输入不会抛异常`() {
        // 远端 tag 是人手写的，可能什么样都有 —— 不能因为一个怪 tag 就让检查更新崩掉
        assertEquals(0, cmp("", ""))
        assertEquals(0, cmp("abc", "abc"))
        assertTrue("解析不出数字时视为 0，任何正常版本都算更新", cmp("1.0.0", "abc") > 0)
        assertEquals(0, cmp("v", ""))
    }

    @Test
    fun `normalizeVersion 只剥前缀`() {
        assertEquals("1.0.2", UpdateChecker.normalizeVersion("v1.0.2"))
        assertEquals("1.0.2", UpdateChecker.normalizeVersion("V1.0.2"))
        assertEquals("1.0.2", UpdateChecker.normalizeVersion("  1.0.2  "))
        assertEquals("1.0.2-beta", UpdateChecker.normalizeVersion("v1.0.2-beta"))
        // 中间或结尾的 v 不该被动
        assertEquals("1.0.2v", UpdateChecker.normalizeVersion("1.0.2v"))
    }

    @Test
    fun `真实升级路径`() {
        // 按顺序每一步都必须判定为「有新版」
        val chain = listOf("1.0.0", "1.0.1", "1.0.2", "1.0.9", "1.0.10", "1.0.11", "1.1.0", "2.0.0")
        for (i in 0 until chain.size - 1) {
            assertTrue(
                "${chain[i]} -> ${chain[i + 1]} 应该判定为有新版",
                cmp(chain[i + 1], chain[i]) > 0,
            )
            assertTrue(
                "${chain[i + 1]} -> ${chain[i]} 不该判定为有新版",
                cmp(chain[i], chain[i + 1]) <= 0,
            )
        }
    }
}
