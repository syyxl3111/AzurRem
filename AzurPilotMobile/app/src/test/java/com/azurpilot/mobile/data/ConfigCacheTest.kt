package com.azurpilot.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * ConfigCache 的磁盘往返测试。
 *
 * ── 为什么要为这个写测试 ──────────────────────────────────────
 *
 * 缓存坏掉的方式是**静默**的：磁盘上 JSON 一旦写坏（或者键拼错、
 * 或者 rename 失败没兜住），`schema()` / `values()` 全部返回 null。
 * 在真机上这表现成「缓存从来没生效过，打开还是慢」，而**没有任何报错** ——
 * 你没法从界面上分辨是「没缓存」还是「缓存了但读不回来」。
 *
 * 所以这里把最容易出错的几处钉死：
 *   1. 内存命中（不走磁盘）
 *   2. 磁盘往返（换一个实例读同一目录）
 *   3. **写坏的文件不能让 App 崩**，只是退回「没有缓存」
 *   4. 只有一半的条目（有结构没值）不算命中 —— 半个数据渲染不出来
 *   5. 键必须带 server + instance，换实例不能串数据
 *   6. 没有改动时 flush 不写盘
 *   7. 原子写不留 .tmp 垃圾
 *
 * 跑：`gradlew :app:testDebugUnitTest`
 */
class ConfigCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun schemaData(task: String, argName: String = "启用该功能") = JSONObject(
        """
        {
          "task": "$task",
          "displayName": "委托",
          "help": "",
          "groups": [
            {
              "key": "Scheduler",
              "name": "任务设置",
              "help": "",
              "args": [
                {
                  "key": "Enable",
                  "name": "$argName",
                  "help": "将这个任务加入调度器",
                  "type": "checkbox",
                  "default": false,
                  "options": {"True": "已启用", "False": "关闭"}
                }
              ]
            }
          ]
        }
        """.trimIndent(),
    )

    private fun values(enable: Boolean) = JSONObject(
        """{"Scheduler": {"Enable": $enable}}""",
    )

    private fun newCache() = ConfigCache(tmp.root)

    // ─────────────────────────────────────────────────────

    @Test
    fun `put 之后内存里立刻能读到`() {
        val c = newCache()
        val k = c.key("http://h:1", "alas", "Commission")
        c.put(k, "Commission", schemaData("Commission"), values(true))

        val schema = c.schema(k)
        assertNotNull("结构应该命中", schema)
        assertEquals("委托", schema!!.displayName)
        assertEquals(1, schema.groups.size)
        assertEquals("启用该功能", schema.groups[0].args[0].name)

        val v = c.values(k)
        assertNotNull("值应该命中", v)
        assertTrue(v!!.getJSONObject("Scheduler").getBoolean("Enable"))

        assertTrue("结构和值都有才算命中", c.has(k))
        assertEquals(1, c.size())
    }

    @Test
    fun `落盘之后换一个实例读同一目录仍然命中`() {
        val k = newCache().key("http://h:1", "alas", "Commission")

        newCache().apply {
            put(k, "Commission", schemaData("Commission"), values(false))
            flushBlocking()
        }

        // 全新实例 = 模拟杀进程重开
        val reopened = newCache()
        assertTrue("重开后应该命中", reopened.has(k))
        assertEquals("委托", reopened.schema(k)!!.displayName)
        assertFalse(
            "值要原样读回来",
            reopened.values(k)!!.getJSONObject("Scheduler").getBoolean("Enable"),
        )
        assertTrue("写入时间应该被记住", reopened.savedAt(k) > 0L)
    }

    @Test
    fun `磁盘文件被写坏时不崩 只是退回没有缓存`() {
        val k = newCache().key("http://h:1", "alas", "Commission")
        newCache().apply {
            put(k, "Commission", schemaData("Commission"), values(true))
            flushBlocking()
        }
        // 模拟写到一半被杀进程 / 磁盘出错
        java.io.File(tmp.root, "config_cache.json").writeText("""{"v":1,"e":{"a":""")

        val reopened = newCache()
        assertNull("坏文件应该当成没有缓存", reopened.schema(k))
        assertNull(reopened.values(k))
        assertFalse(reopened.has(k))
        assertEquals(0, reopened.size())

        // 而且还能继续正常用（覆盖掉那份坏数据）
        reopened.put(k, "Commission", schemaData("Commission"), values(true))
        reopened.flushBlocking()
        assertTrue(newCache().has(k))
    }

    @Test
    fun `只有结构没有值不算命中`() {
        val c = newCache()
        val k = c.key("http://h:1", "alas", "Commission")
        c.put(k, "Commission", schemaData("Commission"), null)

        assertNotNull("结构在", c.schema(k))
        assertNull("值不在", c.values(k))
        assertFalse("半个条目渲染不出来，不能算命中 —— 否则打开会是一片空白", c.has(k))
    }

    @Test
    fun `只有值没有结构也不算命中`() {
        val c = newCache()
        val k = c.key("http://h:1", "alas", "Commission")
        c.put(k, "Commission", null, values(true))

        assertNull(c.schema(k))
        assertNotNull(c.values(k))
        assertFalse(c.has(k))
    }

    @Test
    fun `键带 server 和 instance 换实例不会串数据`() {
        val c = newCache()
        val a = c.key("http://h:1", "alas", "Commission")
        val b = c.key("http://h:1", "alas2", "Commission")
        val d = c.key("http://h:2", "alas", "Commission")

        assertTrue("三个键必须互不相同", setOf(a, b, d).size == 3)

        c.put(a, "Commission", schemaData("Commission"), values(true))
        assertTrue(c.has(a))
        assertFalse("换实例不该命中", c.has(b))
        assertFalse("换服务器不该命中", c.has(d))
    }

    @Test
    fun `键对 server 结尾斜杠和空格不敏感`() {
        val c = newCache()
        val a = c.key("http://h:1", "alas", "Commission")
        val b = c.key("http://h:1/", "alas", "Commission")
        val d = c.key("  http://h:1  ", "alas", "Commission")
        assertEquals(a, b)
        assertEquals(a, d)
    }

    @Test
    fun `没有改动时 flush 不写盘`() {
        val c = newCache()
        c.flushBlocking()
        assertFalse("没 put 过就不该产生文件", java.io.File(tmp.root, "config_cache.json").exists())

        val k = c.key("http://h:1", "alas", "Commission")
        c.put(k, "Commission", schemaData("Commission"), values(true))
        c.flushBlocking()
        assertTrue(java.io.File(tmp.root, "config_cache.json").exists())

        val before = java.io.File(tmp.root, "config_cache.json").lastModified()
        Thread.sleep(20)
        c.flushBlocking()   // 第二次没改动
        assertEquals("第二次 flush 不该重写", before, java.io.File(tmp.root, "config_cache.json").lastModified())
    }

    @Test
    fun `原子写不留 tmp 垃圾`() {
        val c = newCache()
        val k = c.key("http://h:1", "alas", "Commission")
        c.put(k, "Commission", schemaData("Commission"), values(true))
        c.flushBlocking()

        val leftovers = tmp.root.listFiles()?.map { it.name }.orEmpty()
        assertEquals("目录里应该只有正式文件：$leftovers", listOf("config_cache.json"), leftovers)
    }

    @Test
    fun `invalidate 之后不再命中`() {
        val c = newCache()
        val k = c.key("http://h:1", "alas", "Commission")
        c.put(k, "Commission", schemaData("Commission"), values(true))
        assertTrue(c.has(k))

        c.invalidate(k)
        assertFalse(c.has(k))
        assertEquals(0, c.size())

        c.flushBlocking()
        assertFalse("失效也要落盘，否则下次重开又冒出来", newCache().has(k))
    }

    @Test
    fun `clear 之后磁盘也空了`() {
        val k = newCache().key("http://h:1", "alas", "Commission")
        val c = newCache()
        c.put(k, "Commission", schemaData("Commission"), values(true))
        c.flushBlocking()

        c.clear()
        c.awaitIdle()   // clear 的删除是异步的
        assertFalse(java.io.File(tmp.root, "config_cache.json").exists())
        assertFalse(newCache().has(k))
    }

    @Test
    fun `异步 flush 之后数据确实落到了盘上`() {
        val k = newCache().key("http://h:1", "alas", "Commission")
        val c = newCache()
        c.put(k, "Commission", schemaData("Commission"), values(true))
        c.flush()
        c.awaitIdle()

        assertTrue("flush 是异步的，awaitIdle 之后必须已经写完", newCache().has(k))
    }

    @Test
    fun `同一个缓存里多个任务互不干扰`() {
        val c = newCache()
        val ks = c.key("http://h:1", "alas", "Commission")
        val kg = c.key("http://h:1", "alas", "Guild")

        c.put(ks, "Commission", schemaData("Commission", "委托开关"), values(true))
        c.put(kg, "Guild", schemaData("Guild", "大舰队开关"), values(false))
        c.flushBlocking()

        val reopened = newCache()
        assertEquals(2, reopened.size())
        assertEquals("委托开关", reopened.schema(ks)!!.groups[0].args[0].name)
        assertEquals("大舰队开关", reopened.schema(kg)!!.groups[0].args[0].name)
        assertTrue(reopened.values(ks)!!.getJSONObject("Scheduler").getBoolean("Enable"))
        assertFalse(reopened.values(kg)!!.getJSONObject("Scheduler").getBoolean("Enable"))
    }

    @Test
    fun `put 只给值时会保留原来的结构`() {
        val c = newCache()
        val k = c.key("http://h:1", "alas", "Commission")
        c.put(k, "Commission", schemaData("Commission"), values(false))
        // 保存一个配置项时走的就是这条路：只回填 values，schemaData 传 null
        c.put(k, "Commission", null, values(true))

        assertNotNull("结构不该被抹掉", c.schema(k))
        assertTrue(c.values(k)!!.getJSONObject("Scheduler").getBoolean("Enable"))
        assertEquals("委托", c.schema(k)!!.displayName)
    }
}
